package ir.pardava.mobile.core

import android.os.Handler
import android.os.Looper
import ir.pardava.mobile.data.PardavaApi
import ir.pardava.mobile.data.UpdateProfileIn
import ir.pardava.mobile.data.dto.RefreshIn
import ir.pardava.mobile.data.dto.TokenOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Owns the OkHttp/Retrofit stack:
 * - Authorization + Accept-Language headers
 * - 401 → single-flight refresh → retry (rotated refresh token persisted)
 * - refresh failure → session cleared + [onSessionExpired] (login screen)
 */
class ApiClient(
    val session: SessionManager,
    private val store: TokenStore,
    private val debugLogging: Boolean = false,
) {

    /** Invoked (main thread) when the refresh chain fails — UI navigates to login. */
    var onSessionExpired: (() -> Unit)? = null

    /** Current UI language for Accept-Language; updated by the language switcher. */
    @Volatile
    var acceptLanguage: String = "fa"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Bare client for the refresh call — must NOT recurse through the authenticator. */
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val authenticator = okhttp3.Authenticator { _, response ->
        val refreshed = synchronized(this) {
            val currentAccess = session.access
            val requestAuth = response.request.header("Authorization")
            // Another thread already rotated the token while we waited: just retry.
            if (currentAccess != null && requestAuth != null && requestAuth != "Bearer $currentAccess") {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccess")
                    .build()
            }
            val refreshTok = session.refresh ?: return@synchronized null
            val newTokens = refreshBlocking(refreshTok) ?: run {
                runBlocking { session.clear() }
                Handler(Looper.getMainLooper()).post { onSessionExpired?.invoke() }
                return@synchronized null
            }
            runBlocking { session.save(newTokens.access_token, newTokens.refresh_token, newTokens.user.id) }
            response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.access_token}")
                .build()
        }
        refreshed
    }

    private fun refreshBlocking(refreshToken: String): TokenOut? = runCatching {
        val payload = json.encodeToString(RefreshIn.serializer(), RefreshIn(refreshToken))
        val request = Request.Builder()
            .url("${session.baseUrl}api/v1/auth/refresh")
            .post(payload.toRequestBody())
            .build()
        refreshClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) return@use null
            json.decodeFromString(TokenOut.serializer(), body ?: return@use null)
        }
    }.getOrNull()

    private val api: PardavaApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (debugLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val ok = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor())
            .addInterceptor(logging)
            .authenticator(authenticator)
            .build()
        Retrofit.Builder()
            .baseUrl(session.baseUrl)
            .client(ok)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PardavaApi::class.java)
    }

    private fun headerInterceptor() = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        session.access?.let { builder.header("Authorization", "Bearer $it") }
        builder.header("Accept-Language", acceptLanguage)
        builder.header("X-Client", "pardava-android")
        chain.proceed(builder.build())
    }

    /** Relative signed-media URLs (e.g. `/api/v1/media/...`) → absolute. */
    fun absoluteUrl(url: String): String =
        if (url.startsWith("http")) url else session.baseUrl.trimEnd('/') + url

    fun api(): PardavaApi = api

    suspend fun logout() {
        runCatching { api().logout(ir.pardava.mobile.data.dto.LogoutIn(session.refresh ?: "")) }
        session.clear()
    }

    suspend fun setLanguage(lang: String) {
        acceptLanguage = lang
        runCatching { api().updateMe(body = UpdateProfileIn(preferred_language = lang)) }
    }
}

private fun String.toRequestBody(): okhttp3.RequestBody =
    okhttp3.RequestBody.create("application/json".toMediaType(), this)

/**
 * In-memory mirror of the persisted tokens so interceptors never block on DataStore.
 */
class SessionManager(private val store: TokenStore, scope: CoroutineScope) {

    @Volatile
    var access: String? = null
        private set

    @Volatile
    var refresh: String? = null
        private set

    @Volatile
    var baseUrl: String = BuildConfigDefault.url
        private set

    @Volatile
    var userId: Long? = null
        private set

    private val readySignal = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Completes once the persisted session has been read from DataStore. */
    suspend fun awaitReady() = readySignal.await()

    init {
        scope.launch {
            store.baseUrl.collect { url ->
                baseUrl = url.trimEnd('/') + "/"
            }
        }
        scope.launch {
            var first = true
            store.tokens.collect { t ->
                access = t.access
                refresh = t.refresh
                if (first) {
                    first = false
                    readySignal.complete(Unit)
                }
            }
        }
    }

    suspend fun restore() {
        val snap = store.snapshot()
        access = snap.access
        refresh = snap.refresh
        baseUrl = snap.baseUrl.trimEnd('/') + "/"
    }

    suspend fun save(accessToken: String, refreshToken: String, userId: Long? = null) {
        store.save(accessToken, refreshToken, userId)
        access = accessToken
        refresh = refreshToken
        userId?.let { this.userId = it }
    }

    suspend fun clear() {
        store.clear()
        access = null
        refresh = null
    }

    val isSignedIn: Boolean get() = refresh != null
}

/** Helper: run a suspended API call and translate [HttpException] into [ApiException]. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw ApiException(ApiError.from(e))
}

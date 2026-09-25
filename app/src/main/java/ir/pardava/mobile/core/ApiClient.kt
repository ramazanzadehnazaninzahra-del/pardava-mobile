package ir.pardava.mobile.core

import android.os.Handler
import android.os.Looper
import ir.pardava.mobile.data.PardavaApi
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.Envelope
import ir.pardava.mobile.data.dto.UserDto
import ir.pardava.mobile.data.dto.requireOk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
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
 * Owns the OkHttp/Retrofit stack for the Pardava Courses API:
 * - Base URL is the SITE ROOT (https://pardava.ir/); paths carry api/courses/…
 * - Single session token (pdv_…) sent as Authorization: Bearer — no refresh flow.
 * - Every HTTP answer is 200; envelope ok=false is translated into [ApiException].
 * - HTML proxy/error pages (e.g. wrong URL) surface as friendly network errors.
 */
class ApiClient(
    val session: SessionManager,
    private val store: TokenStore,
    private val debugLogging: Boolean = false,
) {

    /** Invoked (main thread) when a call proves the session token is dead — UI offers re-login. */
    var onSessionExpired: (() -> Unit)? = null

    /** Current UI language for Accept-Language; updated by the language switcher. */
    @Volatile
    var acceptLanguage: String = "fa"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private val apiLock = Any()

    @Volatile
    private var apiInstance: PardavaApi? = null

    @Volatile
    private var apiBaseUrl: String? = null

    /** Retrofit instance, rebuilt whenever [SessionManager.baseUrl] changes. */
    val api: PardavaApi
        get() {
            val url = session.baseUrl
            var inst = apiInstance
            if (inst == null || apiBaseUrl != url) {
                synchronized(apiLock) {
                    inst = apiInstance
                    if (inst == null || apiBaseUrl != url) {
                        val logging = HttpLoggingInterceptor().apply {
                            level = if (debugLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                        }
                        val ok = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .addInterceptor(headerInterceptor())
                            .addInterceptor(logging)
                            .build()
                        inst = Retrofit.Builder()
                            .baseUrl(url)
                            .client(ok)
                            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                            .build()
                            .create(PardavaApi::class.java)
                        apiBaseUrl = url
                        apiInstance = inst
                    }
                }
            }
            return inst!!
        }

    private fun headerInterceptor() = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        session.token?.let { builder.header("Authorization", "Bearer $it") }
        builder.header("Accept-Language", acceptLanguage)
        builder.header("X-Client", "pardava-android")
        chain.proceed(builder.build())
    }

    /** Relative URLs from the server (covers, avatars, …) → absolute against the site root. */
    fun absoluteUrl(url: String?): String? =
        url?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http")) it else session.baseUrl.trimEnd('/') + it
        }

    /**
     * Run a suspended API call and normalize every failure mode into [ApiException]:
     * - envelope ok=false → its own code/message
     * - HTTP errors (rare, e.g. CDN hiccups) → status code
     * - HTML instead of JSON (proxy error pages) → friendly message
     * - auth failures clear the local session and notify the UI once.
     */
    suspend fun <T : Envelope> call(block: suspend () -> T): T = try {
        block().requireOk()
    } catch (e: ApiException) {
        if (e.isAuthError && session.isSignedIn) {
            session.clear()
            Handler(Looper.getMainLooper()).post { onSessionExpired?.invoke() }
        }
        throw e
    } catch (e: HttpException) {
        throw ApiException(e.code(), friendlyHttpMessage(e.code()))
    } catch (e: SerializationException) {
        throw ApiException(0, "پاسخ سرور معتبر نبود. آدرس سرور را در تنظیمات بررسی کنید.")
    } catch (e: java.io.IOException) {
        throw ApiException(0, "به سرور دسترسی نیست. اتصال اینترنت را بررسی کنید.")
    }

    private fun friendlyHttpMessage(status: Int): String = when (status) {
        404 -> "این بخش روی سرور پیدا نشد. آدرس سرور را در تنظیمات بررسی کنید."
        in 500..599 -> "سرور موقتاً در دسترس نیست. کمی بعد دوباره تلاش کنید."
        else -> "مشکلی پیش آمد (کد $status). دوباره تلاش کنید."
    }

    suspend fun logout() {
        val token = session.token
        runCatching {
            if (token != null) api.logout()
        }
        session.clear()
    }

    suspend fun setLanguage(lang: String) {
        acceptLanguage = lang
    }
}

/**
 * In-memory mirror of the persisted session so interceptors never block on DataStore.
 * Holds the single session token, the cached user profile and the site root URL.
 */
class SessionManager(private val store: TokenStore, scope: CoroutineScope) {

    /** Notified whenever the signed-in state flips — drives reactive UI updates. */
    var onSessionChanged: ((Boolean) -> Unit)? = null

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var user: UserDto? = null
        private set

    @Volatile
    var baseUrl: String = normalizeBaseUrl(BuildConfigDefault.url)
        private set

    private val readySignal = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Completes once the persisted session has been read from DataStore. */
    suspend fun awaitReady() = readySignal.await()

    init {
        scope.launch {
            store.baseUrl.collect { url -> baseUrl = normalizeBaseUrl(url) }
        }
        scope.launch {
            var first = true
            store.session.collect { s ->
                token = s.token
                if (first) {
                    first = false
                    readySignal.complete(Unit)
                }
            }
        }
    }

    suspend fun restore() {
        val snap = store.snapshot()
        token = snap.token
        user = snap.user
        baseUrl = normalizeBaseUrl(snap.baseUrl)
    }

    suspend fun saveSession(newToken: String, newUser: UserDto?) {
        store.save(newToken, newUser)
        token = newToken
        user = newUser
        onSessionChanged?.invoke(true)
    }

    suspend fun clear() {
        store.clear()
        token = null
        user = null
        onSessionChanged?.invoke(false)
    }

    /** Runtime site-root switching (settings screen) — memory + DataStore, instant. */
    suspend fun setBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        store.setBaseUrl(normalized)
        baseUrl = normalized
    }

    val isSignedIn: Boolean get() = token != null

    companion object {
        /**
         * Accepts "pardava.ir", "pardava.ir/", "https://pardava.ir" … and always
         * returns a Retrofit-ready absolute URL ending in exactly one '/'.
         * Bare hosts get https:// — plain http stays for LAN debugging servers.
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) url = BuildConfigDefault.url.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            url = url.trimEnd('/')
            return "$url/"
        }

        /**
         * v0.5.0 migration: sessions configured against the retired local
         * FastAPI (10.0.2.2 / localhost / :8100) are moved to the production site.
         */
        fun migrateLegacyUrl(stored: String, default: String): String {
            val host = runCatching { java.net.URI(stored).host }.getOrNull()
            return if (host in LEGACY_HOSTS || stored.contains(":8100")) default else stored
        }

        private val LEGACY_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")
    }
}

/**
 * Unit-test indirection so the default URL can be read without BuildConfig
 * (mirrors the value configured in app/build.gradle.kts).
 */
object BuildConfigDefault {
    @Volatile
    var url: String = "https://pardava.ir/"

    @Volatile
    var googleClientId: String = ""
}

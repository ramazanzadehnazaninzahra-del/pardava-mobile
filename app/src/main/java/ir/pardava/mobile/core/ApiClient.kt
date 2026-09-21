package ir.pardava.mobile.core

import ir.pardava.mobile.data.PardavaApi
import ir.pardava.mobile.data.dto.UserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Owns the OkHttp/Retrofit stack for the site Courses API:
 * - Bearer token + Accept-Language headers
 * - Retrofit rebuilt whenever [SessionManager.baseUrl] changes
 * - No refresh flow (the site issues one long-lived token per session)
 */
class ApiClient(
    val session: SessionManager,
    private val store: TokenStore,
    private val debugLogging: Boolean = false,
) {

    /** Current UI language for Accept-Language; updated by the language switcher. */
    @Volatile
    var acceptLanguage: String = "fa"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val apiLock = Any()

    @Volatile
    private var apiInstance: PardavaApi? = null

    @Volatile
    private var apiBaseUrl: String? = null

    /**
     * Retrofit instance, rebuilt lazily whenever [SessionManager.baseUrl] changes
     * (server-address setting). All traffic goes through [api].
     */
    private val api: PardavaApi
        get() {
            val url = session.baseUrl
            var inst = apiInstance
            if (inst == null || apiBaseUrl != url) {
                synchronized(apiLock) {
                    inst = apiInstance
                    if (inst == null || apiBaseUrl != url) {
                        val logging = HttpLoggingInterceptor().apply {
                            level = if (debugLogging) {
                                HttpLoggingInterceptor.Level.BASIC
                            } else {
                                HttpLoggingInterceptor.Level.NONE
                            }
                        }
                        val ok = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
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
        session.token?.let {
            builder.header("Authorization", "Bearer $it")
            builder.header("X-Api-Token", it)
        }
        builder.header("Accept-Language", acceptLanguage)
        builder.header("X-Client", "pardava-android")
        chain.proceed(builder.build())
    }

    /** Relative server URLs (e.g. lesson file links) → absolute against the prefix. */
    fun absoluteUrl(url: String): String =
        if (url.startsWith("http")) url else session.baseUrl.trimEnd('/') + "/" + url.trimStart('/')

    /** Base URL of the courses index WITHOUT trailing slash (Flask route ""). */
    val coursesIndexUrl: String get() = session.baseUrl.trimEnd('/')

    fun api(): PardavaApi = api

    suspend fun logout() {
        runCatching { apiCall { api().logout() } }
        session.clear()
    }

    /** Validate a pasted/issued token via /auth/me, store it, and return the profile. */
    suspend fun signInWithToken(raw: String): UserDto {
        val previous = session.token
        session.token = raw.trim()
        try {
            val me = apiCall { api().me() }
            val user = me.user ?: UserDto()
            session.save(raw.trim())
            session.profile = user
            return user
        } catch (e: Exception) {
            session.token = previous
            throw e
        }
    }

    suspend fun saveLogin(token: String, user: UserDto?) {
        session.save(token.trim())
        session.profile = user
    }

    suspend fun refreshProfile(): UserDto? = try {
        val me = apiCall { api().me() }
        me.user?.also { session.profile = it }
    } catch (e: ApiException) {
        // Token revoked/expired server-side → drop the local session.
        if (e.error.status == 401) session.clear()
        null
    }
}

/**
 * In-memory mirror of the persisted session so interceptors never block on DataStore.
 */
class SessionManager(private val store: TokenStore, scope: CoroutineScope) {

    @Volatile
    var token: String? = null
        internal set

    @Volatile
    var baseUrl: String = BuildConfigDefault.url
        private set

    /** Latest known profile (from login response or /auth/me). */
    @Volatile
    var profile: UserDto? = null

    private val _signedIn = MutableStateFlow(false)

    /** Reactive sign-in state for screens that must refresh after login/logout. */
    val signedIn: StateFlow<Boolean> = _signedIn

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
            store.token.collect { t ->
                token = t
                _signedIn.value = t != null
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
        baseUrl = snap.baseUrl.trimEnd('/') + "/"
        _signedIn.value = snap.token != null
    }

    suspend fun save(newToken: String) {
        store.save(newToken)
        token = newToken
        _signedIn.value = true
    }

    suspend fun clear() {
        store.clear()
        token = null
        profile = null
        _signedIn.value = false
    }

    /** Runtime server switching — memory + DataStore, instant. */
    suspend fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/') + "/"
        store.setBaseUrl(normalized)
        baseUrl = normalized
    }

    val isSignedIn: Boolean get() = token != null
}

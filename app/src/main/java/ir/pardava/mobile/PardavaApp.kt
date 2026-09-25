package ir.pardava.mobile

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.BuildConfigDefault
import ir.pardava.mobile.core.EventLogger
import ir.pardava.mobile.core.FontScale
import ir.pardava.mobile.core.SessionManager
import ir.pardava.mobile.core.ThemeMode
import ir.pardava.mobile.core.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Appearance preferences surfaced in the Settings screen. */
data class AppSettings(
    val themeMode: String = ThemeMode.SYSTEM,
    val fontScale: String = FontScale.NORMAL,
)

class PardavaApp : Application() {

    lateinit var session: SessionManager
        private set
    lateinit var api: ApiClient
        private set
    lateinit var logger: EventLogger
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var store: TokenStore

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * Compose-observable signed-in state — flips the instant a session is saved
     * or cleared (Google web-bridge, OTP, password, logout) so every open screen
     * refreshes without re-entering it.
     */
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /**
     * One-time exchange code delivered by the pardava://auth/callback deep link
     * after the Google web-bridge flow completes on pardava.ir. Consumed by the
     * navigation host which swaps the code for a real pdv_ session token.
     */
    private val _pendingAuthCode = MutableStateFlow<String?>(null)
    val pendingAuthCode: StateFlow<String?> = _pendingAuthCode.asStateFlow()

    fun postAuthCode(code: String) {
        _pendingAuthCode.value = code
    }

    fun consumeAuthCode() {
        _pendingAuthCode.value = null
    }

    override fun onCreate() {
        super.onCreate()
        store = TokenStore(this)
        // Wire BuildConfig defaults through the indirection object (test-friendly).
        BuildConfigDefault.url = BuildConfig.DEFAULT_BASE_URL
        BuildConfigDefault.googleClientId = BuildConfig.GOOGLE_CLIENT_ID
        session = SessionManager(store, appScope)
        session.onSessionChanged = { signed -> _signedIn.value = signed }
        api = ApiClient(session, store, debugLogging = BuildConfig.DEBUG)
        logger = EventLogger.get(api, store) { currentLanguage() }
        logger.start(this)
        appScope.launch {
            session.restore()
            _signedIn.value = session.isSignedIn
        }
        // install & session telemetry (site-side customer_events, source=android)
        appScope.launch {
            val firstInstall = store.installId.first().isBlank()
            if (firstInstall) {
                logger.log(
                    "app_install",
                    detail = mapOf(
                        "version" to BuildConfig.VERSION_NAME,
                        "version_code" to BuildConfig.VERSION_CODE.toString(),
                        "sdk" to android.os.Build.VERSION.SDK_INT.toString(),
                        "model" to android.os.Build.MODEL.take(40),
                    ),
                )
            }
            logger.log("app_session", detail = mapOf("version" to BuildConfig.VERSION_NAME))
        }
        appScope.launch {
            store.themeMode.collect { mode -> _settings.value = _settings.value.copy(themeMode = mode) }
        }
        appScope.launch {
            store.fontScale.collect { scale -> _settings.value = _settings.value.copy(fontScale = scale) }
        }
        // First launch: default to Persian; afterwards the stored per-app locale
        // wins (autoStoreLocales keeps it across processes).
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
        }
        api.acceptLanguage = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags().take(2).ifEmpty { "fa" }
    }

    /** Apply in-app language switch (fa/en) — triggers activity recreation. */
    fun setAppLanguage(lang: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        appScope.launch { api.setLanguage(lang) }
    }

    fun currentLanguage(): String =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().split("-").firstOrNull() ?: "fa"

    fun setThemeMode(mode: String) {
        appScope.launch { store.setThemeMode(mode) }
    }

    fun setFontScale(scale: String) {
        appScope.launch { store.setFontScale(scale) }
    }

    /** Build-time identity for the in-app update pipeline. */
    val versionCode: Int get() = BuildConfig.VERSION_CODE
    val versionName: String get() = BuildConfig.VERSION_NAME

    /** Resolve a site-relative path against the active site root. */
    fun siteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val root = session.baseUrl.trimEnd('/')
        return if (path.startsWith("/")) "$root$path" else "$root/$path"
    }

    fun applicationContextCompat(): android.content.Context = this
}

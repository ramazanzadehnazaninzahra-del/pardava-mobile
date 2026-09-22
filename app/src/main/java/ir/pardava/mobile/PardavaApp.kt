package ir.pardava.mobile

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.BuildConfigDefault
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var store: TokenStore

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        store = TokenStore(this)
        // Wire BuildConfig defaults through the indirection object (test-friendly).
        BuildConfigDefault.url = BuildConfig.DEFAULT_BASE_URL
        BuildConfigDefault.googleClientId = BuildConfig.GOOGLE_CLIENT_ID
        session = SessionManager(store, appScope)
        api = ApiClient(session, store, debugLogging = BuildConfig.DEBUG)
        appScope.launch {
            session.restore()
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
}

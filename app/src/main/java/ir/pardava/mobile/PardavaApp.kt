package ir.pardava.mobile

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.BuildConfigDefault
import ir.pardava.mobile.core.SessionManager
import ir.pardava.mobile.core.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PardavaApp : Application() {

    lateinit var session: SessionManager
        private set
    lateinit var api: ApiClient
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val store = TokenStore(this)
        // Wire BuildConfig defaults through the indirection object (test-friendly).
        BuildConfigDefault.url = BuildConfig.DEFAULT_BASE_URL
        BuildConfigDefault.googleClientId = BuildConfig.GOOGLE_CLIENT_ID
        session = SessionManager(store, appScope)
        api = ApiClient(session, store, debugLogging = BuildConfig.DEBUG)
        appScope.launch { session.restore() }
        // First launch: default to Persian (platform default), afterwards the
        // stored per-app locale wins (autoStoreLocales).
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
        }
        api.acceptLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).ifEmpty { "fa" }
    }

    /** Apply in-app language switch (fa/en) — triggers activity recreation. */
    fun setAppLanguage(lang: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        api.acceptLanguage = lang
    }

    fun currentLanguage(): String =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().split("-").firstOrNull() ?: "fa"
}

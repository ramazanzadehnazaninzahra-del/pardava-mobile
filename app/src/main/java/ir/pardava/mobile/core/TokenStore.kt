package ir.pardava.mobile.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "pardava_session_v2")

/**
 * Persisted session for the site Courses API: one opaque token (`pdv_…`)
 * plus the chosen server address. (The old FastAPI access/refresh pair is
 * gone — the site API has no refresh flow.)
 */
class TokenStore(private val context: Context) {

    data class SessionSnapshot(val token: String?, val baseUrl: String)

    private object Keys {
        val TOKEN = stringPreferencesKey("api_token")
        val BASE_URL = stringPreferencesKey("base_url")
    }

    val token: Flow<String?> = context.sessionStore.data.map { it[Keys.TOKEN] }

    val baseUrl: Flow<String> =
        context.sessionStore.data.map { it[Keys.BASE_URL] ?: BuildConfigDefault.url }

    suspend fun snapshot(): SessionSnapshot {
        val p = context.sessionStore.data.first()
        return SessionSnapshot(p[Keys.TOKEN], p[Keys.BASE_URL] ?: BuildConfigDefault.url)
    }

    suspend fun save(token: String) {
        context.sessionStore.edit { it[Keys.TOKEN] = token }
    }

    suspend fun setBaseUrl(url: String) {
        context.sessionStore.edit { it[Keys.BASE_URL] = url.trimEnd('/') }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.remove(Keys.TOKEN) }
    }
}

/** Indirection so unit tests can read the default without BuildConfig. */
object BuildConfigDefault {
    var url: String = "https://pardava.ir/api/courses/"
    var googleClientId: String = ""
}

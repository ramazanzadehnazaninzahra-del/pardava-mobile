package ir.pardava.mobile.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pardava_session")

/** Persisted session: tokens, server-known user id, and chosen API base URL. */
class TokenStore(private val context: Context) {

    data class Tokens(val access: String?, val refresh: String?)

    data class SessionSnapshot(val access: String?, val refresh: String?, val baseUrl: String)

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val BASE_URL = stringPreferencesKey("base_url")
        val USER_ID = longPreferencesKey("user_id")
    }

    val tokens: Flow<Tokens> = context.dataStore.data.map { p -> Tokens(p[Keys.ACCESS], p[Keys.REFRESH]) }

    val baseUrl: Flow<String> = context.dataStore.data.map { p -> p[Keys.BASE_URL] ?: BuildConfigDefault.url }

    val hasSession: Flow<Boolean> = context.dataStore.data.map { it[Keys.REFRESH] != null }

    suspend fun snapshot(): SessionSnapshot {
        val p = context.dataStore.data.first()
        return SessionSnapshot(p[Keys.ACCESS], p[Keys.REFRESH], p[Keys.BASE_URL] ?: BuildConfigDefault.url)
    }

    suspend fun save(access: String, refresh: String, userId: Long? = null) {
        context.dataStore.edit { p ->
            p[Keys.ACCESS] = access
            p[Keys.REFRESH] = refresh
            if (userId != null) p[Keys.USER_ID] = userId
        }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url.trimEnd('/') }
    }

    suspend fun clear() {
        context.dataStore.edit { p ->
            p.remove(Keys.ACCESS)
            p.remove(Keys.REFRESH)
        }
    }
}

/** Indirection so unit tests can read the default without BuildConfig. */
object BuildConfigDefault {
    var url: String = "http://10.0.2.2:8100/"
}

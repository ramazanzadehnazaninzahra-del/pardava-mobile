package ir.pardava.mobile.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.pardava.mobile.data.dto.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "pardava_session")

/** Persisted session: single API token, cached user, site-root URL, appearance prefs. */
class TokenStore(private val context: Context) {

    data class SessionData(val token: String?, val user: UserDto?)

    data class SessionSnapshot(
        val token: String?,
        val user: UserDto?,
        val baseUrl: String,
        val themeMode: String,
        val fontScale: String,
    )

    private object Keys {
        val TOKEN = stringPreferencesKey("session_token")
        val USER = stringPreferencesKey("user_json")
        val BASE_URL = stringPreferencesKey("base_url")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = stringPreferencesKey("font_scale")
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val session: Flow<SessionData> = context.dataStore.data.map { p ->
        SessionData(
            token = p[Keys.TOKEN],
            user = p[Keys.USER]?.let { runCatching { json.decodeFromString(UserDto.serializer(), it) }.getOrNull() },
        )
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { p ->
        // Legacy local-backend URLs are silently migrated to the production site.
        migrate(p[Keys.BASE_URL] ?: BuildConfigDefault.url)
    }

    val themeMode: Flow<String> = context.dataStore.data.map { p -> p[Keys.THEME_MODE] ?: ThemeMode.SYSTEM }

    val fontScale: Flow<String> = context.dataStore.data.map { p -> p[Keys.FONT_SCALE] ?: FontScale.NORMAL }

    suspend fun snapshot(): SessionSnapshot {
        val p = context.dataStore.data.first()
        return SessionSnapshot(
            token = p[Keys.TOKEN],
            user = p[Keys.USER]?.let { runCatching { json.decodeFromString(UserDto.serializer(), it) }.getOrNull() },
            baseUrl = migrate(p[Keys.BASE_URL] ?: BuildConfigDefault.url),
            themeMode = p[Keys.THEME_MODE] ?: ThemeMode.SYSTEM,
            fontScale = p[Keys.FONT_SCALE] ?: FontScale.NORMAL,
        )
    }

    suspend fun save(token: String, user: UserDto?) {
        context.dataStore.edit { p ->
            p[Keys.TOKEN] = token
            p.remove(Keys.USER)
            user?.let { p[Keys.USER] = json.encodeToString(UserDto.serializer(), it) }
        }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url.trimEnd('/') }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setFontScale(scale: String) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale }
    }

    suspend fun clear() {
        context.dataStore.edit { p ->
            p.remove(Keys.TOKEN)
            p.remove(Keys.USER)
        }
    }

    private fun migrate(stored: String): String =
        SessionManager.migrateLegacyUrl(stored, BuildConfigDefault.url)
}

/** Allowed theme modes (settings screen). */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    val ALL = listOf(SYSTEM, LIGHT, DARK)
}

/** Allowed font-size steps (multiplies the system font scale). */
object FontScale {
    const val SMALL = "small"
    const val NORMAL = "normal"
    const val LARGE = "large"
    const val XLARGE = "xlarge"
    val ALL = listOf(SMALL, NORMAL, LARGE, XLARGE)
    fun factor(key: String): Float = when (key) {
        SMALL -> 0.9f
        LARGE -> 1.15f
        XLARGE -> 1.3f
        else -> 1.0f
    }
}

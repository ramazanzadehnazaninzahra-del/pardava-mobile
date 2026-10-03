package ir.pardava.mobile.core

import ir.pardava.mobile.data.dto.AppConfigResponse
import ir.pardava.mobile.data.dto.AppSectionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Server-driven app design (پنل «دیزاین اپ» روی سایت):
 * - tabs   → bottom navigation sections
 * - home   → home-screen blocks (banners, prices ticker, courses, tools, …)
 * - tools  → feature entries (native screens + web tools)
 *
 * Priority: fresh server answer → last cached answer (DataStore) → built-in
 * defaults. Every consumer renders only what the server marked enabled, in
 * the order it sent; if the config is empty/broken the app falls back to the
 * same defaults the server ships with.
 */
data class AppConfig(
    val tabs: List<AppSectionDto> = emptyList(),
    val home: List<AppSectionDto> = emptyList(),
    val tools: List<AppSectionDto> = emptyList(),
    val updatedAt: String? = null,
    /** true once real data (fresh or cached) has been applied. */
    val fromServer: Boolean = false,
)

class AppConfigStore(private val client: ApiClient, private val store: TokenStore) {

    private val _config = MutableStateFlow(
        AppConfig(tabs = Defaults.tabs, home = Defaults.home, tools = Defaults.tools),
    )
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    /** First load reads the persisted cache so offline launches keep the last design. */
    suspend fun restoreCache() {
        val json = store.appConfigJsonValue() ?: return
        if (json.isBlank()) return
        runCatching {
            val cached = client.json.decodeFromString(AppConfigResponse.serializer(), json)
            val parsed = toConfig(cached)
            if (isValid(parsed)) _config.value = parsed
        }
    }

    /** Fetch fresh config from the site; silently keeps cache/defaults on failure. */
    suspend fun refresh() {
        runCatching {
            val resp = client.call { client.api.appConfig() }
            val fresh = toConfig(resp)
            if (isValid(fresh)) {
                _config.value = fresh
                store.setAppConfigJson(client.json.encodeToString(AppConfigResponse.serializer(), resp))
            }
        }
    }

    fun tool(key: String): AppSectionDto? = _config.value.tools.firstOrNull { it.key == key }

    private fun toConfig(r: AppConfigResponse) = AppConfig(
        tabs = r.tabs,
        home = r.home,
        tools = r.tools,
        updatedAt = r.updatedAt,
        fromServer = true,
    )

    private fun isValid(c: AppConfig): Boolean = c.tabs.isNotEmpty() && c.home.isNotEmpty()

    /** Built-in defaults — mirror of the server's seed (app works offline/out-of-the-box). */
    object Defaults {
        private fun s(key: String, title: String, icon: String, kind: String = "native", url: String = "") =
            AppSectionDto(key = key, title = title, icon = icon, kind = kind, url = url)

        val tabs = listOf(
            s("tab_home", "خانه", "home"),
            s("tab_courses", "دوره‌ها", "school"),
            s("tab_services", "سرویس‌ها", "apps"),
            s("tab_articles", "مقالات", "article"),
            s("tab_profile", "پروفایل", "person"),
        )
        val home = listOf(
            s("home_banners", "بنرهای اصلی", "image"),
            s("home_prices", "نرخ لحظه‌ای", "candlestick_chart"),
            s("home_courses", "دوره‌های تازه", "school"),
            s("home_tools", "ابزارهای هوشمند", "apps"),
            s("home_league", "لیگ پردآوا", "emoji_events"),
            s("home_articles", "تازه‌ترین مقالات", "article"),
            s("home_news", "اخبار فوری", "newspaper"),
        )
        val tools = listOf(
            s("tool_prices", "قیمت لحظه‌ای", "candlestick_chart"),
            s("tool_ai_chat", "هوش مصنوعی", "chat"),
            s("tool_news", "اخبار", "newspaper"),
            s("tool_hoquqyar", "حقوق‌یار", "balance", kind = "web", url = "/hoquqyar"),
            s("tool_speech", "استودیو گفتار", "mic", kind = "web", url = "/speech"),
            s("tool_translate", "مترجم", "translate", kind = "web", url = "/translate"),
            s("tool_ocr", "عکس به متن", "document_scanner", kind = "web", url = "/text-reader"),
            s("tool_photo", "پردازش تصویر", "tune", kind = "web", url = "/image-processing"),
            s("tool_market", "رصد بازار", "monitoring", kind = "web", url = "/market-monitor"),
            s("tool_games", "بازی‌ها", "sports_esports", kind = "web", url = "/games"),
        )
    }
}

package ir.pardava.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.data.dto.AppSectionDto
import ir.pardava.mobile.data.dto.MobileArticleDto
import ir.pardava.mobile.data.dto.ServiceItemDto
import ir.pardava.mobile.ui.screens.articles.ArticlesScreen
import ir.pardava.mobile.ui.screens.courses.CoursesScreen
import ir.pardava.mobile.ui.screens.home.HomeScreen
import ir.pardava.mobile.ui.screens.leaderboard.LeaderboardScreen
import ir.pardava.mobile.ui.screens.profile.ProfileScreen
import ir.pardava.mobile.ui.screens.services.ServicesScreen

private data class TabSpec(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

/**
 * Mobile shell whose bottom tabs are server-driven (پنل «دیزاین اپ»):
 * the admin can enable/disable and reorder tabs; the app renders the enabled
 * ones in the server order and falls back to the classic 5-tab layout when
 * the config is missing or broken. «خانه» is always available as the base tab.
 * rememberSaveable keeps the selection across recreations (index clamped).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    app: PardavaApp,
    onOpenCourse: (String) -> Unit,
    onOpenService: (ServiceItemDto) -> Unit,
    onOpenArticle: (MobileArticleDto) -> Unit,
    onOpenLeague: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenChat: () -> Unit = {},
    onOpenVoiceAssistant: () -> Unit = {},
    onOpenPrices: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    onOpenNews: () -> Unit = {},
    onOpenTool: (AppSectionDto) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val cfg by app.appConfig.config.collectAsStateWithLifecycle()

    val enabledKeys = cfg.tabs.mapNotNull { it.key }.toSet()
    // درصورت غیرفعال‌بودن تب سرویس‌ها، قرص «همهٔ سرویس‌ها» در خانه دیده نمی‌شود.
    val servicesEnabled = "tab_services" in enabledKeys
    var tabKeyJump by rememberSaveable { mutableStateOf<String?>(null) }

    val allTabs = listOf(
        TabSpec("tab_home", stringResource(R.string.nav_home), Icons.Filled.Home) {
            HomeScreen(
                app = app,
                onOpenCourse = onOpenCourse,
                onOpenService = onOpenService,
                onOpenAllServices = if (servicesEnabled) {
                    { tabKeyJump = "tab_services" }
                } else {
                    null
                },
                onOpenArticle = onOpenArticle,
                onOpenLeague = onOpenLeague,
                onOpenPrices = onOpenPrices,
                onOpenAiChat = onOpenAiChat,
                onOpenNews = onOpenNews,
                onOpenTool = onOpenTool,
            )
        },
        TabSpec("tab_courses", stringResource(R.string.nav_courses), Icons.Filled.School) {
            CoursesScreen(app = app, onOpenCourse = onOpenCourse)
        },
        TabSpec("tab_services", stringResource(R.string.nav_services), Icons.Outlined.Apps) {
            ServicesScreen(
                app = app,
                onOpenService = onOpenService,
                onOpenTool = onOpenTool,
            )
        },
        TabSpec("tab_articles", stringResource(R.string.nav_articles), Icons.Outlined.Article) {
            ArticlesScreen(app = app, onOpenArticle = onOpenArticle)
        },
        TabSpec("tab_profile", stringResource(R.string.nav_profile), Icons.Filled.Person) {
            ProfileScreen(
                app = app,
                onOpenLogin = onOpenLogin,
                onOpenSettings = onOpenSettings,
                onOpenCourse = onOpenCourse,
                onOpenLeague = onOpenLeague,
                onOpenChat = onOpenChat,
            )
        },
    )

    // فقط تب‌های فعال، به ترتیب سرور؛ کلیدهای ناشناخته نادیده گرفته می‌شوند و
    // اگر کانفیگ خراب/خالی بود، چیدمان کلاسیک ۵تایی برمی‌گردد.
    val visibleTabs = if (enabledKeys.isEmpty()) {
        allTabs
    } else {
        allTabs.filter { it.key in enabledKeys }
    }.ifEmpty { allTabs }

    // «خانه» باید همیشه دیده شود؛ اگر ادمین همهٔ تب‌ها را خاموش کرده باشد.
    val finalTabs = if (visibleTabs.none { it.key == "tab_home" }) allTabs else visibleTabs
    val safeTab = tab.coerceIn(0, finalTabs.lastIndex)

    LaunchedEffect(tabKeyJump, finalTabs.size) {
        val key = tabKeyJump ?: return@LaunchedEffect
        val idx = finalTabs.indexOfFirst { it.key == key }
        if (idx >= 0) tab = idx
        tabKeyJump = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                finalTabs.forEachIndexed { index, spec ->
                    NavigationBarItem(
                        selected = safeTab == index,
                        onClick = { tab = index },
                        icon = { Icon(spec.icon, contentDescription = spec.label) },
                        label = { Text(spec.label) },
                    )
                }
            }
        },
        // دکمهٔ چسبان گفتگو — روی همهٔ تب‌ها بالای نوار پایین در دسترس است
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenVoiceAssistant,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.va_fab_desc),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            finalTabs[safeTab].content()
        }
    }
}

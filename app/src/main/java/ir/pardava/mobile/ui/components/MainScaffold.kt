package ir.pardava.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.data.dto.MobileArticleDto
import ir.pardava.mobile.data.dto.ServiceItemDto
import ir.pardava.mobile.ui.screens.articles.ArticlesScreen
import ir.pardava.mobile.ui.screens.courses.CoursesScreen
import ir.pardava.mobile.ui.screens.home.HomeScreen
import ir.pardava.mobile.ui.screens.leaderboard.LeaderboardScreen
import ir.pardava.mobile.ui.screens.profile.ProfileScreen
import ir.pardava.mobile.ui.screens.services.ServicesScreen

private data class TabSpec(
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

/**
 * Five-tab mobile shell mirroring the site's structure:
 * Home / Courses / Services / Articles / Profile.
 * rememberSaveable keeps the selected tab across language-switch recreations.
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
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        TabSpec(stringResource(R.string.nav_home), Icons.Filled.Home) {
            HomeScreen(
                app = app,
                onOpenCourse = onOpenCourse,
                onOpenService = onOpenService,
                onOpenAllServices = { tab = 2 },
                onOpenArticle = onOpenArticle,
                onOpenLeague = onOpenLeague,
            )
        },
        TabSpec(stringResource(R.string.nav_courses), Icons.Filled.School) {
            CoursesScreen(app = app, onOpenCourse = onOpenCourse)
        },
        TabSpec(stringResource(R.string.nav_services), Icons.Outlined.Apps) {
            ServicesScreen(app = app, onOpenService = onOpenService)
        },
        TabSpec(stringResource(R.string.nav_articles), Icons.Outlined.Article) {
            ArticlesScreen(app = app, onOpenArticle = onOpenArticle)
        },
        TabSpec(stringResource(R.string.nav_profile), Icons.Filled.Person) {
            ProfileScreen(
                app = app,
                onOpenLogin = onOpenLogin,
                onOpenSettings = onOpenSettings,
                onOpenCourse = onOpenCourse,
                onOpenLeague = onOpenLeague,
            )
        },
    )

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
                tabs.forEachIndexed { index, spec ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(spec.icon, contentDescription = spec.label) },
                        label = { Text(spec.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            tabs[tab].content()
        }
    }
}

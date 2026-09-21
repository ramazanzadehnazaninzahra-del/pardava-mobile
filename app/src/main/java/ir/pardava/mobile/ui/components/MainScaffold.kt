package ir.pardava.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.ui.screens.courses.CoursesScreen
import ir.pardava.mobile.ui.screens.league.LeagueScreen
import ir.pardava.mobile.ui.screens.profile.ProfileScreen

private data class TabSpec(
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

/** Main bottom-tab scaffold: Courses / League / Profile — all public. */
@Composable
fun MainScaffold(
    app: PardavaApp,
    onOpenCourse: (String) -> Unit,
    onGoLogin: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        TabSpec(stringResource(R.string.nav_courses), Icons.Filled.School) {
            CoursesScreen(app = app, onOpenCourse = onOpenCourse)
        },
        TabSpec(stringResource(R.string.nav_league), Icons.Filled.EmojiEvents) {
            LeagueScreen(app = app)
        },
        TabSpec(stringResource(R.string.nav_profile), Icons.Filled.Person) {
            ProfileScreen(app = app, onGoLogin = onGoLogin)
        },
    )

    Scaffold(
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

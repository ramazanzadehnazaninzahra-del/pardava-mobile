package ir.pardava.mobile.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.ui.components.MainScaffold
import ir.pardava.mobile.ui.screens.course.CourseScreen
import ir.pardava.mobile.ui.screens.lesson.LessonScreen
import ir.pardava.mobile.ui.screens.login.LoginScreen
import ir.pardava.mobile.ui.screens.settings.SettingsScreen

object Routes {
    const val MAIN = "main"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val COURSE = "course/{slug}"
    const val LESSON = "lesson/{slug}/{lessonId}"

    fun course(slug: String) = "course/$slug"
    fun lesson(slug: String, lessonId: Long) = "lesson/$slug/$lessonId"
}

/**
 * Guest-first navigation: the app starts on MAIN whether or not the user is
 * signed in — browsing courses, course pages and the league needs no account.
 * The login screen is only pushed when an action requires a session
 * (enrolling, opening locked lessons, completing lessons, the profile tab).
 */
@Composable
fun PardavaNav(app: PardavaApp) {
    val nav = rememberNavController()
    var startReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        app.session.awaitReady()
        startReady = true
        // Dead session token discovered mid-app → drop the stale profile state.
        app.api.onSessionExpired = {
            // Session is already cleared; screens observe isSignedIn via refresh hooks.
        }
    }

    if (!startReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = nav, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            MainScaffold(
                app = app,
                onOpenCourse = { slug -> nav.navigate(Routes.course(slug)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenLogin = { nav.navigate(Routes.LOGIN) },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                app = app,
                onSignedIn = { nav.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                app = app,
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Routes.COURSE,
            arguments = listOf(navArgument("slug") { type = NavType.StringType }),
        ) { entry ->
            CourseScreen(
                app = app,
                slug = entry.arguments?.getString("slug") ?: "",
                onBack = { nav.popBackStack() },
                onOpenLesson = { slug, lessonId -> nav.navigate(Routes.lesson(slug, lessonId)) },
                onOpenLogin = { nav.navigate(Routes.LOGIN) },
            )
        }

        composable(
            Routes.LESSON,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("lessonId") { type = NavType.LongType },
            ),
        ) { entry ->
            LessonScreen(
                app = app,
                slug = entry.arguments?.getString("slug") ?: "",
                lessonId = entry.arguments?.getLong("lessonId") ?: 0L,
                onBack = { nav.popBackStack() },
                onOpenLogin = { nav.navigate(Routes.LOGIN) },
            )
        }
    }
}

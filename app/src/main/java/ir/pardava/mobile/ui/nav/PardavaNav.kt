package ir.pardava.mobile.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.UpdateManager
import ir.pardava.mobile.data.dto.ExchangeCodeIn
import ir.pardava.mobile.data.dto.MobileArticleDto
import ir.pardava.mobile.data.dto.ServiceItemDto
import ir.pardava.mobile.ui.components.MainScaffold
import ir.pardava.mobile.ui.screens.articles.ArticleReaderScreen
import ir.pardava.mobile.ui.screens.course.CourseScreen
import ir.pardava.mobile.ui.screens.leaderboard.LeaderboardScreen
import ir.pardava.mobile.ui.screens.lesson.LessonScreen
import ir.pardava.mobile.ui.screens.login.LoginScreen
import ir.pardava.mobile.ui.screens.settings.SettingsScreen
import ir.pardava.mobile.ui.screens.web.WebScreen
import kotlinx.coroutines.launch

object Routes {
    const val MAIN = "main"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val LEAGUE = "league"
    const val COURSE = "course/{slug}"
    const val LESSON = "lesson/{slug}/{lessonId}"
    const val WEB = "web/{title}/{url}"
    const val ARTICLE = "article/{key}"

    fun course(slug: String) = "course/$slug"
    fun lesson(slug: String, lessonId: Long) = "lesson/$slug/$lessonId"
    fun web(title: String, url: String) = "web/$title/${android.net.Uri.encode(url)}"
    fun article(key: String) = "article/${android.net.Uri.encode(key)}"
}

/**
 * Guest-first navigation: starts on MAIN with the Home tab. Login is only
 * pushed when an action needs a session. The Google web-bridge deep link
 * (pardava://auth/callback?code=…) is exchanged here for a real pdv_ token.
 */
@Composable
fun PardavaNav(app: PardavaApp) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var startReady by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf<ir.pardava.mobile.data.dto.LatestVersionDto?>(null) }
    var updating by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by app.settings.collectAsStateWithLifecycle()
    val darkTheme = when (settings.themeMode) {
        ir.pardava.mobile.core.ThemeMode.LIGHT -> false
        ir.pardava.mobile.core.ThemeMode.DARK -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    LaunchedEffect(Unit) {
        app.session.awaitReady()
        startReady = true
    }

    // ---- Google web-bridge exchange (code → pdv_ token) ----
    LaunchedEffect(Unit) {
        app.pendingAuthCode.collect { code ->
            if (code != null) {
                try {
                    val resp = app.api.call { app.api.api.exchangeCode(ExchangeCodeIn(code)) }
                    val token = resp.token
                    if (resp.ok == true && token != null) {
                        app.session.saveSession(token, resp.user)
                    } else if (!app.session.isSignedIn) {
                        scope.launch {
                            snackbar.showSnackbar(resp.error ?: app.getString(R.string.error_generic))
                        }
                    }
                } catch (e: Exception) {
                    scope.launch { snackbar.showSnackbar(e.message ?: "") }
                } finally {
                    app.consumeAuthCode()
                }
            }
        }
    }

    // ---- silent version check once per launch → in-app update dialog ----
    LaunchedEffect(Unit) {
        val latest = UpdateManager.check(app.api) ?: return@LaunchedEffect
        if (UpdateManager.isNewer(latest, app.versionCode, app.versionName)) {
            updateAvailable = latest
        }
    }

    if (!startReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // ---- in-app update dialog ----
    updateAvailable?.let { latest ->
        AlertDialog(
            onDismissRequest = { if (!updating) updateAvailable = null },
            title = { Text(stringResource(R.string.update_title, latest.versionName ?: "")) },
            text = { Text(latest.whatsNew?.ifBlank { stringResource(R.string.update_body) } ?: stringResource(R.string.update_body)) },
            confirmButton = {
                TextButton(
                    enabled = !updating,
                    onClick = {
                        updating = true
                        UpdateManager.download(context, latest.apkUrl ?: return@TextButton)
                        scope.launch {
                            snackbar.showSnackbar(app.getString(R.string.update_downloading))
                        }
                        updating = false
                        updateAvailable = null
                    },
                ) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = {
                TextButton(onClick = { updateAvailable = null }) { Text(stringResource(R.string.update_later)) }
            },
        )
    }

    NavHost(navController = nav, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { _ ->
                MainScaffold(
                    app = app,
                    onOpenCourse = { slug -> nav.navigate(Routes.course(slug)) },
                    onOpenService = { svc ->
                        svc.url?.let {
                            nav.navigate(Routes.web(svc.title ?: "پردآوا", app.siteUrl(it)))
                        }
                    },
                    onOpenArticle = { art ->
                        val key = art.slug ?: art.id?.toString() ?: return@MainScaffold
                        nav.navigate(Routes.article(key))
                    },
                    onOpenLeague = { nav.navigate(Routes.LEAGUE) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenLogin = { nav.navigate(Routes.LOGIN) },
                )
            }
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

        composable(Routes.LEAGUE) {
            LeaderboardScreen(app = app, onBack = { nav.popBackStack() })
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

        composable(
            Routes.WEB,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType },
            ),
        ) { entry ->
            WebScreen(
                url = entry.arguments?.getString("url") ?: "",
                title = entry.arguments?.getString("title") ?: "پردآوا",
                isDark = darkTheme,
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Routes.ARTICLE,
            arguments = listOf(navArgument("key") { type = NavType.StringType }),
        ) { entry ->
            ArticleReaderScreen(
                app = app,
                key = entry.arguments?.getString("key") ?: "",
                isDark = darkTheme,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

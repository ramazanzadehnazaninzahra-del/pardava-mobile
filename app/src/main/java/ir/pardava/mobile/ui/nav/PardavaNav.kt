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
import ir.pardava.mobile.ui.screens.quiz.QuizScreen

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val COURSE = "course/{slug}"
    const val LESSON = "lesson/{courseSlug}/{lessonSlug}"
    const val QUIZ = "quiz/{courseSlug}/{lessonSlug}/{quizId}"

    fun course(slug: String) = "course/$slug"
    fun lesson(courseSlug: String, lessonSlug: String) = "lesson/$courseSlug/$lessonSlug"
    fun quiz(courseSlug: String, lessonSlug: String, quizId: Long) = "quiz/$courseSlug/$lessonSlug/$quizId"
}

@Composable
fun PardavaNav(app: PardavaApp) {
    val nav = rememberNavController()
    var startReady by remember { mutableStateOf(false) }
    var startRoute by remember { mutableStateOf(Routes.MAIN) }

    LaunchedEffect(Unit) {
        app.session.awaitReady()
        startRoute = if (app.session.isSignedIn) Routes.MAIN else Routes.LOGIN
        startReady = true
        // Auto-refresh chain exhausted → force re-login anywhere in the app.
        app.api.onSessionExpired = {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    if (!startReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = nav, startDestination = startRoute) {

        composable(Routes.LOGIN) {
            LoginScreen(
                app = app,
                onSignedIn = {
                    nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable(Routes.MAIN) {
            MainScaffold(
                app = app,
                onOpenCourse = { slug -> nav.navigate(Routes.course(slug)) },
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
                onOpenLesson = { courseSlug, lessonSlug ->
                    nav.navigate(Routes.lesson(courseSlug, lessonSlug))
                },
            )
        }

        composable(
            Routes.LESSON,
            arguments = listOf(
                navArgument("courseSlug") { type = NavType.StringType },
                navArgument("lessonSlug") { type = NavType.StringType },
            ),
        ) { entry ->
            LessonScreen(
                app = app,
                courseSlug = entry.arguments?.getString("courseSlug") ?: "",
                lessonSlug = entry.arguments?.getString("lessonSlug") ?: "",
                onBack = { nav.popBackStack() },
                onOpenQuiz = { course, lesson, quizId ->
                    nav.navigate(Routes.quiz(course, lesson, quizId))
                },
            )
        }

        composable(
            Routes.QUIZ,
            arguments = listOf(
                navArgument("courseSlug") { type = NavType.StringType },
                navArgument("lessonSlug") { type = NavType.StringType },
                navArgument("quizId") { type = NavType.LongType },
            ),
        ) { entry ->
            QuizScreen(
                app = app,
                courseSlug = entry.arguments?.getString("courseSlug") ?: "",
                lessonSlug = entry.arguments?.getString("lessonSlug") ?: "",
                quizId = entry.arguments?.getLong("quizId") ?: 0L,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

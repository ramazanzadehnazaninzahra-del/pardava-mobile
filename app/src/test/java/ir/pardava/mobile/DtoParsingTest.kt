package ir.pardava.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ir.pardava.mobile.core.ApiError
import ir.pardava.mobile.data.dto.LessonDetail
import ir.pardava.mobile.data.dto.SubmitResultOut
import ir.pardava.mobile.data.dto.TokenOut
import kotlinx.serialization.json.Json

/**
 * Wire-compatibility tests: the JSON the FastAPI backend emits must deserialize
 * into the app DTOs unchanged (unknown keys ignored, nulls tolerated).
 */
class DtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `parses lesson detail with lock state and fallback`() {
        val payload = """
        {
          "id": 4, "slug": "conditionals", "chapter_id": 1, "course_id": 1, "course_slug": "intro-to-programming",
          "title": "شرط‌ها", "body": "بدنه", "code_sample": "if x > 0:", "exercise": "تمرین",
          "lang": "fa", "fallback_used": false,
          "videos": [{"id": 7, "lang": "fa", "duration_seconds": 600, "is_placeholder": true, "extra_unknown": 1}],
          "subtitles": [{"id": 9, "lang": "fa"}],
          "quiz": {"id": 3, "title": "آزمون شرط‌ها", "question_count": 7, "duration_seconds": 600,
                   "max_attempts": 3, "pass_score_percent": 60, "attempts_used": 1, "fallback_used": false},
          "locked": true, "completed": false, "quiz_passed": false,
          "unlock_requirements": {"watch": {"type": "watch", "fa": "تماشای ۹۰٪ ویدیو", "en": "Watch 90% of the video"}},
          "best_score_percent": 33.3333, "attempts_used": 1
        }
        """.trimIndent()

        val lesson = json.decodeFromString(LessonDetail.serializer(), payload)

        assertEquals(4L, lesson.id)
        assertTrue(lesson.locked)
        assertEquals(600, lesson.quiz?.duration_seconds)
        assertEquals(1, lesson.videos.size)
        assertEquals("fa", lesson.videos[0].lang)
        assertEquals("تماشای ۹۰٪ ویدیو", lesson.unlock_requirements["watch"]?.get("fa"))
        assertEquals("Watch 90% of the video", lesson.unlock_requirements["watch"]?.get("en"))
    }

    @Test
    fun `parses token pair with user`() {
        val payload = """
        {
          "access_token": "a.b.c", "refresh_token": "r-1", "token_type": "bearer", "expires_in": 1800,
          "user": {"id": 12, "phone": "+989121111111", "email": null, "display_name": null,
                   "avatar_url": null, "preferred_language": "fa", "role": "learner", "is_active": true}
        }
        """.trimIndent()

        val tokens = json.decodeFromString(TokenOut.serializer(), payload)
        assertEquals("r-1", tokens.refresh_token)
        assertEquals("fa", tokens.user.preferred_language)
    }

    @Test
    fun `parses submit result with per question report and xp`() {
        val payload = """
        {
          "attempt_id": 55, "attempt_no": 1, "score_percent": 85.7, "passed": true, "pass_score_percent": 60,
          "earned_points": 6, "total_points": 7,
          "per_question": [
            {"question_id": 1, "correct": true, "points": 1,
             "correct_answer": {"option_id": 2}, "explanation": "چون ..."},
            {"question_id": 2, "correct": false, "points": 1,
             "correct_answer": {"accepted": ["print(\"hi\")"]}, "explanation": null}
          ],
          "xp_awarded": [{"type": "quiz_passed", "amount": 15}, {"type": "first_pass", "amount": 10}],
          "new_achievements": [{"code": "first_quiz", "name": "اولین آزمون", "description": null, "xp_reward": 5}]
        }
        """.trimIndent()

        val result = json.decodeFromString(SubmitResultOut.serializer(), payload)
        assertTrue(result.passed)
        assertEquals(2, result.xp_awarded.size)
        assertFalse(result.per_question[1].correct)
    }

    @Test
    fun `parses bilingual error envelope`() {
        val body = """{"error": {"code": "lesson_locked",
                     "message_fa": "جلسه قفل است", "message_en": "Lesson is locked"}}"""
        val err = ApiError.from(403, body)
        assertEquals(403, err.status)
        assertEquals("lesson_locked", err.code)
        assertEquals("جلسه قفل است", err.message("fa"))
        assertEquals("Lesson is locked", err.message("en"))
    }

    @Test
    fun `error parsing survives malformed body`() {
        val err = ApiError.from(500, "<html>Server error</html>")
        assertEquals("unknown", err.code)
    }
}

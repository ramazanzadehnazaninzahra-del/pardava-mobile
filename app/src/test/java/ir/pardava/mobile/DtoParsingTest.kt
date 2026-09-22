package ir.pardava.mobile

import ir.pardava.mobile.core.BuildConfigDefault
import ir.pardava.mobile.core.SessionManager
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.CourseDetailResponse
import ir.pardava.mobile.data.dto.CoursesListResponse
import ir.pardava.mobile.data.dto.LessonContentResponse
import ir.pardava.mobile.data.dto.LeagueResponse
import ir.pardava.mobile.data.dto.MeResponse
import ir.pardava.mobile.data.dto.OtpRequestResponse
import ir.pardava.mobile.data.dto.TokenResponse
import ir.pardava.mobile.data.dto.requireOk
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses REAL responses captured from https://pardava.ir/api/courses
 * (v1.1.0) so the DTO layer can never drift away from the live contract.
 */
class DtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun `courses list parses live shape`() {
        val body = """
        {"count":1,"courses":[{"category":"programming","category_label":"برنامه‌نویسی",
        "cover_url":"/static/images/courses/ai-for-kids.png","description":"…",
        "enrolled":false,"id":1,"is_free":true,"is_published":true,"lesson_count":8,
        "level":"beginner","level_label":"مقدماتی","points_per_lesson":10,"price":0,
        "sequential_unlock":true,"slug":"ai-for-kids","summary":"…","title":"سفر هیجان‌انگیز",
        "total_minutes":0}],"ok":true}
        """.trimIndent()
        val parsed = json.decodeFromString(CoursesListResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals(1, parsed.count)
        val course = parsed.courses.first()
        assertEquals("ai-for-kids", course.slug)
        assertEquals(true, course.effectiveFree)
        assertEquals(8, course.effectiveLessonCount)
        assertEquals(false, course.enrolled)
        assertEquals("beginner", course.level)
    }

    @Test
    fun `course detail parses live shape with lesson states`() {
        val body = """
        {"completed_count":0,"course":{"category":"programming","category_label":"برنامه‌نویسی",
        "cover_url":"/static/images/courses/ai-for-kids.png","description":"d","id":1,
        "is_free":true,"is_published":true,"lesson_count":8,"level":"beginner",
        "level_label":"مقدماتی","points_per_lesson":10,"price":0,"sequential_unlock":true,
        "slug":"ai-for-kids","summary":"s","title":"t","total_minutes":96},
        "enrolled":false,"full_access":false,
        "lessons":[{"duration_min":10,"id":1,"is_free_preview":true,"position":1,
        "state":"preview","title":"درس ۱"},{"duration_min":12,"id":2,"is_free_preview":false,
        "position":2,"state":"locked","title":"درس ۲"}],
        "my_points_in_course":0,"ok":true,"pending_purchase":null,"total_points":0}
        """.trimIndent()
        val parsed = json.decodeFromString(CourseDetailResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals(false, parsed.enrolled)
        assertEquals(2, parsed.lessons.size)
        assertEquals("preview", parsed.lessons[0].state)
        assertEquals("locked", parsed.lessons[1].state)
        assertTrue(parsed.lessons[1].isFreePreview == false)
    }

    @Test
    fun `lesson content parses live shape with navigation ids`() {
        val body = """
        {"course":{"slug":"ai-for-kids","title":"سفر"},
        "lesson":{"description":"سلام!","duration_min":10,"id":1,"is_free_preview":true,
        "next_id":2,"next_state":"locked","position":1,"prev_id":null,"state":"preview",
        "title":"درس ۱"},"ok":true}
        """.trimIndent()
        val parsed = json.decodeFromString(LessonContentResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        val lesson = parsed.lesson!!
        assertEquals(1L, lesson.id)
        assertEquals(2L, lesson.nextId)
        assertNull(lesson.prevId)
        assertEquals("locked", lesson.nextState)
    }

    @Test
    fun `league parses live shape`() {
        val body = """
        {"courses":[{"slug":"ai-for-kids","title":"سفر"}],
        "leaders":[],"ok":true,"scope":{"slug":"ai-for-kids","title":"سفر"}}
        """.trimIndent()
        val parsed = json.decodeFromString(LeagueResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals("ai-for-kids", parsed.scope?.slug)
        assertTrue(parsed.leaders.isEmpty())
        assertEquals(1, parsed.courses.size)
    }

    @Test
    fun `error envelope throws with server message`() {
        val body = """{"code":401,"error":"توکن نامعتبر یا منقضی است.","ok":false}"""
        val parsed = json.decodeFromString(TokenResponse.serializer(), body)
        val thrown = runCatching { parsed.requireOk() }
        assertTrue(thrown.exceptionOrNull() is ApiException)
        val e = thrown.exceptionOrNull() as ApiException
        assertEquals(401, e.code)
        assertTrue(e.isAuthError)
        assertEquals("توکن نامعتبر یا منقضی است.", e.message)
    }

    @Test
    fun `otp request parses live shape`() {
        val body = """{"expires_in":300,"ok":true,"sent":true}"""
        val parsed = json.decodeFromString(OtpRequestResponse.serializer(), body)
        assertTrue(parsed.sent == true)
        assertEquals(300, parsed.expiresIn)
        assertNull(parsed.dev_code)
    }

    @Test
    fun `token response parses login shape`() {
        val body = """
        {"ok":true,"token":"pdv_Xy3abc","created":true,
        "user":{"id":7,"name":"سارا","email":"s@example.com","picture_url":null}}
        """.trimIndent()
        val parsed = json.decodeFromString(TokenResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals("pdv_Xy3abc", parsed.token)
        assertEquals(7L, parsed.user?.id)
        assertEquals("سارا", parsed.user?.displayName)
    }

    @Test
    fun `me parses tolerant shape`() {
        val body = """
        {"ok":true,"user":{"id":7,"username":"sara","name":"سارا"},
        "points":120,"enrolled_count":2}
        """.trimIndent()
        val parsed = json.decodeFromString(MeResponse.serializer(), body)
        assertEquals(120, parsed.points)
        assertEquals(2, parsed.enrolledCount)
        assertEquals("سارا", parsed.user?.displayName)
    }

    @Test
    fun `html proxy error page fails softly via lenient json only for objects`() {
        // The API never returns HTML — but if a CDN does, the ViewModel catch-all
        // shows a friendly message. Here we just assert our Json stays strict enough
        // to reject non-JSON garbage at the transport layer (SerializationException).
        val garbage = "<html lang=\"en\" dir=\"ltr\"><head><title>Upstream Error</title>"
        val thrown = runCatching {
            json.decodeFromString(CoursesListResponse.serializer(), garbage)
        }
        assertTrue(thrown.isFailure)
    }
}

class UrlNormalizationTest {

    @Test
    fun `bare host gets https and trailing slash`() {
        assertEquals("https://pardava.ir/", SessionManager.normalizeBaseUrl("pardava.ir"))
        assertEquals("https://pardava.ir/", SessionManager.normalizeBaseUrl("pardava.ir/"))
        assertEquals("https://pardava.ir/", SessionManager.normalizeBaseUrl(" https://pardava.ir "))
    }

    @Test
    fun `lan http urls are kept for self-hosting`() {
        assertEquals("http://192.168.1.20:8200/", SessionManager.normalizeBaseUrl("http://192.168.1.20:8200"))
    }

    @Test
    fun `legacy emulator urls migrate to production default`() {
        val migrated = SessionManager.migrateLegacyUrl("http://10.0.2.2:8100/", "https://pardava.ir/")
        assertEquals("https://pardava.ir/", migrated)
        val localhost = SessionManager.migrateLegacyUrl("http://localhost:8100/", "https://pardava.ir/")
        assertEquals("https://pardava.ir/", localhost)
    }

    @Test
    fun `production url passes migration untouched`() {
        assertEquals(
            "https://pardava.ir/",
            SessionManager.migrateLegacyUrl("https://pardava.ir/", "https://pardava.ir/"),
        )
    }
}

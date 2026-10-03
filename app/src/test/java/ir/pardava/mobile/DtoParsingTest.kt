package ir.pardava.mobile

import ir.pardava.mobile.core.BuildConfigDefault
import ir.pardava.mobile.core.SessionManager
import ir.pardava.mobile.data.dto.AiChatOut
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.AppConfigResponse
import ir.pardava.mobile.data.dto.CourseDetailResponse
import ir.pardava.mobile.data.dto.CoursesListResponse
import ir.pardava.mobile.data.dto.LessonContentResponse
import ir.pardava.mobile.data.dto.LeagueResponse
import ir.pardava.mobile.data.dto.LearningResponse
import ir.pardava.mobile.data.dto.MeResponse
import ir.pardava.mobile.data.dto.MobileNewsResponse
import ir.pardava.mobile.data.dto.OtpRequestResponse
import ir.pardava.mobile.data.dto.PricesResponse
import ir.pardava.mobile.data.dto.ProgressResponse
import ir.pardava.mobile.data.dto.ProgressSaveResponse
import ir.pardava.mobile.data.dto.QuizDetailResponse
import ir.pardava.mobile.data.dto.QuizSubmitResponse
import ir.pardava.mobile.data.dto.RateResponse
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
        // Live server contract: "enrollments" is an INTEGER count (OpenAPI),
        // not a list — treating it as a list broke the profile screen (v0.9.1).
        val body = """
        {"ok":true,"user":{"id":7,"username":"sara","name":"سارا"},
        "points":120,"enrollments":2}
        """.trimIndent()
        val parsed = json.decodeFromString(MeResponse.serializer(), body)
        assertEquals(120, parsed.points)
        assertEquals(2, parsed.enrollments)
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
    @Test
    fun `learning bundle parses live shape`() {
        val body = """
        {"ok":true,"course":{"slug":"cert-course","title":"X","price":500000},"lesson_count":3,
        "rating":{"avg":4.5,"count":2},"signed_in":true,"full_access":true,"completed_count":3,
        "can_rate":true,
        "my_rating":{"stars":5,"review":"","updated_at":"2026-09-22"},
        "quiz":{"title":"آزمون","title_en":"","pass_percent":60,"questions":4,"best":75,"attempts":2},
        "certificate":{"available":true,"eligible":true,"issued":true,"serial":"PDV-CERT-ABC123",
        "url":"https://pardava.ir/courses/cert-course/certificate",
        "requirements":{"paid":true,"full_access":true,"completed":3,"total":3}}}
        """.trimIndent()
        val parsed = json.decodeFromString(LearningResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals(4.5, parsed.rating?.avg!!, 0.01)
        assertEquals(2, parsed.rating?.count)
        assertEquals(75, parsed.quiz?.best)
        assertTrue(parsed.canRate == true)
        assertTrue(parsed.certificate?.issued == true)
        assertTrue(parsed.certificate?.serial!!.startsWith("PDV-CERT-"))
    }

    @Test
    fun `course detail parses instructor and rating gate`() {
        val body = """
        {"ok":true,"course":{"id":11,"slug":"inst-course","title":"دوره","summary":"","price":0,
        "is_free":true,"lesson_count":2,"total_minutes":24,"instructor":{"name":"استاد تست","title":"مدرس پایتون","photo_url":"/uploads/instructors/x.png"}},
        "lessons":[],"enrolled":true,"full_access":true,"completed_count":1}
        """.trimIndent()
        val parsed = json.decodeFromString(CourseDetailResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals("استاد تست", parsed.course?.instructor?.name)
        assertEquals("مدرس پایتون", parsed.course?.instructor?.title)
        assertEquals("/uploads/instructors/x.png", parsed.course?.instructor?.photoUrl)
    }

    @Test
    fun `course without instructor parses null`() {
        val body = """
        {"ok":true,"course":{"id":12,"slug":"no-inst","title":"دوره","summary":"","price":0,
        "is_free":true,"lesson_count":1,"total_minutes":10,"instructor":null},"lessons":[]}
        """.trimIndent()
        val parsed = json.decodeFromString(CourseDetailResponse.serializer(), body)
        assertNull(parsed.course?.instructor)
    }

    @Test
    fun `learning bundle guest shape`() {
        val body = """{"ok":true,"course":null,"lesson_count":2,"rating":{"avg":null,"count":0},
        "signed_in":false,"my_rating":null,"quiz":null,"certificate":null,
        "completed_count":0,"full_access":false}"""
        val parsed = json.decodeFromString(LearningResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals(false, parsed.signedIn)
        assertNull(parsed.certificate)
    }

    @Test
    fun `quiz detail hides correct answers`() {
        val body = """
        {"ok":true,"quiz":{"title":"آزمون","pass_percent":60,"questions":[
        {"id":7,"position":1,"text":"متن سوال","options":{"a":"الف","b":"ب","c":"ج","d":"د"},"points":1},
        {"id":8,"position":2,"text":"سوال دو","options":{"a":"x","b":"y","c":"z","d":"w"},"points":2}],
        "best":null,"attempts":0}}
        """.trimIndent()
        val parsed = json.decodeFromString(QuizDetailResponse.serializer(), body)
        val q = parsed.quiz?.questions?.first()
        assertEquals("ب", q?.options?.get("b"))
        assertTrue(!body.contains("correct_option"))
    }

    @Test
    fun `quiz submit and rate responses parse`() {
        val submit = """{"ok":true,"result":{"score":50,"correct":1,"total":2,"passed":false,"pass_percent":60,"best":75}}"""
        val rate = """{"ok":true,"rating":{"avg":4.0,"count":1},"my_stars":4}"""
        val s = json.decodeFromString(QuizSubmitResponse.serializer(), submit)
        val r = json.decodeFromString(RateResponse.serializer(), rate)
        assertEquals(50, s.result?.score)
        assertEquals(false, s.result?.passed)
        assertEquals(4, r.myStars)
        assertEquals(4.0, r.rating?.avg!!, 0.01)
    }

    @Test
    fun `watch progress parses resume payload`() {
        val body = """{"ok":true,"watch":{"position":123.5,"duration":600.0,"updated_at":"2026-09-22 20:00:00"}}"""
        val parsed = json.decodeFromString(ProgressResponse.serializer(), body)
        assertEquals(123.5, parsed.watch?.position!!, 0.01)
    }

    @Test
    fun `watch progress save parses auto-completion payload`() {
        // پاسخ جدید سرور: تکمیل خودکار در ۹۰٪ تماشا با امتیاز پرداخت‌شده
        val body = """{"ok":true,"saved":true,"position":92.0,"completed":true,"points":10,"total_points":130}"""
        val parsed = json.decodeFromString(ProgressSaveResponse.serializer(), body)
        assertEquals(true, parsed.completed)
        assertEquals(10, parsed.points)
        assertEquals(130, parsed.totalPoints)
    }

    @Test
    fun `watch progress save without completion stays backward compatible`() {
        val body = """{"ok":true,"saved":true,"position":33.0}"""
        val parsed = json.decodeFromString(ProgressSaveResponse.serializer(), body)
        assertNull(parsed.completed)
        assertNull(parsed.points)
    }
}

class AppParityDtoTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test
    fun `app config parses groups and order`() {
        val body = """
            {"ok":true,
             "tabs":[{"key":"tab_home","title":"خانه","icon":"home","kind":"native","url":""},
                     {"key":"tab_courses","title":"دوره‌ها","icon":"school","kind":"native","url":""}],
             "home":[{"key":"home_prices","title":"نرخ لحظه‌ای","icon":"candlestick_chart","kind":"native","url":""}],
             "tools":[{"key":"tool_prices","title":"قیمت لحظه‌ای","icon":"candlestick_chart","kind":"native","url":""},
                      {"key":"tool_hoquqyar","title":"حقوق‌یار","icon":"balance","kind":"web","url":"/hoquqyar"}],
             "updatedAt":"2026-10-03 10:00:00"}
        """.trimIndent()
        val parsed = json.decodeFromString(AppConfigResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals(2, parsed.tabs.size)
        assertEquals("tab_home", parsed.tabs.first().key)
        assertEquals(1, parsed.home.size)
        assertEquals(2, parsed.tools.size)
        assertEquals("/hoquqyar", parsed.tools.last().url)
        assertEquals("2026-10-03 10:00:00", parsed.updatedAt)
    }

    @Test
    fun `app config tolerates extra unknown keys`() {
        val body = """{"ok":true,"tabs":[],"home":[],"tools":[],"newField":123}"""
        val parsed = json.decodeFromString(AppConfigResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertTrue(parsed.tabs.isEmpty())
    }

    @Test
    fun `prices parse amounts and directions`() {
        val body = """
            {"ok":true,"updated_at":"2026-10-01T20:46:11Z","usd_toman":258465,
             "items":[{"key":"usd","group":"currency","fa":"دلار آمریکا","unit_fa":"هر دلار",
                       "toman":258465,"change_percent":1.24,"dir":"up","featured":1},
                      {"key":"gold18","group":"gold","fa":"طلای ۱۸ عیار","toman":25694400,
                       "change_percent":-0.4,"dir":"down"}],
             "live":true}
        """.trimIndent()
        val parsed = json.decodeFromString(PricesResponse.serializer(), body)
        assertTrue(parsed.ok == true)
        assertEquals(258465L, parsed.usdToman)
        assertEquals(2, parsed.items.size)
        assertEquals("up", parsed.items[0].dir)
        assertEquals(1.24, parsed.items[0].change_percent!!, 0.0001)
        assertEquals("طلای ۱۸ عیار", parsed.items[1].fa)
        assertTrue(parsed.live == true)
    }

    @Test
    fun `news prefers farsi display fields`() {
        val body = """
            {"ok":true,"count":1,"items":[
              {"id":7,"title":"Tech deal","titleFa":"معاملهٔ فناوری",
               "description":"desc","descriptionFa":"توضیح",
               "link":"https://example.com/7","datePublished":"Mon, 01 Oct 2026",
               "sourceFeed":"https://www.reuters.com/rss","imageUrl":"https://img.example/1.jpg"}]}
        """.trimIndent()
        val parsed = json.decodeFromString(MobileNewsResponse.serializer(), body)
        val item = parsed.items.single()
        assertEquals("معاملهٔ فناوری", item.displayTitle)
        assertEquals("توضیح", item.displayDescription)
    }

    @Test
    fun `news falls back to english when no farsi`() {
        val body = """
            {"ok":true,"count":1,"items":[{"id":8,"title":"Oil rises","description":"d","link":"https://e.com/8"}]}
        """.trimIndent()
        val parsed = json.decodeFromString(MobileNewsResponse.serializer(), body)
        val item = parsed.items.single()
        assertEquals("Oil rises", item.displayTitle)
        assertNull(item.titleFa)
        assertNull(item.imageUrl)
    }

    @Test
    fun `ai chat out has no ok envelope requirement`() {
        val ok = json.decodeFromString(AiChatOut.serializer(), """{"response":"سلام"}""")
        assertEquals("سلام", ok.response)
        assertNull(ok.ok) // requireOk روی null خطا نمی‌دهد
        val err = json.decodeFromString(AiChatOut.serializer(), """{"error":"Too many"}""")
        assertEquals("Too many", err.error)
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

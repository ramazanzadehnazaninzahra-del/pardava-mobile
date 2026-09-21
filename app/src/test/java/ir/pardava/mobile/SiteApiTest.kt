package ir.pardava.mobile

import ir.pardava.mobile.core.ApiError
import ir.pardava.mobile.core.ApiException
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.PardavaApi
import ir.pardava.mobile.data.dto.CourseDetailOut
import ir.pardava.mobile.data.dto.CoursesOut
import ir.pardava.mobile.data.dto.LeagueOut
import ir.pardava.mobile.data.dto.LessonDetailOut
import ir.pardava.mobile.data.dto.MeOut
import ir.pardava.mobile.data.dto.TokenOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Full-stack tests of the site Courses API contract (envelope `{ok, ...}`,
 * business errors inside HTTP 200) against MockWebServer.
 */
class SiteApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PardavaApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PardavaApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    @Test
    fun `courses index parses envelope and cards`() = runBlocking(Dispatchers.IO) {
        enqueue(
            """
            {"ok":true,"count":2,"courses":[
              {"id":1,"slug":"python-basics","title":"پایتون","category":"programming",
               "category_label":"برنامه‌نویسی","level":"beginner","level_label":"مقدماتی",
               "summary":"خلاصه","price":0,"is_free":true,"is_published":true,
               "sequential_unlock":false,"points_per_lesson":10,"cover_url":"",
               "lesson_count":8,"enrolled":false},
              {"id":2,"slug":"opencv","title":"اوپن‌سی‌وی","price":490000,"is_free":false,
               "lesson_count":12,"enrolled":true}
            ]}
            """.trimIndent(),
        )
        val out = apiCall { api.coursesIndex(server.url("/api/courses").toString()) }
        assertTrue(out.ok)
        assertEquals(2, out.count)
        assertEquals("python-basics", out.courses[0].slug)
        assertEquals(true, out.courses[0].is_free)
        assertEquals(false, out.courses[1].is_free)
        assertEquals(true, out.courses[1].enrolled)
        // Index must be requested WITHOUT a trailing slash.
        val path = server.takeRequest().path ?: ""
        assertTrue("no trailing slash expected, got $path", !path.endsWith("//"))
        assertTrue(path.endsWith("/api/courses"))
    }

    @Test
    fun `course detail parses lessons and states`() = runBlocking(Dispatchers.IO) {
        enqueue(
            """
            {"ok":true,
             "course":{"id":1,"slug":"python-basics","title":"پایتون","price":0,"is_free":true,
                       "lesson_count":3,"total_minutes":42,"points_per_lesson":10},
             "enrolled":true,"full_access":true,"completed_count":1,
             "pending_purchase":null,"my_points_in_course":30,"total_points":30,
             "lessons":[
               {"id":11,"title":"مقدمه","position":1,"duration_min":14,
                "is_free_preview":false,"state":"done"},
               {"id":12,"title":"نصب پایتون","position":2,"duration_min":15,
                "is_free_preview":true,"state":"preview",
                "file":{"name":"setup.pdf","size":12345}},
               {"id":13,"title":"متغیرها","position":3,"duration_min":13,
                "is_free_preview":false,"state":"open",
                "file":{"name":"vars.pdf","size":99,"url":"https://pardava.ir/api/courses/python-basics/lessons/13/file"}}
             ]}
            """.trimIndent(),
        )
        val detail = apiCall { api.course("python-basics") }
        assertTrue(detail.ok)
        assertEquals(3, detail.lessons.size)
        assertEquals("done", detail.lessons[0].state)
        assertEquals(true, detail.lessons[0].isAccessible)
        assertEquals("preview", detail.lessons[1].state)
        assertEquals(12345L, detail.lessons[1].file?.size)
        assertEquals("vars.pdf", detail.lessons[2].file?.name)
        // Neighbours are only returned by the single-lesson endpoint, not here.
        assertEquals(null, detail.lessons[2].next_id)
        assertEquals(30, detail.total_points)
    }

    @Test
    fun `lesson detail parses navigation fields`() = runBlocking(Dispatchers.IO) {
        enqueue(
            """
            {"ok":true,"course":{"slug":"python-basics","title":"پایتون"},
             "lesson":{"id":12,"title":"نصب","position":2,"duration_min":15,
                       "is_free_preview":true,"state":"preview",
                       "description":"توضیح درس","prev_id":11,"next_id":13,
                       "next_state":"open"}}
            """.trimIndent(),
        )
        val out = apiCall { api.lesson("python-basics", 12) }
        assertTrue(out.ok)
        assertEquals("python-basics", out.course?.slug)
        assertEquals("توضیح درس", out.lesson?.description)
        assertEquals(11L, out.lesson?.prev_id)
        assertEquals("open", out.lesson?.next_state)
    }

    @Test
    fun `league parses leaders`() = runBlocking(Dispatchers.IO) {
        enqueue(
            """
            {"ok":true,"scope":null,"courses":[{"slug":"python-basics","title":"پایتون"}],
             "leaders":[{"rank":1,"user_id":7,"name":"سارا","picture_url":"","points":120}]}
            """.trimIndent(),
        )
        val out = apiCall { api.league(null) }
        assertTrue(out.ok)
        assertEquals(1, out.courses.size)
        assertEquals("سارا", out.leaders[0].name)
        assertEquals(120, out.leaders[0].points)
        // No course filter → no query parameter.
        val path = server.takeRequest().path ?: ""
        assertEquals("/league", path)
    }

    @Test
    fun `ok false envelope throws ApiException with server message`() = runBlocking(Dispatchers.IO) {
        enqueue("""{"ok":false,"code":403,"error":"دسترسی به این درس نیاز به ثبت‌نام دارد.","action":"enroll"}""")
        try {
            apiCall { api.lesson("python-basics", 13) }
            throw AssertionError("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(403, e.error.status)
            assertEquals("enroll", e.error.action)
            assertTrue(e.error.message.contains("ثبت‌نام"))
        }
    }

    @Test
    fun `auth me parses profile points and enrollments`() = runBlocking(Dispatchers.IO) {
        enqueue(
            """
            {"ok":true,"user":{"id":5,"username":"sara","name":"سارا","email":"sara@gmail.com",
             "picture_url":"https://…/a.png"},"points":85,"enrollments":2}
            """.trimIndent(),
        )
        val me: MeOut = apiCall { api.me() }
        assertEquals(85, me.points)
        assertEquals(2, me.enrollments)
        assertEquals("سارا", me.user?.displayName)
    }

    @Test
    fun `google login response yields token`() = runBlocking(Dispatchers.IO) {
        enqueue("""{"ok":true,"token":"pdv_abc123","created":true,
                    "user":{"id":9,"username":"","name":"Ali","email":"ali@gmail.com"}}""")
        val out: TokenOut = apiCall { api.googleLogin(ir.pardava.mobile.data.dto.GoogleIn("id-token")) }
        assertTrue(out.ok)
        assertEquals("pdv_abc123", out.token)
        assertEquals("ali@gmail.com", out.user?.email)
    }

    @Test
    fun `non-json 500 maps to friendly error`() {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("<html>boom</html>"),
        )
        try {
            runBlocking(Dispatchers.IO) {
                apiCall { api.coursesIndex(server.url("/api/courses").toString()) }
            }
            throw AssertionError("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.error.message.isNotBlank())
        }
    }

    @Test
    fun `envelope error helper keeps code and action`() {
        val out = CoursesOut(ok = false, code = 404, error = "دوره یافت نشد.", action = null)
        val err = ApiError.envelope(out)
        assertEquals("404", err.code)
        assertEquals("دوره یافت نشد.", err.message)
    }

    @Test
    fun `detail empty lessons list never breaks parsing`() = runBlocking(Dispatchers.IO) {
        enqueue("""{"ok":true,"course":{"id":3,"slug":"soon","title":"به‌زودی"},"lessons":[]}""")
        val d: CourseDetailOut = apiCall { api.course("soon") }
        assertTrue(d.lessons.isEmpty())
        assertEquals(false, d.enrolled)
    }

    @Test
    fun `lesson detail unknown lesson error surfaces action`() = runBlocking(Dispatchers.IO) {
        enqueue("""{"ok":false,"code":403,"error":"برای دسترسی به این درس باید وارد حساب خود شوید.","action":"login","course":"python-basics"}""")
        try {
            apiCall { api.lesson("python-basics", 99) }
            throw AssertionError("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("login", e.error.action)
        }
    }

    @Test
    fun `lesson detail out parses anonymously`() = runBlocking(Dispatchers.IO) {
        enqueue("""{"ok":true,"course":{"slug":"s","title":"t"},"lesson":{"id":1,"title":"x","state":"preview"}}""")
        val l: LessonDetailOut = apiCall { api.lesson("s", 1) }
        assertEquals("preview", l.lesson?.state)
    }

    @Test
    fun `league scoped by course keeps query param`() = runBlocking(Dispatchers.IO) {
        enqueue("""{"ok":true,"scope":{"slug":"python-basics","title":"پایتون"},"courses":[],"leaders":[]}""")
        apiCall { api.league("python-basics") }
        assertEquals("/league?course=python-basics", server.takeRequest().path)
    }
}

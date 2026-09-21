package ir.pardava.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Full-stack Retrofit test against MockWebServer: a 401 on a protected call must
 * trigger exactly one refresh (rotated token persisted) and then succeed with the
 * new access token.
 */
class AuthRefreshTest {

    private lateinit var server: MockWebServer
    private lateinit var ok: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        ok = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private class TokenStore {
        var access: String = "expired-access"
        var refresh: String = "valid-refresh"
    }

    @Test
    fun `401 triggers single refresh then retry with rotated access token`() = runBlocking(Dispatchers.IO) {
        val store = TokenStore()
        var refreshing = false

        // Minimal authenticator mimicking ApiClient's chain: uses the refresh
        // endpoint exactly once and rotates both tokens.
        val client = ok.newBuilder()
            .authenticator { _, response ->
                if (refreshing) return@authenticator null
                refreshing = true
                val bodyJson = """{"access_token":"new-access","refresh_token":"new-refresh",
                                   "expires_in":1800,"user":{"id":1,"preferred_language":"fa"}}"""
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(bodyJson.replace("\n", " ")),
                )
                val resp = ok.newCall(
                    Request.Builder()
                        .url(server.url("/api/v1/auth/refresh"))
                        .post(
                            okhttp3.RequestBody.create(
                                "application/json".toMediaType(),
                                """{"refresh_token":"${store.refresh}"}""",
                            ),
                        )
                        .build(),
                ).execute()
                resp.use { r ->
                    if (!r.isSuccessful) return@authenticator null
                    store.access = "new-access"
                    store.refresh = "new-refresh"
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${store.access}")
                        .build()
                }
            }
            .build()

        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":{"code":"unauthorized"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"preferred_language":"fa"}"""),
        )

        val request = Request.Builder()
            .url(server.url("/api/v1/users/me"))
            .header("Authorization", "Bearer ${store.access}")
            .build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("new-access", store.access)
        assertEquals("new-refresh", store.refresh)
        // Request order: original call → refresh → retried original call.
        assertEquals("/api/v1/users/me", server.takeRequest().path)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("/api/v1/users/me", server.takeRequest().path)
    }
}

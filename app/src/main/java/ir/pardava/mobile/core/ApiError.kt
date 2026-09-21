package ir.pardava.mobile.core

import ir.pardava.mobile.data.dto.WithEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import retrofit2.HttpException
import retrofit2.Response

/**
 * Error model for the site Courses API.
 *
 * Two shapes are possible:
 * 1. Envelope inside HTTP 200: `{"ok":false,"code":403,"error":"…","action":"enroll"}`
 * 2. Real HTTP error from the CDN/host (possibly HTML) — parsed defensively.
 *
 * `error` text arrives already localized (Persian) from the server.
 */
data class ApiError(
    val status: Int,
    val code: String?,
    val message: String,
    val action: String?,
) {
    companion object {

        /** Parse an HTTP error body (may be JSON, may be HTML). */
        fun from(status: Int, body: String?): ApiError {
            val obj = runCatching {
                (Json.parseToJsonElement(body ?: "") as? JsonObject)?.jsonObject
            }.getOrNull()
            if (obj != null && obj["ok"]?.jsonPrimitive?.booleanOrNull == false) {
                return ApiError(
                    status = status,
                    code = obj["code"]?.jsonPrimitive?.let { it.intOrNull?.toString() ?: it.content },
                    message = obj["error"]?.jsonPrimitive?.content ?: "خطای ناشناخته",
                    action = obj["action"]?.jsonPrimitive?.content,
                )
            }
            return ApiError(
                status = status,
                code = obj?.get("code")?.jsonPrimitive?.content,
                message = when {
                    status == 401 -> "برای این عملیات باید وارد شوید."
                    status == 403 -> "دسترسی به این بخش مجاز نیست."
                    status == 404 -> "یافت نشد."
                    status >= 500 -> "سرور موقتاً در دسترس نیست."
                    else -> "مشکلی پیش آمد. دوباره تلاش کنید."
                },
                action = null,
            )
        }

        fun from(e: HttpException): ApiError = from(e.code(), runCatching {
            e.response()?.errorBody()?.string()
        }.getOrNull())

        /** Business error carried inside an HTTP-200 envelope. */
        fun envelope(e: WithEnvelope, fallbackStatus: Int = 400): ApiError = ApiError(
            status = e.code ?: fallbackStatus,
            code = e.code?.toString(),
            message = e.error ?: "درخواست انجام نشد.",
            action = e.action,
        )
    }
}

/** Thrown by [apiCall] for both envelope errors and HTTP errors. */
class ApiException(val error: ApiError) : Exception(error.message)

/** Map a Retrofit [Response] to its body or throw [ApiException]. */
fun <T> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) throw ApiException(ApiError.from(code(), errorBody()?.string()))
    return body() ?: throw ApiException(ApiError(status = code(), message = "پاسخ خالی بود.", code = "empty_body", action = null))
}

/**
 * Run a suspended API call and normalize failures into [ApiException]:
 * HTTP errors → [ApiError.from]; `ok:false` envelopes → [ApiError.envelope].
 */
suspend fun <T : WithEnvelope> apiCall(block: suspend () -> T): T {
    val result = try {
        block()
    } catch (e: HttpException) {
        throw ApiException(ApiError.from(e))
    }
    if (!result.ok) throw ApiException(ApiError.envelope(result))
    return result
}

/** Machine-readable `action` values the UI reacts to. */
object ErrorActions {
    const val LOGIN = "login"
    const val ENROLL = "enroll"
    const val PURCHASE = "purchase"
    const val COMPLETE_PREVIOUS = "complete_previous"
    const val RELOGIN = "relogin"
    const val LATER = "later"
}

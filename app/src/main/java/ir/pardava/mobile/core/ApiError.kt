package ir.pardava.mobile.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import retrofit2.Response

/**
 * Bilingual error envelope from the LMS API:
 * `{"error": {"code": "...", "message_fa": "...", "message_en": "..."}}`.
 */
data class ApiError(
    val status: Int,
    val code: String,
    val messageFa: String,
    val messageEn: String,
) {
    fun message(lang: String): String = if (lang == "en") messageEn else messageFa

    companion object {
        fun from(status: Int, body: String?): ApiError {
            val json = runCatching { Json.parseToJsonElement(body ?: "").jsonObject }.getOrNull()
            val err = (json?.get("error") as? JsonObject)
            return ApiError(
                status = status,
                code = err?.get("code")?.toString()?.trim('"') ?: "unknown",
                messageFa = err?.get("message_fa")?.toString()?.trim('"') ?: "خطای ناشناخته",
                messageEn = err?.get("message_en")?.toString()?.trim('"') ?: "Unknown error",
            )
        }

        fun from(e: HttpException): ApiError = from(e.code(), e.response()?.errorBody()?.string())
    }
}

/** Thrown by the API client on non-2xx responses. */
class ApiException(val error: ApiError) : Exception(error.messageEn)

/** Maps a Retrofit [Response] to its body or throws [ApiException]. */
fun <T> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) throw ApiException(ApiError.from(code(), errorBody()?.string()))
    return body() ?: throw ApiException(ApiError(status = code(), code = "empty_body", messageFa = "پاسخ خالی", messageEn = "Empty body"))
}

/** Map of error codes the UI treats specially (sequential-lock etc.). */
object ErrorCodes {
    const val LESSON_LOCKED = "lesson_locked"
    const val QUIZ_MAX_ATTEMPTS = "quiz_max_attempts"
    const val ATTEMPT_EXPIRED = "attempt_expired"
    const val RATE_LIMITED = "rate_limited"
    const val INVALID_OTP = "invalid_otp"
}

package ir.pardava.mobile.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Pardava Courses API (https://pardava.ir/api/courses, v1.1.0) contract notes:
 * - EVERY response is HTTP 200 with a JSON envelope that always carries `ok`.
 * - Business errors arrive as ok=false + machine `code` (int) + human `error`
 *   (already localized by the server) + optional `action`.
 * - All DTOs therefore tolerate unknown keys and default everything optional;
 *   errors are surfaced through [Envelope.requireOk].
 */

/** Shared envelope fields. Every response DTO embeds this interface's defaults. */
interface Envelope {
    val ok: Boolean?
    val code: Int?
    val error: String?
    val action: String?
}

/** Thrown by the client when the envelope says ok=false or transport fails. */
class ApiException(
    val code: Int,
    override val message: String,
    val action: String? = null,
) : Exception(message) {
    val isAuthError: Boolean get() = code == 401 || code == 403
}

/** UI helper: turn any envelope into ok or throw [ApiException]. */
fun <T : Envelope> T.requireOk(): T {
    if (ok == false) throw ApiException(code ?: 500, error ?: "خطای ناشناخته", action)
    return this
}

/* ---------------- auth ---------------- */

@Serializable
data class LoginIn(val username: String, val password: String, val label: String? = "android")

@Serializable
data class OtpRequestIn(val phone: String)

@Serializable
data class OtpVerifyIn(val phone: String, val code: String)

@Serializable
data class GoogleLoginIn(val id_token: String, val label: String? = "android")

@Serializable
data class UserDto(
    val id: Long? = null,
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
    val picture_url: String? = null,
) {
    /** Best human-readable display name across all auth providers. */
    val displayName: String
        get() = listOfNotNull(name, username, email).firstOrNull { it.isNotBlank() } ?: "کاربر پردآوا"
}

@Serializable
data class TokenResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val token: String? = null,
    val user: UserDto? = null,
    val created: Boolean? = null,
) : Envelope

@Serializable
data class OtpRequestResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val sent: Boolean? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    /** Some deployments echo the OTP in development; never shown in release UI. */
    val dev_code: String? = null,
) : Envelope

@Serializable
data class MeResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val user: UserDto? = null,
    val points: Int? = null,
    @SerialName("enrolled_count") val enrolledCount: Int? = null,
    val enrollments: List<CourseRefDto>? = null,
) : Envelope

@Serializable
data class SimpleOkResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val message: String? = null,
) : Envelope

/* ---------------- catalog ---------------- */

@Serializable
data class CourseRefDto(val slug: String? = null, val title: String? = null)

@Serializable
data class CourseCardDto(
    val id: Long? = null,
    val slug: String? = null,
    val title: String? = null,
    val category: String? = null,
    val category_label: String? = null,
    val level: String? = null,
    val level_label: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val cover_url: String? = null,
    val price: Int? = null,
    @SerialName("is_free") val isFree: Boolean? = null,
    @SerialName("is_paid") val isPaid: Boolean? = null,
    @SerialName("lesson_count") val lessonCount: Int? = null,
    @SerialName("lessons_count") val lessonsCountAlt: Int? = null,
    @SerialName("students_count") val studentsCount: Int? = null,
    val enrolled: Boolean? = null,
    @SerialName("total_minutes") val totalMinutes: Int? = null,
    @SerialName("points_per_lesson") val pointsPerLesson: Int? = null,
    @SerialName("sequential_unlock") val sequentialUnlock: Boolean? = null,
    @SerialName("is_published") val isPublished: Boolean? = null,
) {
    val effectiveLessonCount: Int get() = lessonCount ?: lessonsCountAlt ?: 0
    val effectiveFree: Boolean
        get() = isFree ?: isPaid?.not() ?: ((price ?: 0) <= 0)
}

@Serializable
data class CoursesListResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val count: Int? = null,
    val courses: List<CourseCardDto> = emptyList(),
) : Envelope

@Serializable
data class LessonItemDto(
    val id: Long? = null,
    val position: Int? = null,
    val title: String? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("is_free_preview") val isFreePreview: Boolean? = null,
    val state: String? = null,
) {
    companion object {
        const val STATE_PREVIEW = "preview"
        const val STATE_LOCKED = "locked"
        const val STATE_AVAILABLE = "available"
        const val STATE_COMPLETED = "completed"
    }
}

@Serializable
data class CourseDetailResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val course: CourseCardDto? = null,
    val lessons: List<LessonItemDto> = emptyList(),
    val enrolled: Boolean? = null,
    @SerialName("full_access") val fullAccess: Boolean? = null,
    @SerialName("completed_count") val completedCount: Int? = null,
    @SerialName("my_points_in_course") val myPointsInCourse: Int? = null,
    @SerialName("total_points") val totalPoints: Int? = null,
    @SerialName("pending_purchase") val pendingPurchase: Boolean? = null,
) : Envelope

@Serializable
data class LessonContentDto(
    val id: Long? = null,
    val position: Int? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("is_free_preview") val isFreePreview: Boolean? = null,
    val state: String? = null,
    @SerialName("prev_id") val prevId: Long? = null,
    @SerialName("next_id") val nextId: Long? = null,
    @SerialName("next_state") val nextState: String? = null,
    @SerialName("has_file") val hasFile: Boolean? = null,
    @SerialName("file_name") val fileName: String? = null,
)

@Serializable
data class LessonContentResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val course: CourseRefDto? = null,
    val lesson: LessonContentDto? = null,
) : Envelope

@Serializable
data class CompleteResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val message: String? = null,
    @SerialName("points_awarded") val pointsAwarded: Int? = null,
    @SerialName("total_points") val totalPoints: Int? = null,
    @SerialName("already_completed") val alreadyCompleted: Boolean? = null,
) : Envelope

/* ---------------- league ---------------- */

@Serializable
data class LeaderRowDto(
    val rank: Int? = null,
    val user_id: Long? = null,
    val name: String? = null,
    val display_name: String? = null,
    val username: String? = null,
    val points: Int? = null,
    val total_points: Int? = null,
    @SerialName("lessons_completed") val lessonsCompleted: Int? = null,
    @SerialName("is_you") val isYou: Boolean? = null,
    @SerialName("picture_url") val pictureUrl: String? = null,
) {
    val displayName: String
        get() = listOfNotNull(name, display_name, username).firstOrNull { !it.isNullOrBlank() } ?: "دانش‌پژوه"
    val effectivePoints: Int get() = points ?: total_points ?: 0
}

@Serializable
data class LeagueResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val scope: CourseRefDto? = null,
    val courses: List<CourseRefDto> = emptyList(),
    val leaders: List<LeaderRowDto> = emptyList(),
) : Envelope

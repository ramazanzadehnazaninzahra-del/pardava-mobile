package ir.pardava.mobile.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    /**
     * Server contract (OpenAPI): `enrollments` is an INTEGER count, not a list.
     * Treating it as a list broke deserialization on every /auth/me call and
     * made the profile screen fail permanently. The enrolled course list comes
     * from /api/courses (each row carries `enrolled`) — see ProfileViewModel.
     */
    val enrollments: Int? = null,
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
        // Server vocabulary (courses_page._lesson_state): open | preview | locked | enroll | done
        const val STATE_PREVIEW = "preview"
        const val STATE_LOCKED = "locked"
        const val STATE_OPEN = "open"
        const val STATE_ENROLL = "enroll"
        const val STATE_DONE = "done"
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
    @SerialName("has_video") val hasVideo: Boolean? = null,
    @SerialName("file_name") val fileName: String? = null,
    val file: LessonFileDto? = null,
)

/** Attachment metadata (url may be empty when the master stays on storage). */
@Serializable
data class LessonFileDto(
    val name: String? = null,
    val size: Long? = null,
    val url: String? = null,
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

/* ---------------- learning bundle (watch progress, quiz, certificate, rating) ---------------- */

/** Saved playback position for a lesson (resume support). */
@Serializable
data class WatchDto(
    val position: Double? = null,
    val duration: Double? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class WatchIn(
    val position: Double,
    val duration: Double,
    /** رویداد پایان پخش از سوی کلاینت؛ سرور همین را هم تکمیل خودکار حساب می‌کند. */
    val completed: Boolean? = null,
)

@Serializable
data class ProgressResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val watch: WatchDto? = null,
) : Envelope

@Serializable
data class ProgressSaveResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val saved: Boolean? = null,
    val position: Double? = null,
    /** تکمیل خودکار بر اساس زمان تماشا (۹۰٪ یا پایان ویدیو) از سوی سرور. */
    val completed: Boolean? = null,
    val points: Int? = null,
    @SerialName("total_points") val totalPoints: Int? = null,
) : Envelope

@Serializable
data class RatingSummaryDto(
    val avg: Double? = null,
    val count: Int? = null,
)

@Serializable
data class MyRatingDto(
    val stars: Int? = null,
    val review: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CertificateDto(
    val available: Boolean? = null,
    val eligible: Boolean? = null,
    val issued: Boolean? = null,
    val serial: String? = null,
    val url: String? = null,
    val requirements: CertRequirementsDto? = null,
)

@Serializable
data class CertRequirementsDto(
    val paid: Boolean? = null,
    @SerialName("full_access") val fullAccess: Boolean? = null,
    val completed: Int? = null,
    val total: Int? = null,
)

@Serializable
data class QuizMetaDto(
    val title: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("pass_percent") val passPercent: Int? = null,
    val questions: Int? = null,
    val best: Int? = null,
    val attempts: Int? = null,
)

/** One-call bundle powering the course screen extras. */
@Serializable
data class LearningResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val course: CourseRefDto? = null,
    @SerialName("lesson_count") val lessonCount: Int? = null,
    val rating: RatingSummaryDto? = null,
    @SerialName("signed_in") val signedIn: Boolean? = null,
    @SerialName("full_access") val fullAccess: Boolean? = null,
    @SerialName("completed_count") val completedCount: Int? = null,
    @SerialName("my_rating") val myRating: MyRatingDto? = null,
    val quiz: QuizMetaDto? = null,
    val certificate: CertificateDto? = null,
) : Envelope

@Serializable
data class QuizQuestionDto(
    val id: Long? = null,
    val position: Int? = null,
    val text: String? = null,
    val options: Map<String, String> = emptyMap(),
    val points: Int? = null,
)

@Serializable
data class QuizDetailResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val quiz: QuizDto? = null,
) : Envelope

@Serializable
data class QuizDto(
    val title: String? = null,
    @SerialName("pass_percent") val passPercent: Int? = null,
    val questions: List<QuizQuestionDto> = emptyList(),
    val best: Int? = null,
    val attempts: Int? = null,
)

@Serializable
data class QuizSubmitIn(val answers: Map<String, String>)

@Serializable
data class QuizResultDto(
    val score: Int? = null,
    val correct: Int? = null,
    val total: Int? = null,
    val passed: Boolean? = null,
    @SerialName("pass_percent") val passPercent: Int? = null,
    val best: Int? = null,
)

@Serializable
data class QuizSubmitResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val result: QuizResultDto? = null,
) : Envelope

@Serializable
data class RateIn(val stars: Int, val review: String? = null)

@Serializable
data class RateResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val rating: RatingSummaryDto? = null,
    @SerialName("my_stars") val myStars: Int? = null,
) : Envelope

/** Android install/usage event batch — recorded server-side in customer_events. */
@Serializable
data class AppLogEvent(
    val name: String,
    val ts: Long? = null,
    val path: String? = null,
    val label: String? = null,
    val detail: Map<String, String>? = null,
    val value: Double? = null,
)

@Serializable
data class AppLogBatchIn(
    val events: List<AppLogEvent>,
    @SerialName("install_id") val installId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val lang: String? = null,
)

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

/* ---------------- mobile service (https://pardava.ir/api/mobile) ---------------- */

@Serializable
data class LatestVersionDto(
    @SerialName("versionCode") val versionCode: Int? = null,
    @SerialName("versionName") val versionName: String? = null,
    @SerialName("apkUrl") val apkUrl: String? = null,
    @SerialName("whatsNew") val whatsNew: String? = null,
    @SerialName("minVersionCode") val minVersionCode: Int? = null,
    @SerialName("publishedAt") val publishedAt: String? = null,
)

@Serializable
data class VersionResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val latest: LatestVersionDto? = null,
) : Envelope

@Serializable
data class ServiceItemDto(
    val key: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val url: String? = null,
    val icon: String? = null,
    val category: String? = null,
)

@Serializable
data class MobileArticleDto(
    val id: Long? = null,
    val slug: String? = null,
    val title: String? = null,
    val excerpt: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val tag: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class HomeResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val services: List<ServiceItemDto> = emptyList(),
    val courses: List<CourseCardDto> = emptyList(),
    val articles: List<MobileArticleDto> = emptyList(),
    val stats: HomeStatsDto? = null,
) : Envelope

@Serializable
data class HomeStatsDto(
    val courses: Int? = null,
    val articles: Int? = null,
    val services: Int? = null,
)

@Serializable
data class ArticlesResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val count: Int? = null,
    val articles: List<MobileArticleDto> = emptyList(),
) : Envelope

@Serializable
data class ArticleDetailDto(
    val id: Long? = null,
    val slug: String? = null,
    val title: String? = null,
    val excerpt: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val tag: String? = null,
    val author: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val html: String? = null,
    val url: String? = null,
)

@Serializable
data class ArticleDetailResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val article: ArticleDetailDto? = null,
) : Envelope

/** Google web-bridge exchange response — carries the same pdv_ token as other logins. */
@Serializable
data class ExchangeResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val token: String? = null,
    val user: UserDto? = null,
    @SerialName("site_url") val siteUrl: String? = null,
) : Envelope

@Serializable
data class ServicesResponse(
    override val ok: Boolean? = true,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
    val count: Int? = null,
    val services: List<ServiceItemDto> = emptyList(),
) : Envelope

@Serializable
data class ExchangeCodeIn(val code: String)

@Serializable
data class CancelAuthIn(val nonce: String)

package ir.pardava.mobile.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * DTOs for the Pardava Courses API (https://pardava.ir/api/courses).
 *
 * Convention: every response is HTTP 200 with a JSON envelope `{ok, ...}`;
 * business errors carry `ok:false` + machine-readable `code`/`action` fields.
 * All fields have defaults so unknown/missing keys never break parsing.
 */
interface WithEnvelope {
    val ok: Boolean
    val code: Int?
    val error: String?
    val action: String?
}

@Serializable
data class UserDto(
    val id: Long = 0,
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val picture_url: String = "",
) {
    /** Best display name: name → username → email prefix. */
    val displayName: String
        get() = (name.ifBlank { username }).ifBlank { email.substringBefore('@') }
}

@Serializable
data class TokenOut(
    override val ok: Boolean = false,
    val token: String = "",
    val user: UserDto? = null,
    val created: Boolean? = null,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
) : WithEnvelope

@Serializable
data class MeOut(
    override val ok: Boolean = false,
    val user: UserDto? = null,
    val points: Int = 0,
    val enrollments: Int = 0,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
) : WithEnvelope

@Serializable
data class CourseDto(
    val id: Long = 0,
    val slug: String = "",
    val title: String = "",
    val category: String = "",
    val category_label: String = "",
    val level: String = "",
    val level_label: String = "",
    val summary: String = "",
    val description: String? = null,
    val price: Long = 0,
    val is_free: Boolean = true,
    val is_published: Boolean = true,
    val sequential_unlock: Boolean = false,
    val points_per_lesson: Int = 0,
    val cover_url: String = "",
    val lesson_count: Int = 0,
    val total_minutes: Int = 0,
    val enrolled: Boolean = false,
)

@Serializable
data class PendingPurchaseDto(
    val amount: Long = 0,
    val created_at: String = "",
)

@Serializable
data class LessonFileDto(
    val name: String = "",
    val size: Long = 0,
    val url: String? = null,
)

/** Lesson state from the server: open | preview | locked | enroll | done. */
@Serializable
data class LessonDto(
    val id: Long = 0,
    val title: String = "",
    val position: Int = 0,
    val duration_min: Int = 0,
    val is_free_preview: Boolean = false,
    val state: String = "locked",
    val file: LessonFileDto? = null,
    val description: String? = null,
    val prev_id: Long? = null,
    val next_id: Long? = null,
    val next_state: String? = null,
) {
    val isAccessible: Boolean get() = state == "open" || state == "preview" || state == "done"
}

@Serializable
data class CourseDetailOut(
    override val ok: Boolean = false,
    val course: CourseDto? = null,
    val enrolled: Boolean = false,
    val full_access: Boolean = false,
    val completed_count: Int = 0,
    val pending_purchase: PendingPurchaseDto? = null,
    val my_points_in_course: Int = 0,
    val total_points: Int = 0,
    val lessons: List<LessonDto> = emptyList(),
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
) : WithEnvelope

@Serializable
data class CoursesOut(
    override val ok: Boolean = false,
    val count: Int = 0,
    val courses: List<CourseDto> = emptyList(),
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
) : WithEnvelope

@Serializable(with = CourseRefDtoSerializer::class)
data class CourseRefDto(
    val slug: String = "",
    val title: String = "",
)

/**
 * Tolerant deserializer: success payloads return `{"slug":…,"title":…}` while
 * error envelopes may carry `"course":"<slug>"` as a plain string. Both decode.
 */
object CourseRefDtoSerializer : KSerializer<CourseRefDto> {
    private val delegate = CourseRefDto.serializer()
    private val json = Json { ignoreUnknownKeys = true }

    // Nullable: the field is `CourseRefDto?` in LessonDetailOut.
    override val descriptor: SerialDescriptor = delegate.descriptor.nullable

    override fun serialize(encoder: Encoder, value: CourseRefDto) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): CourseRefDto {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val el = jsonDecoder.decodeJsonElement()) {
            is JsonObject -> json.decodeFromJsonElement(delegate, el)
            is JsonPrimitive -> CourseRefDto(slug = el.content)
            else -> CourseRefDto()
        }
    }
}

@Serializable
data class LeaderDto(
    val rank: Int = 0,
    val user_id: Long = 0,
    val name: String = "",
    val picture_url: String = "",
    val points: Int = 0,
)

@Serializable
data class LeagueOut(
    override val ok: Boolean = false,
    val scope: CourseRefDto? = null,
    val courses: List<CourseRefDto> = emptyList(),
    val leaders: List<LeaderDto> = emptyList(),
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
) : WithEnvelope

@Serializable
data class LessonDetailOut(
    override val ok: Boolean = false,
    val course: CourseRefDto? = null,
    val lesson: LessonDto? = null,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
) : WithEnvelope

/** Generic action response: enroll / purchase / complete / logout. */
@Serializable
data class MessageOut(
    override val ok: Boolean = false,
    val message: String? = null,
    val status: String? = null,
    val course: String? = null,
    val revoked: Boolean? = null,
    val already_completed: Boolean? = null,
    val points_awarded: Int? = null,
    val total_points: Int? = null,
    override val code: Int? = null,
    override val error: String? = null,
    override val action: String? = null,
) : WithEnvelope

// ---------------------------------------------------------------- requests

@Serializable
data class LoginIn(
    val username: String,
    val password: String,
    val label: String = "pardava-android",
)

@Serializable
data class OtpRequestIn(val phone: String)

@Serializable
data class OtpVerifyIn(
    val phone: String,
    val code: String,
)

@Serializable
data class GoogleIn(
    val id_token: String,
    val label: String = "pardava-android",
)

@Serializable
data class PurchaseIn(val note: String = "")

package ir.pardava.mobile.data.dto

import kotlinx.serialization.Serializable

/* ---------- Auth ---------- */

@Serializable
data class OtpRequestIn(val phone: String)

@Serializable
data class OtpRequestOut(
    val ok: Boolean = true,
    val phone: String,
    val expires_in: Int,
    val dev_code: String? = null,
)

@Serializable
data class OtpVerifyIn(val phone: String, val code: String, val device: String? = null)

@Serializable
data class GoogleLoginIn(val id_token: String, val device: String? = null)

@Serializable
data class GoogleExchangeIn(val code: String, val device: String? = null)

@Serializable
data class RefreshIn(val refresh_token: String)

@Serializable
data class LogoutIn(val refresh_token: String)

@Serializable
data class UserOut(
    val id: Long,
    val phone: String? = null,
    val email: String? = null,
    val display_name: String? = null,
    val avatar_url: String? = null,
    val preferred_language: String = "fa",
    val role: String = "learner",
    val is_active: Boolean = true,
)

@Serializable
data class TokenOut(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "bearer",
    val expires_in: Int,
    val user: UserOut,
)

/* ---------- Catalog ---------- */

@Serializable
data class CourseBrief(
    val id: Long,
    val slug: String,
    val level: String = "beginner",
    val cover_url: String? = null,
    val title: String,
    val description: String? = null,
    val chapters_count: Int = 0,
    val lessons_count: Int = 0,
    val lang: String = "fa",
    val fallback_used: Boolean = false,
)

@Serializable
data class LessonBrief(
    val id: Long,
    val slug: String,
    val title: String,
    val sort_order: Int = 0,
    val estimated_minutes: Int? = null,
    val has_quiz: Boolean = false,
    val is_published: Boolean = true,
    val fallback_used: Boolean = false,
    val locked: Boolean = false,
    val completed: Boolean = false,
    val quiz_passed: Boolean = false,
    val unlock_requirements: List<String> = emptyList(),
    val best_score_percent: Double? = null,
    val attempts_used: Int = 0,
)

@Serializable
data class ChapterOut(
    val id: Long,
    val title: String,
    val description: String? = null,
    val sort_order: Int = 0,
    val fallback_used: Boolean = false,
    val lessons: List<LessonBrief> = emptyList(),
)

@Serializable
data class CourseDetail(
    val id: Long,
    val slug: String,
    val level: String = "beginner",
    val cover_url: String? = null,
    val title: String,
    val description: String? = null,
    val lang: String = "fa",
    val fallback_used: Boolean = false,
    val chapters: List<ChapterOut> = emptyList(),
)

@Serializable
data class VideoOut(
    val id: Long,
    val lang: String,
    val duration_seconds: Int? = null,
    val is_placeholder: Boolean = false,
)

@Serializable
data class SubtitleOut(val id: Long, val lang: String)

@Serializable
data class QuizMetaOut(
    val id: Long,
    val title: String,
    val question_count: Int,
    val duration_seconds: Int? = null,
    val max_attempts: Int,
    val pass_score_percent: Int,
    val attempts_used: Int? = null,
    val fallback_used: Boolean = false,
)

@Serializable
data class LessonDetail(
    val id: Long,
    val slug: String,
    val chapter_id: Long,
    val course_id: Long,
    val course_slug: String,
    val title: String,
    val body: String? = null,
    val code_sample: String? = null,
    val exercise: String? = null,
    val lang: String = "fa",
    val fallback_used: Boolean = false,
    val videos: List<VideoOut> = emptyList(),
    val subtitles: List<SubtitleOut> = emptyList(),
    val quiz: QuizMetaOut? = null,
    val locked: Boolean = false,
    val completed: Boolean = false,
    val quiz_passed: Boolean = false,
    val unlock_requirements: Map<String, Map<String, String>> = emptyMap(),
    val best_score_percent: Double? = null,
    val attempts_used: Int = 0,
)

@Serializable
data class VideoUrlOut(
    val video_id: Long,
    val lang: String,
    val url: String,
    val expires_in: Int,
    val is_placeholder: Boolean = false,
)

@Serializable
data class SubtitleUrlOut(
    val subtitle_id: Long,
    val lang: String,
    val url: String,
    val expires_in: Int,
)

@Serializable
data class ProgressIn(
    val video_lang: String? = null,
    val watched_seconds: Int? = null,
    val watched_percent: Int? = null,
)

@Serializable
data class ProgressOut(
    val lesson_id: Long,
    val lesson_slug: String,
    val course_id: Long,
    val course_slug: String,
    val video_lang: String? = null,
    val watched_seconds: Int = 0,
    val watched_percent: Int = 0,
    val completed: Boolean = false,
)

/* ---------- Quiz engine ---------- */

@Serializable
data class AttemptOut(
    val attempt_id: Long,
    val quiz_id: Long,
    val attempt_no: Int,
    val status: String,
    val started_at: String,
    val expires_at: String? = null,
    val duration_seconds: Int? = null,
    val question_count: Int,
    val attempts_remaining: Int,
    val max_attempts: Int,
)

@Serializable
data class AttemptOptionOut(val id: Long, val text: String)

@Serializable
data class AttemptQuestionOut(
    val id: Long,
    val type: String,
    val points: Int,
    val prompt: String,
    val options: List<AttemptOptionOut> = emptyList(),
    val answered: Boolean = false,
)

@Serializable
data class AttemptQuestionsOut(
    val attempt_id: Long,
    val attempt_no: Int,
    val status: String,
    val expires_at: String? = null,
    val questions: List<AttemptQuestionOut> = emptyList(),
)

@Serializable
data class SubmitIn(val answers: Map<String, kotlinx.serialization.json.JsonElement>)

@Serializable
data class PerQuestionResultOut(
    val question_id: Long,
    val correct: Boolean,
    val points: Int,
    val correct_answer: kotlinx.serialization.json.JsonObject,
    val explanation: String? = null,
)

@Serializable
data class XpAwardOut(val type: String, val amount: Int)

@Serializable
data class NewAchievementOut(
    val code: String,
    val name: String,
    val description: String? = null,
    val xp_reward: Int = 0,
)

@Serializable
data class SubmitResultOut(
    val attempt_id: Long,
    val attempt_no: Int,
    val score_percent: Double,
    val passed: Boolean,
    val pass_score_percent: Int,
    val earned_points: Int,
    val total_points: Int,
    val per_question: List<PerQuestionResultOut> = emptyList(),
    val xp_awarded: List<XpAwardOut> = emptyList(),
    val new_achievements: List<NewAchievementOut> = emptyList(),
)

/* ---------- Gamification ---------- */

@Serializable
data class LevelProgressOut(
    val level: Int,
    val current_level_xp: Int,
    val next_level_xp: Int,
    val percent: Double,
)

@Serializable
data class UserStatsOut(
    val xp_total: Int,
    val level: Int,
    val level_progress: LevelProgressOut,
    val current_streak: Int,
    val longest_streak: Int,
    val lessons_completed: Int,
    val quizzes_passed: Int,
    val perfect_quizzes: Int,
    val rank: Int,
    val achievements_count: Int,
)

@Serializable
data class AchievementOut(
    val code: String,
    val icon: String? = null,
    val xp_reward: Int,
    val name: String,
    val description: String? = null,
    val earned_at: String? = null,
)

@Serializable
data class LeaderboardEntryOut(
    val rank: Int,
    val user_id: Long,
    val display_name: String,
    val xp: Int,
)

@Serializable
data class LeaderboardOut(
    val scope: String,
    val period_key: String,
    val entries: List<LeaderboardEntryOut> = emptyList(),
    val me: LeaderboardEntryOut? = null,
)

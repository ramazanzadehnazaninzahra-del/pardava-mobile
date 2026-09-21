package ir.pardava.mobile.data

import ir.pardava.mobile.data.dto.AttemptOut
import ir.pardava.mobile.data.dto.AttemptQuestionsOut
import ir.pardava.mobile.data.dto.CourseBrief
import ir.pardava.mobile.data.dto.CourseDetail
import ir.pardava.mobile.data.dto.GoogleExchangeIn
import ir.pardava.mobile.data.dto.GoogleLoginIn
import ir.pardava.mobile.data.dto.LeaderboardOut
import ir.pardava.mobile.data.dto.LessonDetail
import ir.pardava.mobile.data.dto.LogoutIn
import ir.pardava.mobile.data.dto.OtpRequestIn
import ir.pardava.mobile.data.dto.OtpRequestOut
import ir.pardava.mobile.data.dto.OtpVerifyIn
import ir.pardava.mobile.data.dto.ProgressIn
import ir.pardava.mobile.data.dto.ProgressOut
import ir.pardava.mobile.data.dto.RefreshIn
import ir.pardava.mobile.data.dto.SubtitleUrlOut
import ir.pardava.mobile.data.dto.SubmitIn
import ir.pardava.mobile.data.dto.SubmitResultOut
import ir.pardava.mobile.data.dto.TokenOut
import ir.pardava.mobile.data.dto.UserOut
import ir.pardava.mobile.data.dto.UserStatsOut
import ir.pardava.mobile.data.dto.VideoUrlOut
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PardavaApi {

    /* ---- auth ---- */

    @POST("api/v1/auth/otp/request")
    suspend fun otpRequest(@Body body: OtpRequestIn): OtpRequestOut

    @POST("api/v1/auth/otp/verify")
    suspend fun otpVerify(@Body body: OtpVerifyIn): TokenOut

    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginIn): TokenOut

    @POST("api/v1/auth/google/exchange")
    suspend fun googleExchange(@Body body: GoogleExchangeIn): TokenOut

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshIn): TokenOut

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutIn)

    @GET("api/v1/auth/me")
    suspend fun me(): UserOut

    /* ---- users ---- */

    @PATCH("api/v1/users/me")
    suspend fun updateMe(@Body body: UpdateProfileIn): UserOut

    @GET("api/v1/users/me/progress")
    suspend fun myProgress(): List<ProgressOut>

    /* ---- catalog ---- */

    @GET("api/v1/courses")
    suspend fun courses(@Query("lang") lang: String? = null): List<CourseBrief>

    @GET("api/v1/courses/{slug}")
    suspend fun course(@Path("slug") slug: String, @Query("lang") lang: String? = null): CourseDetail

    @GET("api/v1/courses/{slug}/lessons/{lessonSlug}")
    suspend fun lesson(
        @Path("slug") slug: String,
        @Path("lessonSlug") lessonSlug: String,
        @Query("lang") lang: String? = null,
    ): LessonDetail

    @POST("api/v1/lessons/{lessonId}/progress")
    suspend fun reportProgress(@Path("lessonId") lessonId: Long, @Body body: ProgressIn): Map<String, kotlinx.serialization.json.JsonElement>

    @GET("api/v1/videos/{videoId}/url")
    suspend fun videoUrl(@Path("videoId") videoId: Long): VideoUrlOut

    @GET("api/v1/subtitles/{subtitleId}/url")
    suspend fun subtitleUrl(@Path("subtitleId") subtitleId: Long): SubtitleUrlOut

    /* ---- quiz engine ---- */

    @POST("api/v1/quizzes/{quizId}/attempts")
    suspend fun startAttempt(@Path("quizId") quizId: Long): AttemptOut

    @GET("api/v1/quizzes/attempts/{attemptId}")
    suspend fun attemptQuestions(@Path("attemptId") attemptId: Long): AttemptQuestionsOut

    @POST("api/v1/quizzes/attempts/{attemptId}/submit")
    suspend fun submitAttempt(@Path("attemptId") attemptId: Long, @Body body: SubmitIn): SubmitResultOut

    /* ---- gamification ---- */

    @GET("api/v1/leaderboard")
    suspend fun leaderboard(@Query("scope") scope: String = "weekly"): LeaderboardOut

    @GET("api/v1/users/me/stats")
    suspend fun myStats(): UserStatsOut

    @GET("api/v1/users/me/achievements")
    suspend fun myAchievements(): List<ir.pardava.mobile.data.dto.AchievementOut>
}

/** Profile patch payload (both fields optional). */
@Serializable
data class UpdateProfileIn(
    val preferred_language: String? = null,
    val display_name: String? = null,
)

package ir.pardava.mobile.data

import ir.pardava.mobile.data.dto.CourseDetailOut
import ir.pardava.mobile.data.dto.CoursesOut
import ir.pardava.mobile.data.dto.GoogleIn
import ir.pardava.mobile.data.dto.LeagueOut
import ir.pardava.mobile.data.dto.LessonDetailOut
import ir.pardava.mobile.data.dto.LoginIn
import ir.pardava.mobile.data.dto.MeOut
import ir.pardava.mobile.data.dto.MessageOut
import ir.pardava.mobile.data.dto.OtpRequestIn
import ir.pardava.mobile.data.dto.OtpVerifyIn
import ir.pardava.mobile.data.dto.PurchaseIn
import ir.pardava.mobile.data.dto.TokenOut
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Pardava Courses API (site backend, https://pardava.ir/api/courses).
 *
 * Retrofit base URL = "<server>/api/courses/" — every relative path below is
 * appended to it. The courses index lives at the prefix WITHOUT a trailing
 * slash (Flask route ""), so it is fetched via an absolute [Url] override.
 */
interface PardavaApi {

    // ---------------------------------------------------------------- auth

    @POST("auth/login")
    suspend fun login(@Body body: LoginIn): TokenOut

    @POST("auth/otp/request")
    suspend fun otpRequest(@Body body: OtpRequestIn): MessageOut

    @POST("auth/otp/verify")
    suspend fun otpVerify(@Body body: OtpVerifyIn): TokenOut

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleIn): TokenOut

    @GET("auth/me")
    suspend fun me(): MeOut

    @POST("auth/logout")
    suspend fun logout(): MessageOut

    // ---------------------------------------------------------------- courses

    /** Absolute URL override — the index route must NOT end with a slash. */
    @GET
    suspend fun coursesIndex(@Url url: String): CoursesOut

    @GET("league")
    suspend fun league(@Query("course") course: String? = null): LeagueOut

    @GET("{slug}")
    suspend fun course(@Path("slug") slug: String): CourseDetailOut

    // ---------------------------------------------------------------- learning

    @POST("{slug}/enroll")
    suspend fun enroll(@Path("slug") slug: String): MessageOut

    @POST("{slug}/purchase")
    suspend fun purchase(
        @Path("slug") slug: String,
        @Body body: PurchaseIn = PurchaseIn(),
    ): MessageOut

    @GET("{slug}/lessons/{lessonId}")
    suspend fun lesson(
        @Path("slug") slug: String,
        @Path("lessonId") lessonId: Long,
    ): LessonDetailOut

    @POST("{slug}/lessons/{lessonId}/complete")
    suspend fun completeLesson(
        @Path("slug") slug: String,
        @Path("lessonId") lessonId: Long,
    ): MessageOut

    @Streaming
    @GET("{slug}/lessons/{lessonId}/file")
    suspend fun lessonFile(
        @Path("slug") slug: String,
        @Path("lessonId") lessonId: Long,
    ): ResponseBody
}

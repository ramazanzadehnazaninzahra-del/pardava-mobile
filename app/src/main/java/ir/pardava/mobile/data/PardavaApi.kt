package ir.pardava.mobile.data

import ir.pardava.mobile.data.dto.ArticleDetailResponse
import ir.pardava.mobile.data.dto.ArticlesResponse
import ir.pardava.mobile.data.dto.CancelAuthIn
import ir.pardava.mobile.data.dto.CompleteResponse
import ir.pardava.mobile.data.dto.CourseDetailResponse
import ir.pardava.mobile.data.dto.CoursesListResponse
import ir.pardava.mobile.data.dto.ExchangeCodeIn
import ir.pardava.mobile.data.dto.ExchangeResponse
import ir.pardava.mobile.data.dto.GoogleLoginIn
import ir.pardava.mobile.data.dto.HomeResponse
import ir.pardava.mobile.data.dto.LeagueResponse
import ir.pardava.mobile.data.dto.LessonContentResponse
import ir.pardava.mobile.data.dto.LoginIn
import ir.pardava.mobile.data.dto.MeResponse
import ir.pardava.mobile.data.dto.OtpRequestIn
import ir.pardava.mobile.data.dto.OtpRequestResponse
import ir.pardava.mobile.data.dto.OtpVerifyIn
import ir.pardava.mobile.data.dto.ServicesResponse
import ir.pardava.mobile.data.dto.SimpleOkResponse
import ir.pardava.mobile.data.dto.TokenResponse
import ir.pardava.mobile.data.dto.VersionResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pardava Courses API (v1.1.0) — base URL is the SITE ROOT (e.g. https://pardava.ir/)
 * and every path below is prefixed with api/courses/…
 *
 * Transport quirk: the server always answers HTTP 200; business failures arrive
 * inside the envelope (ok=false + code + error + action) and are converted to
 * [ir.pardava.mobile.data.dto.ApiException] via requireOk().
 */
interface PardavaApi {

    /* ---- auth (token = single session token `pdv_…`, no refresh) ---- */

    @POST("api/courses/auth/login")
    suspend fun login(@Body body: LoginIn): TokenResponse

    @POST("api/courses/auth/otp/request")
    suspend fun otpRequest(@Body body: OtpRequestIn): OtpRequestResponse

    @POST("api/courses/auth/otp/verify")
    suspend fun otpVerify(@Body body: OtpVerifyIn): TokenResponse

    @POST("api/courses/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginIn): TokenResponse

    @GET("api/courses/auth/me")
    suspend fun me(): MeResponse

    @POST("api/courses/auth/logout")
    suspend fun logout(): SimpleOkResponse

    /* ---- catalog (public browsing — no login required) ---- */

    @GET("api/courses")
    suspend fun courses(): CoursesListResponse

    @GET("api/courses/league")
    suspend fun league(@Query("course") course: String? = null): LeagueResponse

    @GET("api/courses/{slug}")
    suspend fun course(@Path("slug") slug: String): CourseDetailResponse

    /* ---- learning (login required; lesson addressed by numeric id) ---- */

    @POST("api/courses/{slug}/enroll")
    suspend fun enroll(@Path("slug") slug: String): SimpleOkResponse

    @POST("api/courses/{slug}/purchase")
    suspend fun purchase(@Path("slug") slug: String): SimpleOkResponse

    @GET("api/courses/{slug}/lessons/{lessonId}")
    suspend fun lesson(
        @Path("slug") slug: String,
        @Path("lessonId") lessonId: Long,
    ): LessonContentResponse

    @POST("api/courses/{slug}/lessons/{lessonId}/complete")
    suspend fun complete(
        @Path("slug") slug: String,
        @Path("lessonId") lessonId: Long,
    ): CompleteResponse

    /* ---- mobile service (https://pardava.ir/api/mobile) — version sync,
     *      content aggregate, articles and the Google web-bridge exchange ---- */

    @GET("api/mobile/version")
    suspend fun mobileVersion(): VersionResponse

    @GET("api/mobile/home")
    suspend fun mobileHome(): HomeResponse

    @GET("api/mobile/services")
    suspend fun mobileServices(): ServicesResponse

    @GET("api/mobile/articles")
    suspend fun mobileArticles(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): ArticlesResponse

    @GET("api/mobile/articles/{key}")
    suspend fun mobileArticle(@Path("key") key: String): ArticleDetailResponse

    @POST("api/mobile/auth/exchange")
    suspend fun exchangeCode(@Body body: ExchangeCodeIn): ExchangeResponse

    @POST("api/mobile/auth/cancel")
    suspend fun cancelAuth(@Body body: CancelAuthIn): SimpleOkResponse
}

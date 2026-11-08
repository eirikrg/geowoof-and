package com.geowoof.core.network

import com.geowoof.core.network.model.ChangePasswordBody
import com.geowoof.core.network.model.DeleteAccountBody
import com.geowoof.core.network.model.EmailBody
import com.geowoof.core.network.model.GoogleSignInBody
import com.geowoof.core.network.model.LoginBody
import com.geowoof.core.network.model.LogoutBody
import com.geowoof.core.network.model.NetworkAccessToken
import com.geowoof.core.network.model.NetworkAuthProviders
import com.geowoof.core.network.model.NetworkSession
import com.geowoof.core.network.model.NetworkUser
import com.geowoof.core.network.model.NetworkUsernameAvailability
import com.geowoof.core.network.model.PrivacyBody
import com.geowoof.core.network.model.RefreshBody
import com.geowoof.core.network.model.RegisterBody
import com.geowoof.core.network.model.ResetPasswordBody
import com.geowoof.core.network.model.UpdateMeBody
import com.geowoof.core.network.model.UsernameBody
import com.geowoof.core.network.model.VerifyEmailBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/** Identity endpoints. Uses the authenticated client. */
internal interface AuthApi {
    @GET("api/v1/auth/providers")
    suspend fun providers(): NetworkAuthProviders

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterBody): NetworkSession

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginBody): NetworkSession

    @POST("api/v1/auth/google")
    suspend fun signInWithGoogle(@Body body: GoogleSignInBody): NetworkSession

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutBody)

    @POST("api/v1/auth/email/resend")
    suspend fun resendEmailVerification()

    @POST("api/v1/auth/email/verify")
    suspend fun verifyEmail(@Body body: VerifyEmailBody): NetworkAccessToken

    @POST("api/v1/auth/password/forgot")
    suspend fun forgotPassword(@Body body: EmailBody)

    @POST("api/v1/auth/password/reset")
    suspend fun resetPassword(@Body body: ResetPasswordBody)

    @POST("api/v1/auth/password/change")
    suspend fun changePassword(@Body body: ChangePasswordBody): NetworkSession

    @GET("api/v1/me")
    suspend fun me(): NetworkUser

    @PATCH("api/v1/me")
    suspend fun updateMe(@Body body: UpdateMeBody): NetworkUser

    @PUT("api/v1/me/username")
    suspend fun changeUsername(@Body body: UsernameBody): NetworkUser

    @PUT("api/v1/me/privacy")
    suspend fun changePrivacy(@Body body: PrivacyBody): NetworkUser

    @HTTP(method = "DELETE", path = "api/v1/me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountBody)

    @GET("api/v1/usernames/availability")
    suspend fun usernameAvailability(@Query("username") username: String): NetworkUsernameAvailability
}

/** Session refresh, on a separate client without the authenticator (no recursion). */
internal interface RefreshApi {
    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshBody): NetworkSession
}

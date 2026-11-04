package com.geowoof.core.network

import com.geowoof.core.model.Outcome
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
import com.geowoof.core.network.model.RegisterBody
import com.geowoof.core.network.model.ResetPasswordBody
import com.geowoof.core.network.model.UpdateMeBody
import com.geowoof.core.network.model.UsernameBody
import com.geowoof.core.network.model.VerifyEmailBody
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Remote data source of the account and session (Identity module of the API). */
interface AuthNetworkDataSource {
    suspend fun providers(): Outcome<NetworkAuthProviders>

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        language: String?,
    ): Outcome<NetworkSession>

    suspend fun signIn(email: String, password: String): Outcome<NetworkSession>

    suspend fun signInWithGoogle(idToken: String, language: String?): Outcome<NetworkSession>

    suspend fun signOut(refreshToken: String?): Outcome<Unit>

    suspend fun resendEmailVerification(): Outcome<Unit>

    suspend fun verifyEmail(code: String): Outcome<NetworkAccessToken>

    suspend fun requestPasswordReset(email: String): Outcome<Unit>

    suspend fun resetPassword(email: String, code: String, newPassword: String): Outcome<Unit>

    suspend fun changePassword(currentPassword: String?, newPassword: String): Outcome<NetworkSession>

    suspend fun deleteAccount(password: String?, googleIdToken: String?): Outcome<Unit>

    suspend fun currentUser(): Outcome<NetworkUser>

    suspend fun updateProfile(displayName: String?, language: String?): Outcome<NetworkUser>

    suspend fun changeUsername(username: String): Outcome<NetworkUser>

    suspend fun usernameAvailability(username: String): Outcome<NetworkUsernameAvailability>
}

internal class RetrofitAuthNetworkDataSource @Inject constructor(private val api: AuthApi, private val json: Json) :
    AuthNetworkDataSource {
    override suspend fun providers() = apiCall(json) { api.providers() }

    override suspend fun register(email: String, password: String, displayName: String, language: String?) =
        apiCall(json) { api.register(RegisterBody(email, password, displayName, language)) }

    override suspend fun signIn(email: String, password: String) = apiCall(json) {
        api.login(LoginBody(email, password))
    }

    override suspend fun signInWithGoogle(idToken: String, language: String?) =
        apiCall(json) { api.signInWithGoogle(GoogleSignInBody(idToken, language)) }

    override suspend fun signOut(refreshToken: String?) = apiCall(json) {
        api.logout(LogoutBody(refreshToken = refreshToken))
    }

    override suspend fun resendEmailVerification() = apiCall(json) { api.resendEmailVerification() }

    override suspend fun verifyEmail(code: String) = apiCall(json) { api.verifyEmail(VerifyEmailBody(code)) }

    override suspend fun requestPasswordReset(email: String) = apiCall(json) { api.forgotPassword(EmailBody(email)) }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        apiCall(json) { api.resetPassword(ResetPasswordBody(email, code, newPassword)) }

    override suspend fun changePassword(currentPassword: String?, newPassword: String) =
        apiCall(json) { api.changePassword(ChangePasswordBody(currentPassword, newPassword)) }

    override suspend fun deleteAccount(password: String?, googleIdToken: String?) =
        apiCall(json) { api.deleteAccount(DeleteAccountBody(password, googleIdToken)) }

    override suspend fun currentUser() = apiCall(json) { api.me() }

    override suspend fun updateProfile(displayName: String?, language: String?) =
        apiCall(json) { api.updateMe(UpdateMeBody(displayName, language)) }

    override suspend fun changeUsername(username: String) = apiCall(json) { api.changeUsername(UsernameBody(username)) }

    override suspend fun usernameAvailability(username: String) = apiCall(json) { api.usernameAvailability(username) }
}

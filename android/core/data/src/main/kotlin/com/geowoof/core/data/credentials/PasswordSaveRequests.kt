package com.geowoof.core.data.credentials

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Email and password to offer to the user's password manager (Credential Manager) after a
 * successful sign-in, sign-up or password change. The activity performs the save, because the
 * form that produced them is gone as soon as the session changes.
 */
@Singleton
class PasswordSaveRequests @Inject constructor() {
    private val requests = Channel<PasswordCredential>(Channel.CONFLATED)

    val pending: Flow<PasswordCredential> = requests.receiveAsFlow()

    fun request(email: String, password: String) {
        requests.trySend(PasswordCredential(email.trim(), password))
    }
}

data class PasswordCredential(val email: String, val password: String) {
    /** Never prints the password. */
    override fun toString() = "PasswordCredential(email=$email)"
}

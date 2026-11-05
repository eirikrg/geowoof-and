package com.geowoof.core.data.repository

import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.mapper.toStored
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.User
import com.geowoof.core.model.UsernameAvailability
import com.geowoof.core.model.map
import com.geowoof.core.network.AuthNetworkDataSource
import com.geowoof.core.network.model.NetworkUser
import javax.inject.Inject

/** The signed-in user's public identity: @username and display name. */
interface AccountRepository {
    suspend fun usernameAvailability(username: String): Outcome<UsernameAvailability>

    /** The last step of creating an account, where the person also chooses who can see their profile. */
    suspend fun chooseUsername(username: String, isPrivate: Boolean = false): Outcome<User>

    /** Becoming public accepts the people waiting to follow, on the server. */
    suspend fun setPrivate(isPrivate: Boolean): Outcome<User>

    suspend fun updateDisplayName(displayName: String): Outcome<User>
}

internal class DefaultAccountRepository @Inject constructor(
    private val network: AuthNetworkDataSource,
    private val sessions: SessionManager,
) : AccountRepository {
    override suspend fun usernameAvailability(username: String) =
        network.usernameAvailability(username).map { it.toModel() }

    override suspend fun chooseUsername(username: String, isPrivate: Boolean) =
        cache(network.changeUsername(username, isPrivate))

    override suspend fun setPrivate(isPrivate: Boolean) = cache(network.changePrivacy(isPrivate))

    override suspend fun updateDisplayName(displayName: String) =
        cache(network.updateProfile(displayName.trim(), language = null))

    private suspend fun cache(outcome: Outcome<NetworkUser>): Outcome<User> = when (outcome) {
        is Outcome.Success -> {
            val user = outcome.value.toStored()
            sessions.updateUser(user)
            Outcome.Success(user.toModel())
        }

        is Outcome.Failure -> outcome
    }
}

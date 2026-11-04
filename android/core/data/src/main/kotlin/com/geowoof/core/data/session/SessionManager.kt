package com.geowoof.core.data.session

import com.geowoof.core.common.ApplicationScope
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.mapper.toStored
import com.geowoof.core.datastore.SessionStorage
import com.geowoof.core.datastore.StoredSession
import com.geowoof.core.datastore.StoredUser
import com.geowoof.core.model.SessionState
import com.geowoof.core.network.auth.SessionTokenProvider
import com.geowoof.core.network.model.NetworkSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of the session. Keeps it in memory (the HTTP interceptor reads the access token
 * synchronously) and persists every change to the encrypted [SessionStorage].
 */
@Singleton
class SessionManager @Inject constructor(
    private val storage: SessionStorage,
    private val userData: UserDataCleaner,
    @ApplicationScope scope: CoroutineScope,
) : SessionTokenProvider {
    private val current = MutableStateFlow<Snapshot>(Snapshot.Loading)

    val sessionState: StateFlow<SessionState> = current
        .map { snapshot ->
            when (snapshot) {
                Snapshot.Loading -> SessionState.Loading

                is Snapshot.Loaded -> snapshot.session?.let { SessionState.SignedIn(it.user.toModel()) }
                    ?: SessionState.SignedOut
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    init {
        scope.launch {
            val stored = storage.read()
            // A sign-in that finished before the file was read wins.
            current.compareAndSet(Snapshot.Loading, Snapshot.Loaded(stored))
        }
    }

    val storedSession: StoredSession? get() = (current.value as? Snapshot.Loaded)?.session

    override fun accessToken(): String? = storedSession?.accessToken

    override fun refreshToken(): String? = storedSession?.refreshToken

    override suspend fun onTokensRefreshed(session: NetworkSession) = start(session)

    override suspend fun onSessionExpired() = clear()

    suspend fun start(session: NetworkSession) = save(session.toStored())

    suspend fun updateUser(user: StoredUser) {
        val session = storedSession ?: return
        save(session.copy(user = user))
    }

    suspend fun updateAccessToken(accessToken: String, expiresAtEpochMs: Long, user: StoredUser) {
        val session = storedSession ?: return
        save(session.copy(accessToken = accessToken, accessTokenExpiresAtEpochMs = expiresAtEpochMs, user = user))
    }

    suspend fun clear() {
        save(null)
        userData.clear()
    }

    private suspend fun save(session: StoredSession?) {
        current.update { Snapshot.Loaded(session) }
        storage.write(session)
    }

    private sealed interface Snapshot {
        data object Loading : Snapshot

        data class Loaded(val session: StoredSession?) : Snapshot
    }
}

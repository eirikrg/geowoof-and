package com.geowoof.core.data.repository

import com.geowoof.core.data.mapper.toBody
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.database.ProfileDao
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileDraft
import com.geowoof.core.model.SessionState
import com.geowoof.core.network.ProfileNetworkDataSource
import com.geowoof.core.network.model.NetworkProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The profile of the signed-in user: read from Room, written online. */
interface ProfileRepository {
    /** `null` until it has been downloaded for the first time. */
    val myProfile: Flow<Profile?>

    suspend fun refresh(): Outcome<Unit>

    suspend fun save(draft: ProfileDraft): Outcome<Profile>
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultProfileRepository @Inject constructor(
    private val network: ProfileNetworkDataSource,
    private val dao: ProfileDao,
    private val sessions: SessionManager,
    private val changes: ContentChanges,
) : ProfileRepository {
    override val myProfile: Flow<Profile?> = sessions.sessionState
        .map { (it as? SessionState.SignedIn)?.user?.id }
        .distinctUntilChanged()
        .flatMapLatest { userId -> if (userId == null) flowOf(null) else dao.observe(userId).map { it?.toModel() } }

    override suspend fun refresh(): Outcome<Unit> = when (val outcome = network.myProfile()) {
        is Outcome.Success -> {
            dao.upsert(outcome.value.toEntity())
            Outcome.Success(Unit)
        }

        is Outcome.Failure -> outcome
    }

    override suspend fun save(draft: ProfileDraft): Outcome<Profile> =
        when (val outcome = network.replaceMyProfile(draft.toBody())) {
            is Outcome.Success -> Outcome.Success(store(outcome.value)).also { changes.notify(ContentChange.Profile) }
            is Outcome.Failure -> outcome
        }

    private suspend fun store(profile: NetworkProfile): Profile {
        val entity = profile.toEntity()
        dao.upsert(entity)

        // The account screens read the name from the session.
        sessions.storedSession?.user?.let { sessions.updateUser(it.copy(displayName = profile.displayName)) }
        return entity.toModel()
    }
}

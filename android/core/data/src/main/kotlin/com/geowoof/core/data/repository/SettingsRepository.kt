package com.geowoof.core.data.repository

import com.geowoof.core.database.UserSettingsDao
import com.geowoof.core.database.UserSettingsEntity
import com.geowoof.core.datastore.PromptsStorage
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSettings
import com.geowoof.core.model.map
import com.geowoof.core.model.toEnumOrNull
import com.geowoof.core.network.SettingsNetworkDataSource
import com.geowoof.core.network.model.NetworkSettings
import com.geowoof.core.network.model.SettingsBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Read from Room, because a walk needs them without a connection, and written online. */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun current(): UserSettings

    suspend fun refresh(): Outcome<Unit>

    suspend fun setLiveVisibility(visibility: LiveVisibility): Outcome<UserSettings>

    suspend fun setHideStartDistance(meters: Int): Outcome<UserSettings>

    /** This device already asked whether the map can open where the person is. It stays after signing out. */
    suspend fun wasAskedForLocation(): Boolean

    suspend fun markAskedForLocation()
}

internal class DefaultSettingsRepository @Inject constructor(
    private val network: SettingsNetworkDataSource,
    private val dao: UserSettingsDao,
    private val prompts: PromptsStorage,
) : SettingsRepository {
    override val settings: Flow<UserSettings> =
        dao.observe().map { it?.toModel() ?: UserSettings() }.distinctUntilChanged()

    override suspend fun current(): UserSettings = settings.first()

    override suspend fun refresh(): Outcome<Unit> = network.settings().map { store(it) }

    override suspend fun setLiveVisibility(visibility: LiveVisibility): Outcome<UserSettings> =
        network.updateSettings(SettingsBody(liveVisibility = visibility.name)).map { store(it) }

    override suspend fun setHideStartDistance(meters: Int): Outcome<UserSettings> =
        network.updateSettings(SettingsBody(hideStartDistanceM = meters)).map { store(it) }

    override suspend fun wasAskedForLocation(): Boolean = prompts.prompts.first().askedForLocation

    override suspend fun markAskedForLocation() = prompts.update { it.copy(askedForLocation = true) }

    private suspend fun store(settings: NetworkSettings): UserSettings {
        val entity = UserSettingsEntity(
            liveVisibility = settings.liveVisibility,
            hideStartDistanceM = settings.hideStartDistanceM,
        )
        dao.upsert(entity)
        return entity.toModel()
    }

    private fun UserSettingsEntity.toModel() = UserSettings(
        liveVisibility = liveVisibility.toEnumOrNull<LiveVisibility>(),
        hideStartDistanceM = hideStartDistanceM,
    )
}

package com.geowoof.core.testing

import com.geowoof.core.data.repository.LiveRepository
import com.geowoof.core.data.repository.SettingsRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LiveNearby
import com.geowoof.core.model.LivePet
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.LiveWalker
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.MapViewport
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

object TestWalkers {
    private const val RETIRO_LATITUDE = 40.4153
    private const val RETIRO_LONGITUDE = -3.6844

    fun walker(
        userId: String = "bea-id",
        username: String = "bea",
        displayName: String = "Bea",
        position: GeoPoint = GeoPoint(RETIRO_LATITUDE, RETIRO_LONGITUDE),
        pets: List<LivePet> = listOf(LivePet("mishi-id", "Mishi", null)),
    ) = LiveWalker(userId, username, displayName, null, pets, position, Instant.parse("2026-11-05T10:00:00Z"))
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    var failure: AppError? = null

    /** What the server has when the app asks. */
    var refreshed: UserSettings? = null
    var refreshes = 0
        private set

    var askedForLocation = false

    override suspend fun wasAskedForLocation() = askedForLocation

    override suspend fun markAskedForLocation() {
        askedForLocation = true
    }

    override suspend fun current(): UserSettings = settings.value

    override suspend fun refresh(): Outcome<Unit> {
        refreshes++
        failure?.let { return Outcome.Failure(it) }
        refreshed?.let { settings.value = it }
        return Outcome.Success(Unit)
    }

    override suspend fun setLiveVisibility(visibility: LiveVisibility): Outcome<UserSettings> {
        failure?.let { return Outcome.Failure(it) }
        settings.update { it.copy(liveVisibility = visibility) }
        return Outcome.Success(settings.value)
    }

    override suspend fun setHideStartDistance(meters: Int): Outcome<UserSettings> {
        failure?.let { return Outcome.Failure(it) }
        settings.update { it.copy(hideStartDistanceM = meters) }
        return Outcome.Success(settings.value)
    }
}

/** What the map receives is decided by the test, and what is shared stays written in [calls]. */
class FakeLiveRepository : LiveRepository {
    val nearby = MutableStateFlow(LiveNearby())

    /** The areas that have been asked for, in order. */
    val viewports = mutableListOf<MapViewport>()

    /** "start:walk:pets", "position:latitude" and "stop", in order. */
    val calls = mutableListOf<String>()

    var startResult: Outcome<LiveVisibility> = Outcome.Success(LiveVisibility.FriendsOnly)
    var positionResult: Outcome<Unit> = Outcome.Success(Unit)

    /** How many are keeping the connection open now. */
    var connectionUsers = 0
        private set

    override fun nearby(viewports: Flow<MapViewport>): Flow<LiveNearby> = channelFlow {
        whileConnected {
            launch { viewports.collect { this@FakeLiveRepository.viewports += it } }
            nearby.collect { send(it) }
        }
    }

    override suspend fun <T> whileConnected(block: suspend () -> T): T {
        connectionUsers++
        try {
            return block()
        } finally {
            connectionUsers--
        }
    }

    override suspend fun startSharing(walkId: String, petIds: List<String>): Outcome<LiveVisibility> {
        calls += "start:$walkId:${petIds.joinToString(",")}"
        return startResult
    }

    override suspend fun updatePosition(fix: LocationFix): Outcome<Unit> {
        calls += "position:${fix.position.latitude}"
        return positionResult
    }

    override suspend fun stopSharing(): Outcome<Unit> {
        calls += "stop"
        return Outcome.Success(Unit)
    }
}

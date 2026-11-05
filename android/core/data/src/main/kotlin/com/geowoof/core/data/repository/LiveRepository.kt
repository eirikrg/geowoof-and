package com.geowoof.core.data.repository

import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LiveNearby
import com.geowoof.core.model.LivePet
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.LiveWalker
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.MapViewport
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.map
import com.geowoof.core.model.onSuccess
import com.geowoof.core.network.LiveNetworkDataSource
import com.geowoof.core.network.model.NetworkLiveEvent
import com.geowoof.core.network.model.NetworkLiveWalker
import com.geowoof.core.network.realtime.RealtimeState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Nothing of this is stored: it is only true while it arrives (docs/decisions/0007). */
interface LiveRepository {
    /** While it is collected, the connection stays open and follows [viewports]. */
    fun nearby(viewports: Flow<MapViewport>): Flow<LiveNearby>

    /** The connection stays open at least as long as [block] takes. */
    suspend fun <T> whileConnected(block: suspend () -> T): T

    /** @return the visibility that the server is applying. */
    suspend fun startSharing(walkId: String, petIds: List<String>): Outcome<LiveVisibility>

    suspend fun updatePosition(fix: LocationFix): Outcome<Unit>

    suspend fun stopSharing(): Outcome<Unit>
}

internal class DefaultLiveRepository @Inject constructor(
    private val network: LiveNetworkDataSource,
    private val clock: Clock,
) : LiveRepository {
    override fun nearby(viewports: Flow<MapViewport>): Flow<LiveNearby> = channelFlow {
        network.whileOpen {
            val map = MutableStateFlow(LiveMap())
            launch(start = CoroutineStart.UNDISPATCHED) {
                network.events.collect { event -> map.update { it.applying(event, clock.millis()) } }
            }
            launch {
                while (true) {
                    delay(EXPIRY_CHECK_MS)
                    map.update { it.withoutExpired(clock.millis()) }
                }
            }
            launch {
                // Also after a reconnection: a new connection on the server listens to nothing.
                combine(network.state, viewports) { state, viewport -> state to viewport }
                    .distinctUntilChanged()
                    .collectLatest { (state, viewport) ->
                        map.update { it.copy(viewport = viewport) }
                        if (state == RealtimeState.Connected) subscribe(viewport, map)
                    }
            }
            map.map { it.toNearby() }
                .distinctUntilChanged()
                .collect { send(it) }
        }
    }

    private suspend fun subscribe(viewport: MapViewport, map: MutableStateFlow<LiveMap>) {
        val answer = network.subscribeViewport(viewport.north, viewport.south, viewport.east, viewport.west)
        answer.onSuccess { found ->
            val now = clock.millis()
            map.update { current ->
                current.copy(
                    isTooWide = found.tooWide,
                    walkers = found.walkers.associate { it.userId to TrackedWalker(it.toModel(), now) },
                )
            }
        }
    }

    override suspend fun <T> whileConnected(block: suspend () -> T): T = network.whileOpen(block)

    override suspend fun startSharing(walkId: String, petIds: List<String>): Outcome<LiveVisibility> =
        network.startSharing(walkId, petIds).map { visibility -> LiveVisibility.valueOf(visibility) }

    override suspend fun updatePosition(fix: LocationFix): Outcome<Unit> = network.updatePosition(
        latitude = fix.position.latitude,
        longitude = fix.position.longitude,
        accuracyM = fix.accuracyM,
        recordedAt = Instant.ofEpochMilli(fix.timeEpochMs),
    )

    override suspend fun stopSharing(): Outcome<Unit> = network.stopSharing()

    private companion object {
        const val EXPIRY_CHECK_MS = 5_000L
    }
}

/** It keeps when each one arrived by the clock of the phone, because that of the server may not agree with it. */
private data class TrackedWalker(val walker: LiveWalker, val receivedAtMs: Long)

/** What is known of the map at a moment. A cell is larger than the screen, so more arrives than is shown. */
private data class LiveMap(
    val viewport: MapViewport? = null,
    val isTooWide: Boolean = false,
    val walkers: Map<String, TrackedWalker> = emptyMap(),
) {
    fun applying(event: NetworkLiveEvent, nowMs: Long): LiveMap = when (event) {
        is NetworkLiveEvent.WalkerUpdated ->
            copy(walkers = walkers + (event.walker.userId to TrackedWalker(event.walker.toModel(), nowMs)))

        is NetworkLiveEvent.WalkerLeft -> copy(walkers = walkers - event.userId)
    }

    fun withoutExpired(nowMs: Long): LiveMap =
        copy(walkers = walkers.filterValues { nowMs - it.receivedAtMs < POSITION_LIFETIME_MS })

    fun toNearby() = LiveNearby(
        walkers = walkers.values
            .map { it.walker }
            .filter { viewport?.contains(it.position) == true }
            .sortedBy { it.displayName },
        isTooWide = isTooWide,
    )

    private companion object {
        /** The same as the server: a walker who stops sending disappears even if nobody says so. */
        const val POSITION_LIFETIME_MS = 30_000L
    }
}

private fun NetworkLiveWalker.toModel() = LiveWalker(
    userId = userId,
    username = username,
    displayName = displayName,
    avatar = avatar?.toModel(),
    pets = pets.map { LivePet(it.id, it.name, it.avatar?.toModel()) },
    position = GeoPoint(latitude, longitude),
    updatedAt = Instant.parse(updatedAt),
)

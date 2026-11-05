package com.geowoof.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.database.GeoWoofDatabase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LiveNearby
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.MapViewport
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSettings
import com.geowoof.core.network.LiveNetworkDataSource
import com.geowoof.core.network.SettingsNetworkDataSource
import com.geowoof.core.network.model.NetworkLiveEvent
import com.geowoof.core.network.model.NetworkLiveWalker
import com.geowoof.core.network.model.NetworkSettings
import com.geowoof.core.network.model.NetworkViewport
import com.geowoof.core.network.model.SettingsBody
import com.geowoof.core.network.realtime.RealtimeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private fun walker(userId: String = "bea-id", latitude: Double = 40.4153, displayName: String = "Bea") =
    NetworkLiveWalker(
        userId = userId,
        username = userId,
        displayName = displayName,
        latitude = latitude,
        longitude = -3.6844,
        updatedAt = "2026-11-05T10:00:00Z",
    )

private class FakeLiveNetwork : LiveNetworkDataSource {
    override val state = MutableStateFlow(RealtimeState.Connected)
    override val events = MutableSharedFlow<NetworkLiveEvent>(extraBufferCapacity = 16)

    var users = 0
    var answer: Outcome<NetworkViewport> = Outcome.Success(NetworkViewport(tooWide = false))
    val subscriptions = mutableListOf<Double>()
    val calls = mutableListOf<String>()

    override suspend fun <T> whileOpen(block: suspend () -> T): T {
        users++
        try {
            return block()
        } finally {
            users--
        }
    }

    override suspend fun subscribeViewport(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ): Outcome<NetworkViewport> {
        subscriptions += north
        return answer
    }

    override suspend fun startSharing(walkId: String, petIds: List<String>): Outcome<String> {
        calls += "start:$walkId:$petIds"
        return Outcome.Success("Everyone")
    }

    override suspend fun updatePosition(
        latitude: Double,
        longitude: Double,
        accuracyM: Float,
        recordedAt: Instant,
    ): Outcome<Unit> {
        calls += "position:$latitude:$longitude:$accuracyM:$recordedAt"
        return Outcome.Success(Unit)
    }

    override suspend fun stopSharing(): Outcome<Unit> {
        calls += "stop"
        return Outcome.Success(Unit)
    }
}

class LiveRepositoryTest {
    private val network = FakeLiveNetwork()
    private val viewports = MutableStateFlow(Madrid)
    private var nowMs = 0L
    private val clock = object : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val repository = DefaultLiveRepository(network, clock)
    private val seen = mutableListOf<LiveNearby>()

    private val latest get() = seen.last()

    private fun TestScope.collect() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        repository.nearby(viewports).collect { seen += it }
    }

    @Test
    fun it_shows_who_is_there_when_it_starts_looking() = runTest {
        network.answer = Outcome.Success(NetworkViewport(tooWide = false, walkers = listOf(walker())))

        collect()
        runCurrent()

        assertEquals(listOf("bea-id"), latest.walkers.map { it.userId })
        assertEquals(GeoPoint(40.4153, -3.6844), latest.walkers.single().position)
    }

    @Test
    fun the_connection_stays_open_only_while_someone_looks() = runTest {
        val looking = collect()
        runCurrent()
        assertEquals(1, network.users)

        looking.cancel()
        runCurrent()

        assertEquals(0, network.users)
    }

    @Test
    fun a_position_moves_them_and_someone_who_leaves_disappears() = runTest {
        collect()
        runCurrent()

        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker(latitude = 40.41)))
        runCurrent()
        assertEquals(40.41, latest.walkers.single().position.latitude, 0.0)

        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker(latitude = 40.42)))
        runCurrent()
        assertEquals(40.42, latest.walkers.single().position.latitude, 0.0)

        network.events.emit(NetworkLiveEvent.WalkerLeft("bea-id"))
        runCurrent()
        assertTrue(latest.walkers.isEmpty())
    }

    @Test
    fun what_arrives_from_outside_the_screen_is_not_shown() = runTest {
        collect()
        runCurrent()

        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker(latitude = 41.5)))
        runCurrent()
        assertTrue(latest.walkers.isEmpty())

        viewports.value = MapViewport(north = 42.0, south = 41.0, east = -3.0, west = -4.0)
        runCurrent()
        assertTrue(network.subscriptions.contains(42.0))
    }

    @Test
    fun someone_who_stops_sending_disappears_even_if_nobody_says_so() = runTest {
        collect()
        runCurrent()
        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker()))
        runCurrent()

        nowMs = 29_000
        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(1, latest.walkers.size)

        nowMs = 31_000
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(latest.walkers.isEmpty())
    }

    @Test
    fun they_are_shown_in_a_stable_order() = runTest {
        collect()
        runCurrent()

        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker("c", displayName = "Carlos")))
        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker("a", displayName = "Ana")))
        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker("c", displayName = "Carlos")))
        runCurrent()

        assertEquals(listOf("Ana", "Carlos"), latest.walkers.map { it.displayName })
    }

    @Test
    fun an_area_too_wide_shows_nobody_and_says_so() = runTest {
        network.answer = Outcome.Success(NetworkViewport(tooWide = false, walkers = listOf(walker())))
        collect()
        runCurrent()
        assertEquals(1, latest.walkers.size)

        network.answer = Outcome.Success(NetworkViewport(tooWide = true))
        viewports.value = Madrid.copy(north = 41.0)
        runCurrent()

        assertTrue(latest.isTooWide)
        assertTrue(latest.walkers.isEmpty())
    }

    @Test
    fun after_a_reconnection_it_asks_again_for_the_same_area() = runTest {
        collect()
        runCurrent()
        assertEquals(1, network.subscriptions.size)

        network.state.value = RealtimeState.Connecting
        runCurrent()
        assertEquals(1, network.subscriptions.size)

        network.answer = Outcome.Success(NetworkViewport(tooWide = false, walkers = listOf(walker())))
        network.state.value = RealtimeState.Connected
        runCurrent()

        assertEquals(listOf(Madrid.north, Madrid.north), network.subscriptions)
        assertEquals(1, latest.walkers.size)
    }

    @Test
    fun while_it_cannot_connect_it_does_not_ask_and_what_it_had_stays_until_it_expires() = runTest {
        collect()
        runCurrent()
        network.events.emit(NetworkLiveEvent.WalkerUpdated(walker()))
        runCurrent()

        network.state.value = RealtimeState.Connecting
        viewports.value = Madrid.copy(north = 40.5)
        runCurrent()

        assertEquals(1, network.subscriptions.size)
        assertEquals(1, latest.walkers.size)
    }

    @Test
    fun a_failed_look_keeps_what_it_had() = runTest {
        network.answer = Outcome.Success(NetworkViewport(tooWide = false, walkers = listOf(walker())))
        collect()
        runCurrent()

        network.answer = Outcome.Failure(AppError.Network)
        viewports.value = Madrid.copy(north = 40.5)
        runCurrent()

        assertEquals(1, latest.walkers.size)
        assertFalse(latest.isTooWide)
    }

    @Test
    fun a_position_goes_with_the_moment_it_was_recorded() = runTest {
        repository.updatePosition(LocationFix(GeoPoint(40.4, -3.6), 8f, 1_000))

        assertEquals("position:40.4:-3.6:8.0:1970-01-01T00:00:01Z", network.calls.single())
    }

    @Test
    fun starting_answers_the_visibility_being_applied() = runTest {
        assertEquals(Outcome.Success(LiveVisibility.Everyone), repository.startSharing("walk", listOf("luna")))
    }

    private companion object {
        val Madrid = MapViewport(north = 40.43, south = 40.40, east = -3.66, west = -3.70)
    }
}

private class FakeSettingsNetwork : SettingsNetworkDataSource {
    var stored = NetworkSettings(liveVisibility = null, hideStartDistanceM = 200)
    var failure: AppError? = null
    val bodies = mutableListOf<SettingsBody>()

    override suspend fun settings(): Outcome<NetworkSettings> =
        failure?.let { Outcome.Failure(it) } ?: Outcome.Success(stored)

    override suspend fun updateSettings(body: SettingsBody): Outcome<NetworkSettings> {
        bodies += body
        failure?.let { return Outcome.Failure(it) }
        stored =
            NetworkSettings(
                body.liveVisibility ?: stored.liveVisibility,
                body.hideStartDistanceM ?: stored.hideStartDistanceM,
            )
        return Outcome.Success(stored)
    }
}

/** Against a real Room database: a walk reads them from there, without a connection. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private lateinit var database: GeoWoofDatabase
    private val network = FakeSettingsNetwork()
    private val repository by lazy { DefaultSettingsRepository(network, database.userSettingsDao()) }

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                GeoWoofDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun without_having_asked_the_server_nothing_has_been_chosen() = runTest {
        assertEquals(UserSettings(liveVisibility = null, hideStartDistanceM = 200), repository.current())
    }

    @Test
    fun refreshing_keeps_what_the_server_says() = runTest {
        network.stored = NetworkSettings("Everyone", 500)

        repository.refresh()

        assertEquals(UserSettings(LiveVisibility.Everyone, 500), repository.settings.first())
    }

    @Test
    fun choosing_only_sends_what_changes() = runTest {
        repository.setLiveVisibility(LiveVisibility.Nobody)
        repository.setHideStartDistance(0)

        assertEquals(
            listOf(SettingsBody(liveVisibility = "Nobody"), SettingsBody(hideStartDistanceM = 0)),
            network.bodies,
        )
        assertEquals(UserSettings(LiveVisibility.Nobody, 0), repository.current())
    }

    @Test
    fun if_it_cannot_be_saved_nothing_changes_here() = runTest {
        network.stored = NetworkSettings("FriendsOnly", 200)
        repository.refresh()
        network.failure = AppError.Network

        val answer = repository.setLiveVisibility(LiveVisibility.Everyone)

        assertEquals(Outcome.Failure(AppError.Network), answer)
        assertEquals(LiveVisibility.FriendsOnly, repository.current().liveVisibility)
    }

    @Test
    fun a_visibility_it_does_not_know_counts_as_not_chosen_so_nothing_is_shared() = runTest {
        network.stored = NetworkSettings("SomethingNew", 200)

        repository.refresh()

        assertEquals(null, repository.current().liveVisibility)
    }
}

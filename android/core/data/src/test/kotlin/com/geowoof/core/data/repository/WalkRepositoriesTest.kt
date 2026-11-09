package com.geowoof.core.data.repository

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.common.geo.PolylineCodec
import com.geowoof.core.data.mapper.toBundle
import com.geowoof.core.data.media.WalkPhotoStore
import com.geowoof.core.database.GeoWoofDatabase
import com.geowoof.core.database.WalkWithChildren
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.AppError
import com.geowoof.core.model.EncodedSegment
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.NewWalk
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.network.WalksNetworkDataSource
import com.geowoof.core.network.model.NetworkImage
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkWalk
import com.geowoof.core.network.model.NetworkWalkSegment
import com.geowoof.core.network.model.NetworkWalkStats
import com.geowoof.core.network.model.WalkBody
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakeWalkSyncScheduler
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** The walks against a real Room database, because it is what survives the death of the process. */
@RunWith(AndroidJUnit4::class)
class WalkRepositoriesTest {
    private lateinit var database: GeoWoofDatabase
    private val network = FakeWalksNetwork()
    private val media = FakeMediaRepository()
    private val photos = FakePhotoStore()
    private val scheduler = FakeWalkSyncScheduler()
    private val now = Instant.parse("2026-11-02T09:00:00Z")

    private val active get() = DefaultActiveWalkRepository(database.activeWalkDao(), Clock.fixed(now, ZoneOffset.UTC))
    private val synchronizer get() = DefaultWalkSynchronizer(database.walkDao(), network, media, photos)

    private fun walks() = DefaultWalksRepository(
        database.walkDao(),
        network,
        photos,
        scheduler,
        StandardTestDispatcher(),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            GeoWoofDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun newWalk(id: String = "walk", photoUris: List<String> = emptyList()) = NewWalk(
        id = id,
        startedAt = TestWalks.START,
        endedAt = TestWalks.START.plusSeconds(600),
        timeZone = "Europe/Madrid",
        petIds = listOf("luna"),
        visibility = WalkVisibility.Friends,
        note = "Por el Retiro",
        stats = TestWalks.stats(distanceM = 111, durationS = 600, movingTimeS = 80),
        segments = listOf(
            EncodedSegment(PolylineCodec.encode(listOf(GeoPoint(40.0, -3.0), GeoPoint(40.1, -3.0))), listOf(0, 4000)),
        ),
        photoUris = photoUris,
        updatedAt = TestWalks.START.plusSeconds(660),
    )

    private fun point(offsetMs: Long, latitude: Double = 40.4) = RoutePoint(GeoPoint(latitude, -3.7), offsetMs)

    // ------------------------------------------------------------ the active walk

    @Test
    fun a_walk_starts_waiting_for_the_first_position() = runTest {
        val walk = active.start(listOf("luna", "toby"))

        assertEquals(ActiveWalkStatus.Preparing, walk.status)
        assertEquals(listOf("luna", "toby"), walk.petIds)
        assertEquals(now, walk.startedAt)
        assertEquals(7, UUID.fromString(walk.id).version())
    }

    @Test
    fun a_point_is_only_written_while_recording() = runTest {
        val walk = active.start(listOf("luna"))

        active.appendPoint(walk.id, 0, point(0), 5f)
        assertEquals(0, active.current()?.pointCount)

        active.beginRecording(now.toEpochMilli())
        active.appendPoint(walk.id, 0, point(0), 5f)
        assertEquals(1, active.current()?.pointCount)

        active.pause()
        active.appendPoint(walk.id, 0, point(4000), 5f)
        assertEquals(1, active.current()?.pointCount)
    }

    @Test
    fun resuming_opens_a_new_segment() = runTest {
        val walk = active.start(listOf("luna"))
        active.beginRecording(now.toEpochMilli())
        active.appendPoint(walk.id, 0, point(0), 5f)
        active.appendPoint(walk.id, 0, point(4000), 5f)
        active.pause()

        active.resume()
        active.appendPoint(walk.id, 1, point(600_000, latitude = 41.0), 5f)

        val resumed = checkNotNull(active.current())
        assertEquals(ActiveWalkStatus.Recording, resumed.status)
        assertEquals(1, resumed.currentSegment)
        assertEquals(listOf(2, 1), resumed.segments.map { it.points.size })
    }

    @Test
    fun an_interrupted_walk_without_points_waits_for_a_position_again() = runTest {
        active.start(listOf("luna"))

        active.interrupt()
        assertEquals(ActiveWalkStatus.Interrupted, active.current()?.status)

        active.resume()
        assertEquals(ActiveWalkStatus.Preparing, active.current()?.status)
    }

    @Test
    fun the_route_always_fits_inside_the_walk() = runTest {
        val walk = active.start(listOf("luna"))
        active.beginRecording(now.toEpochMilli())

        // The clock of the GPS is ahead of the clock of the phone.
        active.appendPoint(walk.id, 0, point(30_000), 5f)
        active.finish()

        val finished = checkNotNull(active.current())
        assertEquals(ActiveWalkStatus.Finished, finished.status)
        assertEquals(now.plusSeconds(30), finished.endedAt)
    }

    @Test
    fun a_walk_forgotten_for_days_ends_within_the_limit_of_the_server() = runTest {
        val longAgo = now.minusSeconds(100_000)
        active.start(listOf("luna"))
        active.beginRecording(longAgo.toEpochMilli())

        active.finish()

        assertEquals(longAgo.plusSeconds(12 * 3600), active.current()?.endedAt)
    }

    @Test
    fun the_walk_survives_a_new_instance_of_the_repository() = runTest {
        val walk = active.start(listOf("luna"))
        active.beginRecording(now.toEpochMilli())
        active.appendPoint(walk.id, 0, point(0), 5f)

        val restored = active.activeWalk.first()

        assertEquals(walk.id, restored?.id)
        assertEquals(1, restored?.pointCount)
    }

    // ------------------------------------------------------------ saving

    @Test
    fun saving_ends_the_active_walk_and_asks_for_the_upload() = runTest {
        active.start(listOf("luna"))

        val result = walks().savePending(newWalk(photoUris = listOf("content://photo")))

        assertEquals(Outcome.Success("walk"), result)
        assertNull(active.current())
        assertEquals(listOf("walk"), scheduler.enqueued)
        val saved = checkNotNull(walks().observeWalk("walk").first())
        assertEquals(WalkSyncStatus.PendingSync, saved.syncStatus)
        assertEquals("/files/walk/0.jpg", saved.photos.single().localPath)
        assertTrue(walks().hasUnsyncedWalks())
    }

    @Test
    fun a_photo_that_cannot_be_kept_leaves_the_walk_as_it_was() = runTest {
        active.start(listOf("luna"))
        photos.failure = AppError.Unexpected("disk")

        val result = walks().savePending(newWalk(photoUris = listOf("content://photo")))

        assertTrue(result is Outcome.Failure)
        assertNotNull(active.current())
        assertEquals(emptyList<String>(), scheduler.enqueued)
    }

    @Test
    fun the_route_of_a_saved_walk_can_be_read_back() = runTest {
        walks().savePending(newWalk())

        val route = checkNotNull(database.walkDao().track("walk"))

        assertTrue(route.contains("timeOffsetsMs"))
    }

    // ------------------------------------------------------------ uploading

    @Test
    fun an_upload_sends_the_photos_first_and_stores_the_answer_of_the_server() = runTest {
        media.result = Outcome.Success("asset-1")
        walks().savePending(newWalk(photoUris = listOf("content://photo")))

        val result = synchronizer.sync("walk")

        assertEquals(WalkSyncResult.Done, result)
        assertEquals("/files/walk/0.jpg" to MediaPurpose.WalkPhoto, media.uploads.single())
        assertEquals(listOf("asset-1"), network.bodies.single().photoAssetIds)
        assertEquals(TestWalks.START.toString(), network.bodies.single().startedAt)
        val walk = checkNotNull(walks().observeWalk("walk").first())
        assertEquals(WalkSyncStatus.Synced, walk.syncStatus)
        assertEquals(999, walk.stats.distanceM)
        assertEquals("asset-1", walk.photos.single().image?.assetId)
        assertNull(walk.photos.single().localPath)
        assertEquals(listOf("walk"), photos.deleted)
        assertFalse(walks().hasUnsyncedWalks())
    }

    @Test
    fun without_a_connection_it_is_retried_later_without_uploading_the_photos_again() = runTest {
        media.result = Outcome.Success("asset-1")
        walks().savePending(newWalk(photoUris = listOf("content://photo")))
        network.putResult = Outcome.Failure(AppError.Network)

        assertEquals(WalkSyncResult.Retry, synchronizer.sync("walk"))
        assertEquals(WalkSyncStatus.PendingSync, walks().observeWalk("walk").first()?.syncStatus)

        network.putResult = null
        assertEquals(WalkSyncResult.Done, synchronizer.sync("walk"))
        assertEquals(1, media.uploads.size)
    }

    @Test
    fun a_problem_of_the_server_is_retried() = runTest {
        walks().savePending(newWalk())
        network.putResult = Outcome.Failure(AppError.Api(status = 503, code = "server.unavailable"))

        assertEquals(WalkSyncResult.Retry, synchronizer.sync("walk"))
    }

    @Test
    fun a_walk_rejected_by_the_server_waits_for_the_user() = runTest {
        walks().savePending(newWalk())
        network.putResult = Outcome.Failure(AppError.Api(status = 422, code = "walks.invalid_track"))

        assertEquals(WalkSyncResult.Failed("walks.invalid_track"), synchronizer.sync("walk"))

        val walk = checkNotNull(walks().observeWalk("walk").first())
        assertEquals(WalkSyncStatus.Failed, walk.syncStatus)
        assertEquals("walks.invalid_track", walk.syncErrorCode)
    }

    @Test
    fun a_rejected_walk_can_be_tried_again() = runTest {
        walks().savePending(newWalk())
        network.putResult = Outcome.Failure(AppError.Api(status = 422, code = "walks.invalid_track"))
        synchronizer.sync("walk")
        scheduler.enqueued.clear()
        network.putResult = null

        walks().retrySync("walk")

        assertEquals(listOf("walk"), scheduler.enqueued)
        assertEquals(WalkSyncResult.Done, synchronizer.sync("walk"))
    }

    @Test
    fun a_photo_rejected_by_the_server_stops_the_upload() = runTest {
        media.result = Outcome.Failure(AppError.Api(status = 422, code = "media.invalid_image"))
        walks().savePending(newWalk(photoUris = listOf("content://photo")))

        assertEquals(WalkSyncResult.Failed("media.invalid_image"), synchronizer.sync("walk"))
        assertEquals(emptyList<WalkBody>(), network.bodies)
    }

    @Test
    fun an_upload_of_something_that_no_longer_exists_does_nothing() = runTest {
        assertEquals(WalkSyncResult.Done, synchronizer.sync("does-not-exist"))
        assertEquals(emptyList<WalkBody>(), network.bodies)
    }

    @Test
    fun a_walk_that_the_server_already_had_does_not_leave_its_copies_on_the_device() = runTest {
        walks().savePending(newWalk(photoUris = listOf("content://photo")))
        database.walkDao().storeFromServer(listOf(networkWalk("walk").toBundle()))

        assertEquals(WalkSyncResult.Done, synchronizer.sync("walk"))

        assertEquals(emptyList<WalkBody>(), network.bodies)
        assertEquals(listOf("walk"), photos.deleted)
    }

    @Test
    fun a_deleted_walk_disappears_at_once_and_the_server_finds_out_later() = runTest {
        walks().savePending(newWalk())
        synchronizer.sync("walk")
        scheduler.enqueued.clear()

        walks().delete("walk")

        assertEquals(WalkSyncStatus.PendingDelete, walks().observeWalk("walk").first()?.syncStatus)
        assertEquals(listOf("walk"), scheduler.enqueued)
        assertEquals(WalkSyncResult.Done, synchronizer.sync("walk"))
        assertEquals(listOf("walk"), network.deleted)
        assertNull(walks().observeWalk("walk").first())
    }

    @Test
    fun deleting_something_the_server_never_received_is_not_an_error() = runTest {
        walks().savePending(newWalk())
        walks().delete("walk")
        network.deleteResult = Outcome.Failure(AppError.Api(status = 404, code = "walks.not_found"))

        assertEquals(WalkSyncResult.Done, synchronizer.sync("walk"))
        assertNull(walks().observeWalk("walk").first())
    }

    @Test
    fun deleting_without_a_connection_is_retried() = runTest {
        walks().savePending(newWalk())
        walks().delete("walk")
        network.deleteResult = Outcome.Failure(AppError.Network)

        assertEquals(WalkSyncResult.Retry, synchronizer.sync("walk"))
        assertEquals(WalkSyncStatus.PendingDelete, walks().observeWalk("walk").first()?.syncStatus)
    }

    // ------------------------------------------------------------ reading from the server

    @OptIn(ExperimentalPagingApi::class)
    private fun emptyState() = PagingState<Int, WalkWithChildren>(emptyList(), null, PagingConfig(pageSize = 2), 0)

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun the_list_is_brought_page_by_page_and_keeps_what_is_pending() = runTest {
        walks().savePending(newWalk(id = "pending"))
        val mediator = WalkRemoteMediator(null, database.walkDao(), network)
        network.pages[null] = NetworkPage(listOf(networkWalk("a"), networkWalk("b")), "next")
        network.pages["next"] = NetworkPage(listOf(networkWalk("c")), null)

        val first = mediator.load(LoadType.REFRESH, emptyState())
        assertFalse((first as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        val second = mediator.load(LoadType.APPEND, emptyState())
        assertTrue((second as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        for (id in listOf("a", "b", "c", "pending")) {
            assertNotNull(id, database.walkDao().get(id))
        }
        val third = mediator.load(LoadType.APPEND, emptyState())
        assertTrue((third as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(listOf(null, "next"), network.requestedCursors)
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun a_failed_page_is_reported_and_leaves_the_list_as_it_was() = runTest {
        val mediator = WalkRemoteMediator(null, database.walkDao(), network)
        network.pages[null] = NetworkPage(listOf(networkWalk("a")), null)
        mediator.load(LoadType.REFRESH, emptyState())
        network.pageFailure = AppError.Network

        val result = mediator.load(LoadType.REFRESH, emptyState())

        val error = (result as RemoteMediator.MediatorResult.Error).throwable as WalkLoadException
        assertEquals(AppError.Network, error.error)
        assertNotNull(database.walkDao().get("a"))
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun the_list_of_a_pet_uses_its_own_cursor() = runTest {
        val mediator = WalkRemoteMediator("luna", database.walkDao(), network)
        network.pages[null] = NetworkPage(listOf(networkWalk("a")), "next")

        mediator.load(LoadType.REFRESH, emptyState())

        assertEquals(listOf("luna"), network.requestedPets)
        assertEquals("next", database.walkDao().pageKey("luna")?.nextCursor)
    }

    /** Room keeps what the server said: a visibility newer than the app opened as private, instead of closing it. */
    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun a_visibility_that_the_app_does_not_know_does_not_break_the_walk() = runTest {
        network.pages[null] = NetworkPage(listOf(networkWalk("a").copy(visibility = "Neighbours")), null)

        WalkRemoteMediator(null, database.walkDao(), network).load(LoadType.REFRESH, emptyState())

        assertEquals(WalkVisibility.OnlyMe, walks().observeWalk("a").first()?.visibility)
    }

    @Test
    fun the_full_route_arrives_when_the_walk_is_opened() = runTest {
        val polyline = PolylineCodec.encode(listOf(GeoPoint(40.0, -3.0), GeoPoint(40.1, -3.0)))
        network.walks["a"] = networkWalk("a", listOf(NetworkWalkSegment(polyline, listOf(0, 4000))))
        val repository =
            DefaultWalksRepository(
                database.walkDao(),
                network,
                photos,
                scheduler,
                UnconfinedTestDispatcher(testScheduler),
            )

        repository.refreshWalk("a")

        val route = checkNotNull(repository.observeRoute("a").first())
        assertEquals(2, route.single().points.size)
        assertEquals(4000L, route.single().points[1].offsetMs)
    }

    @Test
    fun a_walk_deleted_from_another_device_disappears() = runTest {
        database.walkDao().storeFromServer(listOf(networkWalk("a").toBundle()))
        network.walks.remove("a")

        val result = walks().refreshWalk("a")

        assertTrue(result is Outcome.Failure)
        assertNull(walks().observeWalk("a").first())
    }

    @Test
    fun a_pending_walk_is_not_lost_because_the_server_does_not_have_it_yet() = runTest {
        walks().savePending(newWalk())

        walks().refreshWalk("walk")

        assertNotNull(walks().observeWalk("walk").first())
    }

    @Test
    fun the_totals_are_kept_for_the_person_and_for_each_pet() = runTest {
        network.stats = NetworkWalkStats(4, 1000, 3000, 2500, 2)
        val repository = walks()

        repository.refreshTotals()
        network.stats = NetworkWalkStats(1, 100, 300, 250, 1)
        repository.refreshTotals("luna")

        assertEquals(4, repository.observeTotals().first()?.walkCount)
        assertEquals(1, repository.observeTotals("luna").first()?.walkCount)
        assertNull(repository.observeTotals("toby").first())
    }

    private fun networkWalk(id: String, segments: List<NetworkWalkSegment>? = null) = NetworkWalk(
        id = id,
        startedAt = "2026-11-02T08:00:00Z",
        endedAt = "2026-11-02T08:10:00Z",
        timeZone = "Europe/Madrid",
        petIds = listOf("luna"),
        visibility = "Friends",
        distanceM = 999,
        durationS = 600,
        movingTimeS = 500,
        averageSpeedMps = 1.5,
        paceSecondsPerKm = 660,
        previewPolylines = listOf("_p~iF~ps|U_ulLnnqC", "_p~iF~ps|U"),
        photos = emptyList<NetworkImage>(),
        updatedAt = "2026-11-02T08:11:00Z",
        segments = segments,
    )

    private class FakePhotoStore : WalkPhotoStore {
        var failure: AppError? = null
        val deleted = mutableListOf<String>()

        override suspend fun store(walkId: String, contentUris: List<String>): Outcome<List<String>> =
            failure?.let { Outcome.Failure(it) }
                ?: Outcome.Success(contentUris.indices.map { "/files/$walkId/$it.jpg" })

        override suspend fun delete(walkId: String) {
            deleted += walkId
        }

        override suspend fun deleteAll() = Unit
    }

    private inner class FakeWalksNetwork : WalksNetworkDataSource {
        var putResult: Outcome<NetworkWalk>? = null
        var deleteResult: Outcome<Unit> = Outcome.Success(Unit)
        var pageFailure: AppError? = null
        var stats = NetworkWalkStats(0, 0, 0, 0, 0)
        val bodies = mutableListOf<WalkBody>()
        val deleted = mutableListOf<String>()
        val pages = mutableMapOf<String?, NetworkPage<NetworkWalk>>()
        val walks = mutableMapOf<String, NetworkWalk>()
        val requestedCursors = mutableListOf<String?>()
        val requestedPets = mutableListOf<String>()

        override suspend fun putWalk(id: String, body: WalkBody): Outcome<NetworkWalk> {
            bodies += body
            return putResult ?: Outcome.Success(
                networkWalk(id).copy(
                    photos = body.photoAssetIds.map { NetworkImage(it, "thumb", "medium") },
                    segments = body.segments,
                ),
            )
        }

        override suspend fun walk(id: String): Outcome<NetworkWalk> =
            walks[id]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Api(404, "walks.not_found"))

        override suspend fun deleteWalk(id: String): Outcome<Unit> {
            deleted += id
            return deleteResult
        }

        override suspend fun myWalks(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>> = page(cursor)

        override suspend fun petWalks(petId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>> {
            requestedPets += petId
            return page(cursor)
        }

        private fun page(cursor: String?): Outcome<NetworkPage<NetworkWalk>> {
            requestedCursors += cursor
            return pageFailure?.let { Outcome.Failure(it) }
                ?: Outcome.Success(pages[cursor] ?: NetworkPage(emptyList(), null))
        }

        override suspend fun myStats(): Outcome<NetworkWalkStats> = Outcome.Success(stats)

        override suspend fun petStats(petId: String): Outcome<NetworkWalkStats> = Outcome.Success(stats)
    }
}

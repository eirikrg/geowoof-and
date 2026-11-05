package com.geowoof.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalkDaoTest {
    private lateinit var database: GeoWoofDatabase
    private val walks get() = database.walkDao()
    private val active get() = database.activeWalkDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            GeoWoofDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun bundle(
        id: String,
        status: String = "Synced",
        track: String? = "[]",
        pets: List<String> = listOf("luna"),
    ) = WalkBundle(
        walk = WalkEntity(
            id = id,
            startedAtEpochMs = 1,
            endedAtEpochMs = 2,
            timeZone = "Europe/Madrid",
            visibility = "Friends",
            note = null,
            distanceM = 100,
            durationS = 60,
            movingTimeS = 50,
            averageSpeedMps = 2.0,
            paceSecondsPerKm = 500,
            previewPolylines = "",
            updatedAtEpochMs = 3,
            syncStatus = status,
            syncErrorCode = null,
        ),
        pets = pets.map { WalkPetEntity(id, it) },
        photos = listOf(WalkPhotoEntity(id, 0, "asset", null, null, null)),
        segmentsJson = track,
    )

    private fun activeWalk(id: String) = ActiveWalkEntity(id, 1, null, "Europe/Madrid", "luna", "Recording", 0)

    @Test
    fun saving_a_finished_walk_ends_the_active_one() = runTest {
        active.upsert(activeWalk("walk"))
        active.appendPoint(ActiveWalkPointEntity("walk", 0, 0, 40.0, -3.0, 5f))

        walks.savePendingAndClearActive(bundle("walk", status = "PendingSync"))

        assertNull(active.current())
        assertEquals("PendingSync", walks.get("walk")?.walk?.syncStatus)
        assertEquals(1, walks.countUnsynced())
    }

    @Test
    fun a_point_cannot_be_written_twice() = runTest {
        active.upsert(activeWalk("walk"))

        active.appendPoint(ActiveWalkPointEntity("walk", 0, 4000, 40.0, -3.0, 5f))
        active.appendPoint(ActiveWalkPointEntity("walk", 0, 4000, 41.0, -3.0, 5f))

        assertEquals(40.0, active.current()!!.points.single().latitude, 0.0)
    }

    @Test
    fun refreshing_the_first_page_keeps_what_has_not_been_uploaded() = runTest {
        walks.storeFromServer(listOf(bundle("old")))
        walks.savePendingAndClearActive(bundle("pending", status = "PendingSync"))

        walks.replaceFirstPage(listOf(bundle("new")), nextCursor = "next")

        assertNull(walks.get("old"))
        assertNotNull(walks.get("pending"))
        assertNotNull(walks.get("new"))
        assertEquals("next", walks.pageKey(MY_WALKS_KEY)?.nextCursor)
    }

    @Test
    fun refreshing_the_walks_of_a_pet_only_touches_that_pet() = runTest {
        walks.storeFromServer(listOf(bundle("with-luna"), bundle("with-toby", pets = listOf("toby"))))

        walks.replaceFirstPageForPet("luna", emptyList(), nextCursor = null)

        assertEquals(emptyList<WalkPetEntity>(), walks.get("with-luna")?.pets)
        assertEquals("toby", walks.get("with-toby")?.pets?.single()?.petId)
    }

    @Test
    fun the_server_does_not_bring_back_a_walk_being_deleted() = runTest {
        walks.storeFromServer(listOf(bundle("walk")))
        walks.setSyncStatus("walk", "PendingDelete", null)

        walks.storeFromServer(listOf(bundle("walk")))

        assertEquals("PendingDelete", walks.get("walk")?.walk?.syncStatus)
    }

    @Test
    fun a_summary_of_the_server_keeps_the_route_already_stored() = runTest {
        walks.storeFromServer(listOf(bundle("walk", track = "[1]")))

        walks.storeFromServer(listOf(bundle("walk", track = null)))

        assertEquals("[1]", walks.track("walk"))
        assertEquals("[1]", walks.observeTrack("walk").first())
    }

    @Test
    fun deleting_a_walk_deletes_its_children() = runTest {
        walks.storeFromServer(listOf(bundle("walk")))

        walks.delete("walk")

        assertNull(walks.track("walk"))
        assertNull(walks.get("walk"))
    }
}

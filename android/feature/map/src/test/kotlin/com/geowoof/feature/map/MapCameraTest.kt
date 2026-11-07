package com.geowoof.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.testing.TestWalks
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Here there is no map, as when the app opens and Google Maps has not started: CameraUpdateFactory throws then, so
 * anything that uses it fails these tests. The movement with a map needs Play services and is seen on a device.
 */
@RunWith(AndroidJUnit4::class)
class MapCameraTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val camera = CameraPositionState()
    private val whereItWas = camera.position
    private var timesReached = 0

    private fun show(walk: ActiveWalk? = null, target: CameraTarget? = null) {
        composeRule.setContent {
            MapCameraEffects(camera, walk, isLookingAround = false, target, onReachTarget = { timesReached++ })
        }
        composeRule.waitForIdle()
    }

    private fun assertCameraIsAt(place: GeoPoint, zoom: Float) {
        assertEquals(LatLng(place.latitude, place.longitude), camera.position.target)
        assertEquals(zoom, camera.position.zoom)
    }

    private fun ActiveWalk.end() = requireNotNull(lastPoint).position

    @Test
    fun a_walk_in_progress_can_come_before_there_is_a_map() {
        val walk = TestWalks.activeWalk()

        show(walk = walk)

        assertCameraIsAt(walk.end(), WALK_ZOOM)
    }

    @Test
    fun each_new_point_of_the_walk_takes_the_camera_with_it() {
        val first = TestWalks.activeWalk(segments = listOf(TestWalks.segment(points = 2)))
        val later = TestWalks.activeWalk(segments = listOf(TestWalks.segment(points = 5)))
        var walk by mutableStateOf(first)
        composeRule.setContent {
            MapCameraEffects(camera, walk, isLookingAround = false, target = null, onReachTarget = {})
        }
        composeRule.waitForIdle()
        assertCameraIsAt(first.end(), WALK_ZOOM)

        walk = later
        composeRule.waitForIdle()

        assertCameraIsAt(later.end(), WALK_ZOOM)
    }

    /** With each point it took them back, and there was no way to see what is around. */
    @Test
    fun while_the_person_looks_around_new_points_do_not_take_the_map_away() {
        val first = TestWalks.activeWalk(segments = listOf(TestWalks.segment(points = 2)))
        val later = TestWalks.activeWalk(segments = listOf(TestWalks.segment(points = 5)))
        var walk by mutableStateOf(first)
        composeRule.setContent {
            MapCameraEffects(camera, walk, isLookingAround = true, target = null, onReachTarget = {})
        }
        composeRule.waitForIdle()

        walk = later
        composeRule.waitForIdle()

        assertEquals(whereItWas, camera.position)
    }

    @Test
    fun when_they_finish_looking_around_the_camera_goes_back_to_the_walk() {
        val walk = TestWalks.activeWalk()
        var isLookingAround by mutableStateOf(true)
        composeRule.setContent {
            MapCameraEffects(camera, walk, isLookingAround, target = null, onReachTarget = {})
        }
        composeRule.waitForIdle()
        assertEquals(whereItWas, camera.position)

        isLookingAround = false
        composeRule.waitForIdle()

        assertCameraIsAt(walk.end(), WALK_ZOOM)
    }

    @Test
    fun a_walk_with_no_points_yet_leaves_the_camera_where_it_was() {
        show(walk = TestWalks.activeWalk(segments = emptyList()))

        assertEquals(whereItWas, camera.position)
    }

    @Test
    fun without_a_walk_the_camera_stays_where_it_was() {
        show()

        assertEquals(whereItWas, camera.position)
        assertEquals(0, timesReached)
    }

    @Test
    fun the_location_button_can_be_pressed_before_there_is_a_map() {
        val person = GeoPoint(39.54, -0.57)

        show(target = CameraTarget(person, isJump = false))

        assertCameraIsAt(person, PERSON_ZOOM)
        assertEquals(1, timesReached)
    }

    @Test
    fun the_first_time_it_jumps_to_the_person() {
        val person = GeoPoint(39.54, -0.57)

        show(target = CameraTarget(person, isJump = true))

        assertCameraIsAt(person, PERSON_ZOOM)
        assertEquals(1, timesReached)
    }
}

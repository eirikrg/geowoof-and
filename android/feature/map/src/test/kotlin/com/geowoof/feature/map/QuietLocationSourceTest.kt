package com.geowoof.feature.map

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuietLocationSourceTest {
    private val locations = MutableSharedFlow<Location>()
    private val heard = mutableListOf<Location>()

    private fun TestScope.source() = QuietLocationSource(
        locations,
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun the_map_hears_the_positions_while_it_wants_them() = runTest {
        val source = source()
        source.activate { heard += it }

        val position = Location("test")
        locations.emit(position)

        assertEquals(listOf(position), heard)
    }

    @Test
    fun nothing_keeps_asking_for_positions_after_the_map_stops_wanting_them() = runTest {
        val source = source()
        source.activate { heard += it }
        assertEquals(1, locations.subscriptionCount.value)

        source.deactivate()
        locations.emit(Location("test"))

        assertEquals(0, locations.subscriptionCount.value)
        assertEquals(emptyList<Location>(), heard)
    }

    @Test
    fun activating_it_twice_does_not_ask_twice() = runTest {
        val source = source()
        source.activate { heard += it }
        source.activate { heard += it }

        assertEquals(1, locations.subscriptionCount.value)
    }
}

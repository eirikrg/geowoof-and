package com.geowoof.feature.map

import android.location.Location
import com.google.android.gms.maps.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The positions of the blue dot when no walk is being recorded. Left to itself, the map keeps the GPS on for as long as
 * it is open: on the phone of the tests that was a third of what the app used, only to draw that dot.
 */
internal class QuietLocationSource(private val locations: Flow<Location>, private val scope: CoroutineScope) :
    LocationSource {
    private var collecting: Job? = null

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        collecting?.cancel()
        collecting = scope.launch { locations.collect(listener::onLocationChanged) }
    }

    override fun deactivate() {
        collecting?.cancel()
        collecting = null
    }
}

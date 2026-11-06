package com.geowoof.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/** The location that the app can use. A walk needs [Precise]: an approximate one cannot draw a route. */
enum class LocationPermissionLevel {
    Precise,
    Approximate,
    None,
}

/** Permissions and device settings, separated from the positions so that both can be replaced in tests. */
interface LocationAccess {
    fun permissionLevel(): LocationPermissionLevel

    /** Whether location is turned on in the device settings. */
    fun isLocationEnabled(): Boolean
}

/** Thrown by [LocationDataSource.fixes] when the permission was revoked while receiving positions. */
class LocationPermissionException : RuntimeException("The location permission is not granted.")

interface LocationDataSource {
    /** Positions while it is collected. It fails with [LocationPermissionException] without permission. */
    fun fixes(): Flow<LocationFix>

    /**
     * Where the person is, to show the map there. A recent position is the only answer. An old one can be from another
     * city, so a new one is asked for: if that takes too long the old one comes first, and then the new one. Nothing
     * comes without permission or without any position. It ends by itself, and stopping it stops the search.
     */
    fun whereThePersonIs(): Flow<GeoPoint>

    /**
     * Positions for the dot on the map when no walk is being recorded: from Wi-Fi and the cell network, because the
     * GPS would use a lot of battery for something that does not need that precision. Only while it is collected.
     */
    fun quietLocations(): Flow<Location>
}

internal class AndroidLocationAccess @Inject constructor(@ApplicationContext private val context: Context) :
    LocationAccess {
    override fun permissionLevel(): LocationPermissionLevel = when {
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermissionLevel.Precise
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermissionLevel.Approximate
        else -> LocationPermissionLevel.None
    }

    override fun isLocationEnabled(): Boolean =
        context.getSystemService<LocationManager>()?.let(LocationManagerCompat::isLocationEnabled) == true

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Fused positions with high accuracy, every 4 s and without batching them (ADR-0006). */
internal class FusedLocationDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: LocationAccess,
) : LocationDataSource {
    // The permission is checked just before, and a revocation surfaces as a SecurityException.
    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<LocationFix> = callbackFlow {
        if (access.permissionLevel() != LocationPermissionLevel.Precise) throw LocationPermissionException()

        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix()) }
            }
        }
        try {
            client.requestLocationUpdates(REQUEST, callback, Looper.getMainLooper())
        } catch (@Suppress("SwallowedException") exception: SecurityException) {
            throw LocationPermissionException()
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }

    // A revocation surfaces as a SecurityException, and then there is simply nothing to say.
    @SuppressLint("MissingPermission")
    override fun whereThePersonIs(): Flow<GeoPoint> = flow {
        if (access.permissionLevel() == LocationPermissionLevel.None) return@flow
        val client = LocationServices.getFusedLocationProviderClient(context)
        val last = client.lastLocation.awaitOrNull()
        if (last?.isRecent() == true) {
            emit(last.toGeoPoint())
        } else {
            emitCurrent(client, last)
        }
    }.catch { cause -> if (cause !is SecurityException) throw cause }

    @SuppressLint("MissingPermission")
    private suspend fun FlowCollector<GeoPoint>.emitCurrent(client: FusedLocationProviderClient, old: Location?) {
        val cancellation = CancellationTokenSource()
        try {
            val current = client.getCurrentLocation(CURRENT_REQUEST, cancellation.token)
            val inTime = withTimeoutOrNull(OLD_POSITION_WAIT_MS) { current.awaitOrNull() }
            if (inTime != null) {
                emit(inTime.toGeoPoint())
            } else {
                old?.let { emit(it.toGeoPoint()) }
                current.awaitOrNull()?.let { emit(it.toGeoPoint()) }
            }
        } finally {
            // Without it, the search would go on using the battery after the answer is no longer needed.
            cancellation.cancel()
        }
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private fun Location.isRecent() =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / NANOS_PER_MILLI <= RECENT_MS

    /** `task.result` throws if the task failed or was cancelled, and here that only means not knowing. */
    @SuppressLint("MissingPermission")
    override fun quietLocations(): Flow<Location> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        client.lastLocation.awaitOrNull()?.takeIf { it.isRecent() }?.let { trySend(it) }
        client.requestLocationUpdates(QUIET_REQUEST, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }.catch { cause -> if (cause !is SecurityException) throw cause }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> continuation.resume(if (task.isSuccessful) task.result else null) }
    }

    private fun Location.toFix() = LocationFix(
        position = GeoPoint(latitude, longitude),
        accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        timeEpochMs = time,
    )

    private companion object {
        const val INTERVAL_MS = 4_000L
        const val MIN_INTERVAL_MS = 2_000L
        const val RECENT_MS = 120_000L
        const val OLD_POSITION_WAIT_MS = 2_500L
        const val SEARCH_MS = 30_000L
        const val QUIET_INTERVAL_MS = 15_000L
        const val QUIET_MIN_INTERVAL_MS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L

        val CURRENT_REQUEST: CurrentLocationRequest = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(SEARCH_MS)
            .setMaxUpdateAgeMillis(RECENT_MS)
            .build()

        val QUIET_REQUEST: LocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, QUIET_INTERVAL_MS)
                .setMinUpdateIntervalMillis(QUIET_MIN_INTERVAL_MS)
                .build()

        val REQUEST: LocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMaxUpdateDelayMillis(0)
            .build()
    }
}

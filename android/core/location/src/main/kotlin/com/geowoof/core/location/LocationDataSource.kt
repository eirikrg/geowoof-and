package com.geowoof.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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

    /** Roughly where the person is, to show the map there. `null` if it is not known or there is no permission. */
    suspend fun lastKnown(): GeoPoint?
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

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): GeoPoint? {
        if (access.permissionLevel() == LocationPermissionLevel.None) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val location = client.lastLocation.awaitOrNull()
                ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).awaitOrNull()
            location?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (@Suppress("SwallowedException") exception: SecurityException) {
            null
        }
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> continuation.resume(task.result.takeIf { task.isSuccessful }) }
    }

    private fun Location.toFix() = LocationFix(
        position = GeoPoint(latitude, longitude),
        accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        timeEpochMs = time,
    )

    private companion object {
        const val INTERVAL_MS = 4_000L
        const val MIN_INTERVAL_MS = 2_000L

        val REQUEST: LocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMaxUpdateDelayMillis(0)
            .build()
    }
}

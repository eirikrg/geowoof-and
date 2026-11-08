package com.geowoof.core.domain.walks

import com.geowoof.core.common.geo.GeoMath
import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.data.repository.LiveRepository
import com.geowoof.core.data.repository.SettingsRepository
import com.geowoof.core.location.LocationDataSource
import com.geowoof.core.location.LocationPermissionException
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.AppError
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSettings
import com.geowoof.core.model.code
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Shares the position of a walk on the live map. Nothing of what happens here can touch the
 * recording: it listens to the device on its own, it does not queue anything and a failure only
 * means that the next position tries again.
 *
 * The positions near the start stay on the device, so that the server never learns where a walk
 * begins. The route of the walk is not used because it only grows when moving: someone standing
 * still would disappear, while they keep receiving positions and are really there.
 */
class LivePositionPublisher @Inject constructor(
    private val location: LocationDataSource,
    private val settings: SettingsRepository,
    private val live: LiveRepository,
    private val activeWalks: ActiveWalkRepository,
    private val filter: FilterLocationUseCase,
) {
    /** Until it is cancelled, following what the person chooses meanwhile. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun publish(walk: ActiveWalk) {
        try {
            settings.settings.collectLatest { current ->
                if (current.liveVisibility == null ||
                    current.liveVisibility == LiveVisibility.Nobody
                ) {
                    return@collectLatest
                }
                live.whileConnected { share(walk, current) }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Whatever happens to the map, the walk goes on.
        }
    }

    private suspend fun share(walk: ActiveWalk, current: UserSettings) {
        // From Room: a choice made halfway through the walk does not move where it started.
        val start = (activeWalks.current() ?: walk).segments.firstOrNull {
            it.points.isNotEmpty()
        }?.points?.first()?.position
        val run = SharingRun(walk, start, current.hideStartDistanceM)
        try {
            location.fixes().conflate().collect { fix -> run.onFix(fix) }
        } catch (@Suppress("SwallowedException") exception: LocationPermissionException) {
            // The recording finds out too, and it is the one that deals with it.
        } finally {
            if (run.isSharing) withContext(NonCancellable) { withTimeoutOrNull(STOP_TIMEOUT_MS) { live.stopSharing() } }
        }
    }

    /** What is decided from one position to the next. */
    private inner class SharingRun(
        private val walk: ActiveWalk,
        private var start: GeoPoint?,
        private val hideStartDistanceM: Int,
    ) {
        private var lastSentAtMs: Long? = null
        private var isRefused = false

        var isSharing = false
            private set

        suspend fun onFix(fix: LocationFix) {
            if (isRefused || fix.accuracyM > MAX_ACCURACY_M) return
            // The same position that would start the route: with a worse one the door could be outside the zone.
            if (start == null && filter.rejection(fix, anchor = null) != null) return
            val origin = start ?: fix.position.also { start = it }
            when {
                GeoMath.distanceMeters(origin, fix.position) < hideStartDistanceM -> hide()
                lastSentAtMs?.let { fix.timeEpochMs - it < MIN_INTERVAL_MS } == true -> Unit
                else -> send(fix)
            }
        }

        /** Whoever was looking stops seeing them at once, at the edge of the zone and not 30 s later. */
        private suspend fun hide() {
            if (!isSharing) return
            isSharing = false
            live.stopSharing()
        }

        private suspend fun send(fix: LocationFix) {
            lastSentAtMs = fix.timeEpochMs
            if (!isSharing) {
                when (val started = live.startSharing(walk.id, walk.petIds)) {
                    is Outcome.Success -> isSharing = true

                    is Outcome.Failure -> {
                        onRefused(started.error)
                        return
                    }
                }
            }
            (live.updatePosition(fix) as? Outcome.Failure)?.let { onFailure(it.error) }
        }

        private suspend fun onFailure(error: AppError) {
            when (error.code) {
                // The session expired on the server while there was no connection: the next one starts it again.
                NOT_SHARING -> isSharing = false

                else -> onRefused(error)
            }
        }

        /**
         * Asking again would get the same answer until what is known of the person changes.
         * Refreshing it stops this run if they chose Nobody on another device.
         */
        private suspend fun onRefused(error: AppError) {
            if (error.code == SHARING_DISABLED || error.code == PET_NOT_OWNED) {
                isSharing = false
                isRefused = true
                settings.refresh()
            }
        }
    }

    private companion object {
        /** The server takes one every 3 s. */
        const val MIN_INTERVAL_MS = 5_000L

        /** Worse than this is not where the person is. */
        const val MAX_ACCURACY_M = 50f
        const val STOP_TIMEOUT_MS = 3_000L
        const val NOT_SHARING = "live.not_sharing"
        const val SHARING_DISABLED = "live.sharing_disabled"
        const val PET_NOT_OWNED = "live.pet_not_owned"
    }
}

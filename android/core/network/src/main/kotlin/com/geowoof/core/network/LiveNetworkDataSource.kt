package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkLiveEvent
import com.geowoof.core.network.model.NetworkLiveWalker
import com.geowoof.core.network.model.NetworkSettings
import com.geowoof.core.network.model.NetworkStartSharing
import com.geowoof.core.network.model.NetworkViewport
import com.geowoof.core.network.model.NetworkWalkerLeft
import com.geowoof.core.network.model.SettingsBody
import com.geowoof.core.network.realtime.HubMessage
import com.geowoof.core.network.realtime.RealtimeConnection
import com.geowoof.core.network.realtime.RealtimeState
import com.geowoof.core.network.realtime.hubCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import java.time.Instant
import javax.inject.Inject

/** The endpoints of the live settings. */
internal interface SettingsApi {
    @GET("api/v1/me/settings")
    suspend fun settings(): NetworkSettings

    @PATCH("api/v1/me/settings")
    suspend fun updateSettings(@Body body: SettingsBody): NetworkSettings
}

interface SettingsNetworkDataSource {
    suspend fun settings(): Outcome<NetworkSettings>

    suspend fun updateSettings(body: SettingsBody): Outcome<NetworkSettings>
}

internal class RetrofitSettingsNetworkDataSource @Inject constructor(
    private val api: SettingsApi,
    private val json: Json,
) : SettingsNetworkDataSource {
    override suspend fun settings() = apiCall(json) { api.settings() }

    override suspend fun updateSettings(body: SettingsBody) = apiCall(json) { api.updateSettings(body) }
}

/** The live hub. A call fails at once without a connection: nothing waits for it to come back. */
interface LiveNetworkDataSource {
    val state: StateFlow<RealtimeState>

    val events: Flow<NetworkLiveEvent>

    /** The connection stays open at least as long as [block] takes. */
    suspend fun <T> whileOpen(block: suspend () -> T): T

    suspend fun subscribeViewport(north: Double, south: Double, east: Double, west: Double): Outcome<NetworkViewport>

    suspend fun startSharing(walkId: String, petIds: List<String>): Outcome<String>

    suspend fun updatePosition(
        latitude: Double,
        longitude: Double,
        accuracyM: Float,
        recordedAt: Instant,
    ): Outcome<Unit>

    suspend fun stopSharing(): Outcome<Unit>
}

internal class SignalRLiveNetworkDataSource(private val connection: RealtimeConnection, private val json: Json) :
    LiveNetworkDataSource {
    override val state: StateFlow<RealtimeState> = connection.state

    override val events: Flow<NetworkLiveEvent> = connection.messages.mapNotNull { it.toEvent() }

    override suspend fun <T> whileOpen(block: suspend () -> T): T = connection.whileOpen(block)

    override suspend fun subscribeViewport(north: Double, south: Double, east: Double, west: Double) = hubCall {
        val area = mapOf("north" to north, "south" to south, "east" to east, "west" to west)
        json.decodeFromString<NetworkViewport>(connection.invokeForResult("SubscribeViewport", area))
    }

    override suspend fun startSharing(walkId: String, petIds: List<String>) = hubCall {
        val request = mapOf("walkId" to walkId, "petIds" to petIds)
        json.decodeFromString<NetworkStartSharing>(connection.invokeForResult("StartSharing", request)).visibility
    }

    override suspend fun updatePosition(latitude: Double, longitude: Double, accuracyM: Float, recordedAt: Instant) =
        hubCall {
            val position = mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracyM" to accuracyM,
                "recordedAt" to recordedAt.toString(),
            )
            connection.invoke("UpdatePosition", position)
        }

    override suspend fun stopSharing() = hubCall { connection.invoke("StopSharing") }

    /** One that does not understand is ignored: a newer server must not break the map. */
    private fun HubMessage.toEvent(): NetworkLiveEvent? = try {
        when (event) {
            WALKER_UPDATED -> NetworkLiveEvent.WalkerUpdated(json.decodeFromString<NetworkLiveWalker>(payload))
            WALKER_LEFT -> NetworkLiveEvent.WalkerLeft(json.decodeFromString<NetworkWalkerLeft>(payload).userId)
            else -> null
        }
    } catch (@Suppress("SwallowedException") exception: SerializationException) {
        null
    }

    companion object {
        const val WALKER_UPDATED = "WalkerUpdated"
        const val WALKER_LEFT = "WalkerLeft"
    }
}

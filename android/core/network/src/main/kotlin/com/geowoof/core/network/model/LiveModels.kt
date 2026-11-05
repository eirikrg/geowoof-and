package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// The settings of docs/api.md (phase 7) and the messages of the hub in docs/realtime.md.

@Serializable
data class NetworkSettings(val liveVisibility: String? = null, val hideStartDistanceM: Int)

/** What is not sent stays as it is. */
@Serializable
data class SettingsBody(val liveVisibility: String? = null, val hideStartDistanceM: Int? = null)

@Serializable
data class NetworkLivePet(val id: String, val name: String, val avatar: NetworkImage? = null)

@Serializable
data class NetworkLiveWalker(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatar: NetworkImage? = null,
    val pets: List<NetworkLivePet> = emptyList(),
    val latitude: Double,
    val longitude: Double,
    val updatedAt: String,
)

@Serializable
data class NetworkViewport(val tooWide: Boolean, val walkers: List<NetworkLiveWalker> = emptyList())

@Serializable
internal data class NetworkStartSharing(val visibility: String)

@Serializable
internal data class NetworkWalkerLeft(val userId: String)

sealed interface NetworkLiveEvent {
    data class WalkerUpdated(val walker: NetworkLiveWalker) : NetworkLiveEvent

    data class WalkerLeft(val userId: String) : NetworkLiveEvent
}

package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// Wire format of friends, blocks, reports and search (docs/api.md, phase 4).

@Serializable
data class NetworkUserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val city: String? = null,
    val avatar: NetworkImage? = null,
)

@Serializable
data class NetworkRelationship(val status: String, val requestId: String? = null)

@Serializable
data class NetworkFriendRequest(
    val id: String,
    val user: NetworkUserSummary,
    val direction: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class NetworkFriend(val user: NetworkUserSummary, val since: String)

@Serializable
data class NetworkBlock(val user: NetworkUserSummary, val since: String)

@Serializable
data class NetworkPetSearchResult(
    val id: String,
    val name: String,
    val species: String,
    val speciesOther: String? = null,
    val breed: String? = null,
    val avatar: NetworkImage? = null,
    val owner: NetworkUserSummary,
)

@Serializable
data class FriendRequestBody(val userId: String)

@Serializable
data class ReportBody(val targetType: String, val targetId: String, val reason: String, val details: String?)

@Serializable
data class NetworkReport(val id: String)

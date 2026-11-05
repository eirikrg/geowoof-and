package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// Wire format of followers, blocks, reports and search (docs/api.md, phases 4 and 5).

@Serializable
data class NetworkUserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val city: String? = null,
    val avatar: NetworkImage? = null,
)

@Serializable
data class NetworkRelationship(
    val status: String,
    val followsYou: Boolean = false,
    val requestId: String? = null,
    val incomingRequestId: String? = null,
)

@Serializable
data class NetworkFollowRequest(
    val id: String,
    val user: NetworkUserSummary,
    val direction: String,
    val createdAt: String,
)

@Serializable
data class NetworkCounts(val followers: Int, val following: Int, val friends: Int)

@Serializable
data class NetworkPerson(val user: NetworkUserSummary, val since: String)

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
data class ReportBody(val targetType: String, val targetId: String, val reason: String, val details: String?)

@Serializable
data class NetworkReport(val id: String)

package com.geowoof.core.model

import java.time.Instant

/** A person in a list. */
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val city: String?,
    val avatar: RemoteImage?,
)

enum class RelationshipStatus { Self, None, Friends, RequestSent, RequestReceived, Blocked }

/** What the signed-in person has to do with another. [requestId] is there while a request is pending. */
data class Relationship(val status: RelationshipStatus, val requestId: String? = null)

enum class RequestDirection { Received, Sent }

/** A pending friend request, with the other person. */
data class FriendRequest(val id: String, val user: UserSummary, val direction: RequestDirection, val createdAt: Instant)

data class Friend(val user: UserSummary, val since: Instant)

data class BlockedUser(val user: UserSummary, val since: Instant)

enum class ReportReason { Spam, Harassment, InappropriateContent, Impersonation, Other }

data class PetSearchResult(
    val id: String,
    val name: String,
    val species: PetSpecies,
    val speciesOther: String?,
    val breed: String?,
    val avatar: RemoteImage?,
    val owner: UserSummary,
)

/** A walk of someone else. Its [route] can be empty: the server hides what is near the start and the end. */
data class UserWalk(val walk: Walk, val ownerId: String, val route: List<RouteSegment>)

package com.geowoof.core.model

import java.time.Instant

/** A person in a list. If their profile is private and you do not follow them, [displayName] is their username. */
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val city: String?,
    val avatar: RemoteImage?,
)

enum class RelationshipStatus { Self, None, Requested, Following, Friends, Blocked }

/**
 * What the signed-in person has to do with another.
 *
 * [followsYou] means that following them would make you friends. [requestId] is your pending request to
 * follow them, and [incomingRequestId] is theirs to follow you.
 */
data class Relationship(
    val status: RelationshipStatus,
    val followsYou: Boolean = false,
    val requestId: String? = null,
    val incomingRequestId: String? = null,
)

enum class RequestDirection { Received, Sent }

/** A pending request to follow a private profile, with the other person. */
data class FollowRequest(val id: String, val user: UserSummary, val direction: RequestDirection, val createdAt: Instant)

/** Anyone can see these numbers. The lists are only for that person and their friends. */
data class ProfileCounts(val followers: Int, val following: Int, val friends: Int)

enum class PeopleListKind { Followers, Following, Friends }

/** Someone in a list of followers, of people followed or of friends. */
data class Person(val user: UserSummary, val since: Instant)

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

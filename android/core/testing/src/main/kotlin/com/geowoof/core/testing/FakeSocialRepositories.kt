package com.geowoof.core.testing

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.BlockedUser
import com.geowoof.core.model.FollowRequest
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Person
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.UserSummary
import com.geowoof.core.model.UserWalk
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.time.Instant

object TestSocial {
    val SINCE: Instant = Instant.parse("2026-11-01T10:00:00Z")

    /** The load states of a list that has finished: without them, a screen would keep waiting. */
    val FINISHED = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = true),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    )

    fun user(
        id: String = "bea-id",
        username: String = "bea",
        displayName: String = "Bea",
        city: String? = "Madrid",
        avatar: RemoteImage? = null,
    ) = UserSummary(id, username, displayName, city, avatar)

    fun request(
        id: String = "request-1",
        user: UserSummary = user(),
        direction: RequestDirection = RequestDirection.Received,
    ) = FollowRequest(id, user, direction, SINCE)

    fun person(user: UserSummary = user()) = Person(user, SINCE)

    fun blocked(user: UserSummary = user()) = BlockedUser(user, SINCE)

    fun petResult(
        id: String = "luna",
        name: String = "Luna",
        breed: String? = "Border collie",
        owner: UserSummary = user(),
    ) = PetSearchResult(id, name, PetSpecies.Dog, null, breed, null, owner)

    fun <T : Any> pages(items: List<T>): PagingData<T> = PagingData.from(items, FINISHED)
}

@Suppress("TooManyFunctions")
class FakeSocialRepository : SocialRepository {
    override val receivedRequests = MutableStateFlow<List<FollowRequest>>(emptyList())
    override val sentRequests = MutableStateFlow<List<FollowRequest>>(emptyList())
    override val blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())

    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var relationshipResult: Outcome<Relationship> = Outcome.Success(Relationship(RelationshipStatus.None))

    /** What a change answers with, when the relationship it leaves matters to the test. */
    var followResult: Outcome<Relationship> = Outcome.Success(Relationship(RelationshipStatus.Following))
    var actionResult: Outcome<Unit> = Outcome.Success(Unit)

    /** What was asked of the repository, in order: "follow:bea-id", "block:bea-id"... */
    val calls = mutableListOf<String>()

    private fun record(call: String): Outcome<Unit> {
        calls += call
        return actionResult
    }

    private fun answerWithRelationship(call: String): Outcome<Relationship> {
        calls += call
        return when (val result = actionResult) {
            is Outcome.Failure -> result
            is Outcome.Success -> relationshipResult
        }
    }

    override suspend fun refreshRequests(): Outcome<Unit> {
        calls += "refreshRequests"
        return refreshResult
    }

    override suspend fun refreshBlockedUsers(): Outcome<Unit> {
        calls += "refreshBlocked"
        return refreshResult
    }

    override suspend fun relationship(userId: String) = relationshipResult

    override suspend fun follow(userId: String): Outcome<Relationship> {
        calls += "follow:$userId"
        return followResult.also { if (it is Outcome.Success) relationshipResult = it }
    }

    override suspend fun unfollow(userId: String) = answerWithRelationship("unfollow:$userId")

    override suspend fun removeFollower(userId: String) = answerWithRelationship("removeFollower:$userId")

    override suspend fun acceptRequest(requestId: String) = answerWithRelationship("accept:$requestId").also {
        if (it is Outcome.Success) receivedRequests.update { list -> list.filterNot { r -> r.id == requestId } }
    }

    override suspend fun rejectRequest(requestId: String) = record("reject:$requestId").also {
        if (it is Outcome.Success) receivedRequests.update { list -> list.filterNot { r -> r.id == requestId } }
    }

    override suspend fun cancelRequest(requestId: String) = record("cancel:$requestId").also {
        if (it is Outcome.Success) sentRequests.update { list -> list.filterNot { r -> r.id == requestId } }
    }

    override suspend fun block(userId: String) = record("block:$userId")

    override suspend fun unblock(userId: String) = record("unblock:$userId").also {
        if (it is Outcome.Success) blockedUsers.update { list -> list.filterNot { b -> b.user.id == userId } }
    }

    override suspend fun report(userId: String, reason: ReportReason, details: String?) =
        record("report:$userId:$reason:$details")
}

@Suppress("TooManyFunctions")
class FakePeopleRepository : PeopleRepository {
    var profileResult: Outcome<Profile> = Outcome.Success(TestPets.profile(displayName = "Bea", username = "bea"))
    var countsResult: Outcome<ProfileCounts> = Outcome.Success(
        ProfileCounts(followers = 12, following = 8, friends = 5),
    )
    var petsResult: Outcome<List<Pet>> = Outcome.Success(emptyList())
    var totalsResult: Outcome<WalkTotals> = Outcome.Success(TestWalks.totals())
    var walkResult: Outcome<UserWalk>? = null
    var users: List<UserSummary> = emptyList()
    var pets: List<PetSearchResult> = emptyList()
    var walks: List<Walk> = emptyList()
    var people: List<Person> = emptyList()

    /** What was searched, in order. */
    val searches = mutableListOf<String>()

    /** Which lists were asked for: "walks:bea-id", "pets:bea-id", "followers:bea-id"... */
    val reads = mutableListOf<String>()

    override fun searchUsers(query: String): Flow<PagingData<UserSummary>> {
        searches += "users:$query"
        return flowOf(TestSocial.pages(users))
    }

    override fun searchPets(query: String): Flow<PagingData<PetSearchResult>> {
        searches += "pets:$query"
        return flowOf(TestSocial.pages(pets))
    }

    override suspend fun profile(userId: String) = profileResult

    override suspend fun counts(userId: String) = countsResult

    override fun people(userId: String, kind: PeopleListKind): Flow<PagingData<Person>> {
        reads += "${kind.name.lowercase()}:$userId"
        return flowOf(TestSocial.pages(people))
    }

    override suspend fun pets(userId: String): Outcome<List<Pet>> {
        reads += "pets:$userId"
        return petsResult
    }

    override fun walks(userId: String): Flow<PagingData<Walk>> {
        reads += "walks:$userId"
        return flowOf(TestSocial.pages(walks))
    }

    override suspend fun walkTotals(userId: String) = totalsResult

    override suspend fun walk(walkId: String) = checkNotNull(walkResult) { "No walk for this test" }
}

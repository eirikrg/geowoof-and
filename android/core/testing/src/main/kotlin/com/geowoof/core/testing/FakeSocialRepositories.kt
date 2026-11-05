package com.geowoof.core.testing

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.BlockedUser
import com.geowoof.core.model.Friend
import com.geowoof.core.model.FriendRequest
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.Profile
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.UserSummary
import com.geowoof.core.model.UserWalk
import com.geowoof.core.model.Walk
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
    ) = FriendRequest(id, user, direction, SINCE)

    fun friend(user: UserSummary = user()) = Friend(user, SINCE)

    fun blocked(user: UserSummary = user()) = BlockedUser(user, SINCE)

    fun petResult(
        id: String = "luna",
        name: String = "Luna",
        breed: String? = "Border collie",
        owner: UserSummary = user(),
    ) = PetSearchResult(id, name, PetSpecies.Dog, null, breed, null, owner)

    fun <T : Any> pages(items: List<T>): PagingData<T> = PagingData.from(items, FINISHED)
}

class FakeSocialRepository : SocialRepository {
    override val friends = MutableStateFlow<List<Friend>>(emptyList())
    override val receivedRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    override val sentRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    override val blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())

    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var relationshipResult: Outcome<Relationship> = Outcome.Success(Relationship(RelationshipStatus.None))
    var sendResult: Outcome<Relationship> = Outcome.Success(Relationship(RelationshipStatus.RequestSent, "request-1"))
    var actionResult: Outcome<Unit> = Outcome.Success(Unit)

    /** What was asked of the repository, in order: "accept:request-1", "block:bea-id"... */
    val calls = mutableListOf<String>()

    private fun record(call: String): Outcome<Unit> {
        calls += call
        return actionResult
    }

    override suspend fun refreshFriends(): Outcome<Unit> {
        calls += "refreshFriends"
        return refreshResult
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

    override suspend fun sendRequest(userId: String): Outcome<Relationship> {
        calls += "send:$userId"
        return sendResult
    }

    override suspend fun acceptRequest(requestId: String) = record("accept:$requestId").also {
        if (it is Outcome.Success) receivedRequests.update { list -> list.filterNot { r -> r.id == requestId } }
    }

    override suspend fun rejectRequest(requestId: String) = record("reject:$requestId").also {
        if (it is Outcome.Success) receivedRequests.update { list -> list.filterNot { r -> r.id == requestId } }
    }

    override suspend fun cancelRequest(requestId: String) = record("cancel:$requestId").also {
        if (it is Outcome.Success) sentRequests.update { list -> list.filterNot { r -> r.id == requestId } }
    }

    override suspend fun removeFriend(userId: String) = record("remove:$userId").also {
        if (it is Outcome.Success) friends.update { list -> list.filterNot { f -> f.user.id == userId } }
    }

    override suspend fun block(userId: String) = record("block:$userId")

    override suspend fun unblock(userId: String) = record("unblock:$userId").also {
        if (it is Outcome.Success) blockedUsers.update { list -> list.filterNot { b -> b.user.id == userId } }
    }

    override suspend fun report(userId: String, reason: ReportReason, details: String?) =
        record("report:$userId:$reason:$details")
}

class FakePeopleRepository : PeopleRepository {
    var profileResult: Outcome<Profile> = Outcome.Success(TestPets.profile(displayName = "Bea", username = "bea"))
    var petsResult: Outcome<List<Pet>> = Outcome.Success(emptyList())
    var walkResult: Outcome<UserWalk>? = null
    var users: List<UserSummary> = emptyList()
    var pets: List<PetSearchResult> = emptyList()
    var walks: List<Walk> = emptyList()

    /** What was searched, in order. */
    val searches = mutableListOf<String>()

    override fun searchUsers(query: String): Flow<PagingData<UserSummary>> {
        searches += "users:$query"
        return flowOf(TestSocial.pages(users))
    }

    override fun searchPets(query: String): Flow<PagingData<PetSearchResult>> {
        searches += "pets:$query"
        return flowOf(TestSocial.pages(pets))
    }

    override suspend fun profile(userId: String) = profileResult

    override suspend fun pets(userId: String) = petsResult

    override fun walks(userId: String): Flow<PagingData<Walk>> = flowOf(TestSocial.pages(walks))

    override suspend fun walk(walkId: String) = checkNotNull(walkResult) { "No walk for this test" }
}

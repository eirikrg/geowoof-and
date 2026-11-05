package com.geowoof.core.data.repository

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.database.GeoWoofDatabase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.network.PeopleNetworkDataSource
import com.geowoof.core.network.SocialNetworkDataSource
import com.geowoof.core.network.model.NetworkBlock
import com.geowoof.core.network.model.NetworkFriend
import com.geowoof.core.network.model.NetworkFriendRequest
import com.geowoof.core.network.model.NetworkImage
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkPetSearchResult
import com.geowoof.core.network.model.NetworkProfile
import com.geowoof.core.network.model.NetworkRelationship
import com.geowoof.core.network.model.NetworkReport
import com.geowoof.core.network.model.NetworkUserSummary
import com.geowoof.core.network.model.NetworkWalk
import com.geowoof.core.network.model.NetworkWalkSegment
import com.geowoof.core.network.model.ReportBody
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIME = "2026-11-05T10:00:00Z"

private fun networkUser(id: String = "bea-id", username: String = "bea") = NetworkUserSummary(
    id = id,
    username = username,
    displayName = username.replaceFirstChar(Char::uppercase),
    city = "Madrid",
    avatar = NetworkImage("asset", "https://thumb", "https://medium"),
)

private fun networkRequest(
    id: String = "request-1",
    user: NetworkUserSummary = networkUser(),
    direction: String = "Received",
    status: String = "Pending",
) = NetworkFriendRequest(id, user, direction, status, TIME)

private class FakeSocialNetwork : SocialNetworkDataSource {
    var friendPages = mutableMapOf<String?, NetworkPage<NetworkFriend>>(null to NetworkPage(emptyList(), null))
    var received: Outcome<NetworkPage<NetworkFriendRequest>> = Outcome.Success(NetworkPage(emptyList(), null))
    var sent: Outcome<NetworkPage<NetworkFriendRequest>> = Outcome.Success(NetworkPage(emptyList(), null))
    var blocked: List<NetworkBlock> = emptyList()
    var sendAnswer: Outcome<NetworkFriendRequest> = Outcome.Success(networkRequest(direction = "Sent"))
    var answerResult: Outcome<Unit> = Outcome.Success(Unit)
    val reports = mutableListOf<ReportBody>()
    val calls = mutableListOf<String>()

    override suspend fun sendRequest(userId: String) = sendAnswer

    override suspend fun receivedRequests(limit: Int, cursor: String?) = received

    override suspend fun sentRequests(limit: Int, cursor: String?) = sent

    override suspend fun acceptRequest(id: String): Outcome<NetworkFriendRequest> {
        calls += "accept:$id"
        return when (val result = answerResult) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(networkRequest(id, status = "Accepted"))
        }
    }

    override suspend fun rejectRequest(id: String): Outcome<Unit> {
        calls += "reject:$id"
        return answerResult
    }

    override suspend fun cancelRequest(id: String): Outcome<Unit> {
        calls += "cancel:$id"
        return answerResult
    }

    override suspend fun friends(limit: Int, cursor: String?) = Outcome.Success(friendPages.getValue(cursor))

    override suspend fun removeFriend(userId: String): Outcome<Unit> {
        calls += "remove:$userId"
        return answerResult
    }

    override suspend fun blocks(limit: Int, cursor: String?) = Outcome.Success(NetworkPage(blocked, null))

    override suspend fun block(userId: String): Outcome<Unit> {
        calls += "block:$userId"
        return answerResult
    }

    override suspend fun unblock(userId: String): Outcome<Unit> {
        calls += "unblock:$userId"
        return answerResult
    }

    override suspend fun report(body: ReportBody): Outcome<NetworkReport> {
        reports += body
        return Outcome.Success(NetworkReport("report-1"))
    }
}

private class FakePeopleNetwork : PeopleNetworkDataSource {
    var relationshipAnswer = NetworkRelationship("Friends")
    var userPages: Outcome<NetworkPage<NetworkUserSummary>> = Outcome.Success(NetworkPage(emptyList(), null))
    var walkAnswer: Outcome<NetworkWalk> = Outcome.Failure(AppError.Network)
    var petsAnswer: Outcome<NetworkPage<NetworkPet>> = Outcome.Success(NetworkPage(emptyList(), null))
    val cursors = mutableListOf<String?>()

    override suspend fun searchUsers(
        query: String,
        limit: Int,
        cursor: String?,
    ): Outcome<NetworkPage<NetworkUserSummary>> {
        cursors += cursor
        return userPages
    }

    override suspend fun searchPets(
        query: String,
        limit: Int,
        cursor: String?,
    ): Outcome<NetworkPage<NetworkPetSearchResult>> = Outcome.Success(
        NetworkPage(
            listOf(NetworkPetSearchResult("luna", "Luna", "Unicorn", null, "Mezcla", null, networkUser())),
            null,
        ),
    )

    override suspend fun profile(userId: String) =
        Outcome.Success(NetworkProfile(userId, "bea", "Bea", null, null, null, TIME))

    override suspend fun relationship(userId: String) = Outcome.Success(relationshipAnswer)

    override suspend fun pets(userId: String) = petsAnswer

    override suspend fun walks(userId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>> =
        Outcome.Success(NetworkPage(emptyList(), null))

    override suspend fun walk(walkId: String) = walkAnswer
}

@RunWith(AndroidJUnit4::class)
class SocialRepositoriesTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        GeoWoofDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val network = FakeSocialNetwork()
    private val people = FakePeopleNetwork()
    private val repository = DefaultSocialRepository(network, people, database.socialDao())
    private val peopleRepository = DefaultPeopleRepository(people)

    @After
    fun closeDatabase() = database.close()

    @Test
    fun all_the_pages_of_friends_reach_room_and_what_is_gone_disappears() = runTest {
        network.friendPages = mutableMapOf(
            null to NetworkPage(listOf(NetworkFriend(networkUser("1", "bea"), TIME)), "next"),
            "next" to NetworkPage(listOf(NetworkFriend(networkUser("2", "carlos"), TIME)), null),
        )
        repository.refreshFriends()
        assertEquals(setOf("bea", "carlos"), repository.friends.first().map { it.user.username }.toSet())

        network.friendPages =
            mutableMapOf(null to NetworkPage(listOf(NetworkFriend(networkUser("2", "carlos"), TIME)), null))
        repository.refreshFriends()

        val friend = repository.friends.first().single()
        assertEquals("carlos", friend.user.username)
        assertEquals("asset", friend.user.avatar?.assetId)
        assertEquals("Madrid", friend.user.city)
    }

    @Test
    fun the_requests_are_separated_by_direction() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest("1", networkUser("a", "ana"))), null))
        network.sent = Outcome.Success(NetworkPage(listOf(networkRequest("2", networkUser("b", "bea"), "Sent")), null))

        assertEquals(Outcome.Success(Unit), repository.refreshRequests())

        assertEquals(listOf("ana"), repository.receivedRequests.first().map { it.user.username })
        assertEquals(listOf("bea"), repository.sentRequests.first().map { it.user.username })
    }

    @Test
    fun without_a_connection_the_requests_stay_as_they_were() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        repository.refreshRequests()

        network.received = Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Failure(AppError.Network), repository.refreshRequests())
        assertEquals(1, repository.receivedRequests.first().size)
    }

    @Test
    fun sending_a_request_keeps_it_in_the_sent_list() = runTest {
        val answer = repository.sendRequest("bea-id")

        val relationship = (answer as Outcome.Success).value
        assertEquals(RelationshipStatus.RequestSent, relationship.status)
        assertEquals("request-1", relationship.requestId)
        assertEquals(listOf("request-1"), repository.sentRequests.first().map { it.id })
    }

    @Test
    fun asking_someone_who_had_asked_makes_friends_and_leaves_no_request() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        repository.refreshRequests()
        network.sendAnswer = Outcome.Success(networkRequest(status = "Accepted"))
        network.friendPages = mutableMapOf(null to NetworkPage(listOf(NetworkFriend(networkUser(), TIME)), null))

        val answer = repository.sendRequest("bea-id")

        assertEquals(RelationshipStatus.Friends, (answer as Outcome.Success).value.status)
        assertEquals(emptyList<Any>(), repository.receivedRequests.first())
        assertEquals(1, repository.friends.first().size)
    }

    @Test
    fun accepting_removes_the_request_and_brings_the_new_friend() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        repository.refreshRequests()
        network.friendPages = mutableMapOf(null to NetworkPage(listOf(NetworkFriend(networkUser(), TIME)), null))

        assertEquals(Outcome.Success(Unit), repository.acceptRequest("request-1"))

        assertEquals(emptyList<Any>(), repository.receivedRequests.first())
        assertEquals(listOf("bea"), repository.friends.first().map { it.user.username })
    }

    @Test
    fun rejecting_and_cancelling_remove_the_request() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest("1")), null))
        network.sent = Outcome.Success(NetworkPage(listOf(networkRequest("2", direction = "Sent")), null))
        repository.refreshRequests()

        repository.rejectRequest("1")
        repository.cancelRequest("2")

        assertEquals(emptyList<Any>(), repository.receivedRequests.first())
        assertEquals(emptyList<Any>(), repository.sentRequests.first())
        assertEquals(listOf("reject:1", "cancel:2"), network.calls)
    }

    @Test
    fun an_answer_the_server_rejects_updates_the_list_to_how_it_is_now() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        repository.refreshRequests()
        val conflict = AppError.Api(status = 409, code = "social.request_not_pending")
        network.answerResult = Outcome.Failure(conflict)
        network.received = Outcome.Success(NetworkPage(emptyList(), null))

        assertEquals(Outcome.Failure(conflict), repository.acceptRequest("request-1"))

        assertEquals(emptyList<Any>(), repository.receivedRequests.first())
    }

    @Test
    fun without_a_connection_an_answer_keeps_the_request() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        repository.refreshRequests()
        network.answerResult = Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Failure(AppError.Network), repository.rejectRequest("request-1"))

        assertEquals(1, repository.receivedRequests.first().size)
    }

    @Test
    fun removing_a_friend_only_removes_it_if_the_server_agrees() = runTest {
        network.friendPages = mutableMapOf(null to NetworkPage(listOf(NetworkFriend(networkUser(), TIME)), null))
        repository.refreshFriends()
        network.answerResult = Outcome.Failure(AppError.Network)

        repository.removeFriend("bea-id")
        assertEquals(1, repository.friends.first().size)

        network.answerResult = Outcome.Success(Unit)
        repository.removeFriend("bea-id")
        assertEquals(0, repository.friends.first().size)
    }

    @Test
    fun blocking_removes_that_person_from_friends_and_requests() = runTest {
        network.friendPages = mutableMapOf(null to NetworkPage(listOf(NetworkFriend(networkUser(), TIME)), null))
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        repository.refreshFriends()
        repository.refreshRequests()
        network.blocked = listOf(NetworkBlock(networkUser(), TIME))

        assertEquals(Outcome.Success(Unit), repository.block("bea-id"))

        assertEquals(emptyList<Any>(), repository.friends.first())
        assertEquals(emptyList<Any>(), repository.receivedRequests.first())
        assertEquals(listOf("bea"), repository.blockedUsers.first().map { it.user.username })
    }

    @Test
    fun unblocking_removes_it_from_the_list() = runTest {
        network.blocked = listOf(NetworkBlock(networkUser(), TIME))
        repository.refreshBlockedUsers()

        repository.unblock("bea-id")

        assertEquals(emptyList<Any>(), repository.blockedUsers.first())
    }

    @Test
    fun a_report_sends_the_reason_and_empty_details_as_nothing() = runTest {
        repository.report("bea-id", ReportReason.Harassment, "   ")
        repository.report("bea-id", ReportReason.Other, " insists ")

        assertEquals(ReportBody("User", "bea-id", "Harassment", null), network.reports[0])
        assertEquals(ReportBody("User", "bea-id", "Other", "insists"), network.reports[1])
    }

    @Test
    fun an_unknown_relationship_shows_no_action() = runTest {
        people.relationshipAnswer = NetworkRelationship("SomethingNew")

        assertEquals(RelationshipStatus.None, (repository.relationship("bea-id") as Outcome.Success).value.status)
    }

    @Test
    fun a_page_of_the_search_goes_on_with_its_cursor() = runTest {
        people.userPages = Outcome.Success(NetworkPage(listOf(networkUser()), "next"))
        val source = CursorPagingSource<NetworkUserSummary> { limit, cursor -> people.searchUsers("be", limit, cursor) }

        val first = source.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page
        source.load(PagingSource.LoadParams.Append("next", 20, false))

        assertEquals("next", first.nextKey)
        assertEquals(null, first.prevKey)
        assertEquals(listOf(null, "next"), people.cursors)
    }

    @Test
    fun a_page_that_fails_tells_why() = runTest {
        people.userPages = Outcome.Failure(AppError.Network)
        val source = CursorPagingSource<NetworkUserSummary> { limit, cursor -> people.searchUsers("be", limit, cursor) }

        val result = source.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Error

        assertEquals(AppError.Network, (result.throwable as RemoteLoadException).error)
    }

    @Test
    fun a_walk_of_someone_else_can_come_without_a_route() = runTest {
        people.walkAnswer = Outcome.Success(networkWalk(segments = emptyList()))

        val walk = (peopleRepository.walk("walk") as Outcome.Success).value

        assertEquals("bea-id", walk.ownerId)
        assertTrue(walk.route.isEmpty())
        assertEquals(WalkVisibility.Everyone, walk.walk.visibility)
        assertEquals(listOf("https://thumb"), walk.walk.photos.map { it.image?.thumbUrl })
    }

    @Test
    fun a_walk_of_someone_else_brings_what_can_be_seen_of_the_route() = runTest {
        people.walkAnswer = Outcome.Success(
            networkWalk(segments = listOf(NetworkWalkSegment("_p~iF~ps|U_ulLnnqC", listOf(60_000, 68_000)))),
        )

        val walk = (peopleRepository.walk("walk") as Outcome.Success).value

        assertEquals(listOf(60_000L, 68_000L), walk.route.single().points.map { it.offsetMs })
    }

    @Test
    fun a_pet_of_an_unknown_species_counts_as_other() = runTest {
        val found = NetworkPetSearchResult("luna", "Luna", "Unicorn", null, "Mezcla", null, networkUser()).toModel()

        assertEquals(PetSpecies.Other, found.species)
        assertEquals("bea", found.owner.username)
    }

    private fun networkWalk(segments: List<NetworkWalkSegment>) = NetworkWalk(
        id = "walk",
        ownerId = "bea-id",
        startedAt = TIME,
        endedAt = "2026-11-05T10:30:00Z",
        timeZone = "Europe/Madrid",
        petIds = listOf("luna"),
        visibility = "Everyone",
        note = "Por el Retiro",
        distanceM = 1000,
        durationS = 1800,
        movingTimeS = 1700,
        averageSpeedMps = 0.6,
        paceSecondsPerKm = 1700,
        previewPolylines = emptyList(),
        photos = listOf(NetworkImage("asset", "https://thumb", "https://medium")),
        updatedAt = TIME,
        segments = segments,
    )
}

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
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.network.PeopleNetworkDataSource
import com.geowoof.core.network.SocialNetworkDataSource
import com.geowoof.core.network.model.NetworkBlock
import com.geowoof.core.network.model.NetworkCounts
import com.geowoof.core.network.model.NetworkFollowRequest
import com.geowoof.core.network.model.NetworkImage
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPerson
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkPetSearchResult
import com.geowoof.core.network.model.NetworkProfile
import com.geowoof.core.network.model.NetworkRelationship
import com.geowoof.core.network.model.NetworkReport
import com.geowoof.core.network.model.NetworkUserSummary
import com.geowoof.core.network.model.NetworkWalk
import com.geowoof.core.network.model.NetworkWalkSegment
import com.geowoof.core.network.model.NetworkWalkStats
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
) = NetworkFollowRequest(id, user, direction, TIME)

@Suppress("TooManyFunctions")
private class FakeSocialNetwork : SocialNetworkDataSource {
    var received: Outcome<NetworkPage<NetworkFollowRequest>> = Outcome.Success(NetworkPage(emptyList(), null))
    var sent: Outcome<NetworkPage<NetworkFollowRequest>> = Outcome.Success(NetworkPage(emptyList(), null))
    var blocked: List<NetworkBlock> = emptyList()
    var relationshipAnswer: Outcome<NetworkRelationship> = Outcome.Success(NetworkRelationship("Following"))
    var answerResult: Outcome<Unit> = Outcome.Success(Unit)
    val reports = mutableListOf<ReportBody>()
    val calls = mutableListOf<String>()

    private fun relationship(call: String): Outcome<NetworkRelationship> {
        calls += call
        return relationshipAnswer
    }

    override suspend fun follow(userId: String) = relationship("follow:$userId")

    override suspend fun unfollow(userId: String) = relationship("unfollow:$userId")

    override suspend fun removeFollower(userId: String) = relationship("removeFollower:$userId")

    override suspend fun receivedRequests(limit: Int, cursor: String?) = received

    override suspend fun sentRequests(limit: Int, cursor: String?) = sent

    override suspend fun acceptRequest(id: String) = relationship("accept:$id")

    override suspend fun rejectRequest(id: String): Outcome<Unit> {
        calls += "reject:$id"
        return answerResult
    }

    override suspend fun cancelRequest(id: String): Outcome<Unit> {
        calls += "cancel:$id"
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

@Suppress("TooManyFunctions")
private class FakePeopleNetwork : PeopleNetworkDataSource {
    var relationshipAnswer = NetworkRelationship("Friends", followsYou = true)
    var userPages: Outcome<NetworkPage<NetworkUserSummary>> = Outcome.Success(NetworkPage(emptyList(), null))
    var walkAnswer: Outcome<NetworkWalk> = Outcome.Failure(AppError.Network)
    var petsAnswer: Outcome<NetworkPage<NetworkPet>> = Outcome.Success(NetworkPage(emptyList(), null))
    var profileAnswer = NetworkProfile("bea-id", "bea", "Bea", null, null, null, TIME)
    val cursors = mutableListOf<String?>()
    val lists = mutableListOf<String>()

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

    override suspend fun profile(userId: String) = Outcome.Success(profileAnswer)

    override suspend fun relationship(userId: String) = Outcome.Success(relationshipAnswer)

    override suspend fun counts(userId: String) = Outcome.Success(NetworkCounts(12, 8, 5))

    override suspend fun people(
        userId: String,
        list: String,
        limit: Int,
        cursor: String?,
    ): Outcome<NetworkPage<NetworkPerson>> {
        lists += list
        return Outcome.Success(NetworkPage(listOf(NetworkPerson(networkUser(), TIME)), null))
    }

    override suspend fun pets(userId: String) = petsAnswer

    override suspend fun walks(userId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>> =
        Outcome.Success(NetworkPage(emptyList(), null))

    override suspend fun walkStats(userId: String) = Outcome.Success(NetworkWalkStats(4, 1000, 3000, 2500, 2))

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
    private val repository = DefaultSocialRepository(network, people, database.socialDao(), database.feedDao())
    private val peopleRepository = DefaultPeopleRepository(people)

    @After
    fun closeDatabase() = database.close()

    @Test
    fun the_requests_are_separated_by_direction() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest("1", networkUser("a", "ana"))), null))
        network.sent = Outcome.Success(NetworkPage(listOf(networkRequest("2", networkUser("b", "bea"), "Sent")), null))

        assertEquals(Outcome.Success(Unit), repository.refreshRequests())

        assertEquals(listOf("ana"), repository.receivedRequests.first().map { it.user.username })
        assertEquals(listOf("bea"), repository.sentRequests.first().map { it.user.username })
    }

    @Test
    fun what_is_no_longer_on_the_server_disappears() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest("1"), networkRequest("2")), null))
        repository.refreshRequests()

        network.received = Outcome.Success(NetworkPage(listOf(networkRequest("2")), null))
        repository.refreshRequests()

        val request = repository.receivedRequests.first().single()
        assertEquals("2", request.id)
        assertEquals("asset", request.user.avatar?.assetId)
        assertEquals("Madrid", request.user.city)
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
    fun following_a_public_profile_answers_at_once_and_leaves_no_request() = runTest {
        val answer = repository.follow("bea-id")

        assertEquals(RelationshipStatus.Following, (answer as Outcome.Success).value.status)
        assertEquals(emptyList<Any>(), repository.sentRequests.first())
    }

    @Test
    fun following_a_private_profile_keeps_the_request_in_the_sent_list() = runTest {
        network.relationshipAnswer = Outcome.Success(NetworkRelationship("Requested", requestId = "request-1"))
        network.sent = Outcome.Success(NetworkPage(listOf(networkRequest(direction = "Sent")), null))

        val relationship = (repository.follow("bea-id") as Outcome.Success).value

        assertEquals(RelationshipStatus.Requested, relationship.status)
        assertEquals("request-1", relationship.requestId)
        assertEquals(listOf("request-1"), repository.sentRequests.first().map { it.id })
    }

    @Test
    fun the_relationship_says_if_they_follow_you_and_their_request() = runTest {
        network.relationshipAnswer = Outcome.Success(
            NetworkRelationship("None", followsYou = true, incomingRequestId = "request-2"),
        )

        val relationship = (repository.unfollow("bea-id") as Outcome.Success).value

        assertTrue(relationship.followsYou)
        assertEquals("request-2", relationship.incomingRequestId)
    }

    @Test
    fun unfollowing_removes_the_sent_request_but_not_theirs() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest("theirs")), null))
        network.sent = Outcome.Success(NetworkPage(listOf(networkRequest("mine", direction = "Sent")), null))
        repository.refreshRequests()
        network.relationshipAnswer = Outcome.Success(NetworkRelationship("None"))

        repository.unfollow("bea-id")

        assertEquals(emptyList<Any>(), repository.sentRequests.first())
        assertEquals(listOf("theirs"), repository.receivedRequests.first().map { it.id })
        assertEquals(listOf("unfollow:bea-id"), network.calls)
    }

    @Test
    fun a_follower_can_be_removed() = runTest {
        network.relationshipAnswer = Outcome.Success(NetworkRelationship("Following"))

        val relationship = (repository.removeFollower("bea-id") as Outcome.Success).value

        assertEquals(listOf("removeFollower:bea-id"), network.calls)
        assertEquals(false, relationship.followsYou)
    }

    @Test
    fun accepting_removes_the_request_and_answers_with_the_relationship() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        repository.refreshRequests()
        network.relationshipAnswer = Outcome.Success(NetworkRelationship("None", followsYou = true))

        val answer = repository.acceptRequest("request-1")

        assertTrue((answer as Outcome.Success).value.followsYou)
        assertEquals(emptyList<Any>(), repository.receivedRequests.first())
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
        network.relationshipAnswer = Outcome.Failure(conflict)
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
    fun blocking_removes_the_requests_of_that_person() = runTest {
        network.received = Outcome.Success(NetworkPage(listOf(networkRequest()), null))
        network.sent = Outcome.Success(NetworkPage(listOf(networkRequest("2", direction = "Sent")), null))
        repository.refreshRequests()
        network.blocked = listOf(NetworkBlock(networkUser(), TIME))

        assertEquals(Outcome.Success(Unit), repository.block("bea-id"))

        assertEquals(emptyList<Any>(), repository.receivedRequests.first())
        assertEquals(emptyList<Any>(), repository.sentRequests.first())
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
    fun the_numbers_and_the_totals_of_a_person_come_from_the_server() = runTest {
        assertEquals(Outcome.Success(ProfileCounts(12, 8, 5)), peopleRepository.counts("bea-id"))
        assertEquals(Outcome.Success(WalkTotals(4, 1000, 3000, 2500, 2)), peopleRepository.walkTotals("bea-id"))
    }

    @Test
    fun a_private_profile_says_so() = runTest {
        people.profileAnswer = NetworkProfile("bea-id", "bea", "bea", null, null, null, TIME, isPrivate = true)

        assertTrue((peopleRepository.profile("bea-id") as Outcome.Success).value.isPrivate)
    }

    @Test
    fun an_older_server_counts_as_public() = runTest {
        assertEquals(false, (peopleRepository.profile("bea-id") as Outcome.Success).value.isPrivate)
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

package com.geowoof.core.data.repository

import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.database.FeedDao
import com.geowoof.core.database.SocialDao
import com.geowoof.core.model.AppError
import com.geowoof.core.model.BlockedUser
import com.geowoof.core.model.FollowRequest
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.map
import com.geowoof.core.model.onFailure
import com.geowoof.core.model.onSuccess
import com.geowoof.core.network.PeopleNetworkDataSource
import com.geowoof.core.network.SocialNetworkDataSource
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.ReportBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Whom the signed-in person follows, their requests and their blocks. The requests and the blocks are read
 * from Room and everything is written online (docs/decisions/0009). If a person has changed something
 * meanwhile, the next refresh brings it.
 */
@Suppress("TooManyFunctions")
interface SocialRepository {
    val receivedRequests: Flow<List<FollowRequest>>
    val sentRequests: Flow<List<FollowRequest>>
    val blockedUsers: Flow<List<BlockedUser>>

    suspend fun refreshRequests(): Outcome<Unit>

    suspend fun refreshBlockedUsers(): Outcome<Unit>

    suspend fun relationship(userId: String): Outcome<Relationship>

    /** The answer is [RelationshipStatus.Requested] with a private profile, which has to accept. */
    suspend fun follow(userId: String): Outcome<Relationship>

    /** Stops following that person, or cancels the request to follow them. */
    suspend fun unfollow(userId: String): Outcome<Relationship>

    suspend fun removeFollower(userId: String): Outcome<Relationship>

    /** Lets that person follow you. It does not make you follow them. */
    suspend fun acceptRequest(requestId: String): Outcome<Relationship>

    suspend fun rejectRequest(requestId: String): Outcome<Unit>

    suspend fun cancelRequest(requestId: String): Outcome<Unit>

    suspend fun block(userId: String): Outcome<Unit>

    suspend fun unblock(userId: String): Outcome<Unit>

    suspend fun report(userId: String, reason: ReportReason, details: String?): Outcome<Unit>
}

@Suppress("TooManyFunctions")
internal class DefaultSocialRepository @Inject constructor(
    private val network: SocialNetworkDataSource,
    private val people: PeopleNetworkDataSource,
    private val dao: SocialDao,
    private val feed: FeedDao,
) : SocialRepository {
    override val receivedRequests: Flow<List<FollowRequest>> = requests(RequestDirection.Received)

    override val sentRequests: Flow<List<FollowRequest>> = requests(RequestDirection.Sent)

    override val blockedUsers: Flow<List<BlockedUser>> = dao.observeBlocked().map { rows -> rows.map { it.toModel() } }

    private fun requests(direction: RequestDirection) =
        dao.observeRequests(direction.name).map { rows -> rows.map { it.toModel() } }

    override suspend fun refreshRequests(): Outcome<Unit> {
        val received = allPages { cursor -> network.receivedRequests(PAGE_SIZE, cursor) }.map { items ->
            dao.replaceRequests(RequestDirection.Received.name, items.map { it.toEntity() })
        }
        val sent = allPages { cursor -> network.sentRequests(PAGE_SIZE, cursor) }.map { items ->
            dao.replaceRequests(RequestDirection.Sent.name, items.map { it.toEntity() })
        }
        return if (received is Outcome.Failure) received else sent
    }

    override suspend fun refreshBlockedUsers(): Outcome<Unit> =
        allPages { cursor -> network.blocks(PAGE_SIZE, cursor) }.map { items ->
            dao.replaceBlocked(items.map { it.toEntity() })
        }

    override suspend fun relationship(userId: String): Outcome<Relationship> =
        people.relationship(userId).map { it.toModel() }

    override suspend fun follow(userId: String): Outcome<Relationship> =
        network.follow(userId).map { it.toModel() }.onSuccess { relationship ->
            // The answer does not bring the person, and the list of sent requests shows them.
            if (relationship.status == RelationshipStatus.Requested) refreshRequests()
        }

    override suspend fun unfollow(userId: String): Outcome<Relationship> =
        network.unfollow(userId).map { it.toModel() }.onSuccess {
            dao.deleteRequestsWith(userId, RequestDirection.Sent.name)
        }

    override suspend fun removeFollower(userId: String): Outcome<Relationship> =
        network.removeFollower(userId).map { it.toModel() }

    override suspend fun acceptRequest(requestId: String): Outcome<Relationship> =
        answer(requestId) { network.acceptRequest(requestId).map { it.toModel() } }

    override suspend fun rejectRequest(requestId: String): Outcome<Unit> =
        answer(requestId) { network.rejectRequest(requestId) }

    override suspend fun cancelRequest(requestId: String): Outcome<Unit> =
        answer(requestId) { network.cancelRequest(requestId) }

    /** If the server rejects the answer, the request has changed: the list must show how it is now. */
    private suspend fun <T> answer(requestId: String, call: suspend () -> Outcome<T>): Outcome<T> = call()
        .onSuccess { dao.deleteRequest(requestId) }
        .onFailure { error -> if (error is AppError.Api) refreshRequests() }

    override suspend fun block(userId: String): Outcome<Unit> = network.block(userId).onSuccess {
        dao.deleteRequestsWith(userId)
        feed.deleteAllOf(userId)
        refreshBlockedUsers()
    }

    override suspend fun unblock(userId: String): Outcome<Unit> = network.unblock(userId).onSuccess {
        dao.deleteBlocked(userId)
    }

    override suspend fun report(userId: String, reason: ReportReason, details: String?): Outcome<Unit> =
        network.report(ReportBody(TARGET_USER, userId, reason.name, details?.trim()?.ifEmpty { null })).map { }

    /** These lists are short, and Room holds them whole. The limit only stops a server that never ends. */
    private suspend fun <T> allPages(load: suspend (cursor: String?) -> Outcome<NetworkPage<T>>): Outcome<List<T>> {
        val items = mutableListOf<T>()
        var cursor: String? = null
        var failure: Outcome.Failure? = null
        var pages = 0
        do {
            when (val page = load(cursor)) {
                is Outcome.Failure -> failure = page

                is Outcome.Success -> {
                    items += page.value.items
                    cursor = page.value.nextCursor
                }
            }
            pages++
        } while (failure == null && cursor != null && pages < MAX_PAGES)
        return failure ?: Outcome.Success(items)
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_PAGES = 100
        const val TARGET_USER = "User"
    }
}

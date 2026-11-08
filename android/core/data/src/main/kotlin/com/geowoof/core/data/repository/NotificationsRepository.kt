package com.geowoof.core.data.repository

import androidx.paging.PagingData
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.database.ChatDao
import com.geowoof.core.database.InboxCountersEntity
import com.geowoof.core.datastore.PromptsStorage
import com.geowoof.core.model.AppNotification
import com.geowoof.core.model.NotificationType
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.map
import com.geowoof.core.network.NotificationsNetworkDataSource
import com.geowoof.core.network.model.NetworkNotification
import com.geowoof.core.network.model.NetworkPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Where the id of this device for pushes comes from. Without Firebase there is none, and everything
 * else works.
 */
interface PushTokenProvider {
    /** Registers the device with the push service. The id arrives afterwards, through the messaging service. */
    suspend fun register()

    suspend fun currentToken(): String?
}

/**
 * The list comes from the server, like anything about other people. Only the number of the badge
 * is kept, to show it at once.
 */
interface NotificationsRepository {
    val unreadCount: Flow<Int>

    /** The types this version does not know are left out. */
    fun notifications(): Flow<PagingData<AppNotification>>

    suspend fun refreshUnreadCount(): Outcome<Unit>

    suspend fun markAllRead(): Outcome<Unit>

    /** So that pushes reach this person on this device. */
    suspend fun registerDevice(token: String): Outcome<Unit>

    /** So that this device is asked only once to allow them. It stays when the session ends, like the permission. */
    val wasAskedForPermission: Flow<Boolean>

    suspend fun markAskedForPermission()
}

internal class DefaultNotificationsRepository @Inject constructor(
    private val network: NotificationsNetworkDataSource,
    private val dao: ChatDao,
    private val prompts: PromptsStorage,
) : NotificationsRepository {
    override val unreadCount: Flow<Int> = dao.observeUnreadNotifications().map { it ?: 0 }.distinctUntilChanged()

    override fun notifications(): Flow<PagingData<AppNotification>> = cursorPager { limit, cursor ->
        network.notifications(limit, cursor).map { page ->
            NetworkPage(page.items.mapNotNull { it.toModelOrNull() }, page.nextCursor)
        }
    }

    override suspend fun refreshUnreadCount(): Outcome<Unit> = network.unreadCount().map { store(it) }

    override suspend fun markAllRead(): Outcome<Unit> = network.readAll().map { store(0) }

    override suspend fun registerDevice(token: String): Outcome<Unit> = network.registerDevice(token)

    override val wasAskedForPermission: Flow<Boolean> = prompts.prompts.map { it.askedForNotifications }

    override suspend fun markAskedForPermission() = prompts.update { it.copy(askedForNotifications = true) }

    private suspend fun store(count: Int) = dao.upsertCounters(InboxCountersEntity(unreadNotifications = count))
}

internal fun NetworkNotification.toModelOrNull(): AppNotification? {
    val known = NotificationType.entries.firstOrNull { it.name == type } ?: return null
    return AppNotification(id, known, actor.toModel(), postId, requestId, isRead, Instant.parse(createdAt))
}

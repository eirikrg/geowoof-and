package com.geowoof.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The conversations of this person, read without a connection. One that never had a message has no sender. */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    @Embedded(prefix = "other_") val other: UserColumns,
    val canSend: Boolean,
    val unreadCount: Int,
    val lastSenderId: String?,
    val lastBody: String?,
    val lastHasImage: Boolean,
    val otherLastReadMessageId: String?,
    /** The last message the server knows that this person has read, so as not to tell it twice. */
    val myLastReadMessageId: String?,
    val updatedAtEpochMs: Long,
)

/** [clientMessageId] is the identity here, because [id] does not exist until the server has the message. */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId", "sentAtEpochMs"), Index("status")],
)
data class ChatMessageEntity(
    @PrimaryKey val clientMessageId: String,
    val id: String?,
    val conversationId: String,
    val senderId: String,
    val body: String?,
    val imageAssetId: String?,
    val imageThumbUrl: String?,
    val imageMediumUrl: String?,
    val localImagePath: String?,
    val status: String,
    val errorCode: String?,
    val sentAtEpochMs: Long,
)

/** A single row, for what the shell shows on a badge. */
@Entity(tableName = "inbox_counters")
data class InboxCountersEntity(@PrimaryKey val id: Int = ROW_ID, val unreadNotifications: Int) {
    companion object {
        const val ROW_ID = 0
    }
}

@Dao
abstract class ChatDao {
    @Query("SELECT * FROM conversations WHERE lastSenderId IS NOT NULL ORDER BY updatedAtEpochMs DESC, id DESC")
    abstract fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract suspend fun conversation(id: String): ConversationEntity?

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations")
    abstract fun observeUnreadMessages(): Flow<Int>

    @Upsert
    abstract suspend fun upsertConversation(conversation: ConversationEntity)

    @Upsert
    protected abstract suspend fun upsertConversations(conversations: List<ConversationEntity>)

    /**
     * Those the server no longer gives, as after a block, take their messages with them. Not
     * those that never had a message, which it does not list, or with something waiting to be sent.
     */
    @Query(
        """
        DELETE FROM conversations
        WHERE id NOT IN (:keptIds)
            AND lastSenderId IS NOT NULL
            AND id NOT IN (SELECT conversationId FROM chat_messages WHERE status != 'Sent')
        """,
    )
    protected abstract suspend fun deleteOthers(keptIds: List<String>)

    /** Makes the list match what the server returned. */
    @Transaction
    open suspend fun replaceConversations(conversations: List<ConversationEntity>) {
        deleteOthers(conversations.map { it.id })
        upsertConversations(conversations)
    }

    @Query("DELETE FROM conversations WHERE id = :id")
    abstract suspend fun deleteConversation(id: String)

    @Query("UPDATE conversations SET otherLastReadMessageId = :messageId WHERE id = :id")
    abstract suspend fun setOtherLastRead(id: String, messageId: String)

    @Query("UPDATE conversations SET unreadCount = 0, myLastReadMessageId = :messageId WHERE id = :id")
    abstract suspend fun setMyLastRead(id: String, messageId: String)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :id ORDER BY sentAtEpochMs DESC, clientMessageId DESC")
    abstract fun observeMessages(id: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE clientMessageId = :clientMessageId")
    abstract suspend fun message(clientMessageId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE status = 'Pending' ORDER BY sentAtEpochMs, clientMessageId")
    abstract suspend fun pendingMessages(): List<ChatMessageEntity>

    @Query("SELECT id FROM chat_messages WHERE conversationId = :id AND id IS NOT NULL ORDER BY id DESC LIMIT 1")
    abstract suspend fun lastMessageId(id: String): String?

    @Query(
        """
        SELECT id FROM chat_messages
        WHERE conversationId = :id AND id IS NOT NULL AND senderId != :myUserId
        ORDER BY id DESC LIMIT 1
        """,
    )
    abstract suspend fun lastReceivedMessageId(id: String, myUserId: String): String?

    @Upsert
    abstract suspend fun upsertMessage(message: ChatMessageEntity)

    @Upsert
    abstract suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Query("SELECT unreadNotifications FROM inbox_counters WHERE id = ${InboxCountersEntity.ROW_ID}")
    abstract fun observeUnreadNotifications(): Flow<Int?>

    @Upsert
    abstract suspend fun upsertCounters(counters: InboxCountersEntity)
}

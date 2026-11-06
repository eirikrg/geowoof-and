package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.DeviceBody
import com.geowoof.core.network.model.DeviceTokenBody
import com.geowoof.core.network.model.NetworkChatEvent
import com.geowoof.core.network.model.NetworkConversation
import com.geowoof.core.network.model.NetworkConversationRead
import com.geowoof.core.network.model.NetworkCount
import com.geowoof.core.network.model.NetworkMessage
import com.geowoof.core.network.model.NetworkNotification
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkTyping
import com.geowoof.core.network.model.ReadBody
import com.geowoof.core.network.model.SendMessageBody
import com.geowoof.core.network.realtime.HubMessage
import com.geowoof.core.network.realtime.RealtimeConnection
import com.geowoof.core.network.realtime.RealtimeState
import com.geowoof.core.network.realtime.hubCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** The chat of docs/api.md, phase 8. */
internal interface ChatApi {
    @PUT("api/v1/conversations/direct/{userId}")
    suspend fun openWith(@Path("userId") userId: String): NetworkConversation

    @GET("api/v1/conversations")
    suspend fun conversations(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkConversation>

    @GET("api/v1/conversations/{id}")
    suspend fun conversation(@Path("id") id: String): NetworkConversation

    @GET("api/v1/conversations/{id}/messages")
    suspend fun messages(
        @Path("id") id: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
        @Query("after") after: String?,
    ): NetworkPage<NetworkMessage>

    @POST("api/v1/conversations/{id}/messages")
    suspend fun send(@Path("id") id: String, @Body body: SendMessageBody): NetworkMessage

    @POST("api/v1/conversations/{id}/read")
    suspend fun read(@Path("id") id: String, @Body body: ReadBody)
}

interface ChatNetworkDataSource {
    suspend fun openWith(userId: String): Outcome<NetworkConversation>

    suspend fun conversations(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkConversation>>

    suspend fun conversation(id: String): Outcome<NetworkConversation>

    /** From the newest to the oldest. */
    suspend fun messages(id: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkMessage>>

    /** From the oldest to the newest: what has arrived since that message. */
    suspend fun messagesAfter(id: String, messageId: String, limit: Int): Outcome<NetworkPage<NetworkMessage>>

    suspend fun send(id: String, body: SendMessageBody): Outcome<NetworkMessage>

    suspend fun read(id: String, messageId: String): Outcome<Unit>
}

internal class RetrofitChatNetworkDataSource @Inject constructor(private val api: ChatApi, private val json: Json) :
    ChatNetworkDataSource {
    override suspend fun openWith(userId: String) = apiCall(json) { api.openWith(userId) }

    override suspend fun conversations(limit: Int, cursor: String?) = apiCall(json) { api.conversations(limit, cursor) }

    override suspend fun conversation(id: String) = apiCall(json) { api.conversation(id) }

    override suspend fun messages(id: String, limit: Int, cursor: String?) = apiCall(json) {
        api.messages(id, limit, cursor, after = null)
    }

    override suspend fun messagesAfter(id: String, messageId: String, limit: Int) =
        apiCall(json) { api.messages(id, limit, cursor = null, after = messageId) }

    override suspend fun send(id: String, body: SendMessageBody) = apiCall(json) { api.send(id, body) }

    override suspend fun read(id: String, messageId: String) = apiCall(json) { api.read(id, ReadBody(messageId)) }
}

/** The notifications and the devices of docs/api.md, phase 8. */
internal interface NotificationsApi {
    @GET("api/v1/notifications")
    suspend fun notifications(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkNotification>

    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): NetworkCount

    @POST("api/v1/notifications/read-all")
    suspend fun readAll()

    @PUT("api/v1/devices")
    suspend fun registerDevice(@Body body: DeviceBody)

    @HTTP(method = "DELETE", path = "api/v1/devices", hasBody = true)
    suspend fun unregisterDevice(@Body body: DeviceTokenBody)
}

interface NotificationsNetworkDataSource {
    suspend fun notifications(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkNotification>>

    suspend fun unreadCount(): Outcome<Int>

    suspend fun readAll(): Outcome<Unit>

    suspend fun registerDevice(token: String): Outcome<Unit>

    suspend fun unregisterDevice(token: String): Outcome<Unit>
}

internal class RetrofitNotificationsNetworkDataSource @Inject constructor(
    private val api: NotificationsApi,
    private val json: Json,
) : NotificationsNetworkDataSource {
    override suspend fun notifications(limit: Int, cursor: String?) = apiCall(json) { api.notifications(limit, cursor) }

    override suspend fun unreadCount() = apiCall(json) { api.unreadCount().count }

    override suspend fun readAll() = apiCall(json) { api.readAll() }

    override suspend fun registerDevice(token: String) = apiCall(json) {
        api.registerDevice(DeviceBody(token, PLATFORM))
    }

    override suspend fun unregisterDevice(token: String) = apiCall(json) {
        api.unregisterDevice(DeviceTokenBody(token))
    }
}

private const val PLATFORM = "Android"

/** The chat hub of docs/realtime.md. It only tells: sending and reading go by REST. */
interface ChatRealtimeDataSource {
    val state: StateFlow<RealtimeState>

    val events: Flow<NetworkChatEvent>

    /** The connection stays open at least as long as [block] takes. */
    suspend fun <T> whileOpen(block: suspend () -> T): T

    suspend fun setTyping(conversationId: String): Outcome<Unit>
}

internal class SignalRChatRealtimeDataSource(private val connection: RealtimeConnection, private val json: Json) :
    ChatRealtimeDataSource {
    override val state: StateFlow<RealtimeState> = connection.state

    override val events: Flow<NetworkChatEvent> = connection.messages.mapNotNull { it.toEvent() }

    override suspend fun <T> whileOpen(block: suspend () -> T): T = connection.whileOpen(block)

    override suspend fun setTyping(conversationId: String) = hubCall {
        connection.invoke(SET_TYPING, mapOf("conversationId" to conversationId))
    }

    /** One that it does not understand is ignored: a newer server must not break the chat. */
    private fun HubMessage.toEvent(): NetworkChatEvent? = try {
        when (event) {
            MESSAGE_RECEIVED -> NetworkChatEvent.MessageReceived(json.decodeFromString<NetworkMessage>(payload))

            CONVERSATION_READ -> json.decodeFromString<NetworkConversationRead>(payload).let {
                NetworkChatEvent.ConversationRead(it.conversationId, it.userId, it.lastReadMessageId)
            }

            TYPING_CHANGED -> json.decodeFromString<NetworkTyping>(payload).let {
                NetworkChatEvent.TypingChanged(it.conversationId, it.userId)
            }

            else -> null
        }
    } catch (@Suppress("SwallowedException") exception: SerializationException) {
        null
    }

    companion object {
        const val MESSAGE_RECEIVED = "MessageReceived"
        const val CONVERSATION_READ = "ConversationRead"
        const val TYPING_CHANGED = "TypingChanged"

        private const val SET_TYPING = "SetTyping"
    }
}

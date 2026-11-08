package com.geowoof.core.notifications

import android.annotation.SuppressLint
import android.content.Context
import com.geowoof.core.common.ApplicationScope
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.ChatRepository
import com.geowoof.core.data.repository.ContentChange
import com.geowoof.core.data.repository.ContentChanges
import com.geowoof.core.data.repository.NotificationsRepository
import com.geowoof.core.data.repository.OpenConversations
import com.geowoof.core.data.repository.PushTokenProvider
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.HealthReminderItem
import com.geowoof.core.model.NotificationType
import com.geowoof.core.model.SessionState
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What a push says. All of it comes as text. */
internal sealed interface PushContent {
    data class Message(val conversationId: String, val senderName: String, val body: String, val hasImage: Boolean) :
        PushContent

    data class Social(
        val type: NotificationType,
        val actorId: String,
        val actorName: String,
        val postId: String?,
        val notificationId: String,
    ) : PushContent
}

/** What it does not understand is `null`: a newer server must not break anything here. */
internal fun Map<String, String>.toPushContent(): PushContent? = when (val type = this["type"]) {
    null -> null
    MESSAGE_TYPE -> toMessage()
    else -> toSocial(type)
}

private fun Map<String, String>.toMessage(): PushContent? {
    val conversationId = this["conversationId"]
    val senderName = this["senderName"]
    return if (conversationId != null && senderName != null) {
        PushContent.Message(conversationId, senderName, this["body"].orEmpty(), hasImage = this["hasImage"] == "true")
    } else {
        null
    }
}

private fun Map<String, String>.toSocial(type: String): PushContent? {
    val known = NotificationType.entries.firstOrNull { it.name == type } ?: return null
    val actorId = this["actorId"]
    val actorName = this["actorName"]
    val notificationId = this["notificationId"]
    return if (actorId != null && actorName != null && notificationId != null) {
        PushContent.Social(known, actorId, actorName, this["postId"], notificationId)
    } else {
        null
    }
}

private const val MESSAGE_TYPE = "Message"

/** Shows what arrives. Android does it, and the tests replace it. */
internal interface NotificationPresenter {
    fun show(content: PushContent)

    /** The notifications of a conversation are no longer news once it is open. */
    fun clearConversation(conversationId: String)

    /** Something of the health of a pet that is due on [dueOn]. */
    fun showHealthReminder(item: HealthReminderItem, dueOn: LocalDate)
}

/**
 * Decides what to do with a push. It brings the data up to date in any case, and it does not
 * interrupt if the conversation is on the screen. After signing out nothing is shown: the device
 * may not have been able to leave. A push can be what starts the process, so the session may still
 * be being read. Someone who starts following, or accepts a request, changes what the lists show,
 * as when the person does it: [ContentChanges] makes them load again when they come back.
 */
@Suppress("LongParameterList")
internal class PushHandler @Inject constructor(
    private val auth: AuthRepository,
    private val chat: ChatRepository,
    private val notifications: NotificationsRepository,
    private val social: SocialRepository,
    private val changes: ContentChanges,
    private val openConversations: OpenConversations,
    private val presenter: NotificationPresenter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun onPush(data: Map<String, String>) {
        if (auth.readSession() !is SessionState.SignedIn) return
        val content = data.toPushContent() ?: return
        when (content) {
            is PushContent.Message -> {
                scope.launch { chat.refreshConversations() }
                if (openConversations.current != content.conversationId) presenter.show(content)
            }

            is PushContent.Social -> {
                scope.launch { notifications.refreshUnreadCount() }
                if (content.type in REQUESTS) scope.launch { social.refreshRequests() }
                if (content.type in RELATIONSHIPS) changes.notify(ContentChange.Relationships)
                presenter.show(content)
            }
        }
    }
}

/** The ones that change a list of requests, which Room has. */
private val REQUESTS = setOf(NotificationType.FollowRequested, NotificationType.FollowRequestAccepted)

private val RELATIONSHIPS = setOf(
    NotificationType.UserFollowed,
    NotificationType.FollowRequestAccepted,
    NotificationType.FriendshipStarted,
)

private suspend fun AuthRepository.readSession(): SessionState = sessionState.first { it != SessionState.Loading }

/**
 * Tells the server where to send the pushes of the person signed in. The id always arrives through
 * the messaging service: when it is asked for, when it changes and from time to time on its own.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val auth: AuthRepository,
    private val notifications: NotificationsRepository,
    private val tokens: PushTokenProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun register() {
        if (auth.sessionState.value is SessionState.SignedIn) tokens.register()
    }

    internal fun onRegistered(id: String) {
        scope.launch {
            if (auth.readSession() is SessionState.SignedIn) notifications.registerDevice(id)
        }
    }
}

/** Without google-services.json there is no Firebase, and asking it for anything would throw. */
internal class FirebasePushTokenProvider @Inject constructor(@ApplicationContext private val context: Context) :
    PushTokenProvider {
    override suspend fun register() {
        if (FirebaseApp.getApps(context).isNotEmpty()) askFirebase { FirebaseMessaging.getInstance().register() }
    }

    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return askFirebase { FirebaseInstallations.getInstance().id }
    }

    /** Without Play services or without a connection there is no answer: it will be asked again next time. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun <T> askFirebase(task: () -> Task<T>): T? = try {
        withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                task().addOnCompleteListener { answer ->
                    continuation.resume(if (answer.isSuccessful) answer.result else null)
                }
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }

    private companion object {
        const val FIREBASE_TIMEOUT_MS = 5_000L
    }
}

@Singleton
internal class OpenConversationsRegistry @Inject constructor(private val presenter: NotificationPresenter) :
    OpenConversations {
    @Volatile
    override var current: String? = null
        private set

    override fun open(conversationId: String) {
        current = conversationId
        presenter.clearConversation(conversationId)
    }

    override fun close(conversationId: String) {
        if (current == conversationId) current = null
    }
}

/** The lint check still asks for `onNewToken`, which is deprecated: `onRegistered` tells the same with a FID. */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
@AndroidEntryPoint
class GeoWoofMessagingService : FirebaseMessagingService() {
    @Inject
    internal lateinit var handler: PushHandler

    @Inject
    lateinit var registrar: PushRegistrar

    /** It runs on a worker thread, and the process may end as soon as it returns. */
    override fun onMessageReceived(message: RemoteMessage) = runBlocking { handler.onPush(message.data) }

    override fun onRegistered(fid: String) = registrar.onRegistered(fid)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NotificationsModule {
    @Binds
    fun bindsPushTokenProvider(provider: FirebasePushTokenProvider): PushTokenProvider

    @Binds
    fun bindsOpenConversations(registry: OpenConversationsRegistry): OpenConversations

    @Binds
    fun bindsNotificationPresenter(presenter: AndroidNotificationPresenter): NotificationPresenter
}

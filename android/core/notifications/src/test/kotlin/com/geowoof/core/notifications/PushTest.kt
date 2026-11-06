package com.geowoof.core.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.model.NotificationType
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeChatRepository
import com.geowoof.core.testing.FakeNotificationsRepository
import com.geowoof.core.testing.FakeOpenConversations
import com.geowoof.core.testing.FakePushTokenProvider
import com.geowoof.core.testing.TestUsers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private val MESSAGE = mapOf(
    "type" to "Message",
    "conversationId" to "c1",
    "messageId" to "m1",
    "senderId" to "bea-id",
    "senderName" to "Bea",
    "body" to "¿Salimos?",
    "hasImage" to "false",
)
private val LIKE = mapOf(
    "type" to "PostLiked",
    "notificationId" to "n1",
    "actorId" to "bea-id",
    "actorName" to "Bea",
    "postId" to "p1",
)
private val SIGNED_IN = SessionState.SignedIn(TestUsers.user())

class PushContentTest {
    @Test
    fun a_message_is_read_with_all_it_says() {
        assertEquals(PushContent.Message("c1", "Bea", "¿Salimos?", hasImage = false), MESSAGE.toPushContent())
    }

    @Test
    fun a_photo_comes_without_text() {
        val photo = MESSAGE + mapOf("body" to "", "hasImage" to "true")

        assertEquals(PushContent.Message("c1", "Bea", "", hasImage = true), photo.toPushContent())
    }

    @Test
    fun a_notification_is_read_with_its_kind() {
        assertEquals(PushContent.Social(NotificationType.PostLiked, "bea-id", "Bea", "p1", "n1"), LIKE.toPushContent())
    }

    @Test
    fun what_is_not_understood_is_ignored() {
        assertNull(emptyMap<String, String>().toPushContent())
        assertNull(
            mapOf(
                "type" to "SomethingNew",
                "actorId" to "a",
                "actorName" to "A",
                "notificationId" to "n",
            ).toPushContent(),
        )
        assertNull((MESSAGE - "conversationId").toPushContent())
        assertNull((LIKE - "actorId").toPushContent())
    }
}

private class RecordingPresenter : NotificationPresenter {
    val shown = mutableListOf<PushContent>()
    val cleared = mutableListOf<String>()

    override fun show(content: PushContent) {
        shown += content
    }

    override fun clearConversation(conversationId: String) {
        cleared += conversationId
    }
}

class PushHandlerTest {
    private val chat = FakeChatRepository()
    private val notifications = FakeNotificationsRepository()
    private val open = FakeOpenConversations()
    private val presenter = RecordingPresenter()

    private fun TestScope.handler(auth: FakeAuthRepository) =
        PushHandler(auth, chat, notifications, open, presenter, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun a_message_is_shown_and_the_list_brought_up_to_date() = runTest {
        handler(FakeAuthRepository(SIGNED_IN)).onPush(MESSAGE)

        assertEquals(listOf<PushContent>(PushContent.Message("c1", "Bea", "¿Salimos?", false)), presenter.shown)
        assertEquals(listOf("refresh"), chat.calls)
    }

    @Test
    fun the_conversation_on_the_screen_does_not_interrupt() = runTest {
        open.open("c1")

        handler(FakeAuthRepository(SIGNED_IN)).onPush(MESSAGE)

        assertEquals(emptyList<PushContent>(), presenter.shown)
        assertEquals(listOf("refresh"), chat.calls)
    }

    @Test
    fun another_conversation_does() = runTest {
        open.open("c2")

        handler(FakeAuthRepository(SIGNED_IN)).onPush(MESSAGE)

        assertEquals(1, presenter.shown.size)
    }

    @Test
    fun a_notification_is_shown_and_its_badge_brought_up_to_date() = runTest {
        handler(FakeAuthRepository(SIGNED_IN)).onPush(LIKE)

        assertEquals(1, presenter.shown.size)
        assertEquals(1, notifications.refreshes)
    }

    @Test
    fun after_signing_out_nothing_is_shown_or_asked() = runTest {
        handler(FakeAuthRepository(SessionState.SignedOut)).onPush(MESSAGE)

        assertEquals(emptyList<PushContent>(), presenter.shown)
        assertEquals(emptyList<String>(), chat.calls)
    }

    @Test
    fun a_push_that_starts_the_process_waits_for_the_session_to_be_read() = runTest {
        val auth = FakeAuthRepository(SessionState.Loading)
        val handler = handler(auth)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { handler.onPush(MESSAGE) }
        runCurrent()
        assertEquals(emptyList<PushContent>(), presenter.shown)

        auth.setSession(SIGNED_IN)
        runCurrent()

        assertEquals(1, presenter.shown.size)
    }

    @Test
    fun what_is_not_understood_shows_nothing() = runTest {
        handler(FakeAuthRepository(SIGNED_IN)).onPush(mapOf("type" to "SomethingNew"))

        assertEquals(emptyList<PushContent>(), presenter.shown)
    }
}

class PushRegistrarTest {
    private val notifications = FakeNotificationsRepository()
    private val tokens = FakePushTokenProvider()

    private fun TestScope.registrar(auth: FakeAuthRepository) =
        PushRegistrar(auth, notifications, tokens, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun someone_signed_in_registers_the_device() = runTest {
        registrar(FakeAuthRepository(SIGNED_IN)).register()

        assertEquals(1, tokens.registrations)
    }

    @Test
    fun nobody_signed_in_does_not() = runTest {
        registrar(FakeAuthRepository(SessionState.SignedOut)).register()

        assertEquals(0, tokens.registrations)
    }

    @Test
    fun the_id_goes_to_the_server_when_it_arrives() = runTest {
        registrar(FakeAuthRepository(SIGNED_IN)).onRegistered("fid-1")

        assertEquals(listOf("fid-1"), notifications.devices)
    }

    @Test
    fun without_a_session_it_stays_here() = runTest {
        registrar(FakeAuthRepository(SessionState.SignedOut)).onRegistered("fid-1")

        assertEquals(emptyList<String>(), notifications.devices)
    }

    @Test
    fun the_id_that_arrives_while_the_session_is_being_read_waits_for_it() = runTest {
        val auth = FakeAuthRepository(SessionState.Loading)
        registrar(auth).onRegistered("fid-1")
        assertEquals(emptyList<String>(), notifications.devices)

        auth.setSession(SIGNED_IN)
        runCurrent()

        assertEquals(listOf("fid-1"), notifications.devices)
    }
}

class OpenConversationsRegistryTest {
    private val presenter = RecordingPresenter()
    private val registry = OpenConversationsRegistry(presenter)

    @Test
    fun opening_one_clears_its_notification() {
        registry.open("c1")

        assertEquals("c1", registry.current)
        assertEquals(listOf("c1"), presenter.cleared)
    }

    @Test
    fun closing_another_one_changes_nothing() {
        registry.open("c1")

        registry.close("c2")
        assertEquals("c1", registry.current)

        registry.close("c1")
        assertNull(registry.current)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AndroidNotificationPresenterTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val presenter = AndroidNotificationPresenter(context)
    private val system get() = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    @Before
    fun setUp() {
        val packages = shadowOf(context.packageManager)
        val launcher = ComponentName(context, "com.geowoof.Launcher")
        packages.addActivityIfNotPresent(launcher)
        packages.addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private fun linkOf(index: Int = 0) = shadowOf(system.allNotifications[index].contentIntent).savedIntent.dataString

    @Test
    fun a_conversation_keeps_growing_in_a_single_notification() {
        presenter.show(PushContent.Message("c1", "Bea", "Hola", hasImage = false))
        presenter.show(PushContent.Message("c1", "Bea", "¿Salimos?", hasImage = false))

        assertEquals(1, system.allNotifications.size)
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
            system.allNotifications.single(),
        )
        assertEquals(listOf("Hola", "¿Salimos?"), style?.messages?.map { it.text.toString() })
        assertEquals("geowoof://chat?conversationId=c1", linkOf())
    }

    @Test
    fun two_conversations_are_two_notifications() {
        presenter.show(PushContent.Message("c1", "Bea", "Hola", hasImage = false))
        presenter.show(PushContent.Message("c2", "Carlos", "Hola", hasImage = false))

        assertEquals(2, system.allNotifications.size)
    }

    @Test
    fun a_photo_says_it_is_a_photo() {
        presenter.show(PushContent.Message("c1", "Bea", "", hasImage = true))

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
            system.allNotifications.single(),
        )
        assertEquals("Photo", style?.messages?.single()?.text.toString())
    }

    @Test
    fun opening_the_conversation_clears_it() {
        presenter.show(PushContent.Message("c1", "Bea", "Hola", hasImage = false))

        presenter.clearConversation("c1")

        assertEquals(0, system.allNotifications.size)
    }

    @Test
    fun each_kind_of_notification_leads_to_where_it_happened() {
        fun social(type: NotificationType, postId: String? = null) =
            PushContent.Social(type, "bea-id", "Bea", postId, "n-${type.name}")

        val links = mapOf(
            social(NotificationType.PostLiked, "p1") to "geowoof://post/p1",
            social(NotificationType.CommentAdded, "p1") to "geowoof://post/p1",
            social(NotificationType.PostReposted, "p1") to "geowoof://post/p1",
            social(NotificationType.UserMentioned, "p1") to "geowoof://post/p1",
            social(NotificationType.UserMentioned) to "geowoof://notifications",
            social(NotificationType.FollowRequested) to "geowoof://follow-requests",
            social(NotificationType.UserFollowed) to "geowoof://user/bea-id",
            social(NotificationType.FollowRequestAccepted) to "geowoof://user/bea-id",
            social(NotificationType.FriendshipStarted) to "geowoof://user/bea-id",
        )

        links.forEach { (content, link) ->
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
            presenter.show(content)
            assertEquals(link, linkOf())
        }
    }

    @Test
    fun a_notification_says_who_did_what() {
        presenter.show(PushContent.Social(NotificationType.PostLiked, "bea-id", "Bea", "p1", "n1"))

        val notification = system.allNotifications.single()
        assertEquals("Bea liked your post", notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertTrue(notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun without_the_permission_nothing_is_posted_and_nothing_breaks() {
        system.setNotificationsEnabled(false)

        presenter.show(PushContent.Message("c1", "Bea", "Hola", hasImage = false))

        assertEquals(0, system.allNotifications.size)
    }
}

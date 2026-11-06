package com.geowoof.core.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.net.toUri
import com.geowoof.core.model.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The notifications of the system, built here so that they are in the language of the device.
 * Tapping one opens the app with a link that only the app itself uses: there is no intent filter,
 * so no other app can open these screens.
 */
internal class AndroidNotificationPresenter @Inject constructor(@ApplicationContext private val context: Context) :
    NotificationPresenter {
    private val manager = NotificationManagerCompat.from(context)

    override fun show(content: PushContent) {
        createChannels()
        when (content) {
            is PushContent.Message -> showMessage(content)
            is PushContent.Social -> showSocial(content)
        }
    }

    override fun clearConversation(conversationId: String) = manager.cancel(conversationId, MESSAGE_NOTIFICATION_ID)

    private fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_MESSAGES, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.core_notifications_channel_messages))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_SOCIAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.core_notifications_channel_social))
                    .build(),
            ),
        )
    }

    /** One per conversation, which keeps growing while it has not been read. */
    private fun showMessage(message: PushContent.Message) {
        val previous = manager.activeNotifications
            .firstOrNull { it.tag == message.conversationId && it.id == MESSAGE_NOTIFICATION_ID }
            ?.notification
            ?.let(NotificationCompat.MessagingStyle::extractMessagingStyleFromNotification)
        val me = Person.Builder().setName(context.getString(R.string.core_notifications_you)).build()
        val style = previous ?: NotificationCompat.MessagingStyle(me)
        val sender = Person.Builder().setName(message.senderName).build()
        style.addMessage(message.text(), System.currentTimeMillis(), sender)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.core_notifications_ic_notification)
            .setColor(BRAND_COLOR)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openLink("geowoof://chat?conversationId=${message.conversationId}"))
            .setAutoCancel(true)
            .build()
        post(message.conversationId, MESSAGE_NOTIFICATION_ID, notification)
    }

    private fun PushContent.Message.text(): String = when {
        body.isNotEmpty() -> body
        hasImage -> context.getString(R.string.core_notifications_photo)
        else -> ""
    }

    private fun showSocial(social: PushContent.Social) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.core_notifications_ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(context.getString(social.type.textRes(), social.actorName))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(openLink(social.link()))
            .setAutoCancel(true)
            .build()
        post(social.notificationId, SOCIAL_NOTIFICATION_ID, notification)
    }

    private fun PushContent.Social.link(): String = when (type) {
        NotificationType.PostLiked,
        NotificationType.CommentAdded,
        NotificationType.PostReposted,
        NotificationType.UserMentioned,
        ->
            postId?.let { "geowoof://post/$it" } ?: "geowoof://notifications"

        NotificationType.FollowRequested -> "geowoof://follow-requests"

        NotificationType.UserFollowed, NotificationType.FollowRequestAccepted, NotificationType.FriendshipStarted ->
            "geowoof://user/$actorId"
    }

    private fun NotificationType.textRes(): Int = when (this) {
        NotificationType.UserFollowed -> R.string.core_notifications_user_followed
        NotificationType.FollowRequested -> R.string.core_notifications_follow_requested
        NotificationType.FollowRequestAccepted -> R.string.core_notifications_follow_request_accepted
        NotificationType.FriendshipStarted -> R.string.core_notifications_friendship_started
        NotificationType.PostLiked -> R.string.core_notifications_post_liked
        NotificationType.CommentAdded -> R.string.core_notifications_comment_added
        NotificationType.PostReposted -> R.string.core_notifications_post_reposted
        NotificationType.UserMentioned -> R.string.core_notifications_user_mentioned
    }

    private fun openLink(link: String): PendingIntent {
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        val intent = launch
            .setAction(Intent.ACTION_VIEW)
            .setData(link.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, link.hashCode(), intent, flags)
    }

    /** Without the permission nothing happens, and the badges of the app say the same. */
    @SuppressLint("MissingPermission")
    private fun post(tag: String, id: Int, notification: android.app.Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(tag, id, notification)
    }

    private companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_SOCIAL = "social"
        const val MESSAGE_NOTIFICATION_ID = 3001
        const val SOCIAL_NOTIFICATION_ID = 3002
    }
}

/** The primary color of the theme, for the icon of the notification when it is opened. */
private const val BRAND_COLOR = 0xFF3C6847.toInt()

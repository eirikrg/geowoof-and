package com.geowoof.sync.work

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.geowoof.core.data.repository.ChatSendResult
import com.geowoof.core.data.repository.ChatSendScheduler
import com.geowoof.core.data.repository.ChatSender
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Sends what is waiting in the chat. A retry is safe, because the server recognises each message
 * (docs/decisions/0014).
 */
@HiltWorker
class SendMessagesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sender: ChatSender,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (sender.sendPending()) {
        ChatSendResult.Done -> Result.success()
        ChatSendResult.Retry -> Result.retry()
    }

    /** Expedited work needs it before Android 12. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(applicationContext.getString(R.string.sync_work_messages_channel))
            .build()
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.sync_work_ic_cloud_upload)
            .setContentTitle(applicationContext.getString(R.string.sync_work_sending_messages))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "chat_uploads"
        const val NOTIFICATION_ID = 2002
    }
}

/**
 * One work for the whole queue, which goes in order. If it is already running it goes on after it,
 * so that what was added meanwhile is also sent.
 */
internal class WorkManagerChatSendScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    ChatSendScheduler {
    override fun enqueue() {
        val request = OneTimeWorkRequestBuilder<SendMessagesWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    private companion object {
        const val TAG = "send-messages"
        const val BACKOFF_SECONDS = 15L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ChatSendModule {
    @Binds
    fun bindsChatSendScheduler(scheduler: WorkManagerChatSendScheduler): ChatSendScheduler
}

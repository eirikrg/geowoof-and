package com.geowoof.sync.work

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.geowoof.core.data.repository.WalkSyncResult
import com.geowoof.core.data.repository.WalkSynchronizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Uploads one walk. A walk rejected by the server stays as Failed, and the user decides (docs/decisions/0009). */
@HiltWorker
class SyncWalkWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: WalkSynchronizer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val walkId = inputData.getString(KEY_WALK_ID) ?: return Result.failure()
        return when (synchronizer.sync(walkId)) {
            WalkSyncResult.Done -> Result.success()
            WalkSyncResult.Retry -> Result.retry()
            is WalkSyncResult.Failed -> Result.failure()
        }
    }

    /** Expedited work needs it before Android 12. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(applicationContext.getString(R.string.sync_work_channel))
            .build()
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.sync_work_ic_cloud_upload)
            .setContentTitle(applicationContext.getString(R.string.sync_work_uploading))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    internal companion object {
        const val KEY_WALK_ID = "walkId"
        private const val CHANNEL_ID = "walk_uploads"
        private const val NOTIFICATION_ID = 2001
    }
}

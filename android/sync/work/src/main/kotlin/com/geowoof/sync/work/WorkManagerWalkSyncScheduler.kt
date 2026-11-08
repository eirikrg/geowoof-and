package com.geowoof.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.geowoof.core.data.repository.WalkSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** One unique work per walk, when there is a connection and with exponential backoff. */
internal class WorkManagerWalkSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    WalkSyncScheduler {
    override fun enqueue(walkId: String) {
        val request = OneTimeWorkRequestBuilder<SyncWalkWorker>()
            .setInputData(workDataOf(SyncWalkWorker.KEY_WALK_ID to walkId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(walkId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    internal companion object {
        const val TAG = "sync-walk"
        private const val BACKOFF_SECONDS = 30L

        fun uniqueName(walkId: String) = "sync-walk-$walkId"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SyncModule {
    @Binds
    fun bindsWalkSyncScheduler(scheduler: WorkManagerWalkSyncScheduler): WalkSyncScheduler
}

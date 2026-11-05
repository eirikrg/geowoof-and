package com.geowoof.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.geowoof.core.data.repository.WalkSyncResult
import com.geowoof.core.data.repository.WalkSynchronizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWalkWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private suspend fun run(
        result: WalkSyncResult,
        input: Data = workDataOf(SyncWalkWorker.KEY_WALK_ID to "walk"),
    ): Result {
        val synchronizer = object : WalkSynchronizer {
            override suspend fun sync(walkId: String) = result
        }
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = SyncWalkWorker(appContext, workerParameters, synchronizer)
        }
        return TestListenableWorkerBuilder<SyncWalkWorker>(context, input).setWorkerFactory(factory).build().doWork()
    }

    @Test
    fun an_uploaded_walk_ends_the_work() = runTest {
        assertEquals(Result.success(), run(WalkSyncResult.Done))
    }

    @Test
    fun without_a_connection_or_with_a_server_problem_it_is_retried() = runTest {
        assertEquals(Result.retry(), run(WalkSyncResult.Retry))
    }

    @Test
    fun a_walk_rejected_by_the_server_is_not_retried() = runTest {
        assertEquals(Result.failure(), run(WalkSyncResult.Failed("walks.invalid_track")))
    }

    @Test
    fun work_without_a_walk_fails() = runTest {
        assertEquals(Result.failure(), run(WalkSyncResult.Done, Data.EMPTY))
    }

    @Test
    fun each_walk_has_its_own_work_that_waits_for_a_connection() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = WorkManagerWalkSyncScheduler(context)

        scheduler.enqueue("walk")
        scheduler.enqueue("walk")

        val manager = WorkManager.getInstance(context)
        val active = manager.getWorkInfosForUniqueWork(
            WorkManagerWalkSyncScheduler.uniqueName("walk"),
        ).get().filterNot {
            it.state.isFinished
        }
        assertEquals(NetworkType.CONNECTED, active.single().constraints.requiredNetworkType)

        scheduler.cancelAll()

        assertTrue(manager.getWorkInfosByTag(WorkManagerWalkSyncScheduler.TAG).get().all { it.state.isFinished })
    }
}

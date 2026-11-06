package com.geowoof.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.geowoof.core.data.repository.ChatSendResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SendMessagesWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private suspend fun run(result: ChatSendResult): Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = SendMessagesWorker(appContext, workerParameters) { result }
        }
        return TestListenableWorkerBuilder<SendMessagesWorker>(context).setWorkerFactory(factory).build().doWork()
    }

    @Test
    fun an_empty_queue_ends_the_work() = runTest {
        assertEquals(Result.success(), run(ChatSendResult.Done))
    }

    @Test
    fun a_problem_of_the_connection_tries_again_later() = runTest {
        assertEquals(Result.retry(), run(ChatSendResult.Retry))
    }

    @Test
    fun the_queue_is_one_work_that_waits_for_a_connection_and_can_be_cancelled() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = WorkManagerChatSendScheduler(context)
        val workManager = WorkManager.getInstance(context)

        scheduler.enqueue()
        scheduler.enqueue()

        val works = workManager.getWorkInfosForUniqueWork("send-messages").get()
        assertTrue(works.isNotEmpty())
        assertTrue(works.all { it.constraints.requiredNetworkType == NetworkType.CONNECTED })

        scheduler.cancelAll()

        assertTrue(
            workManager.getWorkInfosForUniqueWork("send-messages").get().all {
                it.state ==
                    WorkInfo.State.CANCELLED
            },
        )
    }
}

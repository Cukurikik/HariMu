package com.harimu.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.harimu.app.core.data.AppDatabase
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.jobs.EnqueueOutcome
import com.harimu.app.core.jobs.JobManager
import com.harimu.app.core.jobs.JobQueuePolicy
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class JobQueueAndManagerTest {

    private lateinit var app: HariMuApp
    private lateinit var db: AppDatabase
    private lateinit var storage: FileStorageManager
    private lateinit var jobManager: JobManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Context>() as HariMuApp
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)

        storage = app.container.storage
        db = app.container.database
        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 1024 * 1024, 512 * 1024, 512 * 1024)

        jobManager = JobManager(
            context = app,
            workManager = WorkManager.getInstance(app),
            jobDao = db.jobDao(),
            storage = storage,
            queuePolicy = JobQueuePolicy(maxParallel = 2)
        )
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
        storage.clearTempCache()
    }

    @Test
    fun queuePolicyEnforcesMaxTwoParallelJobsInFifoOrder() {
        val policy = JobQueuePolicy(maxParallel = 2)

        assertEquals(EnqueueOutcome.START_NOW, policy.enqueue("j1"))
        assertEquals(EnqueueOutcome.START_NOW, policy.enqueue("j2"))
        assertEquals(EnqueueOutcome.QUEUED, policy.enqueue("j3"))
        assertEquals(EnqueueOutcome.QUEUED, policy.enqueue("j4"))

        val snap1 = policy.snapshot()
        assertEquals(setOf("j1", "j2"), snap1.activeIds)
        assertEquals(listOf("j3", "j4"), snap1.queuedIds)

        // Completing j1 promotes j3 immediately
        assertEquals("j3", policy.completeAndDequeueNext("j1"))
        assertEquals(setOf("j2", "j3"), policy.snapshot().activeIds)
        assertEquals(listOf("j4"), policy.snapshot().queuedIds)

        // Cancelling active j2 promotes j4
        val (removedJ2, promotedFromJ2) = policy.cancel("j2")
        assertTrue(removedJ2)
        assertEquals("j4", promotedFromJ2)
        assertEquals(setOf("j3", "j4"), policy.snapshot().activeIds)
        assertTrue(policy.snapshot().queuedIds.isEmpty())

        // Restoring keeps at most 2 active and leaves excess queued
        val toStart = policy.restore(runningIds = listOf("r1", "r2", "r3"), queuedIds = listOf("q1"))
        assertEquals(listOf("r1", "r2"), toStart)
        assertEquals(listOf("r3", "q1"), policy.snapshot().queuedIds)
    }

    @Test
    fun dummyWorkerRunsCompletesWritesHistoryAndCleansJobTemp() = runBlocking {
        val stubTool = ToolRegistry.findById("VID-05")!!
        val jobId = "worker-test-1"
        db.jobDao().upsert(
            JobEntity(
                jobId = jobId,
                workRequestId = "",
                toolId = stubTool.id,
                toolTitle = stubTool.title(false),
                groupName = stubTool.group.name,
                isDummy = true,
                status = ToolJobWorker.STATUS_QUEUED,
                progressPercent = 0,
                progressMessage = "Queued"
            )
        )

        val worker = TestListenableWorkerBuilder<ToolJobWorker>(app)
            .setInputData(
                workDataOf(
                    ToolJobWorker.KEY_JOB_ID to jobId,
                    ToolJobWorker.KEY_TOOL_ID to stubTool.id,
                    ToolJobWorker.KEY_TOOL_TITLE to stubTool.title(false),
                    ToolJobWorker.KEY_GROUP_NAME to stubTool.group.name,
                    ToolJobWorker.KEY_IS_DUMMY to true,
                    ToolJobWorker.KEY_IS_ENGLISH to false
                )
            )
            .build()

        val result = worker.doWork()
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)

        val stored = db.jobDao().getById(jobId)
        assertNotNull(stored)
        assertEquals(ToolJobWorker.STATUS_SUCCEEDED, stored!!.status)
        assertEquals(100, stored.progressPercent)
        assertTrue(stored.outputPath.contains("HariMu_VID-05_"))
        assertFalse(java.io.File(storage.tempDir, "job_$jobId").exists())

        val history = db.historyDao().getAllOnce()
        assertEquals(1, history.size)
        assertEquals("VID-05", history.first().toolId)
    }

    @Test
    fun cancelJobMarksCancelledClearsTempAndPromotesQueuedJob() = runBlocking {
        val t1 = ToolRegistry.findById("CORE-02")!!
        val t2 = ToolRegistry.findById("AUD-03")!!
        val t3 = ToolRegistry.findById("PDF-10")!!

        val j1 = jobManager.enqueueDummyJob(t1, isEnglish = false)
        val j2 = jobManager.enqueueDummyJob(t2, isEnglish = false)
        val j3 = jobManager.enqueueDummyJob(t3, isEnglish = false)

        assertEquals(setOf(j1.jobId, j2.jobId), jobManager.queuePolicy.snapshot().activeIds)
        assertEquals(listOf(j3.jobId), jobManager.queuePolicy.snapshot().queuedIds)

        jobManager.cancelJob(j1.jobId)

        val cancelled = db.jobDao().getById(j1.jobId)
        assertEquals(ToolJobWorker.STATUS_CANCELLED, cancelled?.status)
        assertEquals(setOf(j2.jobId, j3.jobId), jobManager.queuePolicy.snapshot().activeIds)
        assertTrue(jobManager.queuePolicy.snapshot().queuedIds.isEmpty())
    }

    @Test
    fun recoverPendingJobsRestoresActiveAndQueuedLimitsAfterRestart() = runBlocking {
        // Simulate 3 interrupted jobs saved in Room before process death
        db.jobDao().upsert(
            JobEntity("rec-1", "", "CORE-02", "Prompt Video", "CORE", true, ToolJobWorker.STATUS_RUNNING, 50, "50%", createdAt = 1L)
        )
        db.jobDao().upsert(
            JobEntity("rec-2", "", "VID-08", "Slow Mo", "VIDEO", true, ToolJobWorker.STATUS_QUEUED, 0, "Queued", createdAt = 2L)
        )
        db.jobDao().upsert(
            JobEntity("rec-3", "", "AUD-05", "Noise Gate", "AUDIO", true, ToolJobWorker.STATUS_QUEUED, 0, "Queued", createdAt = 3L)
        )

        jobManager.recoverPendingJobs()

        val snap = jobManager.queuePolicy.snapshot()
        assertEquals(setOf("rec-1", "rec-2"), snap.activeIds)
        assertEquals(listOf("rec-3"), snap.queuedIds)

        val rec1 = db.jobDao().getById("rec-1")
        val rec3 = db.jobDao().getById("rec-3")
        assertTrue(rec1?.workRequestId?.isNotBlank() == true)
        assertEquals("", rec3?.workRequestId)
        assertNull(rec1?.errorMessage?.takeIf { it.isNotBlank() })
    }
}

package com.harimu.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.harimu.app.core.cloud.CloudProvider
import com.harimu.app.core.cloud.UploadQueue
import com.harimu.app.core.data.AppDatabase
import com.harimu.app.core.data.HistoryEntity
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.jobs.JobManager
import com.harimu.app.core.jobs.JobQueuePolicy
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolInputCodec
import com.harimu.app.core.toolapi.ToolOutputCodec
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import com.harimu.app.feature.history.HistoryScreen
import com.harimu.app.feature.home.HomeScreen
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class JobCenterAndMigrationTest {

    private lateinit var app: HariMuApp
    private lateinit var db: AppDatabase
    private lateinit var storage: FileStorageManager
    private lateinit var jobManager: JobManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Context>() as HariMuApp
        val wmConfig = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, wmConfig)

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
    fun migration4To5PreservesLegacyRowsAndSupportsP11Columns() = runBlocking {
        val dbName = "migration_4_5_test.db"
        app.deleteDatabase(dbName)
        val legacyHelper = object : SQLiteOpenHelper(app, dbName, null, 4) {
            override fun onCreate(sqlite: SQLiteDatabase) {
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `toolId` TEXT NOT NULL, `toolTitle` TEXT NOT NULL, `groupName` TEXT NOT NULL, `outputPath` TEXT NOT NULL, `outputName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `summary` TEXT NOT NULL, `success` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (`toolId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`toolId`))"
                )
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vault_keys` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alias` TEXT NOT NULL, `algorithm` TEXT NOT NULL, `keySizeBits` INTEGER NOT NULL, `publicKeyPem` TEXT NOT NULL, `privateKeyPem` TEXT NOT NULL, `fingerprintSha256` TEXT NOT NULL, `keystoreBacked` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS `jobs` (`jobId` TEXT NOT NULL, `workRequestId` TEXT NOT NULL, `toolId` TEXT NOT NULL, `toolTitle` TEXT NOT NULL, `groupName` TEXT NOT NULL, `isDummy` INTEGER NOT NULL, `status` TEXT NOT NULL, `progressPercent` INTEGER NOT NULL, `progressMessage` TEXT NOT NULL, `outputPath` TEXT NOT NULL, `outputMime` TEXT NOT NULL, `summary` TEXT NOT NULL, `errorMessage` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`jobId`))"
                )
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_projects` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `clipsJson` TEXT NOT NULL, `outputResolution` TEXT NOT NULL, `lastExportPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_projects` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `tracksJson` TEXT NOT NULL, `masterVolume` REAL NOT NULL, `lastExportPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                sqlite.execSQL(
                    "INSERT INTO `history` (`toolId`, `toolTitle`, `groupName`, `outputPath`, `outputName`, `mimeType`, `fileSizeBytes`, `summary`, `success`, `createdAt`) VALUES ('IMG-02', 'Smart Resizer', 'IMAGE', '/tmp/out.png', 'out.png', 'image/png', 1024, 'Resized 50%', 1, 1000)"
                )
                sqlite.execSQL(
                    "INSERT INTO `jobs` (`jobId`, `workRequestId`, `toolId`, `toolTitle`, `groupName`, `isDummy`, `status`, `progressPercent`, `progressMessage`, `outputPath`, `outputMime`, `summary`, `errorMessage`, `createdAt`, `updatedAt`) VALUES ('legacy-job-1', '', 'VID-01', 'Trim Video', 'VIDEO', 0, 'SUCCEEDED', 100, 'Selesai', '/tmp/trim.mp4', 'video/mp4', 'Trim ok', '', 1000, 2000)"
                )
            }

            override fun onUpgrade(sqlite: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        legacyHelper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(app, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .build()
        try {
            val histories = migratedDb.historyDao().getAllOnce()
            assertEquals(1, histories.size)
            assertEquals("IMG-02", histories.first().toolId)
            assertEquals("", histories.first().jobId)
            assertEquals("", histories.first().outputsJson)

            val legacyJob = migratedDb.jobDao().getById("legacy-job-1")
            assertNotNull(legacyJob)
            assertEquals("VID-01", legacyJob!!.toolId)
            assertEquals(ToolJobWorker.MODE_WORKER, legacyJob.executionMode)
            assertFalse(legacyJob.isRetryable)
            assertEquals(0L, legacyJob.startedAt)
            assertEquals(0L, legacyJob.completedAt)

            migratedDb.jobDao().upsert(
                JobEntity(
                    jobId = "new-p11-job",
                    workRequestId = "",
                    toolId = "CNV-23",
                    toolTitle = "JSON/XML",
                    groupName = "CONVERTER",
                    isDummy = false,
                    status = ToolJobWorker.STATUS_PREPARING,
                    executionMode = ToolJobWorker.MODE_IN_PROCESS,
                    inputSummary = "text (12 chars)",
                    isRetryable = true
                )
            )
            val saved = migratedDb.jobDao().getById("new-p11-job")
            assertEquals(ToolJobWorker.MODE_IN_PROCESS, saved?.executionMode)
            assertTrue(saved?.isRetryable == true)
        } finally {
            migratedDb.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test
    fun inProcessLifecycleTracksPreparingRunningCompleteAndInterruptsOnRecovery() = runBlocking {
        val tool = ToolRegistry.findById("CNV-23")!!
        val input = ToolInput(textInput = "{\"hello\":\"world\"}")
        val tracked = jobManager.startInProcessJob(tool, input, isEnglish = true)
        assertEquals(ToolJobWorker.STATUS_PREPARING, tracked.status)
        assertEquals(ToolJobWorker.MODE_IN_PROCESS, tracked.executionMode)
        assertTrue(tracked.startedAt > 0L)

        jobManager.updateInProcessJob(tracked.jobId, ToolJobWorker.STATUS_RUNNING, 65, "Converting...")
        val running = db.jobDao().getById(tracked.jobId)!!
        assertEquals(ToolJobWorker.STATUS_RUNNING, running.status)
        assertEquals(65, running.progressPercent)

        val outFile = File(storage.outputsDir, "p11_inproc.xml").apply { writeText("<hello>world</hello>") }
        try {
            jobManager.completeInProcessJob(
                tracked.jobId,
                ToolResult(
                    outputs = listOf(ToolOutputFile(outFile, "application/xml", outFile.name)),
                    summary = "Converted to XML",
                    textOutput = "<hello>world</hello>"
                )
            )
            val done = db.jobDao().getById(tracked.jobId)!!
            assertEquals(ToolJobWorker.STATUS_SUCCEEDED, done.status)
            assertEquals(100, done.progressPercent)
            assertEquals("<hello>world</hello>", done.textOutput)
            assertTrue(done.outputsJson.contains("p11_inproc.xml"))
            assertTrue(done.completedAt >= done.startedAt)

            // Process death simulation: another IN_PROCESS job stuck in PREPARING gets marked INTERRUPTED
            val orphaned = jobManager.startInProcessJob(tool, input, isEnglish = false)
            jobManager.recoverPendingJobs()
            val interrupted = db.jobDao().getById(orphaned.jobId)!!
            assertEquals(ToolJobWorker.STATUS_INTERRUPTED, interrupted.status)
            assertTrue(interrupted.errorMessage.isNotBlank())
            assertTrue(jobManager.canRetry(interrupted))
        } finally {
            outFile.delete()
        }
    }

    @Test
    fun expiredFileInputCannotRetryAndRetryJobReEnqueuesValidJob() = runBlocking {
        val imgTool = ToolRegistry.findById("IMG-02")!!
        val missingInputFile = File(storage.tempDir, "deleted_source_${System.nanoTime()}.png")
        val missingUri = Uri.fromFile(missingInputFile)
        val inputJsonMissing = ToolInputCodec.encode(ToolInput(uris = listOf(missingUri)))

        db.jobDao().upsert(
            JobEntity(
                jobId = "expired-input-job",
                workRequestId = "",
                toolId = imgTool.id,
                toolTitle = imgTool.title(true),
                groupName = imgTool.group.name,
                isDummy = false,
                status = ToolJobWorker.STATUS_QUEUED,
                progressPercent = 0,
                progressMessage = "Queued",
                executionMode = ToolJobWorker.MODE_WORKER,
                inputJson = inputJsonMissing,
                isRetryable = true
            )
        )

        jobManager.recoverPendingJobs()
        val recovered = db.jobDao().getById("expired-input-job")!!
        assertEquals(ToolJobWorker.STATUS_INTERRUPTED, recovered.status)
        assertFalse(recovered.isRetryable)
        assertFalse(jobManager.canRetry(recovered))
        assertNull(jobManager.retryJob("expired-input-job", isEnglish = true))

        // Valid dummy/stub job retries cleanly and clearFinishedJobs leaves active ones untouched
        val stubTool = ToolRegistry.findById("VID-05")!!
        val failedDummy = JobEntity(
            jobId = "retryable-dummy",
            workRequestId = "",
            toolId = stubTool.id,
            toolTitle = stubTool.title(true),
            groupName = stubTool.group.name,
            isDummy = true,
            status = ToolJobWorker.STATUS_FAILED,
            progressPercent = 0,
            errorMessage = "Disk error",
            isEnglish = true,
            executionMode = ToolJobWorker.MODE_WORKER,
            isRetryable = true
        )
        db.jobDao().upsert(failedDummy)
        assertTrue(jobManager.canRetry(failedDummy))

        val retried = jobManager.retryJob("retryable-dummy", isEnglish = true)
        assertNotNull(retried)
        assertEquals(stubTool.id, retried!!.toolId)
        assertEquals(ToolJobWorker.STATUS_QUEUED, retried.status)

        jobManager.clearFinishedJobs()
        assertNull(db.jobDao().getById("retryable-dummy"))
        assertNotNull(db.jobDao().getById(retried.jobId))
    }

    @Test
    fun uploadQueueTasksFlowReflectsEnqueueAndClear() = runBlocking {
        val queue = UploadQueue(app)
        queue.clearFinished()
        assertTrue(queue.tasksFlow.value.isEmpty())

        val uploadFile = File(app.cacheDir, "upload_flow_sample.txt").apply { writeText("hello cloud") }
        try {
            val task = queue.enqueue(uploadFile, "text/plain", CloudProvider.S3_R2)
            assertEquals(1, queue.tasksFlow.value.size)
            assertEquals(task.id, queue.tasksFlow.value.first().id)

            queue.removeTask(task.id)
            assertTrue(queue.tasksFlow.value.isEmpty())
        } finally {
            uploadFile.delete()
            queue.clearFinished()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun historyScreenShowsJobCenterFiltersMissingFileAndOpensDetailSheet() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val outMissing = File(activity.cacheDir, "missing_p11_out.png").apply { delete() }

            runBlocking {
                container.workspace.clearHistory()
                container.jobManager.clearFinishedJobs()
                container.uploadQueue.clearFinished()

                container.database.jobDao().upsert(
                    JobEntity(
                        jobId = "ui-active-1",
                        workRequestId = "",
                        toolId = "VID-01",
                        toolTitle = "Trim Video UI",
                        groupName = "VIDEO",
                        isDummy = true,
                        status = ToolJobWorker.STATUS_RUNNING,
                        progressPercent = 45,
                        progressMessage = "Encoding 45%",
                        executionMode = ToolJobWorker.MODE_WORKER
                    )
                )
                container.database.jobDao().upsert(
                    JobEntity(
                        jobId = "ui-failed-1",
                        workRequestId = "",
                        toolId = "VID-05",
                        toolTitle = "Video Stub UI",
                        groupName = "VIDEO",
                        isDummy = true,
                        status = ToolJobWorker.STATUS_FAILED,
                        progressPercent = 0,
                        errorMessage = "Insufficient space",
                        executionMode = ToolJobWorker.MODE_WORKER,
                        isRetryable = true
                    )
                )
                container.workspace.recordHistory(
                    toolId = "IMG-02",
                    toolTitle = "Smart Resizer",
                    groupName = "IMAGE",
                    outputFile = outMissing,
                    mimeType = "image/png",
                    summary = "Resized output",
                    jobId = "ui-hist-job",
                    outputsJson = ToolOutputCodec.encode(
                        listOf(ToolOutputCodec.Entry(outMissing.absolutePath, "image/png", outMissing.name))
                    )
                )
            }

            try {
                setTestContent(activity, isEnglish = true) {
                    HistoryScreen(container = container)
                }

                val nodes = waitUntilNodes(activity) { current ->
                    findByTag(current, "job_overview_card") != null &&
                        findByTag(current, "job_card_ui-active-1") != null
                }
                assertNotNull(findByTag(nodes, "job_overview_card"))
                assertNotNull(findByTag(nodes, "job_search_input"))
                assertNotNull(findByTag(nodes, "cancel_job_ui-active-1"))

                val scrolledFailed = scrollUntilNodes(activity) { current ->
                    findByTag(current, "retry_job_ui-failed-1") != null
                }
                assertNotNull(findByTag(scrolledFailed, "retry_job_ui-failed-1"))

                val scrolledHistory = scrollUntilNodes(activity) { current ->
                    current.any {
                        it.config.getOrNull(SemanticsProperties.Text)
                            ?.joinToString(" ")
                            ?.contains("no longer exists", ignoreCase = true) == true
                    }
                }
                val missingWarning = scrolledHistory.firstOrNull {
                    it.config.getOrNull(SemanticsProperties.Text)
                        ?.joinToString(" ")
                        ?.contains("no longer exists", ignoreCase = true) == true
                }
                assertNotNull("Expected missing-file warning on deleted output", missingWarning)

                // Scroll back up to filter chips and switch to Attention filter
                val topNodes = scrollUntilNodes(activity, deltaY = -420f) { current ->
                    findByTag(current, "job_filter_attention") != null
                }
                clickNode(findByTag(topNodes, "job_filter_attention")!!)
                val attentionNodes = waitUntilNodes(activity) { current ->
                    findByTag(current, "job_card_ui-active-1") == null &&
                        findByTag(current, "job_card_ui-failed-1") != null
                }
                assertNotNull(findByTag(attentionNodes, "retry_job_ui-failed-1"))

                // Open detail modal sheet by clicking the failed job card
                clickNode(findByTag(attentionNodes, "job_card_ui-failed-1")!!)
                val sheetNodes = waitUntilNodes(activity) { current ->
                    findByTag(current, "job_detail_sheet") != null
                }
                assertNotNull(findByTag(sheetNodes, "job_detail_sheet"))
                assertNotNull(findByTag(sheetNodes, "job_detail_retry_btn"))
            } finally {
                runBlocking {
                    container.workspace.clearHistory()
                    container.jobManager.deleteJob("ui-active-1")
                    container.jobManager.deleteJob("ui-failed-1")
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun homeScreenDisplaysJobCenterBannerOnlyWhenJobsNeedVisibility() {
        withMainActivity { activity ->
            var opened = false
            setTestContent(activity, isEnglish = true) {
                HomeScreen(
                    isEnglish = true,
                    favoriteIds = emptySet(),
                    recentToolIds = emptyList(),
                    onOpenTool = {},
                    onOpenGroup = {},
                    onToggleFavorite = {},
                    activeJobCount = 0,
                    attentionJobCount = 0,
                    onOpenJobCenter = { opened = true }
                )
            }
            assertNull(findByTag(collectSemanticsNodes(activity.window.decorView), "home_job_center_banner"))

            setTestContent(activity, isEnglish = true) {
                HomeScreen(
                    isEnglish = true,
                    favoriteIds = emptySet(),
                    recentToolIds = emptyList(),
                    onOpenTool = {},
                    onOpenGroup = {},
                    onToggleFavorite = {},
                    activeJobCount = 1,
                    attentionJobCount = 2,
                    onOpenJobCenter = { opened = true }
                )
            }
            val banner = findByTag(collectSemanticsNodes(activity.window.decorView), "home_job_center_banner")
            assertNotNull("Expected home_job_center_banner when active/attention jobs exist", banner)
            clickNode(banner!!)
            flushMainUi(activity)
            assertTrue(opened)
        }
    }

    private fun withMainActivity(block: (MainActivity) -> Unit) {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            flushMainUi(activity)
            block(activity)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            flushMainUi()
        }
    }

    private fun setTestContent(
        activity: MainActivity,
        isEnglish: Boolean,
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        content: @Composable () -> Unit
    ) {
        activity.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val targetLocale = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("id")
            val localizedConfig = remember(baseConfig, targetLocale) {
                Configuration(baseConfig).apply { setLocale(targetLocale) }
            }
            val localizedResources = remember(baseContext, localizedConfig) {
                baseContext.createConfigurationContext(localizedConfig).resources
            }
            val localizedContext = remember(baseContext, localizedResources) {
                object : ContextWrapper(baseContext) {
                    override fun getResources(): Resources = localizedResources
                }
            }
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalResources provides localizedResources,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity
            ) {
                HariMuTheme(
                    themeMode = themeMode,
                    isSystemDark = themeMode == ThemeMode.DARK || themeMode == ThemeMode.AMOLED,
                    dynamicColor = false,
                    content = content
                )
            }
        }
        flushMainUi(activity)
    }

    private fun flushMainUi(activity: MainActivity? = null, frames: Int = 4) {
        fun forceLayoutTree(view: View) {
            view.forceLayout()
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    forceLayoutTree(view.getChildAt(i))
                }
            }
        }
        repeat(frames) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            activity?.window?.decorView?.let { decor ->
                val dm = activity.resources.displayMetrics
                forceLayoutTree(decor)
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
                )
                decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
                decor.viewTreeObserver.dispatchOnPreDraw()
            }
        }
    }

    private fun waitUntilNodes(
        activity: MainActivity,
        predicate: (List<SemanticsNode>) -> Boolean
    ): List<SemanticsNode> {
        repeat(50) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            if (predicate(nodes)) return nodes
            Thread.sleep(20)
        }
        return collectSemanticsNodes(activity.window.decorView)
    }

    private fun collectSemanticsNodes(rootView: View, mergingEnabled: Boolean = false): List<SemanticsNode> {
        val roots = mutableListOf<RootForTest>()
        fun walk(view: View) {
            if (view is RootForTest) roots += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(rootView)
        return roots.flatMap { root ->
            root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = mergingEnabled)
        }
    }

    private fun scrollUntilNodes(
        activity: MainActivity,
        deltaY: Float = 360f,
        maxSteps: Int = 8,
        predicate: (List<SemanticsNode>) -> Boolean
    ): List<SemanticsNode> {
        var nodes = waitUntilNodes(activity, predicate)
        if (predicate(nodes)) return nodes
        repeat(maxSteps) {
            val scrollable = nodes.firstOrNull {
                it.config.getOrNull(SemanticsActions.ScrollBy) != null
            } ?: return nodes
            val action = scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action ?: return nodes
            action.invoke(0f, deltaY)
            nodes = waitUntilNodes(activity, predicate)
            if (predicate(nodes)) return nodes
        }
        return nodes
    }

    private fun findByTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode? =
        nodes.firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == tag
        }

    private fun clickNode(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
        action.invoke()
    }
}

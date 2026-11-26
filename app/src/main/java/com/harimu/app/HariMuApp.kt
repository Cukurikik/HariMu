package com.harimu.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.harimu.app.core.data.AppDatabase
import com.harimu.app.core.data.SettingsRepository
import com.harimu.app.core.data.WorkspaceRepository
import com.harimu.app.core.cloud.CloudCredentialStore
import com.harimu.app.core.cloud.CloudUploader
import com.harimu.app.core.cloud.UploadQueue
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.ai.AiClient
import com.harimu.app.core.files.ModelManager
import com.harimu.app.core.jobs.JobManager
import com.harimu.app.core.prayer.PrayerTimesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val database = AppDatabase.create(app)
    val storage = FileStorageManager(app)
    val modelManager = ModelManager(app)
    val settings = SettingsRepository(app)
    val workspace = WorkspaceRepository(database, settings, storage)
    val cloudStore = CloudCredentialStore(app)
    val cloudUploader = CloudUploader()
    val uploadQueue = UploadQueue(app, cloudUploader)
    val aiClient: AiClient by lazy { AiClient(settings) }
    val prayerTimes: PrayerTimesRepository by lazy { PrayerTimesRepository() }
    val jobManager: JobManager by lazy {
        JobManager(
            context = app,
            workManager = WorkManager.getInstance(app),
            jobDao = database.jobDao(),
            storage = storage
        )
    }
}

class HariMuApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            if (container.settings.current().autoCleanTemp) {
                container.storage.clearTempCache()
            }
            container.jobManager.recoverPendingJobs()
        }
    }
}

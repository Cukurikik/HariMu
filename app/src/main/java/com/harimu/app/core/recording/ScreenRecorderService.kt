package com.harimu.app.core.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.harimu.app.MainActivity
import com.harimu.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

object ScreenRecordingController {
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    internal var engine: ScreenRecorderEngine? = null
    internal var projection: MediaProjection? = null
    internal var paused = false

    internal fun update(state: RecordingUiState) {
        _state.value = state
        paused = state.paused
    }

    fun pause() {
        engine?.pause()
        _state.value = _state.value.copy(paused = true)
    }

    fun resume() {
        engine?.resume()
        _state.value = _state.value.copy(paused = false)
    }

    fun stop() {
        engine?.stop()
    }

    fun reset() {
        if (!_state.value.running) _state.value = RecordingUiState()
    }

    internal fun releaseAll() {
        engine?.release()
        engine = null
        projection?.stop()
        projection = null
        _state.value = _state.value.copy(running = false)
    }
}

/**
 * Foreground service tipe `mediaProjection` (CORE-05). Wajib: Android 14+ menghentikan sesi
 * MediaProjection bila service tidak berjalan dengan tipe yang benar, dan persetujuan pengguna
 * diminta per sesi oleh layar.
 */
class ScreenRecorderService : Service() {

    private var overlay: RecordingOverlay? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            ScreenRecordingController.state.collectLatest { state ->
                if (state.running) {
                    ServiceCompat.startForeground(
                        this@ScreenRecorderService,
                        NOTIFICATION_ID,
                        buildNotification(state.paused),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        } else {
                            0
                        }
                    )
                } else {
                    overlay?.hide()
                    ServiceCompat.stopForeground(this@ScreenRecorderService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(false),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    } else {
                        0
                    }
                )
                startRecording(resultCode, data, intent)
            }
            ACTION_PAUSE -> ScreenRecordingController.pause()
            ACTION_RESUME -> ScreenRecordingController.resume()
            ACTION_STOP -> ScreenRecordingController.stop()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent, intent: Intent) {
        if (ScreenRecordingController.state.value.running) return
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (projection == null) {
            ScreenRecordingController.update(
                RecordingUiState(error = getString(R.string.recording_projection_denied))
            )
            stopSelf()
            return
        }
        val config = ScreenRecorderEngine.Config(
            width = intent.getIntExtra(EXTRA_WIDTH, 1280),
            height = intent.getIntExtra(EXTRA_HEIGHT, 720),
            fps = intent.getIntExtra(EXTRA_FPS, 30),
            bitrateBps = intent.getIntExtra(EXTRA_BITRATE, 6_000_000),
            audioMode = runCatching {
                ScreenRecorderEngine.AudioMode.valueOf(
                    intent.getStringExtra(EXTRA_AUDIO) ?: ScreenRecorderEngine.AudioMode.MIC.name
                )
            }.getOrDefault(ScreenRecorderEngine.AudioMode.MIC),
            container = runCatching {
                ScreenRecorderEngine.Container.valueOf(
                    intent.getStringExtra(EXTRA_CONTAINER) ?: ScreenRecorderEngine.Container.MP4.name
                )
            }.getOrDefault(ScreenRecorderEngine.Container.MP4)
        )
        val engine = ScreenRecorderEngine(applicationContext)
        ScreenRecordingController.engine = engine
        ScreenRecordingController.projection = projection
        engine.listenerPaused = { ScreenRecordingController.update(ScreenRecordingController.state.value.copy(paused = true)) }
        engine.listenerResumed = { ScreenRecordingController.update(ScreenRecordingController.state.value.copy(paused = false)) }
        ScreenRecordingController.update(RecordingUiState(running = true))
        runCatching {
            engine.start(projection, config, object : ScreenRecorderEngine.Listener {
                override fun onFinished(file: File, durationMs: Long) {
                    ScreenRecordingController.update(
                        RecordingUiState(running = false, output = file, elapsedMs = durationMs)
                    )
                    overlay?.hide()
                }

                override fun onError(message: String) {
                    ScreenRecordingController.update(RecordingUiState(running = false, error = message))
                    overlay?.hide()
                }
            })
        }.onFailure { error ->
            ScreenRecordingController.update(
                RecordingUiState(running = false, error = error.message ?: "Perekaman layar gagal")
            )
            stopSelf()
        }

        if (intent.getBooleanExtra(EXTRA_OVERLAY, false) && Settings.canDrawOverlays(this)) {
            overlay = RecordingOverlay(
                context = this,
                onToggle = {
                    if (ScreenRecordingController.state.value.paused) ScreenRecordingController.resume()
                    else ScreenRecordingController.pause()
                },
                onStop = { ScreenRecordingController.stop() }
            ).also { it.show() }
        }
    }

    override fun onDestroy() {
        overlay?.hide()
        ScreenRecordingController.releaseAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(paused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ScreenRecorderService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ScreenRecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_harimu)
            .setContentTitle(getString(R.string.recording_screen_title))
            .setContentText(getString(if (paused) R.string.recording_paused else R.string.recording_active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(if (paused) R.string.action_resume else R.string.action_pause), toggleIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = getString(R.string.recording_channel_desc) }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "harimu_recording"
        const val NOTIFICATION_ID = 4212
        const val ACTION_START = "com.harimu.app.recording.SCREEN_START"
        const val ACTION_PAUSE = "com.harimu.app.recording.SCREEN_PAUSE"
        const val ACTION_RESUME = "com.harimu.app.recording.SCREEN_RESUME"
        const val ACTION_STOP = "com.harimu.app.recording.SCREEN_STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_WIDTH = "width"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_FPS = "fps"
        private const val EXTRA_BITRATE = "bitrate"
        private const val EXTRA_AUDIO = "audio"
        private const val EXTRA_CONTAINER = "container"
        private const val EXTRA_OVERLAY = "overlay"

        fun intent(
            context: Context,
            resultCode: Int,
            data: Intent,
            config: ScreenRecorderEngine.Config,
            overlay: Boolean
        ): Intent = Intent(context, ScreenRecorderService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_RESULT_CODE, resultCode)
            .putExtra(EXTRA_RESULT_DATA, data)
            .putExtra(EXTRA_WIDTH, config.width)
            .putExtra(EXTRA_HEIGHT, config.height)
            .putExtra(EXTRA_FPS, config.fps)
            .putExtra(EXTRA_BITRATE, config.bitrateBps)
            .putExtra(EXTRA_AUDIO, config.audioMode.name)
            .putExtra(EXTRA_CONTAINER, config.container.name)
            .putExtra(EXTRA_OVERLAY, overlay)
    }
}

/** Tombol mengambang opsional (butuh izin "tampil di atas aplikasi lain"). */
class RecordingOverlay(
    private val context: Context,
    private val onToggle: () -> Unit,
    private val onStop: () -> Unit
) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show() {
        if (!Settings.canDrawOverlays(context) || view != null) return
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC11151C.toInt())
            setPadding(16, 8, 16, 8)
            elevation = 8f
        }
        fun button(label: String, action: () -> Unit) = TextView(context).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            setPadding(24, 12, 24, 12)
            setOnClickListener { action() }
            row.addView(this)
        }
        button("⏯") { onToggle() }
        button("⏹") { onStop() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }
        runCatching { manager.addView(row, params) }.onSuccess { view = row }
    }

    fun hide() {
        view?.let { runCatching { manager.removeView(it) } }
        view = null
    }
}

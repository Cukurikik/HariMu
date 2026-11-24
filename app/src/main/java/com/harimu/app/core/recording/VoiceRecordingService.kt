package com.harimu.app.core.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/** Status rekaman yang dipakai UI studio (suara maupun layar). */
data class RecordingUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0L,
    val output: File? = null,
    val error: String? = null,
    val note: String = ""
)

/**
 * Jembatan antara UI studio dan [VoiceRecorderEngine] yang hidup di dalam
 * [VoiceRecordingService]. Service memegang foreground service tipe `microphone` supaya Android
 * tidak membunuh proses saat layar mati.
 */
object VoiceRecordingController {

    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()
    private val _spectrum = MutableStateFlow(FloatArray(SpectrumAnalyzer.BANDS) { 0.05f })
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    private var engine: VoiceRecorderEngine? = null
    private var format: VoiceRecorderEngine.OutputFormat = VoiceRecorderEngine.OutputFormat.WAV

    fun start(context: Context, options: VoiceRecorderEngine.Options) {
        if (_state.value.running) return
        format = options.format
        val recorder = VoiceRecorderEngine(context.applicationContext).also { engine = it }
        recorder.setPauseListener {
            _state.value = _state.value.copy(paused = true, note = "focus")
            _spectrum.value = FloatArray(SpectrumAnalyzer.BANDS) { 0f }
        }
        _state.value = RecordingUiState(running = true)
        try {
            recorder.start(options, object : VoiceRecorderEngine.Listener {
                override fun onSpectrum(bands: FloatArray, elapsedMs: Long) {
                    _spectrum.value = bands
                    _state.value = _state.value.copy(elapsedMs = elapsedMs, paused = recorder.isPaused)
                }

                override fun onPaused(reason: String) {
                    _state.value = _state.value.copy(paused = true, note = reason)
                }

                override fun onFinished(session: VoiceRecorderEngine.Session) {
                    _state.value = RecordingUiState(
                        running = false,
                        output = session.output,
                        note = session.format.extension
                    )
                    engine = null
                }

                override fun onError(message: String) {
                    _state.value = _state.value.copy(running = false, error = message)
                    engine = null
                }
            })
        } catch (error: Exception) {
            _state.value = RecordingUiState(error = error.message ?: "Perekaman gagal")
            engine = null
        }
    }

    fun pause() {
        engine?.pause()
        _state.value = _state.value.copy(paused = true, note = "manual")
    }

    fun resume() {
        engine?.resume()
        _state.value = _state.value.copy(paused = false, note = "")
    }

    fun stop() {
        engine?.stop()
    }

    fun reset() {
        _state.value = RecordingUiState()
        _spectrum.value = FloatArray(SpectrumAnalyzer.BANDS) { 0.05f }
    }

    val activeFormat: VoiceRecorderEngine.OutputFormat get() = format
}

class VoiceRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            VoiceRecordingController.state.collectLatest { state ->
                if (state.running) {
                    ServiceCompat.startForeground(
                        this@VoiceRecordingService,
                        NOTIFICATION_ID,
                        buildNotification(state.paused),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            0
                        }
                    )
                } else {
                    ServiceCompat.stopForeground(this@VoiceRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val format = runCatching {
                    VoiceRecorderEngine.OutputFormat.valueOf(
                        intent.getStringExtra(EXTRA_FORMAT) ?: VoiceRecorderEngine.OutputFormat.WAV.name
                    )
                }.getOrDefault(VoiceRecorderEngine.OutputFormat.WAV)
                val sampleRate = intent.getIntExtra(EXTRA_SAMPLE_RATE, 44_100)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(false),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                )
                VoiceRecordingController.start(this, VoiceRecorderEngine.Options(sampleRate, format))
            }
            ACTION_PAUSE -> VoiceRecordingController.pause()
            ACTION_RESUME -> VoiceRecordingController.resume()
            ACTION_STOP -> VoiceRecordingController.stop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        VoiceRecordingController.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(paused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceRecordingService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, VoiceRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_harimu)
            .setContentTitle(getString(R.string.recording_voice_title))
            .setContentText(getString(if (paused) R.string.recording_paused else R.string.recording_active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(if (paused) R.string.action_resume else R.string.action_pause), toggleIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
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
        const val NOTIFICATION_ID = 4211
        const val ACTION_START = "com.harimu.app.recording.VOICE_START"
        const val ACTION_PAUSE = "com.harimu.app.recording.VOICE_PAUSE"
        const val ACTION_RESUME = "com.harimu.app.recording.VOICE_RESUME"
        const val ACTION_STOP = "com.harimu.app.recording.VOICE_STOP"
        private const val EXTRA_FORMAT = "format"
        private const val EXTRA_SAMPLE_RATE = "sample_rate"

        fun intent(context: Context, options: VoiceRecorderEngine.Options): Intent =
            Intent(context, VoiceRecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_FORMAT, options.format.name)
                .putExtra(EXTRA_SAMPLE_RATE, options.sampleRate)
    }
}

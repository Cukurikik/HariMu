package com.harimu.app.core.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

object VoiceRecordingController {
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    internal var engine: VoiceRecorderEngine? = null

    internal fun update(state: RecordingUiState) {
        _state.value = state
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
        _state.value = _state.value.copy(running = false)
    }
}

/**
 * Foreground service tipe `microphone` (CORE-06).
 * Otomatis menjeda saat kehilangan fokus audio (mis. panggilan masuk).
 */
class VoiceRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            if (VoiceRecordingController.state.value.running &&
                !VoiceRecordingController.state.value.paused
            ) {
                VoiceRecordingController.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createChannel()
        scope.launch {
            VoiceRecordingController.state.collectLatest { state ->
                if (state.running) {
                    ServiceCompat.startForeground(
                        this@VoiceRecordingService,
                        NOTIFICATION_ID,
                        buildNotification(state.paused),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            0
                        }
                    )
                } else {
                    abandonAudioFocus()
                    ServiceCompat.stopForeground(
                        this@VoiceRecordingService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(false),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                )
                startRecording(intent)
            }
            ACTION_PAUSE -> VoiceRecordingController.pause()
            ACTION_RESUME -> VoiceRecordingController.resume()
            ACTION_STOP -> VoiceRecordingController.stop()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (VoiceRecordingController.state.value.running) return
        requestAudioFocus()
        val format = runCatching {
            VoiceRecorderEngine.Format.valueOf(
                intent.getStringExtra(EXTRA_FORMAT) ?: VoiceRecorderEngine.Format.WAV.name
            )
        }.getOrDefault(VoiceRecorderEngine.Format.WAV)

        val config = VoiceRecorderEngine.Config(
            sampleRate = intent.getIntExtra(EXTRA_SAMPLE_RATE, 44_100),
            channels = intent.getIntExtra(EXTRA_CHANNELS, 1),
            format = format,
            bitrateBps = intent.getIntExtra(EXTRA_BITRATE, 128_000)
        )
        val engine = VoiceRecorderEngine(applicationContext)
        VoiceRecordingController.engine = engine
        VoiceRecordingController.update(RecordingUiState(running = true))

        runCatching {
            engine.start(config, object : VoiceRecorderEngine.Listener {
                override fun onSpectrum(bands: List<Float>, elapsedMs: Long) {
                    val cur = VoiceRecordingController.state.value
                    if (cur.running) {
                        VoiceRecordingController.update(
                            cur.copy(elapsedMs = elapsedMs, spectrum = bands)
                        )
                    }
                }

                override fun onFinished(file: File, durationMs: Long) {
                    VoiceRecordingController.update(
                        RecordingUiState(running = false, output = file, elapsedMs = durationMs)
                    )
                }

                override fun onError(message: String) {
                    VoiceRecordingController.update(
                        RecordingUiState(running = false, error = message)
                    )
                }
            })
        }.onFailure { err ->
            VoiceRecordingController.update(
                RecordingUiState(running = false, error = err.message ?: "Gagal merekam suara")
            )
            stopSelf()
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusListener)
        }
    }

    override fun onDestroy() {
        abandonAudioFocus()
        VoiceRecordingController.releaseAll()
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
            this, 10,
            Intent(this, VoiceRecordingService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 11,
            Intent(this, VoiceRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_harimu)
            .setContentTitle(getString(R.string.recording_channel_name))
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
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.recording_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = getString(R.string.recording_channel_desc) }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = ScreenRecorderService.CHANNEL_ID
        const val NOTIFICATION_ID = 4213
        const val ACTION_START = "com.harimu.app.recording.VOICE_START"
        const val ACTION_PAUSE = "com.harimu.app.recording.VOICE_PAUSE"
        const val ACTION_RESUME = "com.harimu.app.recording.VOICE_RESUME"
        const val ACTION_STOP = "com.harimu.app.recording.VOICE_STOP"
        private const val EXTRA_FORMAT = "format"
        private const val EXTRA_SAMPLE_RATE = "sample_rate"
        private const val EXTRA_CHANNELS = "channels"
        private const val EXTRA_BITRATE = "bitrate"

        fun intent(context: Context, config: VoiceRecorderEngine.Config): Intent =
            Intent(context, VoiceRecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_FORMAT, config.format.name)
                .putExtra(EXTRA_SAMPLE_RATE, config.sampleRate)
                .putExtra(EXTRA_CHANNELS, config.channels)
                .putExtra(EXTRA_BITRATE, config.bitrateBps)
    }
}

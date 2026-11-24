package com.harimu.app.feature.studios

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.harimu.app.core.media.AudioBatch2Math

data class EqLiveEngineStatus(
    val active: Boolean = false,
    val usingDynamicsProcessing: Boolean = false,
    val bandCount: Int = 0,
    val deviceCenterFreqsHz: List<Int> = emptyList(),
    val minMillibel: Int = -1200,
    val maxMillibel: Int = 1200
)

class EqualizerFxController {

    private var player: MediaPlayer? = null
    private var dynamicsProcessing: Any? = null
    private var legacyEq: Equalizer? = null
    private var currentStatus = EqLiveEngineStatus()

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    val isLoaded: Boolean
        get() = player != null

    fun prepareAndPlay(
        context: Context,
        uri: Uri,
        gainsDb: List<Float>,
        preampDb: Float,
        bypassed: Boolean
    ): Result<EqLiveEngineStatus> = runCatching {
        release()
        val mp = MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
            isLooping = true
            start()
        }
        player = mp

        val status = attachAudioFx(
            sessionId = mp.audioSessionId,
            gainsDb = gainsDb,
            preampDb = preampDb,
            bypassed = bypassed
        )
        currentStatus = status
        status
    }

    private fun attachAudioFx(
        sessionId: Int,
        gainsDb: List<Float>,
        preampDb: Float,
        bypassed: Boolean
    ): EqLiveEngineStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dpResult = runCatching {
                createAndAttachDynamicsProcessing(sessionId, gainsDb, preampDb, bypassed)
            }
            if (dpResult.isSuccess) {
                return dpResult.getOrThrow()
            }
        }
        return runCatching {
            val eq = Equalizer(0, sessionId).apply { enabled = !bypassed }
            val range = eq.bandLevelRange
            val minMb = range.getOrNull(0)?.toInt() ?: -1500
            val maxMb = range.getOrNull(1)?.toInt() ?: 1500
            val freqs = (0 until eq.numberOfBands).map { idx ->
                (eq.getCenterFreq(idx.toShort()) / 1000).coerceAtLeast(20)
            }
            legacyEq = eq
            applyLegacyEqGains(eq, freqs, minMb, maxMb, gainsDb, bypassed)
            EqLiveEngineStatus(
                active = true,
                usingDynamicsProcessing = false,
                bandCount = eq.numberOfBands.toInt(),
                deviceCenterFreqsHz = freqs,
                minMillibel = minMb,
                maxMillibel = maxMb
            )
        }.getOrElse {
            EqLiveEngineStatus(active = false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createAndAttachDynamicsProcessing(
        sessionId: Int,
        gainsDb: List<Float>,
        preampDb: Float,
        bypassed: Boolean
    ): EqLiveEngineStatus {
        val safeGains = AudioBatch2Math.normalizeBandGains(gainsDb)
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true,
            10,
            false,
            0,
            false,
            0,
            true
        )
        val preEq = DynamicsProcessing.Eq(true, true, 10)
        for (i in 0 until 10) {
            preEq.setBand(
                i,
                DynamicsProcessing.EqBand(
                    true,
                    AudioBatch2Math.EQ_10_BAND_CUTOFFS_HZ[i],
                    if (bypassed) 0f else safeGains[i]
                )
            )
        }
        builder.setPreEqAllChannelsTo(preEq)
        builder.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                true,
                true,
                0,
                5f,
                50f,
                10f,
                -0.5f,
                0f
            )
        )
        builder.setInputGainAllChannelsTo(if (bypassed) 0f else preampDb.coerceIn(-6f, 6f))
        val dp = DynamicsProcessing(0, sessionId, builder.build()).apply {
            enabled = true
        }
        dynamicsProcessing = dp
        return EqLiveEngineStatus(
            active = true,
            usingDynamicsProcessing = true,
            bandCount = 10,
            deviceCenterFreqsHz = AudioBatch2Math.EQ_10_BAND_FREQS_HZ,
            minMillibel = -1200,
            maxMillibel = 1200
        )
    }

    fun updateParameters(gainsDb: List<Float>, preampDb: Float, bypassed: Boolean) {
        val safeGains = AudioBatch2Math.normalizeBandGains(gainsDb)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = dynamicsProcessing as? DynamicsProcessing
            if (dp != null) {
                runCatching {
                    dp.setInputGainAllChannelsTo(if (bypassed) 0f else preampDb.coerceIn(-6f, 6f))
                    for (i in 0 until 10) {
                        dp.setPreEqBandAllChannelsTo(
                            i,
                            DynamicsProcessing.EqBand(
                                true,
                                AudioBatch2Math.EQ_10_BAND_CUTOFFS_HZ[i],
                                if (bypassed) 0f else safeGains[i]
                            )
                        )
                    }
                }
                return
            }
        }
        legacyEq?.let { eq ->
            runCatching {
                eq.enabled = !bypassed
                applyLegacyEqGains(
                    eq = eq,
                    freqs = currentStatus.deviceCenterFreqsHz,
                    minMb = currentStatus.minMillibel,
                    maxMb = currentStatus.maxMillibel,
                    gainsDb = safeGains,
                    bypassed = bypassed
                )
            }
        }
    }

    private fun applyLegacyEqGains(
        eq: Equalizer,
        freqs: List<Int>,
        minMb: Int,
        maxMb: Int,
        gainsDb: List<Float>,
        bypassed: Boolean
    ) {
        val levels = if (bypassed) {
            List(freqs.size) { 0 }
        } else {
            AudioBatch2Math.map10BandsToDeviceEqLevels(gainsDb, freqs, minMb, maxMb)
        }
        levels.forEachIndexed { idx, levelMb ->
            eq.setBandLevel(idx.toShort(), levelMb.toShort())
        }
    }

    fun togglePlayPause(): Boolean {
        val mp = player ?: return false
        return runCatching {
            if (mp.isPlaying) {
                mp.pause()
                false
            } else {
                mp.start()
                true
            }
        }.getOrDefault(false)
    }

    fun pause() {
        runCatching {
            if (player?.isPlaying == true) {
                player?.pause()
            }
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { (dynamicsProcessing as? DynamicsProcessing)?.release() }
        }
        dynamicsProcessing = null
        runCatching { legacyEq?.release() }
        legacyEq = null
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        currentStatus = EqLiveEngineStatus()
    }
}

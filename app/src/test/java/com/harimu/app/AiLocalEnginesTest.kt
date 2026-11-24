package com.harimu.app

import com.harimu.app.core.ai.AiLocalEngines
import com.harimu.app.core.engines.PcmAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.File

/**
 * Test murni-JVM untuk pemrosesan AI lokal yang tidak menyentuh Bitmap:
 * pemisahan stem mid/side dan penulisan WAV-nya.
 */
class AiLocalEnginesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun readChannelSamples(file: File, channel: Int, channels: Int): ShortArray {
        val bytes = file.readBytes()
        // Header WAV 44 byte, PCM 16-bit little-endian.
        val data = ByteArray(bytes.size - 44)
        System.arraycopy(bytes, 44, data, 0, data.size)
        val input = DataInputStream(data.inputStream())
        val totalFrames = data.size / 2 / channels
        val out = ShortArray(totalFrames)
        for (frame in 0 until totalFrames) {
            for (ch in 0 until channels) {
                val sample = java.lang.Short.reverseBytes(input.readShort())
                if (ch == channel) out[frame] = sample
            }
        }
        return out
    }

    @Test
    fun splitStemsExtractsCenterAndSideChannels() {
        // 4 frame stereo: L dan R sama (tengah) lalu L/R berlawanan (sisi).
        val samples = shortArrayOf(
            1000, 1000,
            -2000, -2000,
            3000, -3000,
            500, -500
        )
        val dir = temp.newFolder("stems")
        val pcm = PcmAudio(samples, sampleRate = 44100, channels = 2)
        val result = AiLocalEngines.splitStems(pcm, dir)

        assertTrue(result.vocals.exists() && result.vocals.length() > 44)
        assertTrue(result.instrumental.exists() && result.instrumental.length() > 44)

        val vocals = readChannelSamples(result.vocals, 0, 2)
        val instrumental = readChannelSamples(result.instrumental, 0, 2)
        assertEquals(4, vocals.size)
        // Vokal = (L+R)/2: frame tengah utuh, frame sisi saling menghapus.
        assertEquals(1000, vocals[0].toInt())
        assertEquals(-2000, vocals[1].toInt())
        assertEquals(0, vocals[2].toInt())
        assertEquals(0, vocals[3].toInt())
        // Instrumen = (L-R)/2: hanya bagian sisi yang tersisa.
        assertEquals(0, instrumental[0].toInt())
        assertEquals(0, instrumental[1].toInt())
        assertEquals(3000, instrumental[2].toInt())
        assertEquals(500, instrumental[3].toInt())
    }

    @Test
    fun monoInputDuplicatesTheCenterIntoBothCharacters() {
        val samples = shortArrayOf(1200, -800, 0)
        val dir = temp.newFolder("mono")
        val result = AiLocalEngines.splitStems(PcmAudio(samples, 22050, channels = 1), dir)
        val vocals = readChannelSamples(result.vocals, 0, 2)
        assertEquals(3, vocals.size)
        assertEquals(1200, vocals[0].toInt())
        val right = readChannelSamples(result.vocals, 1, 2)
        assertEquals(1200, right[0].toInt())
    }

    @Test
    fun instrumentalOnlySkipsTheVocalFile() {
        val dir = temp.newFolder("inst")
        val result = AiLocalEngines.splitStems(
            PcmAudio(shortArrayOf(10, 20), 16000, 2),
            dir,
            instrumentalOnly = true
        )
        assertTrue(!result.vocals.exists())
        assertTrue(result.instrumental.exists())
    }

}

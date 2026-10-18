package com.hustlecity.game

import com.hustlecity.game.audio.Audio
import com.hustlecity.game.audio.MusicGen
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

/** Renders the procedural music offline: checks levels and speed, and writes WAVs to build/audio for listening. */
class AudioTest {
    private val outDir = File("build/audio").apply { mkdirs() }

    private fun writeWav(name: String, samples: FloatArray) {
        val f = File(outDir, name)
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(0)
            val dataLen = samples.size * 2
            fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            raf.write("RIFF".toByteArray()); raf.write(le32(36 + dataLen)); raf.write("WAVEfmt ".toByteArray())
            raf.write(le32(16)); raf.write(le16(1)); raf.write(le16(1)); raf.write(le32(Audio.SR)); raf.write(le32(Audio.SR * 2)); raf.write(le16(2)); raf.write(le16(16))
            raf.write("data".toByteArray()); raf.write(le32(dataLen))
            val bytes = ByteArray(dataLen)
            for (i in samples.indices) {
                val v = (samples[i] * 30000f).toInt().coerceIn(-32768, 32767)
                bytes[i * 2] = v.toByte(); bytes[i * 2 + 1] = (v shr 8).toByte()
            }
            raf.write(bytes)
        }
    }

    @Test
    fun stationsRenderCleanlyAndFast() {
        val seconds = 20
        for (st in Audio.stations.indices) {
            val gen = MusicGen(st, 0)
            val buf = FloatArray(Audio.SR * seconds)
            val t0 = System.nanoTime()
            for (i in buf.indices) buf[i] = gen.next() * .55f
            val ms = (System.nanoTime() - t0) / 1e6
            var peak = 0f; var sum = 0.0
            for (v in buf) { assertTrue("NaN in station $st", !v.isNaN()); peak = maxOf(peak, abs(v)); sum += v * v }
            val rms = sqrt(sum / buf.size)
            println("station ${Audio.stations[st].name}: peak=%.2f rms=%.3f render=%.0f ms for %d s".format(peak, rms, ms, seconds))
            assertTrue("station $st is silent (rms=$rms)", rms > .01)
            assertTrue("station $st clips hard (peak=$peak)", peak < 1.6f)
            assertTrue("station $st too slow to render in real time ($ms ms)", ms < seconds * 1000 * .25)
            writeWav("station_${Audio.stations[st].name.lowercase().replace("-", "")}.wav", FloatArray(buf.size) { buf[it] / (1f + abs(buf[it])) })
        }
    }

    @Test
    fun tracksEndAndAdvance() {
        val gen = MusicGen(1, 3)
        var n = 0L
        while (!gen.finished && n < Audio.SR * 400L) { gen.next(); n++ }
        assertTrue("track never finished", gen.finished)
        val minutes = n / Audio.SR / 60.0
        println("pop track length: %.1f min".format(minutes))
        assertTrue(minutes in 1.0..4.0)
    }
}

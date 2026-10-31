package com.hustlecity.game.desktop

import android.media.MediaPlayer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** The Windows build reads the player's own MP3s for the store speakers: decoded to PCM, and played through to the end. */
class SongDecodeTest {
    private fun tone(): File {
        val f = Files.createTempFile("tone", ".mp3").toFile()
        f.writeBytes(javaClass.getResourceAsStream("/audio/tone.mp3")!!.readBytes())
        return f
    }

    @Test
    fun mp3sDecodeToPcm() {
        val file = tone()
        val src = AudioSystem.getAudioInputStream(file)
        val f = src.format
        val pcm = AudioSystem.getAudioInputStream(AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.sampleRate, 16, f.channels, f.channels * 2, f.sampleRate, false), src)
        val bytes = pcm.readAllBytes().size
        val seconds = bytes / (f.sampleRate * f.channels * 2)
        println("tone.mp3: ${f.encoding} ${f.sampleRate} Hz x${f.channels}, decoded %.2f s".format(seconds))
        assertTrue("1.5 s of sound came out (%.2f s)".format(seconds), seconds in 1.3f..1.8f)
        // and the player opens it
        MediaPlayer().apply { setDataSource(file.path); prepare(); release() }
    }

    @Test
    fun aSongPlaysToTheEnd() {
        // only where there's a sound device to play it on
        val canPlay = runCatching { AudioSystem.getSourceDataLine(AudioFormat(44100f, 16, 2, true, false)) }.isSuccess
        org.junit.Assume.assumeTrue("no sound device", canPlay)
        val done = CountDownLatch(1)
        val mp = MediaPlayer()
        mp.setDataSource(tone().path)
        mp.setVolume(.2f, .2f)
        mp.setOnCompletionListener { done.countDown() }
        mp.setOnErrorListener { _, _, _ -> done.countDown(); true }
        mp.prepare()
        mp.start()
        assertTrue("finished playing", done.await(10, TimeUnit.SECONDS))
        mp.release()
    }
}

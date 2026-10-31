package android.media

import android.content.Context
import android.net.Uri
import java.io.File
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop stand-in for Android's MediaPlayer, for the player's own songs: decodes a local file (MP3 through the MP3SPI
 * decoder on the classpath, WAV and AIFF natively) to 16-bit PCM and plays it on a thread of its own. Callbacks come
 * on that thread.
 */
class MediaPlayer {
    fun interface OnPreparedListener { fun onPrepared(mp: MediaPlayer) }
    fun interface OnCompletionListener { fun onCompletion(mp: MediaPlayer) }
    fun interface OnErrorListener { fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean }

    private var path: String? = null
    private var onPrepared: OnPreparedListener? = null
    private var onCompletion: OnCompletionListener? = null
    private var onError: OnErrorListener? = null
    @Volatile private var gain = 1f
    @Volatile private var stopped = false
    @Volatile private var paused = false
    @Volatile private var pcm: AudioInputStream? = null
    @Volatile private var line: SourceDataLine? = null
    private var thread: Thread? = null

    val isPlaying: Boolean get() = thread?.isAlive == true && !paused

    fun setDataSource(path: String) { this.path = path }
    fun setDataSource(context: Context, uri: Uri) { path = uri.path ?: uri.toString() }
    fun setVolume(left: Float, right: Float) { gain = maxOf(left, right).coerceIn(0f, 1f) }
    fun setOnPreparedListener(l: OnPreparedListener) { onPrepared = l }
    fun setOnCompletionListener(l: OnCompletionListener) { onCompletion = l }
    fun setOnErrorListener(l: OnErrorListener) { onError = l }

    /** Opens and decodes the file's header (throws if it can't be read). */
    fun prepare() {
        val file = File(path ?: error("no data source"))
        val src = AudioSystem.getAudioInputStream(file)
        val f = src.format
        val target = javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, f.sampleRate, 16, f.channels,
            f.channels * 2, f.sampleRate, false)
        pcm = AudioSystem.getAudioInputStream(target, src)
    }

    fun prepareAsync() {
        Thread({
            try { prepare(); if (!stopped) onPrepared?.onPrepared(this) }
            catch (t: Throwable) { if (!stopped) onError?.onError(this, 1, 0) }
        }, "song-prepare").apply { isDaemon = true }.start()
    }

    fun start() {
        if (thread?.isAlive == true) { paused = false; return }
        val stream = pcm ?: return
        thread = Thread({ play(stream) }, "song").apply { isDaemon = true; start() }
    }

    private fun play(stream: AudioInputStream) {
        try {
            val out = AudioSystem.getSourceDataLine(stream.format)
            line = out
            out.open(stream.format, 16384)
            out.start()
            val buf = ByteArray(8192)
            while (!stopped) {
                if (paused) { Thread.sleep(30); continue }
                val n = stream.read(buf)
                if (n < 0) break
                // the volume, applied to each little-endian 16-bit sample
                val g = gain
                if (g < .999f) {
                    var i = 0
                    while (i + 1 < n) {
                        val v = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort()
                        val s = (v * g).toInt()
                        buf[i] = s.toByte(); buf[i + 1] = (s shr 8).toByte()
                        i += 2
                    }
                }
                out.write(buf, 0, n)
            }
            if (!stopped) out.drain()
            out.close()
            if (!stopped) onCompletion?.onCompletion(this)
        } catch (t: Throwable) {
            if (!stopped) onError?.onError(this, 1, 0)
        } finally {
            runCatching { stream.close() }
        }
    }

    fun pause() { paused = true }
    fun stop() { stopped = true; runCatching { line?.stop() } }
    fun release() { stop(); runCatching { line?.close() }; runCatching { pcm?.close() } }
}

package android.media

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop stand-in for Android's streaming AudioTrack, playing 16-bit PCM through Java Sound. Like the original,
 * [write] blocks while the line's buffer is full, which is what paces the game's mixing thread. Without a sound
 * device the track reports [STATE_UNINITIALIZED] and the game runs silently.
 */
class AudioTrack private constructor(format: AudioFormat, bufferBytes: Int) {
    private val line: SourceDataLine? = runCatching {
        val fmt = javax.sound.sampled.AudioFormat(format.sampleRate.toFloat(), 16, format.channelCount, true, false)
        AudioSystem.getSourceDataLine(fmt).apply {
            // Windows' sound mixer wants more headroom than a phone, or the music stutters
            open(fmt, maxOf(bufferBytes, format.sampleRate * format.channelCount * 2 / 6))
        }
    }.getOrNull()
    private var bytes = ByteArray(0)

    val state: Int get() = if (line != null) STATE_INITIALIZED else STATE_UNINITIALIZED

    fun play() { line?.start() }
    fun pause() { line?.stop() }
    fun flush() { line?.flush() }
    fun release() { line?.close() }

    fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        val l = line ?: return ERROR_INVALID_OPERATION
        if (bytes.size < sizeInShorts * 2) bytes = ByteArray(sizeInShorts * 2)
        for (i in 0 until sizeInShorts) {
            val s = audioData[offsetInShorts + i].toInt()
            bytes[i * 2] = s.toByte()
            bytes[i * 2 + 1] = (s shr 8).toByte()
        }
        l.write(bytes, 0, sizeInShorts * 2)
        return sizeInShorts
    }

    class Builder {
        private var format = AudioFormat.Builder().build()
        private var bufferBytes = 4096
        fun setAudioAttributes(attributes: AudioAttributes) = this
        fun setAudioFormat(format: AudioFormat) = also { this.format = format }
        fun setBufferSizeInBytes(bytes: Int) = also { bufferBytes = bytes }
        fun setTransferMode(mode: Int) = this
        fun build() = AudioTrack(format, bufferBytes)
    }

    companion object {
        const val MODE_STREAM = 1
        const val STATE_UNINITIALIZED = 0
        const val STATE_INITIALIZED = 1
        const val ERROR_INVALID_OPERATION = -3

        @JvmStatic fun getMinBufferSize(sampleRateInHz: Int, channelConfig: Int, audioFormat: Int): Int = sampleRateInHz / 10
    }
}

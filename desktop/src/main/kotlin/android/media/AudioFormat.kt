package android.media

/** Desktop stand-in for Android's AudioFormat (16-bit PCM only). */
class AudioFormat private constructor(val sampleRate: Int, val channelMask: Int, val encoding: Int) {
    val channelCount: Int get() = if (channelMask == CHANNEL_OUT_STEREO) 2 else 1

    class Builder {
        private var rate = 44100
        private var mask = CHANNEL_OUT_MONO
        private var enc = ENCODING_PCM_16BIT
        fun setSampleRate(rate: Int) = also { this.rate = rate }
        fun setChannelMask(mask: Int) = also { this.mask = mask }
        fun setEncoding(encoding: Int) = also { enc = encoding }
        fun build() = AudioFormat(rate, mask, enc)
    }

    companion object {
        const val ENCODING_PCM_16BIT = 2
        const val CHANNEL_OUT_MONO = 4
        const val CHANNEL_OUT_STEREO = 12
    }
}

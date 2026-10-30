package android.media

/** Desktop stand-in for Android's AudioAttributes: accepted and ignored. */
class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(usage: Int) = this
        fun setContentType(type: Int) = this
        fun build() = AudioAttributes()
    }

    companion object {
        const val USAGE_GAME = 14
        const val CONTENT_TYPE_MUSIC = 2
    }
}

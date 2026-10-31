package android.net

/** Desktop stand-in for Android's Uri: songs on Windows are plain file paths (or file: URIs). */
class Uri private constructor(private val s: String) {
    /** The file it points at. */
    val path: String? get() = if (s.startsWith("file:")) runCatching { java.io.File(java.net.URI(s)).path }.getOrNull() else s

    override fun toString() = s

    companion object {
        @JvmStatic fun parse(s: String): Uri = Uri(s)
    }
}

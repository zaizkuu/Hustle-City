package android.graphics

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/** Desktop stand-in for Android's Typeface: the system's default sans-serif in the four styles. */
class Typeface private constructor(val skia: org.jetbrains.skia.Typeface?, val style: Int) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        private val styles = Array(4) { s -> Typeface(load(s), s) }

        @JvmField val DEFAULT: Typeface = styles[NORMAL]
        @JvmField val DEFAULT_BOLD: Typeface = styles[BOLD]
        @JvmField val SANS_SERIF: Typeface = styles[NORMAL]

        @JvmStatic fun create(family: Typeface?, style: Int): Typeface = styles[style and 3]

        @JvmStatic fun defaultFromStyle(style: Int): Typeface = styles[style and 3]

        private fun load(style: Int): org.jetbrains.skia.Typeface? {
            val fs = when (style) { BOLD -> FontStyle.BOLD; ITALIC -> FontStyle.ITALIC; BOLD_ITALIC -> FontStyle.BOLD_ITALIC; else -> FontStyle.NORMAL }
            val mgr = FontMgr.default
            // Segoe UI on Windows; whatever sans-serif the system has elsewhere
            for (family in listOf("Segoe UI", "Roboto", "Helvetica Neue", "Arial", "DejaVu Sans", "Liberation Sans", "Noto Sans")) {
                runCatching { mgr.matchFamilyStyle(family, fs) }.getOrNull()?.let { return it }
            }
            return runCatching { mgr.legacyMakeTypeface("", fs) }.getOrNull()
        }
    }
}

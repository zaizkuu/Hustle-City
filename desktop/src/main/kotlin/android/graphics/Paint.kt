package android.graphics

import org.jetbrains.skia.Font
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin

/**
 * Desktop stand-in for Android's Paint, backed by a Skia paint and font. Covers what the game's renderers use:
 * color and alpha, fill or stroke, stroke width, caps and joins, and text size, alignment and typeface.
 */
class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }

    val skia = org.jetbrains.skia.Paint().apply { isAntiAlias = flags and ANTI_ALIAS_FLAG != 0; color = 0xFF000000.toInt() }
    private var fontDirty = true
    private var skiaFont: Font? = null

    var color: Int
        get() = skia.color
        set(value) { skia.color = value }

    var alpha: Int
        get() = skia.color ushr 24
        set(value) { skia.color = (skia.color and 0x00FFFFFF) or (value.coerceIn(0, 255) shl 24) }

    var isAntiAlias: Boolean
        get() = skia.isAntiAlias
        set(value) { skia.isAntiAlias = value }

    var style: Style = Style.FILL
        set(value) {
            field = value
            skia.mode = when (value) { Style.FILL -> PaintMode.FILL; Style.STROKE -> PaintMode.STROKE; Style.FILL_AND_STROKE -> PaintMode.STROKE_AND_FILL }
        }

    var strokeWidth: Float
        get() = skia.strokeWidth
        set(value) { skia.strokeWidth = value }

    var strokeCap: Cap = Cap.BUTT
        set(value) {
            field = value
            skia.strokeCap = when (value) { Cap.BUTT -> PaintStrokeCap.BUTT; Cap.ROUND -> PaintStrokeCap.ROUND; Cap.SQUARE -> PaintStrokeCap.SQUARE }
        }

    var strokeJoin: Join = Join.MITER
        set(value) {
            field = value
            skia.strokeJoin = when (value) { Join.MITER -> PaintStrokeJoin.MITER; Join.ROUND -> PaintStrokeJoin.ROUND; Join.BEVEL -> PaintStrokeJoin.BEVEL }
        }

    var textSize: Float = 12f
        set(value) { if (field != value) { field = value; fontDirty = true } }

    var textAlign: Align = Align.LEFT

    var typeface: Typeface? = null
        set(value) { if (field !== value) { field = value; fontDirty = true } }

    /** The Skia font for this paint's typeface and text size. */
    val font: Font
        get() {
            val f = skiaFont
            if (f != null && !fontDirty) return f
            f?.close()
            return Font((typeface ?: Typeface.DEFAULT).skia, textSize).also {
                it.isSubpixel = true
                it.edging = org.jetbrains.skia.FontEdging.ANTI_ALIAS
                skiaFont = it
                fontDirty = false
            }
        }

    fun measureText(text: String): Float = font.measureTextWidth(text, skia)

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
    }
}

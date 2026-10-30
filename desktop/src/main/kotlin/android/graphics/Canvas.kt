package android.graphics

import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect

/**
 * Desktop stand-in for Android's Canvas, drawing through Skia: either onto the Compose canvas of a window, or into a
 * [Bitmap]. Covers the calls the game's renderers make.
 */
class Canvas(val skia: org.jetbrains.skia.Canvas) {
    constructor(bitmap: Bitmap) : this(org.jetbrains.skia.Canvas(bitmap.skia))

    enum class VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }

    /** Skia on the desktop draws vertices fast, so the batcher never needs its path fallback. */
    val isHardwareAccelerated: Boolean get() = false

    private val colorPaint by lazy { org.jetbrains.skia.Paint() }

    fun save(): Int = skia.save()
    fun restore() { skia.restore() }
    fun restoreToCount(count: Int) { skia.restoreToCount(count) }
    fun translate(dx: Float, dy: Float) { skia.translate(dx, dy) }
    fun scale(sx: Float, sy: Float) { skia.scale(sx, sy) }
    fun rotate(degrees: Float) { skia.rotate(degrees) }
    fun rotate(degrees: Float, px: Float, py: Float) { skia.translate(px, py); skia.rotate(degrees); skia.translate(-px, -py) }
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean { skia.clipRect(Rect.makeLTRB(left, top, right, bottom)); return true }

    fun drawColor(color: Int) {
        colorPaint.color = color
        skia.drawPaint(colorPaint)
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) { skia.drawRect(Rect.makeLTRB(left, top, right, bottom), paint.skia) }
    fun drawRect(r: RectF, paint: Paint) { skia.drawRect(r.toSkia(), paint.skia) }

    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) {
        skia.drawRRect(RRect.makeLTRB(left, top, right, bottom, rx, ry), paint.skia)
    }

    fun drawRoundRect(r: RectF, rx: Float, ry: Float, paint: Paint) = drawRoundRect(r.left, r.top, r.right, r.bottom, rx, ry, paint)

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) { skia.drawCircle(cx, cy, radius, paint.skia) }
    fun drawOval(r: RectF, paint: Paint) { skia.drawOval(r.toSkia(), paint.skia) }
    fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) { skia.drawOval(Rect.makeLTRB(left, top, right, bottom), paint.skia) }
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) { skia.drawLine(x0, y0, x1, y1, paint.skia) }
    fun drawPath(path: Path, paint: Paint) { skia.drawPath(path.skia, paint.skia) }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        val font = paint.font
        val dx = when (paint.textAlign) {
            Paint.Align.LEFT -> 0f
            Paint.Align.CENTER -> -font.measureTextWidth(text, paint.skia) / 2f
            Paint.Align.RIGHT -> -font.measureTextWidth(text, paint.skia)
        }
        skia.drawString(text, x + dx, y, font, paint.skia)
    }

    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        val image = org.jetbrains.skia.Image.makeFromBitmap(bitmap.skia)
        skia.drawImage(image, left, top, paint?.skia)
        image.close()
    }

    /**
     * Skia takes exactly sized arrays, so the used part of each array is copied out (Android reads it in place).
     * Only colored triangles are supported: the game never draws textured vertices.
     */
    fun drawVertices(
        mode: VertexMode, vertexCount: Int, verts: FloatArray, vertOffset: Int, texs: FloatArray?, texOffset: Int,
        colors: IntArray?, colorOffset: Int, indices: ShortArray?, indexOffset: Int, indexCount: Int, paint: Paint,
    ) {
        val n = vertexCount / 2
        if (n < 3) return
        val pos = verts.copyOfRange(vertOffset, vertOffset + n * 2)
        val col = colors?.copyOfRange(colorOffset, colorOffset + n)
        val idx = indices?.copyOfRange(indexOffset, indexOffset + indexCount)
        val vm = when (mode) {
            VertexMode.TRIANGLES -> org.jetbrains.skia.VertexMode.TRIANGLES
            VertexMode.TRIANGLE_STRIP -> org.jetbrains.skia.VertexMode.TRIANGLE_STRIP
            VertexMode.TRIANGLE_FAN -> org.jetbrains.skia.VertexMode.TRIANGLE_FAN
        }
        // DST keeps the vertex colors as they are, as Android draws them (Skia would otherwise mix in the paint's color)
        skia.drawVertices(vm, pos, col, null, idx, BlendMode.DST, paint.skia)
    }
}

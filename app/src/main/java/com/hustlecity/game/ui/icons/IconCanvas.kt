package com.hustlecity.game.ui.icons

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath

/** Draws game icons on a plain [Canvas]: the city map pins, minimap markers and in-world labels. */
object IconCanvas {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val tmp = Path()
    private val pinPath = Path()

    /** Draws [icon] centred on (cx, cy), [size] pixels wide; [TINT] layers use [tint]. */
    fun draw(canvas: Canvas, icon: GameIcon, cx: Float, cy: Float, size: Float, tint: Int = 0xFFFFFFFF.toInt()) {
        val s = size / 24f
        matrix.setScale(s, s)
        matrix.postTranslate(cx - size / 2f, cy - size / 2f)
        for (l in icon.layers) {
            l.path.asAndroidPath().transform(matrix, tmp)
            val base = if (l.color == TINT) tint else l.color.toInt()
            paint.color = base
            paint.alpha = ((base ushr 24) * l.alpha).toInt().coerceIn(0, 255)
            if (l.stroke > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = l.stroke * s
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            } else paint.style = Paint.Style.FILL
            canvas.drawPath(tmp, paint)
        }
        paint.style = Paint.Style.FILL
    }

    fun draw(canvas: Canvas, id: String, cx: Float, cy: Float, size: Float, tint: Int = 0xFFFFFFFF.toInt()) =
        draw(canvas, GameIcons[id], cx, cy, size, tint)

    /**
     * A map pin: a colored disc with a white rim and a white glyph, and a pointer underneath touching (x, y + r * 1.6).
     * (x, y) is the centre of the disc; [r] its radius in pixels.
     */
    fun pin(canvas: Canvas, id: String, x: Float, y: Float, r: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(x + r * .1f, y + r * .16f, r * 1.12f, paint)
        // pointer
        pinPath.rewind()
        pinPath.moveTo(x - r * .42f, y + r * .9f); pinPath.lineTo(x + r * .42f, y + r * .9f); pinPath.lineTo(x, y + r * 1.6f); pinPath.close()
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawPath(pinPath, paint)
        canvas.drawCircle(x, y, r * 1.12f, paint)
        paint.color = color
        canvas.drawCircle(x, y, r, paint)
        // a soft highlight on the top half
        paint.color = 0x22FFFFFF
        canvas.drawCircle(x, y - r * .25f, r * .78f, paint)
        draw(canvas, id, x, y, r * 1.25f)
    }
}

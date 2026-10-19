package com.hustlecity.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Park

/** A top-down picture of a whole city: water, parks, roads, rail lines, buildings and places. */
object MapPreview {
    /** Renders [city] so its longer side is [size] pixels; [buildings] can be left out for small thumbnails. */
    fun render(city: City, size: Int, buildings: Boolean = true): Bitmap {
        val b = city.bounds
        val scale = size / maxOf(b[2] - b[0], b[3] - b[1])
        val w = ((b[2] - b[0]) * scale).toInt().coerceAtLeast(1); val h = ((b[3] - b[1]) * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        fun x(v: Float) = (v - b[0]) * scale
        fun y(v: Float) = (v - b[1]) * scale
        fun poly(pts: FloatArray, color: Int) {
            path.reset()
            for (k in 0 until pts.size / 2) if (k == 0) path.moveTo(x(pts[0]), y(pts[1])) else path.lineTo(x(pts[k * 2]), y(pts[k * 2 + 1]))
            path.close(); p.color = color; p.style = Paint.Style.FILL; c.drawPath(path, p)
        }
        c.drawColor(if (city.shore.land.isEmpty()) LAND else WATER)
        for (l in city.shore.land) poly(l, LAND)
        for (wt in city.shore.water) poly(wt, WATER)
        for (i in city.shore.islands) poly(i, LAND)
        for (cn in city.shore.canals) poly(cn, 0xFF3B6E96.toInt())
        for (pk in city.parks) poly(pk.poly, if (pk.kind == Park.PLAZA) 0xFFD8CFB8.toInt() else 0xFF6DA35C.toInt())
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for (e in city.roads.edges) {
            p.color = if (e.overWater) 0xFF8A6D4B.toInt() else 0xFF4A4D53.toInt()
            p.strokeWidth = maxOf(e.cls.halfWidth * 2 * scale, if (buildings) 1f else .8f + e.cls.rank * .5f)
            c.drawLine(x(e.ax), y(e.ay), x(e.bx), y(e.by), p)
        }
        for (r in city.rails) {
            p.color = r.color; p.strokeWidth = maxOf(2f, 3f * scale)
            for (k in 0 until r.pts.size / 2 - 1) c.drawLine(x(r.pts[k * 2]), y(r.pts[k * 2 + 1]), x(r.pts[k * 2 + 2]), y(r.pts[k * 2 + 3]), p)
        }
        if (!buildings) return bmp
        p.style = Paint.Style.FILL
        for (bd in city.buildings) {
            val pts = floatArrayOf(bd.wx(0f, 0f), bd.wy(0f, 0f), bd.wx(bd.w, 0f), bd.wy(bd.w, 0f), bd.wx(bd.w, bd.d), bd.wy(bd.w, bd.d), bd.wx(0f, bd.d), bd.wy(0f, bd.d))
            val shade = (0xFF - (bd.height * 1.6f).toInt().coerceIn(0, 140))
            val hc = bd.hood.color
            poly(pts, when {
                bd.placeId != null -> 0xFFE0B04A.toInt()
                else -> (0xFF shl 24) or ((hc shr 16 and 255) * shade / 255 shl 16) or ((hc shr 8 and 255) * shade / 255 shl 8) or ((hc and 255) * shade / 255)
            })
        }
        return bmp
    }

    private const val LAND = 0xFFB9BCC1.toInt()
    private const val WATER = 0xFF27445C.toInt()
}

package com.hustlecity.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.hustlecity.game.render.gpu.GpuFrame
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Draws model meshes where there's no GPU frame: the shop menus' previews are painted on a Canvas, but should show the
 * same furniture and vehicles as the HD world. A small z-buffered rasterizer at twice the picture's resolution (averaged
 * down for smooth edges), lit per vertex the way the world shader lights them at midday.
 */
class SoftRaster(private val size: Int) {
    private val n = size * SS
    private val rgb = IntArray(n * n)
    /** 1/depth of what's drawn at each sample; 0 is nothing. */
    private val inv = FloatArray(n * n)

    // per vertex of the mesh being drawn: screen position, 1/depth and lit color
    private var vx = FloatArray(0); private var vy = FloatArray(0); private var vz = FloatArray(0)
    private var cr = FloatArray(0); private var cg = FloatArray(0); private var cb = FloatArray(0)

    // the sun over the camera's left shoulder
    private var lx = 0f; private var ly = 0f; private var lz = 1f

    /**
     * Draws [m] with its origin at ([ox], [oy], [oz]), its x axis along ([ax], [ay]) and y along ([bx], [by]), scaled by
     * [sx], [sy], [sz]: the same placement and vehicle tints as [GpuFrame.mesh].
     */
    fun mesh(proj: Projector, m: Models.Mesh, ox: Float, oy: Float, oz: Float, ax: Float, ay: Float, bx: Float, by: Float,
             sx: Float, sy: Float, sz: Float, paint: Int, lenses: Int, accent: Int, windowsLit: Boolean) {
        val nv = m.vertices
        if (vx.size < nv) {
            vx = FloatArray(nv); vy = FloatArray(nv); vz = FloatArray(nv)
            cr = FloatArray(nv); cg = FloatArray(nv); cb = FloatArray(nv)
        }
        run {
            val fx = proj.fwdX; val fy = proj.fwdY; val rx = proj.rightX; val ry = proj.rightY
            val x = -fx * .55f - rx * .5f; val y = -fy * .55f - ry * .5f; val z = .8f
            val l = sqrt(x * x + y * y + z * z)
            lx = x / l; ly = y / l; lz = z / l
        }
        val car = paint != 0 || accent != 0 || lenses != GpuFrame.LENSES_LIT || !windowsLit
        val parts = if (car) m.parts else null
        val p = m.pos; val nr = m.nrm
        val kax = ax * sx; val kay = ay * sx; val kbx = bx * sy; val kby = by * sy
        val f = proj.focal * SS; val ccx = proj.centerX * SS; val ccy = proj.centerY * SS
        for (i in 0 until nv) {
            val px = p[i * 3]; val py = p[i * 3 + 1]
            val wx = ox + px * kax + py * kbx; val wy = oy + px * kay + py * kby; val wz = oz + p[i * 3 + 2] * sz
            val cz = proj.camZOf(wx, wy, wz).coerceAtLeast(proj.near)
            vx[i] = ccx + f * proj.camXOf(wx, wy, wz) / cz
            vy[i] = ccy - f * proj.camYOf(wx, wy, wz) / cz
            vz[i] = 1f / cz
            // the vertex color, tinted as the GPU tints it
            var c = m.rgba[i]
            var flags = java.lang.Float.intBitsToFloat(m.flags[i]).toInt()
            if (parts != null) {
                val part = parts[i]
                if (part == Models.PART_PAINT && paint != 0) c = tint(c, paint)
                else if (part == Models.PART_ACCENT && accent != 0) c = tint(c, accent)
                else if (part == Models.PART_WINDOW && !windowsLit) { c = (c and -0x1000000) or GpuFrame.WINDOW_GLASS; flags = flags and 1.inv() }
                else if ((part == Models.PART_LENS || part == Models.PART_RED_LENS) &&
                    (lenses == GpuFrame.LENSES_OFF || (lenses == GpuFrame.LENSES_BRAKING && part != Models.PART_RED_LENS))) flags = flags and 1.inv()
            }
            val nx = nr[i * 3]; val ny = nr[i * 3 + 1]
            light(i, c, flags, nx * ax + ny * bx, nx * ay + ny * by, nr[i * 3 + 2], wx, wy, wz, proj)
        }
        val idx = m.idx
        var t = 0
        while (t + 2 < idx.size) { tri(idx[t], idx[t + 1], idx[t + 2]); t += 3 }
    }

    /** The color [c] (the GPU's byte order) as the paint [argb], as dark as the modelled grey. */
    private fun tint(c: Int, argb: Int): Int {
        val k = c and 255
        val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
        return (c and -0x1000000) or ((b * k / 255) shl 16) or ((g * k / 255) shl 8) or (r * k / 255)
    }

    /** The world shader's lighting for one vertex, in linear light, then toned and encoded for display. */
    private fun light(i: Int, c: Int, flags: Int, nx0: Float, ny0: Float, nz0: Float, wx: Float, wy: Float, wz: Float, proj: Projector) {
        val ar = sq((c and 255) / 255f); val ag = sq(((c shr 8) and 255) / 255f); val ab = sq(((c shr 16) and 255) / 255f)
        var r: Float; var g: Float; var b: Float
        if (flags and 1 == 1) {
            r = ar * 1.15f; g = ag * 1.15f; b = ab * 1.15f
        } else {
            val mat = flags shr 1
            var vx = proj.camX - wx; var vy = proj.camY - wy; var vz = proj.camZ - wz
            val vl = sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(1e-4f); vx /= vl; vy /= vl; vz /= vl
            var nx = nx0; var ny = ny0; var nz = nz0
            val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f); nx /= nl; ny /= nl; nz /= nl
            if (nx * vx + ny * vy + nz * vz < 0f) { nx = -nx; ny = -ny; nz = -nz }
            var spec = .06f; var gloss = 28f; var reflect = 0f
            val nv = max(nx * vx + ny * vy + nz * vz, 0f)
            when (mat) {
                3 -> { reflect = .18f + .6f * (1f - nv).pow(4); spec = .9f; gloss = 140f }
                7 -> { spec = .12f; gloss = 50f }
                8 -> { spec = .25f; gloss = 70f }
                9 -> { spec = .35f; gloss = 60f }
                13 -> spec = .03f
                15 -> { spec = .02f; gloss = 8f }
                16 -> { spec = .7f; gloss = 90f; reflect = .08f + .3f * (1f - nv).pow(4) }
                17 -> { spec = .09f; gloss = 18f }
            }
            val nl2 = nx * lx + ny * ly + nz * lz
            val ndl = max(nl2, 0f)
            val wrap = max((nl2 + .3f) / 1.3f, 0f)
            val up = nz * .5f + .5f
            val vertical = 1f - abs(nz)
            val ao = if (mat == 15 || mat >= 17) 1f else 1f + (.55f + .45f * smooth(wz / 3.5f) - 1f) * vertical
            var hx = lx + vx; var hy = ly + vy; var hz = lz + vz
            val hl = sqrt(hx * hx + hy * hy + hz * hz).coerceAtLeast(1e-4f); hx /= hl; hy /= hl; hz /= hl
            val s = max(nx * hx + ny * hy + nz * hz, 0f).pow(gloss) * spec
            val direct = ndl + (wrap - ndl) * (if (mat == 17 || mat == 15) .55f else .25f)
            r = ar * ((GROUND[0] + (SKY[0] - GROUND[0]) * up) * ao + SUN[0] * direct) + SUN[0] * s * ndl
            g = ag * ((GROUND[1] + (SKY[1] - GROUND[1]) * up) * ao + SUN[1] * direct) + SUN[1] * s * ndl
            b = ab * ((GROUND[2] + (SKY[2] - GROUND[2]) * up) * ao + SUN[2] * direct) + SUN[2] * s * ndl
            if (reflect > 0f) {
                // the sky it mirrors
                val rz = -vz + 2f * (nx * vx + ny * vy + nz * vz) * nz
                val k = smooth((rz + .05f) / .65f)
                r += (sq(HORIZON[0] + (TOP[0] - HORIZON[0]) * k) + SUN[0] * s - r) * reflect
                g += (sq(HORIZON[1] + (TOP[1] - HORIZON[1]) * k) + SUN[1] * s - g) * reflect
                b += (sq(HORIZON[2] + (TOP[2] - HORIZON[2]) * k) + SUN[2] * s - b) * reflect
            }
        }
        cr[i] = display(r); cg[i] = display(g); cb[i] = display(b)
    }

    private fun tri(a: Int, b: Int, c: Int) {
        val x0 = vx[a]; val y0 = vy[a]; val x1 = vx[b]; val y1 = vy[b]; val x2 = vx[c]; val y2 = vy[c]
        val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if (abs(area) < 1e-6f) return
        val minX = max(floor(min(x0, min(x1, x2))).toInt(), 0); val maxX = min(ceil(max(x0, max(x1, x2))).toInt(), n - 1)
        val minY = max(floor(min(y0, min(y1, y2))).toInt(), 0); val maxY = min(ceil(max(y0, max(y1, y2))).toInt(), n - 1)
        if (minX > maxX || minY > maxY) return
        val ia = 1f / area
        for (y in minY..maxY) {
            val py = y + .5f
            for (x in minX..maxX) {
                val px = x + .5f
                // barycentric weights; all of one sign inside, whichever way the triangle winds
                val w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) * ia
                val w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) * ia
                val w2 = 1f - w0 - w1
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                val z = w0 * vz[a] + w1 * vz[b] + w2 * vz[c]
                val k = y * n + x
                if (z <= inv[k]) continue
                inv[k] = z
                val r = (w0 * cr[a] + w1 * cr[b] + w2 * cr[c]).coerceIn(0f, 255f).toInt()
                val g = (w0 * cg[a] + w1 * cg[b] + w2 * cg[c]).coerceIn(0f, 255f).toInt()
                val bl = (w0 * cb[a] + w1 * cb[b] + w2 * cb[c]).coerceIn(0f, 255f).toInt()
                rgb[k] = (r shl 16) or (g shl 8) or bl
            }
        }
    }

    /** Paints what was drawn over [canvas] (everything drawn on it so far lies behind). */
    fun drawOnto(canvas: Canvas) {
        val out = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            var r = 0; var g = 0; var b = 0; var k = 0
            for (j in 0 until SS) for (i in 0 until SS) {
                val s = (y * SS + j) * n + x * SS + i
                if (inv[s] == 0f) continue
                val c = rgb[s]
                r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; k++
            }
            if (k > 0) out[y * size + x] = ((255 * k / (SS * SS)) shl 24) or ((r / k) shl 16) or ((g / k) shl 8) or (b / k)
        }
        val bmp = Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
    }

    private fun sq(v: Float) = v * v
    private fun smooth(t0: Float): Float { val t = t0.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    /** Exposure, the shader's soft shoulder and its display encoding, to 0..255. */
    private fun display(c: Float): Float {
        val v = max(c, 0f)
        return sqrt(v * (1f + v * .08f) / (1f + v * .5f)) * 255f
    }

    private companion object {
        /** Samples per pixel along each axis. */
        const val SS = 2
        // midday light (GpuLighting's daytime values)
        val SUN = floatArrayOf(1.02f, .96f, .86f)
        val SKY = floatArrayOf(.44f, .52f, .64f)
        val GROUND = floatArrayOf(.32f, .29f, .26f)
        val TOP = floatArrayOf(.33f, .56f, .86f)
        val HORIZON = floatArrayOf(.72f, .83f, .94f)
    }
}

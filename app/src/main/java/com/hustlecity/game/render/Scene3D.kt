package com.hustlecity.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Perspective camera orbiting a ground target. World axes: x east, y south, z up (meters).
 */
class Projector {
    var tx = 0f
    var ty = 0f
    var dist = 90f
    var pitch = Math.toRadians(52.0).toFloat()
    var yaw = 0f
    var fovY = Math.toRadians(42.0).toFloat()
    var screenW = 1f
    var screenH = 1f
    var centerX = 0f
    var centerY = 0f
    var focal = 1f
    var near = 0.8f

    var camX = 0f; var camY = 0f; var camZ = 0f
    private var rX = 1f; private var rY = 0f
    private var uX = 0f; private var uY = 0f; private var uZ = 1f
    private var fX = 0f; private var fY = 0f; private var fZ = -1f
    /** Horizontal unit vector pointing from target towards the camera. */
    var backX = 0f; var backY = 1f

    fun update(w: Float, h: Float, centerYFrac: Float = .5f) {
        screenW = w; screenH = h
        centerX = w * .5f
        centerY = h * centerYFrac
        focal = (h * .5f) / tan(fovY * .5f)
        backX = sin(yaw); backY = cos(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        camX = tx + dist * cp * backX
        camY = ty + dist * cp * backY
        camZ = dist * sp
        fX = -cp * backX; fY = -cp * backY; fZ = -sp
        rX = cos(yaw); rY = -sin(yaw)
        uX = -sp * backX; uY = -sp * backY; uZ = cp
    }

    fun camXOf(x: Float, y: Float, z: Float) = (x - camX) * rX + (y - camY) * rY
    fun camYOf(x: Float, y: Float, z: Float) = (x - camX) * uX + (y - camY) * uY + (z - camZ) * uZ
    fun camZOf(x: Float, y: Float, z: Float) = (x - camX) * fX + (y - camY) * fY + (z - camZ) * fZ

    /** Projects a point; returns false if it is behind the near plane. Result in [out] (x, y, depth). */
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val zc = camZOf(x, y, z)
        if (zc < near) return false
        out[0] = centerX + focal * camXOf(x, y, z) / zc
        out[1] = centerY - focal * camYOf(x, y, z) / zc
        out[2] = zc
        return true
    }

    /** Intersects the ray through a screen pixel with the plane z = [planeZ]. */
    fun unproject(sx: Float, sy: Float, out: FloatArray, planeZ: Float = 0f): Boolean {
        val a = (sx - centerX) / focal
        val b = -(sy - centerY) / focal
        val dx = fX + a * rX + b * uX
        val dy = fY + a * rY + b * uY
        val dz = fZ + b * uZ
        if (dz > -1e-4f) return false
        val t = (planeZ - camZ) / dz
        out[0] = camX + dx * t
        out[1] = camY + dy * t
        return true
    }

    fun rayDir(sx: Float, sy: Float, out: FloatArray) {
        val a = (sx - centerX) / focal
        val b = -(sy - centerY) / focal
        out[0] = fX + a * rX + b * uX
        out[1] = fY + a * rY + b * uY
        out[2] = fZ + b * uZ
    }

    /** Axis aligned ground rectangle that contains everything visible (x0, y0, x1, y1). */
    fun visibleGround(out: FloatArray, margin: Float) {
        val p = FloatArray(2)
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        val far = dist * 6f
        val pts = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(screenW, 0f), floatArrayOf(0f, screenH), floatArrayOf(screenW, screenH))
        for (s in pts) {
            if (!unproject(s[0], s[1], p) || dist2(p[0], p[1], camX, camY) > far * far) {
                // ray goes above the horizon: clamp to far distance along the view direction
                val d = FloatArray(3); rayDir(s[0], s[1], d)
                val len = sqrt(d[0] * d[0] + d[1] * d[1]).coerceAtLeast(1e-3f)
                p[0] = camX + d[0] / len * far
                p[1] = camY + d[1] / len * far
            }
            x0 = min(x0, p[0]); y0 = min(y0, p[1]); x1 = max(x1, p[0]); y1 = max(y1, p[1])
        }
        x0 = min(x0, camX); x1 = max(x1, camX); y0 = min(y0, camY); y1 = max(y1, camY)
        out[0] = x0 - margin; out[1] = y0 - margin; out[2] = x1 + margin; out[3] = y1 + margin
    }

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float { val dx = ax - bx; val dy = ay - by; return dx * dx + dy * dy }
}

/** Collects convex polygons into one indexed triangle mesh, drawn with a single drawVertices call. */
class RenderBatch(private val maxVerts: Int = 60000) {
    private val pos = FloatArray(maxVerts * 2)
    private val colors = IntArray(maxVerts * 2)
    private val idx = ShortArray(maxVerts * 3)
    private var vCount = 0
    private var iCount = 0
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
    private val polyPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private var canvas: Canvas? = null
    private var useVertices = true
    // fallback storage of polygon boundaries for drawPath rendering
    private val polyStart = IntArray(maxVerts)
    private var polyCount = 0
    var trianglesDrawn = 0
        private set

    fun begin(c: Canvas) {
        canvas = c
        useVertices = Build.VERSION.SDK_INT >= 29 || !c.isHardwareAccelerated
        vCount = 0; iCount = 0; polyCount = 0
        trianglesDrawn = 0
    }

    fun poly(xs: FloatArray, ys: FloatArray, n: Int, color: Int) {
        if (n < 3) return
        if (vCount + n >= maxVerts || iCount + (n - 2) * 3 >= idx.size) flush()
        val base = vCount
        polyStart[polyCount++] = base
        for (i in 0 until n) {
            pos[vCount * 2] = xs[i]
            pos[vCount * 2 + 1] = ys[i]
            colors[vCount] = color
            vCount++
        }
        for (i in 1 until n - 1) {
            idx[iCount++] = base.toShort()
            idx[iCount++] = (base + i).toShort()
            idx[iCount++] = (base + i + 1).toShort()
        }
    }

    fun flush() {
        val c = canvas ?: return
        if (vCount == 0) return
        trianglesDrawn += iCount / 3
        if (useVertices) {
            c.drawVertices(Canvas.VertexMode.TRIANGLES, vCount * 2, pos, 0, null, 0, colors, 0, idx, 0, iCount, paint)
        } else {
            for (p in 0 until polyCount) {
                val start = polyStart[p]
                val end = if (p + 1 < polyCount) polyStart[p + 1] else vCount
                path.rewind()
                path.moveTo(pos[start * 2], pos[start * 2 + 1])
                for (v in start + 1 until end) path.lineTo(pos[v * 2], pos[v * 2 + 1])
                path.close()
                polyPaint.color = colors[start]
                c.drawPath(path, polyPaint)
            }
        }
        vCount = 0; iCount = 0; polyCount = 0
    }
}

/**
 * World-space polygon helpers with near plane clipping, lighting and night tinting.
 */
class Scene3D(val proj: Projector, val batch: RenderBatch) {
    private val cx = FloatArray(24); private val cy = FloatArray(24); private val cz = FloatArray(24)
    private val ox = FloatArray(24); private val oy = FloatArray(24); private val oz = FloatArray(24)
    private val sx = FloatArray(24); private val sy = FloatArray(24)
    private val wx = FloatArray(24); private val wy = FloatArray(24); private val wz = FloatArray(24)

    var ambR = 1f; var ambG = 1f; var ambB = 1f
    /** 1 = full daylight, 0 = deep night. */
    var daylight = 1f

    fun applyDaylight(d: Float) {
        daylight = d.coerceIn(0f, 1f)
        val a = .46f + .54f * daylight
        ambR = a * (if (daylight < .6f) .92f else 1f)
        ambG = a * (if (daylight < .6f) .95f else 1f)
        ambB = min(1f, a * (if (daylight < .6f) 1.18f else 1f))
    }

    /** Overcast skies dim and cool the light; a lightning flash brightens everything for a moment. */
    fun applyWeather(cloud: Float, rain: Float, storm: Float, flash: Float) {
        val dim = 1f - .14f * cloud - .1f * rain - .12f * storm
        ambR *= dim; ambG *= dim; ambB *= dim * (1f + .05f * rain)
        if (flash > 0f) { ambR += flash * .7f; ambG += flash * .7f; ambB += flash * .8f }
    }

    /** Distance haze: 0 = none. Colors fade toward [fogColor] between [fogStart] and [fogEnd] meters from the camera. */
    var fog = 0f
    var fogColor = 0xFFB8C2CC.toInt()
    var fogStart = 80f
    var fogEnd = 400f

    /** Snow lying on top faces (roofs, car roofs, awnings), 0..1. */
    var snow = 0f
    private val SNOW_WHITE = 0xFFF2F5F8.toInt()

    fun mix(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        val u = t.coerceAtMost(1f)
        val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
        val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
        return (a and 0xFF000000.toInt()) or ((ar + ((br - ar) * u).toInt()) shl 16) or ((ag + ((bg - ag) * u).toInt()) shl 8) or (ab + ((bb - ab) * u).toInt())
    }

    private fun fogged(color: Int, depth: Float): Int {
        if (fog <= 0f) return color
        val t = ((depth - fogStart) / (fogEnd - fogStart)).coerceIn(0f, 1f) * fog
        return mix(color, lit(fogColor, 1f) and 0xFFFFFF or (color and 0xFF000000.toInt()), t)
    }

    /** A top-face color with snow on it. */
    fun snowy(color: Int, amount: Float = 1f): Int = if (snow <= 0f) color else mix(color, SNOW_WHITE, snow * amount)

    fun lit(color: Int, f: Float): Int {
        val a = color ushr 24
        val r = min(255f, ((color shr 16) and 255) * f * ambR).toInt()
        val g = min(255f, ((color shr 8) and 255) * f * ambG).toInt()
        val b = min(255f, (color and 255) * f * ambB).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun withAlpha(color: Int, alpha: Float): Int = ((((color ushr 24) * alpha).toInt().coerceIn(0, 255)) shl 24) or (color and 0xFFFFFF)

    /** Horizontal direction the sunlight comes from (unit vector). */
    var sunX = .45f
    var sunY = .89f

    /** Shade for a vertical face with horizontal outward normal (nx, ny). */
    fun sideShade(nx: Float, ny: Float): Float = .6f + .34f * max(0f, nx * sunX + ny * sunY)

    /** Screen-space circle centred on a projected world point; radius in meters. */
    fun blob(x: Float, y: Float, z: Float, r: Float, color: Int, segments: Int = 10, dxFrac: Float = 0f, dyFrac: Float = 0f, squash: Float = .9f) {
        val zc = proj.camZOf(x, y, z)
        if (zc < proj.near) return
        val rr = r * proj.focal / zc
        if (rr < .4f) return
        val fc = fogged(color, zc)
        val px = proj.centerX + proj.focal * proj.camXOf(x, y, z) / zc + dxFrac * rr
        val py = proj.centerY - proj.focal * proj.camYOf(x, y, z) / zc + dyFrac * rr
        if (px + rr < 0 || px - rr > proj.screenW || py + rr < 0 || py - rr > proj.screenH) return
        val n = segments.coerceIn(5, 20)
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * (Math.PI * 2).toFloat()
            sx[i] = px + cos(a) * rr
            sy[i] = py + sin(a) * rr * squash
        }
        batch.poly(sx, sy, n, fc)
    }

    /** Emits a world polygon. Color is used as-is (apply [lit] beforehand). */
    fun poly(n: Int, xs: FloatArray, ys: FloatArray, zs: FloatArray, color: Int) {
        var allIn = true
        for (i in 0 until n) {
            cx[i] = proj.camXOf(xs[i], ys[i], zs[i])
            cy[i] = proj.camYOf(xs[i], ys[i], zs[i])
            cz[i] = proj.camZOf(xs[i], ys[i], zs[i])
            if (cz[i] < proj.near) allIn = false
        }
        var m = n
        var px = cx; var py = cy; var pz = cz
        if (!allIn) {
            m = 0
            val near = proj.near
            for (i in 0 until n) {
                val j = (i + 1) % n
                val inI = cz[i] >= near
                val inJ = cz[j] >= near
                if (inI) { ox[m] = cx[i]; oy[m] = cy[i]; oz[m] = cz[i]; m++ }
                if (inI != inJ) {
                    val t = (near - cz[i]) / (cz[j] - cz[i])
                    ox[m] = cx[i] + (cx[j] - cx[i]) * t
                    oy[m] = cy[i] + (cy[j] - cy[i]) * t
                    oz[m] = near
                    m++
                }
            }
            if (m < 3) return
            px = ox; py = oy; pz = oz
        }
        val f = proj.focal
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until m) {
            sx[i] = proj.centerX + f * px[i] / pz[i]
            sy[i] = proj.centerY - f * py[i] / pz[i]
            if (sx[i] < minX) minX = sx[i]; if (sx[i] > maxX) maxX = sx[i]
            if (sy[i] < minY) minY = sy[i]; if (sy[i] > maxY) maxY = sy[i]
        }
        if (maxX < 0 || minX > proj.screenW || maxY < 0 || minY > proj.screenH) return
        var depth = 0f
        if (fog > 0f) { for (i in 0 until m) depth += pz[i]; depth /= m }
        batch.poly(sx, sy, m, if (fog > 0f) fogged(color, depth) else color)
    }

    fun quad(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, color: Int) {
        wx[0] = x0; wy[0] = y0; wz[0] = z0
        wx[1] = x1; wy[1] = y1; wz[1] = z1
        wx[2] = x2; wy[2] = y2; wz[2] = z2
        wx[3] = x3; wy[3] = y3; wz[3] = z3
        poly(4, wx, wy, wz, color)
    }

    fun groundRect(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Int) {
        quad(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, color)
    }

    /** Flat ellipse/circle on the ground (or at height z). */
    fun disc(x: Float, y: Float, z: Float, rx: Float, ry: Float, segments: Int, color: Int) {
        val n = segments.coerceIn(3, 20)
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * (Math.PI * 2).toFloat()
            wx[i] = x + cos(a) * rx
            wy[i] = y + sin(a) * ry
            wz[i] = z
        }
        poly(n, wx, wy, wz, color)
    }

    /**
     * Axis aligned box with lighting and back-face culling.
     * [top] and [side] are base colors (unlit).
     */
    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, top: Int, side: Int, alpha: Float = 1f) {
        val cxp = proj.camX; val cyp = proj.camY; val czp = proj.camZ
        fun col(c: Int, f: Float): Int { val l = lit(c, f); return if (alpha < 1f) withAlpha(l, alpha) else l }
        if (cyp < y0) quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, col(side, sideShade(0f, -1f)))
        if (cyp > y1) quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, col(side, sideShade(0f, 1f)))
        if (cxp < x0) quad(x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, col(side, sideShade(-1f, 0f)))
        if (cxp > x1) quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, col(side, sideShade(1f, 0f)))
        if (czp > z1) quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, col(snowy(top), 1f))
        else if (czp < z0) quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, col(top, .5f))
    }

    /**
     * Oriented box: centre (x, y), length along heading, width across, from z0 to z1.
     * Heading 0 points north (-y).
     */
    fun orientedBox(x: Float, y: Float, heading: Float, length: Float, width: Float, z0: Float, z1: Float, top: Int, side: Int, front: Int = side, offsetForward: Float = 0f) {
        val fx = sin(heading); val fy = -cos(heading)
        val rx = -fy; val ry = fx
        val hl = length * .5f; val hw = width * .5f
        val ccx = x + fx * offsetForward; val ccy = y + fy * offsetForward
        // corners: front-left, front-right, back-right, back-left
        val ax = floatArrayOf(ccx + fx * hl - rx * hw, ccx + fx * hl + rx * hw, ccx - fx * hl + rx * hw, ccx - fx * hl - rx * hw)
        val ay = floatArrayOf(ccy + fy * hl - ry * hw, ccy + fy * hl + ry * hw, ccy - fy * hl + ry * hw, ccy - fy * hl - ry * hw)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val ex = ax[j] - ax[i]; val ey = ay[j] - ay[i]
            // outward normal for clockwise-in-screen ordering: rotate edge
            var nx = ey; var ny = -ex
            val len = sqrt(nx * nx + ny * ny)
            nx /= len; ny /= len
            val mx = (ax[i] + ax[j]) * .5f; val my = (ay[i] + ay[j]) * .5f
            // make sure normal points away from centre
            if ((mx - ccx) * nx + (my - ccy) * ny < 0) { nx = -nx; ny = -ny }
            if ((proj.camX - mx) * nx + (proj.camY - my) * ny > 0) {
                val base = if (i == 0) front else side
                quad(ax[i], ay[i], z0, ax[j], ay[j], z0, ax[j], ay[j], z1, ax[i], ay[i], z1, lit(base, sideShade(nx, ny)))
            }
        }
        if (proj.camZ > z1) {
            wx[0] = ax[0]; wy[0] = ay[0]; wz[0] = z1
            wx[1] = ax[1]; wy[1] = ay[1]; wz[1] = z1
            wx[2] = ax[2]; wy[2] = ay[2]; wz[2] = z1
            wx[3] = ax[3]; wy[3] = ay[3]; wz[3] = z1
            poly(4, wx, wy, wz, lit(snowy(top), 1f))
        }
    }

    /** A camera-facing billboard quad standing on the ground (used for people). */
    fun billboard(x: Float, y: Float, z0: Float, z1: Float, halfWidth: Float, color: Int) {
        // right vector of the camera in world space is (cos(yaw), -sin(yaw))
        val rx = cos(proj.yaw) * halfWidth; val ry = -sin(proj.yaw) * halfWidth
        quad(x - rx, y - ry, z0, x + rx, y + ry, z0, x + rx, y + ry, z1, x - rx, y - ry, z1, color)
    }
}

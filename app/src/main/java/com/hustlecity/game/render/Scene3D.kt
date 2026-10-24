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
    // camera axes for the GPU renderer: right, up and forward
    val rightX get() = rX; val rightY get() = rY
    val upX get() = uX; val upY get() = uY; val upZ get() = uZ
    val fwdX get() = fX; val fwdY get() = fY; val fwdZ get() = fZ
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
        // indices are 16-bit: flush before they would wrap
        if (vCount + n >= min(maxVerts, 32000) || iCount + (n - 2) * 3 >= idx.size) flush()
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
    /**
     * When set, polygons go to this GPU frame in world space instead of being projected for the Canvas: lighting,
     * fog and depth are then done per pixel by the shaders, so [lit] and [sideShade] become neutral.
     */
    var gpu: com.hustlecity.game.render.gpu.GpuFrame? = null
    /** Flat layers painted in order on the ground (roads, markings, floor tiles, shadows): they never hide anything. */
    var groundPass = false
    /** Forces the next polygons to be emissive (self-lit) on the GPU. */
    var glow = false
    /**
     * Building the GPU's cached city: cull against a screen widened by half a screen on every side, since the cache is
     * drawn from nearby camera positions (moved, turned or zoomed a little) until it's rebuilt; culling to this
     * frame's screen would leave holes at the edges.
     */
    var cacheBuild = false
    /** Where model meshes go when there's no GPU frame (the menus' previews); without either they aren't drawn. */
    var soft: SoftRaster? = null
    /** Below 1, everything drawn is see-through by this much (a viaduct hiding the player). */
    var fade = 1f
    private fun faded(color: Int): Int = if (fade >= 1f) color else withAlpha(color, (color ushr 24) / 255f * fade)
    /** Level of detail for GPU spheres: 0 far .. 2 near. */
    var sphereDetail = 1
    /** Surface material for the GPU shaders (see [Mat]); [topMaterial] overrides it on box tops (roofs) when set. */
    var material = Mat.PLAIN
    var topMaterial = -1

    /** Colors that always read as a material, whoever draws them (glass panes). */
    private val glassRgb = intArrayOf(0x3C5266, 0x1E2A36, 0x26313D, 0x4F7FA6, 0x3E6E8E, 0x5B8DA8, 0x46647E, 0x6A90A8, 0x9FC4DD, 0x9FC6E6)

    /** Wood tones used by furniture models. */
    private val woodRgb = intArrayOf(0x8B5A3C, 0xB07A52, 0x9C6B43, 0x8B6A4E, 0x5D4037, 0x3E2723)

    private fun materialFor(color: Int): Int {
        val rgb = color and 0xFFFFFF
        for (c in glassRgb) if (c == rgb) return Mat.GLASS
        if (material == Mat.PLAIN) for (c in woodRgb) if (c == rgb) return Mat.WOOD
        return material
    }

    // ------------------------------------------------------------------ model frame

    private var fOn = false
    private var fx0 = 0f; private var fy0 = 0f; private var fc = 1f; private var fs = 0f
    private var tfx = FloatArray(64); private var tfy = FloatArray(64)

    /**
     * Draws everything that follows in a rotated local frame: local (x, y) maps to (ox + x·cos − y·sin, oy + x·sin + y·cos).
     * Buildings use it so they can face any direction while their painters work in plain boxes.
     */
    fun setFrame(ox: Float, oy: Float, cos: Float, sin: Float) { fOn = true; fx0 = ox; fy0 = oy; fc = cos; fs = sin }

    fun clearFrame() { fOn = false }

    /** Camera position in the current frame (the world position when there's none). */
    val camLX: Float get() = if (!fOn) proj.camX else (proj.camX - fx0) * fc + (proj.camY - fy0) * fs
    val camLY: Float get() = if (!fOn) proj.camY else -(proj.camX - fx0) * fs + (proj.camY - fy0) * fc

    fun toWorldX(lx: Float, ly: Float) = if (!fOn) lx else fx0 + lx * fc - ly * fs
    fun toWorldY(lx: Float, ly: Float) = if (!fOn) ly else fy0 + lx * fs + ly * fc

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
    /** How hard it's raining (0..1): cars put their lights on. */
    var rain = 0f
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
        if (gpu != null) return color
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
    fun sideShade(nx: Float, ny: Float): Float {
        if (gpu != null) return 1f
        val wx = if (fOn) nx * fc - ny * fs else nx
        val wy = if (fOn) nx * fs + ny * fc else ny
        return .6f + .34f * max(0f, wx * sunX + wy * sunY)
    }

    /** Light sources keep their own color in the GPU renderer: lit windows, lamps, neon, signals, headlights. */
    private val emissiveRgb = intArrayOf(
        0xFFE3A0, 0xFFE6A8, 0xFFD27A, 0xFFF3C4, 0xFFF4D6, 0xFFEBA8, 0xFF5FD8, 0x5FE3FF, 0xFFE680, 0x2EE87A, 0xFFD34D,
        0xFF4D4D, 0xFFF1C9, 0xFFF0C2, 0xFFF2B0, 0xFFD98A, 0xFF2E2E, 0xFFF4D6,
    )

    fun isEmissive(color: Int): Boolean {
        if (glow) return true
        val rgb = color and 0xFFFFFF
        for (c in emissiveRgb) if (c == rgb) return true
        return false
    }

    /** Screen-space circle centred on a projected world point; radius in meters. */
    fun blob(lx: Float, ly: Float, z: Float, r: Float, color0: Int, segments: Int = 10, dxFrac: Float = 0f, dyFrac: Float = 0f, squash: Float = .9f) {
        val color = faded(color0)
        val x = toWorldX(lx, ly); val y = toWorldY(lx, ly)
        val g = gpu
        if (g != null) {
            // screen-space offsets become offsets along the camera's right and up axes; flat discs become ellipsoids
            val ox = proj.rightX * dxFrac * r - proj.upX * dyFrac * r
            val oy = proj.rightY * dxFrac * r - proj.upY * dyFrac * r
            val oz = -proj.upZ * dyFrac * r
            if (proj.camZOf(x + ox, y + oy, z + oz) < -r) return
            g.ellipsoid(streamFor(color), x + ox, y + oy, z + oz, r, squash, color, isEmissive(color), sphereDetail, material)
            return
        }
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
    private fun streamFor(color: Int) = when {
        groundPass -> com.hustlecity.game.render.gpu.GpuFrame.GROUND
        (color ushr 24) < 250 -> com.hustlecity.game.render.gpu.GpuFrame.TRANSPARENT
        else -> com.hustlecity.game.render.gpu.GpuFrame.OPAQUE
    }

    fun poly(n: Int, lxs: FloatArray, lys: FloatArray, zs: FloatArray, color0: Int) {
        val color = faded(color0)
        var xs = lxs; var ys = lys
        if (fOn) {
            if (tfx.size < n) { tfx = FloatArray(n * 2); tfy = FloatArray(n * 2) }
            for (i in 0 until n) { tfx[i] = fx0 + lxs[i] * fc - lys[i] * fs; tfy[i] = fy0 + lxs[i] * fs + lys[i] * fc }
            xs = tfx; ys = tfy
        }
        val g = gpu
        if (g != null) {
            // cull polygons entirely behind the camera or off one side of the screen; the GPU clips the rest
            var behind = 0
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until n) {
                val zc = proj.camZOf(xs[i], ys[i], zs[i])
                if (zc < proj.near) { behind++; continue }
                val px = proj.centerX + proj.focal * proj.camXOf(xs[i], ys[i], zs[i]) / zc
                val py = proj.centerY - proj.focal * proj.camYOf(xs[i], ys[i], zs[i]) / zc
                if (px < minX) minX = px; if (px > maxX) maxX = px
                if (py < minY) minY = py; if (py > maxY) maxY = py
            }
            if (behind == n) return
            val mx = if (cacheBuild) proj.screenW * .5f else 0f; val my = if (cacheBuild) proj.screenH * .5f else 0f
            if (behind == 0 && (maxX < -mx || minX > proj.screenW + mx || maxY < -my || minY > proj.screenH + my)) return
            g.poly(streamFor(color), n, xs, ys, zs, color, isEmissive(color), materialFor(color))
            return
        }
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
     * A soft disc of light on the ground: [color] at [alpha] in the middle fading to nothing at the rim. The GPU blends
     * it smoothly per pixel; the Canvas stacks thin rings that add up to the same falloff.
     */
    fun glowDisc(x: Float, y: Float, z: Float, r: Float, color: Int, alpha: Float) = glowOval(x, y, z, r, r, 1f, 0f, color, alpha)

    /** [glowDisc] stretched into an oval: [rx] along the unit direction ([ux], [uy]), [ry] across it. */
    fun glowOval(x: Float, y: Float, z: Float, rx: Float, ry: Float, ux: Float, uy: Float, color: Int, alpha: Float) {
        val g = gpu
        if (g != null && !fOn) {
            val r = max(rx, ry)
            val cz = proj.camZOf(x, y, z)
            if (cz < -r) return
            if (cz > proj.near) {
                val px = proj.centerX + proj.focal * proj.camXOf(x, y, z) / cz
                val py = proj.centerY - proj.focal * proj.camYOf(x, y, z) / cz
                val pr = proj.focal * r / cz * 1.5f
                if (px < -pr || px > proj.screenW + pr || py < -pr || py > proj.screenH + pr) return
            }
            g.radial(streamFor(withAlpha(color, alpha * .99f)), x, y, z, rx, ry, ux, uy, 28, withAlpha(color, alpha),
                withAlpha(color, alpha * .38f), .42f, withAlpha(color, 0f), true, materialFor(color))
            return
        }
        // each ring adds a little alpha, building toward the centre
        for (i in 0 until 6) {
            val f = 1f - i / 6f
            val n = 18 - i
            for (k in 0 until n) {
                val a = (k.toFloat() / n) * (Math.PI * 2).toFloat()
                val u = cos(a) * rx * f; val v = sin(a) * ry * f
                wx[k] = x + ux * u - uy * v; wy[k] = y + uy * u + ux * v; wz[k] = z + i * .002f
            }
            poly(n, wx, wy, wz, withAlpha(color, alpha * .2f))
        }
    }

    /**
     * Axis aligned box with lighting and back-face culling.
     * [top] and [side] are base colors (unlit).
     */
    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, top: Int, side: Int, alpha: Float = 1f) {
        val cxp = camLX; val cyp = camLY; val czp = proj.camZ
        fun col(c: Int, f: Float): Int { val l = lit(c, f); return if (alpha < 1f) withAlpha(l, alpha) else l }
        if (gpu != null && alpha >= 1f) {
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, side)
            quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, side)
            quad(x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, side)
            quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, side)
            val m = material
            if (topMaterial >= 0) material = topMaterial
            quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, snowy(top))
            material = m
            return
        }
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
            if ((camLX - mx) * nx + (camLY - my) * ny > 0) {
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

    /** Smooth shapes (tubes and ellipsoids) can be drawn: the GPU renderer is on. */
    val smooth: Boolean get() = gpu != null

    /** A smooth tapered tube from a to b (GPU only; see [GpuFrame.tube]). Positions are in the current frame. */
    fun tube(ax: Float, ay: Float, az: Float, ra: Float, bx: Float, by: Float, bz: Float, rb: Float, segs: Int, color0: Int, mat: Int = material,
             flat: Float = 1f, sx: Float = 1f, sy: Float = 0f, sz: Float = 0f, caps: Boolean = false) {
        val g = gpu ?: return
        val color = faded(color0)
        val wax = toWorldX(ax, ay); val way = toWorldY(ax, ay); val wbx = toWorldX(bx, by); val wby = toWorldY(bx, by)
        val r = max(ra, rb)
        if (proj.camZOf(wax, way, az) < -r && proj.camZOf(wbx, wby, bz) < -r) return
        val wsx = if (fOn) sx * fc - sy * fs else sx; val wsy = if (fOn) sx * fs + sy * fc else sy
        g.tube(streamFor(color), wax, way, az, ra, wbx, wby, bz, rb, segs, color, mat, flat, wsx, wsy, sz, caps)
    }

    /**
     * A smooth ellipsoid with its own axes: [rf] along (fx, fy, fz), [rr] along (rx, ry, rz) and [ru] up their cross
     * product (GPU only). [detail] 0..3.
     */
    fun ellipsoid(cx: Float, cy: Float, cz: Float, fx: Float, fy: Float, fz: Float, rf: Float, rx: Float, ry: Float, rz: Float, rr: Float, ru: Float,
                  color0: Int, mat: Int = material, detail: Int = 1) {
        val g = gpu ?: return
        val color = faded(color0)
        val wx = toWorldX(cx, cy); val wy = toWorldY(cx, cy)
        if (proj.camZOf(wx, wy, cz) < -max(rf, max(rr, ru))) return
        val wfx = if (fOn) fx * fc - fy * fs else fx; val wfy = if (fOn) fx * fs + fy * fc else fy
        val wrx = if (fOn) rx * fc - ry * fs else rx; val wry = if (fOn) rx * fs + ry * fc else ry
        g.ellipsoidAxes(streamFor(color), wx, wy, cz, wfx, wfy, fz, rf, wrx, wry, rz, rr, ru, color, mat, detail, isEmissive(color))
    }

    /**
     * Draws a model mesh for furniture of footprint [w] x [d] tiles at grid position (x, y), turned [rot] quarter
     * turns the way box models turn (GPU, or [soft] when set). Returns false when it wasn't drawn.
     */
    fun mesh(m: Models.Mesh, x: Float, y: Float, rot: Int, w: Float, d: Float, alpha: Float = 1f): Boolean {
        // the quarter turn and footprint offset of PartSorter.add, as an affine map of the model's own x, y
        return when (rot and 3) {
            0 -> meshAt(m, x, y, 0f, 1f, 0f, 0f, 1f, alpha = alpha)
            1 -> meshAt(m, x + d, y, 0f, 0f, 1f, -1f, 0f, alpha = alpha)
            2 -> meshAt(m, x + w, y + d, 0f, -1f, 0f, 0f, -1f, alpha = alpha)
            else -> meshAt(m, x, y + w, 0f, 0f, -1f, 1f, 0f, alpha = alpha)
        }
    }

    /**
     * Draws a model mesh with its own origin at grid position (x, y, z), its x axis along (ax, ay) and its y axis along
     * (bx, by), scaled by [sx], [sy], [sz] (GPU, or [soft] when set). Returns false when it wasn't drawn.
     */
    fun meshAt(
        m: Models.Mesh, x: Float, y: Float, z: Float, ax0: Float, ay0: Float, bx0: Float, by0: Float,
        sx: Float = 1f, sy: Float = 1f, sz: Float = 1f, alpha: Float = 1f,
        paint: Int = 0, tiltF: Float = 0f, tiltL: Float = 0f, lenses: Int = com.hustlecity.game.render.gpu.GpuFrame.LENSES_LIT,
        accent: Int = 0, windowsLit: Boolean = true,
    ): Boolean {
        val g = gpu
        if (g == null && soft == null) return false
        var ax = ax0; var ay = ay0; var bx = bx0; var by = by0; var ox = x; var oy = y
        if (fOn) {
            val nax = ax * fc - ay * fs; val nay = ax * fs + ay * fc
            val nbx = bx * fc - by * fs; val nby = bx * fs + by * fc
            val nox = fx0 + ox * fc - oy * fs; val noy = fy0 + ox * fs + oy * fc
            ax = nax; ay = nay; bx = nbx; by = nby; ox = nox; oy = noy
        }
        // skip it when it's wholly behind the camera
        val b = m.bounds
        val mx = (b[0] + b[3]) * .5f * sx; val my = (b[1] + b[4]) * .5f * sy
        val cx = ox + ax * mx + bx * my; val cy = oy + ay * mx + by * my
        val reach = (b[3] - b[0]) * sx + (b[4] - b[1]) * sy + (b[5] - b[2]) * sz
        if (proj.camZOf(cx, cy, z + (b[2] + b[5]) * .5f * sz) < -reach) return true
        if (g == null) {
            soft!!.mesh(proj, m, ox, oy, z, ax, ay, bx, by, sx, sy, sz, paint, lenses, accent, windowsLit)
            return true
        }
        val a = (alpha.coerceIn(0f, 1f) * fade.coerceIn(0f, 1f) * 255).toInt()
        g.mesh(if (a < 250) com.hustlecity.game.render.gpu.GpuFrame.TRANSPARENT else com.hustlecity.game.render.gpu.GpuFrame.OPAQUE, m, ox, oy, z, ax, ay, bx, by, a, sx, sy, sz,
            paint, tiltF, tiltL, lenses, accent, windowsLit)
        return true
    }

    /** A camera-facing billboard quad standing on the ground (used for people). */
    fun billboard(x: Float, y: Float, z0: Float, z1: Float, halfWidth: Float, color: Int) {
        // right vector of the camera in world space is (cos(yaw), -sin(yaw))
        val a = proj.yaw + (if (fOn) kotlin.math.atan2(fs, fc) else 0f)
        val rx = cos(a) * halfWidth; val ry = -sin(a) * halfWidth
        quad(x - rx, y - ry, z0, x + rx, y + ry, z0, x + rx, y + ry, z1, x - rx, y - ry, z1, color)
    }
}

/** GPU surface materials: procedural patterns in the world shader. */
object Mat {
    const val PLAIN = 0
    const val BRICK = 1
    const val STONE = 2
    const val GLASS = 3
    const val ASPHALT = 4
    const val SIDEWALK = 5
    const val GRASS = 6
    const val WOOD = 7
    const val TILE = 8
    const val METAL = 9
    const val ROOF = 10
    const val WATER = 11
    const val PLASTER = 12
    const val FOLIAGE = 13
    const val CONCRETE = 14
    const val FABRIC = 15
    const val PAINT = 16
    /** People: soft-lit skin, hair with a sheen along its strands. */
    const val SKIN = 17
    const val HAIR = 18
}

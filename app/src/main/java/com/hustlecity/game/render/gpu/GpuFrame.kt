package com.hustlecity.game.render.gpu

import com.hustlecity.game.render.Projector
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One frame of world geometry for the GPU renderer, built on the game thread by [com.hustlecity.game.render.Scene3D]
 * and handed to the GL thread. Vertices are 8 ints: position xyz and normal xyz as float bits, color as RGBA bytes,
 * and flags (float bits: 1 = emissive). Three streams draw in order: ground (flat layers painted in order, never
 * hiding anything), opaque (depth tested) and transparent (depth tested, not written, in emission order).
 */
class GpuFrame {
    class Stream(verts: Int) {
        var v = IntArray(verts * STRIDE); private set
        var idx = IntArray(verts * 2); private set
        var nv = 0
        var ni = 0

        fun clear() { nv = 0; ni = 0 }

        fun ensure(addVerts: Int, addIdx: Int) {
            if ((nv + addVerts) * STRIDE > v.size) v = v.copyOf(maxOf(v.size * 2, (nv + addVerts) * STRIDE))
            if (ni + addIdx > idx.size) idx = idx.copyOf(maxOf(idx.size * 2, ni + addIdx))
        }

        fun copyFrom(o: Stream) {
            if (v.size < o.nv * STRIDE) v = IntArray(o.v.size)
            if (idx.size < o.ni) idx = IntArray(o.idx.size)
            System.arraycopy(o.v, 0, v, 0, o.nv * STRIDE)
            System.arraycopy(o.idx, 0, idx, 0, o.ni)
            nv = o.nv; ni = o.ni
        }
    }

    companion object {
        const val STRIDE = 8
        const val GROUND = 0
        const val OPAQUE = 1
        const val TRANSPARENT = 2
        const val MODE_CITY = 0
        const val MODE_INTERIOR = 1
        const val MODE_MAP = 2

        /** Android ARGB to the RGBA byte order GL reads from memory (little-endian int). */
        fun rgba(argb: Int): Int {
            val a = argb ushr 24; val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
            return (a shl 24) or (b shl 16) or (g shl 8) or r
        }
    }

    /** Set when the frame is published, so the GL thread re-uploads only new frames. */
    var version = 0L

    /**
     * Cached geometry that rarely changes (buildings, roads, trees), drawn before this frame's own streams. The GL
     * thread uploads it only when its [version] changes. [skip] lists index ranges (start, end pairs, sorted) of
     * its opaque stream to leave out this frame: buildings hiding the player, which this frame draws see-through.
     */
    @Volatile var staticGeom: GpuFrame? = null
    var skip = IntArray(16)
    var skipCount = 0

    val streams = arrayOf(Stream(1 shl 14), Stream(1 shl 16), Stream(1 shl 13))

    // ---- uniforms
    var width = 1f; var height = 1f
    val cam = FloatArray(3)
    val right = FloatArray(3); val up = FloatArray(3); val fwd = FloatArray(3)
    var focal = 1f; var centerX = 0f; var centerY = 0f
    var near = 1f; var far = 3000f
    /** Direction toward the sun (unit), its color (linear RGB) and the hemisphere ambient colors. */
    val sunDir = floatArrayOf(.4f, .5f, .77f)
    val sunColor = floatArrayOf(1f, .95f, .85f)
    val skyAmbient = floatArrayOf(.45f, .5f, .6f)
    val groundAmbient = floatArrayOf(.3f, .28f, .25f)
    val fogColor = floatArrayOf(.72f, .76f, .8f)
    var fogStart = 200f; var fogEnd = 900f; var fogAmount = 0f
    val skyTop = floatArrayOf(.3f, .5f, .8f)
    val skyHorizon = floatArrayOf(.75f, .82f, .9f)
    var mode = MODE_CITY
    var daylight = 1f
    var wet = 0f
    var exposure = 1f
    /** Background clear color for interiors and maps (sRGB ARGB). */
    var clearColor = 0xFF16181D.toInt()
    /** Seconds, for animated water. */
    var time = 0f

    // ---- sun shadow map
    /** World to shadow-map clip space, column-major. */
    val lightMat = FloatArray(16)
    var shadowOn = false
    var shadowStrength = 0f
    var shadowSize = 2048
    var shadowOffset = .3f
    var shadowTexel = 1f / 2048f

    /**
     * Fits an orthographic sun camera around the ground rectangle [x0, y0]-[x1, y1] (what's on screen) so everything
     * that can cast a shadow into it, up to [maxH] meters tall, is covered.
     */
    fun setShadow(x0: Float, y0: Float, x1: Float, y1: Float, maxH: Float, size: Int, strength: Float) {
        shadowSize = size
        shadowStrength = strength
        shadowOn = strength > .02f
        if (!shadowOn) return
        val lx = -sunDir[0]; val ly = -sunDir[1]; val lz = -sunDir[2]
        // right = forward x world-up, up = right x forward
        var rx = ly * 1f - lz * 0f; var ry = lz * 0f - lx * 1f; var rz = 0f
        var rl = sqrt(rx * rx + ry * ry + rz * rz)
        if (rl < 1e-4f) { rx = 1f; ry = 0f; rz = 0f; rl = 1f }
        rx /= rl; ry /= rl; rz /= rl
        val ux = ry * lz - rz * ly; val uy = rz * lx - rx * lz; val uz = rx * ly - ry * lx
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minD = Float.MAX_VALUE; var maxD = -Float.MAX_VALUE
        for (k in 0 until 8) {
            val px = if (k and 1 == 0) x0 else x1; val py = if (k and 2 == 0) y0 else y1; val pz = if (k and 4 == 0) 0f else maxH
            val X = px * rx + py * ry + pz * rz; val Y = px * ux + py * uy + pz * uz; val D = px * lx + py * ly + pz * lz
            if (pz == 0f) { minX = minOf(minX, X); maxX = maxOf(maxX, X); minY = minOf(minY, Y); maxY = maxOf(maxY, Y) }
            minD = minOf(minD, D); maxD = maxOf(maxD, D)
        }
        // casters between the sun and the receivers sit "before" them along the light
        minD -= maxH / maxOf(.15f, sunDir[2]) + 20f
        maxD += 5f
        val w = maxOf(maxX - minX, 1f); val h = maxOf(maxY - minY, 1f); val dd = maxOf(maxD - minD, 1f)
        val m = lightMat
        java.util.Arrays.fill(m, 0f)
        m[0] = rx * 2f / w; m[4] = ry * 2f / w; m[8] = rz * 2f / w; m[12] = -(minX + maxX) / w
        m[1] = ux * 2f / h; m[5] = uy * 2f / h; m[9] = uz * 2f / h; m[13] = -(minY + maxY) / h
        m[2] = lx * 2f / dd; m[6] = ly * 2f / dd; m[10] = lz * 2f / dd; m[14] = -(minD + maxD) / dd
        m[15] = 1f
        shadowTexel = 1f / size
        shadowOffset = maxOf(w, h) / size * 1.6f
    }

    fun clear() { for (s in streams) s.clear(); staticGeom = null; skipCount = 0 }

    fun setCamera(p: Projector) {
        width = p.screenW; height = p.screenH
        cam[0] = p.camX; cam[1] = p.camY; cam[2] = p.camZ
        right[0] = p.rightX; right[1] = p.rightY; right[2] = 0f
        up[0] = p.upX; up[1] = p.upY; up[2] = p.upZ
        fwd[0] = p.fwdX; fwd[1] = p.fwdY; fwd[2] = p.fwdZ
        focal = p.focal; centerX = p.centerX; centerY = p.centerY
        near = maxOf(p.near, p.dist * .02f).coerceAtMost(4f)
        far = p.dist * 14f + 600f
    }

    fun copyFrom(o: GpuFrame) {
        for (i in streams.indices) streams[i].copyFrom(o.streams[i])
        width = o.width; height = o.height
        o.cam.copyInto(cam); o.right.copyInto(right); o.up.copyInto(up); o.fwd.copyInto(fwd)
        focal = o.focal; centerX = o.centerX; centerY = o.centerY; near = o.near; far = o.far
        o.sunDir.copyInto(sunDir); o.sunColor.copyInto(sunColor); o.skyAmbient.copyInto(skyAmbient); o.groundAmbient.copyInto(groundAmbient)
        o.fogColor.copyInto(fogColor); fogStart = o.fogStart; fogEnd = o.fogEnd; fogAmount = o.fogAmount
        o.skyTop.copyInto(skyTop); o.skyHorizon.copyInto(skyHorizon)
        mode = o.mode; daylight = o.daylight; wet = o.wet; exposure = o.exposure; clearColor = o.clearColor; time = o.time
        o.lightMat.copyInto(lightMat); shadowOn = o.shadowOn; shadowStrength = o.shadowStrength; shadowSize = o.shadowSize
        shadowOffset = o.shadowOffset; shadowTexel = o.shadowTexel
        staticGeom = o.staticGeom
        if (skip.size < o.skipCount * 2) skip = IntArray(o.skipCount * 2)
        System.arraycopy(o.skip, 0, skip, 0, o.skipCount * 2); skipCount = o.skipCount
    }

    private var nx = 0f; private var ny = 0f; private var nz = 1f

    /** Per-vertex flags: material id times two, plus one when emissive. */
    private fun flags(emissive: Boolean, material: Int) = material * 2f + (if (emissive) 1f else 0f)

    /** Newell's method: a robust normal for any planar polygon; false if degenerate. */
    private fun normalOf(n: Int, xs: FloatArray, ys: FloatArray, zs: FloatArray): Boolean {
        var ax = 0f; var ay = 0f; var az = 0f
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            ax += (ys[i] - ys[j]) * (zs[i] + zs[j])
            ay += (zs[i] - zs[j]) * (xs[i] + xs[j])
            az += (xs[i] - xs[j]) * (ys[i] + ys[j])
        }
        val len = sqrt(ax * ax + ay * ay + az * az)
        if (len < 1e-9f) return false
        nx = ax / len; ny = ay / len; nz = az / len
        return true
    }

    /** Appends a convex polygon (fan triangulated). */
    fun poly(stream: Int, n: Int, xs: FloatArray, ys: FloatArray, zs: FloatArray, argb: Int, emissive: Boolean, material: Int = 0) {
        if (n < 3 || !normalOf(n, xs, ys, zs)) return
        val s = streams[stream]
        s.ensure(n, (n - 2) * 3)
        val base = s.nv
        val col = rgba(argb)
        val fl = java.lang.Float.floatToRawIntBits(flags(emissive, material))
        val bnx = java.lang.Float.floatToRawIntBits(nx); val bny = java.lang.Float.floatToRawIntBits(ny); val bnz = java.lang.Float.floatToRawIntBits(nz)
        var o = base * STRIDE
        val v = s.v
        for (i in 0 until n) {
            v[o] = java.lang.Float.floatToRawIntBits(xs[i]); v[o + 1] = java.lang.Float.floatToRawIntBits(ys[i]); v[o + 2] = java.lang.Float.floatToRawIntBits(zs[i])
            v[o + 3] = bnx; v[o + 4] = bny; v[o + 5] = bnz
            v[o + 6] = col; v[o + 7] = fl
            o += STRIDE
        }
        s.nv += n
        val idx = s.idx
        var k = s.ni
        for (i in 1 until n - 1) { idx[k++] = base; idx[k++] = base + i; idx[k++] = base + i + 1 }
        s.ni = k
    }

    /** Appends a lit ellipsoid (tree canopies, globes) with smooth normals. */
    fun ellipsoid(stream: Int, x: Float, y: Float, z: Float, r: Float, squash: Float, argb: Int, emissive: Boolean, detail: Int, material: Int = 0) {
        val t = SPHERES[detail.coerceIn(0, 2)]
        val rings = t.rings; val segs = t.segs
        val s = streams[stream]
        val nVerts = (rings + 1) * (segs + 1)
        s.ensure(nVerts, rings * segs * 6)
        val base = s.nv
        val col = rgba(argb)
        val fl = java.lang.Float.floatToRawIntBits(flags(emissive, material))
        val sq = if (abs(squash) < 1e-3f) 1f else squash
        val v = s.v
        var o = base * STRIDE
        val u = t.unit
        for (k in 0 until nVerts) {
            val ux = u[k * 3]; val uy = u[k * 3 + 1]; val uz = u[k * 3 + 2]
            v[o] = java.lang.Float.floatToRawIntBits(x + ux * r); v[o + 1] = java.lang.Float.floatToRawIntBits(y + uy * r)
            v[o + 2] = java.lang.Float.floatToRawIntBits(z + uz * r * sq)
            // an ellipsoid's normal scales the vertical part by the inverse squash
            val nnz = uz / sq
            val l = sqrt(ux * ux + uy * uy + nnz * nnz)
            v[o + 3] = java.lang.Float.floatToRawIntBits(ux / l); v[o + 4] = java.lang.Float.floatToRawIntBits(uy / l); v[o + 5] = java.lang.Float.floatToRawIntBits(nnz / l)
            v[o + 6] = col; v[o + 7] = fl
            o += STRIDE
        }
        s.nv += nVerts
        val idx = s.idx
        var k = s.ni
        for (ri in 0 until rings) for (si in 0 until segs) {
            val a = base + ri * (segs + 1) + si
            val b = a + segs + 1
            idx[k++] = a; idx[k++] = b; idx[k++] = a + 1
            idx[k++] = a + 1; idx[k++] = b; idx[k++] = b + 1
        }
        s.ni = k
    }

    private class Sphere(val rings: Int, val segs: Int) {
        val unit = FloatArray((rings + 1) * (segs + 1) * 3).also { u ->
            var k = 0
            for (ri in 0..rings) {
                val phi = Math.PI * ri / rings
                for (si in 0..segs) {
                    val th = 2 * Math.PI * si / segs
                    u[k++] = (kotlin.math.sin(phi) * kotlin.math.cos(th)).toFloat()
                    u[k++] = (kotlin.math.sin(phi) * kotlin.math.sin(th)).toFloat()
                    u[k++] = kotlin.math.cos(phi).toFloat()
                }
            }
        }
    }

    private val SPHERES = arrayOf(Sphere(4, 7), Sphere(5, 9), Sphere(7, 12))

    val triangleCount: Int get() = streams.sumOf { it.ni } / 3
}

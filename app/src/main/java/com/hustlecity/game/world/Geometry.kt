package com.hustlecity.game.world

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 2D helpers for the city generator: segments, polygons (flat x, y arrays) and oriented rectangles. */
object Geo {
    /**
     * Intersection of segments AB and CD. On a hit, [out] gets the parameters along AB and CD (0..1).
     * Parallel segments never hit.
     */
    fun segHit(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, out: FloatArray): Boolean {
        val rx = bx - ax; val ry = by - ay
        val sx = dx - cx; val sy = dy - cy
        val den = rx * sy - ry * sx
        if (abs(den) < 1e-6f) return false
        val qx = cx - ax; val qy = cy - ay
        val t = (qx * sy - qy * sx) / den
        val u = (qx * ry - qy * rx) / den
        if (t < 0f || t > 1f || u < 0f || u > 1f) return false
        out[0] = t; out[1] = u
        return true
    }

    /** Ray from (ox, oy) along (dx, dy) against segment CD: distance along the ray, or -1. */
    fun rayHit(ox: Float, oy: Float, dx: Float, dy: Float, cx: Float, cy: Float, ex: Float, ey: Float): Float {
        val sx = ex - cx; val sy = ey - cy
        val den = dx * sy - dy * sx
        if (abs(den) < 1e-6f) return -1f
        val qx = cx - ox; val qy = cy - oy
        val t = (qx * sy - qy * sx) / den
        val u = (qx * dy - qy * dx) / den
        return if (t > 0f && u in 0f..1f) t else -1f
    }

    fun distToSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-9f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        val qx = ax + dx * t - px; val qy = ay + dy * t - py
        return sqrt(qx * qx + qy * qy)
    }

    /** Even-odd point in polygon; [poly] holds x, y pairs. */
    fun inside(x: Float, y: Float, poly: FloatArray): Boolean {
        var c = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[i * 2]; val yi = poly[i * 2 + 1]
            val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    /** Distance from a point to a polygon's outline. */
    fun distToPoly(x: Float, y: Float, poly: FloatArray): Float {
        var best = Float.MAX_VALUE
        val n = poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            best = min(best, distToSeg(x, y, poly[i * 2], poly[i * 2 + 1], poly[j * 2], poly[j * 2 + 1]))
        }
        return best
    }

    /** The parts of segment AB inside a polygon, as x0, y0, x1, y1 quads. */
    fun clip(ax: Float, ay: Float, bx: Float, by: Float, poly: FloatArray): List<FloatArray> {
        val ts = ArrayList<Float>()
        ts += 0f; ts += 1f
        val n = poly.size / 2
        val hit = FloatArray(2)
        for (i in 0 until n) {
            val j = (i + 1) % n
            if (segHit(ax, ay, bx, by, poly[i * 2], poly[i * 2 + 1], poly[j * 2], poly[j * 2 + 1], hit)) ts += hit[0]
        }
        ts.sort()
        val out = ArrayList<FloatArray>()
        for (k in 0 until ts.size - 1) {
            val t0 = ts[k]; val t1 = ts[k + 1]
            if (t1 - t0 < 1e-4f) continue
            val tm = (t0 + t1) * .5f
            if (inside(ax + (bx - ax) * tm, ay + (by - ay) * tm, poly)) {
                out += floatArrayOf(ax + (bx - ax) * t0, ay + (by - ay) * t0, ax + (bx - ax) * t1, ay + (by - ay) * t1)
            }
        }
        // merge touching pieces
        val merged = ArrayList<FloatArray>()
        for (s in out) {
            val last = merged.lastOrNull()
            if (last != null && abs(last[2] - s[0]) < 1e-3f && abs(last[3] - s[1]) < 1e-3f) { last[2] = s[2]; last[3] = s[3] } else merged += s
        }
        return merged
    }

    /** Polygon area (positive for counter-clockwise in a y-down world is fine: only the magnitude is used). */
    fun area(poly: FloatArray): Float {
        var a = 0f
        val n = poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1]
        }
        return abs(a) * .5f
    }

    /**
     * Ear-clipping triangulation of a simple polygon (convex or not): returns x, y triples, three vertices per
     * triangle. Degenerate leftovers are dropped.
     */
    fun triangulate(poly: FloatArray): FloatArray {
        var n = poly.size / 2
        if (n < 3) return FloatArray(0)
        // drop repeated closing vertex
        if (abs(poly[0] - poly[(n - 1) * 2]) < 1e-4f && abs(poly[1] - poly[(n - 1) * 2 + 1]) < 1e-4f) n--
        val idx = ArrayList<Int>(n)
        var signed = 0f
        for (i in 0 until n) { val j = (i + 1) % n; signed += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1] }
        if (signed > 0) for (i in 0 until n) idx += i else for (i in n - 1 downTo 0) idx += i
        val out = ArrayList<Float>(n * 6)
        fun x(i: Int) = poly[i * 2]
        fun y(i: Int) = poly[i * 2 + 1]
        fun cross(a: Int, b: Int, c: Int) = (x(b) - x(a)) * (y(c) - y(a)) - (y(b) - y(a)) * (x(c) - x(a))
        fun inTri(p: Int, a: Int, b: Int, c: Int): Boolean {
            val d1 = (x(p) - x(b)) * (y(a) - y(b)) - (x(a) - x(b)) * (y(p) - y(b))
            val d2 = (x(p) - x(c)) * (y(b) - y(c)) - (x(b) - x(c)) * (y(p) - y(c))
            val d3 = (x(p) - x(a)) * (y(c) - y(a)) - (x(c) - x(a)) * (y(p) - y(a))
            val neg = d1 < 0 || d2 < 0 || d3 < 0; val pos = d1 > 0 || d2 > 0 || d3 > 0
            return !(neg && pos)
        }
        var guard = 0
        var i = 0
        while (idx.size > 3 && guard < n * n) {
            guard++
            val m = idx.size
            val a = idx[(i + m - 1) % m]; val b = idx[i % m]; val c = idx[(i + 1) % m]
            var ear = cross(a, b, c) > 1e-6f
            if (ear) for (k in idx) {
                if (k == a || k == b || k == c) continue
                if (inTri(k, a, b, c)) { ear = false; break }
            }
            if (ear) {
                out += x(a); out += y(a); out += x(b); out += y(b); out += x(c); out += y(c)
                idx.removeAt(i % m)
                if (i >= idx.size) i = 0
            } else i = (i + 1) % m
        }
        if (idx.size == 3) for (k in idx) { out += x(k); out += y(k) }
        return out.toFloatArray()
    }

    /** Bounding box of a polygon: x0, y0, x1, y1. */
    fun bounds(poly: FloatArray): FloatArray {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until poly.size / 2) {
            x0 = min(x0, poly[i * 2]); x1 = max(x1, poly[i * 2])
            y0 = min(y0, poly[i * 2 + 1]); y1 = max(y1, poly[i * 2 + 1])
        }
        return floatArrayOf(x0, y0, x1, y1)
    }

    /** A circle or ellipse as a polygon. */
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 32, rot: Float = 0f): FloatArray {
        val out = FloatArray(n * 2)
        val cr = kotlin.math.cos(rot); val sr = kotlin.math.sin(rot)
        for (i in 0 until n) {
            val a = i * (Math.PI * 2 / n).toFloat()
            val x = kotlin.math.cos(a) * rx; val y = kotlin.math.sin(a) * ry
            out[i * 2] = cx + x * cr - y * sr
            out[i * 2 + 1] = cy + x * sr + y * cr
        }
        return out
    }

    /** Offsets a polyline sideways by [d] (positive = to the left of travel in a y-down world) into a new polyline. */
    fun offset(pts: FloatArray, d: Float): FloatArray {
        val n = pts.size / 2
        val out = FloatArray(pts.size)
        for (i in 0 until n) {
            val i0 = max(0, i - 1); val i1 = min(n - 1, i + 1)
            var dx = pts[i1 * 2] - pts[i0 * 2]; var dy = pts[i1 * 2 + 1] - pts[i0 * 2 + 1]
            val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            dx /= l; dy /= l
            out[i * 2] = pts[i * 2] + dy * d
            out[i * 2 + 1] = pts[i * 2 + 1] - dx * d
        }
        return out
    }

    /** A band polygon of half-width [w] around a polyline (rivers, moats). */
    fun band(pts: FloatArray, w: Float): FloatArray {
        val l = offset(pts, w); val r = offset(pts, -w)
        val n = pts.size / 2
        val out = FloatArray(pts.size * 2)
        for (i in 0 until n) { out[i * 2] = l[i * 2]; out[i * 2 + 1] = l[i * 2 + 1] }
        for (i in 0 until n) { out[(n + i) * 2] = r[(n - 1 - i) * 2]; out[(n + i) * 2 + 1] = r[(n - 1 - i) * 2 + 1] }
        return out
    }

    /** Smooth polyline through control points (Catmull-Rom), [per] points per span. */
    fun spline(ctrl: FloatArray, per: Int = 6): FloatArray {
        val n = ctrl.size / 2
        if (n < 3) return ctrl.copyOf()
        val out = ArrayList<Float>()
        fun p(i: Int, k: Int) = ctrl[i.coerceIn(0, n - 1) * 2 + k]
        for (i in 0 until n - 1) {
            for (s in 0 until per) {
                val t = s / per.toFloat()
                val t2 = t * t; val t3 = t2 * t
                for (k in 0..1) {
                    val p0 = p(i - 1, k); val p1 = p(i, k); val p2 = p(i + 1, k); val p3 = p(i + 2, k)
                    out += .5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
                }
            }
        }
        out += ctrl[(n - 1) * 2]; out += ctrl[(n - 1) * 2 + 1]
        return out.toFloatArray()
    }
}

/**
 * Rectangle with its own orientation: centre, unit axis [ux], [uy] along the width (the perpendicular runs along
 * the depth), and half sizes.
 */
class OBB(val cx: Float, val cy: Float, val ux: Float, val uy: Float, val hw: Float, val hd: Float) {
    val vx get() = -uy
    val vy get() = ux

    fun corners(out: FloatArray) {
        val ax = ux * hw; val ay = uy * hw
        val bx = vx * hd; val by = vy * hd
        out[0] = cx - ax - bx; out[1] = cy - ay - by
        out[2] = cx + ax - bx; out[3] = cy + ay - by
        out[4] = cx + ax + bx; out[5] = cy + ay + by
        out[6] = cx - ax + bx; out[7] = cy - ay + by
    }

    private fun radiusOn(nx: Float, ny: Float) = hw * abs(ux * nx + uy * ny) + hd * abs(vx * nx + vy * ny)

    fun overlaps(o: OBB, margin: Float = 0f): Boolean {
        val dx = o.cx - cx; val dy = o.cy - cy
        for (axis in 0 until 4) {
            val nx: Float; val ny: Float
            when (axis) { 0 -> { nx = ux; ny = uy }; 1 -> { nx = vx; ny = vy }; 2 -> { nx = o.ux; ny = o.uy }; else -> { nx = o.vx; ny = o.vy } }
            if (abs(dx * nx + dy * ny) > radiusOn(nx, ny) + o.radiusOn(nx, ny) + margin) return false
        }
        return true
    }

    /** Distance from the rectangle to segment AB (0 when they touch). */
    fun distToSeg(ax: Float, ay: Float, bx: Float, by: Float): Float {
        // in local coordinates the rectangle is axis aligned
        val lax = (ax - cx) * ux + (ay - cy) * uy; val lay = (ax - cx) * vx + (ay - cy) * vy
        val lbx = (bx - cx) * ux + (by - cy) * uy; val lby = (bx - cx) * vx + (by - cy) * vy
        // the segment crosses the rectangle?
        if (segBox(lax, lay, lbx, lby)) return 0f
        var best = min(pointBox(lax, lay), pointBox(lbx, lby))
        // closest points from the rectangle's corners to the segment
        for (sx in -1..1 step 2) for (sy in -1..1 step 2) {
            best = min(best, Geo.distToSeg(sx * hw, sy * hd, lax, lay, lbx, lby))
        }
        return best
    }

    private fun pointBox(x: Float, y: Float): Float {
        val dx = max(abs(x) - hw, 0f); val dy = max(abs(y) - hd, 0f)
        return sqrt(dx * dx + dy * dy)
    }

    private fun segBox(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        var t0 = 0f; var t1 = 1f
        val dx = bx - ax; val dy = by - ay
        fun clip(p: Float, q: Float): Boolean {
            if (abs(p) < 1e-9f) return q >= 0f
            val r = q / p
            if (p < 0) { if (r > t1) return false; if (r > t0) t0 = r } else { if (r < t0) return false; if (r < t1) t1 = r }
            return true
        }
        return clip(-dx, ax + hw) && clip(dx, hw - ax) && clip(-dy, ay + hd) && clip(dy, hd - ay)
    }

    fun contains(x: Float, y: Float, margin: Float = 0f): Boolean {
        val lx = (x - cx) * ux + (y - cy) * uy; val ly = (x - cx) * vx + (y - cy) * vy
        return abs(lx) <= hw + margin && abs(ly) <= hd + margin
    }
}

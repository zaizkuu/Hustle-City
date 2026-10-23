package com.hustlecity.game.world

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What stands on the pavement: street furniture, tree trunks, signal poles, stoops. Each is an oriented rectangle on
 * the ground. Street furniture is placed around what's already here, people on the sidewalks step round it, and shops
 * keep their sidewalk tables clear of it.
 */
class Pavement(bounds: FloatArray) {
    private val cell = 20f
    private val gx0 = bounds[0] - 40f
    private val gy0 = bounds[1] - 40f
    private val cols = ((bounds[2] + 40f - gx0) / cell).toInt() + 1
    private val rows = ((bounds[3] + 40f - gy0) / cell).toInt() + 1
    private val grid = Array(cols * rows) { IntArray(4) }
    private val gridN = IntArray(cols * rows)

    var count = 0; private set
    // center, unit axis u (v is u turned a quarter), half extents along u and v
    private var cx = FloatArray(1024); private var cy = FloatArray(1024)
    private var ux = FloatArray(1024); private var uy = FloatArray(1024)
    private var hu = FloatArray(1024); private var hv = FloatArray(1024)
    /** What it is (see the kinds below), for whoever asks. */
    private var what = IntArray(1024)

    companion object {
        const val PROP = 0
        const val TREE = 1
        const val POLE = 2
        const val STOOP = 3
    }

    private fun cellOf(v: Float, v0: Float, n: Int) = ((v - v0) / cell).toInt().coerceIn(0, n - 1)

    /** Adds a rectangle centered on (x, y), [halfU] along (axisX, axisY) and [halfV] across it. */
    fun add(x: Float, y: Float, axisX: Float, axisY: Float, halfU: Float, halfV: Float, kind: Int): Int {
        if (count == cx.size) {
            val n = count * 2
            cx = cx.copyOf(n); cy = cy.copyOf(n); ux = ux.copyOf(n); uy = uy.copyOf(n); hu = hu.copyOf(n); hv = hv.copyOf(n); what = what.copyOf(n)
        }
        val i = count++
        cx[i] = x; cy[i] = y; ux[i] = axisX; uy[i] = axisY; hu[i] = halfU; hv[i] = halfV; what[i] = kind
        val r = max(halfU, halfV) * 1.42f
        for (row in cellOf(y - r, gy0, rows)..cellOf(y + r, gy0, rows)) for (col in cellOf(x - r, gx0, cols)..cellOf(x + r, gx0, cols)) {
            val c = row * cols + col
            if (gridN[c] == grid[c].size) grid[c] = grid[c].copyOf(grid[c].size * 2)
            grid[c][gridN[c]++] = i
        }
        return i
    }

    fun kind(i: Int) = what[i]

    /** Calls [f] with every rectangle that may reach within [r] of (x, y) (each once). */
    fun forNear(x: Float, y: Float, r: Float, f: (Int) -> Unit) {
        val seen = HashSet<Int>()
        for (row in cellOf(y - r, gy0, rows)..cellOf(y + r, gy0, rows)) for (col in cellOf(x - r, gx0, cols)..cellOf(x + r, gx0, cols)) {
            val c = row * cols + col
            val list = grid[c]
            for (k in 0 until gridN[c]) if (seen.add(list[k])) f(list[k])
        }
    }

    /** Whether point (x, y) is within [pad] of rectangle [i]. */
    fun contains(i: Int, x: Float, y: Float, pad: Float = 0f): Boolean {
        val dx = x - cx[i]; val dy = y - cy[i]
        return abs(dx * ux[i] + dy * uy[i]) <= hu[i] + pad && abs(-dx * uy[i] + dy * ux[i]) <= hv[i] + pad
    }

    /** Whether a rectangle centered on (x, y) like those [add] takes, grown by [pad], overlaps anything here. */
    fun overlaps(x: Float, y: Float, axisX: Float, axisY: Float, halfU: Float, halfV: Float, pad: Float = 0f): Boolean {
        val r = max(halfU, halfV) * 1.42f + pad + 4f
        var hit = false
        forNear(x, y, r) { i -> if (!hit && overlap(i, x, y, axisX, axisY, halfU + pad, halfV + pad)) hit = true }
        return hit
    }

    /** Separating axes between rectangle [i] and the given one. */
    private fun overlap(i: Int, x: Float, y: Float, ax: Float, ay: Float, h1: Float, h2: Float): Boolean {
        val bx = -ay; val by = ax
        val ix = ux[i]; val iy = uy[i]; val jx = -iy; val jy = ix
        val dx = x - cx[i]; val dy = y - cy[i]
        for (k in 0..3) {
            val (px, py) = when (k) { 0 -> ix to iy; 1 -> jx to jy; 2 -> ax to ay; else -> bx to by }
            val d = abs(dx * px + dy * py)
            val r1 = hu[i] * abs(ix * px + iy * py) + hv[i] * abs(jx * px + jy * py)
            val r2 = h1 * abs(ax * px + ay * py) + h2 * abs(bx * px + by * py)
            if (d > r1 + r2) return false
        }
        return true
    }

    /**
     * Where rectangle [i] lies across a sidewalk running from (ox, oy) along (dx, dy), with (nx, ny) pointing out from
     * the road: out = (along from, along to, out from, out to).
     */
    fun span(i: Int, ox: Float, oy: Float, dx: Float, dy: Float, nx: Float, ny: Float, out: FloatArray) {
        var a0 = Float.MAX_VALUE; var a1 = -Float.MAX_VALUE; var o0 = Float.MAX_VALUE; var o1 = -Float.MAX_VALUE
        val vx = -uy[i]; val vy = ux[i]
        for (s in 0..3) {
            val su = if (s and 1 == 0) -1f else 1f; val sv = if (s < 2) -1f else 1f
            val px = cx[i] + ux[i] * hu[i] * su + vx * hv[i] * sv - ox
            val py = cy[i] + uy[i] * hu[i] * su + vy * hv[i] * sv - oy
            val a = px * dx + py * dy; val o = px * nx + py * ny
            a0 = min(a0, a); a1 = max(a1, a); o0 = min(o0, o); o1 = max(o1, o)
        }
        out[0] = a0; out[1] = a1; out[2] = o0; out[3] = o1
    }
}

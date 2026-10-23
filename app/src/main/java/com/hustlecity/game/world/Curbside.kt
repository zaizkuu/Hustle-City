package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cars parked along the curbs of the city's ordinary streets (not the avenues, whose outer lanes carry traffic),
 * nose to tail with gaps, clear of junctions and of the entrances to parking lots and gas stations. Busy cities park
 * more; Tokyo keeps its streets almost clear. A spot where one of your own vehicles stands leaves its car out
 * ([hide]), and your car bumps into the rest ([bump]).
 */
class Curbside(city: City) {
    companion object {
        private val COLORS = intArrayOf(c(0xFFC0392B), c(0xFFE0E0E0), c(0xFF2C3E50), c(0xFF7F8C8D), c(0xFF1F3A93), c(0xFF212121), c(0xFFF5F5F5),
            c(0xFF8E2442), c(0xFF27AE60), c(0xFFB7B9BC), c(0xFF34495E), c(0xFFE8C547), c(0xFF5D4037), c(0xFF16A085))
        /** Body styles (see CarPainter) and their length, width and height. */
        private val STYLES = intArrayOf(0, 0, 0, 0, 9, 9, 1, 0, 3, 0)
        fun length(style: Int) = when (style) { 1 -> 5.2f; 3 -> 4.5f; 6 -> 3.4f; 9 -> 4.8f; else -> 4.4f }
        fun width(style: Int) = when (style) { 6 -> 1.48f; 9 -> 2.0f; 3 -> 1.95f; else -> 1.85f }
        fun height(style: Int) = when (style) { 1 -> 1.8f; 3 -> 1.2f; 6 -> 1.7f; 9 -> 1.85f; else -> 1.45f }
    }

    var count = 0; private set
    var x = FloatArray(512); private set
    var y = FloatArray(512); private set
    var heading = FloatArray(512); private set
    var style = IntArray(512); private set
    var color = IntArray(512); private set
    /** Unit vector from the curb toward the middle of the road, per car. */
    private var outX = FloatArray(512); private var outY = FloatArray(512)
    /** Left out because one of your vehicles is parked there. */
    val hidden: BooleanArray get() = hid
    private var hid = BooleanArray(512)

    private val cell = 40f
    private val gx0 = city.bounds[0] - 40f
    private val gy0 = city.bounds[1] - 40f
    private val cols = ((city.bounds[2] + 40f - gx0) / cell).toInt() + 1
    private val rows = ((city.bounds[3] + 40f - gy0) / cell).toInt() + 1
    private val grid: Array<IntArray>

    private fun add(px: Float, py: Float, h: Float, st: Int, col: Int, ox: Float, oy: Float) {
        if (count == x.size) {
            x = x.copyOf(count * 2); y = y.copyOf(count * 2); heading = heading.copyOf(count * 2)
            style = style.copyOf(count * 2); color = color.copyOf(count * 2); hid = hid.copyOf(count * 2)
            outX = outX.copyOf(count * 2); outY = outY.copyOf(count * 2)
        }
        x[count] = px; y[count] = py; heading[count] = h; style[count] = st; color[count] = col; outX[count] = ox; outY[count] = oy
        count++
    }

    init {
        var seed = 77 + city.mapId.hashCode()
        fun rand(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65536f }
        val fill = when (city.mapId) { Maps.TOKYO -> .08f; Maps.SEOUL -> .3f; Maps.MANILA -> .42f; else -> .62f }
        val tokyo = city.mapId == Maps.TOKYO
        // entrances nobody parks across
        val noParking = city.buildings.filter { it.kind == BKind.PARKING || it.kind == BKind.GAS || it.placeId != null }
        for (e in city.roads.edges) {
            if (e.cls != RoadClass.STREET || e.overWater || e.under || e.len < 20f) continue
            for (side in -1..1 step 2) {
                // n points from the centre line to this curb
                val nx = e.uy * side; val ny = -e.ux * side
                val off = e.cls.halfWidth - 1.0f
                val h = city.curbHeading(e, -side)
                var s = e.clearA + 6f
                val end = e.len - e.clearB - 6f
                while (s < end) {
                    val st = if (tokyo) 6 else STYLES[(rand() * STYLES.size).toInt().coerceAtMost(STYLES.size - 1)]
                    val len = length(st)
                    val cs = s + len / 2
                    if (cs + len / 2 > end) break
                    val px = e.ax + e.ux * cs + nx * off; val py = e.ay + e.uy * cs + ny * off
                    val free = noParking.none { abs(it.doorX - px) < 9f && abs(it.doorY - py) < 9f }
                    if (free && rand() < fill && !city.inWater(px, py)) add(px, py, h, st, COLORS[(rand() * COLORS.size).toInt().coerceAtMost(COLORS.size - 1)], -nx, -ny)
                    s += len + .9f + rand() * 1.4f
                }
            }
        }
        val lists = Array(cols * rows) { ArrayList<Int>() }
        for (i in 0 until count) lists[cellOf(y[i], gy0, rows) * cols + cellOf(x[i], gx0, cols)] += i
        grid = Array(lists.size) { lists[it].toIntArray() }
    }

    private fun cellOf(v: Float, v0: Float, n: Int) = ((v - v0) / cell).toInt().coerceIn(0, n - 1)

    /** Calls [f] with every parked car near the rectangle. */
    fun forIn(x0: Float, y0: Float, x1: Float, y1: Float, f: (Int) -> Unit) {
        for (r in cellOf(y0, gy0, rows)..cellOf(y1, gy0, rows)) for (cc in cellOf(x0, gx0, cols)..cellOf(x1, gx0, cols)) for (i in grid[r * cols + cc]) f(i)
    }

    private val shown = ArrayList<Int>()

    /**
     * Leaves out the cars where your vehicles (x, y pairs) are parked; the first [parked] of the [n] are standing still,
     * the rest (the one you drive) keep a spot they're leaving empty until they're clear of it. Returns true when that
     * changed.
     */
    fun hide(xs: FloatArray, n: Int, parked: Int = n): Boolean {
        var changed = false
        val now = ArrayList<Int>()
        for (k in 0 until n) {
            val vx = xs[k * 2]; val vy = xs[k * 2 + 1]
            val reach = if (k < parked) 5.5f else 7.5f
            forIn(vx - 8f, vy - 8f, vx + 8f, vy + 8f) { i ->
                if (abs(x[i] - vx) < reach && abs(y[i] - vy) < reach && (k < parked || hid[i]) && i !in now) now += i
            }
        }
        for (i in shown) if (i !in now) { hid[i] = false; changed = true }
        for (i in now) if (!hid[i]) { hid[i] = true; changed = true }
        shown.clear(); shown += now
        return changed
    }

    /** A hash of what's hidden, for caches of the drawn city. */
    fun hiddenKey(): Int { var k = 7; for (i in shown) k = k * 31 + i; return k }

    /**
     * Steers a vehicle's probe circle (radius [r] at (px, py)) out from the parked cars toward the middle of the road:
     * pulling away from the curb nudges your car into the lane instead of running it into the car in front. The new
     * point goes to [out]; returns true when it moved.
     */
    fun nudge(px: Float, py: Float, r: Float, out: FloatArray): Boolean {
        var qx = px; var qy = py; var moved = false
        forIn(px - 5f, py - 5f, px + 5f, py + 5f) { i ->
            if (hid[i]) return@forIn
            val fx = sin(heading[i]); val fy = -cos(heading[i])
            val hl = length(style[i]) * .5f; val hw = width(style[i]) * .5f
            val dx = qx - x[i]; val dy = qy - y[i]
            val along = dx * fx + dy * fy
            if (abs(along) > hl + r * .6f) return@forIn
            val lat = dx * outX[i] + dy * outY[i]
            if (lat >= hw + r || lat < -hw - r) return@forIn
            val push = hw + r - lat
            qx += outX[i] * push; qy += outY[i] * push
            moved = true
        }
        out[0] = qx; out[1] = qy
        return moved
    }

    /**
     * Pushes a circle of radius [r] at (px, py) out of the parked cars (each three circles along its length); the
     * pushed-out point goes to [out]. Returns true when it touched one.
     */
    fun bump(px: Float, py: Float, r: Float, out: FloatArray): Boolean {
        var qx = px; var qy = py; var hit = false
        forIn(px - 5f, py - 5f, px + 5f, py + 5f) { i ->
            if (hid[i]) return@forIn
            val fx = sin(heading[i]); val fy = -cos(heading[i])
            val half = length(style[i]) * .5f - width(style[i]) * .45f
            val cr = width(style[i]) * .52f
            for (k in -1..1) {
                val cx = x[i] + fx * half * k; val cy = y[i] + fy * half * k
                val dx = qx - cx; val dy = qy - cy
                val minD = r + cr
                val d2 = dx * dx + dy * dy
                if (d2 < minD * minD && d2 > 1e-6f) {
                    val d = sqrt(d2)
                    qx = cx + dx / d * minD; qy = cy + dy / d * minD
                    hit = true
                }
            }
        }
        out[0] = qx; out[1] = qy
        return hit
    }
}

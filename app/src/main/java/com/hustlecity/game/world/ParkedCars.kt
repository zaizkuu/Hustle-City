package com.hustlecity.game.world

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The cars parked round the city, and the bays they park in: marked bays along the curbs of ordinary streets (where a
 * parked car leaves the lane clear; on avenues the curb lane is a traffic lane), two in five taken, and the stalls of
 * the parking lots. Which are taken is fixed for a city, apart from the bays [vacate] frees for the player's own cars.
 * They're solid: [bump] keeps the player (on foot or driving) out of them.
 */
class ParkedCars(private val city: City) {
    /** A parked car at (x, y) facing [heading], [length] by [width]; [seed] picks its colour and body. */
    class Car(val x: Float, val y: Float, val heading: Float, val length: Float, val width: Float, val seed: Int)

    /** A bay along a curb: on [edge], [side] of it (as [City.sideOf]), [s] meters from its start; [car] in it, or none. */
    class Bay(val edge: Int, val side: Int, val s: Float, val x: Float, val y: Float, val heading: Float) {
        var car: Car? = null
    }

    val bays = ArrayList<Bay>()
    /** The bays along each street (by edge id), for painting their lines. */
    val bysEdge: Map<Int, List<Bay>> by lazy { bays.groupBy { it.edge } }
    /** The cars in the parking lots (drawn with the lot). */
    val lotCars = ArrayList<Car>()
    /** Bumped when a bay is freed, so cached drawings of the street know to redraw. */
    var version = 0
        private set

    private val cell = 32f
    private val grid = HashMap<Long, MutableList<Car>>()
    private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)

    init {
        val g = city.roads
        // driveways: the stretch in front of a parking lot or gas station stays clear
        val driveways = city.buildings.filter { it.frontEdge >= 0 && (it.kind == BKind.PARKING || it.kind == BKind.GAS) }.groupBy { it.frontEdge }
        for (e in g.edges) {
            if (e.cls != RoadClass.STREET || e.overWater || e.under) continue
            val drives = driveways[e.id].orEmpty().map { (it.doorX - e.ax) * e.ux + (it.doorY - e.ay) * e.uy }
            for (side in -1..1 step 2) {
                val off = e.cls.halfWidth - 1.05f
                // no parking within 9 m of a junction (turning buses swing wide there)
                var s = e.clearA + 9f
                var k = 0
                while (s < e.len - e.clearB - 9f) {
                    val x = e.ax + e.ux * s + e.uy * off * side
                    val y = e.ay + e.uy * s - e.ux * off * side
                    if (drives.none { abs(it - s) < 7f } && !city.shore.wet(x, y) && clearOfOtherRoads(e, x, y)) {
                        val bay = Bay(e.id, side, s, x, y, city.curbHeading(e, side))
                        val h = hash(e.id * 2 + (side + 1) / 2, k)
                        if (h < .4f) bay.car = Car(x, y, bay.heading, 4.2f + hash(k, e.id) * .5f, 1.82f, (h * 1e6f).toInt())
                        bays += bay
                    }
                    s += 6f; k++
                }
            }
        }
        // the lots: every other stall or so, as the lot is drawn
        for (b in city.buildings) {
            if (b.kind != BKind.PARKING) continue
            if (city.mapId == Maps.MANILA && maxOf(b.w, b.d) > 14f && minOf(b.w, b.d) > 9f) continue // a jeepney terminal
            val slots = ((b.w - 4f) / 2.8f).toInt()
            for (sIdx in 0 until minOf(slots, 12)) if ((b.id * 31 + sIdx * 17) % 10 < 6) {
                val lx = 4.4f + sIdx * 2.8f; val ly = if (sIdx % 2 == 0) 3.5f else b.d - 3.5f
                lotCars += Car(b.wx(lx, ly), b.wy(lx, ly), b.angle, 4.3f, 1.85f, b.id + sIdx)
            }
        }
        index()
    }

    /** True when a car parked at (x, y) beside [e] stays off every other road's asphalt (two plans can run close). */
    private fun clearOfOtherRoads(e: RoadEdge, x: Float, y: Float): Boolean {
        var clear = true
        city.roads.near(x, y, 20f) { id ->
            if (id == e.id || !clear) return@near
            val o = city.roads.edges[id]
            if (o.a == e.a || o.a == e.b || o.b == e.a || o.b == e.b) {
                // a road from the same junction: only its own strip counts, not where they meet
                if (Geo.distToSeg(x, y, o.ax + o.ux * o.clearA, o.ay + o.uy * o.clearA, o.bx - o.ux * o.clearB, o.by - o.uy * o.clearB) < o.cls.halfWidth + 3.2f) clear = false
            } else if (Geo.distToSeg(x, y, o.ax, o.ay, o.bx, o.by) < o.cls.halfWidth + 3.2f) clear = false
        }
        return clear
    }

    private fun index() {
        grid.clear()
        fun put(c: Car) { grid.getOrPut(key(floor(c.x / cell).toInt(), floor(c.y / cell).toInt())) { mutableListOf() } += c }
        for (b in bays) b.car?.let(::put)
        for (c in lotCars) put(c)
    }

    /** Every parked car within [r] of (x, y). */
    inline fun near(x: Float, y: Float, r: Float, f: (Car) -> Unit) = forCells(x - r, y - r, x + r, y + r, f)

    @PublishedApi internal inline fun forCells(x0: Float, y0: Float, x1: Float, y1: Float, f: (Car) -> Unit) {
        for (cy in floor(y0 / 32f).toInt()..floor(y1 / 32f).toInt()) for (cx in floor(x0 / 32f).toInt()..floor(x1 / 32f).toInt()) {
            cellCars((cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL))?.forEach(f)
        }
    }

    @PublishedApi internal fun cellCars(k: Long): List<Car>? = grid[k]

    /** Frees the bays within [r] of (x, y): one of the player's cars is parked there, with room to pull out ahead and behind. */
    fun vacate(x: Float, y: Float, r: Float = 8.5f) {
        var any = false
        for (b in bays) if (b.car != null && abs(b.x - x) < r + 3f && abs(b.y - y) < r + 3f && dist(b.x, b.y, x, y) < r) { b.car = null; any = true }
        if (any) { index(); version++ }
    }

    /** The free bay nearest (x, y), on [edge] and [side] if given, within [range]; or null. */
    fun freeBay(x: Float, y: Float, range: Float, edge: Int = -1, side: Int = 0, taken: (Bay) -> Boolean = { false }): Bay? =
        bays.asSequence().filter { it.car == null && (edge < 0 || it.edge == edge) && (side == 0 || it.side == side) && !taken(it) }
            .map { it to dist(it.x, it.y, x, y) }.filter { it.second < range }.minByOrNull { it.second }?.first

    /** Pushes a circle at (x, y) of radius [r] out of every parked car's body; true if it touched one. */
    fun bump(x: Float, y: Float, r: Float, out: FloatArray): Boolean {
        var px = x; var py = y; var hit = false
        near(x, y, r + 4f) { c ->
            if (pushOut(px, py, r, c.x, c.y, c.heading, c.length * .5f, c.width * .5f, out)) { px = out[0]; py = out[1]; hit = true }
        }
        out[0] = px; out[1] = py
        return hit
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    companion object {
        /**
         * Pushes a circle at (px, py) of radius [r] out of a box centred on (cx, cy), facing [heading] (0 = north,
         * clockwise), [hl] half as long and [hw] half as wide; true (with the new centre in [out]) if they overlapped.
         */
        fun pushOut(px: Float, py: Float, r: Float, cx: Float, cy: Float, heading: Float, hl: Float, hw: Float, out: FloatArray): Boolean {
            val dx = px - cx; val dy = py - cy
            if (abs(dx) > hl + r + .5f || abs(dy) > hl + r + .5f) return false
            val fx = sin(heading); val fy = -cos(heading)
            // in the box's own frame: along it (f) and across it (c, to its right)
            val f = dx * fx + dy * fy; val c = -dx * fy + dy * fx
            var nf = f - f.coerceIn(-hl, hl); var nc = c - c.coerceIn(-hw, hw)
            val d2 = nf * nf + nc * nc
            if (d2 >= r * r) return false
            if (d2 > 1e-6f) {
                val d = sqrt(d2); val k = (r - d) / d
                nf *= k; nc *= k
            } else {
                // the centre is inside the box: out through the nearest side
                val ef = hl - abs(f); val ec = hw - abs(c)
                if (ef < ec) { nf = (ef + r) * (if (f >= 0f) 1f else -1f); nc = 0f } else { nc = (ec + r) * (if (c >= 0f) 1f else -1f); nf = 0f }
            }
            out[0] = px + nf * fx - nc * fy; out[1] = py + nf * fy + nc * fx
            return true
        }

        /** Heading (0 = north, clockwise) along (dx, dy). */
        fun headingOf(dx: Float, dy: Float) = atan2(dx, -dy)
    }
}

package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Park
import com.hustlecity.game.world.RoadClass
import com.hustlecity.game.world.RoadEdge
import com.hustlecity.game.world.dist
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Street furniture placed procedurally along every block: lamp posts, fire hydrants, trash cans, benches, bus
 * shelters, newspaper boxes, mailboxes, bike racks and planters, plus fountains, lamps and benches in the parks.
 * Placement is deterministic per city; props face the road (or the path, in parks).
 */
class StreetProps(val city: City) {
    companion object {
        const val LAMP = 0; const val HYDRANT = 1; const val BIN = 2; const val BENCH = 3; const val BUS_STOP = 4
        const val NEWS = 5; const val MAILBOX = 6; const val FOUNTAIN = 7; const val PARK_LAMP = 8; const val BIKE_RACK = 9
        const val PLANTER = 10
        /** Manila: a concrete utility pole with a street light, its wires slung to the next pole and into the houses. */
        const val UTIL_POLE = 11
        /** Manila: a street food cart under a big umbrella (the vendor is drawn by the renderer). */
        const val VENDOR = 12
        /** Tokyo and Seoul: drink vending machines against a wall, lit at night. */
        const val VENDING = 13
        /** Tokyo's yokocho alleys: a string of red paper lanterns across the lane. */
        const val LANTERNS = 14
        /** Manila's barangay streets: banderitas, strings of little fiesta flags, across the street. */
        const val BUNTING = 15
        /** Manila's residential lanes: a home-made basketball hoop on a post, hanging over the street. */
        const val HOOP = 16
        /** Short steel posts guarding the corner of a big road. */
        const val BOLLARD = 17
        /** A parking meter at the kerb of a busy shopping street. */
        const val METER = 18
        /** Sidewalk works: a striped barrier with a warning lamp, a traffic cone at each end. */
        const val WORKS = 19
        /** A street-name sign on its pole at the end of a block. */
        const val SIGN = 20
        /** A dumpster against the wall of a lane. */
        const val DUMPSTER = 21
        /** A billboard on its legs at the back of a parking lot, facing the street. */
        const val BILLBOARD = 22
        /** A cash machine against a shop wall. */
        const val ATM = 23
        /** A phone booth on the pavement. */
        const val PHONE = 24
        /** A bush beside a park path. */
        const val BUSH = 25
        /** A café table under its umbrella, a chair either side, where the sidewalk is wide. */
        const val CAFE = 26
        /** Tokyo's drinking lanes: an izakaya's red paper lantern on its post by the door. */
        const val AKACHOCHIN = 27
        /** How many kinds there are. */
        const val KINDS = 28
        /** Heights of the two strings of banderitas and of the lantern string where they're tied. */
        const val BUNTING_LOW = 5.2f; const val BUNTING_HIGH = 5.75f; const val LANTERN_Z = 4.3f
        /** A pole's service drops: how far along the street each runs, and the heights it leaves the pole and meets the wall. */
        val DROP_X = floatArrayOf(-2.2f, 1.8f, 3.6f)
        val DROP_TOP = floatArrayOf(6.1f, 5.8f, 6.3f)
        val DROP_Z = floatArrayOf(4.6f, 4.3f, 5.4f)
        /** Heights where a bundle over the street meets the houses opposite (three lines, one above the other). */
        const val BUNDLE_Z = 4.75f; const val BUNDLE_STEP = .18f
        /** How much wall a tie needs above it: a house must stand this much taller than the line it holds. */
        const val TIE_CLEAR = .4f
        private const val SPOT = .8f
        /** Manila's neighborhoods of narrow streets and fiestas: Tondo, Quiapo, Sampaloc and Paco. */
        private val BARANGAYS = setOf(0, 2, 3, 7)
        private val FLAGS = intArrayOf(c(0xFFE53935), c(0xFFFDD835), c(0xFF1E88E5), c(0xFFF5F5F5), c(0xFF43A047), c(0xFFEC407A), c(0xFFFB8C00))

        private val POLE = c(0xFF2F3A35)
        private val IRON = c(0xFF2A2D31)
        private val WOOD = c(0xFF9C6B43)
        private val CONCRETE = c(0xFFB9B4AA)
        private val NEWS_COLORS = intArrayOf(c(0xFFD63031), c(0xFF0984E3), c(0xFFFDCB6E), c(0xFF00B894))
        private val VENDING_BODIES = intArrayOf(c(0xFFF2F2F0), c(0xFFD32F2F), c(0xFF1E5BC6), c(0xFFF2F2F0), c(0xFF2E9E4F), c(0xFFF2F2F0))
        private val DRINKS = intArrayOf(c(0xFFE53935), c(0xFF1E88E5), c(0xFFFDD835), c(0xFF43A047), c(0xFF6D4C41), c(0xFFF5F5F5), c(0xFFFB8C00))
    }

    var count = 0; private set
    var x = FloatArray(4096); private set
    var y = FloatArray(4096); private set
    var kind = IntArray(4096); private set
    var face = IntArray(4096); private set
    /** Direction toward the road (unit x, y); props are drawn turned to face it. */
    var dirX = FloatArray(4096); private set
    var dirY = FloatArray(4096); private set
    /**
     * Poles: the next pole the wires run to (-1 for none). Lantern and bunting strings: which ends are tied to a pole of
     * their own (bit 0 the end behind the prop's back, bit 1 the end it faces) rather than to a house front.
     */
    var link = IntArray(4096); private set
    /**
     * Lantern and bunting strings: their length; the prop stands at the middle facing along them. Poles: 1 when wires
     * drop into the houses behind.
     */
    var aux = FloatArray(4096); private set
    /** Poles: how far across the street a bundle of wires runs to the front of a house on the other side (0 for none). */
    var cross = FloatArray(4096); private set
    /** Poles: how far back each of the three service drops runs to the front of a house (0 where there's none to take it). */
    var drops = FloatArray(4096 * 3); private set
    /** Trees hidden because a park path or fountain is there. */
    val hiddenTree = BooleanArray(city.trees.size)
    /**
     * Index into the tree list -> 0 street tree, 1 park tree, 2 park conifer, 3 flowering street tree, 4 palm,
     * 5 ginkgo (golden in autumn).
     */
    val treeKind = IntArray(city.trees.size)
    /** Trees planted in the pavement (not in a park), which stand in a pit with a grate. */
    val streetTree = BooleanArray(city.trees.size)

    private val cell = 40f
    private val gx0 = city.bounds[0] - 40f
    private val gy0 = city.bounds[1] - 40f
    private val gCols = ((city.bounds[2] + 40f - gx0) / cell).toInt() + 1
    private val gRows = ((city.bounds[3] + 40f - gy0) / cell).toInt() + 1
    private val grid: Array<IntArray>
    private val treeGrid: Array<IntArray>

    private var seed = 1234567

    private fun rand(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65536f
    }

    private fun add(px: Float, py: Float, k: Int, dx: Float, dy: Float): Int {
        if (count == x.size) {
            x = x.copyOf(count * 2); y = y.copyOf(count * 2); kind = kind.copyOf(count * 2); face = face.copyOf(count * 2)
            dirX = dirX.copyOf(count * 2); dirY = dirY.copyOf(count * 2); link = link.copyOf(count * 2); aux = aux.copyOf(count * 2)
            cross = cross.copyOf(count * 2); drops = drops.copyOf(count * 6)
        }
        x[count] = px; y[count] = py; kind[count] = k; face[count] = 1; dirX[count] = dx; dirY[count] = dy
        if (k != BUNTING && k != LANTERNS) taken[spot(px, py)] = count
        link[count] = -1; aux[count] = 0f; cross[count] = 0f
        drops[count * 3] = 0f; drops[count * 3 + 1] = 0f; drops[count * 3 + 2] = 0f
        return count++
    }

    // which prop stands in each square of pavement, so two never go in the same place
    private val taken = HashMap<Long, Int>()
    private fun spot(px: Float, py: Float): Long =
        (kotlin.math.floor(px / SPOT).toLong() shl 32) xor (kotlin.math.floor(py / SPOT).toLong() and 0xFFFFFFFFL)

    /** True when something already stands within [r] meters of (px, py). */
    private fun crowded(px: Float, py: Float, r: Float): Boolean {
        val n = kotlin.math.ceil(r / SPOT).toInt().coerceAtLeast(1)
        for (dx in -n..n) for (dy in -n..n) {
            val i = taken[spot(px + dx * SPOT, py + dy * SPOT)] ?: continue
            val ex = x[i] - px; val ey = y[i] - py
            if (ex * ex + ey * ey < r * r) return true
        }
        return false
    }

    /**
     * True when a prop of kind [k] at (px, py) would run into one already standing: their footprints (half lengths,
     * taken as circles) come closer than a little elbow room. Bollards go in rows, so they only mind other kinds.
     */
    private fun clashes(px: Float, py: Float, k: Int): Boolean {
        val mine = footprint(k)
        val n = kotlin.math.ceil((mine + 2.4f) / SPOT).toInt()
        for (dx in -n..n) for (dy in -n..n) {
            val i = taken[spot(px + dx * SPOT, py + dy * SPOT)] ?: continue
            if (k == BOLLARD && kind[i] == BOLLARD) { if (dist(x[i], y[i], px, py) < .9f) return true; continue }
            val r = mine + footprint(kind[i]) * .85f + .25f
            val ex = x[i] - px; val ey = y[i] - py
            if (ex * ex + ey * ey < r * r) return true
        }
        return false
    }

    /** Half the length (along the street) and depth (toward the building) of a prop's footprint. */
    private fun footprint(k: Int): Float = when (k) {
        VENDING -> 1.7f; VENDOR -> 1.5f; BUS_STOP -> 2.3f; BIKE_RACK -> 1.6f; BENCH, NEWS -> 1f; PLANTER -> .65f
        WORKS -> 1.55f; DUMPSTER -> 1.05f; CAFE -> 1.15f; PHONE -> .55f; ATM -> .45f; BILLBOARD -> 3.7f; BUSH -> .75f
        else -> .35f
    }

    /** How far a prop reaches back toward the buildings, and out toward the road, from where it stands. */
    private fun backOf(k: Int): Float = when (k) { BUS_STOP, VENDING -> .7f; DUMPSTER, ATM, PHONE, CAFE, AKACHOCHIN -> .2f; else -> .3f }
    private fun frontOf(k: Int): Float = when (k) { DUMPSTER -> .72f; CAFE -> 1.1f; PHONE -> .55f; WORKS -> .35f; else -> .3f }

    /**
     * Whether a prop of kind [k] at (px, py) keeps clear of every road's asphalt (crossings and junctions too: another
     * street can pass close at a bend or a sharp corner) and of buildings. (ux, uy) runs along its street, (nx, ny)
     * away from it.
     */
    private fun clearSpot(px: Float, py: Float, ux: Float, uy: Float, nx: Float, ny: Float, k: Int): Boolean {
        val g = city.roads
        val half = footprint(k)
        // lamps and poles keep their rhythm along the street; whatever comes after finds another place or goes without
        if (k != LAMP && k != UTIL_POLE && k != BUNTING && k != LANTERNS && (if (k >= BOLLARD) clashes(px, py, k) else crowded(px, py, .75f))) return false
        for (sx in floatArrayOf(-half, 0f, half)) for (sy in floatArrayOf(0f, backOf(k))) {
            val qx = px + ux * sx + nx * sy; val qy = py + uy * sx + ny * sy
            if (g.nearAsphalt(qx, qy, .25f)) return false
            if (k != LAMP && k != UTIL_POLE && k != HYDRANT && building(qx, qy)) return false
        }
        // and off the curb's own side of the street, toward the road
        val front = frontOf(k)
        val fx = px - nx * front; val fy = py - ny * front
        if (k >= BOLLARD && (building(fx, fy) || nearTree(px, py, half + .5f))) return false
        return !(k != LAMP && k != UTIL_POLE && k != HYDRANT && k != BIN && k != MAILBOX && g.nearAsphalt(fx, fy, 0f))
    }

    /** Whether a solid building stands at a point. */
    private fun building(px: Float, py: Float): Boolean = solidAt(px, py) != null

    private fun solidAt(px: Float, py: Float): Building? {
        var hit: Building? = null
        city.forBuildingsIn(px - 1f, py - 1f, px + 1f, py + 1f) { b -> if (hit == null && b.solid && b.contains(px, py)) hit = b }
        return hit
    }

    /**
     * How far from (px, py) toward (dx, dy) the front of the first building stands, looking from [from] to [to] meters
     * out, when that building is at least [z] tall: a line tied there at [z] - [TIE_CLEAR] meets its wall. -1 when there's
     * no building in reach, or the first one is too low and the line would hang in the air over its roof.
     */
    private fun wallAhead(px: Float, py: Float, dx: Float, dy: Float, from: Float, to: Float, z: Float): Float {
        var d = from
        while (d <= to) {
            val b = solidAt(px + dx * d, py + dy * d)
            if (b != null) {
                if (b.height < z) return -1f
                // close in on the face
                var lo = max(0f, d - .1f); var hi = d
                repeat(5) { val m = (lo + hi) * .5f; if (solidAt(px + dx * m, py + dy * m) != null) hi = m else lo = m }
                return lo
            }
            d += .1f
        }
        return -1f
    }

    /** Whether a pole for a string can stand at (px, py): on the pavement, clear of buildings, trees and the other props. */
    private fun poleSpot(px: Float, py: Float): Boolean {
        if (city.shore.anyWater(px, py) || city.parks.any { Geo.inside(px, py, it.poly) } || city.reserved(px, py)) return false
        if (city.roads.nearAsphalt(px, py, .2f)) return false
        for (ox in floatArrayOf(-.25f, .25f)) for (oy in floatArrayOf(-.25f, .25f)) if (building(px + ox, py + oy)) return false
        if (nearTree(px, py, 1.1f)) return false
        for (j in 0 until count) if (kind[j] != BUNTING && kind[j] != LANTERNS && abs(x[j] - px) < 1.1f && abs(y[j] - py) < 1.1f) return false
        return true
    }

    /**
     * A string of [k] (banderitas or lanterns) across street [e] at [s], its top line at [top]: each end tied to the front
     * of the house there, or, where that house is too low or stands too far back, to a pole of its own at the back of the
     * sidewalk. Where an end can have neither, there's no string. The prop stands at the string's middle facing along it
     * toward the side away from (nx, ny); [aux] holds its length and [link] which ends are on poles.
     */
    private fun stringAcross(e: RoadEdge, s: Float, nx: Float, ny: Float, k: Int, top: Float) {
        val cx = e.ax + e.ux * s; val cy = e.ay + e.uy * s
        val back = e.cls.halfWidth + e.cls.sidewalk - .4f
        val reach = e.cls.halfWidth + e.cls.sidewalk + 3f
        var a = wallAhead(cx, cy, nx, ny, e.cls.halfWidth + .3f, reach, top + TIE_CLEAR)
        var b = wallAhead(cx, cy, -nx, -ny, e.cls.halfWidth + .3f, reach, top + TIE_CLEAR)
        var poles = 0
        if (a < 0f) { if (!poleSpot(cx + nx * back, cy + ny * back)) return; a = back; poles = 1 }
        if (b < 0f) { if (!poleSpot(cx - nx * back, cy - ny * back)) return; b = back; poles = poles or 2 }
        // the whole string clear of a landmark's grounds and the water
        for (t in floatArrayOf(a, 0f, -b)) {
            val px = cx + nx * t; val py = cy + ny * t
            if (city.reserved(px, py) || city.shore.anyWater(px, py)) return
        }
        val i = add(cx + nx * (a - b) * .5f, cy + ny * (a - b) * .5f, k, -nx, -ny)
        aux[i] = a + b
        link[i] = poles
    }

    /**
     * A pole's service drops into the houses behind it and the odd bundle over the street to the houses opposite, each
     * kept only where it meets the front of a house tall enough to take it. [nx], [ny] point from the road to the pole's
     * sidewalk; [bundle] says this pole sends a bundle across.
     */
    private fun poleWires(i: Int, e: RoadEdge, nx: Float, ny: Float, bundle: Boolean) {
        val behind = e.cls.sidewalk + 4.5f
        for (k in 0..2) {
            if (k == 2 && i % 2 != 0) continue
            val px = x[i] + e.ux * DROP_X[k]; val py = y[i] + e.uy * DROP_X[k]
            val f = wallAhead(px, py, nx, ny, .4f, behind, DROP_Z[k] + TIE_CLEAR)
            if (f > 0f) { drops[i * 3 + k] = f; aux[i] = 1f }
        }
        if (bundle) {
            // from the pole at the curb, over the road and the far sidewalk to the houses there
            val start = e.cls.halfWidth * 2f + .9f
            val f = wallAhead(x[i], y[i], -nx, -ny, start, start + e.cls.sidewalk + 4f, BUNDLE_Z + BUNDLE_STEP * 2 + TIE_CLEAR)
            if (f > 0f) cross[i] = f
        }
    }

    private fun cellOf(v: Float, o: Float, n: Int) = ((v - o) / cell).toInt().coerceIn(0, n - 1)

    private fun nearTree(px: Float, py: Float, r: Float): Boolean {
        val c0 = cellOf(px - r, gx0, gCols); val c1 = cellOf(px + r, gx0, gCols)
        val r0 = cellOf(py - r, gy0, gRows); val r1 = cellOf(py + r, gy0, gRows)
        for (row in r0..r1) for (col in c0..c1) for (i in treeGrid[row * gCols + col]) {
            val t = city.trees[i]
            if (abs(t.x - px) < r && abs(t.y - py) < r) return true
        }
        return false
    }

    /** Calls [f] with every prop index whose position lies in the rectangle. */
    inline fun forIn(x0: Float, y0: Float, x1: Float, y1: Float, f: (Int) -> Unit) {
        val c0 = cellIndex(x0, true); val c1 = cellIndex(x1, true)
        val r0 = cellIndex(y0, false); val r1 = cellIndex(y1, false)
        for (row in r0..r1) for (col in c0..c1) for (i in cellProps(row, col)) f(i)
    }

    fun cellIndex(v: Float, isX: Boolean) = if (isX) cellOf(v, gx0, gCols) else cellOf(v, gy0, gRows)
    fun cellProps(row: Int, col: Int): IntArray = grid[row * gCols + col]

    init {
        // trees by cell, for spacing props around them
        val tLists = Array(gCols * gRows) { ArrayList<Int>() }
        for ((i, t) in city.trees.withIndex()) tLists[cellOf(t.y, gy0, gRows) * gCols + cellOf(t.x, gx0, gCols)] += i
        treeGrid = Array(tLists.size) { tLists[it].toIntArray() }
        val g = city.roads
        val manila = city.mapId == com.hustlecity.game.world.Maps.MANILA
        val tokyo = city.mapId == com.hustlecity.game.world.Maps.TOKYO
        val seoul = city.mapId == com.hustlecity.game.world.Maps.SEOUL
        for (e in g.edges) {
            if (e.overWater || e.under || e.len < 14f) continue
            val major = e.cls.rank >= 2
            for (side in -1..1 step 2) {
                // n points from the road to this sidewalk; props face the road (-n)
                val nx = e.uy * side; val ny = -e.ux * side
                val curb = e.cls.halfWidth + .6f
                val s0 = e.clearA + 4f; val s1 = e.len - e.clearB - 4f
                if (s1 - s0 < 6f) continue
                fun at(s: Float, off: Float, k: Int): Int {
                    val px = e.ax + e.ux * s + nx * off; val py = e.ay + e.uy * s + ny * off
                    if (city.shore.anyWater(px, py) || city.parks.any { Geo.inside(px, py, it.poly) } || city.reserved(px, py)) return -1
                    if (!clearSpot(px, py, e.ux, e.uy, nx, ny, k)) return -1
                    return add(px, py, k, -nx, -ny)
                }
                val hood = city.neighborhoodAt(e.ax + e.ux * (s0 + s1) * .5f + nx * 12f, e.ay + e.uy * (s0 + s1) * .5f + ny * 12f)
                val busy = hood.traffic > .3f
                val wall = e.cls.halfWidth + e.cls.sidewalk - .45f
                if (manila && side > 0) {
                    // Manila: concrete poles every ~28 m carry the street lights and a tangle of wires from pole to pole
                    var s = s0 + 4f; var prev = -1
                    while (s < s1 - 2f) {
                        val px = e.ax + e.ux * s + nx * curb; val py = e.ay + e.uy * s + ny * curb
                        val i = at(if (nearTree(px, py, 1.6f)) s + 2.2f else s, curb, UTIL_POLE)
                        if (i >= 0) {
                            if (prev >= 0 && dist(x[prev], y[prev], x[i], y[i]) < 40f) link[prev] = i
                            // service drops run back into the houses, and now and then a bundle across the street
                            poleWires(i, e, nx, ny, rand() < .45f)
                            prev = i
                        }
                        s += if (e.cls == RoadClass.LANE) 24f else 28f
                    }
                } else if (e.cls != RoadClass.LANE || side > 0) {
                    // lamp posts every ~25 m (one side only on narrow lanes)
                    var s = s0 + 5f
                    while (s < s1 - 3f) {
                        val px = e.ax + e.ux * s + nx * curb; val py = e.ay + e.uy * s + ny * curb
                        at(if (nearTree(px, py, 1.6f)) s + 2.2f else s, curb, LAMP)
                        s += if (e.cls == RoadClass.LANE) 30f else 25f
                    }
                }
                // food carts on Manila's busy pavements
                if (manila && (busy || e.cls.rank >= 1) && rand() < (if (busy) .7f else .3f)) {
                    val s = s0 + 8f + rand() * (s1 - s0 - 16f).coerceAtLeast(1f)
                    val px = e.ax + e.ux * s + nx * (curb + 1.1f); val py = e.ay + e.uy * s + ny * (curb + 1.1f)
                    if (!nearTree(px, py, 3.6f)) at(s, curb + 1.1f, VENDOR)
                }
                // Manila's barangays: a basketball hoop by the lane, banderitas strung across the street from house to house
                val barangay = manila && e.cls.rank <= 1 && hood.id in BARANGAYS
                if (barangay && e.cls == RoadClass.LANE && rand() < .3f) {
                    val s = s0 + 6f + rand() * (s1 - s0 - 12f).coerceAtLeast(1f)
                    val px = e.ax + e.ux * s + nx * wall; val py = e.ay + e.uy * s + ny * wall
                    if (!nearTree(px, py, 2f)) at(s, wall, HOOP)
                }
                if (barangay && side > 0 && e.id % 4 == 0) {
                    var s = s0 + 4f
                    while (s < s1 - 3f) { stringAcross(e, s, nx, ny, BUNTING, BUNTING_HIGH); s += 7.5f }
                }
                // vending machines against the walls of Tokyo's (and Seoul's) side streets
                if ((tokyo || seoul) && e.cls.rank <= 1 && rand() < (if (tokyo) .75f else .35f)) {
                    val s = s0 + 6f + rand() * (s1 - s0 - 12f).coerceAtLeast(1f)
                    val px = e.ax + e.ux * s + nx * wall; val py = e.ay + e.uy * s + ny * wall
                    if (!nearTree(px, py, 1.8f)) at(s, wall, VENDING)
                }
                // Tokyo's drinking alleys: red lanterns strung across the lane every few meters
                if (tokyo && e.cls == RoadClass.LANE && side > 0 && hood.look == com.hustlecity.game.world.Look.DENSE_ASIA && (e.id % 3 != 0)) {
                    var s = s0 + 3f
                    while (s < s1 - 2f) { stringAcross(e, s, nx, ny, LANTERNS, LANTERN_Z); s += 6.5f }
                }
                if (e.cls == RoadClass.LANE) {
                    if (rand() < .25f) at(s0 + rand() * (s1 - s0), e.cls.halfWidth + e.cls.sidewalk * .5f, if (busy && rand() < .5f) BIN else PLANTER)
                    continue
                }
                // corner furniture at both ends
                for (end in 0..1) {
                    val s = if (end == 0) s0 + 1f else s1 - 1f
                    val r = rand()
                    when {
                        r < .45f -> at(s, curb, BIN)
                        r < .6f -> at(s, curb, MAILBOX)
                    }
                    if (major && rand() < .35f) at(if (end == 0) s0 + 4.5f else s1 - 4.5f, curb + .3f, NEWS)
                }
                // a hydrant on each side of a block
                at(if (rand() < .5f) s0 + 6.5f else s1 - 6.5f, curb, HYDRANT)
                // a bus shelter mid-block on the big roads
                if (major && s1 - s0 > 50f && rand() < .45f) at((s0 + s1) * .5f, curb + .2f, BUS_STOP)
                // benches, bike racks and planters where it's busy
                val n = ((s1 - s0) / 60f).toInt().coerceAtLeast(1)
                repeat(n) {
                    val r = rand()
                    val s = s0 + 12f + rand() * (s1 - s0 - 24f).coerceAtLeast(1f)
                    val px = e.ax + e.ux * s + nx * curb; val py = e.ay + e.uy * s + ny * curb
                    if (nearTree(px, py, 2.2f)) return@repeat
                    when {
                        r < (if (busy) .35f else .15f) -> at(s, curb, BENCH)
                        r < (if (busy) .55f else .25f) -> at(s, curb, BIKE_RACK)
                        r < (if (busy) .75f else .3f) -> at(s, curb, PLANTER)
                    }
                }
            }
        }
        for (park in city.parks) if (park.kind == Park.GREEN) park(park)
        extras()
        // tree kinds: what the map planted, or park trees vs street trees with some conifers and flowering trees
        for ((i, t) in city.trees.withIndex()) {
            val inPark = city.parks.any { it.kind != Park.PLAZA && Geo.inside(t.x, t.y, it.poly) }
            streetTree[i] = !inPark && !city.reserved(t.x, t.y)
            val h = ((i * 2654435761L) ushr 7).toInt() and 0xFF
            treeKind[i] = when (t.species) {
                1 -> 4
                2 -> 2
                3 -> 3
                4 -> 5
                0 -> if (inPark) 1 else 0
                else -> when {
                    inPark && h % 4 == 0 -> 2
                    inPark -> 1
                    h % 5 == 0 -> 3
                    else -> 0
                }
            }
        }
        val lists = Array(gCols * gRows) { ArrayList<Int>() }
        for (i in 0 until count) lists[cellOf(y[i], gy0, gRows) * gCols + cellOf(x[i], gx0, gCols)] += i
        grid = Array(lists.size) { lists[it].toIntArray() }
    }

    /** A fountain where the park's paths cross, lamps and benches along them, and bins at the edges. */
    private fun park(p: Park) {
        if (Geo.area(p.poly) < 2500f) return
        val (cx, cy) = p.centre
        if (Geo.inside(cx, cy, p.poly) && !city.shore.anyWater(cx, cy) && !city.reserved(cx, cy)) {
            add(cx, cy, FOUNTAIN, 0f, 1f)
            for ((i, t) in city.trees.withIndex()) if (abs(t.x - cx) < 8f && abs(t.y - cy) < 8f) hiddenTree[i] = true
        }
        for (path in p.paths) {
            val ax = path[0]; val ay = path[1]; val bx = path[2]; val by = path[3]
            val len = dist(ax, ay, bx, by)
            if (len < 10f) continue
            val ux = (bx - ax) / len; val uy = (by - ay) / len
            // trees stay off the path
            for ((i, t) in city.trees.withIndex()) if (Geo.distToSeg(t.x, t.y, ax, ay, bx, by) < 2.8f) hiddenTree[i] = true
            var s = 6f; var k = 0
            while (s < len - 4f) {
                val px = ax + ux * s; val py = ay + uy * s
                if (dist(px, py, cx, cy) > 9f && !city.shore.anyWater(px, py) && !city.reserved(px, py)) {
                    val side = if (k % 2 == 0) 1f else -1f
                    offRoad(px - uy * 2.4f * side, py + ux * 2.4f * side, PARK_LAMP, uy * side, -ux * side)
                    if (k % 3 != 2) offRoad(px + ux * 5f + uy * 2.6f * side, py + uy * 5f - ux * 2.6f * side, BENCH, -uy * side, ux * side)
                }
                s += 12f; k++
            }
            offRoad(ax + ux * 2f, ay + uy * 2f, BIN, ux, uy)
        }
    }

    /**
     * The rest of the street furniture: street-name signs on the corners, bollards guarding the corners of the big
     * roads, parking meters along busy shopping streets, cash machines and phone booths by the shops, café tables
     * where the sidewalk is wide enough to sit out, now and then sidewalk works behind a barrier and cones, dumpsters
     * in the lanes, a red lantern by the izakaya doors of Tokyo's drinking lanes, bushes along park paths and
     * billboards over parking lots. A pass of its own after the rest, on its own random sequence, so what was placed
     * before stays exactly where it was and these find the room that's left.
     */
    private fun extras() {
        seed = 7654321
        val g = city.roads
        val mapId = city.mapId
        val manila = mapId == com.hustlecity.game.world.Maps.MANILA
        val tokyo = mapId == com.hustlecity.game.world.Maps.TOKYO
        for (e in g.edges) {
            if (e.overWater || e.under || e.len < 14f) continue
            val major = e.cls.rank >= 2
            for (side in -1..1 step 2) {
                val nx = e.uy * side; val ny = -e.ux * side
                val curb = e.cls.halfWidth + .6f
                val s0 = e.clearA + 4f; val s1 = e.len - e.clearB - 4f
                if (s1 - s0 < 6f) continue
                fun at(s: Float, off: Float, k: Int): Int {
                    if (s < 0f || s > e.len) return -1
                    val px = e.ax + e.ux * s + nx * off; val py = e.ay + e.uy * s + ny * off
                    if (city.shore.anyWater(px, py) || city.parks.any { Geo.inside(px, py, it.poly) } || city.reserved(px, py)) return -1
                    if (!clearSpot(px, py, e.ux, e.uy, nx, ny, k)) return -1
                    return add(px, py, k, -nx, -ny)
                }
                fun anywhere(margin: Float) = s0 + margin + rand() * (s1 - s0 - 2f * margin).coerceAtLeast(1f)
                val hood = city.neighborhoodAt(e.ax + e.ux * (s0 + s1) * .5f + nx * 12f, e.ay + e.uy * (s0 + s1) * .5f + ny * 12f)
                val busy = hood.traffic > .3f
                val wall = e.cls.halfWidth + e.cls.sidewalk - .45f
                if (e.cls == RoadClass.LANE) {
                    if (rand() < .3f) at(anywhere(3f), wall - .45f, DUMPSTER)
                    if (tokyo && hood.look == com.hustlecity.game.world.Look.DENSE_ASIA) repeat(2) { if (rand() < .6f) at(anywhere(2f), wall - .05f, AKACHOCHIN) }
                    continue
                }
                // the street's name on the corner where it begins, on its right-hand side
                if (e.cls.rank >= 1 && rand() < .75f) at(if (side > 0) s0 - 1.5f else s1 + 1.5f, curb, SIGN)
                // a row of bollards guarding the corner of a big road
                if (major && rand() < .4f) for (b in 0..2) at(s1 - .4f - b * 1.25f, curb - .1f, BOLLARD)
                // meters along the kerb where people shop (Manila parks where it can)
                if (!manila && busy && e.cls.rank >= 1 && rand() < .5f) {
                    var s = s0 + 9f; var n = 0
                    while (s < s1 - 9f && n < 6) { if (at(s, curb - .1f, METER) >= 0) n++; s += 6.5f }
                }
                // a cash machine, or a phone booth, by the shops
                if (busy && rand() < .4f) { val atm = manila || rand() < .6f; at(anywhere(5f), if (atm) wall else wall - .3f, if (atm) ATM else PHONE) }
                // tables out on a wide sidewalk
                if (e.cls.sidewalk >= 3.6f && (busy || major) && rand() < .4f) {
                    val s = anywhere(6f)
                    if (at(s, wall - 1.05f, CAFE) >= 0 && rand() < .6f) at(s + 2.7f, wall - 1.05f, CAFE)
                }
                // sidewalk works
                if (rand() < .05f) at(anywhere(6f), curb + .25f, WORKS)
            }
        }
        // bushes along the park paths, clear of the trees, the paths and each other
        for (p in city.parks) {
            if (p.kind != Park.GREEN || Geo.area(p.poly) < 1200f) continue
            for (path in p.paths) {
                val ax = path[0]; val ay = path[1]; val bx = path[2]; val by = path[3]
                val len = dist(ax, ay, bx, by)
                if (len < 12f) continue
                val ux = (bx - ax) / len; val uy = (by - ay) / len
                var s = 9f; var k = 0
                while (s < len - 6f) {
                    val side = if (k % 2 == 0) -1f else 1f
                    val px = ax + ux * s - uy * 4.3f * side; val py = ay + uy * s + ux * 4.3f * side
                    if (Geo.inside(px, py, p.poly) && !nearTree(px, py, 2f) && !city.reserved(px, py) && !clashes(px, py, BUSH)) offRoad(px, py, BUSH, uy * side, -ux * side)
                    s += 9f + rand() * 7f; k++
                }
            }
        }
        // billboards on their legs at the back of parking lots, facing the street (Manila's big lots are jeepney terminals)
        for (b in city.buildings) {
            if (b.kind != com.hustlecity.game.world.BKind.PARKING || b.w < 9f || b.d < 8f) continue
            if (manila && max(b.w, b.d) > 14f && kotlin.math.min(b.w, b.d) > 9f) continue
            if (rand() >= .45f) continue
            val px = b.wx(b.w * .5f, 1.1f); val py = b.wy(b.w * .5f, 1.1f)
            if (city.shore.anyWater(px, py) || city.reserved(px, py) || g.nearAsphalt(px, py, 1f) || clashes(px, py, BILLBOARD)) continue
            add(px, py, BILLBOARD, -b.sn, b.cs)
        }
    }

    /** Park furniture where a road cuts through the park stays on the grass side of the kerb. */
    private fun offRoad(px: Float, py: Float, k: Int, dx: Float, dy: Float) {
        if (!city.roads.nearAsphalt(px, py, 1.2f) && !city.inWater(px, py) && !crowded(px, py, .75f)) add(px, py, k, dx, dy)
    }

    // ------------------------------------------------------------------ painting

    /** Draws prop [i]. [near] adds the small parts. */
    fun draw(scene: Scene3D, i: Int, near: Boolean, time: Float) {
        // local +y points at the road: world (-sin, cos) = (dirX, dirY)
        scene.setFrame(x[i], y[i], dirY[i], -dirX[i])
        try { drawLocal(scene, i, near, time) } finally { scene.clearFrame() }
    }

    private var libVersion = -1
    private val propMesh = arrayOfNulls<Mesh>(KINDS)
    private var mCone: Mesh? = null; private var mCart: Mesh? = null; private var mLanternLine: Mesh? = null
    private var mBuntingLine: Mesh? = null; private var mPole: Mesh? = null; private var mCrate: Mesh? = null
    private val tints = IntArray(6)

    /** Umbrellas, awnings and dumpsters come in a few colors; phone booths in the city's own. */
    private val CANOPIES = intArrayOf(c(0xFF27AE60), c(0xFFC0392B), c(0xFF2F5D8C), c(0xFFE0A030), c(0xFF34495E))
    private val DUMPSTERS = intArrayOf(c(0xFF2F6F4F), c(0xFF2F5D8C), c(0xFF6B4E33), c(0xFF5B6770))

    /** Looks the models up again whenever the pack changes: one per kind, and the parts some kinds share. */
    private fun meshes() {
        if (libVersion == MeshLib.version) return
        libVersion = MeshLib.version
        java.util.Arrays.fill(propMesh, null)
        propMesh[LAMP] = MeshLib.get("prop_lamp_post"); propMesh[HYDRANT] = MeshLib.get("prop_fire_hydrant")
        propMesh[BIN] = MeshLib.get("prop_trash_bin"); propMesh[BENCH] = MeshLib.get("prop_park_bench")
        propMesh[BUS_STOP] = MeshLib.get("prop_bus_shelter"); propMesh[NEWS] = MeshLib.get("prop_newspaper_box")
        propMesh[MAILBOX] = MeshLib.get("prop_mailbox"); propMesh[PLANTER] = MeshLib.get("prop_planter")
        propMesh[VENDING] = MeshLib.get("prop_vending_machine"); propMesh[HOOP] = MeshLib.get("prop_basketball_hoop")
        propMesh[BIKE_RACK] = MeshLib.get("prop_bike_rack"); propMesh[FOUNTAIN] = MeshLib.get("prop_fountain")
        propMesh[PARK_LAMP] = MeshLib.get("prop_park_lamp"); propMesh[VENDOR] = MeshLib.get("prop_market_stall")
        propMesh[LANTERNS] = MeshLib.get("prop_lantern_string"); propMesh[BUNTING] = MeshLib.get("prop_bunting_string")
        propMesh[BOLLARD] = MeshLib.get("prop_bollard"); propMesh[METER] = MeshLib.get("prop_parking_meter")
        propMesh[WORKS] = MeshLib.get("prop_barrier"); propMesh[SIGN] = MeshLib.get("prop_street_sign")
        propMesh[DUMPSTER] = MeshLib.get("prop_dumpster"); propMesh[BILLBOARD] = MeshLib.get("prop_billboard")
        propMesh[ATM] = MeshLib.get("prop_atm"); propMesh[PHONE] = MeshLib.get("prop_phone_booth")
        propMesh[BUSH] = MeshLib.get("prop_bush"); propMesh[CAFE] = MeshLib.get("prop_cafe_table")
        propMesh[AKACHOCHIN] = MeshLib.get("prop_paper_lantern")
        mCone = MeshLib.get("prop_traffic_cone"); mCart = MeshLib.get("prop_hotdog_cart"); mPole = MeshLib.get("prop_power_pole")
        mLanternLine = MeshLib.get("prop_lantern_string_line"); mBuntingLine = MeshLib.get("prop_bunting_string_line")
        mCrate = MeshLib.get("item_wooden_crate")
    }

    /** Billboards are big enough to want their model from further off than the rest. */
    fun bigModel(i: Int) = kind[i] == BILLBOARD

    /**
     * Props as their Blender models (GPU renderer only): same spot, same facing (their front toward the road), lamps
     * and lit panels shining at night, signs and lanterns in their own colors after dark. Some keep a part of the
     * painted version the model doesn't have: a fountain's moving water, the wires between Manila's poles, the ties
     * at the ends of a string across the street. False for a kind or a size the models don't cover, which keeps its
     * boxes.
     */
    private fun model(scene: Scene3D, i: Int, near: Boolean, night: Boolean, time: Float): Boolean {
        meshes()
        val k = kind[i]
        if (k < 0 || k >= propMesh.size) return false
        if (k == UTIL_POLE) {
            val pole = mPole ?: return false
            // the model's cross-arms run along its x: turn them across the line of wires (local y)
            scene.mesh(pole, lod(scene, pole), 0f, 0f, 0f, 0f, 1f, 1f, 1f, 8.6f / 8f, null, 0)
            utilityPole(scene, i, near, night, modelled = true)
            return true
        }
        val mesh = (if (k == VENDOR && i % 3 != 2) mCart else propMesh[k]) ?: return false
        val level = lod(scene, mesh)
        tints[2] = if (night) c(0xFFFFE6A8) else c(0xFF8A9199)
        val glow = (if (night) 4 else 0) or (if (night) MeshPart.NIGHT_LIGHTS else 0)
        when (k) {
            NEWS -> for (m in 0..2) {
                tints[1] = NEWS_COLORS[(i + m) % NEWS_COLORS.size]
                scene.mesh(mesh, level, (m - 1) * .55f, 0f, 0f, 1f, 0f, .9f, .9f, .85f, tints, glow)
            }
            VENDING -> {
                val n = 2 + i % 2
                for (m in 0 until n) {
                    tints[1] = VENDING_BODIES[(i + m) % VENDING_BODIES.size]
                    scene.mesh(mesh, level, (m - n * .5f) * 1.02f + .49f, 0f, 0f, 1f, 0f, tints = tints, glowMask = glow)
                }
            }
            FOUNTAIN -> {
                // the model is the stonework and still water; the jets and ripples stay alive on top
                scene.mesh(mesh, level, 0f, 0f, 0f, 1f, 0f, 1.36f, 1.36f, 1f, tints, glow)
                fountainWater(scene, near, time)
            }
            VENDOR -> {
                // a cart with its umbrella in the vendor's colors, or a stall of fruit under a striped awning
                tints[1] = when (i % 3) { 0 -> c(0xFFE74C3C); 1 -> c(0xFF2F80ED); else -> c(0xFFE74C3C) }
                scene.mesh(mesh, level, 0f, 0f, 0f, 1f, 0f, tints = tints, glowMask = glow)
                // the cart's supplies in a crate on the pavement beside it
                if (i % 3 != 2 && near) mCrate?.let { scene.mesh(it, lod(scene, it), -1.65f, -.3f, 0f, 1f, 0f, tints = tints) }
            }
            LANTERNS, BUNTING -> return string(scene, i, k, near, night)
            WORKS -> {
                scene.mesh(mesh, level, 0f, 0f, 0f, 1f, 0f, tints = tints, glowMask = glow)
                mCone?.let { cone -> for (s in -1..1 step 2) scene.mesh(cone, lod(scene, cone), s * 1.45f, 0f, 0f, 1f, 0f, tints = tints) }
            }
            DUMPSTER -> { tints[1] = DUMPSTERS[i % DUMPSTERS.size]; scene.mesh(mesh, level, 0f, 0f, 0f, 1f, 0f, tints = tints, glowMask = glow) }
            CAFE -> { tints[1] = CANOPIES[i % CANOPIES.size]; scene.mesh(mesh, level, 0f, 0f, 0f, if (i % 2 == 0) 1f else -1f, 0f, tints = tints, glowMask = glow) }
            PHONE -> {
                tints[1] = when (city.mapId) { com.hustlecity.game.world.Maps.TOKYO -> c(0xFF3E8E5A); com.hustlecity.game.world.Maps.SEOUL -> c(0xFFD9DEE4); else -> c(0xFFC0392B) }
                scene.mesh(mesh, level, 0f, 0f, 0f, 1f, 0f, tints = tints, glowMask = glow)
            }
            BUSH -> { val s = .8f + (i % 5) * .1f; scene.mesh(mesh, level, 0f, 0f, 0f, if (i % 2 == 0) 1f else 0f, if (i % 2 == 0) 0f else 1f, s, s, s, tints, glow) }
            else -> scene.mesh(mesh, level, 0f, 0f, 0f, 1f, 0f, tints = tints, glowMask = glow)
        }
        return true
    }

    private fun lod(scene: Scene3D, mesh: Mesh) = scene.meshLevel(mesh, scene.pixels(0f, 0f, 1f, maxOf(mesh.width, mesh.height)), 260f, 70f)

    /**
     * A string of lanterns or banderitas across the street as the model, stretched to this street: tied to poles at
     * both ends it's the whole model, poles and all; otherwise just the line, tied by the painter's hooks and poles.
     */
    private fun string(scene: Scene3D, i: Int, k: Int, near: Boolean, night: Boolean): Boolean {
        val span = aux[i]
        if (span <= 0f) return true
        val both = link[i] == 3
        val m = (if (both) propMesh[k] else (if (k == LANTERNS) mLanternLine else mBuntingLine)) ?: return false
        val stretch = span / 9f
        if (stretch < .5f || stretch > 1.8f) return false
        val glow = if (night) MeshPart.NIGHT_LIGHTS else 0
        val level = scene.meshLevel(m, scene.pixels(0f, 0f, 4f, span), 300f, 90f)
        // the model's string runs along its x: turn it along the prop's y, across the street
        if (k == LANTERNS) scene.mesh(m, level, 0f, 0f, 0f, 0f, 1f, stretch, 1f, LANTERN_Z / 4.8f, null, glow)
        else {
            scene.mesh(m, level, 0f, 0f, 0f, 0f, 1f, stretch, 1f, BUNTING_HIGH / 5.7f, null, glow)
            // the lower string of the pair, without poles of its own
            mBuntingLine?.let { scene.mesh(it, level.coerceAtMost(it.levels.size - 1), 0f, 0f, 0f, 0f, 1f, stretch, 1f, BUNTING_LOW / 5.7f, null, glow) }
        }
        if (!both) ties(scene, i, span * .5f, if (k == LANTERNS) LANTERN_Z else BUNTING_LOW, if (k == LANTERNS) LANTERN_Z else BUNTING_HIGH, near, k == BUNTING)
        return true
    }

    private fun drawLocal(scene: Scene3D, i: Int, near: Boolean, time: Float) {
        val px = 0f; val py = 0f; val f = face[i]
        val night = scene.daylight < .5f
        if ((near || bigModel(i)) && scene.meshesOn && model(scene, i, near, night, time)) return
        // unit vector toward the road
        val ox = when (f) { 2 -> -1f; 3 -> 1f; else -> 0f }
        val oy = when (f) { 0 -> -1f; 1 -> 1f; else -> 0f }
        when (kind[i]) {
            LAMP -> {
                scene.box(px - .22f, py - .22f, 0f, px + .22f, py + .22f, .6f, POLE, POLE)
                scene.box(px - .09f, py - .09f, .6f, px + .09f, py + .09f, 6.2f, POLE, POLE)
                // the arm reaches out over the road
                val ax = px + ox * 1.6f; val ay = py + oy * 1.6f
                scene.box(minOf(px, ax) - .05f, minOf(py, ay) - .05f, 6.05f, maxOf(px, ax) + .05f, maxOf(py, ay) + .05f, 6.17f, POLE, POLE)
                val head = if (night) c(0xFFFFE6A8) else c(0xFF5B6168)
                scene.box(ax - .32f, ay - .32f, 5.8f, ax + .32f, ay + .32f, 6.05f, POLE, head)
                if (near) scene.box(ax - .26f, ay - .26f, 5.76f, ax + .26f, ay + .26f, 5.8f, head, head)
            }
            PARK_LAMP -> {
                scene.box(px - .07f, py - .07f, 0f, px + .07f, py + .07f, 3.6f, IRON, IRON)
                scene.blob(px, py, 3.85f, .3f, scene.lit(if (night) c(0xFFFFF0C2) else c(0xFFE8EDF0), 1f), 10)
            }
            HYDRANT -> {
                val red = c(0xFFD63031)
                scene.cylinder(px, py, .16f, 0f, .52f, 8, red)
                scene.cone(px, py, .2f, .52f, .7f, 8, red, .5f)
                if (near) {
                    scene.box(px - .3f, py - .06f, .3f, px + .3f, py + .06f, .4f, c(0xFFB02424), c(0xFFB02424))
                    scene.box(px - .2f, py - .2f, .45f, px + .2f, py + .2f, .5f, c(0xFFE9E4DA), c(0xFFE9E4DA))
                }
            }
            BIN -> {
                val green = c(0xFF2E5E46)
                scene.cylinder(px, py, .3f, 0f, .85f, 8, green, c(0xFF1C1F22))
                if (near) scene.cylinder(px, py, .33f, .78f, .88f, 8, c(0xFF3F7A5C), c(0xFF1C1F22))
            }
            BENCH -> bench(scene, px, py, f, near)
            BUS_STOP -> busStop(scene, px, py, f, near, night)
            NEWS -> {
                for (k in 0..2) {
                    val along = (k - 1) * .55f
                    val bx = if (f >= 2) px else px + along
                    val by = if (f >= 2) py + along else py
                    val col = NEWS_COLORS[(i + k) % NEWS_COLORS.size]
                    scene.box(bx - .22f, by - .22f, 0f, bx + .22f, by + .22f, .95f, col, col)
                    if (near) scene.alongBox(bx, by, f, .32f, .02f, .55f, .85f, c(0xFFE8ECEF), c(0xFFE8ECEF), .22f)
                }
            }
            MAILBOX -> {
                val blue = c(0xFF1F4E9C)
                scene.box(px - .28f, py - .25f, .1f, px + .28f, py + .25f, .95f, blue, blue)
                scene.box(px - .24f, py - .21f, .95f, px + .24f, py + .21f, 1.08f, blue, blue)
                scene.box(px - .2f, py - .2f, 0f, px - .14f, py + .2f, .1f, IRON, IRON)
                scene.box(px + .14f, py - .2f, 0f, px + .2f, py + .2f, .1f, IRON, IRON)
            }
            BIKE_RACK -> {
                for (k in 0..3) {
                    val along = (k - 1.5f) * .75f
                    val bx = if (f >= 2) px else px + along
                    val by = if (f >= 2) py + along else py
                    if (f >= 2) {
                        scene.box(bx - .35f, by - .03f, .78f, bx + .35f, by + .03f, .84f, c(0xFF9AA3AD), c(0xFF9AA3AD))
                        scene.box(bx - .35f, by - .03f, 0f, bx - .29f, by + .03f, .8f, c(0xFF9AA3AD), c(0xFF9AA3AD))
                        scene.box(bx + .29f, by - .03f, 0f, bx + .35f, by + .03f, .8f, c(0xFF9AA3AD), c(0xFF9AA3AD))
                    } else {
                        scene.box(bx - .03f, by - .35f, .78f, bx + .03f, by + .35f, .84f, c(0xFF9AA3AD), c(0xFF9AA3AD))
                        scene.box(bx - .03f, by - .35f, 0f, bx + .03f, by - .29f, .8f, c(0xFF9AA3AD), c(0xFF9AA3AD))
                        scene.box(bx - .03f, by + .29f, 0f, bx + .03f, by + .35f, .8f, c(0xFF9AA3AD), c(0xFF9AA3AD))
                    }
                }
            }
            PLANTER -> {
                scene.box(px - .55f, py - .55f, 0f, px + .55f, py + .55f, .6f, CONCRETE, CONCRETE)
                scene.box(px - .45f, py - .45f, .6f, px + .45f, py + .45f, .62f, c(0xFF5A4030), c(0xFF5A4030))
                scene.blob(px, py, 1.05f, .55f, scene.lit(scene.mix(c(0xFF4C8C3F), c(0xFFF2F5F8), scene.snow * .6f), .95f), 9)
                if (near) scene.blob(px - .1f, py - .1f, 1.25f, .32f, scene.lit(scene.mix(c(0xFF6DB35A), c(0xFFF2F5F8), scene.snow * .7f), 1.05f), 8)
            }
            FOUNTAIN -> fountain(scene, px, py, near, time)
            UTIL_POLE -> utilityPole(scene, i, near, night)
            VENDOR -> foodCart(scene, i, near)
            VENDING -> vending(scene, i, near, night)
            LANTERNS -> lanterns(scene, i, near, night)
            BUNTING -> bunting(scene, i, near)
            HOOP -> hoop(scene, i, near)
            else -> boxes(scene, i, near, night)
        }
    }

    /** The props added with the Blender library, as the classic renderer and Low quality draw them: a few boxes each. */
    private fun boxes(scene: Scene3D, i: Int, near: Boolean, night: Boolean) {
        val steel = c(0xFFB8C0CA); val dark = c(0xFF2A2D31)
        when (kind[i]) {
            BOLLARD -> {
                scene.cylinder(0f, 0f, .08f, 0f, .92f, 6, dark)
                if (near) scene.cylinder(0f, 0f, .085f, .62f, .7f, 6, c(0xFFF2C94C))
            }
            METER -> {
                scene.box(-.04f, -.04f, 0f, .04f, .04f, 1.08f, c(0xFF5B6770), c(0xFF5B6770))
                scene.box(-.1f, -.08f, 1.08f, .1f, .08f, 1.42f, c(0xFFB8C0CA), c(0xFF8E9399))
                if (near) scene.box(-.06f, .08f, 1.22f, .06f, .085f, 1.32f, c(0xFF56CCF2), c(0xFF56CCF2))
            }
            WORKS -> {
                val orange = c(0xFFF2994A); val white = c(0xFFE9EDF2)
                scene.box(-1.1f, -.03f, .4f, 1.1f, .03f, .6f, orange, white)
                scene.box(-1.1f, -.03f, .75f, 1.1f, .03f, .95f, white, orange)
                for (s in -1..1 step 2) {
                    scene.box(s * .9f - .03f, -.28f, 0f, s * .9f + .03f, .28f, 1f, steel, steel)
                    scene.cone(s * 1.45f, 0f, .15f, 0f, .7f, 6, orange, 0f)
                }
                if (night) { scene.glow = true; scene.blob(.9f, 0f, 1.08f, .08f, c(0xFFFFD34D), 6); scene.glow = false }
            }
            SIGN -> {
                val green = c(0xFF1F6F43)
                scene.box(-.035f, -.035f, 0f, .035f, .035f, 3.2f, steel, steel)
                scene.box(-.6f, -.02f, 2.9f, .6f, .02f, 3.15f, green, green)
                scene.box(-.02f, -.6f, 2.55f, .02f, .6f, 2.8f, green, green)
            }
            DUMPSTER -> {
                val body = DUMPSTERS[i % DUMPSTERS.size]
                scene.box(-.95f, -.6f, .15f, .95f, .55f, 1.2f, c(0xFF1B1D21), body)
                if (near) scene.box(-.9f, -.5f, 0f, .9f, .45f, .15f, dark, dark)
            }
            BILLBOARD -> {
                val gun = c(0xFF5B6770)
                for (s in -1..1 step 2) scene.box(s * 2.2f - .12f, -.12f, 0f, s * 2.2f + .12f, .12f, 5f, gun, gun)
                scene.box(-3.6f, -.1f, 5f, 3.6f, .25f, 8.2f, c(0xFF1B1D21), c(0xFF1B1D21))
                // the ad on the front, toward the road
                if (night) scene.glow = true
                scene.quad(-3.45f, .27f, 5.15f, 3.45f, .27f, 5.15f, 3.45f, .27f, 8.05f, -3.45f, .27f, 8.05f, scene.lit(c(0xFFF2C94C), 1f))
                scene.quad(-3.45f, .28f, 5.15f, 3.45f, .28f, 5.15f, 3.45f, .28f, 6.1f, -3.45f, .28f, 6f, scene.lit(c(0xFFEB5757), 1f))
                scene.glow = false
            }
            ATM -> {
                scene.box(-.4f, -.35f, 0f, .4f, .25f, 1.9f, c(0xFF2F5D8C), c(0xFF2F5D8C))
                if (near) scene.quad(-.2f, .26f, 1.1f, .2f, .26f, 1.1f, .2f, .26f, 1.4f, -.2f, .26f, 1.4f, c(0xFF56CCF2))
            }
            PHONE -> {
                val body = when (city.mapId) { com.hustlecity.game.world.Maps.TOKYO -> c(0xFF3E8E5A); com.hustlecity.game.world.Maps.SEOUL -> c(0xFFD9DEE4); else -> c(0xFFC0392B) }
                scene.box(-.52f, -.52f, 2.3f, .52f, .52f, 2.55f, body, body)
                for (sx in -1..1 step 2) for (sy in -1..1 step 2) scene.box(sx * .45f - .05f, sy * .45f - .05f, 0f, sx * .45f + .05f, sy * .45f + .05f, 2.3f, body, body)
                if (near) scene.box(-.4f, -.4f, .12f, .4f, .4f, 2.3f, scene.withAlpha(c(0xFFBFE3F5), .35f), scene.withAlpha(c(0xFFBFE3F5), .35f))
            }
            BUSH -> {
                scene.material = Mat.FOLIAGE
                val s = .8f + (i % 5) * .1f
                scene.blob(0f, 0f, .5f * s, .72f * s, scene.lit(scene.mix(c(0xFF3E8E4E), c(0xFFF2F5F8), scene.snow * .6f), .95f), 9)
                if (near) scene.blob(.35f * s, .1f * s, .55f * s, .4f * s, scene.lit(scene.mix(c(0xFF5FB36A), c(0xFFF2F5F8), scene.snow * .7f), 1.05f), 7)
                scene.material = Mat.PLAIN
            }
            CAFE -> {
                val canopy = CANOPIES[i % CANOPIES.size]
                scene.cylinder(0f, 0f, .4f, .72f, .75f, 8, c(0xFFE9EDF2))
                scene.box(-.03f, -.03f, 0f, .03f, .03f, 2.3f, dark, dark)
                scene.cone(0f, 0f, 1.15f, 2.1f, 2.45f, 8, scene.lit(scene.snowy(canopy), 1f), .8f)
                if (near) for (s in -1..1 step 2) scene.box(s * .62f - .18f, -.18f, 0f, s * .62f + .18f, .18f, .45f, dark, dark)
            }
            AKACHOCHIN -> {
                scene.box(-.04f, -.04f, 0f, .04f, .04f, 2.6f, c(0xFF3E2723), c(0xFF3E2723))
                scene.box(-.03f, 0f, 2.47f, .03f, .5f, 2.53f, c(0xFF3E2723), c(0xFF3E2723))
                if (night) scene.glow = true
                scene.cylinder(0f, .45f, .2f, 1.72f, 2.28f, 8, if (night) c(0xFFFF6A3D) else c(0xFFD8322B))
                scene.glow = false
            }
        }
    }

    // ------------------------------------------------------------------ Manila and Tokyo

    private val wx = FloatArray(4); private val wy = FloatArray(4); private val wz = FloatArray(4)

    /**
     * A sagging wire from (x0, y0, z0) to (x1, y1, z1) in the prop's frame: a flat ribbon with a thin upright one,
     * so it reads from any angle.
     */
    private fun wire(scene: Scene3D, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, sag: Float, color: Int, th: Float = .035f, segs: Int = 6) {
        val dx = x1 - x0; val dy = y1 - y0
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
        val px = -dy / l * th; val py = dx / l * th
        var ax = x0; var ay = y0; var az = z0
        for (k in 1..segs) {
            val t = k / segs.toFloat()
            val bx = x0 + dx * t; val by = y0 + dy * t; val bz = z0 + (z1 - z0) * t - sag * 4f * t * (1f - t)
            scene.quad(ax - px, ay - py, az, bx - px, by - py, bz, bx + px, by + py, bz, ax + px, ay + py, az, color)
            scene.quad(ax, ay, az - th, bx, by, bz - th, bx, by, bz + th, ax, ay, az + th, color)
            ax = bx; ay = by; az = bz
        }
    }

    /** Where world point (wx, wy) lies in prop [i]'s frame: x along the road, y toward it. */
    private fun localX(i: Int, px: Float, py: Float) = (px - x[i]) * dirY[i] + (py - y[i]) * -dirX[i]
    private fun localY(i: Int, px: Float, py: Float) = -(px - x[i]) * -dirX[i] + (py - y[i]) * dirY[i]

    /**
     * A Manila utility pole and its share of the "spaghetti": power lines on the cross-arm, then a sagging mass of
     * phone, cable and internet lines lower down, every one hung by a different crew at a different slack, one drooping
     * in a long loop. Junction boxes and coils of spare cable cling to the pole, drops run into the houses behind, and
     * now and then a bundle crosses the street to the houses opposite.
     */
    private val ARM_PAINTED = floatArrayOf(-.65f, -.22f, .22f, .65f)
    /** Where the power lines sit on the model pole's upper cross-arm (props.py power_pole, turned across the wires). */
    private val ARM_MODEL = floatArrayOf(-1.1f, -.55f, .55f, 1.1f)

    /**
     * [modelled]: the pole itself, its cross-arms, insulators and transformer are the Blender model, already drawn;
     * this adds the street light and the wires, which run to wherever the next pole and the houses are.
     */
    private fun utilityPole(scene: Scene3D, i: Int, near: Boolean, night: Boolean, modelled: Boolean = false) {
        val concrete = c(0xFFA8A49B)
        val dark = c(0xFF1E1F22)
        if (!modelled) {
            scene.box(-.2f, -.2f, 0f, .2f, .2f, 1f, concrete, concrete)
            scene.box(-.15f, -.15f, 1f, .15f, .15f, 8.6f, concrete, concrete)
            // a cross-arm across the line of wires
            scene.box(-.08f, -.75f, 7.9f, .08f, .75f, 8.08f, c(0xFF5A4A3A), c(0xFF5A4A3A))
        }
        // the street light reaches over the road
        scene.box(-.05f, 0f, 6.95f, .05f, 1.7f, 7.05f, IRON, IRON)
        val head = if (night) c(0xFFFFE6A8) else c(0xFF5B6168)
        scene.box(-.28f, 1.35f, 6.75f, .28f, 1.95f, 6.98f, IRON, head)
        if (!modelled && i % 3 == 0) {
            // a transformer drum
            scene.cylinder(0f, -.42f, .3f, 6f, 6.95f, 8, c(0xFF7D858C), c(0xFF5E666D))
            if (near) scene.box(-.05f, -.25f, 6.4f, .05f, -.15f, 6.5f, IRON, IRON)
        }
        // junction boxes of the cable and phone companies, strapped on one above the other
        if (i % 2 == 0) scene.box(-.17f, -.36f, 4.7f, .17f, -.15f, 5.25f, c(0xFF8D9399), c(0xFF6E757C))
        if (i % 3 == 1) scene.box(-.15f, .15f, 5.05f, .15f, .34f, 5.45f, c(0xFF55606A), c(0xFF454E57))
        val arm = if (modelled) ARM_MODEL else ARM_PAINTED
        val power = if (modelled) 8.3f else 8.15f
        if (near && !modelled) for (yy in arm) scene.box(-.04f, yy - .04f, 8.08f, .04f, yy + .04f, 8.22f, c(0xFFE8E4DA), c(0xFFE8E4DA))
        val nxt = link[i]
        val segs = if (near) 7 else 4
        if (nxt >= 0) {
            val tx = localX(i, x[nxt], y[nxt]); val ty = localY(i, x[nxt], y[nxt])
            // power on the cross-arm
            for ((k, yy) in arm.withIndex()) wire(scene, 0f, yy, power, tx, ty + yy, power, .45f + k * .06f, dark, segs = segs)
            // the tangle: seven lines at their own heights and slack, mostly black, a grey one and a pale fibre
            for (k in 0 until 7) {
                val h = ((i * 7 + k * 13) * 2654435761L ushr 9).toInt() and 0xFF
                val z0 = 5.0f + k * .2f + (h % 5) * .04f
                val z1 = z0 + ((h shr 3) % 3 - 1) * .12f
                val sag = .7f + (h % 9) * .12f + k * .05f
                val off = -.18f + k * .06f
                val col = when (k) { 2 -> c(0xFF3C4046); 5 -> c(0xFFCFCBC0); else -> dark }
                wire(scene, .1f, off, z0, tx - .1f, ty + off + ((h shr 5) % 3 - 1) * .08f, z1, sag, col, .04f + (k % 3) * .006f, segs)
            }
            // and one hanging slack in a long loop
            wire(scene, .12f, .05f, 5.5f, tx - .12f, ty + .05f, 5.6f, 2.0f + (i % 3) * .25f, dark, .035f, segs)
            if (near) {
                // coils of spare cable hanging off the pole
                scene.blob(.35f, -.2f, 5.4f, .32f, dark, 8)
                scene.blob(.35f, -.2f, 5.4f, .18f, scene.lit(c(0xFF2C2E31), 1f), 6)
                if (i % 2 == 1) {
                    scene.blob(-.3f, .25f, 6.15f, .26f, dark, 8)
                    scene.blob(-.3f, .25f, 6.15f, .14f, scene.lit(c(0xFF2C2E31), 1f), 6)
                }
                // lines wrapped round the pole where they change direction
                for (k in 0..2) scene.box(-.19f, -.19f, 5.15f + k * .32f, .19f, .19f, 5.24f + k * .32f, dark, dark)
            }
        }
        for (k in 0..2) {
            // service drops back to the fronts of the houses, each ending on an insulator screwed into the wall
            val f = drops[i * 3 + k]
            if (f <= 0f) continue
            val dx = DROP_X[k]; val dz = DROP_Z[k]
            wire(scene, 0f, -.1f, DROP_TOP[k], dx, -f, dz, .3f + k * .05f, dark, .03f, 4)
            if (near) scene.box(dx - .05f, -f, dz - .1f, dx + .05f, -f + .1f, dz + .02f, c(0xFFE8E4DA), c(0xFFE8E4DA))
        }
        val across = cross[i]
        if (across > 0f) {
            // a bundle over the street, gathered at a bracket on the front of a house opposite
            for (k in 0 until 3) wire(scene, 0f, .15f, 5.9f + k * .18f, 0f, across, BUNDLE_Z + k * BUNDLE_STEP, .55f + k * .15f, dark, .03f, segs)
            if (near) scene.box(-.14f, across - .12f, BUNDLE_Z - .12f, .14f, across, BUNDLE_Z + BUNDLE_STEP * 2 + .08f, IRON, IRON)
        }
    }

    private fun foodCart(scene: Scene3D, i: Int, near: Boolean) {
        val kind = i % 3
        val body = when (kind) { 0 -> c(0xFFC9CED3); 1 -> c(0xFF2E7D4F); else -> c(0xFFB5652D) }
        val trim = when (kind) { 0 -> c(0xFFD32F2F); 1 -> c(0xFFF2C230); else -> c(0xFF6B3E1F) }
        // the cart on bicycle wheels, a counter and a glass case or a wok on top
        scene.box(-.8f, -.4f, .45f, .8f, .4f, 1.05f, scene.snowy(body), body)
        scene.box(-.82f, -.42f, 1.05f, .82f, .42f, 1.1f, trim, trim)
        for (sx in floatArrayOf(-.55f, .55f)) {
            scene.box(sx - .03f, .42f, .05f, sx + .03f, .5f, .75f, IRON, IRON)
            scene.cylinder(sx, .46f, .32f, .05f, .12f, 8, c(0xFF2A2D31))
        }
        if (kind == 0) {
            scene.cylinder(-.3f, 0f, .32f, 1.1f, 1.22f, 8, c(0xFF2A2D31), c(0xFF8A5A2C))
            if (near) for (k in 0..4) scene.blob(.25f + (k % 3) * .16f, -.1f + (k / 3) * .18f, 1.18f, .06f, c(0xFFE09A3E), 5)
        } else {
            val glass = scene.withAlpha(c(0xFFDDEBF2), .45f)
            scene.box(-.6f, -.3f, 1.1f, .6f, .3f, 1.5f, glass, glass)
            if (near) for (k in 0..5) scene.box(-.5f + k * .18f, -.15f, 1.12f, -.38f + k * .18f, .15f, 1.24f,
                if (kind == 1) c(0xFFF4D03F) else c(0xFFE67E22), c(0xFFC0392B))
        }
        // the umbrella: alternating panels round a pole
        scene.box(-.03f, -.03f, 1.1f, .03f, .03f, 2.5f, IRON, IRON)
        val a = when (kind) { 0 -> c(0xFFE74C3C); 1 -> c(0xFF2F80ED); else -> c(0xFFF2C230) }
        val b = c(0xFFF5F5F0)
        val r = 1.35f; val panels = 8
        for (k in 0 until panels) {
            val a0 = k * 6.2832f / panels; val a1 = (k + 1) * 6.2832f / panels
            wx[0] = 0f; wy[0] = 0f; wz[0] = 2.75f
            wx[1] = kotlin.math.cos(a0) * r; wy[1] = sin(a0) * r; wz[1] = 2.25f
            wx[2] = kotlin.math.cos(a1) * r; wy[2] = sin(a1) * r; wz[2] = 2.25f
            scene.poly(3, wx, wy, wz, scene.lit(scene.snowy(if (k % 2 == 0) a else b, .8f), 1f))
        }
        if (near) {
            // a stool for customers and a crate of supplies
            scene.cylinder(.2f, .95f, .18f, 0f, .45f, 6, c(0xFFD32F2F))
            scene.box(-1.3f, -.3f, 0f, -.9f, .2f, .35f, c(0xFF2F80ED), c(0xFF2266C4))
        }
    }

    private fun vending(scene: Scene3D, i: Int, near: Boolean, night: Boolean) {
        val n = 2 + i % 2
        for (m in 0 until n) {
            val x0 = (m - n * .5f) * 1.02f; val x1 = x0 + .98f
            val body = VENDING_BODIES[(i + m) % VENDING_BODIES.size]
            scene.box(x0, -.38f, 0f, x1, .38f, 1.86f, body, body)
            val f = .385f
            // the display: rows of little bottles behind glass, bright at night
            val panel = if (night) c(0xFFF3F8FF) else c(0xFFDCE6EE)
            if (night) scene.glow = true
            scene.quad(x0 + .08f, f, .95f, x1 - .08f, f, .95f, x1 - .08f, f, 1.75f, x0 + .08f, f, 1.75f, panel)
            scene.glow = false
            if (near) {
                for (row in 0..2) for (col in 0..5) {
                    val bx = x0 + .14f + col * .13f; val bz = 1.02f + row * .25f
                    val drink = DRINKS[(row * 7 + col * 3 + i + m) % DRINKS.size]
                    scene.quad(bx, f + .01f, bz, bx + .08f, f + .01f, bz, bx + .08f, f + .01f, bz + .16f, bx, f + .01f, bz + .16f, drink)
                }
                // coin slot and the drop tray
                scene.quad(x1 - .3f, f, .6f, x1 - .12f, f, .6f, x1 - .12f, f, .85f, x1 - .3f, f, .85f, c(0xFF3A3D42))
                scene.quad(x0 + .15f, f, .12f, x1 - .15f, f, .12f, x1 - .15f, f, .38f, x0 + .15f, f, .38f, c(0xFF22252A))
            }
        }
        if (near) {
            // a recycling bin beside them
            val bx = n * .51f + .3f
            scene.box(bx - .22f, -.2f, 0f, bx + .22f, .2f, .78f, c(0xFF2F80ED), c(0xFF2F80ED))
        }
    }

    private fun lanterns(scene: Scene3D, i: Int, near: Boolean, night: Boolean) {
        val span = aux[i]
        if (span <= 0f) return
        val dark = c(0xFF26221E)
        val z = LANTERN_Z
        val h = span * .5f
        wire(scene, 0f, -h, z, 0f, h, z, .35f, dark, .025f, 5)
        ties(scene, i, h, z, z, near, false)
        val count = 5
        for (k in 0 until count) {
            val t = (k + .5f) / count
            val ly = span * t - h
            val lz = z - .35f * 4f * t * (1f - t) - .05f
            val red = (k + i) % 4 != 0
            val base = if (red) c(0xFFD8322B) else c(0xFFF1E6CC)
            val col = if (night) (if (red) c(0xFFFF6A3D) else c(0xFFFFF0C8)) else scene.lit(base, 1f)
            if (night) scene.glow = true
            scene.cylinder(0f, ly, .22f, lz - .62f, lz - .1f, 8, col, col)
            scene.glow = false
            if (near) {
                scene.cylinder(0f, ly, .15f, lz - .1f, lz - .02f, 6, dark)
                scene.cylinder(0f, ly, .15f, lz - .7f, lz - .62f, 6, dark)
            }
        }
    }

    /**
     * Banderitas: two strings of little triangular flags across the street, for the barangay fiesta that never quite
     * ends. Up close every flag shows; from afar every other one.
     */
    private fun bunting(scene: Scene3D, i: Int, near: Boolean) {
        val span = aux[i]
        if (span <= 0f) return
        val string = c(0xFFE8E2D0)
        val step = if (near) .42f else .84f
        val h = span * .5f
        ties(scene, i, h, BUNTING_LOW, BUNTING_HIGH, near, true)
        for (row in 0..1) {
            val z = if (row == 0) BUNTING_LOW else BUNTING_HIGH
            val sag = .55f - row * .12f
            val shift = row * .21f
            wire(scene, 0f, -h, z, 0f, h, z, sag, string, .012f, 5)
            var y = -h + .3f + shift
            var k = (i + row * 3) % FLAGS.size
            while (y < h - .3f) {
                val t = (y + h) / span
                val zt = z - sag * 4f * t * (1f - t)
                fx3[0] = 0f; fy3[0] = y - .15f; fz3[0] = zt
                fx3[1] = 0f; fy3[1] = y + .15f; fz3[1] = zt
                fx3[2] = 0f; fy3[2] = y; fz3[2] = zt - .34f
                scene.poly(3, fx3, fy3, fz3, scene.lit(FLAGS[k], 1f))
                y += step; k = (k + 1) % FLAGS.size
            }
        }
    }

    private val fx3 = FloatArray(3); private val fy3 = FloatArray(3); private val fz3 = FloatArray(3)

    /**
     * What holds a string's two ends (at -[h] and [h] along it), its lines running at [low]..[high]: a pole of its own
     * where [link] says so (bamboo with its leaves left on for the banderitas, a slim black post in Tokyo), otherwise
     * hooks screwed into the house front, one per line.
     */
    private fun ties(scene: Scene3D, i: Int, h: Float, low: Float, high: Float, near: Boolean, bamboo: Boolean) {
        for (end in 0..1) {
            val y = if (end == 0) -h else h
            if ((link[i] shr end) and 1 == 1) {
                if (bamboo) {
                    val cane = c(0xFFC2A15E)
                    scene.cylinder(0f, y, .075f, 0f, high + .55f, 6, cane, cane)
                    if (near) {
                        for (n in 1..5) scene.cylinder(0f, y, .088f, n * 1.15f, n * 1.15f + .06f, 6, c(0xFF9C7E43), c(0xFF9C7E43))
                        // the cord wound round the cane where each line is tied
                        for (z in floatArrayOf(low, high)) scene.cylinder(0f, y, .095f, z - .05f, z + .05f, 6, c(0xFFE8E2D0), c(0xFFE8E2D0))
                        scene.blob(0f, y, high + .85f, .42f, scene.lit(c(0xFF6E9A3A), 1f), 7)
                        scene.blob(.18f, y + .1f, high + .6f, .28f, scene.lit(c(0xFF5E8A32), 1f), 6)
                    }
                } else {
                    scene.box(-.05f, y - .05f, 0f, .05f, y + .05f, high + .3f, POLE, POLE)
                    if (near) scene.box(-.07f, y - .07f, high - .06f, .07f, y + .07f, high + .04f, IRON, IRON)
                }
            } else if (near) {
                // a hook out of the wall for each line, pointing along the string
                val out = if (end == 0) .1f else -.1f
                for (z in if (low == high) floatArrayOf(low) else floatArrayOf(low, high))
                    scene.box(-.025f, minOf(y, y + out), z - .025f, .025f, maxOf(y, y + out), z + .025f, IRON, IRON)
            }
        }
    }

    /** A basketball hoop on a post by the lane: a plywood backboard, an orange rim over the street and a ragged net. */
    private fun hoop(scene: Scene3D, i: Int, near: Boolean) {
        val post = if (i % 2 == 0) c(0xFF6B4E33) else c(0xFF5E666D)
        val board = if (i % 3 == 0) c(0xFFD9C9A3) else c(0xFFF2F2EE)
        val orange = c(0xFFE8622A)
        scene.box(-.08f, -.08f, 0f, .08f, .08f, 3.95f, post, post)
        scene.box(-.05f, 0f, 3.55f, .05f, .82f, 3.65f, post, post)
        scene.box(-.62f, .78f, 2.85f, .62f, .84f, 3.8f, board, board)
        if (near) {
            // the painted square over the rim
            val paint = c(0xFFC0392B)
            scene.box(-.25f, .845f, 3.1f, .25f, .855f, 3.13f, paint, paint)
            scene.box(-.25f, .845f, 3.45f, .25f, .855f, 3.48f, paint, paint)
            scene.box(-.25f, .845f, 3.1f, -.22f, .855f, 3.48f, paint, paint)
            scene.box(.22f, .845f, 3.1f, .25f, .855f, 3.48f, paint, paint)
        }
        // the rim: a ring of short bars
        val cy = 1.08f; val r = .23f
        for (k in 0 until 8) {
            val a0 = k * 6.2832f / 8; val a1 = (k + 1) * 6.2832f / 8
            val x0 = kotlin.math.cos(a0) * r; val y0 = cy + kotlin.math.sin(a0) * r
            val x1 = kotlin.math.cos(a1) * r; val y1 = cy + kotlin.math.sin(a1) * r
            scene.box(minOf(x0, x1) - .02f, minOf(y0, y1) - .02f, 3.03f, maxOf(x0, x1) + .02f, maxOf(y0, y1) + .02f, 3.07f, orange, orange)
        }
        if (near) {
            // the net, narrowing as it hangs
            val net = c(0xFFE9E6DD)
            for (k in 0 until 6) {
                val a = k * 6.2832f / 6
                val xa = kotlin.math.cos(a) * r; val ya = cy + kotlin.math.sin(a) * r
                val xb = kotlin.math.cos(a) * r * .55f; val yb = cy + kotlin.math.sin(a) * r * .55f
                scene.quad(xa - .012f, ya, 3.03f, xa + .012f, ya, 3.03f, xb + .012f, yb, 2.62f, xb - .012f, yb, 2.62f, net)
            }
        }
    }

    private fun bench(scene: Scene3D, px: Float, py: Float, f: Int, near: Boolean) {
        val wood = scene.snowy(WOOD)
        // seat, back rest on the side away from the road, and iron legs
        scene.alongBox(px, py, f, 1.7f, .45f, .4f, .48f, wood, WOOD)
        scene.alongBox(px, py, f, 1.7f, .08f, .52f, .92f, wood, WOOD, -.24f)
        if (near) for (s in intArrayOf(-1, 1)) {
            val lx = if (f >= 2) px else px + s * .7f
            val ly = if (f >= 2) py + s * .7f else py
            scene.alongBox(lx, ly, f, .07f, .5f, 0f, .42f, IRON, IRON)
            scene.alongBox(lx, ly, f, .07f, .5f, .48f, .62f, IRON, IRON, .02f)
        }
    }

    private fun busStop(scene: Scene3D, px: Float, py: Float, f: Int, near: Boolean, night: Boolean) {
        val glass = scene.withAlpha(scene.lit(c(0xFFBFD9E8), 1f), .35f)
        val frame = c(0xFF5D6670)
        val len = 4f
        // the shelter's back sits on the building side, open toward the road
        val back = .7f
        val bx = px - (when (f) { 2 -> -1f; 3 -> 1f; else -> 0f }) * back
        val by = py - (when (f) { 0 -> -1f; 1 -> 1f; else -> 0f }) * back
        // back panel with an ad, side panels, roof
        scene.alongBox(bx, by, f, len, .06f, .1f, 2.3f, frame, frame)
        val ad = if (night) c(0xFFFFF1C9) else c(0xFF2F80ED)
        scene.alongBox(bx, by, f, len * .4f, .08f, .3f, 2.1f, ad, ad, .02f)
        for (s in intArrayOf(-1, 1)) {
            val sx = if (f >= 2) bx else bx + s * (len / 2)
            val sy = if (f >= 2) by + s * (len / 2) else by
            scene.alongBox(sx, sy, f, .06f, 1.4f, 0f, 2.35f, frame, frame, .7f)
            if (near) scene.alongBox(sx, sy, f, .02f, 1.3f, .3f, 2.25f, glass, glass, .7f)
        }
        scene.alongBox(px, py, f, len + .3f, 1.7f, 2.35f, 2.5f, scene.snowy(c(0xFF4E565E)), frame, -.05f)
        if (near) {
            // a bench inside and a stop sign on a pole by the curb
            scene.alongBox(bx, by, f, len * .55f, .45f, .42f, .5f, c(0xFF8C939B), c(0xFF6E757C), .4f)
            val ox = when (f) { 2 -> -1f; 3 -> 1f; else -> 0f }; val oy = when (f) { 0 -> -1f; 1 -> 1f; else -> 0f }
            val sx = px + ox * .6f + (if (f >= 2) 0f else len / 2 + .6f); val sy = py + oy * .6f + (if (f >= 2) len / 2 + .6f else 0f)
            scene.box(sx - .05f, sy - .05f, 0f, sx + .05f, sy + .05f, 2.8f, IRON, IRON)
            scene.box(sx - .28f, sy - .28f, 2.4f, sx + .28f, sy + .28f, 2.95f, c(0xFF1F6FD1), c(0xFF1F6FD1))
        }
    }

    /** The fountain model's living water: ripples spreading on the basin and the jets playing over it (not when frozen). */
    private fun fountainWater(scene: Scene3D, near: Boolean, time: Float) {
        if (scene.snow > .3f) return
        for (k in 0..1) {
            val ph = ((time * .5f + k * .5f) % 1f)
            scene.disc(0f, 0f, .48f, .8f + ph * 2f, .8f + ph * 2f, 14, scene.withAlpha(c(0xFFE3F1FA), (1f - ph) * .35f))
        }
        if (!near) return
        val jet = 3.3f + sin(time * 2.3f) * .15f
        scene.blob(0f, 0f, jet, .24f, scene.withAlpha(c(0xFFF4FAFF), .75f), 8)
        for (k in 0 until 6) {
            val a = k * 1.047f + time * .3f
            scene.blob(kotlin.math.cos(a) * 1.55f, sin(a) * 1.55f, .75f, .16f, scene.withAlpha(c(0xFFE3F1FA), .5f), 6)
        }
    }

    private fun fountain(scene: Scene3D, px: Float, py: Float, near: Boolean, time: Float) {
        val stone = c(0xFFCFC6B6)
        val frozen = scene.snow > .3f
        scene.cylinder(px, py, 3.4f, 0f, .55f, 12, stone, stone)
        val water = if (frozen) c(0xFFDCE9F2) else c(0xFF4F8FB8)
        scene.disc(px, py, .57f, 3.05f, 3.05f, 16, scene.lit(water, 1f))
        if (!frozen) {
            // ripples spreading from the middle
            for (k in 0..1) {
                val ph = ((time * .5f + k * .5f) % 1f)
                scene.disc(px, py, .58f, .8f + ph * 2.1f, .8f + ph * 2.1f, 14, scene.withAlpha(c(0xFFE3F1FA), (1f - ph) * .35f))
            }
        }
        scene.cylinder(px, py, .45f, .55f, 1.7f, 8, stone)
        scene.cylinder(px, py, 1.3f, 1.7f, 1.95f, 10, stone, if (frozen) c(0xFFDCE9F2) else c(0xFF5D9CC4))
        scene.cylinder(px, py, .22f, 1.95f, 2.6f, 6, stone)
        if (!frozen && near) {
            val jet = 3.1f + sin(time * 2.3f) * .15f
            scene.blob(px, py, jet, .28f, scene.withAlpha(c(0xFFF4FAFF), .8f), 8)
            scene.blob(px, py, 2.4f, .6f, scene.withAlpha(c(0xFFE3F1FA), .45f), 10)
            for (k in 0 until 6) {
                val a = k * 1.047f + time * .3f
                scene.blob(px + kotlin.math.cos(a) * 1.5f, py + sin(a) * 1.5f, 1.4f, .18f, scene.withAlpha(c(0xFFE3F1FA), .5f), 6)
            }
        }
    }

    /** True for lamps that light the ground at night. */
    fun isLamp(i: Int) = kind[i] == LAMP || kind[i] == PARK_LAMP || kind[i] == UTIL_POLE

    /** Props that move (fountains) are drawn every frame instead of being cached. */
    fun animated(i: Int) = kind[i] == FOUNTAIN

    /** Where the light falls: under the lamp head, which reaches out over the road. */
    fun lightX(i: Int) = x[i] + (if (kind[i] == LAMP || kind[i] == UTIL_POLE) dirX[i] * 1.6f else 0f)
    fun lightY(i: Int) = y[i] + (if (kind[i] == LAMP || kind[i] == UTIL_POLE) dirY[i] * 1.6f else 0f)
}

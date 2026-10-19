package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Park
import com.hustlecity.game.world.RoadClass
import com.hustlecity.game.world.dist
import kotlin.math.abs
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

        private val POLE = c(0xFF2F3A35)
        private val IRON = c(0xFF2A2D31)
        private val WOOD = c(0xFF9C6B43)
        private val CONCRETE = c(0xFFB9B4AA)
        private val NEWS_COLORS = intArrayOf(c(0xFFD63031), c(0xFF0984E3), c(0xFFFDCB6E), c(0xFF00B894))
    }

    var count = 0; private set
    var x = FloatArray(4096); private set
    var y = FloatArray(4096); private set
    var kind = IntArray(4096); private set
    var face = IntArray(4096); private set
    /** Direction toward the road (unit x, y); props are drawn turned to face it. */
    var dirX = FloatArray(4096); private set
    var dirY = FloatArray(4096); private set
    /** Trees hidden because a park path or fountain is there. */
    val hiddenTree = BooleanArray(city.trees.size)
    /**
     * Index into the tree list -> 0 street tree, 1 park tree, 2 park conifer, 3 flowering street tree, 4 palm,
     * 5 ginkgo (golden in autumn).
     */
    val treeKind = IntArray(city.trees.size)

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

    private fun add(px: Float, py: Float, k: Int, dx: Float, dy: Float) {
        if (count == x.size) {
            x = x.copyOf(count * 2); y = y.copyOf(count * 2); kind = kind.copyOf(count * 2); face = face.copyOf(count * 2)
            dirX = dirX.copyOf(count * 2); dirY = dirY.copyOf(count * 2)
        }
        x[count] = px; y[count] = py; kind[count] = k; face[count] = 1; dirX[count] = dx; dirY[count] = dy
        count++
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
        for (e in g.edges) {
            if (e.overWater || e.len < 14f) continue
            val major = e.cls.rank >= 2
            for (side in -1..1 step 2) {
                // n points from the road to this sidewalk; props face the road (-n)
                val nx = e.uy * side; val ny = -e.ux * side
                val curb = e.cls.halfWidth + .6f
                val s0 = e.clearA + 4f; val s1 = e.len - e.clearB - 4f
                if (s1 - s0 < 6f) continue
                fun at(s: Float, off: Float, k: Int) {
                    val px = e.ax + e.ux * s + nx * off; val py = e.ay + e.uy * s + ny * off
                    if (city.shore.anyWater(px, py) || city.parks.any { Geo.inside(px, py, it.poly) }) return
                    add(px, py, k, -nx, -ny)
                }
                val hood = city.neighborhoodAt(e.ax + e.ux * (s0 + s1) * .5f + nx * 12f, e.ay + e.uy * (s0 + s1) * .5f + ny * 12f)
                val busy = hood.traffic > .3f
                // lamp posts every ~25 m (one side only on narrow lanes)
                if (e.cls != RoadClass.LANE || side > 0) {
                    var s = s0 + 5f
                    while (s < s1 - 3f) {
                        val px = e.ax + e.ux * s + nx * curb; val py = e.ay + e.uy * s + ny * curb
                        at(if (nearTree(px, py, 1.6f)) s + 2.2f else s, curb, LAMP)
                        s += if (e.cls == RoadClass.LANE) 30f else 25f
                    }
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
        // tree kinds: what the map planted, or park trees vs street trees with some conifers and flowering trees
        for ((i, t) in city.trees.withIndex()) {
            val inPark = city.parks.any { it.kind != Park.PLAZA && Geo.inside(t.x, t.y, it.poly) }
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
        if (Geo.inside(cx, cy, p.poly) && !city.shore.anyWater(cx, cy)) {
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
                if (dist(px, py, cx, cy) > 9f && !city.shore.anyWater(px, py)) {
                    val side = if (k % 2 == 0) 1f else -1f
                    add(px - uy * 2.4f * side, py + ux * 2.4f * side, PARK_LAMP, uy * side, -ux * side)
                    if (k % 3 != 2) add(px + ux * 5f + uy * 2.6f * side, py + uy * 5f - ux * 2.6f * side, BENCH, -uy * side, ux * side)
                }
                s += 12f; k++
            }
            add(ax + ux * 2f, ay + uy * 2f, BIN, ux, uy)
        }
    }

    // ------------------------------------------------------------------ painting

    /** Draws prop [i]. [near] adds the small parts. */
    fun draw(scene: Scene3D, i: Int, near: Boolean, time: Float) {
        // local +y points at the road: world (-sin, cos) = (dirX, dirY)
        scene.setFrame(x[i], y[i], dirY[i], -dirX[i])
        try { drawLocal(scene, i, near, time) } finally { scene.clearFrame() }
    }

    private fun drawLocal(scene: Scene3D, i: Int, near: Boolean, time: Float) {
        val px = 0f; val py = 0f; val f = face[i]
        val night = scene.daylight < .5f
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
    fun isLamp(i: Int) = kind[i] == LAMP || kind[i] == PARK_LAMP

    /** Props that move (fountains) are drawn every frame instead of being cached. */
    fun animated(i: Int) = kind[i] == FOUNTAIN

    /** Where the light falls: under the lamp head, which reaches out over the road. */
    fun lightX(i: Int) = x[i] + (if (kind[i] == LAMP) dirX[i] * 1.6f else 0f)
    fun lightY(i: Int) = y[i] + (if (kind[i] == LAMP) dirY[i] * 1.6f else 0f)
}

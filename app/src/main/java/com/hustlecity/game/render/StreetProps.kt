package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.world.City
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
    /** Prop indices per block, [row * COLS + col]. */
    val byBlock: Array<IntArray>
    /** Trees hidden because a park path or fountain is there. */
    val hiddenTree = BooleanArray(city.trees.size)
    /** Index into the tree list -> 0 street tree, 1 park tree, 2 park conifer, 3 flowering street tree. */
    val treeKind = IntArray(city.trees.size)

    private var seed = 1234567

    private fun rand(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65536f
    }

    private fun add(px: Float, py: Float, k: Int, f: Int) {
        if (count == x.size) {
            x = x.copyOf(count * 2); y = y.copyOf(count * 2); kind = kind.copyOf(count * 2); face = face.copyOf(count * 2)
        }
        x[count] = px; y[count] = py; kind[count] = k; face[count] = f
        count++
    }

    private fun nearTree(px: Float, py: Float, r: Float): Boolean {
        for (t in city.trees) if (abs(t.x - px) < r && abs(t.y - py) < r) return true
        return false
    }

    init {
        val lists = Array(City.COLS * City.ROWS) { ArrayList<Int>() }
        for (row in 0 until City.ROWS) for (col in 0 until City.COLS) {
            val start = count
            val bx0 = col * City.AV_SPACING + City.AV_HALF + City.SIDEWALK
            val bx1 = (col + 1) * City.AV_SPACING - City.AV_HALF - City.SIDEWALK
            val by0 = row * City.ST_SPACING + City.ST_HALF + City.SIDEWALK
            val by1 = (row + 1) * City.ST_SPACING - City.ST_HALF - City.SIDEWALK
            val curb = City.SIDEWALK - .6f
            val hood = City.neighborhoodOfBlock(col, row)
            val blockIdx = row * City.COLS + col
            if (blockIdx in city.parkBlocks) {
                park(bx0, by0, bx1, by1)
            } else {
                // a bus shelter mid-block on every other avenue side
                val busWest = (row + col) % 2 == 0
                val busY = (by0 + by1) / 2
                if (row % 2 == 0) add(if (busWest) bx0 - curb + .2f else bx1 + curb - .2f, busY, BUS_STOP, if (busWest) 2 else 3)
                // lamp posts along the streets and the avenues
                var lx = bx0 + 9f
                while (lx < bx1 - 6f) {
                    for (side in 0..1) {
                        val ly = if (side == 0) by0 - curb else by1 + curb
                        val px = if (nearTree(lx, ly, 1.6f)) lx + 2.2f else lx
                        add(px, ly, LAMP, side)
                    }
                    lx += 26f
                }
                var ly = by0 + 9f
                while (ly < by1 - 6f) {
                    if (abs(ly - busY) > 5f || row % 2 != 0) {
                        add(bx0 - curb, ly, LAMP, 2)
                        add(bx1 + curb, ly, LAMP, 3)
                    }
                    ly += 22f
                }
                // corner furniture
                for (corner in 0..3) {
                    val west = corner % 2 == 0; val north = corner < 2
                    val cx = if (west) bx0 + 3.2f else bx1 - 3.2f
                    val cy = if (north) by0 - curb else by1 + curb
                    val f = if (north) 0 else 1
                    val r = rand()
                    when {
                        r < .45f -> add(cx, cy, BIN, f)
                        r < .6f -> add(cx, cy, MAILBOX, f)
                    }
                    // newspaper boxes on the avenue side, near the corner
                    if (rand() < .35f) add(if (west) bx0 - curb + .3f else bx1 + curb - .3f, if (north) by0 + 4.5f else by1 - 4.5f, NEWS, if (west) 2 else 3)
                }
                // a hydrant per block side
                for (side in 0..1) {
                    val hx = if (rand() < .5f) bx0 + 6.5f else bx1 - 6.5f
                    add(hx, if (side == 0) by0 - curb else by1 + curb, HYDRANT, side)
                }
                // benches, bike racks and planters in the busier neighborhoods
                val busy = hood == 0 || hood == 2 || hood == 3 || hood == 4
                for (side in 0..1) {
                    val r = rand()
                    val px = bx0 + 20f + rand() * (bx1 - bx0 - 40f)
                    val py = if (side == 0) by0 - curb else by1 + curb
                    if (nearTree(px, py, 2.2f)) continue
                    when {
                        r < (if (busy) .35f else .15f) -> add(px, py, BENCH, side)
                        r < (if (busy) .55f else .25f) -> add(px, py, BIKE_RACK, side)
                        r < (if (busy) .75f else .3f) && hood != 5 -> add(px, py, PLANTER, side)
                    }
                }
            }
            for (i in start until count) lists[blockIdx] += i
        }
        byBlock = Array(lists.size) { lists[it].toIntArray() }
        // tree kinds: park trees vs street trees, some conifers and flowering trees
        for ((i, t) in city.trees.withIndex()) {
            val col = (t.x / City.AV_SPACING).toInt().coerceIn(0, City.COLS - 1)
            val row = (t.y / City.ST_SPACING).toInt().coerceIn(0, City.ROWS - 1)
            val inPark = (row * City.COLS + col) in city.parkBlocks
            val h = ((i * 2654435761L) ushr 7).toInt() and 0xFF
            treeKind[i] = when {
                inPark && h % 4 == 0 -> 2
                inPark -> 1
                h % 5 == 0 -> 3
                else -> 0
            }
        }
    }

    private fun park(bx0: Float, by0: Float, bx1: Float, by1: Float) {
        val cx = (bx0 + bx1) / 2; val cy = (by0 + by1) / 2
        add(cx, cy, FOUNTAIN, 0)
        // keep the paths and the fountain clear of trees
        for ((i, t) in city.trees.withIndex()) {
            if (t.x < bx0 || t.x > bx1 || t.y < by0 || t.y > by1) continue
            if (abs(t.x - cx) < 2.8f || abs(t.y - cy) < 2.8f || (abs(t.x - cx) < 8f && abs(t.y - cy) < 8f)) hiddenTree[i] = true
        }
        // lamps and benches along the paths
        var px = bx0 + 8f
        var k = 0
        while (px < bx1 - 6f) {
            if (abs(px - cx) > 9f) {
                add(px, cy + (if (k % 2 == 0) -2.4f else 2.4f), PARK_LAMP, if (k % 2 == 0) 0 else 1)
                if (k % 3 != 2) add(px + 5f, cy + (if (k % 2 == 0) 2.6f else -2.6f), BENCH, if (k % 2 == 0) 0 else 1)
            }
            px += 12f; k++
        }
        var py = by0 + 7f
        while (py < by1 - 5f) {
            if (abs(py - cy) > 9f) {
                add(cx + 2.4f, py, PARK_LAMP, 2)
                add(cx - 2.6f, py + 4f, BENCH, 3)
            }
            py += 12f
        }
        add(bx0 + 2f, by0 + 2f, BIN, 0)
        add(bx1 - 2f, by1 - 2f, BIN, 1)
    }

    // ------------------------------------------------------------------ painting

    /** Draws prop [i]. [near] adds the small parts. */
    fun draw(scene: Scene3D, i: Int, near: Boolean, time: Float) {
        val px = x[i]; val py = y[i]; val f = face[i]
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

    /** Where the light falls: under the lamp head. */
    fun lightX(i: Int) = x[i] + (if (kind[i] == LAMP) when (face[i]) { 2 -> -1.6f; 3 -> 1.6f; else -> 0f } else 0f)
    fun lightY(i: Int) = y[i] + (if (kind[i] == LAMP) when (face[i]) { 0 -> -1.6f; 1 -> 1.6f; else -> 0f } else 0f)
}

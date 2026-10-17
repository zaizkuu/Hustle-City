package com.hustlecity.game.world

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

enum class BKind(val label: String) {
    RESIDENTIAL("Residential"), RETAIL("Retail"), OFFICE("Office"), WAREHOUSE("Warehouse"),
    SPECIAL("Special"), PARK("Park"), PARKING("Parking Lot"), GAS("Gas Station")
}

class Neighborhood(
    val id: Int,
    val name: String,
    val color: Int,
    val traffic: Float,
    val rentPerSqm: Double,
    val wealth: Float,
    val minH: Float,
    val maxH: Float,
    /** Weights for residential, retail, office, warehouse. */
    val mix: FloatArray,
    val facades: IntArray,
)

class RoofBox(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val h: Float)

class Building(
    val id: Int,
    val minX: Float, val minY: Float, val maxX: Float, val maxY: Float,
    var height: Float,
    var kind: BKind,
    val neighborhood: Int,
    val blockCol: Int, val blockRow: Int,
    /** True when the entrance is on the north side (minY), false for south (maxY). */
    val facesNorth: Boolean,
    val streetIndex: Int,
    val number: Int,
    var area: Int,
    val traffic: Float,
    val parking: Int,
    val landlord: String,
    val facade: Int,
    val roof: Int,
    val windowStyle: Int,
    val roofBoxes: List<RoofBox>,
    val cornerAvenue: Boolean,
) {
    var placeId: String? = null
    val cx get() = (minX + maxX) * .5f
    val cy get() = (minY + maxY) * .5f
    val width get() = maxX - minX
    val depth get() = maxY - minY
    val doorX get() = cx
    val doorY get() = if (facesNorth) minY - 1.6f else maxY + 1.6f
    val streetName get() = City.streetName(streetIndex)
    val address get() = "$number $streetName"
    val solid get() = kind != BKind.PARK && kind != BKind.PARKING && kind != BKind.GAS
    val rentable get() = kind == BKind.RETAIL || kind == BKind.RESIDENTIAL
    val sizeClass: String
        get() = when (kind) {
            BKind.RETAIL -> "A" + when { area <= 75 -> 1; area <= 120 -> 2; area <= 200 -> 3; else -> 4 }
            BKind.RESIDENTIAL -> "B" + when { area <= 55 -> 1; area <= 90 -> 2; else -> 3 }
            BKind.OFFICE -> "C1"
            BKind.WAREHOUSE -> "W1"
            else -> "M1"
        }
    val customerLimit get() = max(5, area / 5)
    val baseRent: Double
        get() {
            val n = City.neighborhoods[neighborhood]
            val mult = if (kind == BKind.RESIDENTIAL) 0.8 else (0.75 + traffic)
            return ((area * n.rentPerSqm * mult) / 10.0).roundToInt() * 10.0
        }
    val value: Double get() = (baseRent * 140 / 1000.0).roundToInt() * 1000.0

    /** Interior grid size in 1 m tiles. */
    val gridW: Int get() = (sqrt(area * 1.6).roundToInt()).coerceIn(6, 22)
    val gridD: Int get() = (area / gridW.toFloat()).roundToInt().coerceIn(5, 16)

    fun contains(x: Float, y: Float) = x in minX..maxX && y in minY..maxY
    fun distToDoor(x: Float, y: Float): Float { val dx = x - doorX; val dy = y - doorY; return sqrt(dx * dx + dy * dy) }
}

class Tree(val x: Float, val y: Float, val r: Float, val h: Float, val tone: Int)

class City(val seed: Long) {
    companion object {
        const val AVENUES = 7
        const val STREETS = 16
        const val AV_SPACING = 150f
        const val ST_SPACING = 70f
        const val AV_HALF = 10f
        const val ST_HALF = 6f
        const val SIDEWALK = 4f
        const val WIDTH = (AVENUES - 1) * AV_SPACING
        const val HEIGHT = (STREETS - 1) * ST_SPACING
        const val COLS = AVENUES - 1
        const val ROWS = STREETS - 1

        val avenueNames = listOf("11th Avenue", "10th Avenue", "9th Avenue", "8th Avenue", "7th Avenue", "6th Avenue", "5th Avenue")

        fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return "$n$suffix"
        }

        fun streetName(k: Int) = ordinal(STREETS - k) + " Street"
        fun avenueX(i: Int) = i * AV_SPACING
        fun streetY(k: Int) = k * ST_SPACING

        val neighborhoods = listOf(
            Neighborhood(0, "Garment District", c(0xFF27AE60), .30f, 30.0, 1.0f, 10f, 42f, floatArrayOf(.35f, .45f, .2f, 0f),
                intArrayOf(c(0xFF9C5B4A), c(0xFFA0644F), c(0xFF8D6E63), c(0xFFB08968), c(0xFFD7C4A3), c(0xFF7B5E57))),
            Neighborhood(1, "Hell's Kitchen", c(0xFF2F80ED), .32f, 32.0, 1.0f, 10f, 32f, floatArrayOf(.5f, .4f, .1f, 0f),
                intArrayOf(c(0xFFA35D4E), c(0xFF8E5446), c(0xFFC2A387), c(0xFF9E7B65), c(0xFFB9A48A), c(0xFF6F4E45))),
            Neighborhood(2, "Industry City", c(0xFFF2994A), .14f, 18.0, .8f, 6f, 14f, floatArrayOf(.15f, .2f, 0f, .65f),
                intArrayOf(c(0xFF7F8C8D), c(0xFF95A5A6), c(0xFF8E6E53), c(0xFF6B7B8C), c(0xFFA09080))),
            Neighborhood(3, "Lower Manhattan", c(0xFF56CCF2), .40f, 42.0, 1.25f, 18f, 62f, floatArrayOf(.25f, .3f, .45f, 0f),
                intArrayOf(c(0xFF8FA3B8), c(0xFF6F8399), c(0xFFB0BEC5), c(0xFF9EA7AD), c(0xFFC9C2B6), c(0xFF7D8A99))),
            Neighborhood(4, "Midtown", c(0xFFBB6BD9), .45f, 48.0, 1.3f, 22f, 78f, floatArrayOf(.2f, .38f, .42f, 0f),
                intArrayOf(c(0xFF8FA3B8), c(0xFF5D6D7E), c(0xFFB0BEC5), c(0xFFA7B4C2), c(0xFFCFC6B8), c(0xFF6E7F91))),
            Neighborhood(5, "Murray Hill", c(0xFFEB5757), .28f, 36.0, 1.15f, 12f, 40f, floatArrayOf(.55f, .35f, .1f, 0f),
                intArrayOf(c(0xFFC9B8A0), c(0xFFE0D6C8), c(0xFFA1887F), c(0xFFBCAAA4), c(0xFF9C6B5B), c(0xFFD8CBB6))),
        )

        fun neighborhoodOfBlock(col: Int, row: Int): Int {
            val rg = (row / 5).coerceIn(0, 2)
            val cg = if (col < 3) 0 else 1
            return when (rg) {
                0 -> if (cg == 0) 1 else 5
                1 -> if (cg == 0) 0 else 4
                else -> if (cg == 0) 3 else 2
            }
        }

        private val ROOFS = intArrayOf(c(0xFF6E7378), c(0xFF5F6368), c(0xFF7D8085), c(0xFF8A8D91), c(0xFF55595E))
    }

    val buildings = ArrayList<Building>()
    val trees = ArrayList<Tree>()
    /** Buildings per block, indexed [row * COLS + col]. */
    val blocks: Array<ArrayList<Building>> = Array(COLS * ROWS) { ArrayList() }
    val parkBlocks = HashSet<Int>()
    val maxHeight: Float
    val placeBuildings = HashMap<String, MutableList<Int>>()

    init {
        val rnd = Random(seed)
        // parks
        parkBlocks += 3 * COLS + 2
        parkBlocks += 8 * COLS + 4
        parkBlocks += 12 * COLS + 1
        var id = 0
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            val n = neighborhoods[neighborhoodOfBlock(col, row)]
            val bx0 = col * AV_SPACING + AV_HALF + SIDEWALK
            val bx1 = (col + 1) * AV_SPACING - AV_HALF - SIDEWALK
            val by0 = row * ST_SPACING + ST_HALF + SIDEWALK
            val by1 = (row + 1) * ST_SPACING - ST_HALF - SIDEWALK
            val blockIdx = row * COLS + col
            if (blockIdx in parkBlocks) {
                val b = Building(id++, bx0, by0, bx1, by1, 0f, BKind.PARK, n.id, col, row, false, row + 1, 0, 0, 0f, 0, "City of Hustle",
                    c(0xFF5FA05A), c(0xFF5FA05A), 0, emptyList(), false)
                add(b)
                var tx = bx0 + 6f
                while (tx < bx1 - 4f) {
                    var ty = by0 + 6f
                    while (ty < by1 - 4f) {
                        if (rnd.nextFloat() < .55f) trees += Tree(tx + rnd.nextFloat() * 5f - 2.5f, ty + rnd.nextFloat() * 5f - 2.5f, 2.2f + rnd.nextFloat() * 1.6f, 4f + rnd.nextFloat() * 3f, rnd.nextInt(3))
                        ty += 9f
                    }
                    tx += 9f
                }
                continue
            }
            val mid = (by0 + by1) * .5f
            for (half in 0..1) {
                val y0 = if (half == 0) by0 else mid
                val y1 = if (half == 0) mid else by1
                val facesNorth = half == 0
                val streetIndex = if (facesNorth) row else row + 1
                var x = bx0
                while (x < bx1 - 0.5f) {
                    var w = 12f + rnd.nextFloat() * 14f
                    if (bx1 - (x + w) < 11f) w = bx1 - x
                    val x1 = min(bx1, x + w)
                    val corner = x <= bx0 + 0.5f || x1 >= bx1 - 0.5f
                    val roll = rnd.nextFloat()
                    var kind = pickKind(n, rnd)
                    if (roll < 0.035f && n.id != 4 && n.id != 3) kind = BKind.PARKING
                    val h = when (kind) {
                        BKind.PARKING -> 0f
                        BKind.WAREHOUSE -> 7f + rnd.nextFloat() * 5f
                        BKind.RETAIL -> max(n.minH * .6f, 6f) + rnd.nextFloat() * (n.maxH - n.minH) * .6f
                        else -> n.minH + rnd.nextFloat() * (n.maxH - n.minH) * (if (kind == BKind.OFFICE) 1f else .75f)
                    }
                    val area = when (kind) {
                        BKind.RETAIL -> roundTo5((x1 - x) * (3.5f + rnd.nextFloat() * 5.5f)).coerceIn(40, 380)
                        BKind.RESIDENTIAL -> roundTo5((x1 - x) * (2.6f + rnd.nextFloat() * 4f)).coerceIn(30, 160)
                        BKind.WAREHOUSE -> roundTo5((x1 - x) * (y1 - y0) * .8f)
                        else -> roundTo5((x1 - x) * (y1 - y0))
                    }
                    val trafficIdx = (n.traffic + (if (corner) .1f else 0f) + (rnd.nextFloat() * .18f - .08f)).coerceIn(.05f, .85f)
                    val facade = n.facades[rnd.nextInt(n.facades.size)]
                    val roof = ROOFS[rnd.nextInt(ROOFS.size)]
                    val windowStyle = when {
                        kind == BKind.OFFICE && rnd.nextFloat() < .7f -> 0
                        kind == BKind.WAREHOUSE -> 2
                        else -> 1
                    }
                    val roofBoxes = ArrayList<RoofBox>()
                    if (h > 0f) {
                        val count = rnd.nextInt(0, 3)
                        repeat(count) {
                            val rw = 2f + rnd.nextFloat() * 3f
                            val rd = 2f + rnd.nextFloat() * 3f
                            val rx = x + 1.5f + rnd.nextFloat() * max(0.1f, (x1 - x) - rw - 3f)
                            val ry = y0 + 1.5f + rnd.nextFloat() * max(0.1f, (y1 - y0) - rd - 3f)
                            roofBoxes += RoofBox(rx, ry, rx + rw, ry + rd, 1.2f + rnd.nextFloat() * 1.5f)
                        }
                    }
                    val number = 2 * ((x + x1) * .5f / 9f).toInt() + if (facesNorth) 2 else 1
                    val landlord = "${Catalog.firstNames.random(rnd)} ${Catalog.lastNames.random(rnd)}"
                    add(Building(id++, x, y0, x1, y1, h, kind, n.id, col, row, facesNorth, streetIndex, number, area, trafficIdx,
                        5 + rnd.nextInt(26), landlord, facade, roof, windowStyle, roofBoxes, corner))
                    x = x1
                }
            }
            // sidewalk trees along the block edges
            if (n.id != 2) {
                var tx = bx0 + 5f
                while (tx < bx1 - 3f) {
                    if (rnd.nextFloat() < .45f) trees += Tree(tx, by0 - 2.2f, 1.6f + rnd.nextFloat() * .8f, 4.5f + rnd.nextFloat() * 2f, rnd.nextInt(3))
                    if (rnd.nextFloat() < .45f) trees += Tree(tx, by1 + 2.2f, 1.6f + rnd.nextFloat() * .8f, 4.5f + rnd.nextFloat() * 2f, rnd.nextInt(3))
                    tx += 13f + rnd.nextFloat() * 6f
                }
            }
        }
        assignPlaces(rnd)
        maxHeight = buildings.maxOf { it.height }
    }

    private fun roundTo5(v: Float) = ((v / 5f).roundToInt() * 5)

    private fun pickKind(n: Neighborhood, rnd: Random): BKind {
        val r = rnd.nextFloat() * n.mix.sum()
        var acc = 0f
        val kinds = arrayOf(BKind.RESIDENTIAL, BKind.RETAIL, BKind.OFFICE, BKind.WAREHOUSE)
        for (i in kinds.indices) {
            acc += n.mix[i]
            if (r <= acc) return kinds[i]
        }
        return BKind.RETAIL
    }

    private fun add(b: Building) {
        buildings += b
        blocks[b.blockRow * COLS + b.blockCol] += b
    }

    private fun assignPlaces(rnd: Random) {
        val wanted = listOf(
            "essentials" to 3, "primekitchen" to 1, "lux" to 4, "metro" to 2, "distro" to 0, "imports" to 2,
            "motors" to 5, "school" to 4, "recruit" to 0, "bank" to 3, "buzz" to 4, "hospital" to 5,
            "fuel" to 0, "fuel" to 1, "fuel" to 2, "fuel" to 5,
        )
        val used = HashSet<Int>()
        for ((placeId, hood) in wanted) {
            val place = Catalog.place(placeId)
            val candidates = buildings.filter {
                it.neighborhood == hood && it.id !in used && it.solid && !it.facesNorth && it.width >= 18f &&
                    it.blockRow % 5 in 1..3
            }.ifEmpty { buildings.filter { it.neighborhood == hood && it.id !in used && it.solid && it.width >= 14f } }
            val b = candidates[rnd.nextInt(candidates.size)]
            used += b.id
            b.placeId = placeId
            if (place.kind == PlaceKind.GAS_STATION) {
                b.kind = BKind.GAS
                b.height = 0f
            } else {
                b.kind = BKind.SPECIAL
                b.area = when (place.kind) {
                    PlaceKind.FURNITURE_STORE, PlaceKind.WHOLESALER, PlaceKind.IMPORTER, PlaceKind.CAR_DEALER -> 1000
                    else -> 400
                }
                b.height = when (place.kind) {
                    PlaceKind.FURNITURE_STORE -> 12f + rnd.nextFloat() * 6f
                    PlaceKind.WHOLESALER, PlaceKind.IMPORTER -> 9f + rnd.nextFloat() * 2f
                    PlaceKind.CAR_DEALER -> 8f
                    PlaceKind.BANK -> 48f
                    PlaceKind.SCHOOL -> 30f
                    PlaceKind.HOSPITAL -> 36f
                    else -> 20f + rnd.nextFloat() * 20f
                }
            }
            placeBuildings.getOrPut(placeId) { mutableListOf() } += b.id
        }
    }

    fun building(id: Int): Building? = buildings.getOrNull(id)

    fun placeBuilding(placeId: String): Building? = placeBuildings[placeId]?.firstOrNull()?.let { buildings[it] }

    fun blockAt(x: Float, y: Float): ArrayList<Building>? {
        val col = (x / AV_SPACING).toInt()
        val row = (y / ST_SPACING).toInt()
        if (x < 0 || y < 0 || col >= COLS || row >= ROWS) return null
        return blocks[row * COLS + col]
    }

    fun buildingAt(x: Float, y: Float): Building? = blockAt(x, y)?.firstOrNull { it.contains(x, y) }

    /** Closest solid building whose door is within [radius] meters. */
    fun nearestDoor(x: Float, y: Float, radius: Float): Building? {
        var best: Building? = null
        var bestD = radius
        val col = (x / AV_SPACING).toInt()
        val row = (y / ST_SPACING).toInt()
        for (r in row - 1..row + 1) for (cIdx in col - 1..col + 1) {
            if (r < 0 || cIdx < 0 || r >= ROWS || cIdx >= COLS) continue
            for (b in blocks[r * COLS + cIdx]) {
                if (b.kind == BKind.PARK || b.kind == BKind.PARKING) continue
                val d = if (b.kind == BKind.GAS) dist(x, y, b.cx, b.cy) - 8f else b.distToDoor(x, y)
                if (d < bestD) { bestD = d; best = b }
            }
        }
        return best
    }

    fun neighborhoodAt(x: Float, y: Float): Neighborhood {
        val col = (x / AV_SPACING).toInt().coerceIn(0, COLS - 1)
        val row = (y / ST_SPACING).toInt().coerceIn(0, ROWS - 1)
        return neighborhoods[neighborhoodOfBlock(col, row)]
    }

    /** Human readable location for the bottom HUD label. */
    fun describeLocation(x: Float, y: Float): String {
        val k = (y / ST_SPACING).roundToInt().coerceIn(0, STREETS - 1)
        val i = (x / AV_SPACING).roundToInt().coerceIn(0, AVENUES - 1)
        val dStreet = abs(y - streetY(k))
        val dAve = abs(x - avenueX(i))
        return if (dAve < dStreet) {
            val num = 2 * (y / 9f).toInt() + 1
            "$num ${avenueNames[i]}"
        } else {
            val num = 2 * (x / 9f).toInt() + 1
            "$num ${streetName(k)}"
        }
    }

    /**
     * Resolves a circle against solid buildings and the city bounds.
     * Returns true if a collision happened. Position is written to [out].
     */
    fun collide(x: Float, y: Float, radius: Float, out: FloatArray): Boolean {
        var px = x.coerceIn(-AV_HALF - SIDEWALK + radius, WIDTH + AV_HALF + SIDEWALK - radius)
        var py = y.coerceIn(-ST_HALF - SIDEWALK + radius, HEIGHT + ST_HALF + SIDEWALK - radius)
        var hit = px != x || py != y
        val col = (px / AV_SPACING).toInt()
        val row = (py / ST_SPACING).toInt()
        for (r in row - 1..row + 1) for (cIdx in col - 1..col + 1) {
            if (r < 0 || cIdx < 0 || r >= ROWS || cIdx >= COLS) continue
            for (b in blocks[r * COLS + cIdx]) {
                if (!b.solid) continue
                val nx = px.coerceIn(b.minX, b.maxX)
                val ny = py.coerceIn(b.minY, b.maxY)
                val dx = px - nx
                val dy = py - ny
                val d2 = dx * dx + dy * dy
                if (d2 < radius * radius) {
                    hit = true
                    if (d2 > 1e-6f) {
                        val d = sqrt(d2)
                        px = nx + dx / d * radius
                        py = ny + dy / d * radius
                    } else {
                        // inside: push out through the nearest edge
                        val l = px - b.minX; val rr = b.maxX - px; val t = py - b.minY; val bb = b.maxY - py
                        when (minOf(l, rr, t, bb)) {
                            l -> px = b.minX - radius
                            rr -> px = b.maxX + radius
                            t -> py = b.minY - radius
                            else -> py = b.maxY + radius
                        }
                    }
                }
            }
        }
        out[0] = px; out[1] = py
        return hit
    }
}

fun dist(x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0; val dy = y1 - y0
    return sqrt(dx * dx + dy * dy)
}

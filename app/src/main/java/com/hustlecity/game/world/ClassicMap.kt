package com.hustlecity.game.world

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.data.c
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The original Hustle City: a Manhattan-style grid of seven avenues and sixteen streets. Kept exactly as it was
 * (same buildings, same ids) so older saves load into the city they were played in.
 */
object ClassicMap : MapDef("classic", "Hustle City", "The original grid: seven avenues, sixteen streets and six neighborhoods.") {
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

    private val avenueNames = listOf("11th Avenue", "10th Avenue", "9th Avenue", "8th Avenue", "7th Avenue", "6th Avenue", "5th Avenue")

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    private fun streetName(k: Int) = ordinal(STREETS - k) + " Street"

    val neighborhoods = listOf(
        Neighborhood(0, "Garment District", c(0xFF27AE60), .30f, 30.0, 1.0f, 10f, 42f, floatArrayOf(.35f, .45f, .2f, 0f),
            intArrayOf(c(0xFF9C5B4A), c(0xFFA0644F), c(0xFF8D6E63), c(0xFFB08968), c(0xFFD7C4A3), c(0xFF7B5E57)), Look.NY_TOWNHOUSE),
        Neighborhood(1, "Hell's Kitchen", c(0xFF2F80ED), .32f, 32.0, 1.0f, 10f, 32f, floatArrayOf(.5f, .4f, .1f, 0f),
            intArrayOf(c(0xFFA35D4E), c(0xFF8E5446), c(0xFFC2A387), c(0xFF9E7B65), c(0xFFB9A48A), c(0xFF6F4E45)), Look.NY_TOWNHOUSE),
        Neighborhood(2, "Industry City", c(0xFFF2994A), .14f, 18.0, .8f, 6f, 14f, floatArrayOf(.15f, .2f, 0f, .65f),
            intArrayOf(c(0xFF7F8C8D), c(0xFF95A5A6), c(0xFF8E6E53), c(0xFF6B7B8C), c(0xFFA09080)), Look.INDUSTRIAL),
        Neighborhood(3, "Lower Manhattan", c(0xFF56CCF2), .40f, 42.0, 1.25f, 18f, 62f, floatArrayOf(.25f, .3f, .45f, 0f),
            intArrayOf(c(0xFF8FA3B8), c(0xFF6F8399), c(0xFFB0BEC5), c(0xFF9EA7AD), c(0xFFC9C2B6), c(0xFF7D8A99)), Look.NY_DOWNTOWN),
        Neighborhood(4, "Midtown", c(0xFFBB6BD9), .45f, 48.0, 1.3f, 22f, 78f, floatArrayOf(.2f, .38f, .42f, 0f),
            intArrayOf(c(0xFF8FA3B8), c(0xFF5D6D7E), c(0xFFB0BEC5), c(0xFFA7B4C2), c(0xFFCFC6B8), c(0xFF6E7F91)), Look.NY_DOWNTOWN),
        Neighborhood(5, "Murray Hill", c(0xFFEB5757), .28f, 36.0, 1.15f, 12f, 40f, floatArrayOf(.55f, .35f, .1f, 0f),
            intArrayOf(c(0xFFC9B8A0), c(0xFFE0D6C8), c(0xFFA1887F), c(0xFFBCAAA4), c(0xFF9C6B5B), c(0xFFD8CBB6)), Look.NY_TOWNHOUSE),
    )

    private fun neighborhoodOfBlock(col: Int, row: Int): Int {
        val rg = (row / 5).coerceIn(0, 2)
        val cg = if (col < 3) 0 else 1
        return when (rg) {
            0 -> if (cg == 0) 1 else 5
            1 -> if (cg == 0) 0 else 4
            else -> if (cg == 0) 3 else 2
        }
    }

    override fun hoodAt(x: Float, y: Float): Int =
        neighborhoodOfBlock((x / AV_SPACING).toInt().coerceIn(0, COLS - 1), (y / ST_SPACING).toInt().coerceIn(0, ROWS - 1))

    override val placeHoods = listOf(
        "essentials" to 3, "primekitchen" to 1, "lux" to 4, "metro" to 2, "distro" to 0, "imports" to 2,
        "motors" to 5, "school" to 4, "recruit" to 0, "bank" to 3, "buzz" to 4, "hospital" to 5,
        "fuel" to 0, "fuel" to 1, "fuel" to 2, "fuel" to 5,
        // added later: keep at the end so earlier picks stay the same for existing saves
        "officeplus" to 2,
    )

    /** A lot as the original generator made it: axis-aligned, entrance north or south. */
    private class Raw(
        val minX: Float, val minY: Float, val maxX: Float, val maxY: Float, var height: Float, var kind: BKind, val hood: Int,
        val blockRow: Int, val facesNorth: Boolean, val streetIndex: Int, val number: Int, var area: Int, val traffic: Float,
        val parking: Int, val landlord: String, val facade: Int, val roof: Int, val windowStyle: Int, val roofBoxes: List<RoofBox>,
        val corner: Boolean,
    ) {
        var placeId: String? = null
        val width get() = maxX - minX
        val solid get() = kind != BKind.PARK && kind != BKind.PARKING && kind != BKind.GAS
    }

    private val ROOFS = intArrayOf(c(0xFF6E7378), c(0xFF5F6368), c(0xFF7D8085), c(0xFF8A8D91), c(0xFF55595E))

    override fun generate(seed: Long): CityLayout {
        val rnd = Random(seed)
        val raws = ArrayList<Raw>()
        val trees = ArrayList<Tree>()
        val parkBlocks = setOf(3 * COLS + 2, 8 * COLS + 4, 12 * COLS + 1)
        val parks = ArrayList<Park>()
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            val n = neighborhoods[neighborhoodOfBlock(col, row)]
            val bx0 = col * AV_SPACING + AV_HALF + SIDEWALK
            val bx1 = (col + 1) * AV_SPACING - AV_HALF - SIDEWALK
            val by0 = row * ST_SPACING + ST_HALF + SIDEWALK
            val by1 = (row + 1) * ST_SPACING - ST_HALF - SIDEWALK
            val blockIdx = row * COLS + col
            if (blockIdx in parkBlocks) {
                raws += Raw(bx0, by0, bx1, by1, 0f, BKind.PARK, n.id, row, false, row + 1, 0, 0, 0f, 0, "City of Hustle",
                    c(0xFF5FA05A), c(0xFF5FA05A), 0, emptyList(), false)
                parks += Park(listOf("Bryant Park", "Madison Square", "Washington Square").getOrElse(parks.size) { "City Park" }, floatArrayOf(bx0, by0, bx1, by0, bx1, by1, bx0, by1))
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
                    raws += Raw(x, y0, x1, y1, h, kind, n.id, row, facesNorth, streetIndex, number, area, trafficIdx,
                        5 + rnd.nextInt(26), landlord, facade, roof, windowStyle, roofBoxes, corner)
                    x = x1
                }
            }
            if (n.id != 2) {
                var tx = bx0 + 5f
                while (tx < bx1 - 3f) {
                    if (rnd.nextFloat() < .45f) trees += Tree(tx, by0 - 2.2f, 1.6f + rnd.nextFloat() * .8f, 4.5f + rnd.nextFloat() * 2f, rnd.nextInt(3))
                    if (rnd.nextFloat() < .45f) trees += Tree(tx, by1 + 2.2f, 1.6f + rnd.nextFloat() * .8f, 4.5f + rnd.nextFloat() * 2f, rnd.nextInt(3))
                    tx += 13f + rnd.nextFloat() * 6f
                }
            }
        }
        assignPlaces(raws, rnd)
        // the lattice of avenues and streets
        val rb = RoadBuilder()
        for (i in 0 until AVENUES) rb.add(i * AV_SPACING, 0f, i * AV_SPACING, HEIGHT, RoadClass.AVENUE, avenueNames[i])
        for (k in 0 until STREETS) rb.add(0f, k * ST_SPACING, WIDTH, k * ST_SPACING, RoadClass.STREET, streetName(k))
        val roads = rb.build({ _, _ -> false })
        numberRoads(roads)
        val hit = RoadHit()
        val buildings = raws.mapIndexed { id, r ->
            val name = streetName(r.streetIndex)
            val doorY = if (r.facesNorth) r.minY - 1.6f else r.maxY + 1.6f
            roads.nearest((r.minX + r.maxX) * .5f, doorY, 30f, hit) { it.name == name }
            val front = if (r.kind == BKind.PARK) -1 else hit.edge
            val b = if (r.facesNorth) {
                Building(id, r.maxX, r.maxY, Math.PI.toFloat(), r.maxX - r.minX, r.maxY - r.minY, r.height, r.kind, neighborhoods[r.hood], name, front,
                    r.number, r.area, r.traffic, r.parking, r.landlord, r.facade, r.roof, r.windowStyle,
                    r.roofBoxes.map { RoofBox(r.maxX - it.x1, r.maxY - it.y1, r.maxX - it.x0, r.maxY - it.y0, it.h) }, r.corner)
            } else {
                Building(id, r.minX, r.minY, 0f, r.maxX - r.minX, r.maxY - r.minY, r.height, r.kind, neighborhoods[r.hood], name, front,
                    r.number, r.area, r.traffic, r.parking, r.landlord, r.facade, r.roof, r.windowStyle,
                    r.roofBoxes.map { RoofBox(it.x0 - r.minX, it.y0 - r.minY, it.x1 - r.minX, it.y1 - r.minY, it.h) }, r.corner)
            }
            b.placeId = r.placeId
            b
        }
        val bounds = floatArrayOf(-AV_HALF - SIDEWALK, -ST_HALF - SIDEWALK, WIDTH + AV_HALF + SIDEWALK, HEIGHT + ST_HALF + SIDEWALK)
        return CityLayout(roads, buildings, trees, Shore(emptyList(), emptyList(), emptyList(), emptyList()), parks, emptyList(), neighborhoods, bounds)
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

    private fun assignPlaces(raws: List<Raw>, rnd: Random) {
        val used = HashSet<Int>()
        for ((placeId, hood) in placeHoods) {
            val place = Catalog.place(placeId)
            val candidates = raws.indices.filter {
                val b = raws[it]
                b.hood == hood && it !in used && b.solid && !b.facesNorth && b.width >= 18f && b.blockRow % 5 in 1..3
            }.ifEmpty { raws.indices.filter { raws[it].hood == hood && it !in used && raws[it].solid && raws[it].width >= 14f } }
            val i = candidates[rnd.nextInt(candidates.size)]
            val b = raws[i]
            used += i
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
        }
    }
}

/** House numbers run along each named road from its first piece. */
internal fun numberRoads(g: RoadGraph) {
    val byName = g.edges.groupBy { it.name }
    for ((_, list) in byName) {
        val dx = list.last().bx - list.first().ax; val dy = list.last().by - list.first().ay
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / l; val uy = dy / l
        val ox = list.minOf { min(it.ax * ux + it.ay * uy, it.bx * ux + it.by * uy) }
        for (e in list) e.numberOrigin = max(0f, (if (e.ux * ux + e.uy * uy >= 0) e.ax * ux + e.ay * uy else e.bx * ux + e.by * uy) - ox)
    }
}

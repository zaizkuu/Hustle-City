package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class BKind(val label: String) {
    RESIDENTIAL("Residential"), RETAIL("Retail"), OFFICE("Office"), WAREHOUSE("Warehouse"),
    SPECIAL("Special"), PARK("Park"), PARKING("Parking Lot"), GAS("Gas Station")
}

/** The architecture a neighborhood is built in; the building painter picks styles from it. */
enum class Look {
    /** Brick walk-ups, brownstones and the odd deco tower. */
    NY_TOWNHOUSE,
    /** Offices: glass, stone and deco towers. */
    NY_DOWNTOWN,
    /** Sheds, warehouses and workshops. */
    INDUSTRIAL,
    /** Spanish colonial stone, balconies and red tile (Intramuros, Binondo). */
    COLONIAL,
    /** Low-rise concrete with corrugated roofs, painted signs and awnings. */
    TROPICAL,
    /** Narrow, tall mixed-use buildings stacked with vertical signs. */
    DENSE_ASIA,
    /** Glass business towers. */
    CBD,
    /** Tiled-roof courtyard houses and low shops. */
    HANOK,
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
    val look: Look = Look.NY_TOWNHOUSE,
    /** Typical lot frontage and depth in meters (min, max). */
    val lotW: FloatArray = floatArrayOf(12f, 26f),
    val lotD: FloatArray = floatArrayOf(16f, 30f),
    /** Chance of a gap (alley) after each building, and of a sidewalk tree every few meters. */
    val alleys: Float = .08f,
    val greenery: Float = .45f,
)

class RoofBox(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val h: Float)

/**
 * A building on its own lot. It has a local frame: x runs along the street front from 0 to [w], y runs from the
 * back (0) to the front (= [d], facing the street), z is up. [ox], [oy] is the back-left corner in the world and
 * [angle] the direction of local x, so buildings can face any way.
 */
class Building(
    val id: Int,
    val ox: Float, val oy: Float,
    val angle: Float,
    val w: Float, val d: Float,
    var height: Float,
    var kind: BKind,
    val hood: Neighborhood,
    /** Name of the road the entrance faces. */
    val streetName: String,
    /** Road the entrance faces, -1 if none. */
    val frontEdge: Int,
    val number: Int,
    var area: Int,
    val traffic: Float,
    val parking: Int,
    val landlord: String,
    val facade: Int,
    val roof: Int,
    val windowStyle: Int,
    /** Rooftop structures, in local coordinates. */
    val roofBoxes: List<RoofBox>,
    val cornerAvenue: Boolean,
    private val addressText: String? = null,
) {
    var placeId: String? = null
    val cs = cos(angle)
    val sn = sin(angle)
    val neighborhood get() = hood.id

    fun wx(lx: Float, ly: Float) = ox + lx * cs - ly * sn
    fun wy(lx: Float, ly: Float) = oy + lx * sn + ly * cs
    fun lx(x: Float, y: Float) = (x - ox) * cs + (y - oy) * sn
    fun ly(x: Float, y: Float) = -(x - ox) * sn + (y - oy) * cs

    val minX = minOf(wx(0f, 0f), wx(w, 0f), wx(w, d), wx(0f, d))
    val maxX = maxOf(wx(0f, 0f), wx(w, 0f), wx(w, d), wx(0f, d))
    val minY = minOf(wy(0f, 0f), wy(w, 0f), wy(w, d), wy(0f, d))
    val maxY = maxOf(wy(0f, 0f), wy(w, 0f), wy(w, d), wy(0f, d))
    val cx = wx(w * .5f, d * .5f)
    val cy = wy(w * .5f, d * .5f)
    val width get() = w
    val depth get() = d
    /** Outward direction of the front (toward the street). */
    val frontX get() = -sn
    val frontY get() = cs
    val doorX = wx(w * .5f, d + 1.6f)
    val doorY = wy(w * .5f, d + 1.6f)
    val address get() = addressText ?: "$number $streetName"
    val solid get() = kind != BKind.PARK && kind != BKind.PARKING && kind != BKind.GAS
    val rentable get() = kind == BKind.RETAIL || kind == BKind.RESIDENTIAL || kind == BKind.OFFICE || kind == BKind.WAREHOUSE
    val sizeClass: String
        get() = when (kind) {
            BKind.RETAIL -> "A" + when { area <= 75 -> 1; area <= 120 -> 2; area <= 200 -> 3; else -> 4 }
            BKind.RESIDENTIAL -> "B" + when { area <= 55 -> 1; area <= 90 -> 2; else -> 3 }
            BKind.OFFICE -> "C" + when { area <= 300 -> 1; area <= 500 -> 2; else -> 3 }
            BKind.WAREHOUSE -> "W" + when { area <= 300 -> 1; area <= 450 -> 2; else -> 3 }
            else -> "M1"
        }
    val customerLimit get() = max(5, area / 5)
    val baseRent: Double
        get() {
            val mult = when (kind) {
                BKind.RESIDENTIAL -> 0.8
                BKind.OFFICE, BKind.WAREHOUSE -> 0.45
                else -> 0.75 + traffic
            }
            return ((area * hood.rentPerSqm * mult) / 10.0).roundToInt() * 10.0
        }
    val value: Double get() = (baseRent * 140 / 1000.0).roundToInt() * 1000.0

    /** Interior grid size in 1 m tiles. */
    val gridW: Int get() = (sqrt(area * 1.6).roundToInt()).coerceIn(6, 22)
    val gridD: Int get() = (area / gridW.toFloat()).roundToInt().coerceIn(5, 16)

    fun contains(x: Float, y: Float): Boolean { val lx = lx(x, y); val ly = ly(x, y); return lx in 0f..w && ly in 0f..d }
    fun distToDoor(x: Float, y: Float): Float { val dx = x - doorX; val dy = y - doorY; return sqrt(dx * dx + dy * dy) }
    fun obb() = OBB(cx, cy, cs, sn, w * .5f, d * .5f)
}

/** A street or park tree. [species]: -1 let the street decide, 0 broadleaf, 1 palm, 2 conifer, 3 cherry, 4 ginkgo. */
class Tree(val x: Float, val y: Float, val r: Float, val h: Float, val tone: Int, val species: Int = -1)

/** Green space, plaza or palace grounds, drawn on the ground. Roads don't cut through unless [roads] is set. */
class Park(val name: String, val poly: FloatArray, val kind: Int = GREEN, val roads: Boolean = false) {
    /** Centroid of the outline's vertices. */
    val centre: Pair<Float, Float> by lazy {
        var x = 0f; var y = 0f
        val n = poly.size / 2
        for (i in 0 until n) { x += poly[i * 2]; y += poly[i * 2 + 1] }
        (x / n) to (y / n)
    }

    /** Two footpaths crossing at the centre, along and across the park's longest side, clipped to the park. */
    val paths: List<FloatArray> by lazy {
        if (kind == PLAZA) return@lazy emptyList()
        var best = 0f; var ux = 1f; var uy = 0f
        val n = poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = poly[j * 2] - poly[i * 2]; val dy = poly[j * 2 + 1] - poly[i * 2 + 1]
            val l = kotlin.math.sqrt(dx * dx + dy * dy)
            if (l > best) { best = l; ux = dx / l; uy = dy / l }
        }
        val (cx, cy) = centre
        val r = 2000f
        Geo.clip(cx - ux * r, cy - uy * r, cx + ux * r, cy + uy * r, poly) + Geo.clip(cx + uy * r, cy - ux * r, cx - uy * r, cy + ux * r, poly)
    }

    companion object {
        const val GREEN = 0
        const val PLAZA = 1
        /** Walled grounds with gravel and old trees (palaces, forts). */
        const val GROUNDS = 2
    }
}

/** An elevated railway (or monorail) running over the streets on pillars. */
class Rail(val name: String, val pts: FloatArray, val color: Int, val loop: Boolean)

/**
 * A one-off structure drawn by the renderer (towers, gates, billboards…). It has a local frame like a building: x runs
 * along its face, y from front (-y) to back (+y), turned by [angle]; [size] scales it. [style] picks a variant.
 */
class Landmark(val kind: Int, val x: Float, val y: Float, val angle: Float = 0f, val size: Float = 1f, val name: String = "", val style: Int = 0) {
    val cs = cos(angle)
    val sn = sin(angle)
    fun wx(lx: Float, ly: Float) = x + lx * cs - ly * sn
    fun wy(lx: Float, ly: Float) = y + lx * sn + ly * cs

    /** Ground it stands on in local meters (x0, y0, x1, y1, before [size]); null when it only dresses the buildings around. */
    val extent: FloatArray? get() = when (kind) {
        TOWER -> if (style == LATTICE) floatArrayOf(-24f, -24f, 24f, 24f) else floatArrayOf(-34f, -34f, 34f, 34f)
        TORII -> floatArrayOf(-10f, -5f, 10f, 46f)
        MONUMENT -> floatArrayOf(-12f, -12f, 12f, 12f)
        PALACE_GATE -> floatArrayOf(-46f, -10f, 46f, 10f)
        CHURCH -> floatArrayOf(-14.5f, -34f, 14.5f, 27f)
        WHEEL -> floatArrayOf(-33f, -11f, 33f, 11f)
        else -> null
    }

    /** [extent] as a world polygon: no buildings, trees or street furniture inside. */
    val footprint: FloatArray? by lazy {
        val ex = extent ?: return@lazy null
        val k = size
        floatArrayOf(wx(ex[0] * k, ex[1] * k), wy(ex[0] * k, ex[1] * k), wx(ex[2] * k, ex[1] * k), wy(ex[2] * k, ex[1] * k),
            wx(ex[2] * k, ex[3] * k), wy(ex[2] * k, ex[3] * k), wx(ex[0] * k, ex[3] * k), wy(ex[0] * k, ex[3] * k))
    }

    /** Roads are cleared out of the footprint (a church square, the ground under a tower or a wheel). */
    val clearsRoads get() = kind == TOWER || kind == CHURCH || kind == WHEEL || kind == PALACE_GATE

    companion object {
        /** [TOWER] styles: a concrete tower on a wooded hill, or a steel lattice. */
        const val CONCRETE = 0
        const val LATTICE = 1
        /** [CHURCH] styles: dome with a campanile beside the front, dome with a tower on the front, octagonal pagoda tower. */
        const val CATHEDRAL = 0
        const val BASILICA = 1
        const val PAGODA_TOWER = 2
        /** [PALACE_GATE] styles: a Korean gate pavilion on an arched stone platform, or a white Japanese yagura gate. */
        const val HANOK_GATE = 0
        const val YAGURA_GATE = 1
        /** [BILLBOARDS] styles: screens on every face, a scramble crossing, a lit arch over the lane, blade signs. */
        const val SCREENS = 0
        const val SCRAMBLE = 1
        const val ARCADE = 2
        const val SIGNS = 3

        /** A slender broadcast tower on a hill (N Seoul Tower). */
        const val TOWER = 0
        /** A shrine gate; the approach runs on to +y. */
        const val TORII = 1
        /** A square of giant lit billboards (Times Square, Shibuya crossing). */
        const val BILLBOARDS = 2
        /** A stone monument or obelisk. */
        const val MONUMENT = 3
        /** A tiled palace gate in the grounds' wall; local +y points into the grounds. */
        const val PALACE_GATE = 4
        /** A cathedral with a dome and bell tower, its front and square toward -y. */
        const val CHURCH = 5
        /** A Ferris wheel. */
        const val WHEEL = 6
    }
}

/** Where the ground is: the sea or rivers ([water]), land masses when the map is mostly water ([land]), and islands. */
class Shore(val water: List<FloatArray>, val land: List<FloatArray>, val islands: List<FloatArray>, val canals: List<FloatArray>) {
    private val waterBox = water.map { Geo.bounds(it) }
    private val landBox = land.map { Geo.bounds(it) }
    private val islandBox = islands.map { Geo.bounds(it) }
    private val canalBox = canals.map { Geo.bounds(it) }

    private fun inAny(x: Float, y: Float, polys: List<FloatArray>, boxes: List<FloatArray>): Boolean {
        for (i in polys.indices) {
            val b = boxes[i]
            if (x < b[0] || x > b[2] || y < b[1] || y > b[3]) continue
            if (Geo.inside(x, y, polys[i])) return true
        }
        return false
    }

    /** Water that blocks roads (bridges excepted). */
    fun wet(x: Float, y: Float): Boolean =
        (inAny(x, y, water, waterBox) || (land.isNotEmpty() && !inAny(x, y, land, landBox))) && !inAny(x, y, islands, islandBox)

    /** Any water, canals included (no buildings there). */
    fun anyWater(x: Float, y: Float) = wet(x, y) || inAny(x, y, canals, canalBox)
}

/** A city, generated from one of the [Maps] and a seed. Same seed and map give the same city. */
class City(val seed: Long, val mapId: String = Maps.CLASSIC) {
    val map: MapDef = Maps.byId(mapId)
    val roads: RoadGraph
    val buildings = ArrayList<Building>()
    val trees = ArrayList<Tree>()
    val water: List<FloatArray>
    val shore: Shore
    val parks: List<Park>
    val rails: List<Rail>
    val landmarks: List<Landmark>
    /** Old city walls (Intramuros), as polylines. */
    val walls: List<FloatArray>
    val neighborhoods: List<Neighborhood>
    /** World area the player can reach: x0, y0, x1, y1. */
    val bounds: FloatArray
    val maxHeight: Float
    val placeBuildings = HashMap<String, MutableList<Int>>()
    /** Cars keep left (Tokyo) instead of right. */
    val leftHand: Boolean

    private val cell = 40f
    private val gx0: Float
    private val gy0: Float
    private val gCols: Int
    private val gRows: Int
    private val grid: Array<IntArray>

    /** Ground polygons triangulated once, for drawing: x, y triples. */
    private val triCache = java.util.IdentityHashMap<FloatArray, FloatArray>()
    fun triangles(poly: FloatArray): FloatArray = triCache.getOrPut(poly) { Geo.triangulate(poly) }
    /** Bounding box of a polygon, cached. */
    private val boxCache = java.util.IdentityHashMap<FloatArray, FloatArray>()
    fun boxOf(poly: FloatArray): FloatArray = boxCache.getOrPut(poly) { Geo.bounds(poly) }

        val width get() = bounds[2] - bounds[0]
    val height get() = bounds[3] - bounds[1]

    init {
        val layout = map.generate(seed)
        roads = layout.roads
        buildings += layout.buildings
        trees += layout.trees
        water = layout.shore.water
        shore = layout.shore
        parks = layout.parks
        rails = layout.rails
        landmarks = layout.landmarks
        walls = layout.walls
        neighborhoods = layout.neighborhoods
        bounds = layout.bounds
        leftHand = map.leftHand
        for (b in buildings) b.placeId?.let { placeBuildings.getOrPut(it) { mutableListOf() } += b.id }
        for (l in landmarks) if (l.kind == Landmark.BILLBOARDS && l.style == Landmark.SCRAMBLE) scrambleNode(l)?.scramble = true
        maxHeight = buildings.maxOfOrNull { it.height } ?: 10f
        gx0 = bounds[0] - 60f; gy0 = bounds[1] - 60f
        gCols = ((bounds[2] + 60f - gx0) / cell).toInt() + 1
        gRows = ((bounds[3] + 60f - gy0) / cell).toInt() + 1
        val lists = Array(gCols * gRows) { ArrayList<Int>() }
        for (b in buildings) {
            val c0 = ((b.minX - gx0) / cell).toInt().coerceIn(0, gCols - 1); val c1 = ((b.maxX - gx0) / cell).toInt().coerceIn(0, gCols - 1)
            val r0 = ((b.minY - gy0) / cell).toInt().coerceIn(0, gRows - 1); val r1 = ((b.maxY - gy0) / cell).toInt().coerceIn(0, gRows - 1)
            for (r in r0..r1) for (cc in c0..c1) lists[r * gCols + cc] += b.id
        }
        grid = Array(lists.size) { lists[it].toIntArray() }
    }

    fun building(id: Int): Building? = buildings.getOrNull(id)

    /** The big junction a scramble-crossing landmark stands on, if any. */
    fun scrambleNode(l: Landmark): RoadNode? =
        roads.nodes.filter { it.degree >= 4 && kotlin.math.hypot(it.x - l.x, it.y - l.y) < 18f }.minByOrNull { kotlin.math.hypot(it.x - l.x, it.y - l.y) }

    fun placeBuilding(placeId: String): Building? = placeBuildings[placeId]?.firstOrNull()?.let { buildings[it] }

    /** Calls [f] for every building whose bounding box touches the rectangle (a building may come up more than once). */
    inline fun forBuildingsIn(x0: Float, y0: Float, x1: Float, y1: Float, f: (Building) -> Unit) {
        val ids = idsIn(x0, y0, x1, y1)
        for (i in 0 until idCount) f(buildings[ids[i]])
    }

    private var idBuf = IntArray(256)
    var idCount = 0
        private set
    private val stamp = IntArray(4096 * 4)
    private var stampGen = 1
    private val seen = HashMap<Int, Int>()

    /** Building ids (each once) whose bounding boxes touch the rectangle, in a shared buffer of [idCount] entries. */
    fun idsIn(x0: Float, y0: Float, x1: Float, y1: Float): IntArray {
        idCount = 0
        stampGen++
        val c0 = floor((x0 - gx0) / cell).toInt().coerceIn(0, gCols - 1); val c1 = floor((x1 - gx0) / cell).toInt().coerceIn(0, gCols - 1)
        val r0 = floor((y0 - gy0) / cell).toInt().coerceIn(0, gRows - 1); val r1 = floor((y1 - gy0) / cell).toInt().coerceIn(0, gRows - 1)
        for (r in r0..r1) for (cc in c0..c1) for (id in grid[r * gCols + cc]) {
            if (id < stamp.size) { if (stamp[id] == stampGen) continue; stamp[id] = stampGen }
            else { if (seen[id] == stampGen) continue; seen[id] = stampGen }
            val b = buildings[id]
            if (b.maxX < x0 || b.minX > x1 || b.maxY < y0 || b.minY > y1) continue
            if (idCount >= idBuf.size) idBuf = idBuf.copyOf(idBuf.size * 2)
            idBuf[idCount++] = id
        }
        return idBuf
    }

    fun buildingAt(x: Float, y: Float): Building? {
        var hit: Building? = null
        forBuildingsIn(x, y, x, y) { if (hit == null && it.contains(x, y)) hit = it }
        return hit
    }

    /** Closest solid building whose door is within [radius] meters. */
    fun nearestDoor(x: Float, y: Float, radius: Float): Building? {
        var best: Building? = null
        var bestD = radius
        forBuildingsIn(x - radius - 40f, y - radius - 40f, x + radius + 40f, y + radius + 40f) { b ->
            if (b.kind == BKind.PARK || b.kind == BKind.PARKING) return@forBuildingsIn
            val d = if (b.kind == BKind.GAS) dist(x, y, b.cx, b.cy) - 8f else b.distToDoor(x, y)
            if (d < bestD) { bestD = d; best = b }
        }
        return best
    }

    fun neighborhoodAt(x: Float, y: Float): Neighborhood = neighborhoods[map.hoodAt(x, y)]

    /** Inside a landmark's grounds or on the old city walls: street furniture stays out. */
    fun reserved(x: Float, y: Float): Boolean {
        for (l in landmarks) { val f = l.footprint ?: continue; if (Geo.inside(x, y, f)) return true }
        for (w in walls) for (i in 0 until w.size / 2 - 1) if (Geo.distToSeg(x, y, w[i * 2], w[i * 2 + 1], w[i * 2 + 2], w[i * 2 + 3]) < WALL_BAND) return true
        return false
    }

    private val hit = RoadHit()

    /** Human readable location for the bottom HUD label: the nearest road and a house number. */
    fun describeLocation(x: Float, y: Float): String {
        if (!roads.nearest(x, y, 80f, hit)) return neighborhoodAt(x, y).name
        val e = roads.edges[hit.edge]
        val num = houseNumber(e, hit.s, hit.side)
        return map.address(num, e.name, neighborhoodAt(x, y).name)
    }

    /** House number of a point [s] meters along [e], on the given side (+1 left of a→b, -1 right). */
    fun houseNumber(e: RoadEdge, s: Float, side: Int): Int {
        val along = e.numberOrigin + s
        return 2 * ((along / 9f).toInt()) + if (side > 0) 2 else 1
    }

    /**
     * Resolves a circle against solid buildings and the city bounds.
     * Returns true if a collision happened. Position is written to [out].
     */
    fun collide(x: Float, y: Float, radius: Float, out: FloatArray): Boolean {
        var px = x.coerceIn(bounds[0] + radius, bounds[2] - radius)
        var py = y.coerceIn(bounds[1] + radius, bounds[3] - radius)
        var hitAny = px != x || py != y
        forBuildingsIn(px - radius, py - radius, px + radius, py + radius) { b ->
            if (!b.solid) return@forBuildingsIn
            // in the building's frame it's an axis-aligned box
            val lx = b.lx(px, py); val ly = b.ly(px, py)
            val nx = lx.coerceIn(0f, b.w); val ny = ly.coerceIn(0f, b.d)
            val dx = lx - nx; val dy = ly - ny
            val d2 = dx * dx + dy * dy
            if (d2 < radius * radius) {
                hitAny = true
                var ox: Float; var oy: Float
                if (d2 > 1e-6f) {
                    val dd = sqrt(d2)
                    ox = nx + dx / dd * radius; oy = ny + dy / dd * radius
                } else {
                    // inside: push out through the nearest side
                    val l = lx; val r = b.w - lx; val t = ly; val bb = b.d - ly
                    ox = lx; oy = ly
                    when (minOf(l, r, t, bb)) {
                        l -> ox = -radius
                        r -> ox = b.w + radius
                        t -> oy = -radius
                        else -> oy = b.d + radius
                    }
                }
                px = b.wx(ox, oy); py = b.wy(ox, oy)
            }
        }
        // the water's edge
        if (shore.anyWater(px, py) && !onBridge(px, py)) {
            out[0] = lastDryX; out[1] = lastDryY
            return true
        }
        lastDryX = px; lastDryY = py
        out[0] = px; out[1] = py
        return hitAny
    }

    private var lastDryX = (bounds[0] + bounds[2]) * .5f
    private var lastDryY = (bounds[1] + bounds[3]) * .5f

    /** On a bridge deck (bridges cross the water). */
    fun onBridge(x: Float, y: Float): Boolean {
        var on = false
        roads.near(x, y, 16f) { id ->
            val e = roads.edges[id]
            if (!on && e.overWater && Geo.distToSeg(x, y, e.ax, e.ay, e.bx, e.by) <= e.cls.outer) on = true
        }
        return on
    }

    fun inWater(x: Float, y: Float): Boolean = shore.anyWater(x, y) && !onBridge(x, y)

    /** Heading (0 = north) of a car parked at the curb on [side] of [e] (+1 left of a→b), driving with the traffic. */
    fun curbHeading(e: RoadEdge, side: Int): Float {
        val withAB = (side < 0) != leftHand
        return if (withAB) kotlin.math.atan2(e.ux, -e.uy) else kotlin.math.atan2(-e.ux, e.uy)
    }

    /** Side of [e] (+1 left of a→b, -1 right) a point lies on. */
    fun sideOf(e: RoadEdge, x: Float, y: Float) = if ((x - e.ax) * e.uy - (y - e.ay) * e.ux >= 0f) 1 else -1

        /** A point on the curb lane in front of [b], [along] meters from the door along the street. */
    fun curbSpot(b: Building, along: Float): Pair<Float, Float> {
        val e = roads.edges.getOrNull(b.frontEdge)
        if (e == null) return b.doorX to b.doorY
        // project the door onto the road, step along it and sit in the outer lane on the building's side
        val s = ((b.doorX - e.ax) * e.ux + (b.doorY - e.ay) * e.uy).coerceIn(0f, e.len)
        val side = if ((b.doorX - e.ax) * e.uy - (b.doorY - e.ay) * e.ux >= 0f) 1f else -1f
        val t = (s + along).coerceIn(min(e.clearA + 3f, e.len * .5f), max(e.len - e.clearB - 3f, e.len * .5f))
        val off = e.cls.halfWidth - 1.3f
        return (e.ax + e.ux * t + e.uy * off * side) to (e.ay + e.uy * t - e.ux * off * side)
    }

    companion object {
        /** City walls are 6 m thick on their centre line; trees and street furniture keep this far from it. */
        const val WALL_BAND = 6f
        private val ROOFS = intArrayOf(c(0xFF6E7378), c(0xFF5F6368), c(0xFF7D8085), c(0xFF8A8D91), c(0xFF55595E))
        fun roofColor(i: Int) = ROOFS[abs(i) % ROOFS.size]
    }
}

fun dist(x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0; val dy = y1 - y0
    return sqrt(dx * dx + dy * dy)
}

internal fun wrapAngle(a: Float): Float {
    var x = a
    while (x > Math.PI) x -= (2 * Math.PI).toFloat()
    while (x < -Math.PI) x += (2 * Math.PI).toFloat()
    return x
}

internal fun hypot2(x: Float, y: Float) = sqrt(x * x + y * y)
internal fun clampf(v: Float, a: Float, b: Float) = max(a, min(b, v))

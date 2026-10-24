package com.hustlecity.game.world

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Everything a map generator produces; [City] indexes it. */
class CityLayout(
    val roads: RoadGraph,
    val buildings: List<Building>,
    val trees: List<Tree>,
    val shore: Shore,
    val parks: List<Park>,
    val rails: List<Rail>,
    val neighborhoods: List<Neighborhood>,
    val bounds: FloatArray,
    val landmarks: List<Landmark> = emptyList(),
    val walls: List<FloatArray> = emptyList(),
)

/** A playable map: its own streets, neighborhoods, landmarks and flavor. */
abstract class MapDef(val id: String, val title: String, val blurb: String) {
    open val leftHand = false
    /** Landlord and resident names. */
    open val firstNames: List<String> = Catalog.firstNames
    open val lastNames: List<String> = Catalog.lastNames
    /** Neighborhood where a new player's first apartment is. */
    open val startHood = 0
    /** Neighborhood index for each place, in the order places are assigned. */
    abstract val placeHoods: List<Pair<String, Int>>

    /**
     * The newest revision of this map's layout. A save keeps the revision its game started on (its buildings are
     * numbered by the layout), so a fixed street plan only reaches new games.
     */
    open val revision = 0

    abstract fun generate(seed: Long, rev: Int = revision): CityLayout

    /** Neighborhood index at a point. */
    abstract fun hoodAt(x: Float, y: Float): Int

    open fun address(number: Int, street: String, hood: String) = "$number $street"

    /**
     * Street traffic: body styles weighted by repetition (0 sedan, 1 pickup, 2 van, 3 sports car, 4 jeepney,
     * 5 tricycle, 6 kei car or boxy minivan, 7 city bus, 8 scooter, 9 SUV, 10 box truck, 11 modern jeepney, 12 moped)
     * and the colors cars come in.
     */
    open val trafficMix: IntArray = intArrayOf(0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 9, 9, 10)
    open val trafficPalette: IntArray = Traffic.DEFAULT_PALETTE

    /** Liveries for a style that has its own (buses, jeepneys): body and accent color pairs; null takes the palette. */
    open fun livery(style: Int): IntArray? = null

    /** Every [taxiEvery]th sedan is a cab, painted in one of [taxiColors]. */
    open val taxiEvery = 6
    open val taxiColors: IntArray = intArrayOf(c(0xFFF2C230))
}

object Maps {
    const val CLASSIC = "classic"
    const val NYC = "nyc"
    const val MANILA = "manila"
    const val TOKYO = "tokyo"
    const val SEOUL = "seoul"

    val all: List<MapDef> by lazy { listOf(ClassicMap, NewYorkMap, ManilaMap, TokyoMap, SeoulMap) }

    fun byId(id: String): MapDef = all.firstOrNull { it.id == id } ?: ClassicMap
}

/** One neighborhood of a generated map, with the points that define its area (nearest seed wins). */
class HoodSpec(val hood: Neighborhood, vararg seeds: Float) {
    val seeds = seeds
}

/**
 * Shared machinery for the hand-designed maps: roads go into a [RoadBuilder], then lots are lined up along every
 * road (buildings face the street), places are assigned, and trees planted.
 */
abstract class PlannedMap(id: String, title: String, blurb: String) : MapDef(id, title, blurb) {
    abstract val hoods: List<HoodSpec>
    /** Map extent: x0, y0, x1, y1. */
    abstract val extent: FloatArray

    protected class Plan(override val rnd: Random, /** Which revision of the layout to lay out. */ val rev: Int) : PlannedMapPlanAccess {
        val roads = RoadBuilder()
        /** Rivers, bays and lakes. */
        val water = ArrayList<FloatArray>()
        /** On a map that's mostly water: the land masses (everything else is water). */
        val land = ArrayList<FloatArray>()
        /** Land inside water (river islands). */
        val islands = ArrayList<FloatArray>()
        /** Narrow streams roads simply cross. */
        val canals = ArrayList<FloatArray>()
        val parks = ArrayList<Park>()
        override val rails = ArrayList<Rail>()
        /** Areas with no buildings (but roads may cross): plazas, river banks, rail yards. */
        val open = ArrayList<FloatArray>()
        val trees = ArrayList<Tree>()
        val landmarks = ArrayList<Landmark>()
        val walls = ArrayList<FloatArray>()
        /** Areas where buildings are taller (downtown cores): polygon and multiplier. */
        override val skyline = ArrayList<Pair<FloatArray, Float>>()
        override val blockers: List<FloatArray> get() = canals + parks.map { it.poly } + open + landmarks.mapNotNull { it.footprint }
        private var shoreCache: Shore? = null
        override val shore: Shore get() = shoreCache ?: Shore(water, land, islands, canals).also { shoreCache = it }
    }

    protected abstract fun plan(p: Plan)

    override fun hoodAt(x: Float, y: Float): Int {
        var best = 0; var bestD = Float.MAX_VALUE
        for ((i, h) in hoods.withIndex()) {
            val s = h.seeds
            for (k in 0 until s.size / 2) {
                val dx = x - s[k * 2]; val dy = y - s[k * 2 + 1]
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    override fun generate(seed: Long, rev: Int): CityLayout {
        val p = Plan(Random(seed), rev)
        plan(p)
        // landmark grounds are cut out of the side streets exactly at their outline
        val cleared = p.landmarks.filter { it.clearsRoads }.mapNotNull { it.footprint }
        val blockedRoads = p.parks.filter { !it.roads }.map { it.poly }
        val shore = p.shore
        val graph = p.roads.build({ x, y -> shore.wet(x, y) }, blockedRoads, cut = cleared)
        numberRoads(graph)
        val hoodList = hoods.map { it.hood }
        val buildings = Lots(this, graph, p, hoodList).place()
        assignPlaces(buildings, p.rnd)
        val trees = ArrayList(p.trees)
        streetTrees(graph, buildings, p, trees)
        parkTrees(p, trees)
        trees.removeAll { t -> clearOfTrees(p, t.x, t.y) }
        return CityLayout(graph, buildings, trees, shore, p.parks, p.rails, hoodList, extent, p.landmarks, p.walls)
    }

    /** Landmark grounds, the corridor under elevated railways and the old city walls have no trees. */
    private fun clearOfTrees(p: Plan, x: Float, y: Float): Boolean {
        for (l in p.landmarks) { val f = l.footprint ?: l.clearance ?: continue; if (Geo.inside(x, y, f)) return true }
        for (r in p.rails) { val q = r.pts; for (i in 0 until q.size / 2 - 1) if (Geo.distToSeg(x, y, q[i * 2], q[i * 2 + 1], q[i * 2 + 2], q[i * 2 + 3]) < 6.5f) return true }
        for (w in p.walls) for (i in 0 until w.size / 2 - 1) if (Geo.distToSeg(x, y, w[i * 2], w[i * 2 + 1], w[i * 2 + 2], w[i * 2 + 3]) < City.WALL_BAND) return true
        return false
    }

    private fun assignPlaces(buildings: List<Building>, rnd: Random) {
        val used = HashSet<Int>()
        for ((placeId, hood) in placeHoods) {
            val place = Catalog.place(placeId)
            val big = place.kind == PlaceKind.FURNITURE_STORE || place.kind == PlaceKind.WHOLESALER || place.kind == PlaceKind.IMPORTER ||
                place.kind == PlaceKind.CAR_DEALER || place.kind == PlaceKind.GAS_STATION
            fun ok(b: Building, minW: Float) = b.id !in used && b.solid && b.frontEdge >= 0 && b.w >= minW && b.d >= 12f
            val candidates = buildings.filter { it.hood.id == hood && ok(it, if (big) 18f else 14f) }
                .ifEmpty { buildings.filter { it.hood.id == hood && ok(it, 10f) } }
                .ifEmpty { buildings.filter { ok(it, 14f) } }
            if (candidates.isEmpty()) continue
            val b = candidates[rnd.nextInt(candidates.size)]
            used += b.id
            b.placeId = placeId
            if (place.kind == PlaceKind.GAS_STATION) {
                b.kind = BKind.GAS
                b.height = 0f
            } else {
                b.kind = BKind.SPECIAL
                b.area = if (big) 1000 else 400
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

    private fun streetTrees(g: RoadGraph, buildings: List<Building>, p: Plan, out: ArrayList<Tree>) {
        val rnd = p.rnd
        for (e in g.edges) {
            if (e.overWater || e.cls == RoadClass.LANE) continue
            for (side in -1..1 step 2) {
                var s = e.clearA + 6f
                while (s < e.len - e.clearB - 4f) {
                    val off = e.cls.halfWidth + e.cls.sidewalk * .62f
                    val x = e.ax + e.ux * s + e.uy * off * side
                    val y = e.ay + e.uy * s - e.ux * off * side
                    val hood = hoods[hoodAt(x, y)].hood
                    if (rnd.nextFloat() < hood.greenery && !p.shore.anyWater(x, y) && !inPark(p, x, y)) {
                        out += Tree(x, y, 1.6f + rnd.nextFloat() * .8f, 4.5f + rnd.nextFloat() * 2f, rnd.nextInt(3), streetSpecies(hood, rnd))
                    }
                    s += 12f + rnd.nextFloat() * 7f
                }
            }
        }
    }

    /** The tree a street in this neighborhood gets; -1 leaves it to the renderer's mix. */
    protected open fun streetSpecies(hood: Neighborhood, rnd: Random): Int = -1

    protected open fun parkSpecies(park: Park, rnd: Random): Int = -1

    private fun inPark(p: Plan, x: Float, y: Float) = p.parks.any { Geo.inside(x, y, it.poly) }

    private fun parkTrees(p: Plan, out: ArrayList<Tree>) {
        val rnd = p.rnd
        for (park in p.parks) {
            if (park.kind == Park.PLAZA) continue
            val bb = Geo.bounds(park.poly)
            var x = bb[0] + 5f
            while (x < bb[2]) {
                var y = bb[1] + 5f
                while (y < bb[3]) {
                    val tx = x + rnd.nextFloat() * 6f - 3f; val ty = y + rnd.nextFloat() * 6f - 3f
                    if (rnd.nextFloat() < (if (park.kind == Park.GROUNDS) .35f else .5f) && Geo.inside(tx, ty, park.poly) &&
                        Geo.distToPoly(tx, ty, park.poly) > 3f && !p.shore.anyWater(tx, ty)) {
                        out += Tree(tx, ty, 2.2f + rnd.nextFloat() * 1.6f, 4f + rnd.nextFloat() * 3f, rnd.nextInt(3), parkSpecies(park, rnd))
                    }
                    y += 9f
                }
                x += 9f
            }
        }
    }

    // ------------------------------------------------------------------ road toolkit

    /**
     * A grid of roads clipped to [region]: lines along the grid's x axis every [sv] meters and along its y axis every
     * [su] meters, rotated by [angle] around ([ox], [oy]).
     */
    protected fun Plan.grid(
        region: FloatArray, ox: Float, oy: Float, angle: Float, su: Float, sv: Float,
        clsU: (Int) -> RoadClass, clsV: (Int) -> RoadClass, nameU: (Int) -> String, nameV: (Int) -> String,
        skipU: (Int) -> Boolean = { false }, skipV: (Int) -> Boolean = { false },
    ) {
        val c = cos(angle); val s = sin(angle)
        val bb = Geo.bounds(region)
        val r = sqrt((bb[2] - bb[0]).let { it * it } + (bb[3] - bb[1]).let { it * it }) + 200f
        val cxr = (bb[0] + bb[2]) * .5f; val cyr = (bb[1] + bb[3]) * .5f
        // grid coordinates covering the region
        val u0 = ((cxr - ox) * c + (cyr - oy) * s) - r; val u1 = u0 + 2 * r
        val v0 = (-(cxr - ox) * s + (cyr - oy) * c) - r; val v1 = v0 + 2 * r
        fun wx(u: Float, v: Float) = ox + u * c - v * s
        fun wy(u: Float, v: Float) = oy + u * s + v * c
        // lines of constant u (running along v)
        var i = kotlin.math.ceil(u0 / su).toInt()
        while (i * su <= u1) {
            if (!skipU(i)) {
                val u = i * su
                for (p in Geo.clip(wx(u, v0), wy(u, v0), wx(u, v1), wy(u, v1), region)) roads.add(p[0], p[1], p[2], p[3], clsU(i), nameU(i))
            }
            i++
        }
        var k = kotlin.math.ceil(v0 / sv).toInt()
        while (k * sv <= v1) {
            if (!skipV(k)) {
                val v = k * sv
                for (p in Geo.clip(wx(u0, v), wy(u0, v), wx(u1, v), wy(u1, v), region)) roads.add(p[0], p[1], p[2], p[3], clsV(k), nameV(k))
            }
            k++
        }
    }

    /**
     * Old-town streets: a jittered lattice of lanes inside [region], some links dropped and the odd diagonal added,
     * so it reads as grown rather than planned. A street runs around the edge so everything connects.
     */
    protected fun Plan.organic(region: FloatArray, spacing: Float, jitter: Float, angle: Float, drop: Float, cls: RoadClass, names: List<String>, ring: RoadClass? = RoadClass.STREET, ringName: String = names.first()) {
        val bb = Geo.bounds(region)
        val c = cos(angle); val s = sin(angle)
        val cxr = (bb[0] + bb[2]) * .5f; val cyr = (bb[1] + bb[3]) * .5f
        val r = sqrt((bb[2] - bb[0]).let { it * it } + (bb[3] - bb[1]).let { it * it }) * .5f + spacing
        val n = (2 * r / spacing).toInt() + 1
        val px = Array(n) { FloatArray(n) }; val py = Array(n) { FloatArray(n) }
        for (i in 0 until n) for (k in 0 until n) {
            val u = -r + i * spacing + (rnd.nextFloat() - .5f) * 2 * jitter
            val v = -r + k * spacing + (rnd.nextFloat() - .5f) * 2 * jitter
            px[i][k] = cxr + u * c - v * s
            py[i][k] = cyr + u * s + v * c
        }
        var nameIdx = 0
        fun link(i0: Int, k0: Int, i1: Int, k1: Int) {
            if (i1 >= n || k1 >= n) return
            if (rnd.nextFloat() < drop) return
            val name = names[(nameIdx++ / 3) % names.size]
            for (p in Geo.clip(px[i0][k0], py[i0][k0], px[i1][k1], py[i1][k1], region)) roads.add(p[0], p[1], p[2], p[3], cls, name)
        }
        for (i in 0 until n) for (k in 0 until n) {
            link(i, k, i + 1, k)
            link(i, k, i, k + 1)
            if (rnd.nextFloat() < .08f) link(i, k, i + 1, k + 1)
        }
        if (ring != null) roads.polyline(region + floatArrayOf(region[0], region[1]), ring, ringName)
    }

    /** A road along a smooth curve through control points. */
    protected fun Plan.curve(ctrl: FloatArray, cls: RoadClass, name: String, bridge: Boolean = false) =
        roads.polyline(Geo.spline(ctrl, 5), cls, name, bridge)

    /** A river: a band of water along a smooth curve. */
    protected fun Plan.river(ctrl: FloatArray, halfWidth: Float): FloatArray {
        val band = Geo.band(Geo.spline(ctrl, 6), halfWidth)
        water += band
        return band
    }

    /** A narrow stream that streets cross without bridges. */
    protected fun Plan.canal(ctrl: FloatArray, halfWidth: Float): FloatArray {
        val band = Geo.band(Geo.spline(ctrl, 6), halfWidth)
        canals += band
        return band
    }

    protected fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    /** A rectangle rotated by [angle] around its centre. */
    protected fun rotRect(cx: Float, cy: Float, w: Float, h: Float, angle: Float): FloatArray {
        val c = cos(angle); val s = sin(angle)
        val out = FloatArray(8)
        val xs = floatArrayOf(-w / 2, w / 2, w / 2, -w / 2); val ys = floatArrayOf(-h / 2, -h / 2, h / 2, h / 2)
        for (i in 0 until 4) { out[i * 2] = cx + xs[i] * c - ys[i] * s; out[i * 2 + 1] = cy + xs[i] * s + ys[i] * c }
        return out
    }

    protected fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}

/**
 * Lines every road with lots: buildings face the street, stop short of junctions and other roads, and keep out of
 * water, parks and open ground.
 */
class Lots(private val map: PlannedMap, private val g: RoadGraph, private val plan: PlannedMapPlanAccess, private val hoods: List<Neighborhood>) {
    companion object {
        /** Lots keep this far from an elevated railway's centre line. */
        const val RAIL_CLEAR = 7f
    }

    private val placed = ArrayList<OBB>()
    private val cell = 40f
    private val buckets = HashMap<Long, MutableList<Int>>()

    private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)

    fun place(): List<Building> {
        val p = plan
        val blockers = p.blockers
        val rnd = p.rnd
        val out = ArrayList<Building>()
        val corners = FloatArray(8)
        val skyline = p.skyline
        for (e in g.edges) {
            if (e.overWater) continue
            for (side in -1..1 step 2) {
                // n points from the road to the lots on this side
                val nx = e.uy * side; val ny = -e.ux * side
                val setback = e.cls.outer + .2f
                var s = e.clearA + endPad(e.a)
                val end = e.len - e.clearB - endPad(e.b)
                while (s < end - 5f) {
                    val mx = e.ax + e.ux * s; val my = e.ay + e.uy * s
                    val hood = hoods[map.hoodAt(mx + nx * 15f, my + ny * 15f)]
                    var w = hood.lotW[0] + rnd.nextFloat() * (hood.lotW[1] - hood.lotW[0])
                    if (end - (s + w) < hood.lotW[0] * .7f) w = end - s
                    if (w < 5f) break
                    var ok = false
                    var depth = hood.lotD[0] + rnd.nextFloat() * (hood.lotD[1] - hood.lotD[0])
                    var tries = 0
                    while (tries < 4 && !ok) {
                        val cx = e.ax + e.ux * (s + w / 2) + nx * (setback + depth / 2)
                        val cy = e.ay + e.uy * (s + w / 2) + ny * (setback + depth / 2)
                        // local y points to the street (-n), local x = (vy, -vx)
                        val vx = -nx; val vy = -ny
                        val ux = vy; val uy = -vx
                        val obb = OBB(cx, cy, ux, uy, w / 2, depth / 2)
                        if (fits(obb, e.id, corners, p, blockers)) {
                            ok = true
                            val id = out.size
                            val ox = cx - ux * w / 2 - vx * depth / 2
                            val oy = cy - uy * w / 2 - vy * depth / 2
                            out += makeBuilding(id, ox, oy, atan2(uy, ux), w, depth, hood, e, s + w / 2, side, rnd, skyline)
                            add(obb)
                        } else {
                            depth *= .72f
                            if (depth < 7f) break
                        }
                        tries++
                    }
                    s += w + if (rnd.nextFloat() < hood.alleys) 2f + rnd.nextFloat() * 3f else 0f
                    if (!ok) s += 2f
                }
            }
        }
        return out
    }

    /**
     * How far past a junction's setback the first lot starts: the strip stops where the crossing road's sidewalk is
     * half done, and a lot must clear all of it, or every corner lot of a short block would be turned down.
     */
    private fun endPad(node: Int): Float {
        val n = g.nodes[node]
        if (n.degree < 3) return 1.5f
        var walk = 0f
        for (id in n.edges) walk = max(walk, g.edges[id].cls.sidewalk)
        return max(1.5f, walk * .5f + .45f)
    }

    private fun add(o: OBB) {
        val id = placed.size
        placed += o
        val r = o.hw + o.hd
        val c0 = kotlin.math.floor((o.cx - r) / cell).toInt(); val c1 = kotlin.math.floor((o.cx + r) / cell).toInt()
        val r0 = kotlin.math.floor((o.cy - r) / cell).toInt(); val r1 = kotlin.math.floor((o.cy + r) / cell).toInt()
        for (y in r0..r1) for (x in c0..c1) buckets.getOrPut(key(x, y)) { mutableListOf() } += id
    }

    private fun fits(o: OBB, edge: Int, corners: FloatArray, p: PlannedMapPlanAccess, blockers: List<FloatArray>): Boolean {
        o.corners(corners)
        val ext = map.extent
        for (i in 0 until 4) {
            val x = corners[i * 2]; val y = corners[i * 2 + 1]
            if (x < ext[0] + 4f || y < ext[1] + 4f || x > ext[2] - 4f || y > ext[3] - 4f) return false
        }
        // other buildings
        val r = o.hw + o.hd
        val c0 = kotlin.math.floor((o.cx - r) / cell).toInt(); val c1 = kotlin.math.floor((o.cx + r) / cell).toInt()
        val r0 = kotlin.math.floor((o.cy - r) / cell).toInt(); val r1 = kotlin.math.floor((o.cy + r) / cell).toInt()
        for (y in r0..r1) for (x in c0..c1) {
            for (id in buckets[key(x, y)].orEmpty()) if (placed[id].overlaps(o, .15f)) return false
        }
        // roads: stay behind every sidewalk
        var clear = true
        g.near(o.cx, o.cy, r + 30f) { id ->
            if (!clear) return@near
            val e = g.edges[id]
            if (o.distToSeg(e.ax, e.ay, e.bx, e.by) < e.cls.outer - (if (id == edge) .05f else -.3f)) clear = false
        }
        if (!clear) return false
        // elevated rails need room for their deck and station platforms (up to 5 m either side of the smoothed line)
        for (rail in p.rails) {
            val pts = rail.pts
            for (i in 0 until pts.size / 2 - 1) if (o.distToSeg(pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3]) < RAIL_CLEAR) return false
        }
        // water, parks and open ground
        for (i in 0 until 4) if (p.shore.wet(corners[i * 2], corners[i * 2 + 1])) return false
        if (p.shore.wet(o.cx, o.cy)) return false
        for (poly in blockers) {
            for (i in 0 until 4) if (Geo.inside(corners[i * 2], corners[i * 2 + 1], poly)) return false
            if (Geo.inside(o.cx, o.cy, poly)) return false
            for (k in 0 until poly.size / 2) if (o.contains(poly[k * 2], poly[k * 2 + 1])) return false
        }
        return true
    }

    private fun makeBuilding(
        id: Int, ox: Float, oy: Float, angle: Float, w: Float, d: Float, hood: Neighborhood, e: RoadEdge, s: Float, side: Int,
        rnd: Random, skyline: List<Pair<FloatArray, Float>>,
    ): Building {
        val corner = s < e.clearA + w + 4f || s > e.len - e.clearB - w - 4f
        var kind = pickKind(hood, rnd)
        if (rnd.nextFloat() < .03f && hood.look != Look.CBD && hood.look != Look.NY_DOWNTOWN) kind = BKind.PARKING
        val cx = ox + (w / 2) * cos(angle) - (d / 2) * sin(angle)
        val cy = oy + (w / 2) * sin(angle) + (d / 2) * cos(angle)
        val boost = skyline.firstOrNull { Geo.inside(cx, cy, it.first) }?.second ?: 1f
        val h = when (kind) {
            BKind.PARKING -> 0f
            BKind.WAREHOUSE -> 7f + rnd.nextFloat() * 5f
            BKind.RETAIL -> max(hood.minH * .6f, 6f) + rnd.nextFloat() * (hood.maxH - hood.minH) * .6f
            else -> (hood.minH + rnd.nextFloat() * (hood.maxH - hood.minH) * (if (kind == BKind.OFFICE) 1f else .75f)) * boost
        }
        val area = when (kind) {
            BKind.RETAIL -> roundTo5(w * (3.5f + rnd.nextFloat() * 5.5f)).coerceIn(40, 380)
            BKind.RESIDENTIAL -> roundTo5(w * (2.6f + rnd.nextFloat() * 4f)).coerceIn(30, 160)
            BKind.WAREHOUSE -> roundTo5(w * d * .8f)
            else -> roundTo5(w * d)
        }
        val trafficIdx = (hood.traffic + e.cls.rank * .04f + (if (corner) .1f else 0f) + (rnd.nextFloat() * .18f - .08f)).coerceIn(.05f, .85f)
        val facade = hood.facades[rnd.nextInt(hood.facades.size)]
        val windowStyle = when {
            kind == BKind.OFFICE && rnd.nextFloat() < .7f -> 0
            kind == BKind.WAREHOUSE -> 2
            else -> 1
        }
        val roofBoxes = ArrayList<RoofBox>()
        if (h > 0f) repeat(rnd.nextInt(0, 3)) {
            val rw = 2f + rnd.nextFloat() * 3f; val rd = 2f + rnd.nextFloat() * 3f
            val rx = 1.5f + rnd.nextFloat() * max(.1f, w - rw - 3f)
            val ry = 1.5f + rnd.nextFloat() * max(.1f, d - rd - 3f)
            roofBoxes += RoofBox(rx, ry, rx + rw, ry + rd, 1.2f + rnd.nextFloat() * 1.5f)
        }
        val number = 2 * ((e.numberOrigin + s) / 9f).toInt() + if (side > 0) 2 else 1
        val landlord = "${map.firstNames.random(rnd)} ${map.lastNames.random(rnd)}"
        return Building(id, ox, oy, angle, w, d, h, kind, hood, e.name, e.id, number, area, trafficIdx, 5 + rnd.nextInt(26), landlord,
            facade, City.roofColor(rnd.nextInt(5)), windowStyle, roofBoxes, corner && e.cls.rank >= 2, map.address(number, e.name, hood.name))
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
}

/** What [Lots] needs from a map's plan. */
interface PlannedMapPlanAccess {
    val rnd: Random
    val rails: List<Rail>
    val skyline: List<Pair<FloatArray, Float>>
    /** Polygons buildings stay out of: canals, parks and open ground. */
    val blockers: List<FloatArray>
    val shore: Shore
}

internal fun angleDiff(a: Float, b: Float): Float {
    var d = abs(a - b) % (2 * Math.PI).toFloat()
    if (d > Math.PI) d = (2 * Math.PI).toFloat() - d
    return d
}

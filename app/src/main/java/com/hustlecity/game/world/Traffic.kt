package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A car on a lane or a pedestrian on a sidewalk, moving over the road graph. */
class Agent {
    var x = 0f
    var y = 0f
    var heading = 0f
    var speed = 0f
    var cruise = 10f
    var color = 0
    /** A livery's second color (stripes, a two-tone roof); 0 when the vehicle has none. */
    var accent = 0
    var length = 4.2f
    var width = 1.8f
    var height = 1.45f
    var style = 0
    var phase = 0f
    /** False until the agent has been placed in the city. */
    var placed = false
    /** Road piece, the node it came from, and how far along it is (meters from that node). */
    var edge = -1
    var from = -1
    var s = 0f
    /** Lane index from the centre line (cars), or side of the road: +1 right of travel, -1 left (pedestrians). */
    var lane = 0
    var side = 1
    /** Next road piece (planned at the coming node) and its lane/side. */
    var next = -1
    var nextLane = 0
    /** Crossing a junction along a curve from (jx0, jy0) via (jcx, jcy) to (jx1, jy1). */
    var inJunction = false
    var jNode = -1
    var jt = 0f
    var jLen = 1f
    var jx0 = 0f; var jy0 = 0f; var jcx = 0f; var jcy = 0f; var jx1 = 0f; var jy1 = 0f
    /** Pedestrian state: 0 walking, 1 waiting at the curb, 2 crossing / walking to the next sidewalk, 3 entering a shop, 4 inside, 5 leaving the shop. */
    var state = 0
    var timer = 0f
    /**
     * A walker's way across a road at a corner: to the near end of its crossing ([wx0], [wy0]), over to the far end
     * ([wx1], [wy1]), then on to the next sidewalk ([tx], [ty]); [leg] is which of those it's on (0, 1, 2).
     */
    var wx0 = 0f; var wy0 = 0f; var wx1 = 0f; var wy1 = 0f
    var leg = 2
    /** The pavement corner to go round on the way to the crossing ([c0x], [c0y]) and on from it ([c2x], [c2y]); NaN for none. */
    var c0x = Float.NaN; var c0y = 0f; var c2x = Float.NaN; var c2y = 0f
    /** Where a walker steps to, to get round the end of a car standing in the way (NaN when there's none), and how often they have this crossing. */
    var roundX = Float.NaN; var roundY = 0f
    var rounds = 0
    var roundOf: Agent? = null
    /** Personal sideways spread on the sidewalk, and a temporary side-step to pass someone. */
    var jitter = 0f
    var dodge = 0f
    var tx = 0f
    var ty = 0f
    var hidden = false
    /** Seconds a car has been held up by cross traffic (used to untangle jams in junctions). */
    var blocked = 0f
    /**
     * The car a stopped car is waiting on (its index; -1 for none, or only a light or someone on foot), and seconds
     * left of going regardless after it was found waiting in a circle of cars all waiting on each other.
     */
    var waitOn = -1
    var unstick = 0f
    /** The road a car came into its junction from, and the lane it was in (while [inJunction]). */
    var jEdge = -1
    var jLane = 0
    /** A car's turn at the coming junction (radians, + to the right), and how far it still sits off its lane's middle while changing lanes. */
    var turn = 0f
    var shift = 0f
    /**
     * The junction (or group of junctions working as one, by its first node) a car is going through whatever the lights
     * now say: it went on amber when it couldn't stop, or it's in it already; and the light group it came in on.
     */
    var through = -1
    var jGroup = 0
    /** How the body leans on its springs: nose up (+) or down when braking, and rolled toward the outside of a turn. */
    var pitch = 0f
    var roll = 0f
    var lastSpeed = 0f
    var lastHeading = 0f
    /** Set down by hand for a shot ([Traffic.placeCar]): others placed after it make room elsewhere, not here. */
    var staged = false
    /** A pedestrian's appearance seed (see [com.hustlecity.game.render.Look]). */
    var look = 1
    /** What a pedestrian is doing while stopped: 1 checking their phone, 2 looking in a shop window. */
    var activity = 0
    /** Out for a run. */
    var jog = false
    /** Who walks with them: 1 a dog on a leash, 2 a child. */
    var companion = 0
    /** Seconds left of a stop on the sidewalk, and the heading to walk on with afterwards. */
    var pause = 0f
    var walkHeading = 0f
    /** How far a dog or child has fallen in behind (0 alongside, 1 in line) where the pavement is too narrow to pass side by side. */
    var tuck = 0f
}

/**
 * Where walker [a]'s dog or child is: the dog trots ahead on the right, the child walks alongside holding a hand, and
 * both fall in behind where the pavement is too narrow to pass side by side ([Agent.tuck]).
 */
fun companionAt(a: Agent, out: FloatArray) {
    val fx = sin(a.heading); val fy = -cos(a.heading)
    val t = a.tuck
    val ahead = if (a.companion == 1) .75f - 1.55f * t else -.6f * t
    val aside = (if (a.companion == 1) .45f else .55f) * (1f - t)
    // right of the heading in a y-down world is (-fy, fx)
    out[0] = a.x + fx * ahead - fy * aside; out[1] = a.y + fy * ahead + fx * aside
}

/** Eases a car's body [a] toward the lean its braking, accelerating and turning would give it. */
fun lean(a: Agent, speed: Float, heading: Float, dt: Float) {
    if (dt <= 0f) return
    var dh = heading - a.lastHeading
    while (dh > PI_F) dh -= 2 * PI_F
    while (dh < -PI_F) dh += 2 * PI_F
    // a respawn or a jump of the camera: start level
    if (abs(dh) > 1f || abs(speed - a.lastSpeed) > 8f) { a.lastSpeed = speed; a.lastHeading = heading; a.pitch = 0f; a.roll = 0f; return }
    val acc = (speed - a.lastSpeed) / dt
    val lat = speed * dh / dt
    val tp = (acc * .012f).coerceIn(-.06f, .045f)
    val tr = (lat * .014f).coerceIn(-.07f, .07f)
    val k = min(1f, dt * 7f)
    a.pitch += (tp - a.pitch) * k; a.roll += (tr - a.roll) * k
    a.lastSpeed = speed; a.lastHeading = heading
}

private const val PI_F = 3.14159265f

/**
 * Cars and pedestrians around the camera. Cars keep to their lane (right, or left on left-hand maps), plan a
 * turn at every junction and follow a curve through it, stop for red lights, give way in unsignalled junctions,
 * and keep their distance. Pedestrians walk the sidewalks, cross on the walk signal and pop into open shops.
 */
class Traffic(private val rnd: Random = Random(7)) {
    val cars = ArrayList<Agent>()
    val peds = ArrayList<Agent>()
    /** Parked player vehicles (x, y pairs) that NPC cars stop for. */
    var obstacles = FloatArray(0)
    var obstacleCount = 0
    /** Shop doors near the camera: x, y, chance a passer-by walks in, and the inward direction (x, y). */
    var doors = FloatArray(0)
    var doorCount = 0
    var lightClock = 0f
    /** Weather: cars drive slower on wet or snowy roads, pedestrians walk faster in the rain. */
    var speedFactor = 1f
    var hurry = 1f

    private var roads: RoadGraph? = null
    private var leftHand = false
    /** Vehicle mix: styles weighted by repetition; beyond the basic four they're map specific (jeepneys, kei cars, buses…). */
    var mix: IntArray = intArrayOf(0, 0, 0, 0, 0, 0, 1, 1, 2, 2)
    var palette: IntArray = DEFAULT_PALETTE
    /** Per style: body and accent color pairs for vehicles with a livery, or null to take the palette. */
    var liveries = arrayOfNulls<IntArray>(16)
    private val shirtColors = intArrayOf(
        c(0xFFEB5757), c(0xFF2F80ED), c(0xFF27AE60), c(0xFFF2C94C), c(0xFF9B51E0), c(0xFFF2994A), c(0xFFE0E0E0), c(0xFF333333), c(0xFF56CCF2),
    )

    /** Switches to a city's roads and its own traffic; everyone is placed again. */
    fun attach(city: City) {
        this.city = city
        bands = arrayOfNulls(city.roads.edges.size * 2)
        roads = city.roads
        leftHand = city.leftHand
        mix = city.map.trafficMix
        palette = city.map.trafficPalette
        liveries = Array(16) { city.map.livery(it) }
        walk = FloatArray(city.roads.edges.size * 4) { Float.NaN }
        for (a in cars) a.placed = false
        for (p in peds) p.placed = false
        coordinateLights(city.roads)
    }

    /**
     * Junctions only a short link apart (the two halves of a crossing over a divided road) work as one: the same timing,
     * and their light groups taken from the same direction, so traffic going straight on gets green at both.
     */
    private fun coordinateLights(g: RoadGraph) {
        val root = IntArray(g.nodes.size) { it }
        fun find(i: Int): Int { var k = i; while (root[k] != k) { root[k] = root[root[k]]; k = root[k] }; return k }
        for (e in g.edges) {
            val na = g.nodes[e.a]; val nb = g.nodes[e.b]
            if (na.signal && nb.signal && na.degree >= 3 && nb.degree >= 3 && shortLink(e)) {
                val ra = find(e.a); val rb = find(e.b)
                if (ra != rb) root[max(ra, rb)] = min(ra, rb)
            }
        }
        cluster = IntArray(g.nodes.size) { find(it) }
        lightPhase = FloatArray(g.nodes.size) { g.nodes[find(it)].phase }
        lightRef = FloatArray(g.nodes.size) { i ->
            val n = g.nodes[find(i)]
            if (n.edges.isEmpty()) 0f else g.edges[n.edges[0]].let { atan2(it.uy, it.ux) }
        }
    }

    /** A road between two junctions with no room to wait on it: whoever crosses one carries on across the other. */
    private fun shortLink(e: RoadEdge) = e.len - e.clearA - e.clearB < SHORT_LINK

    private var lightPhase = FloatArray(0)
    private var lightRef = FloatArray(0)
    /** Each node's group of junctions working as one (its first node's id; its own where it's alone). */
    private var cluster = IntArray(0)
    private fun clusterOf(n: Int) = cluster.getOrElse(n) { n }

    /** Eases the number of pedestrians toward [n]: newcomers walk in from the edge, leavers vanish off-screen. */
    /** How far from the camera people walk (0: the update radius decides); closer in, the same crowd looks busier. */
    var pedRadius = 0f

    fun setPedTarget(n: Int, fx: Float, fy: Float) {
        if (peds.size < n) peds += Agent()
        else if (peds.size > n) {
            val far = peds.filter { !it.hidden }.maxByOrNull { dist(it.x, it.y, fx, fy) } ?: return
            if (dist(far.x, far.y, fx, fy) > 60f) peds.remove(far)
        }
    }

    /**
     * Sets car [index] on road [edge], driving away from node [from] [s] meters along it in [lane], as a vehicle of
     * [style] in one of its liveries: traffic placed by hand where a camera looks. Any car already there moves elsewhere.
     */
    fun placeCar(index: Int, edge: Int, from: Int, s: Float, style: Int, lane: Int = 0) {
        val g = roads ?: return
        val a = cars.getOrNull(index) ?: return
        val e = g.edges.getOrNull(edge) ?: return
        a.edge = edge; a.from = from; a.s = s.coerceIn(0f, e.len)
        a.lane = lane.coerceIn(0, e.cls.lanes - 1)
        a.inJunction = false; a.next = -1
        at(e, from, a.s, laneOffset(e, a.lane), tmp)
        a.x = tmp[0]; a.y = tmp[1]
        a.heading = headingOf(e, from)
        a.placed = true
        a.staged = true
        dress(a, style, e)
        // traffic already in the spot drives on from somewhere else
        for (o in cars) if (o !== a && o.placed && !o.staged && dist(o.x, o.y, a.x, a.y) < 6f) o.placed = false
    }

    /** Puts [fraction] of the walkers on sidewalks within [radius] of (fx, fy): a busy moment right where you're looking. */
    fun scatter(fx: Float, fy: Float, radius: Float, fraction: Float) {
        val g = roads ?: return
        val n = (peds.size * fraction).toInt()
        for (i in 0 until n) {
            // a road picked near the point can run on far past it: keep trying for a spot within the radius
            for (attempt in 0 until 30) {
                spawnPed(g, peds[i], fx, fy, radius, true)
                if (dist(peds[i].x, peds[i].y, fx, fy) <= radius) break
            }
        }
    }

    fun ensure(carCount: Int, pedCount: Int) {
        while (cars.size < carCount) cars += Agent()
        while (cars.size > carCount) cars.removeAt(cars.size - 1)
        while (peds.size < pedCount) peds += Agent()
        while (peds.size > pedCount) peds.removeAt(peds.size - 1)
    }

    // ------------------------------------------------------------------ lights

    /**
     * Which light group an approach belongs to at a junction: roads roughly parallel to the first one (of the junctions
     * working together, see [coordinateLights]) share a phase.
     */
    private fun group(n: RoadNode, e: RoadEdge): Int {
        val a = lightRef.getOrElse(n.id) { 0f }; val b = atan2(e.uy, e.ux)
        var d = abs(a - b) % Math.PI.toFloat()
        if (d > Math.PI / 2) d = Math.PI.toFloat() - d
        return if (d < Math.PI / 4) 0 else 1
    }

    private fun phaseOf(n: RoadNode) = lightPhase.getOrElse(n.id) { n.phase }

    /**
     * Light for traffic on [edge] entering junction [node]: 0 green, 1 yellow, 2 red (always green without lights).
     * Between one direction's red and the other's green both are red a moment, while the junction clears.
     */
    fun light(node: RoadNode, edge: RoadEdge): Int {
        if (!node.signal) return 0
        if (roads == null) return 0
        if (node.scramble) {
            // two road phases, then every car waits while the crossing fills with people
            val t = (lightClock + phaseOf(node)) % SCRAMBLE_CYCLE
            return if (group(node, edge) == 0) when { t < 14f -> 0; t < 17f -> 1; else -> 2 }
            else when { t in 19f..31f -> 0; t in 31f..34f -> 1; else -> 2 }
        }
        val t = (lightClock + phaseOf(node)) % CYCLE
        return if (group(node, edge) == 0) when { t < 18f -> 0; t < 21f -> 1; else -> 2 }
        else when { t in 23f..36f -> 0; t in 36f..39f -> 1; else -> 2 }
    }

    /** Seconds before traffic on [edge] gets its green at [node] (0 while it's green or amber; scrambles aside). */
    fun redLeft(node: RoadNode, edge: RoadEdge): Float {
        if (!node.signal || node.scramble) return if (light(node, edge) == 2) 99f else 0f
        val t = (lightClock + phaseOf(node)) % CYCLE
        return if (group(node, edge) == 0) (if (t >= 21f) CYCLE - t else 0f)
        else if (t >= 39f) CYCLE - t + 23f else if (t < 23f) 23f - t else 0f
    }

    /** True while a scramble crossing's walk signal is on: all its traffic is held and people cross any way they like. */
    fun scrambleWalk(node: RoadNode): Boolean = node.scramble && node.signal && (lightClock + phaseOf(node)) % SCRAMBLE_CYCLE in WALK_START..WALK_END

    /** The light clock reading (in its cycle) at which [node]'s scramble walk signal comes on. */
    fun scrambleWalkClock(node: RoadNode): Float = ((WALK_START - phaseOf(node)) % SCRAMBLE_CYCLE + SCRAMBLE_CYCLE) % SCRAMBLE_CYCLE

    /** Seconds until [node]'s scramble walk signal next comes on (0 while it's on). */
    fun untilScrambleWalk(node: RoadNode): Float {
        val t = (lightClock + phaseOf(node)) % SCRAMBLE_CYCLE
        return if (t in WALK_START..WALK_END) 0f else ((WALK_START - t) % SCRAMBLE_CYCLE + SCRAMBLE_CYCLE) % SCRAMBLE_CYCLE
    }

    // ------------------------------------------------------------------ geometry

    private fun clearAt(e: RoadEdge, node: Int) = if (node == e.a) e.clearA else e.clearB

    /**
     * Clear stretch of each sidewalk, measured from node a: (lo, hi) for the right of a→b, then for the left.
     * Where roads meet at a sharp angle one road's sidewalk runs over the other's asphalt, so walkers stop short of it.
     */
    private var walk = FloatArray(0)

    private fun walkRange(g: RoadGraph, e: RoadEdge, phys: Int): Int {
        val k = e.id * 4 + phys * 2
        if (!walk[k].isNaN()) return k
        val sign = if (phys == 0) 1f else -1f
        val near = e.cls.halfWidth + .3f; val far = e.cls.halfWidth + e.cls.sidewalk * .8f
        // as far out as anyone walks (stepping round something on the pavement)
        val outer = max(far, e.cls.halfWidth + e.cls.sidewalk - .45f)
        val steps = max(2, e.len.toInt())
        var bestLo = 0f; var bestHi = -1f; var runLo = -1f
        for (i in 0..steps) {
            val s = e.len * i / steps
            val bx = e.ax + e.ux * s; val by = e.ay + e.uy * s
            // right of a→b in a y-down world is (-uy, ux)
            val nx = -e.uy * sign; val ny = e.ux * sign
            // off the asphalt, and on land all the way across: where the shore cuts into a sidewalk nobody walks on
            val c = city
            var clear = !g.onAsphalt(bx + nx * near, by + ny * near) && !g.onAsphalt(bx + nx * far, by + ny * far)
            if (clear && c != null) {
                var o = near
                while (clear) {
                    if (c.inWater(bx + nx * o, by + ny * o)) clear = false
                    if (o >= outer) break
                    o = min(outer, o + .5f)
                }
            }
            if (clear) {
                if (runLo < 0f) runLo = s
                if (s - runLo > bestHi - bestLo) { bestLo = runLo; bestHi = s }
            } else runLo = -1f
        }
        if (bestHi - bestLo < 2f) { bestLo = e.len * .5f; bestHi = e.len * .5f }
        walk[k] = bestLo; walk[k + 1] = bestHi
        return k
    }

    /** Which physical sidewalk a walker heading away from [from] on [side] (1 right, -1 left) is on. */
    private fun phys(e: RoadEdge, from: Int, side: Int) = if ((from == e.a) == (side > 0)) 0 else 1

    /** Where the clear sidewalk starts, measured from [from]. */
    private fun walkStart(g: RoadGraph, e: RoadEdge, from: Int, side: Int): Float {
        val k = walkRange(g, e, phys(e, from, side))
        return if (from == e.a) walk[k] else e.len - walk[k + 1]
    }

    /** Where the clear sidewalk ends, measured from [from]. */
    private fun walkEnd(g: RoadGraph, e: RoadEdge, from: Int, side: Int): Float {
        val k = walkRange(g, e, phys(e, from, side))
        return if (from == e.a) walk[k + 1] else e.len - walk[k]
    }

    /** Lane centre offset from the road's centre line, to the right of travel (negative = left). */
    private fun laneOffset(e: RoadEdge, lane: Int): Float {
        val o = (lane.coerceIn(0, e.cls.lanes - 1) + .5f) * e.cls.laneWidth
        return if (leftHand) -o else o
    }

    /** World position [s] meters from node [from] along [e], [off] meters to the right of travel. */
    private fun at(e: RoadEdge, from: Int, s: Float, off: Float, out: FloatArray) {
        val fwd = from == e.a
        val dx = if (fwd) e.ux else -e.ux; val dy = if (fwd) e.uy else -e.uy
        val ox = if (fwd) e.ax else e.bx; val oy = if (fwd) e.ay else e.by
        // right of travel in a y-down world is (-dy, dx)
        out[0] = ox + dx * s - dy * off
        out[1] = oy + dy * s + dx * off
    }

    private fun headingOf(e: RoadEdge, from: Int): Float = if (from == e.a) atan2(e.ux, -e.uy) else atan2(-e.ux, e.uy)

    private val tmp = FloatArray(2)
    private val tmp2 = FloatArray(2)

    // ------------------------------------------------------------------ spawning

    private val candidates = ArrayList<Int>()

    private fun pickEdge(g: RoadGraph, fx: Float, fy: Float, radius: Float, car: Boolean): Int {
        candidates.clear()
        g.forCells(fx - radius, fy - radius, fx + radius, fy + radius) { id ->
            val e = g.edges[id]
            if (e.len < 6f || (!car && e.under)) return@forCells
            // bigger roads get more traffic
            val w = if (car) e.cls.rank + 1 else 2
            repeat(w) { candidates += id }
        }
        return if (candidates.isEmpty()) -1 else candidates[rnd.nextInt(candidates.size)]
    }

    private fun spawnCar(g: RoadGraph, a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean) {
        for (attempt in 0 until 14) {
            val id = pickEdge(g, fx, fy, radius, true)
            if (id < 0) return
            val e = g.edges[id]
            a.edge = id
            a.from = if (rnd.nextBoolean()) e.a else e.b
            val s0 = clearAt(e, a.from); val s1 = e.len - clearAt(e, e.other(a.from))
            if (s1 - s0 < 4f) continue
            a.s = s0 + rnd.nextFloat() * (s1 - s0)
            a.lane = rnd.nextInt(e.cls.lanes)
            a.inJunction = false
            a.next = -1
            at(e, a.from, a.s, laneOffset(e, a.lane), tmp)
            a.x = tmp[0]; a.y = tmp[1]
            a.heading = headingOf(e, a.from)
            if (!initial && dist(a.x, a.y, fx, fy) < radius * .55f) continue
            if (cars.any { it !== a && it.placed && abs(it.x - a.x) < 7f && abs(it.y - a.y) < 7f }) continue
            break
        }
        a.placed = true
        a.staged = false
        dress(a, null, g.edges[a.edge.coerceAtLeast(0)])
    }

    /** Gives a car its body: [style] (or one from the city's mix), a livery or palette color, its real size, and a speed for road [e]. */
    private fun dress(a: Agent, style: Int?, e: RoadEdge) {
        a.color = palette[rnd.nextInt(palette.size)]
        a.style = style ?: mix[rnd.nextInt(mix.size)]
        val livery = liveries.getOrNull(a.style)
        if (livery != null) {
            val k = rnd.nextInt(livery.size / 2) * 2
            a.color = livery[k]; a.accent = livery[k + 1]
        } else a.accent = 0
        // jeepney, tricycle, kei car and bus at their real sizes (CarPainter builds them to fit)
        a.length = when (a.style) { 0 -> 4.2f + rnd.nextFloat() * .6f; 1 -> 5.2f; 2 -> 5.8f; 4 -> 6.2f; 5 -> 2.5f; 6 -> 3.4f; 7 -> 11f; 8 -> 1.9f; 9 -> 4.8f; 10 -> 7.2f; 11 -> 7f; 12 -> 1.8f; else -> 4.6f }
        a.width = when (a.style) { 2 -> 2.1f; 4 -> 2.05f; 5 -> 1.5f; 6 -> 1.48f; 7 -> 2.5f; 8 -> .78f; 9 -> 2.0f; 10 -> 2.4f; 11 -> 2.2f; 12 -> .72f; else -> 1.85f }
        a.height = when (a.style) { 0, 3 -> 1.45f; 1 -> 1.8f; 2 -> 2.5f; 4 -> 2.3f; 5 -> 1.75f; 6 -> 1.7f; 7 -> 3.1f; 8 -> 1.3f; 9 -> 1.85f; 10 -> 3.3f; 11 -> 2.85f; 12 -> 1.3f; else -> 1.5f }
        // city speeds: a bit under the road's limit, slower on small streets
        a.cruise = e.cls.speed * (.68f + rnd.nextFloat() * .2f) * (if (a.style == 5 || a.style == 7 || a.style == 11) .8f else 1f)
        a.speed = a.cruise * .6f
        a.blocked = 0f
    }

    /**
     * How far from the road's centre line a walker keeps: spread over the sidewalk from a step back from the curb (so
     * drivers turning at the corner don't take them for someone about to cross) to 1.2 m short of the shop fronts
     * (where shops put out their tables, buckets and boards).
     */
    private fun pedOffset(e: RoadEdge, a: Agent): Float {
        val lo = e.cls.halfWidth + .9f; val hi = maxOf(lo, e.cls.halfWidth + e.cls.sidewalk - 1.2f)
        return lo + (hi - lo).coerceAtLeast(0f) * (.5f + a.jitter * .5f)
    }

    private var city: City? = null

    /**
     * What stands on each sidewalk (per edge and physical side, see [phys]): runs of (along from, along to, out from,
     * out to), measured from node a along the edge and out from its centre line. Built from the city's pavement the
     * first time someone walks there.
     */
    private var bands = arrayOfNulls<FloatArray>(0)
    private val span = FloatArray(4)

    private fun bandsOf(e: RoadEdge, ph: Int): FloatArray? {
        val pv = city?.pavement ?: return null
        val k = e.id * 2 + ph
        bands[k]?.let { return it }
        val sign = if (ph == 0) 1f else -1f
        val nx = -e.uy * sign; val ny = e.ux * sign
        val inner = e.cls.halfWidth - .2f; val outer = e.cls.halfWidth + e.cls.sidewalk + .3f
        val found = HashSet<Int>()
        val list = ArrayList<FloatArray>()
        var t = 0f
        while (true) {
            val px = e.ax + e.ux * t + nx * (inner + outer) * .5f; val py = e.ay + e.uy * t + ny * (inner + outer) * .5f
            pv.forNear(px, py, 12f) { i ->
                if (!found.add(i)) return@forNear
                pv.span(i, e.ax, e.ay, e.ux, e.uy, nx, ny, span)
                if (span[1] > -1f && span[0] < e.len + 1f && span[3] > inner && span[2] < outer) list += span.copyOf()
            }
            if (t >= e.len) break
            t = minOf(e.len, t + 10f)
        }
        list.sortBy { it[0] }
        val out = FloatArray(list.size * 4)
        for ((j, b) in list.withIndex()) System.arraycopy(b, 0, out, j * 4, 4)
        bands[k] = out
        return out
    }

    // the stretches of sidewalk blocked where a walker is: out from, out to, and how close they are (0..1)
    private val ivLo = FloatArray(16); private val ivHi = FloatArray(16); private val ivW = FloatArray(16)
    private val wetTmp = FloatArray(2)
    /** Whether the last [clearOffset] found no way past at the berth asked for. */
    private var squeezed = false

    /**
     * The offset from the centre line a walker [s] meters from [from] along [e], on [side], takes instead of [want]
     * to step round what stands on the pavement there, keeping [clear] from it: easing out to pass it a little before
     * and back after. With several things side by side (a food cart and a bench beyond it) they look for a way past all
     * of them, and where there's none (a narrow sidewalk) the way least in the way, always on dry land.
     */
    private fun clearOffset(e: RoadEdge, from: Int, side: Int, s: Float, want: Float, clear: Float = CLEAR): Float {
        squeezed = false
        val b = bandsOf(e, phys(e, from, side)) ?: return want
        if (b.isEmpty()) return want
        val sa = if (from == e.a) s else e.len - s
        val lo = e.cls.halfWidth + .3f; val hi = e.cls.halfWidth + e.cls.sidewalk - .45f
        var n = 0; var w = 0f
        var k = 0
        while (k < b.size) {
            val a0 = b[k]; val a1 = b[k + 1]; val o0 = b[k + 2] - clear; val o1 = b[k + 3] + clear
            k += 4
            if (a0 > sa + RAMP) break
            if (sa > a1 + RAMP || n == ivLo.size) continue
            // how hard this one pushes: fully alongside it, easing in and out over RAMP meters
            val near = when { sa < a0 -> 1f - (a0 - sa) / RAMP; sa > a1 -> 1f - (sa - a1) / RAMP; else -> 1f }
            ivLo[n] = o0; ivHi[n] = o1; ivW[n] = near; n++
            if (want > o0 && want < o1) w = max(w, near)
        }
        if (w <= 0f) return want
        // the nearest place across the sidewalk clear of everything here, or else the one in the way of the least
        var best = Float.NaN; var bestCost = Float.MAX_VALUE
        for (i in -1 until n) for (q in 0..1) {
            val p = if (i < 0) (if (q == 0) lo else hi) else if (q == 0) ivLo[i] else ivHi[i]
            if (p < lo || p > hi) continue
            var cost = 0f
            for (j in 0 until n) if (p > ivLo[j] + 1e-3f && p < ivHi[j] - 1e-3f) cost = max(cost, ivW[j])
            if (!(cost < bestCost - 1e-4f || (cost <= bestCost + 1e-4f && abs(p - want) < abs(best - want)))) continue
            at(e, from, s, p * side, wetTmp)
            if (city?.inWater(wetTmp[0], wetTmp[1]) == true) continue
            best = p; bestCost = cost
        }
        if (best.isNaN()) return want
        squeezed = bestCost > 0f
        // ease out as hard as the nearest of everything between here and there pushes (not only what's in the way of
        // [want]: a bench behind with a food cart alongside would leave them halfway, in the cart)
        val p0 = min(want, best); val p1 = max(want, best)
        for (j in 0 until n) if (ivLo[j] < p1 && ivHi[j] > p0) w = max(w, ivW[j])
        return want + (best - want) * (w * w * (3f - 2f * w))
    }

    /** How far a walker keeps from what stands on the pavement: further with a dog or a child alongside. */
    private fun clearOf(a: Agent) = if (a.companion != 0) CLEAR + COMPANION else CLEAR

    private val lookTmp = com.hustlecity.game.render.Look()

    private fun spawnPed(g: RoadGraph, a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean) {
        a.color = shirtColors[rnd.nextInt(shirtColors.size)]
        a.look = rnd.nextInt() or 1
        val lk = lookTmp.decode(a.look, false)
        // a few people are out for a run; some walk a dog or a child; seniors take their time
        a.jog = lk.age != 2 && !lk.shoulderBag && rnd.nextFloat() < .06f
        a.companion = if (a.jog) 0 else rnd.nextFloat().let { if (it < .05f) 1 else if (it < .09f && lk.age == 0) 2 else 0 }
        a.activity = 0; a.pause = 0f
        a.cruise = if (a.jog) 2.6f + rnd.nextFloat() * .6f else (1.1f + rnd.nextFloat() * .6f) * lk.pace * (if (a.companion != 0) .85f else 1f)
        a.speed = a.cruise
        a.phase = rnd.nextFloat() * 6f
        a.jitter = rnd.nextFloat() * 2f - 1f
        a.dodge = 0f
        a.state = 0
        a.tuck = 0f
        a.hidden = false
        for (attempt in 0 until 12) {
            val id = pickEdge(g, fx, fy, radius, false)
            if (id < 0) return
            val e = g.edges[id]
            a.edge = id
            a.from = if (rnd.nextBoolean()) e.a else e.b
            a.side = if (rnd.nextBoolean()) 1 else -1
            val s0 = walkStart(g, e, a.from, a.side); val s1 = walkEnd(g, e, a.from, a.side)
            if (s1 - s0 < 3f) continue
            a.s = s0 + rnd.nextFloat() * (s1 - s0)
            at(e, a.from, a.s, clearOffset(e, a.from, a.side, a.s, pedOffset(e, a), clearOf(a)) * a.side, tmp)
            a.x = tmp[0]; a.y = tmp[1]
            a.heading = headingOf(e, a.from)
            if (initial || dist(a.x, a.y, fx, fy) > radius * .55f) break
        }
        a.next = -1
        a.placed = true
    }

    // ------------------------------------------------------------------ update

    /**
     * @param fx focus point (camera target)
     * @param blockerX the player (or the player's car), which NPC cars stop for
     */
    fun update(dt: Float, fx: Float, fy: Float, radius: Float, blockerX: Float, blockerY: Float, initial: Boolean = false) {
        val g = roads ?: return
        if (g.edges.isEmpty()) return
        lightClock += dt
        if (pedOnRoad.size != peds.size) pedOnRoad = BooleanArray(peds.size)
        for (i in peds.indices) {
            val p = peds[i]
            pedOnRoad[i] = p.placed && !p.hidden && p.state != 1 && p.state != 4 && p.state != 6 && g.onAsphalt(p.x, p.y)
        }
        for (i in cars.indices) {
            val a = cars[i]
            if (initial || !a.placed || dist(a.x, a.y, fx, fy) > radius) spawnCar(g, a, fx, fy, radius, initial || !a.placed)
            stepCar(g, a, i, dt, blockerX, blockerY)
            lean(a, a.speed, a.heading, dt)
        }
        breakDeadlocks()
        val pr = if (pedRadius > 0f) min(pedRadius, radius * .7f) else radius * .7f
        for (p in peds) {
            if (initial || !p.placed || (dist(p.x, p.y, fx, fy) > pr && p.state != 4)) spawnPed(g, p, fx, fy, pr, initial || !p.placed)
            stepPed(g, p, dt)
        }
    }

    /**
     * Cars all stopped and each waiting on the next, round in a circle, would wait for ever: the first of them in the
     * list goes regardless for a moment (a queue behind a red light ends at the light, so it's no circle).
     */
    private fun breakDeadlocks() {
        for (i in cars.indices) {
            if (cars[i].waitOn < 0) continue
            var j = cars[i].waitOn; var k = 0; var first = i
            while (j >= 0 && j != i && k++ < 16) { first = min(first, j); j = cars[j].waitOn }
            if (j == i) cars[first].unstick = 2.5f
        }
    }

    /** Picks the road to take at [node] after arriving on [from]: no U-turns unless it's a dead end; straight and big roads are likelier. */
    private fun chooseNext(g: RoadGraph, node: RoadNode, from: RoadEdge, car: Boolean): Int {
        if (node.degree <= 1) return from.id
        val inA = if (node.id == from.b) atan2(from.uy, from.ux) else atan2(-from.uy, -from.ux)
        var total = 0f
        for (id in node.edges) {
            if (id == from.id) continue
            total += weight(g, node, inA, g.edges[id], car)
        }
        if (total <= 0f) return from.id
        var r = rnd.nextFloat() * total
        for (id in node.edges) {
            if (id == from.id) continue
            val w = weight(g, node, inA, g.edges[id], car)
            r -= w
            if (w > 0f && r <= 0f) return id
        }
        return node.edges.firstOrNull { it != from.id && weight(g, node, inA, g.edges[it], car) > 0f } ?: from.id
    }

    private fun weight(g: RoadGraph, node: RoadNode, inA: Float, e: RoadEdge, car: Boolean): Float {
        if (!car && e.under) return 0f
        // (a stub between two junctions has no pavement of its own to speak of: walkers take a real street if there's one)
        if (!car && shortLink(e)) return .02f
        val out = g.angleAway(e, node.id)
        val turn = angleDiff(inA, out)
        val straight = if (turn < .5f) 2.2f else 1f
        return straight * (if (car) (e.cls.rank + 1f) else 1f)
    }

    /** Signed turn from the heading on [from] into [to] at [node] (radians, positive = right in a y-down world). */
    private fun turnAngle(g: RoadGraph, node: RoadNode, from: RoadEdge, to: RoadEdge): Float {
        val ix = if (node.id == from.b) from.ux else -from.ux; val iy = if (node.id == from.b) from.uy else -from.uy
        val ox = if (node.id == to.a) to.ux else -to.ux; val oy = if (node.id == to.a) to.uy else -to.uy
        return atan2(ix * oy - iy * ox, ix * ox + iy * oy)
    }

    // ------------------------------------------------------------------ cars

    private fun stepCar(g: RoadGraph, a: Agent, index: Int, dt: Float, bx: Float, by: Float) {
        if (a.edge < 0) return
        val e = g.edges[a.edge]
        val toNode = g.nodes[e.other(a.from)]
        val sEnd = e.len - clearAt(e, toNode.id)
        if (!a.inJunction && a.next < 0) {
            a.next = chooseNext(g, toNode, e, true)
            val ne = g.edges[a.next]
            val turn = if (a.next == e.id) Math.PI.toFloat() else turnAngle(g, toNode, e, ne)
            // turning toward the near side uses the outer lane, toward the far side the inner one
            val nearSide = if (leftHand) turn < -.5f else turn > .5f
            val farSide = if (leftHand) turn > .5f else turn < -.5f
            a.nextLane = when {
                nearSide -> ne.cls.lanes - 1
                farSide -> 0
                else -> min(a.lane, ne.cls.lanes - 1)
            }
            a.timer = abs(turn)
            a.turn = turn
        }
        // ease across into a new lane
        if (a.shift != 0f) a.shift = if (abs(a.shift) < LANE_CHANGE * dt) 0f else a.shift - kotlin.math.sign(a.shift) * LANE_CHANGE * dt
        if (!a.inJunction && a.shift == 0f && e.cls.lanes > 1) changeLanes(g, a, e, sEnd - a.s)
        var target = a.cruise * speedFactor
        val fwdX = sin(a.heading); val fwdY = -cos(a.heading)
        var atLight = false
        var blockedBy = -1
        // how far to where it stops for the junction ahead, and to the junction itself (when there is one near)
        var lineLeft = -1f; var junctionLeft = -1f
        if (!a.inJunction) {
            val toEnd = sEnd - a.s
            // where the nose stops short of the junction: behind the stop line at lights, behind the crossing elsewhere
            val toStop = toEnd - (if (toNode.signal) STOP_LINE else GIVE_WAY) - a.length * .5f
            // slow down for the turn ahead
            if (a.timer > .5f && toEnd < 22f) target = min(target, if (a.timer > 2.5f) 3.5f else if (a.timer > 1.2f) 5.5f else 7.5f)
            // lights, and giving way where there are none (a junction just past a bend in the road counts as this one's)
            approach(g, a, e, toNode, toEnd)
            val jn = apNode; val je = apEdge!!
            val toLine = apDist - (if (jn.signal) STOP_LINE else GIVE_WAY) - a.length * .5f
            if (jn !== toNode || (clearAt(e, toNode.id) > 0f && toNode.degree >= 3)) {
                if (apDist < 30f) {
                    lineLeft = toLine; junctionLeft = apDist
                    var hold = false
                    val cl = clusterOf(jn.id)
                    // held up short of the line after going for an amber: it waits for the next green after all
                    if (a.through == cl && !a.inJunction && e.other(a.from) == jn.id && !shortLink(e) && a.speed < 1f && toLine > -.5f && light(jn, je) == 2) a.through = -1
                    blocker = -1
                    val yields = a.unstick <= 0f
                    if (a.through == cl) {
                        // on through (it went on amber, or it's already in among junctions working as one): lights
                        // don't stop it now; between junctions of a group, it goes in turn with whoever's in the next
                        hold = yields && shortLink(e) && junctionBusy(a, jn.id) && a.blocked < 3f
                    } else when (light(jn, je)) {
                        // amber: stop if there's room to, else go on through
                        1 -> if (toLine < a.speed * a.speed / 16f) { a.through = cl; a.jGroup = group(jn, je) } else { hold = true; atLight = true }
                        2 -> { hold = true; atLight = true }
                        // green: let the cross traffic that went on amber get out of the way first
                        else -> hold = yields && if (jn.signal) toLine < 4f && crossInBox(a, jn, je) && a.blocked < 6f
                            else junctionBusy(a, jn.id) && a.blocked < 3f
                    }
                    // turning across the oncoming lanes: wait for a gap
                    if (!hold && yields && jn === toNode && a.next >= 0 && farSide(a.turn) && toLine < 5f && a.blocked < 12f && oncomingInWay(g, a, e, toNode)) hold = true
                    // nor across the way of a car from the next lane over, gone in ahead (it couldn't get into the right lane)
                    if (!hold && yields && jn === toNode && a.next >= 0 && toLine < 3f && a.blocked < 8f && crossesNeighbour(a, e, toNode)) hold = true
                    if (hold) {
                        target = min(target, toLine.coerceAtLeast(0f) * .9f)
                        if (!atLight && blocker >= 0) blockedBy = blocker
                    }
                }
            } else if (toNode.degree <= 1 && toEnd < 14f && turningAt(a, toNode.id)) {
                // a dead end: one car turns round at a time
                target = min(target, (toEnd - 1.2f).coerceAtLeast(0f) * .9f)
            }
            // a car from another approach still turning into the lane we're about to take comes out first, and
            // nobody turns into a lane that's backed up to the junction
            if (toEnd < 12f + a.length && a.unstick <= 0f && mustWaitAt(g, a, e, toNode.id, sEnd, index)) {
                target = min(target, toStop.coerceAtLeast(0f) * .9f)
                if (blocker >= 0) blockedBy = blocker
            }
        }
        // keep distance to whatever is ahead
        var heldByCross = false
        for (j in cars.indices) {
            val o = cars[j]
            if (o === a || !o.placed) continue
            val dx = o.x - a.x; val dy = o.y - a.y
            if (abs(dx) > 18f || abs(dy) > 18f) continue
            var ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            val align = fwdX * sin(o.heading) - fwdY * cos(o.heading)
            // (let go from a jam, a car still keeps behind the one straight ahead of it: it's the others it gives up on)
            if (a.unstick > 0f && !(align > .9f && side < 1f)) continue
            // two cars come out of a junction (a U-turn, a merge) on the same spot: the first in the list drives
            // on, the other waits for it, so they don't carry on as one
            if (abs(ahead) < 1.5f && side < 1.2f && align > .6f) {
                if (j > index) continue
                ahead = .01f
            }
            // (one moving across into this lane is in it already, as far as whoever's behind is concerned)
            val merging = !a.inJunction && !o.inJunction && o.edge == a.edge && o.from == a.from && o.lane == a.lane && o.shift != 0f
            if (ahead <= 0 || ahead >= 15f || (!merging && side >= (o.width + a.width) * .5f + .25f)) continue
            // two that each have the other ahead (side by side across each other's way in a junction): the first in
            // the list goes, the other waits for it
            if (j > index) {
                val ofx = sin(o.heading); val ofy = -cos(o.heading)
                val back = -dx * ofx - dy * ofy
                if (back > 0f && abs(dx * ofy - dy * ofx) < (o.width + a.width) * .5f + .25f) continue
            }
            // (held up by one at an angle: what used to let it creep through after a while, now [breakDeadlocks] does)
            if (align < .6f) heldByCross = true
            val keep = (ahead - (a.length + o.length) * .5f - 2f).coerceAtLeast(0f) * 1.3f
            if (keep < target) { target = keep; blockedBy = j }
        }
        // (waiting at a red light isn't being held up)
        a.blocked = if (atLight) 0f
            else if ((heldByCross || target < .3f) && a.speed < .5f) a.blocked + dt else if (a.blocked > 2.5f && a.blocked < 6f) a.blocked + dt else 0f
        // people on foot anywhere along the way it's about to drive, round the corner too: stop short of them
        val look = 7f + a.length * .5f + a.speed * a.speed / 10f
        val person = personAhead(g, a, look)
        if (person < look) {
            var stopAt = person - a.length * .5f - 1.6f
            // someone on the crossing just ahead: wait behind the line for them, not nosed up to them on the crossing
            if (lineLeft >= 0f && person < junctionLeft + 1f) stopAt = min(stopAt, lineLeft)
            target = min(target, stopAt.coerceAtLeast(0f) * 1.2f)
        }
        run {
            val dx = bx - a.x; val dy = by - a.y
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 12f && side < 2.2f) target = min(target, (ahead - 5f).coerceAtLeast(0f))
        }
        for (k in 0 until obstacleCount) {
            val dx = obstacles[k * 2] - a.x; val dy = obstacles[k * 2 + 1] - a.y
            if (abs(dx) > 14f || abs(dy) > 14f) continue
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 12f && side < 2.2f) target = min(target, (ahead - 5.5f).coerceAtLeast(0f))
        }
        if (a.unstick > 0f) a.unstick -= dt
        // gentle acceleration, firm braking
        val brake = if (target < a.speed - 4f) 16f else 9f
        a.speed += (target - a.speed).coerceIn(-brake * dt, 4.5f * dt)
        if (a.speed < .05f && target <= 0f) a.speed = 0f
        a.waitOn = if (a.speed < .3f && target < .3f) blockedBy else -1
        move(g, a, a.speed * dt)
    }

    private val cv = FloatArray(6)
    private val cvA = FloatArray(2); private val cvB = FloatArray(2)

    /**
     * The curve through the junction at the end of [e] (driven from node [from] in lane [lane]) on to [ne] (from [nFrom])
     * in [nextLane]: start, control point and end (x, y each) into [out].
     */
    private fun curve(g: RoadGraph, e: RoadEdge, from: Int, lane: Int, ne: RoadEdge, nFrom: Int, nextLane: Int, out: FloatArray, shift: Float = 0f) {
        val to = e.other(from)
        at(e, from, e.len - clearAt(e, to), laneOffset(e, lane) + shift, cvA)
        at(ne, nFrom, clearAt(ne, nFrom), laneOffset(ne, nextLane), cvB)
        val x0 = cvA[0]; val y0 = cvA[1]; val x1 = cvB[0]; val y1 = cvB[1]
        out[0] = x0; out[1] = y0; out[4] = x1; out[5] = y1
        val d0x = if (from == e.a) e.ux else -e.ux; val d0y = if (from == e.a) e.uy else -e.uy
        val d1x = if (nFrom == ne.a) ne.ux else -ne.ux; val d1y = if (nFrom == ne.a) ne.uy else -ne.uy
        // control point where the two lane lines meet (or half way for straight crossings)
        val den = d0x * d1y - d0y * d1x
        out[2] = (x0 + x1) * .5f; out[3] = (y0 + y1) * .5f
        if (abs(den) > .15f) {
            val t = ((x1 - x0) * d1y - (y1 - y0) * d1x) / den
            if (t > 0f && t < 60f) { out[2] = x0 + d0x * t; out[3] = y0 + d0y * t }
        } else if (ne.id == e.id) {
            // U-turn at a dead end: swing out past the end
            out[2] = g.nodes[to].x + d0x * 6f; out[3] = g.nodes[to].y + d0y * 6f
        }
    }

    /** Which walkers are out on the road this step (crossing it, or straying on to it): only they're in a car's way. */
    private var pedOnRoad = BooleanArray(0)
    private val nearX = FloatArray(24); private val nearY = FloatArray(24); private val nearI = IntArray(24)
    internal var lastPerson = -1
    private val pathC = FloatArray(6); private val pathP = FloatArray(2)

    /**
     * How far along the way car [a] is about to drive (what's left of its lane, its curve through the junction, the
     * start of the road after) someone on foot stands in it, within [look] meters; more than [look] if nobody does.
     */
    /** Which walker (by index) car [a] is waiting on, if any: for the street audit. */
    internal fun personAheadOf(a: Agent): Int { lastPerson = -1; val g = roads ?: return -1; personAhead(g, a, 7f + a.length * .5f + 3f); return lastPerson }
    private fun personAhead(g: RoadGraph, a: Agent, look: Float): Float {
        var n = 0
        for (i in peds.indices) {
            if (!pedOnRoad.getOrElse(i) { false }) continue
            val p = peds[i]
            if (abs(p.x - a.x) > look + 3f || abs(p.y - a.y) > look + 3f) continue
            if (n < nearX.size) { nearX[n] = p.x; nearY[n] = p.y; nearI[n] = i; n++ }
        }
        if (n == 0) return look + 1f
        val r = a.width * .5f + .6f
        // the stretches of the way: straight on in this lane, the curve, straight on in the next lane
        val e = g.edges[a.edge]
        val straight: Float; val curveT0: Float; val curveLen: Float; val nextEdge: RoadEdge?; val nextFrom: Int; val nextLane: Int
        if (a.inJunction) {
            straight = 0f; curveT0 = a.jt; curveLen = a.jLen
            pathC[0] = a.jx0; pathC[1] = a.jy0; pathC[2] = a.jcx; pathC[3] = a.jcy; pathC[4] = a.jx1; pathC[5] = a.jy1
            nextEdge = e; nextFrom = a.from; nextLane = a.lane
        } else {
            val to = e.other(a.from)
            straight = e.len - clearAt(e, to) - a.s
            val ne = if (a.next >= 0) g.edges[a.next] else null
            if (ne != null && (clearAt(e, to) > 0f || clearAt(ne, to) > 0f || ne.id == e.id)) {
                val lane = a.nextLane.coerceIn(0, ne.cls.lanes - 1)
                curve(g, e, a.from, a.lane, ne, to, lane, pathC)
                curveT0 = 0f
                curveLen = (dist(pathC[0], pathC[1], pathC[2], pathC[3]) + dist(pathC[2], pathC[3], pathC[4], pathC[5])).coerceAtLeast(1f)
                nextEdge = ne; nextFrom = to; nextLane = lane
            } else { curveT0 = 1f; curveLen = 0f; nextEdge = null; nextFrom = -1; nextLane = 0 }
        }
        var d = .5f
        while (d <= look) {
            val c = d - straight
            if (c <= 0f) at(e, a.from, a.s + d, laneOffset(e, a.lane), pathP)
            else if (curveLen > 0f && curveT0 + c / curveLen <= 1f) {
                val t = curveT0 + c / curveLen; val u = 1f - t
                pathP[0] = u * u * pathC[0] + 2 * u * t * pathC[2] + t * t * pathC[4]
                pathP[1] = u * u * pathC[1] + 2 * u * t * pathC[3] + t * t * pathC[5]
            } else if (nextEdge != null) {
                val past = c - (1f - curveT0) * curveLen
                at(nextEdge, nextFrom, min(clearAt(nextEdge, nextFrom) + past, nextEdge.len), laneOffset(nextEdge, nextLane), pathP)
            } else break
            for (k in 0 until n) {
                val dx = nearX[k] - pathP[0]; val dy = nearY[k] - pathP[1]
                if (dx * dx + dy * dy < r * r) { lastPerson = nearI[k]; return d }
            }
            d += 1f
        }
        return look + 1f
    }

    /** How far a turn goes toward the far side: 0 across the oncoming lanes, 1 straight on, 2 toward the curb. */
    private fun turnRank(turn: Float) = if (farSide(turn)) 0 else if (abs(turn) < .5f) 1 else 2

    /**
     * True when a car that came from the same road into [node] just before [a], from another lane, is still in the
     * junction and the two ways cross (the one nearer the centre line turning toward the curb, or the other way round).
     */
    private fun crossesNeighbour(a: Agent, e: RoadEdge, node: RoadNode): Boolean {
        val mine = turnRank(a.turn)
        for (i in cars.indices) {
            val o = cars[i]
            if (o === a || !o.placed || !o.inJunction || o.jNode != node.id || o.jEdge != e.id || o.jt > .85f || o.jLane == a.lane) continue
            val theirs = turnRank(o.turn)
            if ((o.jLane < a.lane && theirs > mine) || (o.jLane > a.lane && theirs < mine)) { blocker = i; return true }
        }
        return false
    }

    /** A turn across the oncoming lanes (left where traffic keeps right). */
    private fun farSide(turn: Float) = if (leftHand) turn > .5f else turn < -.5f

    /** The junction a car on [e] comes to next, past any bends in the road: its node, the road into it and how far to its edge. */
    private var apNode: RoadNode = RoadNode(-1, 0f, 0f)
    private var apEdge: RoadEdge? = null
    private var apDist = 0f

    private fun approach(g: RoadGraph, a: Agent, e: RoadEdge, toNode: RoadNode, toEnd: Float) {
        var n = toNode; var ed = e; var d = toEnd; var guard = 0
        while (n.degree == 2 && clearAt(ed, n.id) <= 0f && d < 40f && guard++ < 6) {
            val nx = g.edges[if (n.edges[0] == ed.id) n.edges[1] else n.edges[0]]
            if (clearAt(nx, n.id) > 0f) break
            val far = nx.other(n.id)
            d += nx.len - clearAt(nx, far)
            ed = nx; n = g.nodes[far]
        }
        apNode = n; apEdge = ed; apDist = d
    }

    /**
     * True when a car turning across the oncoming lanes at [node] should wait: an oncoming car is in the junction, or is
     * coming up to it and not about to stop.
     */
    private fun oncomingInWay(g: RoadGraph, a: Agent, e: RoadEdge, node: RoadNode): Boolean {
        val ix = if (node.id == e.b) e.ux else -e.ux; val iy = if (node.id == e.b) e.uy else -e.uy
        fun oncoming(oe: RoadEdge): Boolean {
            val ox = if (node.id == oe.b) oe.ux else -oe.ux; val oy = if (node.id == oe.b) oe.uy else -oe.uy
            return oe.id != e.id && ix * ox + iy * oy < -.85f
        }
        for (i in cars.indices) {
            val o = cars[i]
            if (o === a || !o.placed) continue
            if (o.inJunction) {
                if (o.jNode == node.id && o.jEdge >= 0 && o.jt < .8f && oncoming(g.edges[o.jEdge])) { blocker = i; return true }
                continue
            }
            if (o.edge < 0) continue
            val oe = g.edges[o.edge]
            if (oe.other(o.from) != node.id || !oncoming(oe)) continue
            val oTo = oe.len - clearAt(oe, node.id) - o.s
            // one turning across too passes on the inside, and one at its own red light isn't coming
            if (o.next >= 0 && farSide(o.turn)) continue
            if (oTo < 5f + o.speed * 2.8f && o.speed > 1.5f && light(node, oe) != 2) { blocker = i; return true }
        }
        return false
    }

    /**
     * On a road with more than one lane each way: moves into the lane its next turn needs, or out from behind a slow
     * vehicle, when the lane alongside is clear and there's room before the junction.
     */
    private fun changeLanes(g: RoadGraph, a: Agent, e: RoadEdge, toEnd: Float) {
        if (toEnd < 22f || a.next < 0) return
        val lanes = e.cls.lanes
        val want = when {
            farSide(a.turn) -> 0
            a.timer > .5f -> lanes - 1
            else -> -1
        }
        var to = -1
        if (want >= 0 && want != a.lane) to = a.lane + kotlin.math.sign((want - a.lane).toFloat()).toInt()
        else if (want < 0 && toEnd > 45f) {
            // stuck behind something slow (a tricycle, a bus pulling off): pass it in the next lane
            for (o in cars) {
                if (o === a || !o.placed || o.inJunction || o.edge != a.edge || o.from != a.from || o.lane != a.lane) continue
                val gap = o.s - a.s - (a.length + o.length) * .5f
                if (gap in 0f..10f && o.speed > 1f && o.speed < a.cruise * speedFactor - 2f) {
                    to = if (a.lane > 0) a.lane - 1 else 1
                    break
                }
            }
        }
        if (to < 0 || to >= lanes || to == a.lane) return
        // the lane alongside has to be clear, a car's length or two either way
        for (o in cars) {
            if (o === a || !o.placed || o.inJunction || o.edge != a.edge || o.from != a.from) continue
            if (o.lane != to && !(o.shift != 0f && o.lane == a.lane)) continue
            val gap = abs(o.s - a.s) - (a.length + o.length) * .5f
            if (gap < 4f + max(0f, (if (o.s < a.s) o.speed - a.speed else a.speed - o.speed)) * 1.5f) return
        }
        a.shift = laneOffset(e, a.lane) - laneOffset(e, to)
        a.lane = to
        if (want < 0) a.nextLane = min(a.lane, g.edges[a.next].cls.lanes - 1)
    }

    /**
     * True when a car that came into junction [node] (or the group of junctions it works with) across [e]'s way, from
     * the other light group, is still in this junction or about to cross it.
     */
    private fun crossInBox(a: Agent, node: RoadNode, e: RoadEdge): Boolean {
        val cl = clusterOf(node.id)
        val mine = group(node, e)
        val g = roads ?: return false
        for (i in cars.indices) {
            val o = cars[i]
            if (o === a || !o.placed || o.through != cl || o.jGroup == mine) continue
            // in this junction (not just leaving it), or on a link from another of the group into it
            if (o.inJunction) { if (o.jNode == node.id && o.jt < .9f) { blocker = i; return true } }
            else if (o.edge >= 0 && shortLink(g.edges[o.edge]) && g.edges[o.edge].other(o.from) == node.id) { blocker = i; return true }
        }
        return false
    }

    /** The car the last of the checks below found in the way (its index), or -1. */
    private var blocker = -1

    /** True when another car is crossing the junction now (unsignalled junctions are first come, first served). */
    private fun junctionBusy(a: Agent, node: Int): Boolean {
        for (i in cars.indices) { val o = cars[i]; if (o !== a && o.inJunction && o.jNode == node && o.edge != a.edge) { blocker = i; return true } }
        return false
    }

    /** True when another car is in the middle of turning at [node]. */
    private fun turningAt(a: Agent, node: Int): Boolean {
        for (i in cars.indices) { val o = cars[i]; if (o !== a && o.inJunction && o.jNode == node) return true }
        return false
    }

    /**
     * True when [a] should hold at the line before turning at [node]: a car from another approach is still turning
     * into the lane it's heading for, or that lane is backed up right to the junction (don't block the box).
     */
    private fun mustWaitAt(g: RoadGraph, a: Agent, e: RoadEdge, node: Int, sEnd: Float, index: Int): Boolean {
        if (a.next < 0) return false
        val ne = g.edges[a.next]
        val lane = a.nextLane.coerceIn(0, ne.cls.lanes - 1)
        val room = clearAt(ne, node) + a.length * .5f + 1f
        var mine = false
        for (i in cars.indices) {
            val o = cars[i]
            // one from another road (or another lane of this one) about to go into the same lane at the same moment:
            // the first in the list goes
            if (i < index && o.placed && !o.inJunction && o.edge >= 0 && (o.edge != a.edge || o.from != a.from || o.lane != a.lane) && o.next == a.next &&
                o.nextLane.coerceIn(0, ne.cls.lanes - 1) == lane && g.edges[o.edge].other(o.from) == node &&
                g.edges[o.edge].len - clearAt(g.edges[o.edge], node) - o.s < 3f + o.length * .5f && sEnd - a.s < 3f + a.length * .5f) { blocker = i; return true }
            if (o === a || o.edge != a.next || o.lane != lane) continue
            if (!o.inJunction) {
                if (o.from == node && o.s < room + o.length * .5f && o.speed < 2f) { blocker = i; return true }
                continue
            }
            if (o.jNode != node) continue
            // one that's been stuck a while is likely waiting for us
            if (o.speed < .3f && o.blocked > 2f) continue
            // where our own curve would start: a car that started there is just ahead of us in the queue
            if (!mine) { at(e, a.from, sEnd, laneOffset(e, a.lane), tmp2); mine = true }
            if (abs(o.jx0 - tmp2[0]) > 1f || abs(o.jy0 - tmp2[1]) > 1f) { blocker = i; return true }
        }
        return false
    }

    /** Moves a car [d] meters along its lane, through junctions and on to the next road piece. */
    private fun move(g: RoadGraph, a: Agent, dIn: Float) {
        var d = dIn
        var guard = 0
        while (guard++ < 4) {
            if (a.inJunction) {
                a.jt += d / a.jLen
                if (a.jt < 1f) {
                    val t = a.jt; val u = 1f - t
                    a.x = u * u * a.jx0 + 2 * u * t * a.jcx + t * t * a.jx1
                    a.y = u * u * a.jy0 + 2 * u * t * a.jcy + t * t * a.jy1
                    val tx = 2 * u * (a.jcx - a.jx0) + 2 * t * (a.jx1 - a.jcx)
                    val ty = 2 * u * (a.jcy - a.jy0) + 2 * t * (a.jy1 - a.jcy)
                    if (tx * tx + ty * ty > 1e-6f) a.heading = atan2(tx, -ty)
                    return
                }
                // out the other side (still in the group if this is a short link on to another of its junctions)
                d = (a.jt - 1f) * a.jLen
                val ne = g.edges[a.edge]
                a.inJunction = false
                if (!shortLink(ne) || clusterOf(ne.other(a.from)) != a.through) a.through = -1
                a.s = clearAt(ne, a.from)
                a.next = -1
                continue
            }
            val e = g.edges[a.edge]
            val to = e.other(a.from)
            val sEnd = e.len - clearAt(e, to)
            a.s += d
            if (a.s < sEnd) {
                at(e, a.from, a.s, laneOffset(e, a.lane) + a.shift, tmp)
                a.x = tmp[0]; a.y = tmp[1]
                // angled a little toward the lane it's moving into
                a.heading = headingOf(e, a.from) + if (a.shift == 0f) 0f else atan2(-kotlin.math.sign(a.shift) * LANE_CHANGE, max(a.speed, 2f))
                return
            }
            d = a.s - sEnd
            if (a.next < 0) a.next = chooseNext(g, g.nodes[to], e, true)
            val ne = g.edges[a.next]
            val nFrom = if (a.next == e.id) to else to
            if (clearAt(e, to) <= 0f && clearAt(ne, to) <= 0f && a.next != e.id) {
                // a bend in the road: carry on (the same distance off the lane's middle if it's changing lanes)
                val lane = a.nextLane.coerceIn(0, ne.cls.lanes - 1)
                if (a.shift != 0f || lane != a.lane) a.shift = (laneOffset(e, a.lane) + a.shift - laneOffset(ne, lane)).coerceIn(-ne.cls.laneWidth, ne.cls.laneWidth)
                a.edge = ne.id; a.from = nFrom; a.lane = lane; a.s = 0f; a.next = -1
                continue
            }
            // a curve through the junction, from the end of this lane to the start of the next
            val lane = a.nextLane.coerceIn(0, ne.cls.lanes - 1)
            curve(g, e, a.from, a.lane, ne, nFrom, lane, cv, a.shift)
            a.shift = 0f
            a.jx0 = cv[0]; a.jy0 = cv[1]; a.jcx = cv[2]; a.jcy = cv[3]; a.jx1 = cv[4]; a.jy1 = cv[5]
            a.jLen = (dist(a.jx0, a.jy0, a.jcx, a.jcy) + dist(a.jcx, a.jcy, a.jx1, a.jy1)).coerceAtLeast(1f)
            a.jt = 0f
            a.jNode = to
            a.jEdge = e.id
            a.jLane = a.lane
            a.inJunction = true
            // into a junction (or a group working as one) from outside it: through it now, in this light group
            val cl = clusterOf(to)
            if (a.through != cl) { a.through = cl; a.jGroup = group(g.nodes[to], e) }
            a.edge = ne.id; a.from = nFrom; a.lane = lane
        }
    }

    // ------------------------------------------------------------------ pedestrians

    private fun stepPed(g: RoadGraph, a: Agent, dt: Float) {
        if (a.edge < 0) return
        when (a.state) {
            3 -> { // walking in through a shop door
                if (walkTo(a, dt, a.tx, a.ty, a.cruise)) { a.state = 4; a.hidden = true; a.timer = 6f + rnd.nextFloat() * 22f }
                return
            }
            4 -> { // shopping inside
                a.timer -= dt
                if (a.timer <= 0f) {
                    a.hidden = false
                    a.state = 5
                    if (rnd.nextBoolean()) { val e = g.edges[a.edge]; a.from = e.other(a.from); a.s = e.len - a.s; a.side = -a.side }
                    a.heading += Math.PI.toFloat()
                }
                return
            }
            5 -> { // back out onto the sidewalk
                val e = g.edges[a.edge]
                at(e, a.from, a.s, clearOffset(e, a.from, a.side, a.s, pedOffset(e, a), clearOf(a)) * a.side, tmp)
                if (walkTo(a, dt, tmp[0], tmp[1], a.cruise)) { a.state = 0; a.timer = 8f }
                return
            }
            1, 2 -> { crossing(g, a, dt); return }
            6 -> { // stopped on the sidewalk: reading a message, or looking at a shop window
                a.speed = 0f
                a.pause -= dt
                if (a.activity == 2) a.heading = a.walkHeading + a.side * 1.5708f
                if (a.pause <= 0f) { a.state = 0; a.activity = 0; a.heading = a.walkHeading }
                return
            }
        }
        val e = g.edges[a.edge]
        val to = g.nodes[e.other(a.from)]
        val sEnd = walkEnd(g, e, a.from, a.side)
        var speed = a.cruise * hurry
        // make room: step aside for people coming the other way, slow behind slower walkers
        var nearest = 99f
        val fx = sin(a.heading); val fy = -cos(a.heading)
        for (o in peds) {
            if (o === a || o.hidden || !o.placed) continue
            val dx = o.x - a.x; val dy = o.y - a.y
            if (abs(dx) > 3f || abs(dy) > 3f) continue
            val ahead = dx * fx + dy * fy
            val side = abs(-dx * fy + dy * fx)
            if (ahead <= 0f || ahead > 2.5f || side > .8f) continue
            val facing = fx * sin(o.heading) - fy * cos(o.heading)
            if (facing < 0f) a.dodge = min(a.dodge + dt * 2.2f, .9f)
            else if (o.speed < speed) { if (ahead < 1.1f) speed = min(speed, o.speed); a.dodge = min(a.dodge + dt * 1.2f, .9f) }
            nearest = min(nearest, ahead)
        }
        if (nearest > 2.5f) a.dodge = max(0f, a.dodge - dt * .6f)
        // drop into an open shop now and then
        a.timer -= dt
        if (a.timer <= 0f) {
            a.timer = .5f
            for (k in 0 until doorCount) {
                val dx = doors[k * 5]; val dy = doors[k * 5 + 1]
                if (abs(dx - a.x) < 3f && abs(dy - a.y) < 3f && !a.jog && a.companion != 1 && rnd.nextFloat() < doors[k * 5 + 2]) {
                    a.state = 3
                    // a couple of meters past the door step is inside the building
                    a.tx = dx + doors[k * 5 + 3] * 2.4f; a.ty = dy + doors[k * 5 + 4] * 2.4f
                    return
                }
            }
        }
        // now and then someone stops to check their phone or look in a window (never mid-run)
        if (!a.jog && nearest > 2f && rnd.nextFloat() < dt * .012f) {
            a.state = 6
            a.activity = if (rnd.nextFloat() < .6f) 1 else 2
            a.pause = 3f + rnd.nextFloat() * 6f
            a.walkHeading = a.heading
            return
        }
        a.speed += (speed - a.speed).coerceIn(-4f * dt, 3f * dt)
        a.phase += dt * a.speed * (if (a.jog) 2.4f else 3f)
        a.s += a.speed * dt
        if (a.s >= sEnd) {
            planCorner(g, a, e, to)
            return
        }
        val want = max(pedOffset(e, a) - a.dodge, e.cls.halfWidth + .3f)
        var off = clearOffset(e, a.from, a.side, a.s, want, clearOf(a))
        // no room to pass side by side: the dog or child falls in behind and they pass as one
        var tight = a.companion != 0 && squeezed
        if (tight) off = clearOffset(e, a.from, a.side, a.s, want)
        if (a.companion == 1 && !tight) {
            // the dog trots on ahead, and gets there first
            val dog = clearOffset(e, a.from, a.side, a.s + .75f, want, clearOf(a))
            if (squeezed) { tight = true; off = clearOffset(e, a.from, a.side, a.s, want) }
            else if (abs(dog - want) > abs(off - want)) off = dog
        }
        a.tuck = (a.tuck + (if (tight) dt else -dt) * 2.5f).coerceIn(0f, 1f)
        at(e, a.from, a.s, off * a.side, tmp)
        val hx = tmp[0] - a.x; val hy = tmp[1] - a.y
        if (hx * hx + hy * hy > 1e-6f) a.heading = atan2(hx, -hy)
        a.x = tmp[0]; a.y = tmp[1]
    }

    /** At the end of a sidewalk: pick the next road and walk to its sidewalk, crossing the road between if needed. */
    private fun planCorner(g: RoadGraph, a: Agent, e: RoadEdge, node: RoadNode) {
        if (node.degree <= 1) {
            // dead end: turn round and walk back on the other side
            a.from = node.id; a.s = e.len - a.s; a.side = -a.side
            return
        }
        var nextId = chooseNext(g, node, e, false)
        var ne = g.edges[nextId]
        // stay on whichever side of the next road we're already on
        val ox = if (node.id == ne.a) ne.ux else -ne.ux; val oy = if (node.id == ne.a) ne.uy else -ne.uy
        val rx = -oy; val ry = ox
        var side = if ((a.x - node.x) * rx + (a.y - node.y) * ry >= 0f) 1 else -1
        // at a scramble half the crowd cuts straight across to the far side, through the middle
        if (node.scramble && rnd.nextFloat() < .5f) side = -side
        fun target(edge: RoadEdge, sd: Int) {
            val st = walkStart(g, edge, node.id, sd)
            at(edge, node.id, st, clearOffset(edge, node.id, sd, st, pedOffset(edge, a), clearOf(a)) * sd, tmp)
        }
        target(ne, side)
        if (wetWalk(a.x, a.y, tmp[0], tmp[1])) {
            // the way round the corner runs over water (the shore cuts the pavement): the far side of that road, another
            // road, or else back the way they came
            var found = false
            for (id in listOf(nextId) + node.edges) {
                val f = g.edges[id]
                if (f.id == e.id && node.degree > 1) continue
                for (sd in intArrayOf(side, -side)) {
                    target(f, sd)
                    if (!wetWalk(a.x, a.y, tmp[0], tmp[1])) { nextId = id; ne = f; side = sd; found = true; break }
                }
                if (found) break
            }
            if (!found) { a.from = node.id; a.s = e.len - a.s; a.side = -a.side; return }
        }
        a.next = nextId
        a.nextLane = side
        a.tx = tmp[0]; a.ty = tmp[1]
        a.roundX = Float.NaN; a.rounds = 0; a.roundOf = null
        a.jNode = node.id
        // which road (if any) the walk crosses
        a.lane = -1
        // (the road they've walked along, or the one they're heading on to, if the corner they're going to is across it)
        for (id in node.edges.sortedBy { if (it == nextId || it == e.id) 1 else 0 }) {
            val f = g.edges[id]
            val fx = if (node.id == f.a) f.ux else -f.ux; val fy = if (node.id == f.a) f.uy else -f.uy
            val reach = f.len
            if (Geo.segHit(a.x, a.y, a.tx, a.ty, node.x, node.y, node.x + fx * reach, node.y + fy * reach, tmp2)) { a.lane = id; break }
        }
        // at a scramble every walk off the curb waits for the walk signal, the crowd spreading along the curb
        val off = node.scramble && dist(a.x, a.y, a.tx, a.ty) > 6f
        a.leg = 2
        if (a.lane >= 0 && !node.scramble && !shortLink(g.edges[a.lane])) {
            // anywhere else the road's crossed on its crossing, just out from the junction: first along the curb to its
            // near end, wait there for the lights, over to the far end, then on round the corner
            val f = g.edges[a.lane]
            val sc = min(clearAt(f, node.id) + 1.85f, f.len * .5f)
            val fx = if (node.id == f.a) f.ux else -f.ux; val fy = if (node.id == f.a) f.uy else -f.uy
            val lateral = (a.x - node.x) * -fy + (a.y - node.y) * fx
            // (waiting a step back from the curb, clear of cars cutting the corner)
            val edge = f.cls.halfWidth + .9f
            at(f, node.id, sc, if (lateral >= 0f) edge else -edge, tmp2)
            a.wx0 = tmp2[0]; a.wy0 = tmp2[1]
            at(f, node.id, sc, if (lateral >= 0f) -edge else edge, tmp2)
            a.wx1 = tmp2[0]; a.wy1 = tmp2[1]
            if (!wetWalk(a.x, a.y, a.wx0, a.wy0) && !wetWalk(a.wx1, a.wy1, a.tx, a.ty)) a.leg = 0
        }
        // round the pavement's corner rather than straight over the junction's asphalt
        a.c0x = Float.NaN; a.c2x = Float.NaN
        if (a.leg == 0) {
            val f = g.edges[a.lane]
            if (cornerVia(g, node, e, a.x, a.y, f, a.wx0, a.wy0, tmp2)) { a.c0x = tmp2[0]; a.c0y = tmp2[1] }
            if (cornerVia(g, node, f, a.wx1, a.wy1, ne, a.tx, a.ty, tmp2)) { a.c2x = tmp2[0]; a.c2y = tmp2[1] }
        } else if (a.lane < 0 && cornerVia(g, node, e, a.x, a.y, ne, a.tx, a.ty, tmp2)) { a.c2x = tmp2[0]; a.c2y = tmp2[1] }
        a.state = if ((a.lane >= 0 || off) && node.signal && a.leg == 2) 1 else 2
        if (off && a.state == 1) {
            // a spot a little back or along, as long as it's still on the pavement
            val wx = a.x - node.x; val wy = a.y - node.y; val wl = max(.01f, kotlin.math.sqrt(wx * wx + wy * wy))
            for (attempt in 0 until 4) {
                val back = rnd.nextFloat() * 1.8f; val along = (rnd.nextFloat() * 2f - 1f) * 2.2f
                val nx = a.x + wx / wl * back - wy / wl * along; val ny = a.y + wy / wl * back + wx / wl * along
                if (!g.onAsphalt(nx, ny) && !g.nearAsphalt(nx, ny, .4f)) { a.x = nx; a.y = ny; break }
            }
        }
    }

    /**
     * Where the pavements of [ea] (beside which (px, py) stands) and [eb] (beside which (tx, ty) does) meet at the corner
     * of [node], into [out]: true when walking round it keeps off the asphalt that a straight walk from one to the other
     * would cut over (a big junction's open middle).
     */
    private fun cornerVia(g: RoadGraph, node: RoadNode, ea: RoadEdge, px: Float, py: Float, eb: RoadEdge, tx: Float, ty: Float, out: FloatArray): Boolean {
        if (ea.id == eb.id || !walkOverRoad(g, px, py, tx, ty)) return false
        val ax = if (node.id == ea.a) ea.ux else -ea.ux; val ay = if (node.id == ea.a) ea.uy else -ea.uy
        val bx = if (node.id == eb.a) eb.ux else -eb.ux; val by = if (node.id == eb.a) eb.uy else -eb.uy
        // the middle of the pavement each end is on, as lines out from the junction
        val sa = if ((px - node.x) * -ay + (py - node.y) * ax >= 0f) 1f else -1f
        val sb = if ((tx - node.x) * -by + (ty - node.y) * bx >= 0f) 1f else -1f
        val oa = sa * (ea.cls.halfWidth + ea.cls.sidewalk * .5f); val ob = sb * (eb.cls.halfWidth + eb.cls.sidewalk * .5f)
        val a0x = node.x - ay * oa; val a0y = node.y + ax * oa
        val b0x = node.x - by * ob; val b0y = node.y + bx * ob
        val det = bx * ay - ax * by
        if (abs(det) < .25f) return false
        val qx = b0x - a0x; val qy = b0y - a0y
        val t = (bx * qy - qx * by) / det
        val k = (ax * qy - ay * qx) / det
        if (t < -3f || k < -3f || t > 45f || k > 45f) return false
        val wx = a0x + ax * t; val wy = a0y + ay * t
        if (g.onAsphalt(wx, wy) || dist(px, py, wx, wy) + dist(wx, wy, tx, ty) > dist(px, py, tx, ty) * 2.5f + 8f) return false
        if (wetWalk(px, py, wx, wy) || wetWalk(wx, wy, tx, ty)) return false
        out[0] = wx; out[1] = wy
        return true
    }

    /** Whether a straight walk from (x0, y0) to (x1, y1) would go out over a road. */
    private fun walkOverRoad(g: RoadGraph, x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val n = (dist(x0, y0, x1, y1) / 1f).toInt() + 1
        for (k in 1 until n) {
            val t = k / n.toFloat()
            if (g.onAsphalt(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)) return true
        }
        return false
    }

    /** Whether a straight walk from (x0, y0) to (x1, y1) would cross water. */
    private fun wetWalk(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val c = city ?: return false
        val n = (dist(x0, y0, x1, y1) / .5f).toInt() + 1
        for (k in 0..n) {
            val t = k / n.toFloat()
            if (c.inWater(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)) return true
        }
        return false
    }

    private fun crossing(g: RoadGraph, a: Agent, dt: Float) {
        if (a.leg == 0) {
            // along the curb (round the corner) to the crossing, then wait there (at lights) or go straight over
            if (!a.c0x.isNaN()) { if (walkTo(a, dt, a.c0x, a.c0y, a.cruise * hurry)) a.c0x = Float.NaN; return }
            if (walkTo(a, dt, a.wx0, a.wy0, a.cruise * hurry)) {
                a.leg = 1
                a.state = if (g.nodes[a.jNode].signal) 1 else 2
                a.pause = 0f
            }
            return
        }
        if (a.state == 1) {
            // waiting for the walk signal: cars on the road we cross must have red (at a scramble, all of them)
            val node = g.nodes[a.jNode]
            val crossed = g.edges.getOrNull(a.lane)
            // (and only while there's red enough left to get across)
            val go = if (node.scramble) scrambleWalk(node)
                else crossed == null || (light(node, crossed) == 2 && redLeft(node, crossed) > (crossed.cls.halfWidth * 2f + 1f) / (a.cruise * hurry) + 1.5f)
            if (go) a.state = 2 else { a.speed = 0f; return }
        }
        // nobody walks into a car: they let one that's moving go by, and step round the nearer end of one that's stopped
        val viaCorner = a.leg == 2 && !a.c2x.isNaN()
        val gx = if (a.leg == 1) a.wx1 else if (viaCorner) a.c2x else a.tx; val gy = if (a.leg == 1) a.wy1 else if (viaCorner) a.c2y else a.ty
        if (!a.roundX.isNaN()) {
            if (carInWay(a, a.roundX, a.roundY)?.let { it.speed > .8f } == true) { a.speed = 0f; return }
            if (walkTo(a, dt, a.roundX, a.roundY, a.cruise * hurry)) a.roundX = Float.NaN
            return
        }
        // (round any one car twice at most: past that there's no way round it, and they squeeze by)
        val c = carInWay(a, gx, gy)?.takeIf { it !== a.roundOf || a.rounds < 2 }
        if (c != null) {
            if (c.speed > .3f) { a.speed = 0f; return }
            if (c !== a.roundOf) { a.roundOf = c; a.rounds = 0 }
            // just off whichever corner of it on their side makes the shorter way
            val fx = sin(c.heading); val fy = -cos(c.heading); val rx = -fy; val ry = fx
            val reach = c.length * .5f + .7f
            val side = (if ((a.x - c.x) * rx + (a.y - c.y) * ry >= 0f) 1f else -1f) * (c.width * .5f + .7f)
            val px = c.x + rx * side; val py = c.y + ry * side
            val front = dist(a.x, a.y, px + fx * reach, py + fy * reach) + dist(px + fx * reach, py + fy * reach, gx, gy)
            val back = dist(a.x, a.y, px - fx * reach, py - fy * reach) + dist(px - fx * reach, py - fy * reach, gx, gy)
            val k = if (front <= back) reach else -reach
            a.roundX = px + fx * k; a.roundY = py + fy * k
            a.rounds++
            return
        }
        var speed = a.cruise * hurry
        val crossed = g.edges.getOrNull(a.lane)
        val node = g.nodes[a.jNode]
        val late = if (node.scramble) !scrambleWalk(node) else crossed != null && node.signal && light(node, crossed) != 2
        if (late) speed *= 1.6f
        if (a.leg == 1) {
            // over the road on the crossing
            if (walkTo(a, dt, a.wx1, a.wy1, speed)) a.leg = 2
            return
        }
        if (viaCorner) { if (walkTo(a, dt, a.c2x, a.c2y, a.cruise * hurry)) a.c2x = Float.NaN; return }
        if (walkTo(a, dt, a.tx, a.ty, a.cruise * hurry)) {
            val ne = g.edges[a.next]
            a.edge = ne.id
            a.from = a.jNode
            a.side = a.nextLane
            a.s = walkStart(g, ne, a.from, a.side)
            a.state = 0
            a.next = -1
        }
    }

    /** The car standing, or coming, right where walker [a] is about to step on its way to ([gx], [gy]), if any. */
    private fun carInWay(a: Agent, gx: Float, gy: Float): Agent? {
        val dx = gx - a.x; val dy = gy - a.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < .2f) return null
        val px = a.x + dx / d * .8f; val py = a.y + dy / d * .8f
        for (o in cars) {
            if (!o.placed || abs(o.x - px) > 7f || abs(o.y - py) > 7f) continue
            val fx = sin(o.heading); val fy = -cos(o.heading)
            // a moving car is in the way a little further ahead of itself
            val ahead = o.length * .5f + .3f + o.speed * .4f
            val ox = px - o.x; val oy = py - o.y
            val along = ox * fx + oy * fy
            if (along < ahead && along > -o.length * .5f - .3f && abs(-ox * fy + oy * fx) < o.width * .5f + .3f) return o
        }
        return null
    }

    /** Walks straight toward a point; true on arrival. */
    private fun walkTo(a: Agent, dt: Float, tx: Float, ty: Float, speed: Float): Boolean {
        val dx = tx - a.x; val dy = ty - a.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < .15f) return true
        a.heading = atan2(dx, -dy)
        val step = min(d, speed * dt)
        a.x += dx / d * step; a.y += dy / d * step
        a.speed = speed
        a.phase += dt * speed * 3f
        return false
    }

    companion object {
        /** Seconds round a scramble crossing's lights: two road phases, then the walk. */
        const val SCRAMBLE_CYCLE = 48f
        /** The scramble's walk, after three seconds of red for the cars still crossing to get clear. */
        private const val WALK_START = 37f
        private const val WALK_END = 46f
        /** Seconds round an ordinary junction's lights: each direction's green and amber, and a moment of red for both after each. */
        const val CYCLE = 41f
        /** A car stops with its nose this far short of a junction with lights (behind the stop line), and this far without (behind the crossing). */
        private const val STOP_LINE = 4.9f
        private const val GIVE_WAY = 3.6f
        /** A link between junctions shorter than this (between their edges) has no room to wait on. */
        private const val SHORT_LINK = 9f
        /** How fast a car moves across into another lane (m/s sideways). */
        private const val LANE_CHANGE = 1.3f

        /** How far ahead a walker starts stepping round something on the pavement, and how wide a berth they give it. */
        private const val RAMP = 1.6f
        private const val CLEAR = .34f
        /** The dog trots, or the child walks, about half a meter to one side (see [companionAt]). */
        private const val COMPANION = .42f

        /** The original city's car colors. */
        val DEFAULT_PALETTE = intArrayOf(
            c(0xFFC0392B), c(0xFFE0E0E0), c(0xFF2C3E50), c(0xFF7F8C8D), c(0xFF1F3A93), c(0xFFF2F2F2), c(0xFF8E2442),
            c(0xFF212121), c(0xFFB0B7BF), c(0xFFF2C94C), c(0xFF27AE60), c(0xFF6D4C41),
        )
    }

    /** Pushes a circle out of NPC cars. Returns true on contact. */
    fun bump(x: Float, y: Float, r: Float, out: FloatArray): Boolean {
        var px = x; var py = y; var hit = false
        for (a in cars) {
            if (!a.placed) continue
            val dx = px - a.x; val dy = py - a.y
            val minD = r + a.width * .6f
            val d2 = dx * dx + dy * dy
            if (d2 < minD * minD && d2 > 1e-4f) {
                val d = sqrt(d2)
                px = a.x + dx / d * minD
                py = a.y + dy / d * minD
                hit = true
            }
        }
        out[0] = px; out[1] = py
        return hit
    }
}

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
    /** Personal sideways spread on the sidewalk, and a temporary side-step to pass someone. */
    var jitter = 0f
    var dodge = 0f
    var tx = 0f
    var ty = 0f
    var hidden = false
    /** Seconds a car has been held up by cross traffic (used to untangle jams in junctions). */
    var blocked = 0f
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
    }

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

    /** Which light group an approach belongs to at a junction: roads roughly parallel to the first one share a phase. */
    private fun group(g: RoadGraph, n: RoadNode, e: RoadEdge): Int {
        val ref = g.edges[n.edges[0]]
        val a = atan2(ref.uy, ref.ux); val b = atan2(e.uy, e.ux)
        var d = abs(a - b) % Math.PI.toFloat()
        if (d > Math.PI / 2) d = Math.PI.toFloat() - d
        return if (d < Math.PI / 4) 0 else 1
    }

    /** Light for traffic on [edge] entering junction [node]: 0 green, 1 yellow, 2 red (always green without lights). */
    fun light(node: RoadNode, edge: RoadEdge): Int {
        if (!node.signal) return 0
        val g = roads ?: return 0
        if (node.scramble) {
            // two road phases, then every car waits while the crossing fills with people
            val t = (lightClock + node.phase) % SCRAMBLE_CYCLE
            return if (group(g, node, edge) == 0) when { t < 14f -> 0; t < 17f -> 1; else -> 2 }
            else when { t in 17f..31f -> 0; t in 31f..34f -> 1; else -> 2 }
        }
        val t = (lightClock + node.phase) % 40f
        return if (group(g, node, edge) == 0) when { t < 20f -> 0; t < 23f -> 1; else -> 2 }
        else when { t in 23f..37f -> 0; t > 37f -> 1; else -> 2 }
    }

    /** True while a scramble crossing's walk signal is on: all its traffic is held and people cross any way they like. */
    fun scrambleWalk(node: RoadNode): Boolean = node.scramble && node.signal && (lightClock + node.phase) % SCRAMBLE_CYCLE in 34.5f..45f

    /** Seconds until [node]'s scramble walk signal next comes on (0 while it's on). */
    fun untilScrambleWalk(node: RoadNode): Float {
        val t = (lightClock + node.phase) % SCRAMBLE_CYCLE
        return if (t in 34.5f..45f) 0f else ((34.5f - t) % SCRAMBLE_CYCLE + SCRAMBLE_CYCLE) % SCRAMBLE_CYCLE
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
        for (i in cars.indices) {
            val a = cars[i]
            if (initial || !a.placed || dist(a.x, a.y, fx, fy) > radius) spawnCar(g, a, fx, fy, radius, initial || !a.placed)
            stepCar(g, a, i, dt, blockerX, blockerY)
            lean(a, a.speed, a.heading, dt)
        }
        val pr = if (pedRadius > 0f) min(pedRadius, radius * .7f) else radius * .7f
        for (p in peds) {
            if (initial || !p.placed || (dist(p.x, p.y, fx, fy) > pr && p.state != 4)) spawnPed(g, p, fx, fy, pr, initial || !p.placed)
            stepPed(g, p, dt)
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
        }
        var target = a.cruise * speedFactor
        val fwdX = sin(a.heading); val fwdY = -cos(a.heading)
        if (!a.inJunction) {
            val toEnd = sEnd - a.s
            // slow down for the turn ahead
            if (a.timer > .5f && toEnd < 22f) target = min(target, if (a.timer > 2.5f) 3.5f else if (a.timer > 1.2f) 5.5f else 7.5f)
            // lights, and giving way where there are none
            if (clearAt(e, toNode.id) > 0f && toNode.degree >= 3) {
                val l = light(toNode, e)
                if (toEnd in 0f..26f) {
                    var stop = false
                    if (l != 0) stop = l == 2 || toEnd > a.speed * a.speed / 16f
                    else if (!toNode.signal && junctionBusy(a, toNode.id)) stop = a.blocked < 3f
                    if (stop) target = min(target, (toEnd - 1.2f).coerceAtLeast(0f) * .9f)
                }
            } else if (toNode.degree <= 1 && toEnd < 14f && turningAt(a, toNode.id)) {
                // a dead end: one car turns round at a time
                target = min(target, (toEnd - 1.2f).coerceAtLeast(0f) * .9f)
            }
            // a car from another approach still turning into the lane we're about to take comes out first, and
            // nobody turns into a lane that's backed up to the junction
            if (toEnd < 12f && mustWaitAt(g, a, e, toNode.id, sEnd)) target = min(target, (toEnd - 1.2f).coerceAtLeast(0f) * .9f)
        }
        // keep distance to whatever is ahead; if two cars hold each other up in a junction for a while, the
        // waiting one creeps through so nothing locks up
        var heldByCross = false
        for (j in cars.indices) {
            val o = cars[j]
            if (o === a || !o.placed) continue
            val dx = o.x - a.x; val dy = o.y - a.y
            if (abs(dx) > 18f || abs(dy) > 18f) continue
            var ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            val align = fwdX * sin(o.heading) - fwdY * cos(o.heading)
            // two cars come out of a junction (a U-turn, a merge) on the same spot: the first in the list drives
            // on, the other waits for it, so they don't carry on as one
            if (abs(ahead) < 1.5f && side < 1.2f && align > .6f) {
                if (j > index) continue
                ahead = .01f
            }
            if (ahead <= 0 || ahead >= 15f || side >= 1.7f + (o.width - 1.8f) * .5f + (a.width - 1.8f) * .5f) continue
            if (align < .6f) {
                if (a.blocked > 2.5f) continue
                heldByCross = true
            }
            target = min(target, (ahead - (a.length + o.length) * .5f - 2f).coerceAtLeast(0f) * 1.3f)
        }
        a.blocked = if ((heldByCross || target < .3f) && a.speed < .5f) a.blocked + dt else if (a.blocked > 2.5f && a.blocked < 6f) a.blocked + dt else 0f
        // pedestrians crossing in front
        for (p in peds) {
            if (p.hidden || !p.placed) continue
            val dx = p.x - a.x; val dy = p.y - a.y
            if (abs(dx) > 12f || abs(dy) > 12f) continue
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 10f && side < 1.6f) target = min(target, (ahead - a.length * .5f - 1.5f).coerceAtLeast(0f) * 1.2f)
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
        // gentle acceleration, firm braking
        val brake = if (target < a.speed - 4f) 16f else 9f
        a.speed += (target - a.speed).coerceIn(-brake * dt, 4.5f * dt)
        if (a.speed < .05f && target <= 0f) a.speed = 0f
        move(g, a, a.speed * dt)
    }

    /** True when another car is crossing the junction now (unsignalled junctions are first come, first served). */
    private fun junctionBusy(a: Agent, node: Int): Boolean {
        for (o in cars) if (o !== a && o.inJunction && o.jNode == node && o.edge != a.edge) return true
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
    private fun mustWaitAt(g: RoadGraph, a: Agent, e: RoadEdge, node: Int, sEnd: Float): Boolean {
        if (a.next < 0) return false
        val ne = g.edges[a.next]
        val lane = a.nextLane.coerceIn(0, ne.cls.lanes - 1)
        val room = clearAt(ne, node) + a.length * .5f + 1f
        var mine = false
        for (i in cars.indices) {
            val o = cars[i]
            if (o === a || o.edge != a.next || o.lane != lane) continue
            if (!o.inJunction) {
                if (o.from == node && o.s < room + o.length * .5f && o.speed < 2f) return true
                continue
            }
            if (o.jNode != node) continue
            // one that's been stuck a while is likely waiting for us
            if (o.speed < .3f && o.blocked > 2f) continue
            // where our own curve would start: a car that started there is just ahead of us in the queue
            if (!mine) { at(e, a.from, sEnd, laneOffset(e, a.lane), tmp2); mine = true }
            if (abs(o.jx0 - tmp2[0]) > 1f || abs(o.jy0 - tmp2[1]) > 1f) return true
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
                // out the other side
                d = (a.jt - 1f) * a.jLen
                val ne = g.edges[a.edge]
                a.inJunction = false
                a.s = clearAt(ne, a.from)
                a.next = -1
                continue
            }
            val e = g.edges[a.edge]
            val to = e.other(a.from)
            val sEnd = e.len - clearAt(e, to)
            a.s += d
            if (a.s < sEnd) {
                at(e, a.from, a.s, laneOffset(e, a.lane), tmp)
                a.x = tmp[0]; a.y = tmp[1]
                a.heading = headingOf(e, a.from)
                return
            }
            d = a.s - sEnd
            if (a.next < 0) a.next = chooseNext(g, g.nodes[to], e, true)
            val ne = g.edges[a.next]
            val nFrom = if (a.next == e.id) to else to
            if (clearAt(e, to) <= 0f && clearAt(ne, to) <= 0f && a.next != e.id) {
                // a bend in the road: carry on
                a.edge = ne.id; a.from = nFrom; a.lane = a.nextLane.coerceIn(0, ne.cls.lanes - 1); a.s = 0f; a.next = -1
                continue
            }
            // a curve through the junction, from the end of this lane to the start of the next
            at(e, a.from, sEnd, laneOffset(e, a.lane), tmp)
            val lane = a.nextLane.coerceIn(0, ne.cls.lanes - 1)
            at(ne, nFrom, clearAt(ne, nFrom), laneOffset(ne, lane), tmp2)
            a.jx0 = tmp[0]; a.jy0 = tmp[1]; a.jx1 = tmp2[0]; a.jy1 = tmp2[1]
            val d0x = if (a.from == e.a) e.ux else -e.ux; val d0y = if (a.from == e.a) e.uy else -e.uy
            val d1x = if (nFrom == ne.a) ne.ux else -ne.ux; val d1y = if (nFrom == ne.a) ne.uy else -ne.uy
            // control point where the two lane lines meet (or half way for straight crossings)
            val den = d0x * d1y - d0y * d1x
            if (abs(den) > .15f) {
                val qx = a.jx1 - a.jx0; val qy = a.jy1 - a.jy0
                val t = (qx * d1y - qy * d1x) / den
                if (t > 0f && t < 60f) { a.jcx = a.jx0 + d0x * t; a.jcy = a.jy0 + d0y * t } else { a.jcx = (a.jx0 + a.jx1) * .5f; a.jcy = (a.jy0 + a.jy1) * .5f }
            } else if (a.next == e.id) {
                // U-turn at a dead end: swing out past the end
                val nx = g.nodes[to].x; val ny = g.nodes[to].y
                a.jcx = nx + d0x * 6f; a.jcy = ny + d0y * 6f
            } else { a.jcx = (a.jx0 + a.jx1) * .5f; a.jcy = (a.jy0 + a.jy1) * .5f }
            a.jLen = (dist(a.jx0, a.jy0, a.jcx, a.jcy) + dist(a.jcx, a.jcy, a.jx1, a.jy1)).coerceAtLeast(1f)
            a.jt = 0f
            a.jNode = to
            a.inJunction = true
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
        a.jNode = node.id
        // which road (if any) the walk crosses
        a.lane = -1
        for (id in node.edges) {
            if (id == nextId || id == e.id) continue
            val f = g.edges[id]
            val fx = if (node.id == f.a) f.ux else -f.ux; val fy = if (node.id == f.a) f.uy else -f.uy
            val reach = clearAt(f, node.id) + f.cls.halfWidth + 3f
            if (Geo.segHit(a.x, a.y, a.tx, a.ty, node.x, node.y, node.x + fx * reach, node.y + fy * reach, tmp2)) { a.lane = id; break }
        }
        // at a scramble every walk off the curb waits for the walk signal, the crowd spreading along the curb
        val off = node.scramble && dist(a.x, a.y, a.tx, a.ty) > 6f
        a.state = if ((a.lane >= 0 || off) && node.signal) 1 else 2
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
        if (a.state == 1) {
            // waiting for the walk signal: cars on the road we cross must have red (at a scramble, all of them)
            val node = g.nodes[a.jNode]
            val crossed = g.edges.getOrNull(a.lane)
            val go = if (node.scramble) scrambleWalk(node) else crossed == null || light(node, crossed) == 2
            if (go) a.state = 2 else { a.speed = 0f; return }
        }
        var speed = a.cruise * hurry
        val crossed = g.edges.getOrNull(a.lane)
        val node = g.nodes[a.jNode]
        val late = if (node.scramble) !scrambleWalk(node) else crossed != null && node.signal && light(node, crossed) != 2
        if (late) speed *= 1.6f
        if (walkTo(a, dt, a.tx, a.ty, speed)) {
            val ne = g.edges[a.next]
            a.edge = ne.id
            a.from = a.jNode
            a.side = a.nextLane
            a.s = walkStart(g, ne, a.from, a.side)
            a.state = 0
            a.next = -1
        }
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

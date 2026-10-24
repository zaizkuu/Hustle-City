package com.hustlecity.game

import com.hustlecity.game.world.Agent
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A closer look at the street AI than [TrafficTest]: five busy minutes round a junction in every city, counting what a
 * player would notice. Cars overlapping by their real outlines, cars running into people, running red lights, cars
 * standing still for a long time (a jam that never clears); people standing stuck, inside buildings or in the water.
 * Prints a report per city and holds each to a limit. HC_NPC=1 also lists where each kind of trouble happened,
 * HC_NPC_SEEDS=n runs n different mornings per city (no limits then: compare the totals), HC_NPC_MAP=<id> one city.
 */
class NpcAuditTest {
    private val verbose = System.getenv("HC_NPC") == "1"

    @Test
    fun audit() {
        val seeds = System.getenv("HC_NPC_SEEDS")?.toIntOrNull() ?: 1
        for (map in Maps.all) if (System.getenv("HC_NPC_MAP")?.let { it == map.id } != false) {
            val city = City(1337L, map.id)
            for (seed in 0 until seeds) run(city, 5 + seed * 17)
        }
    }

    /** Corners of a car's outline, shrunk a little (bumpers and mirrors can touch). */
    private fun corners(a: Agent, out: FloatArray, shrink: Float = .9f) {
        val fx = sin(a.heading); val fy = -cos(a.heading)
        val rx = -fy; val ry = fx
        val hl = a.length * .5f * shrink; val hw = a.width * .5f * shrink
        var k = 0
        for ((sf, sr) in listOf(1 to 1, 1 to -1, -1 to -1, -1 to 1)) {
            out[k++] = a.x + fx * hl * sf + rx * hw * sr
            out[k++] = a.y + fy * hl * sf + ry * hw * sr
        }
    }

    /** Whether two convex quads overlap (separating axes). */
    private fun overlap(p: FloatArray, q: FloatArray): Boolean {
        for (poly in arrayOf(p, q)) for (i in 0 until 4) {
            val j = (i + 1) % 4
            val nx = -(poly[j * 2 + 1] - poly[i * 2 + 1]); val ny = poly[j * 2] - poly[i * 2]
            var pMin = Float.MAX_VALUE; var pMax = -Float.MAX_VALUE; var qMin = Float.MAX_VALUE; var qMax = -Float.MAX_VALUE
            for (k in 0 until 4) {
                val dp = p[k * 2] * nx + p[k * 2 + 1] * ny; pMin = minOf(pMin, dp); pMax = maxOf(pMax, dp)
                val dq = q[k * 2] * nx + q[k * 2 + 1] * ny; qMin = minOf(qMin, dq); qMax = maxOf(qMax, dq)
            }
            if (pMax < qMin || qMax < pMin) return false
        }
        return true
    }

    private fun inside(a: Agent, x: Float, y: Float, pad: Float): Boolean {
        val fx = sin(a.heading); val fy = -cos(a.heading)
        val dx = x - a.x; val dy = y - a.y
        return abs(dx * fx + dy * fy) < a.length * .5f + pad && abs(-dx * fy + dy * fx) < a.width * .5f + pad
    }

    /** What two cars in the same junction were doing: each one's turn, and how their approaches lie to each other. */
    private fun movement(g: com.hustlecity.game.world.RoadGraph, a: Agent, b: Agent): String {
        fun inDir(x: Agent): Float { val e = g.edges[x.jEdge]; return if (x.jNode == e.b) kotlin.math.atan2(e.uy, e.ux) else kotlin.math.atan2(-e.uy, -e.ux) }
        fun outDir(x: Agent): Float { val e = g.edges[x.edge]; return if (x.from == e.a) kotlin.math.atan2(e.uy, e.ux) else kotlin.math.atan2(-e.uy, -e.ux) }
        fun diff(p: Float, q: Float): Float { var d = q - p; while (d > Math.PI) d -= (2 * Math.PI).toFloat(); while (d < -Math.PI) d += (2 * Math.PI).toFloat(); return d }
        fun turn(x: Agent): String { val d = diff(inDir(x), outDir(x)); return if (abs(d) < .5f) "S" else if (d > 0) "R" else "L" }
        val rel = abs(diff(inDir(a), inDir(b)))
        val how = if (a.jEdge == b.jEdge) "same road" else if (rel > 2.6f) "oncoming" else if (rel < .5f) "same way" else "crossing"
        return "${turn(a)}${turn(b)} $how"
    }

    private fun run(city: City, seed: Int) {
        val t = Traffic(Random(seed))
        t.attach(city)
        t.ensure(44, 96)
        val cx = (city.bounds[0] + city.bounds[2]) / 2; val cy = (city.bounds[1] + city.bounds[3]) / 2
        val hub = city.roads.nodes.filter { it.degree >= 3 && it.signal && !city.shore.anyWater(it.x, it.y) }
            .minByOrNull { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }!!
        val fx = hub.x; val fy = hub.y
        t.update(0f, fx, fy, 230f, -9999f, -9999f, initial = true)
        val near = city.buildings.filter { it.solid && abs(it.doorX - fx) < 80f && abs(it.doorY - fy) < 80f }.take(6)
        t.doors = FloatArray(near.size * 5).also { d -> for ((k, b) in near.withIndex()) { d[k * 5] = b.doorX; d[k * 5 + 1] = b.doorY; d[k * 5 + 2] = .3f; d[k * 5 + 3] = -b.frontX; d[k * 5 + 4] = -b.frontY } }
        t.doorCount = near.size
        val g = city.roads
        val dt = 1f / 30f
        val nC = t.cars.size; val nP = t.peds.size
        val pc = FloatArray(8); val qc = FloatArray(8)
        // ongoing troubles (counted once each time they start)
        val carPair = HashSet<Long>(); val carPed = HashSet<Long>()
        var overlaps = 0; var hits = 0; var redRuns = 0; var stops30 = 0; var stops60 = 0; var maxStop = 0f
        var pedStuck = 0; var pedLongWait = 0; var jaywalk = 0; var inBuilding = 0; var inWater = 0; var pedFrames = 0
        val stop = FloatArray(nC); val pedStill = FloatArray(nP); val pedWait = FloatArray(nP)
        val wasIn = BooleanArray(nC); val lightBefore = IntArray(nC); val edgeBefore = IntArray(nC)
        val toEndBefore = FloatArray(nC); val speedBefore = FloatArray(nC); val sinceJunction = FloatArray(nC); val throughBefore = IntArray(nC)
        var linked = 0
        val where = HashMap<String, MutableList<String>>()
        val kinds = HashMap<String, Int>()
        val legs = HashMap<String, Int>()
        val waits = HashMap<String, Int>()
        val hitKinds = HashMap<String, Int>()
        val cornerNodes = HashMap<Int, Int>()
        val walks = ArrayList<String>()
        val watch = System.getenv("HC_NPC_NODE")?.toIntOrNull() ?: -1
        fun note(kind: String, x: Float, y: Float, what: String) {
            val l = where.getOrPut(kind) { ArrayList() }
            if (l.size < 10) l += "(%.0f, %.0f) %s".format(x, y, what)
        }
        val frames = 30 * 300
        for (f in 0 until frames) {
            // the light each car faces as it comes up to its junction
            for ((i, a) in t.cars.withIndex()) {
                wasIn[i] = a.inJunction; edgeBefore[i] = a.edge; lightBefore[i] = -1
                if (a.placed && !a.inJunction && a.edge >= 0) {
                    val e = g.edges[a.edge]
                    val to = g.nodes[e.other(a.from)]
                    if (to.signal && to.degree >= 3) lightBefore[i] = t.light(to, e)
                    toEndBefore[i] = e.len - (if (to.id == e.a) e.clearA else e.clearB) - a.s
                }
                speedBefore[i] = a.speed
                throughBefore[i] = a.through
                sinceJunction[i] = if (a.inJunction) 0f else sinceJunction[i] + dt
            }
            t.update(dt, fx, fy, 230f, -9999f, -9999f)
            val sec = f * dt
            for ((i, a) in t.cars.withIndex()) {
                if (!a.placed) continue
                if (!wasIn[i] && a.inJunction && lightBefore[i] == 2 && sec > 2f && throughBefore[i] >= 0) linked++
                else if (!wasIn[i] && a.inJunction && lightBefore[i] == 2 && sec > 2f) {
                    redRuns++
                    val e = g.edges[edgeBefore[i]]
                    note("red light", a.x, a.y, "car $i style ${a.style} speed %.1f, %.1f m to go, link %.1f m, %.1f s since the last junction".format(
                        speedBefore[i], toEndBefore[i], e.len - e.clearA - e.clearB, sinceJunction[i]))
                }
                if (a.speed < .2f && stop[i] < 30f && stop[i] + dt >= 30f) {
                    // what it's been waiting on all this time
                    val e = g.edges[a.edge]
                    val to = g.nodes[e.other(a.from)]
                    val toEnd = e.len - (if (to.id == e.a) e.clearA else e.clearB) - a.s
                    val fx = sin(a.heading); val fy = -cos(a.heading)
                    val lead = t.cars.filter { it !== a && it.placed && abs(it.x - a.x) < 15f && abs(it.y - a.y) < 15f }
                        .filter { val ah = (it.x - a.x) * fx + (it.y - a.y) * fy; ah > 0 && abs(-(it.x - a.x) * fy + (it.y - a.y) * fx) < 2.5f }
                        .minByOrNull { (it.x - a.x) * fx + (it.y - a.y) * fy }
                    val pi = t.personAheadOf(a)
                    val why = when {
                        pi >= 0 -> "a person in the way"
                        lead != null -> "behind a car (${if (lead.inJunction) "in a junction" else "%.1f s still".format(stop[t.cars.indexOf(lead)])})"
                        a.inJunction -> "in a junction, nothing ahead"
                        to.signal && t.light(to, e) == 2 -> "a red light (%.0f m)".format(toEnd)
                        else -> "at a junction (signal ${to.signal}, light ${t.light(to, e)}, %.0f m, blocked %.0f)".format(toEnd, a.blocked)
                    }
                    waits[why.substringBefore(" (")] = (waits[why.substringBefore(" (")] ?: 0) + 1
                    note("waiting 30 s", a.x, a.y, "car $i: $why")
                }
                if (a.speed < .2f) stop[i] += dt else {
                    if (stop[i] > 30f) stops30++
                    if (stop[i] > 60f) { stops60++; note("stopped over a minute", a.x, a.y, "car $i for %.0f s".format(stop[i])) }
                    stop[i] = 0f
                }
                maxStop = maxOf(maxStop, stop[i])
            }
            for (i in 0 until nC) {
                val a = t.cars[i]; if (!a.placed) continue
                corners(a, pc)
                for (j in i + 1 until nC) {
                    val b = t.cars[j]; if (!b.placed) continue
                    if (abs(a.x - b.x) > 12f || abs(a.y - b.y) > 12f) continue
                    corners(b, qc)
                    val key = i.toLong() * 1000 + j
                    if (overlap(pc, qc)) {
                        if (carPair.add(key)) {
                            overlaps++
                            val kind = if (a.inJunction && b.inJunction && a.jNode == b.jNode && a.jEdge >= 0 && b.jEdge >= 0) movement(g, a, b) else "${if (a.inJunction) "in" else "on"}/${if (b.inJunction) "in" else "on"}"
                            kinds[kind] = (kinds[kind] ?: 0) + 1
                            note("cars overlap", a.x, a.y, "$i/$j $kind, speeds %.1f/%.1f, through ${a.through}/${b.through}, edges ${a.edge}/${b.edge} from ${a.from}/${b.from} lanes ${a.lane}/${b.lane} shifts %.1f/%.1f s %.1f/%.1f styles ${a.style}/${b.style}".format(a.speed, b.speed, a.shift, b.shift, a.s, b.s))
                        }
                    } else carPair.remove(key)
                }
                for ((j, p) in t.peds.withIndex()) {
                    if (p.hidden || !p.placed) continue
                    val key = i.toLong() * 1000 + j
                    if (inside(a, p.x, p.y, .15f)) {
                        if (carPed.add(key)) {
                            hits++
                            // where the walker crosses: how far out along the road they cross from its junction (the
                            // crossing is painted 0.5-3.2 m past the junction's edge), and where the car is
                            var at = ""
                            if (p.state == 2 && p.lane >= 0 && p.jNode >= 0) {
                                val f = g.edges[p.lane]; val n = g.nodes[p.jNode]
                                val dx = if (n.id == f.a) f.ux else -f.ux; val dy = if (n.id == f.a) f.uy else -f.uy
                                val out = (p.x - n.x) * dx + (p.y - n.y) * dy
                                at = ", crossing %.1f m out (edge at %.1f)".format(out, if (n.id == f.a) f.clearA else f.clearB)
                            }
                            val ce = if (a.edge >= 0) g.edges[a.edge] else null
                            val carTo = if (ce != null && !a.inJunction) ce.len - (if (ce.other(a.from) == ce.a) ce.clearA else ce.clearB) - a.s else -1f
                            val hk = "${if (a.speed > 1f) "moving" else "stopped"} car, ${if (p.state == 2) "leg ${p.leg}${if (p.lane >= 0) " crossing" else ""}" else "state ${p.state}"}${if (p.jNode >= 0 && g.nodes[p.jNode].scramble) " scramble" else ""}"
                            hitKinds[hk] = (hitKinds[hk] ?: 0) + 1
                            note("car into a person", p.x, p.y, "car $i (speed %.1f, %s, %.1f m to its junction) ped $j state ${p.state}%s".format(a.speed, if (a.inJunction) "in" else "on", carTo, at))
                        }
                    } else carPed.remove(key)
                }
            }
            for ((j, p) in t.peds.withIndex()) {
                if (p.hidden || !p.placed) { pedStill[j] = 0f; pedWait[j] = 0f; continue }
                pedFrames++
                if (p.jNode == watch && p.state in 1..2 && f % 6 == 0 && walks.size < 20000) walks += "ped $j ${p.x} ${p.y} ${p.state} ${p.leg} ${p.lane}"
                if (p.state == 0 && p.speed < .05f) pedStill[j] += dt else {
                    if (pedStill[j] > 10f) { pedStuck++; note("person stuck", p.x, p.y, "ped $j for %.0f s".format(pedStill[j])) }
                    pedStill[j] = 0f
                }
                if (p.state == 1) pedWait[j] += dt else {
                    if (pedWait[j] > 90f) { pedLongWait++; note("long wait at the curb", p.x, p.y, "ped $j for %.0f s".format(pedWait[j])) }
                    pedWait[j] = 0f
                }
                if (p.state == 0 && g.onAsphalt(p.x, p.y)) { jaywalk++; note("walking in the road", p.x, p.y, "ped $j") }
                if (p.state == 2 && g.onAsphalt(p.x, p.y)) {
                    val k = "leg ${p.leg}${if (p.lane >= 0) " crossing" else ""}${if (g.nodes[p.jNode].scramble) " scramble" else ""}"
                    legs[k] = (legs[k] ?: 0) + 1
                    if (p.leg == 2 && p.lane >= 0) note("leg 2 on the road", p.x, p.y, "ped $j node ${p.jNode} from (%.1f, %.1f) to (%.1f, %.1f)".format(p.wx1, p.wy1, p.tx, p.ty))
                    if (p.leg == 2 && p.lane < 0) { cornerNodes[p.jNode] = (cornerNodes[p.jNode] ?: 0) + 1 }
                }
                if (p.state in 0..2 && city.buildingAt(p.x, p.y)?.solid == true) { inBuilding++; note("inside a building", p.x, p.y, "ped $j state ${p.state}") }
                if (city.inWater(p.x, p.y)) { inWater++; note("in the water", p.x, p.y, "ped $j state ${p.state}") }
            }
        }
        // whoever's still standing after a long time: what they're facing
        for ((i, a) in t.cars.withIndex()) {
            if (stop[i] < 45f || !a.placed) continue
            val e = g.edges[a.edge]
            val to = g.nodes[e.other(a.from)]
            val toEnd = e.len - (if (to.id == e.a) e.clearA else e.clearB) - a.s
            val fx = sin(a.heading); val fy = -cos(a.heading)
            val ahead = t.cars.withIndex().filter { (j, o) -> j != i && o.placed && abs(o.x - a.x) < 15f && abs(o.y - a.y) < 15f }
                .map { (j, o) -> "car $j (%.1f ahead, %.1f aside, speed %.1f, ${if (o.inJunction) "in j${o.jNode}" else "on e${o.edge}"})".format((o.x - a.x) * fx + (o.y - a.y) * fy, -(o.x - a.x) * fy + (o.y - a.y) * fx, o.speed) }
            val pi = t.personAheadOf(a)
            val people = if (pi < 0) "nobody in the way" else t.peds[pi].let { p -> "ped $pi in the way: state ${p.state} leg ${p.leg} speed %.1f at (%.1f, %.1f), heading to (%.1f, %.1f) / (%.1f, %.1f), on asphalt ${g.onAsphalt(p.x, p.y)}, still %.0f s".format(p.speed, p.x, p.y, p.wx1, p.wy1, p.tx, p.ty, pedStill[pi]) }
            println("  stuck car $i (%.0f, %.0f) for %.0f s: ${if (a.inJunction) "in junction ${a.jNode} t %.2f".format(a.jt) else "on edge ${a.edge} %.1f m to node ${to.id} (signal ${to.signal}, light ${t.light(to, e)})".format(toEnd)}, through ${a.through}, blocked %.1f, waits on ${a.waitOn}, speed %.2f, next ${a.next}, turn %.1f; near: $ahead; $people".format(a.x, a.y, stop[i], a.blocked, a.speed, a.turn))
        }
        println("${city.mapId}: car overlaps $overlaps, cars into people $hits, red lights run $redRuns, stops >30 s $stops30, >60 s $stops60 " +
            "(longest %.0f s); people stuck $pedStuck, curb waits >90 s $pedLongWait, frames walking in the road $jaywalk, in buildings $inBuilding, in water $inWater (of $pedFrames)".format(maxStop))
        if (System.getenv("HC_NPC_SEEDS") == null) {
            // what one morning in each city stays within (the street AI before its 1.8 rework managed 20-110 overlaps,
            // 30-100 people walked through or run into and 1-115 red lights run, and once jammed for minutes)
            val c = city.mapId
            assertTrue("$c: cars drive through each other ($overlaps)", overlaps <= 60)
            assertTrue("$c: cars and people walk through each other ($hits)", hits <= 50)
            assertTrue("$c: cars run red lights ($redRuns)", redRuns <= 30)
            assertTrue("$c: a jam that never clears (longest stop %.0f s)".format(maxStop), maxStop < 90f)
            assertTrue("$c: people stand stuck on the pavement ($pedStuck)", pedStuck <= 15)
            assertTrue("$c: people walk inside buildings or in the water ($inBuilding, $inWater)", inBuilding == 0 && inWater == 0)
        }
        println("  frames out on the road while crossing, by leg: $legs")
        println("  waiting 30 s for: $waits")
        println("  cars into people: $hitKinds")
        println("  corner walks on the road, by junction: ${cornerNodes.entries.sortedByDescending { it.value }.take(6).map { "${it.key} (${g.nodes[it.key].degree} roads, signal ${g.nodes[it.key].signal}): ${it.value}" }}")
        // HC_NPC_NODE=<id>: the junction's roads and the walks people took round it, for a plot
        System.getenv("HC_NPC_NODE")?.toIntOrNull()?.let { id ->
            val n = g.nodes[id]
            val out = StringBuilder("node ${n.x} ${n.y}\n")
            for (eid in n.edges) { val e = g.edges[eid]; out.append("edge ${e.ax} ${e.ay} ${e.bx} ${e.by} ${e.cls.halfWidth} ${e.cls.sidewalk} ${if (e.a == id) e.clearA else e.clearB}\n") }
            for (w in walks) out.append(w).append('\n')
            java.io.File("build/npc_node_$id.txt").writeText(out.toString())
        }
        println("  overlaps by movement: $kinds; went on through (amber, or among linked junctions) $linked times")
        if (verbose) for ((k, l) in where) println("  $k: ${l.joinToString("; ")}")
    }
}

package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Looks over every map, on two seeds, for the things a player would notice: buildings standing in the river or on the
 * road, trees growing out of the asphalt or a wall, roads that run over water with no bridge, junctions afloat, doors
 * opening onto the water, landmarks with houses inside them, and pieces of road you can't drive to. (Roads and
 * their sidewalks simply cross the narrow canals, so for them only rivers and the sea count as water.)
 */
class MapAuditTest {
    @Test
    fun mapsHaveNoMisplacedThings() {
        val report = StringBuilder()
        var failed = false
        for (m in Maps.all) for (seed in longArrayOf(2024L, 1337L)) {
            val (ok, line) = audit(City(seed, m.id))
            report.append("$seed ").append(line).append('\n')
            if (!ok) failed = true
        }
        println(report)
        assertTrue(report.toString(), !failed)
    }

    /** A game saved before a layout revision rebuilds the city it was played on, so its shops are where they were. */
    @Test
    fun savedGamesKeepTheirCity() {
        val saves = com.hustlecity.game.engine.SaveManager(null)
        // a save from before the revision field existed
        val e = com.hustlecity.game.engine.GameEngine(null)
        e.newGame("T", 0, 0, 1, mapId = Maps.NYC)
        assertTrue("new games are on the current layout", e.state.layout == City.LAYOUT)
        val old = saves.decode(saves.encode(e.state).replace(Regex("\"layout\":\\d+,?"), ""))
        assertTrue("an old save reads as layout 1 (${old.layout})", old.layout == 1)
        val then = City(1337L, Maps.NYC, 1)
        val now = City(1337L, Maps.NYC)
        assertTrue("the old layout is still generated as it was", then.buildings.size == 816)
        assertTrue("the layouts differ (or this test can go)", then.buildings.size != now.buildings.size || then.roads.edges.size != now.roads.edges.size)
        val again = City(old.seed, old.mapId, old.layout)
        for (i in then.buildings.indices) assertTrue(then.buildings[i].cx == again.buildings[i].cx && then.buildings[i].cy == again.buildings[i].cy)
    }

    private fun audit(city: City): Pair<Boolean, String> {
        val g = city.roads; val shore = city.shore
        val examples = ArrayList<String>()
        fun note(s: String) { if (examples.size < 8) examples += s }
        // out in the water, not just over the edge of the bank (a quay, the end of a bridge)
        fun deep(x: Float, y: Float): Boolean {
            if (!shore.wet(x, y)) return false
            for (k in 0 until 8) if (!shore.wet(x + 4f * kotlin.math.cos(k * .785f), y + 4f * kotlin.math.sin(k * .785f))) return false
            return true
        }
        fun onBridge(x: Float, y: Float): Boolean {
            var hit = false
            g.near(x, y, 40f) { id ->
                val e = g.edges[id]
                if ((e.overWater || e.bridge) && Geo.distToSeg(x, y, e.ax, e.ay, e.bx, e.by) < e.cls.outer + .5f) hit = true
            }
            return hit
        }

        // buildings: checked at the middle, just inside each corner and halfway along each side
        var wetBuildings = 0; var roadBuildings = 0
        val us = floatArrayOf(.5f, .06f, .94f, .94f, .06f, .5f, .94f, .5f, .06f)
        val vs = floatArrayOf(.5f, .06f, .06f, .94f, .94f, .06f, .5f, .94f, .5f)
        for (b in city.buildings) {
            var wet = 0; var road = 0
            for (k in us.indices) {
                val x = b.wx(b.w * us[k], b.d * vs[k]); val y = b.wy(b.w * us[k], b.d * vs[k])
                if (shore.anyWater(x, y)) wet++
                if (g.onAsphalt(x, y)) road++
            }
            if (wet >= 2) { wetBuildings++; note("${b.kind} ${b.id} (${b.address}) stands in the water at %.0f,%.0f".format(b.cx, b.cy)) }
            if (road >= 2 && b.solid) { roadBuildings++; note("${b.kind} ${b.id} (${b.address}) stands on the road at %.0f,%.0f".format(b.cx, b.cy)) }
        }

        // trees: not on the asphalt, in the water or in a building
        var badTrees = 0
        for (t in city.trees) {
            var inside = false
            city.forBuildingsIn(t.x - .5f, t.y - .5f, t.x + .5f, t.y + .5f) { b -> if (b.solid && b.contains(t.x, t.y)) inside = true }
            val why = when {
                g.onAsphalt(t.x, t.y) -> "on the road"
                shore.anyWater(t.x, t.y) -> "in the water"
                inside -> "inside a building"
                else -> null
            }
            if (why != null) { badTrees++; note("tree $why at %.0f,%.0f".format(t.x, t.y)) }
        }

        // roads: over water only as bridges; junctions on dry land or at a bridge
        var wetRoads = 0; var wetNodes = 0
        for (e in g.edges) {
            if (e.overWater || e.bridge) continue
            val steps = maxOf(2, (e.len / 4f).toInt())
            var wet = 0
            for (i in 1 until steps) if (deep(e.x(i.toFloat() / steps), e.y(i.toFloat() / steps))) wet++
            if (wet > 0) { wetRoads++; note("road ${e.id} (${e.name}) runs over water without a bridge near %.0f,%.0f".format(e.x(.5f), e.y(.5f))) }
        }
        for (n in g.nodes) {
            if (!deep(n.x, n.y)) continue
            if (n.edges.any { g.edges[it].overWater || g.edges[it].bridge }) continue
            wetNodes++; note("junction ${n.id} in the water at %.0f,%.0f".format(n.x, n.y))
        }

        // doors open onto dry ground (or a pier)
        var wetDoors = 0
        for (b in city.buildings) if (b.solid && b.rentable && shore.wet(b.doorX, b.doorY) && !onBridge(b.doorX, b.doorY)) {
            wetDoors++; note("${b.kind} ${b.id} (${b.address}) has its door over the water")
        }

        // landmarks keep their ground to themselves
        var crowded = 0
        for (l in city.landmarks) {
            val fp = l.footprint ?: continue
            var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            for (i in 0 until 4) { x0 = minOf(x0, fp[i * 2]); x1 = maxOf(x1, fp[i * 2]); y0 = minOf(y0, fp[i * 2 + 1]); y1 = maxOf(y1, fp[i * 2 + 1]) }
            city.forBuildingsIn(x0, y0, x1, y1) { b ->
                if (b.solid && Geo.inside(b.cx, b.cy, fp)) { crowded++; note("building ${b.id} inside landmark ${l.name.ifEmpty { "kind ${l.kind}" }}") }
            }
        }

        // every road reachable from every other (one network)
        val comp = IntArray(g.nodes.size) { -1 }
        var comps = 0
        val sizes = ArrayList<Int>()
        for (s in g.nodes.indices) {
            if (comp[s] >= 0 || g.nodes[s].degree == 0) continue
            var size = 0
            val stack = ArrayDeque<Int>().apply { add(s) }
            comp[s] = comps
            while (stack.isNotEmpty()) {
                val n = stack.removeLast(); size++
                for (id in g.nodes[n].edges) {
                    val o = g.edges[id].other(n)
                    if (comp[o] < 0) { comp[o] = comps; stack.add(o) }
                }
            }
            sizes += size; comps++
        }
        val main = sizes.indices.maxByOrNull { sizes[it] } ?: 0
        val cutOff = g.nodes.count { it.degree > 0 && comp[it.id] != main }
        if (cutOff > 0) note("$cutOff junctions in ${comps - 1} pieces of road cut off from the rest")

        val ok = wetBuildings == 0 && roadBuildings == 0 && badTrees == 0 && wetRoads == 0 && wetNodes == 0 && wetDoors == 0 && crowded == 0 && cutOff == 0
        val line = "${city.mapId}: buildings in water=$wetBuildings on road=$roadBuildings, bad trees=$badTrees, roads over water=$wetRoads, " +
            "junctions in water=$wetNodes, wet doors=$wetDoors, buildings in landmarks=$crowded, cut-off junctions=$cutOff" +
            if (examples.isEmpty()) "" else "\n   " + examples.joinToString("\n   ")
        return ok to line
    }
}

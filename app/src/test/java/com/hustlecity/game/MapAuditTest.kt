package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.StreetProps
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.RoadEdge
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Walks every map looking for things out of place: a crosswalk painted where nobody could use it, street furniture
 * or a tree standing in a road, a building or the water, two props in the same spot. Writes what it finds to
 * build/map_audit.txt and fails on any of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapAuditTest {
    private class Finding(val what: String, val x: Float, val y: Float)

    /** Where the renderer paints a crossing on [ed] at one end (0 = a, 1 = b): meters along it, or NaN for none. */
    private fun crossingAt(city: City, ed: RoadEdge, end: Int): Float = com.hustlecity.game.world.Crossings.start(city, ed, end)

    @Test
    fun nothingOutOfPlace() {
        val out = StringBuilder()
        var total = 0
        for (map in Maps.all) {
            val e = GameEngine(null)
            e.newGame("T", 0, 0, 1, mapId = map.id)
            val city = e.city
            val g = city.roads
            val found = ArrayList<Finding>()

            fun inBuilding(x: Float, y: Float): Boolean {
                var inside = false
                city.forBuildingsIn(x - 1f, y - 1f, x + 1f, y + 1f) { b -> if (b.solid && b.contains(x, y)) inside = true }
                return inside
            }

            // ---- crosswalks
            var crossings = 0
            for (ed in g.edges) {
                val a0 = crossingAt(city, ed, 0); val a1 = crossingAt(city, ed, 1)
                val hw = ed.cls.halfWidth
                for (end in 0..1) {
                    val a = if (end == 0) a0 else a1
                    if (a.isNaN()) continue
                    crossings++
                    val mx = ed.ax + ed.ux * (a + 1.35f); val my = ed.ay + ed.uy * (a + 1.35f)
                    if (a < 0f || a + 2.7f > ed.len) found += Finding("crossing runs off the end of ${ed.name}", mx, my)
                    // somewhere to step onto at both kerbs
                    for (sd in -1..1 step 2) {
                        val lx = mx + ed.uy * sd * (hw + 1.1f); val ly = my - ed.ux * sd * (hw + 1.1f)
                        when {
                            g.onAsphalt(lx, ly) -> found += Finding("crossing on ${ed.name} ends in another road, not on a pavement", lx, ly)
                            inBuilding(lx, ly) -> found += Finding("crossing on ${ed.name} ends in a building", lx, ly)
                            city.inWater(lx, ly) -> found += Finding("crossing on ${ed.name} ends in the water", lx, ly)
                        }
                    }
                    // the paint itself keeps to its own road
                    var onOther: RoadEdge? = null
                    for (t in floatArrayOf(.1f, 1.35f, 2.6f)) for (o in floatArrayOf(-hw + .5f, 0f, hw - .5f)) {
                        val px = ed.ax + ed.ux * (a + t) + ed.uy * o; val py = ed.ay + ed.uy * (a + t) - ed.ux * o
                        g.near(px, py, 16f) { id ->
                            val o2 = g.edges[id]
                            if (o2.id != ed.id && !o2.under && !ed.under && o2.name != ed.name &&
                                Geo.distToSeg(px, py, o2.ax, o2.ay, o2.bx, o2.by) < o2.cls.halfWidth - .4f) onOther = o2
                        }
                    }
                    onOther?.let { found += Finding("crossing on ${ed.name} is painted across ${it.name}", mx, my) }
                }
                // two crossings back to back on a short block read as one long smear of paint
                if (!a0.isNaN() && !a1.isNaN() && a1 - (a0 + 2.7f) < 3f)
                    found += Finding("two crossings ${"%.1f".format(a1 - a0 - 2.7f)} m apart on ${ed.name} (a ${ed.len.toInt()} m block)", ed.ax + ed.ux * ed.len / 2, ed.ay + ed.uy * ed.len / 2)
            }

            // ---- street furniture
            val pr = StreetProps(city)
            for (i in 0 until pr.count) {
                val k = pr.kind[i]
                val x = pr.x[i]; val y = pr.y[i]
                val strung = k == StreetProps.BUNTING || k == StreetProps.LANTERNS
                if (!strung && k != StreetProps.FOUNTAIN && k != StreetProps.PARK_LAMP && g.onAsphalt(x, y)) found += Finding("prop $k stands in a road", x, y)
                if (!strung && inBuilding(x, y)) found += Finding("prop $k stands inside a building", x, y)
                if (!strung && city.inWater(x, y)) found += Finding("prop $k stands in the water", x, y)
                for (l in city.landmarks) {
                    val ex = l.extent ?: continue
                    if (l.kind == com.hustlecity.game.world.Landmark.BILLBOARDS) continue
                    val dx = x - l.x; val dy = y - l.y
                    val lx = (dx * l.cs + dy * l.sn) / l.size; val ly = (-dx * l.sn + dy * l.cs) / l.size
                    if (!strung && lx > ex[0] + 1f && lx < ex[2] - 1f && ly > ex[1] + 1f && ly < ex[3] - 1f && k != StreetProps.PARK_LAMP && k != StreetProps.BENCH && k != StreetProps.FOUNTAIN)
                        found += Finding("prop $k stands on the ground of ${l.name.ifEmpty { "a landmark" }}", x, y)
                }
            }
            // two things in the same spot (a cheap grid keeps this from being every pair)
            val cell = HashMap<Long, ArrayList<Int>>()
            for (i in 0 until pr.count) {
                if (pr.kind[i] == StreetProps.BUNTING || pr.kind[i] == StreetProps.LANTERNS) continue
                val key = (Math.floor(pr.x[i] / 2.0).toLong() shl 32) xor (Math.floor(pr.y[i] / 2.0).toLong() and 0xFFFFFFFFL)
                cell.getOrPut(key) { ArrayList() } += i
            }
            for ((_, list) in cell) for (p in list.indices) for (q in p + 1 until list.size) {
                val i = list[p]; val j = list[q]
                val d = Math.hypot((pr.x[i] - pr.x[j]).toDouble(), (pr.y[i] - pr.y[j]).toDouble())
                if (d < .5) found += Finding("props ${pr.kind[i]} and ${pr.kind[j]} share a spot (%.2f m apart)".format(d), pr.x[i], pr.y[i])
            }

            // ---- trees
            for (t in city.trees) {
                if (g.onAsphalt(t.x, t.y)) found += Finding("a tree stands in a road", t.x, t.y)
                else if (inBuilding(t.x, t.y)) found += Finding("a tree stands inside a building", t.x, t.y)
                else if (city.inWater(t.x, t.y)) found += Finding("a tree stands in the water", t.x, t.y)
            }

            // ---- buildings
            for (b in city.buildings) {
                if (!b.solid) continue
                for (c in arrayOf(floatArrayOf(.3f, .3f), floatArrayOf(b.w - .3f, .3f), floatArrayOf(b.w - .3f, b.d - .3f), floatArrayOf(.3f, b.d - .3f), floatArrayOf(b.w / 2, b.d / 2))) {
                    val wx = b.wx(c[0], c[1]); val wy = b.wy(c[0], c[1])
                    if (g.onAsphalt(wx, wy)) { found += Finding("building ${b.id} stands in a road", wx, wy); break }
                }
            }

            val byKind = found.groupBy { it.what.replace(Regex("[0-9.]+ m apart|\\(a [0-9]+ m block\\)|building [0-9]+|on .*? (ends|is)|across .*"), "").trim() }
            out.appendLine("== ${map.id}: $crossings crossings, ${pr.count} props, ${city.trees.size} trees, ${city.buildings.size} buildings: ${found.size} out of place")
            for ((k, v) in byKind.entries.sortedByDescending { it.value.size }) {
                out.appendLine("   ${v.size} x $k")
                for (f in v.take(6)) out.appendLine("      ${f.what} at %.0f, %.0f".format(f.x, f.y))
            }
            total += found.size
        }
        File("build").mkdirs()
        File("build/map_audit.txt").writeText(out.toString())
        println(out)
        assertTrue("things out of place on the maps: see build/map_audit.txt\n$out", total == 0)
    }
}

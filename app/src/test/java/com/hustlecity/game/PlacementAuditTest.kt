package com.hustlecity.game

import androidx.test.core.app.ApplicationProvider
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.Models
import com.hustlecity.game.render.StreetProps
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Curbside
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.companionAt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Where everything on the pavement really stands, with the HD models' full footprints: street furniture against the
 * road, buildings and their doorways, trees, parked cars, signal poles and each other. Writes build/placement.txt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacementAuditTest {
    /** An oriented rectangle: center, unit axis (ux, uy) and its perpendicular, half extents along each. */
    class Rect(val cx: Float, val cy: Float, val ux: Float, val uy: Float, val hu: Float, val hv: Float) {
        val vx get() = -uy
        val vy get() = ux
        fun contains(x: Float, y: Float, pad: Float = 0f): Boolean {
            val dx = x - cx; val dy = y - cy
            return abs(dx * ux + dy * uy) <= hu + pad && abs(dx * vx + dy * vy) <= hv + pad
        }
        fun overlaps(o: Rect): Boolean {
            for ((ax, ay) in listOf(ux to uy, vx to vy, o.ux to o.uy, o.vx to o.vy)) {
                val d = abs((o.cx - cx) * ax + (o.cy - cy) * ay)
                val r1 = hu * abs(ux * ax + uy * ay) + hv * abs(vx * ax + vy * ay)
                val r2 = o.hu * abs(o.ux * ax + o.uy * ay) + o.hv * abs(o.vx * ax + o.vy * ay)
                if (d > r1 + r2) return false
            }
            return true
        }
        inline fun samples(step: Float, f: (Float, Float) -> Unit) {
            val nu = max(1, (hu * 2 / step).toInt()); val nv = max(1, (hv * 2 / step).toInt())
            for (i in 0..nu) for (j in 0..nv) {
                val a = -hu + 2 * hu * i / nu; val b = -hv + 2 * hv * j / nv
                f(cx + ux * a + vx * b, cy + uy * a + vy * b)
            }
        }
    }

    companion object {
        val MODEL = mapOf(StreetProps.LAMP to "lamp", StreetProps.HYDRANT to "hydrant", StreetProps.BIN to "bin", StreetProps.BENCH to "bench",
            StreetProps.BUS_STOP to "bus_stop", StreetProps.NEWS to "news", StreetProps.MAILBOX to "mailbox", StreetProps.PARK_LAMP to "park_lamp",
            StreetProps.BIKE_RACK to "bike_rack", StreetProps.PLANTER to "planter")

        /** What a prop stands on: its model's plan (all its looks), the lamp's base only (its arm reaches over the road by design). */
        fun footprint(pr: StreetProps, i: Int): Rect? {
            val k = pr.kind[i]
            // local x along the street = (dirY, -dirX), local y toward the road = (dirX, dirY)
            val ux = pr.dirY[i]; val uy = -pr.dirX[i]
            fun local(x0: Float, y0: Float, x1: Float, y1: Float): Rect {
                val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2
                return Rect(pr.x[i] + ux * mx + pr.dirX[i] * my, pr.y[i] + uy * mx + pr.dirY[i] * my, ux, uy, (x1 - x0) / 2, (y1 - y0) / 2)
            }
            if (k == StreetProps.LAMP || k == StreetProps.PARK_LAMP || k == StreetProps.UTIL_POLE) return local(-.2f, -.2f, .2f, .2f)
            val id = MODEL[k]
            if (id != null) {
                val looks = Models.prop(id) ?: return null
                var x0 = 9f; var y0 = 9f; var x1 = -9f; var y1 = -9f
                for (m in looks) { x0 = minOf(x0, m.bounds[0]); y0 = minOf(y0, m.bounds[1]); x1 = maxOf(x1, m.bounds[3]); y1 = maxOf(y1, m.bounds[4]) }
                return local(x0, y0, x1, y1)
            }
            return when (k) {
                StreetProps.VENDING -> local(-1.5f, -.45f, 1.5f, .35f)
                StreetProps.VENDOR -> local(-1.3f, -.6f, 1.3f, .6f)
                StreetProps.HOOP -> local(-.3f, -.3f, .3f, .3f)
                StreetProps.FOUNTAIN -> local(-3.4f, -3.4f, 3.4f, 3.4f)
                else -> null
            }
        }
    }

    /**
     * Everything stands on paved ground: no street furniture, tree, building or parked car out past the city's paving
     * (or a land mass's shore) or in the water, at the edges of the map as much as anywhere, and the paving reaches past
     * the end of every street.
     */
    @Test
    fun everythingStandsOnTheGround() {
        Models.assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        val out = StringBuilder()
        var total = 0
        for (map in Maps.all) {
            val c = GameEngine(null).also { it.newGame("T", 0, 0, 1, mapId = map.id) }.city
            val sh = c.shore
            val g = c.ground
            fun slab(x: Float, y: Float) = x >= g[0] && x <= g[2] && y >= g[1] && y <= g[3]
            fun paved(x: Float, y: Float): Boolean {
                val land = if (sh.land.isEmpty()) slab(x, y) else sh.land.any { Geo.inside(x, y, it) } || sh.islands.any { Geo.inside(x, y, it) }
                return land && !c.inWater(x, y)
            }
            val bad = ArrayList<String>()
            for (b in c.buildings) if (!paved(b.cx, b.cy)) bad += "building ${b.id} at ${b.cx.toInt()},${b.cy.toInt()}"
            val pr = StreetProps.of(c)
            for (i in 0 until pr.count) if (!paved(pr.x[i], pr.y[i])) bad += "prop (kind ${pr.kind[i]}) at ${pr.x[i].toInt()},${pr.y[i].toInt()}"
            for (t in c.trees) if (!paved(t.x, t.y)) bad += "tree at ${t.x.toInt()},${t.y.toInt()}"
            for (ci in 0 until c.curb.count) if (!paved(c.curb.x[ci], c.curb.y[ci])) bad += "parked car at ${c.curb.x[ci].toInt()},${c.curb.y[ci].toInt()}"
            if (sh.land.isEmpty()) for (ed in c.roads.edges) {
                val r = ed.cls.outer
                if (!slab(ed.ax - r, ed.ay - r) || !slab(ed.ax + r, ed.ay + r) || !slab(ed.bx - r, ed.by - r) || !slab(ed.bx + r, ed.by + r))
                    bad += "street ${ed.id} runs off the paving"
            }
            val line = "${map.id}: ${bad.size} off the ground ${bad.take(6)}"
            println(line); out.appendLine(line)
            total += bad.size
        }
        File("build").mkdirs()
        File("build/ground.txt").writeText(out.toString())
        org.junit.Assert.assertEquals("things off the ground:\n$out", 0, total)
    }

    /**
     * People on the sidewalks step round what stands there: crowds walk each city for a while and hardly anyone is ever
     * caught inside a bench, a bus shelter, a tree, a signal pole or a stoop.
     */
    @Test
    fun peopleWalkRoundIt() {
        Models.assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        val out = StringBuilder()
        var worst = 0.0
        for (map in Maps.all) {
            val e = GameEngine(null).also { it.newGame("T", 0, 0, 1, mapId = map.id); it.ui.tip = null; it.ui.phoneOpen = false }
            e.settings.quality = 2; e.settings.crowds = 3f
            e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
            val pv = e.city.pavement!!
            var samples = 0; var inside = 0; var wet = 0
            val kinds = IntArray(4)
            val where = ArrayList<String>()
            val comp = FloatArray(2)
            // the crowds where you start, at a few junctions and along the waterfront
            val rnd = java.util.Random(11)
            val g = e.city.roads
            val spots = arrayListOf(e.state.player.x to e.state.player.y)
            val signals = g.nodes.filter { it.signal }
            repeat(2) { val n = signals[rnd.nextInt(signals.size)]; spots += n.x to n.y }
            g.edges.filter { ed -> !ed.overWater && (0..4).any { k -> val t = k / 4f; e.city.inWater(ed.ax + (ed.bx - ed.ax) * t + ed.uy * (ed.cls.outer + 2f), ed.ay + (ed.by - ed.ay) * t - ed.ux * (ed.cls.outer + 2f)) } }
                .shuffled(rnd).take(2).forEach { ed -> spots += (ed.ax + ed.bx) / 2 to (ed.ay + ed.by) / 2 }
            for ((sx, sy) in spots) {
            e.state.player.x = sx; e.state.player.y = sy; e.camera.tx = sx; e.camera.ty = sy
            e.traffic.update(0f, sx, sy, 230f, -9999f, -9999f, initial = true)
            e.traffic.scatter(sx, sy, 80f, .7f)
            repeat(30 * 20) { f ->
                e.frame(1 / 30f)
                if (f < 60 || f % 3 != 0) return@repeat
                for (a in e.traffic.peds) {
                    if (a.hidden || !a.placed) continue
                    // nobody walks on water, crossing a road or not
                    if (a.state == 1 || a.state == 2) { if (e.city.inWater(a.x, a.y)) { wet++; if (where.size < 8) where += "${a.x.toInt()},${a.y.toInt()} crossing in the water" }; continue }
                    if (a.state != 0) continue
                    samples++
                    var hit = -1
                    pv.forNear(a.x, a.y, 1f) { i -> if (hit < 0 && pv.contains(i, a.x, a.y, -.1f)) hit = i }
                    if (e.city.inWater(a.x, a.y)) { wet++; if (where.size < 8) where += "${a.x.toInt()},${a.y.toInt()} in the water" }
                    if (hit >= 0) {
                        inside++; kinds[pv.kind(hit)]++
                        if (where.size < 8) where += "${a.x.toInt()},${a.y.toInt()} in kind ${pv.kind(hit)}"
                    }
                    // nor does the dog or child alongside
                    if (a.companion != 0) {
                        companionAt(a, comp)
                        val cx = comp[0]; val cy = comp[1]
                        var chit = -1
                        pv.forNear(cx, cy, 1f) { i -> if (chit < 0 && pv.contains(i, cx, cy, -.1f)) chit = i }
                        if (chit >= 0) {
                            inside++; kinds[pv.kind(chit)]++
                            if (where.size < 8) where += "${cx.toInt()},${cy.toInt()} companion in kind ${pv.kind(chit)}"
                        }
                    }
                }
            }
            }
            val rate = (inside + wet).toDouble() / samples.coerceAtLeast(1)
            worst = maxOf(worst, rate)
            val line = "${map.id}: $samples walker samples, $wet in the water, $inside inside something (props ${kinds[0]}, trees ${kinds[1]}, poles ${kinds[2]}, stoops ${kinds[3]}) ${where.take(5)}"
            println(line); out.appendLine(line)
        }
        File("build").mkdirs()
        File("build/walkers.txt").writeText(out.toString())
        org.junit.Assert.assertTrue("walkers inside things on the pavement:\n$out", worst < .0002)
    }

    private fun carRect(cb: Curbside, i: Int): Rect {
        val fx = sin(cb.heading[i]); val fy = -cos(cb.heading[i])
        return Rect(cb.x[i], cb.y[i], fx, fy, Curbside.length(cb.style[i]) / 2, Curbside.width(cb.style[i]) / 2)
    }

    @Test
    fun audit() {
        Models.assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        Models.clear()
        val out = StringBuilder()
        var total = 0
        for (map in Maps.all) {
            val e = GameEngine(null).also { it.newGame("T", 0, 0, 1, mapId = map.id) }
            val city: City = e.city
            val pr = StreetProps.of(city)
            val counts = LinkedHashMap<String, Int>()
            val examples = LinkedHashMap<String, ArrayList<String>>()
            fun bad(kind: String, what: String) {
                total++
                counts[kind] = (counts[kind] ?: 0) + 1
                val l = examples.getOrPut(kind) { ArrayList() }
                if (l.size < 6) l += what
            }
            val rects = arrayOfNulls<Rect>(pr.count)
            for (i in 0 until pr.count) rects[i] = footprint(pr, i)
            fun name(i: Int) = "${MODEL[pr.kind[i]] ?: "kind ${pr.kind[i]}"} #$i at ${pr.x[i].toInt()},${pr.y[i].toInt()}"
            val skip = setOf(StreetProps.LANTERNS, StreetProps.BUNTING)
            for (i in 0 until pr.count) {
                val r = rects[i] ?: continue
                if (pr.kind[i] in skip) continue
                val inner = Rect(r.cx, r.cy, r.ux, r.uy, (r.hu - .04f).coerceAtLeast(.01f), (r.hv - .04f).coerceAtLeast(.01f))
                // over the road
                var road = false; var wet = false
                inner.samples(.15f) { x, y -> if (!road && city.roads.onAsphalt(x, y)) road = true }
                inner.samples(.5f) { x, y -> if (!wet && pr.kind[i] != StreetProps.FOUNTAIN && city.inWater(x, y)) wet = true }
                if (road) bad("on the road", name(i))
                if (wet) bad("in the water", name(i))
                // in a building, or in front of its door (and a brownstone's stoop)
                var wall = false; var door = false
                city.forBuildingsIn(r.cx - 6f, r.cy - 6f, r.cx + 6f, r.cy + 6f) { b ->
                    if (!b.solid) return@forBuildingsIn
                    inner.samples(.15f) { x, y ->
                        if (b.contains(x, y)) wall = true
                        // the doorway, and a brownstone's stoop running 2.6 m out from it
                        val lx = b.lx(x, y); val ly = b.ly(x, y)
                        val stoop = com.hustlecity.game.render.Styles.of(city, b) == com.hustlecity.game.render.Arch.BROWNSTONE
                        if (lx in b.w / 2 - 1.2f..b.w / 2 + 1.2f && ly in b.d..b.d + (if (stoop) 2.65f else 1.6f)) door = true
                    }
                }
                if (wall) bad("in a building", name(i))
                if (door) bad("blocks a doorway", name(i))
                // on a tree's trunk
                for ((ti, t) in city.trees.withIndex()) {
                    if (pr.hiddenTree[ti] || abs(t.x - r.cx) > 6f || abs(t.y - r.cy) > 6f) continue
                    if (r.contains(t.x, t.y, .3f)) { bad("on a tree", name(i)); break }
                }
                // against another prop
                for (j in i + 1 until pr.count) {
                    val o = rects[j] ?: continue
                    if (pr.kind[j] in skip || abs(o.cx - r.cx) > 8f || abs(o.cy - r.cy) > 8f) continue
                    if (r.overlaps(o)) bad("overlaps a prop", "${name(i)} and ${name(j)}")
                }
                // under a parked car
                val cb = city.curb
                for (ci in 0 until cb.count) {
                    if (abs(cb.x[ci] - r.cx) > 6f || abs(cb.y[ci] - r.cy) > 6f) continue
                    if (carRect(cb, ci).overlaps(r)) { bad("under a parked car", name(i)); break }
                }
            }
            // trees and parked cars on dry land
            for ((ti, t) in city.trees.withIndex()) if (!pr.hiddenTree[ti] && city.inWater(t.x, t.y)) bad("tree in the water", "${t.x.toInt()},${t.y.toInt()}")
            for (ci in 0 until city.curb.count) if (city.inWater(city.curb.x[ci], city.curb.y[ci])) bad("parked car in the water", "${city.curb.x[ci].toInt()},${city.curb.y[ci].toInt()}")
            // signal poles against the furniture and the parked cars
            val g = city.roads
            for (n in g.nodes) {
                if (!n.signal) continue
                for (id in n.edges) {
                    val ed = g.edges[id]
                    if (ed.under) continue
                    val clear = if (ed.a == n.id) ed.clearA else ed.clearB
                    val dx = if (ed.a == n.id) ed.ux else -ed.ux; val dy = if (ed.a == n.id) ed.uy else -ed.uy
                    val side = if (city.leftHand) -1f else 1f
                    val off = ed.cls.halfWidth + 1.4f
                    val px = n.x + dx * (clear + 1f) + dy * off * side; val py = n.y + dy * (clear + 1f) - dx * off * side
                    if (g.onAsphalt(px, py) || city.inWater(px, py)) continue
                    val pole = Rect(px, py, 1f, 0f, .2f, .2f)
                    for (i in 0 until pr.count) {
                        val r = rects[i] ?: continue
                        if (abs(r.cx - px) < 6f && abs(r.cy - py) < 6f && r.overlaps(pole)) bad("against a signal pole", name(i))
                    }
                    for ((ti, t) in city.trees.withIndex()) if (!pr.hiddenTree[ti] && pole.contains(t.x, t.y, .3f)) bad("signal pole on a tree", "${px.toInt()},${py.toInt()}")
                }
            }
            val line = "${map.id}: ${pr.count} props; " + counts.entries.joinToString("; ") { "${it.key} ${it.value}" }
            println(line); out.appendLine(line)
            for ((k, l) in examples) { out.appendLine("  $k: ${l.joinToString(" | ")}") }
        }
        File("build").mkdirs()
        File("build/placement.txt").writeText(out.toString())
        org.junit.Assert.assertEquals("misplaced street furniture (see build/placement.txt):\n$out", 0, total)
    }
}

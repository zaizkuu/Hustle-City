package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * A long, busy run of the street AI on every map with the camera roaming the city the way a player does (driving
 * across town, jumping by taxi): nobody may end up somewhere impossible or freeze. Pedestrians stay out of buildings
 * and off the water; cars stay on the road; nobody's position goes bad; nobody stands still for minutes.
 */
class AiSoakTest {
    @Test
    fun streetLifeHoldsUpEverywhere() {
        val report = StringBuilder()
        var failed = false
        for (map in Maps.all) {
            val r = soak(City(2024L, map.id), minutes = 6f)
            report.append(r.summary).append('\n')
            if (!r.ok) failed = true
        }
        println(report)
        assertTrue(report.toString(), !failed)
    }

    class Result(val ok: Boolean, val summary: String)

    private fun soak(city: City, minutes: Float): Result {
        val t = Traffic(Random(11))
        t.attach(city)
        t.ensure(90, 160)
        val rnd = Random(5)
        val nodes = city.roads.nodes.filter { it.degree >= 2 && !city.shore.wet(it.x, it.y) }
        var target = nodes[rnd.nextInt(nodes.size)]
        var fx = target.x; var fy = target.y
        t.update(0f, fx, fy, 230f, -9999f, -9999f, initial = true)
        val dt = 1f / 30f
        val frames = (minutes * 60 * 30).toInt()
        var bad = 0; var inside = 0; var wet = 0; var offRoad = 0
        val examples = ArrayList<String>()
        val stillPed = IntArray(t.peds.size + 64); val stillCar = IntArray(t.cars.size + 64)
        val lastX = FloatArray(t.peds.size + 64); val lastY = FloatArray(t.peds.size + 64)
        var frozenPeds = 0; var frozenCars = 0
        fun note(s: String) { if (examples.size < 6) examples += s }
        for (f in 0 until frames) {
            // the camera drives toward a junction across town at 14 m/s; every 40 s it jumps somewhere else
            if (f % (30 * 40) == 0 && f > 0) { target = nodes[rnd.nextInt(nodes.size)]; fx = target.x; fy = target.y }
            else {
                val d = hypot(target.x - fx, target.y - fy)
                if (d < 20f) target = nodes[rnd.nextInt(nodes.size)]
                else { fx += (target.x - fx) / d * 14f * dt; fy += (target.y - fy) / d * 14f * dt }
            }
            t.update(dt, fx, fy, 230f, -9999f, -9999f)
            for ((i, a) in t.peds.withIndex()) {
                if (a.hidden || !a.placed) continue
                if (!a.x.isFinite() || !a.y.isFinite()) { bad++; note("ped $i at a bad position"); continue }
                // walking or waiting (not on the way in or out of a shop door)
                if (a.state <= 2) {
                    var hit = -1
                    city.forBuildingsIn(a.x - 1f, a.y - 1f, a.x + 1f, a.y + 1f) { b -> if (b.solid && b.height > 0f && b.contains(a.x, a.y)) hit = b.id }
                    if (hit >= 0) { inside++; note("${city.mapId}: ped $i (state ${a.state}) inside building $hit at %.0f,%.0f".format(a.x, a.y)) }
                    if (city.shore.wet(a.x, a.y) && !onBridge(city, a.x, a.y)) { wet++; note("${city.mapId}: ped $i (state ${a.state}) on the water at %.0f,%.0f".format(a.x, a.y)) }
                }
                if (i < stillPed.size) {
                    val moved = hypot(a.x - lastX[i], a.y - lastY[i])
                    if (moved < .001f && a.state != 1 && a.state != 4) stillPed[i]++ else stillPed[i] = 0
                    if (stillPed[i] == 30 * 90) { frozenPeds++; note("${city.mapId}: ped $i frozen 90 s (state ${a.state}) at %.0f,%.0f".format(a.x, a.y)) }
                    lastX[i] = a.x; lastY[i] = a.y
                }
            }
            for ((i, a) in t.cars.withIndex()) {
                if (!a.placed) continue
                if (!a.x.isFinite() || !a.y.isFinite() || !a.speed.isFinite()) { bad++; note("car $i at a bad position"); continue }
                if (!a.inJunction && a.edge >= 0) {
                    val e = city.roads.edges[a.edge]
                    if (Geo.distToSeg(a.x, a.y, e.ax, e.ay, e.bx, e.by) > e.cls.halfWidth + 1.5f) {
                        offRoad++; note("${city.mapId}: car $i off its road (edge ${e.id}) at %.0f,%.0f".format(a.x, a.y))
                    }
                }
                if (i < stillCar.size) {
                    if (a.speed < .05f) stillCar[i]++ else stillCar[i] = 0
                    if (stillCar[i] == 30 * 90) { frozenCars++; note("${city.mapId}: car $i stopped 90 s (edge ${a.edge}, junction ${a.inJunction}) at %.0f,%.0f".format(a.x, a.y)) }
                }
            }
        }
        val ok = bad == 0 && inside == 0 && wet == 0 && offRoad == 0 && frozenPeds == 0 && frozenCars == 0
        val s = "${city.mapId}: bad=$bad inside=$inside wet=$wet offRoad=$offRoad frozenPeds=$frozenPeds frozenCars=$frozenCars" +
            if (examples.isEmpty()) "" else "\n   " + examples.joinToString("\n   ")
        return Result(ok, s)
    }

    private fun onBridge(city: City, x: Float, y: Float): Boolean {
        var hit = false
        city.roads.near(x, y, 30f) { id ->
            val e = city.roads.edges[id]
            if (e.overWater && Geo.distToSeg(x, y, e.ax, e.ay, e.bx, e.by) < e.cls.outer + .5f) hit = true
        }
        return hit
    }
}

package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.OBB
import com.hustlecity.game.world.RoadClass
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Bays along the streets with cars in some of them: traffic gets by, the cars are solid, and PARK finds a free bay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingTest {
    private fun box(x: Float, y: Float, heading: Float, length: Float, width: Float) = OBB(x, y, sin(heading), -cos(heading), length * .5f, width * .5f)

    @Test
    fun streetsHaveBaysAndTrafficGetsBy() {
        for (map in listOf(Maps.NYC, Maps.TOKYO, Maps.CLASSIC)) {
            val city = City(1337L, map)
            val bays = city.parked.bays
            val taken = bays.count { it.car != null }
            println("$map: ${bays.size} bays, $taken taken, ${city.parked.lotCars.size} cars in lots")
            assertTrue("$map: streets have bays", bays.size > 150)
            assertTrue("$map: some taken, most free ($taken of ${bays.size})", taken in bays.size / 5..bays.size * 3 / 5)
            // only along ordinary streets, never in a junction
            for (b in bays) assertTrue(city.roads.edges[b.edge].cls == RoadClass.STREET)
            // traffic of every size drives past for a few minutes without touching one
            val t = Traffic(Random(2))
            t.attach(city)
            t.ensure(90, 0)
            val n = city.roads.nodes.filter { it.degree >= 3 }
            val focus = n[n.size / 2]
            t.update(0f, focus.x, focus.y, 230f, -9999f, -9999f, initial = true)
            var touches = 0
            repeat(30 * 120) { f ->
                t.update(1f / 30f, focus.x, focus.y, 230f, -9999f, -9999f)
                if (f % 3 != 0) return@repeat
                for (a in t.cars) {
                    if (!a.placed) continue
                    val mine = box(a.x, a.y, a.heading, a.length, a.width)
                    city.parked.near(a.x, a.y, 8f) { c ->
                        if (mine.overlaps(box(c.x, c.y, c.heading, c.length, c.width), -.04f)) {
                            if (touches < 3 || touches % 100 == 0) {
                                val bay = city.parked.bays.first { it.car === c }
                                val be = city.roads.edges[bay.edge]
                                println("  $map: a style ${a.style} (${a.width} m wide) touched a parked car at %.0f,%.0f: car jn=${a.inJunction} edge=${a.edge} lane=${a.lane} speed=%.1f; bay edge ${bay.edge} side ${bay.side} s=%.1f of %.1f (clear %.1f/%.1f) nodes deg ${city.roads.nodes[be.a].degree}/${city.roads.nodes[be.b].degree}".format(c.x, c.y, a.speed, bay.s, be.len, be.clearA, be.clearB))
                            }
                            touches++
                        }
                    }
                }
            }
            assertTrue("$map: traffic clipped parked cars $touches times", touches == 0)
        }
    }

    @Test
    fun parkedCarsAreSolidAndParkFindsAFreeBay() {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1, mapId = Maps.NYC)
        val car = e.city.parked.bays.first { it.car != null }.car!!
        val out = FloatArray(2)
        assertTrue("walking into a parked car", e.city.parked.bump(car.x + .3f, car.y, .45f, out))
        // drive up beside a taken bay and park: you end up in a free bay, not on top of the parked car
        val bay = e.city.parked.bays.first { b -> b.car != null && e.city.parked.bays.any { it.edge == b.edge && it.side == b.side && it.car == null } }
        val v = e.state.vehicles.first()
        v.x = bay.x; v.y = bay.y; v.heading = bay.heading
        e.enterVehicle(v)
        assertTrue(e.parkVehicle())
        val parked = e.city.parked
        var clash = false
        parked.near(v.x, v.y, 6f) { c -> if (box(v.x, v.y, v.heading, v.def.length, v.def.width).overlaps(box(c.x, c.y, c.heading, c.length, c.width), -.05f)) clash = true }
        assertTrue("parked on top of another car", !clash)
        assertTrue("in a bay", parked.bays.any { kotlin.math.abs(it.x - v.x) < .01f && kotlin.math.abs(it.y - v.y) < .01f })
    }
}

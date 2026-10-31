package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.OBB
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Cars are solid end to end: the player's car can't sink into a bus, and traffic never drives through the player's car. */
class CarCollisionTest {
    private fun box(x: Float, y: Float, heading: Float, length: Float, width: Float) = OBB(x, y, sin(heading), -cos(heading), length * .5f, width * .5f)

    @Test
    fun thePlayersCarStopsAtTheEndOfABus() {
        val city = City(1337L, Maps.NYC)
        val t = Traffic(Random(1))
        t.attach(city)
        t.ensure(1, 0)
        t.update(0f, 500f, 500f, 230f, -9999f, -9999f, initial = true)
        val bus = t.cars[0]
        bus.x = 0f; bus.y = 0f; bus.heading = 0f; bus.length = 11f; bus.width = 2.5f
        val out = FloatArray(2)
        // a car's collision circle 5 m up the bus from its middle: well inside its body, not near its centre
        assertTrue("touching the bus's front end", t.bump(.5f, -5f, 1f, out))
        val f = -out[1]
        assertTrue("pushed clear of the bus (now %.2f m along it, %.2f across)".format(f, out[0]), f >= 6.4f || kotlin.math.abs(out[0]) >= 2.2f)
        // and nothing happens well clear of it
        assertTrue(!t.bump(5f, 0f, 1f, out))
    }

    @Test
    fun trafficNeverDrivesThroughThePlayersCar() {
        for (map in listOf(Maps.NYC, Maps.TOKYO, Maps.MANILA)) {
            val city = City(1337L, map)
            val t = Traffic(Random(4))
            t.attach(city)
            t.ensure(60, 0)
            val rnd = Random(9)
            val roads = city.roads.edges.filter { it.len > 60f && !it.overWater && it.cls.rank >= 1 }
            var overlaps = 0; var frames = 0; var passed = 0
            repeat(12) {
                // the player's car stopped somewhere awkward: in a lane, at the curb, or right where traffic turns
                val e = roads[rnd.nextInt(roads.size)]
                val s = rnd.nextFloat() * e.len
                val lateral = (rnd.nextFloat() * 2f - 1f) * (e.cls.halfWidth - 1f)
                val heading = kotlin.math.atan2(e.ux, -e.uy) + (rnd.nextFloat() - .5f) * .8f
                val cx = e.ax + e.ux * s + e.uy * lateral; val cy = e.ay + e.uy * s - e.ux * lateral
                t.obstacles = floatArrayOf(cx, cy, heading, 2.2f, .95f)
                t.obstacleCount = 1
                val mine = box(cx, cy, heading, 4.4f, 1.9f)
                t.update(0f, cx, cy, 230f, -9999f, -9999f, initial = true)
                // nobody starts out on top of it
                for (a in t.cars) if (box(a.x, a.y, a.heading, a.length, a.width).overlaps(mine)) a.placed = false
                t.update(0f, cx, cy, 230f, -9999f, -9999f)
                val near = HashSet<Int>()
                // (the car is dropped in, where no car can appear from: a bus already half way round a corner beside
                // it gets a second and a half to stop, as it would have seen the car coming)
                repeat(45) { t.update(1f / 30f, cx, cy, 230f, -9999f, -9999f) }
                repeat(30 * 40) {
                    t.update(1f / 30f, cx, cy, 230f, -9999f, -9999f)
                    frames++
                    for ((i, a) in t.cars.withIndex()) {
                        if (!a.placed) continue
                        if (box(a.x, a.y, a.heading, a.length, a.width).overlaps(mine, -.05f)) {
                            if (overlaps % 60 == 0) println("  $map overlap: car $i style ${a.style} len ${a.length} jn ${a.inJunction} lane ${a.lane} shift ${a.shift} speed ${a.speed} edge ${a.edge} (obstacle on ${e.id} ${e.cls} lateral %.1f, heading off %.2f) d=%.1f".format(lateral, heading - kotlin.math.atan2(e.ux, -e.uy), hypot(a.x - cx, a.y - cy)))
                            overlaps++
                        }
                        if (hypot(a.x - cx, a.y - cy) < 8f) near += i
                    }
                }
                passed += near.size
            }
            println("$map: $overlaps overlapping frames of $frames; $passed cars came close")
            assertTrue("$map: traffic drove into the player's car ($overlaps frames)", overlaps == 0)
        }
    }
}

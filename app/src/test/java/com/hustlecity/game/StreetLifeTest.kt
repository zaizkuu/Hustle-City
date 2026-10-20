package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Landmark
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.RoadClass
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Roads drawn twice, the Shibuya scramble's walk phase, and the crowds a film shot gathers round itself. */
class StreetLifeTest {
    private val tokyo by lazy { City(1337L, Maps.TOKYO) }
    private val scramble by lazy { tokyo.scrambleNode(tokyo.landmarks.first { it.style == Landmark.SCRAMBLE && it.kind == Landmark.BILLBOARDS })!! }

    @Test
    fun aStreetLyingUnderABoulevardGivesWayToIt() {
        val g = tokyo.roads
        assertTrue(scramble.scramble)
        // Aoyama-dori leaves the scramble along the same line as one of its streets: the street is the one hidden
        val under = scramble.edges.map { g.edges[it] }.filter { it.under }
        assertEquals(1, under.size)
        assertEquals(RoadClass.STREET, under[0].cls)
        assertTrue(scramble.edges.map { g.edges[it] }.any { it.cls == RoadClass.BOULEVARD && !it.under })
        // everywhere, a hidden road runs inside a bigger one, never just past the end of the next piece of its own road
        for (m in Maps.all) {
            val c = City(1337L, m.id)
            for (e in c.roads.edges) {
                if (!e.under) continue
                val x = (e.ax + e.bx) / 2; val y = (e.ay + e.by) / 2
                val cover = c.roads.edges.any { o ->
                    if (o.id == e.id || o.cls.rank < e.cls.rank) return@any false
                    val along = (x - o.ax) * o.ux + (y - o.ay) * o.uy
                    along > 0f && along < o.len && abs(-(x - o.ax) * o.uy + (y - o.ay) * o.ux) <= o.cls.halfWidth
                }
                assertTrue("${m.id}: edge ${e.id} is hidden without a road over it", cover)
            }
        }
    }

    @Test
    fun theScrambleHoldsEveryCarWhilePeopleCross() {
        val t = Traffic(Random(3))
        t.attach(tokyo)
        val g = tokyo.roads
        var walk = 0f; var green = 0f
        for (step in 0 until (Traffic.SCRAMBLE_CYCLE * 10).toInt()) {
            t.lightClock = step / 10f
            if (t.scrambleWalk(scramble)) {
                walk += .1f
                for (id in scramble.edges) assertEquals("cars must wait while people cross", 2, t.light(scramble, g.edges[id]))
            } else if (scramble.edges.any { t.light(scramble, g.edges[it]) == 0 }) green += .1f
        }
        assertTrue("walk lasts ${walk}s", walk in 9f..12f)
        assertTrue("cars get the rest: ${green}s", green > 25f)
    }

    @Test
    fun aCrowdFloodsTheScrambleOnTheWalkSignal() {
        val t = Traffic(Random(5))
        t.attach(tokyo)
        t.ensure(40, 400)
        // the walk comes on twenty seconds in, after a red that gathers people at the corners
        t.lightClock = ((34.5f - scramble.phase - 20f) % Traffic.SCRAMBLE_CYCLE + Traffic.SCRAMBLE_CYCLE) % Traffic.SCRAMBLE_CYCLE
        t.update(0f, scramble.x, scramble.y, 230f, -9999f, -9999f, initial = true)
        t.scatter(scramble.x, scramble.y, 45f, .7f)
        val placed = t.peds.take(280)
        val inside = placed.count { hypot(it.x - scramble.x, it.y - scramble.y) <= 45f }
        assertTrue("scattered within the radius ($inside of ${placed.size})", inside >= placed.size * .95f)
        val dt = 1f / 30f
        var crossingBefore = 0
        var crossingDuring = 0
        for (f in 0 until 30 * 26) {
            t.update(dt, scramble.x, scramble.y, 230f, -9999f, -9999f)
            // people out on the scramble itself: off the curb and walking across it
            val crossing = t.peds.count { !it.hidden && it.state == 2 && it.jNode == scramble.id && hypot(it.x - scramble.x, it.y - scramble.y) < 14f }
            if (f in 30 * 12..30 * 19) crossingBefore = maxOf(crossingBefore, crossing)
            if (f >= 30 * 22) crossingDuring = maxOf(crossingDuring, crossing)
        }
        assertTrue("the crossing stays clear on red ($crossingBefore)", crossingBefore <= 4)
        assertTrue("and fills on the walk ($crossingDuring)", crossingDuring >= 20)
    }
}

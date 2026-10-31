package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The GPS trail runs down the player's own lane: on the right of the road (on the left in Tokyo, where traffic keeps
 * left), a lane or so off the centre line the route follows, never down the middle of the road.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GpsLaneTest {
    @Test
    fun theTrailKeepsToYourLane() {
        for (map in listOf(Maps.NYC, Maps.TOKYO, Maps.MANILA, Maps.CLASSIC)) {
            val e = GameEngine(null).also { it.newGame("T", 0, 0, 1, mapId = map) }
            val left = e.city.leftHand
            val rnd = Random(4)
            var checked = 0; var wrongSide = 0; var middle = 0
            val shops = e.city.buildings.filter { it.kind == BKind.RETAIL && it.frontEdge >= 0 }
            repeat(12) {
                val from = shops[rnd.nextInt(shops.size)]; val to = shops[rnd.nextInt(shops.size)]
                if (from.id == to.id) return@repeat
                val p = e.state.player
                p.x = from.doorX; p.y = from.doorY
                p.waypoint = -1; e.gps.clear()
                e.setWaypoint(to.id)
                repeat(3) { e.frame(1 / 30f) }
                val gps = e.gps
                if (!gps.active) return@repeat
                val c = gps.route; val r = gps.lane
                // the trail beside each stretch of the route's centre line between junctions (well clear of the
                // junctions, where it curves from one lane into the next; not the walks to and from the doors)
                for (q in 1 until gps.points - 2) {
                    val ax = c[q * 2]; val ay = c[q * 2 + 1]; val bx = c[q * 2 + 2]; val by = c[q * 2 + 3]
                    val len = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
                    if (len < 40f) continue
                    val ux = (bx - ax) / len; val uy = (by - ay) / len
                    for (k in 0 until gps.lanePoints - 1) {
                        val mx = (r[k * 2] + r[k * 2 + 2]) / 2; val my = (r[k * 2 + 1] + r[k * 2 + 3]) / 2
                        val along = (mx - ax) * ux + (my - ay) * uy
                        if (along < 18f || along > len - 18f) continue
                        // right of the way the route goes (y points south: right of (ux, uy) is (-uy, ux))
                        val right = (mx - ax) * -uy + (my - ay) * ux
                        if (kotlin.math.abs(right) > 14f) continue
                        val side = if (left) -right else right
                        checked++
                        if (side <= 0f) {
                            if (wrongSide < 3) println("  $map: trail on the wrong side at %.0f,%.0f (%.1f m right of the centre)".format(mx, my, right))
                            wrongSide++
                        } else if (side < 1.6f) {
                            if (middle < 3) println("  $map: trail down the middle at %.0f,%.0f (%.1f m off)".format(mx, my, side))
                            middle++
                        }
                    }
                }
            }
            println("$map (${if (left) "left" else "right"}-hand): $checked stretches checked, $wrongSide on the wrong side, $middle down the middle")
            assertTrue("$map: few stretches checked ($checked)", checked > 20)
            assertTrue("$map: $wrongSide stretches on the wrong side of the road", wrongSide == 0)
            assertTrue("$map: $middle stretches down the middle of the road", middle == 0)
        }
    }
}

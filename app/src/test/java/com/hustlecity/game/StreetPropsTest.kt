package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.StreetProps
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Street furniture stands on the pavement: never on a road, a crossing or a junction, and never inside a building. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreetPropsTest {
    @Test
    fun propsStayOnThePavement() {
        for (map in Maps.all) {
            val e = GameEngine(null)
            e.newGame("T", 0, 0, 1, mapId = map.id)
            val pr = StreetProps(e.city)
            val bad = ArrayList<String>()
            var n = 0
            for (i in 0 until pr.count) {
                val k = pr.kind[i]
                if (k == StreetProps.FOUNTAIN || k == StreetProps.PARK_LAMP) continue
                n++
                val x = pr.x[i]; val y = pr.y[i]
                // along the street is across the facing direction
                val ax = pr.dirY[i]; val ay = -pr.dirX[i]
                val half = when (k) { StreetProps.VENDING -> 1.5f; StreetProps.VENDOR -> 1.3f; else -> 0f }
                for (s in floatArrayOf(-half, 0f, half)) {
                    val px = x + ax * s; val py = y + ay * s
                    if (e.city.roads.onAsphalt(px, py)) { bad += "kind $k at ${x.toInt()},${y.toInt()} on a road"; break }
                    if (k == StreetProps.VENDING || k == StreetProps.VENDOR) {
                        var inside = false
                        e.city.forBuildingsIn(px - 1f, py - 1f, px + 1f, py + 1f) { b -> if (b.solid && b.contains(px, py)) inside = true }
                        if (inside) { bad += "kind $k at ${x.toInt()},${y.toInt()} in a building"; break }
                    }
                }
            }
            println("${map.id}: $n props, ${bad.size} misplaced")
            assertTrue("${map.id}: ${bad.take(10)}", bad.isEmpty())
            assertTrue("${map.id}: still plenty of props ($n)", n > 300)
        }
    }
}

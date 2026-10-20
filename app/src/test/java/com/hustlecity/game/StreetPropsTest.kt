package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.StreetProps
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Street furniture stands on the pavement: never on a road, a crossing or a junction, and never inside a building.
 * Whatever is strung over the street is tied at both ends.
 */
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
                // strings of flags and lanterns hang over the street by design (their ends are checked below)
                if (k == StreetProps.FOUNTAIN || k == StreetProps.PARK_LAMP || k == StreetProps.BUNTING || k == StreetProps.LANTERNS) continue
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

    /**
     * Banderitas, lantern strings and the wires off Manila's poles: every end meets the front of a house tall enough to
     * hold it, or a pole of its own standing on the pavement. Nothing hangs in the air.
     */
    @Test
    fun nothingHangsInTheAir() {
        for (map in listOf(Maps.MANILA, Maps.TOKYO)) {
            val e = GameEngine(null)
            e.newGame("T", 0, 0, 1, mapId = map)
            val city = e.city
            val pr = StreetProps(city)
            val bad = ArrayList<String>()
            var strings = 0; var poles = 0; var wires = 0
            for (i in 0 until pr.count) {
                val k = pr.kind[i]
                val x = pr.x[i]; val y = pr.y[i]; val fx = pr.dirX[i]; val fy = pr.dirY[i]
                if (k == StreetProps.BUNTING || k == StreetProps.LANTERNS) {
                    strings++
                    val h = pr.aux[i] / 2
                    val top = if (k == StreetProps.BUNTING) StreetProps.BUNTING_HIGH else StreetProps.LANTERN_Z
                    for (end in 0..1) {
                        val s = if (end == 0) -1f else 1f
                        val ex = x + fx * h * s; val ey = y + fy * h * s
                        if ((pr.link[i] shr end) and 1 == 1) {
                            poles++
                            if (solid(city, ex, ey) != null || city.roads.onAsphalt(ex, ey)) bad += "kind $k: a pole at ${ex.toInt()},${ey.toInt()} off the pavement"
                        } else if (!onWall(city, ex, ey, fx * s, fy * s, top)) bad += "kind $k: an end at ${ex.toInt()},${ey.toInt()} on no wall"
                    }
                }
                if (k == StreetProps.UTIL_POLE) {
                    // the pole's frame: x along the street, y toward it; the drops run back from it, the bundle over it
                    val ax = fy; val ay = -fx
                    for (d in 0..2) {
                        val f = pr.drops[i * 3 + d]
                        if (f <= 0f) continue
                        wires++
                        val ex = x + ax * StreetProps.DROP_X[d] - fx * f; val ey = y + ay * StreetProps.DROP_X[d] - fy * f
                        if (!onWall(city, ex, ey, -fx, -fy, StreetProps.DROP_Z[d])) bad += "pole $i: drop $d ends at ${ex.toInt()},${ey.toInt()} on no wall"
                    }
                    val c = pr.cross[i]
                    if (c > 0f) {
                        wires++
                        val ex = x + fx * c; val ey = y + fy * c
                        if (!onWall(city, ex, ey, fx, fy, StreetProps.BUNDLE_Z + 2 * StreetProps.BUNDLE_STEP)) bad += "pole $i: the bundle ends at ${ex.toInt()},${ey.toInt()} on no wall"
                    }
                }
            }
            println("$map: $strings strings ($poles ends on poles), $wires wires into houses, ${bad.size} loose")
            assertTrue("$map: ${bad.take(10)}", bad.isEmpty())
            // and the streets are still dressed
            if (map == Maps.MANILA) { assertTrue("banderitas: $strings", strings >= 100); assertTrue("wires: $wires", wires >= 300) }
            else assertTrue("lantern strings: $strings", strings >= 40)
        }
    }

    private fun solid(city: City, px: Float, py: Float): Building? {
        var hit: Building? = null
        city.forBuildingsIn(px - 1f, py - 1f, px + 1f, py + 1f) { b -> if (hit == null && b.solid && b.contains(px, py)) hit = b }
        return hit
    }

    /** An end at (px, py) of a line at height [z] running out toward (dx, dy): just short of a wall it's tied to, which stands taller. */
    private fun onWall(city: City, px: Float, py: Float, dx: Float, dy: Float, z: Float): Boolean {
        if (solid(city, px - dx * .05f, py - dy * .05f) != null) return false
        val b = solid(city, px + dx * .08f, py + dy * .08f) ?: return false
        return b.height >= z + StreetProps.TIE_CLEAR - .001f
    }
}

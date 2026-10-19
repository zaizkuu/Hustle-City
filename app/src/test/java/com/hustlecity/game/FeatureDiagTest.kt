package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Maps
import org.junit.Test

/** Temporary: prints how landmarks sit among the roads. */
class FeatureDiagTest {
    @Test
    fun diag() {
        for (m in Maps.all) {
            val city = City(1337L, m.id)
            val byHood = city.buildings.groupingBy { it.hood.name }.eachCount()
            println("${m.id}: ${city.buildings.size} buildings $byHood")
            for (l in city.landmarks) {
                val f = l.footprint
                val near = ArrayList<String>()
                if (f != null) for (e in city.roads.edges) {
                    // distance from the footprint to the road centre line, sampled
                    var d = Float.MAX_VALUE
                    for (k in 0..20) {
                        val x = e.ax + (e.bx - e.ax) * k / 20f; val y = e.ay + (e.by - e.ay) * k / 20f
                        d = minOf(d, if (Geo.inside(x, y, f)) 0f else Geo.distToPoly(x, y, f))
                    }
                    if (d < e.cls.outer) near += "${e.name}(r${e.cls.rank}, ${"%.1f".format(d)})"
                }
                println("  ${l.name} kind=${l.kind} at ${l.x},${l.y}: ${near.distinct()}")
            }
            for (r in city.rails) println("  rail ${r.name} pts=${r.pts.size / 2}")
            for (w in city.walls) println("  wall ${w.toList().chunked(2)}")
        }
    }
}

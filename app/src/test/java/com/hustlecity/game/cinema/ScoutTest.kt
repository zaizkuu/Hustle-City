package com.hustlecity.game.cinema

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.Maps
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lists each map's landmarks, parks, rails and lots for planning trailer shots (HC_CINEMA=1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScoutTest {
    @Test
    fun scout() {
        assumeTrue(System.getenv("HC_CINEMA") == "1")
        for (m in Maps.all) {
            val e = GameEngine(null); e.newGame("S", 0, 0, 1, mapId = m.id)
            val c = e.city
            println("MAP ${m.id} bounds ${c.bounds.toList()} start ${e.state.player.x},${e.state.player.y} maxH ${c.maxHeight}")
            for (l in c.landmarks) println("  LM ${l.name} kind ${l.kind} style ${l.style} at ${l.x.toInt()},${l.y.toInt()} angle ${l.angle} size ${l.size}")
            for (h in c.neighborhoods) {
                val bs = c.buildings.filter { it.neighborhood == h.id }
                if (bs.isEmpty()) continue
                println("  HOOD ${h.id} ${h.name} ${h.look} n=${bs.size} cx=${bs.map { it.cx }.average().toInt()} cy=${bs.map { it.cy }.average().toInt()} tallest=${bs.maxOf { it.height }.toInt()}")
            }
            for (r in c.rails) println("  RAIL ${r.name}")
            c.parks.sortedByDescending { com.hustlecity.game.world.Geo.area(it.poly) }.take(3).forEach { println("  PARK ${it.centre} kind ${it.kind}") }
            println("  LOTS ${c.buildings.filter { it.kind == com.hustlecity.game.world.BKind.PARKING }.take(4).map { "${it.cx.toInt()},${it.cy.toInt()} ${it.w.toInt()}x${it.d.toInt()}" }}")
            listOf("metro", "motors", "essentials").forEach { id -> c.placeBuilding(id)?.let { println("  PLACE $id ${it.doorX.toInt()},${it.doorY.toInt()}") } }
        }
    }
}

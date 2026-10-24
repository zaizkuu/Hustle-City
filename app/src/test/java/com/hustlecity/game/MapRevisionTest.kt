package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A map's street plan can be fixed for new games without moving anyone's buildings: saves keep the layout revision
 * their game started on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapRevisionTest {
    @Test
    fun everyBridgeLandsOnAStreet() {
        for (m in Maps.all) {
            val g = City(1337L, m.id).roads
            for (e in g.edges) if (e.overWater) for (end in intArrayOf(e.a, e.b)) {
                // follow the bridge's approach out to where it meets other streets
                var node = end; var prev = e.id; var steps = 0
                while (g.nodes[node].degree == 2 && steps < 50) {
                    val next = g.nodes[node].edges.first { it != prev }
                    node = g.edges[next].other(node); prev = next; steps++
                }
                val n = g.nodes[node]
                assertTrue("${m.id}: ${e.name} ends at (%.0f, %.0f) without meeting a street".format(n.x, n.y), n.degree > 1)
            }
        }
    }

    @Test
    fun savesKeepTheirLayout() {
        // the first Manila layout, exactly as games started on it were laid out
        val old = City(1337L, Maps.MANILA, 0)
        assertEquals(0, old.rev)
        assertEquals("the first layout's buildings", 1114, old.buildings.size)
        assertEquals("the first layout's streets", 1012, old.roads.edges.size)
        assertTrue("Manila has a newer layout", Maps.byId(Maps.MANILA).revision >= 1)
        // a new game records the newest
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1, mapId = Maps.MANILA)
        assertEquals(Maps.byId(Maps.MANILA).revision, e.state.mapRev)
        assertEquals(e.state.mapRev, e.city.rev)
        // a save from before revisions were recorded has none: it loads the first layout
        val text = e.saves.encode(e.state).replace(Regex("\"mapRev\":\\s*\\d+,?"), "")
        val loaded = e.saves.decode(text)
        assertEquals(0, loaded.mapRev)
        assertEquals(old.buildings.size, City(loaded.seed, loaded.mapId, loaded.mapRev).buildings.size)
    }
}

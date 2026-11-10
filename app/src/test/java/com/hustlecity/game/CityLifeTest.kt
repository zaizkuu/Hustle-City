package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.Achievements
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Transit
import com.hustlecity.game.engine.buyBuilding
import com.hustlecity.game.engine.endTenancy
import com.hustlecity.game.engine.letOut
import com.hustlecity.game.engine.nearStation
import com.hustlecity.game.engine.rideTrain
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.stations
import com.hustlecity.game.engine.tenantRentOf
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.dist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tenants in buildings you own, the trains you can ride, and the trophies for getting somewhere. */
class CityLifeTest {
    private fun game(map: String = Maps.CLASSIC) = GameEngine(null).also { it.newGame("T", 0, 0, 0, mapId = map) }

    @Test
    fun aTenantPaysRentForABuildingYouOwn() {
        val e = game()
        e.state.player.courses += Catalog.COURSE_REAL_ESTATE
        e.state.player.money = 2_000_000.0
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL }
        // rented buildings can't be sublet
        assertTrue(e.buyBuilding(b.id))
        assertTrue(e.letOut(b.id))
        val r = e.state.rentalFor(b.id)!!
        assertTrue(r.tenant.isNotEmpty())
        assertEquals(e.tenantRentOf(b), r.tenantRent, .01)
        // no business of yours where a tenant lives, and their rent arrives at midnight
        assertNull(e.startBusiness(b.id, "coffee", "X"))
        repeat(1440) { e.simulateMinute() }
        val day = e.state.history.last()
        assertEquals(r.tenantRent / 30.0, day.revenue, .01)
        e.endTenancy(b.id)
        assertTrue(r.tenant.isEmpty())
        assertNotNull(e.startBusiness(b.id, "coffee", "X"))
        assertFalse("a building in use isn't let", e.letOut(b.id))
        assertFalse("nor is your home", e.letOut(e.state.player.home))
    }

    @Test
    fun trainsCarryYouBetweenStations() {
        for (map in listOf(Maps.MANILA, Maps.TOKYO)) {
            val e = game(map)
            val st = e.stations
            assertTrue("$map has stations (${st.size})", st.size >= 4)
            assertEquals("station names are told apart on a line", st.size, st.map { it.line + it.name }.distinct().size)
            val from = st.first(); val to = st.last { it.line == from.line }
            val p = e.state.player
            assertNull("home isn't at a station", e.nearStation()?.takeIf { dist(p.x, p.y, it.x, it.y) > 30f })
            p.x = from.x + 5f; p.y = from.y
            assertEquals(from.id, e.nearStation()!!.id)
            val money = p.money; val minute = e.state.minute
            assertTrue(e.rideTrain(to))
            assertEquals(Transit.fare(from, to), money - p.money, .001)
            var guard = 0
            while (e.skip != null && guard++ < 100_000) e.frame(.05f)
            assertNull(e.skip)
            assertTrue("you step off by the far station ${to.name} ${to.x},${to.y} vs ${p.x},${p.y} skip=${e.skip}", dist(p.x, p.y, to.x, to.y) < 80f)
            assertTrue("on the pavement", !e.city.roads.onAsphalt(p.x, p.y))
            assertTrue("and the ride took its time", e.state.minute - minute >= Transit.minutes(from, to))
            // no riding to another line's stop from here
            st.firstOrNull { it.line != to.line }?.let { assertFalse(e.rideTrain(it)) }
        }
        assertTrue("the classic grid has no railway", game().stations.isEmpty())
    }

    @Test
    fun achievementsUnlockOnceAndStayInTheSave() {
        val e = game()
        Achievements.check(e)
        assertTrue(e.state.achievements.isEmpty())
        e.state.player.money = 2_000_000.0
        repeat(31) { e.simulateMinute() }
        assertTrue("a million in cash is a net worth of a million", "million" in e.state.achievements && "nw100k" in e.state.achievements)
        val day = e.state.achievements["million"]
        e.state.player.money = 10.0
        repeat(60) { e.simulateMinute() }
        assertEquals("kept once earned", day, e.state.achievements["million"])
        val copy = e.saves.decode(e.saves.encode(e.state))
        assertEquals(e.state.achievements, copy.achievements)
        assertEquals("ids are unique", Achievements.all.size, Achievements.all.map { it.id }.distinct().size)
    }
}

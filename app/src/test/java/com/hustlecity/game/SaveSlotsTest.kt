package com.hustlecity.game

import androidx.test.core.app.ApplicationProvider
import com.hustlecity.game.engine.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A new game never writes over another: each takes its own slot until all three are full. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveSlotsTest {
    @Test
    fun gamesKeepToTheirOwnSlots() {
        val e = GameEngine(ApplicationProvider.getApplicationContext())
        for (i in 0 until 3) e.saves.delete(i)
        e.newGame("Ana", 0, 0, 1, mapId = "manila")
        e.state.player.money = 777.0; e.save()
        assertEquals(0, e.saves.slot)
        e.newGame("Ben", 0, 0, 1, mapId = "tokyo")
        assertEquals(1, e.saves.slot)
        val slots = e.saves.slots()
        assertEquals("Ana", slots[0]!!.name); assertEquals("manila", slots[0]!!.mapId); assertEquals(777.0, slots[0]!!.money, .01)
        assertEquals("Ben", slots[1]!!.name)
        assertNull(slots[2])
        // loading the first brings Ana's Manila back, and saving goes to her slot again
        assertTrue(e.loadGame(0))
        assertEquals("Ana", e.state.player.name); assertEquals("manila", e.city.mapId); assertEquals(0, e.saves.slot)
        e.saves.delete(1)
        assertNull(e.saves.slots()[1])
        assertEquals(1, e.saves.firstFree())
        for (i in 0 until 3) e.saves.delete(i)
    }
}

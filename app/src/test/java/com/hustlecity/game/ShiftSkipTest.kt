package com.hustlecity.game

import android.view.KeyEvent
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.toggleWork
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.SkipKind
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Inside your store the keys walk you about, and while you work the floor F fast-forwards to the end of the shift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftSkipTest {
    private fun store(): Triple<GameEngine, InteriorScene, com.hustlecity.game.model.Business> {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.settings.carControls = 2
        e.state.minute = (e.state.minute / 1440) * 1440 + 11 * 60
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.gridW >= 10 && it.gridD >= 8 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "giftshop", "X")!!
        biz.tempClosed = false
        e.state.player.inBuilding = b.id
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.interior = sc
        e.ui.phoneOpen = false
        return Triple(e, sc, biz)
    }

    @Test
    fun keysWalkYouRoundTheStore() {
        val (e, sc, _) = store()
        sc.px = sc.w / 2f; sc.py = sc.d - 2f
        val x0 = sc.px; val y0 = sc.py
        assertTrue("the key is taken indoors", e.onKey(KeyEvent.KEYCODE_W, true))
        repeat(30) { e.frame(1 / 30f) }
        e.onKey(KeyEvent.KEYCODE_W, false)
        repeat(3) { e.frame(1 / 30f) }
        val moved = kotlin.math.hypot(sc.px - x0, sc.py - y0)
        assertTrue("W walked the player (%.2f m)".format(moved), moved > .5f)
    }

    @Test
    fun fFastForwardsToTheEndOfTheShift() {
        val (e, _, biz) = store()
        // not working yet: F does nothing
        e.onKey(KeyEvent.KEYCODE_F, true); e.onKey(KeyEvent.KEYCODE_F, false)
        assertTrue("no skip before working", e.skip == null)
        e.toggleWork(biz)
        assertEquals(biz, e.workingHere())
        e.onKey(KeyEvent.KEYCODE_F, true); e.onKey(KeyEvent.KEYCODE_F, false)
        val job = e.skip
        assertTrue("F started the skip", job != null && job.kind == SkipKind.WORK)
        // it runs to closing time
        assertEquals(biz.closeHour, ((job!!.end % 1440) / 60).toInt())
        var guard = 0
        while (e.skip != null && guard++ < 20000) e.frame(1 / 30f)
        assertTrue("the skip finished", e.skip == null)
        assertEquals(biz.closeHour, e.state.hour)
    }
}

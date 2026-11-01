package com.hustlecity.game

import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.Job
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.toggleWork
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A hair salon: a stylist serves one client at a time, and the others wait in their chairs for them; the stylist
 * goes to whoever has waited longest. You, working the till, stand behind it facing across it, with the cashier beside
 * you, not in you; with no stylist on shift you do the cutting yourself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SalonTest {
    private fun salon(rot: Int, stylists: Int, cashier: Boolean, sofa: Boolean = false): Pair<GameEngine, InteriorScene> {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.state.minute = (e.state.minute / 1440) * 1440 + 11 * 60
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 6..12 && it.gridD in 10..20 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "hairdresser", "Golden Cuts")!!
        biz.tempClosed = false
        val room = biz.backRoom.coerceAtLeast(0)
        biz.furniture += listOf(PlacedItem("cash_register", 3, room + 3, rot), PlacedItem("hair_chair", 6, room + 6, rot), PlacedItem("hair_chair", 3, room + 8, rot))
        if (sofa) biz.furniture += listOf(PlacedItem("lounge_sofa", 1, room + 1, 0), PlacedItem("waiting_chairs", 1, room + 11, 0))
        repeat(stylists) { e.state.employees += Employee(e.state.newId(), "Kim", StaffRole.HAIRSTYLIST, .75, 18.0, 18.0, 27, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127) }
        if (cashier) e.state.employees += Employee(e.state.newId(), "Lee", StaffRole.CUSTOMER_SERVICE, .75, 18.0, 18.0, 27, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        e.state.player.inBuilding = b.id
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.interior = sc
        e.economy.servedRate[biz.id] = 200.0
        e.toggleWork(biz)
        return e to sc
    }

    @Test
    fun oneClientAtATimeAndYouWorkTheTill() {
        for (rot in 0..3) {
            val (_, sc) = salon(rot, 1, true)
            var seatedFrames = 0; var servedFrames = 0; var waitingFrames = 0; var switches = 0
            var lastServed = -1
            repeat(30 * 300) {
                val before = sc.visitors.filter { it.seated }.associateWith { it.wait }
                sc.update(1 / 30f, true)
                val seated = sc.visitors.filter { it.seated }
                if (seated.isEmpty()) return@repeat
                seatedFrames++
                val served = seated.filter { sc.isServed(it.item) }
                // one stylist: never two clients at once, and only with the stylist right there
                assertTrue("rot $rot: one client at a time", served.size <= 1)
                for (v in served) assertTrue("rot $rot: their stylist is at the chair", sc.staff.any { it.job == Job.HAIR && it.item == v.item && it.pathIdx >= it.path.size })
                if (served.isNotEmpty()) servedFrames++
                // the others wait their turn: their time in the chair doesn't run down
                for (v in seated) if (!sc.isServed(v.item) && v in before && v.wait > 0f) { waitingFrames++; assertEquals("rot $rot: waiting, not being served", before[v]!!, v.wait, 1e-4f) }
                served.firstOrNull()?.let { if (it.item != lastServed) { switches++; lastServed = it.item } }
                // you're behind the till, facing straight over it, and the cashier isn't standing in you
                assertTrue(sc.playerJob == Job.TILL)
                val reg = sc.items[sc.playerTill]
                val fx = sin(sc.playerHeading); val fy = -cos(sc.playerHeading)
                val (wx, wy) = when (reg.rot) { 0 -> 0f to 1f; 1 -> -1f to 0f; 2 -> 0f to -1f; else -> 1f to 0f }
                assertTrue("rot $rot: you face across the till", abs(fx - wx) < .01f && abs(fy - wy) < .01f)
                for (s in sc.staff) assertTrue("rot $rot: the cashier isn't standing in you",
                    floor(s.x) != floor(sc.px) || floor(s.y) != floor(sc.py) || s.pathIdx < s.path.size)
            }
            println("rot $rot: someone seated $seatedFrames frames, being served $servedFrames, a client waiting their turn $waitingFrames, stylist moved to the next client $switches times")
            assertTrue("rot $rot: clients sat down", seatedFrames > 30 * 30)
            assertTrue("rot $rot: someone's being served most of the time someone's in a chair ($servedFrames of $seatedFrames)", servedFrames * 10 > seatedFrames * 7)
            assertTrue("rot $rot: with two chairs and one stylist, clients waited their turn", waitingFrames > 0)
            assertTrue("rot $rot: the stylist went from client to client", switches >= 3)
        }
    }

    /** With no stylist on shift, you working there cut the hair, at the chair of whoever has waited longest. */
    @Test
    fun youCutWhenThereIsNoStylist() {
        val (_, sc) = salon(0, 0, false)
        var served = 0; var seated = 0
        repeat(30 * 240) {
            sc.update(1 / 30f, true)
            if (sc.visitors.any { it.seated }) seated++
            if (sc.playerJob == Job.HAIR && sc.visitors.any { it.seated && it.item == sc.playerStation }) served++
        }
        println("you: someone seated $seated frames, you cutting $served")
        assertTrue("clients sat down", seated > 30 * 30)
        assertTrue("you cut their hair ($served of $seated)", served * 10 > seated * 6)
    }

    /**
     * With a couch (and waiting room chairs) in the salon, clients who'd have to wait for the stylist sit there instead of
     * in a salon chair, and are called over one at a time. Whenever a client sets off for a salon chair, either the
     * stylist was free for them, or they were just called up from the waiting area, or the waiting area was full.
     */
    @Test
    fun clientsWaitOnTheCouch() {
        val (e, sc) = salon(0, 1, false, sofa = true)
        e.economy.servedRate[e.state.businesses.first().id] = 30.0
        var onCouch = 0; var served = 0; var calls = 0; var straight = 0
        fun atChair(v: com.hustlecity.game.engine.Person) = v.stage == 0 && sc.needsServer(v.item) && v.seat != null && (v.seated || v.goal >= 0)
        repeat(30 * 300) {
            val before = sc.visitors.associateWith { Triple(atChair(it), it.stage, 0) }
            val waitingBefore = sc.visitors.count { it.stage == InteriorScene.WAIT_STAGE }
            sc.update(1 / 30f, true)
            for (v in sc.visitors) {
                val was = before[v] ?: continue
                if (!atChair(v) || was.first) continue
                val others = sc.visitors.count { it !== v && atChair(it) }
                when {
                    was.second == InteriorScene.WAIT_STAGE -> calls++
                    others == 0 -> straight++
                    else -> assertTrue("a client went to a salon chair with the stylist busy and room to wait ($waitingBefore waiting)", waitingBefore >= 5)
                }
            }
            val waiting = sc.visitors.filter { it.stage == InteriorScene.WAIT_STAGE && it.seated }
            onCouch = maxOf(onCouch, waiting.size)
            for (v in waiting) assertTrue("they wait on the couch", com.hustlecity.game.data.Catalog.item(sc.items[v.item].itemId)?.tags?.contains("waiting") == true)
            if (sc.visitors.any { it.seated && sc.isServed(it.item) }) served++
        }
        println("couch: up to $onCouch waiting, $calls called up from the waiting area, $straight straight to a free stylist, someone served $served frames")
        assertTrue("clients waited on the couch", onCouch >= 1)
        assertTrue("and were called up in turn", calls >= 3)
        assertTrue("and they got served", served > 30 * 60)
    }
}

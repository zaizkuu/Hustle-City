package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
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

/**
 * Self-checkouts and order kiosks: customers pay at them with no cashier there, and they add to what the store can
 * serve without needing more staff. With nobody on shift at all, the store still sells through them while you're in
 * it. When every line is full, customers sit in the waiting area until there's room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelfServiceTest {
    private fun store(type: String, furniture: List<PlacedItem>, cashiers: Int, floor: Int = 0): Pair<GameEngine, InteriorScene> {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.state.minute = (e.state.minute / 1440) * 1440 + 12 * 60
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 8..12 && it.gridD in 10..20 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, type, "Test")!!
        biz.tempClosed = false
        val room = biz.backRoom.coerceAtLeast(0)
        biz.furniture += furniture.map { PlacedItem(it.itemId, it.x, room + it.y, it.rot) }
        for ((pid, _) in biz.type.products) biz.stock[pid] = 500
        repeat(cashiers) { e.state.employees += Employee(e.state.newId(), "C", StaffRole.CUSTOMER_SERVICE, .8, 15.0, 15.0, 25, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127) }
        repeat(floor) { e.state.employees += Employee(e.state.newId(), "F", StaffRole.CLEANER, .8, 15.0, 15.0, 25, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127) }
        e.state.player.inBuilding = b.id
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.interior = sc
        e.economy.servedRate[biz.id] = 250.0
        return e to sc
    }

    @Test
    fun selfCheckoutsAddCapacityWithoutStaff() {
        val (e, _) = store("supermarket", listOf(PlacedItem("cash_register", 1, 2, 0), PlacedItem("self_checkout", 4, 2, 0), PlacedItem("self_checkout", 6, 2, 0)), 1)
        val biz = e.state.businesses.first()
        // one cashier: the staffed till's 40 plus both self-checkouts' 25 each
        assertEquals(90.0, BizCalc.registerCapacity(biz, 1), .01)
        assertEquals(50.0, BizCalc.registerCapacity(biz, 0), .01)
        for (id in listOf("self_checkout", "order_kiosk", "waiting_chairs", "coffee_table")) {
            assertTrue("$id is in the catalog", Catalog.item(id) != null)
            assertTrue("$id is sold somewhere", Catalog.places.any { id in it.stock })
        }
    }

    /** An hour of the economy with nobody on shift; returns the customers served. */
    private fun anHour(e: GameEngine): Int {
        val biz = e.state.businesses.first()
        val before = biz.customersToday
        repeat(60) { e.state.minute++; e.economy.minute() }
        return biz.customersToday - before
    }

    @Test
    fun selfCheckoutsRunTheStoreWhileYoureIn() {
        val (e, _) = store("supermarket", listOf(PlacedItem("self_checkout", 4, 2, 0), PlacedItem("self_checkout", 6, 2, 0), PlacedItem("shelf", 1, 6, 0),
            PlacedItem("drinks_fridge", 5, 6, 0)), 0)
        val biz = e.state.businesses.first()
        assertTrue("the store has what a supermarket needs", BizCalc.requirementsMet(biz))
        assertTrue("nobody is on shift", BizCalc.workers(e, biz, e.state.hour, e.state.weekday).isEmpty())
        assertEquals("you're not working the shift", -1, e.state.player.workingAt)
        // in the store: the self-checkouts serve customers
        val inStore = anHour(e)
        println("self-run hour, in the store: $inStore customers")
        assertTrue("customers were served with you in the store ($inStore)", inStore > 0)
        assertTrue("it can't take more than the self-checkouts' 50 an hour ($inStore)", inStore <= 50)
        // gone: nobody minds the store, so nothing sells
        e.state.player.inBuilding = -1
        assertEquals("nobody served with you gone", 0, anHour(e))
    }

    @Test
    fun aStaffedTillStillNeedsSomeoneOnShift() {
        // only a cash register: being in the store isn't enough, somebody has to work it
        val (e, _) = store("supermarket", listOf(PlacedItem("cash_register", 1, 2, 0), PlacedItem("shelf", 1, 6, 0), PlacedItem("drinks_fridge", 5, 6, 0)), 0)
        assertTrue(BizCalc.requirementsMet(e.state.businesses.first()))
        assertEquals(0, anHour(e))
        // and with you on the shift it sells
        e.state.player.workingAt = e.state.businesses.first().id
        assertTrue(anHour(e) > 0)
    }

    @Test
    fun customersSkipTheUnmannedTillForTheSelfCheckout() {
        // a register with nobody behind it and a self-checkout: they all pay at the self-checkout
        val (_, sc) = store("supermarket", listOf(PlacedItem("cash_register", 1, 2, 0), PlacedItem("self_checkout", 5, 2, 0),
            PlacedItem("shelf", 1, 6, 0), PlacedItem("shelf", 5, 6, 0)), 0)
        val reg = sc.items.indexOfFirst { it.itemId == "cash_register" }
        val self = sc.items.indexOfFirst { it.itemId == "self_checkout" }
        var atSelf = 0; var atReg = 0
        repeat(30 * 240) {
            sc.update(1 / 30f, true)
            for (v in sc.visitors) if (v.payTime > 0f && v.wait > 0f) { if (v.counter == self) atSelf++ else if (v.counter == reg) atReg++ }
        }
        println("unmanned till: $atSelf paying frames at the self-checkout, $atReg at the register")
        assertTrue("customers paid at the self-checkout ($atSelf)", atSelf > 0)
        assertEquals("nobody paid at the unmanned register", 0, atReg)
    }

    @Test
    fun customersPayAtASelfCheckoutWithNoCashier() {
        // a shop with shelves, a self-checkout and nobody on the tills (a cleaner keeps it open)
        val (_, sc) = store("supermarket", listOf(PlacedItem("self_checkout", 4, 2, 0), PlacedItem("shelf", 1, 6, 0), PlacedItem("shelf", 5, 6, 0)), 0, floor = 1)
        val till = sc.items.indexOfFirst { it.itemId == "self_checkout" }
        var paid = 0
        repeat(30 * 240) {
            val paying = sc.visitors.filter { it.counter == till && it.payTime > 0f && it.wait > 0f }.toSet()
            sc.update(1 / 30f, true)
            paid += paying.count { it.wait <= 0f || it.counter != till }
        }
        println("self-checkout: $paid customers paid")
        assertTrue("customers paid at the self-checkout ($paid)", paid >= 5)
    }

    @Test
    fun fullLinesWaitInTheWaitingArea() {
        // one staffed till, a very busy café and waiting room chairs
        val (_, sc) = store("coffee", listOf(PlacedItem("cash_register", 1, 2, 0), PlacedItem("coffee_machine", 4, 0, 0), PlacedItem("waiting_chairs", 5, 7, 0)), 1)
        sc.e.economy.servedRate[sc.e.state.businesses.first().id] = 1500.0
        var seated = 0
        repeat(30 * 180) {
            sc.update(1 / 30f, true)
            seated = maxOf(seated, sc.visitors.count { it.stage == InteriorScene.WAIT_STAGE && it.seated })
        }
        println("full lines: up to $seated waiting on the chairs")
        assertTrue("with the lines full, customers sat in the waiting area ($seated)", seated >= 1)
    }
}

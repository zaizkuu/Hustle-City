package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.CartLine
import com.hustlecity.game.engine.CheckoutTarget
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.bulkDiscount
import com.hustlecity.game.engine.checkout
import com.hustlecity.game.engine.closeBusiness
import com.hustlecity.game.engine.loadFromWarehouse
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.startCampaign
import com.hustlecity.game.engine.takeCourse
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.SkipKind
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mid-game systems and regression checks for fixed bugs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressionTest {
    private fun game() = GameEngine(null).also { it.newGame("T", 0, 0, 0) }
    private fun runMinutes(e: GameEngine, n: Int) = repeat(n) { e.simulateMinute() }

    private fun rent(e: GameEngine, kind: BKind, hood: Int? = null) =
        e.city.buildings.first { e.isVacant(it.id) && it.kind == kind && (hood == null || it.neighborhood == hood) }
            .also { e.state.rentals += Rental(it.id, it.baseRent, 1) }

    @Test
    fun warehouseSuppliesStoresThroughPurchasing() {
        val e = game()
        e.state.player.money = 500_000.0
        // a coffee shop with displays
        val shopB = rent(e, BKind.RETAIL)
        val shop = e.startBusiness(shopB.id, "coffee", "Bean")!!
        shop.furniture += PlacedItem("cash_register", 0, 0, 0)
        shop.furniture += PlacedItem("coffee_machine", 2, 0, 0)
        shop.furniture += PlacedItem("pastry_case", 4, 0, 0)
        // a warehouse with racks, filled via a bulk wholesale delivery
        val whB = rent(e, BKind.WAREHOUSE)
        val wh = e.startBusiness(whB.id, "warehouse", "Depot")!!
        assertTrue(wh.type.facility && !wh.tempClosed)
        repeat(2) { wh.furniture += PlacedItem("pallet_rack", it * 2, 0, 0) }
        assertEquals(48, BizCalc.warehouseBoxes(wh))
        val cart = listOf(CartLine("coffee", true, 6, 54.0), CartLine("croissant", true, 2, 38.4))
        assertEquals(.1, bulkDiscount(cart), 1e-9)
        val metro = e.city.placeBuilding("metro")!!
        assertTrue(e.checkout(metro.id, cart, CheckoutTarget.Deliver(wh)))
        runMinutes(e, 125)
        assertEquals(720, wh.stock["coffee"])
        assertEquals(96, wh.stock["croissant"])
        assertEquals(8, BizCalc.warehouseBoxesUsed(wh))

        // headquarters with a purchasing agent at a desk
        val hqB = rent(e, BKind.OFFICE)
        val hq = e.startBusiness(hqB.id, "hq", "HQ")!!
        hq.furniture += PlacedItem("office_desk", 0, 0, 0)
        e.state.employees += Employee(e.state.newId(), "Pat", StaffRole.PURCHASING_AGENT, .5, 24.0, 24.0, 30, businessId = hq.id, days = 127)
        assertEquals(1, BizCalc.managers(e, StaffRole.PURCHASING_AGENT))

        // run to 06:00 next day: the agent fills the shop from the warehouse first
        val money0 = e.state.player.money
        val to6 = ((24 + 6) * 60 - (e.state.minute % 1440)).toInt()
        runMinutes(e, to6)
        assertEquals(400, (shop.stock["coffee"] ?: 0) + (shop.stock["tea"] ?: 0))
        assertTrue("coffee should come from the warehouse", (wh.stock["coffee"] ?: 0) < 720)
        assertEquals(90, (shop.stock["croissant"] ?: 0) + (shop.stock["cupcake"] ?: 0))
        assertTrue(e.state.player.money < money0)

        // loading a vehicle at the warehouse
        val v = e.state.vehicles.first()
        v.x = whB.doorX + 2; v.y = whB.doorY + 3
        val left = wh.stock["coffee"] ?: 0
        if (left > 0) {
            assertTrue(e.loadFromWarehouse(wh, "coffee"))
            assertEquals(1, v.cargo.size)
        }
    }

    @Test
    fun hrReplacesQuitters() {
        val e = game()
        val shopB = rent(e, BKind.RETAIL)
        val shop = e.startBusiness(shopB.id, "florist", "Petals")!!
        val hqB = rent(e, BKind.OFFICE)
        val hq = e.startBusiness(hqB.id, "hq", "HQ")!!
        hq.furniture += PlacedItem("office_desk", 0, 0, 0)
        e.state.employees += Employee(e.state.newId(), "Hal", StaffRole.HR_MANAGER, .5, 26.0, 26.0, 40, businessId = hq.id, days = 127)
        // an underpaid employee who will quit
        val sad = Employee(e.state.newId(), "Sad", StaffRole.CUSTOMER_SERVICE, .4, 8.0, 20.0, 25, businessId = shop.id, shiftStart = 9, shiftEnd = 17, days = 0b11111, satisfaction = .1)
        e.state.employees += sad
        runMinutes(e, 1440 * 4)
        assertTrue("sad employee should have quit", e.state.employees.none { it.id == sad.id })
        val replacement = e.state.employees.firstOrNull { it.role == StaffRole.CUSTOMER_SERVICE }
        assertNotNull("HR should hire a replacement", replacement)
        assertEquals(shop.id, replacement!!.businessId)
        assertEquals(9, replacement.shiftStart)
    }

    @Test
    fun skipSurvivesSaveAndLoad() {
        val e = game()
        e.takeCourse(Catalog.COURSE_HR)
        assertEquals(SkipKind.COURSE, e.skip?.kind)
        e.frame(.05f)
        val json = e.saves.encode(e.state)
        val e2 = GameEngine(null)
        e2.newGame("T", 0, 0, 0)
        e2.state = e2.saves.decode(json)
        assertEquals(SkipKind.COURSE, e2.skip?.kind)
        var guard = 0
        while (e2.skip != null && guard++ < 10_000) e2.frame(.05f)
        assertTrue(Catalog.COURSE_HR in e2.state.player.courses)
    }

    @Test
    fun campaignBillsExactDays() {
        val e = game()
        val b = rent(e, BKind.RETAIL)
        val biz = e.startBusiness(b.id, "giftshop", "G")!!
        e.state.minute = (e.state.minute / 1440) * 1440 + 10 * 60
        e.startCampaign(biz, "flyers", 3)
        runMinutes(e, 1440 * 4)
        val paid = e.state.history.sumOf { it.expenses["Marketing"] ?: 0.0 }
        assertEquals("3 days of flyers", 3 * Catalog.campaign("flyers").pricePerDay, paid, .01)
    }

    @Test
    fun closingPaysTodaysSalesAndSharedShelfSpace() {
        val e = game()
        val b = rent(e, BKind.RETAIL)
        val biz = e.startBusiness(b.id, "produce", "Farm")!!
        biz.furniture += PlacedItem("produce", 0, 0, 0)
        val cap = BizCalc.displayCapacity(biz, "produce")
        biz.stock["apples"] = cap - 10
        assertEquals(10, BizCalc.roomFor(biz, "bananas"))
        biz.revenueToday = 500.0
        val money = e.state.player.money
        e.closeBusiness(biz)
        assertTrue(e.state.player.money >= money + 500)
    }
}

package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Operations
import com.hustlecity.game.engine.SUBSIDIARY
import com.hustlecity.game.engine.Shift
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.DeliveryContract
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.OwnedVehicle
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The empire-running systems: fleets, reordering, shifts, managers, poaching, buyouts, leases, shares and bonds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationsTest {
    private fun engine(): GameEngine {
        val e = GameEngine(null)
        e.newGame("Test", 0, 0, 1)
        e.ui.tip = null
        e.state.player.money = 500_000.0
        e.state.player.courses += listOf(Catalog.COURSE_HR, Catalog.COURSE_LOGISTICS)
        return e
    }

    /** A furnished, open coffee shop with empty displays. */
    private fun coffeeShop(e: GameEngine): Business {
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW >= 8 && it.gridD >= 8 }
        e.state.rentals += Rental(b.id, b.baseRent, e.state.day, leaseEnd = e.state.day + 60)
        val biz = e.startBusiness(b.id, "coffee", "Test Brew")!!
        biz.furniture += listOf(PlacedItem("cash_register", 0, 6, 0), PlacedItem("coffee_machine", 3, 6, 0), PlacedItem("pastry_case", 6, 6, 0), PlacedItem("table_set", 2, 3, 0))
        biz.tempClosed = false; biz.openHour = 7; biz.closeHour = 21
        return biz
    }

    private fun hours(e: GameEngine, n: Int) = repeat(n * 60) { e.simulateMinute() }

    private fun toHour(e: GameEngine, hour: Int) {
        while (e.state.hour != hour || e.state.minuteOfHour != 0) e.simulateMinute()
    }

    @Test
    fun vanRouteRestocksStores() {
        val e = engine()
        val biz = coffeeShop(e)
        val metro = e.city.placeBuilding("metro")!!
        val van = OwnedVehicle(e.state.newId(), "freighter", metro.doorX + 40f, metro.doorY, 0f)
        e.state.vehicles += van
        val driver = Employee(e.state.newId(), "Dee Driver", StaffRole.DRIVER, .6, 20.0, 20.0, 30)
        e.state.employees += driver
        val r = e.ops.newRoute(van)
        r.stops += biz.id
        e.ops.assignDriver(r, driver)
        toHour(e, 5)
        r.hour = 6
        val money = e.state.player.money
        hours(e, 1)
        assertTrue("van went out: ${r.lastRun}", e.ops.away(van))
        assertTrue("paid for stock", e.state.player.money < money)
        assertTrue("stock on the van", e.state.fleetDrops.isNotEmpty())
        hours(e, 4)
        assertTrue("delivered coffee: ${biz.stock}", (biz.stock["coffee"] ?: 0) > 0)
        assertTrue("van back", !e.ops.away(van))
        assertTrue("nothing left on board", e.state.fleetDrops.none { it.routeId == r.id })
    }

    @Test
    fun autoReorderWhenLow() {
        val e = engine()
        val biz = coffeeShop(e)
        val c = DeliveryContract(hour = 3)
        for ((pid, _) in biz.type.products) if (Catalog.product(pid)?.service == false) c.targets[pid] = BizCalc.capacityFor(biz, pid)
        c.auto = true
        e.ops.defaultMinLevels(c)
        biz.delivery = c
        toHour(e, 10)
        hours(e, 1)
        assertTrue("reordered: ${biz.stock}", (biz.stock["coffee"] ?: 0) > 0)
        assertTrue("logged the auto order", c.lastAuto >= 0)
    }

    @Test
    fun longNightShiftsTireStaff() {
        val e = engine()
        val biz = coffeeShop(e)
        biz.openHour = 0; biz.closeHour = 0
        val emp = Employee(e.state.newId(), "Night Owl", StaffRole.CUSTOMER_SERVICE, .7, 15.0, 15.0, 30, businessId = biz.id, days = 0b1111111)
        e.ops.setShift(emp, Shift.GRAVEYARD)
        emp.shiftEnd = 10 // a 12-hour graveyard shift
        e.state.employees += emp
        val fresh = BizCalc.workers(e, biz, 23, e.state.weekday).firstOrNull()
        hours(e, 24 * 3)
        assertTrue("fatigue built up: ${emp.fatigue}", emp.fatigue > 40)
        assertTrue("slower when tired", e.ops.fatigueFactor(emp) < 1.0 || emp.fatigue <= 40)
        assertTrue("fresh worker counted", fresh != null)
        // a perk and a normal shift bring it back down
        biz.perks += "health"
        e.ops.setShift(emp, Shift.MORNING)
        emp.days = 0b0011111
        val before = emp.fatigue
        hours(e, 16)
        assertTrue("recovers off shift: $before -> ${emp.fatigue}", emp.fatigue < before)
    }

    @Test
    fun storeManagerRunsTheStore() {
        val e = engine()
        val biz = coffeeShop(e)
        e.state.employees += Employee(e.state.newId(), "Morgan Boss", StaffRole.STORE_MANAGER, .8, 32.0, 32.0, 41, businessId = biz.id)
        hours(e, 25)
        val c = biz.delivery
        assertTrue("manager set up reordering", c != null && c.enabled && c.auto && c.byManager)
        assertTrue("manager hired staff", e.state.employees.count { it.businessId == biz.id && it.role == StaffRole.CUSTOMER_SERVICE } >= 1)
    }

    @Test
    fun rivalsPoachAndYouCounter() {
        val e = engine()
        val biz = coffeeShop(e)
        val star = Employee(e.state.newId(), "Star Barista", StaffRole.CUSTOMER_SERVICE, .9, 15.0, 15.0, 28, businessId = biz.id)
        e.state.employees += star
        val rival = e.state.rivals.first()
        star.offerFrom = rival.id; star.offerWage = 22.0; star.offerDay = e.state.day + 2
        e.ops.counterOffer(star)
        assertEquals(22.0, star.wage, .001)
        assertEquals(-1, star.offerFrom)
        val before = e.state.candidates.size
        e.ops.headhunt(rival)
        assertTrue("headhunted a manager", e.state.candidates.size == before + 1 && e.state.candidates.last().role == StaffRole.STORE_MANAGER)
    }

    @Test
    fun buyingRivalsAndTakeovers() {
        val e = engine()
        e.state.player.money = 50_000_000.0
        val rb = e.state.rivalBusinesses.first()
        val ask = e.ops.askingPrice(rb)
        assertTrue("a sane price: $ask", ask > 1000)
        assertTrue(e.ops.buyRivalStore(rb))
        assertEquals(SUBSIDIARY, rb.rivalId)
        val worth = e.netWorth()
        hours(e, 24)
        assertTrue("the subsidiary traded for us: ${rb.revenue}", rb.revenue > 0)
        assertTrue("net worth counts it", worth > 0)
        // take over a whole rival through the stock market
        e.ops.ensureMarket()
        val rival = e.state.rivals.first()
        val t = e.ops.ticker("R${rival.id}")!!
        assertTrue(e.ops.buyShares(t, 30_000))
        assertTrue("controlling stake", e.ops.stake(rival) > .5)
        val stores = e.state.rivalBusinesses.count { it.rivalId == rival.id }
        assertTrue(e.ops.takeover(rival))
        assertTrue("rival gone", e.state.rivals.none { it.id == rival.id })
        assertTrue("its stores are ours", e.ops.subsidiaryStores().size >= stores)
    }

    @Test
    fun leasesRenewWithARaise() {
        val e = engine()
        val biz = coffeeShop(e)
        val r = e.state.rentalFor(biz.buildingId)!!
        val rent = r.rentPerMonth
        r.leaseEnd = e.state.day + 1
        hours(e, 24)
        assertTrue("rent went up: $rent -> ${r.rentPerMonth}", r.rentPerMonth > rent)
        assertEquals(1, r.renewals)
        assertTrue("next renewal scheduled", r.leaseEnd > e.state.day)
    }

    @Test
    fun sharesAndBonds() {
        val e = engine()
        e.ops.ensureMarket()
        val t = e.ops.ticker("HCI")!!
        assertTrue(e.ops.buyShares(t, 100))
        val p0 = t.price
        hours(e, 24)
        assertTrue("prices move", t.history.size >= 2)
        assertTrue("still have them", (e.state.holdings["HCI"] ?: 0) == 100)
        assertTrue(e.ops.sellShares(t, 100))
        assertTrue("sold out", e.state.holdings["HCI"] == null)
        assertTrue(p0 > 0)
        val cash = e.state.player.money
        assertTrue(e.ops.buyBond(10_000.0, 30))
        repeat(31) { hours(e, 24) }
        assertTrue("bond repaid with interest: ${e.state.player.money} vs $cash", e.state.bonds.isEmpty())
    }

    @Test
    fun saveKeepsEverything() {
        val e = engine()
        val biz = coffeeShop(e)
        biz.perks += "coffee"
        val van = OwnedVehicle(e.state.newId(), "hauler", 0f, 0f, 0f)
        e.state.vehicles += van
        e.ops.newRoute(van).stops += biz.id
        e.ops.ensureMarket()
        e.ops.buyShares(e.state.market.first(), 10)
        e.ops.buyBond(1000.0, 90)
        val copy = e.saves.decode(e.saves.encode(e.state))
        assertEquals(1, copy.fleet.size)
        assertEquals(e.state.market.size, copy.market.size)
        assertEquals(1, copy.bonds.size)
        assertTrue("coffee" in copy.businesses.first { it.id == biz.id }.perks)
    }

    @Test
    fun districtsAndHomes() {
        for (look in Look.entries) {
            var sum = 0.0; var n = 0
            for (d in 0 until 7) for (h in 8..21) { sum += Operations.districtFactor(look, h, d); n++ }
            assertEquals("$look averages to 1", 1.0, sum / n, .02)
        }
        assertTrue("offices peak at lunch", Operations.districtFactor(Look.CBD, 12, 2) > Operations.districtFactor(Look.CBD, 21, 2))
        assertTrue("nightlife peaks late", Operations.districtFactor(Look.DENSE_ASIA, 21, 5) > Operations.districtFactor(Look.DENSE_ASIA, 9, 5))
        val e = engine()
        val homes = e.city.buildings.filter { it.kind == BKind.RESIDENTIAL }
        assertTrue("there are mansions", homes.any { e.homeTier(it) == 3 })
        assertTrue("and studios", homes.any { e.homeTier(it) == 0 })
        assertTrue("new vehicles on sale", listOf("zippy", "ranger", "hauler", "vortex").all { id -> Catalog.vehicles.any { it.id == id } })
    }
}

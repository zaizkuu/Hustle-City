package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.CartLine
import com.hustlecity.game.engine.CheckoutTarget
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.checkout
import com.hustlecity.game.engine.commitGhost
import com.hustlecity.game.engine.hire
import com.hustlecity.game.engine.rentBuilding
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.takeCourse
import com.hustlecity.game.engine.takeLoan
import com.hustlecity.game.engine.unloadHere
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameplayTest {

    private fun runMinutes(e: GameEngine, minutes: Int) = repeat(minutes) { e.simulateMinute() }

    /** Lets a time skip (course, sleep, …) finish by pumping frames. */
    private fun finishSkip(e: GameEngine) {
        var guard = 0
        while (e.skip != null && guard++ < 10_000) e.frame(.05f)
    }

    @Test
    fun cityIsSane() {
        val city = City(1337)
        assertTrue(city.buildings.size > 400)
        // every place exists exactly where expected
        for (p in Catalog.places.map { it.id }.distinct()) assertNotNull("missing $p", city.placeBuilding(p))
        // no overlapping solid footprints
        for (a in city.buildings) {
            if (!a.solid) continue
            city.forBuildingsIn(a.minX, a.minY, a.maxX, a.maxY) { b ->
                if (b.id > a.id && b.solid) assertTrue("overlap ${a.id} ${b.id}", !a.obb().overlaps(b.obb(), -.01f))
            }
        }
        // doors are on walkable ground
        val out = FloatArray(2)
        for (b in city.buildings.filter { it.solid }.take(200)) {
            city.collide(b.doorX, b.doorY, .45f, out)
            assertTrue("door of ${b.address} blocked", kotlin.math.abs(out[0] - b.doorX) < .6f && kotlin.math.abs(out[1] - b.doorY) < .6f)
        }
    }

    @Test
    fun fullBusinessLoop() {
        val e = GameEngine(null)
        e.newGame("Test", 0, 0, 1)
        val startMoney = e.state.player.money
        e.takeLoan(Catalog.loans.first())
        assertEquals(startMoney + 15000, e.state.player.money, .01)

        // rent a store and start a coffee shop
        val store = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 75..150 && it.traffic > .3f }
        assertTrue(e.rentBuilding(store.id))
        val biz = e.startBusiness(store.id, "coffee", "Test Brew")!!
        assertTrue(!BizCalc.requirementsMet(biz))

        // buy furniture with delivery to the business
        val furniture = listOf("cash_register", "coffee_machine", "pastry_case")
        val cart = furniture.map { CartLine(it, false, 1, Catalog.item(it)!!.price) }
        val essentials = e.city.placeBuilding("essentials")!!
        assertTrue(e.checkout(essentials.id, cart, CheckoutTarget.Deliver(biz)))
        runMinutes(e, 125)
        assertEquals(3, biz.storage.size)

        // place everything inside
        e.state.player.inBuilding = store.id
        val scene = InteriorScene(e, store, InteriorMode.BUSINESS)
        e.interior = scene
        for (id in furniture) {
            scene.startPlacing(id)
            assertTrue("no room for $id", scene.ghostValid)
            assertTrue(e.commitGhost())
        }
        assertTrue(BizCalc.requirementsMet(biz))
        assertTrue(biz.storage.isEmpty())

        // stock: carry by vehicle → unload
        val car = e.state.vehicles.first()
        car.x = store.doorX + 3f; car.y = store.doorY + 3f
        val metro = e.city.placeBuilding("metro")!!
        car.x = metro.doorX; car.y = metro.doorY + 3f
        val stockCart = listOf(CartLine("coffee", true, 2, 54.0), CartLine("tea", true, 1, 36.0), CartLine("croissant", true, 1, 38.4))
        assertTrue(e.checkout(metro.id, stockCart, CheckoutTarget.Vehicle(car)))
        assertEquals(4, car.cargo.size)
        car.x = store.doorX + 3f; car.y = store.doorY + 3f
        assertTrue(e.unloadHere() > 0)
        assertEquals(240, biz.stock["coffee"])
        assertTrue(car.cargo.isEmpty())

        // open and work a few hours ourselves
        biz.tempClosed = false
        biz.openHour = 7; biz.closeHour = 21
        e.state.minute = (e.state.minute / 1440) * 1440 + 9 * 60
        e.state.player.workingAt = biz.id
        runMinutes(e, 6 * 60)
        e.state.player.workingAt = -1
        assertTrue("no customers served", biz.customersToday > 10)
        assertTrue(biz.revenueToday > 20)
        val pending = biz.revenueToday
        val before = e.state.player.money

        // hire staff (skip the course timer)
        e.state.player.courses += Catalog.COURSE_HR
        val eco = e.economy
        eco.generateCandidates(StaffRole.CUSTOMER_SERVICE, 2)
        val cand = e.state.candidates.first()
        e.hire(cand)
        val emp = e.state.employees.first()
        emp.businessId = biz.id; emp.shiftStart = 7; emp.shiftEnd = 15
        emp.days = 0b1111111

        // run to midnight: sales are collected
        val toMidnight = 1440 - (e.state.minute % 1440).toInt()
        runMinutes(e, toMidnight)
        assertTrue("earnings not collected: before=$before after=${e.state.player.money} pending=$pending ledger=${e.state.history.last()}", e.state.history.last().revenue > 0)
        assertTrue(e.state.history.isNotEmpty())
        assertTrue(e.state.history.last().revenue >= pending * .99)
        assertTrue("collect objective", "collect" in e.state.objectivesDone)

        // a few days with an employee
        runMinutes(e, 3 * 1440)
        val last = biz.history.last()
        assertTrue("employee should serve customers: $last", last.customers > 20)
        assertTrue(emp.hoursToday == 0 && last.wages > 0)

        // save / load round trip
        val json = e.saves.encode(e.state)
        val copy = e.saves.decode(json)
        assertEquals(e.state.minute, copy.minute)
        assertEquals(e.state.businesses.size, copy.businesses.size)
        assertEquals(biz.furniture.size, copy.businesses.first().furniture.size)
        assertEquals(e.state.player.money, copy.player.money, .001)
    }

    @Test
    fun needsAndPassOut() {
        val e = GameEngine(null)
        e.newGame("Test", 0, 0, 1)
        e.state.player.energy = 1.0
        runMinutes(e, 30)
        assertNotNull("should be in hospital skip", e.skip)
        finishSkip(e)
        assertTrue(e.state.player.energy >= 35)
        val hospital = e.city.placeBuilding("hospital")!!
        assertTrue(kotlin.math.abs(e.state.player.x - hospital.doorX) < 3)
    }

    @Test
    fun drivingMovesAndCollides() {
        val e = GameEngine(null)
        e.newGame("Test", 0, 0, 1)
        val v = e.state.vehicles.first()
        e.state.player.x = v.x; e.state.player.y = v.y
        e.settings.carControls = 1 // the joystick option
        e.enterVehicle(v)
        val x0 = v.x; val y0 = v.y
        e.input.jx = 1f // steer east (car starts facing east)
        repeat(120) { e.frame(1 / 30f) }
        assertTrue("car should move", kotlin.math.hypot(v.x - x0, v.y - y0) > 10f)
        // never inside a building
        assertTrue(e.city.buildings.none { it.solid && it.contains(v.x, v.y) })
        e.input.jx = 0f
        e.exitVehicle()
        assertEquals(-1, e.state.player.driving)
    }

    @Test
    fun courseSkipCompletes() {
        val e = GameEngine(null)
        e.newGame("Test", 0, 0, 0)
        val start = e.state.minute
        e.takeCourse(Catalog.COURSE_HR)
        finishSkip(e)
        assertTrue(Catalog.COURSE_HR in e.state.player.courses)
        assertEquals(start + 4 * 60, e.state.minute)
    }
}

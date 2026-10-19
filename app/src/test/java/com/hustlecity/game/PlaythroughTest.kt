package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.CartLine
import com.hustlecity.game.engine.CheckoutTarget
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.checkout
import com.hustlecity.game.engine.commitGhost
import com.hustlecity.game.engine.hire
import com.hustlecity.game.engine.rentBuilding
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.takeLoan
import com.hustlecity.game.engine.unloadHere
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Plays every city the way a player would: borrow, rent a shop, furnish it in the designer (back room and all), stock
 * it from the car, open, work, hire, let days pass and reload the save; then wander, drive and pop in and out of
 * buildings at random while the world and interiors are drawn with both renderers. Nothing may throw, and money,
 * positions and stock must stay sane.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlaythroughTest {
    private val w = 960
    private val h = 450

    private fun runMinutes(e: GameEngine, minutes: Int) = repeat(minutes) { e.simulateMinute() }

    private fun sane(e: GameEngine, where: String) {
        val s = e.state
        val p = s.player
        assertTrue("$where: money ${p.money}", p.money.isFinite())
        assertTrue("$where: player at ${p.x},${p.y}", p.x.isFinite() && p.y.isFinite())
        val b = e.city.bounds
        assertTrue("$where: player off the map at ${p.x},${p.y}", p.x in b[0] - 200f..b[2] + 200f && p.y in b[1] - 200f..b[3] + 200f)
        for (v in s.vehicles) assertTrue("$where: vehicle at ${v.x},${v.y}", v.x.isFinite() && v.y.isFinite() && v.heading.isFinite())
        for (biz in s.businesses) {
            for ((k, q) in biz.stock) assertTrue("$where: ${biz.name} stock $k = $q", q >= 0)
            for ((k, q) in biz.backStock) assertTrue("$where: ${biz.name} back stock $k = $q", q >= 0)
        }
        for (a in e.traffic.cars) assertTrue("$where: car at ${a.x},${a.y}", a.x.isFinite() && a.y.isFinite())
        for (a in e.traffic.peds) assertTrue("$where: walker at ${a.x},${a.y}", a.x.isFinite() && a.y.isFinite())
    }

    @Test
    fun everyCityPlaysThrough() {
        for (map in Maps.all) play(map.id)
    }

    private fun play(mapId: String) {
        val e = GameEngine(null)
        e.newGame("Test", 0, 0, 1, mapId = mapId)
        e.ui.tip = null
        e.takeLoan(Catalog.loans.first())
        // a shop of a typical size somewhere busy
        val store = e.city.buildings.filter { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.area in 80..200 && it.gridD >= 7 }
            .maxByOrNull { it.traffic }
        assertTrue("$mapId: a shop to rent", store != null)
        store!!
        assertTrue("$mapId: rent", e.rentBuilding(store.id))
        val biz = e.startBusiness(store.id, "coffee", "Test Brew")!!
        val furniture = listOf("cash_register", "coffee_machine", "pastry_case", "table_set")
        val essentials = e.city.placeBuilding("essentials")!!
        assertTrue("$mapId: furniture order", e.checkout(essentials.id, furniture.map { CartLine(it, false, 1, Catalog.item(it)!!.price) }, CheckoutTarget.Deliver(biz)))
        runMinutes(e, 125)
        assertTrue("$mapId: delivered ${biz.storage}", biz.storage.size == furniture.size)
        e.state.player.x = store.doorX; e.state.player.y = store.doorY
        e.enterBuilding(store)
        val scene = e.interior!!
        for (id in furniture) {
            scene.startPlacing(id)
            assertTrue("$mapId: no room for $id in a ${store.gridW}x${store.gridD} shop (back room ${biz.backRoom})", scene.ghostValid)
            assertTrue(e.commitGhost())
        }
        assertTrue("$mapId: requirements", BizCalc.requirementsMet(biz))
        repeat(60) { e.frame(1 / 30f) }
        e.exitBuilding()
        // stock from the car
        val car = e.state.vehicles.first()
        val metro = e.city.placeBuilding("metro")!!
        car.x = metro.doorX; car.y = metro.doorY + 3f
        assertTrue("$mapId: stock order", e.checkout(metro.id, listOf(CartLine("coffee", true, 2, 54.0), CartLine("croissant", true, 1, 38.4)), CheckoutTarget.Vehicle(car)))
        car.x = store.doorX + 3f; car.y = store.doorY + 3f
        e.state.player.x = store.doorX; e.state.player.y = store.doorY
        e.enterBuilding(store)
        assertTrue("$mapId: unload", e.unloadHere() > 0)
        repeat(60) { e.frame(1 / 30f) }
        e.exitBuilding()
        sane(e, "$mapId after stocking")
        // open, work, hire, run days
        biz.tempClosed = false; biz.openHour = 7; biz.closeHour = 21
        e.state.minute = (e.state.minute / 1440) * 1440 + 9 * 60
        e.state.player.workingAt = biz.id
        runMinutes(e, 4 * 60)
        e.state.player.workingAt = -1
        assertTrue("$mapId: no customers served", biz.customersToday > 5)
        e.state.player.courses += Catalog.COURSE_HR
        e.economy.generateCandidates(StaffRole.CUSTOMER_SERVICE, 2)
        e.hire(e.state.candidates.first())
        val emp = e.state.employees.last()
        emp.businessId = biz.id; emp.shiftStart = 7; emp.shiftEnd = 20; emp.days = 0b1111111
        runMinutes(e, 2 * 1440)
        sane(e, "$mapId after two days")
        val copy = e.saves.decode(e.saves.encode(e.state))
        assertTrue("$mapId: the save keeps the city", copy.mapId == mapId)

        // wander about, drawing everything as we go
        val rnd = Random(mapId.hashCode())
        val world = WorldRenderer(e)
        val inside = InteriorRenderer(e)
        val canvas = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        val gpu = GpuFrame()
        var frameNo = 0
        fun draw(step: String) {
            frameNo++
            if (frameNo % 5 != 0) return
            try {
                if (e.state.player.inBuilding >= 0 && e.interior != null) {
                    inside.draw(canvas, w, h); inside.draw(canvas, w, h, gpu)
                } else {
                    world.draw(canvas, w, h, 2f); world.draw(canvas, w, h, 2f, gpu)
                }
            } catch (t: Throwable) { throw AssertionError("$mapId: drawing failed during $step", t) }
        }
        for (step in 0 until 60) {
            val what = rnd.nextInt(5)
            val label = "$mapId step $step (${listOf("walk", "drive", "visit", "sleep", "night")[what]})"
            try {
                when (what) {
                    0 -> { // walk somewhere for a few seconds
                        if (e.state.player.driving >= 0) { e.input.brake = true; repeat(60) { e.frame(1 / 30f) }; e.input.brake = false; e.getOutOfCar() }
                        if (e.state.player.inBuilding >= 0) e.exitBuilding()
                        val a = rnd.nextFloat() * 6.28f
                        e.input.jx = kotlin.math.cos(a); e.input.jy = kotlin.math.sin(a)
                        repeat(90) { e.frame(1 / 30f); draw(label) }
                        e.input.jx = 0f; e.input.jy = 0f
                    }
                    1 -> { // drive a stretch
                        if (e.state.player.inBuilding >= 0) e.exitBuilding()
                        val v = e.state.vehicles.first()
                        if (e.state.player.driving < 0) {
                            e.state.player.x = v.x; e.state.player.y = v.y
                            e.enterVehicle(v)
                        }
                        e.input.gas = true; e.input.steer = rnd.nextFloat() * 2 - 1
                        repeat(120) { e.frame(1 / 30f); draw(label) }
                        e.input.gas = false; e.input.steer = 0f; e.input.brake = true
                        repeat(60) { e.frame(1 / 30f) }
                        e.input.brake = false
                        e.parkVehicle()
                    }
                    2 -> { // pop into a building nearby, look around, leave
                        if (e.state.player.driving >= 0) { e.input.brake = true; repeat(60) { e.frame(1 / 30f) }; e.input.brake = false; e.getOutOfCar() }
                        if (e.state.player.inBuilding >= 0) e.exitBuilding()
                        val p = e.state.player
                        val b = e.city.buildings.filter { it.solid && hypot(it.doorX - p.x, it.doorY - p.y) < 250f }.randomOrNull(rnd)
                            ?: if (rnd.nextBoolean()) store else essentials
                        p.x = b.doorX; p.y = b.doorY
                        e.enterBuilding(b)
                        repeat(60) { e.frame(1 / 30f); draw(label) }
                        e.exitBuilding()
                        repeat(10) { e.frame(1 / 30f); draw(label) }
                    }
                    3 -> runMinutes(e, 6 * 60) // time passes
                    else -> { // into the evening and back to the street
                        e.state.minute = (e.state.minute / 1440) * 1440 + (18 + rnd.nextInt(6)) * 60
                        repeat(30) { e.frame(1 / 30f); draw(label) }
                    }
                }
            } catch (t: AssertionError) { throw t } catch (t: Throwable) { throw AssertionError("$label threw", t) }
            sane(e, label)
        }
        println("$mapId: played through, money ${"%.0f".format(e.state.player.money)}, ${biz.customersToday} customers today, day ${e.state.day}")
    }
}

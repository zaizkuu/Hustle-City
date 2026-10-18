package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.engine.Carrier
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.HAND_TRUCK
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.carryCap
import com.hustlecity.game.engine.carryIn
import com.hustlecity.game.engine.cartAllows
import com.hustlecity.game.engine.itemDims
import com.hustlecity.game.engine.payAtRegister
import com.hustlecity.game.engine.pickUp
import com.hustlecity.game.engine.putAllBack
import com.hustlecity.game.engine.returnCart
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.stowHandTruck
import com.hustlecity.game.engine.takeCart
import com.hustlecity.game.engine.unpaidItems
import com.hustlecity.game.engine.useHandTruck
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Big Ambitions-style walk-in shopping: browse the displays, carry with hands/cart/hand truck, pay, load the car. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShoppingTest {
    private fun game() = GameEngine(null).also { it.newGame("T", 0, 0, 1); it.state.player.money = 60_000.0 }

    @Test
    fun everythingOnSaleIsOnDisplayAndReachable() {
        val e = game()
        for (place in Catalog.places.filter { it.kind == PlaceKind.FURNITURE_STORE }) {
            val b = e.city.placeBuilding(place.id)!!
            val sc = InteriorScene(e, b, InteriorMode.PLACE)
            assertEquals("${place.id}: every item on display", place.stock.toSet(), sc.forSale.values.toSet())
            assertEquals("${place.id}: one display per item", place.stock.size, sc.forSale.size)
            assertTrue(sc.registerIndex >= 0 && sc.rackIndex >= 0)
            // flood fill from the door: every display, the checkout and the carts can be walked up to
            val occ = sc.occupancy()
            val seen = BooleanArray(sc.w * sc.d)
            val queue = ArrayDeque<Int>()
            val start = (sc.d - 1) * sc.w + sc.doorX
            seen[start] = true; queue += start
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                val x = c % sc.w; val y = c / sc.w
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until sc.w || ny !in 0 until sc.d) continue
                    val n = ny * sc.w + nx
                    if (seen[n] || occ[n]) continue
                    seen[n] = true; queue += n
                }
            }
            for (i in sc.forSale.keys + sc.registerIndex + sc.rackIndex) {
                val p = sc.items[i]
                val (fw, fd) = itemDims(Catalog.item(p.itemId)!!, p.rot)
                val reachable = (p.x - 1..p.x + fw).any { x -> (p.y - 1..p.y + fd).any { y ->
                    x in 0 until sc.w && y in 0 until sc.d && seen[y * sc.w + x] &&
                        ((x in p.x until p.x + fw) || (y in p.y until p.y + fd))
                } }
                assertTrue("${place.id}: ${p.itemId} can't be reached", reachable)
            }
        }
    }

    @Test
    fun pickPayAndPushTheCartToTheCar() {
        val e = game()
        val store = e.city.placeBuilding("essentials")!!
        e.state.player.x = store.doorX; e.state.player.y = store.doorY
        e.enterBuilding(store)
        assertTrue("walk-in, no menu", e.interior!!.walkIn && e.ui.panel == null)
        // bare hands hold one box
        assertTrue(e.pickUp("shelf"))
        assertFalse(e.pickUp("cash_register"))
        // a store cart holds eight
        assertTrue(e.takeCart(store.id))
        assertEquals(8, e.carryCap())
        assertTrue(e.pickUp("cash_register"))
        assertEquals(2, e.unpaidItems().size)
        // can't walk out without paying
        e.exitBuilding()
        assertEquals(store.id, e.state.player.inBuilding)
        val money = e.state.player.money
        assertTrue(e.payAtRegister())
        assertEquals(money - 1570.0 - 900.0, e.state.player.money, .01)
        assertTrue("furniture" in e.state.objectivesDone)
        e.exitBuilding()
        assertEquals(-1, e.state.player.inBuilding)
        // the cart stays in the parking lot
        assertFalse(e.cartAllows(store.doorX + 200f, store.doorY))
        assertTrue(e.cartAllows(store.doorX + 10f, store.doorY))
        // load the car: everything goes in the trunk and the cart is left behind
        val v = e.state.vehicles.first()
        v.x = store.doorX + 6f; v.y = store.doorY + 3f
        e.enterVehicle(v)
        assertEquals(v.id, e.state.player.driving)
        assertEquals(listOf("cash_register", "shelf"), v.cargo.map { it.id }.sorted())
        assertEquals(Carrier.HANDS, e.state.player.carrier)
        assertTrue(e.state.player.hand.isEmpty())
    }

    @Test
    fun putBackAndForcedExitsLeaveUnpaidGoodsBehind() {
        val e = game()
        val store = e.city.placeBuilding("lux")!!
        e.state.player.x = store.doorX; e.state.player.y = store.doorY
        e.enterBuilding(store)
        e.takeCart(store.id)
        e.pickUp("sofa"); e.pickUp("rug")
        assertEquals(2, e.putAllBack())
        e.pickUp("sofa")
        val money = e.state.player.money
        e.state.player.energy = 0.0
        repeat(3) { e.simulateMinute() }
        // passing out takes you out of the store; the sofa stays there and you weren't charged for it
        assertEquals(-1, e.state.player.inBuilding)
        assertTrue(e.state.player.hand.none { it.unpaid })
        assertTrue(e.state.player.money <= money)
        assertEquals(Carrier.HANDS, e.state.player.carrier)
    }

    @Test
    fun handTruckCarriesFourAndFoldsIntoTheTrunk() {
        val e = game()
        val store = e.city.placeBuilding("essentials")!!
        e.state.player.x = store.doorX; e.state.player.y = store.doorY
        e.enterBuilding(store)
        assertTrue(e.pickUp(HAND_TRUCK))
        assertTrue(e.payAtRegister())
        assertEquals("a bought hand truck is ready to use", Carrier.HAND_TRUCK, e.state.player.carrier)
        assertEquals(4, e.carryCap())
        repeat(4) { assertTrue(e.pickUp("trash_can")) }
        assertFalse(e.pickUp("trash_can"))
        e.payAtRegister()
        e.exitBuilding()
        // fold it into the car with its boxes, and take it out again
        val v = e.state.vehicles.first()
        v.x = store.doorX + 4f; v.y = store.doorY + 3f
        val free = e.cargoSlotsFree(v)
        assertTrue(e.stowHandTruck(v))
        assertEquals(free - 5, e.cargoSlotsFree(v))
        assertEquals(Carrier.HANDS, e.state.player.carrier)
        assertTrue(e.useHandTruck(v.cargo, v.cargo.indexOfFirst { it.id == HAND_TRUCK }))
        assertEquals(Carrier.HAND_TRUCK, e.state.player.carrier)
        // a cart takes the hand truck on board, and gives it back
        e.takeCart(store.id)
        assertEquals(Carrier.CART, e.state.player.carrier)
        assertTrue(e.returnCart())
        assertEquals(Carrier.HAND_TRUCK, e.state.player.carrier)
    }

    @Test
    fun carryingInTakesTripsAndAHandTruckSavesTime() {
        val e = game()
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "giftshop", "Gifts")!!
        val v = e.state.vehicles.first()
        v.x = b.doorX + 3f; v.y = b.doorY + 2f
        repeat(4) { v.cargo += com.hustlecity.game.model.CargoItem("shelf", false) }
        val t0 = e.state.minute
        assertEquals(4, e.carryIn(b.id, v))
        assertEquals(4, biz.storage.size)
        assertEquals("4 trips by hand", t0 + 8, e.state.skip!!.end)
        e.state.skip = null; e.ui.skipping = false
        repeat(4) { v.cargo += com.hustlecity.game.model.CargoItem("plant_small", false) }
        e.state.player.carrier = Carrier.HAND_TRUCK
        val t1 = e.state.minute
        assertEquals(4, e.carryIn(b.id, v))
        assertEquals("one trip with a hand truck", t1 + 2, e.state.skip!!.end)
    }
}

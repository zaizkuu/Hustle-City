package com.hustlecity.game

import com.hustlecity.game.engine.CartLine
import com.hustlecity.game.engine.CheckoutTarget
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.checkout
import com.hustlecity.game.engine.sellFromTrunk
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.stowInTrunk
import com.hustlecity.game.engine.takeFromTrunk
import com.hustlecity.game.engine.unloadDestination
import com.hustlecity.game.engine.unloadInto
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Furniture loaded in the car can be seen, unloaded from the street, carried and sold back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CargoTest {
    @Test
    fun trunkToStore() {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 0)
        e.state.player.money = 50_000.0
        val v = e.state.vehicles.first()
        // buy at Essentials with the car parked outside
        val store = e.city.placeBuilding("essentials")!!
        v.x = store.doorX + 3f; v.y = store.doorY + 2f
        assertTrue(e.checkout(store.id, listOf(CartLine("cash_register", false, 1, 900.0), CartLine("shelf", false, 2, 1570.0)), CheckoutTarget.Vehicle(v)))
        assertEquals(3, v.cargo.size)
        // not near any of my places yet
        assertNull(e.unloadDestination(v))
        // rent a shop and park in front of it
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "giftshop", "Gifts")!!
        v.x = b.doorX + 4f; v.y = b.doorY + 2f
        assertEquals(b.id, e.unloadDestination(v)?.id)
        // carry one item, sell one, unload the rest from the street
        assertTrue(e.takeFromTrunk(v, 0))
        assertEquals(1, e.state.player.hand.size)
        val money = e.state.player.money
        e.sellFromTrunk(v, 0)
        assertTrue(e.state.player.money > money)
        assertEquals(1, v.cargo.size)
        val moved = e.unloadInto(b.id, true, v)
        assertEquals(2, moved)
        assertTrue(v.cargo.isEmpty() && e.state.player.hand.isEmpty())
        assertEquals(listOf("cash_register", "shelf"), biz.storage.sorted())
        // stowing hand items back into the trunk
        e.state.player.hand += com.hustlecity.game.model.CargoItem("plant_small", false)
        assertEquals(1, e.stowInTrunk(v))
        assertEquals(1, v.cargo.size)
    }
}

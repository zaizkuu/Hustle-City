package com.hustlecity.game

import com.hustlecity.game.engine.EATING_TYPES
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.INDEPENDENT
import com.hustlecity.game.engine.eatOwnStock
import com.hustlecity.game.engine.buyStreetFood
import com.hustlecity.game.engine.clearOfCarts
import com.hustlecity.game.engine.findFood
import com.hustlecity.game.engine.nearestFood
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Somewhere to eat in every neighborhood; the food bar takes you to the nearest; your own food store feeds you. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EateriesTest {
    @Test
    fun everyNeighborhoodHasAFastFoodPlace() {
        for (map in Maps.all) {
            val e = GameEngine(null)
            e.newGame("T", 0, 0, 1, mapId = map.id)
            val s = e.state
            for (h in e.city.neighborhoods) {
                val shops = e.city.buildings.count { it.hood.id == h.id && it.kind == BKind.RETAIL }
                // (a business district with a shop or two, all let, may have nowhere)
                if (shops < 3) continue
                val food = s.rivalBusinesses.count { it.typeId == "fastfood" && e.city.building(it.buildingId)?.hood?.id == h.id }
                if (food == 0) {
                    val inHood = e.city.buildings.filter { it.hood.id == h.id && it.kind == BKind.RETAIL }
                    println("  ${h.name}: ${inHood.size} shops, vacant ${inHood.count { e.isVacant(it.id) }}, with a street ${inHood.count { it.frontEdge >= 0 }}, places ${inHood.count { it.placeId != null }}, " +
                        "rivals " + s.rivalBusinesses.filter { e.city.building(it.buildingId)?.hood?.id == h.id }.groupBy { it.typeId }.mapValues { it.value.size })
                }
                assertTrue("${map.id}: ${h.name} has nowhere to eat", food > 0)
            }
            val indies = s.rivalBusinesses.filter { it.rivalId == INDEPENDENT }
            println("${map.id}: ${indies.size} independents: ${indies.joinToString { it.name }}")
            // they're in vacant shops, one per building
            assertEquals(s.rivalBusinesses.size, s.rivalBusinesses.map { it.buildingId }.toSet().size)
            // loading again adds none
            val before = s.rivalBusinesses.size
            e.economy.rivals.ensureEateries()
            assertEquals(before, s.rivalBusinesses.size)
        }
    }

    @Test
    fun theFoodBarFindsFoodAndYourOwnStoreFeedsYou() {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        val food = e.nearestFood()
        assertTrue(food != null && (e.state.rivalBusinessAt(food.id)?.typeId in EATING_TYPES))
        e.findFood()
        assertEquals(food!!.id, e.state.player.waypoint)
        // your own fast food place: a burger from the stock
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "fastfood", "Mine")!!
        biz.stock["burger"] = 5
        e.state.player.hunger = 20.0
        e.eatOwnStock(biz)
        assertEquals(4, biz.stock["burger"])
        assertTrue(e.state.player.hunger > 55.0)
    }

    /** Every city has street food carts; walk up to one and E buys its first snack: you pay, you eat, you're not hungry. */
    @Test
    fun streetFoodFromACart() {
        for (map in Maps.all) {
            val e = GameEngine(null)
            e.newGame("T", 0, 0, 1, mapId = map.id)
            e.ui.tip = null
            val pr = e.props
            val carts = (0 until pr.count).filter { pr.kind[it] == com.hustlecity.game.render.StreetProps.VENDOR }
            assertTrue("${map.id} has street food carts (${carts.size})", carts.size >= 10)
            val i = carts[carts.size / 2]
            val p = e.state.player
            // on the pavement side of it, where customers stand
            p.x = pr.x[i] - pr.dirX[i] * 1.5f; p.y = pr.y[i] - pr.dirY[i] * 1.5f
            p.hunger = 30.0
            e.frame(1 / 30f)
            assertEquals("${map.id}: at the cart", i, e.nearVendor)
            val snack = com.hustlecity.game.engine.StreetFood.menu(map.id).first()
            val cash = p.money
            if (e.vendorFirst()) {
                e.onKey(android.view.KeyEvent.KEYCODE_E, true); e.onKey(android.view.KeyEvent.KEYCODE_E, false)
            } else e.buyStreetFood(snack)
            assertEquals("${map.id}: paid for ${snack.name}", cash - snack.price, p.money, .001)
            assertTrue("${map.id}: ate it", p.hunger >= 30.0 + snack.hunger - .5)
            assertTrue("${map.id}: eating it", e.ui.eatingUntil > e.realTime)
            // and the cart is solid: walking into it stops at its edge
            val tmp = FloatArray(2)
            assertTrue(e.clearOfCarts(pr.x[i], pr.y[i], .45f, tmp))
            assertTrue("${map.id}: pushed out of the cart", kotlin.math.hypot(tmp[0] - pr.x[i], tmp[1] - pr.y[i]) > .8f)
        }
    }
}

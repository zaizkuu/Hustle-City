package com.hustlecity.game

import androidx.test.core.app.ApplicationProvider
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.Currency
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.SUBSIDIARY
import com.hustlecity.game.engine.SaveManager
import com.hustlecity.game.engine.buyBuilding
import com.hustlecity.game.engine.canLet
import com.hustlecity.game.engine.evictTenant
import com.hustlecity.game.engine.itemForTag
import com.hustlecity.game.engine.landlordDaily
import com.hustlecity.game.engine.listForTenants
import com.hustlecity.game.engine.rentBuilding
import com.hustlecity.game.engine.saleOffer
import com.hustlecity.game.engine.sellAndLeaseBack
import com.hustlecity.game.engine.sellBusiness
import com.hustlecity.game.engine.sellVehicle
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.BizDay
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Selling businesses, buildings and cars, letting buildings to tenants, save slots and local money. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PropertyTest {

    private fun game(): GameEngine {
        val e = GameEngine(null)
        e.newGame("P", 0, 0, 1)
        e.state.player.money = 2_000_000.0
        e.state.player.courses += Catalog.COURSE_REAL_ESTATE
        return e
    }

    /** A furnished, profitable-looking coffee shop in a building you rent (or own). */
    private fun coffeeShop(e: GameEngine, own: Boolean): com.hustlecity.game.model.Business {
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 75..150 }
        assertTrue(if (own) e.buyBuilding(b.id) else e.rentBuilding(b.id))
        val biz = e.startBusiness(b.id, "coffee", "Corner Brew")!!
        for ((i, tag) in listOf("register", "coffee", "pastry").withIndex()) biz.furniture += PlacedItem(itemForTag(tag), i * 2, 0, 0)
        biz.stock["coffee"] = 50
        repeat(7) { d -> biz.history += BizDay(d, 1500.0, 300.0, 400.0, 100.0, 0.0, 300, .7) }
        return biz
    }

    @Test
    fun sellingABusinessHandsItToARival() {
        val e = game()
        val biz = coffeeShop(e, own = false)
        val staff = com.hustlecity.game.model.Employee(e.state.newId(), "Sam", biz.type.staffRole, .5, 15.0, 15.0, 30, businessId = biz.id)
        e.state.employees += staff
        val offer = e.saleOffer(biz, withBuilding = false)
        assertNotNull(offer)
        // two months of a $700/day profit, plus the fit-out and stock
        assertTrue("goodwill counts: ${offer!!.price}", offer.price > 700 * 50)
        val money = e.state.player.money
        val buyerCash = offer.buyer.cash
        assertTrue(e.sellBusiness(biz, withBuilding = false))
        assertEquals(money + offer.price, e.state.player.money, 1.0)
        assertEquals(buyerCash - offer.price, offer.buyer.cash, 1.0)
        assertTrue(e.state.businesses.none { it.id == biz.id })
        assertNull("the lease went with it", e.state.rentalFor(biz.buildingId))
        val rb = e.state.rivalBusinessAt(biz.buildingId)!!
        assertEquals(offer.buyer.id, rb.rivalId)
        assertEquals("Corner Brew", rb.name)
        assertTrue("staff stay with the store", e.state.employees.none { it.id == staff.id })
    }

    @Test
    fun sellingAnOwnedStoreMakesTheBuyerYourTenant() {
        val e = game()
        val biz = coffeeShop(e, own = true)
        assertTrue(e.sellBusiness(biz, withBuilding = false))
        val r = e.state.rentalFor(biz.buildingId)!!
        assertTrue(r.owned)
        val rb = e.state.rivalBusinessAt(biz.buildingId)!!
        assertTrue(r.tenant.isNotEmpty())
        assertTrue(rb.tenantRent > 0)
        assertEquals(r.tenantRent, rb.tenantRent, .01)
        // a night's rent comes in
        val before = e.state.player.money
        e.landlordDaily()
        assertEquals(before + r.tenantRent / 30, e.state.player.money, .01)
        // ending the tenancy closes their store
        e.evictTenant(biz.buildingId)
        assertTrue(r.tenant.isEmpty())
        assertNull(e.state.rivalBusinessAt(biz.buildingId))
    }

    @Test
    fun sellingWithTheBuildingTransfersBoth() {
        val e = game()
        val biz = coffeeShop(e, own = true)
        val offer = e.saleOffer(biz, withBuilding = true)!!
        assertTrue(offer.buildingPrice > 0)
        assertTrue(e.sellBusiness(biz, withBuilding = true))
        assertNull(e.state.rentalFor(biz.buildingId))
        assertTrue(e.state.rivalBusinessAt(biz.buildingId)!!.owned)
    }

    @Test
    fun saleAndLeaseBackKeepsTheBusinessRunning() {
        val e = game()
        val biz = coffeeShop(e, own = true)
        val bld = e.city.building(biz.buildingId)!!
        val money = e.state.player.money
        assertTrue(e.sellAndLeaseBack(bld.id))
        val r = e.state.rentalFor(bld.id)!!
        assertFalse(r.owned)
        assertTrue(r.rentPerMonth > 0)
        assertEquals(money + bld.value * .9, e.state.player.money, 1.0)
        assertNotNull(e.state.businessAt(bld.id))
    }

    @Test
    fun listedBuildingsFindTenantsWhoPayRent() {
        val e = game()
        val shop = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 75..200 && it.traffic > .35f }
        val flat = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RESIDENTIAL && it.id != e.state.player.home }
        assertTrue(e.buyBuilding(shop.id))
        assertTrue(e.buyBuilding(flat.id))
        for (b in listOf(shop, flat)) {
            assertTrue(e.canLet(e.state.rentalFor(b.id)!!))
            e.listForTenants(b.id, 1.0)
        }
        repeat(60) { e.landlordDaily() }
        val shopRental = e.state.rentalFor(shop.id)!!
        val flatRental = e.state.rentalFor(flat.id)!!
        assertTrue("the flat found a tenant", flatRental.tenant.isNotEmpty())
        assertTrue("the shop found a tenant", shopRental.tenant.isNotEmpty())
        val store = e.state.rivalBusinessAt(shop.id)!!
        assertTrue(store.tenantRent > 0)
        assertTrue("a rival runs it", e.state.rivals.any { it.id == store.rivalId })
        assertTrue((e.state.ledgerToday["in:Rent income"] ?: 0.0) > 0)
        // rivals never buy a building out from under you
        assertTrue(store.rivalId != SUBSIDIARY && !store.owned)
    }

    @Test
    fun sellingACarSellsItsCargoAndPausesRoutes() {
        val e = game()
        val v = e.state.vehicles.first()
        v.cargo += com.hustlecity.game.model.CargoItem("coffee", true, 120)
        e.state.fleet += com.hustlecity.game.model.FleetRoute(1, v.id)
        val money = e.state.player.money
        val value = e.vehicleValue(v)
        e.sellVehicle(v)
        assertTrue(e.state.vehicles.isEmpty())
        assertEquals(money + value + Catalog.product("coffee")!!.cost * 120 * .5, e.state.player.money, .01)
        assertFalse(e.state.fleet.first().enabled)
    }

    @Test
    fun saveSlotsKeepGamesApart() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // a save from before slots moves into slot 1
        val legacy = GameEngine(null).apply { newGame("Old", 0, 0, 1) }
        java.io.File(ctx.filesDir, "save.json").writeText(legacy.saves.encode(legacy.state))
        val e = GameEngine(ctx)
        assertTrue(e.saves.exists(1))
        assertEquals("Old", e.saves.info(1)!!.name)
        assertEquals(2, e.saves.freeSlot())
        // a new game in another city goes into its own slot
        e.newGame("Manila Me", 0, 0, 1, mapId = Maps.MANILA, slot = 2)
        assertEquals("Manila Me", e.saves.info(2)!!.name)
        assertEquals(Maps.MANILA, e.saves.info(2)!!.mapId)
        assertEquals("Old", e.saves.info(1)!!.name)
        assertEquals(2, e.saves.latestSlot())
        // loading slot 1 switches the game in play to it
        assertTrue(e.loadGame(1))
        assertEquals("Old", e.state.player.name)
        assertEquals(1, e.saves.slot)
        e.state.player.money = 1234.0
        e.save()
        assertTrue(e.loadGame(2))
        assertEquals("Manila Me", e.state.player.name)
        assertTrue(e.loadGame(1))
        assertEquals(1234.0, e.state.player.money, .01)
        e.saves.delete(2)
        assertFalse(e.saves.exists(2))
        assertEquals((1..SaveManager.SLOTS).count { e.saves.exists(it) }, 1)
    }

    @Test
    fun citiesShowTheirOwnMoney() {
        assertEquals("$1,234.50", Currency.USD.format(1234.5))
        assertEquals("-$5,000", Currency.USD.whole(-5000.0))
        assertEquals("¥525", Currency.JPY.format(3.5))
        assertEquals("₩6,750", Currency.KRW.whole(5.0))
        assertEquals("₱168.00", Currency.PHP.format(3.0))
        assertEquals("₩6.75B", Currency.KRW.short(5_000_000.0))
        assertEquals("Reach ¥15,000,000 before -¥750,000", Currency.JPY.localize("Reach \$100,000 before -\$5,000"))
        // prices step and round in local money
        assertEquals(10.0 / 150, Currency.JPY.priceStep(3.5), 1e-9)
        assertEquals(530.0, Currency.JPY.local(Currency.JPY.roundPrice(3.5 + 4.0 / 150)), 1e-6)
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1, mapId = Maps.TOKYO)
        assertTrue(e.money(e.state.player.money).startsWith("¥"))
        assertEquals("¥750,000", e.money(5000.0))
    }

    @Test
    fun noOfferWhenNobodyCanPay() {
        val e = game()
        val biz = coffeeShop(e, own = false)
        e.state.rivals.forEach { it.cash = 1000.0 }
        assertNull(e.saleOffer(biz, withBuilding = false))
        assertFalse(e.sellBusiness(biz, withBuilding = false))
        assertNotNull(e.state.business(biz.id))
        assertTrue(BizCalc.furnitureValue(biz) > 0)
    }
}

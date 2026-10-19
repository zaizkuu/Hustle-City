package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.Person
import com.hustlecity.game.engine.SaveManager
import com.hustlecity.game.engine.restockDisplay
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.stockShelves
import com.hustlecity.game.engine.storeItem
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.CargoItem
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stock you can see on the displays (it follows the business's stock, customers lift items off, restocking fills
 * them in) and the staff-only back room (a partition customers never pass, storage shelves for overflow boxes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShelfStockTest {
    private class Store(val e: GameEngine, val b: Building, val biz: Business) {
        val depth get() = biz.backRoom
        fun scene(): InteriorScene {
            e.state.player.inBuilding = b.id
            e.state.player.x = b.doorX; e.state.player.y = b.doorY
            return InteriorScene(e, b, InteriorMode.BUSINESS).also { e.interior = it }
        }
    }

    /** A freshly rented store (a typical retail unit of the classic city: 8 to 10 tiles deep). */
    private fun store(typeId: String = "giftshop", minW: Int = 12, depths: IntRange = 8..10): Store {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.state.player.money = 100_000.0
        e.state.minute = 12L * 60 + 30 // midday
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW >= minW && it.gridD in depths }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, typeId, "X")!!
        biz.tempClosed = false
        return Store(e, b, biz)
    }

    private fun hire(s: Store, role: StaffRole = StaffRole.CUSTOMER_SERVICE) {
        s.e.state.employees += Employee(s.e.state.newId(), "Pat", role, .6, 15.0, 15.0, 30, businessId = s.biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
    }

    private fun run(sc: InteriorScene, seconds: Float, step: (Int) -> Unit = {}) {
        val n = (seconds * 30).toInt()
        for (k in 0 until n) { sc.update(1f / 30f, true); step(k) }
    }

    @Test
    fun displayFillFollowsTheBusinessStock() {
        val s = store()
        val y = s.depth + 1
        s.biz.furniture += listOf(PlacedItem("shelf", 1, y, 0), PlacedItem("shelf", 4, y, 0), PlacedItem("shelf", 7, y, 0))
        val sc = s.scene()
        val shelf = sc.shelf!!
        val shelves = sc.items.indices.filter { sc.items[it].itemId == "shelf" }
        val cap = Catalog.item("shelf")!!.displayUnits
        run(sc, .1f)
        val slots = shelf.layoutOf(shelves[0])?.slots ?: 0
        assertTrue("shelves show product slots", slots in 12..24)
        assertEquals("empty store: nothing on display", 0, shelves.sumOf { shelf.visible(it) })

        // a third full: every shelf shows about a third of its slots, and the units add up to the stock
        s.biz.stock["gift_cheap"] = cap
        run(sc, 3f)
        assertEquals(cap, shelves.sumOf { shelf.unitsOf(it) })
        for (i in shelves) {
            assertEquals("stock split in proportion over the shelves", cap / 3.0, shelf.unitsOf(i).toDouble(), 2.0)
            assertEquals("visible items follow the shelf's share", shelf.target(i), shelf.visible(i))
            assertEquals(slots / 3.0, shelf.visible(i).toDouble(), 1.0)
        }
        // full up
        s.biz.stock["gift_cheap"] = cap * 2; s.biz.stock["umbrella"] = cap
        run(sc, 3f)
        for (i in shelves) assertEquals("full shelves show every slot", slots, shelf.visible(i))
        assertTrue("both products are on display", shelf.slots(shelves[0])!!.toSet().containsAll(listOf("gift_cheap", "umbrella")))
        // a single unit left still shows one item
        s.biz.stock["gift_cheap"] = 1; s.biz.stock["umbrella"] = 0
        run(sc, 1f)
        assertEquals("one unit left shows exactly one item", 1, shelves.sumOf { shelf.visible(it) })
        s.biz.stock["gift_cheap"] = 0
        run(sc, 1f)
        assertEquals(0, shelves.sumOf { shelf.visible(it) })
    }

    @Test
    fun aCustomerPickingAnItemLeavesOneFewerOnTheShelf() {
        val s = store()
        val y = s.depth + 1
        s.biz.furniture += listOf(PlacedItem("shelf", 1, y, 0), PlacedItem("cash_register", 6, s.b.gridD - 3, 0))
        s.biz.stock["gift_cheap"] = 70
        hire(s)
        val sc = s.scene()
        val shelf = sc.shelf!!
        val i = sc.items.indexOfFirst { it.itemId == "shelf" }
        run(sc, 1f)
        val before = shelf.visible(i)
        assertTrue(before > 1)
        val who = Person(2f, y + 1.5f, 0, 0)
        val pid = shelf.customerTakes(i, who)
        assertEquals("gift_cheap", pid)
        assertEquals("the shelf shows one fewer straight away", before - 1, shelf.visible(i))
        assertEquals("the item is on its way to the customer", 1, shelf.takes.size)
        run(sc, 1f)
        assertTrue(shelf.takes.isEmpty())
        assertEquals("it stays gone while the sale goes through", before - 1, shelf.visible(i))
        // the economy sells it: the count stays one fewer once the stock catches up
        s.biz.stock["gift_cheap"] = 70 - 3
        run(sc, 1f)
        assertTrue(shelf.visible(i) <= before - 1)

        // real shoppers walking the store: they lift items off, carry them to the till and leave with a bag
        s.biz.stock["gift_cheap"] = 70
        val sc2 = s.scene()
        s.e.economy.servedRate[s.biz.id] = 120.0
        val shelf2 = sc2.shelf!!
        run(sc2, 1f)
        val full = shelf2.visible(i)
        var lowest = full
        var holding = 0
        var bagged = 0
        run(sc2, 60f) {
            lowest = minOf(lowest, shelf2.visible(i))
            holding = maxOf(holding, sc2.visitors.count { it.held != 0 && it.heldIn <= 0f && !it.bagged })
            bagged = maxOf(bagged, sc2.visitors.count { it.bagged })
        }
        assertTrue("shoppers took items off the shelf ($lowest of $full left)", lowest < full)
        assertTrue("someone carried an item to the register", holding > 0)
        assertTrue("someone paid and left with a bag", bagged > 0)
    }

    @Test
    fun restockingFillsTheDisplayItemByItemAsTheBoxArrives() {
        val s = store()
        val y = s.depth + 1
        s.biz.furniture += listOf(PlacedItem("shelf", 1, y, 0), PlacedItem("storage_shelf", 1, 0, 0), PlacedItem("cash_register", 6, s.b.gridD - 3, 0))
        s.biz.backStock["gift_cheap"] = 90
        hire(s)
        val sc = s.scene()
        val shelf = sc.shelf!!
        val i = sc.items.indexOfFirst { it.itemId == "shelf" }
        run(sc, 2f)
        assertEquals(0, shelf.visible(i))
        assertEquals("the storage shelf shows the 3 boxes", 3, shelf.visible(sc.items.indexOfFirst { it.itemId == "storage_shelf" }))

        // a member of staff takes a box off the storage shelf in the back room and carries it to the display
        assertTrue(s.e.economy.restockFromBack(s.biz, 1) > 0)
        var carried = false
        var waited = false
        val seen = ArrayList<Int>()
        run(sc, 25f) {
            if (sc.staff.any { it.carry == 1 }) carried = true
            if (shelf.waiting(i) && shelf.visible(i) == 0) waited = true
            seen += shelf.visible(i)
        }
        assertTrue("someone carried the box over", carried)
        assertTrue("the items waited for the box to arrive", waited)
        assertTrue("the run finished", sc.restockRuns == 0)
        assertEquals("the display filled in", shelf.target(i), shelf.visible(i))
        val firstShown = seen.indexOfFirst { it > 0 }
        val filled = seen.indexOfFirst { it == shelf.target(i) }
        assertTrue("items appeared one by one (${filled - firstShown} frames)", filled - firstShown >= 4 && shelf.target(i) >= 4)

        // you can do it yourself too: Restock walks you to the back room and back with a box
        s.e.state.employees.clear()
        s.biz.stock["gift_cheap"] = 0
        run(sc, 1f)
        assertEquals(0, shelf.visible(i))
        assertTrue(s.e.restockDisplay(i))
        var boxInArms = false
        run(sc, 20f) { if (sc.playerBox) boxInArms = true }
        assertTrue("you carried a box from the back room", boxInArms)
        assertFalse(sc.playerBox)
        assertEquals("one box (30 units) went on the shelf", 30, s.biz.stock["gift_cheap"])
        assertEquals(shelf.target(i), shelf.visible(i))
        assertTrue(shelf.visible(i) > 0)
    }

    @Test
    fun aTypicalRentedStoreHasABackRoomCustomersNeverEnter() {
        val s = store()
        val sc = s.scene()
        assertNotNull("a back room", sc.backRoom)
        val room = sc.backRoom!!
        assertTrue("about a third of the floor, at least 3 rows (${room.depth} of ${sc.d})", room.depth in 3..sc.d / 2)
        assertTrue(room.doorW in 1..2)
        // customer furniture goes out front, storage and cleaning in the back
        assertFalse("no displays in the back room", sc.canPlace("shelf", 1, 0, 0))
        assertFalse("no registers in the back room", sc.canPlace("cash_register", 1, 0, 0))
        assertNotNull(sc.backRoomProblem("table_set", 1, 0, 0))
        assertFalse("nothing across the wall", sc.canPlace("storage_shelf", 1, room.depth - 1, 1))
        assertFalse("the doorway stays clear", sc.canPlace("plant_small", room.doorX, room.depth, 0))
        assertTrue(sc.canPlace("storage_shelf", 1, 0, 0))
        assertTrue(sc.canPlace("cleaning_station", sc.w - 1, 0, 0))
        val y = room.depth
        s.biz.furniture += listOf(
            PlacedItem("storage_shelf", 1, 0, 0), PlacedItem("cleaning_station", sc.w - 1, 0, 0),
            PlacedItem("shelf", 1, y + 1, 0), PlacedItem("shelf", 4, y + 1, 0), PlacedItem("drinks_fridge", 7, y, 0),
            PlacedItem("cash_register", sc.w / 2, sc.d - 3, 0),
        )
        for (p in s.biz.furniture) assertNull("${p.itemId} at ${p.x},${p.y}", sc.backRoomProblem(p.itemId, p.x, p.y, p.rot))
        s.biz.stock["gift_cheap"] = 200; s.biz.stock["energy_drink"] = 120
        hire(s)
        hire(s, StaffRole.CLEANER)
        s.e.economy.servedRate[s.biz.id] = 120.0
        var spawned = 0
        val seen = HashSet<Person>()
        var cleanerInBack = false
        run(sc, 120f) {
            for (v in sc.visitors) {
                if (seen.add(v)) spawned++
                assertTrue("a customer walked into the back room (y=${v.y})", v.y >= room.depth - .05f)
                for (t in v.path) assertTrue("a customer's path goes into the back room", t / sc.w >= room.depth)
            }
            if (sc.staff.any { it.y < room.depth }) cleanerInBack = true
        }
        assertTrue("customers came in ($spawned)", spawned > 10)
        assertTrue("staff use the back room", cleanerInBack)
    }

    @Test
    fun overflowGoesToTheBackRoomAndIsRestockedFromIt() {
        val s = store()
        val y = s.depth + 1
        s.biz.furniture += listOf(PlacedItem("shelf", 1, y, 0), PlacedItem("cash_register", 6, s.b.gridD - 3, 0))
        val per = Catalog.product("gift_cheap")!!.unitsPerBox
        // without storage shelves, what doesn't fit on the displays is refused and refunded
        s.biz.pendingDelivery += CargoItem("gift_cheap", true, 300)
        s.biz.pendingDeliveryAt = s.e.state.minute
        s.e.economy.minute()
        assertEquals(140, s.biz.stock["gift_cheap"])
        assertTrue(s.biz.backStock.isEmpty())

        // with a storage shelf in the back room, the overflow is boxed on it
        s.biz.furniture += PlacedItem("storage_shelf", 1, 0, 0)
        s.biz.stock["gift_cheap"] = 0
        s.biz.pendingDelivery += CargoItem("gift_cheap", true, 300)
        s.biz.pendingDeliveryAt = s.e.state.minute
        s.e.economy.minute()
        assertEquals(140, s.biz.stock["gift_cheap"])
        assertEquals(160, s.biz.backStock["gift_cheap"])
        assertEquals((160 + per - 1) / per, BizCalc.storageBoxesUsed(s.biz))
        assertTrue("room for 16 boxes", BizCalc.intakeRoom(s.biz, "gift_cheap") > 0)

        // it sells; staff on shift bring a box out once a whole one fits (closed, so nothing sells meanwhile)
        s.biz.tempClosed = true
        s.biz.stock["gift_cheap"] = 140 - per * 2
        s.e.economy.minute()
        assertEquals("nobody on shift: it waits in the back room", 140 - per * 2, s.biz.stock["gift_cheap"])
        hire(s)
        repeat(3) { s.e.economy.minute() }
        assertEquals("restocked from the back room", 140, s.biz.stock["gift_cheap"])
        assertEquals(160 - per * 2, s.biz.backStock["gift_cheap"])

        // the store restocks whatever fits when it closes
        s.e.state.employees.clear()
        s.biz.stock["gift_cheap"] = 130
        s.biz.closeHour = 14
        s.e.state.minute = 14L * 60
        s.e.economy.minute()
        assertEquals("night restock tops the shelf up", 140, s.biz.stock["gift_cheap"])
        assertEquals(160 - per * 2 - 10, s.biz.backStock["gift_cheap"])

        // putting the storage shelf away sells off the boxes that no longer fit anywhere
        val sc = s.scene()
        val money = s.e.state.player.money
        s.e.storeItem(sc.storageShelfFor())
        assertTrue(s.biz.backStock.isEmpty())
        assertEquals(140, s.biz.stock["gift_cheap"])
        assertTrue("sold off at half price", s.e.state.player.money > money)
    }

    @Test
    fun youCanCarryBoxesInAndUnpackThemOntoTheDisplays() {
        val s = store()
        val y = s.depth + 1
        s.biz.furniture += listOf(PlacedItem("shelf", 1, y, 0), PlacedItem("storage_shelf", 1, 0, 0))
        val sc = s.scene()
        s.e.state.player.hand += CargoItem("gift_cheap", true, 30)
        s.e.state.player.hand += CargoItem("gift_cheap", true, 30)
        assertTrue(s.e.stockShelves())
        run(sc, 15f)
        assertEquals(60, s.biz.stock["gift_cheap"])
        assertTrue(s.e.state.player.hand.isEmpty())
        val i = sc.items.indexOfFirst { it.itemId == "shelf" }
        assertTrue(sc.shelf!!.visible(i) > 0)
    }

    @Test
    fun oldSavesWithoutBackRoomsLoad() {
        val s = store()
        // an old store with a shelf right against the back wall: it keeps its open floor
        s.biz.furniture += listOf(PlacedItem("shelf", 0, 0, 0), PlacedItem("cash_register", 5, 2, 0))
        s.biz.stock["gift_cheap"] = 50
        val json = Json { ignoreUnknownKeys = true }
        val saves = SaveManager(null)
        val text = saves.encode(s.e.state)
        val tree = json.parseToJsonElement(text).jsonObject
        val bizzes = tree["businesses"]!!.jsonArray.map { b -> JsonObject(b.jsonObject.filterKeys { it !in setOf("backStock", "backRoom", "backDoor") }) }
        val old = JsonObject(tree + ("businesses" to JsonArray(bizzes)))
        assertFalse(old.toString().contains("backStock"))
        val state = saves.decode(old.toString())
        val biz = state.businesses.first { it.id == s.biz.id }
        assertEquals(-1, biz.backRoom)
        assertTrue(biz.backStock.isEmpty())
        assertEquals(50, biz.stock["gift_cheap"])
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.state = state
        val b = e.city.building(biz.buildingId)!!
        e.state.player.inBuilding = b.id
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.interior = sc
        assertNull("the old furniture is in the way: no back room", sc.backRoom)
        assertEquals(0, biz.backRoom)
        repeat(60) { e.frame(1 / 30f) }
        assertTrue(sc.shelf!!.visible(0) > 0)

        // an old store with room at the back gets one, fitted around its furniture
        val s2 = store()
        s2.biz.backRoom = -1; s2.biz.backDoor = -1
        s2.biz.furniture += listOf(PlacedItem("shelf", 1, s2.b.gridD - 4, 0))
        val sc2 = s2.scene()
        assertNotNull(sc2.backRoom)
        // and one whose furniture now stands across the wall gets a smaller room, or none, instead of breaking
        val s3 = store(depths = 11..14)
        val deep = s3.biz.backRoom
        assertTrue(deep >= 4)
        s3.biz.furniture += PlacedItem("shelf", 2, 3, 0)
        val sc3 = s3.scene()
        assertTrue("the back room shrank to clear the shelf (${sc3.backRoom?.depth})", (sc3.backRoom?.depth ?: 0) <= 3)
        repeat(30) { sc3.update(1 / 30f, true) }
    }
}

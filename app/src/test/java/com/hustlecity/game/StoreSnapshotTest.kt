package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders your own stores offscreen to build/snapshots: products visible on the displays, partly sold-out shelves,
 * and the staff-only back room with its storage shelves, to check the look without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StoreSnapshotTest {
    private val outDir = File("build/snapshots").apply { mkdirs() }

    private class Shop(val e: GameEngine, val b: Building, val biz: Business, val sc: InteriorScene)

    private fun shop(typeId: String, depths: IntRange = 9..10, furnish: (Business, Int, Int, Int) -> Unit): Shop {
        val e = GameEngine(null)
        e.newGame("Tester", 1, 0, 1)
        e.ui.tip = null
        e.state.minute = 13L * 60 + 30
        e.state.player.courses += Catalog.COURSE_IMPORT
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 13..16 && it.gridD in depths }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, typeId, "Snapshot")!!
        biz.tempClosed = false
        furnish(biz, b.gridW, b.gridD, biz.backRoom.coerceAtLeast(0))
        e.state.player.inBuilding = b.id
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.interior = sc
        return Shop(e, b, biz, sc)
    }

    private fun staff(s: Shop, role: StaffRole) {
        s.e.state.employees += Employee(s.e.state.newId(), "Sam", role, .6, 15.0, 15.0, 30, businessId = s.biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
    }

    private fun render(s: Shop, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        InteriorRenderer(s.e).draw(Canvas(bmp), w, h)
        return bmp
    }

    private fun save(bmp: Bitmap, name: String) {
        File(outDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A busy supermarket: full shelves, fridges and freezers out front, boxes on the storage shelves in the back room. */
    @Test
    fun stockedStoreWithBackRoom() {
        val s = shop("supermarket") { biz, w, d, room ->
            val y = room + 1
            biz.furniture += listOf(
                PlacedItem("storage_shelf", 0, 0, 0), PlacedItem("storage_shelf", 3, 0, 0), PlacedItem("cleaning_station", 6, 0, 0),
                PlacedItem("shelf", 1, y, 0), PlacedItem("shelf", 4, y, 0),
                PlacedItem("drinks_fridge", 7, room, 0), PlacedItem("drinks_fridge", 8, room, 0),
                PlacedItem("fresh_fridge", 1, y + 3, 0), PlacedItem("freezer", 4, y + 3, 0), PlacedItem("ice_cream_freezer", w - 3, y + 1, 1),
                PlacedItem("cash_register", w - 5, d - 3, 0),
            )
            for ((pid, _) in biz.type.products) {
                val p = Catalog.product(pid) ?: continue
                biz.stock[pid] = com.hustlecity.game.engine.BizCalc.capacityFor(biz, pid) / biz.type.products.count { Catalog.product(it.first)?.tag == p.tag }
            }
            biz.backStock["soda"] = 48 * 4; biz.backStock["snacks"] = 60 * 3; biz.backStock["frozen_food"] = 30 * 2; biz.backStock["toilet_paper"] = 24 * 3
        }
        staff(s, StaffRole.CUSTOMER_SERVICE); staff(s, StaffRole.CUSTOMER_SERVICE); staff(s, StaffRole.CLEANER)
        s.e.economy.servedRate[s.biz.id] = 140.0
        repeat(30 * 14) { s.sc.update(1 / 30f, true) }
        // a box comes out of the back room while we watch
        s.biz.stock["snacks"] = (s.biz.stock["snacks"] ?: 0) - 70
        s.e.economy.restockFromBack(s.biz, 1)
        repeat(30 * 3) { s.sc.update(1 / 30f, true) }
        val shelf = s.sc.shelf!!
        val shown = s.sc.items.indices.sumOf { shelf.visible(it) }
        assertTrue("products are on display ($shown)", shown > 80)
        assertTrue(s.sc.backRoom != null)
        save(render(s, 1600, 900), "store_stocked.png")
        // a member of staff on the way from the back room with a box (the display waits for it)
        s.biz.stock["toilet_paper"] = 0
        repeat(30) { s.sc.update(1 / 30f, true) }
        s.e.economy.restockFromBack(s.biz, 1)
        var runner: com.hustlecity.game.engine.Person? = null
        for (k in 0 until 30 * 25) {
            s.sc.update(1 / 30f, true)
            runner = s.sc.staff.firstOrNull { it.carry == 1 && it.y > s.sc.backRoom!!.depth + .6f }
            if (runner != null) break
        }
        assertTrue("someone carries the box out of the back room", runner != null)
        s.sc.zoom = 2.3f; s.sc.px = runner!!.x - 1.6f; s.sc.py = runner.y + 1.4f
        save(render(s, 1600, 900), "store_restock.png")
        repeat(30 * 20) { s.sc.update(1 / 30f, true) }
        // closer, from the shop floor
        s.sc.zoom = 2.1f; s.sc.px = s.sc.w / 2f; s.sc.py = s.sc.backRoom!!.depth + 2.5f
        save(render(s, 1600, 900), "store_stocked_close.png")
        // and from the other side, looking out of the back room
        s.sc.zoom = 1f; s.sc.yaw = Math.toRadians(150.0).toFloat()
        save(render(s, 1600, 900), "store_stocked_reverse.png")
    }

    /**
     * A liquor store late in the day: the shelves half sold, the last customers just emptied most of the right-hand
     * shelf (it shows that before the sales go through), and the beer fridges nearly empty.
     */
    @Test
    fun partlySoldOutDisplays() {
        val s = shop("liquor") { biz, w, d, room ->
            val y = room + 1
            biz.furniture += listOf(
                PlacedItem("storage_shelf", 0, 0, 0), PlacedItem("shelf", 1, y, 0), PlacedItem("shelf", 4, y, 0), PlacedItem("shelf", 7, y, 0),
                PlacedItem("drinks_fridge", 1, y + 3, 0), PlacedItem("drinks_fridge", 2, y + 3, 0), PlacedItem("cash_register", w - 5, d - 3, 0),
            )
            biz.stock["wine"] = 140; biz.stock["whiskey"] = 140; biz.stock["beer"] = 160; biz.stock["energy_drink"] = 40
        }
        staff(s, StaffRole.CUSTOMER_SERVICE)
        repeat(30 * 2) { s.sc.update(1 / 30f, true) }
        val shelf = s.sc.shelf!!
        val shelves = s.sc.items.indices.filter { s.sc.items[it].itemId == "shelf" }
        val full = shelves.sumOf { shelf.visible(it) }
        // a day's sales
        s.biz.stock["wine"] = 110; s.biz.stock["whiskey"] = 80; s.biz.stock["beer"] = 24; s.biz.stock["energy_drink"] = 10
        repeat(30 * 2) { s.sc.update(1 / 30f, true) }
        val half = shelf.visible(shelves[2])
        // and the last shoppers pick the right-hand shelf over
        val who = com.hustlecity.game.engine.Person(8f, s.sc.backRoom!!.depth + 2.5f, 0, 0)
        repeat(7) { shelf.customerTakes(shelves[2], who) }
        repeat(30) { s.sc.update(1 / 30f, true) }
        val now = shelves.sumOf { shelf.visible(it) }
        assertTrue("fewer on display after the sales ($now of $full)", now < full)
        assertTrue("the picked-over shelf shows it", shelf.visible(shelves[2]) == half - 7)
        s.sc.zoom = 2.2f; s.sc.px = 5f; s.sc.py = s.sc.backRoom!!.depth + 3f
        save(render(s, 1600, 900), "store_sold_out.png")
    }

    /** The GPU renderer gets the stock, the back room wall and its sign too (more geometry with full displays). */
    @Test
    fun gpuFrameHasTheStock() {
        val s = shop("supermarket") { biz, w, d, room ->
            biz.furniture += listOf(PlacedItem("storage_shelf", 0, 0, 0), PlacedItem("shelf", 1, room + 1, 0), PlacedItem("drinks_fridge", 4, room + 1, 0),
                PlacedItem("cash_register", w - 5, d - 3, 0))
        }
        repeat(30) { s.sc.update(1 / 30f, true) }
        val r = InteriorRenderer(s.e)
        fun triangles(): Int {
            val frame = com.hustlecity.game.render.gpu.GpuFrame()
            r.draw(Canvas(Bitmap.createBitmap(800, 450, Bitmap.Config.ARGB_8888)), 800, 450, frame)
            // product models on full displays are in the cached layer the GPU keeps
            return frame.triangleCount + (frame.staticGeom?.triangleCount ?: 0)
        }
        val empty = triangles()
        s.biz.stock["snacks"] = 140; s.biz.stock["soda"] = 160; s.biz.backStock["soda"] = 48 * 6
        repeat(30 * 3) { s.sc.update(1 / 30f, true) }
        val stocked = triangles()
        assertTrue("stocked displays add geometry on the GPU ($empty -> $stocked)", stocked > empty + 200)
    }

    /** Every kind of display stocked, one store type per tile, to check each product shape. */
    @Test
    fun displayGallery() {
        val stores = listOf(
            "florist" to listOf("flower_stand", "flower_stand", "shelf"),
            "produce" to listOf("produce", "produce"),
            "coffee" to listOf("coffee_machine", "pastry_case", "pastry_case"),
            "fastfood" to listOf("grill", "fryer", "pizza_oven", "drinks_fridge"),
            "clothing" to listOf("clothing_rack", "round_rack", "shoe_rack"),
            "bookstore" to listOf("bookshelf", "bookshelf", "magazine_rack"),
            "electronics" to listOf("electronics_table", "electronics_table"),
            "jewelry" to listOf("jewelry_case", "jewelry_case", "jewelry_case"),
            "giftshop" to listOf("shelf", "shelf", "drinks_fridge"),
            "nightclub" to listOf("cocktail_bar"),
            "cinema" to listOf("concession_stand", "concession_stand"),
            "gym" to listOf("drinks_fridge", "drinks_fridge"),
        )
        val tw = 800; val th = 450
        val out = Bitmap.createBitmap(tw * 3, th * 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 26f }
        for ((k, entry) in stores.withIndex()) {
            val (type, pieces) = entry
            val s = shop(type, 8..10) { biz, _, _, room ->
                var x = 1
                for (id in pieces) {
                    val def = Catalog.item(id)!!
                    biz.furniture += PlacedItem(id, x, room + 1, 0)
                    x += def.w + 1
                }
                biz.furniture += PlacedItem("storage_shelf", 0, 0, 0)
                for ((pid, _) in biz.type.products) {
                    val p = Catalog.product(pid) ?: continue
                    if (p.service) continue
                    val kinds = biz.type.products.count { Catalog.product(it.first)?.tag == p.tag }
                    biz.stock[pid] = com.hustlecity.game.engine.BizCalc.capacityFor(biz, pid) * 3 / 4 / kinds.coerceAtLeast(1)
                    biz.backStock[pid] = p.unitsPerBox
                }
            }
            repeat(30 * 2) { s.sc.update(1 / 30f, true) }
            s.sc.zoom = 2.3f; s.sc.px = 5f; s.sc.py = s.sc.backRoom?.let { it.depth + 2.5f } ?: 3f
            val bmp = render(s, tw, th)
            canvas.drawBitmap(bmp, (k % 3) * tw.toFloat(), (k / 3) * th.toFloat(), null)
            canvas.drawText(type, (k % 3) * tw + 14f, (k / 3) * th + 34f, label)
        }
        save(out, "store_display_gallery.png")
    }
}

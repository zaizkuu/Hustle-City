package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.Sky
import com.hustlecity.game.model.WeatherSpan
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Maps
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the camera moves for the trailer as numbered JPEG frames in build/trailer/<scene>/. Only runs when
 * HC_TRAILER=1 is set, since it takes a few minutes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TrailerTest {
    private val w = 1280
    private val h = 720
    private val fps = 30

    /** A camera keyframe: target, distance, yaw. */
    private class Key(val x: Float, val y: Float, val dist: Float, val yaw: Float)

    private fun ease(t: Float) = t * t * (3f - 2f * t)

    private fun engine(mapId: String, hour: Float): GameEngine {
        val e = GameEngine(null)
        e.newGame("Alex Rivera", 1, 2, 1, mapId = mapId)
        e.ui.tip = null; e.ui.phoneOpen = false
        e.state.minute = (e.state.minute / 1440) * 1440 + (hour * 60).toLong()
        return e
    }

    private fun weather(e: GameEngine, sky: Sky) {
        e.state.weather.clear()
        e.state.weather += WeatherSpan(e.state.minute - 60, e.state.minute + 3 * 1440L, sky)
        e.weather.ensure()
    }

    /** Flies the camera through [keys] over [seconds], keeping the player (and so the traffic) at the target. */
    private fun fly(e: GameEngine, name: String, seconds: Float, keys: List<Key>, before: (GameEngine, Int) -> Unit = { _, _ -> }) {
        val dir = File("build/trailer/$name").apply { deleteRecursively(); mkdirs() }
        val r = WorldRenderer(e)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val frames = (seconds * fps).toInt()
        fun place(f: Int) {
            val t = f.toFloat() / (frames - 1).coerceAtLeast(1) * (keys.size - 1)
            val i = t.toInt().coerceAtMost(keys.size - 2)
            val u = ease(t - i)
            val a = keys[i]; val b = keys[i + 1]
            e.camera.tx = a.x + (b.x - a.x) * u; e.camera.ty = a.y + (b.y - a.y) * u
            e.camera.dist = a.dist + (b.dist - a.dist) * u; e.camera.zoom = e.camera.dist
            e.camera.yaw = a.yaw + (b.yaw - a.yaw) * u
            e.state.player.x = e.camera.tx; e.state.player.y = e.camera.ty
        }
        // let traffic and people fill the streets first
        place(0)
        repeat(90) { e.frame(1f / fps); place(0) }
        for (f in 0 until frames) {
            before(e, f)
            e.frame(1f / fps)
            place(f)
            canvas.drawColor(0xFF000000.toInt())
            r.draw(canvas, w, h, 2f)
            File(dir, "%04d.jpg".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        }
    }

    private fun on(): Boolean = System.getenv("HC_TRAILER") == "1"

    /** Where a named landmark stands (or the player's start if the map has none by that name). */
    private fun landmark(e: GameEngine, name: String): Pair<Float, Float> =
        e.city.landmarks.firstOrNull { it.name.contains(name, ignoreCase = true) }?.let { it.x to it.y } ?: (e.state.player.x to e.state.player.y)

    @Test
    fun scenes() {
        assumeTrue(on())
        // New York at golden hour: down over Midtown toward the park
        run {
            val e = engine("nyc", 17.6f)
            val park = e.city.parks.maxByOrNull { com.hustlecity.game.world.Geo.area(it.poly) }!!.centre
            val (px, py) = park
            fly(e, "01_nyc", 6f, listOf(Key(px - 40f, py + 260f, 300f, 2.6f), Key(px - 10f, py + 200f, 200f, 3.0f), Key(px + 20f, py + 150f, 140f, 3.3f)))
        }
        // Manila mid-morning: along the street from the first apartment
        run {
            // Binondo: the church, the LRT overhead and jeepneys below
            val e = engine("manila", 10f)
            weather(e, Sky.CLEAR)
            val (x, y) = landmark(e, "binondo")
            fly(e, "02_manila", 6f, listOf(Key(x + 20f, y + 40f, 120f, -.3f), Key(x + 10f, y + 25f, 90f, .3f), Key(x, y + 10f, 70f, .9f)))
        }
        // Tokyo after dark: the Shibuya scramble and its signs
        run {
            val e = engine("tokyo", 20.2f)
            weather(e, Sky.CLEAR)
            val lm = e.city.landmarks.firstOrNull { it.name == "Shibuya Crossing" }
            val x = lm?.x ?: e.state.player.x; val y = lm?.y ?: e.state.player.y
            fly(e, "03_tokyo", 6f, listOf(Key(x + 40f, y - 60f, 130f, -.4f), Key(x + 10f, y - 20f, 85f, .2f), Key(x, y, 65f, .8f)))
        }
        // Tokyo Tower at dusk, the Yamanote line beyond
        run {
            val e = engine("tokyo", 17.8f)
            weather(e, Sky.CLEAR)
            val (x, y) = landmark(e, "tokyo tower")
            fly(e, "03b_tokyo_tower", 5f, listOf(Key(x, y + 60f, 190f, .2f), Key(x, y + 40f, 150f, .7f), Key(x, y + 20f, 125f, 1.2f)))
        }
        // Seoul at sunset: from Gwanghwamun over the hanok roofs of Bukchon
        run {
            val e = engine("seoul", 18.2f)
            weather(e, Sky.CLEAR)
            val (gx, gy) = landmark(e, "gwanghwamun")
            fly(e, "04_seoul", 6f, listOf(Key(gx, gy + 30f, 110f, .2f), Key(gx + 110f, gy - 10f, 85f, .9f), Key(820f, 110f, 65f, 1.6f)))
        }
        // the original city under snow
        run {
            val e = engine(Maps.CLASSIC, 11f)
            weather(e, Sky.SNOW)
            e.state.snowCover = 1.0
            e.skyVis.snow = .8f; e.skyVis.cloud = .8f
            val p = e.state.player
            fly(e, "05_classic_snow", 5f, listOf(Key(p.x, p.y, 130f, 0f), Key(p.x, p.y, 105f, .6f), Key(p.x, p.y, 90f, 1.1f)))
        }
        // downtown night skyline
        run {
            val e = engine(Maps.CLASSIC, 22f)
            val b = e.city.buildings.filter { it.neighborhood == 4 }.maxByOrNull { it.height }!!
            fly(e, "06_skyline", 5f, listOf(Key(b.cx, b.cy + 60f, 260f, -.6f), Key(b.cx, b.cy + 30f, 200f, .2f)))
        }
    }

    /** A busy supermarket of your own: full shelves, staff bringing boxes out of the back room, customers picking. */
    @Test
    fun storeInterior() {
        assumeTrue(on())
        val e = GameEngine(null)
        e.newGame("Alex Rivera", 1, 2, 1)
        e.ui.tip = null
        e.state.minute = 13L * 60 + 30
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 13..16 && it.gridD in 9..10 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "supermarket", "Corner Market")!!
        biz.tempClosed = false
        val room = biz.backRoom.coerceAtLeast(0); val y = room + 1; val w = b.gridW; val d = b.gridD
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
        biz.backStock["soda"] = 48 * 4; biz.backStock["snacks"] = 60 * 3; biz.backStock["frozen_food"] = 30 * 2
        for (role in listOf(StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE, StaffRole.CLEANER))
            e.state.employees += Employee(e.state.newId(), "Sam", role, .6, 15.0, 15.0, 30, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        e.state.player.inBuilding = b.id
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.interior = sc
        e.economy.servedRate[biz.id] = 160.0
        repeat(30 * 12) { sc.update(1f / fps, true) }
        // a box comes out of the back room during the shot
        biz.stock["snacks"] = (biz.stock["snacks"] ?: 0) - 70
        e.economy.restockFromBack(biz, 1)
        val dir = File("build/trailer/07_store").apply { deleteRecursively(); mkdirs() }
        val r = InteriorRenderer(e)
        val bmp = Bitmap.createBitmap(this.w, this.h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val yaw0 = sc.yaw
        val frames = 6 * fps
        for (f in 0 until frames) {
            sc.update(1f / fps, true)
            val t = ease(f / (frames - 1f))
            sc.yaw = yaw0 + .45f * t
            sc.zoom = 1f + .5f * t
            canvas.drawColor(0xFF000000.toInt())
            r.draw(canvas, this.w, this.h)
            File(dir, "%04d.jpg".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        }
    }
}

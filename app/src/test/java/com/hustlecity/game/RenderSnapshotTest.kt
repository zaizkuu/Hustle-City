package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the game world offscreen with real Skia graphics and writes PNGs to build/snapshots
 * so the visuals can be inspected without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderSnapshotTest {
    private val outDir = File("build/snapshots").apply { mkdirs() }
    private val w = 1600
    private val h = 760

    private fun engine(): GameEngine {
        val e = GameEngine(null)
        e.newGame("Tester", 1, 0, 1)
        return e
    }

    private fun save(bmp: Bitmap, name: String) {
        File(outDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderWorld(e: GameEngine, name: String) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // let traffic settle a bit
        repeat(30) { e.frame(1 / 30f) }
        WorldRenderer(e).draw(Canvas(bmp), w, h, 2f)
        save(bmp, name)
        // something other than the clear color must have been drawn
        val center = bmp.getPixel(w / 2, h / 2)
        assertTrue(center != 0)
    }

    @Test
    fun streetDay() {
        val e = engine()
        e.state.minute = 13L * 60
        renderWorld(e, "street_day.png")
    }

    @Test
    fun streetCloseAndNight() {
        val e = engine()
        e.camera.zoom = 40f; e.camera.dist = 40f
        e.state.minute = 22L * 60
        renderWorld(e, "street_night.png")
    }

    @Test
    fun midtownTowers() {
        val e = engine()
        val b = e.city.buildings.first { it.neighborhood == 4 && it.kind == BKind.OFFICE }
        e.state.player.x = b.doorX; e.state.player.y = b.doorY + 4f
        e.camera.tx = e.state.player.x; e.camera.ty = e.state.player.y
        e.camera.zoom = 110f; e.camera.dist = 110f
        e.state.minute = 17L * 60
        renderWorld(e, "midtown.png")
    }

    /** One close-up per architectural style, by day and by night, to check the buildings' personality. */
    @Test
    fun architectureGallery() {
        for (night in listOf(false, true)) for (st in com.hustlecity.game.render.Arch.entries) {
            val e = engine()
            e.ui.tip = null
            val painter = com.hustlecity.game.render.BuildingPainter(WorldRenderer(e), e)
            val b = e.city.buildings.filter { it.solid && painter.style(it) == st && it.placeId == null }
                .maxByOrNull { if (st == com.hustlecity.game.render.Arch.DECO || st == com.hustlecity.game.render.Arch.GLASS) it.height else it.width } ?: continue
            val far = (b.height * .7f).coerceIn(28f, 80f)
            e.state.player.x = b.doorX; e.state.player.y = b.doorY + (if (b.facesNorth) -6f else 6f)
            e.state.minute = (if (night) 21L else 15L) * 60
            repeat(20) {
                e.frame(1 / 30f)
                e.camera.tx = b.doorX; e.camera.ty = b.doorY; e.camera.zoom = far; e.camera.dist = far
                e.camera.yaw = if (b.facesNorth) Math.PI.toFloat() + .5f else .5f
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            WorldRenderer(e).draw(Canvas(bmp), w, h, 2f)
            save(bmp, "arch_${st.name.lowercase()}${if (night) "_night" else ""}.png")
        }
    }

    /** Every vehicle body style plus a taxi, close up, by day and by night. */
    @Test
    fun vehicleGallery() {
        for (night in listOf(false, true)) {
            val proj = com.hustlecity.game.render.Projector()
            val batch = com.hustlecity.game.render.RenderBatch()
            val scene = com.hustlecity.game.render.Scene3D(proj, batch)
            val painter = com.hustlecity.game.render.CarPainter(scene)
            proj.tx = 0f; proj.ty = 0f; proj.dist = 22f
            proj.pitch = Math.toRadians(40.0).toFloat(); proj.yaw = .6f; proj.near = .5f
            proj.update(w.toFloat(), h.toFloat())
            scene.applyDaylight(if (night) .2f else 1f)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(scene.lit(0xFF4A4D53.toInt(), 1f))
            batch.begin(canvas)
            val defs = listOf(Triple(0, 0xFFC0392B.toInt(), false), Triple(1, 0xFF2C3E50.toInt(), false), Triple(2, 0xFFF5F5F5.toInt(), false),
                Triple(3, 0xFFF2994A.toInt(), false), Triple(0, com.hustlecity.game.render.CarPainter.TAXI, true))
            for ((k, d) in defs.withIndex()) {
                val (style, color, taxi) = d
                val len = when (style) { 1 -> 5.2f; 2 -> 5.8f; 3 -> 4.4f; else -> 4.5f }
                val hgt = when (style) { 1 -> 1.8f; 2 -> 2.5f; 3 -> 1.2f; else -> 1.45f }
                val wid = if (style == 2) 2.1f else 1.85f
                painter.draw(-10f + k * 5f, if (k % 2 == 0) -2f else 2f, if (k % 2 == 0) 1.2f else 4.3f, len, wid, hgt, color, style, true, taxi, braking = k == 1)
            }
            batch.flush()
            save(bmp, if (night) "vehicles_night.png" else "vehicles.png")
        }
    }

    @Test
    fun mapMode() {
        val e = engine()
        e.ui.app = AppId.MAPS
        e.ui.mapFilters["hood:0"] = true
        e.ui.mapFilters["rent"] = true
        e.camera.mapX = e.state.player.x; e.camera.mapY = e.state.player.y; e.camera.mapDist = 420f
        renderWorld(e, "map.png")
    }

    @Test
    fun interiorGym() {
        val e = engine()
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area >= 90 }
        e.state.rentals += com.hustlecity.game.model.Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "gym", "Scarlet Gainz")!!
        biz.furniture += PlacedItem("treadmill", 1, 1, 0)
        biz.furniture += PlacedItem("treadmill", 3, 1, 0)
        biz.furniture += PlacedItem("workout_bench", 5, 1, 0)
        biz.furniture += PlacedItem("boxing_bag", 7, 1, 0)
        biz.furniture += PlacedItem("gym_locker", 1, 4, 1)
        biz.furniture += PlacedItem("shower", 8, 4, 0)
        biz.furniture += PlacedItem("drinks_fridge", 9, 1, 0)
        biz.furniture += PlacedItem("fitness_board", 6, 4, 0)
        biz.furniture += PlacedItem("plant_large", 0, 0, 0)
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.ui.designer = true
        e.interior!!.startPlacing("cash_register")
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        InteriorRenderer(e).draw(Canvas(bmp), w, h)
        save(bmp, "interior_gym.png")
    }

    @Test
    fun interiorStore() {
        val e = engine()
        val b = e.city.placeBuilding("essentials")!!
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.PLACE)
        repeat(60) { e.frame(1 / 20f) }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        InteriorRenderer(e).draw(Canvas(bmp), w, h)
        save(bmp, "interior_store.png")
    }
}

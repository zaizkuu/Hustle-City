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

package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.render.Arch
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

    /** Each city's starting street, close up and from higher up, to check its look. */
    @Test
    fun everyCityStreet() {
        for (map in com.hustlecity.game.world.Maps.all) {
            val e = GameEngine(null)
            e.newGame("Tester", 1, 0, 1, mapId = map.id)
            e.state.minute = 15L * 60
            e.camera.zoom = 46f; e.camera.dist = 46f
            renderWorld(e, "city_${map.id}_street.png")
            e.camera.zoom = 130f; e.camera.dist = 130f
            renderWorld(e, "city_${map.id}_wide.png")
        }
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
            e.state.player.x = b.doorX + b.frontX * 6f; e.state.player.y = b.doorY + b.frontY * 6f
            e.state.minute = (if (night) 21L else 15L) * 60
            repeat(20) {
                e.frame(1 / 30f)
                e.camera.tx = b.doorX; e.camera.ty = b.doorY; e.camera.zoom = far; e.camera.dist = far
                // look at the front: yaw 0 views a building whose front faces +y
                e.camera.yaw = kotlin.math.atan2(b.frontX, b.frontY) + .5f
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            WorldRenderer(e).draw(Canvas(bmp), w, h, 2f)
            save(bmp, "arch_${st.name.lowercase()}${if (night) "_night" else ""}.png")
        }
    }

    /** The Asian cities' own styles, close up on their home maps, by day and by night. */
    @Test
    fun regionalArchitecture() {
        val cases = listOf("manila" to Arch.COLONIAL, "manila" to Arch.TROPICAL, "tokyo" to Arch.PENCIL, "seoul" to Arch.PENCIL, "seoul" to Arch.HANOK)
        for (night in listOf(false, true)) for ((map, st) in cases) {
            val e = GameEngine(null)
            e.newGame("Tester", 1, 0, 1, mapId = map)
            e.ui.tip = null
            val painter = com.hustlecity.game.render.BuildingPainter(WorldRenderer(e), e)
            val all = e.city.buildings.filter { it.solid && painter.style(it) == st && it.placeId == null }
            assertTrue("$map has $st buildings", all.isNotEmpty())
            // a typical one: a lot of middling width
            val b = all.sortedBy { it.width }[all.size / 2]
            val far = (b.height * .8f).coerceIn(24f, 60f)
            e.state.player.x = b.doorX + b.frontX * 6f; e.state.player.y = b.doorY + b.frontY * 6f
            e.state.minute = (if (night) 21L else 15L) * 60
            repeat(20) {
                e.frame(1 / 30f)
                e.camera.tx = b.doorX; e.camera.ty = b.doorY; e.camera.zoom = far; e.camera.dist = far
                e.camera.yaw = kotlin.math.atan2(b.frontX, b.frontY) + .5f
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            WorldRenderer(e).draw(Canvas(bmp), w, h, 2f)
            save(bmp, "arch_${map}_${st.name.lowercase()}${if (night) "_night" else ""}.png")
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
        // the cities' own rides: jeepneys, tricycles, kei cars, buses and cabs in local colors; close up, at street
        // zoom, and far away (simple boxes) in the bottom row
        val c = { v: Long -> v.toInt() }
        val city = listOf(
            Ride(4, c(0xFFD32F2F), c(0xFFF2C230)), Ride(4, c(0xFFC9CED3), c(0xFF1E5BC6)), Ride(5, c(0xFF1E5BC6), c(0xFFF5F5F5)),
            Ride(5, c(0xFFF2C230), c(0xFF1E5BC6)), Ride(6, c(0xFFBFE3D0), c(0xFFF7F7F5)), Ride(6, c(0xFFF5F5F2), 0),
            Ride(7, c(0xFFF4F5F2), c(0xFF1C5DAA)), Ride(7, c(0xFF1F6FD1), c(0xFFF4F5F2)), Ride(0, c(0xFF1C1D20), 0, true), Ride(0, c(0xFFF0812E), 0, true),
        )
        for ((name, dist, night) in listOf(Triple("vehicles_city", 30f, false), Triple("vehicles_city_night", 30f, true), Triple("vehicles_city_street", 62f, false))) {
            val proj = com.hustlecity.game.render.Projector()
            val batch = com.hustlecity.game.render.RenderBatch()
            val scene = com.hustlecity.game.render.Scene3D(proj, batch)
            val painter = com.hustlecity.game.render.CarPainter(scene)
            val rows = if (dist > 40f) 2 else 1
            proj.tx = 0f; proj.ty = if (rows == 2) 6f else 0f; proj.dist = dist
            proj.pitch = Math.toRadians(if (dist > 40f) 58.0 else 40.0).toFloat(); proj.yaw = .25f; proj.near = .5f
            proj.update(w.toFloat(), h.toFloat())
            scene.applyDaylight(if (night) .2f else 1f)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(scene.lit(0xFF4A4D53.toInt(), 1f))
            batch.begin(canvas)
            // columns: jeepneys, tricycles, kei cars, buses, cabs; the back line first, its partner in front turned round
            val colX = floatArrayOf(-19f, -11.5f, -5.5f, 4f, 14f)
            for (row in 0 until rows) for (line in 0..1) for ((k, v) in city.withIndex()) {
                if (k % 2 != line) continue
                val (len, wid, hgt) = when (v.style) { 4 -> Triple(6.2f, 2.05f, 2.3f); 5 -> Triple(2.5f, 1.5f, 1.75f); 6 -> Triple(3.4f, 1.48f, 1.7f); 7 -> Triple(11f, 2.5f, 3.1f); else -> Triple(4.5f, 1.85f, 1.45f) }
                val y = (if (line == 0) -4f else 4f) + row * 14f
                painter.draw(colX[k / 2], y, if (line == 0) 1.3f else 4.45f, len, wid, hgt, v.color, v.style, row == 0, v.taxi, braking = k == 3, accent = v.accent)
            }
            batch.flush()
            save(bmp, "$name.png")
        }
    }

    private data class Ride(val style: Int, val color: Int, val accent: Int, val taxi: Boolean = false)

    /** Every tree kind (broadleaf, park broadleaf, conifer, cherry, palm, ginkgo) through the year, up close and far. */
    @Test
    fun treeGallery() {
        for ((name, month) in listOf("summer" to 6, "autumn" to 9, "november" to 10, "winter" to 0, "spring" to 3)) {
            val proj = com.hustlecity.game.render.Projector()
            val batch = com.hustlecity.game.render.RenderBatch()
            val scene = com.hustlecity.game.render.Scene3D(proj, batch)
            val painter = com.hustlecity.game.render.TreePainter(scene)
            proj.tx = 0f; proj.ty = 3f; proj.dist = 36f
            proj.pitch = Math.toRadians(42.0).toFloat(); proj.yaw = .35f; proj.near = .5f
            proj.update(w.toFloat(), h.toFloat())
            scene.applyDaylight(1f)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(scene.lit(0xFFB4B8BD.toInt(), 1f))
            batch.begin(canvas)
            val shade = scene.withAlpha(0xFF000000.toInt(), .22f)
            // the near row in front, the far versions behind; shadows first
            for (pass in 0..1) for (row in 1 downTo 0) for (kind in 0..5) {
                val x = -15f + kind * 6f; val y = if (row == 0) 6f else -6f
                val i = kind * 7 + row
                if (pass == 0) painter.shadow(x, y, 2f, 5.5f, kind, i, -.45f * .9f, -.89f * .9f, shade)
                else painter.draw(x, y, 2f, 5.5f, kind % 3, kind, month, i, row == 0)
            }
            batch.flush()
            save(bmp, "trees_$name.png")
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

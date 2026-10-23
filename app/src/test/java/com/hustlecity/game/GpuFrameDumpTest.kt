package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.render.gpu.Shaders
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds real GPU frames with the game's own code and dumps them (geometry, uniforms, the Canvas overlay layer and the
 * shaders) to build/glframes/<name>/ so tools/glview can render them with WebGL2 exactly as a phone would.
 * Also writes the classic Canvas render of the same view for comparison.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GpuFrameDumpTest {
    private val w = 1600
    private val h = 760
    private val root = File("build/glframes").apply { mkdirs() }

    private fun engine(): GameEngine {
        // HD frames draw the furniture models
        com.hustlecity.game.render.Models.assets = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets
        return GameEngine(null).also { it.newGame("Tester", 1, 0, 1); it.ui.tip = null }
    }

    private fun dump(name: String, frame: GpuFrame, overlay: Bitmap, canvasRender: Bitmap) {
        val dir = File(root, name).apply { mkdirs() }
        File(root, "world.vs").writeText(Shaders.WORLD_VS); File(root, "world.fs").writeText(Shaders.WORLD_FS)
        File(root, "world_fast.fs").writeText(Shaders.worldFs(true))
        File(root, "sky.vs").writeText(Shaders.SKY_VS); File(root, "sky.fs").writeText(Shaders.SKY_FS)
        File(root, "shadow.vs").writeText(Shaders.SHADOW_VS); File(root, "shadow.fs").writeText(Shaders.SHADOW_FS)
        // the cached layer draws first (minus skipped ranges), then the frame's own geometry, as on the GL thread
        val st = frame.staticGeom
        for (i in 0 until 3) {
            val s = frame.streams[i]
            val sv = st?.streams?.get(i)
            val nvS = sv?.nv ?: 0
            val verts = IntArray((nvS + s.nv) * GpuFrame.STRIDE)
            if (sv != null) System.arraycopy(sv.v, 0, verts, 0, nvS * GpuFrame.STRIDE)
            System.arraycopy(s.v, 0, verts, nvS * GpuFrame.STRIDE, s.nv * GpuFrame.STRIDE)
            val idx = ArrayList<Int>()
            fun staticRange(a: Int, b: Int) { for (k in a until b) idx += sv!!.idx[k] }
            val dynamic = { for (k in 0 until s.ni) idx += s.idx[k] + nvS }
            if (sv != null) {
                if (i == GpuFrame.OPAQUE) {
                    dynamic()
                    var from = 0
                    for (k in 0 until frame.skipCount) { staticRange(from, frame.skip[k * 2]); from = frame.skip[k * 2 + 1] }
                    staticRange(from, sv.ni)
                } else { staticRange(0, sv.ni); dynamic() }
            } else dynamic()
            writeInts(File(dir, "v$i.bin"), verts, verts.size)
            writeInts(File(dir, "i$i.bin"), idx.toIntArray(), idx.size)
        }
        fun a(x: FloatArray) = x.joinToString(",", "[", "]")
        File(dir, "frame.json").writeText("""{"width":${frame.width},"height":${frame.height},"cam":${a(frame.cam)},"right":${a(frame.right)},"up":${a(frame.up)},"fwd":${a(frame.fwd)},
"focal":${frame.focal},"centerX":${frame.centerX},"centerY":${frame.centerY},"near":${frame.near},"far":${frame.far},
"sunDir":${a(frame.sunDir)},"sunColor":${a(frame.sunColor)},"skyAmbient":${a(frame.skyAmbient)},"groundAmbient":${a(frame.groundAmbient)},
"fogColor":${a(frame.fogColor)},"fog":[${frame.fogStart},${frame.fogEnd},${frame.fogAmount}],"skyTop":${a(frame.skyTop)},"skyHorizon":${a(frame.skyHorizon)},
"misc":[${frame.daylight},${frame.wet},${frame.exposure},${frame.mode}],"clearColor":${frame.clearColor},"time":${frame.time},
"light":${a(frame.lightMat)},"shadow":[${frame.shadowStrength},${frame.shadowOffset},${frame.shadowTexel},${if (frame.shadowOn) 1 else 0}],"shadowSize":${frame.shadowSize},
"counts":[${frame.streams.joinToString(",") { "[${it.nv},${it.ni}]" }}],"triangles":${frame.triangleCount + (frame.staticGeom?.triangleCount ?: 0)}}""")
        overlay.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(File(dir, "overlay.png")))
        canvasRender.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(File(dir, "canvas.png")))
        assertTrue("$name produced no geometry", frame.triangleCount + (frame.staticGeom?.triangleCount ?: 0) > 0)
    }

    private fun writeInts(f: File, data: IntArray, n: Int) {
        val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asIntBuffer().put(data, 0, n)
        DataOutputStream(FileOutputStream(f)).use { it.write(bb.array()) }
    }

    private fun city(name: String, e: GameEngine) {
        repeat(30) { e.frame(1 / 30f) }
        val r = WorldRenderer(e)
        val canvasBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(canvasBmp), w, h, 2f)
        val frame = GpuFrame()
        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(overlay), w, h, 2f, frame)
        dump(name, frame, overlay, canvasBmp)
    }

    /** Each city's starting street in the HD renderer, plus Tokyo and Seoul after dark. */
    @Test
    fun cities() {
        for ((id, hour) in listOf("nyc" to 15, "manila" to 15, "tokyo" to 15, "seoul" to 15, "tokyo" to 21, "seoul" to 21)) {
            val e = GameEngine(null).also { it.newGame("Tester", 1, 0, 1, mapId = id); it.ui.tip = null }
            e.state.minute = hour * 60L
            e.camera.zoom = 75f; e.camera.dist = 75f; e.camera.yaw = .4f
            city("city_${id}_$hour", e)
        }
    }

    @Test
    fun streetDay() { val e = engine(); e.state.minute = 13L * 60; e.camera.zoom = 60f; e.camera.dist = 60f; city("street_day", e) }

    @Test
    fun streetDusk() { val e = engine(); e.state.minute = 18L * 60 + 20; e.camera.zoom = 90f; e.camera.dist = 90f; e.camera.yaw = .6f; city("street_dusk", e) }

    @Test
    fun streetNight() { val e = engine(); e.state.minute = 22L * 60; e.camera.zoom = 45f; e.camera.dist = 45f; city("street_night", e) }

    @Test
    fun midtown() {
        val e = engine()
        val b = e.city.buildings.first { it.neighborhood == 4 && it.kind == BKind.OFFICE }
        e.state.player.x = b.doorX; e.state.player.y = b.doorY + 4f
        e.camera.tx = e.state.player.x; e.camera.ty = e.state.player.y
        e.camera.zoom = 120f; e.camera.dist = 120f
        e.state.minute = 16L * 60
        city("midtown", e)
    }

    @Test
    fun rain() {
        val e = engine()
        e.state.weather.clear()
        e.state.weather += com.hustlecity.game.model.WeatherSpan(e.state.minute - 60, e.state.minute + 3 * 1440L, com.hustlecity.game.model.Sky.RAIN)
        e.weather.ensure()
        e.skyVis.rain = .75f; e.skyVis.cloud = .9f; e.skyVis.wet = 1f
        e.state.minute = 11L * 60
        e.camera.zoom = 50f; e.camera.dist = 50f
        city("rain", e)
    }

    @Test
    fun snow() {
        val e = engine()
        e.state.snowCover = 1.0
        e.skyVis.snow = .8f; e.skyVis.cloud = .7f
        e.state.minute = 12L * 60
        e.camera.zoom = 70f; e.camera.dist = 70f
        city("snow", e)
    }

    /**
     * Every furniture model on a showroom floor: people sit on the seats (their seat heights come from the catalog),
     * the sofa turns through its four quarter turns, and the box model of each stands beside it for comparison.
     */
    @Test
    fun models() {
        com.hustlecity.game.render.Models.assets = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets
        com.hustlecity.game.render.Models.clear()
        val ids = com.hustlecity.game.render.Models.ids().sorted()
        assertTrue("models are packaged", ids.size >= 20)
        val cols = 6
        val rows = (ids.size + cols - 1) / cols
        // a shot of the whole floor, then one per row up close
        modelShot("models", ids, cols * 4f / 2, rows * 3.5f / 2 + 2f, 30f)
        for (r in 0 until rows) modelShot("models_row$r", ids, cols * 4f / 2 - 1f, r * 3.5f + .3f, 13f)
        modelShot("models_turns", ids, 6f, rows * 3.5f + 2f, 11f)
    }

    private fun modelShot(name: String, ids: List<String>, tx: Float, ty: Float, dist: Float) {
        val cols = 6
        val rows = (ids.size + cols - 1) / cols
        val proj = com.hustlecity.game.render.Projector()
        proj.tx = tx; proj.ty = ty; proj.dist = dist; proj.pitch = Math.toRadians(42.0).toFloat(); proj.yaw = .25f; proj.near = .3f
        proj.update(w.toFloat(), h.toFloat(), .5f)
        val frame = GpuFrame()
        frame.setCamera(proj)
        frame.mode = GpuFrame.MODE_INTERIOR
        com.hustlecity.game.render.gpu.GpuLighting.interior(frame, 0xFF16181D.toInt())
        frame.setShadow(-2f, -2f, cols * 4f + 2f, rows * 3.5f + 6f, 3f, 2048, .5f)
        val scene = com.hustlecity.game.render.Scene3D(proj, com.hustlecity.game.render.RenderBatch())
        scene.gpu = frame
        scene.material = com.hustlecity.game.render.Mat.TILE
        scene.groundRect(-2f, -2f, cols * 4f + 2f, rows * 3.5f + 6f, 0f, 0xFFD9D4CC.toInt())
        scene.material = com.hustlecity.game.render.Mat.PLAIN
        val parts = com.hustlecity.game.render.PartSorter(scene)
        val people = com.hustlecity.game.render.PersonPainter(scene)
        for ((k, id) in ids.withIndex()) {
            val def = com.hustlecity.game.data.Catalog.item(id) ?: continue
            val x = (k % cols) * 4f; val y = (k / cols) * 3.5f
            val m = com.hustlecity.game.render.Models.get(id)!!
            assertTrue("$id fits its ${def.w}x${def.d} footprint", m.bounds[0] > -.05f && m.bounds[1] > -.05f && m.bounds[3] < def.w + .05f && m.bounds[4] < def.d + .05f)
            scene.mesh(m, x, y, 0, def.w.toFloat(), def.d.toFloat())
            // the box model it replaces, behind it
            parts.draw(def, x, y - def.d - .3f, 0, .55f)
            for (spot in com.hustlecity.game.data.Catalog.sitSpots[id].orEmpty()) {
                val face = when (spot.face) { 0 -> 0f; 1 -> 1.5708f; 2 -> 3.14159f; else -> -1.5708f }
                people.draw(x + spot.x, y + spot.y, spot.z, face, 0f, 0f, 0xFF2F80ED.toInt(), com.hustlecity.game.render.PersonPainter.PANTS[1], 0xFFE0AC69.toInt(),
                    com.hustlecity.game.render.PersonPainter.HAIR[0], pose = if (spot.pose == 3) com.hustlecity.game.render.Pose.RUN else com.hustlecity.game.render.Pose.SIT, look = 11 + k)
            }
        }
        // the sofa through its four quarter turns
        val sofa = com.hustlecity.game.render.Models.get("sofa")!!
        for (r in 0..3) scene.mesh(sofa, r * 3.5f, rows * 3.5f + 1.5f, r, 2f, 1f)
        val blank = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dump(name, frame, blank, blank)
    }

    /** Close-up of the HD people: looks, outfits, poses and the things they carry. */
    @Test
    fun people() {
        peopleShot("people", 0f, 0f, 7.5f, 28.0)
        peopleShot("people_faces", .3f, -.2f, 3.4f, 30.0, .97f)
    }

    private fun peopleShot(name: String, tx: Float, ty: Float, dist: Float, pitch: Double, center: Float = .55f) {
        val proj = com.hustlecity.game.render.Projector()
        proj.tx = tx; proj.ty = ty; proj.dist = dist; proj.pitch = Math.toRadians(pitch).toFloat(); proj.yaw = .35f; proj.near = .3f
        proj.update(w.toFloat(), h.toFloat(), center)
        val frame = GpuFrame()
        frame.setCamera(proj)
        frame.setShadow(-12f, -12f, 12f, 12f, 3f, 2048, .6f)
        val scene = com.hustlecity.game.render.Scene3D(proj, com.hustlecity.game.render.RenderBatch())
        scene.gpu = frame
        scene.material = com.hustlecity.game.render.Mat.SIDEWALK
        scene.groundRect(-14f, -14f, 14f, 14f, 0f, 0xFFB9BCC1.toInt())
        scene.material = com.hustlecity.game.render.Mat.PLAIN
        val p = com.hustlecity.game.render.PersonPainter(scene)
        val shirts = intArrayOf(0xFF2F80ED.toInt(), 0xFFEB5757.toInt(), 0xFF27AE60.toInt(), 0xFFF2C94C.toInt(), 0xFF9B51E0.toInt(), 0xFFF5F5F5.toInt(), 0xFF333333.toInt(), 0xFFFF8A65.toInt())
        val skins = com.hustlecity.game.engine.SKIN_TONES
        // two rows of walkers in different looks, then the poses
        for (i in 0 until 8) {
            val x = -3.5f + i * 1.0f
            p.draw(x, -1.2f, 0f, 3.14159f + .3f, i * .8f, 1f, shirts[i], com.hustlecity.game.render.PersonPainter.PANTS[i % 7], skins[i % skins.size],
                com.hustlecity.game.render.PersonPainter.HAIR[i % 7], look = 101 + i * 7919)
            p.draw(x + .5f, 0f, 0f, 3.14159f - .2f, i * 1.1f + 2f, .3f, shirts[(i + 3) % 8], com.hustlecity.game.render.PersonPainter.PANTS[(i + 2) % 7], skins[(i + 2) % skins.size],
                com.hustlecity.game.render.PersonPainter.HAIR[(i + 3) % 7], look = 5003 + i * 104729, warm = i % 2 == 0)
        }
        p.draw(-3f, 1.6f, .45f, 3.14159f, 0f, 0f, shirts[2], com.hustlecity.game.render.PersonPainter.PANTS[0], skins[1], com.hustlecity.game.render.PersonPainter.HAIR[4], pose = com.hustlecity.game.render.Pose.SIT, look = 77)
        p.draw(-1.6f, 1.6f, 0f, 3.14159f, 2.5f, 0f, shirts[4], com.hustlecity.game.render.PersonPainter.PANTS[3], skins[3], com.hustlecity.game.render.PersonPainter.HAIR[2], pose = com.hustlecity.game.render.Pose.DANCE, look = 91)
        p.draw(-.2f, 1.6f, 0f, 3.14159f + .5f, 1.2f, 1f, shirts[1], com.hustlecity.game.render.PersonPainter.PANTS[1], skins[2], com.hustlecity.game.render.PersonPainter.HAIR[1], umbrella = 0xFFF2C94C.toInt(), look = 1234)
        p.draw(1.2f, 1.6f, 0f, 3.14159f - .4f, 0f, 0f, shirts[0], com.hustlecity.game.render.PersonPainter.PANTS[2], skins[0], com.hustlecity.game.render.PersonPainter.HAIR[0], activity = 1, look = 4321)
        p.draw(2.6f, 1.6f, 0f, 3.14159f - 1.2f, 1f, 1f, shirts[5], com.hustlecity.game.render.PersonPainter.PANTS[1], skins[4 % skins.size], com.hustlecity.game.render.PersonPainter.HAIR[3], carry = 2, load = 3, look = 555)
        p.draw(4.2f, 1.6f, 0f, 3.14159f, 2f, 1f, shirts[6], com.hustlecity.game.render.PersonPainter.PANTS[0], skins[1], com.hustlecity.game.render.PersonPainter.HAIR[0], pose = com.hustlecity.game.render.Pose.RUN, look = 999)
        val blank = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dump(name, frame, blank, blank)
    }

    /** A store of each kind with a few pieces of furniture in the middle, so the walls show their dressing. */
    @Test
    fun interiors() {
        for (type in listOf("bookstore", "liquor", "electronics", "clothing", "fastfood", "coffee", "gym", "florist", "lawfirm")) {
            val e = engine()
            e.state.player.courses += com.hustlecity.game.data.Catalog.COURSE_IMPORT
            val t = com.hustlecity.game.data.Catalog.type(type)
            val kind = if (t.office) BKind.OFFICE else BKind.RETAIL
            val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == kind && it.area in (if (t.office) 150..500 else 110..220) }
            e.state.rentals += Rental(b.id, b.baseRent, 1)
            val biz = e.startBusiness(b.id, type, "Test")!!
            biz.backRoom = 0
            var x = 3
            for (req in t.requirements + t.products.mapNotNull { com.hustlecity.game.data.Catalog.product(it.first)?.tag?.let { tag -> com.hustlecity.game.data.Req(tag, tag) } }) {
                val id = com.hustlecity.game.engine.itemForTag(req.tag)
                if (biz.furniture.any { it.itemId == id }) continue
                biz.furniture += PlacedItem(id, x, 3, 0)
                x += (com.hustlecity.game.data.Catalog.item(id)?.w ?: 1) + 1
            }
            // seating and extras where the trade has them
            val extras = when (type) {
                "coffee" -> listOf(Triple("table_set", 3, 6), Triple("table_set", 7, 6), Triple("plant_large", 11, 6), Triple("trash_can", 1, 6))
                "fastfood" -> listOf(Triple("table_set", 3, 6), Triple("table_set", 7, 6), Triple("trash_can", 1, 6))
                "lawfirm" -> listOf(Triple("meeting_table", 3, 6), Triple("filing_cabinet", 8, 6), Triple("plant_large", 9, 6), Triple("lounge_sofa", 11, 6))
                "clothing", "electronics", "liquor" -> listOf(Triple("shopping_baskets", 1, 6), Triple("trash_can", 2, 6), Triple("plant_large", 9, 6))
                else -> listOf(Triple("plant_large", 9, 6))
            }
            for ((id, ex, ey) in extras) biz.furniture += PlacedItem(id, ex, ey, 0)
            for ((pid, _) in t.products) if (com.hustlecity.game.data.Catalog.product(pid)?.service == false) biz.stock[pid] = 40
            biz.tempClosed = false
            e.state.minute = 13L * 60
            e.state.player.inBuilding = b.id
            e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
            repeat(120) { e.frame(1 / 30f) }
            val r = InteriorRenderer(e)
            val frame = GpuFrame()
            val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            r.draw(Canvas(overlay), w, h, frame)
            dump("interior_$type", frame, overlay, overlay)
        }
        // and a home, furnished
        val e = engine()
        for ((id, hx, hy, rot) in listOf(listOf<Any>("sofa", 2, 2, 0), listOf<Any>("tv", 2, 5, 2), listOf<Any>("rug", 2, 3, 0), listOf<Any>("floor_lamp", 5, 3, 0), listOf<Any>("plant_large", 6, 0, 0)))
            e.state.homeFurniture += PlacedItem(id as String, hx as Int, hy as Int, rot as Int)
        e.state.player.inBuilding = e.state.player.home
        e.interior = InteriorScene(e, e.city.building(e.state.player.home)!!, InteriorMode.HOME)
        repeat(30) { e.frame(1 / 30f) }
        val r = InteriorRenderer(e)
        val frame = GpuFrame()
        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(overlay), w, h, frame)
        dump("interior_home", frame, overlay, overlay)
    }

    @Test
    fun interior() {
        val e = engine()
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 90..160 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "giftshop", "Gift Corner")!!
        biz.furniture += PlacedItem("cash_register", 1, 1, 0)
        for (x in 0..3) biz.furniture += PlacedItem("shelf", 1 + x * 3, 4, 0)
        biz.furniture += PlacedItem("plant_large", 0, 0, 0)
        biz.furniture += PlacedItem("painting", 4, 0, 0)
        biz.furniture += PlacedItem("shopping_baskets", 4, 1, 0)
        biz.furniture += PlacedItem("trash_can", 5, 1, 0)
        biz.furniture += PlacedItem("cart_rack", 1, 7, 0)
        biz.furniture += PlacedItem("lounge_sofa", 9, 7, 2)
        biz.stock["gift_cheap"] = 100
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
        repeat(60) { e.frame(1 / 30f) }
        val r = InteriorRenderer(e)
        val canvasBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(canvasBmp), w, h)
        val frame = GpuFrame()
        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(overlay), w, h, frame)
        dump("interior", frame, overlay, canvasBmp)
    }
}

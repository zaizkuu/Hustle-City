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

    private fun engine(mapId: String? = null): GameEngine {
        // HD frames draw the models (furniture, products, street props)
        com.hustlecity.game.render.Models.assets = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets
        return GameEngine(null).also { if (mapId == null) it.newGame("Tester", 1, 0, 1) else it.newGame("Tester", 1, 0, 1, mapId = mapId); it.ui.tip = null }
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

    private fun city(name: String, e: GameEngine, pitch: Float? = null, warm: Int = 30) {
        repeat(warm) { e.frame(1 / 30f) }
        val r = WorldRenderer(e)
        if (pitch != null) r.cinemaPitch = Math.toRadians(pitch.toDouble()).toFloat()
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
            val e = engine(id)
            e.state.minute = hour * 60L
            e.camera.zoom = 75f; e.camera.dist = 75f; e.camera.yaw = .4f
            city("city_${id}_$hour", e)
        }
    }

    /** Close-ups of each kind of modelled street furniture where it stands, by day, and a street corner at night. */
    @Test
    fun streetProps() {
        val e0 = engine("nyc")
        val pr = com.hustlecity.game.render.StreetProps(e0.city)
        val kinds = listOf(com.hustlecity.game.render.StreetProps.BUS_STOP to "bus_stop", com.hustlecity.game.render.StreetProps.NEWS to "news",
            com.hustlecity.game.render.StreetProps.MAILBOX to "mailbox", com.hustlecity.game.render.StreetProps.BIKE_RACK to "bike_rack",
            com.hustlecity.game.render.StreetProps.HYDRANT to "hydrant", com.hustlecity.game.render.StreetProps.BENCH to "bench",
            com.hustlecity.game.render.StreetProps.PARK_LAMP to "park_lamp")
        for ((kind, name) in kinds) {
            val i = (0 until pr.count).firstOrNull { pr.kind[it] == kind } ?: continue
            val e = engine("nyc")
            // stand back from it on the pavement, looking at it across the street's edge
            e.state.player.x = pr.x[i] - pr.dirX[i] * 1.5f + pr.dirY[i] * 2f; e.state.player.y = pr.y[i] - pr.dirY[i] * 1.5f - pr.dirX[i] * 2f
            e.camera.tx = pr.x[i]; e.camera.ty = pr.y[i]
            e.camera.zoom = 7f; e.camera.dist = 7f; e.camera.yaw = .5f
            e.state.minute = 14L * 60
            city("prop_$name", e)
        }
        // night: lamp lenses, signals and the light they throw
        val e = engine("nyc")
        e.state.minute = 21L * 60 + 30
        e.camera.zoom = 32f; e.camera.dist = 32f; e.camera.yaw = .3f
        city("props_night", e)
    }

    /** Close-ups of what's still drawn from shapes: vendors, vending machines, power poles, lanterns, bunting, fountains, hoops. */
    @Test
    fun streetShapes() {
        if (System.getenv("HC_SHAPES") != "1") return
        val SP = com.hustlecity.game.render.StreetProps
        for ((map, kind, name) in listOf(Triple("manila", SP.VENDOR, "vendor"), Triple("manila", SP.UTIL_POLE, "util_pole"), Triple("tokyo", SP.VENDING, "vending"),
            Triple("tokyo", SP.LANTERNS, "lanterns"), Triple("manila", SP.BUNTING, "bunting"), Triple("classic", SP.FOUNTAIN, "fountain"), Triple("manila", SP.HOOP, "hoop"))) {
            val e0 = engine(map)
            val pr = SP.of(e0.city)
            // one near the middle of the city
            val b = e0.city.bounds
            val i = (0 until pr.count).filter { pr.kind[it] == kind }.minByOrNull { kotlin.math.hypot(pr.x[it] - (b[0] + b[2]) / 2, pr.y[it] - (b[1] + b[3]) / 2) } ?: continue
            val e = engine(map)
            e.state.player.x = pr.x[i] - pr.dirX[i] * 1.5f + pr.dirY[i] * 2f; e.state.player.y = pr.y[i] - pr.dirY[i] * 1.5f - pr.dirX[i] * 2f
            e.camera.tx = pr.x[i]; e.camera.ty = pr.y[i]
            // a power pole is tall: from further back and lower down
            val d = if (kind == SP.UTIL_POLE) 17f else 9f
            e.camera.zoom = d; e.camera.dist = d; e.camera.yaw = look(pr.dirX[i], pr.dirY[i]) + .6f
            e.state.minute = (System.getenv("HC_SHAPES_HOUR")?.toLong() ?: 14L) * 60
            city("shape_$name" + (System.getenv("HC_SHAPES_HOUR")?.let { "_$it" } ?: ""), e, if (kind == SP.UTIL_POLE) 9f else 24f)
            if (name == "vending") {
                val f = engine(map)
                f.state.player.x = e.state.player.x; f.state.player.y = e.state.player.y; f.camera.tx = pr.x[i]; f.camera.ty = pr.y[i]
                f.camera.zoom = 40f; f.camera.dist = 40f; f.camera.yaw = .5f; f.state.minute = 14L * 60
                city("shape_vending_wide", f)
                println("vending at ${pr.x[i]},${pr.y[i]} player ${e.state.player.x},${e.state.player.y} inBuilding ${e.state.player.inBuilding}")
            }
        }
    }

    /** Traffic and parked cars from the street, by day and night. Runs with HC_CARS=1; frames go to build/glframes/cars_<city>_<hour>. */
    @Test
    fun cars() {
        if (System.getenv("HC_CARS") != "1") return
        for ((map, hour) in listOf("nyc" to 14, "nyc" to 21, "manila" to 15, "tokyo" to 13, "classic" to 16)) {
            val e = engine(map)
            e.settings.quality = 2; e.settings.busyRoads = 3f
            e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
            val b = e.city.bounds
            // a wide street near the middle of the city, seen from its sidewalk
            val ed = e.city.roads.edges.filter { !it.overWater && it.len > 50f }.minByOrNull {
                kotlin.math.hypot((it.ax + it.bx) / 2 - (b[0] + b[2]) / 2, (it.ay + it.by) / 2 - (b[1] + b[3]) / 2) - it.cls.halfWidth * 8f
            }!!
            val mx = (ed.ax + ed.bx) / 2; val my = (ed.ay + ed.by) / 2
            e.state.player.x = mx + ed.uy * (ed.cls.halfWidth + 1.5f); e.state.player.y = my - ed.ux * (ed.cls.halfWidth + 1.5f)
            e.camera.tx = mx; e.camera.ty = my
            e.camera.zoom = 15f; e.camera.dist = 15f; e.camera.yaw = look(-ed.uy, ed.ux) + .5f
            e.state.minute = hour * 60L
            e.traffic.update(0f, mx, my, 230f, -9999f, -9999f, initial = true)
            repeat(90) { e.frame(1 / 30f) }
            city("cars_${map}_$hour", e, 16f)
        }
    }

    /** One of each car model in a row along the curb (a taxi, a sedan, a hatchback, a pickup, a van, a sports car, an SUV, a box truck), by day and night. */
    @Test
    fun carLineup() {
        if (System.getenv("HC_CARS") != "1") return
        for (hour in listOf(14, 21)) {
            val e = engine("nyc")
            e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
            val b = e.city.bounds
            val ed = e.city.roads.edges.filter { !it.overWater && it.len > 90f && it.cls.lanes >= 2 }
                .minByOrNull { kotlin.math.hypot((it.ax + it.bx) / 2 - (b[0] + b[2]) / 2, (it.ay + it.by) / 2 - (b[1] + b[3]) / 2) }!!
            e.traffic.update(0f, (ed.ax + ed.bx) / 2, (ed.ay + ed.by) / 2, 230f, -9999f, -9999f, initial = true)
            for (c in e.traffic.cars) c.placed = false
            // every few sedans is a cab (car 0 is): the plain sedan and the hatchback go where none is
            val every = e.city.map.taxiEvery
            val plain = (1 until 40).filter { it % every != 0 }
            val row = listOf(Triple(0, 0, 4.6f), Triple(plain[0], 0, 4.7f), Triple(plain[1], 0, 4.2f), Triple(plain[2], 1, 0f), Triple(plain[3], 2, 0f),
                Triple(plain[4], 3, 0f), Triple(plain[5], 9, 0f), Triple(plain[6], 10, 0f))
            for ((slot, car) in row.withIndex()) {
                val (k, style, len) = car
                e.traffic.placeCar(k, ed.id, ed.a, 19f + slot * 8.4f, style)
                val a = e.traffic.cars[k]
                if (len > 0f) a.length = len
                a.speed = 0f
            }
            val mx = ed.ax + ed.ux * 43f; val my = ed.ay + ed.uy * 43f
            val side = ed.cls.halfWidth + 2f
            e.state.player.x = mx - ed.uy * side; e.state.player.y = my + ed.ux * side
            e.camera.tx = mx; e.camera.ty = my
            e.camera.zoom = 34f; e.camera.dist = 34f; e.camera.yaw = look(ed.uy, -ed.ux) + .35f
            e.state.minute = hour * 60L
            // HC_CARS_OLD=1: the same with the cars as they were drawn before their models (everything as shapes)
            val old = System.getenv("HC_CARS_OLD") == "1"
            if (old) com.hustlecity.game.render.Models.enabled = false
            try { city("lineup_$hour" + (if (old) "_old" else ""), e, 14f, warm = 0) } finally { com.hustlecity.game.render.Models.enabled = true }
        }
    }

    /**
     * Each city's own vehicles in a row along the curb, in their liveries, by day and night: Manila's jeepneys, modern
     * jeepneys, tricycles, buses and bikes (traffic keeps right), Tokyo's kei cars, buses and bikes (it keeps left).
     */
    @Test
    fun cityVehicles() {
        if (System.getenv("HC_CARS") != "1") return
        val lengths = mapOf(4 to 6.2f, 5 to 2.5f, 6 to 3.4f, 7 to 11f, 8 to 1.9f, 11 to 7f, 12 to 1.8f)
        for ((map, styles) in listOf("manila" to listOf(4, 11, 5, 6, 7, 8, 12, 12, 5, 4), "tokyo" to listOf(6, 7, 8, 12, 6, 12, 6)))
            for (hour in listOf(14, 21)) {
                val e = engine(map)
                e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
                val b = e.city.bounds
                // a street out in the open: not under an elevated railway
                fun clear(x: Float, y: Float) = e.city.rails.all { r -> (0 until r.pts.size / 2).all { kotlin.math.hypot(r.pts[it * 2] - x, r.pts[it * 2 + 1] - y) > 60f } }
                val ed = e.city.roads.edges.filter { !it.overWater && it.len > 90f && it.cls.lanes >= 2 && clear(it.ax, it.ay) && clear(it.bx, it.by) }
                    .minByOrNull { kotlin.math.hypot((it.ax + it.bx) / 2 - (b[0] + b[2]) / 2, (it.ay + it.by) / 2 - (b[1] + b[3]) / 2) }!!
                e.traffic.update(0f, (ed.ax + ed.bx) / 2, (ed.ay + ed.by) / 2, 230f, -9999f, -9999f, initial = true)
                for (c in e.traffic.cars) c.placed = false
                // in the lane nearest the camera
                val from = if (e.city.leftHand) ed.a else ed.b
                var at = 14f
                for ((k, style) in styles.withIndex()) {
                    val len = lengths.getValue(style)
                    val s = at + len / 2
                    e.traffic.placeCar(k, ed.id, from, if (from == ed.a) s else ed.len - s, style)
                    e.traffic.cars[k].speed = 0f
                    at += len + 1.6f
                }
                // close up, a fifth of the row at a time
                for (w in 0..4) {
                    val mid = 14f + (at - 14f) * (w + .5f) / 5f
                    val mx = ed.ax + ed.ux * mid; val my = ed.ay + ed.uy * mid
                    // seen from the far lane, looking toward the curb (the street's trees and wires behind)
                    val side = ed.cls.halfWidth * .5f
                    e.state.player.x = mx + ed.uy * side; e.state.player.y = my - ed.ux * side
                    e.camera.tx = mx; e.camera.ty = my
                    e.camera.zoom = 10f; e.camera.dist = 10f; e.camera.yaw = look(-ed.uy, ed.ux) + .3f
                    e.state.minute = hour * 60L
                    val old = System.getenv("HC_CARS_OLD") == "1"
                    if (old) com.hustlecity.game.render.Models.enabled = false
                    try { city("rides_${map}_${hour}_$w" + (if (old) "_old" else ""), e, 20f, warm = 0) } finally { com.hustlecity.game.render.Models.enabled = true }
                }
            }
    }

    /**
     * Down a long street with cars every few meters out to 150 m: where they change from models to simple shapes (at
     * the default quality, 80 m from the camera). Runs with HC_LOD=1; frames go to build/glframes/lod_<city>.
     */
    @Test
    fun carDetailDistance() {
        if (System.getenv("HC_LOD") != "1") return
        for (map in listOf("nyc", "manila")) {
            val e = engine(map)
            e.settings.quality = 1
            e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
            val b = e.city.bounds
            // the longest street block (the camera stands well back, so it reaches past 80 m)
            val ed = e.city.roads.edges.filter { !it.overWater && it.cls.lanes >= 2 && it.ax in b[0] + 150f..b[2] - 150f && it.ay in b[1] + 150f..b[3] - 150f }
                .maxByOrNull { it.len }!!
            e.traffic.update(0f, (ed.ax + ed.bx) / 2, (ed.ay + ed.by) / 2, 230f, -9999f, -9999f, initial = true)
            for (c in e.traffic.cars) c.placed = false
            val styles = e.city.map.trafficMix
            var at = 8f; var k = 0
            while (at < ed.len - 5f && k < e.traffic.cars.size) {
                e.traffic.placeCar(k, ed.id, ed.a, at, styles[k % styles.size], lane = k % 2)
                e.traffic.cars[k].speed = 0f
                at += 6.5f; k++
            }
            // the player at the start of the street, the camera behind them looking along it
            e.state.player.x = ed.ax + ed.ux * 6f; e.state.player.y = ed.ay + ed.uy * 6f
            e.camera.tx = e.state.player.x; e.camera.ty = e.state.player.y
            e.camera.zoom = 40f; e.camera.dist = 40f; e.camera.yaw = look(-ed.ux, -ed.uy)
            e.state.minute = 15 * 60L
            city("lod_$map", e, 18f, warm = 0)
        }
    }

    /**
     * A junction with lights after a busy minute, from above: cars wait behind the stop lines and people cross on the
     * crossings. Runs with HC_JUNCTION=1; frames go to build/glframes/junction_<city>_<n>.
     */
    @Test
    fun junctions() {
        if (System.getenv("HC_JUNCTION") != "1") return
        for (map in listOf("nyc", "tokyo", "manila")) {
            val e = engine(map)
            e.settings.quality = 2; e.settings.busyRoads = 3f; e.settings.crowds = 3f
            e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
            val b = e.city.bounds
            val cx = (b[0] + b[2]) / 2; val cy = (b[1] + b[3]) / 2
            val hub = e.city.roads.nodes.filter { it.signal && it.degree >= 4 && !it.scramble }
                .minByOrNull { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }!!
            e.state.player.x = hub.x + 14f; e.state.player.y = hub.y + 14f
            e.camera.tx = hub.x; e.camera.ty = hub.y
            e.camera.zoom = 34f; e.camera.dist = 34f; e.camera.yaw = .6f
            e.state.minute = 15 * 60L
            e.traffic.update(0f, hub.x, hub.y, 230f, -9999f, -9999f, initial = true)
            e.traffic.scatter(hub.x, hub.y, 40f, .6f)
            for (n in 0..2) {
                repeat(30 * 14) { e.frame(1 / 30f) }
                city("junction_${map}_$n", e, 48f)
            }
        }
    }

    /** The edges of the world: what lies past the last streets. Runs with HC_EDGES=1. */
    @Test
    fun edges() {
        if (System.getenv("HC_EDGES") != "1") return
        val spots = System.getenv("HC_EDGE_SPOTS")?.split(';')?.map { it.split(',') }?.map { Quad(it[0], it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) }
            ?: listOf(Quad("tokyo", 650f, 20f, 3.14f), Quad("manila", 900f, 20f, 3.14f), Quad("seoul", 1000f, 20f, 3.14f),
                Quad("nyc", 660f, 30f, 3.14f), Quad("tokyo", 1280f, 650f, 1.57f), Quad("seoul", 20f, 650f, -1.57f), Quad("classic", 450f, 0f, 3.14f))
        for ((map, x, y, yaw) in spots) {
            val e = engine(map)
            e.state.player.x = x; e.state.player.y = y; e.camera.tx = x; e.camera.ty = y
            e.camera.zoom = 90f; e.camera.dist = 90f; e.camera.yaw = yaw
            e.state.minute = 14L * 60
            city("edge_${map}_${x.toInt()}_${y.toInt()}", e)
        }
    }

    /** Camera yaw that looks along (dx, dy) (here: from the road toward a prop). */
    private fun look(dx: Float, dy: Float) = kotlin.math.atan2(dx, dy)

    private data class Quad(val map: String, val x: Float, val y: Float, val yaw: Float)

    /**
     * A sweep for glitches: street-level views all over each city (junctions, bus stops, shopping streets, parks), by day
     * and a few by night, with the crowds out. Runs with HC_SWEEP=1; frames go to build/glframes/sweep_<city>_<n>.
     */
    @Test
    fun sweep() {
        if (System.getenv("HC_SWEEP") != "1") return
        for (map in com.hustlecity.game.world.Maps.all) {
            val e0 = engine(map.id)
            val pr = com.hustlecity.game.render.StreetProps.of(e0.city)
            val rnd = java.util.Random(map.id.hashCode().toLong())
            val spots = ArrayList<Pair<Float, Float>>()
            val signals = e0.city.roads.nodes.filter { it.signal }
            repeat(3) { val n = signals[rnd.nextInt(signals.size)]; spots += n.x to n.y }
            for (k in listOf(com.hustlecity.game.render.StreetProps.BUS_STOP, com.hustlecity.game.render.StreetProps.BENCH, com.hustlecity.game.render.StreetProps.NEWS,
                com.hustlecity.game.render.StreetProps.BIKE_RACK, com.hustlecity.game.render.StreetProps.VENDOR, com.hustlecity.game.render.StreetProps.VENDING,
                com.hustlecity.game.render.StreetProps.FOUNTAIN)) {
                val all = (0 until pr.count).filter { pr.kind[it] == k }
                if (all.isNotEmpty()) { val i = all[rnd.nextInt(all.size)]; spots += pr.x[i] to pr.y[i] }
            }
            for ((n, spot) in spots.withIndex()) {
                val e = engine(map.id)
                e.settings.quality = 2; e.settings.crowds = 3f
                e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
                e.state.player.x = spot.first; e.state.player.y = spot.second
                e.camera.tx = spot.first; e.camera.ty = spot.second
                e.camera.zoom = 38f; e.camera.dist = 38f; e.camera.yaw = rnd.nextFloat() * 6.28f
                e.state.minute = (if (n % 4 == 3) 21L else 14L) * 60
                e.traffic.update(0f, spot.first, spot.second, 230f, -9999f, -9999f, initial = true)
                e.traffic.scatter(spot.first, spot.second, 60f, .65f)
                city("sweep_${map.id}_$n", e)
            }
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

    /** Every product model on a counter, a row per shot, as the HD renderer draws them (all their looks side by side). */
    @Test
    fun products() {
        com.hustlecity.game.render.Models.assets = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets
        com.hustlecity.game.render.Models.clear()
        val ids = com.hustlecity.game.render.Models.productIds().sorted()
        assertTrue("products are packaged", ids.size >= 40)
        val perShot = 4
        for (shot in 0 until (ids.size + perShot - 1) / perShot) {
            val chunk = ids.subList(shot * perShot, minOf(ids.size, (shot + 1) * perShot))
            val proj = com.hustlecity.game.render.Projector()
            proj.tx = 1.6f; proj.ty = .1f; proj.dist = 1.25f; proj.pitch = Math.toRadians(34.0).toFloat(); proj.yaw = 0f; proj.near = .05f
            proj.update(w.toFloat(), h.toFloat(), .5f)
            val frame = GpuFrame()
            frame.setCamera(proj)
            frame.mode = GpuFrame.MODE_INTERIOR
            com.hustlecity.game.render.gpu.GpuLighting.interior(frame, 0xFF16181D.toInt())
            frame.setShadow(-1f, -1f, 4f, 3f, 3f, 2048, .5f)
            val scene = com.hustlecity.game.render.Scene3D(proj, com.hustlecity.game.render.RenderBatch())
            scene.gpu = frame
            scene.material = com.hustlecity.game.render.Mat.WOOD
            scene.groundRect(-1f, -1f, 4.4f, 3f, 0f, 0xFFC08A5B.toInt())
            scene.material = com.hustlecity.game.render.Mat.PLAIN
            for ((k, pid) in chunk.withIndex()) {
                val looks = com.hustlecity.game.render.Models.product(pid)!!
                for ((v, m) in looks.withIndex()) {
                    val b = m.bounds
                    // each look on its own spot: products in a row across, their looks one behind the other
                    val x = k * .8f + .4f; val y = .1f - v * .3f + (looks.size - 1) * .1f
                    val z = if (b[2] < -.01f) .85f else 0f
                    scene.meshAt(m, x - (b[0] + b[3]) / 2, y - (b[1] + b[4]) / 2, z - (if (z > 0f) b[5] else b[2]), 1f, 0f, 0f, 1f)
                    if (z > 0f) scene.box(x - .02f, y - .25f, z, x + .02f, y + .25f, z + .02f, 0xFFB8C0CA.toInt(), 0xFFB8C0CA.toInt())
                }
            }
            val blank = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            dump("products_$shot", frame, blank, blank)
        }
    }

    /** The street prop models side by side on a pavement, facing the road (+y), in two shots: small things, then tall ones. */
    @Test
    fun propModels() {
        com.hustlecity.game.render.Models.assets = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets
        com.hustlecity.game.render.Models.clear()
        val ids = com.hustlecity.game.render.Models.propIds().sorted()
        assertTrue("props are packaged", ids.size >= 10)
        val tall = setOf("lamp", "park_lamp", "traffic_light", "bus_stop")
        for ((shot, list) in listOf(ids.filter { it !in tall }, ids.filter { it in tall }).withIndex()) {
            val proj = com.hustlecity.game.render.Projector()
            val span = if (shot == 0) 3.2f else 5f
            proj.tx = (list.size - 1) * span / 2; proj.ty = .5f; proj.dist = if (shot == 0) 9f else 13f
            proj.pitch = Math.toRadians(if (shot == 0) 32.0 else 22.0).toFloat(); proj.yaw = 0f; proj.near = .1f
            proj.update(w.toFloat(), h.toFloat(), if (shot == 0) .55f else .7f)
            val frame = GpuFrame()
            frame.setCamera(proj)
            com.hustlecity.game.render.gpu.GpuLighting.interior(frame, 0xFF9DB8D2.toInt())
            frame.setShadow(-4f, -4f, list.size * span + 4f, 6f, 8f, 2048, .6f)
            val scene = com.hustlecity.game.render.Scene3D(proj, com.hustlecity.game.render.RenderBatch())
            scene.gpu = frame
            scene.material = com.hustlecity.game.render.Mat.SIDEWALK
            scene.groundRect(-4f, -4f, list.size * span + 4f, 1.2f, 0f, 0xFFB9BCC1.toInt())
            scene.material = com.hustlecity.game.render.Mat.ASPHALT
            scene.groundRect(-4f, 1.2f, list.size * span + 4f, 8f, 0f, 0xFF4A4D52.toInt())
            scene.material = com.hustlecity.game.render.Mat.PLAIN
            for ((k, id) in list.withIndex()) {
                val looks = com.hustlecity.game.render.Models.prop(id)!!
                scene.meshAt(looks[looks.size - 1], k * span, 0f, 0f, 1f, 0f, 0f, 1f)
            }
            val blank = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            dump("prop_models_$shot", frame, blank, blank)
        }
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

    /**
     * Each kind of store with its displays full, seen up close: the products on them. Also writes how much geometry
     * and frame-building time the product models cost against the old box shapes to build/products_perf.txt.
     */
    @Test
    fun stores() {
        val report = StringBuilder()
        val types = listOf("supermarket", "produce", "florist", "coffee", "fastfood", "giftshop", "liquor", "clothing", "bookstore", "electronics", "jewelry", "nightclub", "cinema")
        for (type in types) {
            val e = engine()
            e.state.player.courses += com.hustlecity.game.data.Catalog.COURSE_IMPORT
            val t = com.hustlecity.game.data.Catalog.type(type)
            val b = e.city.buildings.filter { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 150..600 }.maxByOrNull { it.area }!!
            e.state.rentals += Rental(b.id, b.baseRent, 1)
            val biz = e.startBusiness(b.id, type, "Test")!!
            biz.backRoom = 0
            // one display per product kind, side by side, and a second row of the first ones behind
            val ids = t.products.mapNotNull { com.hustlecity.game.data.Catalog.product(it.first) }.filter { !it.service }
                .map { com.hustlecity.game.engine.itemForTag(it.tag) }.distinct()
            var x = 1; var y = 5; var widest = 0
            for (id in ids + ids + ids) {
                val def = com.hustlecity.game.data.Catalog.item(id) ?: continue
                if (x + def.w > 15) { if (y == 2) break; x = 1; y = 2 }
                biz.furniture += PlacedItem(id, x, y, 0)
                x += def.w + 1
                widest = maxOf(widest, x)
            }
            for (p in t.products) if (com.hustlecity.game.data.Catalog.product(p.first)?.service == false) biz.stock[p.first] = 5000
            biz.tempClosed = true
            e.state.minute = 13L * 60
            e.state.player.inBuilding = b.id
            e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
            repeat(150) { e.frame(1 / 30f) }
            val r = InteriorRenderer(e)
            r.cinemaTarget = floatArrayOf(minOf(widest, 9) / 2f + .5f, 5.6f, 3.6f)
            r.cinemaPitch = Math.toRadians(38.0).toFloat()
            val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            fun cost(models: Boolean): String {
                com.hustlecity.game.render.Models.enabled = models
                val f = GpuFrame()
                // the first frame (re)builds the cached products; the rest only what changes from frame to frame
                val t00 = System.nanoTime()
                r.draw(Canvas(overlay), w, h, f)
                val first = (System.nanoTime() - t00) / 1e6
                repeat(4) { r.draw(Canvas(overlay), w, h, f) }
                val t0 = System.nanoTime()
                repeat(20) { r.draw(Canvas(overlay), w, h, f) }
                val ms = (System.nanoTime() - t0) / 20 / 1e6
                val cachedVerts = f.staticGeom?.streams?.sumOf { it.nv } ?: 0
                // a customer takes something: that display changes and the layer is put back together
                val sc = e.interior!!
                val shown = sc.items.indices.filter { sc.shelf?.slots(it)?.any { p -> p != null } == true }
                var change = 0L
                for (k in 0 until 10) {
                    val slots = sc.shelf!!.slots(shown[k % shown.size])!!
                    val s = slots.indexOfFirst { it != null }
                    if (s >= 0) slots[s] = null
                    val t1 = System.nanoTime()
                    r.draw(Canvas(overlay), w, h, f)
                    change += System.nanoTime() - t1
                }
                return "per frame %6d verts %5.2f ms, cached %6d verts (first %6.2f ms, a change %5.2f ms)".format(
                    f.streams.sumOf { it.nv }, ms, cachedVerts, first, change / 10 / 1e6)
            }
            val frame = GpuFrame()
            r.draw(Canvas(overlay), w, h, frame)
            dump("store_$type", frame, overlay, overlay)
            val line = "%-12s models: %s | boxes: %s".format(type, cost(true), cost(false))
            com.hustlecity.game.render.Models.enabled = true
            println(line); report.appendLine(line)
        }
        File("build").mkdirs()
        File("build/products_perf.txt").writeText(report.toString())
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

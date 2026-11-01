package com.hustlecity.game.cinema

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.Job
import com.hustlecity.game.engine.Person
import com.hustlecity.game.engine.StreetFood
import com.hustlecity.game.engine.buyStreetFood
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.toggleWork
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.OwnedVehicle
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.Sky
import com.hustlecity.game.model.WeatherSpan
import com.hustlecity.game.render.Helicopter
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.Railways
import com.hustlecity.game.render.StreetProps
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.RoadClass
import com.hustlecity.game.world.RoadHit
import com.hustlecity.game.world.Traffic
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A camera keyframe: target, distance, yaw and pitch (degrees above the ground). */
class Key(val x: Float, val y: Float, val dist: Float, val yaw: Float, val pitch: Float)

/**
 * The trailer's world shots, flown through the real game with traffic and people moving, recorded for the HD
 * renderer at 1080p and 24 fps into build/cinema/<shot>/. Render them with tools/cinema/render.mjs. Runs only with
 * HC_CINEMA=1; HC_SHOTS picks shots by name prefix; HC_PREVIEW=1 instead saves quick software-rendered frames into
 * build/cinema_preview/<shot>/ to check the framing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CinematicTest {
    private val preview = System.getenv("HC_PREVIEW") == "1"
    private val w = if (preview) 960 else 1920
    private val h = if (preview) 540 else 1080
    private val fps = 24
    /** How busy the streets are on film: eight times the people and three times the cars a phone shows on high. */
    private val crowds = 8f
    private val busyRoads = 3f
    private val root = File(if (preview) "build/cinema_preview" else "build/cinema")

    private fun on(name: String): Boolean {
        if (System.getenv("HC_CINEMA") != "1") return false
        val only = System.getenv("HC_SHOTS")?.split(',')?.filter { it.isNotBlank() } ?: return true
        return only.any { name.startsWith(it) }
    }

    private fun engine(mapId: String, hour: Float, sky: Sky = Sky.CLEAR): GameEngine {
        val e = GameEngine(null)
        e.newGame("Alex Rivera", 1, 2, 1, mapId = mapId)
        e.ui.tip = null; e.ui.phoneOpen = false
        e.settings.quality = 2
        // the city at its busiest: filming has no frame rate to keep
        e.settings.crowds = crowds; e.settings.busyRoads = busyRoads
        e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
        e.state.minute = (e.state.minute / 1440) * 1440 + (hour * 60).toLong()
        e.state.weather.clear()
        e.state.weather += WeatherSpan(e.state.minute - 600, e.state.minute + 3 * 1440L, sky)
        e.weather.ensure()
        return e
    }

    // Catmull-Rom through the keys; the time along the shot eases in and out
    private fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t; val t3 = t2 * t
        return .5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }

    private fun at(keys: List<Key>, u: Float, pick: (Key) -> Float): Float {
        if (keys.size == 1) return pick(keys[0])
        val t = u * (keys.size - 1)
        val i = t.toInt().coerceAtMost(keys.size - 2)
        val f = t - i
        val k0 = keys[(i - 1).coerceAtLeast(0)]; val k1 = keys[i]; val k2 = keys[i + 1]; val k3 = keys[(i + 2).coerceAtMost(keys.size - 1)]
        return cr(pick(k0), pick(k1), pick(k2), pick(k3), f)
    }

    private fun ease(u: Float, amount: Float) = u + (u * u * (3 - 2 * u) - u) * amount

    /** One shot's output: the HD frame sequence, or quick previews. */
    private inner class Out(name: String) {
        val writer = if (preview) null else CinemaWriter(File(root, name))
        val dir = File(root, name).apply { if (preview) { deleteRecursively(); mkdirs() } }
        val gpu = GpuFrame()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        var n = 0

        fun frame(drawCanvas: (Canvas) -> Unit, drawGpu: (Canvas, GpuFrame) -> Unit, weather: Boolean) {
            if (preview) {
                if (n % 4 == 0) {
                    bmp.eraseColor(0xFF000000.toInt())
                    drawCanvas(canvas)
                    File(dir, "%04d.jpg".format(n)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                }
            } else {
                bmp.eraseColor(0)
                drawGpu(canvas, gpu)
                writer!!.write(gpu, if (weather) bmp else null)
            }
            n++
        }
    }

    /**
     * Stops a shot whose camera ends up inside (or brushing) a building: the canvas preview culls the walls it is behind
     * and looks fine, but the HD render shows nothing but the inside of the walls.
     */
    private fun checkCamera(e: GameEngine, r: WorldRenderer, name: String, f: Int) {
        val p = r.proj
        var hit: Building? = null
        e.city.forBuildingsIn(p.camX - 3f, p.camY - 3f, p.camX + 3f, p.camY + 3f) { b ->
            if (b.height > 0f && p.camZ < b.height + 1.5f &&
                (b.contains(p.camX, p.camY) || b.contains(p.camX + 1.5f, p.camY) || b.contains(p.camX - 1.5f, p.camY) ||
                    b.contains(p.camX, p.camY + 1.5f) || b.contains(p.camX, p.camY - 1.5f))) hit = b
        }
        check(hit == null) { "$name frame $f: the camera is inside building ${hit!!.id} at ${p.camX.toInt()},${p.camY.toInt()},${p.camZ.toInt()}" }
    }

    /**
     * Flies the camera along [keys] for [seconds], the game running underneath (traffic and people follow the camera).
     * [each] runs before every frame with the frame index and 0..1 progress; [warm] seconds of simulation fill the
     * streets first.
     */
    private fun fly(e: GameEngine, name: String, seconds: Float, keys: List<Key>, easing: Float = .6f, warm: Float = 3f,
                    weather: Boolean = false, haze: Float = .3f, crowd: Float = 90f, each: (Int, Float) -> Unit = { _, _ -> }) {
        val out = Out(name)
        val r = WorldRenderer(e).apply { cinematic = true; cinemaHaze = haze }
        val frames = (seconds * fps).toInt()
        fun place(u: Float) {
            val v = ease(u, easing)
            val c = e.camera
            c.tx = at(keys, v) { it.x }; c.ty = at(keys, v) { it.y }
            c.dist = at(keys, v) { it.dist }; c.zoom = c.dist
            c.yaw = at(keys, v) { it.yaw }
            r.cinemaPitch = Math.toRadians(at(keys, v) { it.pitch }.toDouble()).toFloat()
            if (e.state.player.driving < 0) { e.state.player.x = c.tx; e.state.player.y = c.ty }
        }
        place(0f)
        // people and cars start spread round the shot, not walking in from out of view, most of the people close by
        e.traffic.update(0f, e.camera.tx, e.camera.ty, 230f, -9999f, -9999f, initial = true)
        e.traffic.scatter(e.camera.tx, e.camera.ty, crowd, .65f)
        repeat((warm * fps).toInt()) { each(-1, 0f); e.frame(1f / fps); place(0f) }
        for (f in 0 until frames) {
            val u = f / (frames - 1f).coerceAtLeast(1f)
            each(f, u)
            e.frame(1f / fps)
            place(u)
            out.frame({ c -> r.draw(c, w, h, 2f) }, { c, g -> r.draw(c, w, h, 3f, g) }, weather)
            checkCamera(e, r, name, f)
        }
        println("$name: ${out.n} frames")
    }

    private fun wrap(a: Float): Float { var x = a; while (x > PI) x -= (2 * PI).toFloat(); while (x < -PI) x += (2 * PI).toFloat(); return x }

    /** Puts a new vehicle of [defId] in the right-hand lane of a big road near (x, y), pointing along it, and drives it. */
    private fun startCar(e: GameEngine, defId: String, x: Float, y: Float, color: Int = 0, minRank: Int = 2, minLen: Float = 80f,
                         bridge: Boolean = false): OwnedVehicle {
        val g = e.city.roads
        // a long straight stretch of a big road near (x, y): the further, the longer it has to be
        val ed = g.edges.filter { it.cls.rank >= minRank && it.overWater == bridge && it.len > 60f }
            .minByOrNull { hypot((it.ax + it.bx) / 2 - x, (it.ay + it.by) / 2 - y) + (minLen - it.len).coerceAtLeast(0f) * 4f }!!
        val side = if (e.city.leftHand) -1f else 1f
        val s = ed.clearA + 8f
        val o = -side * ed.cls.laneWidth * .5f
        val px = ed.ax + ed.ux * s + ed.uy * o; val py = ed.ay + ed.uy * s - ed.ux * o
        val v = OwnedVehicle(e.state.newId(), defId, px, py, atan2(ed.ux, -ed.uy), color = color)
        e.state.vehicles += v
        e.state.player.x = px; e.state.player.y = py
        e.enterVehicle(v)
        e.ui.tip = null
        // a destination down the road, so the GPS has somewhere to go
        val ahead = e.city.buildings.filter { it.solid && hypot(it.doorX - (px + ed.ux * 300f), it.doorY - (py + ed.uy * 300f)) < 160f }
            .minByOrNull { hypot(it.doorX - (px + ed.ux * 320f), it.doorY - (py + ed.uy * 320f)) }
        if (ahead != null) e.setWaypoint(ahead.id)
        return v
    }

    /** Steers the player's car along the GPS route at about [speed] m/s. */
    private fun drive(e: GameEngine, v: OwnedVehicle, speed: Float, drift: Boolean = false) {
        val gps = e.gps
        var target: Pair<Float, Float>? = null
        if (gps.active) for (q in 1 until gps.points) {
            val x = gps.route[q * 2]; val y = gps.route[q * 2 + 1]
            if (hypot(x - v.x, y - v.y) > 16f) { target = x to y; break }
        }
        var turn = 0f
        if (target != null) {
            val want = atan2(target.first - v.x, -(target.second - v.y))
            turn = wrap(want - v.heading)
            e.input.steer = (turn * 2.2f).coerceIn(-1f, 1f)
        } else e.input.steer = 0f
        // throw it sideways into the sharp turns with the handbrake, then power out
        val sliding = drift && abs(turn) > .45f && e.carSpeed > 11f
        e.input.handbrake = sliding
        e.input.gas = !sliding && e.carSpeed < speed
        e.input.brake = !sliding && e.carSpeed > speed + 2f
    }

    /**
     * Follows the player's car: the camera trails it at [relYaw] (radians off straight behind), [dist] meters, [pitch]
     * degrees, each a function of progress, smoothed so it swings rather than snaps.
     */
    private fun chase(e: GameEngine, name: String, seconds: Float, v: OwnedVehicle, speed: Float, relYaw: (Float) -> Float, dist: (Float) -> Float,
                      pitch: (Float) -> Float, lead: Float = 2f, warm: Float = 3f, weather: Boolean = false, haze: Float = .3f,
                      drift: Boolean = false, after: () -> Unit = {}, each: (Int, Float) -> Unit = { _, _ -> }) {
        val out = Out(name)
        val r = WorldRenderer(e).apply { cinematic = true; cinemaHaze = haze }
        val frames = (seconds * fps).toInt()
        // the camera looks along -back = -(sin yaw, cos yaw): straight behind a car heading h is yaw = -h
        var yaw = -v.heading + relYaw(0f)
        var tx = v.x; var ty = v.y
        fun place(u: Float, k: Float) {
            val want = -v.heading + relYaw(u)
            yaw += wrap(want - yaw) * k
            val fx = sin(v.heading); val fy = -cos(v.heading)
            tx += (v.x + fx * lead - tx) * .5f; ty += (v.y + fy * lead - ty) * .5f
            val c = e.camera
            c.tx = tx; c.ty = ty; c.dist = dist(u); c.zoom = c.dist; c.yaw = yaw
            r.cinemaPitch = Math.toRadians(pitch(u).toDouble()).toFloat()
        }
        place(0f, 1f)
        e.traffic.update(0f, v.x, v.y, 230f, -9999f, -9999f, initial = true)
        e.traffic.scatter(v.x, v.y, 110f, .65f)
        repeat((warm * fps).toInt()) { drive(e, v, speed, drift); each(-1, 0f); e.frame(1f / fps); after(); place(0f, 1f) }
        for (f in 0 until frames) {
            val u = f / (frames - 1f).coerceAtLeast(1f)
            drive(e, v, speed, drift); each(f, u)
            e.frame(1f / fps)
            after()
            place(u, .12f)
            out.frame({ c -> r.draw(c, w, h, 2f) }, { c, g -> r.draw(c, w, h, 3f, g) }, weather)
            checkCamera(e, r, name, f)
        }
        println("$name: ${out.n} frames")
    }

    /** An interior shot: the room's own life running, the camera at [target] (tiles), [dist], yaw and pitch over time. */
    private fun inside(e: GameEngine, name: String, seconds: Float, cam: (Float) -> FloatArray, warm: Float = 12f, each: (Int) -> Unit = {}) {
        val sc = e.interior!!
        val out = Out(name)
        val r = InteriorRenderer(e)
        val frames = (seconds * fps).toInt()
        val w0 = (warm * fps).toInt()
        repeat(w0) { k -> each(k - w0); sc.update(1f / fps, true) }
        for (f in 0 until frames) {
            val u = ease(f / (frames - 1f).coerceAtLeast(1f), .6f)
            each(f)
            sc.update(1f / fps, true)
            val c = cam(u)
            r.cinemaTarget = floatArrayOf(c[0], c[1], c[2])
            sc.yaw = c[3]
            r.cinemaPitch = Math.toRadians(c[4].toDouble()).toFloat()
            out.frame({ cv -> r.draw(cv, w, h) }, { cv, g -> r.draw(cv, w, h, g, 3f) }, false)
        }
        println("$name: ${out.n} frames")
    }

    /** Camera yaw that looks along (dx, dy). */
    private fun look(dx: Float, dy: Float) = atan2(-dx, -dy)

    private fun landmark(e: GameEngine, name: String) = e.city.landmarks.first { it.name.contains(name, ignoreCase = true) }

    private fun clearWeather(e: GameEngine) { e.skyVis.rain = 0f; e.skyVis.storm = 0f; e.skyVis.cloud = 0f; e.skyVis.fog = 0f; e.skyVis.wet = 0f }

    // ------------------------------------------------------------------ act one: the cities

    /** New York at golden hour: over the park toward the towers of Midtown. */
    @Test
    fun a1() {
        if (!on("a1")) { assumeTrue(false); return }
        val e = engine("nyc", 17.55f)
        fly(e, "a1_nyc", 3.4f, listOf(Key(880f, 520f, 330f, look(-150f, 260f) + .25f, 14f), Key(840f, 600f, 290f, look(-150f, 260f) + .05f, 17f)),
            easing = .2f) { _, _ -> clearWeather(e) }
    }

    /** Manila in the morning: Binondo's church and the LRT running past. */
    @Test
    fun a2() {
        if (!on("a2")) { assumeTrue(false); return }
        val e = engine("manila", 8.4f)
        val c = landmark(e, "Binondo")
        fly(e, "a2_manila", 3.4f, listOf(Key(c.x + 10f, c.y + 20f, 150f, -.9f, 17f), Key(c.x, c.y + 10f, 128f, -.45f, 20f)),
            easing = .2f) { _, _ -> clearWeather(e) }
    }

    /** Tokyo Tower at dusk, from low over the rooftops. */
    @Test
    fun a3() {
        if (!on("a3")) { assumeTrue(false); return }
        val e = engine("tokyo", 17.85f)
        val t = landmark(e, "Tokyo Tower")
        fly(e, "a3_tokyo", 2.4f, listOf(Key(t.x - 20f, t.y - 30f, 290f, look(119f, 176f) - .2f, 10f), Key(t.x - 10f, t.y - 20f, 265f, look(119f, 176f), 12f)),
            easing = .2f) { _, _ -> clearWeather(e) }
    }

    /** Seoul at sunset: Gwanghwamun and the hanok roofs behind it. */
    @Test
    fun a4() {
        if (!on("a4")) { assumeTrue(false); return }
        val e = engine("seoul", 18.25f)
        val g = landmark(e, "Gwanghwamun")
        fly(e, "a4_seoul", 2.4f, listOf(Key(g.x - 10f, g.y + 10f, 125f, .35f, 14f), Key(g.x + 5f, g.y, 110f, .05f, 16f)),
            easing = .2f) { _, _ -> clearWeather(e) }
    }

    /** The original grid at sunset, the fifth city, sweeping in over Midtown. */
    @Test
    fun a5() {
        if (!on("a5")) { assumeTrue(false); return }
        val e = engine(com.hustlecity.game.world.Maps.CLASSIC, 18.1f)
        fly(e, "a5_classic", 1.7f, listOf(Key(640f, 760f, 300f, -.15f, 15f), Key(650f, 700f, 270f, .1f, 17f)),
            easing = .2f) { _, _ -> clearWeather(e) }
    }

    /** Behind the title: Shinjuku's towers at night. */
    @Test
    fun b1() {
        if (!on("b1")) { assumeTrue(false); return }
        val e = engine("tokyo", 21.3f)
        fly(e, "b1_title", 2.6f, listOf(Key(330f, 470f, 330f, -1.2f, 14f), Key(310f, 470f, 380f, -1.05f, 16f)), easing = .15f, haze = .4f) { _, _ -> clearWeather(e) }
    }

    // ------------------------------------------------------------------ act two: the business

    private fun supermarket(e: GameEngine) {
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 13..16 && it.gridD in 9..10 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "supermarket", "Corner Market")!!
        biz.tempClosed = false; biz.wallColor = 0
        val room = biz.backRoom.coerceAtLeast(0); val y = room + 1; val w = b.gridW; val d = b.gridD
        biz.furniture += listOf(
            PlacedItem("storage_shelf", 0, 0, 0), PlacedItem("storage_shelf", 3, 0, 0), PlacedItem("cleaning_station", 6, 0, 0),
            PlacedItem("shelf", 1, y, 0), PlacedItem("shelf", 4, y, 0), PlacedItem("shelf", 7, y, 0),
            PlacedItem("drinks_fridge", 10, room, 0), PlacedItem("drinks_fridge", 11, room, 0),
            PlacedItem("fresh_fridge", 1, y + 3, 0), PlacedItem("freezer", 4, y + 3, 0), PlacedItem("ice_cream_freezer", w - 3, y + 1, 1),
            PlacedItem("produce", 7, y + 3, 0), PlacedItem("checkout_counter", w - 6, d - 3, 0), PlacedItem("plant_large", 0, d - 1, 0),
            PlacedItem("shopping_baskets", w - 2, d - 2, 0),
        )
        for ((pid, _) in biz.type.products) {
            val p = Catalog.product(pid) ?: continue
            biz.stock[pid] = BizCalc.capacityFor(biz, pid) / biz.type.products.count { Catalog.product(it.first)?.tag == p.tag }
        }
        biz.backStock["soda"] = 48 * 4; biz.backStock["snacks"] = 60 * 3; biz.backStock["frozen_food"] = 30 * 2
        for (role in listOf(StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE, StaffRole.CLEANER))
            e.state.employees += Employee(e.state.newId(), "Sam", role, .6, 15.0, 15.0, 30, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.economy.servedRate[biz.id] = 220.0
    }

    /** Your supermarket in full swing. */
    @Test
    fun c2() {
        if (!on("c2")) { assumeTrue(false); return }
        val e = engine("nyc", 13.5f)
        supermarket(e)
        val sc = e.interior!!
        inside(e, "c2_market", 2.4f, { u ->
            floatArrayOf(sc.w * (.42f + .1f * u), sc.d * .58f, 15f - 3f * u, -.75f + .35f * u, 44f - 4f * u)
        })
    }

    /** A café at breakfast: tables full, the espresso machines going. */
    @Test
    fun c3() {
        if (!on("c3")) { assumeTrue(false); return }
        val e = engine("seoul", 9.2f)
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 11..14 && it.gridD in 8..10 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "coffee", "Morning Ritual")!!
        biz.tempClosed = false
        val room = biz.backRoom.coerceAtLeast(0); val wd = b.gridW; val d = b.gridD
        biz.furniture += listOf(
            PlacedItem("coffee_machine", 1, room, 0), PlacedItem("coffee_machine", 3, room, 0), PlacedItem("pastry_case", 5, room, 0),
            PlacedItem("pastry_case", 6, room, 0), PlacedItem("cash_register", 8, room, 0),
            PlacedItem("table_set", 1, room + 3, 0), PlacedItem("table_set", 4, room + 3, 0), PlacedItem("table_set", 7, room + 3, 0),
            PlacedItem("table_set", 1, room + 6, 0), PlacedItem("table_set", 4, room + 6, 0),
            PlacedItem("plant_large", wd - 1, d - 1, 0), PlacedItem("plant_large", 0, d - 1, 0), PlacedItem("speaker_medium", wd - 1, room, 0),
            PlacedItem("painting", 10, 0, 0), PlacedItem("floor_lamp", wd - 2, room + 3, 0),
        )
        biz.stock["coffee"] = 380; biz.stock["tea"] = 120; biz.stock["croissant"] = 80; biz.stock["cupcake"] = 60
        for (k in 0..1) e.state.employees += Employee(e.state.newId(), "Jun", StaffRole.CUSTOMER_SERVICE, .7, 15.0, 15.0, 26, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.economy.servedRate[biz.id] = 160.0
        val sc = e.interior!!
        inside(e, "c3_cafe", 2.4f, { u -> floatArrayOf(sc.w * .45f, sc.d * (.6f - .05f * u), 11.5f - 1.5f * u, .55f - .3f * u, 40f) }, warm = 20f)
    }

    // ------------------------------------------------------------------ act three: the city as your playground

    /** A rainy night in New York: tail lights, headlights and puddles on the avenue. */
    @Test
    fun c6() {
        if (!on("c6")) { assumeTrue(false); return }
        val e = engine("nyc", 21.2f, Sky.RAIN)
        val v = startCar(e, "volta", 640f, 860f, color = 0xFFC0392B.toInt())
        fun wet() { e.skyVis.rain = .85f; e.skyVis.cloud = .9f; e.skyVis.wet = 1f }
        chase(e, "c6_rain", 2.4f, v, 11f, relYaw = { u -> .5f - .3f * u }, dist = { u -> 13f - 1.5f * u }, pitch = { 13f }, weather = true, haze = .35f) { _, _ -> wet() }
    }

    /** A box truck on its morning route out of the wholesale district. */
    @Test
    fun c7() {
        if (!on("c7")) { assumeTrue(false); return }
        val e = engine("nyc", 8.6f)
        val m = e.city.placeBuilding("metro")!!
        val v = startCar(e, "hauler", m.doorX, m.doorY - 60f, color = 0xFF1F4FA0.toInt(), minRank = 1)
        chase(e, "c7_fleet", 2.4f, v, 8f, relYaw = { u -> PI.toFloat() + .45f - .2f * u }, dist = { 19f }, pitch = { u -> 11f + 3f * u }, lead = 1f) { _, _ -> clearWeather(e) }
    }

    /** Manila's streets: tangled wires, food carts and jeepneys. */
    @Test
    fun c9() {
        if (!on("c9")) { assumeTrue(false); return }
        val e = engine("manila", 10.8f)
        val pr = StreetProps(e.city)
        // a pole with wires running on down the street, a food cart nearby
        val poles = (0 until pr.count).filter { pr.kind[it] == StreetProps.UTIL_POLE && pr.link[it] >= 0 && pr.link[pr.link[it]] >= 0 }
        val pole = poles.minByOrNull { p -> (0 until pr.count).filter { pr.kind[it] == StreetProps.VENDOR }.minOf { hypot(pr.x[it] - pr.x[p], pr.y[it] - pr.y[p]) } + hypot(pr.x[p] - 742f, pr.y[p] - 287f) * .1f }!!
        val nx = pr.x[pr.link[pole]] - pr.x[pole]; val ny = pr.y[pr.link[pole]] - pr.y[pole]
        val l = hypot(nx, ny); val ax = nx / l; val ay = ny / l
        // stand back from the pole, over the road, and look along the line of wires
        val x0 = pr.x[pole] + pr.dirX[pole] * 4f; val y0 = pr.y[pole] + pr.dirY[pole] * 4f
        // jeepneys old and new coming up the street toward the camera and pulling away, mopeds weaving between them
        val traffic = listOf(Triple(15f, true, 4), Triple(23f, true, 12), Triple(33f, true, 11), Triple(44f, true, 12), Triple(54f, true, 5),
            Triple(4f, false, 11), Triple(12f, false, 12), Triple(26f, false, 4), Triple(38f, false, 12))
        fly(e, "c9_manila", 2.4f, listOf(Key(x0 + ax * 6f, y0 + ay * 6f, 24f, look(ax, ay) + .35f, 12f), Key(x0 + ax * 12f, y0 + ay * 12f, 22f, look(ax, ay) + .2f, 11f)),
            easing = .2f, warm = 4f) { f, _ ->
            clearWeather(e)
            if (f == 0) stage(e, pr.x[pole], pr.y[pole], ax, ay, traffic)
        }
    }

    /**
     * Puts [traffic] on the streets ahead of (x, y) along (ax, ay), one vehicle per entry: how many meters ahead, whether
     * it comes toward the camera (against the way it looks), and its style.
     */
    private fun stage(e: GameEngine, x: Float, y: Float, ax: Float, ay: Float, traffic: List<Triple<Float, Boolean, Int>>) {
        for ((k, t) in traffic.withIndex()) {
            val (ahead, toward, style) = t
            val hit = RoadHit()
            if (!e.city.roads.nearest(x + ax * ahead, y + ay * ahead, 16f, hit)) continue
            val ed = e.city.roads.edges[hit.edge]
            val aToB = ed.ux * ax + ed.uy * ay > 0f
            val from = if (aToB != toward) ed.a else ed.b
            e.traffic.placeCar(k, ed.id, from, if (from == ed.a) hit.s else ed.len - hit.s, style)
        }
    }

    /** A Tokyo drinking alley at night under red lanterns. */
    @Test
    fun c10() {
        if (!on("c10")) { assumeTrue(false); return }
        val e = engine("tokyo", 21.4f)
        val pr = StreetProps(e.city)
        val ln = (0 until pr.count).filter { pr.kind[it] == StreetProps.LANTERNS }.minByOrNull { hypot(pr.x[it] - 300f, pr.y[it] - 980f) }!!
        // the lane runs across the lantern strings, whose props hang over its middle
        val hit = RoadHit()
        e.city.roads.nearest(pr.x[ln], pr.y[ln], 12f, hit)
        val lane = e.city.roads.edges[hit.edge]
        val cx = lane.ax + lane.ux * hit.s; val cy = lane.ay + lane.uy * hit.s
        val ax = pr.dirY[ln]; val ay = -pr.dirX[ln]
        fly(e, "c10_yokocho", 2.4f, listOf(Key(cx + ax * 6f, cy + ay * 6f, 16f, look(ax, ay), 10f), Key(cx + ax * 11f, cy + ay * 11f, 15f, look(ax, ay) + .08f, 9f)),
            easing = .2f, warm = 4f, haze = .2f, crowd = 45f) { _, _ -> clearWeather(e) }
    }

    /** The hypercar at golden hour down a Gangnam boulevard. */
    @Test
    fun c13() {
        if (!on("c13")) { assumeTrue(false); return }
        val e = engine("seoul", 17.7f)
        // lighter traffic here, so nothing drives between the camera and the car
        e.settings.busyRoads = 1.2f; e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
        val v = startCar(e, "vortex", 335f, 1097f, color = 0xFFE53935.toInt(), minLen = 220f)
        chase(e, "c13_hypercar", 2.4f, v, 19f, relYaw = { u -> -2.1f + .6f * u }, dist = { u -> 10f + 1.5f * u }, pitch = { 9f }, lead = .5f) { _, _ -> clearWeather(e) }
    }

    /** Snow on the original city's Midtown. */
    @Test
    fun c14() {
        if (!on("c14")) { assumeTrue(false); return }
        val e = engine(com.hustlecity.game.world.Maps.CLASSIC, 11.2f, Sky.SNOW)
        e.state.snowCover = 1.0
        fly(e, "c14_snow", 3.4f, listOf(Key(675f, 560f, 150f, .5f, 22f), Key(665f, 545f, 132f, .75f, 24f)), easing = .2f, weather = true, haze = .45f) { _, _ ->
            e.state.snowCover = 1.0; e.skyVis.snow = .8f; e.skyVis.cloud = .8f
        }
    }

    /** N Seoul Tower lit up at night over the city. */
    @Test
    fun c15() {
        if (!on("c15")) { assumeTrue(false); return }
        val e = engine("seoul", 21.0f)
        val t = landmark(e, "N Seoul")
        fly(e, "c15_seoul_night", 2.4f, listOf(Key(t.x, t.y, 520f, -.3f, 8.2f), Key(t.x, t.y, 480f, -.15f, 8.8f)), easing = .2f, haze = .3f) { _, _ -> clearWeather(e) }
    }

    /** The Shibuya scramble at night: every car holds and the crowd floods across in all directions. */
    @Test
    fun c16() {
        if (!on("c16")) { assumeTrue(false); return }
        val e = engine("tokyo", 20.6f)
        val s = landmark(e, "Shibuya")
        val node = e.city.scrambleNode(s)!!
        // the walk signal comes on three seconds before the shot, after a long red has gathered people at every corner
        val warm = 26f
        e.traffic.lightClock = ((34.5f - node.phase - (warm - 3f)) % Traffic.SCRAMBLE_CYCLE + Traffic.SCRAMBLE_CYCLE) % Traffic.SCRAMBLE_CYCLE
        fly(e, "c16_scramble", 2.6f, listOf(Key(s.x, s.y, 56f, 1.95f, 34f), Key(s.x, s.y, 49f, 2.22f, 30f)), easing = .2f, warm = warm, haze = .25f,
            crowd = 45f) { _, _ ->
            clearWeather(e)
        }
    }

    /** Times Square at night from the sidewalk: crowds under the screens, taxis pouring through. */
    @Test
    fun c17() {
        if (!on("c17")) { assumeTrue(false); return }
        val e = engine("nyc", 20.4f)
        // low over Seventh Avenue, looking up into the square between its crowded sidewalks
        fly(e, "c17_times", 2.4f, listOf(Key(621f, 946f, 28f, .1f, 10f), Key(621f, 930f, 26f, .03f, 9f)), easing = .2f, warm = 5f, haze = .22f,
            crowd = 70f) { _, _ ->
            clearWeather(e)
        }
    }

    // ------------------------------------------------------------------ the Manila trailer (m01 .. m21)

    /** Game time when a train next runs within 6 m of (x, y). */
    private fun trainPasses(e: GameEngine, x: Float, y: Float): Float {
        val rails = Railways(e.city)
        var t = 0f
        while (t < 600f) {
            rails.update(t)
            for (c in 0 until rails.carCount) if (hypot(rails.carX(c) - x, rails.carY(c) - y) < 6f) return t
            t += .1f
        }
        return 0f
    }

    /** Jeepneys old and new, tricycles and mopeds, most of them coming up the street toward the camera. */
    private val parade = listOf(Triple(14f, true, 4), Triple(21f, true, 12), Triple(30f, true, 11), Triple(39f, true, 5), Triple(47f, true, 4),
        Triple(56f, true, 12), Triple(5f, false, 11), Triple(13f, false, 12), Triple(25f, false, 4), Triple(36f, false, 5))

    /** Dawn over Manila Bay, closing on the walls of Intramuros, Rizal Park and the towers beyond. */
    @Test
    fun m01() {
        if (!on("m01")) { assumeTrue(false); return }
        val e = engine("manila", 6.4f)
        fly(e, "m01_dawn", 4.6f, listOf(Key(330f, 700f, 300f, -1.38f, 9f), Key(345f, 690f, 235f, -1.25f, 12f)), easing = .2f, haze = .38f) { _, _ -> clearWeather(e) }
    }

    /** Morning over Intramuros down onto the front of the Manila Cathedral. */
    @Test
    fun m02() {
        if (!on("m02")) { assumeTrue(false); return }
        val e = engine("manila", 7.3f)
        val c = landmark(e, "Cathedral")
        fly(e, "m02_cathedral", 4.6f, listOf(Key(c.x, c.y, 165f, 3.3f, 25f), Key(c.x, c.y, 110f, 2.95f, 17f)), easing = .3f) { _, _ -> clearWeather(e) }
    }

    /** The Rizal Monument: its guards of honour and the flag over Luneta. */
    @Test
    fun m03() {
        if (!on("m03")) { assumeTrue(false); return }
        val e = engine("manila", 7.6f)
        val m = landmark(e, "Rizal")
        // an orbit at one distance: closer in, the flag flies off the top of the frame
        fly(e, "m03_rizal", 2.6f, listOf(Key(m.x + 4f, m.y - 5f, 80f, 3.28f, 11f), Key(m.x + 4f, m.y - 5f, 80f, 3.06f, 11f)), easing = .2f, crowd = 70f) { _, _ -> clearWeather(e) }
    }

    /** City Hall's clock tower against the sunset, the LRT in front. */
    @Test
    fun m04() {
        if (!on("m04")) { assumeTrue(false); return }
        val e = engine("manila", 18.2f)
        fly(e, "m04_cityhall", 2.2f, listOf(Key(452f, 762f, 115f, 2.0f, 7f), Key(452f, 762f, 102f, 1.86f, 10f)), easing = .2f) { _, _ -> clearWeather(e) }
    }

    /** The title: Binondo's arch at dusk from the foot of Jones Bridge. */
    @Test
    fun m05() {
        if (!on("m05")) { assumeTrue(false); return }
        val e = engine("manila", 18.7f)
        val a = landmark(e, "Binondo Arch")
        fly(e, "m05_arch", 2.6f, listOf(Key(a.x, a.y - 4f, 44f, .04f, 7f), Key(a.x, a.y - 1f, 36f, -.04f, 9f)), easing = .2f, crowd = 60f) { _, _ -> clearWeather(e) }
    }

    /** Spaghetti wires and fiesta banderitas over a Paco street, the traffic coming on under them. */
    @Test
    fun m06() {
        if (!on("m06")) { assumeTrue(false); return }
        val e = engine("manila", 10.4f)
        fly(e, "m06_wires", 2.6f, listOf(Key(1003f, 875f, 20f, -1.57f, 7f), Key(1012f, 875f, 19f, -1.5f, 8f)), easing = .2f, warm = 4f, crowd = 45f) { f, _ ->
            clearWeather(e)
            if (f == 0) stage(e, 1003f, 875f, 1f, 0f, parade)
        }
    }

    /** Jeepneys old and new, tricycles and mopeds up a Tondo street under the banderitas. */
    @Test
    fun m07() {
        if (!on("m07")) { assumeTrue(false); return }
        val e = engine("manila", 10.8f)
        val ax = .17f; val ay = .985f
        // above head height, so the people on the street pass under the lens rather than in front of it
        fly(e, "m07_jeepneys", 2.6f, listOf(Key(236f, 268f, 21f, look(ax, ay) + .1f, 10f), Key(237f, 276f, 20f, look(ax, ay) - .05f, 10f)), easing = .2f, warm = 4f,
            crowd = 60f) { f, _ ->
            clearWeather(e)
            if (f == 0) stage(e, 236f, 268f, ax, ay, parade)
        }
    }

    /** An LRT-1 train running along its viaduct over Taft Avenue. */
    @Test
    fun m08() {
        if (!on("m08")) { assumeTrue(false); return }
        val e = engine("manila", 11.0f)
        // the train passes the middle of the view a second and a half in
        val warm = 3f
        e.realTime = trainPasses(e, 528f, 672f) - warm - 1.5f
        fly(e, "m08_lrt", 2.6f, listOf(Key(527f, 668f, 44f, .32f, 14f), Key(527f, 672f, 40f, .24f, 15f)), easing = .2f, warm = warm) { _, _ -> clearWeather(e) }
    }

    /** A barangay street in the late afternoon: banderitas, a hoop, people out on the street. */
    @Test
    fun m09() {
        if (!on("m09")) { assumeTrue(false); return }
        val e = engine("manila", 16.3f)
        fly(e, "m09_barangay", 2.6f, listOf(Key(236f, 273f, 19f, -3.06f, 10f), Key(237f, 280f, 18f, -2.9f, 11f)), easing = .2f, warm = 4f, crowd = 50f) { f, _ ->
            clearWeather(e)
            if (f == 0) stage(e, 236f, 273f, .17f, .985f, listOf(Triple(18f, true, 5), Triple(30f, true, 12), Triple(42f, false, 5)))
        }
    }

    /** Binondo's food carts and the crowds round them. */
    @Test
    fun m10() {
        if (!on("m10")) { assumeTrue(false); return }
        val e = engine("manila", 11.6f)
        fly(e, "m10_binondo", 2.6f, listOf(Key(292f, 388f, 32f, 2.5f, 13f), Key(292f, 384f, 27f, 2.75f, 12f)), easing = .2f, warm = 4f, crowd = 40f) { _, _ -> clearWeather(e) }
    }

    /** The crowd in front of Quiapo Church. */
    @Test
    fun m11() {
        if (!on("m11")) { assumeTrue(false); return }
        val e = engine("manila", 9.4f)
        val q = landmark(e, "Quiapo")
        fly(e, "m11_quiapo", 2.6f, listOf(Key(q.x, q.y, 72f, 3.47f, 11f), Key(q.x, q.y, 60f, 3.62f, 12f)), easing = .2f, warm = 4f, crowd = 50f) { _, _ -> clearWeather(e) }
    }

    /** A jeepney terminal in Tondo at the morning rush. */
    @Test
    fun m12() {
        if (!on("m12")) { assumeTrue(false); return }
        val e = engine("manila", 8.0f)
        fly(e, "m12_terminal", 2.6f, listOf(Key(280f, 58f, 38f, 2.3f, 24f), Key(280f, 58f, 32f, 2.55f, 20f)), easing = .2f, warm = 4f, crowd = 45f) { _, _ -> clearWeather(e) }
    }

    /** EDSA at rush hour: every lane full, the lights coming on. */
    @Test
    fun m13() {
        if (!on("m13")) { assumeTrue(false); return }
        val e = engine("manila", 18.0f)
        e.settings.busyRoads = 7f; e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
        fly(e, "m13_edsa", 2.6f, listOf(Key(1086f, 943f, 120f, 2.44f, 17f), Key(1086f, 943f, 105f, 2.3f, 15f)), easing = .2f, warm = 12f) { _, _ -> clearWeather(e) }
    }

    /** A monsoon night on the wires street: rain, wet asphalt and headlights. */
    @Test
    fun m14() {
        if (!on("m14")) { assumeTrue(false); return }
        val e = engine("manila", 20.2f, Sky.RAIN)
        fun wet() { e.skyVis.rain = .85f; e.skyVis.cloud = .9f; e.skyVis.wet = 1f }
        fly(e, "m14_rain", 2.6f, listOf(Key(1003f, 875f, 20f, -1.6f, 7f), Key(1010f, 875f, 19f, -1.52f, 8f)), easing = .2f, warm = 4f, weather = true, haze = .35f,
            crowd = 35f) { f, _ ->
            wet()
            if (f == 0) stage(e, 1003f, 875f, 1f, 0f, parade)
        }
    }

    /** Binondo's arch at night, its lanterns lit. */
    @Test
    fun m15() {
        if (!on("m15")) { assumeTrue(false); return }
        val e = engine("manila", 20.4f)
        val a = landmark(e, "Binondo Arch")
        fly(e, "m15_arch_night", 2.6f, listOf(Key(a.x + 3f, a.y + 4f, 30f, .25f, 8f), Key(a.x, a.y, 37f, -.08f, 10f)), easing = .2f, crowd = 60f, haze = .25f) { _, _ -> clearWeather(e) }
    }

    /** The Cathedral floodlit at night. */
    @Test
    fun m16() {
        if (!on("m16")) { assumeTrue(false); return }
        val e = engine("manila", 20.6f)
        val c = landmark(e, "Cathedral")
        fly(e, "m16_cathedral_night", 2.6f, listOf(Key(c.x, c.y, 118f, 3.12f, 17f), Key(c.x, c.y, 102f, 2.95f, 15f)), easing = .2f, haze = .25f) { _, _ -> clearWeather(e) }
    }

    /** Makati's towers at night down Ayala Avenue. */
    @Test
    fun m17() {
        if (!on("m17")) { assumeTrue(false); return }
        val e = engine("manila", 21.0f)
        fly(e, "m17_makati", 4.6f, listOf(Key(1080f, 1120f, 235f, 2.22f, 15f), Key(1080f, 1120f, 190f, 2.46f, 12f)), easing = .3f, haze = .35f) { _, _ -> clearWeather(e) }
    }

    /** The MOA Eye lit over Manila Bay at night. */
    @Test
    fun m18() {
        if (!on("m18")) { assumeTrue(false); return }
        val e = engine("manila", 20.8f)
        fly(e, "m18_bay_night", 2.6f, listOf(Key(190f, 1150f, 140f, 2.62f, 9f), Key(190f, 1160f, 125f, 2.76f, 10f)), easing = .2f, haze = .3f) { _, _ -> clearWeather(e) }
    }

    /** Binondo Church at night, its belfry lit, the LRT running past. */
    @Test
    fun m19() {
        if (!on("m19")) { assumeTrue(false); return }
        val e = engine("manila", 20.5f)
        val c = landmark(e, "Binondo Church")
        fly(e, "m19_binondo_night", 2.6f, listOf(Key(c.x, c.y, 95f, 2.3f, 18f), Key(c.x, c.y, 82f, 2.5f, 16f)), easing = .2f, haze = .28f) { _, _ -> clearWeather(e) }
    }

    /** Quiapo Church at night over the lit streets. */
    @Test
    fun m20() {
        if (!on("m20")) { assumeTrue(false); return }
        val e = engine("manila", 20.9f)
        val q = landmark(e, "Quiapo")
        fly(e, "m20_quiapo_night", 2.6f, listOf(Key(q.x, q.y, 100f, 3.4f, 15f), Key(q.x, q.y, 88f, 3.62f, 14f)), easing = .2f, haze = .28f) { _, _ -> clearWeather(e) }
    }

    /** The finale: the sun going down over Manila Bay, pulling back over the MOA Eye. */
    @Test
    fun m21() {
        if (!on("m21")) { assumeTrue(false); return }
        val e = engine("manila", 17.95f)
        // back up along Roxas Boulevard, clear of the towers inland, the bay and the sun on the right
        fly(e, "m21_finale", 7.6f, listOf(Key(190f, 1150f, 150f, 3.0f, 8f), Key(191f, 1147f, 195f, 3.03f, 10f), Key(192f, 1144f, 245f, 3.06f, 12f)),
            easing = .5f, haze = .38f) { _, _ -> clearWeather(e) }
    }

    /** A call at night: the police helicopter overhead, its searchlight on a patrol car running with its lights going. */
    @Test
    fun n21() {
        if (!on("n21")) { assumeTrue(false); return }
        val e = engine("nyc", 22.2f)
        val seconds = 1.6f; val warm = 2f
        // the helicopter's path follows from the time, so where it will be is known before the shot starts
        val t0 = e.realTime + warm
        val hel = Helicopter(e.city)
        hel.update(t0)
        // a patrol car (traffic car 3 answers calls) on a street ahead of it, where the searchlight will find it
        val ax = hel.x + sin(hel.heading) * 45f; val ay = hel.y - cos(hel.heading) * 45f
        val ed = e.city.roads.edges.filter { it.cls.rank >= 1 && !it.overWater && it.len > 50f }
            .minByOrNull { hypot((it.ax + it.bx) / 2 - ax, (it.ay + it.by) / 2 - ay) }!!
        val cx = (ed.ax + ed.bx) / 2; val cy = (ed.ay + ed.by) / 2
        // the camera rides behind the helicopter, looking past it down the beam at the car
        val l = hypot(cx - hel.x, cy - hel.y).coerceAtLeast(1f)
        val dx = (cx - hel.x) / l; val dy = (cy - hel.y) / l
        fun at(t: Float, dist: Float, pitch: Float): Key {
            hel.update(t)
            val p = Math.toRadians(pitch.toDouble()).toFloat()
            val cz = hel.z + 1.5f + sin(p) * dist
            val s = cz / sin(p)
            return Key(hel.x - dx * cos(p) * dist + dx * cos(p) * s, hel.y - dy * cos(p) * dist + dy * cos(p) * s, s, look(dx, dy), pitch)
        }
        val keys = listOf(at(t0, 52f, 40f), at(t0 + seconds / 2, 47f, 39f), at(t0 + seconds, 42f, 38f))
        fly(e, "n21_patrol", seconds, keys, easing = 0f, warm = warm, haze = .3f, crowd = 40f) { f, _ ->
            clearWeather(e)
            if (f < 0 && e.traffic.cars.size > 3 && e.traffic.cars[3].edge != ed.id) e.traffic.placeCar(3, ed.id, ed.a, ed.len * .45f, 0)
        }
    }

    // ------------------------------------------------------------------ the banger trailer's action (banger.py)

    /** Traffic cars that answer calls (WorldRenderer's patrol cars with their lights going), as many as there are. */
    private fun respondingCars(e: GameEngine, n: Int): List<Int> {
        val map = e.city.map
        return (0 until e.traffic.cars.size).filter { it % map.taxiEvery != 0 && it % map.policeEvery == 3 && (it / map.policeEvery) % 3 == 0 }.take(n)
    }

    /**
     * Patrol cars on [v]'s tail: each frame they take the place the player's car had [lag] frames before, [side] meters
     * to its right, so they follow its line through the turns with their lights going. The rest of the traffic keeps
     * out of the way (late at night the avenue is the getaway's).
     */
    private fun pursuers(e: GameEngine, v: OwnedVehicle, lag: IntArray, side: FloatArray = floatArrayOf(1.3f, -1.3f)): () -> Unit {
        val ids = respondingCars(e, lag.size)
        println("pursuers: traffic cars $ids of ${e.traffic.cars.size}")
        val trail = ArrayList<FloatArray>()
        return {
            trail += floatArrayOf(v.x, v.y, v.heading, e.carSpeed)
            for ((i, a) in e.traffic.cars.withIndex()) if (i !in ids && hypot(a.x - v.x, a.y - v.y) < 120f) { a.x = -99999f; a.y = -99999f }
            for ((k, id) in ids.withIndex()) {
                val a = e.traffic.cars[id]
                val p = trail[(trail.size - 1 - lag[k]).coerceAtLeast(0)]
                val side = side[k % side.size]
                a.x = p[0] + cos(p[2]) * side; a.y = p[1] + sin(p[2]) * side
                a.heading = p[2]; a.speed = p[3]; a.cruise = p[3]
                a.style = 0; a.length = 4.7f; a.width = 1.9f; a.height = 1.45f
            }
        }
    }

    /** A hypercar on the run at night with two patrol cars behind; [turn] sends it off the avenue into a side street. */
    private fun getaway(e: GameEngine, turn: Boolean): OwnedVehicle {
        // enough traffic that two of its cars are patrol cars on a call (cars 3 and 105)
        e.settings.busyRoads = 2.5f; e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
        val from = ny(520f, 300f)
        val v = startCar(e, "vortex", from[0], from[1], color = 0xFF8E24AA.toInt(), minLen = 300f)
        if (turn) {
            // somewhere off to the right a block and a half ahead: the route turns at the next corners
            val fx = sin(v.heading); val fy = -cos(v.heading)
            val tx = v.x + fx * 110f - fy * 120f; val ty = v.y + fy * 110f + fx * 120f
            e.city.buildings.filter { it.solid }.minByOrNull { hypot(it.doorX - tx, it.doorY - ty) }?.let { e.setWaypoint(it.id) }
        }
        return v
    }

    /** The getaway from in front, low: the hypercar coming at the lens, the patrol cars' lights right behind it. */
    @Test
    fun b01() {
        if (!on("b01")) { assumeTrue(false); return }
        val e = engine("nyc", 22.4f)
        val v = getaway(e, false)
        chase(e, "b01_chase_front", 3.4f, v, 24f, relYaw = { PI.toFloat() + .3f - .25f * it }, dist = { 7.5f - 1.5f * it }, pitch = { 4f + 2f * it },
            lead = 3f, after = pursuers(e, v, intArrayOf(14, 30))) { _, _ -> clearWeather(e) }
    }

    /** The getaway throwing the hypercar sideways round a corner, from the kerb (cut before the camera meets the corner building). */
    @Test
    fun b02() {
        if (!on("b02")) { assumeTrue(false); return }
        val e = engine("nyc", 22.4f)
        val v = getaway(e, true)
        chase(e, "b02_chase_drift", 1.7f, v, 22f, relYaw = { .75f - .2f * it }, dist = { 11f }, pitch = { 13f }, lead = 1f, warm = 2f, drift = true,
            after = pursuers(e, v, intArrayOf(20, 40), floatArrayOf(-1.2f, -1.2f))) { _, _ -> clearWeather(e) }
    }

    /** The getaway from high behind, the patrol cars' lights washing the avenue. */
    @Test
    fun b03() {
        if (!on("b03")) { assumeTrue(false); return }
        val e = engine("nyc", 22.4f)
        val v = getaway(e, false)
        chase(e, "b03_chase_high", 3.0f, v, 24f, relYaw = { .15f }, dist = { 42f - 6f * it }, pitch = { 38f }, lead = 6f,
            after = pursuers(e, v, intArrayOf(16, 34))) { _, _ -> clearWeather(e) }
    }

    /** A racing drone's run down an avenue at golden hour, low between the towers. */
    @Test
    fun b04() {
        if (!on("b04")) { assumeTrue(false); return }
        val e = engine("nyc", 18.2f)
        val ed = e.city.roads.edges.filter { it.cls.rank >= 2 && !it.overWater && it.len > 60f }
            .minByOrNull { hypot((it.ax + it.bx) / 2 - ny(470f, 520f)[0], (it.ay + it.by) / 2 - ny(470f, 520f)[1]) }!!
        val yaw = look(ed.ux, ed.uy)
        fun k(s: Float) = Key(ed.ax + ed.ux * s, ed.ay + ed.uy * s, 30f, yaw, 10f)
        fly(e, "b04_fpv", 2.6f, listOf(k(0f), k(80f), k(170f)), easing = 0f, haze = .35f, crowd = 80f) { _, _ -> clearWeather(e) }
    }

    /** Alex walking out of Times Square at night, straight at a low lens, the screens blazing behind. */
    @Test
    fun b05() {
        if (!on("b05")) { assumeTrue(false); return }
        val e = engine("nyc", 21.6f)
        val p = e.state.player
        val start = ny(390f, 745f)
        p.x = start[0]; p.y = start[1]
        // walking south down Broadway toward a lens at chest height a dozen meters down the street, which looks back up
        // the square past him (its aim lands on the street far behind him) and creeps in as he comes
        val dx = -.4847f; val dy = .8747f
        val pitch = 3f
        val p0 = Math.toRadians(pitch.toDouble()).toFloat()
        val dist = 1.5f / sin(p0)
        val cx = start[0] + dx * 13f; val cy = start[1] + dy * 13f
        track(e, "b05_hero", 3.2f, warm = .5f, crowd = 85f, haze = .3f, each = { _, _ -> clearWeather(e); walkToward(e, dx * .3f, dy * .3f) }) { u ->
            val push = 1.6f * u
            Key(cx - dx * (dist * cos(p0) + push), cy - dy * (dist * cos(p0) + push), dist, look(-dx, -dy), pitch)
        }
    }

    @Test
    fun x1() {
        // a straight-down debug view: HC_X1=map,x,y,dist[,hour]
        val a = System.getenv("HC_X1")?.split(',') ?: run { assumeTrue(false); return }
        val e = engine(a[0], a.getOrNull(4)?.toFloat() ?: 13f)
        val x = a[1].toFloat(); val y = a[2].toFloat(); val d = a[3].toFloat()
        fly(e, "x1_top", .2f, listOf(Key(x, y, d, 0f, 89f), Key(x, y, d, 0f, 89f)), easing = .2f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun x2() {
        // a line-up of vehicles at the curb to look at up close: HC_X2=map,style,style,...
        val a = System.getenv("HC_X2")?.split(',') ?: run { assumeTrue(false); return }
        val e = engine(a[0], 10.5f)
        val g = e.city.roads
        val ed = g.edges.filter { it.cls.rank == 2 && !it.overWater && it.len > 90f }.minByOrNull { hypot((it.ax + it.bx) / 2 - e.camera.tx, (it.ay + it.by) / 2 - e.camera.ty) }!!
        val styles = a.drop(1).map { it.toInt() }
        val mid = ed.len / 2
        // the camera stands across the road looking at the line, from a little ahead
        val cx = ed.ax + ed.ux * mid; val cy = ed.ay + ed.uy * mid
        val yaw = look(-ed.uy, ed.ux) + (System.getenv("HC_X2_TURN")?.toFloat() ?: .5f)
        fly(e, "x2_lineup", .2f, listOf(Key(cx, cy, 15f, yaw, 14f), Key(cx, cy, 15f, yaw, 14f)), easing = .2f, warm = .05f) { f, _ ->
            clearWeather(e)
            if (f <= 0) for ((k, st) in styles.withIndex()) e.traffic.placeCar(k, ed.id, ed.a, mid - 14f + k * 4.6f, st)
            for (k in styles.indices) e.traffic.cars[k].speed = 0f
        }
    }

    @Test
    fun x3() {
        // still looks from any cameras: HC_X3=map;hour;x,y,dist,yaw,pitch;x,y,dist,yaw,pitch;... -> x3_0, x3_1, ...
        val a = System.getenv("HC_X3")?.split(';')?.map { it.trim() } ?: run { assumeTrue(false); return }
        val e = engine(a[0], a[1].toFloat())
        for ((n, cam) in a.drop(2).withIndex()) {
            val v = cam.split(',').map { it.trim().toFloat() }
            val k = Key(v[0], v[1], v[2], v[3], v[4])
            fly(e, "x3_$n", .05f, listOf(k, k), easing = .2f, warm = 1f) { _, _ -> clearWeather(e) }
        }
    }

    /** The finale: Midtown at night, pulling back as the city lights up. */
    @Test
    fun e1() {
        if (!on("e1")) { assumeTrue(false); return }
        val e = engine("nyc", 21.6f)
        // over the tops of the towers the whole way: the tallest stands about 120 m
        fly(e, "e1_finale", 6.6f, listOf(Key(690f, 820f, 250f, 2.6f, 34f), Key(700f, 800f, 330f, 2.85f, 27f), Key(720f, 780f, 440f, 3.05f, 21f)),
            easing = .5f, haze = .4f) { _, _ -> clearWeather(e) }
    }

    // ------------------------------------------------------------------ the New York story trailer (n01 .. n20)

    /** New York's Manhattan grid (u across the island west to east, v down it north to south) in world coordinates. */
    private fun ny(u: Float, v: Float) = floatArrayOf(640f + u * .8747f - v * .4847f, 40f + u * .4847f + v * .8747f)
    private fun nyKey(u: Float, v: Float, dist: Float, yaw: Float, pitch: Float) = ny(u, v).let { Key(it[0], it[1], dist, yaw, pitch) }
    private val east = look(.8747f, .4847f)
    private val west = look(-.8747f, -.4847f)
    private val north = look(.4847f, -.8747f)
    private val south = look(-.4847f, .8747f)

    /**
     * Like [fly], but the camera is whatever [cam] says at each 0..1 of the shot, so it can follow something that
     * moves on its own (the player walking). The player stays where the game puts them.
     */
    private fun track(e: GameEngine, name: String, seconds: Float, warm: Float = 3f, weather: Boolean = false, haze: Float = .3f,
                      crowd: Float = 90f, each: (Int, Float) -> Unit = { _, _ -> }, cam: (Float) -> Key) {
        val out = Out(name)
        val r = WorldRenderer(e).apply { cinematic = true; cinemaHaze = haze; cinemaPlayer = true }
        val frames = (seconds * fps).toInt()
        fun place(u: Float) {
            val k = cam(u)
            val c = e.camera
            c.tx = k.x; c.ty = k.y; c.dist = k.dist; c.zoom = k.dist; c.yaw = k.yaw
            r.cinemaPitch = Math.toRadians(k.pitch.toDouble()).toFloat()
        }
        place(0f)
        e.traffic.update(0f, e.camera.tx, e.camera.ty, 230f, -9999f, -9999f, initial = true)
        e.traffic.scatter(e.camera.tx, e.camera.ty, crowd, .65f)
        repeat((warm * fps).toInt()) { each(-1, 0f); e.frame(1f / fps); place(0f) }
        for (f in 0 until frames) {
            val u = f / (frames - 1f).coerceAtLeast(1f)
            each(f, u)
            e.frame(1f / fps)
            place(u)
            out.frame({ c -> r.draw(c, w, h, 2f) }, { c, g -> r.draw(c, w, h, 3f, g) }, weather)
            checkCamera(e, r, name, f)
        }
        println("$name: ${out.n} frames")
    }

    /** Joystick input that walks the player along world direction (dx, dy), whichever way the camera faces. */
    private fun walkToward(e: GameEngine, dx: Float, dy: Float) {
        val yaw = e.camera.yaw
        e.input.jx = dx * cos(yaw) - dy * sin(yaw)
        e.input.jy = dx * sin(yaw) + dy * cos(yaw)
    }

    private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u

    /** A business up and running in a vacant storefront of [w] x [d] tiles, with [staff] on every shift. */
    private fun openBusiness(e: GameEngine, type: String, name: String, w: IntRange, d: IntRange, staff: List<StaffRole>,
                             served: Double, furnish: (room: Int, w: Int, d: Int) -> List<PlacedItem>): InteriorScene {
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in w && it.gridD in d }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, type, name)!!
        biz.tempClosed = false
        biz.furniture += furnish(biz.backRoom.coerceAtLeast(0), b.gridW, b.gridD)
        for ((pid, _) in biz.type.products) biz.stock[pid] = 400
        for (role in staff) e.state.employees += Employee(e.state.newId(), "Kim", role, .75, 18.0, 18.0, 27, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.economy.servedRate[biz.id] = served
        return e.interior!!
    }

    /** Cold open: dawn on the Financial District from over the harbour, the towers catching the first light. */
    @Test
    fun n01() {
        if (!on("n01")) { assumeTrue(false); return }
        val e = engine("nyc", 6.7f)
        fly(e, "n01_fidi_dawn", 3.0f, listOf(nyKey(330f, 1230f, 210f, north - .25f, 9f), nyKey(345f, 1215f, 175f, north - .12f, 11f)),
            easing = .2f, haze = .42f) { _, _ -> clearWeather(e) }
    }

    /** Cold open: Alex steps out of the walk-up in Hell's Kitchen and heads down the block. */
    @Test
    fun n02() {
        if (!on("n02")) { assumeTrue(false); return }
        val e = engine("nyc", 8.25f)
        val home = e.city.building(e.state.player.home)!!
        val p = e.state.player
        p.x = home.doorX + home.frontX * 1.2f; p.y = home.doorY + home.frontY * 1.2f
        // along the sidewalk, the street on the left
        val ax = -home.frontY; val ay = home.frontX
        track(e, "n02_hk_morning", 3.0f, warm = 3f, crowd = 50f, each = { f, _ ->
            clearWeather(e)
            if (f >= 0) walkToward(e, ax, ay) else { e.input.jx = 0f; e.input.jy = 0f }
        }) { u ->
            // beside and a little ahead of Alex, low, swinging round as he passes
            val cx = p.x + ax * 3f + home.frontX * 2f; val cy = p.y + ay * 3f + home.frontY * 2f
            Key(cx, cy, lerp(11f, 9f, u), look(-ax, -ay) + lerp(.9f, .45f, u), lerp(13f, 10f, u))
        }
    }

    /** Cold open: the morning rush in Midtown, from above the crossing. ("Everybody in this city has a plan.") */
    @Test
    fun n03() {
        if (!on("n03")) { assumeTrue(false); return }
        val e = engine("nyc", 8.6f)
        fly(e, "n03_rush", 3.0f, listOf(nyKey(390f, 620f, 40f, south + .12f, 68f), nyKey(390f, 620f, 35f, south + .22f, 72f)),
            easing = .2f, warm = 6f, haze = .2f, crowd = 75f) { _, _ -> clearWeather(e) }
    }

    /** The Brooklyn Bridge at golden hour, low over the East River. */
    @Test
    fun n04() {
        if (!on("n04")) { assumeTrue(false); return }
        val e = engine("nyc", 18.15f)
        fly(e, "n04_bridge_gold", 2.8f, listOf(nyKey(845f, 1092f, 100f, north + .55f, 9f), nyKey(855f, 1088f, 92f, north + .42f, 10f)),
            easing = .2f, haze = .38f) { _, _ -> clearWeather(e) }
    }

    /** The lake in Central Park at golden hour, Midtown's towers beyond the trees. */
    @Test
    fun n05() {
        if (!on("n05")) { assumeTrue(false); return }
        val e = engine("nyc", 17.9f)
        fly(e, "n05_lake", 2.8f, listOf(nyKey(392f, 150f, 125f, south - .15f, 9f), nyKey(390f, 158f, 115f, south - .05f, 10f)),
            easing = .2f, haze = .4f, crowd = 50f) { _, _ -> clearWeather(e) }
    }

    /** Times Square as the screens come on, from above the crossroads. */
    @Test
    fun n06() {
        if (!on("n06")) { assumeTrue(false); return }
        val e = engine("nyc", 19.55f)
        fly(e, "n06_times_dusk", 2.6f, listOf(nyKey(390f, 760f, 85f, north, 23f), nyKey(390f, 752f, 76f, north + .05f, 21f)),
            easing = .2f, warm = 5f, haze = .25f, crowd = 80f) { _, _ -> clearWeather(e) }
    }

    /** Brownstones on the Upper West Side at sunset, the light raking down the block. */
    @Test
    fun n07() {
        if (!on("n07")) { assumeTrue(false); return }
        val e = engine("nyc", 18.3f)
        fly(e, "n07_brownstones", 2.6f, listOf(nyKey(140f, 186f, 46f, east + .04f, 11f), nyKey(160f, 186f, 42f, east + .02f, 10f)),
            easing = .2f, haze = .3f, crowd = 50f) { _, _ -> clearWeather(e) }
    }

    /** Brooklyn's warehouses at sunset, Manhattan on the skyline behind them. */
    @Test
    fun n08() {
        if (!on("n08")) { assumeTrue(false); return }
        val e = engine("nyc", 18.5f)
        fly(e, "n08_brooklyn", 2.6f, listOf(Key(1150f, 1060f, 115f, look(-1f, -.15f), 15f), Key(1140f, 1050f, 105f, look(-1f, -.05f), 15f)),
            easing = .2f, haze = .35f) { _, _ -> clearWeather(e) }
    }

    /** A delivery van crossing the Manhattan Bridge in the afternoon. */
    @Test
    fun n09() {
        if (!on("n09")) { assumeTrue(false); return }
        val e = engine("nyc", 16.6f)
        val from = ny(855f, 915f)
        val v = startCar(e, "hauler", from[0], from[1], color = 0xFFF2C94C.toInt(), minRank = 2, minLen = 250f, bridge = true)
        chase(e, "n09_van_bridge", 2.6f, v, 12f, relYaw = { u -> .55f - .3f * u }, dist = { 18f }, pitch = { u -> 9f + 2f * u }, lead = 1f) { _, _ -> clearWeather(e) }
    }

    /** Midtown from the air at midday, under the news. */
    @Test
    fun n10() {
        if (!on("n10")) { assumeTrue(false); return }
        val e = engine("nyc", 11.5f)
        fly(e, "n10_news_aerial", 2.6f, listOf(nyKey(420f, 620f, 360f, south + 2.2f, 38f), nyKey(420f, 620f, 350f, south + 2.32f, 37f)),
            easing = .1f, haze = .3f) { _, _ -> clearWeather(e) }
    }

    /** A gym at full stretch: every treadmill, bike and rower taken. */
    @Test
    fun n11() {
        if (!on("n11")) { assumeTrue(false); return }
        val e = engine("nyc", 18.4f)
        val sc = openBusiness(e, "gym", "Iron Works", 12..16, 9..11, listOf(StaffRole.GYM_TRAINER, StaffRole.GYM_TRAINER), 240.0) { room, w, d ->
            listOf(PlacedItem("treadmill", 1, room + 1, 0), PlacedItem("treadmill", 3, room + 1, 0), PlacedItem("treadmill", 5, room + 1, 0),
                PlacedItem("treadmill", 7, room + 1, 0), PlacedItem("exercise_bike", 1, room + 4, 0), PlacedItem("exercise_bike", 3, room + 4, 0),
                PlacedItem("rowing_machine", 5, room + 4, 0), PlacedItem("rowing_machine", 7, room + 4, 0), PlacedItem("gym_locker", w - 3, room, 0),
                PlacedItem("shower", w - 1, room, 0), PlacedItem("fitness_board", w - 4, room, 0), PlacedItem("drinks_fridge", w - 2, d - 2, 0))
        }
        inside(e, "n11_gym", 2.6f, { u -> floatArrayOf(sc.w * (.35f + .08f * u), sc.d * .45f, 12f - 1.5f * u, -.9f + .25f * u, 36f) }, warm = 30f)
    }

    /** A nightclub at midnight: the floor packed, the DJ on, the bar three deep. */
    @Test
    fun n12() {
        if (!on("n12")) { assumeTrue(false); return }
        val e = engine("nyc", 23.6f)
        val sc = openBusiness(e, "nightclub", "Club Neon", 12..16, 9..11, listOf(StaffRole.DJ, StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE), 300.0) { room, w, d ->
            listOf(PlacedItem("dj_booth", 4, room, 0), PlacedItem("dance_floor", 3, room + 2, 0), PlacedItem("dance_floor", 6, room + 2, 0),
                PlacedItem("cocktail_bar", w - 4, room, 0), PlacedItem("stage_lights", 2, room, 0), PlacedItem("stage_lights", 7, room, 0),
                PlacedItem("lounge_sofa", 0, d - 2, 0), PlacedItem("lounge_sofa", w - 3, d - 2, 0))
        }
        inside(e, "n12_club", 2.6f, { u -> floatArrayOf(sc.w * .42f, sc.d * (.42f + .05f * u), 11f - 1.5f * u, .45f - .3f * u, 34f) }, warm = 30f)
    }

    /** A café on 10th Avenue with a line out the door (the phone clip). */
    @Test
    fun n13() {
        if (!on("n13")) { assumeTrue(false); return }
        val e = engine("nyc", 9.1f)
        val sc = openBusiness(e, "coffee", "Tenth & Bean", 10..14, 8..10, listOf(StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE), 260.0) { room, w, d ->
            listOf(PlacedItem("coffee_machine", 1, room, 0), PlacedItem("coffee_machine", 3, room, 0), PlacedItem("pastry_case", 5, room, 0),
                PlacedItem("cash_register", 7, room, 0), PlacedItem("table_set", 1, room + 4, 0), PlacedItem("table_set", 4, room + 4, 0),
                PlacedItem("table_set", 7, room + 4, 0), PlacedItem("plant_large", w - 1, d - 1, 0), PlacedItem("floor_lamp", 0, d - 1, 0))
        }
        inside(e, "n13_cafe", 2.6f, { u -> floatArrayOf(sc.w * .5f, sc.d * .5f, 10f - 1f * u, 2.6f + .25f * u, 30f) }, warm = 30f)
    }

    /** The goods on the shelves, store by store, close up: a frame or two each (a check on the HD product shapes). */
    @Test
    fun p1() {
        if (!on("p1")) { assumeTrue(false); return }
        val stores = listOf(
            "supermarket" to listOf("drinks_fridge", "shelf", "ice_cream_freezer", "shelf"),
            "liquor" to listOf("drinks_fridge", "shelf", "shelf"),
            "produce" to listOf("produce", "produce", "produce"),
            "florist" to listOf("flower_stand", "flower_stand", "flower_stand"),
            "coffee" to listOf("coffee_machine", "pastry_case", "pastry_case"),
            "fastfood" to listOf("grill", "fryer", "pizza_oven", "drinks_fridge"),
            "cinema" to listOf("concession_stand", "concession_stand"),
            "nightclub" to listOf("cocktail_bar"),
        )
        for ((type, pieces) in stores) {
            val e = engine("nyc", 11f)
            val sc = openBusiness(e, type, "Shop", 10..22, 7..16, emptyList(), 0.0) { room, _, _ ->
                var x = 1
                pieces.map { id -> PlacedItem(id, x, room + 1, 0).also { x += (Catalog.item(id)?.w ?: 1) + 1 } }
            }
            val biz = e.state.businessAt(sc.building.id)!!
            for ((pid, _) in biz.type.products) biz.stock[pid] = 400
            val span = pieces.sumOf { (Catalog.item(it)?.w ?: 1) + 1 }.toFloat()
            inside(e, "p1_$type", .1f, { _ -> floatArrayOf(1f + span * .5f, biz.backRoom.coerceAtLeast(0) + 2.2f, 4.6f, .45f, 38f) }, warm = 1f)
        }
    }

    /** A rainy night crossing in Midtown: umbrellas, headlights, the street a mirror. */
    @Test
    fun n14() {
        if (!on("n14")) { assumeTrue(false); return }
        val e = engine("nyc", 21.5f, Sky.RAIN)
        fly(e, "n14_rain_cross", 2.6f, listOf(nyKey(520f, 682f, 40f, south + .12f, 32f), nyKey(520f, 682f, 36f, south + .22f, 29f)),
            easing = .2f, warm = 5f, weather = true, haze = .4f, crowd = 70f) { _, _ -> e.skyVis.rain = .9f; e.skyVis.cloud = .9f; e.skyVis.wet = 1f }
    }

    /** A hypercar down an empty avenue at night, low and fast. */
    @Test
    fun n15() {
        if (!on("n15")) { assumeTrue(false); return }
        val e = engine("nyc", 22.3f)
        e.settings.busyRoads = 1.2f; e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
        val from = ny(520f, 300f)
        val v = startCar(e, "vortex", from[0], from[1], color = 0xFF8E24AA.toInt(), minLen = 300f)
        chase(e, "n15_hypercar", 2.6f, v, 20f, relYaw = { u -> -2.3f + .7f * u }, dist = { u -> 9f + 1.5f * u }, pitch = { 7f }, lead = .5f, haze = .25f) { _, _ -> clearWeather(e) }
    }

    /** The East River at night, the Manhattan and Brooklyn bridges lit end to end. */
    @Test
    fun n16() {
        if (!on("n16")) { assumeTrue(false); return }
        val e = engine("nyc", 21.7f)
        fly(e, "n16_river_night", 2.6f, listOf(nyKey(858f, 860f, 260f, south + .15f, 11f), nyKey(858f, 875f, 245f, south + .05f, 12f)),
            easing = .2f, haze = .4f) { _, _ -> clearWeather(e) }
    }

    /** Snow on a Hell's Kitchen street. */
    @Test
    fun n17() {
        if (!on("n17")) { assumeTrue(false); return }
        val e = engine("nyc", 11.0f, Sky.SNOW)
        e.state.snowCover = 1.0
        fly(e, "n17_snow", 2.6f, listOf(nyKey(100f, 620f, 48f, east + .06f, 20f), nyKey(120f, 620f, 44f, east + .02f, 18f)), easing = .2f, weather = true,
            haze = .45f, crowd = 60f) { _, _ -> e.state.snowCover = 1.0; e.skyVis.snow = .85f; e.skyVis.cloud = .85f }
    }

    /** Down into the Financial District at night: light trails between the towers. */
    @Test
    fun n18() {
        if (!on("n18")) { assumeTrue(false); return }
        val e = engine("nyc", 21.9f)
        fly(e, "n18_fidi_night", 2.6f, listOf(nyKey(420f, 1170f, 140f, north + 1.6f, 52f), nyKey(420f, 1170f, 120f, north + 1.85f, 50f)),
            easing = .2f, haze = .3f) { _, _ -> clearWeather(e) }
    }

    /** The whole skyline at night from over the Hudson. */
    @Test
    fun n19() {
        if (!on("n19")) { assumeTrue(false); return }
        val e = engine("nyc", 21.2f)
        fly(e, "n19_skyline_night", 2.8f, listOf(nyKey(260f, 760f, 520f, east - .2f, 12f), nyKey(260f, 720f, 500f, east - .05f, 13f)),
            easing = .2f, haze = .45f) { _, _ -> clearWeather(e) }
    }

    /** Finale: sunset from the Brooklyn shore. Alex looks across the river at Manhattan, and the camera rises past him. */
    @Test
    fun n20() {
        if (!on("n20")) { assumeTrue(false); return }
        val e = engine("nyc", 18.55f)
        val p = e.state.player
        val spot = ny(936f, 980f)
        p.x = spot[0]; p.y = spot[1]
        p.heading = atan2(-.8747f, .4847f) // facing west, across the river
        // the camera looks over his shoulder at the skyline, pulling up and back
        val wx = -.8747f; val wy = -.4847f
        track(e, "n20_finale", 8.0f, warm = 3f, crowd = 20f, haze = .4f, each = { _, _ -> clearWeather(e); p.x = spot[0]; p.y = spot[1]; e.input.jx = 0f; e.input.jy = 0f }) { u ->
            val k = ease(u, .7f)
            // the target runs out over the river as the camera rises, so the camera itself stays over the open waterfront
            // low at first, Alex on the left third against the skyline; then up and away over the river
            Key(spot[0] + wx * lerp(7f, 135f, k), spot[1] + wy * lerp(7f, 135f, k), lerp(12f, 145f, k), west + lerp(.25f, .03f, k), lerp(6f, 10f, k))
        }
    }

    // ------------------------------------------------------------------ the life trailer (l01 .. l56, tools/cinema/life.py)

    /** Where the camera of [k] stands: x, y and height. */
    private fun camAt(k: Key): FloatArray {
        val p = Math.toRadians(k.pitch.toDouble()).toFloat()
        val hd = k.dist * cos(p)
        return floatArrayOf(k.x + sin(k.yaw) * hd, k.y + cos(k.yaw) * hd, k.dist * sin(p))
    }

    /** True when the camera of [k] stands [margin] meters or more clear of every building it is below the top of. */
    private fun clear(e: GameEngine, k: Key, margin: Float = 1.6f): Boolean {
        val c = camAt(k)
        var ok = true
        e.city.forBuildingsIn(c[0] - margin - 1f, c[1] - margin - 1f, c[0] + margin + 1f, c[1] + margin + 1f) { b ->
            if (b.height > 0f && c[2] < b.height + 1.5f)
                for (dx in floatArrayOf(-margin, 0f, margin)) for (dy in floatArrayOf(-margin, 0f, margin)) if (b.contains(c[0] + dx, c[1] + dy)) ok = false
        }
        return ok
    }

    /** Fewer people on the streets, for shots down among them: the trailer's usual crowds stand in rows at eye level. */
    private fun thin(e: GameEngine, k: Float = 3f) { e.settings.crowds = k; e.traffic.ensure(e.settings.carCount, e.settings.pedCount) }

    /** True when nothing tall (a tree, a lamp, a pole, a shelter) stands between the camera of [k] and its target. */
    private fun sightClear(e: GameEngine, k: Key): Boolean {
        val c = camAt(k)
        val dx = k.x - c[0]; val dy = k.y - c[1]
        val l2 = (dx * dx + dy * dy).coerceAtLeast(1e-4f)
        fun off(px: Float, py: Float, upTo: Float): Float {
            val t = (((px - c[0]) * dx + (py - c[1]) * dy) / l2).coerceIn(0f, upTo)
            return hypot(c[0] + dx * t - px, c[1] + dy * t - py)
        }
        if (e.city.trees.any { abs(it.x - c[0]) < 40f && abs(it.y - c[1]) < 40f && off(it.x, it.y, .85f) < it.r + .7f }) return false
        val pr = e.props
        for (i in 0 until pr.count) {
            val kd = pr.kind[i]
            if (kd != StreetProps.LAMP && kd != StreetProps.UTIL_POLE && kd != StreetProps.BUS_STOP && kd != StreetProps.NEWS &&
                kd != StreetProps.MAILBOX && kd != StreetProps.VENDING && kd != StreetProps.PLANTER) continue
            if (abs(pr.x[i] - c[0]) > 30f || abs(pr.y[i] - c[1]) > 30f) continue
            if (off(pr.x[i], pr.y[i], .8f) < .9f) return false
        }
        return true
    }

    /** The street food carts nearest (x, y), nearest first, as indices into the engine's props. */
    private fun cartsNear(e: GameEngine, x: Float, y: Float): List<Int> {
        val pr = e.props
        return (0 until pr.count).filter { pr.kind[it] == StreetProps.VENDOR }.sortedBy { hypot(pr.x[it] - x, pr.y[it] - y) }.take(12)
    }

    private fun cartNear(e: GameEngine, x: Float, y: Float, skip: Int = 0) = cartsNear(e, x, y)[skip]

    /** The camera keys of a three-quarter view of cart [i] from along the pavement, in from its side by [a] radians. */
    private fun cartKeys(e: GameEngine, i: Int, side: Float, a: Float, dist: Float, pitch: Float): List<Key> {
        val pr = e.props
        val dx = pr.dirX[i]; val dy = pr.dirY[i]
        val ux = -dy * side; val uy = dx * side
        val cx = pr.x[i] - dx * .3f; val cy = pr.y[i] - dy * .3f
        // looking back up the street and in toward the road: the camera stands out along the pavement
        val yaw = look(-ux * cos(a) + dx * sin(a), -uy * cos(a) + dy * sin(a))
        return listOf(Key(cx + ux * .5f, cy + uy * .5f, dist, yaw + .1f * side, pitch), Key(cx - ux * .3f, cy - uy * .3f, dist * .88f, yaw - .06f * side, pitch - 1.5f))
    }

    /**
     * A street food cart close up, the [nth] of the carts near (x, y) that the camera gets a clear view of: from along the
     * pavement and in from its side as far as the buildings allow, so the cook's face shows over the counter and the
     * people buying stand side-on, the camera creeping past.
     */
    private fun cartShot(e: GameEngine, name: String, seconds: Float, x: Float, y: Float, nth: Int = 0, dist: Float = 8f, pitch: Float = 19f,
                         warm: Float = 8f, crowd: Float = 45f, haze: Float = .22f, weather: Boolean = false,
                         each: (Int, Float) -> Unit = { _, _ -> clearWeather(e) }) {
        thin(e)
        val views = ArrayList<Pair<Int, List<Key>>>()
        for (i in cartsNear(e, x, y)) {
            var best: List<Key>? = null; var bestA = -1f
            for (side in floatArrayOf(1f, -1f)) for (a in floatArrayOf(.8f, .65f, .5f, .35f, .2f)) {
                if (a <= bestA) continue
                val k = cartKeys(e, i, side, a, dist, pitch)
                if (k.all { clear(e, it) && sightClear(e, it) }) { best = k; bestA = a }
            }
            if (best != null && bestA >= .35f) views += i to best
        }
        val (i, keys) = views.getOrNull(nth) ?: error("$name: no clear view of a cart")
        println("$name: cart at ${e.props.x[i].toInt()},${e.props.y[i].toInt()}")
        fly(e, name, seconds, keys, easing = .2f, warm = warm, haze = haze, crowd = crowd, weather = weather, each = each)
    }

    /**
     * Indoors, close on someone [who] picks (picked again if they leave), from in front of them and [turn] radians round;
     * the camera eases after them and creeps in. With [player], close on you instead.
     */
    private fun closeUp(e: GameEngine, name: String, seconds: Float, dist: Float = 5.6f, pitch: Float = 34f, turn: Float = .55f, warm: Float = 30f,
                        player: Boolean = false, lift: Float = .9f, each: (Int) -> Unit = {}, who: (InteriorScene) -> Person? = { null }) {
        val sc = e.interior!!
        var p: Person? = null
        var tx = Float.NaN; var ty = 0f; var yaw = 0f
        inside(e, name, seconds, { u ->
            if (!player && (p == null || (p !in sc.staff && p !in sc.visitors))) p = who(sc)
            val q = p
            val x = if (player) sc.px else q?.x ?: sc.w / 2f
            val y = if (player) sc.py else q?.y ?: sc.d / 2f
            val h = if (player) sc.playerHeading else q?.heading ?: 0f
            if (tx.isNaN()) { tx = x; ty = y; yaw = PI.toFloat() - h + turn } else { tx += (x - tx) * .06f; ty += (y - ty) * .06f }
            // a little toward the camera from their feet, so their head and hands sit in the middle of the frame
            floatArrayOf(tx - sin(yaw) * lift, ty - cos(yaw) * lift, dist * (1f - .1f * u), yaw - .14f * u, pitch)
        }, warm = warm, each = each)
    }

    private fun hire(e: GameEngine, sc: InteriorScene, role: StaffRole) {
        e.state.employees += Employee(e.state.newId(), "Jo", role, .75, 18.0, 18.0, 27, businessId = e.state.businessAt(sc.building.id)!!.id, shiftStart = 0, shiftEnd = 23, days = 127)
    }

    /** A salon: three chairs and a wash station along the back, a waiting sofa and chairs by the window. */
    private fun salon(e: GameEngine, rate: Double = 260.0) = openBusiness(e, "hairdresser", "Golden Cuts", 11..15, 10..13,
        listOf(StaffRole.HAIRSTYLIST, StaffRole.HAIRSTYLIST, StaffRole.CUSTOMER_SERVICE), rate) { room, w, d ->
        listOf(PlacedItem("checkout_counter", 1, room + 1, 0), PlacedItem("cash_register", 1, room + 1, 0),
            PlacedItem("hair_chair", 3, room + 5, 0), PlacedItem("hair_chair", 5, room + 5, 0), PlacedItem("hair_chair", 7, room + 5, 0),
            PlacedItem("hair_wash", 9, room + 5, 0), PlacedItem("sofa", 5, room + 1, 0), PlacedItem("waiting_chairs", 7, room + 1, 0),
            PlacedItem("plant_large", w - 1, d - 1, 0), PlacedItem("plant_large", 0, d - 1, 0))
    }

    /** A gym with a row of bags, a dumbbell rack and a line of rowers. */
    private fun boxingGym(e: GameEngine) = openBusiness(e, "gym", "Iron Works", 12..16, 9..11, listOf(StaffRole.GYM_TRAINER, StaffRole.GYM_TRAINER), 260.0) { room, w, d ->
        listOf(PlacedItem("boxing_bag", 1, room + 1, 0), PlacedItem("boxing_bag", 3, room + 1, 0), PlacedItem("boxing_bag", 5, room + 1, 0),
            PlacedItem("weight_rack", 7, room + 1, 0), PlacedItem("treadmill", 10, room + 1, 0),
            PlacedItem("rowing_machine", 1, room + 4, 0), PlacedItem("rowing_machine", 3, room + 4, 0), PlacedItem("rowing_machine", 5, room + 4, 0),
            PlacedItem("workout_bench", 7, room + 4, 0), PlacedItem("exercise_bike", 9, room + 4, 0),
            PlacedItem("gym_locker", w - 3, room, 0), PlacedItem("fitness_board", w - 4, room, 0), PlacedItem("drinks_fridge", w - 2, d - 2, 0))
    }

    /** A burger joint: grill, fryer and pizza oven behind the counter, order kiosks and tables out front. */
    private fun burgers(e: GameEngine) = openBusiness(e, "fastfood", "Big Bite", 12..16, 9..11, listOf(StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE), 260.0) { room, w, d ->
        listOf(PlacedItem("grill", 1, room, 0), PlacedItem("fryer", 3, room, 0), PlacedItem("pizza_oven", 4, room, 0), PlacedItem("cash_register", 7, room + 1, 0),
            PlacedItem("order_kiosk", 10, room + 1, 0), PlacedItem("order_kiosk", 11, room + 1, 0), PlacedItem("drinks_fridge", w - 1, room, 0),
            PlacedItem("table_set", 1, room + 4, 0), PlacedItem("table_set", 4, room + 4, 0), PlacedItem("table_set", 7, room + 4, 0),
            PlacedItem("table_set", 10, room + 4, 0), PlacedItem("plant_large", w - 1, d - 1, 0))
    }

    private fun inItem(sc: InteriorScene, v: Person, id: String): Boolean {
        val it = sc.items.getOrNull(v.item) ?: return false
        return it.itemId == id && hypot(v.x - (it.x + .5f), v.y - (it.y + .5f)) < 2.2f
    }

    // the morning

    /** Dawn over Manila Bay. */
    @Test
    fun l01() {
        if (!on("l01")) { assumeTrue(false); return }
        val e = engine("manila", 6.35f)
        fly(e, "l01_dawn", 3.6f, listOf(Key(330f, 700f, 300f, -1.38f, 9f), Key(342f, 692f, 250f, -1.27f, 11f)), easing = .2f, haze = .4f) { _, _ -> clearWeather(e) }
    }

    /** The first cart of the day, the cook at the grill. */
    @Test
    fun l02() {
        if (!on("l02")) { assumeTrue(false); return }
        val e = engine(com.hustlecity.game.world.Maps.CLASSIC, 7.1f)
        cartShot(e, "l02_cart_dawn", 3f, 640f, 560f, nth = 0)
    }

    /** Shibuya's scramble at the morning rush. */
    @Test
    fun l03() {
        if (!on("l03")) { assumeTrue(false); return }
        val e = engine("tokyo", 8.4f)
        val s = landmark(e, "Shibuya")
        val node = e.city.scrambleNode(s)!!
        val warm = 26f
        e.traffic.lightClock = ((34.5f - node.phase - (warm - 2f)) % Traffic.SCRAMBLE_CYCLE + Traffic.SCRAMBLE_CYCLE) % Traffic.SCRAMBLE_CYCLE
        fly(e, "l03_scramble_am", 3f, listOf(Key(s.x, s.y, 50f, 2.4f, 40f), Key(s.x, s.y, 44f, 2.6f, 36f)), easing = .2f, warm = warm, haze = .2f, crowd = 45f) { _, _ -> clearWeather(e) }
    }

    /** Alex steps out of home into the morning and heads off down the block. */
    @Test
    fun l04() {
        if (!on("l04")) { assumeTrue(false); return }
        val e = engine(com.hustlecity.game.world.Maps.CLASSIC, 7.9f)
        thin(e)
        val home = e.city.building(e.state.player.home)!!
        val p = e.state.player
        p.x = home.doorX + home.frontX * 1.2f; p.y = home.doorY + home.frontY * 1.2f
        val ax = -home.frontY; val ay = home.frontX
        track(e, "l04_wake", 3.4f, warm = 3f, crowd = 40f, each = { f, _ ->
            clearWeather(e)
            if (f >= 0) walkToward(e, ax * .45f, ay * .45f) else { e.input.jx = 0f; e.input.jy = 0f }
        }) { u ->
            val cx = p.x + ax * 3.5f + home.frontX * 2.2f; val cy = p.y + ay * 3.5f + home.frontY * 2.2f
            Key(cx, cy, lerp(9f, 8f, u), look(-ax, -ay) + lerp(.85f, .5f, u), lerp(12f, 10f, u))
        }
    }

    // the cities, each from the air and then on the street

    @Test
    fun l05() {
        if (!on("l05")) { assumeTrue(false); return }
        val e = engine("nyc", 9.6f)
        fly(e, "l05_nyc_air", 2.4f, listOf(Key(880f, 520f, 330f, look(-150f, 260f) + .25f, 14f), Key(860f, 560f, 300f, look(-150f, 260f) + .12f, 16f)), easing = .2f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun l06() {
        if (!on("l06")) { assumeTrue(false); return }
        val e = engine("nyc", 10.2f)
        val c = ny(390f, 700f)
        cartShot(e, "l06_nyc_cart", 2.4f, c[0], c[1], nth = 0)
    }

    @Test
    fun l07() {
        if (!on("l07")) { assumeTrue(false); return }
        val e = engine("manila", 10.8f)
        val ax = .17f; val ay = .985f
        fly(e, "l07_manila_jeep", 2.4f, listOf(Key(236f, 268f, 21f, look(ax, ay) + .1f, 10f), Key(237f, 275f, 20f, look(ax, ay) - .05f, 10f)), easing = .2f, warm = 4f,
            crowd = 60f) { f, _ ->
            clearWeather(e)
            if (f == 0) stage(e, 236f, 268f, ax, ay, parade)
        }
    }

    @Test
    fun l08() {
        if (!on("l08")) { assumeTrue(false); return }
        val e = engine("manila", 11.4f)
        cartShot(e, "l08_manila_cart", 2.4f, 292f, 388f, nth = 0)
    }

    @Test
    fun l09() {
        if (!on("l09")) { assumeTrue(false); return }
        val e = engine("tokyo", 12.4f)
        val t = landmark(e, "Tokyo Tower")
        fly(e, "l09_tokyo_air", 2.4f, listOf(Key(t.x - 20f, t.y - 30f, 290f, look(119f, 176f) - .2f, 10f), Key(t.x - 12f, t.y - 22f, 270f, look(119f, 176f) - .05f, 12f)),
            easing = .2f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun l10() {
        if (!on("l10")) { assumeTrue(false); return }
        val e = engine("tokyo", 13.0f)
        val s = landmark(e, "Shibuya")
        cartShot(e, "l10_tokyo_yatai", 2.4f, s.x, s.y, nth = 0)
    }

    @Test
    fun l11() {
        if (!on("l11")) { assumeTrue(false); return }
        val e = engine("seoul", 14.2f)
        val g = landmark(e, "Gwanghwamun")
        fly(e, "l11_seoul_air", 2.4f, listOf(Key(g.x - 10f, g.y + 10f, 125f, .35f, 14f), Key(g.x, g.y + 4f, 112f, .15f, 16f)), easing = .2f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun l12() {
        if (!on("l12")) { assumeTrue(false); return }
        val e = engine("seoul", 14.6f)
        cartShot(e, "l12_seoul_pocha", 2.4f, 700f, 480f, nth = 0)
    }

    @Test
    fun l13() {
        if (!on("l13")) { assumeTrue(false); return }
        val e = engine(com.hustlecity.game.world.Maps.CLASSIC, 15.2f)
        fly(e, "l13_classic_air", 2.4f, listOf(Key(640f, 760f, 300f, -.15f, 15f), Key(648f, 715f, 275f, .05f, 17f)), easing = .2f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun l14() {
        if (!on("l14")) { assumeTrue(false); return }
        val e = engine(com.hustlecity.game.world.Maps.CLASSIC, 15.6f)
        cartShot(e, "l14_classic_cart", 2.4f, 420f, 300f, nth = 0)
    }

    // the work

    /** The stylist at the chair, cutting. */
    @Test
    fun l20() {
        if (!on("l20")) { assumeTrue(false); return }
        val e = engine("nyc", 11f)
        salon(e)
        closeUp(e, "l20_salon", 2.6f, dist = 5.2f, pitch = 32f, turn = .7f, warm = 40f) { s ->
            s.staff.filter { it.job == Job.HAIR }.minByOrNull { st -> s.visitors.filter { it.seated }.minOfOrNull { hypot(it.x - st.x, it.y - st.y) } ?: 99f }
        }
    }

    /** The waiting sofa: clients on their phones until a chair comes free. */
    @Test
    fun l21() {
        if (!on("l21")) { assumeTrue(false); return }
        val e = engine("nyc", 11f)
        val sc = salon(e, 420.0)
        val room = e.state.businessAt(sc.building.id)!!.backRoom.coerceAtLeast(0)
        // the sofa and chairs along the back face into the salon: the camera stands out in the room, looking back at them
        inside(e, "l21_waiting", 2.6f, { u -> floatArrayOf(7f + .4f * u, room + 1.8f, 5.6f - .5f * u, .4f - .15f * u, 30f) }, warm = 50f)
    }

    /** On the till: scan, bag, pay. */
    @Test
    fun l22() {
        if (!on("l22")) { assumeTrue(false); return }
        val e = engine("nyc", 13.5f)
        supermarket(e)
        closeUp(e, "l22_till", 2.6f, turn = -.6f, warm = 30f) { s -> s.staff.firstOrNull { it.job == Job.TILL } }
    }

    /** The self-checkouts: scan your own, tap your card. */
    @Test
    fun l23() {
        if (!on("l23")) { assumeTrue(false); return }
        val e = engine("nyc", 13.5f)
        supermarket(e)
        val sc = e.interior!!
        val biz = e.state.businessAt(sc.building.id)!!
        biz.furniture += listOf(PlacedItem("self_checkout", sc.w - 2, sc.d - 4, 0), PlacedItem("self_checkout", sc.w - 3, sc.d - 4, 0))
        e.interior = InteriorScene(e, sc.building, InteriorMode.BUSINESS)
        closeUp(e, "l23_selfcheck", 2.6f, turn = .6f, warm = 40f) { s -> s.visitors.firstOrNull { it.counter >= 0 && s.isSelfService(it.counter) && it.payTime > 0f } }
    }

    /** The bags. */
    @Test
    fun l24() {
        if (!on("l24")) { assumeTrue(false); return }
        val e = engine("nyc", 18.4f)
        boxingGym(e)
        closeUp(e, "l24_boxing", 2.6f, dist = 5.4f, pitch = 28f, turn = 1.1f, warm = 40f) { s -> s.visitors.firstOrNull { inItem(s, it, "boxing_bag") } }
    }

    /** The trainer at the rowers. */
    @Test
    fun l25() {
        if (!on("l25")) { assumeTrue(false); return }
        val e = engine("nyc", 18.4f)
        boxingGym(e)
        closeUp(e, "l25_coach", 2.6f, dist = 6f, turn = 1.4f, warm = 40f) { s -> s.staff.firstOrNull { it.job == Job.TRAIN } }
    }

    /** Alex buys a hot dog off a cart and eats it walking on. */
    @Test
    fun l26() {
        if (!on("l26")) { assumeTrue(false); return }
        val e = engine("nyc", 12.4f)
        thin(e)
        val c = ny(390f, 700f)
        val pr = e.props
        // the camera stands up the pavement ahead of him, a little out toward the street, looking back at him
        fun cam(i: Int, side: Float, fx: Float, fy: Float, u: Float): Key {
            val dx = pr.dirX[i]; val dy = pr.dirY[i]; val ux = -dy * side; val uy = dx * side
            return Key(fx, fy, lerp(6.4f, 6.8f, u), look(-ux * .9f + dx * .45f, -uy * .9f + dy * .45f) - .1f * side, 16f)
        }
        fun spot(i: Int, side: Float) = floatArrayOf(pr.x[i] - pr.dirX[i] * StreetProps.SERVE_AT - pr.dirY[i] * side * .9f, pr.y[i] - pr.dirY[i] * StreetProps.SERVE_AT + pr.dirX[i] * side * .9f)
        val (i, side) = cartsNear(e, c[0], c[1]).flatMap { i -> listOf(i to 1f, i to -1f) }.first { (i, side) ->
            val sp = spot(i, side)
            // clear where he pays and where he will have walked to
            (0..1).all { k ->
                val w = k * 3.5f
                val px = sp[0] - pr.dirY[i] * side * w; val py = sp[1] + pr.dirX[i] * side * w
                cam(i, side, px, py, k.toFloat()).let { clear(e, it) && sightClear(e, it) }
            }
        }
        val dx = pr.dirX[i]; val dy = pr.dirY[i]
        val ux = -dy * side; val uy = dx * side
        val p = e.state.player
        val sp = spot(i, side)
        val sx = sp[0]; val sy = sp[1]
        p.x = sx; p.y = sy
        p.heading = atan2(dx, -dy)
        p.money = p.money.coerceAtLeast(500.0)
        var bought = false
        var fx = sx; var fy = sy
        println("l26_pay: cart at ${pr.x[i].toInt()},${pr.y[i].toInt()}")
        track(e, "l26_pay", 3.8f, warm = 6f, crowd = 45f, each = { f, _ ->
            clearWeather(e)
            if (f < 24) { e.input.jx = 0f; e.input.jy = 0f; p.x = sx; p.y = sy; p.heading = atan2(dx, -dy) }
            if (f == 4 && !bought) { bought = true; e.buyStreetFood(StreetFood.menu("nyc")[0]); e.ui.toasts.clear() }
            if (f >= 30) walkToward(e, ux * .38f, uy * .38f)
        }) { u ->
            fx += (p.x - fx) * .1f; fy += (p.y - fy) * .1f
            cam(i, side, fx, fy, u)
        }
    }

    /** Lunch at the café: every table taken. */
    @Test
    fun l27() {
        if (!on("l27")) { assumeTrue(false); return }
        val e = engine("seoul", 12.5f)
        openBusiness(e, "coffee", "Morning Ritual", 11..14, 8..10, listOf(StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE), 260.0) { room, wd, d ->
            listOf(PlacedItem("coffee_machine", 1, room, 0), PlacedItem("coffee_machine", 3, room, 0), PlacedItem("pastry_case", 5, room, 0),
                PlacedItem("pastry_case", 6, room, 0), PlacedItem("cash_register", 8, room, 0),
                PlacedItem("table_set", 1, room + 3, 0), PlacedItem("table_set", 4, room + 3, 0), PlacedItem("table_set", 7, room + 3, 0),
                PlacedItem("table_set", 1, room + 6, 0), PlacedItem("table_set", 4, room + 6, 0),
                PlacedItem("plant_large", wd - 1, d - 1, 0), PlacedItem("plant_large", 0, d - 1, 0), PlacedItem("floor_lamp", wd - 2, room + 3, 0))
        }
        val sc = e.interior!!
        inside(e, "l27_cafe", 2.6f, { u -> floatArrayOf(sc.w * .42f, sc.d * (.62f - .04f * u), 7.5f - 1f * u, .5f - .25f * u, 34f) }, warm = 50f)
    }

    /** The cleaner mopping the aisle. */
    @Test
    fun l28() {
        if (!on("l28")) { assumeTrue(false); return }
        val e = engine("nyc", 13.5f)
        supermarket(e)
        val sc = e.interior!!
        val biz = e.state.businessAt(sc.building.id)!!
        // a spill by the checkout: six tiles, enough that the mop stays on them the whole shot
        biz.dirt = MutableList(sc.w * sc.d) { k -> if (k / sc.w in sc.d - 4..sc.d - 3 && k % sc.w in sc.w / 2 - 2..sc.w / 2) 30f else 0f }
        // from the front of the shop, looking down over the spill as the cleaner works across it
        inside(e, "l28_mop", 2.6f, { u -> floatArrayOf(sc.w / 2f - .5f + .4f * u, sc.d - 3.2f, 6.2f - .6f * u, .3f - .15f * u, 40f) }, warm = 8f)
    }

    /** Restocking the shelves from the back. */
    @Test
    fun l29() {
        if (!on("l29")) { assumeTrue(false); return }
        val e = engine("nyc", 13.5f)
        supermarket(e)
        val biz = e.state.businessAt(e.interior!!.building.id)!!
        val kinds = biz.stock.keys.toList()
        for (k in kinds) { biz.stock[k] = 3; biz.backStock[k] = 200 }
        closeUp(e, "l29_stock", 2.6f, turn = .6f, warm = 14f, each = { f ->
            if ((f + 1000) % 36 == 0) {
                val k = kinds[((f + 1000) / 36) % kinds.size]
                biz.stock[k] = (biz.stock[k] ?: 0) + 18; biz.backStock[k] = (biz.backStock[k] ?: 0) - 18
            }
        }) { s -> s.staff.firstOrNull { it.job == Job.STOCK } ?: s.staff.firstOrNull { it.carry != 0 } }
    }

    /** Seoul's lunchtime crowd: friends talking, a call, a phone out. */
    @Test
    fun l30() {
        if (!on("l30")) { assumeTrue(false); return }
        val e = engine("seoul", 12.8f)
        cartShot(e, "l30_seoul_lunch", 2.6f, 700f, 480f, nth = 0, dist = 7f, pitch = 16f)
    }

    /** On stage. */
    @Test
    fun l31() {
        if (!on("l31")) { assumeTrue(false); return }
        val e = engine("nyc", 20f)
        openBusiness(e, "theater", "The Gilded", 12..16, 12..15, listOf(StaffRole.ACTOR, StaffRole.ACTOR, StaffRole.CUSTOMER_SERVICE), 220.0) { room, w, d ->
            listOf(PlacedItem("stage", w / 2 - 2, room, 0), PlacedItem("stage_lights", w / 2 - 4, room, 0), PlacedItem("stage_lights", w / 2 + 3, room, 0),
                PlacedItem("cinema_seats", w / 2 - 4, room + 4, 0), PlacedItem("cinema_seats", w / 2 + 1, room + 4, 0),
                PlacedItem("cinema_seats", w / 2 - 4, room + 6, 0), PlacedItem("cinema_seats", w / 2 + 1, room + 6, 0), PlacedItem("cash_register", 0, d - 2, 0))
        }
        closeUp(e, "l31_stage", 2.6f, dist = 5.6f, pitch = 28f, turn = .35f + PI.toFloat(), warm = 40f) { s -> s.staff.firstOrNull { it.job == Job.STAGE } }
    }

    /** Order at the kiosk. */
    @Test
    fun l32() {
        if (!on("l32")) { assumeTrue(false); return }
        val e = engine("tokyo", 12.6f)
        burgers(e)
        closeUp(e, "l32_kiosk", 2.6f, turn = .7f, warm = 40f) { s -> s.visitors.firstOrNull { inItem(s, it, "order_kiosk") } }
    }

    /** Alex on the till at the burger place. */
    @Test
    fun l33() {
        if (!on("l33")) { assumeTrue(false); return }
        val e = engine("tokyo", 12.6f)
        val sc = burgers(e)
        e.toggleWork(e.state.businessAt(sc.building.id)!!)
        closeUp(e, "l33_player_till", 2.6f, turn = -.6f, warm = 30f, player = true)
    }

    // sundown

    /** The skyline from over the Hudson as the sun goes and the city lights up. */
    @Test
    fun l40() {
        if (!on("l40")) { assumeTrue(false); return }
        val e = engine("nyc", 18.2f)
        val m0 = e.state.minute
        fly(e, "l40_timelapse", 5f, listOf(nyKey(260f, 760f, 520f, east - .2f, 12f), nyKey(260f, 730f, 505f, east - .08f, 13f)), easing = .1f, haze = .42f) { _, u ->
            clearWeather(e)
            e.state.minute = m0 + (u * 165).toLong()
        }
    }

    // the night

    @Test
    fun l41() {
        if (!on("l41")) { assumeTrue(false); return }
        val e = engine("tokyo", 21.0f)
        val s = landmark(e, "Shibuya")
        cartShot(e, "l41_yatai_night", 2.6f, s.x, s.y, nth = 1, haze = .2f)
    }

    @Test
    fun l42() {
        if (!on("l42")) { assumeTrue(false); return }
        val e = engine("seoul", 21.4f)
        cartShot(e, "l42_pocha_night", 2.6f, 700f, 480f, nth = 1, haze = .2f)
    }

    @Test
    fun l43() {
        if (!on("l43")) { assumeTrue(false); return }
        val e = engine("nyc", 21.0f)
        val c = ny(390f, 700f)
        cartShot(e, "l43_nyc_cart_night", 2.6f, c[0], c[1], nth = 1, haze = .2f)
    }

    @Test
    fun l44() {
        if (!on("l44")) { assumeTrue(false); return }
        val e = engine("manila", 20.4f)
        val a = landmark(e, "Binondo Arch")
        fly(e, "l44_manila_night", 2.6f, listOf(Key(a.x + 3f, a.y + 4f, 30f, .25f, 8f), Key(a.x, a.y, 37f, -.08f, 10f)), easing = .2f, crowd = 60f, haze = .25f) { _, _ -> clearWeather(e) }
    }

    /** The DJ. */
    @Test
    fun l45() {
        if (!on("l45")) { assumeTrue(false); return }
        val e = engine("nyc", 23.6f)
        club(e)
        closeUp(e, "l45_dj", 2.6f, dist = 6.5f, pitch = 26f, turn = .35f + PI.toFloat(), warm = 40f) { s -> s.staff.firstOrNull { it.job == Job.DJ } }
    }

    /** The floor. */
    @Test
    fun l46() {
        if (!on("l46")) { assumeTrue(false); return }
        val e = engine("nyc", 23.6f)
        val sc = club(e)
        inside(e, "l46_dance", 2.6f, { u -> floatArrayOf(sc.w * .42f, sc.d * (.42f + .05f * u), 9f - 1.5f * u, .45f - .3f * u, 30f) }, warm = 40f)
    }

    private fun club(e: GameEngine) = openBusiness(e, "nightclub", "Club Neon", 12..16, 9..11, listOf(StaffRole.DJ, StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE), 320.0) { room, w, d ->
        listOf(PlacedItem("dj_booth", 4, room, 0), PlacedItem("dance_floor", 3, room + 2, 0), PlacedItem("dance_floor", 6, room + 2, 0),
            PlacedItem("cocktail_bar", w - 4, room, 0), PlacedItem("stage_lights", 2, room, 0), PlacedItem("stage_lights", 7, room, 0),
            PlacedItem("lounge_sofa", 0, d - 2, 0), PlacedItem("lounge_sofa", w - 3, d - 2, 0))
    }

    /** A wet night in Shinjuku from behind the wheel. */
    @Test
    fun l47() {
        if (!on("l47")) { assumeTrue(false); return }
        val e = engine("tokyo", 21.5f, Sky.RAIN)
        val v = startCar(e, "volta", 330f, 470f, color = 0xFFC0392B.toInt())
        fun wet() { e.skyVis.rain = .85f; e.skyVis.cloud = .9f; e.skyVis.wet = 1f }
        chase(e, "l47_rain", 2.6f, v, 11f, relYaw = { u -> .5f - .3f * u }, dist = { u -> 13f - 1.5f * u }, pitch = { 13f }, weather = true, haze = .35f) { _, _ -> wet() }
    }

    /** The late shift: Alex on the till at the corner shop. */
    @Test
    fun l48() {
        if (!on("l48")) { assumeTrue(false); return }
        val e = engine("seoul", 23f)
        val sc = openBusiness(e, "liquor", "Night Owl", 10..14, 8..10, listOf(StaffRole.CUSTOMER_SERVICE), 220.0) { room, w, d ->
            listOf(PlacedItem("drinks_fridge", 1, room, 0), PlacedItem("drinks_fridge", 2, room, 0), PlacedItem("drinks_fridge", 3, room, 0),
                PlacedItem("shelf", 1, room + 3, 0), PlacedItem("shelf", 4, room + 3, 0), PlacedItem("checkout_counter", w - 5, d - 3, 0),
                PlacedItem("plant_large", 0, d - 1, 0))
        }
        e.toggleWork(e.state.businessAt(sc.building.id)!!)
        closeUp(e, "l48_night_till", 2.6f, turn = -.6f, warm = 30f, player = true)
    }

    @Test
    fun l49() {
        if (!on("l49")) { assumeTrue(false); return }
        val e = engine("nyc", 20.4f)
        fly(e, "l49_times", 2.6f, listOf(Key(621f, 946f, 28f, .1f, 10f), Key(621f, 932f, 26f, .03f, 9f)), easing = .2f, warm = 5f, haze = .22f, crowd = 70f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun l50() {
        if (!on("l50")) { assumeTrue(false); return }
        val e = engine("tokyo", 20.6f)
        val s = landmark(e, "Shibuya")
        val node = e.city.scrambleNode(s)!!
        val warm = 26f
        e.traffic.lightClock = ((34.5f - node.phase - (warm - 3f)) % Traffic.SCRAMBLE_CYCLE + Traffic.SCRAMBLE_CYCLE) % Traffic.SCRAMBLE_CYCLE
        fly(e, "l50_scramble_night", 2.6f, listOf(Key(s.x, s.y, 56f, 1.95f, 34f), Key(s.x, s.y, 49f, 2.22f, 30f)), easing = .2f, warm = warm, haze = .25f, crowd = 45f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun l51() {
        if (!on("l51")) { assumeTrue(false); return }
        val e = engine(com.hustlecity.game.world.Maps.CLASSIC, 21.2f)
        fly(e, "l51_classic_night", 2.6f, listOf(Key(640f, 760f, 260f, -.15f, 16f), Key(650f, 720f, 240f, .08f, 18f)), easing = .2f, haze = .3f) { _, _ -> clearWeather(e) }
    }

    @Test
    fun l52() {
        if (!on("l52")) { assumeTrue(false); return }
        val e = engine("manila", 21.0f)
        fly(e, "l52_makati", 2.6f, listOf(Key(1080f, 1120f, 235f, 2.3f, 15f), Key(1080f, 1120f, 205f, 2.44f, 13f)), easing = .2f, haze = .35f) { _, _ -> clearWeather(e) }
    }

    /** Alex walking under the lanterns with a boat of takoyaki. */
    @Test
    fun l53() {
        if (!on("l53")) { assumeTrue(false); return }
        val e = engine("tokyo", 21.3f)
        val pr = StreetProps(e.city)
        val ln = (0 until pr.count).filter { pr.kind[it] == StreetProps.LANTERNS }.minByOrNull { hypot(pr.x[it] - 300f, pr.y[it] - 980f) }!!
        val hit = RoadHit()
        e.city.roads.nearest(pr.x[ln], pr.y[ln], 12f, hit)
        val lane = e.city.roads.edges[hit.edge]
        val ax = pr.dirY[ln]; val ay = -pr.dirX[ln]
        val p = e.state.player
        p.x = lane.ax + lane.ux * hit.s - ax * 6f; p.y = lane.ay + lane.uy * hit.s - ay * 6f
        p.heading = atan2(ax, -ay)
        var fx = p.x; var fy = p.y
        thin(e)
        track(e, "l53_hero_night", 3.6f, warm = 4f, crowd = 40f, haze = .2f, each = { f, _ ->
            clearWeather(e)
            e.ui.eatingUntil = e.realTime + 30f; e.ui.eatingItem = com.hustlecity.game.render.Snack.BOAT; e.ui.payingUntil = e.realTime - 3f
            if (f >= 0) walkToward(e, ax * .33f, ay * .33f) else { e.input.jx = 0f; e.input.jy = 0f }
        }) { u ->
            fx += (p.x - fx) * .12f; fy += (p.y - fy) * .12f
            Key(fx + ax * .8f, fy + ay * .8f, lerp(5.6f, 5f, u), look(-ax, -ay) + .3f, 12f)
        }
    }

    @Test
    fun l54() {
        if (!on("l54")) { assumeTrue(false); return }
        val e = engine("seoul", 21.0f)
        val t = landmark(e, "N Seoul")
        fly(e, "l54_seoul_tower", 2.6f, listOf(Key(t.x, t.y, 520f, -.3f, 8.2f), Key(t.x, t.y, 485f, -.17f, 8.8f)), easing = .2f, haze = .3f) { _, _ -> clearWeather(e) }
    }

    /** Alex on the Brooklyn shore at night, looking round at Manhattan lit up, the camera rising past. */
    @Test
    fun l55() {
        if (!on("l55")) { assumeTrue(false); return }
        val e = engine("nyc", 21.3f)
        val p = e.state.player
        val spot = ny(936f, 980f)
        p.x = spot[0]; p.y = spot[1]
        p.heading = atan2(-.8747f, .4847f)
        val wx = -.8747f; val wy = -.4847f
        track(e, "l55_shore", 6f, warm = 5.5f, crowd = 20f, haze = .4f, each = { _, _ -> clearWeather(e); p.x = spot[0]; p.y = spot[1]; e.input.jx = 0f; e.input.jy = 0f }) { u ->
            val k = ease(u, .6f)
            Key(spot[0] + wx * lerp(6f, 60f, k), spot[1] + wy * lerp(6f, 60f, k), lerp(11f, 70f, k), west + lerp(.3f, .06f, k), lerp(6f, 9f, k))
        }
    }

    /** The finale: up and away over Shinjuku at night, high over the towers from the start. */
    @Test
    fun l56() {
        if (!on("l56")) { assumeTrue(false); return }
        val e = engine("tokyo", 21.3f)
        fly(e, "l56_finale", 5f, listOf(Key(330f, 470f, 300f, -1.2f, 32f), Key(320f, 470f, 380f, -1.1f, 27f), Key(305f, 470f, 470f, -1.0f, 23f)),
            easing = .5f, haze = .4f) { _, _ -> clearWeather(e) }
    }
}

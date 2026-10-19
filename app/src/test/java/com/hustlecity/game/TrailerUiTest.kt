package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hustlecity.game.audio.Audio
import com.hustlecity.game.audio.Sfx
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.ui.menu.HustleCityApp
import com.hustlecity.game.world.BKind
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Gameplay with the HUD for the trailer, captured frame by frame from the real UI into build/trailer/<clip>/, plus the
 * game's own sound effects as WAV files. Only runs when HC_TRAILER=1 is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class TrailerUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val fps = 30

    private fun on() = System.getenv("HC_TRAILER") == "1"

    private fun wrap(a: Float): Float { var x = a; while (x > Math.PI) x -= (2 * Math.PI).toFloat(); while (x < -Math.PI) x += (2 * Math.PI).toFloat(); return x }

    /** Runs the game and captures [seconds] of the screen; [control] steers each frame. */
    private fun record(e: GameEngine, name: String, seconds: Float, warmup: Int = 20, control: (Int) -> Unit) {
        val dir = File("build/trailer/$name").apply { deleteRecursively(); mkdirs() }
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        repeat(warmup) { control(-1); e.frame(1f / fps); rule.mainClock.advanceTimeBy(33) }
        val frames = (seconds * fps).toInt()
        val view = rule.activity.window.decorView
        for (f in 0 until frames) {
            control(f)
            e.frame(1f / fps)
            rule.mainClock.advanceTimeBy(33)
            val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(dir, "%04d.jpg".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle()
        }
    }

    private fun game(mapId: String, hour: Float): GameEngine {
        val e = GameEngine(null)
        e.newGame("Alex Rivera", 1, 2, 1, mapId = mapId)
        e.ui.tip = null; e.ui.phoneOpen = false
        e.state.minute = (e.state.minute / 1440) * 1440 + (hour * 60).toLong()
        e.state.objectivesDone += listOf("loan", "rent", "startbiz")
        return e
    }

    /** A shop a few blocks away, so the GPS has a route with turns. */
    private fun destination(e: GameEngine, minDist: Float, maxDist: Float): Int {
        val p = e.state.player
        return e.city.buildings.filter { it.kind == BKind.RETAIL && hypot(it.doorX - p.x, it.doorY - p.y) in minDist..maxDist }
            .minByOrNull { abs(hypot(it.doorX - p.x, it.doorY - p.y) - (minDist + maxDist) / 2) }!!.id
    }

    /** The route point the player should head for: the first one well ahead. */
    private fun aim(e: GameEngine, ahead: Float): Pair<Float, Float>? {
        val g = e.gps
        if (!g.active) return null
        val p = e.state.player
        for (q in 1 until g.points) {
            val x = g.route[q * 2]; val y = g.route[q * 2 + 1]
            if (hypot(x - p.x, y - p.y) > ahead) return x to y
        }
        return null
    }

    /** The game's own sounds for the trailer's mix. */
    @Test
    fun sounds() {
        assumeTrue(on())
        val sfx = File("build/trailer/sfx").apply { mkdirs() }
        for (s in Sfx.entries) wav(File(sfx, s.name.lowercase() + ".wav"), Audio.effectSamples(s))
    }

    /** Driving across Manila, following the GPS. */
    @Test
    fun drive() {
        assumeTrue(on())
        run {
            val e = game("manila", 9.6f)
            e.settings.carControls = 0
            val v = e.state.vehicles.first()
            e.enterVehicle(v)
            e.ui.tip = null
            e.setWaypoint(destination(e, 250f, 450f))
            record(e, "08_drive_hud", 6f, warmup = 45) {
                val target = aim(e, 14f)
                if (target != null) {
                    val want = atan2(target.first - v.x, -(target.second - v.y))
                    e.input.steer = (wrap(want - v.heading) * 2.2f).coerceIn(-1f, 1f)
                }
                e.input.gas = e.carSpeed < 9.5f
                e.input.brake = false
            }
        }
    }

    /** Walking a Tokyo street at dusk toward a shop. */
    @Test
    fun walk() {
        assumeTrue(on())
        run {
            val e = game("tokyo", 18.4f)
            e.setWaypoint(destination(e, 90f, 160f))
            record(e, "09_walk_hud", 5f) {
                val target = aim(e, 4f) ?: return@record
                val p = e.state.player
                val dx = target.first - p.x; val dy = target.second - p.y
                val d = hypot(dx, dy).coerceAtLeast(.01f)
                val wx = dx / d; val wy = dy / d
                val yaw = e.camera.yaw
                val rx = cos(yaw); val ry = -sin(yaw); val fx = -sin(yaw); val fy = -cos(yaw)
                e.input.jx = wx * rx + wy * ry
                e.input.jy = -(wx * fx + wy * fy)
            }
        }
    }

    /** Inside your own supermarket: stocked shelves, staff and shoppers. */
    @Test
    fun store() {
        assumeTrue(on())
        run {
            val e = game("nyc", 13.5f)
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
            biz.backStock["soda"] = 48 * 4; biz.backStock["snacks"] = 60 * 3
            for (role in listOf(StaffRole.CUSTOMER_SERVICE, StaffRole.CUSTOMER_SERVICE, StaffRole.CLEANER))
                e.state.employees += Employee(e.state.newId(), "Sam", role, .6, 15.0, 15.0, 30, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
            e.state.player.x = b.doorX; e.state.player.y = b.doorY
            e.enterBuilding(b)
            e.ui.tip = null
            e.economy.servedRate[biz.id] = 160.0
            repeat(30 * 12) { e.interior!!.update(1f / fps, true) }
            biz.stock["snacks"] = (biz.stock["snacks"] ?: 0) - 70
            e.economy.restockFromBack(biz, 1)
            val yaw0 = e.interior!!.yaw
            record(e, "10_store_hud", 6f) { f -> if (f >= 0) e.interior!!.yaw = yaw0 + .35f * f / (6f * fps) }
        }
    }

    /** 16-bit mono WAV at the game's sample rate. */
    private fun wav(f: File, data: FloatArray) {
        val n = data.size
        DataOutputStream(FileOutputStream(f)).use { o ->
            fun le32(v: Int) { o.write(v and 255); o.write((v shr 8) and 255); o.write((v shr 16) and 255); o.write((v shr 24) and 255) }
            fun le16(v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }
            o.writeBytes("RIFF"); le32(36 + n * 2); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(Audio.SR); le32(Audio.SR * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(n * 2)
            for (v in data) le16((v * 32000f).toInt().coerceIn(-32768, 32767))
        }
    }
}

package com.hustlecity.game.desktop

import android.content.Context
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.hustlecity.game.Platform
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.Sky
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.model.WeatherSpan
import com.hustlecity.game.ui.menu.HustleCityApp
import com.hustlecity.game.world.BKind
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Store-page screenshots: the game window at 1920x1080 with its HUD, in the HD renderer. Only with
 * -PstoreShots=<folder> (and a display and OpenGL 3.3, like -PgpuTest).
 */
class StoreShots {
    @Test
    fun shots() {
        val out = System.getProperty("hustle.storeShots")?.let(::File)
        assumeTrue(out != null)
        out!!.mkdirs()
        Platform.isDesktop = true
        val e = GameEngine(Context(Files.createTempDirectory("hustle").toFile()))
        e.settings.hd = true
        e.settings.quality = 2
        fun game(mapId: String, hour: Float, sky: Sky = Sky.CLEAR) {
            e.newGame("Alex Rivera", 1, 2, 1, mapId = mapId)
            e.state.minute = (e.state.minute / 1440) * 1440 + (hour * 60).toLong()
            e.state.weather.clear()
            e.state.weather += WeatherSpan(e.state.minute - 600, e.state.minute + 3 * 1440L, sky)
            e.weather.ensure()
            e.ui.tip = null
        }
        // the player on the sidewalk outside the building nearest (x, y)
        fun walkTo(x: Float, y: Float) {
            val b = e.city.buildings.filter { it.solid && it.frontEdge >= 0 }.minByOrNull { (it.cx - x) * (it.cx - x) + (it.cy - y) * (it.cy - y) }!!
            e.state.player.x = b.doorX + b.frontX * 2.5f; e.state.player.y = b.doorY + b.frontY * 2.5f
        }
        game("nyc", 19.6f)
        ImageComposeScene(1920, 1080, Density(1f)) { HustleCityApp(e, onExit = {}) }.use { scene ->
            var t = 0L
            fun settle(n: Int, cam: () -> Unit = {}) {
                val deadline = System.nanoTime() + 90_000_000_000L
                var k = 0
                while (k < n || (e.gpuFps <= 0f && System.nanoTime() < deadline)) {
                    e.frame(1 / 30f)
                    cam()
                    e.ui.tip = null
                    e.ui.toasts.clear()
                    scene.render(t)
                    t += 33_000_000L
                    Thread.sleep(40)
                    javax.swing.SwingUtilities.invokeAndWait { }
                    k++
                }
            }
            fun shot(name: String) {
                File(out, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                println("$name: ${e.gpuFps} fps, gave up=${e.settings.gpuFailed}")
            }

            // Times Square as the screens come on, on foot: the goals on the left
            val ts = e.city.landmarks.first { it.name.contains("Times Square") }
            walkTo(ts.x, ts.y)
            e.ui.phoneOpen = false
            e.ui.objectivesOpen = true
            settle(45) { e.camera.zoom = 58f; e.camera.yaw = .4f }
            shot("01_times_square_a")
            settle(20) { e.camera.zoom = 58f; e.camera.yaw = 2.3f }
            shot("01_times_square_b")

            // your own café on a busy morning: the queue, the tables, the staff
            game("nyc", 9.2f)
            val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 11..14 && it.gridD in 9..11 }
            e.state.rentals += Rental(b.id, b.baseRent, 1)
            val biz = e.startBusiness(b.id, "coffee", "Tenth & Bean")!!
            biz.tempClosed = false
            val room = biz.backRoom.coerceAtLeast(0)
            biz.furniture += listOf(PlacedItem("coffee_machine", 1, room, 0), PlacedItem("coffee_machine", 3, room, 0), PlacedItem("pastry_case", 5, room, 0),
                PlacedItem("cash_register", 7, room, 0), PlacedItem("table_set", 1, room + 4, 0), PlacedItem("table_set", 4, room + 4, 0),
                PlacedItem("table_set", 7, room + 4, 0), PlacedItem("plant_large", b.gridW - 1, b.gridD - 1, 0), PlacedItem("floor_lamp", 0, b.gridD - 1, 0))
            for ((pid, _) in biz.type.products) biz.stock[pid] = 400
            for (k in 0 until 2) e.state.employees += Employee(e.state.newId(), "Kim", StaffRole.CUSTOMER_SERVICE, .75, 18.0, 18.0, 27, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
            e.state.player.inBuilding = b.id
            val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
            e.interior = sc
            e.economy.servedRate[biz.id] = 300.0
            // let the morning rush build up before the camera rolls
            repeat(30 * 50) { sc.update(1 / 30f, true) }
            e.ui.objectivesOpen = false
            e.ui.phoneOpen = true
            settle(30)
            shot("02_cafe")

            // Shibuya at night, the phone out
            game("tokyo", 21.2f)
            val sh = e.city.landmarks.first { it.name.contains("Shibuya", true) }
            walkTo(sh.x, sh.y)
            e.ui.phoneOpen = true
            e.ui.objectivesOpen = false
            settle(45) { e.camera.zoom = 62f; e.camera.yaw = 2.1f }
            shot("03_shibuya_night")

            // a wet night in Midtown behind the wheel, down an avenue
            game("nyc", 21.5f, Sky.RAIN)
            e.ui.phoneOpen = false
            e.ui.objectivesOpen = false
            val av = e.city.roads.edges.filter { it.cls.rank >= 2 && it.len > 90f && !it.overWater }
                .minByOrNull { (it.x(.5f) - ts.x).let { d -> d * d } + (it.y(.5f) - ts.y).let { d -> d * d } }!!
            val v = e.state.vehicles.first()
            // in the right-hand lane near the start, heading along it
            v.x = av.ax + av.ux * 12f - av.uy * av.cls.halfWidth * .45f
            v.y = av.ay + av.uy * 12f + av.ux * av.cls.halfWidth * .45f
            v.heading = av.heading(av.a)
            e.state.player.x = v.x; e.state.player.y = v.y
            e.enterVehicle(v)
            settle(30) { e.input.gas = true; e.camera.zoom = 48f; e.camera.yaw = v.heading + 3.1f }
            settle(25) { e.input.gas = true; e.camera.zoom = 48f; e.camera.yaw = v.heading + 3.1f }
            shot("04_night_drive")
        }
    }
}

package com.hustlecity.game.cinema

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.OwnedVehicle
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.Sky
import com.hustlecity.game.model.WeatherSpan
import com.hustlecity.game.render.gpu.GpuCapture
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.ui.menu.HustleCityApp
import com.hustlecity.game.world.BKind
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The trailer's gameplay shots: the game's real HUD at 1080p over the HD renderer. The HUD is captured with a clear
 * background as an overlay and the 3D world as an HD frame (via [GpuCapture]), both into build/cinema/<shot>/; and
 * stills of the phone apps into build/cinema_ui/. Runs only with HC_CINEMA=1.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xxhdpi")
class CinematicUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val fps = 24

    @After
    fun done() { GpuCapture.frame = null }

    private fun on(name: String): Boolean {
        if (System.getenv("HC_CINEMA") != "1") return false
        val only = System.getenv("HC_SHOTS")?.split(',')?.filter { it.isNotBlank() } ?: return true
        return only.any { name.startsWith(it) }
    }

    private fun game(mapId: String, hour: Float): GameEngine {
        val e = GameEngine(null)
        e.newGame("Alex Rivera", 1, 2, 1, mapId = mapId)
        e.ui.tip = null; e.ui.phoneOpen = false
        e.settings.quality = 2
        // as busy as the world shots
        e.settings.crowds = 8f; e.settings.busyRoads = 3f
        e.traffic.ensure(e.settings.carCount, e.settings.pedCount)
        e.state.minute = (e.state.minute / 1440) * 1440 + (hour * 60).toLong()
        e.state.weather.clear(); e.state.weather += WeatherSpan(e.state.minute - 600, e.state.minute + 3 * 1440L, Sky.CLEAR)
        e.weather.ensure()
        e.state.objectivesDone += listOf("loan", "rent", "startbiz", "furnish", "stock", "open", "hire")
        e.state.player.money = 284_500.0
        return e
    }

    /** Records [seconds] of play: the HUD as a clear overlay, the world as HD frames. [control] runs before each frame. */
    private fun record(e: GameEngine, name: String, seconds: Float, warm: Int = 36, control: (Int) -> Unit) {
        val out = CinemaWriter(File("build/cinema/$name"))
        GpuCapture.frame = GpuFrame()
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        rule.activity.window.setBackgroundDrawable(ColorDrawable(0))
        repeat(warm) { control(-1); rule.mainClock.advanceTimeBy(1000L / fps) }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (f in 0 until (seconds * fps).toInt()) {
            control(f)
            rule.mainClock.advanceTimeBy(1000L / fps)
            bmp.eraseColor(0)
            view.draw(canvas)
            out.write(GpuCapture.frame!!, bmp)
        }
        println("$name: ${out.frames} frames ${bmp.width}x${bmp.height}")
    }

    /** A still of the screen as it is, into build/cinema_ui/[name].png. */
    private fun still(e: GameEngine, name: String, frames: Int = 10, act: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        repeat(frames) { rule.mainClock.advanceTimeBy(1000L / fps) }
        act()
        repeat(4) { rule.mainClock.advanceTimeBy(1000L / fps) }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/cinema_ui").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("$name: still ${bmp.width}x${bmp.height}")
    }

    private fun wrap(a: Float): Float { var x = a; while (x > PI) x -= (2 * PI).toFloat(); while (x < -PI) x += (2 * PI).toFloat(); return x }

    /** A furnished, stocked coffee shop with staff; returns it. */
    private fun cafe(e: GameEngine, name: String = "Golden Bean"): com.hustlecity.game.model.Business {
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 11..14 && it.gridD in 8..10 }
        e.state.rentals += Rental(b.id, b.baseRent, 1, leaseEnd = 61)
        val biz = e.startBusiness(b.id, "coffee", name)!!
        biz.tempClosed = false
        val room = biz.backRoom.coerceAtLeast(0)
        biz.furniture += listOf(
            PlacedItem("coffee_machine", 1, room, 0), PlacedItem("pastry_case", 3, room, 0), PlacedItem("cash_register", 5, room, 0),
            PlacedItem("table_set", 1, room + 3, 0), PlacedItem("table_set", 4, room + 3, 0), PlacedItem("plant_large", 0, b.gridD - 1, 0),
        )
        biz.stock["coffee"] = 300; biz.stock["tea"] = 90; biz.stock["croissant"] = 60
        for ((n, sh) in listOf("Maya Cruz" to 6, "Leo Park" to 14)) e.state.employees += Employee(e.state.newId(), n, StaffRole.CUSTOMER_SERVICE, .62, 15.5, 15.0, 27,
            businessId = biz.id, shiftStart = sh, shiftEnd = sh + 8, days = 127, fatigue = if (sh == 14) 58.0 else 22.0)
        for (k in 0 until 8) {
            biz.history += com.hustlecity.game.model.BizDay(k, 1400.0 + k * 160, 520.0, 280.0, 90.0, 0.0, 120 + k * 9, .82)
        }
        return biz
    }

    /** Laying out a new shop in the designer: a shelf follows your finger across the floor. */
    @Test
    fun u1() {
        if (!on("u1")) { assumeTrue(false); return }
        val e = game("nyc", 13.2f)
        val biz = cafe(e)
        val b = e.city.building(biz.buildingId)!!
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        e.ui.tip = null
        e.ui.designer = true
        val sc = e.interior!!
        sc.startPlacing("table_set")
        record(e, "u1_designer", 2.6f) { f ->
            val t = (f.coerceAtLeast(0)) / (2.6f * fps)
            sc.moveGhostTo((2 + (t * 6).toInt()).coerceAtMost(sc.w - 3), (sc.d - 4).coerceAtLeast(1))
            e.ui.tip = null
        }
    }

    /** Driving across Manila at golden hour with the GPS, speedometer and minimap. */
    @Test
    fun u2() {
        if (!on("u2")) { assumeTrue(false); return }
        val e = game("manila", 16.9f)
        e.settings.carControls = 0
        val v = e.state.vehicles.first()
        e.enterVehicle(v)
        e.ui.tip = null
        val p = e.state.player
        val dest = e.city.buildings.filter { it.kind == BKind.RETAIL && hypot(it.doorX - p.x, it.doorY - p.y) in 280f..480f }.minByOrNull { hypot(it.doorX - p.x, it.doorY - p.y) }!!
        e.setWaypoint(dest.id)
        e.traffic.update(0f, v.x, v.y, 230f, -9999f, -9999f, initial = true)
        e.traffic.scatter(v.x, v.y, 110f, .65f)
        record(e, "u2_drive", 2.6f, warm = 60) {
            val g = e.gps
            var target: Pair<Float, Float>? = null
            if (g.active) for (q in 1 until g.points) {
                val x = g.route[q * 2]; val y = g.route[q * 2 + 1]
                if (hypot(x - v.x, y - v.y) > 14f) { target = x to y; break }
            }
            if (target != null) e.input.steer = (wrap(atan2(target.first - v.x, -(target.second - v.y)) - v.heading) * 2.2f).coerceIn(-1f, 1f)
            e.input.gas = e.carSpeed < 10.5f
            e.ui.tip = null
        }
    }

    /** The phone apps: staff shifts and perks, the fleet, rival buyouts and the stock market. */
    @Test
    fun u3() {
        if (!on("u3")) { assumeTrue(false); return }
        val e = game("nyc", 10f)
        val biz = cafe(e)
        biz.perks += listOf("coffee", "health")
        e.state.employees += Employee(e.state.newId(), "Ava Stone", StaffRole.STORE_MANAGER, .84, 32.0, 32.0, 41, businessId = biz.id, shiftStart = 8, shiftEnd = 16)
        // a morning and an evening shift that overlap at lunch, and two hires still to place
        val role = biz.type.staffRole
        e.state.employees += Employee(e.state.newId(), "Mika Tan", role, .72, 18.0, 18.0, 24, businessId = biz.id, shiftStart = 7, shiftEnd = 15)
        e.state.employees += Employee(e.state.newId(), "Leo Park", role, .65, 17.0, 17.0, 29, businessId = biz.id, shiftStart = 12, shiftEnd = 21)
        e.state.employees += Employee(e.state.newId(), "Rosa Cruz", role, .81, 21.0, 21.0, 36)
        e.state.employees += Employee(e.state.newId(), "Sam Okafor", role, .58, 16.0, 16.0, 22)
        e.ui.app = AppId.BIZHUB; e.ui.bizHubSel = biz.id; e.ui.bizHubTab = 3
        still(e, "app_schedule")
    }

    @Test
    fun u4() {
        if (!on("u4")) { assumeTrue(false); return }
        val e = game("nyc", 7.5f)
        val biz = cafe(e)
        val w2 = cafe(e, "Bean There")
        e.state.player.courses += Catalog.COURSE_LOGISTICS
        val van = OwnedVehicle(e.state.newId(), "hauler", e.state.player.x + 6f, e.state.player.y, 0f, color = 0xFF1F4FA0.toInt())
        val van2 = OwnedVehicle(e.state.newId(), "freighter", e.state.player.x + 12f, e.state.player.y, 0f)
        e.state.vehicles += van; e.state.vehicles += van2
        for (n in listOf("Dana Reyes", "Omar Haddad")) e.state.employees += Employee(e.state.newId(), n, StaffRole.DRIVER, .66, 20.0, 20.0, 34)
        val r = e.ops.newRoute(van)
        r.stops += biz.id; r.stops += w2.id
        e.ops.assignDriver(r, e.ops.drivers()[0])
        r.lastRun = "Day 12 06:00: 38 boxes for 2 stops, \$2,184.40 of stock, \$3.10 fuel. Back at 07:24."
        van.awayUntil = e.state.minute + 50
        e.ui.app = AppId.FLEET
        still(e, "app_fleet")
    }

    @Test
    fun u5() {
        if (!on("u5")) { assumeTrue(false); return }
        val e = game("nyc", 15f)
        repeat(6 * 1440) { e.simulateMinute() }
        e.ui.summary = null
        e.state.player.money = 1_840_000.0
        val rb = e.state.rivalBusinesses.maxByOrNull { it.profit }!!
        e.ui.panel = com.hustlecity.game.engine.Panel.RivalShop(rb.buildingId)
        still(e, "app_buyout")
    }

    @Test
    fun u6() {
        if (!on("u6")) { assumeTrue(false); return }
        val e = game("nyc", 15f)
        e.state.player.money = 2_400_000.0
        e.ops.ensureMarket()
        repeat(9 * 1440) { e.simulateMinute() }
        e.ui.summary = null
        e.ops.buyShares(e.state.market.first { it.id == "HCI" }, 1000)
        e.ops.buyShares(e.state.market.first { it.id == "HMT" }, 1000)
        val r = e.state.rivals.first()
        e.ops.buyShares(e.state.market.first { it.id == "R${r.id}" }, 27_000)
        e.ops.buyBond(100_000.0, 180)
        e.ui.app = AppId.MARKETS
        still(e, "app_markets")
    }

    // ------------------------------------------------------------------ the 1.8 update trailer

    /** Selling a café in Manila to a rival company, the offer in pesos. */
    @Test
    fun v1() {
        if (!on("v1")) { assumeTrue(false); return }
        val e = game("manila", 15f)
        val biz = cafe(e, "Kapihan sa Kanto")
        for (r in e.state.rivals) r.cash = maxOf(r.cash, 4_000_000.0)
        e.ui.app = AppId.BIZHUB; e.ui.bizHubSel = biz.id; e.ui.bizHubTab = 6
        still(e, "v_sell")
    }

    /** A landlord in Tokyo: a shop let to a ramen chain, a flat looking for a tenant, all in yen. */
    @Test
    fun v2() {
        if (!on("v2")) { assumeTrue(false); return }
        val e = game("tokyo", 15f)
        val biz = cafe(e, "Kissa Hoshi")
        e.state.rentals.replaceAll { if (it.buildingId == biz.buildingId) it.copy(owned = true) else it }
        val shops = e.city.buildings.filter { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null }.sortedByDescending { it.area }
        val homes = e.city.buildings.filter { e.isVacant(it.id) && it.kind == BKind.RESIDENTIAL && it.placeId == null }.sortedByDescending { it.area }
        val day = e.state.day
        e.state.rentals += Rental(shops[0].id, shops[0].baseRent, day - 30, owned = true, tenant = "Menya Kintaro", tenantRent = shops[0].baseRent * 1.1, tenantSince = day - 30, tenantLeaseEnd = day + 30)
        e.state.rentals += Rental(shops[1].id, shops[1].baseRent, day - 12, owned = true, tenant = "Sakura Drugstore", tenantRent = shops[1].baseRent * .95, tenantSince = day - 12, tenantLeaseEnd = day + 48)
        e.state.rentals += Rental(homes[0].id, homes[0].baseRent, day - 3, owned = true, listed = true, askPct = 1.05)
        e.ui.app = AppId.REALTY
        still(e, "v_landlord") { rule.onNodeWithText("My properties", substring = true).performClick() }
    }

    /** Six save slots: an empire in every city, each in its own money. */
    @Test
    fun v3() {
        if (!on("v3")) { assumeTrue(false); return }
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val e = GameEngine(ctx)
        for (i in 1..6) e.saves.delete(i)
        val games = listOf(Triple("nyc", "Alex Rivera", 48), Triple("manila", "Maria Santos", 31), Triple("tokyo", "Kenji Mori", 22),
            Triple("seoul", "Ji-woo Han", 15), Triple(com.hustlecity.game.world.Maps.CLASSIC, "Sam Carter", 64))
        for ((k, g) in games.withIndex()) {
            e.newGame(g.second, 1, 2, 1, mapId = g.first, slot = k + 1)
            e.ui.tip = null
            e.state.minute = (g.third - 1) * 1440L + 15 * 60
            e.state.player.money = 18_500.0 + k * 96_250.0 + g.third * 4_100.0
            repeat(k % 3 + 1) { cafe(e, "Cafe $it") }
            e.save()
        }
        e.ui.screen = com.hustlecity.game.engine.Screen.LOAD
        still(e, "v_slots")
        for (i in 1..6) e.saves.delete(i)
    }
}

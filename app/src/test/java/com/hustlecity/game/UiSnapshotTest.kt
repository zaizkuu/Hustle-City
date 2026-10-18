package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.Panel
import com.hustlecity.game.engine.Screen
import com.hustlecity.game.engine.pickUp
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.takeCart
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.ui.menu.HustleCityApp
import com.hustlecity.game.world.BKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Captures full-screen screenshots of the Compose UI (landscape phone) into build/snapshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h380dp-land-xhdpi")
class UiSnapshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/snapshots").apply { mkdirs() }

    private fun shoot(e: GameEngine, name: String, frames: Int = 6) {
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        repeat(frames) {
            e.frame(1 / 30f)
            rule.mainClock.advanceTimeBy(34)
        }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(outDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun game(): GameEngine = GameEngine(null).also { it.newGame("Alex Rivera", 1, 2, 1) }

    private fun withBusiness(e: GameEngine, type: String = "coffee"): Int {
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 70..140 && it.neighborhood == 0 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, type, "Golden Bean")!!
        biz.furniture += PlacedItem("cash_register", 1, 1, 0)
        biz.furniture += PlacedItem("coffee_machine", 4, 0, 0)
        biz.furniture += PlacedItem("pastry_case", 7, 0, 0)
        biz.furniture += PlacedItem("table_set", 1, 3, 0)
        biz.stock["coffee"] = 180; biz.stock["tea"] = 60
        biz.tempClosed = false
        biz.storage += "plant_large"; biz.storage += "speaker_small"
        return b.id
    }

    @Test
    fun mainMenu() {
        val e = GameEngine(null)
        e.ui.screen = Screen.MENU
        shoot(e, "ui_menu.png")
    }

    @Test
    fun newGame() {
        val e = GameEngine(null)
        e.ui.screen = Screen.NEW_GAME
        e.prepareMenuBackground()
        // draw the city thumbnails up front so the picker shows them
        for (m in com.hustlecity.game.world.Maps.all) com.hustlecity.game.ui.menu.CityThumbs.get(m.id)
        shoot(e, "ui_newgame.png")
    }

    @Test
    fun hudStreet() {
        val e = game()
        e.state.minute = 17L * 60 + 1
        e.toast("Welcome to Hustle City!")
        shoot(e, "ui_hud.png")
    }

    @Test
    fun buildingCard() {
        val e = game()
        e.ui.selected = e.city.placeBuilding("essentials")!!.id
        e.ui.phoneOpen = false
        shoot(e, "ui_card.png")
    }

    @Test
    fun bizHubInsight() {
        val e = game()
        withBusiness(e)
        e.ui.app = AppId.BIZHUB
        e.ui.bizHubTab = 1
        repeat(600) { e.simulateMinute() }
        shoot(e, "ui_bizhub_insight.png")
    }

    @Test
    fun bizHubInventory() {
        val e = game()
        withBusiness(e)
        e.ui.app = AppId.BIZHUB
        e.ui.bizHubTab = 2
        shoot(e, "ui_bizhub_inventory.png")
    }

    @Test
    fun furnitureStore() {
        val e = game()
        val b = e.city.placeBuilding("essentials")!!
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        shoot(e, "ui_store.png")
    }

    @Test
    fun furnitureStoreShopping() {
        val e = game()
        val b = e.city.placeBuilding("essentials")!!
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        e.ui.tip = null
        val sc = e.interior!!
        e.takeCart(b.id)
        e.ui.tip = null
        for (id in listOf("shelf", "drinks_fridge", "cash_register")) e.pickUp(id)
        sc.inspect = sc.forSale.entries.first { it.value == "treadmill" }.key
        sc.px = 7.5f; sc.py = sc.d - 4.5f
        shoot(e, "ui_store_shopping.png")
    }

    @Test
    fun manageStorage() {
        val e = game()
        val v = e.state.vehicles.first()
        e.state.player.x = v.x + 1.5f; e.state.player.y = v.y
        v.cargo += com.hustlecity.game.model.CargoItem("shelf", false)
        v.cargo += com.hustlecity.game.model.CargoItem("coffee", true, 60)
        v.cargo += com.hustlecity.game.model.CargoItem("hand_truck", false)
        e.state.player.carrier = com.hustlecity.game.engine.Carrier.HAND_TRUCK
        e.state.player.hand += com.hustlecity.game.model.CargoItem("cash_register", false)
        e.state.player.hand += com.hustlecity.game.model.CargoItem("plant_small", false)
        e.ui.panel = Panel.Trunk(v.id)
        shoot(e, "ui_storage.png")
    }

    @Test
    fun wholesale() {
        val e = game()
        withBusiness(e)
        val b = e.city.placeBuilding("metro")!!
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        shoot(e, "ui_wholesale.png")
    }

    @Test
    fun interiorBusiness() {
        val e = game()
        val bid = withBusiness(e)
        val b = e.city.building(bid)!!
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        e.ui.designer = true
        shoot(e, "ui_interior.png")
    }

    @Test
    fun startBusiness() {
        val e = game()
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        shoot(e, "ui_startbiz.png")
    }

    @Test
    fun maps() {
        val e = game()
        e.ui.app = AppId.MAPS
        e.camera.mapX = e.state.player.x; e.camera.mapY = e.state.player.y; e.camera.mapDist = 650f
        shoot(e, "ui_maps.png")
    }

    @Test
    fun realty() {
        val e = game()
        e.ui.app = AppId.REALTY
        shoot(e, "ui_realty.png")
    }

    @Test
    fun finance() {
        val e = game()
        withBusiness(e)
        repeat(3 * 1440) { e.simulateMinute() }
        e.ui.summary = null
        e.ui.app = AppId.FINANCE
        shoot(e, "ui_finance.png")
    }

    @Test
    fun sleeping() {
        val e = game()
        val home = e.city.building(e.state.player.home)!!
        e.state.player.x = home.doorX; e.state.player.y = home.doorY
        e.enterBuilding(home)
        e.ui.panel = Panel.Sleep
        shoot(e, "ui_home.png")
    }

    @Test
    fun interiorDirect() {
        val e = game()
        val home = e.city.building(e.state.player.home)!!
        e.state.player.inBuilding = home.id
        e.interior = InteriorScene(e, home, InteriorMode.HOME)
        shoot(e, "ui_home_interior.png")
    }

    @Test
    fun tipAndRadio() {
        val e = game()
        shoot(e, "ui_tip_radio.png")
    }

    @Test
    fun endingBankrupt() {
        val e = game()
        e.ui.tip = null
        e.ui.ending = "bankrupt"
        shoot(e, "ui_ending_bankrupt.png")
    }

    @Test
    fun endingVictory() {
        val e = game()
        e.ui.tip = null
        e.state.player.money = 5_400_000.0
        e.ui.ending = "victory"
        shoot(e, "ui_ending_victory.png")
    }

    @Test
    fun warehouseStorage() {
        val e = game()
        e.ui.tip = null
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.WAREHOUSE }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val wh = e.startBusiness(b.id, "warehouse", "Depot")!!
        repeat(3) { wh.furniture += PlacedItem("pallet_rack", it * 2, 0, 0) }
        wh.stock["coffee"] = 600; wh.stock["soda"] = 240; wh.stock["fresh_food"] = 90
        e.ui.app = AppId.BIZHUB; e.ui.bizHubSel = wh.id; e.ui.bizHubTab = 1
        shoot(e, "ui_warehouse.png")
    }

    @Test
    fun warehouseInterior() {
        val e = game()
        e.ui.tip = null
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.WAREHOUSE }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val wh = e.startBusiness(b.id, "warehouse", "Depot")!!
        for (i in 0 until 5) wh.furniture += PlacedItem("pallet_rack", 1 + i * 3, 1, 0)
        for (i in 0 until 5) wh.furniture += PlacedItem("pallet_rack", 1 + i * 3, 4, 0)
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        shoot(e, "ui_warehouse_interior.png")
    }

    @Test
    fun hqTeam() {
        val e = game()
        e.ui.tip = null
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.OFFICE }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val hq = e.startBusiness(b.id, "hq", "Hustle HQ")!!
        hq.furniture += PlacedItem("office_desk", 0, 0, 0)
        hq.furniture += PlacedItem("office_desk", 3, 0, 0)
        e.state.employees += com.hustlecity.game.model.Employee(e.state.newId(), "Hal Brooks", com.hustlecity.game.data.StaffRole.HR_MANAGER, .6, 26.0, 26.0, 41, businessId = hq.id)
        e.ui.app = AppId.BIZHUB; e.ui.bizHubSel = hq.id; e.ui.bizHubTab = 1
        shoot(e, "ui_hq.png")
    }

    @Test
    fun settingsAudio() {
        val e = game()
        e.ui.tip = null
        e.ui.panel = Panel.Settings
        shoot(e, "ui_settings.png")
    }

    @Test
    fun rivalsRace() {
        val e = game()
        e.ui.tip = null
        e.state.sandbox = true
        repeat(20) { e.state.minute = (e.state.minute / 1440 + 1) * 1440 - 1; e.simulateMinute() }
        e.ui.summary = null
        e.ui.app = AppId.RIVALS
        shoot(e, "ui_rivals.png")
    }

    @Test
    fun officeStart() {
        val e = game()
        e.ui.tip = null
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.OFFICE }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        shoot(e, "ui_office_start.png")
    }

    private fun venue(e: GameEngine, type: String, items: List<PlacedItem>, roles: List<com.hustlecity.game.data.StaffRole>): com.hustlecity.game.world.Building {
        e.ui.tip = null
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.gridW >= 13 && it.gridD >= 10 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, type, "Venue")!!
        biz.furniture += items
        biz.tempClosed = false
        for ((n, r) in roles.withIndex()) e.state.employees += com.hustlecity.game.model.Employee(e.state.newId(), "S$n", r, .6, 20.0, 20.0, 30, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        e.economy.servedRate[biz.id] = 120.0
        repeat(30 * 50) { e.interior!!.update(1 / 30f, true) }
        return b
    }

    @Test
    fun nightclubInterior() {
        val e = game()
        e.state.minute = (e.state.minute / 1440) * 1440 + 23 * 60
        venue(e, "nightclub", listOf(PlacedItem("cocktail_bar", 1, 0, 0), PlacedItem("dance_floor", 6, 3, 0), PlacedItem("dj_booth", 7, 0, 0),
            PlacedItem("barstool", 1, 2, 0), PlacedItem("barstool", 3, 2, 0), PlacedItem("neon_circle", 11, 0, 0), PlacedItem("speaker_large", 11, 3, 0)),
            listOf(com.hustlecity.game.data.StaffRole.CUSTOMER_SERVICE, com.hustlecity.game.data.StaffRole.DJ))
        shoot(e, "ui_nightclub.png")
    }

    @Test
    fun cinemaInterior() {
        val e = game()
        e.state.minute = (e.state.minute / 1440) * 1440 + 19 * 60
        venue(e, "cinema", listOf(PlacedItem("cinema_screen", 4, 0, 0), PlacedItem("cinema_seats", 2, 3, 0), PlacedItem("cinema_seats", 6, 3, 0),
            PlacedItem("cinema_seats", 2, 5, 0), PlacedItem("cinema_seats", 6, 5, 0), PlacedItem("cash_register", 10, 7, 0), PlacedItem("concession_stand", 1, 7, 0)),
            listOf(com.hustlecity.game.data.StaffRole.CUSTOMER_SERVICE, com.hustlecity.game.data.StaffRole.PROJECTIONIST))
        shoot(e, "ui_cinema.png")
    }

    private fun weather(e: GameEngine, sky: com.hustlecity.game.model.Sky) {
        e.state.weather.clear()
        e.state.weather += com.hustlecity.game.model.WeatherSpan(e.state.minute - 60, e.state.minute + 3 * 1440L, sky)
        e.weather.ensure()
    }

    @Test
    fun streetRain() {
        val e = game()
        e.ui.tip = null
        weather(e, com.hustlecity.game.model.Sky.RAIN)
        e.skyVis.rain = .75f; e.skyVis.cloud = .9f; e.skyVis.wet = 1f
        e.camera.dist = 38f; e.camera.zoom = 38f
        repeat(30) { e.frame(1 / 30f) }
        shoot(e, "street_rain.png")
    }

    @Test
    fun streetSnow() {
        val e = game()
        e.ui.tip = null
        weather(e, com.hustlecity.game.model.Sky.SNOW)
        e.state.snowCover = 1.0
        e.skyVis.snow = .85f; e.skyVis.cloud = .8f
        e.camera.dist = 60f; e.camera.zoom = 60f
        repeat(30) { e.frame(1 / 30f) }
        shoot(e, "street_snow.png")
    }

    @Test
    fun dirtyCafe() {
        val e = game()
        e.ui.tip = null
        weather(e, com.hustlecity.game.model.Sky.RAIN)
        e.state.minute = (e.state.minute / 1440) * 1440 + 9 * 60
        val b = venue(e, "coffee", listOf(PlacedItem("cash_register", 4, 1, 0), PlacedItem("coffee_machine", 0, 0, 0), PlacedItem("pastry_case", 8, 0, 0),
            PlacedItem("table_set", 1, 4, 0), PlacedItem("table_set", 5, 4, 0), PlacedItem("table_set", 9, 4, 0), PlacedItem("trash_can", 12, 0, 0),
            PlacedItem("barstool", 1, 8, 0), PlacedItem("barstool", 2, 8, 0)),
            listOf(com.hustlecity.game.data.StaffRole.CUSTOMER_SERVICE))
        val biz = e.state.businessAt(b.id)!!
        repeat(400) { e.economy.dirt.onCustomer(biz, b.area) }
        shoot(e, "ui_dirty_cafe.png")
    }

    @Test
    fun streetCloseup() {
        val e = game()
        e.ui.tip = null
        e.ui.phoneOpen = false
        e.ui.objectivesOpen = false
        weather(e, com.hustlecity.game.model.Sky.RAIN)
        e.skyVis.rain = .5f; e.skyVis.cloud = .6f; e.skyVis.wet = .6f
        // on the classic grid: 3rd avenue from the west, 7th street from the north
        val x = 314f
        val y = 430f
        e.state.player.x = x; e.state.player.y = y
        e.camera.tx = x; e.camera.ty = y; e.camera.dist = 16f; e.camera.zoom = 16f
        e.traffic.ensure(30, 120)
        e.traffic.update(0f, x, y, 60f, -9999f, -9999f, initial = true)
        repeat(240) { e.frame(1 / 30f); e.camera.tx = x; e.camera.ty = y; e.camera.dist = 16f; e.camera.zoom = 16f }
        shoot(e, "street_people.png")
    }

    @Test
    fun drivingWithGps() {
        val e = game()
        e.ui.tip = null
        weather(e, com.hustlecity.game.model.Sky.CLEAR)
        val v = e.state.vehicles.first()
        e.enterVehicle(v)
        e.ui.tip = null
        e.ui.phoneOpen = false
        val target = e.city.placeBuilding("metro")!!
        e.state.player.waypoint = target.id
        e.input.gas = true
        repeat(60) { e.frame(1 / 30f) }
        e.input.gas = false
        shoot(e, "ui_driving.png")
    }

    @Test
    fun walkingMinimap() {
        val e = game()
        e.ui.tip = null
        e.ui.phoneOpen = true
        val target = e.city.placeBuilding("essentials")!!
        e.state.player.waypoint = target.id
        repeat(10) { e.frame(1 / 30f) }
        shoot(e, "ui_minimap.png")
    }

    @Test
    fun mapsWithRoute() {
        val e = game()
        e.ui.tip = null
        val target = e.city.placeBuilding("metro")!!
        e.state.player.waypoint = target.id
        repeat(10) { e.frame(1 / 30f) }
        e.ui.app = AppId.MAPS
        e.camera.mapX = (e.state.player.x + target.doorX) / 2; e.camera.mapY = (e.state.player.y + target.doorY) / 2; e.camera.mapDist = 900f
        shoot(e, "ui_maps_route.png")
    }

    @Test
    fun drivingArrowKeys() {
        val e = game()
        e.ui.tip = null
        e.settings.carControls = 2
        weather(e, com.hustlecity.game.model.Sky.CLEAR)
        e.enterVehicle(e.state.vehicles.first())
        e.ui.tip = null
        e.ui.phoneOpen = false
        e.input.gas = true
        repeat(45) { e.frame(1 / 30f) }
        e.input.gas = false
        shoot(e, "ui_driving_arrows.png")
    }
}

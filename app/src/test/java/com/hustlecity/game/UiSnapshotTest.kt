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
import com.hustlecity.game.engine.startBusiness
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
}

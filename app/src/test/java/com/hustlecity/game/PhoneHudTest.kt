package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.startBusiness
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
import kotlin.math.hypot

/** The HUD on narrower phones (the player's own is about 727 x 327 dp): nothing may be cut off or overlap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhoneHudTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(e: GameEngine, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        repeat(8) { e.frame(1 / 30f); rule.mainClock.advanceTimeBy(34) }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File("build/snapshots").mkdirs()
        File("build/snapshots/$name").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun game(): GameEngine = GameEngine(null).also { it.newGame("Alex Rivera", 1, 2, 1, mapId = "manila"); it.ui.tip = null; it.ui.phoneOpen = false }

    private fun walking(tag: String) {
        val e = game()
        val p = e.state.player
        val dest = e.city.buildings.filter { it.kind == BKind.RETAIL && hypot(it.doorX - p.x, it.doorY - p.y) in 150f..300f }.first()
        e.setWaypoint(dest.id)
        e.ui.objectivesOpen = true
        e.toast("Uncle Sal: Welcome to Manila! Rent a shop to get started")
        shoot(e, "phone_${tag}_walk.png")
    }

    private fun driving(tag: String) {
        val e = game()
        e.settings.carControls = 2
        e.enterVehicle(e.state.vehicles.first())
        e.ui.tip = null
        shoot(e, "phone_${tag}_drive.png")
    }

    private fun shop(tag: String) {
        val e = game()
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridD >= 8 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        e.startBusiness(b.id, "coffee", "Corner Market")
        e.state.player.x = b.doorX; e.state.player.y = b.doorY
        e.enterBuilding(b)
        e.ui.tip = null
        e.toast("Uncle Sal: Corner Market — great name! Now buy furniture for it")
        shoot(e, "phone_${tag}_shop.png")
    }

    @Test @Config(qualifiers = "w727dp-h327dp-land-xxhdpi") fun walkPhone() = walking("727")
    @Test @Config(qualifiers = "w727dp-h327dp-land-xxhdpi") fun drivePhone() = driving("727")
    @Test @Config(qualifiers = "w727dp-h327dp-land-xxhdpi") fun shopPhone() = shop("727")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun walkSmall() = walking("640")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun shopSmall() = shop("640")
}

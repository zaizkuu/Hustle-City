package com.hustlecity.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.ui.menu.HustleCityApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The HUD must follow the game state: buttons change when you get in and out of a car. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h400dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HudUpdateTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun step(e: GameEngine, frames: Int = 4) {
        repeat(frames) { e.frame(1 / 30f); rule.mainClock.advanceTimeBy(34) }
    }

    private fun dump(label: String) {
        val texts = rule.onAllNodes(androidx.compose.ui.test.hasText("", substring = true)).fetchSemanticsNodes()
            .map { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString() }
        println("$label: $texts")
    }

    private fun count(text: String) = rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().size

    @Test
    fun drivingSwapsTheControls() {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.ui.tip = null
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        val v = e.state.vehicles.first()
        e.state.player.x = v.x + 1.5f; e.state.player.y = v.y
        step(e)
        dump("walking")
        assertTrue("walking next to the car shows Get in", count("Get in") > 0)
        e.enterVehicle(v)
        e.ui.phoneOpen = false
        e.ui.tip = null
        step(e)
        dump("driving")
        assertEquals("no walking buttons while driving", 0, count("Get in"))
        assertEquals(0, count("Enter Home"))
        assertTrue("park button while driving", count("Park") > 0)
        assertTrue("get out button while driving", count("Get out") > 0)
        assertTrue("gas pedal while driving", count("GAS") > 0)
        e.exitVehicle()
        step(e)
        assertEquals("driving controls gone after getting out", 0, count("GAS"))
        assertTrue("walking buttons are back", count("Get in") > 0)
    }

    @Test
    fun arrowKeyControls() {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.ui.tip = null
        e.settings.carControls = 2
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        e.enterVehicle(e.state.vehicles.first())
        e.ui.phoneOpen = false
        e.ui.tip = null
        step(e)
        assertTrue(count("LEFT") > 0 && count("RIGHT") > 0 && count("GAS") > 0 && count("BRAKE") > 0)
        assertEquals("no steering wheel in arrow mode", 0, count("STEER"))
    }

    @Test
    fun trunkButtonsShowWhenTheCarIsLoaded() {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.ui.tip = null
        rule.mainClock.autoAdvance = false
        rule.setContent { HustleCityApp(e, onExit = {}) }
        val v = e.state.vehicles.first()
        e.state.player.x = v.x + 1.5f; e.state.player.y = v.y
        v.cargo += com.hustlecity.game.model.CargoItem("shelf", false)
        e.state.player.hand += com.hustlecity.game.model.CargoItem("plant_small", false)
        step(e)
        assertTrue("trunk button next to a loaded car", count("Trunk") > 0)
        assertTrue("carrying indicator", count("Carrying") > 0)
    }
}

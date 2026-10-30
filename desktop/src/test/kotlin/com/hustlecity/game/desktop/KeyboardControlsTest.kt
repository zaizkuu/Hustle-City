package com.hustlecity.game.desktop

import android.content.Context
import androidx.compose.ui.input.key.Key
import com.hustlecity.game.Platform
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Panel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Walking and driving with the keyboard, through the same key mapping the game window uses. */
class KeyboardControlsTest {
    /** A new game, a few frames in: the player stands at the door of home, next to the car. */
    private fun game(): GameEngine {
        Platform.isDesktop = true
        val e = GameEngine(Context(Files.createTempDirectory("hustle").toFile()))
        e.newGame("Alex Rivera", 1, 2, 1)
        e.ui.tip = null
        repeat(3) { e.frame(1 / 30f) }
        return e
    }

    private fun press(e: GameEngine, key: Key): Boolean = Keys.handle(e, key, true).also { Keys.handle(e, key, false) }

    @Test
    fun keyboardIsTheDesktopDefault() {
        assertTrue(game().settings.keyboard)
    }

    @Test
    fun wasdAndArrowsWalk() {
        val e = game()
        assertTrue(Keys.handle(e, Key.W, true))
        assertEquals(-1f, e.input.jy)
        Keys.handle(e, Key.DirectionRight, true)
        assertEquals(1f, e.input.jx)
        Keys.handle(e, Key.W, false)
        Keys.handle(e, Key.DirectionRight, false)
        assertEquals(0f, e.input.jx)
        assertEquals(0f, e.input.jy)
    }

    @Test
    fun getInDriveAndGetOut() {
        val e = game()
        assertNotNull("the car is parked by home", e.nearVehicle)
        assertNotNull("the player starts at home's door", e.nearDoor)
        // at a door and a car, F takes the car
        assertTrue(press(e, Key.F))
        assertTrue(e.state.player.driving >= 0)
        Keys.handle(e, Key.W, true)
        assertTrue(e.input.gas)
        Keys.handle(e, Key.W, false)
        Keys.handle(e, Key.Spacebar, true)
        assertTrue(e.input.brake)
        Keys.handle(e, Key.Spacebar, false)
        Keys.handle(e, Key.A, true)
        assertEquals(-1f, e.input.steer)
        Keys.handle(e, Key.A, false)
        // standing still, E gets out
        assertTrue(press(e, Key.E))
        assertTrue(e.state.player.driving < 0)
    }

    @Test
    fun eGoesInTheDoor() {
        val e = game()
        assertTrue(press(e, Key.E))
        assertTrue(e.state.player.inBuilding >= 0)
    }

    @Test
    fun aHeldKeyActsOnce() {
        val e = game()
        assertTrue(Keys.handle(e, Key.F, true))
        assertTrue(e.state.player.driving >= 0)
        // the key repeats while held: that mustn't get straight back out
        assertTrue(Keys.handle(e, Key.E, true))
        assertTrue(e.state.player.driving < 0)
        assertTrue(Keys.handle(e, Key.E, true))
        assertTrue(e.state.player.driving < 0)
    }

    @Test
    fun menusKeepTheirLetters() {
        val e = game()
        e.ui.panel = Panel.Settings
        assertFalse(Keys.handle(e, Key.W, true))
        assertFalse(Keys.handle(e, Key.E, true))
    }
}

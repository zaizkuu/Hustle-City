package com.hustlecity.game.desktop

import android.content.Context
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.hustlecity.game.Platform
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Panel
import com.hustlecity.game.engine.Screen
import com.hustlecity.game.ui.menu.HustleCityApp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Renders the desktop game window headlessly, with the classic renderer drawing through the Android Canvas
 * stand-ins, into build/snapshots. Checks that the pictures aren't blank, and leaves them for a look.
 */
class DesktopSnapshotTest {
    private val outDir = File(System.getProperty("snapshots.dir") ?: "build/snapshots").apply { mkdirs() }

    private fun engine(): GameEngine {
        Platform.isDesktop = true
        return GameEngine(Context(Files.createTempDirectory("hustle").toFile())).also { it.settings.hd = false }
    }

    private fun game(): GameEngine = engine().also { it.newGame("Alex Rivera", 1, 2, 1) }

    /** Renders [e] in a [w] × [h] pixel window at [density], after [frames] game frames. */
    private fun shoot(e: GameEngine, name: String, w: Int = 1600, h: Int = 900, density: Float = 1f, frames: Int = 8, act: () -> Unit = {}) {
        ImageComposeScene(w, h, Density(density)) { HustleCityApp(e, onExit = {}) }.use { scene ->
            var t = 0L
            repeat(frames) { e.frame(1 / 30f); scene.render(t); t += 33_000_000L }
            act()
            repeat(4) { e.frame(1 / 30f); scene.render(t); t += 33_000_000L }
            val image = scene.render(t)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(outDir, name).writeBytes(png)
            // a picture with real content compresses far worse than an empty one
            assertTrue("$name looks blank (${png.size} bytes)", png.size > 40_000)
        }
    }

    @Test fun mainMenu() = shoot(engine().also { it.ui.screen = Screen.MENU }, "desktop_menu.png")

    @Test fun street() = shoot(game(), "desktop_street.png")

    @Test fun streetLaptopScreen() = shoot(game(), "desktop_street_1366.png", w = 1366, h = 705)

    @Test fun streetHighDpi() = shoot(game(), "desktop_street_4k.png", w = 3840, h = 2050, density = 1.5f)

    @Test fun settingsPanel() {
        val e = game()
        e.ui.panel = Panel.Settings
        shoot(e, "desktop_settings_panel.png")
    }

    @Test fun driving() {
        val e = game()
        val v = e.state.vehicles.first()
        e.enterVehicle(v)
        shoot(e, "desktop_driving.png")
    }

    @Test fun keyboardOff() {
        val e = game()
        e.settings.keyboard = false
        shoot(e, "desktop_street_touch.png")
    }
}

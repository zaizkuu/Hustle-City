package com.hustlecity.game.desktop

import android.content.Context
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.hustlecity.game.Platform
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.ui.menu.HustleCityApp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Runs the HD renderer through the desktop's OpenGL path (GL thread, framebuffers, read-back) and saves the game
 * window. Needs an OpenGL 3.3 driver and a display, so it only runs with -Dhustle.gpuTest=true (CI uses a virtual
 * display with Mesa's software renderer).
 */
class DesktopGpuTest {
    private val outDir = File(System.getProperty("snapshots.dir") ?: "build/snapshots").apply { mkdirs() }

    @Test
    fun hdStreet() {
        assumeTrue(System.getProperty("hustle.gpuTest") == "true")
        Platform.isDesktop = true
        val e = GameEngine(Context(Files.createTempDirectory("hustle").toFile()))
        e.settings.hd = true
        e.settings.quality = 2
        e.newGame("Alex Rivera", 1, 2, 1)
        ImageComposeScene(1600, 900, Density(1f)) { HustleCityApp(e, onExit = {}) }.use { scene ->
            var t = 0L
            // the GL thread draws each frame after it's submitted and the window shows it on the next one
            repeat(40) {
                e.frame(1 / 30f)
                scene.render(t)
                t += 33_000_000L
                Thread.sleep(60)
                javax.swing.SwingUtilities.invokeAndWait { }
            }
            assertTrue("no GL context: ${DesktopGl.failure}", DesktopGl.failure == null)
            assertFalse("the HD renderer gave up", e.settings.gpuFailed)
            assertTrue("the GPU drew no frames", e.gpuFps > 0f)
            val png = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(outDir, "desktop_street_hd.png").writeBytes(png)
            assertTrue(png.size > 40_000)
            println("HD renderer: ${DesktopGl.renderer}, ${e.gpuFps} fps at ${e.renderScale}")
            // close up, low sun: the photo materials' detail and bumps
            e.camera.zoom = 26f
            e.camera.yaw = .6f
            repeat(24) {
                e.frame(1 / 30f)
                scene.render(t)
                t += 33_000_000L
                Thread.sleep(60)
                javax.swing.SwingUtilities.invokeAndWait { }
            }
            File(outDir, "desktop_street_hd_close.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}

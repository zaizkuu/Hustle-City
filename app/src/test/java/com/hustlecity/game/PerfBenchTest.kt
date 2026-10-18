package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Times what the main thread does each frame (simulation, building the GPU frame) for typical views and reports the
 * geometry size the GL thread has to upload and draw. Results go to build/perf.txt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PerfBenchTest {
    private val w = 2400 / 2
    private val h = 1080 / 2

    @Test
    fun frameCost() {
        val out = StringBuilder()
        val e = GameEngine(null).also { it.newGame("Bench", 1, 0, 1); it.ui.tip = null; it.ui.phoneOpen = false }
        val r = WorldRenderer(e)
        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        val frame = GpuFrame()
        fun run(name: String, setup: () -> Unit) {
            setup()
            repeat(20) { e.frame(1 / 60f); r.draw(canvas, w, h, 2f, frame) }
            var sim = 0L; var build = 0L
            val n = 40
            repeat(n) {
                val t0 = System.nanoTime()
                e.frame(1 / 60f)
                val t1 = System.nanoTime()
                r.draw(canvas, w, h, 2f, frame)
                val t2 = System.nanoTime()
                sim += t1 - t0; build += t2 - t1
            }
            val verts = frame.streams.sumOf { it.nv }
            val idx = frame.streams.sumOf { it.ni }
            val bytes = verts * GpuFrame.STRIDE * 4 + idx * 4
            val line = "%-14s sim %5.2f ms  build %6.2f ms  verts %6d (g %d o %d t %d)  tris %6d  upload %5.1f MB/frame".format(
                name, sim / n / 1e6, build / n / 1e6, verts, frame.streams[0].nv, frame.streams[1].nv, frame.streams[2].nv, idx / 3, bytes / 1e6)
            println(line); out.appendLine(line)
        }
        run("street") { e.camera.dist = 90f }
        run("street-far") { e.camera.dist = 180f }
        run("street-max") { e.camera.dist = 230f }
        run("night") { e.camera.dist = 120f; e.state.minute = e.state.minute / 1440 * 1440 + 22 * 60 }
        // moving: walking pace and driving pace along the street; the cache rebuilds now and then
        for ((name, speed) in listOf("walking" to 6.5f, "driving" to 15f)) {
            e.camera.dist = 100f
            var total = 0L; var worst = 0L
            val n = 240
            repeat(n) {
                e.state.player.x += speed / 60f
                val t0 = System.nanoTime()
                e.frame(1 / 60f)
                r.draw(canvas, w, h, 2f, frame)
                val dt = System.nanoTime() - t0
                total += dt; worst = maxOf(worst, dt)
            }
            val line = "%-14s avg %5.2f ms  worst %6.2f ms over %d frames".format(name, total / n / 1e6, worst / 1e6, n)
            println(line); out.appendLine(line)
        }
        File("build").mkdirs()
        File("build/perf.txt").writeText(out.toString())
    }
}

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
        for (map in com.hustlecity.game.world.Maps.all) {
        out.appendLine("== ${map.id}"); println("== ${map.id}")
        val e = GameEngine(null).also { it.newGame("Bench", 1, 0, 1, mapId = map.id); it.ui.tip = null; it.ui.phoneOpen = false }
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
            val line = "%-14s sim %5.2f ms  build %6.2f ms  verts %6d (g %d o %d t %d)  tris %6d  upload %5.1f MB/frame  cached tris %d".format(
                name, sim / n / 1e6, build / n / 1e6, verts, frame.streams[0].nv, frame.streams[1].nv, frame.streams[2].nv, idx / 3, bytes / 1e6, r.staticTris())
            println(line); out.appendLine(line)
        }
        run("street") { e.camera.dist = 90f; e.camera.zoom = 90f }
        run("street-far") { e.camera.dist = 180f; e.camera.zoom = 180f }
        run("street-max") { e.camera.dist = 230f; e.camera.zoom = 230f }
        run("night") { e.camera.dist = 120f; e.camera.zoom = 120f; e.state.minute = e.state.minute / 1440 * 1440 + 22 * 60 }
        // moving: walking and driving along the street, turning the camera, zooming. The cache of the city round the view
        // is rebuilt a little each frame in the background; a "phone" run gives that build a quarter of the time per frame,
        // as a phone some four times slower than this machine would get through, to see that it still keeps up
        class Move(val name: String, val speed: Float, val turn: Float = 0f, val zoom: Float = 0f, val phone: Boolean = false)
        val moves = listOf(Move("walking", 6.5f), Move("driving", 15f), Move("fast", 32f), Move("turning", 0f, turn = .9f), Move("zooming", 0f, zoom = .35f),
            Move("phone walk", 6.5f, phone = true), Move("phone drive", 15f, phone = true), Move("phone fast", 32f, phone = true),
            Move("phone turn", 0f, turn = .9f, phone = true), Move("phone zoom", 0f, zoom = .35f, phone = true))
        for (m in moves) {
            e.camera.dist = 100f; e.camera.zoom = 100f
            repeat(20) { e.frame(1 / 60f); r.draw(canvas, w, h, 2f, frame) }
            r.stageBudgetNs = if (m.phone) 625_000L else 2_500_000L
            var total = 0L; var worst = 0L
            val n = 240
            val blocking0 = r.blockingRebuilds
            var spikes = 0
            repeat(n) {
                e.state.player.x += m.speed / 60f
                e.camera.yaw += m.turn / 60f
                // in and out: 100 m to 160 m and back over four seconds
                if (m.zoom > 0f) { e.camera.dist = 100f * (1f + .6f * kotlin.math.sin(it / 240f * Math.PI.toFloat()).let { s -> s * s }); e.camera.zoom = e.camera.dist }
                val b0 = r.blockingRebuilds
                val gc0 = gcMillis()
                val t0 = System.nanoTime()
                e.frame(1 / 60f)
                val t1 = System.nanoTime()
                r.draw(canvas, w, h, 2f, frame)
                val dt = System.nanoTime() - t0
                total += dt; worst = maxOf(worst, dt)
                if (dt > 8_000_000L) { spikes++; if (System.getenv("HC_PROBE") != null) println("   %.1f ms at frame $it: sim %.1f, gc ${gcMillis() - gc0} ms, whole rebuild ${r.blockingRebuilds > b0}".format(dt / 1e6, (t1 - t0) / 1e6)) }
            }
            r.stageBudgetNs = 2_500_000L
            val line = "%-14s avg %5.2f ms  worst %6.2f ms over %d frames, %d over 8 ms, %d blocking cache rebuilds".format(m.name, total / n / 1e6, worst / 1e6, n, spikes, r.blockingRebuilds - blocking0)
            println(line); out.appendLine(line)
        }
        }
        File("build").mkdirs()
        File("build/perf.txt").writeText(out.toString())
    }

    /** Time the JVM has spent collecting garbage so far (by reflection: the Android stubs lack java.lang.management). */
    private fun gcMillis(): Long {
        val beans = Class.forName("java.lang.management.ManagementFactory").getMethod("getGarbageCollectorMXBeans").invoke(null) as List<*>
        val time = Class.forName("java.lang.management.GarbageCollectorMXBean").getMethod("getCollectionTime")
        return beans.sumOf { b -> time.invoke(b) as Long }
    }
}

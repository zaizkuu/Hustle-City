package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * The cached city is built a little wider than the screen, because it's drawn while the view moves and turns until
 * the next one is ready. Nothing it leaves out for being off screen may come on screen before then: built half a
 * screen wide either side (and a screen below) it lasts a .27 radian turn or a 34 m move, and built a screen wide
 * (while the view is being turned) a half-radian turn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CacheMarginTest {
    private val w = 1200
    private val h = 540

    /** The most polygons left out of a cache that came on screen after turning by [turn] or moving by [move] m, over a few starting views. */
    private fun holes(map: String, margin: Float, turn: Float, move: Float = 0f, below: Float = -1f): Int {
        val e = GameEngine(null).also { it.newGame("T", 1, 0, 1, mapId = map); it.ui.tip = null; it.ui.phoneOpen = false }
        val r = WorldRenderer(e)
        val canvas = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        val frame = GpuFrame()
        e.camera.dist = 110f; e.camera.zoom = 110f
        repeat(10) { e.frame(1 / 60f); r.draw(canvas, w, h, 2f, frame) }
        val p = e.state.player
        val px = p.x; val py = p.y
        var worst = 0
        for ((k, yaw0) in listOf(0f, 1.3f, 2.9f, 4.4f).withIndex()) for (dir in listOf(1f, -1f)) {
            p.x = px; p.y = py
            e.camera.yaw = yaw0
            // let the camera settle on the view (it follows the player smoothly)
            repeat(90) { e.frame(1 / 60f) }
            // build the cache for this view, noting what it leaves out
            val left = ArrayList<FloatArray>()
            val before = ArrayList<FloatArray>()
            r.scene.onCulled = { n, xs, ys, zs -> if (r.scene.cacheBuild) {
                left += FloatArray(n * 3).also { a -> for (i in 0 until n) { a[i * 3] = xs[i]; a[i * 3 + 1] = ys[i]; a[i * 3 + 2] = zs[i] } }
                before += FloatArray(4).also { r.scene.onScreen(n, xs, ys, zs, it) }
            } }
            r.forceMargin = margin; r.testMarginBelow = below; r.invalidateCache()
            r.draw(canvas, w, h, 2f, frame)
            r.scene.onCulled = null
            lastTris = r.staticTris()
            // then turn or move, and see whether any of it is on screen now
            e.camera.yaw = yaw0 + turn * dir
            val a = k * 1.7f + dir
            p.x = px + cos(a) * move; p.y = py + sin(a) * move
            repeat(90) { e.frame(1 / 60f) }
            r.draw(canvas, w, h, 2f, frame)
            val xs = FloatArray(32); val ys = FloatArray(32); val zs = FloatArray(32)
            var lost = 0
            val after = FloatArray(4)
            for ((q, poly) in left.withIndex()) {
                val n = poly.size / 3
                for (i in 0 until n) { xs[i] = poly[i * 3]; ys[i] = poly[i * 3 + 1]; zs[i] = poly[i * 3 + 2] }
                if (r.scene.onScreen(n, xs, ys, zs, after)) {
                    if (lost < 3 && System.getenv("HC_PROBE") != null) println("   lost: n=$n at %.1f,%.1f,%.1f screen before %s after %s (screen ${w}x$h)".format(xs[0], ys[0], zs[0], before[q].joinToString { "%.0f".format(it) }, after.joinToString { "%.0f".format(it) }))
                    lost++
                }
            }
            worst = maxOf(worst, lost)
        }
        r.forceMargin = -1f
        return worst
    }

    @Test
    fun survey() {
        org.junit.Assume.assumeTrue(System.getenv("HC_PROBE") != null)
        for (map in listOf(Maps.CLASSIC, Maps.NYC)) {
            for (below in listOf(.5f, .75f, 1f, 1.5f)) {
                for (d in listOf(27f, 34f)) println("$map margin .5, below $below, move $d m: ${holes(map, .5f, 0f, d, below)} polygons came on screen, cache $lastTris tris")
                println("$map margin .5, below $below, turn .3: ${holes(map, .5f, .3f, 0f, below)} polygons came on screen, cache $lastTris tris")
            }
        }
    }

    private var lastTris = 0

    @Test
    fun theCacheLastsUntilItsReplaced() {
        for (map in listOf(Maps.CLASSIC, Maps.NYC, Maps.TOKYO)) {
            val turned = holes(map, .5f, .27f)
            val moved = holes(map, .5f, 0f, 34f)
            val wide = holes(map, 1f, .5f)
            println("$map: on screen after a .27 turn $turned, a 34 m move $moved; wide cache after a .5 turn $wide")
            assertTrue("$map: $turned polygons missing after turning", turned == 0)
            assertTrue("$map: $moved polygons missing after moving", moved == 0)
            assertTrue("$map: $wide polygons missing from the wide cache", wide == 0)
        }
    }
}

package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The GPU renderer's cached city: every map builds one in the driving view, with sane vertices and indices, a margin
 * beyond the screen so it stays whole while the camera drifts, and a size a mid-range phone can upload and draw.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GpuCacheTest {
    @Test
    fun everyCityCachesSoundGeometry() {
        for (map in Maps.all) {
            val e = GameEngine(null).also { it.newGame("B", 1, 0, 1, mapId = map.id); it.ui.tip = null; it.ui.phoneOpen = false }
            e.settings.quality = 2
            e.enterVehicle(e.state.vehicles.first())
            val r = WorldRenderer(e)
            val w = 1200; val h = 540
            val canvas = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
            val f = GpuFrame()
            repeat(10) { e.frame(1 / 30f); r.draw(canvas, w, h, 2f, f) }
            val st = f.staticGeom
            assertNotNull("${map.id}: the city is cached", st)
            st!!
            assertTrue("${map.id}: the ground is cached", st.streams[GpuFrame.GROUND].ni > 0)
            assertTrue("${map.id}: buildings are cached", st.streams[GpuFrame.OPAQUE].ni > 3000)
            var bad = 0; var badIdx = 0
            for (s in st.streams) {
                for (i in 0 until s.nv * GpuFrame.STRIDE step GpuFrame.STRIDE) for (c in 0..2) {
                    val v = java.lang.Float.intBitsToFloat(s.v[i + c])
                    if (v.isNaN() || v.isInfinite() || kotlin.math.abs(v) > 1e5f) bad++
                }
                for (k in 0 until s.ni) if (s.idx[k] < 0 || s.idx[k] >= s.nv) badIdx++
            }
            assertEquals("${map.id}: broken vertices", 0, bad)
            assertEquals("${map.id}: indices out of range", 0, badIdx)
            val mb = st.streams.sumOf { it.nv * GpuFrame.STRIDE * 4 + it.ni * 4 } / 1e6
            println("${map.id}: cache %.2f MB".format(mb))
            assertTrue("${map.id}: cache too big for a phone (%.1f MB)".format(mb), mb < 8.0)
            // the cache reaches past the screen edges so a small move doesn't open holes
            var outside = 0
            val s = st.streams[GpuFrame.OPAQUE]
            val p = r.javaClass.getDeclaredField("proj").apply { isAccessible = true }.get(r) as com.hustlecity.game.render.Projector
            for (i in 0 until s.nv) {
                val x = java.lang.Float.intBitsToFloat(s.v[i * GpuFrame.STRIDE]); val y = java.lang.Float.intBitsToFloat(s.v[i * GpuFrame.STRIDE + 1])
                val z = java.lang.Float.intBitsToFloat(s.v[i * GpuFrame.STRIDE + 2])
                val zc = p.camZOf(x, y, z)
                if (zc <= p.near) continue
                val sx = p.centerX + p.focal * p.camXOf(x, y, z) / zc
                if (sx < -40f || sx > p.screenW + 40f) outside++
            }
            assertTrue("${map.id}: the cache stops at the screen edge", outside > 500)
        }
    }
}

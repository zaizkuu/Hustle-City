package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.CarModels
import com.hustlecity.game.render.Helicopter
import com.hustlecity.game.render.Mat
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The police on the streets and in the air, and the foam where the water meets the quays. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WaterAndPoliceTest {
    @Test
    fun patrolCarModelHasItsLiveryAndLightBar() {
        val m = CarModels.get("Cop")
        assertNotNull("the patrol car is baked into cars.bin", m)
        val roles = m!!.roles.map { it.toInt() }.toSet()
        for (r in listOf(CarModels.PAINT, CarModels.PAINT2, CarModels.GLASS, CarModels.DARK, CarModels.BEACON_RED, CarModels.BEACON_BLUE))
            assertTrue("role $r on the patrol car", r in roles)
        // the light bar sits on the roof
        var k = 0
        for (p in 0 until m.polygons) {
            val n = m.sizes[p].toInt() and 255
            if (m.roles[p].toInt() == CarModels.BEACON_RED || m.roles[p].toInt() == CarModels.BEACON_BLUE)
                for (i in 0 until n) assertTrue("the light bar is on the roof", m.u[m.corners[k + i]] > .85f)
            k += n
        }
    }

    @Test
    fun helicopterStaysOverTheCityAndClearsTheRooftops() {
        for (map in Maps.all) {
            val city = City(7L, map.id)
            val h = Helicopter(city)
            val b = city.bounds
            var t = 0f
            while (t < 600f) {
                h.update(t)
                assertTrue("${map.id}: over the city at $t", h.x > b[0] - 150f && h.x < b[2] + 150f && h.y > b[1] - 150f && h.y < b[3] + 150f)
                var top = 0f
                city.forBuildingsIn(h.x - 8f, h.y - 8f, h.x + 8f, h.y + 8f) { bd -> if (bd.contains(h.x, h.y) && bd.height > top) top = bd.height }
                assertTrue("${map.id}: ${h.z} m clears a ${top} m roof at $t", h.z > top + 8f)
                t += .1f
            }
        }
    }

    @Test
    fun waterMeetsTheQuaysInFoam() {
        val e = GameEngine(null).also { it.newGame("B", 1, 0, 1, mapId = Maps.NYC); it.ui.tip = null; it.ui.phoneOpen = false }
        e.settings.quality = 2
        // on the Brooklyn waterfront, looking over the East River
        e.state.player.x = 983.7f; e.state.player.y = 1350.9f
        val r = WorldRenderer(e)
        val canvas = Canvas(Bitmap.createBitmap(1200, 540, Bitmap.Config.ARGB_8888))
        val f = GpuFrame()
        repeat(10) { e.frame(1 / 30f); r.draw(canvas, 1200, 540, 2f, f) }
        val g = f.staticGeom!!.streams[GpuFrame.GROUND]
        var shore = 0
        for (i in 0 until g.nv) if ((java.lang.Float.intBitsToFloat(g.v[i * GpuFrame.STRIDE + 7]) / 2f).toInt() == Mat.SHORE) shore++
        assertTrue("foam along the quays ($shore corners)", shore >= 8)
    }
}

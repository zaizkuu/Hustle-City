package com.hustlecity.game

import androidx.test.core.app.ApplicationProvider
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StockLayouts
import com.hustlecity.game.data.StockShape
import com.hustlecity.game.render.Models
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

/**
 * The models packaged for the HD renderer: furniture models load, belong to real furniture and fit its footprint;
 * every product sold off a display has a look (or several), modelled at its real size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelsTest {
    private fun setUp() {
        Models.assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        Models.clear()
    }

    @Test
    fun everyModelLoadsAndFits() {
        setUp()
        val ids = Models.ids()
        assertTrue("models are packaged (${ids.size})", ids.size >= 20)
        for (id in ids) {
            val def = Catalog.item(id)
            assertNotNull("$id is a furniture id", def)
            val m = Models.get(id)
            assertNotNull("$id loads", m)
            m!!
            assertTrue("$id has triangles", m.idx.size >= 3 && m.idx.size % 3 == 0)
            assertTrue("$id indices in range", m.idx.all { it in 0 until m.vertices })
            for (v in m.pos) assertTrue("$id positions are finite", v.isFinite())
            for (i in 0 until m.vertices) {
                val nx = m.nrm[i * 3]; val ny = m.nrm[i * 3 + 1]; val nz = m.nrm[i * 3 + 2]
                assertEquals("$id normal $i is unit length", 1f, sqrt(nx * nx + ny * ny + nz * nz), .03f)
            }
            // it stands on the floor inside its footprint, and isn't taller than a room
            val b = m.bounds
            assertEquals("$id stands on the floor", 0f, b[2], .01f)
            assertTrue("$id fits ${def!!.w}x${def.d}: ${b.toList()}", b[0] >= -.02f && b[1] >= -.02f && b[3] <= def.w + .02f && b[4] <= def.d + .02f)
            assertTrue("$id fits under the ceiling", b[5] < 2.6f)
        }
    }

    @Test
    fun seatsAreAtSittingHeight() {
        setUp()
        // a seat's model reaches at least its seat height, and the seat isn't buried in it
        for ((id, spots) in Catalog.sitSpots) {
            val m = Models.get(id) ?: continue
            for (s in spots) if (s.pose == 1) assertTrue("$id is tall enough for a seat at ${s.z}", m.bounds[5] >= s.z - .05f)
        }
    }

    @Test
    fun everyProductHasALook() {
        setUp()
        for (p in Catalog.products) {
            val looks = Models.product(p.id)
            if (p.service) { assertNull("${p.id} is a service: nothing on a shelf", looks); continue }
            assertNotNull("${p.id} has a model", looks)
            for ((k, m) in looks!!.withIndex()) {
                val name = "${p.id} look $k"
                assertTrue("$name has triangles", m.idx.size >= 3 && m.idx.size % 3 == 0)
                assertTrue("$name indices in range", m.idx.all { it in 0 until m.vertices })
                for (v in m.pos) assertTrue("$name positions are finite", v.isFinite())
                for (i in 0 until m.vertices) {
                    val nx = m.nrm[i * 3]; val ny = m.nrm[i * 3 + 1]; val nz = m.nrm[i * 3 + 2]
                    assertEquals("$name normal $i is unit length", 1f, sqrt(nx * nx + ny * ny + nz * nz), .03f)
                }
                // modelled at real size: a slot's worth fits in a hand basket, and it's cheap enough to fill a store with
                val b = m.bounds
                assertTrue("$name is real-sized: ${b.toList()}", b[3] - b[0] < .5f && b[4] - b[1] < .75f && b[5] - b[2] < .9f)
                assertTrue("$name is light enough (${m.idx.size / 3} triangles)", m.idx.size / 3 <= 1300)
                if (StockLayouts.shapeOf(p.id) == StockShape.GARMENT) {
                    assertEquals("$name hangs from its hook", 0f, b[5], .01f)
                    assertTrue("$name hangs down", b[2] < -.4f)
                } else assertEquals("$name stands on the shelf", 0f, b[2], .005f)
            }
        }
        // the box unopened stock comes in
        assertNotNull(Models.product("_box"))
    }

    @Test
    fun withoutAssetsEverythingStaysBoxes() {
        Models.assets = null
        Models.clear()
        try {
            assertNull(Models.get("sofa"))
            assertNull(Models.product("apples"))
            assertTrue(Models.ids().isEmpty())
        } finally {
            setUp()
        }
    }
}

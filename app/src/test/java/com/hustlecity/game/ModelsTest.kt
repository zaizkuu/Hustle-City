package com.hustlecity.game

import androidx.test.core.app.ApplicationProvider
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StockLayouts
import com.hustlecity.game.data.StockShape
import com.hustlecity.game.render.CarPainter
import com.hustlecity.game.render.Models
import com.hustlecity.game.render.StreetProps
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
    fun streetPropsHaveModels() {
        setUp()
        val ids = Models.propIds()
        for (id in listOf("lamp", "park_lamp", "hydrant", "bin", "bench", "bus_stop", "news", "mailbox", "bike_rack", "planter", "traffic_light",
                "food_cart", "vending", "utility_pole", "hoop", "fountain")) {
            assertTrue("$id has a model", id in ids)
            for ((k, m) in Models.prop(id)!!.withIndex()) {
                val name = "$id look $k"
                assertTrue("$name indices in range", m.idx.isNotEmpty() && m.idx.all { it in 0 until m.vertices })
                for (v in m.pos) assertTrue("$name positions are finite", v.isFinite())
                val b = m.bounds
                assertEquals("$name stands on the ground", 0f, b[2], .01f)
                // the city caches hundreds of these: the common ones have to stay light
                val budget = if (id in setOf("lamp", "hydrant", "bin", "park_lamp", "planter")) 250 else 1100
                assertTrue("$name is light enough (${m.vertices} vertices)", m.vertices <= budget)
            }
        }
        // the newspaper boxes come in the four color orders the box version uses; carts in their three, vending machines
        // (pairs and threes), poles and hoops in six
        assertEquals(4, Models.prop("news")!!.size)
        assertEquals(3, Models.prop("food_cart")!!.size)
        for (id in listOf("vending", "utility_pole", "hoop")) assertEquals(id, 6, Models.prop(id)!!.size)
        // every look stays within the ground plan street furniture is placed by
        val kinds = mapOf("lamp" to StreetProps.LAMP, "hydrant" to StreetProps.HYDRANT, "bin" to StreetProps.BIN, "bench" to StreetProps.BENCH,
            "bus_stop" to StreetProps.BUS_STOP, "news" to StreetProps.NEWS, "mailbox" to StreetProps.MAILBOX, "park_lamp" to StreetProps.PARK_LAMP,
            "bike_rack" to StreetProps.BIKE_RACK, "planter" to StreetProps.PLANTER, "food_cart" to StreetProps.VENDOR, "vending" to StreetProps.VENDING,
            "utility_pole" to StreetProps.UTIL_POLE, "hoop" to StreetProps.HOOP, "fountain" to StreetProps.FOUNTAIN)
        // what reaches out overhead (a cart's umbrella, a pole's street light, a hoop's board and rim) is above head height
        val overhead = setOf("food_cart", "utility_pole", "hoop")
        for ((id, k) in kinds) {
            val plan = StreetProps.plan(k)!!
            val base = id == "lamp" || id == "park_lamp"
            for (m in Models.prop(id)!!) {
                // a lamp's plan is its base: only its foot has to fit (the arm reaches out over the road)
                if (base) continue
                var x0 = 9f; var y0 = 9f; var x1 = -9f; var y1 = -9f
                for (v in 0 until m.vertices) {
                    if (id in overhead && m.pos[v * 3 + 2] > 2f) continue
                    x0 = minOf(x0, m.pos[v * 3]); y0 = minOf(y0, m.pos[v * 3 + 1]); x1 = maxOf(x1, m.pos[v * 3]); y1 = maxOf(y1, m.pos[v * 3 + 1])
                }
                assertTrue("$id fits its plan ${plan.toList()}: ${listOf(x0, y0, x1, y1)}", x0 >= plan[0] - .01f && y0 >= plan[1] - .01f && x1 <= plan[2] + .01f && y1 <= plan[3] + .01f)
            }
        }
    }

    /** Riders sit on bikes out on the road, but not on one that's parked (your own, waiting at the curb). */
    @Test
    fun parkedBikesHaveNobodyOnThem() {
        setUp()
        val proj = com.hustlecity.game.render.Projector()
        proj.tx = 0f; proj.ty = 0f; proj.dist = 8f
        proj.update(800f, 600f)
        val scene = com.hustlecity.game.render.Scene3D(proj, com.hustlecity.game.render.RenderBatch())
        val painter = CarPainter(scene)
        fun verts(style: Int, parked: Boolean): Int {
            val f = com.hustlecity.game.render.gpu.GpuFrame()
            scene.gpu = f
            painter.draw(0f, 0f, 0f, 1.9f, .78f, 1.3f, 0xFF2F80ED.toInt(), style, near = true, parked = parked)
            scene.gpu = null
            return f.streams.sumOf { it.nv }
        }
        for (style in intArrayOf(8, 12)) {
            val riding = verts(style, false); val parked = verts(style, true)
            assertTrue("style $style: a rider on the road ($riding vs $parked vertices parked)", riding > parked + 100)
        }
    }

    @Test
    fun carsHaveModels() {
        setUp()
        for ((id, size) in CarPainter.MODEL_SIZE) {
            val m = Models.car(id)
            assertNotNull("$id has a model", m)
            m!!
            assertTrue("$id indices in range", m.idx.isNotEmpty() && m.idx.size % 3 == 0 && m.idx.all { it in 0 until m.vertices })
            for (v in m.pos) assertTrue("$id positions are finite", v.isFinite())
            // there can be dozens on screen
            assertTrue("$id is light enough (${m.vertices} vertices)", m.vertices <= 900)
            // centred on the origin, nose to +x, on its wheels, the size it's scaled from (give or take bumpers, steps,
            // mirrors and a tricycle's lopsided sidecar)
            val b = m.bounds
            assertEquals("$id stands on the ground", 0f, b[2], .01f)
            assertEquals("$id is centred along", 0f, b[0] + b[3], .2f)
            assertEquals("$id is centred across", 0f, b[1] + b[4], .1f)
            assertEquals("$id length", size[0], b[3] - b[0], maxOf(size[0] * .04f, .45f))
            assertTrue("$id width ${b[4] - b[1]}", b[4] - b[1] >= size[1] - .02f && b[4] - b[1] <= size[1] + .5f)
            // (a rider brings a bike up to its height)
            assertTrue("$id height ${b[5]}", (id in CarPainter.RIDDEN || b[5] >= size[2] - .01f) && b[5] <= size[2] + .3f)
            // it takes the car's paint, and has headlights and tail lights
            val parts = m.parts
            assertTrue("$id has paint", parts.count { it == Models.PART_PAINT } >= 30)
            var heads = 0; var tails = 0
            for (v in 0 until m.vertices) {
                if (parts[v] == Models.PART_LENS && m.pos[v * 3] > b[3] - .5f) heads++
                if (parts[v] == Models.PART_RED_LENS && m.pos[v * 3] < b[0] + .5f) tails++
            }
            assertTrue("$id has headlights", heads >= 4)
            assertTrue("$id has tail lights", tails >= 4)
        }
        // the liveries' second colors, and the windows that light up at night
        for (id in listOf("jeepney", "modern_jeep", "tricycle", "kei", "bus", "motorbike", "moped_box"))
            assertTrue("$id takes a second color", Models.car(id)!!.parts.count { it == Models.PART_ACCENT } >= 6)
        for (id in listOf("modern_jeep", "bus")) assertTrue("$id has lit windows", Models.car(id)!!.parts.count { it == Models.PART_WINDOW } >= 24)
        // the light versions for the middle distance: the same size, a good deal lighter, still lit up at night
        for (id in CarPainter.MODEL_SIZE.keys - CarPainter.RIDDEN + listOf("tricycle", "tricycle_l") - listOf("moped_box")) {
            val full = Models.car(id)!!
            val light = Models.car(id + "_lod")
            assertNotNull("$id has a light version", light)
            light!!
            for (k in 0 until 6) assertEquals("$id light version's bounds", full.bounds[k], light.bounds[k], .25f)
            assertTrue("$id light version is lighter (${light.vertices} of ${full.vertices})", light.vertices <= full.vertices * 2 / 3 && light.vertices <= 600)
            assertTrue("$id light version keeps its lights", light.parts.count { it == Models.PART_LENS } >= 4 && light.parts.count { it == Models.PART_RED_LENS } >= 4)
        }
        // a mirror image keeps its doors (or sidecar) on the other side
        for (id in listOf("modern_jeep", "tricycle", "bus")) {
            val r = Models.car(id)!!; val l = Models.car(id + "_l")!!
            assertEquals("$id mirrored", r.bounds[1], -l.bounds[4], .001f)
            assertEquals("$id mirrored", r.bounds[4], -l.bounds[1], .001f)
        }
    }

    @Test
    fun withoutAssetsEverythingStaysBoxes() {
        Models.assets = null
        Models.clear()
        try {
            assertNull(Models.get("sofa"))
            assertNull(Models.product("apples"))
            assertNull(Models.prop("lamp"))
            assertNull(Models.car("sedan"))
            assertTrue(Models.ids().isEmpty())
        } finally {
            setUp()
        }
    }
}

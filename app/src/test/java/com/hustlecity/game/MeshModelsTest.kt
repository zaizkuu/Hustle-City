package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.AnimLib
import com.hustlecity.game.render.MeshLib
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.world.BKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Blender models in the HD renderer (assets/models.hcm, see tools/blender): the pack reads, every model the
 * painters look up is in it, and the frames they build stay within budget at each graphics quality. Writes the
 * numbers to build/models_perf.txt and the frames to build/meshframes/ (tools/blender/view_frames.py renders those).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MeshModelsTest {
    private val w = 1200
    private val h = 540

    @Before
    fun load() {
        File("src/main/assets/models.hcm").inputStream().use { MeshLib.load(it) }
        File("src/main/assets/anims.hca").inputStream().use { AnimLib.load(it) }
    }

    @After
    fun unload() { MeshLib.clear(); AnimLib.clear() }

    @Test
    fun pack() {
        val wanted = listOf("ppl_torso_m_shirt", "ppl_torso_f_tee", "ppl_head_face", "ppl_hair0", "ppl_hair4", "ppl_thigh_leg", "ppl_shin_leg",
            "ppl_upper_arm_long", "ppl_forearm_bare", "ppl_hand_open", "ppl_foot_shoe", "ppl_hair5", "ppl_hips_skirt", "ppl_umbrella_open", "car_sedan", "car_bus", "car_jeepney", "car_taxi", "car_kei_car", "car_modern_jeepney", "stage", "dance_floor", "rowing_machine",
            "prop_lamp_post", "prop_bus_shelter", "cash_register", "sofa", "treadmill", "cinema_seats", "speaker_large",
            "shelf", "bookshelf", "office_chair", "prop_traffic_light", "prop_tree_oak", "prop_tree_oak_bare", "prop_tree_oak_park",
            "prop_lantern_string_line", "car_metro_car", "car_beater", "car_tricycle", "item_burger", "item_safe", "lmk_ferris_rotor",
            "lmk_ferris_gondola", "lmk_lattice_tower", "bld_walkup", "bld_hanok")
        for (n in wanted) assertNotNull("model $n missing from the pack", MeshLib.get(n))
        for (n in MeshLib.names) {
            val m = MeshLib.get(n)!!
            assertTrue("$n has no levels", m.levels.isNotEmpty())
            for (k in 1 until m.levels.size) assertTrue("$n: level $k is heavier than ${k - 1}", m.levels[k].triangles <= m.levels[k - 1].triangles)
            // landmarks and buildings are drawn one or a few at a time; vehicles with riders carry a modelled person
            val cap = when { n.startsWith("bld_") -> 11000; n.startsWith("lmk_") -> 5000; n.startsWith("car_") -> 4000; else -> 2000 }
            assertTrue("$n is too heavy (${m.levels[0].triangles} triangles)", m.levels[0].triangles <= cap)
            assertTrue("$n has odd bounds", m.width > 0f && m.height > 0f)
        }
        // what the painters fit models by
        for (n in MeshLib.names.filter { it.startsWith("bld_") }) assertEquals("$n carries its footprint and wall height", 3, MeshLib.get(n)!!.meta.size)
        assertEquals("the Ferris wheel's rotor knows its hub and rim", 4, MeshLib.get("lmk_ferris_rotor")!!.meta.size)
    }

    /**
     * Every model in the pack is drawn somewhere: the painters name it (a furniture model goes by its catalog id), and
     * every model of the Blender library (art/models/manifest.json, when it's there) made it into the pack.
     */
    @Test
    fun everyModelIsUsed() {
        val src = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val furniture = com.hustlecity.game.data.Catalog.furniture.map { it.id }.toSet()
        val unused = MeshLib.names.filter { n ->
            val named = "\"$n\"" in src
            val furn = n in furniture || (n == "speaker_large")
            val hair = Regex("ppl_hair\\d").matches(n) && "\"ppl_hair\$k\"" in src
            val building = n.startsWith("bld_") && "\"bld_\"" in src && "\"${n.removePrefix("bld_")}\"" in src
            !(named || furn || hair || building)
        }.sorted()
        assertTrue("models in the pack that nothing draws: $unused", unused.isEmpty())
        val manifest = File("../art/models/manifest.json")
        if (manifest.exists()) {
            val ids = Regex("\"id\": \"([a-z0-9_]+)\"").findAll(manifest.readText()).map { it.groupValues[1] }.toList()
            assertTrue("the library lists no models", ids.size > 200)
            val missing = ids.filter { id ->
                listOf(id, "prop_$id", "car_$id", "item_$id", "lmk_$id", "bld_$id", "ppl_" + id.removePrefix("npc_")).none { MeshLib.get(it) != null } &&
                    !(id == "ferris_wheel" && MeshLib.get("lmk_ferris_wheel") != null)
            }
            assertTrue("library models that never made it into the game: $missing", missing.isEmpty())
            println("library: ${ids.size} models, all in the pack (${MeshLib.names.size} meshes with variants)")
        }
    }

    /** A whole person, at each level of detail, stays within budget. */
    @Test
    fun personBudget() {
        // a whole person, at each level
        val limbs = listOf("ppl_upper_arm_long", "ppl_forearm_long", "ppl_hand_open", "ppl_thigh_leg", "ppl_shin_leg", "ppl_foot_shoe")
        val parts = listOf("ppl_torso_m_shirt", "ppl_hips_trousers", "ppl_head_face", "ppl_hair0") + limbs + limbs
        val per = (0..3).map { lv -> parts.sumOf { MeshLib.get(it)!!.level(lv).triangles } }
        println("person triangles by level: $per")
        assertEquals("people are modelled at four levels", 4, MeshLib.get("ppl_head_face")!!.levels.size)
        assertTrue("a person at full detail is too heavy: $per", per[0] <= 10500)
        assertTrue("a person at Medium quality is too heavy: $per", per[1] <= 4600)
        assertTrue("a person across the street is too heavy: $per", per[2] <= 2200)
        assertTrue("a person in the distance is too heavy: $per", per[3] <= 760)
    }

    /** The clips the painter plays are in the pack, on the skeleton it knows, and every pose is a sane one. */
    @Test
    fun clips() {
        val wanted = listOf("walk", "run", "idle", "idle_look", "idle_phone", "sit", "sit_eat", "dance_a", "dance_b", "carry", "carry_idle", "push",
            "push_idle", "work", "reach", "cycle", "row", "lift", "hold", "hang", "wave", "talk")
        val root = FloatArray(3); val q = FloatArray(AnimLib.BONES * 4)
        for (n in wanted) {
            val c = AnimLib.get(n)
            assertNotNull("clip $n missing from the pack", c)
            for (k in 0..40) {
                c!!.at(k / 40f, root, q)
                assertTrue("$n: hips at an odd height ${root[2]}", root[2] in 0f..1.2f)
                for (b in 0 until AnimLib.BONES) {
                    val l = q[b * 4] * q[b * 4] + q[b * 4 + 1] * q[b * 4 + 1] + q[b * 4 + 2] * q[b * 4 + 2] + q[b * 4 + 3] * q[b * 4 + 3]
                    assertTrue("$n: bone $b is not a rotation", l in .98f..1.02f)
                }
            }
        }
        // a loop comes back to where it began
        val a = FloatArray(AnimLib.BONES * 4)
        AnimLib.get("walk")!!.at(0f, root, a); AnimLib.get("walk")!!.at(1f, root, q)
        for (i in a.indices) assertEquals("walk does not close its loop", a[i], q[i], 1e-4f)
        assertEquals("hips sit .88 m up at rest", .88f, AnimLib.rest[AnimLib.HIPS * 3 + 2], 1e-3f)
        assertTrue("the left arm hangs on the left (+x)", AnimLib.rest[AnimLib.UPPER_L * 3] > .1f && AnimLib.rest[AnimLib.UPPER_R * 3] < -.1f)
    }

    private fun tris(f: GpuFrame) = f.triangleCount + (f.staticGeom?.triangleCount ?: 0)

    /** Frame size and build time for the same views with the models off, then at Medium and High. */
    @Test
    fun budgets() {
        val out = StringBuilder()
        fun line(s: String) { println(s); out.appendLine(s) }
        val results = HashMap<String, IntArray>()
        for ((view, dist) in listOf("street" to 60f, "street-close" to 22f, "street-far" to 140f)) {
            // per quality (Low, Medium, High): triangles with the models off, then on
            val counts = IntArray(6)
            for (mode in 0..5) {
                val quality = mode / 2
                val models = mode % 2 == 1
                if (!models) MeshLib.clear() else if (!MeshLib.ready) load()
                val e = GameEngine(null).also { it.newGame("Bench", 1, 0, 1); it.ui.tip = null; it.ui.phoneOpen = false }
                e.settings.quality = quality
                e.state.minute = 15L * 60
                e.camera.zoom = dist; e.camera.dist = dist
                val r = WorldRenderer(e)
                val c = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
                val frame = GpuFrame()
                repeat(20) { e.frame(1 / 60f); r.draw(c, w, h, 2f, frame) }
                var build = 0L
                val n = 30
                repeat(n) { e.frame(1 / 60f); val t = System.nanoTime(); r.draw(c, w, h, 2f, frame); build += System.nanoTime() - t }
                counts[mode] = tris(frame)
                val label = listOf("Low", "Medium", "High")[quality] + if (models) " +models" else ""
                line("%-13s %-15s  dynamic tris %6d  total tris %7d  build %6.2f ms".format(view, label, frame.triangleCount, tris(frame), build / n / 1e6))
                if (view == "street-close" && models && quality >= 1) dump("street_close_q$quality", frame, e.state.player.x, e.state.player.y, 30f)
            }
            results[view] = counts
        }
        File("build").mkdirs()
        File("build/models_perf.txt").writeText(out.toString())
        for ((view, c) in results) {
            // Low is the old renderer exactly; Medium and High may cost more, but not without limit
            assertEquals("$view: Low must not change", c[0], c[1])
            assertTrue("$view: Medium is too heavy (${c[3]} vs ${c[2]})", c[3] <= c[2] * 1.5 + 15000)
            assertTrue("$view: High is too heavy (${c[5]} vs ${c[4]})", c[5] <= c[4] * 2.0 + 30000)
        }
    }

    /** One person in each pose, close up, facing different ways: the modelled figure's posing. */
    @Test
    fun poses() {
        val proj = com.hustlecity.game.render.Projector()
        proj.tx = 0f; proj.ty = 0f; proj.dist = 9f; proj.pitch = Math.toRadians(22.0).toFloat(); proj.yaw = .35f; proj.near = .3f
        proj.update(w.toFloat(), h.toFloat(), .62f)
        val scene = com.hustlecity.game.render.Scene3D(proj, com.hustlecity.game.render.RenderBatch())
        val frame = GpuFrame()
        frame.setCamera(proj)
        scene.gpu = frame
        val p = com.hustlecity.game.render.PersonPainter(scene)
        val shirt = 0xFF2F80ED.toInt(); val skin = 0xFFE0AC69.toInt()
        val pants = com.hustlecity.game.render.PersonPainter.PANTS[1]; val hair = com.hustlecity.game.render.PersonPainter.HAIR[0]
        // standing, mid-stride (phase a quarter turn), sitting on a chair, dancing, carrying a box; then walking east and west
        p.clock = 2.4f
        p.draw(-3f, 0f, 0f, Math.PI.toFloat(), 0f, 0f, shirt, pants, skin, hair)
        p.draw(-1.8f, 0f, 0f, Math.PI.toFloat(), (Math.PI / 2).toFloat(), 1f, 0xFFEB5757.toInt(), pants, skin, hair)
        p.draw(-.6f, 0f, .45f, Math.PI.toFloat(), 0f, 0f, 0xFF27AE60.toInt(), pants, skin, hair, pose = com.hustlecity.game.render.Pose.SIT)
        p.draw(.6f, 0f, 0f, Math.PI.toFloat(), 1f, 0f, 0xFF9B51E0.toInt(), pants, skin, hair, pose = com.hustlecity.game.render.Pose.DANCE)
        p.draw(1.8f, 0f, 0f, Math.PI.toFloat(), 0f, 0f, 0xFFF2994A.toInt(), pants, skin, hair, carry = 1, load = 1)
        // side on, through a stride: full stride, passing, the other foot forward
        for ((k, ph) in listOf(Math.PI / 2, Math.PI / 4, 0.0, -Math.PI / 2).withIndex())
            p.draw(-2.4f + k * 1.4f, -2.6f, 0f, (Math.PI / 2).toFloat(), ph.toFloat(), 1f, 0xFFF2C94C.toInt(), pants, skin, hair)
        // the back row: behind a counter, eating, on the bike, rowing, pressing weights, reaching for a shelf, under an umbrella, with a bag
        val back = com.hustlecity.game.render.Pose
        p.draw(-3.6f, 2.4f, 0f, Math.PI.toFloat(), 0f, 0f, 0xFFE9EDF2.toInt(), pants, skin, hair, apron = 0xFF2C3E50.toInt())
        p.draw(-2.5f, 2.4f, .45f, Math.PI.toFloat(), 0f, 0f, 0xFFEB5757.toInt(), pants, skin, hair, pose = back.EAT)
        p.draw(-1.4f, 2.4f, .78f, Math.PI.toFloat(), 1.2f, 0f, 0xFF56CCF2.toInt(), pants, skin, hair, pose = back.CYCLE)
        p.draw(-.3f, 2.4f, .3f, Math.PI.toFloat(), 2.2f, 0f, 0xFF6FCF97.toInt(), pants, skin, hair, pose = back.ROW)
        p.draw(.8f, 2.4f, .5f, Math.PI.toFloat(), 5.5f, 0f, 0xFFBB6BD9.toInt(), pants, skin, hair, pose = back.LIFT)
        p.draw(1.9f, 2.4f, 0f, Math.PI.toFloat(), 0f, 0f, 0xFFF2994A.toInt(), pants, skin, hair, reach = .5f)
        p.draw(3f, 2.4f, 0f, Math.PI.toFloat(), 0f, 0f, 0xFF2F80ED.toInt(), pants, skin, hair, umbrella = com.hustlecity.game.render.PersonPainter.UMBRELLAS[1])
        p.draw(4.1f, 2.4f, 0f, Math.PI.toFloat(), 1f, 1f, 0xFF27AE60.toInt(), pants, skin, hair, bag = true)
        assertTrue("no figures were drawn", frame.triangleCount > 5 * 1500)
        dump("poses", frame, 0f, 0f, 9f)
    }

    /**
     * Each map around its first landmarks and where you start, by day and after dark, at High quality: the landmarks,
     * buildings, trees, street props and traffic as models, within budget. Dumps one day frame per map (and Tokyo at
     * night) for tools/blender/view_frames.py.
     */
    @Test
    fun cities() {
        val out = StringBuilder()
        for (map in com.hustlecity.game.world.Maps.all) {
            val e = GameEngine(null).also { it.newGame("Tour", 1, 0, 1, mapId = map.id); it.ui.tip = null; it.ui.phoneOpen = false }
            e.settings.quality = 2
            val r = WorldRenderer(e)
            val c = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
            val frame = GpuFrame()
            val lm = com.hustlecity.game.render.Landmarks(e.city)
            // every landmark with ground of its own, framed by its height; then a street where the game starts
            val spots = e.city.landmarks.filter { it.extent != null }.map { floatArrayOf(it.x, it.y, (lm.height(it) * 1.25f).coerceIn(45f, 190f)) } +
                listOf(floatArrayOf(e.state.player.x, e.state.player.y, 32f))
            r.cinematic = true
            r.cinemaPitch = Math.toRadians(30.0).toFloat()
            for ((k, spot) in spots.withIndex()) for (night in listOf(false, true)) {
                e.state.minute = if (night) 21L * 60 + 30 else 14L * 60
                e.state.player.x = spot[0]; e.state.player.y = spot[1]
                e.camera.zoom = spot[2]; e.camera.dist = spot[2]; e.camera.yaw = .6f
                repeat(8) { e.frame(1 / 60f); e.camera.tx = spot[0]; e.camera.ty = spot[1]; r.draw(c, w, h, 2f, frame) }
                val t = tris(frame)
                val label = "${map.id}_${k}_${if (night) "night" else "day"}"
                out.appendLine("%-22s total tris %7d".format(label, t))
                val street = k == spots.size - 1
                if (!night || (street && map.id == com.hustlecity.game.world.Maps.TOKYO)) dump("city_$label", frame, spot[0], spot[1], spot[2] * 1.6f)
                assertTrue("$label is too heavy: $t triangles", t <= 1_200_000)
            }
        }
        println(out)
        File("build").mkdirs()
        File("build/models_perf.txt").appendText(out.toString())
    }

    /** A busy shop: customers and staff as figures, furniture as models. */
    @Test
    fun shop() {
        val e = GameEngine(null).also { it.newGame("Tester", 1, 0, 1); it.ui.tip = null }
        e.settings.quality = 2
        e.state.minute = 13L * 60 + 30
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 13..16 && it.gridD in 9..10 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "coffee", "Bean There")!!
        biz.tempClosed = false
        biz.furniture += PlacedItem("cash_register", 1, 1, 0)
        biz.furniture += PlacedItem("coffee_machine", 4, 1, 0)
        for (k in 0..2) biz.furniture += PlacedItem("table_set", 1 + k * 3, 5, 0)
        biz.furniture += PlacedItem("plant_large", 0, 0, 0)
        biz.furniture += PlacedItem("sofa", 8, 1, 0)
        biz.stock["coffee"] = 400; biz.stock["tea"] = 200
        for (k in 0 until 2) e.state.employees += com.hustlecity.game.model.Employee(e.state.newId(), "Sam", com.hustlecity.game.data.StaffRole.CUSTOMER_SERVICE,
            .6, 15.0, 15.0, 30, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        e.state.player.inBuilding = b.id
        val scene = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.interior = scene
        e.economy.servedRate[biz.id] = 140.0
        repeat(30 * 20) { scene.update(1 / 30f, true) }
        val r = InteriorRenderer(e)
        val frame = GpuFrame()
        r.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)), w, h, frame)
        val withModels = tris(frame)
        MeshLib.clear()
        r.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)), w, h, frame)
        val boxes = tris(frame)
        load()
        r.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)), w, h, frame)
        val sc = e.interior!!
        println("shop: ${sc.visitors.size} visitors, ${sc.staff.size} staff; triangles with models $withModels, boxes $boxes")
        for (v in sc.visitors + sc.staff) println("  person at %.1f,%.1f z %.2f pose %d seated %b heading %.2f door %d,%d".format(v.x, v.y, v.z, v.pose, v.seated, v.heading, sc.doorX, sc.d))
        File("build").mkdirs()
        File("build/models_perf.txt").appendText("shop          ${sc.visitors.size} visitors ${sc.staff.size} staff  models $withModels tris  boxes $boxes tris\n")
        dump("shop", frame, sc.w / 2f, sc.d / 2f, 40f)
        assertTrue("the shop drew nothing", withModels > 0)
        assertTrue("nobody came in", sc.visitors.size + sc.staff.size >= 3)
        assertTrue("the shop is too heavy: $withModels", withModels <= 160000)
    }

    /**
     * Writes the frame's triangles within [radius] meters of (cx, cy) as plain triangles: per vertex x, y, z (floats)
     * and RGBA, with a header of the triangle count and the camera (position and forward).
     */
    private fun dump(name: String, f: GpuFrame, cx: Float, cy: Float, radius: Float) {
        val tris = ArrayList<Int>()
        val srcs = ArrayList<GpuFrame.Stream>()
        f.staticGeom?.let { srcs += it.streams[GpuFrame.OPAQUE] }
        srcs += f.streams[GpuFrame.GROUND]; srcs += f.streams[GpuFrame.OPAQUE]; srcs += f.streams[GpuFrame.TRANSPARENT]
        val r2 = radius * radius
        val bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        val file = File("build/meshframes").apply { mkdirs() }.resolve("$name.bin")
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            val body = java.io.ByteArrayOutputStream()
            val tri = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
            var count = 0
            for (s in srcs) {
                var k = 0
                while (k + 2 < s.ni) {
                    var mx = 0f; var my = 0f
                    for (j in 0 until 3) { val o = s.idx[k + j] * GpuFrame.STRIDE; mx += Float.fromBits(s.v[o]); my += Float.fromBits(s.v[o + 1]) }
                    mx /= 3; my /= 3
                    if ((mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= r2) {
                        tri.clear()
                        for (j in 0 until 3) {
                            val o = s.idx[k + j] * GpuFrame.STRIDE
                            tri.putFloat(Float.fromBits(s.v[o])); tri.putFloat(Float.fromBits(s.v[o + 1])); tri.putFloat(Float.fromBits(s.v[o + 2]))
                            tri.putInt(s.v[o + 6])
                        }
                        body.write(tri.array(), 0, 48)
                        count++
                    }
                    k += 3
                }
            }
            bb.putInt(count)
            for (v in f.cam) bb.putFloat(v)
            for (v in f.fwd) bb.putFloat(v)
            bb.putFloat(f.focal / f.height)
            out.write(bb.array(), 0, bb.position())
            body.writeTo(out)
            tris += count
        }
        println("dumped $name: ${tris.sum()} triangles")
    }
}

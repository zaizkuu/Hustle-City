package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.model.Sky
import com.hustlecity.game.model.WeatherSpan
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.world.Maps
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the camera moves for the trailer as numbered JPEG frames in build/trailer/<scene>/. Only runs when
 * HC_TRAILER=1 is set, since it takes a few minutes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TrailerTest {
    private val w = 1280
    private val h = 720
    private val fps = 30

    /** A camera keyframe: target, distance, yaw. */
    private class Key(val x: Float, val y: Float, val dist: Float, val yaw: Float)

    private fun ease(t: Float) = t * t * (3f - 2f * t)

    private fun engine(mapId: String, hour: Float): GameEngine {
        val e = GameEngine(null)
        e.newGame("Alex Rivera", 1, 2, 1, mapId = mapId)
        e.ui.tip = null; e.ui.phoneOpen = false
        e.state.minute = (e.state.minute / 1440) * 1440 + (hour * 60).toLong()
        return e
    }

    private fun weather(e: GameEngine, sky: Sky) {
        e.state.weather.clear()
        e.state.weather += WeatherSpan(e.state.minute - 60, e.state.minute + 3 * 1440L, sky)
        e.weather.ensure()
    }

    /** Flies the camera through [keys] over [seconds], keeping the player (and so the traffic) at the target. */
    private fun fly(e: GameEngine, name: String, seconds: Float, keys: List<Key>, before: (GameEngine, Int) -> Unit = { _, _ -> }) {
        val dir = File("build/trailer/$name").apply { deleteRecursively(); mkdirs() }
        val r = WorldRenderer(e)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val frames = (seconds * fps).toInt()
        fun place(f: Int) {
            val t = f.toFloat() / (frames - 1).coerceAtLeast(1) * (keys.size - 1)
            val i = t.toInt().coerceAtMost(keys.size - 2)
            val u = ease(t - i)
            val a = keys[i]; val b = keys[i + 1]
            e.camera.tx = a.x + (b.x - a.x) * u; e.camera.ty = a.y + (b.y - a.y) * u
            e.camera.dist = a.dist + (b.dist - a.dist) * u; e.camera.zoom = e.camera.dist
            e.camera.yaw = a.yaw + (b.yaw - a.yaw) * u
            e.state.player.x = e.camera.tx; e.state.player.y = e.camera.ty
        }
        // let traffic and people fill the streets first
        place(0)
        repeat(90) { e.frame(1f / fps); place(0) }
        for (f in 0 until frames) {
            before(e, f)
            e.frame(1f / fps)
            place(f)
            canvas.drawColor(0xFF000000.toInt())
            r.draw(canvas, w, h, 2f)
            File(dir, "%04d.jpg".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        }
    }

    private fun on(): Boolean = System.getenv("HC_TRAILER") == "1"

    @Test
    fun scenes() {
        assumeTrue(on())
        // New York at golden hour: down over Midtown toward the park
        run {
            val e = engine("nyc", 17.6f)
            val park = e.city.parks.maxByOrNull { com.hustlecity.game.world.Geo.area(it.poly) }!!.centre
            val (px, py) = park
            fly(e, "01_nyc", 6f, listOf(Key(px - 40f, py + 260f, 300f, 2.6f), Key(px - 10f, py + 200f, 200f, 3.0f), Key(px + 20f, py + 150f, 140f, 3.3f)))
        }
        // Manila mid-morning: along the street from the first apartment
        run {
            val e = engine(Maps.byId("manila").id, 10f)
            val p = e.state.player
            fly(e, "02_manila", 5f, listOf(Key(p.x, p.y, 95f, -.2f), Key(p.x, p.y, 75f, .5f), Key(p.x, p.y, 60f, 1.2f)))
        }
        // Tokyo after dark: the Shibuya scramble and its signs
        run {
            val e = engine("tokyo", 20.2f)
            weather(e, Sky.CLEAR)
            val lm = e.city.landmarks.firstOrNull { it.name == "Shibuya Crossing" }
            val x = lm?.x ?: e.state.player.x; val y = lm?.y ?: e.state.player.y
            fly(e, "03_tokyo", 6f, listOf(Key(x + 40f, y - 60f, 130f, -.4f), Key(x + 10f, y - 20f, 85f, .2f), Key(x, y, 65f, .8f)))
        }
        // Seoul at sunset: over the hanok roofs of Bukchon
        run {
            val e = engine("seoul", 18.2f)
            val x = 820f; val y = 120f
            fly(e, "04_seoul", 5f, listOf(Key(x - 60f, y + 60f, 110f, .9f), Key(x, y + 30f, 70f, 1.4f), Key(x + 40f, y + 10f, 60f, 1.9f)))
        }
        // the original city under snow
        run {
            val e = engine(Maps.CLASSIC, 11f)
            weather(e, Sky.SNOW)
            e.state.snowCover = 1.0
            e.skyVis.snow = .8f; e.skyVis.cloud = .8f
            val p = e.state.player
            fly(e, "05_classic_snow", 5f, listOf(Key(p.x, p.y, 130f, 0f), Key(p.x, p.y, 105f, .6f), Key(p.x, p.y, 90f, 1.1f)))
        }
        // downtown night skyline
        run {
            val e = engine(Maps.CLASSIC, 22f)
            val b = e.city.buildings.filter { it.neighborhood == 4 }.maxByOrNull { it.height }!!
            fly(e, "06_skyline", 5f, listOf(Key(b.cx, b.cy + 60f, 260f, -.6f), Key(b.cx, b.cy + 30f, 200f, .2f)))
        }
    }

    @Test
    fun storeInterior() {
        assumeTrue(on())
        val e = engine(Maps.CLASSIC, 12f)
        val b = e.city.placeBuilding("essentials")!!
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.PLACE)
        repeat(90) { e.frame(1f / fps) }
        val dir = File("build/trailer/07_store").apply { deleteRecursively(); mkdirs() }
        val r = InteriorRenderer(e)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val start = e.interior!!.yaw
        val frames = 5 * fps
        for (f in 0 until frames) {
            e.frame(1f / fps)
            e.interior!!.yaw = start + .5f * ease(f / (frames - 1f))
            canvas.drawColor(0xFF000000.toInt())
            r.draw(canvas, w, h)
            File(dir, "%04d.jpg".format(f)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        }
    }
}

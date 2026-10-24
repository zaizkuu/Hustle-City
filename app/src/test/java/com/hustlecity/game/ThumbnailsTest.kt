package com.hustlecity.game

import android.graphics.Bitmap
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.render.Models
import com.hustlecity.game.render.Thumbnails
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** With the HD renderer on, the shops' pictures show the modelled furniture and vehicles, whole and in frame. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThumbnailsTest {
    private val out = File("build/thumbs").also { it.mkdirs() }

    @Test
    fun modelledPicturesFillTheirFrame() {
        Models.assets = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets
        val size = 240
        val shots = ArrayList<Pair<String, Bitmap>>()
        for (v in Catalog.vehicles) shots += "vehicle_${v.id}" to models(true) { Thumbnails.vehicle(v.id, size) }
        val modelled = Catalog.furniture.filter { Models.get(it.id) != null }
        assertTrue("furniture models should load", modelled.size >= 10)
        for (f in modelled) shots += "item_${f.id}" to models(true) { Thumbnails.item(f.id, size) }
        for ((name, bmp) in shots) {
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // what's drawn: solid pixels, and how far they reach
            var solid = 0; var x0 = size; var y0 = size; var x1 = -1; var y1 = -1
            for (y in 0 until size) for (x in 0 until size) {
                if ((bmp.getPixel(x, y) ushr 24) < 128) continue
                solid++
                x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y)
            }
            val share = solid.toFloat() / (size * size)
            assertTrue("$name: hardly anything drawn (${"%.3f".format(share)})", share > .015f)
            // whole: not cut off at the picture's edges
            assertTrue("$name: cut off ($x0, $y0)-($x1, $y1)", x0 > 0 && y0 > 0 && x1 < size - 1 && y1 < size - 1)
        }
        // and it's the model, not the box shapes the classic renderer draws
        val classic = models(false) { Thumbnails.vehicle("volta", size) }
        val hd = models(true) { Thumbnails.vehicle("volta", size) }
        var differ = 0
        for (y in 0 until size step 2) for (x in 0 until size step 2) if (classic.getPixel(x, y) != hd.getPixel(x, y)) differ++
        assertTrue("the HD picture should show the model ($differ pixels differ)", differ > 500)
    }

    private fun models(on: Boolean, draw: () -> Bitmap): Bitmap {
        val was = Thumbnails.models
        Thumbnails.models = on
        try { return draw() } finally { Thumbnails.models = was }
    }
}

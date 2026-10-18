package com.hustlecity.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.c
import kotlin.math.max

/** Renders small 3D previews of furniture, vehicles and product boxes for the shop UIs. */
object Thumbnails {
    private val cache = HashMap<String, Bitmap>()
    private val proj = Projector()
    private val batch = RenderBatch(4000)
    private val scene = Scene3D(proj, batch)
    private val parts = PartSorter(scene)
    private val cars = CarPainter(scene)

    private fun begin(size: Int, cx: Float, cy: Float, dist: Float, yawDeg: Double): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        proj.tx = cx; proj.ty = cy; proj.dist = dist
        proj.pitch = Math.toRadians(32.0).toFloat()
        proj.yaw = Math.toRadians(yawDeg).toFloat()
        proj.fovY = Math.toRadians(30.0).toFloat()
        proj.near = .1f
        proj.update(size.toFloat(), size.toFloat(), .5f)
        scene.applyDaylight(1f)
        scene.sunX = .45f; scene.sunY = .89f
        batch.begin(canvas)
        return bmp to canvas
    }

    fun item(id: String, size: Int): Bitmap = cache.getOrPut("i:$id:$size") {
        val def = Catalog.item(id)
        val w = (def?.w ?: 1).toFloat(); val d = (def?.d ?: 1).toFloat()
        val h = def?.parts?.maxOfOrNull { it.z1 } ?: 1f
        val ext = max(max(w, d), h) * 1.1f + .6f
        val (bmp, _) = begin(size, w / 2, d / 2, ext * 2.2f, -30.0)
        proj.update(size.toFloat(), size.toFloat(), .5f + (h / ext) * .18f)
        if (def != null) {
            parts.draw(def, 0f, 0f, 0)
        }
        batch.flush()
        bmp
    }

    fun vehicle(id: String, size: Int): Bitmap = cache.getOrPut("v:$id:$size") {
        val def = Catalog.vehicle(id)
        val (bmp, _) = begin(size, 0f, 0f, def.length * 2.1f, -35.0)
        proj.update(size.toFloat(), size.toFloat(), .58f)
        cars.draw(0f, 0f, (Math.PI / 2).toFloat(), def.length, def.width, def.height, def.color, def.style, true)
        batch.flush()
        bmp
    }

    fun productBox(color: Int, size: Int): Bitmap = cache.getOrPut("p:$color:$size") {
        val (bmp, _) = begin(size, .5f, .5f, 2.6f, -30.0)
        proj.update(size.toFloat(), size.toFloat(), .55f)
        val cardboard = c(0xFFC8A165)
        scene.box(0f, 0f, 0f, 1f, 1f, .8f, cardboard, cardboard)
        scene.box(-.01f, .3f, .3f, 1.01f, .7f, .55f, color, color)
        scene.box(.42f, -.01f, .79f, .58f, 1.01f, .81f, c(0xFFE8D7B0), c(0xFFE8D7B0))
        batch.flush()
        bmp
    }
}

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
            for (p in def.parts) scene.box(p.x0, p.y0, p.z0, p.x1, p.y1, p.z1, p.color, p.color)
        }
        batch.flush()
        bmp
    }

    fun vehicle(id: String, size: Int): Bitmap = cache.getOrPut("v:$id:$size") {
        val def = Catalog.vehicle(id)
        val (bmp, _) = begin(size, 0f, 0f, def.length * 2.1f, -35.0)
        proj.update(size.toFloat(), size.toFloat(), .58f)
        val glass = c(0xFF26313D)
        val hgt = def.height; val len = def.length; val wid = def.width
        val heading = (Math.PI / 2).toFloat()
        // wheels first so the body is painted over the far ones
        val wheel = c(0xFF1E1F22)
        for (sx in floatArrayOf(-len * .32f, len * .32f)) for (sy in floatArrayOf(-wid * .5f, wid * .5f - .2f)) {
            scene.box(sx - .35f, sy, 0f, sx + .35f, sy + .2f, .6f, wheel, wheel)
        }
        when (def.style) {
            2 -> scene.orientedBox(0f, 0f, heading, len, wid, .3f, hgt, def.color, def.color, glass)
            1 -> {
                scene.orientedBox(0f, 0f, heading, len, wid, .32f, hgt * .55f, def.color, def.color)
                scene.orientedBox(0f, 0f, heading, len * .36f, wid * .9f, hgt * .55f, hgt, def.color, glass, glass, len * .14f)
            }
            else -> {
                scene.orientedBox(0f, 0f, heading, len, wid, .25f, hgt * .55f, def.color, def.color)
                scene.orientedBox(0f, 0f, heading, len * .5f, wid * .85f, hgt * .55f, hgt, def.color, glass, glass, -len * .05f)
            }
        }
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

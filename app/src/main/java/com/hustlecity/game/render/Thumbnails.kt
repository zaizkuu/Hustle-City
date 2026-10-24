package com.hustlecity.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.c
import kotlin.math.max
import kotlin.math.min

/** Renders small 3D previews of furniture, vehicles and product boxes for the shop UIs. */
object Thumbnails {
    /**
     * Show the modelled furniture and vehicles, as the HD renderer draws them (set while it runs); otherwise the box
     * shapes the classic renderer draws.
     */
    @Volatile var models = false

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

    fun item(id: String, size: Int): Bitmap = cache.getOrPut("i:$id:$size:$models") {
        val def = Catalog.item(id)
        val w = (def?.w ?: 1).toFloat(); val d = (def?.d ?: 1).toFloat()
        val m = if (models && def != null) Models.get(def.id) else null
        // (a model can stand taller than the box version)
        val h = max(def?.parts?.maxOfOrNull { it.z1 } ?: 1f, m?.bounds?.get(5) ?: 0f)
        val ext = max(max(w, d), h) * 1.1f + .6f
        val (bmp, canvas) = begin(size, w / 2, d / 2, ext * 2.2f, -30.0)
        fit(size, 0f, 0f, 0f, w, d, h, .78f)
        if (m != null) {
            withModels(size, canvas) { scene.mesh(m, 0f, 0f, 0, def!!.w.toFloat(), def.d.toFloat()) }
        } else if (def != null) {
            parts.draw(def, 0f, 0f, 0)
            batch.flush()
        }
        bmp
    }

    private val pt = FloatArray(3)

    /** Moves the camera in or out and centers the picture so the box (x0, y0, z0)-(x1, y1, z1) fills [fill] of it. */
    private fun fit(size: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, fill: Float) {
        repeat(3) {
            proj.update(size.toFloat(), size.toFloat(), .5f)
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (k in 0 until 8) {
                if (!proj.project(if (k and 1 == 0) x0 else x1, if (k and 2 == 0) y0 else y1, if (k and 4 == 0) z0 else z1, pt)) continue
                minX = min(minX, pt[0]); maxX = max(maxX, pt[0]); minY = min(minY, pt[1]); maxY = max(maxY, pt[1])
            }
            if (maxX < minX) return
            val span = max(maxX - minX, maxY - minY)
            if (it < 2) { proj.dist *= span / (size * fill); return@repeat }
            proj.centerX -= (minX + maxX) / 2 - size / 2f
            proj.centerY -= (minY + maxY) / 2 - size / 2f
        }
    }

    /** Runs [draw] with model meshes going to a software rasterizer, then paints them over what else was drawn. */
    private inline fun withModels(size: Int, canvas: Canvas, draw: () -> Unit) {
        val raster = SoftRaster(size)
        scene.soft = raster
        try { draw() } finally { scene.soft = null }
        batch.flush()
        raster.drawOnto(canvas)
    }

    fun vehicle(id: String, size: Int): Bitmap = cache.getOrPut("v:$id:$size:$models") {
        val def = Catalog.vehicle(id)
        val (bmp, canvas) = begin(size, 0f, 0f, def.length * 2.1f, -35.0)
        fit(size, -def.length / 2 - .15f, -def.width / 2 - .1f, 0f, def.length / 2 + .15f, def.width / 2 + .1f, def.height, .86f)
        fun draw() = cars.draw(0f, 0f, (-Math.PI / 2).toFloat(), def.length, def.width, def.height, def.color, def.style, true, parked = true)
        if (models) withModels(size, canvas) { draw() } else { draw(); batch.flush() }
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

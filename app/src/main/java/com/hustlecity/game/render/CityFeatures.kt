package com.hustlecity.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Landmark
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A city's one-off features for the [WorldRenderer]: [Landmarks], elevated [Railways] with their trains, and the
 * [Waterfront] (quays, bridges, old walls). Their pieces are drawables: the still ones go into the GPU's cached city,
 * trains and the Ferris wheel's rotor are drawn every frame. Also paints their ground layers and names them on the map.
 */
class CityFeatures(val city: City) {
    companion object {
        private const val LANDMARK = 0
        private const val ROTOR = 1
        private const val SPAN = 2
        private const val RAILING = 3
        private const val WALL = 4
        private const val DRESS = 5
    }

    val landmarks = Landmarks(city)
    val rails = Railways(city)
    val water = Waterfront(city)

    // parts: kind, index into its owner, bounding circle, height, and a segment for sorting (the closest point counts)
    private var n = 0
    private var kind = IntArray(64); private var idx = IntArray(64)
    private var cx = FloatArray(64); private var cy = FloatArray(64); private var rad = FloatArray(64); private var hgt = FloatArray(64)
    private var ax = FloatArray(64); private var ay = FloatArray(64); private var bx = FloatArray(64); private var by = FloatArray(64)

    private fun part(k: Int, i: Int, x: Float, y: Float, r: Float, h: Float, x0: Float = x, y0: Float = y, x1: Float = x, y1: Float = y) {
        if (n == kind.size) {
            val m = n * 2
            kind = kind.copyOf(m); idx = idx.copyOf(m); cx = cx.copyOf(m); cy = cy.copyOf(m); rad = rad.copyOf(m); hgt = hgt.copyOf(m)
            ax = ax.copyOf(m); ay = ay.copyOf(m); bx = bx.copyOf(m); by = by.copyOf(m)
        }
        kind[n] = k; idx[n] = i; cx[n] = x; cy[n] = y; rad[n] = r; hgt[n] = h; ax[n] = x0; ay[n] = y0; bx[n] = x1; by[n] = y1
        n++
    }

    private val tmp = FloatArray(4)

    init {
        for ((i, l) in city.landmarks.withIndex()) {
            val r = landmarks.radius(l)
            part(LANDMARK, i, l.x, l.y, r, landmarks.height(l))
            if (l.kind == Landmark.WHEEL) part(ROTOR, i, l.x, l.y, r, landmarks.height(l))
        }
        for ((i, sp) in rails.spans.withIndex()) {
            val l = rails.lines.first { it.index == sp.line }
            l.at(sp.q0, tmp); val x0 = tmp[0]; val y0 = tmp[1]
            l.at(sp.q1, tmp)
            part(SPAN, i, sp.cx, sp.cy, sp.r, 14f, x0, y0, tmp[0], tmp[1])
        }
        for ((i, r) in water.railings.withIndex()) {
            val e = city.roads.edges[water.bridges[r.bridge].edge]
            part(RAILING, i, r.cx, r.cy, r.r, 6f, e.ax + e.ux * r.s0, e.ay + e.uy * r.s0, e.ax + e.ux * r.s1, e.ay + e.uy * r.s1)
        }
        for ((i, w) in water.wallPieces.withIndex()) part(WALL, i, w.cx, w.cy, w.r, 12f)
        for ((i, d) in landmarks.dress.withIndex()) {
            val b = city.buildings[d.b]
            part(DRESS, i, b.cx, b.cy, max(b.w, b.d), b.height, b.minX, b.minY, b.maxX, b.maxY)
        }
    }

    /**
     * Calls [add] with (part, sort key) for every part in view: the still ones when [still], the moving ones (the
     * wheel's rotor) when [moving]. Keys are squared distances like the renderer's, smaller for nearer.
     */
    fun collect(bbox: FloatArray, camX: Float, camY: Float, still: Boolean, moving: Boolean, mapMode: Boolean, add: (Int, Float) -> Unit) {
        for (i in 0 until n) {
            val k = kind[i]
            if (k == ROTOR) { if (!moving) continue } else if (!still) continue
            if (mapMode && (k == DRESS || k == RAILING)) continue
            val m = rad[i] + min(hgt[i], 60f) * .5f
            if (cx[i] + m < bbox[0] || cx[i] - m > bbox[2] || cy[i] + m < bbox[1] || cy[i] - m > bbox[3]) continue
            add(i, key(i, camX, camY))
        }
    }

    private fun key(i: Int, camX: Float, camY: Float): Float {
        val d = when (kind[i]) {
            LANDMARK, ROTOR -> {
                val l = city.landmarks[idx[i]]
                val f = if (l.kind == Landmark.TOWER && l.style == Landmark.CONCRETE) .3f else .45f
                max(0f, dist(camX, camY, cx[i], cy[i]) - rad[i] * f)
            }
            DRESS -> {
                // just in front of its building, so it's drawn right after it
                val nx = camX.coerceIn(ax[i], bx[i]); val ny = camY.coerceIn(ay[i], by[i])
                val dd = dist(camX, camY, nx, ny)
                return dd * dd * .999f - .01f
            }
            else -> max(0f, Geo.distToSeg(camX, camY, ax[i], ay[i], bx[i], by[i]) - 3f)
        }
        return d * d
    }

    /** Draws part [i]. [moving] (the Canvas path) also draws what moves with it: trains on a span, the wheel's rotor. */
    fun draw(scene: Scene3D, i: Int, time: Float, moving: Boolean, mapMode: Boolean, hour: Int) {
        val d = dist(scene.proj.camX, scene.proj.camY, cx[i], cy[i])
        when (kind[i]) {
            LANDMARK -> landmarks.draw(scene, idx[i], d, time, moving, mapMode)
            ROTOR -> landmarks.drawRotor(scene, idx[i], d, time)
            SPAN -> { rails.lastTime = time; rails.drawSpan(scene, idx[i], d, moving, mapMode) }
            RAILING -> if (d < 600f) water.drawRailing(scene, idx[i], d)
            WALL -> water.drawWall(scene, idx[i], d)
            DRESS -> landmarks.drawDress(scene, idx[i], d, hour)
        }
    }

    /** Calls [add] with (car, x, y) for every train car in view at [time]. */
    fun forCars(time: Float, bbox: FloatArray, add: (Int, Float, Float) -> Unit) {
        rails.update(time)
        for (i in 0 until rails.carCount) {
            val x = rails.carX(i); val y = rails.carY(i)
            if (x < bbox[0] - 10f || x > bbox[2] + 10f || y < bbox[1] - 10f || y > bbox[3] + 10f) continue
            add(i, x, y)
        }
    }

    fun drawCar(scene: Scene3D, i: Int) {
        val d = dist(scene.proj.camX, scene.proj.camY, rails.carX(i), rails.carY(i))
        rails.drawCar(scene, i, d < 95f)
    }

    /** Ground layers: landmark paving and crossings, lawns, quays and bridge sides; the viaduct's painted shadow. */
    fun drawGround(scene: Scene3D, bbox: FloatArray, mapMode: Boolean, shadowDX: Float, shadowDY: Float, paintShadows: Boolean) {
        landmarks.drawGround(scene, bbox)
        water.drawGround(scene, bbox, mapMode)
        if (paintShadows && rails.spans.isNotEmpty()) rails.shadow(scene, bbox, shadowDX, shadowDY, scene.withAlpha(0xFF000000.toInt(), .2f * scene.daylight))
        scene.material = Mat.PLAIN
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); textAlign = Paint.Align.CENTER }
    private val out = FloatArray(3)

    /** Landmark names over the map. */
    fun mapLabels(canvas: Canvas, proj: Projector, density: Float) {
        if (proj.dist > 1500f) return
        labelPaint.textSize = 11.5f * density
        for (l in city.landmarks) {
            if (!landmarks.labelled(l)) continue
            if (!proj.project(l.x, l.y, landmarks.height(l) * .7f, out)) continue
            if (out[0] < -100f || out[0] > proj.screenW + 100f || out[1] < -30f || out[1] > proj.screenH + 30f) continue
            labelPaint.color = 0xCC000000.toInt()
            canvas.drawText(l.name, out[0] + 1.5f, out[1] + 1.5f, labelPaint)
            labelPaint.color = 0xFFFFF1C9.toInt()
            canvas.drawText(l.name, out[0], out[1], labelPaint)
        }
    }

    private fun dist(x0: Float, y0: Float, x1: Float, y1: Float): Float { val dx = x1 - x0; val dy = y1 - y0; return sqrt(dx * dx + dy * dy) }
}

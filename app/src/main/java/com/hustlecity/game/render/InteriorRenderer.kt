package com.hustlecity.game.render

import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.data.c
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.SHIRT_COLORS
import com.hustlecity.game.engine.SKIN_TONES
import com.hustlecity.game.engine.itemDims
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws building interiors as a cut-away room floating in the dark, like a dollhouse. */
class InteriorRenderer(private val e: GameEngine) {
    val proj = Projector()
    private val batch = RenderBatch()
    val scene = Scene3D(proj, batch)

    private val maxDraw = 1024
    private var dCount = 0
    private val dType = IntArray(maxDraw)
    private val dIdx = IntArray(maxDraw)
    private val dKey = FloatArray(maxDraw)
    private val order = IntArray(maxDraw)

    companion object {
        val BG = c(0xFF16181D)
        val WALL_TOP = c(0xFF1E2024)
        const val WALL_H = 3f
    }

    fun setupCamera(sc: InteriorScene, w: Int, h: Int) {
        proj.tx = sc.w / 2f
        proj.ty = sc.d / 2f
        proj.dist = (max(sc.w, sc.d) * .95f + 5f) / sc.zoom
        proj.pitch = Math.toRadians(56.0).toFloat()
        proj.yaw = sc.yaw
        proj.near = .5f
        proj.update(w.toFloat(), h.toFloat(), .55f)
    }

    fun draw(canvas: Canvas, w: Int, h: Int) {
        val sc = e.interior ?: return
        setupCamera(sc, w, h)
        scene.applyDaylight(1f)
        scene.sunX = .45f; scene.sunY = .89f
        canvas.drawColor(BG)
        batch.begin(canvas)

        // floor (checker tiles)
        val fa = sc.floorColor
        val fb = scene.lit(fa, .9f)
        for (y in 0 until sc.d) for (x in 0 until sc.w) {
            scene.groundRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, 0f, if ((x + y) % 2 == 0) scene.lit(fa, 1f) else fb)
        }
        // entrance mat
        scene.groundRect(sc.doorX - 1f, sc.d - .9f, sc.doorX + 1f, sc.d.toFloat(), .01f, c(0xFF3B3F46))

        if (e.ui.designer) {
            val line = scene.withAlpha(0xFFFFFFFF.toInt(), .22f)
            for (x in 0..sc.w) scene.groundRect(x - .02f, 0f, x + .02f, sc.d.toFloat(), .015f, line)
            for (y in 0..sc.d) scene.groundRect(0f, y - .02f, sc.w.toFloat(), y + .02f, .015f, line)
        }

        // walls: back walls first, front (cut-away) walls last
        val camX = proj.camX; val camY = proj.camY
        val backN = camY > 0; val backS = camY < sc.d; val backW = camX > 0; val backE = camX < sc.w
        val wall = sc.wallColor
        val tall = if (sc.showWalls) WALL_H else .35f
        if (backN) wallN(sc, wall, tall)
        if (backW) wallW(sc, wall, tall)
        if (backE) wallE(sc, wall, tall)
        if (backS) wallS(sc, wall, tall)

        // drawables: items, people, cars
        dCount = 0
        val items = sc.items
        for ((i, p) in items.withIndex()) {
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            add(0, i, key(p.x + fw / 2f, p.y + fd / 2f))
        }
        for ((i, v) in sc.visitors.withIndex()) add(1, i, key(v.x, v.y))
        for ((i, v) in sc.staff.withIndex()) add(2, i, key(v.x, v.y))
        if (e.state.player.inBuilding >= 0) add(3, 0, key(sc.px, sc.py))
        for ((i, car) in sc.showroom.withIndex()) add(4, i, key(car.second, car.third))
        if (sc.ghostId != null) {
            val def = Catalog.item(sc.ghostId!!)
            if (def != null) {
                val (fw, fd) = itemDims(def, sc.ghostRot)
                add(5, 0, key(sc.ghostX + fw / 2f, sc.ghostY + fd / 2f) - .01f)
            }
        }
        for (i in 0 until dCount) order[i] = i
        // insertion sort, far first (counts are small)
        for (i in 1 until dCount) {
            val v = order[i]; val kv = dKey[v]
            var j = i - 1
            while (j >= 0 && dKey[order[j]] < kv) { order[j + 1] = order[j]; j-- }
            order[j + 1] = v
        }
        for (k in 0 until dCount) {
            val i = order[k]
            when (dType[i]) {
                0 -> {
                    val p = items[dIdx[i]]
                    val def = Catalog.item(p.itemId) ?: continue
                    if (dIdx[i] == sc.movingIndex) continue
                    if (dIdx[i] == sc.selectedIndex) {
                        val (fw, fd) = itemDims(def, p.rot)
                        scene.groundRect(p.x.toFloat(), p.y.toFloat(), (p.x + fw).toFloat(), (p.y + fd).toFloat(), .02f, scene.withAlpha(c(0xFF2F80ED), .45f))
                    }
                    drawItem(def, p.x.toFloat(), p.y.toFloat(), p.rot, 1f)
                }
                1 -> sc.visitors.getOrNull(dIdx[i])?.let { person(it.x, it.y, it.shirt, it.skin, it.phase) }
                2 -> sc.staff.getOrNull(dIdx[i])?.let { person(it.x, it.y, it.shirt, it.skin, 0f, apron = true) }
                3 -> {
                    val p = e.state.player
                    scene.disc(sc.px, sc.py, .02f, .45f, .45f, 12, scene.withAlpha(c(0xFF27AE60), .55f))
                    person(sc.px, sc.py, SHIRT_COLORS[p.shirt % SHIRT_COLORS.size], SKIN_TONES[p.skin % SKIN_TONES.size], e.realTime * 6f)
                }
                4 -> {
                    val car = sc.showroom[dIdx[i]]
                    val def = Catalog.vehicle(car.first)
                    scene.disc(car.second, car.third, .02f, 2.6f, 2.6f, 16, scene.withAlpha(0xFFFFFFFF.toInt(), .25f))
                    drawCar(car.second, car.third, 2.4f, def.length, def.width, def.height, def.color, def.style)
                }
                5 -> {
                    val def = Catalog.item(sc.ghostId!!) ?: continue
                    val valid = sc.ghostValid
                    val (fw, fd) = itemDims(def, sc.ghostRot)
                    scene.groundRect(sc.ghostX.toFloat(), sc.ghostY.toFloat(), (sc.ghostX + fw).toFloat(), (sc.ghostY + fd).toFloat(), .03f,
                        scene.withAlpha(if (valid) c(0xFF27AE60) else c(0xFFEB5757), .55f))
                    drawItem(def, sc.ghostX.toFloat(), sc.ghostY.toFloat(), sc.ghostRot, .75f)
                }
            }
        }
        if (!backN) wallN(sc, wall, .35f)
        if (!backW) wallW(sc, wall, .35f)
        if (!backE) wallE(sc, wall, .35f)
        if (!backS) wallS(sc, wall, .35f)
        batch.flush()
    }

    private fun key(x: Float, y: Float): Float { val dx = x - proj.camX; val dy = y - proj.camY; return dx * dx + dy * dy }

    private fun add(type: Int, idx: Int, key: Float) {
        if (dCount >= maxDraw) return
        dType[dCount] = type; dIdx[dCount] = idx; dKey[dCount] = key; dCount++
    }

    private fun wallN(sc: InteriorScene, color: Int, h: Float) {
        scene.box(-.2f, -.2f, 0f, sc.w + .2f, 0f, h, WALL_TOP, color)
        if (h > 1f) {
            var x = 1.5f
            while (x + 1.4f < sc.w - .5f) {
                scene.quad(x, .02f, 1.1f, x + 1.4f, .02f, 1.1f, x + 1.4f, .02f, 2.5f, x, .02f, 2.5f, scene.lit(c(0xFF9FC6E6), .95f))
                x += 3.5f
            }
        }
    }

    private fun wallS(sc: InteriorScene, color: Int, h: Float) {
        scene.box(-.2f, sc.d.toFloat(), 0f, sc.doorX - 1f, sc.d + .2f, h, WALL_TOP, color)
        scene.box(sc.doorX + 1f, sc.d.toFloat(), 0f, sc.w + .2f, sc.d + .2f, h, WALL_TOP, color)
    }

    private fun wallW(sc: InteriorScene, color: Int, h: Float) = scene.box(-.2f, -.2f, 0f, 0f, sc.d + .2f, h, WALL_TOP, color)

    private fun wallE(sc: InteriorScene, color: Int, h: Float) = scene.box(sc.w.toFloat(), -.2f, 0f, sc.w + .2f, sc.d + .2f, h, WALL_TOP, color)

    /** Draws a furniture model at grid position (x, y) with rotation. */
    fun drawItem(def: FurnitureDef, x: Float, y: Float, rot: Int, alpha: Float) {
        val w = def.w.toFloat(); val d = def.d.toFloat()
        for (p in def.parts) {
            // rotate both corners into room space
            val ax: Float; val ay: Float; val bx: Float; val by: Float
            when (rot % 4) {
                0 -> { ax = p.x0; ay = p.y0; bx = p.x1; by = p.y1 }
                1 -> { ax = d - p.y0; ay = p.x0; bx = d - p.y1; by = p.x1 }
                2 -> { ax = w - p.x0; ay = d - p.y0; bx = w - p.x1; by = d - p.y1 }
                else -> { ax = p.y0; ay = w - p.x0; bx = p.y1; by = w - p.x1 }
            }
            scene.box(x + min(ax, bx), y + min(ay, by), p.z0, x + max(ax, bx), y + max(ay, by), p.z1, p.color, p.color, alpha)
        }
    }

    private fun person(x: Float, y: Float, shirt: Int, skin: Int, phase: Float, apron: Boolean = false) {
        val bob = abs(sin(phase)) * .04f
        scene.billboard(x, y, 0f, .8f, .13f, scene.lit(c(0xFF34383F), 1f))
        scene.billboard(x, y, .8f + bob, 1.4f + bob, .22f, scene.lit(if (apron) c(0xFF2C3E50) else shirt, 1f))
        scene.billboard(x, y, 1.45f + bob, 1.72f + bob, .13f, scene.lit(skin, 1f))
    }

    private fun drawCar(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int) {
        val glass = c(0xFF26313D)
        when (style) {
            2 -> scene.orientedBox(x, y, heading, len, wid, .3f, hgt, color, color, glass)
            1 -> {
                scene.orientedBox(x, y, heading, len, wid, .32f, hgt * .55f, color, color)
                scene.orientedBox(x, y, heading, len * .36f, wid * .9f, hgt * .55f, hgt, color, glass, glass, len * .14f)
            }
            else -> {
                scene.orientedBox(x, y, heading, len, wid, .25f, hgt * .55f, color, color)
                scene.orientedBox(x, y, heading, len * .5f, wid * .85f, hgt * .55f, hgt, color, glass, glass, -len * .05f)
            }
        }
    }
}

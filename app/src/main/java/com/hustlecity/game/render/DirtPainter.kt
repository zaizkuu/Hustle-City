package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.model.Business
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A store's dirt, tile by tile as [Business.dirt] has it (0 clean .. 100 filthy): first scuffs and, by the door, muddy
 * shoe prints; then grimy smudges and dried spills that spread and darken; then litter dropped on the floor (crumpled
 * paper, napkins, wrappers, receipts, crumbs, cups and cans on their sides, in a cafe coffee cups and sugar
 * packets), with a fresh spill by a fallen cup on the worst tiles. Everything on a tile stays put from frame to frame.
 */
class DirtPainter(private val scene: Scene3D) {
    companion object {
        private val GRIME = c(0xFF2E241A)
        private val MUD = c(0xFF5A4632)
        private val SCUFF = c(0xFF26231F)
        // coffee, cola, juice and grey mop water
        private val SPILLS = intArrayOf(c(0xFF4A2E1A), c(0xFF2A1A10), c(0xFF7A5A1A), c(0xFF3A3A34))
        private val WRAPPERS = intArrayOf(c(0xFFEB5757), c(0xFFF2C94C), c(0xFF2F80ED), c(0xFF27AE60), c(0xFF9B51E0), c(0xFFF2994A))
        private val CANS = intArrayOf(c(0xFFD32F2F), c(0xFF1565C0), c(0xFF2E7D32), c(0xFFB0B7BF), c(0xFFF9A825))
        private val PAPER = c(0xFFF4F1EA)
        private val NAPKIN = c(0xFFFAFAF7)
        private val CRUMB = c(0xFFC8A165)
        private val CUP = c(0xFFF5F5F2)
        private val COFFEE_SLEEVE = c(0xFF8D6E63)
    }

    /** Small steady hash in [0, 1) so the dirt on a tile stays where it fell. */
    private fun hf(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777216f
    }

    fun draw(sc: InteriorScene, b: Business) {
        val dirt = b.dirt
        if (dirt.size != sc.w * sc.d) return
        val cafe = sc.businessType == "coffee" || sc.businessType == "fastfood"
        val prev = scene.material
        scene.material = Mat.PLAIN
        for (t in dirt.indices) {
            val v = dirt[t]
            if (v < 4f) continue
            val x0 = (t % sc.w).toFloat(); val y0 = (t / sc.w).toFloat()
            val nearDoor = abs(x0 + .5f - sc.doorX) < 2.5f && y0 >= sc.d - 3
            val k = (v / 100f).coerceIn(0f, 1f)
            // grime: a soft smudge that spreads and darkens; by the door it's tracked-in mud
            if (v > 14f) {
                val cx = x0 + .3f + hf(t, 1) * .4f; val cy = y0 + .3f + hf(t, 2) * .4f
                val r = .22f + .42f * k * (.6f + .4f * hf(t, 3))
                scene.stain(cx, cy, .004f, r, t * 7 + 3, if (nearDoor) MUD else GRIME, .16f + .36f * k)
            }
            // a dried spill now and then
            if (v > 32f && hf(t, 4) < .35f) {
                val cx = x0 + .25f + hf(t, 5) * .5f; val cy = y0 + .25f + hf(t, 6) * .5f
                scene.stain(cx, cy, .005f, .12f + .16f * hf(t, 7), t * 13 + 1, SPILLS[(hf(t, 8) * SPILLS.size).toInt()], .32f + .25f * k)
            }
            // scuffs, and muddy shoe prints walking in from the door
            if (nearDoor) prints(t, x0, y0, k) else scuffs(t, x0, y0, k)
            // litter on the dirtier tiles: one piece, up to three as it gets worse
            if (v > 38f) {
                val n = 1 + ((k - .38f) * 4.5f).toInt().coerceIn(0, 2)
                for (i in 0 until n) litter(t, i, x0 + .18f + hf(t, 20 + i) * .64f, y0 + .18f + hf(t, 30 + i) * .64f, hf(t, 40 + i) * 6.2832f, cafe)
            }
            // a fresh spill by a cup knocked over
            if (v > 72f && hf(t, 9) < .5f) {
                val cx = x0 + .35f + hf(t, 10) * .3f; val cy = y0 + .35f + hf(t, 11) * .3f
                val r = .2f + .1f * hf(t, 12)
                scene.stain(cx, cy, .007f, r, t * 5 + 11, if (cafe) SPILLS[0] else SPILLS[1], .62f)
                // still wet: a sheen on it
                scene.stain(cx - r * .2f, cy - r * .25f, .008f, r * .35f, t * 5 + 12, c(0xFFFFFFFF), .16f)
                lying(cx - .18f, cy + .05f, hf(t, 13) * 6.2832f, .04f, .11f, CUP, if (cafe) COFFEE_SLEEVE else c(0xFFD32F2F), CUP)
            }
        }
        scene.material = prev
    }

    private val qx = FloatArray(8); private val qy = FloatArray(8); private val qz = FloatArray(8)

    /** Grey streaks where shoes have dragged. */
    private fun scuffs(t: Int, x0: Float, y0: Float, k: Float) {
        val n = 1 + (k * 3).toInt()
        for (i in 0 until n) {
            val cx = x0 + .15f + hf(t, 50 + i) * .7f; val cy = y0 + .15f + hf(t, 60 + i) * .7f
            val a = hf(t, 70 + i) * 3.1416f
            val len = .08f + .14f * hf(t, 80 + i)
            val ux = cos(a) * len; val uy = sin(a) * len; val wx = -uy * .09f; val wy = ux * .09f
            qx[0] = cx - ux; qy[0] = cy - uy; qx[1] = cx - ux * .2f + wx; qy[1] = cy - uy * .2f + wy
            qx[2] = cx + ux; qy[2] = cy + uy; qx[3] = cx - ux * .2f - wx; qy[3] = cy - uy * .2f - wy
            for (q in 0 until 4) qz[q] = .006f
            scene.poly(4, qx, qy, qz, scene.withAlpha(SCUFF, .12f + .2f * k))
        }
    }

    /** Pairs of muddy shoe prints heading into the store. */
    private fun prints(t: Int, x0: Float, y0: Float, k: Float) {
        val steps = 1 + (k * 2.5f).toInt()
        val a = -1.5708f + (hf(t, 90) - .5f) * .9f
        val fx = cos(a); val fy = sin(a); val sx = -fy; val sy = fx
        val col = scene.withAlpha(MUD, .2f + .35f * k)
        for (s in 0 until steps) {
            val side = if (s % 2 == 0) 1f else -1f
            val along = -.35f + s * .32f
            val cx = x0 + .5f + fx * along + sx * side * .09f; val cy = y0 + .5f + fy * along + sy * side * .09f
            // the sole and, a little behind it, the heel
            ellipse(cx + fx * .04f, cy + fy * .04f, fx, fy, .065f, .042f, col)
            ellipse(cx - fx * .08f, cy - fy * .08f, fx, fy, .038f, .036f, col)
        }
    }

    private fun ellipse(x: Float, y: Float, fx: Float, fy: Float, a: Float, b: Float, color: Int) {
        val n = 8
        for (i in 0 until n) {
            val t = i * 6.2832f / n
            val u = cos(t) * a; val v = sin(t) * b
            qx[i] = x + fx * u - fy * v; qy[i] = y + fy * u + fx * v; qz[i] = .007f
        }
        scene.poly(n, qx, qy, qz, color)
    }

    /** One piece of litter at (x, y) lying at angle [a]: which one, from the tile and the kind of store. */
    private fun litter(t: Int, i: Int, x: Float, y: Float, a: Float, cafe: Boolean) {
        val r = hf(t, 100 + i)
        val kind = if (cafe) when {
            r < .2f -> 0; r < .38f -> 1; r < .5f -> 6; r < .66f -> 7; r < .8f -> 4; else -> 2
        } else when {
            r < .22f -> 0; r < .4f -> 2; r < .58f -> 3; r < .7f -> 1; r < .82f -> 5; r < .9f -> 4; else -> 6
        }
        val ca = cos(a); val sa = sin(a)
        when (kind) {
            0 -> {
                // a crumpled paper ball
                scene.sphereDetail = 0
                scene.blob(x, y, .032f, .034f + .012f * hf(t, 110 + i), if (hf(t, 120 + i) < .7f) c(0xFFDCD8CE) else c(0xFFD9C9A6), 8, 0f, 0f, .8f)
                scene.sphereDetail = 1
            }
            1 -> flat(x, y, ca, sa, .09f, .08f, NAPKIN, crinkle = .012f, seed = t + i)
            2 -> flat(x, y, ca, sa, .07f, .04f, WRAPPERS[(hf(t, 130 + i) * WRAPPERS.size).toInt()], crinkle = .01f, seed = t * 3 + i)
            3 -> {
                // a receipt, curling up at its ends
                val ux = ca * .13f; val uy = sa * .13f; val vx = -sa * .025f; val vy = ca * .025f
                strip(x - ux, y - uy, .018f, x - ux * .4f, y - uy * .4f, .004f, vx, vy, PAPER)
                strip(x - ux * .4f, y - uy * .4f, .004f, x + ux * .4f, y + uy * .4f, .004f, vx, vy, PAPER)
                strip(x + ux * .4f, y + uy * .4f, .004f, x + ux, y + uy, .022f, vx, vy, PAPER)
            }
            4 -> if (cafe) lying(x, y, a, .042f, .1f, CUP, COFFEE_SLEEVE, CUP)
                else lying(x, y, a, .033f, .12f, CANS[(hf(t, 140 + i) * CANS.size).toInt()], CANS[(hf(t, 140 + i) * CANS.size).toInt()], c(0xFFD9DEE4))
            5 -> lying(x, y, a, .033f, .12f, CANS[(hf(t, 150 + i) * CANS.size).toInt()], CANS[(hf(t, 150 + i) * CANS.size).toInt()], c(0xFFD9DEE4))
            6 -> {
                // crumbs
                for (q in 0 until 7) {
                    val cx = x + (hf(t * 7 + q, 160 + i) - .5f) * .2f; val cy = y + (hf(t * 7 + q, 170 + i) - .5f) * .2f
                    val s = .008f + .008f * hf(t * 7 + q, 180)
                    scene.box(cx - s, cy - s, 0f, cx + s, cy + s, s * 1.4f, CRUMB, scene.mix(CRUMB, 0xFF000000.toInt(), .2f))
                }
            }
            else -> flat(x, y, ca, sa, .035f, .022f, if (hf(t, 190 + i) < .5f) c(0xFFFFFFFF) else c(0xFFF8BBD0), crinkle = .003f, seed = t + 5 * i)
        }
    }

    /** Something flat and a little crumpled (a napkin, a wrapper): [hl] by [hw], its corners lifted unevenly. */
    private fun flat(x: Float, y: Float, ca: Float, sa: Float, hl: Float, hw: Float, color: Int, crinkle: Float, seed: Int) {
        val n = 6
        for (i in 0 until n) {
            val a = i * 6.2832f / n + .3f
            val wob = .8f + .35f * hf(seed, 200 + i)
            val u = cos(a) * hl * wob; val v = sin(a) * hw * wob
            qx[i] = x + ca * u - sa * v; qy[i] = y + sa * u + ca * v; qz[i] = .004f + crinkle * hf(seed, 210 + i)
        }
        // in two halves, so the crease shows
        val h = scene.withAlpha(color, 1f)
        qx[6] = x; qy[6] = y; qz[6] = .004f + crinkle * 1.6f
        for (half in 0 until 2) {
            val s = half * 3
            px[0] = qx[6]; py[0] = qy[6]; pz[0] = qz[6]
            for (j in 0..3) { val q = (s + j) % n; px[j + 1] = qx[q]; py[j + 1] = qy[q]; pz[j + 1] = qz[q] }
            scene.poly(5, px, py, pz, if (half == 0) h else scene.mix(h, 0xFF000000.toInt(), .08f))
        }
    }

    private val px = FloatArray(16); private val py = FloatArray(16); private val pz = FloatArray(16)

    private fun strip(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, vx: Float, vy: Float, color: Int) {
        px[0] = ax - vx; py[0] = ay - vy; pz[0] = az
        px[1] = ax + vx; py[1] = ay + vy; pz[1] = az
        px[2] = bx + vx; py[2] = by + vy; pz[2] = bz
        px[3] = bx - vx; py[3] = by - vy; pz[3] = bz
        scene.poly(4, px, py, pz, color)
    }

    /** A cup or a can on its side: a cylinder of radius [r], [len] long, lying along angle [a], its band and its end. */
    private fun lying(x: Float, y: Float, a: Float, r: Float, len: Float, body: Int, band: Int, end: Int) {
        val n = 8
        val ux = cos(a); val uy = sin(a); val sx = -uy; val sy = ux
        val h = len * .5f
        for (i in 0 until n) {
            val t0 = i * 6.2832f / n; val t1 = (i + 1) * 6.2832f / n
            val c0 = cos(t0) * r; val s0 = sin(t0) * r; val c1 = cos(t1) * r; val s1 = sin(t1) * r
            // the side in three rings: body, band, body
            for (seg in 0 until 3) {
                val f0 = -h + seg * len / 3f; val f1 = f0 + len / 3f
                px[0] = x + ux * f0 + sx * c0; py[0] = y + uy * f0 + sy * c0; pz[0] = r + s0
                px[1] = x + ux * f0 + sx * c1; py[1] = y + uy * f0 + sy * c1; pz[1] = r + s1
                px[2] = x + ux * f1 + sx * c1; py[2] = y + uy * f1 + sy * c1; pz[2] = r + s1
                px[3] = x + ux * f1 + sx * c0; py[3] = y + uy * f1 + sy * c0; pz[3] = r + s0
                scene.poly(4, px, py, pz, if (seg == 1) band else body)
            }
        }
        // the closed end (the open end of a cup shows its dark inside)
        for (e in 0 until 2) {
            val f = if (e == 0) -h else h
            for (i in 0 until n) { val t0 = i * 6.2832f / n; px[i] = x + ux * f + sx * cos(t0) * r; py[i] = y + uy * f + sy * cos(t0) * r; pz[i] = r + sin(t0) * r }
            scene.poly(n, px, py, pz, if (e == 0) end else scene.mix(body, 0xFF000000.toInt(), .45f))
        }
    }
}

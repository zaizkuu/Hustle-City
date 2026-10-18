package com.hustlecity.game.render

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Poses for [PersonPainter]. */
object Pose {
    const val STAND = 0
    const val SIT = 1
    const val DANCE = 2
    /** Running on a treadmill: a full walking cycle without moving. */
    const val RUN = 3
}

/**
 * Draws a low-poly 3D person out of boxes: legs, torso, arms, head and hair, facing [heading] (0 = north).
 * Legs and arms swing while walking; people can also sit, dance or run in place, and hold an umbrella.
 * No allocations per call, and parts are drawn back to front so the figure looks solid from any side.
 */
class PersonPainter(private val scene: Scene3D) {
    companion object {
        val PANTS = intArrayOf(c(0xFF34383F), c(0xFF1F3A5F), c(0xFF4A3B2A), c(0xFF5B6770), c(0xFF2E4A3A), c(0xFF22252A), c(0xFF6B4F3A))
        val HAIR = intArrayOf(c(0xFF2B1D14), c(0xFF4A3220), c(0xFF0F0F10), c(0xFF8A5A2B), c(0xFFD9B26A), c(0xFF7A7A7A), c(0xFFA0522D))
        val UMBRELLAS = intArrayOf(c(0xFF1F2933), c(0xFFC0392B), c(0xFF2F80ED), c(0xFFF2C94C), c(0xFF27AE60), c(0xFF9B51E0))
        private const val MAX = 16
    }

    // parts: forward offset, right offset, z0, z1, length (forward), width (right), color, draw-last flag
    private val pf = FloatArray(MAX); private val pr = FloatArray(MAX)
    private val pz0 = FloatArray(MAX); private val pz1 = FloatArray(MAX)
    private val pl = FloatArray(MAX); private val pw = FloatArray(MAX)
    private val pc = IntArray(MAX); private val pfront = IntArray(MAX); private val plast = BooleanArray(MAX)
    private val key = FloatArray(MAX); private val order = IntArray(MAX)
    private var n = 0
    private val qx = FloatArray(4); private val qy = FloatArray(4)
    private val wx = FloatArray(4); private val wy = FloatArray(4); private val wz = FloatArray(4)

    private fun part(f: Float, r: Float, z0: Float, z1: Float, l: Float, w: Float, color: Int, front: Int = color, last: Boolean = false) {
        if (n >= MAX) return
        pf[n] = f; pr[n] = r; pz0[n] = z0; pz1[n] = z1; pl[n] = l; pw[n] = w; pc[n] = color; pfront[n] = front; plast[n] = last
        n++
    }

    /**
     * @param z height of the floor or seat the person stands/sits on (0 for the ground)
     * @param moving 0..1 how much the limbs swing (walking speed)
     * @param umbrella umbrella color, or 0 for none
     */
    fun draw(
        x: Float, y: Float, z: Float, heading: Float, phase: Float, moving: Float,
        shirt: Int, pants: Int, skin: Int, hair: Int, scale: Float = 1f, pose: Int = Pose.STAND,
        apron: Int = 0, umbrella: Int = 0, alpha: Float = 1f,
    ) {
        n = 0
        val s = scale
        // far away people get a cheap three-box version
        val camD = proj().camZOf(x, y, z + .9f * s)
        val lod = camD > 0 && proj().focal * 1.75f * s / camD < 22f
        when (pose) {
            Pose.SIT -> {
                // seat height z: thighs forward, shins down to the floor
                val h = z
                part(.2f * s, -.1f * s, h, h + .14f * s, .42f * s, .14f * s, pants)
                part(.2f * s, .1f * s, h, h + .14f * s, .42f * s, .14f * s, pants)
                part(.39f * s, -.1f * s, (h - .45f * s).coerceAtLeast(0f), h + .02f, .12f * s, .12f * s, pants)
                part(.39f * s, .1f * s, (h - .45f * s).coerceAtLeast(0f), h + .02f, .12f * s, .12f * s, pants)
                upperBody(0f, h + .12f * s, s, shirt, skin, hair, apron, 0f, phase, false)
            }
            Pose.DANCE -> {
                val bounce = abs(sin(phase * 1.6f)) * .08f * s
                val step = sin(phase * 1.6f) * .08f * s
                legs(z + bounce, s, pants, step)
                upperBody(0f, z + .82f * s + bounce, s, shirt, skin, hair, apron, 0f, phase, true)
            }
            else -> {
                val amp = if (pose == Pose.RUN) 1f else moving.coerceIn(0f, 1f)
                val swing = sin(phase) * amp
                val bob = abs(sin(phase)) * .03f * amp * s
                if (lod) {
                    part(0f, 0f, z, z + .82f * s, .16f * s, .3f * s, pants)
                } else legs(z + bob, s, pants, swing * .17f * s)
                upperBody(0f, z + .82f * s + bob, s, shirt, skin, hair, apron, swing, phase, false, lod)
            }
        }
        if (umbrella != 0 && pose == Pose.STAND) {
            val top = z + 2.05f * s
            part(.08f * s, .2f * s, z + 1.1f * s, top, .03f * s, .03f * s, c(0xFF2B2B2B), last = true)
            part(.05f * s, .1f * s, top - .06f * s, top, 1.05f * s, 1.05f * s, umbrella, last = true)
            part(.05f * s, .1f * s, top, top + .1f * s, .6f * s, .6f * s, umbrella, last = true)
        }
        emit(x, y, heading, alpha)
    }

    private fun proj() = scene.proj

    private fun legs(z: Float, s: Float, pants: Int, swing: Float) {
        part(swing, -.09f * s, z, z + .82f * s, .15f * s, .13f * s, pants)
        part(-swing, .09f * s, z, z + .82f * s, .15f * s, .13f * s, pants)
        // shoes
        part(swing + .03f * s, -.09f * s, z, z + .07f * s, .2f * s, .14f * s, c(0xFF1C1C1E))
        part(-swing + .03f * s, .09f * s, z, z + .07f * s, .2f * s, .14f * s, c(0xFF1C1C1E))
    }

    private fun upperBody(f: Float, z: Float, s: Float, shirt: Int, skin: Int, hair: Int, apron: Int, swing: Float, phase: Float, armsUp: Boolean, lod: Boolean = false) {
        part(f, 0f, z, z + .6f * s, .22f * s, .4f * s, shirt, if (apron != 0) apron else shirt)
        if (!lod) {
            if (armsUp) {
                val a = sin(phase * 1.6f) * .12f * s
                part(f, -.26f * s, z + .45f * s + a, z + 1.0f * s + a, .1f * s, .1f * s, shirt)
                part(f, .26f * s, z + .45f * s - a, z + 1.0f * s - a, .1f * s, .1f * s, shirt)
            } else {
                part(f - swing * .13f * s, -.26f * s, z + .06f * s, z + .58f * s, .11f * s, .1f * s, shirt)
                part(f + swing * .13f * s, .26f * s, z + .06f * s, z + .58f * s, .11f * s, .1f * s, shirt)
                // hands
                part(f - swing * .16f * s, -.26f * s, z, z + .08f * s, .09f * s, .09f * s, skin)
                part(f + swing * .16f * s, .26f * s, z, z + .08f * s, .09f * s, .09f * s, skin)
            }
        }
        part(f, 0f, z + .63f * s, z + .88f * s, .21f * s, .2f * s, skin)
        // hair: a cap over the top and back of the head, drawn after the head
        part(f - .02f * s, 0f, z + .8f * s, z + .93f * s, .24f * s, .23f * s, hair, last = true)
    }

    private fun emit(x: Float, y: Float, heading: Float, alpha: Float) {
        val fx = sin(heading); val fy = -cos(heading)
        val rx = -fy; val ry = fx
        val cx = proj().camX; val cy = proj().camY; val cz = proj().camZ
        for (i in 0 until n) {
            val px = x + fx * pf[i] + rx * pr[i]
            val py = y + fy * pf[i] + ry * pr[i]
            val pzc = (pz0[i] + pz1[i]) * .5f
            val dx = px - cx; val dy = py - cy; val dz = pzc - cz
            key[i] = if (plast[i]) -1f else dx * dx + dy * dy + dz * dz
            order[i] = i
        }
        // far first
        for (i in 1 until n) {
            val v = order[i]; val kv = key[v]
            var j = i - 1
            while (j >= 0 && key[order[j]] < kv) { order[j + 1] = order[j]; j-- }
            order[j + 1] = v
        }
        for (k in 0 until n) {
            val i = order[k]
            box(x + fx * pf[i] + rx * pr[i], y + fy * pf[i] + ry * pr[i], fx, fy, pl[i] * .5f, pw[i] * .5f, pz0[i], pz1[i], pc[i], pfront[i], alpha)
        }
    }

    /** Oriented box without allocations: visible sides plus the top. */
    private fun box(x: Float, y: Float, fx: Float, fy: Float, hl: Float, hw: Float, z0: Float, z1: Float, color: Int, front: Int, alpha: Float) {
        val rx = -fy; val ry = fx
        // corners: front-left, front-right, back-right, back-left
        qx[0] = x + fx * hl - rx * hw; qy[0] = y + fy * hl - ry * hw
        qx[1] = x + fx * hl + rx * hw; qy[1] = y + fy * hl + ry * hw
        qx[2] = x - fx * hl + rx * hw; qy[2] = y - fy * hl + ry * hw
        qx[3] = x - fx * hl - rx * hw; qy[3] = y - fy * hl - ry * hw
        val camX = proj().camX; val camY = proj().camY
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val mx = (qx[i] + qx[j]) * .5f; val my = (qy[i] + qy[j]) * .5f
            var nx = mx - x; var ny = my - y
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-5f) continue
            nx /= len; ny /= len
            if ((camX - mx) * nx + (camY - my) * ny <= 0f) continue
            val base = if (i == 0) front else color
            var col = scene.lit(base, scene.sideShade(nx, ny))
            if (alpha < 1f) col = scene.withAlpha(col, alpha)
            scene.quad(qx[i], qy[i], z0, qx[j], qy[j], z0, qx[j], qy[j], z1, qx[i], qy[i], z1, col)
        }
        if (proj().camZ > z1) {
            for (i in 0 until 4) { wx[i] = qx[i]; wy[i] = qy[i]; wz[i] = z1 }
            var col = scene.lit(color, 1.05f)
            if (alpha < 1f) col = scene.withAlpha(col, alpha)
            scene.poly(4, wx, wy, wz, col)
        }
    }
}

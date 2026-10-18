package com.hustlecity.game.render

import com.hustlecity.game.data.c
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-poly vehicles built from extruded side profiles: sedans with a hood and trunk, pickups with a bed, vans,
 * sports cars, taxis with a roof sign. Wheels, lights, bumpers and a contact shadow up close; plain boxes far away.
 */
class CarPainter(private val scene: Scene3D) {
    private val proj get() = scene.proj

    private val wx = FloatArray(16); private val wy = FloatArray(16); private val wz = FloatArray(16)
    private val pf = FloatArray(12); private val pzz = FloatArray(12)
    private val wheelF = FloatArray(8); private val wheelZ = FloatArray(8)

    // frame of the car being drawn
    private var ox = 0f; private var oy = 0f; private var fx = 0f; private var fy = 0f; private var rx = 0f; private var ry = 0f

    companion object {
        val GLASS = c(0xFF26313D)
        val TIRE = c(0xFF1E2023)
        val RIM = c(0xFFB9C0C7)
        val BUMPER = c(0xFF2E3136)
        val TAXI = c(0xFFF2C230)
        val HEAD = c(0xFFFFF4D6)
        val TAIL = c(0xFFC0392B)
    }

    /**
     * Draws a vehicle. [style] 0 sedan, 1 pickup, 2 van, 3 sports. [near] adds wheels, lights and trim.
     * [braking] brightens the tail lights; [taxi] adds the roof sign.
     */
    fun draw(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int,
             near: Boolean, taxi: Boolean = false, braking: Boolean = false) {
        val night = scene.daylight < .5f
        if (!near) {
            farCar(x, y, heading, len, wid, hgt, color, style)
            if (night) headGlow(x, y, heading, len)
            return
        }
        ox = x; oy = y
        fx = sin(heading); fy = -cos(heading)
        rx = -fy; ry = fx
        val hl = len / 2; val hw = wid / 2
        // contact shadow
        quadFlat(-hl - .15f, -hw - .1f, hl + .15f, hw + .1f, .03f, scene.withAlpha(0xFF000000.toInt(), .28f))
        val body = color
        val camF = (proj.camX - x) * fx + (proj.camY - y) * fy
        val camL = (proj.camX - x) * rx + (proj.camY - y) * ry
        val nearSide = if (camL >= 0f) 1 else -1
        // far wheels and the end facing away first, so the body covers them
        wheels(len, wid, style, -nearSide)
        if (camF > 0f) rearParts(hl, hw, hgt, style, night, braking, body) else frontParts(hl, hw, hgt, night)
        when (style) {
            1 -> { // pickup: cab up front, open bed behind
                P(0, -hl, .32f); P(1, hl, .32f); P(2, hl, hgt * .5f); P(3, hl - .25f, hgt * .56f); P(4, -hl, hgt * .56f)
                prism(5, -hw, hw, body, body)
                val cab0 = hl - len * .62f; val cab1 = hl - len * .22f
                if (camF > 0f) bed(hl, hw, hgt, cab0, body)
                P(0, cab0, hgt * .56f); P(1, cab1, hgt * .56f); P(2, cab1 - .45f, hgt); P(3, cab0 + .1f, hgt)
                prism(4, -hw * .92f, hw * .92f, GLASS, body, roofIdx = 2)
                if (camF <= 0f) bed(hl, hw, hgt, cab0, body)
            }
            2 -> { // van: tall box with a raked windscreen
                P(0, -hl, .32f); P(1, hl, .32f); P(2, hl, hgt * .5f); P(3, hl - .75f, hgt); P(4, -hl, hgt)
                prism(5, -hw, hw, body, body, glassIdx = 2)
                // side windows up front and a stripe
                sideRect(hl - 1.9f, hl - .85f, hgt * .58f, hgt * .9f, hw, GLASS)
                sideRect(-hl + .2f, hl - 2.1f, hgt * .45f, hgt * .5f, hw, scene.mix(body, 0xFF000000.toInt(), .25f))
            }
            3 -> { // sports car: long hood, low cabin
                P(0, -hl, .25f); P(1, hl, .25f); P(2, hl, hgt * .42f); P(3, hl - .5f, hgt * .55f); P(4, -hl + .1f, hgt * .58f); P(5, -hl, hgt * .5f)
                prism(6, -hw, hw, body, body)
                val c0 = -hl + len * .2f; val c1 = hl - len * .38f
                P(0, c0, hgt * .56f); P(1, c1, hgt * .56f); P(2, c1 - .9f, hgt); P(3, c0 + .7f, hgt)
                prism(4, -hw * .86f, hw * .86f, GLASS, body, roofIdx = 2)
            }
            else -> { // sedan
                P(0, -hl, .3f); P(1, hl, .3f); P(2, hl, hgt * .45f); P(3, hl - .35f, hgt * .55f); P(4, -hl + .25f, hgt * .58f); P(5, -hl, hgt * .5f)
                prism(6, -hw, hw, body, body)
                val c0 = -hl + len * .24f; val c1 = hl - len * .3f
                P(0, c0, hgt * .56f); P(1, c1, hgt * .56f); P(2, c1 - .7f, hgt); P(3, c0 + .45f, hgt)
                prism(4, -hw * .88f, hw * .88f, GLASS, body, roofIdx = 2)
                if (taxi) {
                    // roof sign and a checker band
                    val mid = (c0 + c1) / 2 - .1f
                    slab(mid - .35f, mid + .35f, -.28f, .28f, hgt, hgt + .28f, if (night) c(0xFFFFF2B0) else c(0xFFFAFAFA))
                    sideRect(-hl + .4f, hl - .4f, hgt * .4f, hgt * .46f, hw, c(0xFF222222))
                }
            }
        }
        if (camF > 0f) frontParts(hl, hw, hgt, night) else rearParts(hl, hw, hgt, style, night, braking, body)
        wheels(len, wid, style, nearSide)
        if (night) headGlow(x, y, heading, len)
        if (braking) {
            val bx = x - fx * (hl + .6f); val by = y - fy * (hl + .6f)
            scene.disc(bx, by, .04f, 1.1f, 1.1f, 10, scene.withAlpha(c(0xFFFF3B30), .22f))
        }
    }

    private fun bed(hl: Float, hw: Float, hgt: Float, cab0: Float, body: Int) {
        val b0 = -hl + .05f; val b1 = cab0 - .05f
        slab(b0 + .1f, b1, -hw + .1f, hw - .1f, hgt * .56f, hgt * .58f, c(0xFF33373C))
        slab(b0, b1, -hw, -hw + .1f, hgt * .56f, hgt * .78f, body)
        slab(b0, b1, hw - .1f, hw, hgt * .56f, hgt * .78f, body)
        slab(b0, b0 + .1f, -hw, hw, hgt * .56f, hgt * .78f, body)
    }

    private fun frontParts(hl: Float, hw: Float, hgt: Float, night: Boolean) {
        slab(hl - .02f, hl + .1f, -hw + .05f, hw - .05f, .25f, .48f, BUMPER)
        val z = hgt * .38f
        val head = if (night) HEAD else c(0xFFE8ECEF)
        frontRect(hl + .11f, -hw + .15f, -hw + .55f, z - .1f, z + .06f, head)
        frontRect(hl + .11f, hw - .55f, hw - .15f, z - .1f, z + .06f, head)
        frontRect(hl + .11f, -.35f, .35f, z - .08f, z + .04f, c(0xFF3A3F45))
    }

    private fun rearParts(hl: Float, hw: Float, hgt: Float, style: Int, night: Boolean, braking: Boolean, body: Int) {
        if (style == 3) slab(-hl + .05f, -hl + .35f, -hw * .9f, hw * .9f, hgt * .7f, hgt * .76f, scene.mix(body, 0xFF000000.toInt(), .4f))
        slab(-hl - .1f, -hl + .02f, -hw + .05f, hw - .05f, .25f, .48f, BUMPER)
        val z = hgt * .38f
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        frontRect(-hl - .11f, -hw + .12f, -hw + .45f, z - .08f, z + .06f, tail, rear = true)
        frontRect(-hl - .11f, hw - .45f, hw - .12f, z - .08f, z + .06f, tail, rear = true)
        frontRect(-hl - .11f, -.25f, .25f, z - .02f, z + .12f, c(0xFFF0F0E8), rear = true)
    }

    private fun headGlow(x: Float, y: Float, heading: Float, len: Float) {
        val hx = sin(heading); val hy = -cos(heading)
        scene.disc(x + hx * (len * .5f + 3f), y + hy * (len * .5f + 3f), .05f, 2.4f, 2.4f, 10, scene.withAlpha(c(0xFFFFEBA8), .2f * (1f - scene.daylight)))
    }

    /** The old two-box car, for traffic far away. */
    private fun farCar(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int) {
        when (style) {
            1 -> {
                scene.orientedBox(x, y, heading, len, wid, .32f, hgt * .55f, color, color)
                scene.orientedBox(x, y, heading, len * .36f, wid * .9f, hgt * .55f, hgt, color, GLASS, GLASS, len * .14f)
            }
            2 -> scene.orientedBox(x, y, heading, len, wid, .3f, hgt, color, color, GLASS)
            else -> {
                scene.orientedBox(x, y, heading, len, wid, .25f, hgt * .55f, color, color)
                scene.orientedBox(x, y, heading, len * .48f, wid * .84f, hgt * .55f, hgt, color, GLASS, GLASS, -len * .05f)
            }
        }
    }

    /** Sets point [i] of the profile (forward, height). */
    private fun P(i: Int, f: Float, z: Float) { pf[i] = f; pzz[i] = z }

    private fun wpt(f: Float, l: Float, z: Float, i: Int) {
        wx[i] = ox + fx * f + rx * l; wy[i] = oy + fy * f + ry * l; wz[i] = z
    }

    private fun shadeFor(nx: Float, ny: Float, nz: Float): Float {
        if (nz >= .7f) return 1f
        val h = sqrt(nx * nx + ny * ny)
        val side = if (h > 1e-3f) scene.sideShade(nx / h, ny / h) else .8f
        return if (nz >= 0f) side + (1f - side) * nz else side * .7f
    }

    /**
     * Extrudes the current profile (forward, z points, counter-clockwise seen from the right) between lateral
     * offsets [l0] and [l1]. Edge [glassIdx] is glass (a windscreen); edge [roofIdx] takes [roof] color.
     */
    private fun prism(n: Int, l0: Float, l1: Float, sides: Int, roof: Int, glassIdx: Int = -1, roofIdx: Int = -1) {
        // profile centroid, to orient edge normals outward
        var cf = 0f; var cz = 0f
        for (i in 0 until n) { cf += pf[i]; cz += pzz[i] }
        cf /= n; cz /= n
        val camX = proj.camX; val camY = proj.camY; val camZ = proj.camZ
        for (i in 0 until n) {
            val j = (i + 1) % n
            val df = pf[j] - pf[i]; val dz = pzz[j] - pzz[i]
            var nf = dz; var nz = -df
            val mf = (pf[i] + pf[j]) / 2; val mz = (pzz[i] + pzz[j]) / 2
            if ((mf - cf) * nf + (mz - cz) * nz < 0) { nf = -nf; nz = -nz }
            val ln = sqrt(nf * nf + nz * nz); if (ln < 1e-4f) continue
            nf /= ln; nz /= ln
            val nwx = fx * nf; val nwy = fy * nf
            val mx = ox + fx * mf; val my = oy + fy * mf
            if ((camX - mx) * nwx + (camY - my) * nwy + (camZ - mz) * nz <= 0f) continue
            val base = when (i) { glassIdx -> GLASS; roofIdx -> roof; else -> if (nz > .7f) roof else sides }
            val col = if (nz > .7f) scene.lit(scene.snowy(base), 1f) else scene.lit(base, shadeFor(nwx, nwy, nz))
            wpt(pf[i], l0, pzz[i], 0); wpt(pf[j], l0, pzz[j], 1); wpt(pf[j], l1, pzz[j], 2); wpt(pf[i], l1, pzz[i], 3)
            scene.poly(4, wx, wy, wz, col)
        }
        // the two side faces
        for (side in 0..1) {
            val l = if (side == 0) l0 else l1
            val sgn = if (side == 0) -1f else 1f
            val px = ox + rx * l; val py = oy + ry * l
            if ((camX - px) * rx * sgn + (camY - py) * ry * sgn <= 0f) continue
            for (i in 0 until n) wpt(pf[i], l, pzz[i], i)
            scene.poly(n, wx, wy, wz, scene.lit(sides, scene.sideShade(rx * sgn, ry * sgn)))
        }
    }

    /** An axis-aligned box in the car's frame (bumpers, bed walls, roof signs). */
    private fun slab(f0: Float, f1: Float, l0: Float, l1: Float, z0: Float, z1: Float, color: Int) {
        P(0, f0, z0); P(1, f1, z0); P(2, f1, z1); P(3, f0, z1)
        prism(4, l0, l1, color, color)
    }

    /** A flat rectangle on both sides of the body (windows, stripes). */
    private fun sideRect(f0: Float, f1: Float, z0: Float, z1: Float, hw: Float, color: Int) {
        for (si in 0..1) {
            val sgn = if (si == 0) -1 else 1
            val l = (hw + .02f) * sgn
            val px = ox + rx * l; val py = oy + ry * l
            if ((proj.camX - px) * rx * sgn + (proj.camY - py) * ry * sgn <= 0f) continue
            wpt(f0, l, z0, 0); wpt(f1, l, z0, 1); wpt(f1, l, z1, 2); wpt(f0, l, z1, 3)
            scene.poly(4, wx, wy, wz, scene.lit(color, scene.sideShade(rx * sgn, ry * sgn)))
        }
    }

    /** A rectangle on the front (or rear) face: lights and plates. */
    private fun frontRect(f: Float, l0: Float, l1: Float, z0: Float, z1: Float, color: Int, rear: Boolean = false) {
        val sgn = if (rear) -1f else 1f
        val px = ox + fx * f; val py = oy + fy * f
        if ((proj.camX - px) * fx * sgn + (proj.camY - py) * fy * sgn <= 0f) return
        wpt(f, l0, z0, 0); wpt(f, l1, z0, 1); wpt(f, l1, z1, 2); wpt(f, l0, z1, 3)
        scene.poly(4, wx, wy, wz, scene.lit(color, max(.85f, scene.sideShade(fx * sgn, fy * sgn))))
    }

    private fun quadFlat(f0: Float, l0: Float, f1: Float, l1: Float, z: Float, color: Int) {
        wpt(f0, l0, z, 0); wpt(f1, l0, z, 1); wpt(f1, l1, z, 2); wpt(f0, l1, z, 3)
        scene.poly(4, wx, wy, wz, color)
    }

    private fun wheels(len: Float, wid: Float, style: Int, only: Int) {
        val r = if (style == 2) .4f else if (style == 3) .34f else .36f
        val axle = len / 2 - (if (style == 2) .8f else .75f)
        val hw = wid / 2
        for (k in 0 until 8) {
            val a = k * 6.2832f / 8 + .3927f
            wheelF[k] = cos(a) * r; wheelZ[k] = sin(a) * r
        }
        val sgn = only
        for (ai in 0..1) {
            val af = if (ai == 0) axle else -axle
            for (k in 0 until 8) { pf[k] = af + wheelF[k]; pzz[k] = r + wheelZ[k] }
            val lIn = (hw - .22f) * sgn; val lOut = (hw + .03f) * sgn
            prism(8, if (sgn < 0) lOut else lIn, if (sgn < 0) lIn else lOut, TIRE, TIRE)
            // hub cap on the outer side
            val l = (hw + .04f) * sgn
            val px = ox + rx * l; val py = oy + ry * l
            if ((proj.camX - px) * rx * sgn + (proj.camY - py) * ry * sgn > 0f) {
                for (k in 0 until 8) wpt(af + wheelF[k] * .55f, l, r + wheelZ[k] * .55f, k)
                scene.poly(8, wx, wy, wz, scene.lit(RIM, scene.sideShade(rx * sgn, ry * sgn)))
            }
        }
    }
}

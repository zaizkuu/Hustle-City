package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.render.WorldRenderer.Companion.AUTUMN
import com.hustlecity.game.render.WorldRenderer.Companion.TREE
import com.hustlecity.game.render.WorldRenderer.Companion.TRUNK
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Street and park trees by kind: 0 street broadleaf, 1 park broadleaf, 2 conifer, 3 flowering cherry, 4 coconut palm,
 * 5 ginkgo. Deciduous trees follow the seasons. Up close they get branches, extra canopy clumps, a palm's coconuts and
 * folded fronds; far away a few big shapes.
 */
class TreePainter(private val scene: Scene3D) {
    // palm trunk joints: centre, height and radius
    private val jx = FloatArray(8); private val jy = FloatArray(8); private val jz = FloatArray(8); private val jr = FloatArray(8)
    private val ringC = FloatArray(8); private val ringS = FloatArray(8)
    private val order = IntArray(10); private val facing = FloatArray(10)

    companion object {
        val PALM_TRUNK = c(0xFF8E7B63)
        val PALM_RING = c(0xFF6A5948)
        val PALM = intArrayOf(c(0xFF4F8A35), c(0xFF5D9A3C), c(0xFF46803A))
        val COCONUT = c(0xFF7A6630)
        val GINKGO = intArrayOf(c(0xFF6E9F3D), c(0xFF7CAB47), c(0xFF639338))
        val GINKGO_GOLD = intArrayOf(c(0xFFF2C12E), c(0xFFE8B020), c(0xFFF7D24C))
        val GINKGO_BARK = c(0xFF6B5D4F)

        /** A palm's real height and crown reach from the generic tree size. */
        fun palmHeight(h: Float) = h * 1.55f
        fun palmReach(r: Float) = min(r * 1.5f, 3.8f)
        fun ginkgoHeight(h: Float) = h * 1.3f
        fun ginkgoRadius(r: Float) = r * .62f
    }

    /**
     * Draws tree [i] of [kind] standing at ([x], [y]): canopy radius [r], height [h], color [tone], in calendar [month]
     * (0 = January). [near] adds the fine detail.
     */
    fun draw(x: Float, y: Float, r: Float, h: Float, tone: Int, kind: Int, month: Int, i: Int, near: Boolean) {
        scene.sphereDetail = if (near) 2 else 1
        val white = scene.lit(0xFFF2F5F8.toInt(), 1f)
        if (kind == 4) { palm(x, y, r, h, tone, i, near, white); return }
        if (kind == 5 && month == 10 && scene.snow < .3f) {
            // ginkgos drop their leaves all at once: a gold carpet round the foot
            val gold = scene.withAlpha(scene.lit(GINKGO_GOLD[(i + tone) % 3], .95f), .7f)
            val cr = ginkgoRadius(r)
            scene.disc(x + cr * .3f, y - cr * .2f, .02f, cr * 1.5f, cr * 1.3f, 10, gold)
            scene.disc(x - cr * .35f, y + cr * .3f, .02f, cr * 1.2f, cr * 1.1f, 9, gold)
        }
        // street trees grow out of a pit with a low iron guard
        if (kind == 0 || kind == 3 || kind == 5) {
            scene.groundRect(x - .7f, y - .7f, x + .7f, y + .7f, .025f, scene.mix(scene.lit(c(0xFF4A3A2C), 1f), white, scene.snow * .8f))
            if (near) {
                val g = c(0xFF2A2D31)
                scene.box(x - .72f, y - .72f, 0f, x + .72f, y - .66f, .35f, g, g)
                scene.box(x - .72f, y + .66f, 0f, x + .72f, y + .72f, .35f, g, g)
            }
        }
        if (kind == 2) {
            // conifer: stacked cones, green all year
            scene.material = Mat.FOLIAGE
            scene.box(x - .14f, y - .14f, 0f, x + .14f, y + .14f, h * .3f, TRUNK, TRUNK)
            val g = TREE[2]
            scene.cone(x, y, r * .95f, h * .25f, h * .75f, 7, scene.mix(g, c(0xFF2E5E36), .3f), .5f)
            scene.cone(x, y, r * .72f, h * .55f, h * 1.05f, 7, g, .6f)
            scene.cone(x, y, r * .45f, h * .85f, h * 1.3f, 6, scene.mix(g, c(0xFF7FB069), .2f), .7f)
            scene.material = Mat.PLAIN
            return
        }
        // deciduous trees follow the seasons: blossom and fresh leaves in spring, deep green in summer,
        // reds and golds in autumn and bare branches in winter
        val bare = month == 11 || month == 0 || month == 1 || (month == 2 && i % 3 != 0)
        if (kind == 5) { ginkgo(x, y, r, h, tone, month, i, near, bare, white); return }
        scene.box(x - .15f, y - .15f, 0f, x + .15f, y + .15f, h * .55f, TRUNK, TRUNK)
        if (near || bare) {
            // a few branches fork out of the trunk
            for (k in 0 until 4) {
                val a = (k * 1.5708f + i * .7f)
                val bx = x + cos(a) * r * .6f; val by = y + sin(a) * r * .6f
                val z0 = h * .45f; val z1 = h * (.78f + (k % 2) * .12f)
                scene.quad(x - .06f, y, z0, x + .06f, y, z0, bx + .03f, by, z1, bx - .03f, by, z1, scene.lit(TRUNK, .9f))
                scene.quad(x, y - .06f, z0, x, y + .06f, z0, bx, by + .03f, z1, bx, by - .03f, z1, scene.lit(TRUNK, .8f))
            }
        }
        scene.material = Mat.FOLIAGE
        if (bare) {
            // a haze of twigs, dusted with snow
            scene.blob(x, y, h * .78f, r * .9f, scene.withAlpha(scene.mix(scene.lit(c(0xFF6B5444), 1f), white, scene.snow * .5f), .35f), 10)
            scene.material = Mat.PLAIN
            return
        }
        val base = when {
            kind == 3 && (month == 3 || month == 2) -> if (i % 2 == 0) c(0xFFF4B6C8) else c(0xFFF7E1E8) // cherry blossom
            month == 3 || month == 4 -> scene.mix(TREE[tone], c(0xFF9CCB5A), .35f)
            month in 8..10 -> AUTUMN[(i + tone) % AUTUMN.size].let { if (month == 8) scene.mix(TREE[tone], it, .45f) else it }
            else -> TREE[tone]
        }
        val z = h * .75f
        scene.blob(x, y, z - r * .15f, r, scene.mix(scene.lit(base, .78f), white, scene.snow * .35f), 12)
        scene.blob(x, y, z, r * .78f, scene.mix(scene.lit(base, .95f), white, scene.snow * .5f), 11, -.12f, -.2f)
        if (near) {
            scene.blob(x, y, z + r * .2f, r * .45f, scene.mix(scene.lit(base, 1.12f), white, scene.snow * .7f), 9, -.35f * scene.sunX, -.55f)
            scene.blob(x, y, z, r * .35f, scene.mix(scene.lit(base, .88f), white, scene.snow * .4f), 8, .55f, -.05f)
        }
        scene.material = Mat.PLAIN
    }

    /**
     * The tree's shadow on the ground, for the renderer without sun shadows: ([sdx], [sdy]) is how far a shadow falls
     * per meter of height. Palms cast a thin trunk line out to the shadow of their crown.
     */
    fun shadow(x: Float, y: Float, r: Float, h: Float, kind: Int, i: Int, sdx: Float, sdy: Float, color: Int) {
        when (kind) {
            4 -> {
                val ph = palmHeight(h); val reach = palmReach(r)
                val la = i * 2.39996f; val lean = palmLean(i)
                val cx = x + cos(la) * lean + sdx * ph; val cy = y + sin(la) * lean + sdy * ph
                val dx = cx - x; val dy = cy - y
                val l = sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
                val px = -dy / l; val py = dx / l
                scene.quad(x + px * .16f, y + py * .16f, .03f, cx + px * .1f, cy + py * .1f, .03f, cx - px * .1f, cy - py * .1f, .03f, x - px * .16f, y - py * .16f, .03f, color)
                scene.disc(cx, cy, .03f, reach * .85f, reach * .8f, 9, color)
            }
            5 -> {
                val gh = ginkgoHeight(h); val cr = ginkgoRadius(r)
                scene.disc(x + sdx * gh * .68f, y + sdy * gh * .68f, .03f, cr * 1.05f, cr * .95f, 8, color)
            }
            else -> scene.disc(x + sdx * h * .8f, y + sdy * h * .8f, .03f, r, r * .85f, 8, color)
        }
    }

    private fun palmLean(i: Int) = .45f + (i % 5) * .2f

    // ------------------------------------------------------------------ palm

    /**
     * Coconut palm: a tall trunk of ringed segments that curves gently away and thins toward the top, coconuts tucked
     * under a crown of long fronds that arch out and droop at the tips. Up close each frond is folded into a shallow V
     * along its midrib. Evergreen; snow only pales it a little.
     */
    private fun palm(x: Float, y: Float, r: Float, h0: Float, tone: Int, i: Int, near: Boolean, white: Int) {
        val h = palmHeight(h0)
        val reach = palmReach(r)
        val la = i * 2.39996f
        val lean = palmLean(i)
        val lx = cos(la) * lean; val ly = sin(la) * lean
        val segs = if (near) 6 else 3
        val sides = if (near) 6 else 4
        for (k in 0..segs) {
            val u = k.toFloat() / segs
            jx[k] = x + lx * u * u; jy[k] = y + ly * u * u; jz[k] = h * u; jr[k] = .21f - .08f * u
        }
        jr[0] = .27f // a swollen foot
        for (s in 0..sides) { val a = s * 6.2832f / sides + .3f; ringC[s] = cos(a); ringS[s] = sin(a) }
        // a patch of bare earth round the foot
        scene.disc(x, y, .025f, .55f, .55f, 8, scene.mix(scene.lit(c(0xFF8C7A5E), 1f), white, scene.snow * .8f))
        val camX = scene.camLX; val camY = scene.camLY
        // the GPU keeps trees in a cache that outlives small camera moves, so it gets every side (depth sorts them)
        val cull = scene.gpu == null
        for (k in 0 until segs) {
            // every segment flares at its base: the rings left by old fronds
            val col = if (k % 2 == 0) PALM_TRUNK else scene.mix(PALM_TRUNK, PALM_RING, .55f)
            val r0 = jr[k] * 1.14f; val r1 = jr[k + 1] * .94f
            val z0 = jz[k]; val z1 = jz[k + 1] + .06f
            for (s in 0 until sides) {
                val nx = ringC[s] + ringC[s + 1]; val ny = ringS[s] + ringS[s + 1]
                if (cull && (camX - jx[k]) * nx + (camY - jy[k]) * ny <= 0f) continue
                val nl = sqrt(nx * nx + ny * ny)
                scene.quad(jx[k] + ringC[s] * r0, jy[k] + ringS[s] * r0, z0, jx[k] + ringC[s + 1] * r0, jy[k] + ringS[s + 1] * r0, z0,
                    jx[k + 1] + ringC[s + 1] * r1, jy[k + 1] + ringS[s + 1] * r1, z1, jx[k + 1] + ringC[s] * r1, jy[k + 1] + ringS[s] * r1, z1,
                    scene.lit(col, scene.sideShade(nx / nl, ny / nl)))
            }
        }
        val cx = jx[segs]; val cy = jy[segs]; val top = jz[segs]
        scene.material = Mat.FOLIAGE
        // the crown's heart, then coconuts hanging under it
        scene.blob(cx, cy, top - .05f, .32f, scene.lit(scene.mix(PALM[tone % 3], PALM_RING, .45f), .8f), 8)
        if (near) for (k in 0 until 3) {
            val a = la + k * 2.1f
            scene.blob(cx + cos(a) * .24f, cy + sin(a) * .24f, top - .3f, .16f, scene.lit(if (k == 1) c(0xFF8C9A3C) else COCONUT, .9f), 8)
        }
        // fronds pointing away from the camera first, so the ones in front cover them
        val n = if (near) 10 else 7
        var dx = camX - cx; var dy = camY - cy
        val dl = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f); dx /= dl; dy /= dl
        for (k in 0 until n) {
            val a = frondAngle(k, n, i)
            facing[k] = cos(a) * dx + sin(a) * dy
            order[k] = k
        }
        for (a in 1 until n) {
            val v = order[a]; var b = a - 1
            while (b >= 0 && facing[order[b]] > facing[v]) { order[b + 1] = order[b]; b-- }
            order[b + 1] = v
        }
        val green = PALM[tone % 3]
        for (o in 0 until n) {
            val k = order[o]
            val a = frondAngle(k, n, i)
            val old = k % 3 == 1
            frond(cx, cy, top, cos(a), sin(a), reach * (if (old) .9f else 1f - (k % 2) * .1f), old, green, near, white)
        }
        scene.material = Mat.PLAIN
    }

    private fun frondAngle(k: Int, n: Int, i: Int) = k * 6.2832f / n + i * .9f + ((k * 37 + i) % 7) * .07f

    /** One frond from the crown out along ([dx], [dy]): it rises, arches over and droops; older ones hang lower. */
    private fun frond(cx: Float, cy: Float, top: Float, dx: Float, dy: Float, len: Float, old: Boolean, green: Int, near: Boolean, white: Int) {
        val rise = if (old) .05f else .5f
        val droop = if (old) 1.2f else .95f
        val col = scene.mix(if (old) scene.mix(green, c(0xFFA8944E), .35f) else green, white, scene.snow * .25f)
        val steps = if (near) 3 else 2
        val px = -dy; val py = dx
        var pd = len * .05f; var pz = top + .04f; var pw = len * .05f
        for (j in 1..steps) {
            val s = j.toFloat() / steps
            val d = len * s
            val z = top + len * (rise * s - droop * s * s)
            val w = if (j == steps) len * .025f else len * (.06f + .12f * sin(3.1416f * s))
            val ax = cx + dx * pd; val ay = cy + dy * pd
            val bx = cx + dx * d; val by = cy + dy * d
            if (near) {
                // the leaflets hang a little below the midrib on either side
                val f0 = pw * .45f; val f1 = w * .45f
                leaf(ax, ay, pz, bx, by, z, bx + px * w, by + py * w, z - f1, ax + px * pw, ay + py * pw, pz - f0, col)
                leaf(ax, ay, pz, bx, by, z, bx - px * w, by - py * w, z - f1, ax - px * pw, ay - py * pw, pz - f0, col)
            } else {
                leaf(ax + px * pw, ay + py * pw, pz, bx + px * w, by + py * w, z, bx - px * w, by - py * w, z, ax - px * pw, ay - py * pw, pz, col)
            }
            pd = d; pz = z; pw = w
        }
    }

    /** A quad of leaves, lit by how much it faces the sky and the sun. */
    private fun leaf(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                     x3: Float, y3: Float, z3: Float, col: Int) {
        var shade = 1f
        if (scene.gpu == null) {
            val ax = x2 - x0; val ay = y2 - y0; val az = z2 - z0
            val bx = x3 - x1; val by = y3 - y1; val bz = z3 - z1
            var nx = ay * bz - az * by; var ny = az * bx - ax * bz; var nz = ax * by - ay * bx
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l > 1e-6f) { nx /= l; ny /= l; nz /= l }
            if (nz < 0f) { nx = -nx; ny = -ny; nz = -nz }
            shade = .6f + .32f * nz + .3f * max(0f, nx * scene.sunX + ny * scene.sunY)
        }
        scene.quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, scene.lit(col, shade))
    }

    // ------------------------------------------------------------------ ginkgo

    /**
     * Ginkgo: a straight trunk with branches climbing steeply out of it and a narrow, upright crown of stacked clumps.
     * Fresh green in spring, deep green in summer, bright gold in October and November (when it drops a gold carpet),
     * bare in winter.
     */
    private fun ginkgo(x: Float, y: Float, r: Float, h0: Float, tone: Int, month: Int, i: Int, near: Boolean, bare: Boolean, white: Int) {
        val h = ginkgoHeight(h0)
        val cr = ginkgoRadius(r)
        scene.box(x - .13f, y - .13f, 0f, x + .13f, y + .13f, h * .62f, GINKGO_BARK, GINKGO_BARK)
        if (near || bare) {
            for (k in 0 until 5) {
                val a = k * 1.2566f + i * .7f
                val br = cr * (.28f + (k % 2) * .14f)
                val bx = x + cos(a) * br; val by = y + sin(a) * br
                val z0 = h * (.3f + (k % 3) * .08f); val z1 = h * (.62f + (k % 2) * .14f)
                scene.quad(x - .05f, y, z0, x + .05f, y, z0, bx + .025f, by, z1, bx - .025f, by, z1, scene.lit(GINKGO_BARK, .9f))
                scene.quad(x, y - .05f, z0, x, y + .05f, z0, bx, by + .025f, z1, bx, by - .025f, z1, scene.lit(GINKGO_BARK, .78f))
            }
        }
        scene.material = Mat.FOLIAGE
        if (bare) {
            // a tall, thin haze of twigs
            val twig = scene.withAlpha(scene.mix(scene.lit(c(0xFF6B5A4A), 1f), white, scene.snow * .5f), .32f)
            scene.blob(x, y, h * .62f, cr * .9f, twig, 10, squash = 1.6f)
            scene.material = Mat.PLAIN
            return
        }
        val base = when (month) {
            9, 10 -> GINKGO_GOLD[(i + tone) % 3]
            8 -> scene.mix(GINKGO[tone % 3], GINKGO_GOLD[0], .35f)
            2, 3, 4 -> scene.mix(GINKGO[tone % 3], c(0xFFB6DA6C), .4f)
            else -> GINKGO[tone % 3]
        }
        // stacked clumps make a narrow column, widest a third of the way up
        val ox = cos(i * 1.7f) * cr * .14f; val oy = sin(i * 1.7f) * cr * .14f
        scene.blob(x, y, h * .5f, cr, scene.mix(scene.lit(base, .8f), white, scene.snow * .35f), 12, squash = 1.35f)
        scene.blob(x + ox, y + oy, h * .74f, cr * .84f, scene.mix(scene.lit(base, .96f), white, scene.snow * .5f), 11, -.1f, -.15f, 1.35f)
        if (near) {
            scene.blob(x - ox, y - oy, h * .91f, cr * .52f, scene.mix(scene.lit(base, 1.1f), white, scene.snow * .7f), 9, -.3f * scene.sunX, -.35f, 1.3f)
            scene.blob(x + oy, y - ox, h * .6f, cr * .5f, scene.mix(scene.lit(base, .88f), white, scene.snow * .4f), 8, .55f, 0f, 1.2f)
        } else {
            scene.blob(x - ox, y - oy, h * .9f, cr * .55f, scene.mix(scene.lit(base, 1.05f), white, scene.snow * .6f), 9, 0f, -.25f, 1.3f)
        }
        scene.material = Mat.PLAIN
    }
}

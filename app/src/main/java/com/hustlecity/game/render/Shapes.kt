package com.hustlecity.game.render

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Shared low-poly shapes for props, landmarks and rooftops.

private val sx = FloatArray(16)
private val sy = FloatArray(16)
private val sz = FloatArray(16)

/** Upright polygonal prism (posts, hydrants, bins, water tanks, columns) with a lit top. */
fun Scene3D.cylinder(cx: Float, cy: Float, rad: Float, z0: Float, z1: Float, sides: Int, color: Int, top: Int = color) {
    val n = sides.coerceIn(4, 12)
    for (i in 0 until n) {
        val a0 = i * 6.2832f / n; val a1 = (i + 1) * 6.2832f / n
        val x0 = cx + cos(a0) * rad; val y0 = cy + sin(a0) * rad
        val x1 = cx + cos(a1) * rad; val y1 = cy + sin(a1) * rad
        val nx = cos((a0 + a1) / 2); val ny = sin((a0 + a1) / 2)
        if ((camLX - (x0 + x1) / 2) * nx + (camLY - (y0 + y1) / 2) * ny <= 0f) continue
        quad(x0, y0, z0, x1, y1, z0, x1, y1, z1, x0, y0, z1, lit(color, sideShade(nx, ny)))
    }
    if (proj.camZ > z1) {
        for (i in 0 until n) { val a = i * 6.2832f / n; sx[i] = cx + cos(a) * rad; sy[i] = cy + sin(a) * rad; sz[i] = z1 }
        poly(n, sx, sy, sz, lit(snowy(top), 1f))
    }
}

/** Cone or pyramid (roofs, conifers, umbrellas). */
fun Scene3D.cone(cx: Float, cy: Float, rad: Float, z0: Float, z1: Float, sides: Int, color: Int, snowAmount: Float = .7f) {
    val n = sides.coerceIn(3, 12)
    for (i in 0 until n) {
        val a0 = i * 6.2832f / n + .3927f; val a1 = (i + 1) * 6.2832f / n + .3927f
        val x0 = cx + cos(a0) * rad; val y0 = cy + sin(a0) * rad
        val x1 = cx + cos(a1) * rad; val y1 = cy + sin(a1) * rad
        val nx = cos((a0 + a1) / 2); val ny = sin((a0 + a1) / 2)
        // faces turned away are hidden behind the front ones when seen from above
        if (proj.camZ < z1 && (camLX - (x0 + x1) / 2) * nx + (camLY - (y0 + y1) / 2) * ny <= 0f) continue
        sx[0] = x0; sy[0] = y0; sz[0] = z0
        sx[1] = x1; sy[1] = y1; sz[1] = z0
        sx[2] = cx; sy[2] = cy; sz[2] = z1
        poly(3, sx, sy, sz, lit(snowy(color, snowAmount), .72f + .28f * max(0f, nx * sunX + ny * sunY)))
    }
}

/** A box whose long side runs along x (face 0/1) or y (face 2/3), centred on (x, y). */
fun Scene3D.alongBox(x: Float, y: Float, face: Int, along: Float, across: Float, z0: Float, z1: Float, top: Int, side: Int, shiftOut: Float = 0f) {
    val ha = along / 2; val hc = across / 2
    when (face) {
        0 -> box(x - ha, y - hc - shiftOut, z0, x + ha, y + hc - shiftOut, z1, top, side)
        1 -> box(x - ha, y - hc + shiftOut, z0, x + ha, y + hc + shiftOut, z1, top, side)
        2 -> box(x - hc - shiftOut, y - ha, z0, x + hc - shiftOut, y + ha, z1, top, side)
        else -> box(x - hc + shiftOut, y - ha, z0, x + hc + shiftOut, y + ha, z1, top, side)
    }
}

/**
 * A turned shape (bottles, cans, fruit, cups, buns): rings of radius [rs] (times [rx] along x and [ry] along y, so it
 * can be oval) at heights [zs], bottom to top, [sides] around. Band i, between rings i and i + 1, is [colors] i (the last
 * color carries on); a flat cap in [cap] closes the top when the last ring is open. Faces turned from the camera are left out.
 */
fun Scene3D.lathe(cx: Float, cy: Float, rx: Float, ry: Float, rs: FloatArray, zs: FloatArray, colors: IntArray, sides: Int = 8,
                   cap: Int = colors.last(), count: Int = rs.size) {
    val n = sides.coerceIn(3, 16)
    val scale = (rx + ry) * .5f
    for (b in 0 until count - 1) {
        val r0 = rs[b]; val r1 = rs[b + 1]; val z0 = zs[b]; val z1 = zs[b + 1]
        if (r0 <= 0f && r1 <= 0f) continue
        val color = colors[b.coerceAtMost(count - 1)]
        val dz = z1 - z0; val dr = (r0 - r1) * scale
        for (i in 0 until n) {
            val a0 = i * 6.2832f / n; val a1 = (i + 1) * 6.2832f / n
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val am = (a0 + a1) * .5f; val nx = cos(am); val ny = sin(am)
            val mr = (r0 + r1) * .5f
            val mx = cx + nx * mr * rx; val my = cy + ny * mr * ry; val mz = (z0 + z1) * .5f
            if ((camLX - mx) * nx * dz + (camLY - my) * ny * dz + (proj.camZ - mz) * dr <= 0f) continue
            val col = lit(color, sideShade(nx, ny) * (if (dr > dz) 1.08f else 1f))
            var k = 0
            sx[k] = cx + c0 * r0 * rx; sy[k] = cy + s0 * r0 * ry; sz[k] = z0; k++
            if (r0 > 0f) { sx[k] = cx + c1 * r0 * rx; sy[k] = cy + s1 * r0 * ry; sz[k] = z0; k++ }
            sx[k] = cx + c1 * r1 * rx; sy[k] = cy + s1 * r1 * ry; sz[k] = z1; k++
            if (r1 > 0f) { sx[k] = cx + c0 * r1 * rx; sy[k] = cy + s0 * r1 * ry; sz[k] = z1; k++ }
            poly(k, sx, sy, sz, col)
        }
    }
    val top = count - 1
    if (rs[top] > 0f && proj.camZ > zs[top]) {
        for (i in 0 until n) { val a = i * 6.2832f / n; sx[i] = cx + cos(a) * rs[top] * rx; sy[i] = cy + sin(a) * rs[top] * ry; sz[i] = zs[top] }
        poly(n, sx, sy, sz, lit(cap, 1f))
    }
}

private val rbx = FloatArray(16)
private val rby = FloatArray(16)
private val rbz = FloatArray(16)
private val rbx2 = FloatArray(16)
private val rby2 = FloatArray(16)
private val rbz2 = FloatArray(16)
private val rq = FloatArray(4)
private val rqy = FloatArray(4)
private val rqz = FloatArray(4)

/**
 * A box with its upright edges and its top edges rounded off to radius [r] in [steps] facets a quarter turn: cushions,
 * mattresses and counter tops, and with a small [r] the bevel that keeps furniture from looking cut from blocks. The
 * top face takes [top], the rest [side]. With [under] the bottom edges are rounded the same way (a cushion lifted off
 * the floor); otherwise the sides run straight down to [z0]. GPU renderer only: elsewhere it is a plain [Scene3D.box].
 */
fun Scene3D.roundBox(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float, top: Int, side: Int = top, steps: Int = 2, under: Boolean = false) {
    val rr = minOf(r, (x1 - x0) * .5f, (y1 - y0) * .5f, (z1 - z0) * (if (under) .5f else 1f))
    if (gpu == null || rr < .004f) { box(x0, y0, z0, x1, y1, z1, top, side); return }
    val st = steps.coerceIn(1, 3)
    val n = 4 * (st + 1)
    val ix0 = x0 + rr; val iy0 = y0 + rr; val ix1 = x1 - rr; val iy1 = y1 - rr
    // a ring round the box at height z, its corners rounded to radius rho
    fun ring(rho: Float, z: Float, xs: FloatArray, ys: FloatArray, zs: FloatArray) {
        var k = 0
        for (c in 0 until 4) {
            val cx = if (c == 0 || c == 3) ix1 else ix0
            val cy = if (c < 2) iy1 else iy0
            for (j in 0..st) {
                val a = (c + j.toFloat() / st) * 1.5708f
                xs[k] = cx + kotlin.math.cos(a) * rho; ys[k] = cy + kotlin.math.sin(a) * rho; zs[k] = z; k++
            }
        }
    }
    fun band(color: Int) {
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            rq[0] = rbx[i]; rqy[0] = rby[i]; rqz[0] = rbz[i]
            rq[1] = rbx[j]; rqy[1] = rby[j]; rqz[1] = rbz[j]
            rq[2] = rbx2[j]; rqy[2] = rby2[j]; rqz[2] = rbz2[j]
            rq[3] = rbx2[i]; rqy[3] = rby2[i]; rqz[3] = rbz2[i]
            poly(4, rq, rqy, rqz, color)
        }
    }
    fun swap() { rbx2.copyInto(rbx); rby2.copyInto(rby); rbz2.copyInto(rbz) }
    // up from the bottom (rounded underneath, or straight from the floor), then over the top edge
    if (under) {
        ring(0f, z0, rbx, rby, rbz)
        for (s in 1..st) { val a = s.toFloat() / st * 1.5708f; ring(rr * kotlin.math.sin(a), z0 + rr - rr * kotlin.math.cos(a), rbx2, rby2, rbz2); band(side); swap() }
    } else ring(rr, z0, rbx, rby, rbz)
    ring(rr, z1 - rr, rbx2, rby2, rbz2); band(side); swap()
    for (s in 1..st) {
        val a = s.toFloat() / st * 1.5708f
        ring(rr * kotlin.math.cos(a), z1 - rr + rr * kotlin.math.sin(a), rbx2, rby2, rbz2)
        band(if (s == st) top else side); swap()
    }
    rq[0] = ix0; rqy[0] = iy0; rqz[0] = z1
    rq[1] = ix1; rqy[1] = iy0; rqz[1] = z1
    rq[2] = ix1; rqy[2] = iy1; rqz[2] = z1
    rq[3] = ix0; rqy[3] = iy1; rqz[3] = z1
    poly(4, rq, rqy, rqz, top)
}

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

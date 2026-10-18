package com.hustlecity.game.render

import com.hustlecity.game.data.FurnitureDef
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a furniture model's boxes back to front for the current camera. Any two boxes that don't overlap are split
 * by a plane along one axis, and the one on the camera's side of it goes last; overlapping boxes keep the model's
 * own order. That lets models stack detail (handles, screens, legs) without parts showing through each other.
 * The GPU renderer has a depth buffer, so there the boxes simply go out in the model's order (no sorting cost).
 */
class PartSorter(private val scene: Scene3D) {
    private val cap = 72
    private val x0 = FloatArray(cap); private val y0 = FloatArray(cap); private val z0 = FloatArray(cap)
    private val x1 = FloatArray(cap); private val y1 = FloatArray(cap); private val z1 = FloatArray(cap)
    private val col = IntArray(cap)
    private val top = IntArray(cap)
    private val before = Array(cap) { BooleanArray(cap) }
    private val indeg = IntArray(cap)
    private val done = BooleanArray(cap)
    private var count = 0

    /** Draws [def] at grid position (x, y) turned [rot] quarter turns, optionally see-through, leaving out the parts in [skip] (bit per part). */
    fun draw(def: FurnitureDef, x: Float, y: Float, rot: Int, alpha: Float = 1f, skip: Long = 0L) {
        begin()
        for (i in 0 until min(def.parts.size, 64)) {
            if (skip != 0L && (skip ushr i) and 1L == 1L) continue
            val p = def.parts[i]
            add(def, x, y, rot, p.x0, p.y0, p.z0, p.x1, p.y1, p.z1, p.color)
        }
        end(alpha)
    }

    /** Starts a batch of boxes drawn (and sorted) together, like one model. */
    fun begin() { count = 0 }

    /**
     * Adds a box given in [def]'s own tile units (x across, y deep, z up), turned [rot] quarter turns like the model
     * and placed at (x, y). [topColor] colors its top face (a can's lid, a box's label).
     */
    fun add(def: FurnitureDef, x: Float, y: Float, rot: Int, ax0: Float, ay0: Float, az0: Float, ax1: Float, ay1: Float, az1: Float, color: Int, topColor: Int = color) {
        if (count >= cap) return
        val w = def.w.toFloat(); val d = def.d.toFloat()
        val ax: Float; val ay: Float; val bx: Float; val by: Float
        when (rot % 4) {
            0 -> { ax = ax0; ay = ay0; bx = ax1; by = ay1 }
            1 -> { ax = d - ay0; ay = ax0; bx = d - ay1; by = ax1 }
            2 -> { ax = w - ax0; ay = d - ay0; bx = w - ax1; by = d - ay1 }
            else -> { ax = ay0; ay = w - ax0; bx = ay1; by = w - ax1 }
        }
        val i = count++
        x0[i] = x + min(ax, bx); x1[i] = x + max(ax, bx)
        y0[i] = y + min(ay, by); y1[i] = y + max(ay, by)
        z0[i] = az0; z1[i] = az1; col[i] = color; top[i] = topColor
    }

    /** Draws the batch. */
    fun end(alpha: Float = 1f) { sortAndDraw(count, alpha); count = 0 }

    /** Draws boxes already placed with [set]. */
    fun drawBoxes(n: Int, alpha: Float = 1f) = sortAndDraw(min(n, cap), alpha)

    fun set(i: Int, ax0: Float, ay0: Float, az0: Float, ax1: Float, ay1: Float, az1: Float, color: Int) {
        x0[i] = ax0; y0[i] = ay0; z0[i] = az0; x1[i] = ax1; y1[i] = ay1; z1[i] = az1; col[i] = color; top[i] = color
    }

    private fun sortAndDraw(n: Int, alpha: Float) {
        if (scene.gpu != null) {
            for (i in 0 until n) scene.box(x0[i], y0[i], z0[i], x1[i], y1[i], z1[i], top[i], col[i], alpha)
            return
        }
        val cx = scene.proj.camX; val cy = scene.proj.camY; val cz = scene.proj.camZ
        for (i in 0 until n) { indeg[i] = 0; done[i] = false; java.util.Arrays.fill(before[i], 0, n, false) }
        val eps = .001f
        for (a in 0 until n) for (b in a + 1 until n) {
            // +1: a is behind b (draw a first), -1: b first, 0: no constraint
            val rel = when {
                x1[a] <= x0[b] + eps -> side(cx, (x1[a] + x0[b]) / 2)
                x1[b] <= x0[a] + eps -> -side(cx, (x1[b] + x0[a]) / 2)
                y1[a] <= y0[b] + eps -> side(cy, (y1[a] + y0[b]) / 2)
                y1[b] <= y0[a] + eps -> -side(cy, (y1[b] + y0[a]) / 2)
                z1[a] <= z0[b] + eps -> side(cz, (z1[a] + z0[b]) / 2)
                z1[b] <= z0[a] + eps -> -side(cz, (z1[b] + z0[a]) / 2)
                else -> 0
            }
            if (rel > 0) { before[a][b] = true; indeg[b]++ } else if (rel < 0) { before[b][a] = true; indeg[a]++ }
        }
        // Kahn's algorithm, preferring the model's own order; a cycle falls back to the lowest index
        for (k in 0 until n) {
            var pick = -1
            for (i in 0 until n) if (!done[i] && indeg[i] == 0) { pick = i; break }
            if (pick < 0) for (i in 0 until n) if (!done[i]) { pick = i; break }
            done[pick] = true
            for (j in 0 until n) if (before[pick][j]) indeg[j]--
            scene.box(x0[pick], y0[pick], z0[pick], x1[pick], y1[pick], z1[pick], top[pick], col[pick], alpha)
        }
    }

    /** +1 when the camera is on the high side of the plane at [mid] (so the low box is behind), else -1. */
    private fun side(cam: Float, mid: Float) = if (cam > mid) 1 else -1
}

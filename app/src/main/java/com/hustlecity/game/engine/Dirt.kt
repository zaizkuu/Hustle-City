package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.model.Business
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Procedural floor dirt. Every customer leaves a little dirt along the routes people actually walk (door → displays →
 * register), more of it near the entrance when shoes are wet, and now and then a spill or a piece of litter. Trash
 * cans catch litter nearby. Cleaners work the dirtiest tiles first. Store cleanliness is the average over the floor.
 */
class DirtModel(private val e: GameEngine) {
    private class Layout(val key: Int, val w: Int, val d: Int, val weight: FloatArray, val nearBin: BooleanArray, val walk: BooleanArray, val hot: IntArray, val door: IntArray)

    private val layouts = HashMap<Int, Layout>()

    fun size(b: Business): Pair<Int, Int> {
        val bld = e.city.building(b.buildingId)
        return (bld?.gridW ?: 8) to (bld?.gridD ?: 6)
    }

    /** Makes sure the dirt grid matches the floor (old saves get dirt spread to match their cleanliness). */
    fun ensure(b: Business) {
        val (w, d) = size(b)
        if (b.dirt.size == w * d) return
        val lay = layout(b)
        val start = (100.0 - b.cleanliness).toFloat().coerceIn(0f, 100f)
        val mean = lay.weight.average().toFloat().coerceAtLeast(1e-6f)
        b.dirt = MutableList(w * d) { i -> if (!lay.walk[i]) 0f else (start * (.4f + .6f * lay.weight[i] / mean)).coerceIn(0f, 100f) }
        b.cleanliness = cleanliness(b)
    }

    private fun layout(b: Business): Layout {
        val (w, d) = size(b)
        val key = b.furniture.hashCode() * 31 + w * 7 + d
        layouts[b.id]?.let { if (it.key == key) return it }
        val occ = BooleanArray(w * d)
        val targets = ArrayList<Int>()
        val bins = ArrayList<Int>()
        fun idx(x: Int, y: Int) = if (x in 0 until w && y in 0 until d) y * w + x else -1
        for (p in b.furniture) {
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            val walkable = "rug" in def.tags || "dance_floor" in def.tags
            if (!walkable) for (yy in p.y until p.y + fd) for (xx in p.x until p.x + fw) idx(xx, yy).let { if (it >= 0) occ[it] = true }
            if ("trash" in def.tags) bins += idx(p.x, p.y)
            if (def.fn == ItemFn.DISPLAY || def.fn == ItemFn.EQUIPMENT || def.fn == ItemFn.CASH_REGISTER || def.fn == ItemFn.SEATING || def.fn == ItemFn.OFFICE) {
                // the tiles in front of it, where customers stand
                val r = p.rot % 4
                when (r) {
                    0 -> for (xx in p.x until p.x + fw) targets += idx(xx, p.y + fd)
                    1 -> for (yy in p.y until p.y + fd) targets += idx(p.x - 1, yy)
                    2 -> for (xx in p.x until p.x + fw) targets += idx(xx, p.y - 1)
                    else -> for (yy in p.y until p.y + fd) targets += idx(p.x + fw, yy)
                }
                if ("dance_floor" in def.tags) for (yy in p.y until p.y + fd) for (xx in p.x until p.x + fw) targets += idx(xx, yy)
            }
        }
        val weight = FloatArray(w * d) { if (occ[it]) 0f else .08f }
        val doorX = w / 2
        val door = intArrayOf(idx(doorX, d - 1), idx(doorX - 1, d - 1))
        // breadth-first routes from the door: every tile on the way to a display gets foot traffic
        val prev = IntArray(w * d) { -1 }
        val queue = IntArray(w * d)
        var head = 0; var tail = 0
        val start = door[0]
        if (start >= 0) {
            queue[tail++] = start; prev[start] = start
            while (head < tail) {
                val cur = queue[head++]
                val cx = cur % w; val cy = cur / w
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val ni = idx(cx + dx, cy + dy)
                    if (ni < 0 || occ[ni] || prev[ni] != -1) continue
                    prev[ni] = cur; queue[tail++] = ni
                }
            }
            for (t in targets) {
                if (t < 0 || occ[t] || prev[t] == -1) continue
                var cur = t
                var guard = 0
                while (cur != start && guard++ < w * d) { weight[cur] += 1f; cur = prev[cur] }
                weight[start] += 1f
            }
        }
        // the entrance mat area takes the brunt
        for (i in weight.indices) {
            if (occ[i]) continue
            val x = i % w; val y = i / w
            val dd = abs(x - doorX + .5f) + abs(y - (d - 1))
            if (dd < 3f) weight[i] += (3f - dd) * 1.5f
        }
        val sum = weight.sum().coerceAtLeast(1e-6f)
        for (i in weight.indices) weight[i] /= sum
        val nearBin = BooleanArray(w * d) { i -> bins.any { bt -> bt >= 0 && max(abs(bt % w - i % w), abs(bt / w - i / w)) <= 3 } }
        val hot = weight.indices.filter { !occ[it] }.sortedByDescending { weight[it] }.take(max(4, w * d / 4)).toIntArray()
        return Layout(key, w, d, weight, nearBin, BooleanArray(w * d) { !occ[it] }, hot, door).also { layouts[b.id] = it }
    }

    /** One customer's worth of dirt. Returns the drop in cleanliness. */
    fun onCustomer(b: Business, area: Int) {
        ensure(b)
        val lay = layout(b)
        val n = b.dirt.size
        val f = e.weather.dirtFactor()
        // the same total as before (about 28 / area cleanliness points per customer), spread along the walking routes
        val mass = (28.0 / area * n * f).toFloat()
        var added = 0f
        for (i in 0 until n) {
            val wgt = lay.weight[i]
            if (wgt <= 0f) continue
            val old = b.dirt[i]
            val nv = min(100f, old + mass * wgt)
            b.dirt[i] = nv
            added += nv - old
        }
        // wet shoes: extra mud at the door
        if (f > 1.5) for (t in lay.door) if (t >= 0) { val old = b.dirt[t]; b.dirt[t] = min(100f, old + 1.2f); added += b.dirt[t] - old }
        // the odd spill or wrapper; trash cans catch most litter near them
        if (lay.hot.isNotEmpty() && e.rnd.nextDouble() < .025) {
            val t = lay.hot[e.rnd.nextInt(lay.hot.size)]
            if (!lay.nearBin[t] || e.rnd.nextDouble() < .25) {
                val old = b.dirt[t]
                b.dirt[t] = min(100f, old + 12f + e.rnd.nextFloat() * 14f)
                added += b.dirt[t] - old
            }
        }
        b.cleanliness = (b.cleanliness - added / n).coerceIn(0.0, 100.0)
    }

    /** Cleans [points] of average cleanliness, dirtiest tiles first. */
    fun clean(b: Business, points: Double) {
        ensure(b)
        var budget = (points * b.dirt.size).toFloat()
        val order = b.dirt.indices.sortedByDescending { b.dirt[it] }
        for (i in order) {
            if (budget <= 0f) break
            val v = b.dirt[i]
            if (v <= 0f) break
            val take = min(v, budget)
            b.dirt[i] = v - take
            budget -= take
        }
        b.cleanliness = cleanliness(b)
    }

    fun cleanAll(b: Business) {
        ensure(b)
        for (i in b.dirt.indices) b.dirt[i] = 0f
        b.cleanliness = 100.0
    }

    fun cleanliness(b: Business): Double = if (b.dirt.isEmpty()) b.cleanliness else (100.0 - b.dirt.average()).coerceIn(0.0, 100.0)

    /** The dirtiest walkable tiles (for cleaners to head to). */
    fun dirtiest(b: Business, k: Int): List<Int> {
        if (b.dirt.isEmpty()) return emptyList()
        val lay = layout(b)
        return b.dirt.indices.filter { lay.walk.getOrElse(it) { false } && b.dirt[it] > 8f }.sortedByDescending { b.dirt[it] }.take(k)
    }

    /** Scrubs the tile a cleaner is standing on (visual cleaning between the hourly totals). */
    fun scrub(b: Business, tile: Int, amount: Float) {
        if (tile !in b.dirt.indices) return
        val old = b.dirt[tile]
        b.dirt[tile] = max(0f, old - amount)
        b.cleanliness = (b.cleanliness + (old - b.dirt[tile]) / b.dirt.size).coerceIn(0.0, 100.0)
    }
}

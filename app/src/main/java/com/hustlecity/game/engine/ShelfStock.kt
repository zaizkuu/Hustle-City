package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.data.StockLayout
import com.hustlecity.game.data.StockLayouts
import com.hustlecity.game.model.Business
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * What the displays of your store visibly hold. The business keeps its stock per product; this splits each kind of
 * stock over the displays that carry it, keeps that split steady as things sell or arrive, and turns it into product
 * slots the renderer draws. It also plays the changes: a customer lifting an item off a shelf (that shelf shows one
 * fewer at once), and new stock filling in item by item, held back while a staff member is still bringing the box.
 *
 * Displays group by the kind of product they show (their product tag); storage shelves show the back room's boxes
 * and a warehouse's pallet racks show its boxes. Purely visual: it never changes the economy.
 */
class ShelfStock(private val sc: InteriorScene, private val biz: Business) {
    /** Where new stock for a display came from. */
    enum class Source { PLAYER, BACK_ROOM, DELIVERY }

    /** An item flying from a shelf into a customer's hands. */
    class Take(val item: Int, val slot: Int, val pid: String, val person: Person) { var t = 0f }

    private class Group(val key: String) {
        val displays = ArrayList<Int>()
        var pids: List<String> = emptyList()
        val amounts = HashMap<String, Int>()
        var total = 0
        var back = 0
        var lastBack = -1
        /** Displays where visible customers took something the economy hasn't sold yet, oldest first. */
        val picks = ArrayDeque<Int>()
        var pickAge = 0f
        /** Each product's share of the slots (kept steady so items don't hop around as stock changes a little). */
        var shares = FloatArray(0)
        var sharePids: List<String> = emptyList()
        var version = 0
    }

    private var sig = Int.MIN_VALUE
    private var n = 0
    private var group = arrayOfNulls<Group>(0)
    private var layout = arrayOfNulls<StockLayout>(0)
    private var cap = IntArray(0)
    private var units = IntArray(0)
    private var shown = FloatArray(0)
    private var ceilVis = IntArray(0)
    private var ceilT = FloatArray(0)
    private var hold = FloatArray(0)
    private var slotPid = arrayOfNulls<Array<String?>>(0)
    private var slotAge = arrayOfNulls<FloatArray>(0)
    private var lastVis = IntArray(0)
    private var lastVer = IntArray(0)
    private val groups = LinkedHashMap<String, Group>()
    private val tmpPids = arrayOfNulls<String>(StockLayout.MAX_SLOTS)
    private val warehouse = BizCalc.isWarehouse(biz)

    /** The display the player is stocking: the next stock that arrives for its kind goes onto it. */
    var focus = -1
    private var focusT = 0f
    val takes = ArrayList<Take>()

    /** Asked when stock arrives on a display; returns true when someone is bringing it (the items wait for them). */
    var onRestock: ((item: Int, source: Source) -> Boolean)? = null

    fun stocked(i: Int) = i in 0 until n && group[i] != null
    fun layoutOf(i: Int): StockLayout? = if (i in 0 until n) layout[i] else null
    /** Units (or boxes) this display holds, and how many it can hold. */
    fun unitsOf(i: Int) = if (i in 0 until n) units[i] else 0
    fun capacityOf(i: Int) = if (i in 0 until n) cap[i] else 0
    /** Products shown in each slot (null = empty), and seconds since each one appeared. */
    fun slots(i: Int): Array<String?>? = if (i in 0 until n) slotPid[i] else null
    fun ages(i: Int): FloatArray? = if (i in 0 until n) slotAge[i] else null
    /** Items visible on a display right now. */
    fun visible(i: Int): Int = if (!stocked(i)) 0 else visibleCount(i)
    /** Items the display should show for its stock once animations finish. */
    fun target(i: Int): Int = if (!stocked(i)) 0 else targetCount(i)
    /** True while new stock for this display waits for the person bringing it. */
    fun waiting(i: Int) = i in 0 until n && hold[i] > 0f

    /** Lets new stock fill in now (whoever brought it has arrived). */
    fun release(i: Int) { if (i in 0 until n) hold[i] = 0f }

    /** The units each product has on this display (its share of the kind), for the display's info card. */
    fun contents(i: Int): List<Pair<String, Int>> {
        val g = group.getOrNull(i) ?: return emptyList()
        if (g.total <= 0) return emptyList()
        return g.pids.mapNotNull { pid -> val a = g.amounts[pid] ?: 0; if (a <= 0) null else pid to (a.toLong() * units[i] / g.total).toInt() }
            .filter { it.second > 0 }
    }

    /** Points the next stock arriving for this display's kind at it (the player is putting a box on it). */
    fun focusOn(i: Int) { focus = i; focusT = 4f }

    /** Stock arriving in the next moment was put there by the player (nobody needs to bring it). */
    fun playerStocked() { playerT = 1.5f }
    private var playerT = 0f

    // ------------------------------------------------------------------ bookkeeping

    private fun groupKey(def: FurnitureDef): String? {
        if (def.displayUnits <= 0) return null
        if (warehouse) return if ("pallet_rack" in def.tags) "*" else null
        if (def.fn == ItemFn.STORAGE) return "#back"
        if (def.fn != ItemFn.DISPLAY || "pallet_rack" in def.tags) return null
        val tags = biz.type.products.mapNotNull { Catalog.product(it.first)?.takeIf { p -> !p.service }?.tag }
        return def.tags.firstOrNull { it in tags } ?: def.tags.firstOrNull()
    }

    private fun signature(): Int {
        var h = sc.items.size
        for (p in sc.items) h = ((h * 31 + p.itemId.hashCode()) * 31 + p.x * 977 + p.y * 61 + p.rot)
        return h
    }

    private fun rebuild() {
        val items = sc.items
        n = items.size
        group = arrayOfNulls(n); layout = arrayOfNulls(n)
        cap = IntArray(n); units = IntArray(n); shown = FloatArray(n)
        ceilVis = IntArray(n) { -1 }; ceilT = FloatArray(n); hold = FloatArray(n)
        slotPid = arrayOfNulls(n); slotAge = arrayOfNulls(n); lastVis = IntArray(n) { -1 }; lastVer = IntArray(n) { -1 }
        groups.clear()
        takes.clear()
        for ((i, p) in items.withIndex()) {
            val def = Catalog.item(p.itemId) ?: continue
            val lay = StockLayouts.of(def.id) ?: continue
            val key = groupKey(def) ?: continue
            val g = groups.getOrPut(key) { Group(key) }
            g.displays += i
            group[i] = g; layout[i] = lay; cap[i] = def.displayUnits
            slotPid[i] = arrayOfNulls(lay.slots); slotAge[i] = FloatArray(lay.slots) { 99f }
        }
        for (g in groups.values) {
            measure(g)
            split(g)
            g.lastBack = g.back
            for (i in g.displays) shown[i] = targetCount(i).toFloat()
        }
        focus = -1
    }

    /** Reads the business's stock for a group. */
    private fun measure(g: Group) {
        g.amounts.clear()
        when (g.key) {
            "*" -> for ((pid, q) in biz.stock) if (q > 0) g.amounts[pid] = BizCalc.boxesOf(pid, q)
            "#back" -> for ((pid, q) in biz.backStock) if (q > 0) g.amounts[pid] = BizCalc.boxesOf(pid, q)
            else -> {
                var back = 0
                for ((pid, _) in biz.type.products) {
                    val p = Catalog.product(pid) ?: continue
                    if (p.tag != g.key || p.service) continue
                    g.amounts[pid] = (biz.stock[pid] ?: 0).coerceAtLeast(0)
                    back += biz.backStock[pid] ?: 0
                }
                g.back = back
            }
        }
        if (g.key == "*" || g.key == "#back") g.pids = g.amounts.keys.sortedBy { pid -> biz.type.products.indexOfFirst { it.first == pid }.let { if (it < 0) 99 else it } * 1000 + pid.hashCode().mod(997) }
        else if (g.pids.isEmpty()) g.pids = biz.type.products.map { it.first }.filter { Catalog.product(it)?.tag == g.key }
        g.total = g.amounts.values.sum()
    }

    /** Spreads a group's stock over its displays in proportion to what each holds (emptiest first for leftovers). */
    private fun split(g: Group) {
        val capTotal = g.displays.sumOf { cap[it] }.coerceAtLeast(1)
        var left = g.total
        for (i in g.displays) { units[i] = min(cap[i], (g.total.toLong() * cap[i] / capTotal).toInt()); left -= units[i] }
        while (left > 0) {
            val i = g.displays.filter { units[it] < cap[it] }.minByOrNull { units[it].toFloat() / cap[it] } ?: g.displays.maxByOrNull { cap[it] } ?: break
            val step = max(1, min(left, cap[i] - units[i]))
            units[i] += step; left -= step
        }
    }

    private fun targetCount(i: Int): Int {
        val u = units[i]
        if (u <= 0) return 0
        val slots = layout[i]!!.slots
        return max(1, min(slots, ceil(u.toDouble() * slots / cap[i].coerceAtLeast(1)).toInt()))
    }

    private fun visibleCount(i: Int): Int {
        val v = floor(shown[i]).toInt()
        return if (ceilVis[i] >= 0) min(v, ceilVis[i]) else v
    }

    // ------------------------------------------------------------------ every frame

    fun update(dt: Float) {
        val s = signature()
        if (s != sig) { sig = s; rebuild() }
        if (focusT > 0f) { focusT -= dt; if (focusT <= 0f) focus = -1 }
        if (playerT > 0f) playerT -= dt
        for (g in groups.values) reconcile(g, dt)
        for (i in 0 until n) if (group[i] != null) animate(i, dt)
        val it = takes.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.t += dt
            if (t.t >= TAKE_TIME) it.remove()
        }
    }

    /** Matches the displays to the business's stock: sales come off the displays customers took from first. */
    private fun reconcile(g: Group, dt: Float) {
        measure(g)
        var have = 0
        for (i in g.displays) have += units[i]
        if (g.picks.isNotEmpty()) {
            g.pickAge += dt
            // a visible pick the economy never sold (an extra the store doesn't sell much of): put it back
            if (g.pickAge > PICK_TIMEOUT) { g.picks.removeFirst(); g.pickAge = 0f }
        }
        if (g.total < have) {
            var k = have - g.total
            while (k > 0 && g.picks.isNotEmpty()) {
                val i = g.picks.removeFirst()
                g.pickAge = 0f
                if (units[i] > 0) { units[i]--; k-- }
            }
            if (k > 0) take(g, k)
        } else if (g.total > have) {
            val source = when {
                (focus >= 0 && group.getOrNull(focus) === g) || playerT > 0f -> Source.PLAYER
                g.lastBack >= 0 && g.back < g.lastBack -> Source.BACK_ROOM
                else -> Source.DELIVERY
            }
            give(g, g.total - have, source)
        }
        g.lastBack = g.back
        updateShares(g)
    }

    /** Takes [k] units off the fullest displays. */
    private fun take(g: Group, k0: Int) {
        var k = k0
        while (k > 0) {
            val i = g.displays.filter { units[it] > 0 }.maxByOrNull { units[it].toFloat() / cap[it] } ?: break
            val step = max(1, min(k, units[i] - (units[i] * 3) / 4))
            units[i] -= step; k -= step
        }
    }

    /** Puts [k] new units on the displays: the one the player is stocking first, then the emptiest. */
    private fun give(g: Group, k0: Int, source: Source) {
        var k = k0
        val grew = HashSet<Int>()
        if (source == Source.PLAYER && focus in g.displays) {
            val add = min(k, cap[focus] - units[focus]).coerceAtLeast(0)
            if (add > 0) { units[focus] += add; k -= add; grew += focus }
        }
        while (k > 0) {
            val i = g.displays.filter { units[it] < cap[it] }.minByOrNull { units[it].toFloat() / cap[it] } ?: g.displays.maxByOrNull { cap[it] } ?: break
            val step = max(1, min(k, max(1, (cap[i] - units[i]) / 2)))
            units[i] += step; k -= step; grew += i
        }
        for (i in grew) {
            ceilVis[i] = -1
            // someone brings the box: the items appear once they're there
            if (source != Source.PLAYER && onRestock?.invoke(i, source) == true) hold[i] = HOLD_MAX
        }
    }

    private fun updateShares(g: Group) {
        val pids = g.pids
        val tot = g.total
        if (tot <= 0) return
        var changed = g.shares.size != pids.size || g.sharePids != pids
        if (!changed) for ((k, pid) in pids.withIndex()) {
            val sNow = (g.amounts[pid] ?: 0).toFloat() / tot
            val old = g.shares[k]
            if ((old == 0f) != (sNow == 0f) || kotlin.math.abs(old - sNow) > .12f) { changed = true; break }
        }
        if (!changed) return
        g.shares = FloatArray(pids.size) { (g.amounts[pids[it]] ?: 0).toFloat() / tot }
        g.sharePids = pids
        g.version++
    }

    private fun animate(i: Int, dt: Float) {
        val lay = layout[i]!!
        val f = targetCount(i)
        if (ceilVis[i] >= 0) {
            ceilT[i] -= dt
            if (f <= ceilVis[i] || ceilT[i] <= 0f) ceilVis[i] = -1
        }
        if (hold[i] > 0f) hold[i] -= dt
        if (f < shown[i]) shown[i] = f.toFloat()
        else if (f > shown[i] && hold[i] <= 0f) shown[i] = min(f.toFloat(), shown[i] + max(6f, lay.slots / FILL_TIME) * dt)
        val v = visibleCount(i)
        val g = group[i]!!
        val pids = slotPid[i]!!
        val ages = slotAge[i]!!
        if (v != lastVis[i] || g.version != lastVer[i]) {
            assign(i, v, tmpPids)
            for (s in 0 until lay.slots) {
                if (tmpPids[s] != pids[s]) { if (tmpPids[s] != null) ages[s] = 0f; pids[s] = tmpPids[s] }
            }
            lastVis[i] = v; lastVer[i] = g.version
        }
        for (s in 0 until lay.slots) if (ages[s] < 99f) ages[s] += dt
    }

    /**
     * Which product each slot shows when [v] items are visible: every product gets a block of slots in proportion to
     * its share, and fills it from the front; the blocks fill evenly, so a half-empty display is half empty everywhere.
     */
    private fun assign(i: Int, v: Int, out: Array<String?>) {
        val lay = layout[i]!!
        val m = lay.slots
        for (s in 0 until m) out[s] = null
        val g = group[i]!!
        val pids = g.sharePids
        val sh = g.shares
        val r = pids.size
        if (v <= 0 || r == 0) return
        // block sizes by largest remainder, at least one slot for anything in stock
        val size = IntArray(r)
        var used = 0
        for (k in 0 until r) { size[k] = floor(sh[k] * m).toInt(); if (sh[k] > 0f && size[k] == 0 && m >= r) size[k] = 1; used += size[k] }
        while (used > m) { val k = (0 until r).maxByOrNull { size[it] } ?: break; size[k]--; used-- }
        while (used < m) {
            val k = (0 until r).filter { sh[it] > 0f }.maxByOrNull { sh[it] * m - size[it] } ?: break
            size[k]++; used++
        }
        val start = IntArray(r)
        for (k in 1 until r) start[k] = start[k - 1] + size[k - 1]
        val got = IntArray(r)
        for (item in 0 until min(v, m)) {
            var best = -1; var bestD = -Float.MAX_VALUE
            for (k in 0 until r) {
                if (got[k] >= size[k]) continue
                val d = size[k] * (item + 1f) / m - got[k]
                if (d > bestD) { bestD = d; best = k }
            }
            if (best < 0) break
            out[start[best] + got[best]] = pids[best]
            got[best]++
        }
    }

    // ------------------------------------------------------------------ customers

    /**
     * A customer takes an item off display [i]: it shows one fewer straight away, and the item flies to their hands.
     * Returns the product taken, or null when the display looks empty.
     */
    fun customerTakes(i: Int, person: Person): String? {
        if (!stocked(i)) return null
        val v = visibleCount(i)
        if (v <= 0) return null
        assign(i, v - 1, tmpPids)
        val now = slotPid[i]!!
        var slot = -1
        for (s in now.indices) if (now[s] != null && tmpPids[s] == null) { slot = s; break }
        val pid = if (slot >= 0) now[slot] else now.firstOrNull { it != null }
        ceilVis[i] = v - 1
        ceilT[i] = PICK_TIMEOUT
        val g = group[i]!!
        g.picks.addLast(i)
        if (g.picks.size == 1) g.pickAge = 0f
        if (pid != null && slot >= 0) takes += Take(i, slot, pid, person)
        return pid
    }

    companion object {
        /** Seconds a new delivery waits for the staff member carrying it before it fills in anyway. */
        const val HOLD_MAX = 14f
        /** Seconds for an item to fly from a shelf into a customer's hands. */
        const val TAKE_TIME = .45f
        /** Seconds a visible pick waits for the economy to sell it before the item comes back. */
        const val PICK_TIMEOUT = 25f
        /** Seconds a whole display takes to fill in, item by item. */
        const val FILL_TIME = 1.6f
    }
}

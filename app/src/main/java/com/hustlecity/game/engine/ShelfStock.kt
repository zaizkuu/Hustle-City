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
 * fewer at once), and new stock filling in item by item, held back while someone is still bringing the box.
 *
 * Displays group by the kind of product they show (their product tag); storage shelves show the back room's boxes
 * and a warehouse's pallet racks show its boxes. Purely visual: it never changes the economy. Nothing here allocates
 * from frame to frame; the arrays are rebuilt only when the furniture changes.
 */
class ShelfStock(private val sc: InteriorScene, private val biz: Business) {
    /** Where new stock for a display came from. */
    enum class Source { PLAYER, BACK_ROOM, DELIVERY }

    /** An item on its way from a shelf into a customer's hands. */
    class Take(val item: Int, val slot: Int, val pid: String, val person: Person) { var t = 0f }

    private class Group(val key: String, val pids: Array<String>) {
        val displays = ArrayList<Int>()
        /** Units (boxes for storage and pallet racks) of each of [pids] the business has right now. */
        val amounts = IntArray(pids.size)
        var total = 0
        /** Units of this kind waiting in the back room, and how many there were a frame ago. */
        var back = 0
        var lastBack = -1
        /** Displays where visible customers took something the economy hasn't sold yet, oldest first. */
        val picks = ArrayDeque<Int>()
        var pickAge = 0f
        /** Each product's share of the slots (kept steady so items don't hop around as stock changes a little). */
        val shares = FloatArray(pids.size)
        var version = 0
        /** Boxes instead of products (storage shelves, pallet racks). */
        val boxed get() = key == WAREHOUSE || key == BACK
    }

    private var sig = Int.MIN_VALUE
    private var n = 0
    private var ids = arrayOfNulls<String>(0)
    private var group = arrayOfNulls<Group>(0)
    private var layout = arrayOfNulls<StockLayout>(0)
    private var cap = IntArray(0)
    private var units = IntArray(0)
    private var shown = FloatArray(0)
    private var ceilVis = IntArray(0)
    private var ceilT = FloatArray(0)
    private var hold = FloatArray(0)
    private var grew = BooleanArray(0)
    private var gain = IntArray(0)
    private var slotPid = arrayOfNulls<Array<String?>>(0)
    private var slotAge = arrayOfNulls<FloatArray>(0)
    private var lastVis = IntArray(0)
    private var lastVer = IntArray(0)
    private val groups = ArrayList<Group>()
    private val tmpPids = arrayOfNulls<String>(StockLayout.MAX_SLOTS)
    private var tmpSize = IntArray(8)
    private var tmpStart = IntArray(8)
    private var tmpGot = IntArray(8)
    private val warehouse = BizCalc.isWarehouse(biz)

    /** The display the player is stocking: the next stock that arrives for its kind goes onto it. */
    var focus = -1
        private set
    private var focusT = 0f
    private var playerT = 0f

    /** Items on their way from a shelf to a customer's hands. */
    val takes = ArrayList<Take>()

    /** Asked when stock arrives on a display; returns true when someone is bringing it (the items wait for them). */
    var onRestock: ((item: Int, source: Source) -> Boolean)? = null

    /** True when item [i] of the scene is a display (or storage shelf) whose stock is drawn. */
    fun stocked(i: Int) = i in 0 until n && group[i] != null && sc.items.getOrNull(i)?.itemId == ids[i]
    fun layoutOf(i: Int): StockLayout? = if (stocked(i)) layout[i] else null
    /** Units (or boxes) this display holds, and how many it can hold. */
    fun unitsOf(i: Int) = if (stocked(i)) units[i] else 0
    fun capacityOf(i: Int) = if (stocked(i)) cap[i] else 0
    /** Products shown in each slot (null = empty), and seconds since each one appeared. */
    fun slots(i: Int): Array<String?>? = if (stocked(i)) slotPid[i] else null
    fun ages(i: Int): FloatArray? = if (stocked(i)) slotAge[i] else null
    /** True when a display shows boxes of stock rather than the products themselves (storage shelves, pallet racks). */
    fun boxed(i: Int) = stocked(i) && group[i]!!.boxed
    /** Items visible on a display right now. */
    fun visible(i: Int): Int = if (!stocked(i)) 0 else visibleCount(i)
    /** Items the display should show for its stock once animations finish. */
    fun target(i: Int): Int = if (!stocked(i)) 0 else targetCount(i)
    /** True while new stock for this display waits for the person bringing it. */
    fun waiting(i: Int) = stocked(i) && hold[i] > 0f
    /** True while items are still filling in (or waiting for whoever brings them). */
    fun filling(i: Int) = stocked(i) && (hold[i] > 0f || floor(shown[i]).toInt() < targetCount(i))

    /** Lets new stock fill in now (whoever brought it has arrived): on this display and the others of its kind it went to. */
    fun release(i: Int) {
        val g = group.getOrNull(i) ?: run { if (i in 0 until n) hold[i] = 0f; return }
        for (k in g.displays) hold[k] = 0f
    }

    /** The units each product has on this display (its share of the kind), for the display's info card. */
    fun contents(i: Int): List<Pair<String, Int>> {
        if (!stocked(i)) return emptyList()
        val g = group[i]!!
        if (g.total <= 0) return emptyList()
        val out = ArrayList<Pair<String, Int>>()
        for (k in g.pids.indices) {
            val a = g.amounts[k]
            if (a <= 0) continue
            val share = (a.toLong() * units[i] / g.total).toInt()
            if (share > 0) out += g.pids[k] to share
        }
        return out
    }

    /** Points the next stock arriving for this display's kind at it (the player is putting a box on it). */
    fun focusOn(i: Int) { focus = i; focusT = 4f }

    /** Stock arriving in the next moment was put there by the player (nobody needs to bring it). */
    fun playerStocked() { playerT = 1.5f }

    // ------------------------------------------------------------------ bookkeeping

    private fun groupKey(def: FurnitureDef): String? {
        if (def.displayUnits <= 0) return null
        if (warehouse) return if ("pallet_rack" in def.tags) WAREHOUSE else null
        if (def.fn == ItemFn.STORAGE) return BACK
        if (def.fn != ItemFn.DISPLAY || "pallet_rack" in def.tags) return null
        for (t in def.tags) if (biz.type.products.any { Catalog.product(it.first)?.let { p -> !p.service && p.tag == t } == true }) return t
        return def.tags.firstOrNull()
    }

    /** The products a group can show, in the store's own order. */
    private fun productsFor(key: String): Array<String> = when (key) {
        WAREHOUSE -> Catalog.products.filter { !it.service }.map { it.id }.toTypedArray()
        BACK -> biz.type.products.map { it.first }.filter { Catalog.product(it)?.service == false }.toTypedArray()
        else -> biz.type.products.map { it.first }.filter { Catalog.product(it)?.tag == key }.toTypedArray()
    }

    private fun signature(): Int {
        val items = sc.items
        var h = items.size
        for (k in items.indices) { val p = items[k]; h = ((h * 31 + p.itemId.hashCode()) * 31 + p.x * 977 + p.y * 61 + p.rot) }
        return h
    }

    private fun rebuild() {
        val items = sc.items
        n = items.size
        ids = arrayOfNulls(n); group = arrayOfNulls(n); layout = arrayOfNulls(n)
        cap = IntArray(n); units = IntArray(n); shown = FloatArray(n); grew = BooleanArray(n); gain = IntArray(n)
        ceilVis = IntArray(n) { -1 }; ceilT = FloatArray(n); hold = FloatArray(n)
        slotPid = arrayOfNulls(n); slotAge = arrayOfNulls(n); lastVis = IntArray(n) { -1 }; lastVer = IntArray(n) { -1 }
        groups.clear()
        takes.clear()
        for ((i, p) in items.withIndex()) {
            val def = Catalog.item(p.itemId) ?: continue
            val lay = StockLayouts.of(def.id) ?: continue
            val key = groupKey(def) ?: continue
            val g = groups.firstOrNull { it.key == key } ?: Group(key, productsFor(key)).also { groups += it }
            g.displays += i
            ids[i] = p.itemId; group[i] = g; layout[i] = lay; cap[i] = def.displayUnits
            slotPid[i] = arrayOfNulls(lay.slots); slotAge[i] = FloatArray(lay.slots) { 99f }
        }
        var most = 1
        for (g in groups) {
            most = max(most, g.pids.size)
            measure(g)
            split(g)
            g.lastBack = g.back
            updateShares(g, force = true)
            for (i in g.displays) shown[i] = targetCount(i).toFloat()
        }
        if (tmpSize.size < most) { tmpSize = IntArray(most); tmpStart = IntArray(most); tmpGot = IntArray(most) }
        focus = -1
    }

    /** Reads the business's stock for a group. */
    private fun measure(g: Group) {
        var total = 0
        var back = 0
        val src = if (g.key == BACK) biz.backStock else biz.stock
        for (k in g.pids.indices) {
            val pid = g.pids[k]
            val q = (src[pid] ?: 0).coerceAtLeast(0)
            val a = if (g.boxed) BizCalc.boxesOf(pid, q) else q
            g.amounts[k] = a
            total += a
            if (!g.boxed) back += biz.backStock[pid] ?: 0
        }
        g.total = total
        g.back = back
    }

    /** Spreads a group's stock over its displays in proportion to what each holds (emptiest first for leftovers). */
    private fun split(g: Group) {
        var capTotal = 0
        for (i in g.displays) capTotal += cap[i]
        capTotal = capTotal.coerceAtLeast(1)
        var left = g.total
        for (i in g.displays) { units[i] = min(cap[i], (g.total.toLong() * cap[i] / capTotal).toInt()); left -= units[i] }
        while (left > 0) {
            val i = emptiest(g)
            if (i < 0) break
            val step = max(1, min(left, cap[i] - units[i]))
            units[i] += step; left -= step
        }
    }

    /** The display of a group with the most room for its size, or the biggest one when all are full. */
    private fun emptiest(g: Group): Int {
        var best = -1; var bestF = Float.MAX_VALUE
        for (i in g.displays) if (units[i] < cap[i]) { val f = units[i].toFloat() / cap[i]; if (f < bestF) { bestF = f; best = i } }
        if (best >= 0) return best
        for (i in g.displays) if (best < 0 || cap[i] > cap[best]) best = i
        return best
    }

    /** The fullest display of a group that still holds something, or -1. */
    private fun fullest(g: Group): Int {
        var best = -1; var bestF = -1f
        for (i in g.displays) if (units[i] > 0) { val f = units[i].toFloat() / cap[i].coerceAtLeast(1); if (f > bestF) { bestF = f; best = i } }
        return best
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
        for (k in groups.indices) reconcile(groups[k], dt)
        for (i in 0 until n) if (group[i] != null) animate(i, dt)
        var k = takes.size - 1
        while (k >= 0) {
            val t = takes[k]
            t.t += dt
            if (t.t >= TAKE_TIME) takes.removeAt(k)
            k--
        }
    }

    /** Matches the displays to the business's stock: sales come off the displays customers took from first. */
    private fun reconcile(g: Group, dt: Float) {
        measure(g)
        var have = 0
        for (i in g.displays) have += units[i]
        if (g.picks.isNotEmpty()) {
            g.pickAge += dt
            // a visible pick the economy never sold (an extra the store doesn't sell much of): it goes back
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

    /** Takes [k0] units off the displays, fullest first, so they stay filled in proportion to their size. */
    private fun take(g: Group, k0: Int) {
        var k = k0
        while (k > 0) {
            val i = fullest(g)
            if (i < 0) break
            units[i]--; k--
        }
    }

    /**
     * Puts [k0] new units on the displays: the one the player is stocking first, the rest emptiest first (so the
     * kind's stock stays split in proportion to the displays' sizes). Whoever brings it is sent to the display that
     * gains the most; the items wait for them.
     */
    private fun give(g: Group, k0: Int, source: Source) {
        var k = k0
        for (i in g.displays) { grew[i] = false; gain[i] = 0 }
        if (source == Source.PLAYER && focus >= 0 && group[focus] === g) {
            val add = min(k, cap[focus] - units[focus]).coerceAtLeast(0)
            if (add > 0) { units[focus] += add; gain[focus] += add; k -= add; grew[focus] = true }
        }
        while (k > 0) {
            val i = emptiest(g)
            if (i < 0) break
            units[i]++; gain[i]++; k--; grew[i] = true
        }
        var most = -1
        for (i in g.displays) if (grew[i]) { ceilVis[i] = -1; if (most < 0 || gain[i] > gain[most]) most = i }
        if (most < 0 || source == Source.PLAYER || hold[most] > 0f) return
        // someone brings the box: the items appear once they're there
        if (onRestock?.invoke(most, source) == true) for (i in g.displays) if (grew[i]) hold[i] = HOLD_MAX
    }

    private fun updateShares(g: Group, force: Boolean = false) {
        val tot = g.total
        if (tot <= 0) return
        var changed = force
        if (!changed) for (k in g.pids.indices) {
            val now = g.amounts[k].toFloat() / tot
            val old = g.shares[k]
            if ((old == 0f) != (now == 0f) || kotlin.math.abs(old - now) > .12f) { changed = true; break }
        }
        if (!changed) return
        for (k in g.pids.indices) g.shares[k] = g.amounts[k].toFloat() / tot
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
     * its share, and fills it in the display's own order (see [StockLayout.order]); the blocks fill evenly, so a
     * half-empty display is half empty everywhere.
     */
    private fun assign(i: Int, v: Int, out: Array<String?>) {
        val lay = layout[i]!!
        val m = lay.slots
        for (s in 0 until m) out[s] = null
        val g = group[i]!!
        val sh = g.shares
        val r = g.pids.size
        if (v <= 0 || r == 0) return
        // block sizes by largest remainder, at least one slot for anything in stock
        val size = tmpSize; val start = tmpStart; val got = tmpGot
        var used = 0
        var inStock = 0
        for (k in 0 until r) if (sh[k] > 0f) inStock++
        for (k in 0 until r) {
            size[k] = floor(sh[k] * m).toInt()
            if (sh[k] > 0f && size[k] == 0 && m >= inStock) size[k] = 1
            used += size[k]; got[k] = 0
        }
        while (used > m) {
            var big = 0
            for (k in 1 until r) if (size[k] > size[big]) big = k
            size[big]--; used--
        }
        while (used < m) {
            var best = -1; var bestR = -Float.MAX_VALUE
            for (k in 0 until r) if (sh[k] > 0f && sh[k] * m - size[k] > bestR) { bestR = sh[k] * m - size[k]; best = k }
            if (best < 0) break
            size[best]++; used++
        }
        start[0] = 0
        for (k in 1 until r) start[k] = start[k - 1] + size[k - 1]
        for (item in 0 until min(v, m)) {
            var best = -1; var bestD = -Float.MAX_VALUE
            for (k in 0 until r) {
                if (got[k] >= size[k]) continue
                val d = size[k] * (item + 1f) / m - got[k]
                if (d > bestD) { bestD = d; best = k }
            }
            if (best < 0) break
            out[nth(lay, start[best], size[best], got[best])] = g.pids[best]
            got[best]++
        }
    }

    /** The [k]-th slot to fill among slots start until start + size. */
    private fun nth(lay: StockLayout, start: Int, size: Int, k: Int): Int {
        var c = 0
        val order = lay.order
        for (j in order.indices) {
            val s = order[j]
            if (s >= start && s < start + size) { if (c == k) return s; c++ }
        }
        return start + k
    }

    // ------------------------------------------------------------------ customers

    /**
     * A customer takes an item off display [i]: it shows one fewer straight away, and the item goes to their hands.
     * Returns the product taken, or null when the display looks empty (or isn't a product display).
     */
    fun customerTakes(i: Int, person: Person): String? {
        if (!stocked(i) || group[i]!!.boxed) return null
        val v = visibleCount(i)
        if (v <= 0) return null
        assign(i, v - 1, tmpPids)
        val now = slotPid[i]!!
        var slot = -1
        for (s in now.indices) if (now[s] != null && tmpPids[s] == null) { slot = s; break }
        if (slot < 0) for (s in now.indices) if (now[s] != null) { slot = s; break }
        val pid = if (slot >= 0) now[slot] else null
        ceilVis[i] = v - 1
        ceilT[i] = PICK_TIMEOUT
        val g = group[i]!!
        g.picks.addLast(i)
        if (g.picks.size == 1) g.pickAge = 0f
        if (pid != null) takes += Take(i, slot, pid, person)
        return pid
    }

    companion object {
        /** Seconds new stock waits for the person carrying it before it fills in anyway (a long walk round a big store). */
        const val HOLD_MAX = 40f
        /** Seconds for an item to go from a shelf into a customer's hands. */
        const val TAKE_TIME = .45f
        /** Seconds a visible pick waits for the economy to sell it before the item comes back. */
        const val PICK_TIMEOUT = 25f
        /** Seconds a whole display takes to fill in, item by item. */
        const val FILL_TIME = 1.6f
        private const val WAREHOUSE = "*"
        private const val BACK = "#back"
    }
}

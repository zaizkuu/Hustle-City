package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.model.PlacedItem
import kotlin.math.floor
import kotlin.random.Random

/**
 * Lays out a room nobody furnishes by hand. A piece only goes in if the room stays usable: every floor tile can still
 * be reached from the door, every counter has room for a customer in front and staff behind, every display and
 * machine can be walked up to, and every seat can be sat on. Anything that would break that is left out.
 */
class Fit(val w: Int, val d: Int, val doorX: Int = w / 2) {
    val items = ArrayList<PlacedItem>()
    private val occ = BooleanArray(w * d)
    /** Floor kept free of furniture but still walkable (the cars in a showroom). */
    private val reserved = BooleanArray(w * d)

    private fun idx(x: Int, y: Int) = if (x in 0 until w && y in 0 until d) y * w + x else -1

    fun isDoor(x: Int, y: Int) = y == d - 1 && (x == doorX || x == doorX - 1)

    /** The square just inside the door stays clear, so nobody walks in face first into a shelf. */
    private fun entrance(x: Int, y: Int) = y >= d - 2 && x in doorX - 2..doorX + 1

    fun reserve(x: Int, y: Int) { idx(x, y).let { if (it >= 0) reserved[it] = true } }

    private fun wallMounted(def: FurnitureDef) = def.parts.isNotEmpty() && def.parts.minOf { it.z0 } > .3f

    fun fits(id: String, x: Int, y: Int, rot: Int = 0): Boolean {
        val def = Catalog.item(id) ?: return false
        val (fw, fd) = itemDims(def, rot)
        if (x < 0 || y < 0 || x + fw > w || y + fd > d) return false
        val high = wallMounted(def)
        for (yy in y until y + fd) for (xx in x until x + fw) {
            val i = yy * w + xx
            if (occ[i] || isDoor(xx, yy) || entrance(xx, yy) || (reserved[i] && !high)) return false
        }
        return true
    }

    /** Places a piece if it fits and the room stays usable; returns whether it went in. */
    fun put(id: String, x: Int, y: Int, rot: Int = 0): Boolean {
        if (!fits(id, x, y, rot)) return false
        val p = PlacedItem(id, x, y, rot)
        items += p
        mark(p, true)
        if (sound()) return true
        items.removeAt(items.size - 1)
        mark(p, false)
        return false
    }

    /** Tries each spot in turn until one takes the piece. */
    fun putFirst(id: String, spots: List<Triple<Int, Int, Int>>): Boolean = spots.any { (x, y, r) -> put(id, x, y, r) }

    private fun mark(p: PlacedItem, on: Boolean) {
        val def = Catalog.item(p.itemId) ?: return
        val (fw, fd) = itemDims(def, p.rot)
        for (yy in p.y until p.y + fd) for (xx in p.x until p.x + fw) occ[yy * w + xx] = on
    }

    // ------------------------------------------------------------------ walls

    /** Pieces along the back wall, facing into the room, from [x0] to [x1] (exclusive), [gap] tiles apart. */
    fun backWall(ids: List<String>, x0: Int = 1, x1: Int = w - 1, gap: Int = 0, max: Int = Int.MAX_VALUE): Int {
        var x = x0; var n = 0; var k = 0
        while (x < x1 && n < max && ids.isNotEmpty()) {
            val id = ids[k % ids.size]
            val def = Catalog.item(id) ?: break
            if (x + def.w > x1) break
            if (put(id, x, 0, 0)) { n++; k++; x += def.w + gap } else x++
        }
        return n
    }

    /** Pieces along the left wall (facing right), from [y0] to [y1] (exclusive). */
    fun leftWall(ids: List<String>, y0: Int, y1: Int, gap: Int = 0, max: Int = Int.MAX_VALUE): Int = sideWall(ids, y0, y1, gap, max, left = true)

    /** Pieces along the right wall (facing left), from [y0] to [y1] (exclusive). */
    fun rightWall(ids: List<String>, y0: Int, y1: Int, gap: Int = 0, max: Int = Int.MAX_VALUE): Int = sideWall(ids, y0, y1, gap, max, left = false)

    private fun sideWall(ids: List<String>, y0: Int, y1: Int, gap: Int, max: Int, left: Boolean): Int {
        var y = y0; var n = 0; var k = 0
        while (y < y1 && n < max && ids.isNotEmpty()) {
            val id = ids[k % ids.size]
            val def = Catalog.item(id) ?: break
            val rot = if (left) 3 else 1
            val (fw, fd) = itemDims(def, rot)
            if (y + fd > y1) break
            if (put(id, if (left) 0 else w - fw, y, rot)) { n++; k++; y += fd + gap } else y++
        }
        return n
    }

    /** Pieces along the front wall either side of the door (facing in), outermost first. */
    fun frontWall(ids: List<String>, max: Int = Int.MAX_VALUE): Int {
        var n = 0
        for ((k, id) in ids.withIndex()) {
            if (n >= max) break
            val def = Catalog.item(id) ?: continue
            val xs = if (k % 2 == 0) (1..doorX - 3 - def.w).toList() else (w - 1 - def.w downTo doorX + 2).toList()
            if (xs.any { put(id, it, d - 1, 2) }) n++
        }
        return n
    }

    /** One of [ids] in each free corner (wall pieces turned to face in). */
    fun corners(vararg ids: String) {
        val spots = listOf(Triple(0, 0, 0), Triple(w - 1, 0, 0), Triple(0, d - 1, 3), Triple(w - 1, d - 1, 1))
        for ((k, s) in spots.withIndex()) put(ids[k % ids.size], s.first, s.second, s.third)
    }

    /** A row of pieces from [x0] to [x1] (exclusive) at depth [y], facing [rot], [gap] tiles apart. */
    fun row(ids: List<String>, y: Int, x0: Int, x1: Int, rot: Int, gap: Int = 0, max: Int = Int.MAX_VALUE): Int {
        var x = x0; var n = 0; var k = 0
        while (x < x1 && n < max && ids.isNotEmpty()) {
            val id = ids[k % ids.size]
            val def = Catalog.item(id) ?: break
            val (fw, _) = itemDims(def, rot)
            if (x + fw > x1) break
            if (put(id, x, y, rot)) { n++; k++; x += fw + gap } else x++
        }
        return n
    }

    /** Anywhere it fits, trying spots nearest the walls first (wall pieces turned to face in). */
    fun anywhere(id: String): Boolean {
        val def = Catalog.item(id) ?: return false
        val cands = ArrayList<Triple<Int, Int, Int>>()
        for (y in 0 until d) for (x in 0 until w) {
            val rot = when {
                y == 0 -> 0
                x == 0 -> 3
                x + def.d >= w -> 1
                y + def.d >= d -> 2
                else -> 0
            }
            cands += Triple(x, y, rot)
        }
        cands.sortBy { (x, y, _) -> minOf(x, y, w - 1 - x, d - 1 - y) }
        return putFirst(id, cands)
    }

    // ------------------------------------------------------------------ checks

    private val seen = BooleanArray(w * d)
    private val queue = IntArray(w * d)

    /** True when every free tile is reachable from the door and every piece can be used. */
    fun sound(): Boolean {
        val block = BooleanArray(w * d)
        for (p in items) {
            val def = Catalog.item(p.itemId) ?: continue
            if ("dance_floor" in def.tags) continue
            val (fw, fd) = itemDims(def, p.rot)
            for (yy in p.y until p.y + fd) for (xx in p.x until p.x + fw) block[yy * w + xx] = true
        }
        java.util.Arrays.fill(seen, false)
        val start = idx(doorX, d - 1)
        var head = 0; var tail = 0
        queue[tail++] = start; seen[start] = true
        while (head < tail) {
            val cur = queue[head++]
            val cx = cur % w; val cy = cur / w
            for (k in 0 until 4) {
                val ni = idx(cx + DX[k], cy + DY[k])
                if (ni < 0 || seen[ni] || block[ni]) continue
                seen[ni] = true
                queue[tail++] = ni
            }
        }
        for (i in 0 until w * d) if (!block[i] && !seen[i]) return false
        return items.all { usable(it) }
    }

    private fun reached(x: Int, y: Int) = idx(x, y).let { it >= 0 && seen[it] }

    private fun touches(x: Int, y: Int) = reached(x, y) || (0 until 4).any { reached(x + DX[it], y + DY[it]) }

    /** Tiles in front of a piece (or behind it). */
    private fun side(p: PlacedItem, def: FurnitureDef, behind: Boolean): List<Pair<Int, Int>> {
        val (fw, fd) = itemDims(def, p.rot)
        return when (if (behind) (p.rot + 2) % 4 else p.rot % 4) {
            0 -> (p.x until p.x + fw).map { it to p.y + fd }
            1 -> (p.y until p.y + fd).map { p.x - 1 to it }
            2 -> (p.x until p.x + fw).map { it to p.y - 1 }
            else -> (p.y until p.y + fd).map { p.x + fw to it }
        }
    }

    private fun usable(p: PlacedItem): Boolean {
        val def = Catalog.item(p.itemId) ?: return true
        val spots = Catalog.sitSpots[def.id]
        if (spots != null) {
            // every seat must have a free tile beside it to step from
            return spots.all { sp ->
                val (ax, ay) = when (p.rot % 4) {
                    0 -> sp.x to sp.y
                    1 -> (def.d - sp.y) to sp.x
                    2 -> (def.w - sp.x) to (def.d - sp.y)
                    else -> sp.y to (def.w - sp.x)
                }
                touches(floor(p.x + ax).toInt(), floor(p.y + ay).toInt())
            }
        }
        if ("dance_floor" in def.tags) return touches(p.x, p.y)
        return when (def.fn) {
            ItemFn.CASH_REGISTER -> side(p, def, false).any { reached(it.first, it.second) } && side(p, def, true).any { reached(it.first, it.second) }
            ItemFn.DISPLAY, ItemFn.EQUIPMENT, ItemFn.LOCKER, ItemFn.SHOWER, ItemFn.OFFICE, ItemFn.BOARD, ItemFn.STORAGE ->
                side(p, def, false).any { reached(it.first, it.second) }
            ItemFn.BED, ItemFn.HOME_FRIDGE, ItemFn.TV, ItemFn.SOFA, ItemFn.CLEANING -> {
                val (fw, fd) = itemDims(def, p.rot)
                (p.y until p.y + fd).any { yy -> (p.x until p.x + fw).any { xx -> touches(xx, yy) } }
            }
            else -> true
        }
    }

    companion object {
        private val DX = intArrayOf(1, -1, 0, 0)
        private val DY = intArrayOf(0, 0, 1, -1)
    }
}

/** The fit-outs of every room the player doesn't furnish: public places, rivals' stores and the starter flat. */
object Layouts {
    // ------------------------------------------------------------------ the starter flat

    /** What a new player's flat comes with: somewhere to sleep, cook, eat and hang clothes, with room left for more. */
    fun starterHome(w: Int, d: Int): List<PlacedItem> {
        val f = Fit(w, d)
        f.put("bed", 0, 0)
        f.put("nightstand", 2, 0)
        f.putFirst("kitchen_counter", listOf(Triple(w - 3, 0, 0), Triple(w - 2, 0, 0), Triple(3, 0, 0)))
        f.putFirst("home_fridge", listOf(Triple(w - 1, 0, 0), Triple(w - 4, 0, 0), Triple(3, 0, 0))) || f.anywhere("home_fridge")
        f.putFirst("wardrobe", listOf(Triple(0, 3, 3), Triple(0, 2, 3)))
        f.putFirst("dining_table", listOf(Triple(w - 2, 2, 1), Triple(w - 3, 2, 0), Triple(w - 2, 3, 1)))
        return f.items
    }

    // ------------------------------------------------------------------ rivals' stores

    /** A rival's store of [typeId] at fit-out [level] (1..3), the same every visit. */
    fun rival(f: Fit, typeId: String, level: Int, seed: Int) {
        val lv = level.coerceIn(1, 3)
        val rnd = Random(seed)
        val t = Catalog.type(typeId)
        when {
            t.office -> office(f, typeId, lv, rnd)
            typeId == "coffee" -> cafe(f, lv, food = false)
            typeId == "fastfood" -> cafe(f, lv, food = true)
            typeId == "hairdresser" -> salon(f, lv)
            typeId == "gym" -> gym(f, lv)
            typeId == "nightclub" -> club(f, lv)
            typeId == "cinema" || typeId == "theater" -> auditorium(f, lv, stage = typeId == "theater")
            else -> shop(f, typeId, lv, rnd)
        }
    }

    private class ShopSpec(val back: List<String>, val sides: List<String>, val islands: List<String>, val extras: List<String>, val counter: String = "cash_register")

    private fun spec(typeId: String): ShopSpec = when (typeId) {
        "florist" -> ShopSpec(listOf("flower_stand"), listOf("flower_stand", "flower_stand", "shelf"), listOf("flower_stand"),
            listOf("plant_large", "plant_large", "plant_small", "painting", "speaker_small", "trash_can", "plant_small"))
        "produce" -> ShopSpec(listOf("produce"), listOf("produce"), listOf("produce"),
            listOf("digital_scale", "shopping_baskets", "plant_large", "speaker_small", "trash_can", "digital_scale", "plant_large"))
        "supermarket" -> ShopSpec(listOf("drinks_fridge", "drinks_fridge", "fresh_fridge"), listOf("shelf", "shelf", "ice_cream_freezer"), listOf("shelf", "shelf", "freezer"),
            listOf("shopping_baskets", "security_camera", "trash_can", "shopping_baskets", "plant_large", "speaker_medium"), counter = "checkout_counter")
        "giftshop" -> ShopSpec(listOf("shelf"), listOf("shelf", "drinks_fridge"), listOf("shelf"),
            listOf("neon_circle", "plant_large", "security_camera", "painting", "speaker_small", "trash_can", "plant_small"))
        "liquor" -> ShopSpec(listOf("drinks_fridge"), listOf("shelf"), listOf("shelf"),
            listOf("security_camera", "neon_cactus", "plant_large", "speaker_small", "trash_can", "security_camera"))
        "clothing" -> ShopSpec(listOf("clothing_rack"), listOf("fitting_room", "fitting_room", "shoe_rack", "clothing_rack"), listOf("round_rack", "clothing_rack"),
            listOf("mannequin", "mannequin", "security_camera", "plant_large", "floor_lamp", "painting", "speaker_medium", "mannequin"))
        "bookstore" -> ShopSpec(listOf("bookshelf"), listOf("bookshelf"), listOf("bookshelf", "magazine_rack"),
            listOf("lounge_sofa", "floor_lamp", "plant_large", "security_camera", "speaker_small", "plant_small", "trash_can"))
        "electronics" -> ShopSpec(listOf("electronics_table"), listOf("electronics_table", "tv"), listOf("electronics_table"),
            listOf("security_camera", "security_camera", "neon_circle", "plant_large", "speaker_medium", "trash_can"))
        "jewelry" -> ShopSpec(listOf("jewelry_case"), listOf("jewelry_case", "mannequin"), listOf("jewelry_case"),
            listOf("security_camera", "lounge_sofa", "painting", "plant_large", "floor_lamp", "security_camera", "plant_large"))
        else -> {
            val t = Catalog.type(typeId)
            val ids = (t.requirements.map { itemForTag(it.tag) } + t.products.mapNotNull { Catalog.product(it.first)?.let { p -> itemForTag(p.tag) } })
                .filter { Catalog.item(it)?.fn == ItemFn.DISPLAY }.distinct().ifEmpty { listOf("shelf") }
            ShopSpec(ids, ids, ids, listOf("plant_large", "speaker_small", "trash_can"))
        }
    }

    /** Checkouts near the front on the right (a third on the left), facing the door. */
    private fun counters(f: Fit, id: String, n: Int) {
        val cw = Catalog.item(id)?.w ?: 2
        var placed = 0
        var x = f.w - 1 - cw
        while (placed < n && x > f.doorX + 1) { if (f.put(id, x, f.d - 3)) { placed++; x -= cw + 1 } else x-- }
        x = 1
        while (placed < n && x + cw < f.doorX - 1) { if (f.put(id, x, f.d - 3)) { placed++; x += cw + 1 } else x++ }
    }

    /** Back-to-back rows of displays down the middle, with aisles between: [pairs] of them at most. */
    private fun islands(f: Fit, ids: List<String>, pairs: Int) {
        var gy = 3
        var n = 0
        while (n < pairs && gy + 1 <= f.d - 5) {
            val depth = ids.maxOf { Catalog.item(it)?.d ?: 1 }
            if (depth > 1) {
                // deep pieces (round racks) stand alone, spaced out
                f.row(ids, gy, 3, f.w - 3, 0, gap = 1)
            } else {
                // split around the walkway from the door
                f.row(ids, gy, 3, f.doorX - 1, 2); f.row(ids, gy + 1, 3, f.doorX - 1, 0)
                f.row(ids, gy, f.doorX + 1, f.w - 3, 2); f.row(ids, gy + 1, f.doorX + 1, f.w - 3, 0)
            }
            gy += 3; n++
        }
    }

    private fun shop(f: Fit, typeId: String, lv: Int, rnd: Random) {
        val s = spec(typeId)
        counters(f, s.counter, lv.coerceAtMost(if (typeId == "supermarket") 3 else 2))
        f.backWall(s.back)
        f.leftWall(s.sides, 2, f.d - 3)
        f.rightWall(s.sides.reversed(), 2, f.d - 4)
        islands(f, s.islands, lv)
        for (id in s.extras.take(3 + lv * 2)) f.anywhere(id)
        if (rnd.nextBoolean()) f.anywhere("wall_lamp")
    }

    /** Coffee shop or fast food: the counter and kitchen at the back, tables out front. */
    private fun cafe(f: Fit, lv: Int, food: Boolean) {
        val line = if (food) listOf("grill", "fryer", "drinks_fridge", "pizza_oven", "fryer", "ice_cream_freezer", "grill")
            else listOf("pastry_case", "coffee_machine", "pastry_case", "coffee_machine", "pastry_case")
        val span = (line.size + 2).coerceAtMost(f.w - 2)
        val x0 = ((f.w - span * 2) / 2).coerceAtLeast(1)
        f.backWall(line, x0, f.w - 1, max = 3 + lv * 2)
        // registers in front of the kitchen, staff between them and the machines
        f.row(listOf("cash_register"), 2, f.doorX - 1 - lv, f.w - 2, 0, gap = 1, max = lv.coerceAtMost(3))
        // tables in a grid with walkways
        var y = 4
        while (y + 2 <= f.d - 2) {
            f.row(listOf("table_set"), y, 1, f.doorX - 2, 0, gap = 1)
            f.row(listOf("table_set"), y, f.doorX + 2, f.w - 1, 0, gap = 1)
            y += 3
        }
        f.leftWall(listOf("barstool"), 2, 4)
        f.rightWall(listOf("lounge_sofa"), 3, f.d - 2, max = lv)
        for (id in listOf(if (food) "neon_circle" else "painting", "plant_large", "trash_can", "speaker_small", "plant_small", if (food) "trash_can" else "floor_lamp")) f.anywhere(id)
    }

    private fun salon(f: Fit, lv: Int) {
        f.backWall(listOf("hair_chair"), 1, f.w - 1, gap = 1, max = 2 + lv)
        f.leftWall(listOf("hair_wash"), 3, f.d - 3, gap = 1, max = lv)
        counters(f, "cash_register", 1)
        f.rightWall(listOf("shelf"), 3, f.d - 4, max = 1)
        f.frontWall(listOf("bench", "lounge_sofa"))
        for (id in listOf("plant_large", "painting", "speaker_small", "plant_small", "trash_can", "floor_lamp")) f.anywhere(id)
    }

    private fun gym(f: Fit, lv: Int) {
        // cardio facing the windows, weights in the middle, lockers and showers by the changing area
        f.backWall(listOf("treadmill", "treadmill", "exercise_bike", "rowing_machine"), 1, f.w - 1, max = 4 + lv * 2)
        f.row(listOf("workout_bench", "boxing_bag", "workout_bench", "weight_rack"), 4, 3, f.w - 3, 0, gap = 1, max = 2 + lv * 2)
        f.leftWall(listOf("gym_locker"), 3, f.d - 3, max = 1 + lv)
        f.rightWall(listOf("shower"), 3, f.d - 4, gap = 1, max = 1 + lv)
        f.putFirst("reception_desk", listOf(Triple(f.w - 5, f.d - 3, 0), Triple(1, f.d - 3, 0)))
        f.anywhere("fitness_board"); f.anywhere("drinks_fridge")
        for (id in listOf("speaker_large", "plant_large", "water_cooler", "trash_can", "speaker_large")) f.anywhere(id)
    }

    private fun club(f: Fit, lv: Int) {
        val cx = f.doorX
        // the DJ plays from a booth with room behind it, speakers either side, the dance floor in front
        f.put("dj_booth", cx - 1, 1)
        f.put("speaker_large", cx - 3, 0); f.put("speaker_large", cx + 2, 0)
        f.put("dance_floor", cx - 2, 3)
        if (lv >= 2) f.put("dance_floor", cx - 5, 3) || f.put("dance_floor", cx + 1, 3)
        if (lv >= 3) f.put("dance_floor", cx + 1, 3) || f.put("dance_floor", cx - 5, 3)
        for ((x, y) in listOf(cx - 3 to 6, cx + 2 to 6, cx - 6 to 2, cx + 5 to 2)) f.put("stage_lights", x, y)
        // bars along the left wall, with room behind for the bartender
        var y = 2
        repeat((lv + 1) / 2 + 1) { while (y + 3 <= f.d - 3 && !f.put("cocktail_bar", 1, y, 3)) y++; y += 4 }
        f.rightWall(listOf("lounge_sofa"), 2, f.d - 3, gap = 1, max = 1 + lv)
        f.putFirst("coat_check", listOf(Triple(f.w - 4, f.d - 3, 0), Triple(f.doorX + 3, f.d - 3, 0)))
        for (id in listOf("neon_circle", "neon_cactus", "neon_circle", "plant_large", "trash_can", "barstool", "barstool")) f.anywhere(id)
    }

    /** Cinema (a screen) or theater (a stage): rows of seats facing it, the lobby by the door. */
    private fun auditorium(f: Fit, lv: Int, stage: Boolean) {
        val cx = f.w / 2
        if (stage) {
            f.put("stage", cx - 2, 0)
            f.put("stage_lights", cx - 4, 0); f.put("stage_lights", cx + 3, 0)
        } else {
            f.put("cinema_screen", cx - 2, 0)
            f.put("speaker_large", cx - 4, 0); f.put("speaker_large", cx + 3, 0)
        }
        // rows of seats with a walkway between each, an aisle down the middle
        var y = if (stage) 4 else 3
        var rows = 0
        while (y <= f.d - 6 && rows < 2 + lv) {
            f.put("cinema_seats", cx - 4, y); f.put("cinema_seats", cx + 1, y)
            if (lv >= 2) { f.put("cinema_seats", cx - 8, y); f.put("cinema_seats", cx + 5, y) }
            y += 2; rows++
        }
        // the lobby: tickets on the right, snacks on the left
        f.putFirst("cash_register", listOf(Triple(f.w - 4, f.d - 3, 0), Triple(f.doorX + 2, f.d - 3, 0)))
        f.putFirst("concession_stand", listOf(Triple(2, f.d - 3, 0), Triple(1, f.d - 3, 0), Triple(f.doorX - 4, f.d - 3, 0)))
        if (lv >= 3) f.putFirst("concession_stand", listOf(Triple(5, f.d - 3, 0), Triple(f.doorX - 4, f.d - 3, 0)))
        f.frontWall(listOf("lounge_sofa", "lounge_sofa"))
        for (id in listOf("painting", "painting", "plant_large", "trash_can", "plant_large", "trash_can")) f.anywhere(id)
    }

    /** Office firms: rows of desks, a meeting table, filing and a water cooler, and whatever the trade needs. */
    private fun office(f: Fit, typeId: String, lv: Int, rnd: Random) {
        desks(f, 2 + lv * 2)
        f.putFirst("meeting_table", listOf(Triple(f.w - 4, f.d - 5, 0), Triple(1, f.d - 5, 0), Triple(f.w - 5, f.d - 6, 0)))
        val trade = when (typeId) {
            "design" -> List(lv + 1) { "graphic_tablet" } + listOf("painting", "neon_circle")
            "travel" -> List(lv) { "desk_globe" } + listOf("painting", "info_screen")
            "events" -> List(lv) { "desk_calendar" } + listOf("painting", "whiteboard")
            "lawfirm" -> listOf("bookshelf", "bookshelf", "painting", "rug")
            "webdev" -> listOf("whiteboard", "lounge_sofa", "neon_cactus", "info_screen")
            else -> listOf("painting")
        }
        for (id in trade) f.anywhere(id)
        val common = listOf("filing_cabinet", "water_cooler", "copier", "plant_large", "filing_cabinet", "plant_large", "wall_clock", "trash_can", "plant_small")
        for (id in common.take(5 + lv)) f.anywhere(id)
        if (rnd.nextBoolean()) f.anywhere("speaker_small")
    }

    /** Rows of desks facing the back wall, their chairs on the near side, walkways between rows. */
    private fun desks(f: Fit, n: Int, x0: Int = 1, x1: Int = f.w - 1, y0: Int = 1, chair: Boolean = false): Int {
        var placed = 0
        var y = y0
        while (placed < n && y + 2 <= f.d - 3) {
            var x = x0
            while (placed < n && x + 2 <= x1) {
                if (f.put("office_desk", x, y)) {
                    placed++
                    // a visitor's chair on the far side, facing whoever sits at the desk
                    if (chair && y > 0) f.put("chair", x, y - 1)
                    x += 3
                } else x++
            }
            y += if (chair) 4 else 3
        }
        return placed
    }

    // ------------------------------------------------------------------ public places

    fun place(f: Fit, kind: PlaceKind) {
        when (kind) {
            PlaceKind.BANK -> bank(f)
            PlaceKind.HOSPITAL -> hospital(f)
            PlaceKind.SCHOOL -> school(f)
            PlaceKind.RECRUITER -> recruiter(f)
            PlaceKind.MARKETING -> marketing(f)
            PlaceKind.WHOLESALER, PlaceKind.IMPORTER -> wholesale(f, kind == PlaceKind.IMPORTER)
            else -> {}
        }
    }

    private fun bank(f: Fit) {
        val cx = f.w / 2
        f.put("vault_door", cx - 1, 0)
        f.put("info_screen", cx - 4, 0); f.put("info_screen", cx + 2, 0)
        // tellers in a row, with room behind for the clerks
        val n = ((f.w - 4) / 4).coerceIn(2, 4)
        val x0 = cx - (n * 4 - 1) / 2
        for (k in 0 until n) f.put("teller_counter", x0 + k * 4, 2)
        // loan officers on either side, a client's chair across each desk
        f.put("office_desk", 1, 6); f.put("chair", 1, 5)
        f.put("office_desk", f.w - 3, 6); f.put("chair", f.w - 2, 5)
        if (f.d >= 13) { f.put("office_desk", 1, 9); f.put("chair", 1, 8); f.put("office_desk", f.w - 3, 9); f.put("chair", f.w - 2, 8) }
        // waiting in the middle, facing the tellers
        f.put("waiting_chairs", cx - 4, f.d - 5, 2); f.put("waiting_chairs", cx + 1, f.d - 5, 2)
        f.frontWall(listOf("atm", "atm"))
        f.corners("plant_large")
        for (id in listOf("painting", "painting", "wall_clock", "trash_can", "water_cooler", "plant_large")) f.anywhere(id)
    }

    private fun hospital(f: Fit) {
        // the ward along the back wall: beds with a drip stand each, screens between them
        var x = 1
        while (x + 3 <= f.w - 1) {
            f.put("hospital_bed", x, 0); f.put("iv_stand", x + 1, 0)
            if (x + 3 < f.w - 1) f.put("privacy_screen", x + 2, 0)
            x += 3
        }
        f.leftWall(listOf("medicine_cabinet", "medicine_cabinet"), 3, f.d - 3)
        // the doctor's desk and the reception
        f.put("office_desk", f.w - 3, 4); f.put("chair", f.w - 2, 3)
        f.putFirst("reception_desk", listOf(Triple(f.doorX + 2, f.d - 4, 0), Triple(f.w - 5, f.d - 4, 0)))
        // waiting rows facing the reception
        f.put("waiting_chairs", 3, f.d - 6, 0); f.put("waiting_chairs", 3, f.d - 4, 0)
        f.put("waiting_chairs", f.doorX - 4, f.d - 6, 0)
        f.put("info_screen", f.w - 1, f.d - 6, 1) || f.anywhere("info_screen")
        for (id in listOf("wall_clock", "water_cooler", "plant_large", "plant_large", "trash_can", "notice_board")) f.anywhere(id)
    }

    private fun school(f: Fit) {
        val cx = f.w / 2
        // the board and the teacher's lectern at the front of the class
        f.put("whiteboard", cx - 2, 0); f.put("whiteboard", cx - 1, 0); f.put("whiteboard", cx, 0); f.put("whiteboard", cx + 1, 0)
        f.put("info_screen", cx - 5, 0); f.put("wall_clock", cx + 3, 0)
        f.put("lectern", cx - 1, 2)
        // desks in columns with gaps, facing the board
        for (y in 4..(f.d - 5)) {
            var x = 2
            while (x <= f.w - 4) { f.put("student_desk", x, y); x += 2 }
        }
        f.leftWall(listOf("bookshelf"), 1, f.d - 3)
        f.rightWall(listOf("gym_locker"), 1, f.d - 4)
        f.putFirst("reception_desk", listOf(Triple(f.w - 5, f.d - 3, 0), Triple(f.doorX + 2, f.d - 3, 0)))
        for (id in listOf("notice_board", "desk_globe", "plant_large", "water_cooler", "plant_large", "trash_can")) f.anywhere(id)
    }

    private fun recruiter(f: Fit) {
        // interview desks: a recruiter on one side, a candidate on the other
        desks(f, 6, 1, f.w - 1, 2, chair = true)
        f.putFirst("reception_desk", listOf(Triple(f.doorX + 2, f.d - 3, 0), Triple(f.w - 5, f.d - 3, 0)))
        f.put("waiting_chairs", 1, f.d - 3, 0) || f.put("waiting_chairs", 1, f.d - 4, 0)
        f.put("notice_board", 0, 0); f.put("notice_board", f.w - 1, 0)
        for (id in listOf("water_cooler", "filing_cabinet", "filing_cabinet", "painting", "plant_large", "plant_large", "info_screen", "wall_clock", "trash_can")) f.anywhere(id)
    }

    private fun marketing(f: Fit) {
        // campaign screens on the back wall, creatives at desks, a meeting table and a lounge
        f.put("info_screen", 1, 0); f.put("info_screen", f.w - 3, 0)
        f.put("whiteboard", f.w / 2 - 1, 0); f.put("whiteboard", f.w / 2, 0)
        desks(f, 6, 1, f.w - 6, 2, chair = true)
        f.putFirst("meeting_table", listOf(Triple(f.w - 4, 2, 0), Triple(f.w - 5, 3, 0)))
        f.putFirst("reception_desk", listOf(Triple(f.doorX + 2, f.d - 3, 0), Triple(f.w - 5, f.d - 3, 0)))
        f.put("lounge_sofa", 1, f.d - 3, 0) || f.put("lounge_sofa", 1, f.d - 4, 0)
        f.put("coffee_table", 1, f.d - 2)
        for (id in listOf("graphic_tablet", "graphic_tablet", "neon_circle", "neon_cactus", "copier", "plant_large", "plant_large", "speaker_small", "trash_can")) f.anywhere(id)
    }

    private fun wholesale(f: Fit, importer: Boolean) {
        // tall racks along the back, aisles of shelves and pallets, the checkout by the door
        f.backWall(listOf("pallet_rack"))
        f.putFirst("checkout_counter", listOf(Triple(f.w - 4, f.d - 3, 0), Triple(f.doorX + 2, f.d - 3, 0)))
        val stock = if (importer) listOf("shelf", "pallet", "pallet") else listOf("pallet", "shelf", "pallet")
        var gy = 3
        while (gy + 1 <= f.d - 5) {
            f.row(stock, gy, 3, f.doorX - 1, 2); f.row(stock.reversed(), gy + 1, 3, f.doorX - 1, 0)
            f.row(stock, gy, f.doorX + 1, f.w - 3, 2); f.row(stock.reversed(), gy + 1, f.doorX + 1, f.w - 3, 0)
            gy += 3
        }
        f.leftWall(listOf("pallet_rack"), 2, f.d - 3)
        f.rightWall(listOf("pallet_rack"), 2, f.d - 4)
        f.put("cart_rack", 1, f.d - 1, 2)
        f.anywhere("forklift"); f.anywhere("hand_truck"); f.anywhere("info_screen")
        for (id in listOf("water_cooler", "trash_can", "hand_truck")) f.anywhere(id)
    }
}

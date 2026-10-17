package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.data.c
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.world.Building
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

enum class InteriorMode { BUSINESS, HOME, PLACE, RIVAL, EMPTY }

class Person(var x: Float, var y: Float, var shirt: Int, var skin: Int) {
    var path: IntArray = IntArray(0)
    var pathIdx = 0
    var stage = 0
    var wait = 0f
    var done = false
    var phase = 0f
}

val FLOOR_COLORS = intArrayOf(c(0xFFC08A5B), c(0xFFD9D4CC), c(0xFF6B5E55), c(0xFF5C6BC0), c(0xFF9E9E9E), c(0xFFB5654A), c(0xFF8FD3B6), c(0xFF3A3A3A))
val WALL_COLORS = intArrayOf(c(0xFFEDE3D3), c(0xFFF5F5F5), c(0xFFE57058), c(0xFF34495E), c(0xFF9CB59A), c(0xFFE2B33C), c(0xFFF1A7C1), c(0xFFB0B5BA))
val SKIN_TONES = intArrayOf(c(0xFFF1C27D), c(0xFFE0AC69), c(0xFFC68642), c(0xFF8D5524), c(0xFFFFDBAC), c(0xFF5C3A21))
val SHIRT_COLORS = intArrayOf(c(0xFF2F80ED), c(0xFFEB5757), c(0xFF27AE60), c(0xFFF2C94C), c(0xFF9B51E0), c(0xFF333333), c(0xFFF2994A), c(0xFFE0E0E0))

fun itemDims(def: FurnitureDef, rot: Int): Pair<Int, Int> = if (rot % 2 == 0) def.w to def.d else def.d to def.w

fun itemForTag(tag: String): String = when (tag) {
    "register" -> "cash_register"
    "coffee" -> "coffee_machine"
    "pastry" -> "pastry_case"
    "workout" -> "treadmill"
    "locker" -> "gym_locker"
    "clothing" -> "clothing_rack"
    "books" -> "bookshelf"
    "electronics" -> "electronics_table"
    "jewelry" -> "jewelry_case"
    "seating" -> "table_set"
    else -> tag
}

class InteriorScene(val e: GameEngine, val building: Building, val mode: InteriorMode) {
    val business: Business? = if (mode == InteriorMode.BUSINESS) e.state.businessAt(building.id) else null
    val w: Int
    val d: Int
    val doorX: Int
    private val generated = ArrayList<PlacedItem>()
    /** Vehicles on display in a dealership: (vehicle def id, x, y). */
    val showroom = ArrayList<Triple<String, Float, Float>>()
    var showWalls = true
    var px = 0f
    var py = 0f
    private var ppath = IntArray(0)
    private var ppathIdx = 0
    val visitors = ArrayList<Person>()
    val staff = ArrayList<Person>()
    private var spawnAcc = 0f
    var ghostId: String? = null
    var ghostX = 0
    var ghostY = 0
    var ghostRot = 0
    /** Index in [items] of the item being moved, -1 when placing a new one. */
    var movingIndex = -1
    var selectedIndex = -1
    var yaw = Math.toRadians(-35.0).toFloat()
    var zoom = 1f
    private val placeKind: PlaceKind? = building.placeId?.let { Catalog.place(it).kind }

    init {
        when (mode) {
            InteriorMode.HOME -> { w = building.gridW.coerceAtLeast(7); d = building.gridD.coerceAtLeast(6) }
            InteriorMode.PLACE, InteriorMode.RIVAL -> { w = building.gridW.coerceIn(10, 20); d = building.gridD.coerceIn(8, 14) }
            else -> { w = building.gridW; d = building.gridD }
        }
        doorX = w / 2
        px = doorX.toFloat()
        py = d - .5f
        if (mode == InteriorMode.PLACE || mode == InteriorMode.RIVAL) generateLayout()
    }

    val items: MutableList<PlacedItem>
        get() = when (mode) {
            InteriorMode.BUSINESS -> business!!.furniture
            InteriorMode.HOME -> e.state.homeFurniture
            else -> generated
        }

    val floorColor: Int
        get() = when (mode) {
            InteriorMode.BUSINESS -> FLOOR_COLORS[business!!.floorColor % FLOOR_COLORS.size]
            InteriorMode.HOME -> FLOOR_COLORS[0]
            InteriorMode.RIVAL -> FLOOR_COLORS[(building.id * 7) % FLOOR_COLORS.size]
            InteriorMode.PLACE -> if (placeKind == PlaceKind.WHOLESALER || placeKind == PlaceKind.IMPORTER || placeKind == PlaceKind.CAR_DEALER) FLOOR_COLORS[4] else FLOOR_COLORS[1]
            InteriorMode.EMPTY -> FLOOR_COLORS[4]
        }

    val wallColor: Int
        get() = when (mode) {
            InteriorMode.BUSINESS -> WALL_COLORS[business!!.wallColor % WALL_COLORS.size]
            InteriorMode.HOME -> WALL_COLORS[0]
            InteriorMode.RIVAL -> WALL_COLORS[(building.id * 3) % WALL_COLORS.size]
            InteriorMode.PLACE -> building.placeId?.let { Catalog.place(it).color } ?: WALL_COLORS[1]
            InteriorMode.EMPTY -> WALL_COLORS[1]
        }

    fun isDoorTile(x: Int, y: Int) = y == d - 1 && (x == doorX || x == doorX - 1)

    fun occupancy(ignore: Int = -1): BooleanArray {
        val occ = BooleanArray(w * d)
        for ((i, p) in items.withIndex()) {
            if (i == ignore) continue
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            for (yy in p.y until p.y + fd) for (xx in p.x until p.x + fw) if (xx in 0 until w && yy in 0 until d) occ[yy * w + xx] = true
        }
        return occ
    }

    fun canPlace(itemId: String, x: Int, y: Int, rot: Int, ignore: Int = -1): Boolean {
        val def = Catalog.item(itemId) ?: return false
        val (fw, fd) = itemDims(def, rot)
        if (x < 0 || y < 0 || x + fw > w || y + fd > d) return false
        val occ = occupancy(ignore)
        for (yy in y until y + fd) for (xx in x until x + fw) {
            if (occ[yy * w + xx] || isDoorTile(xx, yy)) return false
        }
        return true
    }

    fun itemAt(x: Int, y: Int): Int {
        for ((i, p) in items.withIndex()) {
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            if (x in p.x until p.x + fw && y in p.y until p.y + fd) return i
        }
        return -1
    }

    /** Free tiles directly in front of an item (where customers stand). */
    fun frontTiles(p: PlacedItem, occ: BooleanArray, behind: Boolean = false): List<Int> {
        val def = Catalog.item(p.itemId) ?: return emptyList()
        val (fw, fd) = itemDims(def, p.rot)
        val r = if (behind) (p.rot + 2) % 4 else p.rot
        val out = ArrayList<Int>()
        when (r) {
            0 -> for (xx in p.x until p.x + fw) out += idx(xx, p.y + fd)
            1 -> for (yy in p.y until p.y + fd) out += idx(p.x - 1, yy)
            2 -> for (xx in p.x until p.x + fw) out += idx(xx, p.y - 1)
            else -> for (yy in p.y until p.y + fd) out += idx(p.x + fw, yy)
        }
        return out.filter { it >= 0 && !occ[it] }
    }

    private fun idx(x: Int, y: Int) = if (x in 0 until w && y in 0 until d) y * w + x else -1

    private fun bfs(from: Int, to: Int, occ: BooleanArray): IntArray? {
        if (from < 0 || to < 0) return null
        if (from == to) return intArrayOf(to)
        val prev = IntArray(w * d) { -1 }
        val queue = IntArray(w * d)
        var head = 0; var tail = 0
        queue[tail++] = from
        prev[from] = from
        while (head < tail) {
            val cur = queue[head++]
            if (cur == to) break
            val cx = cur % w; val cy = cur / w
            for (k in 0 until 4) {
                val nx = cx + DX[k]; val ny = cy + DY[k]
                if (nx !in 0 until w || ny !in 0 until d) continue
                val ni = ny * w + nx
                if (prev[ni] != -1 || (occ[ni] && ni != to)) continue
                prev[ni] = cur
                queue[tail++] = ni
            }
        }
        if (prev[to] == -1) return null
        val path = ArrayList<Int>()
        var cur = to
        while (cur != from) { path += cur; cur = prev[cur] }
        path.reverse()
        return path.toIntArray()
    }

    private fun registers(): List<PlacedItem> = items.filter { Catalog.item(it.itemId)?.fn == ItemFn.CASH_REGISTER }

    private fun browseTargets(): List<PlacedItem> = items.filter {
        val fn = Catalog.item(it.itemId)?.fn
        fn == ItemFn.DISPLAY || fn == ItemFn.EQUIPMENT || fn == ItemFn.SEATING || fn == ItemFn.SHOWER || fn == ItemFn.LOCKER || fn == ItemFn.OFFICE
    }

    fun walkPlayerTo(tx: Int, ty: Int) {
        if (e.state.player.workingAt >= 0) return
        val occ = occupancy()
        val start = idx(floor(px).toInt().coerceIn(0, w - 1), floor(py).toInt().coerceIn(0, d - 1))
        val path = bfs(start, idx(tx, ty), occ) ?: return
        ppath = path; ppathIdx = 0
    }

    /** Where the player stands when working the floor. */
    fun workSpot(): Pair<Float, Float>? {
        val occ = occupancy()
        val reg = registers().firstOrNull()
        if (reg != null) {
            val t = frontTiles(reg, occ, behind = true).firstOrNull() ?: frontTiles(reg, occ).firstOrNull()
            if (t != null) return (t % w + .5f) to (t / w + .5f)
        }
        val any = browseTargets().firstOrNull() ?: return null
        val t = frontTiles(any, occ).firstOrNull() ?: return null
        return (t % w + .5f) to (t / w + .5f)
    }

    fun update(dt: Float, simRunning: Boolean) {
        val step = if (simRunning) dt else 0f
        // player walking
        if (e.state.player.workingAt >= 0 && business != null && e.state.player.workingAt == business.id) {
            workSpot()?.let { (sx, sy) -> px = sx; py = sy }
            ppath = IntArray(0)
        } else if (ppathIdx < ppath.size) {
            val t = ppath[ppathIdx]
            val tx = t % w + .5f; val ty = t / w + .5f
            if (moveToward(tx, ty, 3.2f * dt, true)) ppathIdx++
        }
        // customers
        val rate = when (mode) {
            InteriorMode.BUSINESS -> e.economy.servedRate[business!!.id] ?: 0.0
            InteriorMode.RIVAL -> 22.0
            InteriorMode.PLACE -> if (placeKind == PlaceKind.HOSPITAL) 8.0 else 14.0
            else -> 0.0
        }
        spawnAcc += (rate / 60.0 * e.speedMultiplier().coerceAtLeast(if (simRunning) 1f else 0f) * step).toFloat()
        if (spawnAcc >= 1f) {
            spawnAcc = 0f
            if (visitors.size < 14) spawnVisitor()
        }
        val occ = occupancy()
        for (v in visitors) stepVisitor(v, step, occ)
        visitors.removeAll { it.done }
        updateStaff()
    }

    private fun moveToward(tx: Float, ty: Float, maxStep: Float, isPlayer: Boolean, person: Person? = null): Boolean {
        val cx = if (isPlayer) px else person!!.x
        val cy = if (isPlayer) py else person!!.y
        val dx = tx - cx; val dy = ty - cy
        val dd = sqrt(dx * dx + dy * dy)
        val nx: Float; val ny: Float
        val arrived = dd <= maxStep
        if (arrived) { nx = tx; ny = ty } else { nx = cx + dx / dd * maxStep; ny = cy + dy / dd * maxStep }
        if (isPlayer) { px = nx; py = ny } else { person!!.x = nx; person.y = ny; person.phase += maxStep * 4f }
        return arrived
    }

    private fun spawnVisitor() {
        val occ = occupancy()
        val targets = browseTargets()
        if (targets.isEmpty()) return
        val target = targets[e.rnd.nextInt(targets.size)]
        val fronts = frontTiles(target, occ)
        if (fronts.isEmpty()) return
        val start = idx(doorX, d - 1)
        val path = bfs(start, fronts[e.rnd.nextInt(fronts.size)], occ) ?: return
        val p = Person(doorX.toFloat(), d + .2f, SHIRT_COLORS[e.rnd.nextInt(SHIRT_COLORS.size)], SKIN_TONES[e.rnd.nextInt(SKIN_TONES.size)])
        p.path = path
        visitors += p
    }

    private fun stepVisitor(v: Person, dt: Float, occ: BooleanArray) {
        if (v.wait > 0f) { v.wait -= dt; return }
        if (v.pathIdx < v.path.size) {
            val t = v.path[v.pathIdx]
            if (moveToward(t % w + .5f, t / w + .5f, 1.7f * dt, false, v)) v.pathIdx++
            return
        }
        val here = idx(floor(v.x).toInt().coerceIn(0, w - 1), floor(v.y).toInt().coerceIn(0, d - 1))
        when (v.stage) {
            0 -> {
                v.wait = if (business?.typeId == "gym" || business?.typeId == "hairdresser") 6f + e.rnd.nextFloat() * 4f else 1.5f + e.rnd.nextFloat() * 2.5f
                v.stage = 1
                val reg = registers().firstOrNull()
                val goal = reg?.let { frontTiles(it, occ).firstOrNull() } ?: idx(doorX, d - 1)
                v.path = bfs(here, goal, occ) ?: IntArray(0); v.pathIdx = 0
            }
            1 -> {
                v.wait = 1f
                v.stage = 2
                v.path = bfs(here, idx(doorX, d - 1), occ) ?: IntArray(0); v.pathIdx = 0
            }
            2 -> {
                if (moveToward(doorX.toFloat(), d + .6f, 1.7f * dt, false, v)) v.done = true
            }
        }
    }

    private fun updateStaff() {
        staff.clear()
        val s = e.state
        when (mode) {
            InteriorMode.BUSINESS -> {
                val b = business!!
                val occ = occupancy()
                val regs = registers()
                var regIdx = 0
                val working = s.employees.filter { it.businessId == b.id && it.worksAt(s.hour, s.weekday) }
                for ((n, emp) in working.withIndex()) {
                    val spot: Int? = when (emp.role) {
                        StaffRole.CUSTOMER_SERVICE -> regs.getOrNull(regIdx++)?.let { frontTiles(it, occ, true).firstOrNull() }
                        StaffRole.CLEANER -> null
                        else -> browseTargets().getOrNull(n)?.let { frontTiles(it, occ).lastOrNull() }
                    }
                    val tile = spot ?: freeTile(occ, n * 7 + 3)
                    if (tile >= 0) staff += Person(tile % w + .5f, tile / w + .5f, SHIRT_COLORS[(emp.id) % SHIRT_COLORS.size], SKIN_TONES[emp.id % SKIN_TONES.size])
                }
            }
            InteriorMode.RIVAL, InteriorMode.PLACE -> {
                val occ = occupancy()
                registers().firstOrNull()?.let { reg ->
                    frontTiles(reg, occ, true).firstOrNull()?.let { t -> staff += Person(t % w + .5f, t / w + .5f, c(0xFF2C3E50), SKIN_TONES[building.id % SKIN_TONES.size]) }
                }
            }
            else -> {}
        }
    }

    private fun freeTile(occ: BooleanArray, seed: Int): Int {
        for (k in 0 until w * d) {
            val i = (seed * 31 + k * 17) % (w * d)
            if (!occ[i] && !isDoorTile(i % w, i / w)) return i
        }
        return -1
    }

    // ------------------------------------------------------------------ designer

    fun startPlacing(itemId: String) {
        ghostId = itemId
        movingIndex = -1
        selectedIndex = -1
        ghostRot = 0
        val spot = findSpot(itemId)
        ghostX = spot?.first ?: 0
        ghostY = spot?.second ?: 0
    }

    fun startMoving(index: Int) {
        val p = items.getOrNull(index) ?: return
        ghostId = p.itemId
        movingIndex = index
        ghostX = p.x; ghostY = p.y; ghostRot = p.rot
        selectedIndex = -1
    }

    fun findSpot(itemId: String, rot: Int = 0): Pair<Int, Int>? {
        for (y in 0 until d) for (x in 0 until w) if (canPlace(itemId, x, y, rot)) return x to y
        return null
    }

    val ghostValid: Boolean get() = ghostId?.let { canPlace(it, ghostX, ghostY, ghostRot, movingIndex) } ?: false

    fun moveGhostTo(tx: Int, ty: Int) {
        val def = Catalog.item(ghostId ?: return) ?: return
        val (fw, fd) = itemDims(def, ghostRot)
        ghostX = (tx - fw / 2).coerceIn(0, (w - fw).coerceAtLeast(0))
        ghostY = (ty - fd / 2).coerceIn(0, (d - fd).coerceAtLeast(0))
    }

    fun rotateGhost() {
        val def = Catalog.item(ghostId ?: return) ?: return
        ghostRot = (ghostRot + 1) % 4
        val (fw, fd) = itemDims(def, ghostRot)
        ghostX = ghostX.coerceIn(0, (w - fw).coerceAtLeast(0))
        ghostY = ghostY.coerceIn(0, (d - fd).coerceAtLeast(0))
    }

    // ------------------------------------------------------------------ generated layouts

    private fun put(id: String, x: Int, y: Int, rot: Int = 0): Boolean {
        if (!canPlace(id, x, y, rot)) return false
        generated += PlacedItem(id, x, y, rot)
        return true
    }

    private fun generateLayout() {
        if (mode == InteriorMode.RIVAL) {
            val rb = e.state.rivalBusinessAt(building.id) ?: return
            val type = Catalog.type(rb.typeId)
            val tags = LinkedHashSet<String>()
            type.requirements.forEach { tags += it.tag }
            type.products.forEach { Catalog.product(it.first)?.let { p -> tags += p.tag } }
            tags.remove("register")
            if (type.needsRegister) put("cash_register", w - 4, d - 3)
            var x = 1; var y = 1
            val list = tags.flatMap { t -> List(if (t == "workout") 3 else 2) { itemForTag(t) } } + listOf("plant_large", "speaker_medium")
            for (id in list) {
                val def = Catalog.item(id) ?: continue
                if (x + def.w > w - 1) { x = 1; y += 3 }
                if (y + def.d > d - 3) break
                put(id, x, y)
                x += def.w + 1
            }
            if (type.id == "coffee" || type.id == "fastfood") { put("table_set", 1, d - 4); put("table_set", 4, d - 4) }
            put("plant_large", 0, d - 2)
            return
        }
        when (placeKind) {
            PlaceKind.FURNITURE_STORE -> {
                put("cash_register", w - 3, d - 3)
                val stock = Catalog.place(building.placeId!!).stock
                var x = 1; var y = 1; var rowDepth = 1
                for (id in stock) {
                    val def = Catalog.item(id) ?: continue
                    if (x + def.w > w - 1) { x = 1; y += rowDepth + 1; rowDepth = 1 }
                    if (y + def.d > d - 3) break
                    if (put(id, x, y)) { x += def.w + 1; rowDepth = maxOf(rowDepth, def.d) }
                }
            }
            PlaceKind.WHOLESALER, PlaceKind.IMPORTER -> {
                put("cash_register", w - 3, d - 3)
                var y = 1
                while (y + 1 < d - 3) {
                    var x = 1
                    while (x + 2 <= w - 1) {
                        put(if ((x + y) % 3 == 0) "shelf" else "pallet", x, y)
                        x += if ((x + y) % 3 == 0) 2 else 1
                    }
                    y += 3
                }
            }
            PlaceKind.CAR_DEALER -> {
                put("desk", w - 4, 1)
                put("plant_large", w - 1, 0)
                put("plant_large", 0, 0)
                val defs = Catalog.vehicles
                for ((i, v) in defs.withIndex()) {
                    val col = i % 3; val row = i / 3
                    showroom += Triple(v.id, 2.5f + col * 5f, 3f + row * 5f)
                }
            }
            else -> {
                put("cash_register", doorX - 1, 2)
                put("plant_large", 0, 0)
                put("plant_large", w - 1, 0)
                var x = 1
                while (x + 2 <= w - 1) {
                    if (abs(x - doorX) > 2) put("desk", x, d / 2)
                    x += 4
                }
                put("bench", 1, d - 2)
                put("bench", w - 3, d - 2)
                put("painting", 3, 0)
                put("wall_lamp", w - 4, 0)
            }
        }
    }

    companion object {
        private val DX = intArrayOf(1, -1, 0, 0)
        private val DY = intArrayOf(0, 0, 1, -1)
    }
}

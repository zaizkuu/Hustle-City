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
    /** Waypoints (tile indices) of the current walk, already smoothed into straight runs. */
    var path: IntArray = IntArray(0)
    var pathIdx = 0
    var stage = 0
    var wait = 0f
    var done = false
    var phase = 0f
    /** Tiles still to visit before paying (displays, machines, seats). */
    var stops: IntArray = IntArray(0)
    var stopIdx = 0
    var speed = 1.6f
    /** Counter whose queue this customer stands in (index into the scene's items), -1 when not queuing. */
    var counter = -1
    var goal = -1
    var blockedFor = 0f
    /** Seconds left of ignoring the crowd after being stuck too long. */
    var ghost = 0f
    /** Tags of the seats this customer will use after paying ("" = none). */
    var seatTag = ""
    /** Tile this customer has reserved (display spot or seat), so two people never share one. */
    var claim = -1
    var retries = 0
    // 3D look and pose
    var heading = 0f
    var moving = 0f
    var pose = 0
    var z = 0f
    var pants = 0
    var hair = 0
    /** Appearance seed for the HD renderer's people (see [com.hustlecity.game.render.Look]). */
    var look = 1
    /** Seat or machine this person will use at the current stop. */
    var seat: SeatPos? = null
    var seated = false
    /** Item index being used (to face it). */
    var item = -1
    /** What a customer has picked up: a product color (0 = nothing yet) shown in their hand once [heldIn] runs out. */
    var held = 0
    var heldCount = 0
    var heldIn = 0f
    /** True once they've paid: the goods go into a paper bag. */
    var bagged = false
    /** Staff carrying a box to a display ([com.hustlecity.game.render.PersonPainter] carry pose 1). */
    var carry = 0
}

/** A place to sit or exercise on a piece of furniture, in room coordinates. */
class SeatPos(val tile: Int, val x: Float, val y: Float, val z: Float, val heading: Float, val pose: Int)

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
    "desk" -> "office_desk"
    "bar" -> "cocktail_bar"
    "screen" -> "cinema_screen"
    "cinema_seat" -> "cinema_seats"
    "concession" -> "concession_stand"
    "tablet" -> "graphic_tablet"
    "globe" -> "desk_globe"
    "calendar" -> "desk_calendar"
    "ice_cream" -> "ice_cream_freezer"
    "shoes" -> "shoe_rack"
    "magazines" -> "magazine_rack"
    "security_cam" -> "security_camera"
    "baskets" -> "shopping_baskets"
    "trash" -> "trash_can"
    "scale" -> "digital_scale"
    "meeting" -> "meeting_table"
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
    var playerHeading = 0f
    var playerMoving = 0f
    private var ppath = IntArray(0)
    private var ppathIdx = 0
    val visitors = ArrayList<Person>()
    val staff = ArrayList<Person>()
    private var spawnAcc = 0f
    private var lastCash = -10f
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

    /** Walk-in store: displays (index into [items]) and the furniture each one sells. */
    val forSale = HashMap<Int, String>()
    /** The store's own register and its rack of free carts, -1 if none. */
    var registerIndex = -1
        private set
    var rackIndex = -1
        private set
    /** Item the player is looking at (its card is shown), -1 for none. */
    var inspect = -1
    /** Runs once the player's current walk ends. */
    private var onArrive: (() -> Unit)? = null
    val walkIn: Boolean get() = mode == InteriorMode.PLACE && placeKind == PlaceKind.FURNITURE_STORE

    /** The staff-only back room of your store, or null for an open floor. */
    var backRoom: BackRoom? = null
        private set

    /** What your store's displays visibly hold (null outside your own businesses). */
    val shelf: ShelfStock? = business?.let { ShelfStock(this, it) }

    /** True while the player carries a box from the back room to a display (drawn in their arms). */
    var playerBox = false

    init {
        when {
            mode == InteriorMode.HOME -> { w = building.gridW.coerceAtLeast(7); d = building.gridD.coerceAtLeast(6) }
            mode == InteriorMode.PLACE && placeKind == PlaceKind.FURNITURE_STORE -> {
                val stock = Catalog.place(building.placeId!!).stock
                w = 20
                d = (12..30).firstOrNull { showroom(stock, w, it) != null } ?: 30
            }
            mode == InteriorMode.PLACE || mode == InteriorMode.RIVAL -> { w = building.gridW.coerceIn(10, 20); d = building.gridD.coerceIn(8, 14) }
            else -> { w = building.gridW; d = building.gridD }
        }
        doorX = w / 2
        px = doorX.toFloat()
        py = d - .5f
        // big showrooms start a little closer, following you around
        if (walkIn) zoom = 1.35f
        if (mode == InteriorMode.PLACE || mode == InteriorMode.RIVAL) generateLayout()
        business?.let { b ->
            BackRoom.resolve(b, w, d)
            backRoom = BackRoom.of(b, w, d)
        }
        shelf?.onRestock = { item, source -> sendRunner(item, source) }
    }

    /** True for tiles of the back room (customers never go there). */
    fun inBackRoom(x: Int, y: Int) = backRoom?.contains(y) == true && x in 0 until w && y >= 0

    /**
     * Builds or removes the back room wall (Interior designer). Building it needs the back of the store free of
     * displays, registers and seats; returns false (with the reason in a toast) when it can't be done.
     */
    fun setBackRoom(on: Boolean): Boolean {
        val b = business ?: return false
        if (!on) { b.backRoom = 0; b.backDoor = -1; backRoom = null; return true }
        if (!BackRoom.eligible(b)) { e.toast("${b.type.name}s don't have a back room"); return false }
        if (BackRoom.preferredDepth(w, d) < 3) { e.toast("This store isn't deep enough for a back room"); return false }
        val fit = BackRoom.fit(items, w, d)
        if (fit == null) { e.toast("Move displays, registers and seats away from the back wall first", ToastKind.BAD); return false }
        b.backRoom = fit.first; b.backDoor = fit.second
        backRoom = BackRoom.of(b, w, d)
        return true
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
        if (backRoom?.problem(def, x, y, fw, fd, rot) != null) return false
        val occ = occupancy(ignore)
        for (yy in y until y + fd) for (xx in x until x + fw) {
            if (occ[yy * w + xx] || isDoorTile(xx, yy)) return false
        }
        return true
    }

    /** Why an item can't go here because of the back room (customer furniture inside it, the wall, the doorway), or null. */
    fun backRoomProblem(itemId: String, x: Int, y: Int, rot: Int): String? {
        val room = backRoom ?: return null
        val def = Catalog.item(itemId) ?: return null
        val (fw, fd) = itemDims(def, rot)
        return room.problem(def, x, y, fw, fd, rot)
    }

    /** Why the item being placed can't go where it is, or null when it fits. */
    val ghostProblem: String?
        get() {
            val id = ghostId ?: return null
            if (ghostValid) return null
            return backRoomProblem(id, ghostX, ghostY, ghostRot) ?: "Blocked"
        }

    /** A friendly hint about where the item being placed belongs (it can still go here), or null. */
    val ghostHint: String?
        get() {
            val room = backRoom ?: return null
            val def = Catalog.item(ghostId ?: return null) ?: return null
            if (!ghostValid) return null
            val inside = room.contains(ghostY)
            return when {
                def.fn == ItemFn.STORAGE && !inside -> "Storage shelves belong in the back room, out of the customers' way"
                def.fn == ItemFn.CLEANING && !inside -> "Cleaning stations belong in the back room: cleaners wait there between jobs"
                inside -> "In the back room: staff only"
                else -> null
            }
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
        val wall = backRoom
        while (head < tail) {
            val cur = queue[head++]
            if (cur == to) break
            val cx = cur % w; val cy = cur / w
            for (k in 0 until 4) {
                val nx = cx + DX[k]; val ny = cy + DY[k]
                if (nx !in 0 until w || ny !in 0 until d) continue
                if (wall != null && wall.blocks(cx, cy, nx, ny)) continue
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
        onArrive = null
        val hit = itemAt(tx, ty)
        if (hit >= 0) { walkToItem(hit); return }
        val occ = occupancy()
        val start = idx(floor(px).toInt().coerceIn(0, w - 1), floor(py).toInt().coerceIn(0, d - 1))
        val path = bfs(start, idx(tx, ty), occ) ?: return
        ppath = path; ppathIdx = 0
    }

    /** Walks up to an item (the nearest free tile next to it), then runs [then]. */
    fun walkToItem(index: Int, then: (() -> Unit)? = null) {
        val p = items.getOrNull(index) ?: return
        val def = Catalog.item(p.itemId) ?: return
        val (fw, fd) = itemDims(def, p.rot)
        val occ = occupancy()
        val start = idx(floor(px).toInt().coerceIn(0, w - 1), floor(py).toInt().coerceIn(0, d - 1))
        // breadth-first from the player to any free tile touching the item's footprint
        val prev = IntArray(w * d) { -1 }
        val queue = IntArray(w * d)
        var head = 0; var tail = 0
        if (start < 0) return
        queue[tail++] = start; prev[start] = start
        var goal = -1
        while (head < tail) {
            val cur = queue[head++]
            val cx = cur % w; val cy = cur / w
            val touches = (cx in p.x - 1..p.x + fw && cy in p.y until p.y + fd) || (cy in p.y - 1..p.y + fd && cx in p.x until p.x + fw)
            // the tile must be on the item's side of the back room wall
            val sameSide = backRoom?.let { it.contains(cy) == it.contains(p.y) } ?: true
            if (touches && sameSide && !occ[cur]) { goal = cur; break }
            for (k in 0 until 4) {
                val nx = cx + DX[k]; val ny = cy + DY[k]
                if (nx !in 0 until w || ny !in 0 until d) continue
                if (backRoom?.blocks(cx, cy, nx, ny) == true) continue
                val ni = ny * w + nx
                if (prev[ni] != -1 || occ[ni]) continue
                prev[ni] = cur
                queue[tail++] = ni
            }
        }
        if (goal < 0) return
        val path = ArrayList<Int>()
        var cur = goal
        while (cur != start) { path += cur; cur = prev[cur] }
        path.reverse()
        ppath = path.toIntArray(); ppathIdx = 0
        onArrive = {
            // face the item
            playerHeading = kotlin.math.atan2(p.x + fw / 2f - px, -(p.y + fd / 2f - py))
            then?.invoke()
        }
        // already there: run it now (it may start the next walk, so clear the hook first)
        if (ppath.isEmpty()) { val f = onArrive; onArrive = null; f?.invoke() }
    }

    /** Index of the storage shelf nearest the player, or -1 when there is none. */
    fun storageShelfFor(): Int {
        var best = -1; var bestD = Float.MAX_VALUE
        for ((i, p) in items.withIndex()) {
            if (Catalog.item(p.itemId)?.fn != ItemFn.STORAGE) continue
            val dd = abs(p.x + .5f - px) + abs(p.y + .5f - py)
            if (dd < bestD) { bestD = dd; best = i }
        }
        return best
    }

    /** True when the player stands next to item [index] (close enough to pick it up or pay). */
    fun playerNear(index: Int): Boolean {
        val p = items.getOrNull(index) ?: return false
        val def = Catalog.item(p.itemId) ?: return false
        val (fw, fd) = itemDims(def, p.rot)
        val dx = maxOf(p.x - px, 0f, px - (p.x + fw))
        val dy = maxOf(p.y - py, 0f, py - (p.y + fd))
        return dx * dx + dy * dy < 1.3f * 1.3f
    }

    val playerWalking: Boolean get() = ppathIdx < ppath.size

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
            if (moveToward(tx, ty, 3.2f * dt, true)) {
                ppathIdx++
                if (ppathIdx >= ppath.size) onArrive?.let { onArrive = null; it() }
            }
        }
        // customers
        val rate = when (mode) {
            InteriorMode.BUSINESS -> e.economy.servedRate[business!!.id] ?: 0.0
            InteriorMode.RIVAL -> e.state.rivalBusinessAt(building.id)?.let { rb -> (30.0 + 25.0 * rb.level) * Catalog.type(rb.typeId).curve[e.state.hour] } ?: 0.0
            InteriorMode.PLACE -> if (placeKind == PlaceKind.HOSPITAL) 20.0 else 40.0
            else -> 0.0
        }
        // a quarter of the real customers walk in visibly; the rest are served off-screen
        spawnAcc += (rate / 60.0 * .25 * e.speedMultiplier().coerceAtLeast(if (simRunning) 1f else 0f) * step).toFloat()
        if (spawnAcc >= 1f) {
            spawnAcc = 0f
            if (visitors.size < 16) spawnVisitor()
        }
        val staffOcc = walkOcc()
        val occ = customerOcc(staffOcc)
        refreshQueues(occ)
        playerMoving *= .7f
        for (v in visitors) v.moving *= .7f
        for (v in staff) v.moving *= .7f
        for (v in visitors) stepVisitor(v, step, occ)
        for (v in visitors) if (v.done) leaveQueue(v)
        visitors.removeAll { it.done }
        updateStaff(step, staffOcc)
        shelf?.update(dt)
    }

    private fun moveToward(tx: Float, ty: Float, maxStep: Float, isPlayer: Boolean, person: Person? = null): Boolean {
        val cx = if (isPlayer) px else person!!.x
        val cy = if (isPlayer) py else person!!.y
        val dx = tx - cx; val dy = ty - cy
        val dd = sqrt(dx * dx + dy * dy)
        val nx: Float; val ny: Float
        val arrived = dd <= maxStep
        if (arrived) { nx = tx; ny = ty } else { nx = cx + dx / dd * maxStep; ny = cy + dy / dd * maxStep }
        if (isPlayer) {
            if (dd > 1e-3f) { playerHeading = kotlin.math.atan2(dx, -dy); playerMoving = 1f }
            px = nx; py = ny
        } else {
            if (dd > 1e-3f) { person!!.heading = kotlin.math.atan2(dx, -dy); person.moving = 1f }
            person!!.x = nx; person.y = ny; person.phase += maxStep * 4f
        }
        return arrived
    }

    // ------------------------------------------------------------------ customers

    /** Longest queue right now (for tests and the HUD). */
    val longestQueue get() = queues.values.maxOfOrNull { it.size } ?: 0

    /** What kind of place this is, for customer routines. */
    private val typeId: String? = when (mode) {
        InteriorMode.BUSINESS -> business?.typeId
        InteriorMode.RIVAL -> e.state.rivalBusinessAt(building.id)?.typeId
        else -> null
    }

    /** Occupancy for walking: like [occupancy], but people can walk onto a dance floor. */
    private fun walkOcc(): BooleanArray {
        val occ = BooleanArray(w * d)
        for (p in items) {
            val def = Catalog.item(p.itemId) ?: continue
            if ("dance_floor" in def.tags) continue
            val (fw, fd) = itemDims(def, p.rot)
            for (yy in p.y until p.y + fd) for (xx in p.x until p.x + fw) if (xx in 0 until w && yy in 0 until d) occ[yy * w + xx] = true
        }
        return occ
    }

    private var custOcc = BooleanArray(0)

    /** Where customers may walk: [walkOcc] with the staff-only back room closed off. */
    private fun customerOcc(base: BooleanArray): BooleanArray {
        val room = backRoom ?: return base
        if (custOcc.size != base.size) custOcc = BooleanArray(base.size)
        System.arraycopy(base, 0, custOcc, 0, base.size)
        for (i in 0 until (room.depth * w).coerceAtMost(base.size)) custOcc[i] = true
        return custOcc
    }

    private fun itemsWith(tag: String) = items.withIndex().filter { Catalog.item(it.value.itemId)?.tags?.contains(tag) == true }

    private fun frontTile(p: PlacedItem, occ: BooleanArray): Int = frontTiles(p, occ).let { if (it.isEmpty()) -1 else it[e.rnd.nextInt(it.size)] }

    /** A random item (index) carrying one of these tags, -1 if there is none. */
    private fun stopAt(vararg tags: String): Int {
        val cands = items.withIndex().filter { (_, p) -> !inBackRoom(p.x, p.y) && Catalog.item(p.itemId)?.tags?.any { it in tags } == true }
        return if (cands.isEmpty()) -1 else cands[e.rnd.nextInt(cands.size)].index
    }

    private fun claimed(except: Person): Set<Int> = visitors.mapNotNullTo(HashSet()) { if (it !== except && it.claim >= 0) it.claim else null }

    /** A free, unreserved spot to stand at an item: in front of it, or anywhere on a dance floor. */
    private fun spotAt(item: Int, occ: BooleanArray, v: Person): Int {
        val p = items.getOrNull(item) ?: return -1
        val def = Catalog.item(p.itemId) ?: return -1
        val taken = claimed(v)
        v.seat = null
        v.item = item
        val seats = seatsOf(item).filter { it.tile !in taken }
        if (Catalog.sitSpots.containsKey(def.id)) {
            if (seats.isEmpty()) return -1
            val sp = seats[e.rnd.nextInt(seats.size)]
            v.seat = sp
            return sp.tile
        }
        val tiles = if ("dance_floor" in def.tags) {
            val (fw, fd) = itemDims(def, p.rot)
            (0 until fw * fd).map { idx(p.x + it % fw, p.y + it / fw) }
        } else frontTiles(p, occ)
        // (not where the line for the register forms, and only where they can get to without going through anyone)
        val queuing = queueSlots.values.flatMapTo(HashSet()) { it.asIterable() }
        val open = reachable(v, occ)
        val free = tiles.filter { it >= 0 && it !in taken && it !in queuing && open[it] }
        return if (free.isEmpty()) -1 else free[e.rnd.nextInt(free.size)]
    }

    /** Seats/machines on an item, rotated into the room. */
    fun seatsOf(item: Int): List<SeatPos> {
        val p = items.getOrNull(item) ?: return emptyList()
        val def = Catalog.item(p.itemId) ?: return emptyList()
        val spots = Catalog.sitSpots[def.id] ?: return emptyList()
        val w0 = def.w.toFloat(); val d0 = def.d.toFloat()
        return spots.map { sp ->
            val (ax, ay) = when (p.rot % 4) {
                0 -> sp.x to sp.y
                1 -> (d0 - sp.y) to sp.x
                2 -> (w0 - sp.x) to (d0 - sp.y)
                else -> sp.y to (w0 - sp.x)
            }
            // facing direction, rotated the same way
            val lx = when (sp.face) { 1 -> 1f; 3 -> -1f; else -> 0f }
            val ly = when (sp.face) { 0 -> -1f; 2 -> 1f; else -> 0f }
            val (vx, vy) = when (p.rot % 4) { 0 -> lx to ly; 1 -> -ly to lx; 2 -> -lx to -ly; else -> ly to -lx }
            val wx = p.x + ax; val wy = p.y + ay
            SeatPos(idx(floor(wx).toInt().coerceIn(0, w - 1), floor(wy).toInt().coerceIn(0, d - 1)), wx, wy, sp.z, kotlin.math.atan2(vx, -vy), if (sp.pose == 3) 3 else 1)
        }
    }

    /** A free seat among every item with this tag. */
    private fun seatFor(tag: String, occ: BooleanArray, v: Person): Int {
        val cands = items.indices.filter { Catalog.item(items[it].itemId)?.tags?.contains(tag) == true }.shuffled(e.rnd)
        for (i in cands) { val t = spotAt(i, occ, v); if (t >= 0) return t }
        return -1
    }

    /** Service counters with a queue: cash registers, or the bar in a nightclub. */
    private fun counters(): List<Int> {
        // in a walk-in store, the registers on display are for sale: only the checkout serves
        if (registerIndex >= 0) return listOf(registerIndex)
        val regs = items.withIndex().filter { Catalog.item(it.value.itemId)?.fn == ItemFn.CASH_REGISTER }.map { it.index }
        if (regs.isNotEmpty()) return regs
        return if (typeId == "nightclub") itemsWith("bar").map { it.index } else emptyList()
    }

    private val queues = HashMap<Int, ArrayList<Person>>()
    private val queueSlots = HashMap<Int, IntArray>()

    /** Lines up the tiles in front of each counter: customers queue along them, first come first served. */
    private fun refreshQueues(occ: BooleanArray) {
        queueSlots.clear()
        for (ci in counters()) {
            val p = items.getOrNull(ci) ?: continue
            val first = frontTiles(p, occ).firstOrNull() ?: continue
            val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1), intArrayOf(1, 0))
            var dir = p.rot % 4
            val slots = arrayListOf(first)
            var cur = first
            while (slots.size < 7) {
                var next = -1
                for (turn in intArrayOf(0, 1, 3)) {
                    val dd = dirs[(dir + turn) % 4]
                    val nx = cur % w + dd[0]; val ny = cur / w + dd[1]
                    val ni = idx(nx, ny)
                    if (ni < 0 || occ[ni] || isDoorTile(nx, ny) || ni in slots) continue
                    next = ni; dir = (dir + turn) % 4; break
                }
                if (next < 0) break
                slots += next; cur = next
            }
            queueSlots[ci] = slots.toIntArray()
        }
        // forget counters that were removed or moved
        val gone = queues.keys.filter { it !in queueSlots }
        for (k in gone) queues.remove(k)?.forEach { it.counter = -1; it.stage = 3; it.goal = -1 }
    }

    private fun leaveQueue(v: Person) {
        if (v.counter >= 0) queues[v.counter]?.remove(v)
        v.counter = -1
    }

    private fun spawnVisitor() {
        val start = idx(doorX, d - 1)
        if (start < 0) return
        val stops = ArrayList<Int>()
        var seat = ""
        var pays = counters().isNotEmpty()
        val t = typeId
        val r = e.rnd
        when {
            t == "coffee" || t == "fastfood" -> if (r.nextFloat() < .6f) seat = "seating"
            t == "cinema" -> { if (r.nextFloat() < .5f) stops += stopAt("concession"); seat = "cinema_seat" }
            t == "theater" -> seat = "cinema_seat"
            t == "nightclub" -> seat = "dance_floor"
            t == "hairdresser" -> { if (r.nextFloat() < .5f) stops += stopAt("hair_wash"); stops += stopAt("hair_chair") }
            t == "gym" -> { stops += stopAt("locker"); repeat(1 + r.nextInt(2)) { stops += stopAt("workout") }; if (r.nextFloat() < .6f) stops += stopAt("shower"); pays = false }
            t != null && Catalog.type(t).office -> { stops += stopAt("desk"); pays = false } // a client meeting
            else -> {
                val targets = if (forSale.isNotEmpty()) forSale.keys.toList() else items.indices.filter { i ->
                    !inBackRoom(items[i].x, items[i].y) && Catalog.item(items[i].itemId)?.fn.let { it == ItemFn.DISPLAY || it == ItemFn.EQUIPMENT || it == ItemFn.OFFICE || it == ItemFn.LOCKER }
                }
                repeat(1 + r.nextInt(3)) { targets.randomOrNull(r)?.let { stops += it } }
            }
        }
        val list = stops.filter { it >= 0 }.distinct()
        if (list.isEmpty() && !pays && seat.isEmpty()) return
        val p = Person(doorX.toFloat(), d + .2f, SHIRT_COLORS[r.nextInt(SHIRT_COLORS.size)], SKIN_TONES[r.nextInt(SKIN_TONES.size)])
        p.speed = 1.35f + r.nextFloat() * .5f
        p.pants = r.nextInt(16)
        p.hair = r.nextInt(16)
        p.look = r.nextInt() or 1
        p.stops = list.toIntArray()
        p.seatTag = seat
        // food and tickets are paid for first; shops are paid on the way out
        val payFirst = t == "coffee" || t == "fastfood" || t == "cinema" || t == "theater" || t == "nightclub"
        p.stage = when {
            pays && payFirst -> 1
            list.isNotEmpty() -> 0
            pays -> 1
            else -> 4
        }
        if (!pays && p.stage == 1) p.stage = 0
        p.goal = -1
        visitors += p
    }

    /** Walks along the smoothed path; returns true when the end is reached. Customers wait for each other instead of overlapping. */
    private fun walk(v: Person, dt: Float, occ: BooleanArray): Boolean {
        if (v.pathIdx >= v.path.size) return true
        val t = v.path[v.pathIdx]
        val tx = t % w + .5f; val ty = t / w + .5f
        // someone right in front of us? step aside round them (to the right, else the left), or wait a moment; only
        // when there's no way round for a while squeeze past
        if (v.ghost > 0f) v.ghost -= dt
        else {
            val dx = tx - v.x; val dy = ty - v.y
            val dd = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
            val o = personAhead(v, dx / dd, dy / dd, .7f)
            if (o != null) {
                // one who's settled there since the way was planned (sat down, dancing, in the line): plan round them
                if (v.blockedFor == 0f && v.goal >= 0 && (o.wait > 0f || o.seated || o.stage == 2)) {
                    val goal = v.goal
                    goTo(v, goal, occ)
                    if (v.pathIdx < v.path.size) { v.blockedFor = dt; return false }
                }
                v.blockedFor += dt
                for (sd in floatArrayOf(1f, -1f)) {
                    val sx = v.x + (-dy / dd * sd * .55f + dx / dd * .25f); val sy = v.y + (dx / dd * sd * .55f + dy / dd * .25f)
                    if (free(sx, sy, v, occ)) { moveToward(sx, sy, v.speed * .8f * dt, false, v); return false }
                }
                if (v.blockedFor > 2.5f) { v.ghost = .9f; v.blockedFor = 0f }
                return false
            }
            v.blockedFor = 0f
        }
        if (moveToward(tx, ty, v.speed * dt, false, v)) v.pathIdx++
        return v.pathIdx >= v.path.size
    }

    /** Someone (customer or staff) within [reach] meters ahead of [v] along (fx, fy), or null. */
    private fun personAhead(v: Person, fx: Float, fy: Float, reach: Float): Person? {
        for (list in arrayOf(visitors, staff)) for (o in list) {
            if (o === v || o.done) continue
            val ox = o.x - v.x; val oy = o.y - v.y
            if (ox * ox + oy * oy > reach * reach) continue
            if (ox * fx + oy * fy > .15f) return o
        }
        return null
    }

    /** True when (x, y) is clear floor for [v] to step on: no furniture, the room's edge or back-room wall, nor anyone within a shoulder's width. */
    private fun free(x: Float, y: Float, v: Person, occ: BooleanArray): Boolean {
        val i = idx(floor(x).toInt(), floor(y).toInt())
        if (i < 0 || occ[i] || x < .25f || y < .25f || x > w - .25f || y > d - .25f) return false
        if (backRoom?.cuts(v.x, v.y, x, y) == true) return false
        for (list in arrayOf(visitors, staff)) for (o in list) {
            if (o === v || o.done) continue
            val ox = o.x - x; val oy = o.y - y
            if (ox * ox + oy * oy < .45f * .45f) return false
        }
        if ((px - x) * (px - x) + (py - y) * (py - y) < .45f * .45f) return false
        return true
    }

    /** [occ] with the tiles of everyone staying put (seated, dancing, at a display, in a line) closed, but [keep] and [keep2]. */
    private fun roundOthers(v: Person, occ: BooleanArray, keep: Int, keep2: Int = -1): BooleanArray {
        val round = occ.copyOf()
        for (o in visitors) {
            if (o === v || o.done || (o.wait <= 0f && !o.seated && o.stage != 2)) continue
            val t = idx(floor(o.x).toInt(), floor(o.y).toInt())
            if (t >= 0 && t != keep && t != keep2) round[t] = true
        }
        return round
    }

    /** Which tiles [v] can get to from where they stand without going through anyone staying put. */
    private fun reachable(v: Person, occ: BooleanArray): BooleanArray {
        val here = idx(floor(v.x).toInt().coerceIn(0, w - 1), floor(v.y).toInt().coerceIn(0, d - 1))
        val round = roundOthers(v, occ, here)
        val seen = BooleanArray(w * d)
        if (here < 0) return seen
        val queue = IntArray(w * d)
        var head = 0; var tail = 0
        queue[tail++] = here; seen[here] = true
        val wall = backRoom
        while (head < tail) {
            val cur = queue[head++]
            val cx = cur % w; val cy = cur / w
            for (k in 0 until 4) {
                val nx = cx + DX[k]; val ny = cy + DY[k]
                if (nx !in 0 until w || ny !in 0 until d) continue
                if (wall != null && wall.blocks(cx, cy, nx, ny)) continue
                val ni = ny * w + nx
                if (seen[ni]) continue
                seen[ni] = true
                // a spot taken by someone can be reached, but not walked through
                if (!round[ni]) queue[tail++] = ni
            }
        }
        return seen
    }

    private fun goTo(v: Person, goal: Int, occ: BooleanArray) {
        val here = idx(floor(v.x).toInt().coerceIn(0, w - 1), floor(v.y).toInt().coerceIn(0, d - 1))
        v.goal = goal
        // the way round anyone sitting, dancing, standing at a display or in the line for the register, if there is one
        val round = roundOthers(v, occ, here, goal)
        val way = if (goal < 0) null else bfs(here, goal, round)?.let { smooth(here, it, round) }
        v.path = if (goal < 0) IntArray(0) else way ?: smooth(here, bfs(here, goal, occ) ?: intArrayOf(goal), occ)
        v.pathIdx = 0
    }

    /** Cuts a tile-by-tile path into straight runs wherever the way is clear, so people walk diagonally and naturally. */
    private fun smooth(from: Int, path: IntArray, occ: BooleanArray): IntArray {
        if (path.size <= 2) return path
        val out = ArrayList<Int>()
        var cur = from
        var i = 0
        while (i < path.size) {
            var j = path.size - 1
            while (j > i && !clear(cur, path[j], occ)) j--
            out += path[j]
            cur = path[j]
            i = j + 1
        }
        return out.toIntArray()
    }

    /** True when a person can walk in a straight line between two tile centers without brushing furniture. */
    private fun clear(a: Int, b: Int, occ: BooleanArray): Boolean {
        val ax = a % w + .5f; val ay = a / w + .5f
        val bx = b % w + .5f; val by = b / w + .5f
        val dx = bx - ax; val dy = by - ay
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-3f) return true
        if (backRoom?.cuts(ax, ay, bx, by) == true) return false
        val nx = -dy / len * .3f; val ny = dx / len * .3f
        var t = 0f
        while (t <= len) {
            val x = ax + dx / len * t; val y = ay + dy / len * t
            for (k in -1..1) {
                val sx = floor(x + nx * k).toInt(); val sy = floor(y + ny * k).toInt()
                val i = idx(sx, sy)
                if (i < 0 || (occ[i] && i != b)) return false
            }
            t += .25f
        }
        return true
    }

    /** After reaching a stop: step onto the seat/machine, or turn to face the display. True once settled. */
    private fun settle(v: Person, dt: Float): Boolean {
        val sp = v.seat
        if (sp != null && !v.seated) {
            if (!moveToward(sp.x, sp.y, v.speed * dt, false, v)) return false
            v.seated = true
            v.heading = sp.heading
            v.pose = sp.pose
            v.z = if (sp.pose == 3) sp.z else sp.z
            return true
        }
        if (sp == null) {
            val p = items.getOrNull(v.item)
            val def = p?.let { Catalog.item(it.itemId) }
            if (p != null && def != null) {
                val (fw, fd) = itemDims(def, p.rot)
                val cx = p.x + fw / 2f; val cy = p.y + fd / 2f
                if ("dance_floor" in def.tags) v.pose = 2
                else if (kotlin.math.abs(cx - v.x) + kotlin.math.abs(cy - v.y) > .05f) v.heading = kotlin.math.atan2(cx - v.x, -(cy - v.y))
            }
        }
        return true
    }

    /** Gets up from a seat (or stops dancing) before walking on. */
    private fun standUp(v: Person) {
        if (v.pose == 0 && v.z == 0f && !v.seated) return
        v.pose = 0; v.z = 0f; v.seated = false; v.seat = null
    }

    private fun stepVisitor(v: Person, dt: Float, occ: BooleanArray) {
        if (dt <= 0f) return
        if (v.heldIn > 0f) v.heldIn -= dt
        if (v.wait > 0f) { v.wait -= dt; if (v.pose == 2 || v.pose == 3) v.phase += dt * 5f; return }
        if (v.goal < 0 || v.stage == 3) standUp(v)
        when (v.stage) {
            0 -> { // browsing displays / using machines
                if (v.stopIdx >= v.stops.size) {
                    // done looking around: pay (unless already paid, or nothing to pay at), then sit or leave
                    val mustPay = v.counter != -2 && counters().isNotEmpty() && typeId != "gym" && typeId?.let { Catalog.type(it).office } != true
                    v.stage = if (mustPay) 1 else 4
                    v.goal = -1
                    return
                }
                if (v.goal < 0) {
                    // pick a free spot at the next display; if all are taken, skip it
                    val spot = spotAt(v.stops[v.stopIdx], occ, v)
                    if (spot < 0) { v.stopIdx++; return }
                    v.claim = spot
                    goTo(v, spot, occ)
                }
                if (walk(v, dt, occ) && settle(v, dt)) {
                    v.stopIdx++
                    // shoppers take an item off the display (it shows one fewer) and carry it to the register
                    shelf?.customerTakes(v.item, v)?.let { pid ->
                        v.held = Catalog.product(pid)?.color ?: c(0xFFC8A165)
                        v.heldCount++
                        v.heldIn = ShelfStock.TAKE_TIME
                    }
                    v.wait = when (typeId) {
                        "gym", "hairdresser" -> 7f + e.rnd.nextFloat() * 5f
                        else -> if (typeId?.let { Catalog.type(it).office } == true) 10f + e.rnd.nextFloat() * 6f else 1.2f + e.rnd.nextFloat() * 2.5f
                    }
                    v.goal = -1
                }
            }
            1 -> { // join the shortest queue (if every line is full, look around a bit longer, or give up)
                v.claim = -1
                val best = counters().filter { queueSlots.containsKey(it) }.minByOrNull { queues[it]?.size ?: 0 }
                if (best == null) { v.stage = 3; v.goal = -1; return }
                if ((queues[best]?.size ?: 0) >= (queueSlots[best]?.size ?: 0)) {
                    if (v.retries++ < 4) { v.wait = 2f; return }
                    v.stage = 3; v.goal = -1; return
                }
                v.counter = best
                queues.getOrPut(best) { ArrayList() } += v
                v.stage = 2
                v.goal = -1
            }
            2 -> { // shuffle forward in the queue, pay at the front
                val q = queues[v.counter]
                val slots = queueSlots[v.counter]
                if (q == null || slots == null || v !in q) { v.stage = 3; v.goal = -1; return }
                val pos = q.indexOf(v)
                if (pos >= slots.size) return // the line got shorter slots (furniture moved): wait here
                val slot = slots[pos]
                if (v.goal != slot) goTo(v, slot, occ)
                if (walk(v, dt, occ) && pos == 0) {
                    if (v.blockedFor >= 0f) { v.wait = 1.2f + e.rnd.nextFloat() * 1.6f; v.blockedFor = -1f; return }
                    // paid
                    if (mode == InteriorMode.BUSINESS && e.realTime - lastCash > 2.5f) {
                        lastCash = e.realTime
                        com.hustlecity.game.audio.Audio.play(com.hustlecity.game.audio.Sfx.CASH)
                    }
                    v.blockedFor = 0f
                    if (v.heldCount > 0) v.bagged = true
                    leaveQueue(v)
                    v.counter = -2
                    v.stage = if (v.stopIdx < v.stops.size) 0 else 4
                    v.goal = -1
                }
            }
            4 -> { // sit down to eat, watch the film or dance
                if (v.seatTag.isEmpty()) { v.stage = 3; v.goal = -1; return }
                if (v.goal < 0) {
                    val seat = seatFor(v.seatTag, occ, v)
                    if (seat < 0) { v.seatTag = ""; return } // full house: take it to go
                    v.claim = seat
                    goTo(v, seat, occ)
                }
                if (walk(v, dt, occ) && settle(v, dt)) {
                    v.wait = when (typeId) { "cinema", "theater" -> 14f; "nightclub" -> 10f; else -> 7f } + e.rnd.nextFloat() * 8f
                    v.seatTag = ""
                    v.stage = 3
                    v.goal = -1
                }
            }
            3 -> { // leave
                v.claim = -1
                val door = idx(doorX, d - 1)
                if (v.goal != door) goTo(v, door, occ)
                if (walk(v, dt, occ) && moveToward(doorX.toFloat(), d + .6f, v.speed * dt, false, v)) v.done = true
            }
        }
    }

    // ------------------------------------------------------------------ staff

    private val staffById = HashMap<Int, Person>()
    private val staffGoal = HashMap<Int, Int>()
    private val staffTimer = HashMap<Int, Float>()

    /** Where a staff member works: a tile, plus the item to face or a seat to sit on. */
    private class Post(val tile: Int, val face: Int = -1, val seat: SeatPos? = null)

    private fun deskSeat(item: Int): SeatPos? {
        val p = items.getOrNull(item) ?: return null
        val def = Catalog.item(p.itemId) ?: return null
        // the chair in front of the desk, facing the screen
        val (ax, ay, vx, vy) = when (p.rot % 4) {
            0 -> floatArrayOf(1f, 1.15f, 0f, -1f)
            1 -> floatArrayOf(-.15f, 1f, 1f, 0f)
            2 -> floatArrayOf(1f, -.15f, 0f, 1f)
            else -> floatArrayOf(1.15f, 1f, -1f, 0f)
        }.let { arrayOf(it[0], it[1], it[2], it[3]) }
        val wx = p.x + ax; val wy = p.y + ay
        if (wx < 0 || wy < 0 || wx >= w || wy >= d || def.w < 2) return null
        return SeatPos(idx(floor(wx).toInt(), floor(wy).toInt()), wx, wy, .45f, kotlin.math.atan2(vx, -vy), 1)
    }

    private fun updateStaff(dt: Float, occ: BooleanArray) {
        val s = e.state
        val posts = LinkedHashMap<Int, Post>()
        val looks = HashMap<Int, Pair<Int, Int>>()
        when (mode) {
            InteriorMode.BUSINESS -> {
                val b = business!!
                val occFull = occupancy()
                val counters = counters()
                var regIdx = 0
                var desk = 0
                val desks = itemsWith("desk").map { it.index }
                val working = s.employees.filter { it.businessId == b.id && it.worksAt(s.hour, s.weekday) }
                for ((n, emp) in working.withIndex()) {
                    fun behind(item: Int?): Post? = item?.let { ii -> items.getOrNull(ii)?.let { frontTiles(it, occFull, true).firstOrNull() ?: frontTiles(it, occFull).firstOrNull() }?.let { Post(it, ii) } }
                    val post: Post? = when (emp.role) {
                        StaffRole.CUSTOMER_SERVICE -> behind(counters.getOrNull(regIdx++))
                        StaffRole.CLEANER -> {
                            // head for the dirtiest floor, scrub it, move on; with nothing to clean, wait in the back room
                            val cur = staffGoal[emp.id]
                            val t = staffTimer[emp.id] ?: 0f
                            if (cur != null && cur >= 0 && t > 0f) Post(cur)
                            else (e.economy.dirt.dirtiest(b, 6).randomOrNull(e.rnd) ?: cleanerRest(occFull) ?: freeTile(occFull, e.rnd.nextInt(w * d))).let { staffTimer[emp.id] = 4f; Post(it) }
                        }
                        StaffRole.DJ -> behind(itemsWith("dj_booth").firstOrNull()?.index)
                        StaffRole.ACTOR -> itemsWith("stage").firstOrNull()?.let { st -> frontTiles(st.value, occFull).getOrNull(n % 4)?.let { Post(it, -1) } }
                        StaffRole.PROJECTIONIST -> behind(itemsWith("screen").firstOrNull()?.index)
                        else -> if (b.type.office) desks.getOrNull(desk++)?.let { di -> deskSeat(di)?.let { Post(it.tile, di, it) } ?: behind(di) }
                            else browseTargets().getOrNull(n)?.let { it2 -> items.indexOf(it2).let { ii -> frontTiles(it2, occFull).lastOrNull()?.let { Post(it, ii) } } }
                    }
                    val base = post ?: freeTile(occFull, n * 7 + 3).takeIf { it >= 0 }?.let { Post(it) } ?: continue
                    // someone bringing a box of stock goes to the box, then to the display
                    val p = runs[emp.id]?.let { r -> if (r.stage <= 1) Post(r.from) else Post(r.to, r.display) } ?: base
                    posts[emp.id] = p
                    looks[emp.id] = SHIRT_COLORS[emp.id % SHIRT_COLORS.size] to SKIN_TONES[emp.id % SKIN_TONES.size]
                    if (emp.role == StaffRole.CLEANER && dt > 0f) {
                        val person = staffById[emp.id]
                        if (person != null && person.pathIdx >= person.path.size) {
                            staffTimer[emp.id] = (staffTimer[emp.id] ?: 0f) - dt
                            e.economy.dirt.scrub(b, p.tile, 6f * dt)
                            person.phase += dt * 6f
                        }
                    }
                }
            }
            InteriorMode.RIVAL, InteriorMode.PLACE -> {
                val occFull = occupancy()
                counters().firstOrNull()?.let { ci ->
                    items.getOrNull(ci)?.let { reg -> frontTiles(reg, occFull, true).firstOrNull()?.let { t -> posts[-1] = Post(t, ci); looks[-1] = c(0xFF2C3E50) to SKIN_TONES[building.id % SKIN_TONES.size] } }
                }
            }
            else -> {}
        }
        staffById.keys.retainAll(posts.keys)
        runs.entries.removeAll { (k, r) ->
            (k !in posts || r.display !in items.indices).also { gone -> if (gone) { shelf?.release(r.display); staffById[k]?.carry = 0 } }
        }
        for ((key, post) in posts) {
            val (shirt, skin) = looks[key]!!
            val p = staffById.getOrPut(key) {
                // new shift: walk in from the door (or be at the post already when the scene opens)
                val fresh = staff.isEmpty() && visitors.isEmpty()
                Person(if (fresh) post.tile % w + .5f else doorX.toFloat(), if (fresh) post.tile / w + .5f else d - .5f, shirt, skin).also {
                    it.speed = 1.8f; it.pants = key and 15; it.hair = (key * 7) and 15; it.look = key * 7919 + 3
                }
            }
            if (staffGoal[key] != post.tile || p.goal != post.tile) {
                staffGoal[key] = post.tile
                p.pose = 0; p.z = 0f; p.seated = false
                goTo(p, post.tile, occ)
            }
            if (dt > 0f && p.pathIdx < p.path.size) {
                val t = p.path[p.pathIdx]
                if (moveToward(t % w + .5f, t / w + .5f, p.speed * dt, false, p)) p.pathIdx++
            } else if (p.pathIdx >= p.path.size) {
                runs[key]?.let { if (p.goal == post.tile) advanceRun(key, it, p, dt) }
                val seat = post.seat
                if (seat != null && !p.seated) {
                    if (dt <= 0f || moveToward(seat.x, seat.y, p.speed * dt, false, p)) { p.x = seat.x; p.y = seat.y; p.seated = true; p.pose = 1; p.z = seat.z; p.heading = seat.heading }
                } else if (seat == null && post.face >= 0) {
                    items.getOrNull(post.face)?.let { it2 ->
                        Catalog.item(it2.itemId)?.let { def ->
                            val (fw, fd) = itemDims(def, it2.rot)
                            p.heading = kotlin.math.atan2(it2.x + fw / 2f - p.x, -(it2.y + fd / 2f - p.y))
                        }
                    }
                }
            }
        }
        staff.clear()
        staff.addAll(staffById.values)
    }

    /** A staff member bringing a box: to the box ([from]: a storage shelf or the door), pick it up, to the display, unpack. */
    private class Run(val display: Int, val from: Int, val to: Int) { var stage = 0; var t = 0f }
    private val runs = HashMap<Int, Run>()

    /**
     * Sends someone on shift to bring new stock to display [item]: from the back room's storage shelves, or in from the
     * door for a delivery. Prefers staff who aren't serving anyone. Returns false when nobody can go (it just fills in).
     */
    private fun sendRunner(item: Int, source: ShelfStock.Source): Boolean {
        if (mode != InteriorMode.BUSINESS || staffById.isEmpty()) return false
        val p = items.getOrNull(item) ?: return false
        val occ = occupancy()
        val to = frontTiles(p, occ).firstOrNull() ?: return false
        val from = if (source == ShelfStock.Source.BACK_ROOM) storageSpot(occ) ?: return false else idx(doorX, d - 1)
        if (from < 0) return false
        val emps = e.state.employees
        val busyTills = queues.values.any { it.isNotEmpty() }
        val key = staffById.keys.filter { it !in runs }.mapNotNull { id ->
            val score = when (emps.firstOrNull { it.id == id }?.role) {
                StaffRole.DJ, StaffRole.ACTOR, StaffRole.PROJECTIONIST, null -> return@mapNotNull null
                StaffRole.CUSTOMER_SERVICE -> if (busyTills) return@mapNotNull null else 2
                StaffRole.CLEANER -> 1
                else -> 0
            }
            id to score
        }.minByOrNull { it.second }?.first ?: return false
        runs[key] = Run(item, from, to)
        return true
    }

    private fun advanceRun(key: Int, r: Run, p: Person, dt: Float) {
        when (r.stage) {
            0 -> { r.stage = 1; r.t = 0f } // at the box
            1 -> { r.t += dt; if (r.t > .7f) { r.stage = 2; p.carry = 1 } } // picked it up: off to the display
            2 -> { shelf?.release(r.display); r.stage = 3; r.t = 0f } // unpacking: the items fill in
            else -> {
                r.t += dt
                // the box is empty once the display has filled in
                if (r.t > 1.0f && shelf?.filling(r.display) != true || r.t > 4f) { p.carry = 0; runs.remove(key) }
            }
        }
    }

    /** Staff members bringing stock to a display right now (for tests and the HUD). */
    val restockRuns: Int get() = runs.size

    /** Where staff stand to take a box off a storage shelf. */
    private fun storageSpot(occ: BooleanArray): Int? {
        for (p in items) {
            if (Catalog.item(p.itemId)?.fn != ItemFn.STORAGE) continue
            frontTiles(p, occ).firstOrNull()?.let { return it }
        }
        return null
    }

    /** Where an idle cleaner waits: by the cleaning station, or anywhere in the back room. */
    private fun cleanerRest(occ: BooleanArray): Int? {
        for (p in items) {
            if (Catalog.item(p.itemId)?.fn != ItemFn.CLEANING) continue
            frontTiles(p, occ).firstOrNull()?.let { return it }
            for (k in 0 until 4) idx(p.x + DX[k], p.y + DY[k]).let { if (it >= 0 && !occ[it]) return it }
        }
        val room = backRoom ?: return null
        for (y in 0 until room.depth) for (x in 0 until w) {
            val i = y * w + x
            if (!occ[i] && !room.isDoorTile(x, y)) return i
        }
        return null
    }

    private fun freeTile(occ: BooleanArray, seed: Int): Int {
        for (k in 0 until w * d) {
            val i = (seed * 31 + k * 17).mod(w * d)
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

    fun cancelGhost() {
        ghostId = null
        movingIndex = -1
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
                // everything the store sells is on display, in rows with an aisle in front of each row
                for (p in showroom(Catalog.place(building.placeId!!).stock, w, d).orEmpty()) {
                    if (put(p.itemId, p.x, p.y, p.rot)) forSale[generated.size - 1] = p.itemId
                }
                // the checkout by the exit, and the free carts by the door
                if (put("checkout_counter", w - 5, d - 3)) registerIndex = generated.size - 1
                if (put("cart_rack", 1, d - 2)) rackIndex = generated.size - 1
                put("plant_large", w - 1, d - 1)
                put("trash_can", doorX + 2, d - 1)
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
                // as many as the floor holds, scooters to trucks
                val defs = Catalog.vehicles
                val cols = ((w - 1) / 5).coerceIn(1, 4)
                for ((i, v) in defs.withIndex()) {
                    val col = i % cols; val row = i / cols
                    val y = 3f + row * 5f
                    if (y + v.length * .4f > d - 2.5f) break
                    showroom += Triple(v.id, 2.5f + col * 5f, y)
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

        /**
         * Lays out a store's [stock] as a showroom of [w] by [d] tiles: rows of displays from the back wall
         * (wall-mounted pieces go first, so they hang there), each with a walkway in front, and the last five rows kept free for
         * the checkout, the carts and the entrance. Returns null if it doesn't fit.
         */
        fun showroom(stock: List<String>, w: Int, d: Int): List<PlacedItem>? {
            val zone = d - 5
            val defs = stock.mapNotNull { Catalog.item(it) }
            val wallMounted = { def: FurnitureDef -> def.d == 1 && def.parts.isNotEmpty() && def.parts.minOf { it.z0 } > .3f }
            val queue = (defs.filter(wallMounted) + defs.filterNot(wallMounted).sortedWith(compareBy({ -it.d }, { -it.w })))
                .toMutableList()
            val out = ArrayList<PlacedItem>()
            var y = 0
            while (queue.isNotEmpty()) {
                var x = 1
                var rowDepth = 0
                val it = queue.iterator()
                while (it.hasNext()) {
                    val def = it.next()
                    if (x + def.w > w - 1 || y + def.d > zone) continue
                    out += PlacedItem(def.id, x, y, 0)
                    x += def.w + 1
                    rowDepth = maxOf(rowDepth, def.d)
                    it.remove()
                }
                if (rowDepth == 0) return null
                y += rowDepth + 1
            }
            return out
        }
    }
}

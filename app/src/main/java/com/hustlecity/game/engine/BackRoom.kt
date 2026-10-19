package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.PlacedItem
import kotlin.math.roundToInt

/**
 * The staff-only back room of a rented store, like the default layouts in Big Ambitions: the back rows of the floor
 * (the side away from the entrance) behind a wall with a doorway, for storage shelves and the cleaning station.
 * The wall runs along the tile edge at y = [depth], so it takes no floor space. Customers never go in.
 */
class BackRoom(val depth: Int, val doorX: Int, val doorW: Int) {
    /** True for tiles inside the back room. */
    fun contains(y: Int) = y < depth

    fun inDoorway(x: Int) = x >= doorX && x < doorX + doorW

    /** True when a step between two neighboring tiles goes through the wall instead of the doorway. */
    fun blocks(cx: Int, cy: Int, nx: Int, ny: Int): Boolean =
        cx == nx && ((cy == depth - 1 && ny == depth) || (cy == depth && ny == depth - 1)) && !inDoorway(nx)

    /** True when a straight walk between two points would go through the wall rather than the doorway. */
    fun cuts(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        val wy = depth.toFloat()
        if ((ay < wy) == (by < wy)) return false
        val t = (wy - ay) / (by - ay)
        val x = ax + (bx - ax) * t
        return x < doorX + .3f || x > doorX + doorW - .3f
    }

    /** The tiles on both sides of the doorway, kept clear so staff can get through. */
    fun isDoorTile(x: Int, y: Int) = inDoorway(x) && (y == depth - 1 || y == depth)

    /**
     * Why [def] can't stand at (x, y) with footprint fw × fd turned [rot] quarter turns, or null when the back room
     * doesn't mind. Customer furniture stays out front, facing the shop floor; nothing blocks the wall or doorway.
     */
    fun problem(def: FurnitureDef, x: Int, y: Int, fw: Int, fd: Int, rot: Int = 0): String? {
        if (y < depth && y + fd > depth) return "It can't stand across the back room wall"
        for (yy in y until y + fd) for (xx in x until x + fw) if (isDoorTile(xx, yy)) return "Keep the back room doorway clear"
        if (!def.fn.customerFacing) return null
        if (y + fd <= depth) return "Customers can't go into the back room: displays, registers and seats go out front"
        if (y == depth && rot % 4 == 2) return "Turn it to face the shop floor: customers can't reach it from the back room"
        if (y == depth && rot % 4 == 0 && def.fn == com.hustlecity.game.data.ItemFn.CASH_REGISTER) return "Leave room behind the register for your cashier"
        return null
    }

    companion object {
        /** Business types whose stores get a back room: shops, food and services (not office firms, warehouses or HQ). */
        fun eligible(b: Business) = !b.type.facility && !b.type.office

        /**
         * Rows to wall off for a floor [d] tiles deep: about a third, 3 to 5 rows, leaving at least 4 for the shop.
         * Units shallower than 7 tiles keep an open floor (0).
         */
        fun preferredDepth(w: Int, d: Int) = if (d < 7 || w < 6) 0 else (d * .32f).roundToInt().coerceIn(3, 5).coerceAtMost(d - 4)

        fun doorWidth(w: Int) = if (w >= 10) 2 else 1

        /** The back room a business has, or null for none (or not laid out yet). */
        fun of(b: Business, w: Int, d: Int): BackRoom? {
            if (b.backRoom !in 1..d - 2 || b.backDoor !in 0 until w) return null
            return BackRoom(b.backRoom, b.backDoor, doorWidth(w).coerceAtMost(w - b.backDoor))
        }

        /**
         * Lays out a back room around a store's furniture: the deepest room from the preferred depth down to 3 rows, with
         * the doorway near the right wall (or the left, or the middle), whose wall cuts through nothing, whose doorway is
         * clear and that leaves every piece customers use out front. Returns (depth, doorX), or null when that can't be
         * done without moving furniture.
         */
        fun fit(items: List<PlacedItem>, w: Int, d: Int): Pair<Int, Int>? {
            val top = preferredDepth(w, d)
            if (top < 3) return null
            val dw = doorWidth(w)
            val doors = listOf(w - dw - 1, 1, w / 4, (w * 3) / 4 - dw).filter { it in 0..w - dw }.distinct()
            for (depth in top downTo 3) for (door in doors) {
                if (allows(BackRoom(depth, door, dw), items)) return depth to door
            }
            return null
        }

        /** True when every piece of [items] can stay where it is with this back room. */
        fun allows(room: BackRoom, items: List<PlacedItem>): Boolean = items.all { p ->
            val def = Catalog.item(p.itemId) ?: return@all true
            val (fw, fd) = itemDims(def, p.rot)
            room.problem(def, p.x, p.y, fw, fd, p.rot) == null
        }

        /**
         * Lays out the back room when a store's interior is set up or seen. A new store gets one; an old save gets one
         * where its furniture leaves room, and otherwise keeps its open floor. A back room that no longer suits the
         * furniture (moved outside the designer) is moved, made smaller or taken out, so nothing ends up stuck.
         */
        fun resolve(b: Business, w: Int, d: Int) {
            if (b.backRoom == 0) return
            if (!eligible(b)) { b.backRoom = 0; b.backDoor = -1; return }
            of(b, w, d)?.let { if (allows(it, b.furniture)) return }
            val fit = fit(b.furniture, w, d)
            b.backRoom = fit?.first ?: 0
            b.backDoor = fit?.second ?: -1
        }
    }
}

package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Walking a store with the stick or the keys: walls, furniture and the back room's wall stop you; the door lets you out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InteriorWalkTest {
    private fun store(): Pair<GameEngine, InteriorScene> {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.gridW >= 12 && it.gridD >= 9 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "giftshop", "X")!!
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        val row = sc.d - 4
        biz.furniture += PlacedItem("shelf", 3, row, 0)
        e.state.player.inBuilding = b.id
        e.interior = sc
        return e to sc
    }

    private fun walk(sc: InteriorScene, wx: Float, wy: Float, seconds: Float): Boolean {
        repeat((seconds * 30).toInt()) { if (sc.walkDirect(wx, wy, 1f, 1 / 30f)) return true }
        return false
    }

    @Test
    fun wallsAndFurnitureStopYouAndTheDoorLetsYouOut() {
        val (_, sc) = store()
        // into the side wall
        sc.px = 2f; sc.py = sc.d - 1.5f
        walk(sc, -1f, 0f, 3f)
        assertTrue("stopped at the wall (x=${sc.px})", sc.px in .2f..1f)
        // into the shelf from the front: it stays on this side of it
        val shelfRow = sc.d - 4
        sc.px = 3.5f; sc.py = sc.d - 1.5f
        walk(sc, 0f, -1f, 3f)
        assertTrue("stopped by the shelf (y=${sc.py}, shelf at $shelfRow)", sc.py > shelfRow + 1f)
        // sliding: pushing diagonally into the wall still moves along it
        sc.px = .5f; sc.py = sc.d - 1.5f
        walk(sc, -1f, -1f, .5f)
        assertTrue("slid along the wall", sc.py < sc.d - 2f)
        // the back room's wall (when the store has one) can only be crossed at its doorway
        sc.backRoom?.let { room ->
            val wallX = (0 until sc.w).first { !room.inDoorway(it) && it > 0 } + .5f
            sc.px = wallX; sc.py = room.depth + 1.5f
            walk(sc, 0f, -1f, 3f)
            assertTrue("the back room wall stopped you (y=${sc.py}, wall at ${room.depth})", sc.py > room.depth)
        }
        // and out of the front door
        sc.px = sc.doorX.toFloat(); sc.py = sc.d - 1.5f
        assertTrue("walked out of the door", walk(sc, 0f, 1f, 3f))
        // not through the front wall beside it
        sc.px = sc.doorX + 3.5f; sc.py = sc.d - 1.5f
        assertTrue("the front wall holds", !walk(sc, 0f, 1f, 3f))
    }
}

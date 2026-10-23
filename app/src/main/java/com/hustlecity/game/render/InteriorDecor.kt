package com.hustlecity.game.render

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.data.c
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.itemDims
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dresses a room's walls for its trade, the way a fitted-out shop looks: bookcases in a bookstore, wine racks in a
 * liquor store, TVs in an electronics store, mirrors in a gym, menu boards over a burger counter, art and shelves at
 * home. Dressing only goes on stretches of wall nothing stands against, so it never clashes with the furniture you
 * place (move a shelf to the wall and the dressing behind it makes way). The walls leave out their lights and
 * windows where it goes.
 */
class InteriorDecor(private val scene: Scene3D) {
    companion object {
        const val N = 0; const val W = 1; const val E = 2
        private const val BOOKS = 0; private const val WINE = 1; private const val TRINKETS = 2; private const val TVS = 3
        private const val RAIL = 4; private const val MIRROR = 5; private const val STATION = 6; private const val PLANTERS = 7
        private const val CAFE = 8; private const val POSTERS = 9; private const val NEON = 10; private const val OFFICE = 11
        private const val SAFETY = 12; private const val HOME = 13; private const val PROMO = 14; private const val CRATES = 15

        private val WOOD = c(0xFF8B5A3C); private val WOOD_D = c(0xFF6B4530); private val WOOD_L = c(0xFFB07A52)
        private val WHITE = c(0xFFF2F2EF); private val DARK = c(0xFF26292E); private val STEEL = c(0xFFB8C0CA)
        private val GLASS = c(0xFF9FC6E6)
        private val SPINES = intArrayOf(c(0xFFB0303A), c(0xFF2F5FA8), c(0xFF2F6B45), c(0xFFC9A227), c(0xFF5B3A8C), c(0xFFE0D6C2), c(0xFF1F2A44),
            c(0xFFD9822B), c(0xFF6B2737), c(0xFF3E8E9E), c(0xFF8C8C8C), c(0xFF1C1C1E))
        private val BRIGHT = intArrayOf(c(0xFFEB5757), c(0xFFF2C94C), c(0xFF2F80ED), c(0xFF27AE60), c(0xFF9B51E0), c(0xFFF2994A), c(0xFF56CCF2), c(0xFFFF8AD8))
        private val GARMENTS = intArrayOf(c(0xFF2F80ED), c(0xFF1F2A44), c(0xFFF5F5F5), c(0xFFB0303A), c(0xFF333333), c(0xFFC9B48E), c(0xFF6B8E5A), c(0xFFE0A0B8),
            c(0xFF3F5F86), c(0xFFF2C94C))
        /** Light sources keep their color in the GPU renderer (see Scene3D.isEmissive): screens and neon. */
        private val SCREENS = intArrayOf(c(0xFF5FE3FF), c(0xFFFF5FD8), c(0xFFFFE680), c(0xFF2EE87A), c(0xFFFFD34D))

        /** The look of the walls for whatever this room is. */
        fun themeOf(sc: InteriorScene): Int = when (sc.mode) {
            InteriorMode.HOME -> HOME
            InteriorMode.BUSINESS -> sc.business?.typeId?.let { forType(it) } ?: -1
            InteriorMode.RIVAL -> sc.e.state.rivalBusinessAt(sc.building.id)?.typeId?.let { forType(it) } ?: -1
            InteriorMode.PLACE -> when (sc.building.placeId?.let { Catalog.place(it).kind }) {
                PlaceKind.FURNITURE_STORE -> TRINKETS
                PlaceKind.WHOLESALER, PlaceKind.IMPORTER -> SAFETY
                PlaceKind.CAR_DEALER -> POSTERS
                null, PlaceKind.GAS_STATION -> -1
                else -> OFFICE
            }
            InteriorMode.EMPTY -> -1
        }

        private fun forType(id: String) = when (id) {
            "florist" -> PLANTERS; "produce" -> CRATES; "coffee" -> CAFE; "supermarket" -> PROMO; "fastfood" -> POSTERS
            "hairdresser" -> STATION; "gym" -> MIRROR; "giftshop" -> TRINKETS; "liquor" -> WINE; "clothing" -> RAIL
            "bookstore" -> BOOKS; "electronics" -> TVS; "jewelry" -> MIRROR; "nightclub" -> NEON; "cinema", "theater" -> POSTERS
            "warehouse" -> SAFETY
            else -> OFFICE
        }

        /** A wall-mounted menu board goes over the counter of food places. */
        private fun hasMenu(sc: InteriorScene) = sc.mode == InteriorMode.BUSINESS && sc.business?.typeId.let { it == "fastfood" || it == "coffee" } ||
            sc.mode == InteriorMode.RIVAL && sc.e.state.rivalBusinessAt(sc.building.id)?.typeId.let { it == "fastfood" || it == "coffee" }

        private fun h(a: Int, b: Int): Float {
            var x = a * 374761393 + b * 668265263
            x = (x xor (x ushr 13)) * 1274126177
            return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
        }
    }

    private val unitWall = IntArray(48); private val unitA = FloatArray(48); private val unitB = FloatArray(48); private val unitKind = IntArray(48)
    private var units = 0
    private var theme = -1
    private var roomW = 0f
    private var occ = BooleanArray(0)
    private var gw = 0
    /** Span of the north wall a menu board takes (empty when none). */
    var menuA = 0f; var menuB = 0f
    private val corner = BooleanArray(4)

    /** Works out where the dressing goes this frame (call before drawing the walls). */
    fun plan(sc: InteriorScene) {
        theme = themeOf(sc)
        units = 0
        menuA = 0f; menuB = 0f
        roomW = sc.w.toFloat()
        if (theme < 0 || !sc.showWalls) return
        gw = sc.w
        if (occ.size < sc.w * sc.d) occ = BooleanArray(sc.w * sc.d)
        java.util.Arrays.fill(occ, 0, sc.w * sc.d, false)
        for (p in sc.items) {
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            for (y in p.y until (p.y + fd).coerceAtMost(sc.d)) for (x in p.x until (p.x + fw).coerceAtMost(sc.w)) if (x >= 0 && y >= 0) occ[y * sc.w + x] = true
        }
        val first = sc.backRoom?.depth ?: 0
        // side walls: free stretches in the first column / last column, in two-tile units
        for (side in 0..1) {
            val x = if (side == 0) 0 else sc.w - 1
            var y = first
            while (y < sc.d - 1) {
                var len = 0
                while (y + len < sc.d - 1 && !occ[(y + len) * sc.w + x]) len++
                var u = y
                while (len >= 2 && units < unitWall.size) {
                    val n = if (len == 3) 3 else 2
                    unitWall[units] = if (side == 0) W else E
                    unitA[units] = u + .15f; unitB[units] = u + n - .15f
                    unitKind[units] = kindFor(units)
                    units++
                    u += n; len -= n
                }
                y = u + len + 1
            }
        }
        // the menu board over a food counter, middle of the back wall
        if (first == 0 && hasMenu(sc) && sc.w >= 6) { menuA = sc.w / 2f - 1.8f; menuB = sc.w / 2f + 1.8f }
        corner[0] = !occ[first * sc.w]; corner[1] = !occ[first * sc.w + sc.w - 1]
        corner[2] = !occ[(sc.d - 1) * sc.w]; corner[3] = !occ[(sc.d - 1) * sc.w + sc.w - 1]
    }

    /** Some trades mix two kinds of wall unit. */
    private fun kindFor(i: Int): Int = when (theme) {
        MIRROR -> if (i % 2 == 0) MIRROR else POSTERS
        RAIL -> if (i % 3 == 2) MIRROR else RAIL
        OFFICE -> if (i % 2 == 0) OFFICE else POSTERS
        STATION -> if (i % 2 == 0) STATION else TRINKETS
        else -> theme
    }

    /** True when dressing covers the wall at [u] (tiles along it): the wall leaves its light out there. */
    fun covers(wall: Int, u: Float): Boolean {
        for (i in 0 until units) if (unitWall[i] == wall && u > unitA[i] - .4f && u < unitB[i] + .4f) return true
        return false
    }

    /** True when a window from [a] to [b] along the back wall would be behind the menu board. */
    fun hidesWindow(a: Float, b: Float) = menuB > menuA && b > menuA && a < menuB

    // ---------------------------------------------------------------- drawing

    /** A box against a wall: [a]..[b] along it, [n0]..[n1] out from it, [z0]..[z1] up. */
    private fun wb(wall: Int, a: Float, b: Float, n0: Float, n1: Float, z0: Float, z1: Float, top: Int, side: Int = top) {
        when (wall) {
            N -> scene.box(a, n0, z0, b, n1, z1, top, side)
            W -> scene.box(n0, a, z0, n1, b, z1, top, side)
            else -> scene.box(roomW - n1, a, z0, roomW - n0, b, z1, top, side)
        }
    }

    /** A flat face on a wall (for posters, screens and boards): [n] out from the wall. */
    private fun wq(wall: Int, a: Float, b: Float, n: Float, z0: Float, z1: Float, color: Int) {
        when (wall) {
            N -> scene.quad(a, n, z0, b, n, z0, b, n, z1, a, n, z1, color)
            W -> scene.quad(n, a, z0, n, b, z0, n, b, z1, n, a, z1, color)
            else -> scene.quad(roomW - n, a, z0, roomW - n, b, z0, roomW - n, b, z1, roomW - n, a, z1, color)
        }
    }

    /** A round thing hung on a wall (clock face, plate, speaker cone). */
    private val rx = FloatArray(12); private val ry = FloatArray(12); private val rz = FloatArray(12)
    private fun wdisc(wall: Int, u: Float, z: Float, r: Float, n: Float, color: Int, sides: Int = 10) {
        for (i in 0 until sides) {
            val t = i * 6.2832f / sides
            val a = u + cos(t) * r; val zz = z + sin(t) * r
            when (wall) { N -> { rx[i] = a; ry[i] = n }; W -> { rx[i] = n; ry[i] = a }; else -> { rx[i] = roomW - n; ry[i] = a } }
            rz[i] = zz
        }
        scene.poly(sides, rx, ry, rz, color)
    }

    /** A point in front of a wall in room coordinates (for blobs). */
    private fun wx(wall: Int, u: Float, n: Float) = when (wall) { N -> u; W -> n; else -> roomW - n }
    private fun wy(wall: Int, u: Float, n: Float) = when (wall) { N -> n; else -> u }

    /** Draws the dressing on the walls the camera sees the inside of. */
    fun draw(sc: InteriorScene, showN: Boolean, showW: Boolean, showE: Boolean) {
        if (theme < 0 || !sc.showWalls) return
        for (i in 0 until units) {
            val wall = unitWall[i]
            if ((wall == W && !showW) || (wall == E && !showE)) continue
            unit(wall, unitA[i], unitB[i], unitKind[i], i)
        }
        if (showN && menuB > menuA) menuBoard(menuA, menuB)
        if (showN && sc.backRoom == null) radiators(sc)
        // big plants in the free corners, a clock and a security camera
        val first = (sc.backRoom?.depth ?: 0).toFloat()
        if (corner[0] && (showN || showW)) plant(.42f, first + .42f, 7)
        if (corner[1] && (showN || showE)) plant(roomW - .42f, first + .42f, 11)
        if (corner[2] && showW && theme != SAFETY) plant(.42f, sc.d - .5f, 13)
        if (corner[3] && showE && theme != SAFETY) plant(roomW - .42f, sc.d - .5f, 17)
        if (showW && !covers(W, sc.d - 2.2f)) clock(W, sc.d - 2.2f)
        if (theme != HOME) {
            if (showW || showN) camera(.12f, first + .12f)
            if (showE || showN) camera(roomW - .12f, first + .12f)
        }
    }

    private fun unit(wall: Int, a: Float, b: Float, kind: Int, seed: Int) {
        when (kind) {
            BOOKS -> bookcase(wall, a, b, 2.3f, seed, 5)
            OFFICE -> if (seed % 4 == 0) whiteboard(wall, a, b, seed) else bookcase(wall, a, b, 1.9f, seed, 4)
            WINE -> wineRack(wall, a, b, seed)
            TRINKETS -> floatingShelves(wall, a, b, seed, WHITE)
            TVS -> tvWall(wall, a, b, seed)
            RAIL -> clothesRail(wall, a, b, seed)
            MIRROR -> mirror(wall, a, b)
            STATION -> station(wall, a, b, seed)
            PLANTERS -> planters(wall, a, b, seed)
            CAFE -> cafeWall(wall, a, b, seed)
            POSTERS -> posters(wall, a, b, seed)
            NEON -> neon(wall, a, b, seed)
            SAFETY -> safety(wall, a, b, seed)
            HOME -> homeWall(wall, a, b, seed)
            PROMO -> promo(wall, a, b, seed)
            CRATES -> crates(wall, a, b, seed)
        }
    }

    /** A wooden bookcase full of books, a few gaps and a stray book lying flat. */
    private fun bookcase(wall: Int, a: Float, b: Float, height: Float, seed: Int, shelves: Int) {
        val depth = .34f
        wb(wall, a, b, 0f, .03f, 0f, height, WOOD_D)
        wb(wall, a, a + .04f, .03f, depth, 0f, height, WOOD)
        wb(wall, b - .04f, b, .03f, depth, 0f, height, WOOD)
        wb(wall, a, b, .03f, depth, height - .04f, height, WOOD_L, WOOD)
        wb(wall, a, b, .03f, depth, 0f, .08f, WOOD_D)
        val step = (height - .12f) / shelves
        for (s in 0 until shelves) {
            val z = .08f + s * step
            wb(wall, a + .04f, b - .04f, .03f, depth, z, z + .025f, WOOD_L, WOOD)
            var u = a + .06f
            var k = 0
            while (u < b - .1f) {
                val r = h(seed * 31 + s, k++)
                if (r < .07f) { u += .12f; continue }
                val bw = .03f + h(seed + k, s) * .045f
                val bh = (.62f + .3f * h(k, seed + s)) * (step - .06f)
                val col = SPINES[((r * 997).toInt()) % SPINES.size]
                if (r > .96f && u + .26f < b - .06f) {
                    // a book lying on its side
                    wb(wall, u, u + .24f, .08f, .3f, z + .025f, z + .07f, col, scene.mix(col, 0xFFFFFFFF.toInt(), .5f))
                    u += .27f; continue
                }
                wb(wall, u, u + bw, .06f, .3f, z + .025f, z + .025f + bh, scene.mix(col, 0xFFFFFFFF.toInt(), .55f), col)
                u += bw + .004f
            }
        }
    }

    private fun whiteboard(wall: Int, a: Float, b: Float, seed: Int) {
        wb(wall, a + .1f, b - .1f, 0f, .03f, 1.0f, 2.05f, STEEL)
        wq(wall, a + .14f, b - .14f, .031f, 1.04f, 2.01f, WHITE)
        // marker notes and a chart
        for (k in 0 until 5) {
            val y = 1.85f - k * .14f
            val len = (.4f + .5f * h(seed, k)) * (b - a - .4f)
            wq(wall, a + .25f, a + .25f + len, .033f, y, y + .025f, if (k == 0) c(0xFF2F5FA8) else c(0xFF3A3F45))
        }
        for (k in 0 until 4) {
            val x = b - .7f + k * .13f
            wq(wall, x, x + .09f, .033f, 1.12f, 1.12f + .12f + .35f * h(seed + 3, k), BRIGHT[(seed + k) % BRIGHT.size])
        }
        wb(wall, a + .3f, b - .3f, .03f, .09f, .98f, 1.01f, STEEL)
    }

    private fun wineRack(wall: Int, a: Float, b: Float, seed: Int) {
        val top = 2.15f
        wb(wall, a, b, 0f, .03f, 0f, top, WOOD_D)
        wb(wall, a, a + .05f, .03f, .32f, 0f, top, WOOD)
        wb(wall, b - .05f, b, .03f, .32f, 0f, top, WOOD)
        wb(wall, a, b, .03f, .32f, top - .05f, top, WOOD_L, WOOD)
        val cols = ((b - a - .1f) / .11f).toInt()
        val rows = 15
        for (r in 0 until rows) {
            val z = .1f + r * .135f
            wb(wall, a + .05f, b - .05f, .03f, .3f, z - .015f, z, WOOD)
            for (k in 0 until cols) {
                val v = h(seed * 7 + r, k)
                if (v < .12f) continue
                val u = a + .1f + k * .11f
                val glass = when { v < .5f -> c(0xFF1E3B26); v < .8f -> c(0xFF4A1420); else -> c(0xFF8A5A1E) }
                // the bottle's base faces out; the foil cap on its neck shows behind
                wb(wall, u - .035f, u + .035f, .2f, .3f, z + .005f, z + .075f, glass, glass)
                if (v > .9f) wb(wall, u - .02f, u + .02f, .3f, .31f, z + .02f, z + .06f, c(0xFFE8DCC0))
            }
        }
    }

    private fun floatingShelves(wall: Int, a: Float, b: Float, seed: Int, shelf: Int) {
        for (s in 0 until 3) {
            val z = 1.05f + s * .42f
            wb(wall, a, b, 0f, .28f, z - .03f, z, shelf, scene.mix(shelf, 0xFF000000.toInt(), .12f))
            var u = a + .08f
            var k = 0
            while (u < b - .15f) {
                val r = h(seed * 13 + s, k++)
                val iw = .08f + r * .1f; val ih = .08f + h(k, seed) * .2f
                val col = BRIGHT[((r * 911).toInt()) % BRIGHT.size]
                if (r < .3f) scene.blob(wx(wall, u + iw / 2, .14f), wy(wall, u + iw / 2, .14f), z + ih * .5f, ih * .5f, col)
                else wb(wall, u, u + iw, .06f, .22f, z, z + ih, scene.mix(col, 0xFFFFFFFF.toInt(), .3f), col)
                u += iw + .06f + h(seed, k) * .08f
            }
        }
    }

    private fun tvWall(wall: Int, a: Float, b: Float, seed: Int) {
        val n = if (b - a > 2.2f) 2 else 1
        val w = (b - a) / n
        for (k in 0 until n) {
            val u0 = a + k * w + .08f; val u1 = a + (k + 1) * w - .08f
            for (row in 0..1) {
                val zz = if (row == 0) 1.05f else 1.75f
                val big = (row + k) % 2 == 0
                val s0 = if (big) u0 else u0 + .12f; val s1 = if (big) u1 else u1 - .12f
                wb(wall, s0, s1, .02f, .07f, zz, zz + .58f, DARK)
                scene.glow = true
                wq(wall, s0 + .03f, s1 - .03f, .072f, zz + .03f, zz + .55f, SCREENS[(seed + k * 2 + row) % SCREENS.size])
                // something on screen: a darker band and a bright spot
                wq(wall, s0 + .03f, s1 - .03f, .074f, zz + .03f, zz + .2f, scene.mix(SCREENS[(seed + k + row + 1) % SCREENS.size], 0xFF000000.toInt(), .4f))
                scene.glow = false
            }
        }
        // a shelf of boxed gadgets underneath
        wb(wall, a + .05f, b - .05f, 0f, .3f, .82f, .85f, WHITE)
        var u = a + .12f; var k = 0
        while (u < b - .25f) { val col = BRIGHT[(seed + k++) % BRIGHT.size]; wb(wall, u, u + .18f, .06f, .24f, .85f, .97f, WHITE, col); u += .26f }
    }

    private fun clothesRail(wall: Int, a: Float, b: Float, seed: Int) {
        // the rail on two brackets, garments on hangers, folded stacks on a shelf above
        wb(wall, a + .1f, a + .14f, 0f, .32f, 1.66f, 1.7f, STEEL)
        wb(wall, b - .14f, b - .1f, 0f, .32f, 1.66f, 1.7f, STEEL)
        wb(wall, a + .1f, b - .1f, .28f, .32f, 1.62f, 1.66f, STEEL)
        var u = a + .2f; var k = 0
        while (u < b - .22f) {
            val col = GARMENTS[((h(seed, k) * 997).toInt()) % GARMENTS.size]
            val long = h(k, seed) > .6f
            wb(wall, u, u + .035f, .14f, .44f, if (long) .78f else 1.08f, 1.58f, scene.mix(col, 0xFFFFFFFF.toInt(), .15f), col)
            u += .075f; k++
        }
        wb(wall, a, b, 0f, .34f, 2.02f, 2.05f, WOOD_L, WOOD)
        u = a + .1f; k = 0
        while (u < b - .35f) {
            val col = GARMENTS[(seed + k++) % GARMENTS.size]
            for (j in 0 until 3) wb(wall, u, u + .3f, .05f, .3f, 2.05f + j * .055f, 2.1f + j * .055f, scene.mix(col, 0xFFFFFFFF.toInt(), .2f), col)
            u += .38f
        }
    }

    private fun mirror(wall: Int, a: Float, b: Float) {
        val frame = c(0xFF2E3238)
        wb(wall, a + .1f, b - .1f, 0f, .045f, .2f, 2.3f, frame)
        wq(wall, a + .16f, b - .16f, .046f, .26f, 2.24f, GLASS)
    }

    private fun station(wall: Int, a: Float, b: Float, seed: Int) {
        // a salon mirror ringed with bulbs over a counter of products
        val m0 = (a + b) / 2 - .5f; val m1 = (a + b) / 2 + .5f
        wb(wall, m0 - .06f, m1 + .06f, 0f, .04f, 1.02f, 2.08f, WHITE)
        wq(wall, m0, m1, .041f, 1.08f, 2.02f, GLASS)
        scene.glow = true
        for (k in 0 until 6) {
            val z = 1.12f + k * .17f
            wb(wall, m0 - .05f, m0 - .01f, .04f, .08f, z, z + .05f, c(0xFFFFF3C4))
            wb(wall, m1 + .01f, m1 + .05f, .04f, .08f, z, z + .05f, c(0xFFFFF3C4))
        }
        scene.glow = false
        wb(wall, a + .05f, b - .05f, 0f, .32f, .9f, .95f, WHITE, c(0xFFD9D9D6))
        var u = a + .12f; var k = 0
        while (u < b - .12f) {
            val col = BRIGHT[(seed + k++) % BRIGHT.size]
            wb(wall, u, u + .05f, .1f, .16f, .95f, 1.08f + h(seed, k) * .08f, col)
            u += .11f
        }
    }

    private fun planters(wall: Int, a: Float, b: Float, seed: Int) {
        for (s in 0 until 3) {
            val z = .7f + s * .55f
            wb(wall, a, b, 0f, .3f, z - .04f, z, WOOD_L, WOOD)
            var u = a + .15f; var k = 0
            while (u < b - .15f) {
                val pot = if (h(seed, k) > .5f) c(0xFFB5654A) else c(0xFFE9E3D6)
                wb(wall, u - .09f, u + .09f, .06f, .24f, z, z + .16f, pot)
                val bloom = BRIGHT[(seed + s * 3 + k) % BRIGHT.size]
                scene.blob(wx(wall, u, .15f), wy(wall, u, .15f), z + .24f, .13f, c(0xFF4E8C3A), squash = .8f)
                scene.blob(wx(wall, u + .04f, .18f), wy(wall, u + .04f, .18f), z + .3f, .07f, bloom)
                scene.blob(wx(wall, u - .05f, .12f), wy(wall, u - .05f, .12f), z + .28f, .06f, bloom)
                u += .32f; k++
            }
        }
    }

    private fun cafeWall(wall: Int, a: Float, b: Float, seed: Int) {
        // a chalkboard with the day's specials, and a shelf of mugs and coffee bags
        wb(wall, a + .1f, b - .1f, 0f, .04f, 1.35f, 2.3f, WOOD)
        wq(wall, a + .15f, b - .15f, .041f, 1.4f, 2.25f, c(0xFF2B3330))
        for (k in 0 until 6) {
            val z = 2.1f - k * .13f
            wq(wall, a + .3f, a + .3f + (.35f + .55f * h(seed, k)) * (b - a - .7f), .043f, z, z + .03f, c(0xFFE8E4DA))
            wq(wall, b - .45f, b - .3f, .043f, z, z + .03f, c(0xFFF2C94C))
        }
        wb(wall, a, b, 0f, .26f, 1.0f, 1.03f, WOOD_L, WOOD)
        var u = a + .1f; var k = 0
        while (u < b - .15f) {
            if (k % 3 == 2) wb(wall, u, u + .14f, .06f, .2f, 1.03f, 1.28f, c(0xFF6D4C41), c(0xFF5A3B2E))
            else wb(wall, u, u + .09f, .08f, .17f, 1.03f, 1.14f, WHITE, BRIGHT[(seed + k) % BRIGHT.size])
            u += .17f; k++
        }
    }

    private fun posters(wall: Int, a: Float, b: Float, seed: Int) {
        val n = if (b - a > 1.9f) 2 else 1
        val w = (b - a) / n
        for (k in 0 until n) {
            val u0 = a + k * w + .15f; val u1 = a + (k + 1) * w - .15f
            val bg = BRIGHT[(seed * 3 + k) % BRIGHT.size]
            val art = BRIGHT[(seed * 3 + k + 3) % BRIGHT.size]
            wb(wall, u0 - .04f, u1 + .04f, 0f, .03f, 1.1f, 2.45f, DARK)
            wq(wall, u0, u1, .031f, 1.14f, 2.41f, scene.mix(bg, 0xFF000000.toInt(), .25f))
            // a bold shape and a title strip
            wq(wall, u0 + .12f, u1 - .12f, .033f, 1.55f, 2.2f, art)
            wq(wall, u0 + .1f, u1 - .1f, .033f, 1.22f, 1.38f, WHITE)
        }
    }

    private fun neon(wall: Int, a: Float, b: Float, seed: Int) {
        // a speaker cabinet hung on the wall and neon strips above and below
        val m = (a + b) / 2
        wb(wall, m - .3f, m + .3f, 0f, .4f, 1.3f, 2.2f, DARK)
        for (k in 0..1) wdisc(wall, m, 1.55f + k * .4f, .17f - k * .06f, .401f, c(0xFF15161A))
        scene.glow = true
        val col = SCREENS[seed % SCREENS.size]
        wb(wall, a, b, 0f, .05f, 2.5f, 2.55f, col)
        wb(wall, a, b, 0f, .05f, .32f, .36f, SCREENS[(seed + 1) % SCREENS.size])
        scene.glow = false
    }

    private fun safety(wall: Int, a: Float, b: Float, seed: Int) {
        // hazard stripes at bumper height, a sign, and a fire extinguisher
        var u = a
        var k = 0
        while (u < b) {
            wq(wall, u, minOf(u + .2f, b), .012f, .85f, 1.05f, if (k % 2 == 0) c(0xFFF2C230) else c(0xFF1C1C1E))
            u += .2f; k++
        }
        wb(wall, a + .2f, a + .9f, 0f, .02f, 1.5f, 2.0f, if (seed % 2 == 0) c(0xFF27AE60) else c(0xFF2F5FA8))
        wq(wall, a + .27f, a + .83f, .021f, 1.6f, 1.66f, WHITE)
        wq(wall, a + .27f, a + .7f, .021f, 1.75f, 1.81f, WHITE)
        wb(wall, b - .4f, b - .26f, .03f, .17f, .35f, .85f, c(0xFFC0392B))
        wb(wall, b - .37f, b - .29f, .08f, .12f, .85f, .95f, DARK)
    }

    private fun homeWall(wall: Int, a: Float, b: Float, seed: Int) {
        // framed pictures over a floating shelf with books and a plant
        val m = (a + b) / 2
        val frames = if (b - a > 1.5f) 2 else 1
        for (k in 0 until frames) {
            val c0 = if (frames == 1) m else if (k == 0) m - .45f else m + .45f
            val hw = if (k == 0) .32f else .24f
            val z0 = if (k == 0) 1.5f else 1.62f
            wb(wall, c0 - hw - .04f, c0 + hw + .04f, 0f, .03f, z0 - .04f, z0 + hw * 1.6f + .04f, if (seed % 2 == 0) WOOD_D else DARK)
            wq(wall, c0 - hw, c0 + hw, .031f, z0, z0 + hw * 1.6f, WHITE)
            wq(wall, c0 - hw + .06f, c0 + hw - .06f, .032f, z0 + .06f, z0 + hw * 1.6f - .06f, BRIGHT[(seed + k * 3) % BRIGHT.size])
            wq(wall, c0 - hw + .06f, c0 + hw - .06f, .033f, z0 + .06f, z0 + hw * .6f, scene.mix(BRIGHT[(seed + k * 3 + 1) % BRIGHT.size], 0xFFFFFFFF.toInt(), .3f))
        }
        wb(wall, a + .1f, b - .1f, 0f, .24f, 1.12f, 1.15f, WOOD_L, WOOD)
        var u = a + .18f; var k = 0
        while (u < m - .1f) { val col = SPINES[(seed + k++) % SPINES.size]; wb(wall, u, u + .05f, .04f, .2f, 1.15f, 1.38f, col); u += .055f }
        scene.blob(wx(wall, b - .35f, .12f), wy(wall, b - .35f, .12f), 1.32f, .14f, c(0xFF4E8C3A))
        wb(wall, b - .42f, b - .28f, .05f, .19f, 1.15f, 1.27f, c(0xFFE9E3D6))
    }

    private fun promo(wall: Int, a: Float, b: Float, seed: Int) {
        // a sale banner over a shelf of brightly boxed groceries
        wb(wall, a + .1f, b - .1f, 0f, .02f, 1.85f, 2.45f, c(0xFFEB5757))
        wq(wall, a + .2f, b - .2f, .021f, 2.2f, 2.34f, WHITE)
        wq(wall, a + .2f, (a + b) / 2, .021f, 1.97f, 2.08f, c(0xFFF2C94C))
        for (s in 0 until 2) {
            val z = .95f + s * .4f
            wb(wall, a, b, 0f, .3f, z - .03f, z, STEEL, c(0xFF8E959C))
            var u = a + .06f; var k = 0
            while (u < b - .2f) {
                val col = BRIGHT[((h(seed + s, k++) * 997).toInt()) % BRIGHT.size]
                wb(wall, u, u + .14f, .05f, .2f, z, z + .26f, scene.mix(col, 0xFFFFFFFF.toInt(), .35f), col)
                u += .17f
            }
        }
    }

    private fun crates(wall: Int, a: Float, b: Float, seed: Int) {
        val fruit = intArrayOf(c(0xFFD93A2B), c(0xFFF2C94C), c(0xFFF2994A), c(0xFF6FCF97), c(0xFF8E2442))
        for (s in 0 until 3) {
            val z = .45f + s * .5f
            var u = a + .05f; var k = 0
            while (u < b - .45f) {
                // an open wooden crate tipped toward you, heaped with fruit
                wb(wall, u, u + .45f, .02f, .36f, z, z + .05f, WOOD_L)
                wb(wall, u, u + .45f, .32f, .36f, z, z + .18f, WOOD_L, WOOD)
                wb(wall, u, u + .45f, .02f, .06f, z, z + .3f, WOOD)
                val col = fruit[(seed + s + k) % fruit.size]
                for (j in 0 until 5) scene.blob(wx(wall, u + .07f + j * .08f, .2f), wy(wall, u + .07f + j * .08f, .2f), z + .12f + (j % 2) * .04f, .05f, col)
                u += .5f; k++
            }
        }
    }

    private fun menuBoard(a: Float, b: Float) {
        // three backlit panels with pictures of the food and price strips
        wb(N, a - .06f, b + .06f, 0f, .06f, 1.98f, 2.84f, DARK)
        val w = (b - a) / 3
        val food = intArrayOf(c(0xFFD9822B), c(0xFFF2C94C), c(0xFFB0303A))
        for (k in 0 until 3) {
            val u0 = a + k * w + .05f; val u1 = a + (k + 1) * w - .05f
            scene.glow = true
            wq(N, u0, u1, .061f, 2.03f, 2.79f, c(0xFFFFF4D6))
            scene.glow = false
            wq(N, u0 + .08f, u1 - .08f, .063f, 2.42f, 2.72f, food[k])
            for (j in 0 until 3) wq(N, u0 + .08f, u0 + .08f + (u1 - u0 - .16f) * (.5f + .15f * j), .063f, 2.1f + j * .1f, 2.15f + j * .1f, DARK)
        }
    }

    private fun radiators(sc: InteriorScene) {
        var x = 1.5f
        while (x + 1.4f < sc.w - .5f) {
            val t0 = x.toInt(); val t1 = (x + 1.4f).toInt()
            if (!hidesWindow(x, x + 1.4f) && (t0..t1).none { it in 0 until sc.w && occ[it] }) {
                wb(N, x + .1f, x + 1.3f, .02f, .1f, .22f, .78f, WHITE, c(0xFFE4E4E0))
                var u = x + .16f
                while (u < x + 1.26f) { wb(N, u, u + .03f, .1f, .105f, .26f, .74f, c(0xFFD8D8D4)); u += .09f }
            }
            x += 3.5f
        }
    }

    private fun plant(x: Float, y: Float, seed: Int) {
        val pot = if (seed % 2 == 0) c(0xFFE9E3D6) else c(0xFF3A3F45)
        scene.box(x - .2f, y - .2f, 0f, x + .2f, y + .2f, .42f, scene.mix(pot, 0xFF000000.toInt(), .1f), pot)
        scene.box(x - .17f, y - .17f, .4f, x + .17f, y + .17f, .43f, c(0xFF4A3424), c(0xFF4A3424))
        val m = scene.material
        scene.material = Mat.FOLIAGE
        val leaf = if (seed % 3 == 0) c(0xFF3F7F3A) else c(0xFF4E8C3A)
        scene.blob(x, y, .9f, .33f, leaf, squash = 1.1f)
        scene.blob(x + .14f, y - .1f, 1.25f, .24f, scene.mix(leaf, 0xFFFFFFFF.toInt(), .12f))
        scene.blob(x - .12f, y + .12f, 1.4f, .2f, leaf)
        scene.material = m
    }

    private fun clock(wall: Int, u: Float) {
        wdisc(wall, u, 2.45f, .2f, .03f, DARK, 12)
        wdisc(wall, u, 2.45f, .17f, .035f, WHITE, 12)
        wq(wall, u - .01f, u + .01f, .037f, 2.45f, 2.58f, DARK)
        wq(wall, u, u + .1f, .037f, 2.44f, 2.46f, DARK)
    }

    private fun camera(x: Float, y: Float) {
        scene.box(x - .06f, y - .06f, 2.78f, x + .06f, y + .06f, 2.9f, WHITE, WHITE)
        scene.blob(x + (if (x < 1f) .08f else -.08f), y + .08f, 2.74f, .07f, DARK)
    }
}

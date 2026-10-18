package com.hustlecity.game.render

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.c
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Architectural styles. Every building gets one from its kind, neighborhood and a stable hash. */
enum class Arch { WALKUP, BROWNSTONE, DECO, GLASS, CLASSIC, MODERN, INDUSTRIAL, SHOP }

/**
 * Draws buildings with personality: brick walk-ups with fire escapes and water towers, brownstones with stoops,
 * art deco towers with setbacks and spires, glass skyscrapers, stone offices, modern blocks with balconies,
 * industrial sheds with sawtooth roofs and loading docks, and corner shops. Landmarks get their own look,
 * and storefronts dress up for their business. Detail fades out with distance.
 */
class BuildingPainter(private val r: WorldRenderer, private val e: GameEngine) {
    private val scene get() = r.scene
    private val proj get() = r.proj

    private var styles: Array<Arch> = emptyArray()
    private var styleCity: City? = null

    private val px = FloatArray(16); private val py = FloatArray(16); private val pz = FloatArray(16)

    companion object {
        private val IRON = c(0xFF2A2D31)
        private val STONE = intArrayOf(c(0xFFD9CFBF), c(0xFFE6DDD0), c(0xFFCFC3AE), c(0xFFBFB6A8))
        private val BROWNSTONE = intArrayOf(c(0xFF6B4436), c(0xFF7A4B3A), c(0xFF5E3D31), c(0xFF83574A))
        private val GLASS_TINTS = intArrayOf(c(0xFF4F7FA6), c(0xFF3E6E8E), c(0xFF5B8DA8), c(0xFF46647E), c(0xFF6A90A8))
        private val MODERN = intArrayOf(c(0xFFE8E6E1), c(0xFFD5D8DC), c(0xFFBFC5CB), c(0xFFF0EDE6))
        private val METAL = intArrayOf(c(0xFF8A9199), c(0xFF9AA3AA), c(0xFF7E878F), c(0xFFA8B0B6))
        private val AD_COLORS = intArrayOf(c(0xFFEB5757), c(0xFF2F80ED), c(0xFFF2C94C), c(0xFF27AE60), c(0xFF9B51E0), c(0xFFF2994A))
    }

    fun hash(a: Int, b: Int, c: Int): Int {
        var h = a * 73856093 xor b * 19349663 xor c * 83492791
        h = h xor (h ushr 13)
        return h and 0x7fffffff
    }

    fun style(b: Building): Arch {
        if (styleCity !== e.city) {
            styleCity = e.city
            styles = Array(e.city.buildings.size) { pick(e.city.buildings[it]) }
        }
        return styles.getOrElse(b.id) { Arch.MODERN }
    }

    private fun pick(b: Building): Arch {
        val h = hash(b.id, 7, 11) % 100
        return when (b.kind) {
            BKind.WAREHOUSE -> Arch.INDUSTRIAL
            BKind.OFFICE -> when {
                b.height > 46f -> if (h < 45) Arch.DECO else Arch.GLASS
                b.height > 28f -> if (h < 40) Arch.GLASS else if (h < 75) Arch.CLASSIC else Arch.DECO
                else -> if (h < 55) Arch.CLASSIC else Arch.MODERN
            }
            BKind.RESIDENTIAL -> when {
                b.height < 20f && (b.neighborhood == 5 || b.neighborhood == 0) && h < 55 -> Arch.BROWNSTONE
                b.height > 34f -> if (h < 50) Arch.MODERN else Arch.DECO
                b.neighborhood == 3 || b.neighborhood == 4 -> if (h < 45) Arch.MODERN else Arch.WALKUP
                else -> if (h < 80) Arch.WALKUP else Arch.MODERN
            }
            BKind.RETAIL -> when {
                b.height < 11f -> Arch.SHOP
                b.neighborhood == 2 -> Arch.INDUSTRIAL
                h < 55 -> Arch.WALKUP
                h < 80 -> Arch.MODERN
                else -> Arch.CLASSIC
            }
            else -> Arch.CLASSIC
        }
    }

    /** Wall color for the style (brownstone, glass, concrete…) or the city's own facade palette. */
    fun facadeColor(b: Building, base: Int): Int = when (style(b)) {
        Arch.BROWNSTONE -> BROWNSTONE[b.id % BROWNSTONE.size]
        Arch.GLASS -> GLASS_TINTS[b.id % GLASS_TINTS.size]
        Arch.MODERN -> MODERN[b.id % MODERN.size]
        Arch.CLASSIC -> STONE[b.id % STONE.size]
        Arch.INDUSTRIAL -> if (hash(b.id, 3, 3) % 2 == 0) base else METAL[b.id % METAL.size]
        else -> base
    }

    private fun trim(b: Building): Int = when (style(b)) {
        Arch.BROWNSTONE -> c(0xFF3E2A22)
        Arch.WALKUP, Arch.SHOP -> STONE[(b.id / 3) % STONE.size]
        Arch.DECO -> c(0xFFE9DFC9)
        Arch.GLASS -> c(0xFFB9C4CE)
        Arch.MODERN -> c(0xFF5D6670)
        Arch.CLASSIC -> c(0xFFF1EBDF)
        Arch.INDUSTRIAL -> c(0xFF4E4F52)
    }

    // ------------------------------------------------------------------ geometry helpers

    /** Quad on a face of the box [x0,y0]-[x1,y1]: face 0 north, 1 south, 2 west, 3 east; u runs along the face. */
    fun faceQuad(x0: Float, y0: Float, x1: Float, y1: Float, face: Int, u0: Float, u1: Float, z0: Float, z1: Float, color: Int, out: Float = .04f) {
        when (face) {
            0 -> { val y = y0 - out; scene.quad(x1 - u0, y, z0, x1 - u1, y, z0, x1 - u1, y, z1, x1 - u0, y, z1, color) }
            1 -> { val y = y1 + out; scene.quad(x0 + u0, y, z0, x0 + u1, y, z0, x0 + u1, y, z1, x0 + u0, y, z1, color) }
            2 -> { val x = x0 - out; scene.quad(x, y0 + u0, z0, x, y0 + u1, z0, x, y0 + u1, z1, x, y0 + u0, z1, color) }
            else -> { val x = x1 + out; scene.quad(x, y1 - u0, z0, x, y1 - u1, z0, x, y1 - u1, z1, x, y1 - u0, z1, color) }
        }
    }

    /** A box sticking out of a face by [depth] (balconies, sills, signs, stoops). */
    fun faceBox(x0: Float, y0: Float, x1: Float, y1: Float, face: Int, u0: Float, u1: Float, z0: Float, z1: Float, depth: Float, top: Int, side: Int) {
        when (face) {
            0 -> scene.box(x1 - u1, y0 - depth, z0, x1 - u0, y0, z1, top, side)
            1 -> scene.box(x0 + u0, y1, z0, x0 + u1, y1 + depth, z1, top, side)
            2 -> scene.box(x0 - depth, y0 + u0, z0, x0, y0 + u1, z1, top, side)
            else -> scene.box(x1, y1 - u1, z0, x1 + depth, y1 - u0, z1, top, side)
        }
    }

    /** A box on a face spanning [o0, o1] outward from the wall (railings, rails, bay windows). */
    fun faceSlab(x0: Float, y0: Float, x1: Float, y1: Float, face: Int, u0: Float, u1: Float, z0: Float, z1: Float, o0: Float, o1: Float, top: Int, side: Int) {
        when (face) {
            0 -> scene.box(x1 - u1, y0 - o1, z0, x1 - u0, y0 - o0, z1, top, side)
            1 -> scene.box(x0 + u0, y1 + o0, z0, x0 + u1, y1 + o1, z1, top, side)
            2 -> scene.box(x0 - o1, y0 + u0, z0, x0 - o0, y0 + u1, z1, top, side)
            else -> scene.box(x1 + o0, y1 - u1, z0, x1 + o1, y1 - u0, z1, top, side)
        }
    }

    /** Writes the world point at [u] along the face, [out] from the wall and height [z] into slot [i]. */
    private fun pt(x0: Float, y0: Float, x1: Float, y1: Float, face: Int, u: Float, out: Float, z: Float, i: Int) {
        when (face) {
            0 -> { px[i] = x1 - u; py[i] = y0 - out }
            1 -> { px[i] = x0 + u; py[i] = y1 + out }
            2 -> { px[i] = x0 - out; py[i] = y0 + u }
            else -> { px[i] = x1 + out; py[i] = y1 - u }
        }
        pz[i] = z
    }

    /** A quad standing at right angles to the face at [u] (balcony sides, stoop railings). Corners are (out, z). */
    private fun sideQuad(x0: Float, y0: Float, x1: Float, y1: Float, face: Int, u: Float,
                         oa: Float, za: Float, ob: Float, zb: Float, oc: Float, zc: Float, od: Float, zd: Float, color: Int) {
        pt(x0, y0, x1, y1, face, u, oa, za, 0); pt(x0, y0, x1, y1, face, u, ob, zb, 1)
        pt(x0, y0, x1, y1, face, u, oc, zc, 2); pt(x0, y0, x1, y1, face, u, od, zd, 3)
        scene.poly(4, px, py, pz, color)
    }

    private fun faceLen(x0: Float, y0: Float, x1: Float, y1: Float, face: Int) = if (face <= 1) x1 - x0 else y1 - y0

    private fun shadeOf(face: Int) = when (face) { 0 -> scene.sideShade(0f, -1f); 1 -> scene.sideShade(0f, 1f); 2 -> scene.sideShade(-1f, 0f); else -> scene.sideShade(1f, 0f) }

    /** Faces of a box the camera can see. */
    private inline fun visibleFaces(x0: Float, y0: Float, x1: Float, y1: Float, f: (Int) -> Unit) {
        if (proj.camY < y0) f(0)
        if (proj.camY > y1) f(1)
        if (proj.camX < x0) f(2)
        if (proj.camX > x1) f(3)
    }

    fun cylinder(cx: Float, cy: Float, rad: Float, z0: Float, z1: Float, sides: Int, color: Int, top: Int = color) =
        scene.cylinder(cx, cy, rad, z0, z1, sides, color, top)

    fun cone(cx: Float, cy: Float, rad: Float, z0: Float, z1: Float, sides: Int, color: Int) = scene.cone(cx, cy, rad, z0, z1, sides, color)

    // ------------------------------------------------------------------ main entry

    /**
     * Draws a building. [roof]/[facadeIn] already include the style, map-mode and selection colors; [d0] is the
     * distance to the camera. Without [detail] only the massing is drawn.
     */
    fun draw(b: Building, roof: Int, facadeIn: Int, d0: Float, detail: Boolean) {
        if (b.kind == BKind.GAS) { gasStation(b, d0); return }
        val st = style(b)
        val facade = facadeIn
        val h = b.height
        val q = e.settings.quality
        val near = detail && d0 < (when (q) { 0 -> 80f; 1 -> 125f; else -> 170f })
        when {
            st == Arch.DECO && detail && h > 30f -> decoMassing(b, roof, facade, d0, near)
            st == Arch.INDUSTRIAL && detail && d0 < 260f && b.width > 10f -> industrialMassing(b, roof, facade, d0, near)
            else -> scene.box(b.minX, b.minY, 0f, b.maxX, b.maxY, h, roof, facade)
        }
        if (!detail) return
        if (st != Arch.DECO && st != Arch.INDUSTRIAL) roofline(b, st, d0, near)
        if (d0 > 330f) return
        visibleFaces(b.minX, b.minY, b.maxX, b.maxY) { face -> facade(b, st, face, d0, near) }
        if (d0 < 300f && q >= 1) rooftop(b, st, d0)
        b.placeId?.let { landmark(b, it, d0) }
    }

    // ------------------------------------------------------------------ massing

    private fun decoMassing(b: Building, roof: Int, facade: Int, d0: Float, near: Boolean) {
        val h = b.height
        val t1 = h * .62f; val t2 = h * .84f
        val i1 = min(2.6f, min(b.width, b.depth) * .14f); val i2 = i1 * 2f
        val tr = trim(b)
        scene.box(b.minX, b.minY, 0f, b.maxX, b.maxY, t1, roof, facade)
        scene.box(b.minX + i1, b.minY + i1, t1, b.maxX - i1, b.maxY - i1, t2, roof, facade)
        scene.box(b.minX + i2, b.minY + i2, t2, b.maxX - i2, b.maxY - i2, h, roof, facade)
        // crown: stepped cap and a spire
        val cx = b.cx; val cy = b.cy
        val cw = min(b.width, b.depth) * .5f - i2
        scene.box(cx - cw * .6f, cy - cw * .6f, h, cx + cw * .6f, cy + cw * .6f, h + 2.2f, tr, tr)
        scene.box(cx - cw * .35f, cy - cw * .35f, h + 2.2f, cx + cw * .35f, cy + cw * .35f, h + 4.2f, tr, tr)
        if (hash(b.id, 1, 2) % 3 != 0) scene.box(cx - .25f, cy - .25f, h + 4.2f, cx + .25f, cy + .25f, h + 4.2f + h * .14f, c(0xFFCFD6DC), c(0xFFB8C0C8))
        if (d0 > 330f) return
        // vertical piers on every tier, and the crown lit at night
        val night = scene.daylight < .5f
        for (tier in 0..2) {
            val ins = if (tier == 0) 0f else if (tier == 1) i1 else i2
            val z0 = if (tier == 0) 0f else if (tier == 1) t1 else t2
            val z1 = if (tier == 0) t1 else if (tier == 1) t2 else h
            val x0 = b.minX + ins; val y0 = b.minY + ins; val x1 = b.maxX - ins; val y1 = b.maxY - ins
            visibleFaces(x0, y0, x1, y1) { face ->
                val shade = shadeOf(face)
                val len = faceLen(x0, y0, x1, y1, face)
                val glass = scene.lit(WorldRenderer.GLASS, shade)
                val pier = scene.lit(tr, shade)
                val floors = ((z1 - z0 - (if (tier == 0) 4.4f else .4f)) / 3.3f).toInt()
                val baseZ = if (tier == 0) 4.4f else z0 + .2f
                // window columns between piers
                val cols = (len / 2.4f).toInt().coerceAtLeast(1)
                val cw2 = len / cols
                for (cIdx in 0 until cols) {
                    val u0 = cIdx * cw2 + .5f; val u1 = (cIdx + 1) * cw2 - .5f
                    if (near && floors <= 18) {
                        for (f in 0 until floors) {
                            val wz = baseZ + f * 3.3f + .7f
                            val col = if (night) (if (hash(b.id, f * 64 + cIdx, face + tier * 4) % 100 < 38) WorldRenderer.LIT else scene.lit(WorldRenderer.GLASS_NIGHT, 1f)) else glass
                            faceQuad(x0, y0, x1, y1, face, u0, u1, wz, wz + 1.9f, col)
                        }
                    } else faceQuad(x0, y0, x1, y1, face, u0, u1, baseZ, z1 - .5f, if (night) scene.lit(WorldRenderer.GLASS_NIGHT, 1f) else glass)
                }
                for (cIdx in 0..cols) {
                    val u = cIdx * cw2
                    faceQuad(x0, y0, x1, y1, face, (u - .25f).coerceAtLeast(0f), (u + .25f).coerceAtMost(len), z0, z1, pier, .06f)
                }
                if (tier == 2 && night) faceQuad(x0, y0, x1, y1, face, .3f, len - .3f, z1 - 1.2f, z1 - .2f, scene.withAlpha(c(0xFFFFD98A), .9f), .08f)
            }
        }
    }

    private fun industrialMassing(b: Building, roof: Int, facade: Int, d0: Float, near: Boolean) {
        val h = b.height
        val wall = h - 2.6f
        scene.box(b.minX, b.minY, 0f, b.maxX, b.maxY, wall, roof, facade)
        // sawtooth roof: north-facing glass teeth running east-west
        val teeth = (b.depth / 6f).toInt().coerceIn(2, 6)
        val tw = b.depth / teeth
        val roofCol = scene.lit(scene.snowy(c(0xFF6F7377)), 1f)
        val glass = scene.lit(c(0xFF9FC4DD), scene.sideShade(0f, -1f))
        val sideCol = scene.lit(facade, scene.sideShade(1f, 0f))
        for (t in 0 until teeth) {
            val y0 = b.minY + t * tw; val y1 = y0 + tw
            // vertical glazing on the north side of each tooth
            if (proj.camY < y0 + .1f) scene.quad(b.maxX, y0, wall, b.minX, y0, wall, b.minX, y0, h, b.maxX, y0, h, glass)
            // sloped roof down to the south
            scene.quad(b.minX, y0, h, b.maxX, y0, h, b.maxX, y1, wall, b.minX, y1, wall, roofCol)
            // gable ends
            if (proj.camX > b.maxX) { px[0] = b.maxX; py[0] = y0; pz[0] = wall; px[1] = b.maxX; py[1] = y1; pz[1] = wall; px[2] = b.maxX; py[2] = y0; pz[2] = h; scene.poly(3, px, py, pz, sideCol) }
            if (proj.camX < b.minX) { px[0] = b.minX; py[0] = y0; pz[0] = wall; px[1] = b.minX; py[1] = y1; pz[1] = wall; px[2] = b.minX; py[2] = y0; pz[2] = h; scene.poly(3, px, py, pz, sideCol) }
        }
        // smokestack
        if (hash(b.id, 9, 9) % 3 == 0) {
            val sx = b.minX + 2.5f; val sy = b.maxY - 2.5f
            cylinder(sx, sy, 1f, wall, h + 9f, 8, c(0xFF8E4B3A), c(0xFF2A2A2A))
            cylinder(sx, sy, 1.12f, h + 7.6f, h + 8.2f, 8, c(0xFF5B5B5B))
        }
    }

    // ------------------------------------------------------------------ roofline and rooftop

    private fun roofline(b: Building, st: Arch, d0: Float, near: Boolean) {
        val h = b.height
        val tr = trim(b)
        when (st) {
            Arch.WALKUP, Arch.BROWNSTONE, Arch.CLASSIC -> {
                // cornice sticking out over the street-facing walls
                val o = if (st == Arch.CLASSIC) .45f else .32f
                scene.box(b.minX, b.minY - o, h - .7f, b.maxX, b.maxY + o, h, tr, tr)
                if (st == Arch.CLASSIC) scene.box(b.minX, b.minY - .1f, 0f, b.maxX, b.maxY + .1f, min(7f, h * .3f), tr, tr) // stone base
            }
            Arch.SHOP, Arch.MODERN -> {
                // parapet around the roof
                if (d0 < 220f) {
                    val p = .25f; val ph = .8f
                    val col = tr
                    scene.box(b.minX, b.minY, h, b.maxX, b.minY + p, h + ph, col, col)
                    scene.box(b.minX, b.maxY - p, h, b.maxX, b.maxY, h + ph, col, col)
                    scene.box(b.minX, b.minY, h, b.minX + p, b.maxY, h + ph, col, col)
                    scene.box(b.maxX - p, b.minY, h, b.maxX, b.maxY, h + ph, col, col)
                }
            }
            Arch.GLASS -> {
                // mechanical penthouse
                val ins = min(b.width, b.depth) * .2f
                scene.box(b.minX + ins, b.minY + ins, h, b.maxX - ins, b.maxY - ins, h + 3.4f, c(0xFF7D868F), c(0xFF6B737B))
            }
            else -> {}
        }
    }

    private fun rooftop(b: Building, st: Arch, d0: Float) {
        val h = b.height + (if (st == Arch.SHOP || st == Arch.MODERN) 0f else 0f)
        val k = hash(b.id, 5, 17)
        // original rooftop boxes (AC units)
        for (rb in b.roofBoxes) if (st != Arch.GLASS && st != Arch.DECO && st != Arch.INDUSTRIAL) {
            scene.box(rb.x0, rb.y0, h, rb.x1, rb.y1, h + rb.h, c(0xFFA7ABB0), c(0xFF8C9095))
            if (d0 < 140f) scene.box(rb.x0 + .3f, rb.y0 + .3f, h + rb.h, rb.x1 - .3f, rb.y1 - .3f, h + rb.h + .12f, c(0xFF5C6066), c(0xFF5C6066))
        }
        when (st) {
            Arch.WALKUP, Arch.BROWNSTONE -> {
                // the classic New York water tower
                if (k % 3 == 0 && b.width > 9f) waterTower(b.minX + b.width * .7f, b.cy - b.depth * .15f, h)
                // chimneys
                if (d0 < 200f) {
                    val n = 1 + k % 2
                    for (i in 0 until n) {
                        val cx = b.minX + 1.6f + i * (b.width - 3.2f) / max(1, n)
                        scene.box(cx - .4f, b.maxY - 2f, h, cx + .4f, b.maxY - 1.2f, h + 1.8f, c(0xFF5A3B32), c(0xFF7A4B3E))
                    }
                }
            }
            Arch.MODERN -> {
                if (k % 2 == 0) solarPanels(b, h) else roofGarden(b, h)
            }
            Arch.GLASS -> {
                val ins = min(b.width, b.depth) * .2f
                if (k % 3 == 0) {
                    // helipad
                    val r0 = min(b.width, b.depth) * .32f
                    if (r0 > 4f) {
                        scene.disc(b.cx, b.cy, h + 3.45f, r0, r0, 14, scene.lit(c(0xFF3E4650), 1f))
                        scene.disc(b.cx, b.cy, h + 3.47f, r0 * .82f, r0 * .82f, 14, scene.lit(c(0xFFF2C94C), 1f))
                        scene.disc(b.cx, b.cy, h + 3.48f, r0 * .74f, r0 * .74f, 14, scene.lit(c(0xFF3E4650), 1f))
                        val hw = r0 * .3f
                        scene.box(b.cx - hw, b.cy - .2f, h + 3.48f, b.cx + hw, b.cy + .2f, h + 3.5f, c(0xFFFFFFFF), c(0xFFFFFFFF))
                        scene.box(b.cx - hw, b.cy - hw, h + 3.48f, b.cx - hw + .4f, b.cy + hw, h + 3.5f, c(0xFFFFFFFF), c(0xFFFFFFFF))
                        scene.box(b.cx + hw - .4f, b.cy - hw, h + 3.48f, b.cx + hw, b.cy + hw, h + 3.5f, c(0xFFFFFFFF), c(0xFFFFFFFF))
                    }
                } else {
                    // antenna with a red light
                    val ax = b.maxX - ins - 1f; val ay = b.minY + ins + 1f
                    scene.box(ax - .12f, ay - .12f, h + 3.4f, ax + .12f, ay + .12f, h + 3.4f + 9f, c(0xFFBFC6CC), c(0xFF9AA2A9))
                    if (scene.daylight < .6f || (e.realTime % 2f) < 1f) scene.box(ax - .25f, ay - .25f, h + 12.3f, ax + .25f, ay + .25f, h + 12.7f, c(0xFFFF4D4D), c(0xFFFF4D4D))
                }
            }
            Arch.CLASSIC -> {
                if (k % 2 == 0) {
                    // flagpole with a flag
                    val fx = b.cx; val fy = if (b.facesNorth) b.minY + 1.5f else b.maxY - 1.5f
                    scene.box(fx - .08f, fy - .08f, h, fx + .08f, fy + .08f, h + 7f, c(0xFFD5D8DC), c(0xFFBFC5CB))
                    val wave = sin(e.realTime * 3f + b.id) * .3f
                    scene.quad(fx + .08f, fy, h + 6.9f, fx + 2.6f, fy + wave, h + 6.9f, fx + 2.6f, fy + wave, h + 5.4f, fx + .08f, fy, h + 5.4f, scene.lit(AD_COLORS[k % AD_COLORS.size], 1f))
                }
            }
            Arch.SHOP -> {
                // rooftop billboard on some low buildings
                if (k % 4 == 0 && b.width > 10f && d0 < 260f) billboard(b, h, k)
            }
            else -> {}
        }
    }

    private fun waterTower(x: Float, y: Float, h: Float) {
        val wood = c(0xFF8B6A4E)
        val leg = c(0xFF3A3D42)
        for (dx in intArrayOf(-1, 1)) for (dy in intArrayOf(-1, 1)) {
            scene.box(x + dx * 1.3f - .1f, y + dy * 1.3f - .1f, h, x + dx * 1.3f + .1f, y + dy * 1.3f + .1f, h + 3f, leg, leg)
        }
        scene.box(x - 1.5f, y - 1.5f, h + 2.9f, x + 1.5f, y + 1.5f, h + 3.05f, leg, leg)
        cylinder(x, y, 1.8f, h + 3.05f, h + 6.6f, 10, wood, c(0xFF6E5440))
        // hoops
        cylinder(x, y, 1.84f, h + 4f, h + 4.12f, 10, leg)
        cylinder(x, y, 1.84f, h + 5.5f, h + 5.62f, 10, leg)
        cone(x, y, 2f, h + 6.6f, h + 8f, 10, c(0xFF5B5F64))
    }

    private fun solarPanels(b: Building, h: Float) {
        val rows = ((b.depth - 3f) / 2.6f).toInt().coerceIn(1, 6)
        val panel = scene.lit(c(0xFF22385A), 1f)
        val frame = c(0xFF9AA3AD)
        for (rIdx in 0 until rows) {
            val y0 = b.minY + 1.5f + rIdx * 2.6f
            scene.quad(b.minX + 1.2f, y0, h + .3f, b.maxX - 1.2f, y0, h + .3f, b.maxX - 1.2f, y0 + 1.6f, h + 1.1f, b.minX + 1.2f, y0 + 1.6f, h + 1.1f, panel)
            scene.box(b.minX + 1.2f, y0 + 1.5f, h, b.minX + 1.4f, y0 + 1.7f, h + 1.1f, frame, frame)
            scene.box(b.maxX - 1.4f, y0 + 1.5f, h, b.maxX - 1.2f, y0 + 1.7f, h + 1.1f, frame, frame)
        }
    }

    private fun roofGarden(b: Building, h: Float) {
        scene.box(b.minX + 1f, b.minY + 1f, h, b.maxX - 1f, b.maxY - 1f, h + .35f, scene.snowy(c(0xFF6DA35C)), c(0xFF8B6A4E))
        val n = ((b.width - 2f) / 4f).toInt().coerceIn(1, 5)
        for (i in 0 until n) {
            val tx = b.minX + 2.5f + i * (b.width - 5f) / max(1, n - 1).toFloat()
            scene.blob(tx, b.cy + (if (i % 2 == 0) -1.5f else 1.5f), h + 1.4f, 1.2f, scene.lit(scene.mix(c(0xFF4C8C3F), c(0xFFF2F5F8), scene.snow * .5f), 1f), 8)
        }
    }

    private fun billboard(b: Building, h: Float, k: Int) {
        val y = if (b.facesNorth) b.minY + 1.2f else b.maxY - 1.2f
        val leg = c(0xFF3A3D42)
        val x0 = b.minX + 1.5f; val x1 = b.maxX - 1.5f
        scene.box(x0 + 1f, y - .1f, h, x0 + 1.2f, y + .1f, h + 3f, leg, leg)
        scene.box(x1 - 1.2f, y - .1f, h, x1 - 1f, y + .1f, h + 3f, leg, leg)
        val ad = AD_COLORS[k % AD_COLORS.size]
        val face = if (b.facesNorth) 0 else 1
        val yy = if (face == 0) y - .12f else y + .12f
        val sh = shadeOf(face)
        scene.box(x0, y - .1f, h + 3f, x1, y + .1f, h + 7f, c(0xFF2C2F33), c(0xFF2C2F33))
        scene.quad(x0 + .2f, yy, h + 3.2f, x1 - .2f, yy, h + 3.2f, x1 - .2f, yy, h + 6.8f, x0 + .2f, yy, h + 6.8f, scene.lit(ad, sh * 1.1f))
        // a simple graphic: a band and a circle
        scene.quad(x0 + .6f, yy * 1f + (if (face == 0) -.01f else .01f), h + 3.8f, x1 - .6f, yy + (if (face == 0) -.01f else .01f), h + 3.8f,
            x1 - .6f, yy + (if (face == 0) -.01f else .01f), h + 4.4f, x0 + .6f, yy + (if (face == 0) -.01f else .01f), h + 4.4f, scene.lit(c(0xFFFFFFFF), sh))
    }

    // ------------------------------------------------------------------ facades

    private fun facade(b: Building, st: Arch, face: Int, d0: Float, near: Boolean) {
        if (st == Arch.DECO && b.height > 30f) return // drawn with the setbacks
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        val len = faceLen(x0, y0, x1, y1, face)
        val shade = shadeOf(face)
        val night = scene.daylight < .5f
        val front = (face == 0 && b.facesNorth) || (face == 1 && !b.facesNorth)
        val biz = r.bizAt(b.id)
        val rival = if (biz == null) r.rivalAt(b.id) else null
        val hasShop = (b.kind == BKind.RETAIL || b.kind == BKind.SPECIAL || biz != null || rival != null) && st != Arch.INDUSTRIAL
        val baseZ = if (hasShop) 4.2f else if (st == Arch.BROWNSTONE) 1.6f else 1.2f
        val h = if (st == Arch.INDUSTRIAL && b.width > 10f && d0 < 260f) b.height - 2.6f else b.height
        val glass = scene.lit(WorldRenderer.GLASS, shade)
        val floors = ((h - baseZ - .8f) / 3.3f).toInt()
        val tr = scene.lit(trim(b), shade)
        val lit = { f: Int, cIdx: Int -> if (hash(b.id, f * 64 + cIdx, face) % 100 < 40) WorldRenderer.LIT else scene.lit(WorldRenderer.GLASS_NIGHT, 1f) }
        when (st) {
            Arch.INDUSTRIAL -> {
                if (h > 6f) faceQuad(x0, y0, x1, y1, face, 1.5f, len - 1.5f, h - 2.6f, h - 1.4f, glass)
                // corrugated wall ribs on metal sheds
                if (near && facadeColor(b, b.facade) != b.facade) {
                    var u = .6f
                    val rib = scene.lit(c(0xFF6B737B), shade)
                    while (u < len - .3f) { faceQuad(x0, y0, x1, y1, face, u, u + .12f, 0f, h - .1f, rib, .05f); u += 1.1f }
                }
                if (front) loadingDocks(b, face, len, shade, night)
            }
            Arch.GLASS -> {
                // curtain wall: mullions and floor lines, with whole floors lit at night
                val floorsAll = ((h - 1f) / 3.3f).toInt()
                if (night) for (f in 0 until floorsAll) {
                    if (hash(b.id, f, face) % 100 < 45) faceQuad(x0, y0, x1, y1, face, .2f, len - .2f, 1f + f * 3.3f + .3f, 1f + f * 3.3f + 2.9f, scene.withAlpha(WorldRenderer.LIT, .85f))
                }
                if (near || d0 < 200f) {
                    val line = scene.lit(c(0xFFC8D3DC), shade)
                    for (f in 0..floorsAll) faceQuad(x0, y0, x1, y1, face, 0f, len, 1f + f * 3.3f, 1f + f * 3.3f + .18f, line, .05f)
                    if (near) {
                        var u = 1.5f
                        while (u < len) { faceQuad(x0, y0, x1, y1, face, u - .06f, u + .06f, 1f, h, line, .05f); u += 1.5f }
                    }
                }
            }
            Arch.MODERN -> {
                // ribbon windows
                val day = scene.lit(c(0xFF3C5266), shade)
                val segs = ((len - 1f) / 3.2f).toInt().coerceAtLeast(1)
                val sw = (len - 1f) / segs
                for (f in 0 until floors) {
                    val z0 = baseZ + f * 3.3f + .8f
                    if (!night) faceQuad(x0, y0, x1, y1, face, .5f, len - .5f, z0, z0 + 1.8f, day)
                    else for (sIdx in 0 until segs) faceQuad(x0, y0, x1, y1, face, .5f + sIdx * sw, .5f + (sIdx + 1) * sw, z0, z0 + 1.8f, lit(f, sIdx))
                    if (near) for (sIdx in 1 until segs) {
                        val u = .5f + sIdx * sw
                        faceQuad(x0, y0, x1, y1, face, u - .05f, u + .05f, z0, z0 + 1.8f, tr, .05f)
                    }
                }
                if (front && near && b.kind == BKind.RESIDENTIAL) {
                    // balconies: a slab, a glass front and glass sides, in pairs along the facade
                    val glassRail = scene.withAlpha(scene.lit(c(0xFFBFE0F2), shade), .42f)
                    val slots = ((len - 2f) / 6.5f).toInt().coerceAtLeast(1)
                    val step = (len - 2f) / slots
                    for (f in 0 until floors) {
                        val z = baseZ + f * 3.3f + .1f
                        for (k in 0 until slots) {
                            if (hash(b.id, f, k) % 5 == 0) continue
                            val u0 = 1f + k * step + step * .2f; val u1 = 1f + (k + 1) * step - step * .2f
                            faceSlab(x0, y0, x1, y1, face, u0, u1, z, z + .16f, 0f, 1.3f, tr, trim(b))
                            faceQuad(x0, y0, x1, y1, face, u0, u1, z + .16f, z + 1.05f, glassRail, 1.3f)
                            sideQuad(x0, y0, x1, y1, face, u0, 0f, z + .16f, 1.3f, z + .16f, 1.3f, z + 1.05f, 0f, z + 1.05f, glassRail)
                            sideQuad(x0, y0, x1, y1, face, u1, 0f, z + .16f, 1.3f, z + .16f, 1.3f, z + 1.05f, 0f, z + 1.05f, glassRail)
                            faceSlab(x0, y0, x1, y1, face, u0, u1, z + 1.02f, z + 1.08f, 1.25f, 1.32f, c(0xFF9AA3AD), c(0xFF9AA3AD))
                            // a plant or a chair on some balconies
                            if (hash(b.id, f + 7, k) % 3 == 0) faceSlab(x0, y0, x1, y1, face, u0 + .3f, u0 + .7f, z + .16f, z + .6f, .4f, .8f, c(0xFF8B5A3C), c(0xFF8B5A3C))
                        }
                    }
                }
            }
            else -> {
                // punched windows in columns, with stone lintels and sills up close
                val colW = if (st == Arch.BROWNSTONE) 2.6f else 3f
                val cols = ((len - 1.6f) / colW).toInt().coerceAtLeast(1)
                val start = (len - cols * colW) / 2f
                val detailed = near && floors <= 14 && e.settings.detailedWindows
                if (floors > 0) for (f in 0 until floors) {
                    val z0 = baseZ + f * 3.3f + .85f
                    val z1 = z0 + (if (st == Arch.CLASSIC || st == Arch.BROWNSTONE) 2f else 1.7f)
                    if (detailed) {
                        for (cIdx in 0 until cols) {
                            val u0 = start + cIdx * colW + .7f; val u1 = u0 + colW - 1.4f
                            faceQuad(x0, y0, x1, y1, face, u0, u1, z0, z1, if (night) lit(f, cIdx) else glass)
                            if (d0 < 110f) {
                                faceQuad(x0, y0, x1, y1, face, u0 - .15f, u1 + .15f, z1, z1 + .28f, tr, .06f)
                                faceBox(x0, y0, x1, y1, face, u0 - .1f, u1 + .1f, z0 - .14f, z0, .14f, trim(b), trim(b))
                                // a mullion splits the window
                                faceQuad(x0, y0, x1, y1, face, (u0 + u1) / 2 - .04f, (u0 + u1) / 2 + .04f, z0, z1, tr, .05f)
                            }
                        }
                    } else {
                        faceQuad(x0, y0, x1, y1, face, .7f, len - .7f, z0, z1, if (night) lit(f, 0) else glass)
                    }
                }
                if (st == Arch.CLASSIC && near) {
                    // pilasters between window columns
                    for (cIdx in 0..cols) {
                        val u = start + cIdx * colW
                        faceQuad(x0, y0, x1, y1, face, (u - .2f).coerceAtLeast(0f), (u + .2f).coerceAtMost(len), min(7f, h * .3f), h - .7f, tr, .05f)
                    }
                }
                if (st == Arch.BROWNSTONE && front && near && floors >= 2) {
                    // a bay window running up the front
                    val u0 = len * .55f; val u1 = min(len - .6f, u0 + 3f)
                    val bayTop = baseZ + floors * 3.3f
                    faceBox(x0, y0, x1, y1, face, u0, u1, baseZ + .4f, bayTop, .7f, scene.snowy(trim(b)), BROWNSTONE[b.id % BROWNSTONE.size])
                    for (f in 0 until floors) {
                        val z0 = baseZ + f * 3.3f + .85f
                        faceQuad(x0, y0, x1, y1, face, u0 + .35f, u1 - .35f, z0, z0 + 2f, if (night) lit(f, 99) else glass, .74f)
                        faceQuad(x0, y0, x1, y1, face, (u0 + u1) / 2 - .05f, (u0 + u1) / 2 + .05f, z0, z0 + 2f, tr, .76f)
                        faceSlab(x0, y0, x1, y1, face, u0 + .25f, u1 - .25f, z0 - .14f, z0, .7f, .86f, trim(b), trim(b))
                    }
                }
            }
        }
        if (!front) return
        if (hasShop) storefront(b, face, len, shade, d0, near, biz, rival)
        else if (st != Arch.INDUSTRIAL) entrance(b, st, face, len, shade, near)
    }

    private fun entrance(b: Building, st: Arch, face: Int, len: Float, shade: Float, near: Boolean) {
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        val u = len / 2
        val doorZ = if (st == Arch.BROWNSTONE) 1.5f else 0f
        faceQuad(x0, y0, x1, y1, face, u - .8f, u + .8f, doorZ, doorZ + 2.5f, scene.lit(c(0xFF3B2A22), shade), .06f)
        faceQuad(x0, y0, x1, y1, face, u - 1f, u + 1f, doorZ + 2.5f, doorZ + 2.8f, scene.lit(trim(b), shade), .07f)
        if (!near) return
        if (st == Arch.BROWNSTONE) {
            // the stoop: steps up to the parlor floor with iron railings
            for (s in 0 until 5) {
                val z = s * .3f
                faceBox(x0, y0, x1, y1, face, u - 1.1f, u + 1.1f, 0f, z + .3f, 2.6f - s * .5f, scene.snowy(c(0xFF8C6A5A)), c(0xFF6E5246))
            }
            val iron = scene.lit(IRON, 1f)
            for (side in intArrayOf(-1, 1)) {
                val ru = u + side * 1.18f
                // the handrail runs from the door down to a newel post on the sidewalk
                sideQuad(x0, y0, x1, y1, face, ru, 0f, 2.55f, 2.5f, 1.05f, 2.5f, .95f, 0f, 2.45f, iron)
                sideQuad(x0, y0, x1, y1, face, ru + .07f, 0f, 2.55f, 2.5f, 1.05f, 2.5f, .95f, 0f, 2.45f, iron)
                for (k in 0..3) {
                    val o = .4f + k * .6f; val zt = 2.5f - o * .58f
                    val step = ((2.6f - o) / .5f).toInt().coerceIn(0, 4)
                    faceSlab(x0, y0, x1, y1, face, ru, ru + .05f, step * .3f + .3f, zt, o - .025f, o + .025f, IRON, IRON)
                }
                faceSlab(x0, y0, x1, y1, face, ru - .03f, ru + .1f, 0f, 1.15f, 2.42f, 2.58f, IRON, IRON)
            }
        } else {
            // a small canopy over the door
            faceBox(x0, y0, x1, y1, face, u - 1.3f, u + 1.3f, 2.9f, 3.1f, 1.2f, scene.snowy(c(0xFF2C3E50)), c(0xFF2C3E50))
        }
        // fire escape zig-zagging down a walk-up
        if (st == Arch.WALKUP && b.kind == BKind.RESIDENTIAL && hash(b.id, 4, 4) % 2 == 0) fireEscape(b, face, len)
    }

    private fun fireEscape(b: Building, face: Int, len: Float) {
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        val floors = ((b.height - 1.2f - .8f) / 3.3f).toInt()
        val u0 = len * .12f; val u1 = u0 + 3.4f
        if (u1 > len - 1f) return
        val grille = scene.withAlpha(scene.lit(IRON, 1f), .45f)
        for (f in 1 until floors) {
            val z = 1.2f + f * 3.3f
            faceBox(x0, y0, x1, y1, face, u0, u1, z, z + .1f, 1.1f, IRON, IRON)
            // railing: a see-through grille with a top bar and corner posts
            faceQuad(x0, y0, x1, y1, face, u0, u1, z + .1f, z + .95f, grille, 1.1f)
            sideQuad(x0, y0, x1, y1, face, u0, 0f, z + .1f, 1.1f, z + .1f, 1.1f, z + .95f, 0f, z + .95f, grille)
            sideQuad(x0, y0, x1, y1, face, u1, 0f, z + .1f, 1.1f, z + .1f, 1.1f, z + .95f, 0f, z + .95f, grille)
            faceSlab(x0, y0, x1, y1, face, u0, u1, z + .95f, z + 1.01f, 1.05f, 1.11f, IRON, IRON)
            faceSlab(x0, y0, x1, y1, face, u0, u0 + .05f, z + .95f, z + 1.01f, 0f, 1.1f, IRON, IRON)
            faceSlab(x0, y0, x1, y1, face, u1 - .05f, u1, z + .95f, z + 1.01f, 0f, 1.1f, IRON, IRON)
            faceSlab(x0, y0, x1, y1, face, u0, u0 + .06f, z, z + 1f, 1.04f, 1.11f, IRON, IRON)
            faceSlab(x0, y0, x1, y1, face, u1 - .06f, u1, z, z + 1f, 1.04f, 1.11f, IRON, IRON)
            // ladder to the next platform
            if (f < floors - 1) {
                val a = if (f % 2 == 0) u0 + .3f else u1 - 1.4f
                val out = .55f
                when (face) {
                    0 -> scene.quad(x1 - a, y0 - out, z, x1 - a - 1.1f, y0 - out, z, x1 - a - .1f, y0 - out, z + 3.3f, x1 - a + 1f, y0 - out, z + 3.3f, scene.lit(IRON, 1f))
                    1 -> scene.quad(x0 + a, y1 + out, z, x0 + a + 1.1f, y1 + out, z, x0 + a + .1f, y1 + out, z + 3.3f, x0 + a - 1f, y1 + out, z + 3.3f, scene.lit(IRON, 1f))
                    else -> {}
                }
            }
        }
    }

    private fun loadingDocks(b: Building, face: Int, len: Float, shade: Float, night: Boolean) {
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        val doors = ((len - 2f) / 6f).toInt().coerceIn(1, 4)
        val step = len / doors
        for (d in 0 until doors) {
            val u = step * d + step / 2
            faceQuad(x0, y0, x1, y1, face, u - 2f, u + 2f, .9f, 4.6f, scene.lit(c(0xFFB0B6BC), shade), .05f)
            for (k in 1..6) faceQuad(x0, y0, x1, y1, face, u - 2f, u + 2f, .9f + k * .6f, .9f + k * .6f + .06f, scene.lit(c(0xFF8C9298), shade), .06f)
            // dock platform and rubber bumpers
            faceBox(x0, y0, x1, y1, face, u - 2.4f, u + 2.4f, 0f, .9f, .8f, scene.snowy(c(0xFF8A8D91)), c(0xFF6F7377))
            faceBox(x0, y0, x1, y1, face, u - 2.2f, u - 1.9f, .2f, .8f, .9f, IRON, IRON)
            faceBox(x0, y0, x1, y1, face, u + 1.9f, u + 2.2f, .2f, .8f, .9f, IRON, IRON)
            if (night) faceQuad(x0, y0, x1, y1, face, u - .3f, u + .3f, 4.9f, 5.2f, c(0xFFFFE6A8), .08f)
        }
        // canopy along the docks
        faceBox(x0, y0, x1, y1, face, .5f, len - .5f, 5.3f, 5.5f, 1.8f, scene.snowy(c(0xFF5D6670)), c(0xFF4E565E))
    }

    // ------------------------------------------------------------------ storefronts

    private fun storefront(b: Building, face: Int, len: Float, shade: Float, d0: Float, near: Boolean,
                           biz: com.hustlecity.game.model.Business?, rival: com.hustlecity.game.model.RivalBusiness?) {
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        val night = scene.daylight < .5f
        val typeId = biz?.typeId ?: rival?.typeId
        val hour = e.state.hour
        val open = when {
            biz != null -> !biz.tempClosed && BizCalc.isWithinHours(biz, hour)
            rival != null -> Catalog.type(rival.typeId).curve[hour] >= .05f
            else -> true
        }
        val brand = when {
            b.placeId != null -> Catalog.place(b.placeId!!).color
            biz != null -> biz.type.color
            rival != null -> Catalog.type(rival.typeId).color
            else -> 0
        }
        val shopGlass = if (night && open) WorldRenderer.LIT else scene.lit(WorldRenderer.GLASS_DARK, shade)
        faceQuad(x0, y0, x1, y1, face, .8f, len - .8f, .3f, 3.1f, shopGlass)
        if (near) {
            if (brand != 0) {
                // merchandise and displays behind the glass, in the shop's colors
                var u = .9f; var k = 0
                while (u + 1.6f < len - .8f) {
                    if (kotlin.math.abs(u + .8f - len / 2) > 1.3f) {
                        val col = scene.lit(scene.mix(AD_COLORS[(k + b.id) % AD_COLORS.size], brand, .55f), shade * (if (night) .65f else .95f))
                        val top = .95f + (hash(b.id, k, 3) % 3) * .25f
                        faceQuad(x0, y0, x1, y1, face, u + .25f, u + 1.45f, .55f, top, col, .045f)
                        faceQuad(x0, y0, x1, y1, face, u + .15f, u + 1.55f, .5f, .58f, scene.lit(c(0xFFE9E4DA), shade), .046f)
                    }
                    u += 1.8f; k++
                }
            } else if (b.kind == BKind.RETAIL && r.isVacant(b)) {
                // whitewashed windows on an empty shop
                faceQuad(x0, y0, x1, y1, face, .8f, len - .8f, .55f, 2.95f, scene.lit(c(0xFFD9D3C5), shade), .045f)
            }
        }
        val frame = scene.lit(c(0xFF2B2F35), shade)
        // shop window frames and the door
        if (near) {
            var u = .8f
            while (u < len - .8f) { faceQuad(x0, y0, x1, y1, face, u - .07f, u + .07f, .3f, 3.1f, frame, .05f); u += 1.8f }
            faceQuad(x0, y0, x1, y1, face, .8f, len - .8f, .3f, .55f, frame, .05f)
            faceQuad(x0, y0, x1, y1, face, .8f, len - .8f, 3f, 3.15f, frame, .05f)
        }
        faceQuad(x0, y0, x1, y1, face, len / 2 - .8f, len / 2 + .8f, 0f, 2.5f, scene.lit(c(0xFF1C2229), shade), .06f)
        // roll-down shutters when closed after dark
        if (!open && night && typeId != null) {
            faceQuad(x0, y0, x1, y1, face, .8f, len - .8f, .3f, 3.1f, scene.lit(c(0xFF8E959C), shade), .07f)
            if (near) for (k in 1..9) faceQuad(x0, y0, x1, y1, face, .8f, len - .8f, .3f + k * .3f, .3f + k * .3f + .04f, scene.lit(c(0xFF6E757C), shade), .08f)
        }
        if (brand == 0) {
            if (b.kind == BKind.RETAIL && r.isVacant(b) && d0 < 160f) r.forRentLabel(b, face)
            return
        }
        // sign band with a trim line
        faceQuad(x0, y0, x1, y1, face, .5f, len - .5f, 3.3f, 4.3f, scene.lit(brand, shade), .06f)
        faceQuad(x0, y0, x1, y1, face, .5f, len - .5f, 4.3f, 4.42f, scene.lit(c(0xFFF4F4F4), shade), .07f)
        when (typeId) {
            "nightclub" -> neon(b, face, len, open)
            "cinema", "theater" -> marquee(b, face, len, brand, night)
            "coffee", "florist", "produce", "giftshop", "bookstore", "fastfood", "supermarket" -> stripedAwning(b, face, len * .12f, len * .88f, brand)
            "jewelry", "electronics", "clothing" -> {} // sleek: no awning
            else -> awning(b, face, len * .15f, len * .85f, scene.lit(brand, 1f), scene.lit(brand, shade * .8f))
        }
        if (near && d0 < 120f) sidewalkProps(b, face, len, typeId, brand)
        if (d0 < 140f) r.shopLabel(b, face, when {
            b.placeId != null -> Catalog.place(b.placeId!!).name
            biz != null -> biz.name
            else -> rival!!.name
        })
    }

    private fun awning(b: Building, face: Int, u0: Float, u1: Float, top: Int, front: Int) {
        val out = 1.4f
        when (face) {
            0 -> {
                val y = b.minY
                scene.quad(b.maxX - u0, y, 3.2f, b.maxX - u1, y, 3.2f, b.maxX - u1, y - out, 2.7f, b.maxX - u0, y - out, 2.7f, scene.snowy(top))
                scene.quad(b.maxX - u0, y - out, 2.7f, b.maxX - u1, y - out, 2.7f, b.maxX - u1, y - out, 2.35f, b.maxX - u0, y - out, 2.35f, front)
            }
            1 -> {
                val y = b.maxY
                scene.quad(b.minX + u0, y, 3.2f, b.minX + u1, y, 3.2f, b.minX + u1, y + out, 2.7f, b.minX + u0, y + out, 2.7f, scene.snowy(top))
                scene.quad(b.minX + u0, y + out, 2.7f, b.minX + u1, y + out, 2.7f, b.minX + u1, y + out, 2.35f, b.minX + u0, y + out, 2.35f, front)
            }
            else -> {}
        }
    }

    /** Classic striped canvas awning in the brand color and white. */
    private fun stripedAwning(b: Building, face: Int, u0: Float, u1: Float, brand: Int) {
        val stripes = ((u1 - u0) / .7f).toInt().coerceAtLeast(2)
        val w = (u1 - u0) / stripes
        val white = c(0xFFF4F1EA)
        for (i in 0 until stripes) {
            val col = if (i % 2 == 0) brand else white
            awning(b, face, u0 + i * w, u0 + (i + 1) * w, scene.lit(col, 1f), scene.lit(col, .8f))
        }
    }

    private fun neon(b: Building, face: Int, len: Float, open: Boolean) {
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        val night = scene.daylight < .6f
        val pink = if (night && open) c(0xFFFF5FD8) else c(0xFF8A3A78)
        val blue = if (night && open) c(0xFF5FE3FF) else c(0xFF2E6A7A)
        faceQuad(x0, y0, x1, y1, face, len * .2f, len * .8f, 4.6f, 4.8f, pink, .12f)
        faceQuad(x0, y0, x1, y1, face, len * .2f, len * .8f, 6.2f, 6.4f, blue, .12f)
        faceQuad(x0, y0, x1, y1, face, len * .2f, len * .2f + .2f, 4.6f, 6.4f, pink, .12f)
        faceQuad(x0, y0, x1, y1, face, len * .8f - .2f, len * .8f, 4.6f, 6.4f, blue, .12f)
        if (night && open) faceQuad(x0, y0, x1, y1, face, len * .15f, len * .85f, 4.3f, 6.7f, scene.withAlpha(c(0xFFFF5FD8), .18f), .1f)
    }

    /** Cinema and theater marquee: a lit canopy with a row of bulbs. */
    private fun marquee(b: Building, face: Int, len: Float, brand: Int, night: Boolean) {
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        faceBox(x0, y0, x1, y1, face, len * .1f, len * .9f, 3.3f, 4.4f, 2f, scene.snowy(c(0xFF2C2F33)), brand)
        val bulbs = ((len * .8f) / .7f).toInt()
        val blink = ((e.realTime * 4f).toInt() % 2)
        for (k in 0 until bulbs) {
            val u = len * .1f + .35f + k * .7f
            val on = night || (k + blink) % 2 == 0
            faceBox(x0, y0, x1, y1, face, u - .1f, u + .1f, 3.35f, 3.55f, 2.05f, if (on) c(0xFFFFE680) else c(0xFFB59A3A), if (on) c(0xFFFFE680) else c(0xFFB59A3A))
        }
    }

    private fun sidewalkProps(b: Building, face: Int, len: Float, typeId: String?, brand: Int) {
        // positions just outside the shop window, on the sidewalk
        fun at(u: Float, out: Float): Pair<Float, Float> = when (face) {
            0 -> (b.maxX - u) to (b.minY - out)
            1 -> (b.minX + u) to (b.maxY + out)
            2 -> (b.minX - out) to (b.minY + u)
            else -> (b.maxX + out) to (b.maxY - u)
        }
        when (typeId) {
            "coffee", "fastfood" -> for (k in 0..1) {
                val (x, y) = at(if (k == 0) 2.2f else len - 2.2f, 2.3f)
                scene.cylinderLike(x, y, .45f, .72f, .78f, c(0xFFE0E0E0))
                scene.box(x - .04f, y - .04f, 0f, x + .04f, y + .04f, .72f, IRON, IRON)
                // umbrella
                scene.box(x - .03f, y - .03f, .78f, x + .03f, y + .03f, 2.3f, c(0xFFE0E0E0), c(0xFFE0E0E0))
                cone(x, y, 1.3f, 2f, 2.5f, 8, brand)
                for (s in intArrayOf(-1, 1)) scene.box(x + s * .7f - .2f, y - .2f, 0f, x + s * .7f + .2f, y + .2f, .45f, c(0xFF3A3D42), c(0xFF3A3D42))
            }
            "florist" -> for (k in 0 until 4) {
                val (x, y) = at(1.2f + k * .9f, 1.2f)
                scene.box(x - .3f, y - .3f, 0f, x + .3f, y + .3f, .45f, c(0xFF9AA3AD), c(0xFF8C939B))
                scene.blob(x, y, .7f, .38f, scene.lit(AD_COLORS[(k + b.id) % AD_COLORS.size], 1f), 7)
            }
            "produce" -> for (k in 0 until 3) {
                val (x, y) = at(1.4f + k * 1.3f, 1.3f)
                scene.box(x - .55f, y - .4f, 0f, x + .55f, y + .4f, .7f, c(0xFFB07A52), c(0xFF8B5A3C))
                scene.box(x - .5f, y - .35f, .7f, x + .5f, y + .35f, .82f, intArrayOf(c(0xFFEB5757), c(0xFFF2C94C), c(0xFF27AE60))[k], intArrayOf(c(0xFFEB5757), c(0xFFF2C94C), c(0xFF27AE60))[k])
            }
            null -> {}
            else -> {
                // A-frame sandwich board
                val (x, y) = at(len - 1.6f, 1.5f)
                scene.box(x - .3f, y - .12f, 0f, x + .3f, y + .12f, .9f, brand, c(0xFF2C2F33))
            }
        }
    }

    // ------------------------------------------------------------------ landmarks

    private fun landmark(b: Building, placeId: String, d0: Float) {
        if (d0 > 320f) return
        val face = if (b.facesNorth) 0 else 1
        val len = b.width
        val x0 = b.minX; val y0 = b.minY; val x1 = b.maxX; val y1 = b.maxY
        val h = b.height
        when (placeId) {
            "bank" -> {
                // a classical portico: steps, columns and a pediment
                val stone = c(0xFFEDE6D8)
                for (s in 0 until 3) faceBox(x0, y0, x1, y1, face, len * .15f - s * .3f, len * .85f + s * .3f, 0f, .25f * (3 - s), 2.6f - s * .5f, scene.snowy(stone), stone)
                val cols = 6
                for (k in 0 until cols) {
                    val u = len * .18f + k * (len * .64f) / (cols - 1)
                    val (cx, cy) = if (face == 0) (x1 - u) to (y0 - 1.6f) else (x0 + u) to (y1 + 1.6f)
                    cylinder(cx, cy, .38f, .75f, 7.4f, 8, stone)
                }
                faceBox(x0, y0, x1, y1, face, len * .13f, len * .87f, 7.4f, 8.4f, 2.2f, scene.snowy(stone), stone)
                // pediment
                val yy = if (face == 0) y0 - 2.2f else y1 + 2.2f
                val ua = len * .13f; val ub = len * .87f
                val xa = if (face == 0) x1 - ua else x0 + ua; val xb = if (face == 0) x1 - ub else x0 + ub
                px[0] = xa; py[0] = yy; pz[0] = 8.4f
                px[1] = xb; py[1] = yy; pz[1] = 8.4f
                px[2] = (xa + xb) / 2; py[2] = yy; pz[2] = 10.6f
                scene.poly(3, px, py, pz, scene.lit(stone, shadeOf(face)))
            }
            "school" -> {
                // clock tower
                val cx = b.cx; val cy = b.cy
                scene.box(cx - 2f, cy - 2f, h, cx + 2f, cy + 2f, h + 7f, c(0xFFF1EBDF), c(0xFFB5523B))
                cone(cx, cy, 2.9f, h + 7f, h + 10.5f, 4, c(0xFF3F5160))
                val (fx, fy) = if (face == 0) cx to (cy - 2.05f) else cx to (cy + 2.05f)
                scene.disc(fx, fy, h + 5f, 1.1f, .01f, 12, scene.lit(c(0xFFFFFFFF), 1f))
                // clock hands follow the game clock
                val hr = (e.state.hour % 12 + e.state.minuteOfHour / 60f) / 12f * 6.2832f
                val mn = e.state.minuteOfHour / 60f * 6.2832f
                val dy = if (face == 0) -2.08f else 2.08f
                scene.quad(cx - .04f, cy + dy, h + 5f, cx + .04f, cy + dy, h + 5f, cx + sin(hr) * .6f + .04f, cy + dy, h + 5f + cos(hr) * .6f, cx + sin(hr) * .6f - .04f, cy + dy, h + 5f + cos(hr) * .6f, c(0xFF222222))
                scene.quad(cx - .03f, cy + dy, h + 5f, cx + .03f, cy + dy, h + 5f, cx + sin(mn) * .9f + .03f, cy + dy, h + 5f + cos(mn) * .9f, cx + sin(mn) * .9f - .03f, cy + dy, h + 5f + cos(mn) * .9f, c(0xFF222222))
            }
            "hospital" -> {
                // red cross high on the front and an ambulance bay
                val sh = shadeOf(face)
                val zc = h - 4f
                faceQuad(x0, y0, x1, y1, face, len / 2 - .6f, len / 2 + .6f, zc - 1.8f, zc + 1.8f, scene.lit(c(0xFFE53935), sh), .1f)
                faceQuad(x0, y0, x1, y1, face, len / 2 - 1.8f, len / 2 + 1.8f, zc - .6f, zc + .6f, scene.lit(c(0xFFE53935), sh), .1f)
                faceBox(x0, y0, x1, y1, face, len * .55f, len * .9f, 3.6f, 3.9f, 4f, scene.snowy(c(0xFFE0E0E0)), c(0xFFE53935))
                val (ax, ay) = if (face == 0) (x1 - len * .72f) to (y0 - 4f) else (x0 + len * .72f) to (y1 + 4f)
                r.car(ax, ay, (Math.PI / 2).toFloat(), 5.4f, 2.1f, 2.4f, c(0xFFF5F5F5), 2)
                scene.box(ax - 2.7f, ay - 1.06f, 1.2f, ax + 2.7f, ay + 1.06f, 1.45f, c(0xFFE53935), c(0xFFE53935))
            }
            "motors" -> {
                // flags and two cars on display out front
                for (k in 0 until 3) {
                    val u = len * (.2f + k * .3f)
                    val (fx, fy) = if (face == 0) (x1 - u) to (y0 - 3.2f) else (x0 + u) to (y1 + 3.2f)
                    scene.box(fx - .06f, fy - .06f, 0f, fx + .06f, fy + .06f, 6f, c(0xFFD5D8DC), c(0xFFBFC5CB))
                    val wave = sin(e.realTime * 3f + k) * .25f
                    scene.quad(fx, fy, 5.9f, fx + 1.6f, fy + wave, 5.5f, fx + 1.6f, fy + wave, 5.1f, fx, fy, 4.7f, scene.lit(AD_COLORS[k % AD_COLORS.size], 1f))
                }
                for (k in 0..1) {
                    val u = len * (.3f + k * .4f)
                    val (cx, cy) = if (face == 0) (x1 - u) to (y0 - 1.9f) else (x0 + u) to (y1 + 1.9f)
                    val def = Catalog.vehicles[(k * 3 + 1) % Catalog.vehicles.size]
                    r.car(cx, cy, (Math.PI / 2).toFloat(), def.length, def.width, def.height, def.color, def.style)
                }
            }
            "metro", "distro", "imports" -> {
                // a delivery truck backed up to the building
                val (tx, ty) = if (face == 0) (x0 + len * .3f) to (y0 - 3.5f) else (x0 + len * .3f) to (y1 + 3.5f)
                r.car(tx, ty, (Math.PI / 2).toFloat(), 7f, 2.4f, 3.2f, c(0xFFF5F5F5), 2)
                scene.box(tx - 3.2f, ty - 1.22f, 1.6f, tx + 1.5f, ty + 1.22f, 2.4f, Catalog.place(placeId).color, Catalog.place(placeId).color)
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------------ gas station

    private fun gasStation(b: Building, d0: Float) {
        val color = Catalog.place("fuel").color
        val x0 = b.minX + 3f; val x1 = b.maxX - 3f; val y0 = b.minY + 5f; val y1 = b.maxY - 5f
        val pillar = c(0xFFE0E0E0)
        for ((px0, py0) in listOf(x0 + .5f to y0 + .5f, x1 - .9f to y0 + .5f, x0 + .5f to y1 - .9f, x1 - .9f to y1 - .9f)) scene.box(px0, py0, 0f, px0 + .4f, py0 + .4f, 5f, pillar, pillar)
        val mx = (x0 + x1) / 2
        // pumps on islands
        for (s in intArrayOf(-1, 1)) {
            val pxp = mx + s * 2.5f
            scene.box(pxp - 1.2f, b.cy - 1.4f, 0f, pxp + 1.2f, b.cy + 1.4f, .2f, c(0xFFBDBDBD), c(0xFF9E9E9E))
            scene.box(pxp - .5f, b.cy - .6f, .2f, pxp + .5f, b.cy + .6f, 1.8f, c(0xFFD32F2F), c(0xFFD32F2F))
            scene.box(pxp - .45f, b.cy - .62f, 1.1f, pxp + .45f, b.cy - .6f, 1.6f, c(0xFF263238), c(0xFF263238))
        }
        scene.box(x0, y0, 5f, x1, y1, 5.7f, c(0xFFF5F5F5), color)
        if (scene.daylight < .5f) scene.disc(b.cx, b.cy, .05f, (x1 - x0) * .45f, (y1 - y0) * .45f, 14, scene.withAlpha(c(0xFFFFF3C4), .25f))
        // price sign by the road
        if (d0 < 200f) {
            val sx = b.minX + 1.5f; val sy = if (b.facesNorth) b.minY + 1.5f else b.maxY - 1.5f
            scene.box(sx - .1f, sy - .1f, 0f, sx + .1f, sy + .1f, 5f, c(0xFF6E757C), c(0xFF6E757C))
            scene.box(sx - 1f, sy - .2f, 5f, sx + 1f, sy + .2f, 7.2f, color, c(0xFF263238))
        }
        r.gasLabel(b, y0, y1)
    }
}

/** A short upright drum (bistro table tops, planters) — a thin polygonal prism. */
fun Scene3D.cylinderLike(x: Float, y: Float, rad: Float, z0: Float, z1: Float, color: Int) {
    box(x - rad * .8f, y - rad * .8f, z0, x + rad * .8f, y + rad * .8f, z1, color, color)
}

@Suppress("unused")
private fun lenOf(a: Float, b: Float) = sqrt(a * a + b * b)

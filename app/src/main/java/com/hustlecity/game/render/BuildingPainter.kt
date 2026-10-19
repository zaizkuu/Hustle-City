package com.hustlecity.game.render

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.c
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Look
import com.hustlecity.game.world.Maps
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Architectural styles. Every building gets one from its kind, its neighborhood's look and a stable hash.
 * The last four belong to the Asian cities: Spanish colonial houses with wooden balconies and tile roofs (Manila),
 * painted concrete low-rises under tin roofs (Manila), narrow "pencil" buildings hung with vertical signs (Tokyo,
 * Seoul) and hanok with curved tile roofs (Seoul).
 */
enum class Arch { WALKUP, BROWNSTONE, DECO, GLASS, CLASSIC, MODERN, INDUSTRIAL, SHOP, COLONIAL, TROPICAL, PENCIL, HANOK }

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
        private val WOOD = c(0xFF5A3A26)
        private val CAPIZ = c(0xFFEDE6D3)
        private val HANJI = c(0xFFF2E7CF)
        private val TERRACOTTA = intArrayOf(c(0xFFB0563C), c(0xFFA34B34), c(0xFFBD6447))
        private val TIN = intArrayOf(c(0xFF9C5A3C), c(0xFF3D6E8C), c(0xFFB03A2E), c(0xFF4E7D5B), c(0xFF8C9399), c(0xFFA7A9A4))
        private val GIWA = c(0xFF3F444A)
        private val TILE_FACADE = intArrayOf(c(0xFFE8E2D6), c(0xFFD9D4CC), c(0xFFC8B8A6), c(0xFFE4DCE6), c(0xFFBFC4C9), c(0xFFF0ECE4))
        /** Neon colors the GPU renderer treats as light sources. */
        private val NEON = intArrayOf(c(0xFFFF5FD8), c(0xFF5FE3FF), c(0xFFFFE680), c(0xFF2EE87A), c(0xFFFFD34D), c(0xFFFF4D4D))
        private val SIGN_DAY = intArrayOf(c(0xFFD23C6E), c(0xFF2F80ED), c(0xFFF2C94C), c(0xFF27AE60), c(0xFFE0E0E0), c(0xFFEB5757))
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
        if (e.city.mapId == Maps.CLASSIC) return classicPick(b, h)
        if (b.kind == BKind.WAREHOUSE) return Arch.INDUSTRIAL
        val office = officePick(b, h)
        val tall = b.height > 34f
        return when (b.hood.look) {
            Look.NY_TOWNHOUSE -> when (b.kind) {
                BKind.OFFICE -> office
                BKind.RESIDENTIAL -> when { b.height < 20f && h < 60 -> Arch.BROWNSTONE; tall -> if (h < 50) Arch.MODERN else Arch.DECO; h < 80 -> Arch.WALKUP; else -> Arch.MODERN }
                BKind.RETAIL -> when { b.height < 11f -> Arch.SHOP; h < 65 -> Arch.WALKUP; h < 85 -> Arch.MODERN; else -> Arch.CLASSIC }
                else -> Arch.CLASSIC
            }
            Look.NY_DOWNTOWN -> when (b.kind) {
                BKind.OFFICE -> office
                BKind.RESIDENTIAL -> when { tall -> if (h < 40) Arch.DECO else if (h < 75) Arch.GLASS else Arch.MODERN; h < 60 -> Arch.WALKUP; else -> Arch.CLASSIC }
                BKind.RETAIL -> when { b.height < 11f -> Arch.SHOP; h < 50 -> Arch.CLASSIC; h < 80 -> Arch.WALKUP; else -> Arch.DECO }
                else -> Arch.CLASSIC
            }
            Look.CBD -> when (b.kind) {
                BKind.OFFICE -> if (b.height > 30f) (if (h < 70) Arch.GLASS else Arch.MODERN) else (if (h < 55) Arch.MODERN else Arch.GLASS)
                BKind.RESIDENTIAL -> if (h < 60) Arch.MODERN else Arch.GLASS
                BKind.RETAIL -> if (b.height < 11f) Arch.SHOP else Arch.MODERN
                else -> Arch.MODERN
            }
            Look.COLONIAL -> when {
                b.kind == BKind.OFFICE -> if (h < 60) Arch.CLASSIC else Arch.COLONIAL
                b.height <= 16f -> Arch.COLONIAL
                else -> if (h < 50) Arch.CLASSIC else Arch.TROPICAL
            }
            Look.TROPICAL -> when {
                b.kind == BKind.OFFICE -> if (b.height > 24f) Arch.GLASS else Arch.MODERN
                b.height <= 20f -> Arch.TROPICAL
                else -> if (h < 70) Arch.MODERN else Arch.GLASS
            }
            Look.DENSE_ASIA -> when {
                b.kind == BKind.OFFICE && b.height > 30f -> if (h < 60) Arch.GLASS else Arch.MODERN
                b.height > 44f -> Arch.MODERN
                else -> Arch.PENCIL
            }
            Look.HANOK -> if (b.height <= 9f && b.kind != BKind.OFFICE) Arch.HANOK else Arch.PENCIL
            Look.INDUSTRIAL -> when {
                b.kind == BKind.OFFICE -> Arch.MODERN
                b.kind == BKind.RETAIL && h < 30 -> Arch.INDUSTRIAL
                else -> regionalLowRise()
            }
        }
    }

    /** Everyday low-rise of the city: walk-ups in New York, tin-roofed concrete in Manila, pencil buildings in Tokyo and Seoul. */
    private fun regionalLowRise(): Arch = when (e.city.mapId) {
        "manila" -> Arch.TROPICAL
        "tokyo", "seoul" -> Arch.PENCIL
        else -> Arch.WALKUP
    }

    private fun officePick(b: Building, h: Int): Arch = when {
        b.height > 46f -> if (h < 45) Arch.DECO else Arch.GLASS
        b.height > 28f -> if (h < 40) Arch.GLASS else if (h < 75) Arch.CLASSIC else Arch.DECO
        else -> if (h < 55) Arch.CLASSIC else Arch.MODERN
    }

    /** The original city's styles, keyed to its neighborhoods. */
    private fun classicPick(b: Building, h: Int): Arch = when (b.kind) {
        BKind.WAREHOUSE -> Arch.INDUSTRIAL
        BKind.OFFICE -> officePick(b, h)
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

    /** Pitched roofs: Spanish tile, hanok and the tin roofs of low tropical houses. */
    private fun pitched(b: Building, st: Arch) = st == Arch.COLONIAL || st == Arch.HANOK || (st == Arch.TROPICAL && b.height < 10f)

    private fun roofRise(b: Building, st: Arch): Float = when {
        st == Arch.HANOK -> min(2.8f, min(b.w, b.d) * .38f)
        st == Arch.COLONIAL -> min(3.4f, min(b.w, b.d) * .3f)
        st == Arch.TROPICAL && b.height < 10f -> min(1.7f, min(b.w, b.d) * .2f)
        else -> 0f
    }

    /** Height of the walls (below any pitched roof). */
    private fun wallTop(b: Building, st: Arch): Float {
        val rise = roofRise(b, st)
        if (rise <= 0f) return b.height
        val min = if (st == Arch.HANOK && (b.kind == BKind.RETAIL || b.placeId != null)) 4.6f else 3f
        return max(min, b.height - rise)
    }

    /** Wall color for the style (brownstone, glass, concrete…) or the city's own facade palette. */
    fun facadeColor(b: Building, base: Int): Int = when (style(b)) {
        Arch.BROWNSTONE -> BROWNSTONE[b.id % BROWNSTONE.size]
        Arch.GLASS -> GLASS_TINTS[b.id % GLASS_TINTS.size]
        Arch.MODERN -> MODERN[b.id % MODERN.size]
        Arch.CLASSIC -> STONE[b.id % STONE.size]
        Arch.INDUSTRIAL -> if (hash(b.id, 3, 3) % 2 == 0) base else METAL[b.id % METAL.size]
        Arch.PENCIL -> if (hash(b.id, 6, 2) % 3 == 0) base else TILE_FACADE[b.id % TILE_FACADE.size]
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
        Arch.COLONIAL -> c(0xFFE9DFC9)
        Arch.TROPICAL -> c(0xFFDAD5CB)
        Arch.PENCIL -> c(0xFF6B7178)
        Arch.HANOK -> WOOD
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
        if (scene.camLY < y0) f(0)
        if (scene.camLY > y1) f(1)
        if (scene.camLX < x0) f(2)
        if (scene.camLX > x1) f(3)
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
        // everything is drawn in the building's own frame: x along the front, y from the back to the street
        scene.setFrame(b.ox, b.oy, b.cs, b.sn)
        try { drawLocal(b, roof, facadeIn, d0, detail) } finally { scene.clearFrame() }
    }

    private fun drawLocal(b: Building, roof: Int, facadeIn: Int, d0: Float, detail: Boolean) {
        if (b.kind == BKind.GAS) { gasStation(b, d0); return }
        val st = style(b)
        val facade = facadeIn
        val h = b.height
        val q = e.settings.quality
        val near = detail && d0 < (when (q) { 0 -> 80f; 1 -> 125f; else -> 170f })
        scene.material = when (st) {
            Arch.WALKUP -> Mat.BRICK
            Arch.BROWNSTONE, Arch.DECO, Arch.CLASSIC -> Mat.STONE
            Arch.GLASS -> Mat.GLASS
            Arch.MODERN -> Mat.CONCRETE
            Arch.INDUSTRIAL -> if (facadeColor(b, b.facade) != b.facade) Mat.METAL else Mat.BRICK
            Arch.SHOP -> if (b.id % 3 == 0) Mat.PLASTER else Mat.BRICK
            Arch.COLONIAL, Arch.HANOK -> Mat.PLASTER
            Arch.TROPICAL -> Mat.CONCRETE
            Arch.PENCIL -> if (facadeColor(b, b.facade) != b.facade) Mat.TILE else Mat.CONCRETE
        }
        scene.topMaterial = Mat.ROOF
        when {
            st == Arch.DECO && detail && h > 30f -> decoMassing(b, roof, facade, d0, near)
            st == Arch.INDUSTRIAL && detail && d0 < 260f && b.w > 10f -> industrialMassing(b, roof, facade, d0, near)
            pitched(b, st) -> {
                val wt = wallTop(b, st)
                scene.box(0f, 0f, 0f, b.w, b.d, wt, roof, facade)
                pitchedRoof(b, st, wt, roof, d0)
            }
            else -> scene.box(0f, 0f, 0f, b.w, b.d, h, roof, facade)
        }
        scene.material = Mat.PLAIN
        scene.topMaterial = -1
        if (!detail) return
        if (st != Arch.DECO && st != Arch.INDUSTRIAL) roofline(b, st, d0, near)
        if (d0 > 330f) return
        visibleFaces(0f, 0f, b.w, b.d) { face -> facade(b, st, face, d0, near) }
        if (d0 < 300f && q >= 1) rooftop(b, st, d0)
        b.placeId?.let { landmark(b, it, d0) }
    }

    // ------------------------------------------------------------------ massing

    private fun decoMassing(b: Building, roof: Int, facade: Int, d0: Float, near: Boolean) {
        val h = b.height
        val t1 = h * .62f; val t2 = h * .84f
        val i1 = min(2.6f, min(b.w, b.d) * .14f); val i2 = i1 * 2f
        val tr = trim(b)
        scene.box(0f, 0f, 0f, b.w, b.d, t1, roof, facade)
        scene.box(i1, i1, t1, b.w - i1, b.d - i1, t2, roof, facade)
        scene.box(i2, i2, t2, b.w - i2, b.d - i2, h, roof, facade)
        // crown: stepped cap and a spire
        val cx = (b.w * .5f); val cy = (b.d * .5f)
        val cw = min(b.w, b.d) * .5f - i2
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
            val x0 = ins; val y0 = ins; val x1 = b.w - ins; val y1 = b.d - ins
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
        scene.box(0f, 0f, 0f, b.w, b.d, wall, roof, facade)
        // sawtooth roof: north-facing glass teeth running east-west
        val teeth = (b.d / 6f).toInt().coerceIn(2, 6)
        val tw = b.d / teeth
        val roofCol = scene.lit(scene.snowy(c(0xFF6F7377)), 1f)
        val glass = scene.lit(c(0xFF9FC4DD), scene.sideShade(0f, -1f))
        val sideCol = scene.lit(facade, scene.sideShade(1f, 0f))
        for (t in 0 until teeth) {
            val y0 = t * tw; val y1 = y0 + tw
            // vertical glazing on the north side of each tooth
            if (scene.camLY < y0 + .1f) scene.quad(b.w, y0, wall, 0f, y0, wall, 0f, y0, h, b.w, y0, h, glass)
            // sloped roof down to the south
            scene.quad(0f, y0, h, b.w, y0, h, b.w, y1, wall, 0f, y1, wall, roofCol)
            // gable ends
            if (scene.camLX > b.w) { px[0] = b.w; py[0] = y0; pz[0] = wall; px[1] = b.w; py[1] = y1; pz[1] = wall; px[2] = b.w; py[2] = y0; pz[2] = h; scene.poly(3, px, py, pz, sideCol) }
            if (scene.camLX < 0f) { px[0] = 0f; py[0] = y0; pz[0] = wall; px[1] = 0f; py[1] = y1; pz[1] = wall; px[2] = 0f; py[2] = y0; pz[2] = h; scene.poly(3, px, py, pz, sideCol) }
        }
        // smokestack
        if (hash(b.id, 9, 9) % 3 == 0) {
            val sx = 2.5f; val sy = b.d - 2.5f
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
                // the cornice caps the roof edge; it stands a little proud so it never shares a plane with the roof or walls
                scene.box(0f - o * .3f, 0f - o, h - .7f, b.w + o * .3f, b.d + o, h + .06f, tr, tr)
                if (st == Arch.CLASSIC) scene.box(0f - .1f, 0f - .1f, 0f, b.w + .1f, b.d + .1f, min(7f, h * .3f), tr, tr) // stone base
            }
            Arch.SHOP, Arch.MODERN, Arch.TROPICAL -> {
                // parapet around the roof
                if (d0 < 220f && !pitched(b, st)) {
                    val p = .25f; val ph = .8f
                    val col = tr
                    scene.box(0f, 0f, h, b.w, p, h + ph, col, col)
                    scene.box(0f, b.d - p, h, b.w, b.d, h + ph, col, col)
                    scene.box(0f, 0f, h, p, b.d, h + ph, col, col)
                    scene.box(b.w - p, 0f, h, b.w, b.d, h + ph, col, col)
                }
            }
            Arch.GLASS -> {
                // mechanical penthouse
                val ins = min(b.w, b.d) * .2f
                scene.box(ins, ins, h, b.w - ins, b.d - ins, h + 3.4f, c(0xFF7D868F), c(0xFF6B737B))
            }
            Arch.PENCIL -> {
                // a thin parapet with a metal railing on top
                if (d0 < 220f) {
                    val p = .2f
                    scene.box(0f, 0f, h, b.w, p, h + .5f, tr, tr)
                    scene.box(0f, b.d - p, h, b.w, b.d, h + .5f, tr, tr)
                    scene.box(0f, 0f, h, p, b.d, h + .5f, tr, tr)
                    scene.box(b.w - p, 0f, h, b.w, b.d, h + .5f, tr, tr)
                    if (near) {
                        val rail = c(0xFF9AA3AD)
                        scene.box(0f, b.d - .1f, h + 1.1f, b.w, b.d, h + 1.18f, rail, rail)
                        scene.box(0f, 0f, h + 1.1f, .1f, b.d, h + 1.18f, rail, rail)
                        scene.box(b.w - .1f, 0f, h + 1.1f, b.w, b.d, h + 1.18f, rail, rail)
                        var u = 0f
                        while (u <= b.w) { scene.box(u - .04f, b.d - .1f, h + .5f, u + .04f, b.d, h + 1.1f, rail, rail); u += 1.4f }
                    }
                }
            }
            else -> {}
        }
    }

    private fun rooftop(b: Building, st: Arch, d0: Float) {
        val h = b.height + (if (st == Arch.SHOP || st == Arch.MODERN) 0f else 0f)
        val k = hash(b.id, 5, 17)
        // original rooftop boxes (AC units)
        if (pitched(b, st)) return
        for (rb in b.roofBoxes) if (st != Arch.GLASS && st != Arch.DECO && st != Arch.INDUSTRIAL) {
            scene.box(rb.x0, rb.y0, h, rb.x1, rb.y1, h + rb.h, c(0xFFA7ABB0), c(0xFF8C9095))
            if (d0 < 140f) scene.box(rb.x0 + .3f, rb.y0 + .3f, h + rb.h, rb.x1 - .3f, rb.y1 - .3f, h + rb.h + .12f, c(0xFF5C6066), c(0xFF5C6066))
        }
        when (st) {
            Arch.WALKUP, Arch.BROWNSTONE -> {
                // the classic New York water tower
                if (k % 3 == 0 && b.w > 9f) waterTower(b.w * .7f, (b.d * .5f) - b.d * .15f, h)
                // chimneys
                if (d0 < 200f) {
                    val n = 1 + k % 2
                    for (i in 0 until n) {
                        val cx = 1.6f + i * (b.w - 3.2f) / max(1, n)
                        scene.box(cx - .4f, b.d - 2f, h, cx + .4f, b.d - 1.2f, h + 1.8f, c(0xFF5A3B32), c(0xFF7A4B3E))
                    }
                }
            }
            Arch.MODERN -> {
                if (k % 2 == 0) solarPanels(b, h) else roofGarden(b, h)
            }
            Arch.GLASS -> {
                val ins = min(b.w, b.d) * .2f
                if (k % 3 == 0) {
                    // helipad
                    val r0 = min(b.w, b.d) * .32f
                    if (r0 > 4f) {
                        scene.disc((b.w * .5f), (b.d * .5f), h + 3.45f, r0, r0, 14, scene.lit(c(0xFF3E4650), 1f))
                        scene.disc((b.w * .5f), (b.d * .5f), h + 3.47f, r0 * .82f, r0 * .82f, 14, scene.lit(c(0xFFF2C94C), 1f))
                        scene.disc((b.w * .5f), (b.d * .5f), h + 3.48f, r0 * .74f, r0 * .74f, 14, scene.lit(c(0xFF3E4650), 1f))
                        val hw = r0 * .3f
                        scene.box((b.w * .5f) - hw, (b.d * .5f) - .2f, h + 3.48f, (b.w * .5f) + hw, (b.d * .5f) + .2f, h + 3.5f, c(0xFFFFFFFF), c(0xFFFFFFFF))
                        scene.box((b.w * .5f) - hw, (b.d * .5f) - hw, h + 3.48f, (b.w * .5f) - hw + .4f, (b.d * .5f) + hw, h + 3.5f, c(0xFFFFFFFF), c(0xFFFFFFFF))
                        scene.box((b.w * .5f) + hw - .4f, (b.d * .5f) - hw, h + 3.48f, (b.w * .5f) + hw, (b.d * .5f) + hw, h + 3.5f, c(0xFFFFFFFF), c(0xFFFFFFFF))
                    }
                } else {
                    // antenna with a red light
                    val ax = b.w - ins - 1f; val ay = ins + 1f
                    scene.box(ax - .12f, ay - .12f, h + 3.4f, ax + .12f, ay + .12f, h + 3.4f + 9f, c(0xFFBFC6CC), c(0xFF9AA2A9))
                    if (scene.daylight < .6f || (e.realTime % 2f) < 1f) scene.box(ax - .25f, ay - .25f, h + 12.3f, ax + .25f, ay + .25f, h + 12.7f, c(0xFFFF4D4D), c(0xFFFF4D4D))
                }
            }
            Arch.CLASSIC -> {
                if (k % 2 == 0) {
                    // flagpole with a flag
                    val fx = (b.w * .5f); val fy = b.d - 1.5f
                    scene.box(fx - .08f, fy - .08f, h, fx + .08f, fy + .08f, h + 7f, c(0xFFD5D8DC), c(0xFFBFC5CB))
                    val wave = sin(e.realTime * 3f + b.id) * .3f
                    scene.quad(fx + .08f, fy, h + 6.9f, fx + 2.6f, fy + wave, h + 6.9f, fx + 2.6f, fy + wave, h + 5.4f, fx + .08f, fy, h + 5.4f, scene.lit(AD_COLORS[k % AD_COLORS.size], 1f))
                }
            }
            Arch.SHOP -> {
                // rooftop billboard on some low buildings
                if (k % 4 == 0 && b.w > 10f && d0 < 260f) billboard(b, h, k)
            }
            Arch.TROPICAL -> {
                // a water tank on a stand, and rebar left sticking out of the columns for the next floor
                val tx = b.w - 2.2f; val ty = 2.2f
                val leg = c(0xFF6E7378)
                for (dx in intArrayOf(-1, 1)) for (dy in intArrayOf(-1, 1)) scene.box(tx + dx * .8f - .07f, ty + dy * .8f - .07f, h, tx + dx * .8f + .07f, ty + dy * .8f + .07f, h + 1.6f, leg, leg)
                val tank = if (k % 2 == 0) c(0xFF2F6FB5) else c(0xFF3A3D42)
                cylinder(tx, ty, 1.05f, h + 1.6f, h + 3.3f, 10, tank, scene.mix(tank, c(0xFFFFFFFF), .15f))
                if (k % 3 != 0 && d0 < 160f) {
                    val bar = c(0xFF6B4A3A)
                    for (cx in floatArrayOf(.3f, b.w - .3f)) for (cy in floatArrayOf(.3f, b.d - .3f)) for (q in 0..2) {
                        val ox = cx + (q - 1) * .12f
                        scene.box(ox - .025f, cy - .025f, h, ox + .025f, cy + .025f, h + 1.4f + q * .2f, bar, bar)
                    }
                }
            }
            Arch.PENCIL -> {
                // a water tank or a cooling tower, and a rooftop sign on some
                if (k % 3 == 0) {
                    val tx = b.w * .5f; val ty = b.d * .35f
                    cylinder(tx, ty, min(1.2f, b.w * .2f), h + .2f, h + 2.4f, 10, c(0xFFB9C0C6), c(0xFF9AA3AD))
                } else if (k % 3 == 1 && b.w > 5f) {
                    scene.box(b.w * .5f - 1.2f, 1f, h, b.w * .5f + 1.2f, 3.4f, h + 2f, c(0xFF8C939B), c(0xFF7B828A))
                    cylinder(b.w * .5f, 2.2f, .9f, h + 2f, h + 2.4f, 10, c(0xFF5C6066))
                }
                if (k % 5 == 0 && b.w > 7f && d0 < 260f) billboard(b, h, k)
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
        val rows = ((b.d - 3f) / 2.6f).toInt().coerceIn(1, 6)
        val panel = scene.lit(c(0xFF22385A), 1f)
        val frame = c(0xFF9AA3AD)
        for (rIdx in 0 until rows) {
            val y0 = 1.5f + rIdx * 2.6f
            scene.quad(1.2f, y0, h + .3f, b.w - 1.2f, y0, h + .3f, b.w - 1.2f, y0 + 1.6f, h + 1.1f, 1.2f, y0 + 1.6f, h + 1.1f, panel)
            scene.box(1.2f, y0 + 1.5f, h, 1.4f, y0 + 1.7f, h + 1.1f, frame, frame)
            scene.box(b.w - 1.4f, y0 + 1.5f, h, b.w - 1.2f, y0 + 1.7f, h + 1.1f, frame, frame)
        }
    }

    private fun roofGarden(b: Building, h: Float) {
        scene.box(1f, 1f, h, b.w - 1f, b.d - 1f, h + .35f, scene.snowy(c(0xFF6DA35C)), c(0xFF8B6A4E))
        val n = ((b.w - 2f) / 4f).toInt().coerceIn(1, 5)
        for (i in 0 until n) {
            val tx = 2.5f + i * (b.w - 5f) / max(1, n - 1).toFloat()
            scene.blob(tx, (b.d * .5f) + (if (i % 2 == 0) -1.5f else 1.5f), h + 1.4f, 1.2f, scene.lit(scene.mix(c(0xFF4C8C3F), c(0xFFF2F5F8), scene.snow * .5f), 1f), 8)
        }
    }

    private fun billboard(b: Building, h: Float, k: Int) {
        val y = b.d - 1.2f
        val leg = c(0xFF3A3D42)
        val x0 = 1.5f; val x1 = b.w - 1.5f
        scene.box(x0 + 1f, y - .1f, h, x0 + 1.2f, y + .1f, h + 3f, leg, leg)
        scene.box(x1 - 1.2f, y - .1f, h, x1 - 1f, y + .1f, h + 3f, leg, leg)
        val ad = AD_COLORS[k % AD_COLORS.size]
        val face = 1
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
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
        val len = faceLen(x0, y0, x1, y1, face)
        val shade = shadeOf(face)
        val night = scene.daylight < .5f
        val front = face == 1
        val biz = r.bizAt(b.id)
        val rival = if (biz == null) r.rivalAt(b.id) else null
        val hasShop = (b.kind == BKind.RETAIL || b.kind == BKind.SPECIAL || biz != null || rival != null) && st != Arch.INDUSTRIAL
        val baseZ = if (hasShop) 4.2f else if (st == Arch.BROWNSTONE) 1.6f else 1.2f
        val h = if (st == Arch.INDUSTRIAL && b.w > 10f && d0 < 260f) b.height - 2.6f else wallTop(b, st)
        val glass = scene.lit(WorldRenderer.GLASS, shade)
        val floors = ((h - baseZ - .8f) / 3.3f).toInt()
        val tr = scene.lit(trim(b), shade)
        val lit = { f: Int, cIdx: Int -> if (hash(b.id, f * 64 + cIdx, face) % 100 < 40) WorldRenderer.LIT else scene.lit(WorldRenderer.GLASS_NIGHT, 1f) }
        when (st) {
            Arch.COLONIAL -> colonialFacade(b, face, len, h, shade, near, front && hasShop, night)
            Arch.TROPICAL -> tropicalFacade(b, face, len, h, shade, near, front && hasShop, night)
            Arch.PENCIL -> pencilFacade(b, face, len, h, shade, near, front, hasShop, night)
            Arch.HANOK -> hanokFacade(b, face, len, h, shade, near, front && hasShop, night)
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
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
        val u = len / 2
        if (st == Arch.HANOK) {
            // a wooden double gate under its own little tiled roof
            faceQuad(x0, y0, x1, y1, face, u - 1f, u + 1f, 0f, 2.3f, scene.lit(c(0xFF4A2F20), shade), .08f)
            if (near) faceQuad(x0, y0, x1, y1, face, u - .03f, u + .03f, 0f, 2.3f, scene.lit(c(0xFF2A1A12), shade), .09f)
            faceSlab(x0, y0, x1, y1, face, u - 1.4f, u + 1.4f, 2.4f, 2.62f, 0f, .9f, scene.snowy(GIWA), GIWA)
            return
        }
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
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
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
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
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
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
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
                val y = 0f
                scene.quad(b.w - u0, y, 3.2f, b.w - u1, y, 3.2f, b.w - u1, y - out, 2.7f, b.w - u0, y - out, 2.7f, scene.snowy(top))
                scene.quad(b.w - u0, y - out, 2.7f, b.w - u1, y - out, 2.7f, b.w - u1, y - out, 2.35f, b.w - u0, y - out, 2.35f, front)
            }
            1 -> {
                val y = b.d
                scene.quad(u0, y, 3.2f, u1, y, 3.2f, u1, y + out, 2.7f, u0, y + out, 2.7f, scene.snowy(top))
                scene.quad(u0, y + out, 2.7f, u1, y + out, 2.7f, u1, y + out, 2.35f, u0, y + out, 2.35f, front)
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
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
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
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
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
            0 -> (b.w - u) to (0f - out)
            1 -> (u) to (b.d + out)
            2 -> (0f - out) to (u)
            else -> (b.w + out) to (b.d - u)
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

    // ------------------------------------------------------------------ pitched roofs and regional facades

    /**
     * A hipped roof over the building from the wall top [wt], with deep eaves. Hanok eaves sweep up at the corners;
     * tin roofs are shallow with ribs; colonial roofs are clay tile.
     */
    private fun pitchedRoof(b: Building, st: Arch, wt: Float, roofIn: Int, d0: Float) {
        val k = hash(b.id, 5, 17)
        val base = when {
            roofIn != b.roof -> roofIn // selected: keep the highlight
            st == Arch.HANOK -> GIWA
            st == Arch.COLONIAL -> TERRACOTTA[k % TERRACOTTA.size]
            else -> TIN[k % TIN.size]
        }
        val color = scene.snowy(base)
        val rise = roofRise(b, st)
        val o = when (st) { Arch.HANOK -> 1.1f; Arch.COLONIAL -> .7f; else -> .45f }
        val lift = if (st == Arch.HANOK) .55f else 0f
        val segs = if (st == Arch.HANOK && d0 < 200f) 6 else 1
        scene.material = if (st == Arch.TROPICAL) Mat.METAL else Mat.TILE
        hipRoof(-o, -o, b.w + o, b.d + o, wt - (if (st == Arch.HANOK) .25f else .05f), rise, lift, segs, color)
        scene.material = Mat.PLAIN
        if (d0 > 160f) return
        if (st == Arch.HANOK) {
            // a heavy ridge with its ends swept up
            val alongX = b.w + 2 * o >= b.d + 2 * o
            val half = min(b.w, b.d) * .5f + o
            val zr = wt - .25f + rise
            val ridge = scene.snowy(c(0xFF2E3237))
            if (alongX) {
                val rx0 = -o + half; val rx1 = b.w + o - half; val ry = b.d * .5f
                scene.box(rx0 - .3f, ry - .22f, zr - .1f, rx1 + .3f, ry + .22f, zr + .3f, ridge, ridge)
                scene.box(rx0 - .45f, ry - .2f, zr, rx0 - .15f, ry + .2f, zr + .65f, ridge, ridge)
                scene.box(rx1 + .15f, ry - .2f, zr, rx1 + .45f, ry + .2f, zr + .65f, ridge, ridge)
            } else {
                val ry0 = -o + half; val ry1 = b.d + o - half; val rx = b.w * .5f
                scene.box(rx - .22f, ry0 - .3f, zr - .1f, rx + .22f, ry1 + .3f, zr + .3f, ridge, ridge)
                scene.box(rx - .2f, ry0 - .45f, zr, rx + .2f, ry0 - .15f, zr + .65f, ridge, ridge)
                scene.box(rx - .2f, ry1 + .15f, zr, rx + .2f, ry1 + .45f, zr + .65f, ridge, ridge)
            }
            // white-plastered eave ends under the tiles
            val fascia = scene.lit(c(0xFFE9E3D5), 1f)
            scene.box(-o, b.d + o - .12f, wt - .55f, b.w + o, b.d + o, wt - .3f, fascia, fascia)
        } else if (st == Arch.TROPICAL && d0 < 110f) {
            // corrugation ribs running down the front slope
            val rib = scene.lit(scene.mix(base, c(0xFF000000), .25f), 1f)
            val zr = wt - .05f + rise; val ry = b.d * .5f
            var x = .3f
            while (x < b.w) {
                scene.quad(x - .05f, b.d + o, wt - .03f, x + .05f, b.d + o, wt - .03f, x + .05f, ry, zr + .02f, x - .05f, ry, zr + .02f, rib)
                x += .9f
            }
        }
    }

    private val rx = FloatArray(4); private val ry = FloatArray(4); private val rz = FloatArray(4)

    private fun roofPoly(n: Int, color: Int) {
        // light the slope a little brighter than the walls
        var nx = 0f; var ny = 0f
        for (i in 0 until n) { val j = (i + 1) % n; nx += (ry[i] - ry[j]) * (rz[i] + rz[j]); ny += (rz[i] - rz[j]) * (rx[i] + rx[j]) }
        val l = sqrt(nx * nx + ny * ny)
        val shade = if (l > 1e-5f) (1f + scene.sideShade(nx / l, ny / l)) * .5f else 1f
        scene.poly(n, rx, ry, rz, scene.lit(color, shade + .08f))
    }

    private fun roofTri(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, color: Int) {
        rx[0] = x0; ry[0] = y0; rz[0] = z0; rx[1] = x1; ry[1] = y1; rz[1] = z1; rx[2] = x2; ry[2] = y2; rz[2] = z2
        roofPoly(3, color)
    }

    private fun roofQuad(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, color: Int) {
        rx[0] = x0; ry[0] = y0; rz[0] = z0; rx[1] = x1; ry[1] = y1; rz[1] = z1; rx[2] = x2; ry[2] = y2; rz[2] = z2; rx[3] = x3; ry[3] = y3; rz[3] = z3
        roofPoly(4, color)
    }

    /**
     * Hipped roof over [X0,Y0]-[X1,Y1] from eave height [z0], ridge [rise] above along the longer side. [lift] raises
     * the eaves toward the corners (split into [segs] pieces so they curve). Slopes facing away from the camera
     * are drawn first for the software painter.
     */
    private fun hipRoof(X0: Float, Y0: Float, X1: Float, Y1: Float, z0: Float, rise: Float, lift: Float, segs: Int, color: Int) {
        val w = X1 - X0; val d = Y1 - Y0
        val alongX = w >= d
        val half = (if (alongX) d else w) * .5f
        val zr = z0 + rise
        val cx = (X0 + X1) * .5f; val cy = (Y0 + Y1) * .5f
        // eave height at a point along a side, t in 0..1 from one corner to the other
        fun ez(t: Float) = z0 + lift * (2f * t - 1f) * (2f * t - 1f)
        for (pass in 0..1) {
            for (side in 0..3) {
                // outward direction of this slope
                val nx = when (side) { 2 -> -1f; 3 -> 1f; else -> 0f }; val ny = when (side) { 0 -> -1f; 1 -> 1f; else -> 0f }
                val facing = (scene.camLX - cx) * nx + (scene.camLY - cy) * ny > 0f
                if (facing != (pass == 1)) continue
                val long = if (alongX) side <= 1 else side >= 2
                if (long) {
                    // a long slope: strips from the eave up to the ridge (clamped to the ridge's ends)
                    for (sIdx in 0 until segs) {
                        val ta = sIdx.toFloat() / segs; val tb = (sIdx + 1).toFloat() / segs
                        when (side) {
                            0 -> { val xa = X0 + w * ta; val xb = X0 + w * tb
                                roofQuad(xa, Y0, ez(ta), xb, Y0, ez(tb), xb.coerceIn(X0 + half, X1 - half), cy, zr, xa.coerceIn(X0 + half, X1 - half), cy, zr, color) }
                            1 -> { val xa = X1 - w * ta; val xb = X1 - w * tb
                                roofQuad(xa, Y1, ez(ta), xb, Y1, ez(tb), xb.coerceIn(X0 + half, X1 - half), cy, zr, xa.coerceIn(X0 + half, X1 - half), cy, zr, color) }
                            2 -> { val ya = Y1 - d * ta; val yb = Y1 - d * tb
                                roofQuad(X0, ya, ez(ta), X0, yb, ez(tb), cx, yb.coerceIn(Y0 + half, Y1 - half), zr, cx, ya.coerceIn(Y0 + half, Y1 - half), zr, color) }
                            else -> { val ya = Y0 + d * ta; val yb = Y0 + d * tb
                                roofQuad(X1, ya, ez(ta), X1, yb, ez(tb), cx, yb.coerceIn(Y0 + half, Y1 - half), zr, cx, ya.coerceIn(Y0 + half, Y1 - half), zr, color) }
                        }
                    }
                } else {
                    // a hip end: two triangles, so a lifted eave dips in the middle
                    val zc = ez(0f); val zm = ez(.5f)
                    when (side) {
                        0 -> { val px = cx; val py = Y0 + half
                            roofTri(X0, Y0, zc, cx, Y0, zm, px, py, zr, color); roofTri(cx, Y0, zm, X1, Y0, zc, px, py, zr, color) }
                        1 -> { val px = cx; val py = Y1 - half
                            roofTri(X1, Y1, zc, cx, Y1, zm, px, py, zr, color); roofTri(cx, Y1, zm, X0, Y1, zc, px, py, zr, color) }
                        2 -> { val px = X0 + half; val py = cy
                            roofTri(X0, Y1, zc, X0, cy, zm, px, py, zr, color); roofTri(X0, cy, zm, X0, Y0, zc, px, py, zr, color) }
                        else -> { val px = X1 - half; val py = cy
                            roofTri(X1, Y0, zc, X1, cy, zm, px, py, zr, color); roofTri(X1, cy, zm, X1, Y1, zc, px, py, zr, color) }
                    }
                }
            }
        }
    }

    /** Window light at night: some rooms lit, some dark. */
    private fun nightWindow(b: Building, f: Int, col: Int, face: Int): Int =
        if (hash(b.id, f * 64 + col, face) % 100 < 40) WorldRenderer.LIT else scene.lit(WorldRenderer.GLASS_NIGHT, 1f)

    /**
     * Bahay na bato: a stone ground floor with arched openings, and on the street side an upper floor of wood that
     * juts out over the sidewalk, glazed with rows of capiz-shell panels. Shuttered windows elsewhere.
     */
    private fun colonialFacade(b: Building, face: Int, len: Float, h: Float, shade: Float, near: Boolean, shop: Boolean, night: Boolean) {
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
        val stone = scene.lit(c(0xFF8E7A62), shade)
        val wood = scene.lit(WOOD, shade)
        val glass = scene.lit(WorldRenderer.GLASS_DARK, shade)
        val g1 = 3.9f
        faceQuad(x0, y0, x1, y1, face, 0f, len, 0f, if (shop) .3f else g1, stone, .03f)
        val cols = ((len - 1.2f) / 3f).toInt().coerceAtLeast(1)
        val start = (len - cols * 3f) / 2f
        if (!shop) for (cIdx in 0 until cols) {
            val u0 = start + cIdx * 3f + .75f; val u1 = u0 + 1.5f
            faceQuad(x0, y0, x1, y1, face, u0, u1, .8f, 2.9f, if (night) nightWindow(b, 0, cIdx, face) else glass)
            if (near) {
                // a round arch over the opening and an iron grille
                faceQuad(x0, y0, x1, y1, face, u0 - .2f, u1 + .2f, 2.9f, 3.35f, scene.lit(c(0xFFCDBB9C), shade), .05f)
                for (q in 1..3) faceQuad(x0, y0, x1, y1, face, u0 + q * .375f - .03f, u0 + q * .375f + .03f, .8f, 2.9f, scene.lit(IRON, 1f), .06f)
            }
        }
        val upper = ((h - g1 - .2f) / 3.3f).toInt()
        if (upper <= 0) return
        if (face == 1) {
            // the wooden volada over the street
            val top = min(h - .15f, g1 + upper * 3.3f)
            faceBox(x0, y0, x1, y1, face, .2f, len - .2f, g1, top, .8f, scene.snowy(WOOD), WOOD)
            faceSlab(x0, y0, x1, y1, face, .1f, len - .1f, g1 - .25f, g1, 0f, .95f, WOOD, WOOD)
            for (f in 0 until upper) {
                val z0 = g1 + f * 3.3f + .7f; val z1 = z0 + 1.9f
                val panes = ((len - .8f) / .95f).toInt().coerceAtLeast(2)
                val pw = (len - .8f) / panes
                if (near) for (q in 0 until panes) {
                    val u = .4f + q * pw
                    val col = if (night && hash(b.id, f, q / 3) % 3 == 0) WorldRenderer.LIT else scene.lit(CAPIZ, shade)
                    faceQuad(x0, y0, x1, y1, face, u + .06f, u + pw - .06f, z0, z1, col, .84f)
                    // the capiz grid
                    faceQuad(x0, y0, x1, y1, face, u + .06f, u + pw - .06f, (z0 + z1) / 2 - .03f, (z0 + z1) / 2 + .03f, wood, .86f)
                } else faceQuad(x0, y0, x1, y1, face, .45f, len - .45f, z0, z1, if (night) WorldRenderer.LIT else scene.lit(CAPIZ, shade), .84f)
                // ventanilla: a low sliding panel under the windows
                if (near) faceQuad(x0, y0, x1, y1, face, .45f, len - .45f, z0 - .55f, z0 - .1f, scene.lit(c(0xFF3E2A1C), shade), .85f)
            }
            return
        }
        for (f in 0 until upper) {
            val z0 = g1 + f * 3.3f + .6f; val z1 = z0 + 2.1f
            for (cIdx in 0 until cols) {
                val u0 = start + cIdx * 3f + .9f; val u1 = u0 + 1.2f
                faceQuad(x0, y0, x1, y1, face, u0, u1, z0, z1, if (night) nightWindow(b, f + 1, cIdx, face) else glass)
                if (near) {
                    val shutter = scene.lit(if (b.id % 2 == 0) c(0xFF3E5B45) else c(0xFF5B3A29), shade)
                    faceQuad(x0, y0, x1, y1, face, u0 - .6f, u0 - .05f, z0, z1, shutter, .06f)
                    faceQuad(x0, y0, x1, y1, face, u1 + .05f, u1 + .6f, z0, z1, shutter, .06f)
                }
            }
        }
    }

    /**
     * Painted concrete low-rise: windows behind iron grilles with little concrete hoods, a painted advertisement on
     * some side walls and, on houses, a sari-sari store window with a tin awning.
     */
    private fun tropicalFacade(b: Building, face: Int, len: Float, h: Float, shade: Float, near: Boolean, shop: Boolean, night: Boolean) {
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
        val k = hash(b.id, face, 31)
        val glass = scene.lit(WorldRenderer.GLASS_DARK, shade)
        val trimCol = scene.lit(c(0xFFDAD5CB), shade)
        faceQuad(x0, y0, x1, y1, face, 0f, len, 0f, .5f, scene.lit(c(0xFF8F8A80), shade), .03f)
        val baseZ = if (shop) 4.2f else .9f
        val floors = ((h - baseZ - .6f) / 3.1f).toInt()
        // a painted billboard on a blank side wall
        if (face >= 2 && k % 3 == 0 && len > 7f && h > 7f) {
            val ad = AD_COLORS[k % AD_COLORS.size]
            faceQuad(x0, y0, x1, y1, face, len * .12f, len * .88f, h * .45f, h - 1f, scene.lit(ad, shade), .04f)
            faceQuad(x0, y0, x1, y1, face, len * .18f, len * .82f, h * .45f + (h * .55f - 1f) * .55f, h * .45f + (h * .55f - 1f) * .7f, scene.lit(c(0xFFF4F1EA), shade), .05f)
            return
        }
        val cols = ((len - 1f) / 3.2f).toInt().coerceAtLeast(1)
        val start = (len - cols * 3.2f) / 2f
        for (f in 0 until floors) {
            val z0 = baseZ + f * 3.1f + .9f; val z1 = z0 + 1.5f
            for (cIdx in 0 until cols) {
                if (hash(b.id, f * 7 + cIdx, face) % 6 == 0) continue
                val u0 = start + cIdx * 3.2f + .7f; val u1 = u0 + 1.8f
                faceQuad(x0, y0, x1, y1, face, u0, u1, z0, z1, if (night) nightWindow(b, f, cIdx, face) else glass)
                if (!near) continue
                val iron = scene.lit(c(0xFF33363A), 1f)
                for (q in 1..4) faceQuad(x0, y0, x1, y1, face, u0 + q * .36f - .025f, u0 + q * .36f + .025f, z0, z1, iron, .06f)
                faceQuad(x0, y0, x1, y1, face, u0, u1, (z0 + z1) / 2 - .025f, (z0 + z1) / 2 + .025f, iron, .06f)
                if (hash(b.id, f, cIdx + 9) % 3 == 0) {
                    // a colored tin awning
                    val col = TIN[(k + cIdx) % TIN.size]
                    faceSlab(x0, y0, x1, y1, face, u0 - .2f, u1 + .2f, z1 + .15f, z1 + .25f, 0f, .6f, scene.snowy(col), col)
                } else faceBox(x0, y0, x1, y1, face, u0 - .15f, u1 + .15f, z1 + .1f, z1 + .22f, .45f, trimCol, trimCol)
            }
        }
        if (face != 1 || shop || !near) return
        // the sari-sari window: a counter behind a grille, under a tin awning
        val u0 = len * .55f; val u1 = min(len - .5f, u0 + 2.2f)
        if (u1 - u0 < 1.2f) return
        faceQuad(x0, y0, x1, y1, face, u0, u1, .9f, 2.1f, scene.lit(c(0xFF3A3530), shade), .05f)
        for (q in 0..5) faceQuad(x0, y0, x1, y1, face, u0 + .2f + q * .32f, u0 + .32f + q * .32f, 1.2f, 1.6f, scene.lit(AD_COLORS[(q + k) % AD_COLORS.size], shade), .06f)
        faceQuad(x0, y0, x1, y1, face, u0, u1, .85f, .95f, scene.lit(c(0xFFB0A090), shade), .3f)
        faceSlab(x0, y0, x1, y1, face, u0 - .3f, u1 + .3f, 2.25f, 2.35f, 0f, .9f, scene.snowy(TIN[k % TIN.size]), TIN[k % TIN.size])
    }

    /**
     * A narrow tiled building: banded windows floor by floor, air-conditioner units on the walls and, on the street
     * side, a tall vertical sign of stacked panels that lights up at night.
     */
    private fun pencilFacade(b: Building, face: Int, len: Float, h: Float, shade: Float, near: Boolean, front: Boolean, shop: Boolean, night: Boolean) {
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
        val glass = scene.lit(WorldRenderer.GLASS, shade)
        val tr = scene.lit(trim(b), shade)
        val baseZ = if (shop && front) 4.2f else 1f
        val floors = ((h - baseZ - .6f) / 3.2f).toInt()
        val segs = ((len - .8f) / 2.2f).toInt().coerceAtLeast(1)
        val sw = (len - .8f) / segs
        val side = face >= 2
        for (f in 0 until floors) {
            val z0 = baseZ + f * 3.2f + .9f; val z1 = z0 + (if (side) 1.3f else 1.7f)
            if (side && hash(b.id, f, face) % 3 != 0) {
                // side walls: a small window or two
                val u = len * (.3f + (hash(b.id, f, 5) % 40) / 100f)
                faceQuad(x0, y0, x1, y1, face, u - .5f, u + .5f, z0, z1, if (night) nightWindow(b, f, 0, face) else glass)
            } else if (!side) {
                for (sIdx in 0 until segs) {
                    val u0 = .4f + sIdx * sw + .08f; val u1 = .4f + (sIdx + 1) * sw - .08f
                    faceQuad(x0, y0, x1, y1, face, u0, u1, z0, z1, if (night) nightWindow(b, f, sIdx, face) else glass)
                }
                if (near) faceQuad(x0, y0, x1, y1, face, 0f, len, z0 - .55f, z0 - .4f, tr, .05f)
            }
            // air conditioners hung on the wall
            if (near && hash(b.id, f * 3 + face, 77) % 3 == 0) {
                val u = if (side) len * .7f else .9f + (hash(b.id, f, 3) % 100) / 100f * (len - 2f)
                faceBox(x0, y0, x1, y1, face, u - .4f, u + .4f, z0 - .3f, z0 + .3f, .4f, c(0xFFD9DCDF), c(0xFFC2C6CA))
            }
        }
        if (front && floors >= 1 && len >= 3.5f) verticalSign(b, len, h, floors, near, night)
    }

    /** A blade sign standing out from the front corner: stacked panels with characters, lit at night. */
    private fun verticalSign(b: Building, len: Float, h: Float, floors: Int, near: Boolean, night: Boolean) {
        val k = hash(b.id, 13, 29)
        val sx = if (k % 2 == 0) .45f else len - .45f
        val z0 = 3.6f; val z1 = min(h - .4f, z0 + 3.2f * min(floors, 6))
        if (z1 - z0 < 2.5f) return
        val y0 = b.d + .15f; val y1 = b.d + 1.45f
        val frame = c(0xFF2B2F35)
        scene.box(sx - .16f, y0, z0, sx + .16f, y1, z1, frame, frame)
        // panels on the side the camera sees
        val xs = if (scene.camLX > sx) sx + .17f else sx - .17f
        val panels = ((z1 - z0) / 2.4f).toInt().coerceAtLeast(1)
        val ph = (z1 - z0) / panels
        for (q in 0 until panels) {
            val pz0 = z0 + q * ph + .1f; val pz1 = z0 + (q + 1) * ph - .1f
            val idx = (k + q) % NEON.size
            val col = if (night) NEON[idx] else scene.lit(SIGN_DAY[idx], 1f)
            if (night) scene.glow = true
            scene.quad(xs, y0 + .08f, pz0, xs, y1 - .08f, pz0, xs, y1 - .08f, pz1, xs, y0 + .08f, pz1, col)
            scene.glow = false
            if (near) {
                // characters: white strokes down the panel
                val white = if (night) c(0xFFFFF4D6) else c(0xFFF4F4F4)
                val chars = ((pz1 - pz0) / .8f).toInt().coerceAtLeast(1)
                for (ch in 0 until chars) {
                    val cz = pz0 + .15f + ch * .8f
                    val off = if (xs > sx) .01f else -.01f
                    scene.quad(xs + off, y0 + .35f, cz, xs + off, y1 - .35f, cz, xs + off, y1 - .35f, cz + .1f, xs + off, y0 + .35f, cz + .1f, white)
                    scene.quad(xs + off, y0 + .7f, cz, xs + off, y0 + .8f, cz, xs + off, y0 + .8f, cz + .55f, xs + off, y0 + .7f, cz + .55f, white)
                }
            }
        }
    }

    /**
     * Hanok: a granite base, white plaster walls framed by dark timber posts and beams, and lattice windows papered
     * with hanji that glow warmly at night.
     */
    private fun hanokFacade(b: Building, face: Int, len: Float, h: Float, shade: Float, near: Boolean, shop: Boolean, night: Boolean) {
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
        val wood = scene.lit(WOOD, shade)
        faceQuad(x0, y0, x1, y1, face, 0f, len, 0f, .65f, scene.lit(c(0xFF9C9B93), shade), .05f)
        val bays = ((len) / 2.4f).toInt().coerceAtLeast(1)
        val bw = len / bays
        for (q in 0..bays) {
            val u = q * bw
            faceQuad(x0, y0, x1, y1, face, (u - .14f).coerceAtLeast(0f), (u + .14f).coerceAtMost(len), .65f, h, wood, .06f)
        }
        faceQuad(x0, y0, x1, y1, face, 0f, len, h - .45f, h - .2f, wood, .06f)
        if (shop) return
        for (q in 0 until bays) {
            if (face == 1 && kotlin.math.abs(q * bw + bw / 2 - len / 2) < 1.2f) continue // the gate
            if (hash(b.id, q, face) % 4 == 0) continue
            val u0 = q * bw + .4f; val u1 = (q + 1) * bw - .4f
            val z0 = 1.2f; val z1 = min(h - .7f, 2.6f)
            if (z1 - z0 < .6f) continue
            val paper = if (night && hash(b.id, q, 3) % 3 != 0) c(0xFFFFE3A0) else scene.lit(HANJI, shade)
            faceQuad(x0, y0, x1, y1, face, u0, u1, z0, z1, paper, .04f)
            if (near) {
                for (g in 1..3) { val u = u0 + (u1 - u0) * g / 4; faceQuad(x0, y0, x1, y1, face, u - .025f, u + .025f, z0, z1, wood, .05f) }
                for (g in 1..2) { val z = z0 + (z1 - z0) * g / 3; faceQuad(x0, y0, x1, y1, face, u0, u1, z - .025f, z + .025f, wood, .05f) }
            }
        }
    }

    // ------------------------------------------------------------------ landmarks

    private fun landmark(b: Building, placeId: String, d0: Float) {
        if (d0 > 320f) return
        val face = 1
        val len = b.w
        val x0 = 0f; val y0 = 0f; val x1 = b.w; val y1 = b.d
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
                val cx = (b.w * .5f); val cy = (b.d * .5f)
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
        val x0 = 3f; val x1 = b.w - 3f; val y0 = 5f; val y1 = b.d - 5f
        val pillar = c(0xFFE0E0E0)
        for ((px0, py0) in listOf(x0 + .5f to y0 + .5f, x1 - .9f to y0 + .5f, x0 + .5f to y1 - .9f, x1 - .9f to y1 - .9f)) scene.box(px0, py0, 0f, px0 + .4f, py0 + .4f, 5f, pillar, pillar)
        val mx = (x0 + x1) / 2
        // pumps on islands
        for (s in intArrayOf(-1, 1)) {
            val pxp = mx + s * 2.5f
            scene.box(pxp - 1.2f, (b.d * .5f) - 1.4f, 0f, pxp + 1.2f, (b.d * .5f) + 1.4f, .2f, c(0xFFBDBDBD), c(0xFF9E9E9E))
            scene.box(pxp - .5f, (b.d * .5f) - .6f, .2f, pxp + .5f, (b.d * .5f) + .6f, 1.8f, c(0xFFD32F2F), c(0xFFD32F2F))
            scene.box(pxp - .45f, (b.d * .5f) - .62f, 1.1f, pxp + .45f, (b.d * .5f) - .6f, 1.6f, c(0xFF263238), c(0xFF263238))
        }
        scene.box(x0, y0, 5f, x1, y1, 5.7f, c(0xFFF5F5F5), color)
        if (scene.daylight < .5f) scene.disc((b.w * .5f), (b.d * .5f), .05f, (x1 - x0) * .45f, (y1 - y0) * .45f, 14, scene.withAlpha(c(0xFFFFF3C4), .25f))
        // price sign by the road
        if (d0 < 200f) {
            val sx = 1.5f; val sy = b.d - 1.5f
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

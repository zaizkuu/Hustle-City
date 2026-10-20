package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Landmark
import com.hustlecity.game.world.Look
import com.hustlecity.game.world.Maps
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The city's landmarks: broadcast towers (a steel lattice like Tokyo Tower, or a concrete tower on a wooded hill like
 * N Seoul Tower), shrine gates, monuments, palace gates, churches with domes and bell towers, a Ferris wheel, a city hall
 * with its clock tower and a Chinatown arch over the street.
 * Billboard squares dress the buildings around them with giant screens and blade signs that glow at night.
 * Everything is drawn in the landmark's own frame, scaled by its size.
 */
class Landmarks(private val city: City) {
    companion object {
        private val ORANGE = c(0xFFE0522A)
        private val WHITE = c(0xFFF1EFEA)
        private val GLOW_ORANGE = c(0xFFFF8A3D)
        private val GLOW_WHITE = c(0xFFFFE3C2)
        private val DARK_GLASS = c(0xFF26313D)
        private val CONCRETE = c(0xFFE9E8E2)
        private val HILL = c(0xFF4A7A3E)
        private val FOREST = intArrayOf(c(0xFF3E6E36), c(0xFF4C7F3F), c(0xFF35602F))
        private val VERMILION = c(0xFFD9442B)
        private val BLACK = c(0xFF232323)
        private val GRANITE = c(0xFFCFC8BB)
        private val BRONZE = c(0xFF5F5236)
        private val TILE_ROOF = c(0xFF4C535B)
        private val KOREAN_ROOF = c(0xFF5A626C)
        private val RED_WOOD = c(0xFFA3302A)
        private val DANCHEONG = c(0xFF3C8A74)
        private val PLASTER = c(0xFFF2EFE7)
        private val ISHIGAKI = c(0xFF9C988E)
        private val SCREEN_BG = intArrayOf(c(0xFF1B3A8C), c(0xFFD62D3A), c(0xFFC2187A), c(0xFF14A6C9), c(0xFFF2C230), c(0xFF1FA35B),
            c(0xFF6A2BB8), c(0xFFF26A1B), c(0xFF15171C), c(0xFFE8E8E8))
        private val SCREEN_FG = intArrayOf(c(0xFFFFFFFF), c(0xFFFFE14D), c(0xFF4DE1FF), c(0xFFFF5FD8), c(0xFF8CFF6B), c(0xFFFF4D4D))
        private val RIM_LIGHTS = intArrayOf(c(0xFFFF5FD8), c(0xFF5FE3FF), c(0xFFFFE680), c(0xFF2EE87A))
        private val PILASTERS = floatArrayOf(-8.2f, -2.8f, 2.8f, 8.2f)
        private val GONDOLAS = intArrayOf(c(0xFFE94B5A), c(0xFFF2C94C), c(0xFF2F80ED), c(0xFF27AE60), c(0xFF9B51E0), c(0xFFF2994A))
        /** Seconds per turn of the Ferris wheel. */
        private const val WHEEL_TURN = 300f
    }

    /** A giant screen (or a blade sign) on face [face] of building [b], [u0]..[u1] along it, for billboard square [lm]. */
    class Dress(val lm: Int, val b: Int, val face: Int, val u0: Float, val u1: Float, val z0: Float, val z1: Float, val blade: Boolean, val seed: Int)

    val dress = ArrayList<Dress>()
    /** Per landmark: length of the causeway in front of a gate (0 if none) and the arch-sign road for an arcade. */
    private val causeway = FloatArray(city.landmarks.size)
    private val arcadeEdge = IntArray(city.landmarks.size) { -1 }
    private val scrambleNode = IntArray(city.landmarks.size) { -1 }
    /** Per street arch: how far out its inner columns (at the curbs) and outer ones (on the sidewalks) stand. */
    private val archInner = FloatArray(city.landmarks.size) { 6.7f }
    private val archOuter = FloatArray(city.landmarks.size) { 9.2f }

    init {
        for ((i, l) in city.landmarks.withIndex()) {
            when (l.kind) {
                Landmark.BILLBOARDS -> dressSquare(i, l)
                Landmark.PALACE_GATE -> {
                    // the gate's front looks over water? then a causeway crosses it to the far bank
                    var y = -7f; var wet = false
                    while (y > -60f) {
                        val w = city.shore.wet(l.wx(0f, y * l.size), l.wy(0f, y * l.size))
                        if (w) wet = true else if (wet) break
                        y -= 1f
                    }
                    causeway[i] = if (wet) -y + 1f else 0f
                }
                Landmark.ARCH -> {
                    val hit = com.hustlecity.game.world.RoadHit()
                    if (city.roads.nearest(l.x, l.y, 30f, hit)) {
                        val cls = city.roads.edges[hit.edge].cls
                        archInner[i] = cls.halfWidth + .7f; archOuter[i] = max(cls.outer - .8f, cls.halfWidth + 2.4f)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ billboard squares

    private fun dressSquare(li: Int, l: Landmark) {
        val square = l.style == Landmark.SCREENS
        val reach = (if (square) 62f else 48f) * l.size + 10f
        val cand = ArrayList<FloatArray>()
        val seen = HashSet<Int>()
        city.forBuildingsIn(l.x - reach - 40f, l.y - reach - 40f, l.x + reach + 40f, l.y + reach + 40f) { b ->
            if (!b.solid || b.height < 8f || !seen.add(b.id)) return@forBuildingsIn
            // every face looking onto the square (Times Square wraps its corners in screens too)
            var faces = 0
            for (f in 0 until 4) {
                val (fx, fy) = faceCentre(b, f)
                val (nx, ny) = faceNormal(b, f)
                val dx = l.x - fx; val dy = l.y - fy
                val d = sqrt(dx * dx + dy * dy)
                if (d > reach || d < 1f) continue
                if ((dx * nx + dy * ny) / d > (if (square) .3f else .45f)) { cand += floatArrayOf(b.id.toFloat(), f.toFloat(), d); faces++ }
            }
            if (!square && faces > 1) {
                // elsewhere just the face looking most squarely at it
                val mine = cand.subList(cand.size - faces, cand.size)
                val keep = mine.minByOrNull { it[2] }!!
                mine.clear(); cand += keep
            }
        }
        cand.sortBy { it[2] }
        var screens = 0
        val most = if (square) 26 else 9
        for (cd in cand) {
            val b = city.buildings[cd[0].toInt()]
            val f = cd[1].toInt()
            val len = if (f <= 1) b.w else b.d
            val dense = b.hood.look == Look.DENSE_ASIA
            val seed = b.id * 7 + li * 131 + f * 17
            if (screens < most) {
                val w = min(min(len - 1.4f, (if (square) 17f else 15f) * l.size), b.height * .95f)
                if (w >= 4f) {
                    val h = min(w * .62f + 1.5f, b.height - 4.5f)
                    if (h >= 3f) {
                        val z0 = if (l.style == Landmark.SCREENS) max(5.2f, min(b.height * .3f, b.height - h - 1.2f)) else max(5f, min(9f, b.height - h - 1.2f))
                        val u0 = (len - w) * (if (seed % 3 == 0) .5f else if (seed % 3 == 1) .25f else .75f).coerceIn(.1f, .9f)
                        dress += Dress(li, b.id, f, u0, u0 + w, z0, z0 + h, false, seed)
                        screens++
                        // Times Square stacks more screens up the towers
                        var top = z0 + h
                        var k = 1
                        while (square && k <= 2 && b.height > top + 10f) {
                            val h2 = min(h * (.85f - k * .1f), b.height - (top + 2.5f) - 2f)
                            if (h2 < 3f) break
                            val ww = w * (.9f - k * .1f)
                            dress += Dress(li, b.id, f, len * .5f - ww * .5f, len * .5f + ww * .5f, top + 2.5f, top + 2.5f + h2, false, seed + k)
                            top += 2.5f + h2; k++
                        }
                    }
                }
            }
            // blade signs on the Asian streets: narrow vertical boards sticking out at the corner
            if (dense || l.style != Landmark.SCREENS) {
                val top = min(b.height - .8f, 4f + 9f * l.size + (seed % 5))
                if (top > 7f) dress += Dress(li, b.id, f, if (seed % 2 == 0) .7f else len - .7f, 0f, 3.6f, top, true, seed + 3)
            }
        }
        if (l.style == Landmark.SCRAMBLE) scrambleNode[li] = city.scrambleNode(l)?.id ?: -1
        if (l.style == Landmark.ARCADE) {
            val hit = com.hustlecity.game.world.RoadHit()
            if (city.roads.nearest(l.x, l.y, 60f, hit)) arcadeEdge[li] = hit.edge
        }
    }

    private fun faceCentre(b: Building, f: Int): Pair<Float, Float> = when (f) {
        0 -> b.wx(b.w * .5f, 0f) to b.wy(b.w * .5f, 0f)
        1 -> b.wx(b.w * .5f, b.d) to b.wy(b.w * .5f, b.d)
        2 -> b.wx(0f, b.d * .5f) to b.wy(0f, b.d * .5f)
        else -> b.wx(b.w, b.d * .5f) to b.wy(b.w, b.d * .5f)
    }

    private fun faceNormal(b: Building, f: Int): Pair<Float, Float> = when (f) {
        0 -> b.sn to -b.cs
        1 -> -b.sn to b.cs
        2 -> -b.cs to -b.sn
        else -> b.cs to b.sn
    }

    /** Draws dressing item [i] (a screen or blade sign) in its building's frame. */
    fun drawDress(scene: Scene3D, i: Int, d: Float, hour: Int) {
        val it = dress[i]
        val b = city.buildings[it.b]
        if (d > 520f) return
        scene.setFrame(b.ox, b.oy, b.cs, b.sn)
        try { if (it.blade) blade(scene, b, it, d) else screen(scene, b, it, d, hour) } finally { scene.glow = false; scene.material = Mat.PLAIN; scene.clearFrame() }
    }

    /** Quad on a building face (BuildingPainter's convention: 0 back, 1 front, 2 and 3 the sides), [out] off the wall. */
    private fun fq(scene: Scene3D, b: Building, f: Int, u0: Float, u1: Float, z0: Float, z1: Float, out: Float, color: Int) {
        when (f) {
            0 -> { val y = -out; scene.quad(b.w - u0, y, z0, b.w - u1, y, z0, b.w - u1, y, z1, b.w - u0, y, z1, color) }
            1 -> { val y = b.d + out; scene.quad(u0, y, z0, u1, y, z0, u1, y, z1, u0, y, z1, color) }
            2 -> { val x = -out; scene.quad(x, u0, z0, x, u1, z0, x, u1, z1, x, u0, z1, color) }
            else -> { val x = b.w + out; scene.quad(x, b.d - u0, z0, x, b.d - u1, z0, x, b.d - u1, z1, x, b.d - u0, z1, color) }
        }
    }

    private fun faceVisible(scene: Scene3D, b: Building, f: Int): Boolean = scene.gpu != null || when (f) {
        0 -> scene.camLY < 0f
        1 -> scene.camLY > b.d
        2 -> scene.camLX < 0f
        else -> scene.camLX > b.w
    }

    /** A box standing out of face [f] from [o0] to [o1]. */
    private fun fbox(scene: Scene3D, b: Building, f: Int, u0: Float, u1: Float, z0: Float, z1: Float, o0: Float, o1: Float, top: Int, side: Int) {
        when (f) {
            0 -> scene.box(b.w - u1, -o1, z0, b.w - u0, -o0, z1, top, side)
            1 -> scene.box(u0, b.d + o0, z0, u1, b.d + o1, z1, top, side)
            2 -> scene.box(-o1, u0, z0, -o0, u1, z1, top, side)
            else -> scene.box(b.w + o0, b.d - u1, z0, b.w + o1, b.d - u0, z1, top, side)
        }
    }

    private fun screen(scene: Scene3D, b: Building, s: Dress, d: Float, hour: Int) {
        if (!faceVisible(scene, b, s.face)) return
        val frame = c(0xFF1E2126)
        fbox(scene, b, s.face, s.u0 - .25f, s.u1 + .25f, s.z0 - .25f, s.z1 + .25f, .05f, .4f, frame, frame)
        // the picture changes every hour; screens are lights, bright by day and glowing at night
        val k = (s.seed + hour * 7) and 0x7fffffff
        val bg = SCREEN_BG[k % SCREEN_BG.size]
        val fg = SCREEN_FG[(k / 3) % SCREEN_FG.size]
        val night = scene.daylight < .55f
        scene.glow = true
        fun col(cc: Int) = if (night) cc else scene.lit(cc, 1.25f)
        val u0 = s.u0; val u1 = s.u1; val z0 = s.z0; val z1 = s.z1
        val w = u1 - u0; val h = z1 - z0
        fq(scene, b, s.face, u0, u1, z0, z1, .42f, col(bg))
        if (d < 360f) {
            when (k % 4) {
                0 -> {
                    // a logo disc and two lines of text
                    disc(scene, b, s.face, u0 + w * .25f, z0 + h * .55f, min(w, h) * .26f, .44f, col(fg))
                    fq(scene, b, s.face, u0 + w * .5f, u1 - w * .08f, z0 + h * .58f, z0 + h * .7f, .44f, col(0xFFFFFFFF.toInt()))
                    fq(scene, b, s.face, u0 + w * .5f, u1 - w * .22f, z0 + h * .38f, z0 + h * .48f, .44f, col(fg))
                }
                1 -> {
                    // a product shot on a bright band
                    fq(scene, b, s.face, u0, u1, z0 + h * .18f, z0 + h * .42f, .44f, col(fg))
                    fq(scene, b, s.face, u0 + w * .38f, u0 + w * .62f, z0 + h * .12f, z0 + h * .86f, .46f, col(SCREEN_BG[(k + 4) % SCREEN_BG.size]))
                    fq(scene, b, s.face, u0 + w * .08f, u0 + w * .32f, z0 + h * .62f, z0 + h * .72f, .44f, col(0xFFFFFFFF.toInt()))
                }
                2 -> {
                    // a face in close-up
                    disc(scene, b, s.face, u0 + w * .5f, z0 + h * .52f, min(w, h) * .34f, .44f, col(c(0xFFF0C8A0)))
                    fq(scene, b, s.face, u0 + w * .05f, u1 - w * .05f, z0 + h * .06f, z0 + h * .17f, .44f, col(fg))
                }
                else -> {
                    // stacked headlines
                    for (r in 0 until 3) {
                        val zz = z0 + h * (.2f + r * .24f)
                        fq(scene, b, s.face, u0 + w * .08f, u1 - w * (.08f + r * .12f), zz, zz + h * .12f, .44f, col(if (r == 1) fg else 0xFFFFFFFF.toInt()))
                    }
                }
            }
        }
        scene.glow = false
    }

    private val dx = FloatArray(16); private val dy = FloatArray(16); private val dz = FloatArray(16)

    /** A filled circle lying on a building face. */
    private fun disc(scene: Scene3D, b: Building, f: Int, u: Float, z: Float, r: Float, out: Float, color: Int) {
        val n = 10
        for (k in 0 until n) {
            val a = k * 2f * PI.toFloat() / n
            val uu = u + cos(a) * r; val zz = z + sin(a) * r
            when (f) {
                0 -> { dx[k] = b.w - uu; dy[k] = -out }
                1 -> { dx[k] = uu; dy[k] = b.d + out }
                2 -> { dx[k] = -out; dy[k] = uu }
                else -> { dx[k] = b.w + out; dy[k] = b.d - uu }
            }
            dz[k] = zz
        }
        scene.poly(n, dx, dy, dz, color)
    }

    private fun blade(scene: Scene3D, b: Building, s: Dress, d: Float) {
        if (d > 300f) return
        val k = s.seed and 0x7fffffff
        val bg = SCREEN_BG[k % 8]
        val night = scene.daylight < .55f
        // a narrow board standing out from the wall, lit letters down both sides
        val u = s.u0
        fbox(scene, b, s.face, u - .18f, u + .18f, s.z0, s.z1, .1f, 1.5f, c(0xFF2A2D31), bg)
        if (d < 160f) {
            scene.glow = true
            val letter = if (night) SCREEN_FG[k % SCREEN_FG.size] else c(0xFFFFFFFF)
            var z = s.z1 - .9f
            while (z > s.z0 + .5f) {
                // letters on both broad sides of the board
                when (s.face) {
                    0, 1 -> {
                        val y0 = if (s.face == 1) b.d + .35f else -1.25f; val y1 = y0 + .9f
                        val x0 = if (s.face == 1) u else b.w - u
                        for (sd in -1..1 step 2) scene.quad(x0 + sd * .19f, y0, z - .55f, x0 + sd * .19f, y1, z - .55f, x0 + sd * .19f, y1, z, x0 + sd * .19f, y0, z, letter)
                    }
                    else -> {
                        val x0 = if (s.face == 3) b.w + .35f else -1.25f; val x1 = x0 + .9f
                        val y0 = if (s.face == 2) u else b.d - u
                        for (sd in -1..1 step 2) scene.quad(x0, y0 + sd * .19f, z - .55f, x1, y0 + sd * .19f, z - .55f, x1, y0 + sd * .19f, z, x0, y0 + sd * .19f, z, letter)
                    }
                }
                z -= 1.05f
            }
            scene.glow = false
        }
    }

    // ------------------------------------------------------------------ landmark frame helpers

    private var k = 1f
    private val tx = FloatArray(24); private val ty = FloatArray(24); private val tz = FloatArray(24)

    private fun bx(scene: Scene3D, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, top: Int, side: Int) =
        scene.box(min(x0, x1) * k, min(y0, y1) * k, z0 * k, max(x0, x1) * k, max(y0, y1) * k, z1 * k, top, side)

    /** A box that gives off light: full color on the Canvas path too (skips night shading). */
    private fun glowBox(scene: Scene3D, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int) {
        scene.glow = true
        if (scene.gpu != null) { bx(scene, x0, y0, z0, x1, y1, z1, color, color); scene.glow = false; return }
        val ax = x0 * k; val ay = y0 * k; val az = z0 * k; val cx = x1 * k; val cy = y1 * k; val cz = z1 * k
        if (scene.camLY < ay) scene.quad(ax, ay, az, cx, ay, az, cx, ay, cz, ax, ay, cz, color)
        if (scene.camLY > cy) scene.quad(ax, cy, az, cx, cy, az, cx, cy, cz, ax, cy, cz, color)
        if (scene.camLX < ax) scene.quad(ax, ay, az, ax, cy, az, ax, cy, cz, ax, ay, cz, color)
        if (scene.camLX > cx) scene.quad(cx, ay, az, cx, cy, az, cx, cy, cz, cx, ay, cz, color)
        scene.quad(ax, ay, cz, cx, ay, cz, cx, cy, cz, ax, cy, cz, color)
        scene.glow = false
    }

    private fun q(scene: Scene3D, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, color: Int) =
        scene.quad(x0 * k, y0 * k, z0 * k, x1 * k, y1 * k, z1 * k, x2 * k, y2 * k, z2 * k, x3 * k, y3 * k, z3 * k, color)

    /** A truncated cone (or prism) with [sides] faces turned by [rot], faces away from the camera culled on the Canvas path. */
    private fun frustum(scene: Scene3D, cx: Float, cy: Float, r0: Float, z0: Float, r1: Float, z1: Float, sides: Int, color: Int, rot: Float = 0f, cap: Int = 0) {
        val n = sides.coerceIn(3, 20)
        for (i in 0 until n) {
            val a0 = rot + i * 2f * PI.toFloat() / n; val a1 = rot + (i + 1) * 2f * PI.toFloat() / n
            val nx = cos((a0 + a1) * .5f); val ny = sin((a0 + a1) * .5f)
            if (scene.gpu == null) {
                // the face's real normal leans up as the sides taper: (n·height, rise)
                val rm = (r0 + r1) * .5f * k; val mz = (z0 + z1) * .5f * k
                val ex = scene.camLX - (cx * k + nx * rm); val ey = scene.camLY - (cy * k + ny * rm)
                if ((ex * nx + ey * ny) * (z1 - z0) + (scene.proj.camZ - mz) * (r0 - r1) <= 0f) continue
            }
            q(scene, cx + cos(a0) * r0, cy + sin(a0) * r0, z0, cx + cos(a1) * r0, cy + sin(a1) * r0, z0,
                cx + cos(a1) * r1, cy + sin(a1) * r1, z1, cx + cos(a0) * r1, cy + sin(a0) * r1, z1,
                if (scene.glow) color else scene.lit(color, scene.sideShade(nx, ny) * (if (r1 < r0) 1.05f else 1f)))
        }
        if (cap != 0 && r1 > 0f) {
            for (i in 0 until n) { val a = rot + i * 2f * PI.toFloat() / n; tx[i] = (cx + cos(a) * r1) * k; ty[i] = (cy + sin(a) * r1) * k; tz[i] = z1 * k }
            scene.poly(n, tx, ty, tz, scene.lit(scene.snowy(cap), 1f))
        }
    }

    // ------------------------------------------------------------------ entry points

    /**
     * Draws landmark [i]. [moving] adds the animated parts (the wheel's rotor) for the Canvas path, which draws
     * everything every frame; on the GPU the rotor is its own per-frame drawable ([drawRotor]).
     */
    fun draw(scene: Scene3D, i: Int, d: Float, time: Float, moving: Boolean, mapMode: Boolean) {
        val l = city.landmarks[i]
        k = l.size
        scene.setFrame(l.x, l.y, l.cs, l.sn)
        try {
            when (l.kind) {
                Landmark.TOWER -> if (l.style == Landmark.LATTICE) lattice(scene, d, mapMode) else concreteTower(scene, l, d)
                Landmark.TORII -> { torii(scene, 0f, 1f, d); torii(scene, 38f, .72f, d); lanterns(scene, d) }
                Landmark.MONUMENT -> monument(scene, d)
                Landmark.PALACE_GATE -> if (l.style == Landmark.YAGURA_GATE) yagura(scene, i, d) else hanokGate(scene, d)
                Landmark.CHURCH -> church(scene, l, d)
                Landmark.WHEEL -> wheel(scene, d, time, moving)
                Landmark.BILLBOARDS -> arcade(scene, i, l)
                Landmark.ARCH -> paifang(scene, i, d)
                Landmark.CITY_HALL -> cityHall(scene, l, d)
            }
        } finally { scene.glow = false; scene.material = Mat.PLAIN; scene.clearFrame() }
    }

    /** The Ferris wheel's turning part, drawn every frame on the GPU path. */
    fun drawRotor(scene: Scene3D, i: Int, d: Float, time: Float) {
        val l = city.landmarks[i]
        k = l.size
        scene.setFrame(l.x, l.y, l.cs, l.sn)
        try { rotor(scene, d, time) } finally { scene.glow = false; scene.material = Mat.PLAIN; scene.clearFrame() }
    }

    fun height(l: Landmark): Float = l.size * when (l.kind) {
        Landmark.TOWER -> if (l.style == Landmark.LATTICE) 150f else 140f
        Landmark.WHEEL -> 62f
        Landmark.CHURCH -> 36f
        Landmark.MONUMENT -> 27f
        Landmark.CITY_HALL -> 44f
        Landmark.PALACE_GATE -> 22f
        Landmark.TORII -> 12f
        else -> 12f
    }

    fun radius(l: Landmark): Float {
        val e = l.extent ?: return 20f
        return l.size * sqrt(max(e[0] * e[0], e[2] * e[2]) + max(e[1] * e[1], e[3] * e[3]))
    }

    // ------------------------------------------------------------------ ground

    private fun inBox(l: Landmark, bbox: FloatArray): Boolean {
        val r = radius(l) + 10f
        return l.x + r >= bbox[0] && l.x - r <= bbox[2] && l.y + r >= bbox[1] && l.y - r <= bbox[3]
    }

    /**
     * Squares, forecourts and approaches, in the ground pass before the roads: a square's rectangle can reach past the
     * curb, and the street, its curbs and markings then lie over it.
     */
    fun drawPaving(scene: Scene3D, bbox: FloatArray) {
        for (l in city.landmarks) {
            if (!inBox(l, bbox)) continue
            k = l.size
            val pave = scene.lit(c(0xFFCBC4B6), 1f); val edge = scene.lit(c(0xFFA69F92), 1f)
            scene.setFrame(l.x, l.y, l.cs, l.sn)
            scene.material = Mat.SIDEWALK
            try {
                when (l.kind) {
                    Landmark.TOWER -> if (l.style == Landmark.LATTICE) { rect(scene, -24f, -24f, 24f, 24f, .03f, edge); rect(scene, -23f, -23f, 23f, 23f, .035f, pave) }
                    Landmark.TORII -> { scene.material = Mat.PLAIN; rect(scene, -4.2f, -5f, 4.2f, 46f, .035f, scene.lit(c(0xFFD9D1BF), 1f)) }
                    Landmark.MONUMENT -> {
                        rect(scene, -12f, -12f, 12f, 12f, .03f, edge); rect(scene, -11.2f, -11.2f, 11.2f, 11.2f, .035f, pave)
                        scene.material = Mat.GRASS
                        for (sx in -1..1 step 2) for (sy in -1..1 step 2) rect(scene, sx * 10.5f, sy * 10.5f, sx * 7.5f, sy * 7.5f, .04f, scene.lit(c(0xFF6DA35C), 1f))
                    }
                    Landmark.CHURCH -> { rect(scene, -14.5f, -34f, 14.5f, 27f, .03f, edge); rect(scene, -13.8f, -33.3f, 13.8f, 26.3f, .035f, pave) }
                    Landmark.WHEEL -> { rect(scene, -33f, -11f, 33f, 11f, .03f, edge); rect(scene, -32f, -10f, 32f, 10f, .035f, pave) }
                    Landmark.PALACE_GATE -> if (l.style == Landmark.HANOK_GATE) rect(scene, -20f, -10f, 20f, -8.6f, .035f, pave)
                    Landmark.CITY_HALL -> {
                        rect(scene, -21f, -13f, 21f, 12f, .03f, edge); rect(scene, -20.3f, -12.3f, 20.3f, 11.3f, .035f, pave)
                        // lawns either side of the walk up to the tower door
                        scene.material = Mat.GRASS
                        rect(scene, -9f, -12.3f, 1f, -10f, .04f, scene.lit(c(0xFF6DA35C), 1f))
                        rect(scene, 4f, -12.3f, 16f, -10f, .04f, scene.lit(c(0xFF6DA35C), 1f))
                    }
                }
            } finally { scene.clearFrame() }
        }
        scene.material = Mat.PLAIN
    }

    /** The scramble crossings' diagonal zebras, over the road markings. */
    fun drawGround(scene: Scene3D, bbox: FloatArray) {
        for ((i, l) in city.landmarks.withIndex()) {
            if (l.kind == Landmark.BILLBOARDS && scrambleNode[i] >= 0 && inBox(l, bbox)) scramble(scene, city.roads.nodes[scrambleNode[i]])
        }
        scene.material = Mat.PLAIN
    }

    private fun rect(scene: Scene3D, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Int) =
        scene.quad(x0 * k, y0 * k, z, x1 * k, y0 * k, z, x1 * k, y1 * k, z, x0 * k, y1 * k, z, color)

    /**
     * The diagonal zebra crossings of a scramble crossing: two bands from corner to corner across the junction, picked
     * to run as straight through the middle as its roads allow. The second band stops at the first, so the centre
     * reads as one crossing over the other instead of a grid.
     */
    private fun scramble(scene: Scene3D, n: com.hustlecity.game.world.RoadNode) {
        val m = scrambleCorners(n)
        if (m < 4) return
        // the pair of diagonals that cross nearest the middle and most squarely
        var best = Float.MAX_VALUE; var bi = -1; var bj = -1; var bk = -1; var bl = -1
        for (i in 0 until m) for (j in i + 2 until m) {
            if (i == 0 && j == m - 1) continue
            for (k in i + 1 until j) for (l in j + 1 until m + i) {
                val ll = l % m
                if (ll == i) continue
                val d1 = straightness(n, i, j); val d2 = straightness(n, k, ll)
                val ax = cx[j] - cx[i]; val ay = cy[j] - cy[i]; val bx = cx[ll] - cx[k]; val by = cy[ll] - cy[k]
                val cosA = abs(ax * bx + ay * by) / (kotlin.math.hypot(ax, ay) * kotlin.math.hypot(bx, by)).coerceAtLeast(1e-3f)
                val score = d1 + d2 + cosA * 1.2f
                if (score < best) { best = score; bi = i; bj = j; bk = k; bl = ll }
            }
        }
        if (bi < 0) return
        val white = scene.lit(WorldRenderer.WHITE_LINE, .95f)
        band(scene, n, cx[bi], cy[bi], cx[bj], cy[bj], white, Float.NaN, 0f, 0f, 0f)
        band(scene, n, cx[bk], cy[bk], cx[bl], cy[bl], white, cx[bi], cy[bi], cx[bj], cy[bj])
    }

    /** True when (x, y) falls on (or right by) the zebra crossing over one of the roads meeting at [n]. */
    private fun onCrossing(n: com.hustlecity.game.world.RoadNode, x: Float, y: Float): Boolean {
        val g = city.roads
        for (id in n.edges) {
            val e = g.edges[id]
            if (e.under) continue
            val clear = if (e.a == n.id) e.clearA else e.clearB
            val dx = if (e.a == n.id) e.ux else -e.ux; val dy = if (e.a == n.id) e.uy else -e.uy
            val along = (x - n.x) * dx + (y - n.y) * dy
            val side = abs(-(x - n.x) * dy + (y - n.y) * dx)
            if (along in clear - .6f..clear + 4.2f && side < e.cls.halfWidth + 1.2f) return true
        }
        return false
    }

    private val cx = FloatArray(16); private val cy = FloatArray(16)
    private val ca = FloatArray(16); private val co = IntArray(16)

    /**
     * The kerb corners of a junction where people step off, in order round it: between each road and the next, just
     * out on the asphalt. Returns how many.
     */
    private fun scrambleCorners(n: com.hustlecity.game.world.RoadNode): Int {
        val g = city.roads
        var m = 0
        for (id in n.edges) { val e = g.edges[id]; if (!e.under && m < co.size) { co[m] = id; ca[m] = g.angleAway(e, n.id); m++ } }
        for (i in 1 until m) { val v = co[i]; val a = ca[i]; var j = i - 1; while (j >= 0 && ca[j] > a) { co[j + 1] = co[j]; ca[j + 1] = ca[j]; j-- }; co[j + 1] = v; ca[j + 1] = a }
        for (q in 0 until m) {
            val e0 = g.edges[co[q]]; val e1 = g.edges[co[(q + 1) % m]]
            // the "after" mouth corner of this road and the "before" corner of the next: the kerb runs between them
            val (x0, y0) = mouthCorner(n, e0, -1f)
            val (x1, y1) = mouthCorner(n, e1, 1f)
            var px = (x0 + x1) * .5f; var py = (y0 + y1) * .5f
            val dx = n.x - px; val dy = n.y - py; val d = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-3f)
            px += dx / d * 1.6f; py += dy / d * 1.6f
            cx[q] = px; cy[q] = py
        }
        return m
    }

    private fun mouthCorner(n: com.hustlecity.game.world.RoadNode, e: com.hustlecity.game.world.RoadEdge, side: Float): Pair<Float, Float> {
        val clear = if (e.a == n.id) e.clearA else e.clearB
        val dx = if (e.a == n.id) e.ux else -e.ux; val dy = if (e.a == n.id) e.uy else -e.uy
        val hw = e.cls.halfWidth
        return (n.x + dx * clear + dy * hw * side) to (n.y + dy * clear - dx * hw * side)
    }

    /** How far a walk from corner [i] to corner [j] misses the middle of the junction (meters, scaled). */
    private fun straightness(n: com.hustlecity.game.world.RoadNode, i: Int, j: Int): Float {
        val ax = cx[i]; val ay = cy[i]; val bx = cx[j]; val by = cy[j]
        val l = kotlin.math.hypot(bx - ax, by - ay).coerceAtLeast(1e-3f)
        val miss = abs((bx - ax) * (ay - n.y) - (by - ay) * (ax - n.x)) / l
        return miss / 4f
    }

    /**
     * A zebra band from (x0, y0) to (x1, y1): bars across it one after another along the way people walk. Bars that
     * would land on the band from (ox0, oy0) to (ox1, oy1), or on the ordinary crossings over each road at [n], are
     * left out.
     */
    private fun band(scene: Scene3D, n: com.hustlecity.game.world.RoadNode, x0: Float, y0: Float, x1: Float, y1: Float, white: Int,
                     ox0: Float, oy0: Float, ox1: Float, oy1: Float) {
        val len = kotlin.math.hypot(x1 - x0, y1 - y0)
        if (len < 4f) return
        val ux = (x1 - x0) / len; val uy = (y1 - y0) / len
        val px = -uy * 2.2f; val py = ux * 2.2f
        var t = .3f
        while (t < len - .9f) {
            val bx = x0 + ux * (t + .3f); val by = y0 + uy * (t + .3f)
            if ((ox0.isNaN() || Geo.distToSeg(bx, by, ox0, oy0, ox1, oy1) > 2.2f + 1.4f) && !onCrossing(n, bx, by)) {
                val ax = x0 + ux * t; val ay = y0 + uy * t
                val cx1 = ax + ux * .6f; val cy1 = ay + uy * .6f
                scene.quad(ax - px, ay - py, .045f, cx1 - px, cy1 - py, .045f, cx1 + px, cy1 + py, .045f, ax + px, ay + py, .045f, white)
            }
            t += 1.25f
        }
    }

    // ------------------------------------------------------------------ Tokyo Tower

    /** Half width of the lattice tower at height [z] (before scaling). */
    private fun tw(z: Float): Float = 1.2f + 18.8f * (1f - (z / 150f).coerceIn(0f, 1f)).pow(2.2f)

    private val levels = floatArrayOf(0f, 20f, 34f, 46f, 58f, 66f, 78f, 90f, 100f, 104.5f, 116f)
    private val fd = FloatArray(4); private val fo = IntArray(4)

    private fun lattice(scene: Scene3D, d: Float, mapMode: Boolean) {
        val night = scene.daylight < .55f && !mapMode
        val detail = d < 700f
        scene.material = Mat.PAINT
        // faces seen from inside (the far side, through the lattice) first, farthest first
        for (f in 0 until 4) { fd[f] = faceDist(scene, f); fo[f] = f }
        for (a in 1 until 4) { val v = fo[a]; var b = a - 1; while (b >= 0 && fd[fo[b]] < fd[v]) { fo[b + 1] = fo[b]; b-- }; fo[b + 1] = v }
        for (f in fo) if (fd[f] >= 0f) latticeFace(scene, f, night, detail, far = true)
        // the shop building under the legs
        scene.material = Mat.CONCRETE
        bx(scene, -14f, -14f, 0f, 14f, 14f, 13f, c(0xFFD8D8D4), c(0xFFE4E2DC))
        scene.material = Mat.PLAIN
        for (f in 0 until 4) fasciaBand(scene, f, 14f, 3.5f, 12f, if (night) WorldRenderer.LIT else DARK_GLASS)
        // the faces seen from outside, the decks and the mast, bottom to top
        scene.material = Mat.PAINT
        for (f in fo) if (fd[f] < 0f) latticeFace(scene, f, night, detail, far = false, below = 58f)
        deck(scene, 58f, 66f, 10f, night)
        for (f in fo) if (fd[f] < 0f) latticeFace(scene, f, night, detail, far = false, above = 66f, below = 100f)
        deck(scene, 100f, 104.5f, 4.2f, night)
        for (f in fo) if (fd[f] < 0f) latticeFace(scene, f, night, detail, far = false, above = 104.5f)
        // the antenna: red and white bands, a warning light on top
        var z = 116f; var r = 1.1f; var band = 0
        while (z < 150f) {
            val z1 = min(150f, z + 5f)
            val cc = if (band % 2 == 0) ORANGE else WHITE
            if (night) glowBox(scene, -r, -r, z, r, r, z1, if (band % 2 == 0) GLOW_ORANGE else GLOW_WHITE) else bx(scene, -r, -r, z, r, r, z1, cc, cc)
            r = max(.3f, r - .12f); z = z1; band++
        }
        if (night || (scene.gpu == null && d < 400f)) glowBox(scene, -.5f, -.5f, 150f, .5f, .5f, 151f, c(0xFFFF4D4D))
        scene.material = Mat.PLAIN
    }

    /** Distance of face [f]'s plane from the camera along its normal: negative when the camera sees its outside. */
    private fun faceDist(scene: Scene3D, f: Int): Float {
        val w = tw(40f) * k
        return when (f) { 0 -> scene.camLY + w; 1 -> w - scene.camLY; 2 -> scene.camLX + w; else -> w - scene.camLX }
    }

    /** Point on lattice face [f] at fraction [u] (-1..1) across and height [z], before scaling. */
    private fun fx(f: Int, u: Float, z: Float): Float { val w = tw(z); return when (f) { 0, 1 -> u * w; 2 -> -w; else -> w } }
    private fun fy(f: Int, u: Float, z: Float): Float { val w = tw(z); return when (f) { 0 -> -w; 1 -> w; else -> u * w } }

    /** A strip on face [f] from (u0, z0) to (u1, z1), [t] meters wide across the strip. */
    private fun strip(scene: Scene3D, f: Int, u0: Float, z0: Float, u1: Float, z1: Float, t: Float, color: Int, horizontal: Boolean) {
        if (horizontal) {
            q(scene, fx(f, u0, z0 - t * .5f), fy(f, u0, z0 - t * .5f), z0 - t * .5f, fx(f, u1, z1 - t * .5f), fy(f, u1, z1 - t * .5f), z1 - t * .5f,
                fx(f, u1, z1 + t * .5f), fy(f, u1, z1 + t * .5f), z1 + t * .5f, fx(f, u0, z0 + t * .5f), fy(f, u0, z0 + t * .5f), z0 + t * .5f, color)
        } else {
            val du0 = t * .5f / tw(z0); val du1 = t * .5f / tw(z1)
            q(scene, fx(f, u0 - du0, z0), fy(f, u0 - du0, z0), z0, fx(f, u1 - du1, z1), fy(f, u1 - du1, z1), z1,
                fx(f, u1 + du1, z1), fy(f, u1 + du1, z1), z1, fx(f, u0 + du0, z0), fy(f, u0 + du0, z0), z0, color)
        }
    }

    private fun latticeFace(scene: Scene3D, f: Int, night: Boolean, detail: Boolean, far: Boolean, above: Float = -1f, below: Float = 999f) {
        val nx = when (f) { 2 -> -1f; 3 -> 1f; else -> 0f }; val ny = when (f) { 0 -> -1f; 1 -> 1f; else -> 0f }
        val shade = scene.sideShade(nx, ny) * (if (far) .82f else 1f)
        if (night) scene.glow = true
        for (li in 0 until levels.size - 1) {
            val z0 = levels[li]; val z1 = levels[li + 1]
            if (z0 < above - .01f || z1 > below + .01f) continue
            if (z0 >= 58f && z1 <= 66f) continue
            if (z0 >= 100f && z1 <= 104.5f) continue
            val base = if (li % 2 == 0) ORANGE else WHITE
            val col = if (night) (if (li % 2 == 0) GLOW_ORANGE else GLOW_WHITE) else scene.lit(base, shade)
            val leg = if (z1 <= 46f) 2.6f else 1.3f
            // corner legs
            strip(scene, f, -1f + leg * .5f / tw(z0), z0, -1f + leg * .5f / tw(z1), z1, leg, col, false)
            strip(scene, f, 1f - leg * .5f / tw(z0), z0, 1f - leg * .5f / tw(z1), z1, leg, col, false)
            // a girt at the top of each section and X bracing inside it
            strip(scene, f, -1f, z1, 1f, z1, if (z1 <= 46f) 1.1f else .7f, col, true)
            if (li == 0) {
                // the great arch between the legs at the foot
                val n = 8
                for (s in 0 until n) {
                    val ua = -1f + 2f * s / n; val ub = -1f + 2f * (s + 1) / n
                    val za = 14f + 6f * sqrt(max(0f, 1f - ua * ua)); val zb = 14f + 6f * sqrt(max(0f, 1f - ub * ub))
                    strip(scene, f, ua, za, ub, zb, 1.3f, col, true)
                }
            } else if (detail || z0 >= 66f) {
                strip(scene, f, -1f, z0, 1f, z1, .55f, col, false)
                strip(scene, f, 1f, z0, -1f, z1, .55f, col, false)
            }
        }
        scene.glow = false
    }

    /** An observation deck ringing the tower: a glass band between white floors. */
    private fun deck(scene: Scene3D, z0: Float, z1: Float, r: Float, night: Boolean) {
        scene.material = Mat.PLAIN
        val h = z1 - z0
        bx(scene, -r, -r, z0, r, r, z0 + h * .22f, WHITE, WHITE)
        if (night) glowBox(scene, -r + .1f, -r + .1f, z0 + h * .22f, r - .1f, r - .1f, z1 - h * .2f, WorldRenderer.LIT)
        else { scene.material = Mat.GLASS; bx(scene, -r + .1f, -r + .1f, z0 + h * .22f, r - .1f, r - .1f, z1 - h * .2f, DARK_GLASS, c(0xFF3C5266)); scene.material = Mat.PLAIN }
        bx(scene, -r - .2f, -r - .2f, z1 - h * .2f, r + .2f, r + .2f, z1, c(0xFFDADAD6), WHITE)
        scene.material = Mat.PAINT
    }

    /** A band on all four sides of the box under the tower (windows). */
    private fun fasciaBand(scene: Scene3D, f: Int, r: Float, z0: Float, z1: Float, color: Int) {
        val o = r + .05f
        when (f) {
            0 -> if (scene.gpu != null || scene.camLY < -o * k) q(scene, -r + 1f, -o, z0, r - 1f, -o, z0, r - 1f, -o, z1, -r + 1f, -o, z1, color)
            1 -> if (scene.gpu != null || scene.camLY > o * k) q(scene, -r + 1f, o, z0, r - 1f, o, z0, r - 1f, o, z1, -r + 1f, o, z1, color)
            2 -> if (scene.gpu != null || scene.camLX < -o * k) q(scene, -o, -r + 1f, z0, -o, r - 1f, z0, -o, r - 1f, z1, -o, -r + 1f, z1, color)
            else -> if (scene.gpu != null || scene.camLX > o * k) q(scene, o, -r + 1f, z0, o, r - 1f, z0, o, r - 1f, z1, o, -r + 1f, z1, color)
        }
    }

    // ------------------------------------------------------------------ N Seoul Tower

    private fun concreteTower(scene: Scene3D, l: Landmark, d: Float) {
        val night = scene.daylight < .55f
        val hillH = 18f
        // trees on the slopes: the ones behind the hill go first so the hill hides them
        val trees = 14
        val camA = atan2(scene.camLY, scene.camLX)
        for (pass in 0..1) {
            if (pass == 1) {
                // the hill: a wooded dome in rings
                scene.material = Mat.GRASS
                val rings = floatArrayOf(33f, 27f, 19f, 11f, 0f)
                val hs = floatArrayOf(0f, 7f, 13f, 16.5f, hillH)
                for (r in 0 until 4) frustum(scene, 0f, 0f, rings[r], hs[r], max(rings[r + 1], 10f), hs[r + 1], 14, scene.mix(HILL, FOREST[r % 3], .4f))
                frustum(scene, 0f, 0f, 10f, 16.5f, 10f, hillH, 14, HILL, cap = c(0xFFBDB6A8))
                scene.material = Mat.PLAIN
            }
            if (d < 500f) for (t in 0 until trees) {
                val a = t * 2.4f + .3f
                val behind = abs(wrapAngle(a - camA)) > PI.toFloat() / 2
                if ((pass == 0) != behind) continue
                val rr = 14f + (t * 37 % 15)
                val zz = hillH * (1f - (rr / 33f).pow(2f)) .coerceAtLeast(0f)
                scene.material = Mat.FOLIAGE
                scene.blob(cos(a) * rr * k, sin(a) * rr * k, (zz + 2.6f) * k, (3f + (t % 3)) * k, scene.lit(FOREST[t % 3], .95f), 9)
                scene.material = Mat.PLAIN
            }
        }
        // the tower: a base pavilion, the white shaft, the observation pods and the mast
        scene.material = Mat.CONCRETE
        bx(scene, -6f, -6f, hillH, 6f, 6f, hillH + 6f, c(0xFFDCDCD6), c(0xFFE8E6E0))
        frustum(scene, 0f, 0f, 3.4f, hillH + 6f, 2.3f, hillH + 68f, 10, CONCRETE, PI.toFloat() / 10)
        val pz = hillH + 68f
        frustum(scene, 0f, 0f, 2.3f, pz, 6.6f, pz + 3f, 12, CONCRETE)
        if (night) { scene.glow = true; frustum(scene, 0f, 0f, 6.8f, pz + 3f, 7.2f, pz + 8f, 12, c(0xFF5FE3FF)); scene.glow = false }
        else { scene.material = Mat.GLASS; frustum(scene, 0f, 0f, 6.8f, pz + 3f, 7.2f, pz + 8f, 12, c(0xFF3E6E8E)); scene.material = Mat.CONCRETE }
        frustum(scene, 0f, 0f, 7.2f, pz + 8f, 6.4f, pz + 10f, 12, CONCRETE)
        frustum(scene, 0f, 0f, 6.4f, pz + 10f, 5f, pz + 13f, 12, c(0xFFD7D7D1), cap = c(0xFFCFCFC9))
        scene.material = Mat.PLAIN
        var z = pz + 13f; var r = 1.4f; var band = 0
        while (z < pz + 52f) {
            val z1 = z + 5.5f
            val col = if (band % 2 == 0) c(0xFFE04B3A) else WHITE
            if (night) glowBox(scene, -r, -r, z, r, r, z1, if (band % 2 == 0) c(0xFFFF4D4D) else GLOW_WHITE) else bx(scene, -r, -r, z, r, r, z1, col, col)
            r = max(.35f, r - .15f); z = z1; band++
        }
        if (night) glowBox(scene, -.45f, -.45f, z, .45f, .45f, z + .8f, c(0xFFFF4D4D))
    }

    private fun wrapAngle(a: Float): Float { var x = a; while (x > PI) x -= 2 * PI.toFloat(); while (x < -PI) x += 2 * PI.toFloat(); return x }

    // ------------------------------------------------------------------ torii

    private fun torii(scene: Scene3D, y: Float, s: Float, d: Float) {
        val red = VERMILION
        val px = 5f * s; val h = 10.4f * s
        for (sx in -1..1 step 2) {
            scene.cylinder(sx * px * k, y * k, .62f * s * k, .8f * s * k, h * k, 8, red)
            scene.cylinder(sx * px * k, y * k, .78f * s * k, 0f, .8f * s * k, 8, BLACK)
        }
        // nuki: the tie beam through the pillars
        bx(scene, -7.1f * s, y - .32f * s, 8.1f * s, 7.1f * s, y + .32f * s, 8.85f * s, red, red)
        // gakuzuka and the plaque
        bx(scene, -.32f * s, y - .25f * s, 8.85f * s, .32f * s, y + .25f * s, 10.1f * s, red, red)
        if (d < 200f) bx(scene, -.85f * s, y - .36f * s, 9f * s, .85f * s, y + .36f * s, 9.95f * s, BLACK, BLACK)
        // shimaki and kasagi: the top beams, black-capped, rising toward their ends
        bx(scene, -7.4f * s, y - .42f * s, 10.1f * s, 7.4f * s, y + .42f * s, 10.6f * s, red, red)
        val ends = floatArrayOf(-8.6f, -5.6f, 5.6f, 8.6f)
        for (seg in 0..2) {
            val xa = ends[seg] * s; val xb = ends[seg + 1] * s
            val za = 10.6f * s + kasagiLift(xa, s); val zb = 10.6f * s + kasagiLift(xb, s)
            for (part in 0..1) {
                val z0a = za + (if (part == 0) 0f else .55f * s); val z0b = zb + (if (part == 0) 0f else .55f * s)
                val t = if (part == 0) .55f * s else .35f * s
                val col = if (part == 0) red else BLACK
                val y0 = y - .62f * s; val y1 = y + .62f * s
                val sh0 = scene.sideShade(0f, -1f); val sh1 = scene.sideShade(0f, 1f)
                if (scene.gpu != null || scene.camLY < y0 * k) q(scene, xa, y0, z0a, xb, y0, z0b, xb, y0, z0b + t, xa, y0, z0a + t, scene.lit(col, sh0))
                if (scene.gpu != null || scene.camLY > y1 * k) q(scene, xa, y1, z0a, xb, y1, z0b, xb, y1, z0b + t, xa, y1, z0a + t, scene.lit(col, sh1))
                q(scene, xa, y0, z0a + t, xb, y0, z0b + t, xb, y1, z0b + t, xa, y1, z0a + t, scene.lit(scene.snowy(col), 1f))
                if (seg != 1) {
                    val xe = if (seg == 0) xa else xb; val ze = if (seg == 0) z0a else z0b
                    q(scene, xe, y0, ze, xe, y1, ze, xe, y1, ze + t, xe, y0, ze + t, scene.lit(col, .8f))
                }
            }
        }
    }

    /** How far the top beam sweeps up toward its tips. */
    private fun kasagiLift(x: Float, s: Float) = if (abs(x) <= 5.6f * s) 0f else (abs(x) - 5.6f * s) * .17f

    /** Stone lanterns either side of the approach, lit at night. */
    private fun lanterns(scene: Scene3D, d: Float) {
        if (d > 260f) return
        val night = scene.daylight < .55f
        val stone = c(0xFFB5AFA3)
        for (yi in 0..1) for (sx in -1..1 step 2) { val y = if (yi == 0) -3.5f else 14f
            val x = sx * 6.2f
            bx(scene, x - .55f, y - .55f, 0f, x + .55f, y + .55f, .4f, stone, stone)
            scene.cylinder(x * k, y * k, .18f * k, .4f * k, 1.5f * k, 6, stone)
            bx(scene, x - .45f, y - .45f, 1.5f, x + .45f, y + .45f, 1.65f, stone, stone)
            if (night) glowBox(scene, x - .3f, y - .3f, 1.65f, x + .3f, y + .3f, 2.25f, c(0xFFFFE3A0)) else bx(scene, x - .3f, y - .3f, 1.65f, x + .3f, y + .3f, 2.25f, stone, c(0xFF8E887C))
            scene.cone(x * k, y * k, .75f * k, 2.25f * k, 2.85f * k, 4, stone)
        }
    }

    // ------------------------------------------------------------------ monument

    private fun monument(scene: Scene3D, d: Float) {
        val night = scene.daylight < .55f
        scene.material = Mat.STONE
        bx(scene, -6.5f, -6.5f, 0f, 6.5f, 6.5f, .4f, GRANITE, GRANITE)
        bx(scene, -5.5f, -5.5f, .4f, 5.5f, 5.5f, .8f, GRANITE, GRANITE)
        bx(scene, -4.6f, -4.6f, .8f, 4.6f, 4.6f, 1.2f, GRANITE, GRANITE)
        bx(scene, -3f, -3f, 1.2f, 3f, 3f, 5.2f, c(0xFFD6D0C4), c(0xFFC5BEB1))
        bx(scene, -3.3f, -3.3f, 5.2f, 3.3f, 3.3f, 5.7f, GRANITE, GRANITE)
        // the obelisk, floodlit at night
        val shaft = if (night) GLOW_WHITE else c(0xFFDAD4C8)
        if (night) scene.glow = true
        frustum(scene, 0f, 0f, 2.5f, 5.7f, 1.45f, 17.5f, 4, shaft, PI.toFloat() / 4)
        frustum(scene, 0f, 0f, 1.45f, 17.5f, .05f, 19.4f, 4, shaft, PI.toFloat() / 4)
        scene.glow = false
        scene.material = Mat.PLAIN
        // a bronze figure in front of the pedestal
        if (d < 380f) {
            val y = -4.1f
            bx(scene, -.9f, y - .7f, 1.2f, .9f, y + .7f, 1.6f, GRANITE, GRANITE)
            bx(scene, -.42f, y - .25f, 1.6f, -.08f, y + .25f, 2.9f, BRONZE, BRONZE)
            bx(scene, .08f, y - .25f, 1.6f, .42f, y + .25f, 2.9f, BRONZE, BRONZE)
            frustum(scene, 0f, y, .75f, 2.6f, .55f, 4.2f, 6, BRONZE)
            scene.blob(0f, y * k, 4.55f * k, .32f * k, scene.lit(BRONZE, 1.05f), 8)
        }
        // honour guards in white standing either side of the statue
        if (city.mapId == Maps.MANILA && d < 260f) for (sx in -1..1 step 2) guard(scene, sx * 2.6f, -4.3f, 1.2f)
        // a tall flagpole flying the country's flag
        val fxp = 8.5f; val fyp = -8.5f; val top = 24.5f
        bx(scene, fxp - .5f, fyp - .5f, 0f, fxp + .5f, fyp + .5f, .7f, GRANITE, GRANITE)
        bx(scene, fxp - .12f, fyp - .12f, .7f, fxp + .12f, fyp + .12f, top, c(0xFFD5D8DC), c(0xFFBFC5CB))
        scene.blob(fxp * k, fyp * k, (top + .2f) * k, .22f * k, scene.lit(c(0xFFD9B44A), 1f), 8)
        if (city.mapId == Maps.NYC) usFlag(scene, fxp + .12f, fyp, top - .3f, 5.6f)
        else philippineFlag(scene, fxp + .12f, fyp, top - .3f, 6.4f, d < 420f)
    }

    /** A guard of honour at attention: navy trousers, white tunic and cap, a rifle at the shoulder. */
    private fun guard(scene: Scene3D, x: Float, y: Float, z: Float) {
        val navy = c(0xFF1B2A4A); val white = c(0xFFF2F2EE)
        for (sx in -1..1 step 2) bx(scene, x + sx * .11f - .08f, y - .09f, z, x + sx * .11f + .08f, y + .09f, z + .9f, navy, navy)
        bx(scene, x - .25f, y - .14f, z + .9f, x + .25f, y + .14f, z + 1.52f, white, white)
        bx(scene, x - .26f, y - .15f, z + .9f, x + .26f, y + .15f, z + .98f, c(0xFFD9B44A), c(0xFFD9B44A))
        for (sx in -1..1 step 2) bx(scene, x + sx * .31f - .06f, y - .07f, z + .95f, x + sx * .31f + .06f, y + .07f, z + 1.5f, white, white)
        scene.blob(x * k, y * k, (z + 1.66f) * k, .13f * k, scene.lit(c(0xFF8D5A3B), 1f), 8)
        bx(scene, x - .15f, y - .15f, z + 1.75f, x + .15f, y + .15f, z + 1.84f, white, white)
        bx(scene, x - .1f, y - .21f, z + 1.74f, x + .1f, y - .15f, z + 1.77f, BLACK, BLACK)
        bx(scene, x + .34f, y - .05f, z + .75f, x + .38f, y + .05f, z + 1.95f, c(0xFF3A3A3A), c(0xFF3A3A3A))
    }

    // ------------------------------------------------------------------ flags

    private var flagX = 0f; private var flagY = 0f; private var flagZ = 0f; private var flagL = 1f
    private val fu = FloatArray(12); private val fv = FloatArray(12)
    private fun flagPx(u: Float) = flagX + u
    private fun flagPy(u: Float, off: Float) = flagY + .3f * (u / flagL) * sin(u / flagL * 8f) + off
    private fun flagPz(u: Float, v: Float) = flagZ - v - .25f * (u / flagL) * (u / flagL)

    /** A rectangle of cloth [u0]..[u1] out from the hoist and [v0]..[v1] down from the top, rippling in the wind. */
    private fun cloth(scene: Scene3D, u0: Float, v0: Float, u1: Float, v1: Float, color: Int, off: Float = 0f) {
        val n = max(1, ((u1 - u0) / flagL * 10f).toInt())
        for (i in 0 until n) {
            val a = u0 + (u1 - u0) * i / n; val b = u0 + (u1 - u0) * (i + 1) / n
            q(scene, flagPx(a), flagPy(a, off), flagPz(a, v0), flagPx(b), flagPy(b, off), flagPz(b, v0),
                flagPx(b), flagPy(b, off), flagPz(b, v1), flagPx(a), flagPy(a, off), flagPz(a, v1), color)
        }
    }

    /** A small shape painted on the cloth: [n] points ([fu], [fv]) lifted [off] off it. */
    private fun clothPoly(scene: Scene3D, n: Int, color: Int, off: Float) {
        for (i in 0 until n) { tx[i] = flagPx(fu[i]) * k; ty[i] = flagPy(fu[i], off) * k; tz[i] = flagPz(fu[i], fv[i]) * k }
        scene.poly(n, tx, ty, tz, color)
    }

    /** Painted marks go on both faces of the cloth on the GPU, the face toward the camera on the Canvas. */
    private fun clothSide(scene: Scene3D, s: Int) = scene.gpu != null || (scene.camLY - flagY * k) * s >= 0f

    /** Blue over red, the white triangle at the hoist with the golden sun and three stars. */
    private fun philippineFlag(scene: Scene3D, x: Float, y: Float, z: Float, len: Float, near: Boolean) {
        flagX = x; flagY = y; flagZ = z; flagL = len
        val h = len / 2f
        cloth(scene, 0f, 0f, len, h / 2f, scene.lit(c(0xFF0038A8), 1f))
        cloth(scene, 0f, h / 2f, len, h, scene.lit(c(0xFFCE1126), 1f))
        val a = h * .866f
        val gold = scene.lit(c(0xFFFCD116), 1f)
        for (s in -1..1 step 2) {
            if (!clothSide(scene, s)) continue
            // the triangle in strips so it follows the ripple
            for (i in 0 until 4) {
                val u0 = a * i / 4; val u1 = a * (i + 1) / 4
                fu[0] = u0; fv[0] = u0 / a * h / 2f; fu[1] = u1; fv[1] = u1 / a * h / 2f
                fu[2] = u1; fv[2] = h - u1 / a * h / 2f; fu[3] = u0; fv[3] = h - u0 / a * h / 2f
                clothPoly(scene, 4, scene.lit(c(0xFFFFFFFF), 1f), s * .03f)
            }
            if (!near) continue
            val cu = a / 3f; val cv = h / 2f; val r = h * .1f
            for (i in 0 until 10) { val t = i * 2f * PI.toFloat() / 10; fu[i] = cu + cos(t) * r; fv[i] = cv + sin(t) * r }
            clothPoly(scene, 10, gold, s * .05f)
            for (i in 0 until 8) {
                val t = i * PI.toFloat() / 4
                fu[0] = cu + cos(t - .2f) * r * .9f; fv[0] = cv + sin(t - .2f) * r * .9f
                fu[1] = cu + cos(t) * r * 2.15f; fv[1] = cv + sin(t) * r * 2.15f
                fu[2] = cu + cos(t + .2f) * r * .9f; fv[2] = cv + sin(t + .2f) * r * .9f
                clothPoly(scene, 3, gold, s * .05f)
            }
            star(scene, h * .1f, h * .12f, h * .06f, gold, s * .05f)
            star(scene, h * .1f, h * .88f, h * .06f, gold, s * .05f)
            star(scene, a - h * .13f, h / 2f, h * .06f, gold, s * .05f)
        }
    }

    /** A five-pointed star on the cloth: a point at a time, then the pentagon between them. */
    private fun star(scene: Scene3D, cu: Float, cv: Float, r: Float, color: Int, off: Float) {
        val tip = -PI.toFloat() / 2
        for (i in 0 until 5) {
            val t = tip + i * 2f * PI.toFloat() / 5
            fu[0] = cu + cos(t) * r; fv[0] = cv + sin(t) * r
            fu[1] = cu + cos(t + .63f) * r * .4f; fv[1] = cv + sin(t + .63f) * r * .4f
            fu[2] = cu + cos(t - .63f) * r * .4f; fv[2] = cv + sin(t - .63f) * r * .4f
            clothPoly(scene, 3, color, off)
        }
        for (i in 0 until 5) { val t = tip + .63f + i * 2f * PI.toFloat() / 5; fu[i] = cu + cos(t) * r * .4f; fv[i] = cv + sin(t) * r * .4f }
        clothPoly(scene, 5, color, off)
    }

    /** Stripes with the blue canton. */
    private fun usFlag(scene: Scene3D, x: Float, y: Float, z: Float, len: Float) {
        flagX = x; flagY = y; flagZ = z; flagL = len
        val h = len * .53f
        for (st in 0 until 7) cloth(scene, 0f, st * h / 7, len, (st + 1) * h / 7, scene.lit(if (st % 2 == 0) c(0xFFB22234) else c(0xFFFFFFFF), 1f))
        for (s in -1..1 step 2) if (clothSide(scene, s)) cloth(scene, 0f, 0f, len * .4f, h * 4 / 7, scene.lit(c(0xFF3C3B6E), 1f), s * .03f)
    }

    // ------------------------------------------------------------------ palace gates

    /** A hip roof with upturned corners: eaves rectangle at [ze], rising to a ridge [rx] long at [zr]. */
    private fun hipRoof(scene: Scene3D, ex: Float, ey: Float, ze: Float, rx: Float, zr: Float, color: Int, curl: Float, ox: Float = 0f) {
        val top = scene.snowy(color)
        // long slopes (front and back), then the hipped ends
        q(scene, ox - ex, -ey, ze + curl, ox + ex, -ey, ze + curl, ox + rx, 0f, zr, ox - rx, 0f, zr, scene.lit(top, .9f))
        q(scene, ox + ex, ey, ze + curl, ox - ex, ey, ze + curl, ox - rx, 0f, zr, ox + rx, 0f, zr, scene.lit(top, 1.02f))
        tri(scene, ox - ex, ey, ze + curl, ox - ex, -ey, ze + curl, ox - rx, 0f, zr, scene.lit(top, .95f))
        tri(scene, ox + ex, -ey, ze + curl, ox + ex, ey, ze + curl, ox + rx, 0f, zr, scene.lit(top, .97f))
        // the eaves dip toward the middle of each side and sweep up at the corners
        if (curl > 0f) {
            q(scene, ox - ex * .55f, -ey, ze, ox + ex * .55f, -ey, ze, ox + ex, -ey, ze + curl, ox - ex, -ey, ze + curl, scene.lit(color, .7f))
            q(scene, ox - ex * .55f, ey, ze, ox + ex * .55f, ey, ze, ox + ex, ey, ze + curl, ox - ex, ey, ze + curl, scene.lit(color, .75f))
        }
    }

    private fun tri(scene: Scene3D, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, color: Int) {
        tx[0] = x0 * k; ty[0] = y0 * k; tz[0] = z0 * k
        tx[1] = x1 * k; ty[1] = y1 * k; tz[1] = z1 * k
        tx[2] = x2 * k; ty[2] = y2 * k; tz[2] = z2 * k
        scene.poly(3, tx, ty, tz, color)
    }

    /** Gwanghwamun: a granite platform pierced by three arches, a two-storey painted pavilion, walls either side. */
    private fun hanokGate(scene: Scene3D, d: Float) {
        val night = scene.daylight < .55f
        scene.material = Mat.STONE
        // walls running off along the palace's edge
        for (sx in -1..1 step 2) {
            bx(scene, sx * 17f, -1f, 0f, sx * 46f, 1f, 1.4f, GRANITE, GRANITE)
            scene.material = Mat.PLASTER
            bx(scene, sx * 17f, -.85f, 1.4f, sx * 46f, .85f, 4f, c(0xFFB0574A), c(0xFF9E4B3F))
            scene.material = Mat.STONE
            val x0 = sx * 17f; val x1 = sx * 46f
            q(scene, x0, -1.5f, 4f, x1, -1.5f, 4f, x1, 0f, 4.9f, x0, 0f, 4.9f, scene.lit(scene.snowy(TILE_ROOF), .9f))
            q(scene, x1, 1.5f, 4f, x0, 1.5f, 4f, x0, 0f, 4.9f, x1, 0f, 4.9f, scene.lit(scene.snowy(TILE_ROOF), 1f))
        }
        bx(scene, -17f, -8.5f, 0f, 17f, 8.5f, 7.5f, scene.snowy(c(0xFFC9C2B2)), GRANITE)
        // three arches on each face
        val dark = scene.lit(c(0xFF2A2622), 1f)
        for (fs in -1..1 step 2) { val fy = fs * 8.53f
            if (scene.gpu == null && ((fy < 0 && scene.camLY > fy * k) || (fy > 0 && scene.camLY < fy * k))) continue
            for (a in -1..1) archFace(scene, a * 8f, fy, if (a == 0) 2.6f else 1.9f, if (a == 0) 5.4f else 4.4f, dark)
        }
        // the pavilion: red columns and walls under bright painted beams, two tiers of tiled roof
        scene.material = Mat.WOOD
        bx(scene, -12f, -4.6f, 7.5f, 12f, 4.6f, 10.3f, RED_WOOD, RED_WOOD)
        scene.material = Mat.PAINT
        bx(scene, -12.2f, -4.8f, 10.3f, 12.2f, 4.8f, 11.2f, DANCHEONG, DANCHEONG)
        if (night) glowBox(scene, -11.8f, -4.65f, 8f, 11.8f, 4.65f, 9.8f, c(0xFFFFD98A))
        // a broad lower skirt roof, the smaller upper storey and its steeper roof above
        scene.material = Mat.ROOF
        hipRoof(scene, 18f, 9.6f, 11f, 12.5f, 13.9f, KOREAN_ROOF, 1f)
        scene.material = Mat.WOOD
        bx(scene, -8.8f, -3.3f, 13.4f, 8.8f, 3.3f, 15.6f, RED_WOOD, RED_WOOD)
        scene.material = Mat.PAINT
        bx(scene, -9f, -3.5f, 15.6f, 9f, 3.5f, 16.4f, DANCHEONG, DANCHEONG)
        if (night) glowBox(scene, -8.6f, -3.35f, 13.8f, 8.6f, 3.35f, 15.2f, c(0xFFFFD98A))
        scene.material = Mat.ROOF
        hipRoof(scene, 12.8f, 6f, 16.4f, 7.2f, 20.6f, KOREAN_ROOF, 1.2f)
        scene.material = Mat.PLAIN
        bx(scene, -7.6f, -.35f, 20.5f, 7.6f, .35f, 21.2f, c(0xFFE2DDD2), c(0xFFD4CEC2))
        for (sx in -1..1 step 2) bx(scene, sx * 7.6f - .5f, -.45f, 20.6f, sx * 7.6f + .5f, .45f, 22f, c(0xFFD4CEC2), c(0xFFC7C0B3))
    }

    /** A round-topped arch opening on a face at y = [fy], [hh] tall from [z0]. */
    private fun archFace(scene: Scene3D, ax: Float, fy: Float, hw: Float, hh: Float, color: Int, z0: Float = 0f) =
        archAt(scene, ax, fy, 1f, 0f, hw, z0, hh, color)

    /** A round-topped arch on a wall through ([ax], [ay]) running along ([ux], [uy]). */
    private fun archAt(scene: Scene3D, ax: Float, ay: Float, ux: Float, uy: Float, hw: Float, z0: Float, hh: Float, color: Int) {
        val n = 8
        val spring = z0 + hh - hw
        tx[0] = (ax - ux * hw) * k; ty[0] = (ay - uy * hw) * k; tz[0] = z0 * k
        tx[1] = (ax + ux * hw) * k; ty[1] = (ay + uy * hw) * k; tz[1] = z0 * k
        for (s in 0..n) {
            val a = s * PI.toFloat() / n
            tx[2 + s] = (ax + ux * cos(a) * hw) * k; ty[2 + s] = (ay + uy * cos(a) * hw) * k; tz[2 + s] = (spring + sin(a) * hw) * k
        }
        scene.poly(n + 3, tx, ty, tz, color)
    }

    /** Arched openings on all four faces of a square stage [o] from its centre (the faces toward the camera on the Canvas). */
    private fun openings(scene: Scene3D, x: Float, y: Float, o: Float, z0: Float, hh: Float, hw: Float, color: Int) {
        if (scene.gpu != null || scene.camLY < (y - o) * k) archAt(scene, x, y - o, 1f, 0f, hw, z0, hh, color)
        if (scene.gpu != null || scene.camLY > (y + o) * k) archAt(scene, x, y + o, -1f, 0f, hw, z0, hh, color)
        if (scene.gpu != null || scene.camLX < (x - o) * k) archAt(scene, x - o, y, 0f, 1f, hw, z0, hh, color)
        if (scene.gpu != null || scene.camLX > (x + o) * k) archAt(scene, x + o, y, 0f, -1f, hw, z0, hh, color)
    }

    /** Little stone urns on the four corners of a ledge. */
    private fun urns(scene: Scene3D, x: Float, y: Float, o: Float, z: Float, color: Int) {
        for (sx in -1..1 step 2) for (sy in -1..1 step 2) {
            frustum(scene, x + sx * o, y + sy * o, .3f, z, .22f, z + .5f, 6, color)
            frustum(scene, x + sx * o, y + sy * o, .22f, z + .5f, .02f, z + 1.1f, 6, color)
        }
    }

    /** Otemon: a white watchtower gate over a passage between battered stone walls, a causeway across the moat. */
    private fun yagura(scene: Scene3D, i: Int, d: Float) {
        val night = scene.daylight < .55f
        val cw = causeway[i]
        scene.material = Mat.STONE
        if (cw > 0f) {
            // the causeway: an earthen bridge with stone sides over the moat
            val y0 = -7f; val y1 = -cw
            bx(scene, -5f, y1, -Waterfront.DEPTH, 5f, y0, .25f, scene.snowy(c(0xFFBDB3A0)), ISHIGAKI)
        }
        // stone masses either side of the passage, then walls along the moat with a white plastered wall on top
        for (sx in -1..1 step 2) {
            batter(scene, min(sx * 4.6f, sx * 17f), max(sx * 4.6f, sx * 17f), -6.5f, 6.5f, 0f, 5.2f, 1.6f)
            batter(scene, min(sx * 17f, sx * 46f), max(sx * 17f, sx * 46f), -2.6f, 2.6f, 0f, 4f, 1.1f)
            scene.material = Mat.PLASTER
            bx(scene, sx * 17f, -.5f, 4f, sx * 46f, .5f, 6f, PLASTER, PLASTER)
            scene.material = Mat.ROOF
            q(scene, sx * 17f, -1f, 6f, sx * 46f, -1f, 6f, sx * 46f, 0f, 6.7f, sx * 17f, 0f, 6.7f, scene.lit(scene.snowy(TILE_ROOF), .9f))
            q(scene, sx * 46f, 1f, 6f, sx * 17f, 1f, 6f, sx * 17f, 0f, 6.7f, sx * 46f, 0f, 6.7f, scene.lit(scene.snowy(TILE_ROOF), 1f))
            scene.material = Mat.STONE
        }
        // the watari-yagura spanning the passage: white walls, dark windows, a big hip roof
        scene.material = Mat.PLASTER
        bx(scene, -16f, -5.2f, 5.2f, 16f, 5.2f, 11f, PLASTER, PLASTER)
        scene.material = Mat.PLAIN
        bx(scene, -16.05f, -5.25f, 5.2f, 16.05f, 5.25f, 6.4f, c(0xFF3A3C40), c(0xFF3A3C40))
        if (d < 300f) for (wx in -3..3) {
            val x = wx * 4f
            val col = if (night) c(0xFFFFE3A0) else c(0xFF2C2F33)
            if (night) scene.glow = true
            for (fs in -1..1 step 2) { val fy = fs * 5.25f
                if (scene.gpu == null && ((fy < 0 && scene.camLY > fy * k) || (fy > 0 && scene.camLY < fy * k))) continue
                q(scene, x - .8f, fy, 8.2f, x + .8f, fy, 8.2f, x + .8f, fy, 9.4f, x - .8f, fy, 9.4f, if (night) col else scene.lit(col, 1f))
            }
            scene.glow = false
        }
        scene.material = Mat.ROOF
        hipRoof(scene, 17.6f, 6.3f, 11f, 12.8f, 14.6f, c(0xFF5C6168), .5f)
        scene.material = Mat.PLAIN
        bx(scene, -13.4f, -.4f, 14.6f, 13.4f, .4f, 15.3f, c(0xFF3F4348), c(0xFF3F4348))
        for (sx in -1..1 step 2) bx(scene, sx * 13.4f - .4f, -.3f, 15.1f, sx * 13.4f + .4f, .3f, 16.6f, c(0xFF2F3236), c(0xFF2F3236))
    }

    /** Stone walls with sloping (battered) faces from [z0] to [z1], insetting [inset] at the top on the long sides. */
    private fun batter(scene: Scene3D, x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float, inset: Float) {
        val sf = scene.lit(ISHIGAKI, scene.sideShade(0f, -1f)); val sb = scene.lit(ISHIGAKI, scene.sideShade(0f, 1f))
        if (scene.gpu != null || scene.camLY < y0 * k) q(scene, x0, y0, z0, x1, y0, z0, x1, y0 + inset, z1, x0, y0 + inset, z1, sf)
        if (scene.gpu != null || scene.camLY > y1 * k) q(scene, x1, y1, z0, x0, y1, z0, x0, y1 - inset, z1, x1, y1 - inset, z1, sb)
        if (scene.gpu != null || scene.camLX < x0 * k) q(scene, x0, y0, z0, x0, y1, z0, x0, y1 - inset, z1, x0, y0 + inset, z1, scene.lit(ISHIGAKI, scene.sideShade(-1f, 0f)))
        if (scene.gpu != null || scene.camLX > x1 * k) q(scene, x1, y0, z0, x1, y1, z0, x1, y1 - inset, z1, x1, y0 + inset, z1, scene.lit(ISHIGAKI, scene.sideShade(1f, 0f)))
        q(scene, x0, y0 + inset, z1, x1, y0 + inset, z1, x1, y1 - inset, z1, x0, y1 - inset, z1, scene.lit(scene.snowy(c(0xFFA9A497)), 1f))
    }

    // ------------------------------------------------------------------ churches

    private fun church(scene: Scene3D, l: Landmark, d: Float) {
        val night = scene.daylight < .55f
        val wall = when (l.style) { Landmark.BASILICA -> c(0xFFE6DCC4); Landmark.PAGODA_TOWER -> c(0xFFBDB29C); else -> c(0xFFD8C9AA) }
        val roof = when (l.style) { Landmark.BASILICA -> c(0xFF8E4A3A); Landmark.PAGODA_TOWER -> c(0xFF9A4E3A); else -> c(0xFF5D9283) }
        val dome = when (l.style) { Landmark.BASILICA -> c(0xFFA7B0B5); Landmark.PAGODA_TOWER -> c(0xFF7E8C84); else -> c(0xFF5E9C8C) }
        val trim = c(0xFFF2EDE2)
        val wallC = if (night) scene.mix(wall, c(0xFFFFE3C2), .35f) else wall
        // Quiapo's front stands between twin belfries; the Cathedral's dome rides high on a tall drum
        val twin = l.style == Landmark.BASILICA
        val lift = if (l.style == Landmark.CATHEDRAL) 4.5f else 0f
        // parts in back-to-front order for the Canvas path: 0 apse, 1 nave, 2 transept, 3 dome, 4 facade, 5 and 6 towers
        val cx = churchX; val cy = churchY
        cx[5] = if (twin) -10.6f else 10.4f
        cx[6] = 10.6f
        val parts = if (twin) 7 else 6
        val ord = churchOrd; val dd = churchD
        for (p in 0 until parts) { val ex = scene.camLX - cx[p] * k; val ey = scene.camLY - cy[p] * k; dd[p] = sqrt(ex * ex + ey * ey) + churchBias[p] * k; ord[p] = p }
        for (a in 1 until parts) { val v = ord[a]; var b = a - 1; while (b >= 0 && dd[ord[b]] < dd[v]) { ord[b + 1] = ord[b]; b-- }; ord[b + 1] = v }
        for (a in 0 until parts) when (val p = ord[a]) {
            0 -> { // apse: half an octagon
                scene.material = Mat.STONE
                frustum(scene, 0f, 12f, 7.4f, 0f, 7.4f, 11f, 8, wallC, PI.toFloat() / 8)
                scene.material = Mat.ROOF
                frustum(scene, 0f, 12f, 7.8f, 11f, 2f, 15f, 8, roof, PI.toFloat() / 8)
            }
            1 -> { // nave with a gable roof
                scene.material = Mat.STONE
                bx(scene, -7.5f, -20f, 0f, 7.5f, 12f, 13f, wallC, wallC)
                if (d < 300f) windows(scene, 7.5f, -18f, 10f, 5.5f, 10f, night)
                scene.material = Mat.ROOF
                gable(scene, -8.2f, 8.2f, -20f, 12f, 13f, 18.5f, roof)
            }
            2 -> { // transept
                scene.material = Mat.STONE
                bx(scene, -13f, 0f, 0f, 13f, 10f, 12f, wallC, wallC)
                scene.material = Mat.ROOF
                gableX(scene, -13.6f, 13.6f, -.6f, 10.6f, 12f, 17f, roof)
            }
            3 -> { // dome on a drum over the crossing, ribbed, with a lantern and a cross
                scene.material = Mat.STONE
                frustum(scene, 0f, 5f, 5.6f, 15f, 5.6f, 20f + lift, 12, wallC)
                frustum(scene, 0f, 5f, 6f, 19.6f + lift, 6f, 20.1f + lift, 12, trim)
                if (d < 300f) {
                    if (night) scene.glow = true
                    for (w in 0 until 6) {
                        val a = w * PI.toFloat() / 3 + .26f
                        val nx = cos(a); val ny = sin(a)
                        if (scene.gpu == null && (scene.camLX - nx * 5.6f * k) * nx + (scene.camLY - (5f + ny * 5.6f) * k) * ny <= 0f) continue
                        val tx0 = -ny * .6f; val ty0 = nx * .6f
                        val z0 = 16.2f + lift * .4f; val z1 = 18.8f + lift
                        q(scene, nx * 5.65f - tx0, 5f + ny * 5.65f - ty0, z0, nx * 5.65f + tx0, 5f + ny * 5.65f + ty0, z0,
                            nx * 5.65f + tx0, 5f + ny * 5.65f + ty0, z1, nx * 5.65f - tx0, 5f + ny * 5.65f - ty0, z1, if (night) WorldRenderer.LIT else scene.lit(DARK_GLASS, 1f))
                    }
                    scene.glow = false
                }
                scene.material = Mat.ROOF
                val rs = domeR
                val zs = domeZ
                for (r in 0 until 4) frustum(scene, 0f, 5f, rs[r], zs[r] + lift, rs[r + 1], zs[r + 1] + lift, 12, scene.mix(dome, c(0xFFFFFFFF), r * .05f))
                // pale ribs from the drum to the lantern
                if (d < 360f) for (rb in 0 until 12) {
                    val a = rb * PI.toFloat() / 6
                    val nx = cos(a); val ny = sin(a)
                    if (scene.gpu == null && (scene.camLX - nx * 5f * k) * nx + (scene.camLY - (5f + ny * 5f) * k) * ny <= 0f) continue
                    for (r in 0 until 4) {
                        val r0 = rs[r] + .05f; val r1 = rs[r + 1] + .05f; val w0 = .16f + rs[r] * .02f; val w1 = .16f + rs[r + 1] * .02f
                        q(scene, nx * r0 - ny * w0, 5f + ny * r0 + nx * w0, zs[r] + lift, nx * r0 + ny * w0, 5f + ny * r0 - nx * w0, zs[r] + lift,
                            nx * r1 + ny * w1, 5f + ny * r1 - nx * w1, zs[r + 1] + lift, nx * r1 - ny * w1, 5f + ny * r1 + nx * w1, zs[r + 1] + lift,
                            scene.lit(scene.mix(dome, trim, .55f), scene.sideShade(nx, ny)))
                    }
                }
                scene.material = Mat.STONE
                frustum(scene, 0f, 5f, 1.1f, 27.8f + lift, 1.1f, 30f + lift, 8, trim, cap = dome)
                frustum(scene, 0f, 5f, 1.2f, 30f + lift, .2f, 31f + lift, 8, dome)
                cross(scene, 0f, 5f, 31f + lift, 2.2f)
            }
            4 -> { // facade: a tall front with a pediment, cornices, saints in niches, carved doors and a rose window, floodlit at night
                scene.material = Mat.STONE
                val front = if (night) floodlit(scene, wall) else wallC
                val tr = if (night) floodlit(scene, trim) else trim
                if (night) scene.glow = true
                bx(scene, -8.8f, -21f, 0f, 8.8f, -19.8f, 17f, front, front)
                tri(scene, -8.8f, -21f, 17f, 8.8f, -21f, 17f, 0f, -21f, 21.5f, if (night) front else scene.lit(wallC, scene.sideShade(0f, -1f)))
                scene.glow = false
                tri(scene, 8.8f, -19.8f, 17f, -8.8f, -19.8f, 17f, 0f, -19.8f, 21.5f, scene.lit(wallC, .9f))
                q(scene, -8.8f, -21f, 17f, 0f, -21f, 21.5f, 0f, -19.8f, 21.5f, -8.8f, -19.8f, 17f, scene.lit(scene.snowy(roof), .95f))
                q(scene, 0f, -21f, 21.5f, 8.8f, -21f, 17f, 8.8f, -19.8f, 17f, 0f, -19.8f, 21.5f, scene.lit(scene.snowy(roof), 1f))
                cross(scene, 0f, -20.4f, 21.5f, 1.8f)
                if (scene.gpu != null || scene.camLY < -21f * k) {
                    val dark = scene.lit(c(0xFF3A2E26), 1f)
                    for (di in -1..1) archFace(scene, di * 5f, -21.03f, if (di == 0) 1.4f else 1f, if (di == 0) 5f else 3.8f, dark)
                    if (d < 260f) {
                        // carved wooden doors inside the arches
                        val wood = scene.lit(c(0xFF6B4226), 1f)
                        for (di in -1..1) archFace(scene, di * 5f, -21.06f, if (di == 0) 1.1f else .78f, if (di == 0) 4.6f else 3.45f, wood)
                    }
                    // rose window in a stone ring
                    val n = 12
                    for (s in 0 until n) { val a = s * 2 * PI.toFloat() / n; tx[s] = cos(a) * 2.35f * k; ty[s] = -21.035f * k; tz[s] = (11.5f + sin(a) * 2.35f) * k }
                    if (night) scene.glow = true
                    scene.poly(n, tx, ty, tz, tr)
                    for (s in 0 until n) { val a = s * 2 * PI.toFloat() / n; tx[s] = cos(a) * 2f * k; ty[s] = -21.05f * k; tz[s] = (11.5f + sin(a) * 2f) * k }
                    scene.poly(n, tx, ty, tz, if (night) c(0xFFFFD98A) else scene.lit(c(0xFF3E4F6A), 1f))
                    if (!night) scene.glow = false
                    // pilasters, a cornice between the two orders and a heavier one under the pediment
                    for (px in PILASTERS) bx(scene, px - .45f, -21.4f, 0f, px + .45f, -21f, 16.6f, tr, tr)
                    bx(scene, -9f, -21.5f, 8.6f, 9f, -21f, 9.2f, tr, tr)
                    bx(scene, -9.2f, -21.65f, 16.6f, 9.2f, -20.9f, 17.3f, tr, tr)
                    scene.glow = false
                    // saints in arched niches between the upper pilasters
                    if (d < 260f) for (sx in -1..1 step 2) {
                        val nx = sx * 5.5f
                        archFace(scene, nx, -21.04f, .8f, 3.9f, dark, z0 = 10.1f)
                        if (night) scene.glow = true
                        bx(scene, nx - .75f, -21.6f, 9.9f, nx + .75f, -21f, 10.2f, tr, tr)
                        val stone = if (night) floodlit(scene, c(0xFFEDE8DC)) else c(0xFFEDE8DC)
                        bx(scene, nx - .32f, -21.42f, 10.2f, nx + .32f, -21.08f, 12.5f, stone, stone)
                        scene.blob(nx * k, -21.25f * k, 12.8f * k, .27f * k, if (night) stone else scene.lit(stone, 1f), 8)
                        scene.glow = false
                    }
                }
                // floodlights on the square throw warm light up the front
                if (night) {
                    scene.clearFrame()
                    for (sx in -1..1 step 2) scene.glowOval(l.wx(sx * 5f * k, -24.5f * k), l.wy(sx * 5f * k, -24.5f * k), .06f, 6.5f * k, 4.5f * k, l.cs, l.sn, c(0xFFFFD9A0), .3f)
                    scene.setFrame(l.x, l.y, l.cs, l.sn)
                }
            }
            5 -> tower(scene, l, cx[5], if (twin) -18.2f else -17f, wallC, roof, dome, trim, night, d)
            6 -> tower(scene, l, cx[6], -18.2f, wallC, roof, dome, trim, night, d)
        }
        scene.material = Mat.PLAIN
    }

    /** A stone color under floodlights at night: warmer, and a touch dimmer since it's drawn self-lit. */
    private fun floodlit(scene: Scene3D, color: Int) = scene.mix(scene.mix(color, c(0xFFFFD9A0), .3f), c(0xFF000000), .2f)

    private val domeR = floatArrayOf(6.1f, 5.7f, 4.8f, 3.4f, 1.3f)
    private val domeZ = floatArrayOf(20f, 22.6f, 24.8f, 26.6f, 27.8f)
    private val churchX = FloatArray(7); private val churchY = floatArrayOf(16f, -4f, 5f, 5f, -20f, -17f, -17f)
    private val churchBias = floatArrayOf(0f, 0f, -2f, -14f, -1f, -6f, -6f)
    private val churchOrd = IntArray(7); private val churchD = FloatArray(7)

    private fun tower(scene: Scene3D, l: Landmark, x: Float, y: Float, wall: Int, roof: Int, dome: Int, trim: Int, night: Boolean, d: Float) {
        scene.material = Mat.STONE
        if (l.style == Landmark.PAGODA_TOWER) {
            // Binondo's octagonal belfry: five stepped tiers, each with a ledge
            var z = 0f; var r = 3.9f
            for (t in 0 until 5) {
                val h = if (t == 0) 7f else 4.6f
                frustum(scene, x, y, r, z, r * .97f, z + h, 8, if (t % 2 == 0) wall else trim, PI.toFloat() / 8)
                if (t > 0) {
                    if (night) scene.glow = true
                    frustum(scene, x, y, r * .99f, z + h * .35f, r * .97f, z + h * .8f, 8, if (night) c(0xFFFFD98A) else scene.mix(wall, c(0xFF2A2622), .65f), PI.toFloat() / 8)
                    scene.glow = false
                }
                frustum(scene, x, y, r * 1.18f, z + h, r * 1.05f, z + h + .5f, 8, roof, PI.toFloat() / 8, cap = trim)
                z += h + .5f; r *= .86f
            }
            frustum(scene, x, y, r * 1.1f, z, .3f, z + 3.2f, 8, dome, PI.toFloat() / 8)
            cross(scene, x, y, z + 3.2f, 1.6f)
            return
        }
        val open = if (night) c(0xFFFFD98A) else scene.lit(c(0xFF2A2622), 1f)
        if (l.style == Landmark.BASILICA) {
            // Quiapo's belfries: a tall square base, two open stages stepping in, a dome and lantern, urns on the ledges
            bx(scene, x - 3.2f, y - 3.2f, 0f, x + 3.2f, y + 3.2f, 14f, wall, wall)
            if (night) scene.glow = true
            openings(scene, x, y, 3.22f, 8.2f, 3.4f, .65f, if (night) c(0xFFFFD98A) else scene.lit(DARK_GLASS, 1f))
            scene.glow = false
            bx(scene, x - 3.5f, y - 3.5f, 14f, x + 3.5f, y + 3.5f, 14.6f, trim, trim)
            urns(scene, x, y, 3.2f, 14.6f, trim)
            bx(scene, x - 2.8f, y - 2.8f, 14.6f, x + 2.8f, y + 2.8f, 19.6f, wall, wall)
            if (night) scene.glow = true
            openings(scene, x, y, 2.82f, 15.3f, 3.7f, 1.05f, open)
            scene.glow = false
            bx(scene, x - 3.1f, y - 3.1f, 19.6f, x + 3.1f, y + 3.1f, 20.1f, trim, trim)
            urns(scene, x, y, 2.8f, 20.1f, trim)
            bx(scene, x - 2.3f, y - 2.3f, 20.1f, x + 2.3f, y + 2.3f, 23.9f, wall, wall)
            if (night) scene.glow = true
            openings(scene, x, y, 2.32f, 20.7f, 2.8f, .75f, open)
            scene.glow = false
            bx(scene, x - 2.6f, y - 2.6f, 23.9f, x + 2.6f, y + 2.6f, 24.3f, trim, trim)
            scene.material = Mat.ROOF
            frustum(scene, x, y, 2.3f, 24.3f, 2.1f, 25.4f, 8, dome, PI.toFloat() / 8)
            frustum(scene, x, y, 2.1f, 25.4f, 1.4f, 26.6f, 8, dome, PI.toFloat() / 8)
            frustum(scene, x, y, 1.4f, 26.6f, .45f, 27.4f, 8, dome, PI.toFloat() / 8)
            scene.material = Mat.STONE
            frustum(scene, x, y, .45f, 27.4f, .45f, 28.3f, 6, trim, cap = dome)
            cross(scene, x, y, 28.3f, 1.5f)
            return
        }
        // a square campanile: shaft, open belfry, a small dome
        bx(scene, x - 3f, y - 3f, 0f, x + 3f, y + 3f, 22f, wall, wall)
        if (d < 300f) {
            // corner quoins and slit windows up the shaft
            for (sx in -1..1 step 2) for (sy in -1..1 step 2) bx(scene, x + sx * 2.75f - .3f, y + sy * 2.75f - .3f, 0f, x + sx * 2.75f + .3f, y + sy * 2.75f + .3f, 22f, trim, trim)
            if (night) scene.glow = true
            for (zw in floatArrayOf(7f, 13f, 18f)) openings(scene, x, y, 3.02f, zw, 2.2f, .4f, if (night) c(0xFFFFD98A) else scene.lit(DARK_GLASS, 1f))
            scene.glow = false
        }
        bx(scene, x - 3.3f, y - 3.3f, 22f, x + 3.3f, y + 3.3f, 22.7f, trim, trim)
        bx(scene, x - 2.7f, y - 2.7f, 22.7f, x + 2.7f, y + 2.7f, 28f, wall, wall)
        val dark = open
        if (night) scene.glow = true
        val o = 2.72f
        if (scene.gpu != null || scene.camLY < (y - o) * k) q(scene, x - 1.4f, y - o, 23.6f, x + 1.4f, y - o, 23.6f, x + 1.4f, y - o, 27f, x - 1.4f, y - o, 27f, dark)
        if (scene.gpu != null || scene.camLY > (y + o) * k) q(scene, x - 1.4f, y + o, 23.6f, x + 1.4f, y + o, 23.6f, x + 1.4f, y + o, 27f, x - 1.4f, y + o, 27f, dark)
        if (scene.gpu != null || scene.camLX < (x - o) * k) q(scene, x - o, y - 1.4f, 23.6f, x - o, y + 1.4f, 23.6f, x - o, y + 1.4f, 27f, x - o, y - 1.4f, 27f, dark)
        if (scene.gpu != null || scene.camLX > (x + o) * k) q(scene, x + o, y - 1.4f, 23.6f, x + o, y + 1.4f, 23.6f, x + o, y + 1.4f, 27f, x + o, y - 1.4f, 27f, dark)
        scene.glow = false
        bx(scene, x - 3.1f, y - 3.1f, 28f, x + 3.1f, y + 3.1f, 28.6f, trim, trim)
        if (d < 300f) urns(scene, x, y, 2.75f, 28.6f, trim)
        scene.material = Mat.ROOF
        frustum(scene, x, y, 2.7f, 28.6f, 2.1f, 30.6f, 8, dome)
        frustum(scene, x, y, 2.1f, 30.6f, .45f, 32.6f, 8, dome)
        cross(scene, x, y, 32.6f, 1.6f)
    }

    private fun windows(scene: Scene3D, hx: Float, y0: Float, y1: Float, z0: Float, z1: Float, night: Boolean) {
        val col = if (night) c(0xFFFFD98A) else scene.lit(c(0xFF3E4F6A), 1f)
        if (night) scene.glow = true
        var y = y0 + 2f
        while (y < y1 - 1f) {
            for (sx in -1..1 step 2) {
                if (scene.gpu == null && ((sx < 0 && scene.camLX > -hx * k) || (sx > 0 && scene.camLX < hx * k))) continue
                val x = sx * (hx + .03f)
                q(scene, x, y - .7f, z0, x, y + .7f, z0, x, y + .7f, z1, x, y - .7f, z1, col)
            }
            y += 5f
        }
        scene.glow = false
    }

    /** A gable roof running along y from [y0] to [y1]: eaves at [ze] on x = [x0]/[x1], the ridge at [zr]. */
    private fun gable(scene: Scene3D, x0: Float, x1: Float, y0: Float, y1: Float, ze: Float, zr: Float, color: Int) {
        val m = (x0 + x1) * .5f
        q(scene, x0, y0, ze, m, y0, zr, m, y1, zr, x0, y1, ze, scene.lit(scene.snowy(color), scene.sideShade(-1f, 0f) * 1.1f))
        q(scene, m, y0, zr, x1, y0, ze, x1, y1, ze, m, y1, zr, scene.lit(scene.snowy(color), scene.sideShade(1f, 0f) * 1.1f))
        tri(scene, x0 + .7f, y0, ze, x1 - .7f, y0, ze, m, y0, zr - .4f, scene.lit(color, .85f))
        tri(scene, x0 + .7f, y1, ze, x1 - .7f, y1, ze, m, y1, zr - .4f, scene.lit(color, .85f))
    }

    /** A gable roof running along x. */
    private fun gableX(scene: Scene3D, x0: Float, x1: Float, y0: Float, y1: Float, ze: Float, zr: Float, color: Int) {
        val m = (y0 + y1) * .5f
        q(scene, x0, y0, ze, x1, y0, ze, x1, m, zr, x0, m, zr, scene.lit(scene.snowy(color), scene.sideShade(0f, -1f) * 1.1f))
        q(scene, x0, m, zr, x1, m, zr, x1, y1, ze, x0, y1, ze, scene.lit(scene.snowy(color), scene.sideShade(0f, 1f) * 1.1f))
        tri(scene, x0, y0 + .7f, ze, x0, y1 - .7f, ze, x0, m, zr - .4f, scene.lit(color, .85f))
        tri(scene, x1, y0 + .7f, ze, x1, y1 - .7f, ze, x1, m, zr - .4f, scene.lit(color, .85f))
    }

    private fun cross(scene: Scene3D, x: Float, y: Float, z: Float, h: Float) {
        val gold = c(0xFFD9B44A)
        bx(scene, x - .1f, y - .1f, z, x + .1f, y + .1f, z + h, gold, gold)
        bx(scene, x - .45f, y - .1f, z + h * .62f, x + .45f, y + .1f, z + h * .74f, gold, gold)
    }

    // ------------------------------------------------------------------ Ferris wheel

    private val HUB = 31f
    private val RIM = 28f

    private fun wheel(scene: Scene3D, d: Float, time: Float, moving: Boolean) {
        val night = scene.daylight < .55f
        val steel = c(0xFFE4E6E8)
        // the camera's side of the wheel: the far A-frame, then the wheel, then the near one
        val near = if (scene.camLY >= 0f) 1 else -1
        scene.material = Mat.CONCRETE
        bx(scene, -9f, -6f, 0f, 9f, 6f, 1.1f, c(0xFFCFCAC0), c(0xFFB9B4AA))
        bx(scene, -6f, -4f, 1.1f, 6f, 4f, 3.6f, c(0xFFE8E6E0), c(0xFF5D7FA0))
        scene.material = Mat.METAL
        aFrame(scene, -near, steel)
        if (moving) rotor(scene, d, time)
        aFrame(scene, near, steel)
        // the hub and axle
        scene.material = Mat.PLAIN
        bx(scene, -1.4f, -5f, HUB - 1.4f, 1.4f, 5f, HUB + 1.4f, c(0xFFB8BDC2), c(0xFFA2A8AE))
        if (night) glowBox(scene, -1f, -5.05f, HUB - 1f, 1f, 5.05f, HUB + 1f, c(0xFFFFF1C9))
    }

    /** One of the two A-frames holding the axle, at y = [side]·4.6. */
    private fun aFrame(scene: Scene3D, side: Int, color: Int) {
        val y = side * 4.6f
        for (sx in -1..1 step 2) {
            val fx = sx * 15f
            // a leg from the ground up to the hub, as a slab with some thickness
            for (ts in -1..1 step 2) { val t = ts * .45f
                q(scene, fx - .9f, y + t, 0f, fx + .9f, y + t, 0f, .5f * sx, y + t, HUB, -.5f * sx, y + t, HUB, scene.lit(color, scene.sideShade(0f, if (t > 0) 1f else -1f)))
            }
            q(scene, fx - .9f * sx, y - .45f, 0f, fx - .9f * sx, y + .45f, 0f, -.5f * sx, y + .45f, HUB, -.5f * sx, y - .45f, HUB, scene.lit(color, .85f))
        }
        bx(scene, -9f, y - .3f, 12f, 9f, y + .3f, 12.8f, color, color)
    }

    private fun rotor(scene: Scene3D, d: Float, time: Float) {
        val night = scene.daylight < .55f
        val spin = time * 2f * PI.toFloat() / WHEEL_TURN
        val segs = if (d < 400f) 32 else 18
        val steel = c(0xFFEDEFF1)
        scene.material = Mat.METAL
        if (night) scene.glow = true
        for (rs in -1..1 step 2) { val ry = rs * 1.7f
            for (s in 0 until segs) {
                val a0 = spin + s * 2 * PI.toFloat() / segs; val a1 = spin + (s + 1) * 2 * PI.toFloat() / segs
                val col = if (night) RIM_LIGHTS[((s + (time * 2f).toInt()) / 4) % 4] else scene.lit(steel, scene.sideShade(0f, if (ry > 0) 1f else -1f))
                q(scene, sin(a0) * (RIM - .5f), ry, HUB + cos(a0) * (RIM - .5f), sin(a1) * (RIM - .5f), ry, HUB + cos(a1) * (RIM - .5f),
                    sin(a1) * (RIM + .3f), ry, HUB + cos(a1) * (RIM + .3f), sin(a0) * (RIM + .3f), ry, HUB + cos(a0) * (RIM + .3f), col)
            }
        }
        scene.glow = false
        // spokes
        val spokes = if (d < 400f) 16 else 8
        val sc = scene.lit(c(0xFFC9CDD1), 1f)
        for (rs in -1..1 step 2) for (s in 0 until spokes) { val ry = rs * 1.7f
            val a = spin + s * 2 * PI.toFloat() / spokes
            val ca = cos(a); val sa = sin(a)
            val px = ca * .12f; val pz = -sa * .12f
            q(scene, -px, ry, HUB - pz, px, ry, HUB + pz, sa * RIM + px, ry, HUB + ca * RIM + pz, sa * RIM - px, ry, HUB + ca * RIM - pz, sc)
        }
        // gondolas hang level from the rim
        val cabins = 24
        scene.material = Mat.PAINT
        for (g in 0 until cabins) {
            val a = spin + g * 2 * PI.toFloat() / cabins
            val gx = sin(a) * RIM; val gz = HUB + cos(a) * RIM
            val col = GONDOLAS[g % GONDOLAS.size]
            if (d < 400f) bx(scene, gx - .08f, -.08f, gz - .9f, gx + .08f, .08f, gz, steel, steel)
            bx(scene, gx - 1.1f, -1.2f, gz - 3.3f, gx + 1.1f, 1.2f, gz - .9f, col, col)
            if (night) glowBox(scene, gx - 1.12f, -1.22f, gz - 2.6f, gx + 1.12f, 1.22f, gz - 1.6f, c(0xFFFFE6A8))
        }
        scene.material = Mat.PLAIN
    }

    // ------------------------------------------------------------------ Chinatown arch

    /**
     * Binondo's friendship arch over the street: red columns on granite plinths, a tall middle span over the road and
     * lower ones over the sidewalks, painted friezes with a gold name board, green glazed roofs with upturned eaves and
     * gold ridges, and red lanterns that glow at night.
     */
    private fun paifang(scene: Scene3D, i: Int, d: Float) {
        val night = scene.daylight < .55f
        val inner = archInner[i]; val outer = archOuter[i]
        val red = c(0xFFB3261E); val gold = c(0xFFE0B23A); val green = c(0xFF2F7A57); val frieze = c(0xFF1F6E78)
        scene.material = Mat.STONE
        for (sx in -1..1 step 2) for (cxp in floatArrayOf(inner, outer)) {
            val x = sx * cxp
            bx(scene, x - .75f, -.75f, 0f, x + .75f, .75f, 1f, GRANITE, GRANITE)
            bx(scene, x - .58f, -.58f, 1f, x + .58f, .58f, 1.35f, GRANITE, GRANITE)
        }
        scene.material = Mat.PAINT
        for (sx in -1..1 step 2) {
            bx(scene, sx * inner - .4f, -.4f, 1.35f, sx * inner + .4f, .4f, 9.7f, red, red)
            bx(scene, sx * outer - .34f, -.34f, 1.35f, sx * outer + .34f, .34f, 7.2f, red, red)
            // gold bands near the tops of the columns
            bx(scene, sx * inner - .44f, -.44f, 6.5f, sx * inner + .44f, .44f, 6.75f, gold, gold)
        }
        // the middle span: beams either side of a painted frieze with the gold name board
        bx(scene, -inner - .5f, -.45f, 6.9f, inner + .5f, .45f, 7.5f, red, red)
        bx(scene, -inner + .4f, -.3f, 7.5f, inner - .4f, .3f, 8.9f, frieze, frieze)
        bx(scene, -2.7f, -.42f, 7.62f, 2.7f, .42f, 8.78f, gold, gold)
        if (d < 200f) for (fs in -1..1 step 2) {
            if (scene.gpu == null && scene.camLY * fs < 0f) continue
            // four big characters in dark red
            for (ch in 0 until 4) {
                val x0 = -2.25f + ch * 1.2f
                q(scene, x0, fs * .43f, 7.8f, x0 + .9f, fs * .43f, 7.8f, x0 + .9f, fs * .43f, 8.6f, x0, fs * .43f, 8.6f, scene.lit(c(0xFF8E1B16), 1f))
            }
        }
        bx(scene, -inner - .6f, -.5f, 8.9f, inner + .6f, .5f, 9.5f, red, red)
        // bracket sets under the eaves
        if (d < 260f) {
            var x = -inner
            while (x <= inner + .01f) { bx(scene, x - .22f, -.62f, 9.5f, x + .22f, .62f, 9.85f, gold, green); x += (inner * 2f) / 8f }
        }
        scene.material = Mat.ROOF
        hipRoof(scene, inner + 2.1f, 1.75f, 9.85f, inner - 1.2f, 12f, green, .6f)
        scene.material = Mat.PAINT
        bx(scene, -inner + .8f, -.2f, 11.85f, inner - .8f, .2f, 12.35f, gold, gold)
        for (sx in -1..1 step 2) {
            // the ridge's curled ends
            bx(scene, sx * (inner - .8f) - .25f, -.22f, 11.9f, sx * (inner - .8f) + .25f, .22f, 13.1f, gold, gold)
            // the side spans over the sidewalks
            val m = sx * (inner + outer) / 2f; val hw = (outer - inner) / 2f
            bx(scene, m - hw - .35f, -.35f, 5.3f, m + hw + .35f, .35f, 5.8f, red, red)
            bx(scene, m - hw + .3f, -.24f, 5.8f, m + hw - .3f, .24f, 6.6f, frieze, frieze)
            bx(scene, m - hw - .45f, -.4f, 6.6f, m + hw + .45f, .4f, 7.1f, red, red)
            scene.material = Mat.ROOF
            hipRoof(scene, hw + 1.2f, 1.3f, 7.25f, max(.2f, hw * .4f), 8.7f, green, .45f, m)
            scene.material = Mat.PAINT
            bx(scene, m - max(.2f, hw * .4f) - .2f, -.15f, 8.6f, m + max(.2f, hw * .4f) + .2f, .15f, 8.95f, gold, gold)
        }
        // red lanterns hung under the middle span
        val lantern = if (night) c(0xFFFF4D4D) else c(0xFFD6312A)
        for (lx in floatArrayOf(-inner * .55f, 0f, inner * .55f)) {
            bx(scene, lx - .03f, -.03f, 6.35f, lx + .03f, .03f, 6.9f, c(0xFF2A2A2A), c(0xFF2A2A2A))
            scene.blob(lx * k, 0f, 5.95f * k, .45f * k, if (night) lantern else scene.lit(lantern, 1f), 10)
            bx(scene, lx - .2f, -.2f, 6.3f, lx + .2f, .2f, 6.4f, gold, gold)
        }
        scene.material = Mat.PLAIN
    }

    // ------------------------------------------------------------------ city hall

    /**
     * Manila City Hall: a long four-storey hall with a rusticated ground floor, arched windows and a portico, and at its
     * end the tall clock tower with a clock on every face, an open belfry, a small dome and the flag.
     */
    private fun cityHall(scene: Scene3D, l: Landmark, d: Float) {
        val night = scene.daylight < .55f
        val wall = c(0xFFE7DCC2); val trim = c(0xFFF5F0E5); val base = c(0xFFCDBF9F)
        val near = d < 320f
        scene.material = Mat.STONE
        // the hall: a rusticated ground floor, three floors above, a cornice and a parapet
        bx(scene, -18f, -9f, 0f, 18f, 9f, 4.2f, base, base)
        bx(scene, -17.6f, -8.6f, 4.2f, 17.6f, 8.6f, 15.4f, wall, wall)
        bx(scene, -18.2f, -9.2f, 4f, 18.2f, 9.2f, 4.4f, trim, trim)
        bx(scene, -18.3f, -9.3f, 15.4f, 18.3f, 9.3f, 16.1f, trim, trim)
        bx(scene, -17.7f, -8.7f, 16.1f, 17.7f, 8.7f, 17f, c(0xFF9A948A), wall)
        if (near) {
            // rows of windows, arched on the ground floor, lit here and there at night
            val dark = scene.lit(DARK_GLASS, 1f)
            fun win(x: Float, y: Float, ux: Float, uy: Float, f: Int, seed: Int) {
                val lit = night && (seed * 7 + f * 3) % 5 < 3
                if (lit) scene.glow = true
                val col = if (lit) WorldRenderer.LIT else dark
                if (f == 0) archAt(scene, x, y, ux, uy, .7f, 1.2f, 2.4f, col)
                else {
                    val z0 = 1.1f + f * 3.65f; val z1 = z0 + 2.2f
                    q(scene, x - ux * .6f, y - uy * .6f, z0, x + ux * .6f, y + uy * .6f, z0, x + ux * .6f, y + uy * .6f, z1, x - ux * .6f, y - uy * .6f, z1, col)
                }
                scene.glow = false
            }
            var n = 0
            var x = -9.5f
            while (x < 17f) {
                for (f in 0 until 4) {
                    if (scene.gpu != null || scene.camLY < -8.6f * k) win(x, if (f == 0) -9.03f else -8.63f, 1f, 0f, f, n)
                    if (scene.gpu != null || scene.camLY > 8.6f * k) win(x, if (f == 0) 9.03f else 8.63f, -1f, 0f, f, n + 3)
                }
                x += 2.9f; n++
            }
            x = -16f
            while (x < -10f) {
                for (f in 0 until 4) if (scene.gpu != null || scene.camLY > 8.6f * k) win(x, if (f == 0) 9.03f else 8.63f, -1f, 0f, f, n + 1)
                x += 2.9f; n++
            }
            var y = -6.5f
            while (y < 7f) {
                for (f in 0 until 4) if (scene.gpu != null || scene.camLX > 17.6f * k) win(if (f == 0) 18.03f else 17.63f, y, 0f, 1f, f, n)
                y += 2.9f; n++
            }
            // a portico of columns up two floors in the middle of the front, under a pediment
            for (cxp in floatArrayOf(2f, 4.4f, 6.8f, 9.2f)) {
                frustum(scene, cxp, -10f, .38f, 4.4f, .34f, 11.6f, 8, trim)
                bx(scene, cxp - .5f, -10.5f, 11.6f, cxp + .5f, -9.5f, 12f, trim, trim)
            }
            bx(scene, 1.2f, -10.7f, 12f, 10f, -8.6f, 12.9f, trim, trim)
            tri(scene, 1.2f, -10.7f, 12.9f, 10f, -10.7f, 12.9f, 5.6f, -10.7f, 14.7f, scene.lit(trim, scene.sideShade(0f, -1f)))
            q(scene, 1.2f, -10.7f, 12.9f, 5.6f, -10.7f, 14.7f, 5.6f, -8.6f, 14.7f, 1.2f, -8.6f, 12.9f, scene.lit(scene.snowy(c(0xFF9A948A)), .95f))
            q(scene, 5.6f, -10.7f, 14.7f, 10f, -10.7f, 12.9f, 10f, -8.6f, 12.9f, 5.6f, -8.6f, 14.7f, scene.lit(scene.snowy(c(0xFF9A948A)), 1f))
            // steps up to it
            bx(scene, .8f, -11.6f, 0f, 10.4f, -9f, .3f, base, base)
            bx(scene, 1.2f, -11f, .3f, 10f, -9f, .6f, base, base)
        }
        // the clock tower at the hall's end, standing out in front of it
        val tx0 = -14.5f; val ty0 = -8.4f
        bx(scene, tx0 - 4.3f, ty0 - 4.3f, 0f, tx0 + 4.3f, ty0 + 4.3f, 4.4f, base, base)
        bx(scene, tx0 - 4f, ty0 - 4f, 4.4f, tx0 + 4f, ty0 + 4f, 23f, wall, wall)
        if (near) {
            for (sx in -1..1 step 2) for (sy in -1..1 step 2) bx(scene, tx0 + sx * 3.75f - .4f, ty0 + sy * 3.75f - .4f, 4.4f, tx0 + sx * 3.75f + .4f, ty0 + sy * 3.75f + .4f, 23f, trim, trim)
            val dark = scene.lit(c(0xFF3A2E26), 1f)
            if (scene.gpu != null || scene.camLY < (ty0 - 4.3f) * k) archAt(scene, tx0, ty0 - 4.32f, 1f, 0f, 1.5f, 0f, 3.9f, dark)
            if (night) scene.glow = true
            for (zw in floatArrayOf(7f, 11.2f, 15.4f, 19.4f)) openings(scene, tx0, ty0, 4.02f, zw, 2.4f, .55f, if (night) WorldRenderer.LIT else scene.lit(DARK_GLASS, 1f))
            scene.glow = false
        }
        bx(scene, tx0 - 4.5f, ty0 - 4.5f, 23f, tx0 + 4.5f, ty0 + 4.5f, 23.7f, trim, trim)
        // the clock stage
        bx(scene, tx0 - 3.8f, ty0 - 3.8f, 23.7f, tx0 + 3.8f, ty0 + 3.8f, 30.4f, wall, wall)
        clock(scene, tx0, ty0 - 3.82f, 0f, -1f, 27f, 2.3f, night, near)
        clock(scene, tx0, ty0 + 3.82f, 0f, 1f, 27f, 2.3f, night, near)
        clock(scene, tx0 - 3.82f, ty0, -1f, 0f, 27f, 2.3f, night, near)
        clock(scene, tx0 + 3.82f, ty0, 1f, 0f, 27f, 2.3f, night, near)
        bx(scene, tx0 - 4.2f, ty0 - 4.2f, 30.4f, tx0 + 4.2f, ty0 + 4.2f, 31f, trim, trim)
        if (near) urns(scene, tx0, ty0, 3.9f, 31f, trim)
        // the belfry, open on every side, then an octagonal drum, a dome and a flagpole
        bx(scene, tx0 - 2.9f, ty0 - 2.9f, 31f, tx0 + 2.9f, ty0 + 2.9f, 35.4f, wall, wall)
        if (night) scene.glow = true
        openings(scene, tx0, ty0, 2.92f, 31.6f, 3.2f, 1.1f, if (night) c(0xFFFFD98A) else scene.lit(c(0xFF2A2622), 1f))
        scene.glow = false
        bx(scene, tx0 - 3.2f, ty0 - 3.2f, 35.4f, tx0 + 3.2f, ty0 + 3.2f, 35.9f, trim, trim)
        frustum(scene, tx0, ty0, 2.1f, 35.9f, 2.1f, 37.3f, 8, wall, PI.toFloat() / 8)
        scene.material = Mat.ROOF
        frustum(scene, tx0, ty0, 2.3f, 37.3f, 1.7f, 38.5f, 8, c(0xFF7E8C84), PI.toFloat() / 8)
        frustum(scene, tx0, ty0, 1.7f, 38.5f, .3f, 39.8f, 8, c(0xFF7E8C84), PI.toFloat() / 8)
        scene.material = Mat.PLAIN
        bx(scene, tx0 - .06f, ty0 - .06f, 39.8f, tx0 + .06f, ty0 + .06f, 44f, c(0xFFD5D8DC), c(0xFFBFC5CB))
        philippineFlag(scene, tx0 + .06f, ty0, 43.8f, 2.6f, near)
        if (night) {
            // floodlights at the tower's foot
            scene.clearFrame()
            scene.glowOval(l.wx(tx0 * k, (ty0 - 6f) * k), l.wy(tx0 * k, (ty0 - 6f) * k), .06f, 6f * k, 4f * k, l.cs, l.sn, c(0xFFFFD9A0), .3f)
            scene.setFrame(l.x, l.y, l.cs, l.sn)
        }
    }

    /** A clock face on a wall through ([x], [y]) facing ([nx], [ny]): a bronze rim, a white face lit at night, hands at ten past ten. */
    private fun clock(scene: Scene3D, x: Float, y: Float, nx: Float, ny: Float, z: Float, r: Float, night: Boolean, near: Boolean) {
        if (scene.gpu == null && (scene.camLX - x * k) * nx + (scene.camLY - y * k) * ny <= 0f) return
        // the face's own left-to-right, as seen standing in front of it
        val ux = ny; val uy = -nx
        fun disc(rr: Float, out: Float, color: Int) {
            for (i in 0 until 16) {
                val a = i * 2f * PI.toFloat() / 16
                tx[i] = (x + ux * cos(a) * rr + nx * out) * k; ty[i] = (y + uy * cos(a) * rr + ny * out) * k; tz[i] = (z + sin(a) * rr) * k
            }
            scene.poly(16, tx, ty, tz, color)
        }
        val ink = scene.lit(c(0xFF262321), 1f)
        disc(r, .03f, scene.lit(c(0xFF6E5A32), 1f))
        if (night) scene.glow = true
        disc(r * .86f, .06f, if (night) c(0xFFFFF3C4) else scene.lit(c(0xFFF4F1E8), 1f))
        scene.glow = false
        if (!near) return
        fun bar(a: Float, r0: Float, r1: Float, w: Float, out: Float) {
            val ca = cos(a); val sa = sin(a)
            // perpendicular to the bar within the face
            val pu = -sa * w; val pz = ca * w
            q(scene, x + ux * (ca * r0 + pu) + nx * out, y + uy * (ca * r0 + pu) + ny * out, z + sa * r0 + pz,
                x + ux * (ca * r1 + pu) + nx * out, y + uy * (ca * r1 + pu) + ny * out, z + sa * r1 + pz,
                x + ux * (ca * r1 - pu) + nx * out, y + uy * (ca * r1 - pu) + ny * out, z + sa * r1 - pz,
                x + ux * (ca * r0 - pu) + nx * out, y + uy * (ca * r0 - pu) + ny * out, z + sa * r0 - pz, ink)
        }
        for (h in 0 until 12) bar(h * PI.toFloat() / 6, r * .68f, r * .8f, if (h % 3 == 0) r * .05f else r * .025f, .09f)
        // ten past ten: the hour hand toward ten, the minute hand toward two (angles counted from three o'clock, up)
        bar(PI.toFloat() * 5f / 6f, 0f, r * .45f, r * .045f, .1f)
        bar(PI.toFloat() / 6f, 0f, r * .66f, r * .03f, .11f)
    }

    // ------------------------------------------------------------------ Kabukicho's arch sign

    private fun arcade(scene: Scene3D, i: Int, l: Landmark) {
        val ei = arcadeEdge[i]
        if (ei < 0) return
        val e = city.roads.edges[ei]
        // across the lane near its start, in world space
        scene.clearFrame()
        val s = (e.clearA + 4f).coerceAtMost(e.len * .5f)
        val cx = e.ax + e.ux * s; val cy = e.ay + e.uy * s
        scene.setFrame(cx, cy, e.uy, -e.ux)
        k = 1f
        val half = e.cls.halfWidth + 1f
        val night = scene.daylight < .55f
        val post = c(0xFF2A2D31)
        for (sx in -1..1 step 2) bx(scene, sx * half - .2f, -.2f, 0f, sx * half + .2f, .2f, 7.6f, post, post)
        bx(scene, -half - .3f, -.35f, 5.6f, half + .3f, .35f, 7.4f, c(0xFFB0202A), c(0xFFC8232F))
        // lit lettering on both faces
        val letter = if (night) c(0xFFFFE680) else c(0xFFFFFFFF)
        if (night) scene.glow = true
        for (fs in -1..1 step 2) { val fy = fs * .37f
            var x = -half + .8f
            while (x < half - .8f) {
                q(scene, x, fy, 5.95f, x + .75f, fy, 5.95f, x + .75f, fy, 7.05f, x, fy, 7.05f, letter)
                x += 1.05f
            }
        }
        scene.glow = false
    }

    // ------------------------------------------------------------------ map labels

    /** Landmarks named on the map: all but the smaller billboard streets. */
    fun labelled(l: Landmark) = l.kind != Landmark.BILLBOARDS || l.style == Landmark.SCRAMBLE || l.style == Landmark.SCREENS
}

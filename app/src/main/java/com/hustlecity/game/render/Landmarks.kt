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
 * N Seoul Tower), shrine gates, monuments, palace gates, churches with domes and bell towers, and a Ferris wheel.
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
        if (l.style == Landmark.SCRAMBLE) {
            var best = -1; var bd = 18f
            for (n in city.roads.nodes) { val d = kotlin.math.hypot(n.x - l.x, n.y - l.y); if (n.degree >= 4 && d < bd) { bd = d; best = n.id } }
            scrambleNode[li] = best
        }
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
        Landmark.MONUMENT -> 20f
        Landmark.PALACE_GATE -> 22f
        Landmark.TORII -> 12f
        else -> 12f
    }

    fun radius(l: Landmark): Float {
        val e = l.extent ?: return 20f
        return l.size * sqrt(max(e[0] * e[0], e[2] * e[2]) + max(e[1] * e[1], e[3] * e[3]))
    }

    // ------------------------------------------------------------------ ground

    /** Paving, approaches and crossings, in the ground pass. */
    fun drawGround(scene: Scene3D, bbox: FloatArray) {
        for ((i, l) in city.landmarks.withIndex()) {
            val r = radius(l) + 10f
            if (l.x + r < bbox[0] || l.x - r > bbox[2] || l.y + r < bbox[1] || l.y - r > bbox[3]) continue
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
                }
            } finally { scene.clearFrame() }
            if (l.kind == Landmark.BILLBOARDS && scrambleNode[i] >= 0) scramble(scene, city.roads.nodes[scrambleNode[i]])
        }
        scene.material = Mat.PLAIN
    }

    private fun rect(scene: Scene3D, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Int) =
        scene.quad(x0 * k, y0 * k, z, x1 * k, y0 * k, z, x1 * k, y1 * k, z, x0 * k, y1 * k, z, color)

    /** The diagonal zebra crossings of a scramble crossing. */
    private fun scramble(scene: Scene3D, n: com.hustlecity.game.world.RoadNode) {
        val g = city.roads
        var reach = 0f
        for (id in n.edges) { val e = g.edges[id]; reach = max(reach, (if (e.a == n.id) e.clearA else e.clearB) + 2f) }
        reach = reach.coerceIn(10f, 24f)
        val white = scene.lit(WorldRenderer.WHITE_LINE, .95f)
        for (ai in 0..1) { val a = PI.toFloat() / 4 + ai * PI.toFloat() / 2
            val ux = cos(a); val uy = sin(a)
            // bars across the band, one after another along the way people walk
            val px = -uy * 2.2f; val py = ux * 2.2f
            var t = -reach
            while (t < reach) {
                if (abs(t) > 1.2f) {
                    val x0 = n.x + ux * t; val y0 = n.y + uy * t
                    val x1 = x0 + ux * .6f; val y1 = y0 + uy * .6f
                    scene.quad(x0 - px, y0 - py, .045f, x1 - px, y1 - py, .045f, x1 + px, y1 + py, .045f, x0 + px, y0 + py, .045f, white)
                }
                t += 1.25f
            }
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
        // a flagpole flying the country's flag
        val fxp = 8.5f; val fyp = -8.5f
        bx(scene, fxp - .1f, fyp - .1f, 0f, fxp + .1f, fyp + .1f, 18f, c(0xFFD5D8DC), c(0xFFBFC5CB))
        if (city.mapId == Maps.NYC) {
            // stripes and a blue canton
            for (s in 0 until 5) {
                val z1 = 17.8f - s * .48f
                q(scene, fxp + .1f, fyp, z1, fxp + 4.1f, fyp, z1, fxp + 4.1f, fyp, z1 - .48f, fxp + .1f, fyp, z1 - .48f, scene.lit(if (s % 2 == 0) c(0xFFB22234) else c(0xFFFFFFFF), 1f))
            }
            q(scene, fxp + .1f, fyp + .01f, 17.8f, fxp + 1.9f, fyp + .01f, 17.8f, fxp + 1.9f, fyp + .01f, 16.6f, fxp + .1f, fyp + .01f, 16.6f, scene.lit(c(0xFF3C3B6E), 1f))
        } else {
            // blue over red with a white triangle at the hoist
            q(scene, fxp + .1f, fyp, 17.8f, fxp + 4.1f, fyp, 17.8f, fxp + 4.1f, fyp, 16.6f, fxp + .1f, fyp, 16.6f, scene.lit(c(0xFF1F3F99), 1f))
            q(scene, fxp + .1f, fyp, 16.6f, fxp + 4.1f, fyp, 16.6f, fxp + 4.1f, fyp, 15.4f, fxp + .1f, fyp, 15.4f, scene.lit(c(0xFFCE2939), 1f))
            q(scene, fxp + .1f, fyp + .01f, 17.8f, fxp + 1.8f, fyp + .01f, 16.6f, fxp + .1f, fyp + .01f, 15.4f, fxp + .1f, fyp + .01f, 16.6f, scene.lit(c(0xFFFFFFFF), 1f))
        }
    }

    // ------------------------------------------------------------------ palace gates

    /** A hip roof with upturned corners: eaves rectangle at [ze], rising to a ridge [rx] long at [zr]. */
    private fun hipRoof(scene: Scene3D, ex: Float, ey: Float, ze: Float, rx: Float, zr: Float, color: Int, curl: Float) {
        val top = scene.snowy(color)
        // long slopes (front and back), then the hipped ends
        q(scene, -ex, -ey, ze + curl, ex, -ey, ze + curl, rx, 0f, zr, -rx, 0f, zr, scene.lit(top, .9f))
        q(scene, ex, ey, ze + curl, -ex, ey, ze + curl, -rx, 0f, zr, rx, 0f, zr, scene.lit(top, 1.02f))
        tri(scene, -ex, ey, ze + curl, -ex, -ey, ze + curl, -rx, 0f, zr, scene.lit(top, .95f))
        tri(scene, ex, -ey, ze + curl, ex, ey, ze + curl, rx, 0f, zr, scene.lit(top, .97f))
        // the eaves dip toward the middle of each side and sweep up at the corners
        if (curl > 0f) {
            q(scene, -ex * .55f, -ey, ze, ex * .55f, -ey, ze, ex, -ey, ze + curl, -ex, -ey, ze + curl, scene.lit(color, .7f))
            q(scene, -ex * .55f, ey, ze, ex * .55f, ey, ze, ex, ey, ze + curl, -ex, ey, ze + curl, scene.lit(color, .75f))
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

    /** A round-topped arch opening on a face at y = [fy]. */
    private fun archFace(scene: Scene3D, ax: Float, fy: Float, hw: Float, hh: Float, color: Int) {
        val n = 8
        val spring = hh - hw
        tx[0] = (ax - hw) * k; ty[0] = fy * k; tz[0] = 0f
        tx[1] = (ax + hw) * k; ty[1] = fy * k; tz[1] = 0f
        for (s in 0..n) {
            val a = s * PI.toFloat() / n
            tx[2 + s] = (ax + cos(a) * hw) * k; ty[2 + s] = fy * k; tz[2 + s] = (spring + sin(a) * hw) * k
        }
        scene.poly(n + 3, tx, ty, tz, color)
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
        // parts in back-to-front order for the Canvas path: 0 apse, 1 nave, 2 transept, 3 dome, 4 facade, 5 tower
        val cx = churchX; val cy = churchY
        cx[5] = if (l.style == Landmark.BASILICA) -10.4f else 10.4f
        val ord = churchOrd; val dd = churchD
        for (p in 0 until 6) { val ex = scene.camLX - cx[p] * k; val ey = scene.camLY - cy[p] * k; dd[p] = sqrt(ex * ex + ey * ey) + churchBias[p] * k; ord[p] = p }
        for (a in 1 until 6) { val v = ord[a]; var b = a - 1; while (b >= 0 && dd[ord[b]] < dd[v]) { ord[b + 1] = ord[b]; b-- }; ord[b + 1] = v }
        for (p in ord) when (p) {
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
            3 -> { // dome on a drum over the crossing, a lantern and a cross
                scene.material = Mat.STONE
                frustum(scene, 0f, 5f, 5.6f, 15f, 5.6f, 20f, 12, wallC)
                if (d < 300f) {
                    if (night) scene.glow = true
                    for (w in 0 until 6) {
                        val a = w * PI.toFloat() / 3 + .26f
                        val nx = cos(a); val ny = sin(a)
                        if (scene.gpu == null && (scene.camLX - nx * 5.6f * k) * nx + (scene.camLY - (5f + ny * 5.6f) * k) * ny <= 0f) continue
                        val tx0 = -ny * .6f; val ty0 = nx * .6f
                        q(scene, nx * 5.65f - tx0, 5f + ny * 5.65f - ty0, 16.2f, nx * 5.65f + tx0, 5f + ny * 5.65f + ty0, 16.2f,
                            nx * 5.65f + tx0, 5f + ny * 5.65f + ty0, 18.8f, nx * 5.65f - tx0, 5f + ny * 5.65f - ty0, 18.8f, if (night) WorldRenderer.LIT else scene.lit(DARK_GLASS, 1f))
                    }
                    scene.glow = false
                }
                scene.material = Mat.ROOF
                val rs = floatArrayOf(6.1f, 5.7f, 4.8f, 3.4f, 1.3f)
                val zs = floatArrayOf(20f, 22.6f, 24.8f, 26.6f, 27.8f)
                for (r in 0 until 4) frustum(scene, 0f, 5f, rs[r], zs[r], rs[r + 1], zs[r + 1], 12, scene.mix(dome, c(0xFFFFFFFF), r * .05f))
                scene.material = Mat.STONE
                frustum(scene, 0f, 5f, 1.1f, 27.8f, 1.1f, 30f, 8, trim, cap = dome)
                cross(scene, 0f, 5f, 30f, 2.2f)
            }
            4 -> { // facade: a tall front with a pediment, doors and a rose window
                scene.material = Mat.STONE
                bx(scene, -8.8f, -21f, 0f, 8.8f, -19.8f, 17f, wallC, wallC)
                tri(scene, -8.8f, -21f, 17f, 8.8f, -21f, 17f, 0f, -21f, 21.5f, scene.lit(wallC, scene.sideShade(0f, -1f)))
                tri(scene, 8.8f, -19.8f, 17f, -8.8f, -19.8f, 17f, 0f, -19.8f, 21.5f, scene.lit(wallC, .9f))
                q(scene, -8.8f, -21f, 17f, 0f, -21f, 21.5f, 0f, -19.8f, 21.5f, -8.8f, -19.8f, 17f, scene.lit(scene.snowy(roof), .95f))
                q(scene, 0f, -21f, 21.5f, 8.8f, -21f, 17f, 8.8f, -19.8f, 17f, 0f, -19.8f, 21.5f, scene.lit(scene.snowy(roof), 1f))
                cross(scene, 0f, -20.4f, 21.5f, 1.8f)
                if (scene.gpu != null || scene.camLY < -21f * k) {
                    val dark = scene.lit(c(0xFF3A2E26), 1f)
                    for (di in -1..1) archFace(scene, di * 5f, -21.03f, if (di == 0) 1.4f else 1f, if (di == 0) 5f else 3.8f, dark)
                    // rose window
                    val n = 12
                    for (s in 0 until n) { val a = s * 2 * PI.toFloat() / n; tx[s] = cos(a) * 2f * k; ty[s] = -21.04f * k; tz[s] = (11.5f + sin(a) * 2f) * k }
                    if (night) scene.glow = true
                    scene.poly(n, tx, ty, tz, if (night) c(0xFFFFD98A) else scene.lit(c(0xFF3E4F6A), 1f))
                    scene.glow = false
                    // pilasters and a cornice
                    for (px in PILASTERS) bx(scene, px - .45f, -21.4f, 0f, px + .45f, -21f, 16.6f, trim, trim)
                    bx(scene, -9f, -21.5f, 8.6f, 9f, -21f, 9.2f, trim, trim)
                }
            }
            5 -> tower(scene, l, cx[5], wallC, roof, dome, trim, night)
        }
        scene.material = Mat.PLAIN
    }

    private val churchX = FloatArray(6); private val churchY = floatArrayOf(16f, -4f, 5f, 5f, -20f, -17f)
    private val churchBias = floatArrayOf(0f, 0f, -2f, -14f, -1f, -6f)
    private val churchOrd = IntArray(6); private val churchD = FloatArray(6)

    private fun tower(scene: Scene3D, l: Landmark, x: Float, wall: Int, roof: Int, dome: Int, trim: Int, night: Boolean) {
        val y = -17f
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
        // a square campanile: shaft, open belfry, a small dome
        bx(scene, x - 3f, y - 3f, 0f, x + 3f, y + 3f, 22f, wall, wall)
        bx(scene, x - 3.3f, y - 3.3f, 22f, x + 3.3f, y + 3.3f, 22.7f, trim, trim)
        bx(scene, x - 2.7f, y - 2.7f, 22.7f, x + 2.7f, y + 2.7f, 28f, wall, wall)
        val dark = if (night) c(0xFFFFD98A) else scene.lit(c(0xFF2A2622), 1f)
        if (night) scene.glow = true
        val o = 2.72f
        if (scene.gpu != null || scene.camLY < (y - o) * k) q(scene, x - 1.4f, y - o, 23.6f, x + 1.4f, y - o, 23.6f, x + 1.4f, y - o, 27f, x - 1.4f, y - o, 27f, dark)
        if (scene.gpu != null || scene.camLY > (y + o) * k) q(scene, x - 1.4f, y + o, 23.6f, x + 1.4f, y + o, 23.6f, x + 1.4f, y + o, 27f, x - 1.4f, y + o, 27f, dark)
        if (scene.gpu != null || scene.camLX < (x - o) * k) q(scene, x - o, y - 1.4f, 23.6f, x - o, y + 1.4f, 23.6f, x - o, y + 1.4f, 27f, x - o, y - 1.4f, 27f, dark)
        if (scene.gpu != null || scene.camLX > (x + o) * k) q(scene, x + o, y - 1.4f, 23.6f, x + o, y + 1.4f, 23.6f, x + o, y + 1.4f, 27f, x + o, y - 1.4f, 27f, dark)
        scene.glow = false
        bx(scene, x - 3.1f, y - 3.1f, 28f, x + 3.1f, y + 3.1f, 28.6f, trim, trim)
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

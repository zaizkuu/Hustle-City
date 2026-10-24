package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Maps
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Where the city meets the water, and its old walls. Rivers and bays get stone quays: the water is drawn at street
 * level, so a quay wall dropping [DEPTH] meters below it on the far bank (the side facing the camera) makes the water
 * read as lying below the city. Bridges get parapets with pilasters and lamps, and arches and cutwater piers on the
 * side facing the camera. City walls get ramparts with a grassy walk, crenellations, bastions and gates.
 */
class Waterfront(private val city: City) {
    companion object {
        /** How far the water seems to lie below the streets. */
        const val DEPTH = 5f
        private val QUAY = c(0xFF8E8A80)
        private val QUAY_WET = c(0xFF5E5B53)
        private val BRIDGE_STONE = c(0xFFB7AE9C)
        private val ARCH_SHADOW = c(0xFF0C1218)
        private val STEEL = c(0xFF35473F)
        private val RAMPART = c(0xFF8A7C66)
        private val RAMPART_LIGHT = c(0xFFA39478)
        private val RAMPART_TOP = c(0xFF6F8250)
        private val LAWN = c(0xFF6A9A55)
        private val GATE = c(0xFFB5A588)
        private val LAMP = c(0xFF2F3A35)
        private val PIER = c(0xFFADB0B4)
    }

    // ------------------------------------------------------------------ quays

    /** Quay runs: x0, y0, x1, y1, land normal x, y, depth — 7 floats each. */
    private val quay: FloatArray
    private val quayCount: Int

    // ------------------------------------------------------------------ piers

    /** Streets that run on past the shore end on a pier: edge, from, to (meters along it), 3 floats each. */
    private val piers: FloatArray

    // ------------------------------------------------------------------ bridges

    /** A road piece over water: its wet stretches and the supports (abutments and piers) along it. */
    class Bridge(val edge: Int, val wetA: FloatArray, val wetB: FloatArray, val supports: FloatArray, val railA: Float, val railB: Float)

    /** A stretch of bridge parapet (a drawable). */
    class Railing(val bridge: Int, val s0: Float, val s1: Float, val cx: Float, val cy: Float, val r: Float)

    val bridges = ArrayList<Bridge>()
    val railings = ArrayList<Railing>()
    private val steel = city.mapId == Maps.NYC

    // ------------------------------------------------------------------ city walls

    /** A piece of city wall: kind 0 curtain wall, 1 gate over a road, 2 corner bastion; meters [t0, t1] along segment [seg]. */
    class WallPiece(val kind: Int, val wall: Int, val seg: Int, val t0: Float, val t1: Float, val cx: Float, val cy: Float, val r: Float, val wet: Boolean)

    private class WallSeg(val ax: Float, val ay: Float, val ux: Float, val uy: Float, val len: Float, val so: Float) {
        /** Outward normal. */
        val nx get() = -uy * so
        val ny get() = ux * so
    }

    val wallPieces = ArrayList<WallPiece>()
    private val wallSegs = ArrayList<ArrayList<WallSeg>>()
    /** Lawn strips outside the walls: x, y of the strip's start and end on the outer face, plus the outward normal. */
    private val lawn = ArrayList<FloatArray>()

    init {
        // quays along every shore that has land on one side and water on the other
        val out = ArrayList<Float>()
        val sh = city.shore
        val b = city.bounds
        val polys = ArrayList<FloatArray>(); val depth = ArrayList<Float>()
        // ponds in parks get a low kerb; rivers, moats and the sea a proper quay
        for (w in sh.water) {
            polys += w
            val bb = Geo.bounds(w)
            depth += if (city.parks.any { Geo.inside((bb[0] + bb[2]) * .5f, (bb[1] + bb[3]) * .5f, it.poly) }) 1.4f else DEPTH
        }
        for (w in sh.islands) { polys += w; depth += DEPTH }
        for (w in sh.land) { polys += w; depth += DEPTH }
        for ((pi, poly) in polys.withIndex()) {
            val n = poly.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                val ax = poly[i * 2]; val ay = poly[i * 2 + 1]; val bx = poly[j * 2]; val by = poly[j * 2 + 1]
                val len = dist(ax, ay, bx, by)
                if (len < .3f) continue
                val ux = (bx - ax) / len; val uy = (by - ay) / len
                val steps = max(1, ceil(len / 3f).toInt())
                var runSide = 0; var rx = 0f; var ry = 0f
                for (k in 0..steps) {
                    var side = 0
                    if (k < steps) {
                        val mx = ax + (bx - ax) * (k + .5f) / steps; val my = ay + (by - ay) * (k + .5f) / steps
                        if (mx > b[0] - 40f && mx < b[2] + 40f && my > b[1] - 40f && my < b[3] + 40f && !underBridge(mx, my)) {
                            val left = sh.wet(mx + uy * 1.6f, my - ux * 1.6f); val right = sh.wet(mx - uy * 1.6f, my + ux * 1.6f)
                            // land is on the dry side; +1 when that's the left of a→b (uy, -ux)
                            if (left != right) side = if (!left) 1 else -1
                        }
                    }
                    if (side != runSide) {
                        if (runSide != 0) {
                            val ex = ax + (bx - ax) * k / steps; val ey = ay + (by - ay) * k / steps
                            out += rx; out += ry; out += ex; out += ey; out += uy * runSide; out += -ux * runSide; out += depth[pi]
                        }
                        runSide = side
                        rx = ax + (bx - ax) * k / steps; ry = ay + (by - ay) * k / steps
                    }
                }
            }
        }
        quay = out.toFloatArray()
        quayCount = quay.size / 7
        buildBridges()
        buildWalls()
        piers = buildPiers()
    }

    /** The wet stretches of the streets that aren't bridges, out to the turning circle where they end. */
    private fun buildPiers(): FloatArray {
        val out = ArrayList<Float>()
        val g = city.roads
        for (e in g.edges) {
            if (e.overWater) continue
            val steps = max(2, ceil(e.len).toInt())
            var start = -1f
            for (k in 0..steps) {
                val s = e.len * k / steps
                val wet = city.shore.wet(e.ax + e.ux * s, e.ay + e.uy * s)
                if (wet && start < 0f) start = max(0f, s - e.len / steps)
                if ((!wet || k == steps) && start >= 0f) {
                    var a = start; var b = s
                    // a dead end's turning circle reaches past the node
                    if (a <= 0f && g.nodes[e.a].degree == 1) a = -e.cls.halfWidth
                    if (k == steps && wet && g.nodes[e.b].degree == 1) b = e.len + e.cls.halfWidth
                    out += e.id.toFloat(); out += a; out += b
                    start = -1f
                }
            }
        }
        return out.toFloatArray()
    }

    private fun underBridge(x: Float, y: Float): Boolean {
        var hit = false
        city.roads.near(x, y, 24f) { id ->
            val e = city.roads.edges[id]
            if (!hit && e.overWater && Geo.distToSeg(x, y, e.ax, e.ay, e.bx, e.by) < e.cls.outer + .6f) hit = true
        }
        return hit
    }

    private fun buildBridges() {
        for (e in city.roads.edges) {
            if (!e.overWater) continue
            val wa = ArrayList<Float>(); val wb = ArrayList<Float>()
            val steps = max(2, ceil(e.len / 2f).toInt())
            var start = -1f
            for (k in 0..steps) {
                val s = e.len * k / steps
                val wet = city.shore.wet(e.ax + e.ux * s, e.ay + e.uy * s)
                if (wet && start < 0f) start = s
                if ((!wet || k == steps) && start >= 0f) { wa += start; wb += if (wet) s else max(start, s - e.len / steps); start = -1f }
            }
            if (wa.isEmpty()) continue
            val sup = ArrayList<Float>()
            for (i in wa.indices) {
                val n = max(1, ((wb[i] - wa[i]) / 18f).roundToInt())
                for (k in 0..n) sup += wa[i] + (wb[i] - wa[i]) * k / n
            }
            val ra = max(e.clearA, wa.first() - 8f); val rb = min(e.len - e.clearB, wb.last() + 8f)
            val idx = bridges.size
            bridges += Bridge(e.id, wa.toFloatArray(), wb.toFloatArray(), sup.toFloatArray(), ra, rb)
            if (rb - ra < 1f) continue
            val chunks = max(1, ceil((rb - ra) / 24f).toInt())
            for (k in 0 until chunks) {
                val s0 = ra + (rb - ra) * k / chunks; val s1 = ra + (rb - ra) * (k + 1) / chunks
                val m = (s0 + s1) * .5f
                railings += Railing(idx, s0, s1, e.ax + e.ux * m, e.ay + e.uy * m, (s1 - s0) * .5f + e.cls.outer)
            }
        }
    }

    private fun buildWalls() {
        for ((wi, w0) in city.walls.withIndex()) {
            var w = w0
            if (w.size >= 4 && abs(w[0] - w[w.size - 2]) < .01f && abs(w[1] - w[w.size - 1]) < .01f) w = w.copyOf(w.size - 2)
            val n = w.size / 2
            if (n < 3) { wallSegs.add(ArrayList()); continue }
            var cx = 0f; var cy = 0f
            for (i in 0 until n) { cx += w[i * 2]; cy += w[i * 2 + 1] }
            cx /= n; cy /= n
            val segs = ArrayList<WallSeg>()
            for (i in 0 until n) {
                val j = (i + 1) % n
                val ax = w[i * 2]; val ay = w[i * 2 + 1]
                val len = dist(ax, ay, w[j * 2], w[j * 2 + 1])
                val ux = (w[j * 2] - ax) / len; val uy = (w[j * 2 + 1] - ay) / len
                // outward: away from the middle of the walled town
                val so = if ((ax + ux * len * .5f - cx) * -uy + (ay + uy * len * .5f - cy) * ux >= 0f) 1f else -1f
                segs += WallSeg(ax, ay, ux, uy, len, so)
            }
            wallSegs.add(segs)
            val hit = FloatArray(2)
            for ((si, sg) in segs.withIndex()) {
                // gates where roads pass through
                val gaps = ArrayList<FloatArray>()
                for (e in city.roads.edges) {
                    if (!Geo.segHit(sg.ax, sg.ay, sg.ax + sg.ux * sg.len, sg.ay + sg.uy * sg.len, e.ax, e.ay, e.bx, e.by, hit)) continue
                    val sin = abs(sg.ux * e.uy - sg.uy * e.ux).coerceAtLeast(.35f)
                    val g = (e.cls.halfWidth + 1.2f) / sin
                    val t = hit[0] * sg.len
                    gaps += floatArrayOf(t - g, t + g)
                }
                gaps.sortBy { it[0] }
                val merged = ArrayList<FloatArray>()
                for (g in gaps) { val l = merged.lastOrNull(); if (l != null && g[0] <= l[1] + 2f) l[1] = max(l[1], g[1]) else merged += g }
                // curtain wall between the corners' bastions and the gates, in pieces short enough to sort
                var t = 0f
                val ends = ArrayList<Float>()
                for (g in merged) { ends += g[0]; ends += g[1] }
                val cuts = ArrayList<FloatArray>()
                for (g in merged) { if (g[0] > t) cuts += floatArrayOf(t, g[0]); t = g[1] }
                if (t < sg.len) cuts += floatArrayOf(t, sg.len)
                for (cIdx in cuts) {
                    val pieces = max(1, ceil((cIdx[1] - cIdx[0]) / 18f).toInt())
                    for (k in 0 until pieces) {
                        val a = cIdx[0] + (cIdx[1] - cIdx[0]) * k / pieces; val b2 = cIdx[0] + (cIdx[1] - cIdx[0]) * (k + 1) / pieces
                        addPiece(0, wi, si, sg, a, b2)
                    }
                    // lawn outside, wherever no road runs
                    var q = cIdx[0]
                    while (q < cIdx[1] - .5f) {
                        val q1 = min(cIdx[1], q + 4f)
                        val m = (q + q1) * .5f
                        val px = sg.ax + sg.ux * m + sg.nx * 7.5f; val py = sg.ay + sg.uy * m + sg.ny * 7.5f
                        if (!nearRoad(px, py, 4.5f) && !city.shore.anyWater(px, py)) lawn += floatArrayOf(si.toFloat(), q, q1, wi.toFloat())
                        q = q1
                    }
                }
                for (g in merged) addPiece(1, wi, si, sg, max(0f, g[0]), min(sg.len, g[1]))
                // a bastion on the corner at the segment's start
                val px = sg.ax; val py = sg.ay
                wallPieces += WallPiece(2, wi, si, 0f, 0f, px + sg.nx * 6f, py + sg.ny * 6f, 22f, city.shore.wet(px + sg.nx * 10f, py + sg.ny * 10f))
            }
        }
    }

    private fun addPiece(kind: Int, wi: Int, si: Int, sg: WallSeg, a: Float, b: Float) {
        if (b - a < .3f) return
        val m = (a + b) * .5f
        val x = sg.ax + sg.ux * m; val y = sg.ay + sg.uy * m
        wallPieces += WallPiece(kind, wi, si, a, b, x, y, (b - a) * .5f + 6f, city.shore.wet(x + sg.nx * 2f, y + sg.ny * 2f))
    }

    private fun nearRoad(x: Float, y: Float, pad: Float): Boolean {
        var hit = false
        city.roads.near(x, y, 30f) { id ->
            val e = city.roads.edges[id]
            if (!hit && Geo.distToSeg(x, y, e.ax, e.ay, e.bx, e.by) < e.cls.outer + pad) hit = true
        }
        return hit
    }

    // ------------------------------------------------------------------ ground pass

    private val px = FloatArray(8); private val py = FloatArray(8); private val pz = FloatArray(8)

    /**
     * Ground-pass layers drawn after the roads: lawns by the walls, then quay walls and bridge undersides facing the
     * camera. They reach below street level, so they read as standing above lower water.
     */
    fun drawGround(scene: Scene3D, bbox: FloatArray, mapMode: Boolean) {
        val camX = scene.proj.camX; val camY = scene.proj.camY
        // lawns outside the city walls
        scene.material = Mat.GRASS
        val grass = scene.mix(scene.lit(LAWN, 1f), scene.lit(0xFFF2F5F8.toInt(), 1f), scene.snow * .9f)
        for (l in lawn) {
            val sg = wallSegs.getOrNull(l[3].toInt())?.getOrNull(l[0].toInt()) ?: continue
            val a = l[1]; val b = l[2]
            val x0 = sg.ax + sg.ux * a; val y0 = sg.ay + sg.uy * a
            if (x0 < bbox[0] - 20f || x0 > bbox[2] + 20f || y0 < bbox[1] - 20f || y0 > bbox[3] + 20f) continue
            val x1 = sg.ax + sg.ux * b; val y1 = sg.ay + sg.uy * b
            scene.quad(x0 + sg.nx * 3.1f, y0 + sg.ny * 3.1f, .02f, x1 + sg.nx * 3.1f, y1 + sg.ny * 3.1f, .02f,
                x1 + sg.nx * 11f, y1 + sg.ny * 11f, .02f, x0 + sg.nx * 11f, y0 + sg.ny * 11f, .02f, grass)
        }
        // quay walls on the bank across the water
        scene.material = Mat.STONE
        for (k in 0 until quayCount) {
            val o = k * 7
            val x0 = quay[o]; val y0 = quay[o + 1]; val x1 = quay[o + 2]; val y1 = quay[o + 3]
            if (max(x0, x1) < bbox[0] || min(x0, x1) > bbox[2] || max(y0, y1) < bbox[1] || min(y0, y1) > bbox[3]) continue
            val lx = quay[o + 4]; val ly = quay[o + 5]; val d = quay[o + 6]
            // seen from the water side only
            if ((camX - (x0 + x1) * .5f) * lx + (camY - (y0 + y1) * .5f) * ly >= 0f) continue
            val sh = scene.sideShade(-lx, -ly)
            scene.quad(x0, y0, 0f, x1, y1, 0f, x1, y1, -d + .55f, x0, y0, -d + .55f, scene.lit(QUAY, sh))
            scene.quad(x0, y0, -d + .55f, x1, y1, -d + .55f, x1, y1, -d, x0, y0, -d, scene.lit(QUAY_WET, sh))
        }
        // the piers' faces down to the water (their decks go down before the streets, see drawPierDecks)
        scene.material = Mat.STONE
        for (k in 0 until piers.size / 3) {
            val e = city.roads.edges[piers[k * 3].toInt()]
            val a = piers[k * 3 + 1]; val b = piers[k * 3 + 2]
            val x0 = e.ax + e.ux * a; val y0 = e.ay + e.uy * a; val x1 = e.ax + e.ux * b; val y1 = e.ay + e.uy * b
            if (max(x0, x1) < bbox[0] - 20f || min(x0, x1) > bbox[2] + 20f || max(y0, y1) < bbox[1] - 20f || min(y0, y1) > bbox[3] + 20f) continue
            val w = e.cls.outer + .4f
            val nx = e.uy * w; val ny = -e.ux * w
            fun face(ax: Float, ay: Float, bx: Float, by: Float, ox: Float, oy: Float) {
                if ((camX - (ax + bx) * .5f) * ox + (camY - (ay + by) * .5f) * oy <= 0f) return
                val sh = scene.sideShade(ox, oy)
                scene.quad(ax, ay, .014f, bx, by, .014f, bx, by, -DEPTH + .55f, ax, ay, -DEPTH + .55f, scene.lit(QUAY, sh))
                scene.quad(ax, ay, -DEPTH + .55f, bx, by, -DEPTH + .55f, bx, by, -DEPTH, ax, ay, -DEPTH, scene.lit(QUAY_WET, sh))
            }
            face(x0 + nx, y0 + ny, x1 + nx, y1 + ny, e.uy, -e.ux)
            face(x1 - nx, y1 - ny, x0 - nx, y0 - ny, -e.uy, e.ux)
            if (b > e.len) face(x1 + nx, y1 + ny, x1 - nx, y1 - ny, e.ux, e.uy)
            if (a < 0f) face(x0 - nx, y0 - ny, x0 + nx, y0 + ny, -e.ux, -e.uy)
        }
        // bridges: the side facing the camera, with arches between the piers
        val water = ARCH_SHADOW
        for (br in bridges) {
            val e = city.roads.edges[br.edge]
            if (max(e.ax, e.bx) < bbox[0] - 20f || min(e.ax, e.bx) > bbox[2] + 20f || max(e.ay, e.by) < bbox[1] - 20f || min(e.ay, e.by) > bbox[3] + 20f) continue
            val nx = e.uy; val ny = -e.ux
            val mx = (e.ax + e.bx) * .5f; val my = (e.ay + e.by) * .5f
            val side = if ((camX - mx) * nx + (camY - my) * ny >= 0f) 1f else -1f
            val o = e.cls.outer * side
            val sh = scene.sideShade(nx * side, ny * side)
            val stone = scene.lit(BRIDGE_STONE, sh)
            val dark = scene.lit(BRIDGE_STONE, sh * .82f)
            val ring = scene.lit(c(0xFFD3CBB9), sh)
            fun x(s: Float, off: Float) = e.ax + e.ux * s + nx * off
            fun y(s: Float, off: Float) = e.ay + e.uy * s + ny * off
            for (i in br.wetA.indices) {
                val a = br.wetA[i]; val b = br.wetB[i]
                // the deck's edge beam
                scene.quad(x(a, o), y(a, o), .04f, x(b, o), y(b, o), .04f, x(b, o), y(b, o), -1.1f, x(a, o), y(a, o), -1.1f, dark)
                if (mapMode) continue
                for (k in br.supports.indices) {
                    val p = br.supports[k]
                    if (p < a - .1f || p >= b - .1f || k + 1 >= br.supports.size) continue
                    val q = br.supports[k + 1]
                    if (q > b + .1f) continue
                    arch(scene, p, q, o, ::x, ::y, stone, water, ring)
                }
                for (p in br.supports) {
                    if (p <= a + .5f || p >= b - .5f) continue
                    pier(scene, e.ux, e.uy, nx * side, ny * side, x(p, o), y(p, o), stone, dark, camX, camY)
                }
            }
        }
        scene.material = Mat.PLAIN
    }

    /** Piers where streets run on out over the water: their concrete decks, drawn over the water and under the streets. */
    fun drawPierDecks(scene: Scene3D, bbox: FloatArray) {
        scene.material = Mat.SIDEWALK
        val deck = scene.mix(scene.lit(PIER, 1f), scene.lit(0xFFF2F5F8.toInt(), 1f), scene.snow * .9f)
        for (k in 0 until piers.size / 3) {
            val e = city.roads.edges[piers[k * 3].toInt()]
            val a = piers[k * 3 + 1]; val b = piers[k * 3 + 2]
            val x0 = e.ax + e.ux * a; val y0 = e.ay + e.uy * a; val x1 = e.ax + e.ux * b; val y1 = e.ay + e.uy * b
            if (max(x0, x1) < bbox[0] - 20f || min(x0, x1) > bbox[2] + 20f || max(y0, y1) < bbox[1] - 20f || min(y0, y1) > bbox[3] + 20f) continue
            val w = e.cls.outer + .4f
            val nx = e.uy * w; val ny = -e.ux * w
            scene.quad(x0 + nx, y0 + ny, .014f, x1 + nx, y1 + ny, .014f, x1 - nx, y1 - ny, .014f, x0 - nx, y0 - ny, .014f, deck)
        }
    }

    private inline fun arch(scene: Scene3D, p: Float, q: Float, o: Float, x: (Float, Float) -> Float, y: (Float, Float) -> Float, stone: Int, shadow: Int, ring: Int) {
        val steps = 10
        val crown = -1.55f
        for (k in 0 until steps) {
            val u0 = p + (q - p) * k / steps; val u1 = p + (q - p) * (k + 1) / steps
            val z0 = archZ(k.toFloat() / steps, crown); val z1 = archZ((k + 1f) / steps, crown)
            // spandrel, the arch ring of lighter voussoirs, then the shadow under the arch
            scene.quad(x(u0, o), y(u0, o), -1.1f, x(u1, o), y(u1, o), -1.1f, x(u1, o), y(u1, o), z1 + .35f, x(u0, o), y(u0, o), z0 + .35f, stone)
            scene.quad(x(u0, o), y(u0, o), z0 + .35f, x(u1, o), y(u1, o), z1 + .35f, x(u1, o), y(u1, o), z1, x(u0, o), y(u0, o), z0, ring)
            scene.quad(x(u0, o), y(u0, o), z0, x(u1, o), y(u1, o), z1, x(u1, o), y(u1, o), -DEPTH, x(u0, o), y(u0, o), -DEPTH, shadow)
        }
    }

    /** Height of an arch's underside [f] of the way across, springing just above the water. */
    private fun archZ(f: Float, crown: Float): Float {
        val u = (f * 2f - 1f) * .97f
        return -DEPTH + .15f + (crown + DEPTH - .15f) * sqrt(max(0f, 1f - u * u))
    }

    /** A pier standing out of the bridge's face with a pointed cutwater, its faces toward the camera. */
    private fun pier(scene: Scene3D, ux: Float, uy: Float, nx: Float, ny: Float, bx: Float, by: Float, stone: Int, dark: Int, camX: Float, camY: Float) {
        val hw = 1.3f; val out = 1f
        val ax = bx - ux * hw; val ay = by - uy * hw; val cx = bx + ux * hw; val cy = by + uy * hw
        val ox = nx * out; val oy = ny * out
        // front face
        scene.quad(ax + ox, ay + oy, .04f, cx + ox, cy + oy, .04f, cx + ox, cy + oy, -DEPTH, ax + ox, ay + oy, -DEPTH, stone)
        // the side facing the camera along the bridge
        val s = if ((camX - bx) * ux + (camY - by) * uy >= 0f) 1f else -1f
        val ex = bx + ux * hw * s; val ey = by + uy * hw * s
        scene.quad(ex, ey, .04f, ex + ox, ey + oy, .04f, ex + ox, ey + oy, -DEPTH, ex, ey, -DEPTH, dark)
        // cutwater: a wedge pointing out into the stream, capped below the deck
        val tx = bx + nx * (out + 1.6f); val ty = by + ny * (out + 1.6f)
        val zTop = -1.75f
        scene.quad(ax + ox, ay + oy, zTop, tx, ty, zTop, tx, ty, -DEPTH, ax + ox, ay + oy, -DEPTH, if (s < 0) stone else dark)
        scene.quad(tx, ty, zTop, cx + ox, cy + oy, zTop, cx + ox, cy + oy, -DEPTH, tx, ty, -DEPTH, if (s > 0) stone else dark)
        px[0] = ax + ox; py[0] = ay + oy; pz[0] = zTop
        px[1] = cx + ox; py[1] = cy + oy; pz[1] = zTop
        px[2] = tx; py[2] = ty; pz[2] = zTop
        scene.poly(3, px, py, pz, scene.lit(BRIDGE_STONE, 1.05f))
    }

    // ------------------------------------------------------------------ bridge parapets

    /** A stretch of bridge parapet with pilasters over the piers and lamps on every other one. */
    fun drawRailing(scene: Scene3D, i: Int, d: Float) {
        val r = railings[i]
        val br = bridges[r.bridge]
        val e = city.roads.edges[br.edge]
        val near = d < 110f
        val night = scene.daylight < .55f
        val y = e.cls.outer - .2f
        scene.setFrame(e.ax, e.ay, e.ux, e.uy)
        try {
            if (steel) {
                scene.material = Mat.CONCRETE
                for (sd in -1..1 step 2) scene.box(r.s0, sd * y - .2f, 0f, r.s1, sd * y + .2f, .3f, BRIDGE_STONE, BRIDGE_STONE)
                scene.material = Mat.METAL
                for (sd in -1..1 step 2) {
                    scene.box(r.s0, sd * y - .06f, 1.02f, r.s1, sd * y + .06f, 1.12f, STEEL, STEEL)
                    if (near) {
                        scene.box(r.s0, sd * y - .03f, .62f, r.s1, sd * y + .03f, .68f, STEEL, STEEL)
                        var s = ceil(r.s0 / 2.4f) * 2.4f
                        while (s < r.s1) { scene.box(s - .05f, sd * y - .05f, .3f, s + .05f, sd * y + .05f, 1.05f, STEEL, STEEL); s += 2.4f }
                    } else scene.quad(r.s0, sd * y, .3f, r.s1, sd * y, .3f, r.s1, sd * y, 1.02f, r.s0, sd * y, 1.02f, scene.withAlpha(scene.lit(STEEL, 1f), .55f))
                }
            } else {
                scene.material = Mat.STONE
                for (sd in -1..1 step 2) {
                    scene.box(r.s0, sd * y - .17f, 0f, r.s1, sd * y + .17f, .98f, BRIDGE_STONE, BRIDGE_STONE)
                    scene.box(r.s0, sd * y - .24f, .98f, r.s1, sd * y + .24f, 1.1f, scene.lit(BRIDGE_STONE, 1.08f), BRIDGE_STONE)
                }
            }
            scene.material = Mat.STONE
            for ((k, p) in br.supports.withIndex()) {
                if (p < r.s0 || p >= r.s1) continue
                for (sd in -1..1 step 2) {
                    scene.box(p - .45f, sd * y - .42f, 0f, p + .45f, sd * y + .42f, 1.45f, BRIDGE_STONE, BRIDGE_STONE)
                    if (k % 2 == 0 && d < 260f) lampStandard(scene, p, sd * y, night, near)
                }
            }
        } finally { scene.material = Mat.PLAIN; scene.clearFrame() }
    }

    /** A cast-iron lamp standard on a pier's pilaster: a moulded base, a post with a collar, an arm with two globes and one on top. */
    private fun lampStandard(scene: Scene3D, s: Float, o: Float, night: Boolean, near: Boolean) {
        val globe = if (night) c(0xFFFFF3C4) else c(0xFFE9E6DA)
        scene.box(s - .2f, o - .2f, 1.45f, s + .2f, o + .2f, 2.15f, LAMP, LAMP)
        scene.box(s - .085f, o - .085f, 2.15f, s + .085f, o + .085f, 5.7f, LAMP, LAMP)
        if (!near) { scene.box(s - .24f, o - .24f, 5.3f, s + .24f, o + .24f, 5.85f, LAMP, globe); return }
        scene.box(s - .15f, o - .15f, 3.2f, s + .15f, o + .15f, 3.42f, LAMP, LAMP)
        scene.box(s - .9f, o - .05f, 5.12f, s + .9f, o + .05f, 5.24f, LAMP, LAMP)
        for (gx in -1..1 step 2) {
            scene.box(s + gx * .9f - .04f, o - .04f, 4.98f, s + gx * .9f + .04f, o + .04f, 5.12f, LAMP, LAMP)
            scene.blob(s + gx * .9f, o, 4.74f, .27f, globe, 8)
        }
        scene.blob(s, o, 5.98f, .3f, globe, 8)
        scene.box(s - .03f, o - .03f, 6.25f, s + .03f, o + .03f, 6.55f, LAMP, LAMP)
    }

    // ------------------------------------------------------------------ city walls

    fun drawWall(scene: Scene3D, i: Int, d: Float) {
        val p = wallPieces[i]
        val segs = wallSegs.getOrNull(p.wall) ?: return
        val sg = segs[p.seg]
        val near = d < 140f
        when (p.kind) {
            2 -> { bastion(scene, segs, p.seg, p.wet, near); return }
        }
        // local frame: x along the wall, y outward
        scene.setFrame(sg.ax, sg.ay, sg.ux, sg.uy)
        val flip = sg.so
        try {
            if (p.kind == 0) curtain(scene, p.t0, p.t1, flip, if (p.wet) -DEPTH else 0f, near)
            else gate(scene, p.t0, p.t1, flip, near)
        } finally { scene.material = Mat.PLAIN; scene.clearFrame() }
    }

    /** Writes a quad in the wall's frame where y is "outward" offset [o] times [f] (the frame's y may point inward). */
    private fun wq(scene: Scene3D, f: Float, x0: Float, o0: Float, z0: Float, x1: Float, o1: Float, z1: Float, x2: Float, o2: Float, z2: Float, x3: Float, o3: Float, z3: Float, color: Int) =
        scene.quad(x0, o0 * f, z0, x1, o1 * f, z1, x2, o2 * f, z2, x3, o3 * f, z3, color)

    private fun curtain(scene: Scene3D, a: Float, b: Float, f: Float, z0: Float, near: Boolean) {
        val camO = scene.camLY * f
        val top = 6.2f
        scene.material = Mat.STONE
        val outSh = scene.sideShade(0f, f); val inSh = scene.sideShade(0f, -f)
        // battered outer face, plain inner face
        if (camO > 2.3f || scene.gpu != null) wq(scene, f, a, 3f, z0, b, 3f, z0, b, 2.3f, top, a, 2.3f, top, scene.lit(RAMPART, outSh))
        if (camO < -3f || scene.gpu != null) wq(scene, f, a, -3f, z0, b, -3f, z0, b, -3f, top, a, -3f, top, scene.lit(RAMPART, inSh))
        // ends next to gates and bastions
        if (scene.gpu != null || scene.camLX < a) wq(scene, f, a, -3f, z0, a, 3f, z0, a, 2.3f, top, a, -3f, top, scene.lit(RAMPART, scene.sideShade(-1f, 0f)))
        if (scene.gpu != null || scene.camLX > b) wq(scene, f, b, -3f, z0, b, 3f, z0, b, 2.3f, top, b, -3f, top, scene.lit(RAMPART, scene.sideShade(1f, 0f)))
        // the rampart walk is grown over
        scene.material = Mat.GRASS
        wq(scene, f, a, -3f, top, b, -3f, top, b, 1.1f, top, a, 1.1f, top, scene.lit(scene.snowy(RAMPART_TOP), 1f))
        scene.material = Mat.STONE
        // low wall on the town side, parapet on the outside
        scene.box(a, min(-3f * f, -2.7f * f), top, b, max(-3f * f, -2.7f * f), top + .55f, RAMPART_LIGHT, RAMPART)
        scene.box(a, min(1.1f * f, 2.3f * f), top, b, max(1.1f * f, 2.3f * f), top + 1f, scene.snowy(RAMPART_LIGHT), RAMPART)
        if (near) {
            // merlons
            var x = ceil(a / 2.6f) * 2.6f
            while (x + 1.3f < b) {
                scene.box(x, min(1.15f * f, 2.25f * f), top + 1f, x + 1.3f, max(1.15f * f, 2.25f * f), top + 1.65f, scene.snowy(RAMPART_LIGHT), RAMPART)
                x += 2.6f
            }
        }
    }

    private fun gate(scene: Scene3D, a: Float, b: Float, f: Float, near: Boolean) {
        scene.material = Mat.STONE
        val y0 = -3.6f; val y1 = 3.6f
        val lo = min(y0 * f, y1 * f); val hi = max(y0 * f, y1 * f)
        // twin towers either side of the road
        for (side in 0..1) {
            val t0 = if (side == 0) a - 4.6f else b; val t1 = t0 + 4.6f
            scene.box(t0, lo, 0f, t1, hi, 9f, scene.snowy(RAMPART_LIGHT), GATE)
            scene.box(t0 - .2f, lo - .2f, 9f, t1 + .2f, hi + .2f, 9.5f, scene.snowy(RAMPART_LIGHT), RAMPART_LIGHT)
            if (near) for (k in 0..1) {
                val x = t0 + .3f + k * (t1 - t0 - 1.5f)
                scene.box(x, lo, 9.5f, x + 1.2f, lo + 1f, 10.2f, RAMPART_LIGHT, RAMPART_LIGHT)
                scene.box(x, hi - 1f, 9.5f, x + 1.2f, hi, 10.2f, RAMPART_LIGHT, RAMPART_LIGHT)
            }
        }
        // the arch over the road: spandrels on both faces with a lighter ring, and a walk on top
        val steps = 8
        val crown = 6.6f; val spring = 4.6f
        val gs = scene.lit(GATE, scene.sideShade(0f, f)); val gi = scene.lit(GATE, scene.sideShade(0f, -f))
        val ring = scene.lit(RAMPART_LIGHT, 1.05f)
        val camO = scene.camLY * f
        for (k in 0 until steps) {
            val u0 = a + (b - a) * k / steps; val u1 = a + (b - a) * (k + 1) / steps
            val z0 = spring + (crown - spring) * sqrt(max(0f, 1f - sq(2f * k / steps - 1f)))
            val z1 = spring + (crown - spring) * sqrt(max(0f, 1f - sq(2f * (k + 1) / steps - 1f)))
            for (os in -1..1 step 2) { val o = os * 3.4f
                if (scene.gpu == null && ((o > 0f && camO < o) || (o < 0f && camO > o))) continue
                wq(scene, f, u0, o, z0 + .55f, u1, o, z1 + .55f, u1, o, 8.6f, u0, o, 8.6f, if (o > 0f) gs else gi)
                wq(scene, f, u0, o * 1.003f, z0, u1, o * 1.003f, z1, u1, o * 1.003f, z1 + .55f, u0, o * 1.003f, z0 + .55f, ring)
            }
        }
        wq(scene, f, a, -3.4f, 8.6f, b, -3.4f, 8.6f, b, 3.4f, 8.6f, a, 3.4f, 8.6f, scene.lit(scene.snowy(RAMPART_LIGHT), 1f))
        // a crest over the outer face
        val m = (a + b) * .5f
        scene.box(m - 2.2f, min(3.3f * f, 3.8f * f), 8.6f, m + 2.2f, max(3.3f * f, 3.8f * f), 10.4f, RAMPART_LIGHT, GATE)
        px[0] = m - 2.2f; py[0] = 3.81f * f; pz[0] = 10.4f
        px[1] = m + 2.2f; py[1] = 3.81f * f; pz[1] = 10.4f
        px[2] = m; py[2] = 3.81f * f; pz[2] = 11.6f
        scene.poly(3, px, py, pz, gs)
    }

    private fun sq(v: Float) = v * v

    /** An arrowhead bastion on the corner where segment [si] starts, with a sentry box on its point. */
    private fun bastion(scene: Scene3D, segs: List<WallSeg>, si: Int, wet: Boolean, near: Boolean) {
        val cur = segs[si]; val prev = segs[(si - 1 + segs.size) % segs.size]
        val vx = cur.ax; val vy = cur.ay
        var bx = cur.nx + prev.nx; var by = cur.ny + prev.ny
        val bl = sqrt(bx * bx + by * by).coerceAtLeast(1e-4f); bx /= bl; by /= bl
        val z0 = if (wet) -DEPTH else 0f
        val top = 6.8f
        // base triangle: back along the previous wall, the point outward, on along this wall
        val ax = vx - prev.ux * 13f; val ay = vy - prev.uy * 13f
        val tx = vx + bx * 19f; val ty = vy + by * 19f
        val cx = vx + cur.ux * 13f; val cy = vy + cur.uy * 13f
        val mx = (ax + tx + cx) / 3f; val my = (ay + ty + cy) / 3f
        fun ins(v: Float, m: Float) = v + (m - v) * .07f
        scene.material = Mat.STONE
        val xs = floatArrayOf(ax, tx, cx); val ys = floatArrayOf(ay, ty, cy)
        for (k in 0 until 3) {
            val j = (k + 1) % 3
            val ex = xs[j] - xs[k]; val ey = ys[j] - ys[k]
            var nx = ey; var ny = -ex
            val l = sqrt(nx * nx + ny * ny).coerceAtLeast(1e-4f); nx /= l; ny /= l
            if ((xs[k] - mx) * nx + (ys[k] - my) * ny < 0f) { nx = -nx; ny = -ny }
            if (scene.gpu == null && (scene.proj.camX - xs[k]) * nx + (scene.proj.camY - ys[k]) * ny <= 0f) continue
            scene.quad(xs[k], ys[k], z0, xs[j], ys[j], z0, ins(xs[j], mx), ins(ys[j], my), top, ins(xs[k], mx), ins(ys[k], my), top,
                scene.lit(RAMPART, scene.sideShade(nx, ny)))
        }
        scene.material = Mat.GRASS
        for (k in 0 until 3) { px[k] = ins(xs[k], mx); py[k] = ins(ys[k], my); pz[k] = top }
        scene.poly(3, px, py, pz, scene.lit(scene.snowy(RAMPART_TOP), 1f))
        scene.material = Mat.STONE
        // parapet along the two outer faces
        for (k in 0..1) {
            val x0 = ins(xs[k], mx); val y0 = ins(ys[k], my); val x1 = ins(xs[k + 1], mx); val y1 = ins(ys[k + 1], my)
            val ix = (mx - (x0 + x1) * .5f); val iy = (my - (y0 + y1) * .5f)
            val il = sqrt(ix * ix + iy * iy).coerceAtLeast(1e-4f)
            val ox = ix / il * 1f; val oy = iy / il * 1f
            scene.quad(x0, y0, top, x1, y1, top, x1, y1, top + 1f, x0, y0, top + 1f, scene.lit(RAMPART_LIGHT, .9f))
            scene.quad(x0, y0, top + 1f, x1, y1, top + 1f, x1 + ox, y1 + oy, top + 1f, x0 + ox, y0 + oy, top + 1f, scene.lit(scene.snowy(RAMPART_LIGHT), 1f))
        }
        // garita: a round sentry box with a domed cap on the point
        val gx = ins(tx, mx) + (mx - tx) * .06f; val gy = ins(ty, my) + (my - ty) * .06f
        scene.cylinder(gx, gy, 1.05f, top, top + 2.3f, if (near) 8 else 6, RAMPART_LIGHT)
        scene.cone(gx, gy, 1.25f, top + 2.3f, top + 3.6f, if (near) 8 else 6, c(0xFF7A6A55))
        scene.material = Mat.PLAIN
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float { val dx = bx - ax; val dy = by - ay; return sqrt(dx * dx + dy * dy) }
}

package com.hustlecity.game.render

import com.hustlecity.game.data.c
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Geo
import com.hustlecity.game.world.Rail
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Elevated railways: a concrete viaduct along each [Rail], on piers that keep out of junctions (a column in the median
 * of the road below, or a portal frame over it), island-platform stations, and trains of a few cars running station
 * to station both ways. Spans between piers are the drawables; trains are drawn every frame.
 */
class Railways(private val city: City) {
    companion object {
        const val PIER_TOP = 6.55f
        const val GIRDER_Z = 7.25f
        const val SLAB_Z = 8.15f
        const val DECK = 8.5f
        const val PARAPET = 9.45f
        private const val HW = 4f
        private const val HW_ST = 5.1f
        private const val TRACK = 1.75f
        private const val TRACK_ST = 3.3f
        private const val STEP = 4f
        private const val GAP = 1.2f
        private const val V = 12.5f
        private const val ACC = .8f

        private val CONCRETE = c(0xFFB9B6AE)
        private val CONCRETE_DARK = c(0xFF8F8C86)
        private val BALLAST = c(0xFF6B655D)
        private val SLEEPER = c(0xFF9A948A)
        private val RAIL = c(0xFF8C9096)
        private val STEEL = c(0xFF5E6A73)
        private val POLE = c(0xFF4A5056)
        private val BODY = c(0xFFD9DEE2)
        private val ROOF = c(0xFFA9AFB5)
        private val UNDER = c(0xFF34383D)
        private val WINDOW = c(0xFF26313D)
        private val PLATFORM = c(0xFFCFCAC0)
        private val CANOPY = c(0xFFE9ECEE)
    }

    /** One line's smoothed centre line, sampled every few meters, with its piers, stations and trains. */
    inner class Line(val rail: Rail, val index: Int) {
        val loop = rail.loop
        var n = 0
        var x = FloatArray(0); var y = FloatArray(0); var s = FloatArray(0)
        var tx = FloatArray(0); var ty = FloatArray(0)
        var len = 0f
        val piers = ArrayList<Pier>()
        val stations = ArrayList<Float>()
        var platformHalf = 0f
        var cars = 4
        var carLen = 15f
        val circuits = ArrayList<Circuit>()
        val color = rail.color

        /** Writes x, y, tangent x, tangent y at [q] meters along the line into [out]. */
        fun at(q: Float, out: FloatArray) {
            val qq = if (loop) ((q % len) + len) % len else q.coerceIn(0f, len)
            var lo = 0; var hi = n - 1
            while (hi - lo > 1) { val m = (lo + hi) ushr 1; if (s[m] <= qq) lo = m else hi = m }
            val t = ((qq - s[lo]) / max(1e-4f, s[hi] - s[lo])).coerceIn(0f, 1f)
            out[0] = x[lo] + (x[hi] - x[lo]) * t; out[1] = y[lo] + (y[hi] - y[lo]) * t
            var ux = tx[lo] + (tx[hi] - tx[lo]) * t; var uy = ty[lo] + (ty[hi] - ty[lo]) * t
            val l = sqrt(ux * ux + uy * uy).coerceAtLeast(1e-5f); ux /= l; uy /= l
            out[2] = ux; out[3] = uy
        }

        /** Signed distance from [a] to [b] along the line (the short way round on a loop). */
        fun delta(a: Float, b: Float): Float {
            var d = b - a
            if (loop) { if (d > len / 2) d -= len else if (d < -len / 2) d += len }
            return d
        }

        /** 1 on a station platform, easing to 0 over 30 m beyond it: the tracks spread round an island platform. */
        fun spread(q: Float): Float {
            var best = 0f
            for (st in stations) {
                val d = abs(delta(st, q)) - platformHalf
                val v = if (d <= 0f) 1f else if (d < 30f) { val t = 1f - d / 30f; t * t * (3f - 2f * t) } else 0f
                if (v > best) best = v
            }
            return best
        }

        fun halfWidth(q: Float) = HW + (HW_ST - HW) * spread(q)
        fun track(q: Float) = TRACK + (TRACK_ST - TRACK) * spread(q)
        fun onPlatform(q: Float) = stations.any { abs(delta(it, q)) <= platformHalf }
    }

    /** A pier at [q]: a single column, or a portal frame with columns at lateral offsets [a] and [b] (left positive). */
    class Pier(val q: Float, val bent: Boolean, val a: Float, val b: Float, val wet: Boolean)

    /** Trains going round one circuit of stops: [legFrom] → [legTo] in order, then back to the first. */
    class Circuit(val line: Line, val side: Int, val stops: FloatArray, val open: Boolean, val trains: Int) {
        val legFrom = ArrayList<Float>(); val legDist = ArrayList<Float>(); val legDir = ArrayList<Int>(); val legOut = ArrayList<Boolean>()
        val legTravel = ArrayList<Float>(); val legEnd = ArrayList<Float>()
        var period = 1f
    }

    /** A span of viaduct between two piers: the drawable unit. */
    class Span(val line: Int, val q0: Float, val q1: Float, val pier: Int, val cx: Float, val cy: Float, val r: Float)

    val lines = ArrayList<Line>()
    val spans = ArrayList<Span>()
    private val tmp = FloatArray(4)

    init {
        for ((i, r) in city.rails.withIndex()) {
            val l = Line(r, i)
            buildPath(l)
            if (l.n < 2) continue
            l.cars = if (l.loop) 6 else 4
            l.carLen = if (l.loop) 16f else 14f
            placeStations(l)
            placePiers(l)
            makeSpans(l)
            makeCircuits(l)
            lines += l
        }
    }

    // ------------------------------------------------------------------ layout

    private fun buildPath(l: Line) {
        var p = l.rail.pts
        val closed = l.loop
        if (closed && p.size >= 4 && abs(p[0] - p[p.size - 2]) < .01f && abs(p[1] - p[p.size - 1]) < .01f) p = p.copyOf(p.size - 2)
        // corner cutting twice: smooth curves that stay within a meter or so of the plan
        repeat(2) { p = chaikin(p, closed) }
        val m = p.size / 2
        var total = 0f
        val seg = FloatArray(if (closed) m else m - 1)
        for (i in seg.indices) { val j = (i + 1) % m; seg[i] = dist(p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1]); total += seg[i] }
        val cnt = max(2, ceil(total / STEP).toInt())
        val step = total / cnt
        l.n = cnt + 1
        l.x = FloatArray(l.n); l.y = FloatArray(l.n); l.s = FloatArray(l.n); l.tx = FloatArray(l.n); l.ty = FloatArray(l.n)
        var si = 0; var acc = 0f
        for (k in 0..cnt) {
            val want = k * step
            while (si < seg.size - 1 && acc + seg[si] < want) { acc += seg[si]; si++ }
            val j = (si + 1) % m
            val t = ((want - acc) / max(seg[si], 1e-4f)).coerceIn(0f, 1f)
            l.x[k] = p[si * 2] + (p[j * 2] - p[si * 2]) * t
            l.y[k] = p[si * 2 + 1] + (p[j * 2 + 1] - p[si * 2 + 1]) * t
            l.s[k] = want
        }
        if (closed) { l.x[cnt] = l.x[0]; l.y[cnt] = l.y[0] }
        l.len = total
        for (k in 0 until l.n) {
            val a = if (k == 0) (if (closed) cnt - 1 else 0) else k - 1
            val b = if (k == l.n - 1) (if (closed) 1 else k) else k + 1
            val dx = l.x[b] - l.x[a]; val dy = l.y[b] - l.y[a]
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            l.tx[k] = dx / d; l.ty[k] = dy / d
        }
    }

    private fun chaikin(p: FloatArray, closed: Boolean): FloatArray {
        val m = p.size / 2
        val out = ArrayList<Float>(m * 4)
        if (!closed) { out += p[0]; out += p[1] }
        val segs = if (closed) m else m - 1
        for (i in 0 until segs) {
            val j = (i + 1) % m
            val ax = p[i * 2]; val ay = p[i * 2 + 1]; val bx = p[j * 2]; val by = p[j * 2 + 1]
            out += ax * .75f + bx * .25f; out += ay * .75f + by * .25f
            out += ax * .25f + bx * .75f; out += ay * .25f + by * .75f
        }
        if (!closed) { out += p[(m - 1) * 2]; out += p[(m - 1) * 2 + 1] }
        return out.toFloatArray()
    }

    private fun placeStations(l: Line) {
        val lt = l.cars * l.carLen + (l.cars - 1) * GAP
        l.platformHalf = lt / 2 + 4f
        if (l.loop) {
            val k = max(3, (l.len / 520f).roundToInt())
            for (i in 0 until k) l.stations += (i + .5f) * l.len / k
        } else {
            val a = l.platformHalf + 16f; val b = l.len - l.platformHalf - 16f
            if (b <= a) { l.stations += l.len / 2; return }
            val k = max(2, ((b - a) / 520f).roundToInt() + 1)
            for (i in 0 until k) l.stations += a + (b - a) * i / (k - 1)
        }
    }

    private var pierA = 0f; private var pierB = 0f

    /** 0 where no pier can stand, 1 for a column (clear ground or a road's median), 2 for a portal frame over a road. */
    private fun pierKind(l: Line, q: Float): Int {
        l.at(q, tmp)
        val px = tmp[0]; val py = tmp[1]; val tx = tmp[2]; val ty = tmp[3]
        var bad = false; var bent = false
        val g = city.roads
        g.near(px, py, 30f) { id ->
            val e = g.edges[id]
            val d = Geo.distToSeg(px, py, e.ax, e.ay, e.bx, e.by)
            if (d >= e.cls.halfWidth + 1.4f) return@near
            val par = abs(e.ux * tx + e.uy * ty)
            if (par < .94f) { bad = true; return@near }
            if (e.cls.rank >= 2 && d < .9f) return@near
            if (!bent) {
                // the frame's columns stand on the road's sidewalks
                val t = ((px - e.ax) * e.ux + (py - e.ay) * e.uy).coerceIn(0f, e.len)
                val qx = e.ax + e.ux * t - px; val qy = e.ay + e.uy * t - py
                val cOff = qx * ty - qy * tx
                val w = e.cls.halfWidth + e.cls.sidewalk * .55f
                pierA = cOff - w; pierB = cOff + w
                bent = true
            }
        }
        return if (bad) 0 else if (bent) 2 else 1
    }

    private fun placePiers(l: Line) {
        val spacing = 26f
        var q = if (l.loop) 0f else 3f
        val end = if (l.loop) l.len - 14f else l.len - 3f
        var last = -1e9f
        val shifts = floatArrayOf(0f, -3f, 3f, -6f, 6f, -9f, 9f, -12f, 12f)
        while (q <= end) {
            var placed = false
            for (sh in shifts) {
                val qq = q + sh
                if (qq - last < 14f || qq < 0f || qq > l.len) continue
                if (pierKind(l, qq) == 1) { addPier(l, qq, false); last = qq; placed = true; break }
            }
            if (!placed && q - last >= 14f && pierKind(l, q) == 2) { addPier(l, q, true); last = q; placed = true }
            q = (if (placed) last else q) + spacing
        }
        if (!l.loop && l.len - last > 10f) addPier(l, l.len - 3f, false)
    }

    private fun addPier(l: Line, q: Float, bent: Boolean) {
        l.at(q, tmp)
        l.piers += Pier(q, bent, pierA, pierB, city.shore.wet(tmp[0], tmp[1]))
    }

    private fun makeSpans(l: Line) {
        val ps = l.piers
        fun add(q0: Float, q1: Float, pier: Int) {
            if (q1 - q0 < .5f) return
            l.at((q0 + q1) * .5f, tmp)
            spans += Span(l.index, q0, q1, pier, tmp[0], tmp[1], (q1 - q0) * .5f + 6f)
        }
        if (ps.isEmpty()) { add(0f, l.len, -1); return }
        if (!l.loop) add(0f, ps[0].q, -1)
        for (i in ps.indices) {
            val next = if (i + 1 < ps.size) ps[i + 1].q else if (l.loop) ps[0].q + l.len else l.len
            add(ps[i].q, next, i)
        }
    }

    private fun makeCircuits(l: Line) {
        val st = l.stations.sorted().toFloatArray()
        if (l.loop) {
            val trains = max(2, (l.len / 1000f).roundToInt())
            for (side in intArrayOf(1, -1)) {
                val c = Circuit(l, side, st, false, trains)
                val m = st.size
                for (i in 0 until m) {
                    val a = if (side > 0) st[i] else st[m - 1 - i]
                    val b = if (side > 0) st[(i + 1) % m] else st[(m - 2 - i + m) % m]
                    val d = ((if (side > 0) b - a else a - b) % l.len + l.len) % l.len
                    addLeg(c, a, d, side, true, 18f)
                }
                finish(c); l.circuits += c
            }
        } else {
            val trains = (2 * l.len / 1100f).roundToInt().coerceIn(2, 4)
            val c = Circuit(l, 1, st, true, trains)
            for (i in 0 until st.size - 1) addLeg(c, st[i], st[i + 1] - st[i], 1, true, if (i == st.size - 2) 26f else 16f)
            for (i in st.size - 1 downTo 1) addLeg(c, st[i], st[i] - st[i - 1], -1, false, if (i == 1) 26f else 16f)
            finish(c); l.circuits += c
        }
    }

    private fun addLeg(c: Circuit, from: Float, d: Float, dir: Int, out: Boolean, dwell: Float) {
        c.legFrom += from; c.legDist += d; c.legDir += dir; c.legOut += out
        val travel = travelTime(d)
        c.legTravel += travel
        c.legEnd += (c.legEnd.lastOrNull() ?: 0f) + travel + dwell
    }

    private fun finish(c: Circuit) { c.period = c.legEnd.lastOrNull()?.coerceAtLeast(1f) ?: 1f }

    private fun travelTime(d: Float): Float {
        val da = V * V / (2 * ACC)
        return if (d < 2 * da) 2 * sqrt(d / ACC) else 2 * V / ACC + (d - 2 * da) / V
    }

    /** Distance covered [t] seconds into a [d] meter run that starts and ends at rest. */
    private fun travelled(d: Float, t: Float): Float {
        val da = V * V / (2 * ACC)
        val total = travelTime(d)
        if (t >= total) return d
        if (d < 2 * da) {
            val half = total / 2
            return if (t < half) .5f * ACC * t * t else d - .5f * ACC * (total - t) * (total - t)
        }
        val ta = V / ACC
        return when {
            t < ta -> .5f * ACC * t * t
            t > total - ta -> d - .5f * ACC * (total - t) * (total - t)
            else -> da + V * (t - ta)
        }
    }

    // ------------------------------------------------------------------ trains

    /** Cars this frame: position, forward unit vector, line, place along the line, and flags (1 front, 2 back). */
    var carCount = 0; private set
    private var carX = FloatArray(64); private var carY = FloatArray(64)
    private var carFx = FloatArray(64); private var carFy = FloatArray(64)
    private var carQ = FloatArray(64); private var carLine = IntArray(64); private var carFlags = IntArray(64)
    private var carTime = Float.NaN

    fun carX(i: Int) = carX[i]
    fun carY(i: Int) = carY[i]

    /** Places every car for time [t] (seconds); cheap to call again for the same time. */
    fun update(t: Float) {
        if (t == carTime) return
        carTime = t
        carCount = 0
        for (l in lines) for (cIdx in l.circuits.indices) {
            val c = l.circuits[cIdx]
            for (k in 0 until c.trains) {
                val tau = ((t + k * c.period / c.trains + l.index * 37f + cIdx * 11f) % c.period + c.period) % c.period
                var leg = 0
                while (leg < c.legEnd.size - 1 && tau > c.legEnd[leg]) leg++
                val start = if (leg == 0) 0f else c.legEnd[leg - 1]
                val dir = c.legDir[leg]
                val qc = c.legFrom[leg] + dir * travelled(c.legDist[leg], tau - start)
                placeTrain(l, c, qc, dir, c.legOut[leg])
            }
        }
    }

    private val pa = FloatArray(4); private val pb = FloatArray(4)

    private fun placeTrain(l: Line, c: Circuit, qc: Float, dir: Int, out: Boolean) {
        val pitch = l.carLen + GAP
        for (j in 0 until l.cars) {
            val q = qc + (j - (l.cars - 1) * .5f) * pitch * dir
            if (!l.loop && (q < 0f || q > l.len)) continue
            val half = l.carLen * .5f - 2f
            val qf = q + half * dir; val qb = q - half * dir
            l.at(qf, pa); l.at(qb, pb)
            val lf = lateral(l, c, qf, out); val lb = lateral(l, c, qb, out)
            // left of travel along the line is (ty, -tx)
            val fx = pa[0] + pa[3] * lf; val fy = pa[1] - pa[2] * lf
            val bx = pb[0] + pb[3] * lb; val by = pb[1] - pb[2] * lb
            var dx = fx - bx; var dy = fy - by
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f); dx /= d; dy /= d
            if (carCount == carX.size) grow()
            carX[carCount] = (fx + bx) * .5f; carY[carCount] = (fy + by) * .5f
            carFx[carCount] = dx; carFy[carCount] = dy
            carQ[carCount] = q; carLine[carCount] = l.index
            carFlags[carCount] = (if (j == l.cars - 1) 1 else 0) or (if (j == 0) 2 else 0)
            carCount++
        }
    }

    /** Which track a car at [q] is on: a loop keeps to its own side, a line crosses over at each terminus. */
    private fun lateral(l: Line, c: Circuit, q: Float, out: Boolean): Float {
        var side = c.side.toFloat()
        if (c.open) {
            val st = c.stops
            side = if (out) {
                val x0 = st[0] + l.platformHalf + 6f
                ramp((q - x0) / 30f) * 2f - 1f
            } else {
                val x1 = st[st.size - 1] - l.platformHalf - 6f
                ramp((q - x1 + 30f) / 30f) * 2f - 1f
            }
        }
        return side * l.track(q)
    }

    private fun ramp(t: Float): Float { val u = t.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun grow() {
        val n = carX.size * 2
        carX = carX.copyOf(n); carY = carY.copyOf(n); carFx = carFx.copyOf(n); carFy = carFy.copyOf(n)
        carQ = carQ.copyOf(n); carLine = carLine.copyOf(n); carFlags = carFlags.copyOf(n)
    }

    /** One train car, in its own frame (x forward). [near] adds windows, doors and roof gear. */
    fun drawCar(scene: Scene3D, i: Int, near: Boolean) {
        if (i >= carCount) return
        val l = lines.firstOrNull { it.index == carLine[i] } ?: return
        scene.setFrame(carX[i], carY[i], carFx[i], carFy[i])
        try { carLocal(scene, l, carFlags[i], near) } finally { scene.clearFrame() }
    }

    private fun carLocal(scene: Scene3D, l: Line, flags: Int, near: Boolean) {
        val hl = l.carLen * .5f; val hw = 1.45f
        val z0 = DECK + .62f; val z1 = DECK + 3.95f
        val night = scene.daylight < .55f
        val cx = scene.camLX; val cy = scene.camLY
        scene.box(-hl + .8f, -1.15f, DECK + .1f, hl - .8f, 1.15f, z0, UNDER, UNDER)
        scene.material = Mat.METAL
        scene.box(-hl, -hw, z0, hl, hw, z1, ROOF, BODY)
        scene.material = Mat.PLAIN
        val stripe = scene.lit(l.color, 1f)
        val glass = if (night) WorldRenderer.LIT else scene.lit(WINDOW, 1f)
        for (sd in intArrayOf(-1, 1)) {
            val yy = sd * (hw + .03f)
            if ((sd > 0 && cy <= yy) || (sd < 0 && cy >= yy)) continue
            val shade = scene.sideShade(0f, sd.toFloat())
            // the line's color in a band under the windows and a thin one above
            scene.quad(-hl, yy, z0 + .55f, hl, yy, z0 + .55f, hl, yy, z0 + 1.05f, -hl, yy, z0 + 1.05f, scene.lit(l.color, shade))
            if (!near) { scene.quad(-hl + .6f, yy, z0 + 1.35f, hl - .6f, yy, z0 + 1.35f, hl - .6f, yy, z0 + 2.35f, -hl + .6f, yy, z0 + 2.35f, glass); continue }
            scene.quad(-hl, yy, z1 - .45f, hl, yy, z1 - .45f, hl, yy, z1 - .3f, -hl, yy, z1 - .3f, scene.lit(l.color, shade))
            // doors with windows between them
            val doors = 4
            val pitch = l.carLen / doors
            for (d in 0 until doors) {
                val u = -hl + pitch * (d + .5f)
                scene.quad(u - .65f, yy, z0 + .05f, u + .65f, yy, z0 + .05f, u + .65f, yy, z1 - .6f, u - .65f, yy, z1 - .6f, scene.lit(c(0xFF9DA4AA), shade))
                scene.quad(u - .45f, yy * 1.002f, z0 + 1.4f, u + .45f, yy * 1.002f, z0 + 1.4f, u + .45f, yy * 1.002f, z0 + 2.3f, u - .45f, yy * 1.002f, z0 + 2.3f, glass)
                if (d < doors - 1) {
                    val w0 = u + .9f; val w1 = u + pitch - .9f
                    scene.quad(w0, yy, z0 + 1.3f, w1, yy, z0 + 1.3f, w1, yy, z0 + 2.4f, w0, yy, z0 + 2.4f, glass)
                }
            }
        }
        // the cab ends: a dark windscreen with the line color under it, head or tail lights
        for (end in intArrayOf(-1, 1)) {
            val front = (end > 0 && flags and 1 != 0) || (end < 0 && flags and 2 != 0)
            if (!front) continue
            val xx = end * (hl + .03f)
            if ((end > 0 && cx <= xx) || (end < 0 && cx >= xx)) continue
            val shade = scene.sideShade(end.toFloat(), 0f)
            scene.quad(xx, -hw + .15f, z0 + 1.45f, xx, hw - .15f, z0 + 1.45f, xx, hw - .15f, z1 - .35f, xx, -hw + .15f, z1 - .35f, scene.lit(WINDOW, shade))
            scene.quad(xx, -hw, z0 + .4f, xx, hw, z0 + .4f, xx, hw, z0 + 1.2f, xx, -hw, z0 + 1.2f, scene.lit(l.color, shade))
            val lamp = if (end > 0) c(0xFFFFF4D6) else c(0xFFFF2E2E)
            val on = night || end > 0
            for (sd in intArrayOf(-1, 1)) {
                val yy = sd * (hw - .45f)
                scene.quad(xx * 1.001f, yy - .22f, z0 + .55f, xx * 1.001f, yy + .22f, z0 + .55f, xx * 1.001f, yy + .22f, z0 + .9f, xx * 1.001f, yy - .22f, z0 + .9f,
                    if (on) lamp else scene.lit(c(0xFF6E7378), 1f))
            }
        }
        if (near) {
            // roof gear: an air conditioner and, on every other car, a pantograph
            scene.box(-1.6f, -.8f, z1, 1.6f, .8f, z1 + .35f, c(0xFFC4C9CD), c(0xFFB2B8BD))
            if (flags and 1 == 0) scene.box(hl - 4f, -.9f, z1 + .35f, hl - 3.2f, .9f, z1 + .45f, POLE, POLE)
        }
    }

    // ------------------------------------------------------------------ viaduct

    private val sq = FloatArray(64); private val sx = FloatArray(64); private val sy = FloatArray(64)
    private val snx = FloatArray(64); private val sny = FloatArray(64); private val shw = FloatArray(64); private val str = FloatArray(64)
    private var sn = 0
    private val qx = FloatArray(4); private val qy = FloatArray(4); private val qz = FloatArray(4)

    /** Samples a span at the line's points (and its two ends) into the s* arrays. */
    private fun sample(l: Line, q0: Float, q1: Float) {
        sn = 0
        fun put(q: Float) {
            if (sn >= sq.size) return
            l.at(q, tmp)
            sq[sn] = q; sx[sn] = tmp[0]; sy[sn] = tmp[1]
            // left normal
            snx[sn] = tmp[3]; sny[sn] = -tmp[2]
            shw[sn] = l.halfWidth(q); str[sn] = l.track(q)
            sn++
        }
        put(q0)
        val step = l.len / (l.n - 1)
        var k = ceil(q0 / step + 1e-3f).toInt()
        while (k * step < q1 - .5f && sn < sq.size - 1) { put(k * step); k++ }
        put(q1)
    }

    /**
     * A strip lofted along the sampled span between (offset [oa], height [za]) and ([ob], [zb]), offsets as multiples of
     * the half width (kw) and the track (kt) plus a constant. With [face] ±1 it is a wall facing left/right, culled
     * when turned away on the Canvas path.
     */
    private fun loft(scene: Scene3D, oaW: Float, oaC: Float, za: Float, obW: Float, obC: Float, zb: Float, color: Int, face: Int, trk: Float = 0f) {
        val cpu = scene.gpu == null
        for (k in 0 until sn - 1) {
            val j = k + 1
            val oa0 = shw[k] * oaW + str[k] * trk + oaC; val oa1 = shw[j] * oaW + str[j] * trk + oaC
            val ob0 = shw[k] * obW + str[k] * trk + obC; val ob1 = shw[j] * obW + str[j] * trk + obC
            var col = color
            if (face != 0) {
                val nx = (snx[k] + snx[j]) * .5f * face; val ny = (sny[k] + sny[j]) * .5f * face
                if (cpu) {
                    val mx = (sx[k] + sx[j]) * .5f + nx * oa0; val my = (sy[k] + sy[j]) * .5f + ny * oa0
                    if ((scene.proj.camX - mx) * nx + (scene.proj.camY - my) * ny <= 0f) continue
                }
                col = scene.lit(color, scene.sideShade(nx, ny))
            }
            qx[0] = sx[k] + snx[k] * oa0; qy[0] = sy[k] + sny[k] * oa0; qz[0] = za
            qx[1] = sx[j] + snx[j] * oa1; qy[1] = sy[j] + sny[j] * oa1; qz[1] = za
            qx[2] = sx[j] + snx[j] * ob1; qy[2] = sy[j] + sny[j] * ob1; qz[2] = zb
            qx[3] = sx[k] + snx[k] * ob0; qy[3] = sy[k] + sny[k] * ob0; qz[3] = zb
            scene.poly(4, qx, qy, qz, col)
        }
    }

    /**
     * Draws span [i]: its pier, the box girder and deck, tracks, platforms and parapets with the line's stripe, and
     * (when [trains]) the cars on it. [d] is the distance from the camera, for detail.
     */
    fun drawSpan(scene: Scene3D, i: Int, d: Float, trains: Boolean, mapMode: Boolean) {
        val sp = spans[i]
        val l = lines.firstOrNull { it.index == sp.line } ?: return
        val near = !mapMode && d < 95f
        val mid = !mapMode && d < 260f
        if (sp.pier >= 0) drawPier(scene, l, l.piers[sp.pier], mid)
        sample(l, sp.q0, sp.q1)
        val long = sp.q1 - sp.q0 > 40f
        scene.material = if (long) Mat.METAL else Mat.CONCRETE
        // box girder (a deep steel one over long spans), then the deck slab
        val gz = if (long) 6.3f else GIRDER_Z
        val gcol = if (long) STEEL else CONCRETE_DARK
        loft(scene, 0f, 2.3f, gz, 0f, 2.3f, SLAB_Z, gcol, 1)
        loft(scene, 0f, -2.3f, gz, 0f, -2.3f, SLAB_Z, gcol, -1)
        scene.material = Mat.CONCRETE
        loft(scene, 1f, 0f, SLAB_Z, 1f, 0f, DECK, CONCRETE, 1)
        loft(scene, -1f, 0f, SLAB_Z, -1f, 0f, DECK, CONCRETE, -1)
        loft(scene, -1f, 0f, DECK, 1f, 0f, DECK, scene.lit(CONCRETE, .92f), 0)
        scene.material = Mat.PLAIN
        if (mid) for (sd in intArrayOf(-1, 1)) {
            // ballast beds, and up close the sleepers and rails
            loft(scene, 0f, sd * 1.3f, DECK + .03f, 0f, -sd * 1.3f, DECK + .03f, scene.lit(BALLAST, 1f), 0, sd.toFloat())
            if (near) {
                sleepers(scene, l, sd)
                val rail = scene.lit(RAIL, 1.1f)
                for (r in intArrayOf(-1, 1)) loft(scene, 0f, r * .53f - .05f, DECK + .2f, 0f, r * .53f + .05f, DECK + .2f, rail, 0, sd.toFloat())
            }
        } else if (!mapMode) loft(scene, 0f, -3f, DECK + .03f, 0f, 3f, DECK + .03f, scene.lit(BALLAST, 1f), 0)
        // the camera's side of the line, for drawing the far parapet before the trains and the near one after
        val camSide = if ((scene.proj.camX - sp.cx) * snx[sn / 2] + (scene.proj.camY - sp.cy) * sny[sn / 2] >= 0f) 1 else -1
        if (!mapMode) platforms(scene, l, sp, mid)
        parapet(scene, l, -camSide, mid)
        if (trains && !mapMode) {
            update(lastTime)
            for (k in 0 until carCount) {
                if (carLine[k] != l.index) continue
                val dq = l.delta(sp.q0, carQ[k])
                if (dq < -l.carLen * .5f || dq > sp.q1 - sp.q0 + l.carLen * .5f) continue
                drawCar(scene, k, near)
            }
        }
        parapet(scene, l, camSide, mid)
        if (!mapMode) canopy(scene, l, sp, mid)
        if (mid && sp.pier >= 0 && (sp.pier % 2 == 0) && !l.onPlatform(sp.q0)) catenary(scene, l, sp, near)
    }

    /** Time the trains drawn with the spans are placed for (set by the renderer each frame). */
    var lastTime = 0f

    private fun parapet(scene: Scene3D, l: Line, sd: Int, mid: Boolean) {
        val f = sd.toFloat()
        scene.material = Mat.CONCRETE
        loft(scene, f, 0f, DECK, f, 0f, PARAPET, CONCRETE, sd)
        loft(scene, f, -f * .25f, DECK, f, -f * .25f, PARAPET, CONCRETE, -sd)
        loft(scene, f, -f * .25f, PARAPET, f, 0f, PARAPET, scene.lit(CONCRETE, 1.05f), 0)
        scene.material = Mat.PLAIN
        // the line's color along the outside
        loft(scene, f, f * .03f, DECK + .3f, f, f * .03f, DECK + (if (mid) .62f else .75f), l.color, sd)
    }

    private fun sleepers(scene: Scene3D, l: Line, sd: Int) {
        val col = scene.lit(SLEEPER, 1f)
        val q0 = sq[0]; val q1 = sq[sn - 1]
        var q = ceil(q0 / 1.4f) * 1.4f
        while (q < q1) {
            l.at(q, tmp)
            val nx = tmp[3]; val ny = -tmp[2]
            val o = sd * l.track(q)
            val cx = tmp[0] + nx * o; val cy = tmp[1] + ny * o
            val ax = tmp[2] * .13f; val ay = tmp[3] * .13f
            scene.quad(cx - nx * 1.2f - ax, cy - ny * 1.2f - ay, DECK + .1f, cx + nx * 1.2f - ax, cy + ny * 1.2f - ay, DECK + .1f,
                cx + nx * 1.2f + ax, cy + ny * 1.2f + ay, DECK + .1f, cx - nx * 1.2f + ax, cy - ny * 1.2f + ay, DECK + .1f, col)
            q += 1.4f
        }
    }

    private fun drawPier(scene: Scene3D, l: Line, p: Pier, mid: Boolean) {
        l.at(p.q, tmp)
        // local x along the line, local y = -left
        scene.setFrame(tmp[0], tmp[1], tmp[2], tmp[3])
        val z0 = if (p.wet) -Waterfront.DEPTH else 0f
        scene.material = Mat.CONCRETE
        try {
            if (p.bent) {
                val y0 = min(-p.a, -p.b); val y1 = max(-p.a, -p.b)
                scene.box(-.7f, y0 - .7f, z0, .7f, y0 + .7f, GIRDER_Z - .9f, CONCRETE, CONCRETE)
                scene.box(-.7f, y1 - .7f, z0, .7f, y1 + .7f, GIRDER_Z - .9f, CONCRETE, CONCRETE)
                scene.box(-.85f, y0 - .9f, GIRDER_Z - .9f, .85f, y1 + .9f, GIRDER_Z, CONCRETE, CONCRETE_DARK)
            } else {
                scene.box(-.8f, -.95f, z0, .8f, .95f, PIER_TOP, CONCRETE, CONCRETE)
                // the hammerhead cap widens in two steps under the girder
                scene.box(-.9f, -2.2f, PIER_TOP, .9f, 2.2f, PIER_TOP + .35f, CONCRETE, CONCRETE_DARK)
                scene.box(-.9f, -3f, PIER_TOP + .35f, .9f, 3f, GIRDER_Z, CONCRETE, CONCRETE_DARK)
                if (mid && !p.wet) scene.box(-1.1f, -1.25f, 0f, 1.1f, 1.25f, .5f, CONCRETE_DARK, CONCRETE_DARK)
            }
        } finally { scene.material = Mat.PLAIN; scene.clearFrame() }
    }

    private var partLo = 0f; private var partHi = 0f

    /** The stretch of station [st]'s platform (shortened by [inset] at both ends) lying on span [sp], into partLo..partHi. */
    private fun platformPart(l: Line, sp: Span, st: Float, inset: Float): Boolean {
        partLo = max(l.delta(st, sp.q0), -l.platformHalf + inset)
        partHi = min(l.delta(st, sp.q1), l.platformHalf - inset)
        if (partHi - partLo < .3f) return false
        partLo += st; partHi += st
        return true
    }

    private fun platforms(scene: Scene3D, l: Line, sp: Span, mid: Boolean) {
        var any = false
        for (st in l.stations) {
            if (!platformPart(l, sp, st, 0f)) continue
            val q0 = partLo; val q1 = partHi
            any = true
            sample(l, q0, q1)
            scene.material = Mat.TILE
            loft(scene, 0f, -1.8f, DECK + 1f, 0f, 1.8f, DECK + 1f, scene.lit(PLATFORM, 1f), 0)
            loft(scene, 0f, 1.8f, DECK, 0f, 1.8f, DECK + 1f, PLATFORM, 1)
            loft(scene, 0f, -1.8f, DECK, 0f, -1.8f, DECK + 1f, PLATFORM, -1)
            scene.material = Mat.PLAIN
            if (mid) {
                // yellow safety lines along both edges
                val yellow = scene.lit(c(0xFFF2C94C), 1f)
                loft(scene, 0f, 1.25f, DECK + 1.02f, 0f, 1.5f, DECK + 1.02f, yellow, 0)
                loft(scene, 0f, -1.5f, DECK + 1.02f, 0f, -1.25f, DECK + 1.02f, yellow, 0)
            }
            // canopy posts down the middle
            var q = ceil((q0 + 2f) / 9f) * 9f
            while (q < q1 - 1f) {
                l.at(q, tmp)
                scene.box(tmp[0] - .15f, tmp[1] - .15f, DECK + 1f, tmp[0] + .15f, tmp[1] + .15f, DECK + 4.6f, POLE, POLE)
                q += 9f
            }
        }
        if (any) sample(l, sp.q0, sp.q1)
    }

    private fun canopy(scene: Scene3D, l: Line, sp: Span, mid: Boolean) {
        var any = false
        val night = scene.daylight < .55f
        for (st in l.stations) {
            if (!platformPart(l, sp, st, 2f)) continue
            any = true
            sample(l, partLo, partHi)
            scene.material = Mat.METAL
            // a shallow gable roof over the island platform and the trains' roof edges
            loft(scene, 0f, -2.6f, DECK + 4.6f, 0f, 0f, DECK + 4.95f, scene.lit(CANOPY, .95f), 0)
            loft(scene, 0f, 0f, DECK + 4.95f, 0f, 2.6f, DECK + 4.6f, scene.lit(CANOPY, 1.02f), 0)
            scene.material = Mat.PLAIN
            if (night && mid) loft(scene, 0f, -1.2f, DECK + 4.55f, 0f, 1.2f, DECK + 4.55f, c(0xFFFFF1C9), 0)
        }
        if (any) sample(l, sp.q0, sp.q1)
    }

    private fun catenary(scene: Scene3D, l: Line, sp: Span, near: Boolean) {
        l.at(sp.q0, tmp)
        val nx = tmp[3]; val ny = -tmp[2]
        val hw = l.halfWidth(sp.q0) - .45f
        for (sd in intArrayOf(-1, 1)) {
            val px = tmp[0] + nx * hw * sd; val py = tmp[1] + ny * hw * sd
            scene.box(px - .12f, py - .12f, DECK, px + .12f, py + .12f, DECK + 6.1f, POLE, POLE)
        }
        val ax = tmp[0] - nx * hw; val ay = tmp[1] - ny * hw; val bx = tmp[0] + nx * hw; val by = tmp[1] + ny * hw
        val ux = tmp[2] * .1f; val uy = tmp[3] * .1f
        scene.quad(ax - ux, ay - uy, DECK + 5.7f, bx - ux, by - uy, DECK + 5.7f, bx + ux, by + uy, DECK + 5.9f, ax + ux, ay + uy, DECK + 5.9f, scene.lit(POLE, 1f))
        if (near) for (sd in intArrayOf(-1, 1)) loft(scene, 0f, -.04f, DECK + 5.1f, 0f, .04f, DECK + 5.1f, scene.lit(c(0xFF2A2D31), 1f), 0, sd.toFloat())
    }

    /** The viaduct's painted shadow on the ground (Canvas path), offset by the sun. */
    fun shadow(scene: Scene3D, bbox: FloatArray, dx: Float, dy: Float, color: Int) {
        for (sp in spans) {
            if (sp.cx + sp.r < bbox[0] - 60f || sp.cx - sp.r > bbox[2] + 60f || sp.cy + sp.r < bbox[1] - 60f || sp.cy - sp.r > bbox[3] + 60f) continue
            val l = lines.firstOrNull { it.index == sp.line } ?: continue
            sample(l, sp.q0, sp.q1)
            val ox = dx * DECK; val oy = dy * DECK
            for (k in 0 until sn - 1) {
                val j = k + 1
                qx[0] = sx[k] + snx[k] * shw[k] + ox; qy[0] = sy[k] + sny[k] * shw[k] + oy
                qx[1] = sx[j] + snx[j] * shw[j] + ox; qy[1] = sy[j] + sny[j] * shw[j] + oy
                qx[2] = sx[j] - snx[j] * shw[j] + ox; qy[2] = sy[j] - sny[j] * shw[j] + oy
                qx[3] = sx[k] - snx[k] * shw[k] + ox; qy[3] = sy[k] - sny[k] * shw[k] + oy
                qz[0] = .03f; qz[1] = .03f; qz[2] = .03f; qz[3] = .03f
                scene.poly(4, qx, qy, qz, color)
            }
        }
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float { val dx = bx - ax; val dy = by - ay; return sqrt(dx * dx + dy * dy) }
}

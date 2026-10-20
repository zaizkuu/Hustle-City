package com.hustlecity.game.world

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Road sizes. Lanes run each way; [halfWidth] is the asphalt from the centre line to the curb, and lanes split it
 * evenly (the outer lane doubles as the parking lane on wide roads).
 */
enum class RoadClass(val halfWidth: Float, val lanes: Int, val sidewalk: Float, val speed: Float, val rank: Int) {
    LANE(4f, 1, 2.5f, 7f, 0),
    STREET(6f, 1, 4f, 10f, 1),
    AVENUE(10f, 2, 4f, 13f, 2),
    BOULEVARD(12f, 3, 5f, 15f, 3);

    val laneWidth get() = halfWidth / lanes
    /** Distance from the centre line to the outer edge of the sidewalk. */
    val outer get() = halfWidth + sidewalk
}

class RoadNode(val id: Int, val x: Float, val y: Float) {
    val edges = ArrayList<Int>()
    /** Traffic lights at this junction. */
    var signal = false
    /** Light phase offset in seconds, so neighbouring junctions don't switch all at once. */
    var phase = 0f
    /** A scramble crossing: every car stops together and people cross in all directions, diagonals included. */
    var scramble = false
    val degree get() = edges.size
}

class RoadEdge(val id: Int, val a: Int, val b: Int, val cls: RoadClass, val name: String, val bridge: Boolean) {
    var ax = 0f; var ay = 0f; var bx = 0f; var by = 0f
    var len = 0f
    /** Unit direction from a to b. */
    var ux = 0f; var uy = 0f
    /** Distance from each end's node centre to where the road's own strip starts (junction setback). */
    var clearA = 0f; var clearB = 0f
    /** Over water (drawn as a bridge deck). */
    var overWater = false
    /** Group of edges sharing a name, for house numbers measured along the whole road. */
    var numberOrigin = 0f
    /**
     * Runs inside a bigger road for its whole length (two plans drew the same street): it keeps its asphalt and its
     * traffic, but the bigger road's paint, curbs, lights and sidewalks are the ones that show.
     */
    var under = false

    fun other(node: Int) = if (node == a) b else a
    fun x(t: Float) = ax + (bx - ax) * t
    fun y(t: Float) = ay + (by - ay) * t
    /** Heading (0 = north, clockwise) when driving from [from]. */
    fun heading(from: Int): Float = if (from == a) atan2(ux, -uy) else atan2(-ux, uy)
}

/** Result of a nearest-road query. */
class RoadHit {
    var edge = -1
    /** Distance along the edge from node a. */
    var s = 0f
    var dist = Float.MAX_VALUE
    /** +1 when the point lies to the left of a→b, -1 to the right. */
    var side = 1
    var x = 0f; var y = 0f
}

/** The road network: junctions and straight road pieces between them (curves are chains of short pieces). */
class RoadGraph(val nodes: List<RoadNode>, val edges: List<RoadEdge>) {
    private val cell = 48f
    private val minX: Float; private val minY: Float
    private val cols: Int; private val rows: Int
    private val grid: Array<IntArray>

    init {
        for (e in edges) {
            val na = nodes[e.a]; val nb = nodes[e.b]
            e.ax = na.x; e.ay = na.y; e.bx = nb.x; e.by = nb.y
            val dx = e.bx - e.ax; val dy = e.by - e.ay
            e.len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
            e.ux = dx / e.len; e.uy = dy / e.len
        }
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (n in nodes) { x0 = min(x0, n.x); y0 = min(y0, n.y); x1 = max(x1, n.x); y1 = max(y1, n.y) }
        if (nodes.isEmpty()) { x0 = 0f; y0 = 0f; x1 = 1f; y1 = 1f }
        minX = x0 - 40f; minY = y0 - 40f
        cols = ((x1 + 40f - minX) / cell).toInt() + 1
        rows = ((y1 + 40f - minY) / cell).toInt() + 1
        val lists = Array(cols * rows) { ArrayList<Int>() }
        for (e in edges) {
            val pad = e.cls.outer + 2f
            val c0 = ((min(e.ax, e.bx) - pad - minX) / cell).toInt().coerceIn(0, cols - 1)
            val c1 = ((max(e.ax, e.bx) + pad - minX) / cell).toInt().coerceIn(0, cols - 1)
            val r0 = ((min(e.ay, e.by) - pad - minY) / cell).toInt().coerceIn(0, rows - 1)
            val r1 = ((max(e.ay, e.by) + pad - minY) / cell).toInt().coerceIn(0, rows - 1)
            for (r in r0..r1) for (c in c0..c1) {
                // keep only cells the segment actually passes near
                val cx = minX + (c + .5f) * cell; val cy = minY + (r + .5f) * cell
                if (Geo.distToSeg(cx, cy, e.ax, e.ay, e.bx, e.by) <= cell * .75f + pad) lists[r * cols + c] += e.id
            }
        }
        grid = Array(lists.size) { lists[it].toIntArray() }
        setbacks()
        markUnder()
    }

    /** Flags edges that lie along a bigger road's asphalt from end to end (see [RoadEdge.under]). */
    private fun markUnder() {
        for (e in edges) {
            var covered = true
            for (t in floatArrayOf(.2f, .5f, .8f)) {
                val x = e.ax + (e.bx - e.ax) * t; val y = e.ay + (e.by - e.ay) * t
                var hit = false
                near(x, y, 16f) { id ->
                    val o = edges[id]
                    if (hit || o.id == e.id || o.overWater != e.overWater) return@near
                    if (o.cls.rank < e.cls.rank || (o.cls.rank == e.cls.rank && o.id > e.id)) return@near
                    if (abs(o.ux * e.ux + o.uy * e.uy) < .96f) return@near
                    // alongside the middle of the other road, not just touching the end it shares with this one
                    val along = (x - o.ax) * o.ux + (y - o.ay) * o.uy
                    if (along < .5f || along > o.len - .5f) return@near
                    if (abs(-(x - o.ax) * o.uy + (y - o.ay) * o.ux) <= o.cls.halfWidth * .9f) hit = true
                }
                if (!hit) { covered = false; break }
            }
            e.under = covered
        }
    }

    /** Edge ids whose corridor may come within [r] of (x, y). Calls [f] possibly more than once per edge. */
    fun near(x: Float, y: Float, r: Float, f: (Int) -> Unit) = forCells(x - r, y - r, x + r, y + r, f)

    fun forCells(x0: Float, y0: Float, x1: Float, y1: Float, f: (Int) -> Unit) {
        val c0 = floor((x0 - minX) / cell).toInt().coerceIn(0, cols - 1)
        val c1 = floor((x1 - minX) / cell).toInt().coerceIn(0, cols - 1)
        val r0 = floor((y0 - minY) / cell).toInt().coerceIn(0, rows - 1)
        val r1 = floor((y1 - minY) / cell).toInt().coerceIn(0, rows - 1)
        for (r in r0..r1) for (c in c0..c1) for (id in grid[r * cols + c]) f(id)
    }

    /** Nearest road centre line to (x, y) within [maxDist]; [filter] can skip edges. */
    fun nearest(x: Float, y: Float, maxDist: Float, out: RoadHit, filter: ((RoadEdge) -> Boolean)? = null): Boolean {
        out.edge = -1; out.dist = maxDist
        forCells(x - maxDist, y - maxDist, x + maxDist, y + maxDist) { id ->
            val e = edges[id]
            if (filter != null && !filter(e)) return@forCells
            val t = (((x - e.ax) * e.ux + (y - e.ay) * e.uy)).coerceIn(0f, e.len)
            val px = e.ax + e.ux * t; val py = e.ay + e.uy * t
            val dx = x - px; val dy = y - py
            val d = sqrt(dx * dx + dy * dy)
            if (d < out.dist) {
                out.dist = d; out.edge = id; out.s = t; out.x = px; out.y = py
                // left of a→b is (uy, -ux) in a y-down world
                out.side = if (dx * e.uy - dy * e.ux >= 0f) 1 else -1
            }
        }
        return out.edge >= 0
    }

    /** True when (x, y) is on a road's asphalt or within [margin] meters of it (junctions and crossings included). */
    fun nearAsphalt(x: Float, y: Float, margin: Float): Boolean {
        var on = false
        near(x, y, 14f + margin) { id ->
            val e = edges[id]
            if (!on && Geo.distToSeg(x, y, e.ax, e.ay, e.bx, e.by) <= e.cls.halfWidth + margin) on = true
        }
        return on
    }

    /** True when (x, y) is on a road's asphalt (not the sidewalk). */
    fun onAsphalt(x: Float, y: Float): Boolean {
        var on = false
        near(x, y, 14f) { id ->
            val e = edges[id]
            if (!on && Geo.distToSeg(x, y, e.ax, e.ay, e.bx, e.by) <= e.cls.halfWidth) on = true
        }
        return on
    }

    /** Edges leaving [node], with the angle of each (radians, atan2 of the direction away from the node). */
    fun angleAway(edge: RoadEdge, node: Int): Float = if (node == edge.a) atan2(edge.uy, edge.ux) else atan2(-edge.uy, -edge.ux)

    /** How far each road's strip stops short of a junction so crossing roads don't overlap. */
    private fun setbacks() {
        for (n in nodes) {
            if (n.degree < 2) continue
            for (i in n.edges) {
                val ei = edges[i]
                var clear = 0f
                if (n.degree >= 3 || sharpBend(n)) {
                    val ai = angleAway(ei, n.id)
                    for (j in n.edges) {
                        if (j == i) continue
                        val ej = edges[j]
                        var d = abs(angleAway(ej, n.id) - ai)
                        if (d > Math.PI) d = (2 * Math.PI - d).toFloat()
                        // how far out this road's edge stays inside the other road (plus room for a crosswalk):
                        // a lot for a sharp fork, nothing for the road carrying straight on
                        val sn = kotlin.math.sin(d); val cs = kotlin.math.cos(d)
                        val hj = ej.cls.halfWidth + ej.cls.sidewalk * .5f
                        val hi = ei.cls.halfWidth
                        val need = if (cs >= 0f) (hj + hi * cs) / max(sn, .25f)
                            else min(hi * sn / -cs, (hj + hi * cs) / max(sn, .05f)).coerceAtLeast(0f)
                        clear = max(clear, need)
                    }
                    clear = clear.coerceAtMost(min(ei.len * .45f, 34f))
                }
                if (ei.a == n.id) ei.clearA = clear else ei.clearB = clear
            }
        }
    }

    private fun sharpBend(n: RoadNode): Boolean {
        if (n.degree != 2) return false
        val e0 = edges[n.edges[0]]; val e1 = edges[n.edges[1]]
        var d = abs(angleAway(e0, n.id) - angleAway(e1, n.id))
        if (d > Math.PI) d = (2 * Math.PI - d).toFloat()
        // a straight continuation is π apart; anything sharper than ~35° off straight gets a proper junction
        return d < Math.PI - .6
    }
}

/**
 * Collects road segments from the map's generators and resolves them into a planar [RoadGraph]: crossings become
 * junctions, dead ends close to another road are extended into T-junctions, and stray pieces are dropped.
 */
class RoadBuilder {
    private class Seg(var ax: Float, var ay: Float, var bx: Float, var by: Float, val cls: RoadClass, val name: String, val bridge: Boolean) {
        val cuts = ArrayList<Float>()
    }

    private val segs = ArrayList<Seg>()

    fun add(ax: Float, ay: Float, bx: Float, by: Float, cls: RoadClass, name: String, bridge: Boolean = false) {
        val dx = bx - ax; val dy = by - ay
        if (dx * dx + dy * dy < 1f) return
        segs += Seg(ax, ay, bx, by, cls, name, bridge)
    }

    fun polyline(pts: FloatArray, cls: RoadClass, name: String, bridge: Boolean = false) {
        for (i in 0 until pts.size / 2 - 1) add(pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3], cls, name, bridge)
    }

    /** Adds a polyline, keeping only the parts inside [region]. */
    fun polylineIn(pts: FloatArray, region: FloatArray, cls: RoadClass, name: String) {
        for (i in 0 until pts.size / 2 - 1) {
            for (p in Geo.clip(pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3], region)) add(p[0], p[1], p[2], p[3], cls, name)
        }
    }

    /**
     * @param wet where roads may only cross as bridges
     * @param blocked polygons no road may enter (parks without paths, palaces)
     * @param cut polygons lanes and streets are cut out of exactly at the outline (landmark grounds); avenues stay
     */
    fun build(wet: (Float, Float) -> Boolean, blocked: List<FloatArray> = emptyList(), snap: Float = 2.5f, cut: List<FloatArray> = emptyList()): RoadGraph {
        extendDeadEnds(28f)
        split()
        if (cut.isNotEmpty()) {
            val hit = FloatArray(2)
            for (s in segs) if (s.cls.rank <= 1) for (poly in cut) {
                val n = poly.size / 2
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    if (Geo.segHit(s.ax, s.ay, s.bx, s.by, poly[i * 2], poly[i * 2 + 1], poly[j * 2], poly[j * 2 + 1], hit)) s.cuts += hit[0]
                }
            }
        }
        // pieces
        class Piece(val ax: Float, val ay: Float, val bx: Float, val by: Float, val cls: RoadClass, val name: String, val bridge: Boolean)
        val pieces = ArrayList<Piece>()
        for (s in segs) {
            s.cuts += 0f; s.cuts += 1f
            s.cuts.sort()
            for (k in 0 until s.cuts.size - 1) {
                val t0 = s.cuts[k]; val t1 = s.cuts[k + 1]
                val x0 = s.ax + (s.bx - s.ax) * t0; val y0 = s.ay + (s.by - s.ay) * t0
                val x1 = s.ax + (s.bx - s.ax) * t1; val y1 = s.ay + (s.by - s.ay) * t1
                val dx = x1 - x0; val dy = y1 - y0
                if (dx * dx + dy * dy < .25f) continue
                val mx = (x0 + x1) * .5f; val my = (y0 + y1) * .5f
                val isWet = wet(mx, my)
                if (isWet && !s.bridge) continue
                if (blocked.any { Geo.inside(mx, my, it) }) continue
                if (s.cls.rank <= 1 && cut.any { Geo.inside(mx, my, it) }) continue
                pieces += Piece(x0, y0, x1, y1, s.cls, s.name, s.bridge && isWet)
            }
        }
        // nodes: merge points closer than [snap]
        val nodeX = ArrayList<Float>(); val nodeY = ArrayList<Float>()
        val hash = HashMap<Long, MutableList<Int>>()
        fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
        fun nodeAt(x: Float, y: Float): Int {
            val cx = floor(x / snap).toInt(); val cy = floor(y / snap).toInt()
            for (gx in cx - 1..cx + 1) for (gy in cy - 1..cy + 1) {
                hash[key(gx, gy)]?.forEach { id ->
                    val dx = nodeX[id] - x; val dy = nodeY[id] - y
                    if (dx * dx + dy * dy <= snap * snap) return id
                }
            }
            val id = nodeX.size
            nodeX += x; nodeY += y
            hash.getOrPut(key(cx, cy)) { mutableListOf() } += id
            return id
        }
        class Link(val a: Int, val b: Int, val cls: RoadClass, val name: String, val bridge: Boolean)
        val links = HashMap<Long, Link>()
        for (p in pieces) {
            val a = nodeAt(p.ax, p.ay); val b = nodeAt(p.bx, p.by)
            if (a == b) continue
            val k = if (a < b) key(a, b) else key(b, a)
            val old = links[k]
            if (old == null || p.cls.rank > old.cls.rank) links[k] = Link(min(a, b), max(a, b), p.cls, p.name, p.bridge || (old?.bridge ?: false))
        }
        // keep the biggest connected network, then trim short dead-end stubs
        var alive = links.values.toMutableList()
        repeat(3) {
            val deg = IntArray(nodeX.size)
            for (l in alive) { deg[l.a]++; deg[l.b]++ }
            alive = alive.filter { l ->
                val len = sqrt((nodeX[l.a] - nodeX[l.b]).let { it * it } + (nodeY[l.a] - nodeY[l.b]).let { it * it })
                !((deg[l.a] == 1 || deg[l.b] == 1) && len < 14f)
            }.toMutableList()
        }
        val adj = HashMap<Int, MutableList<Link>>()
        for (l in alive) { adj.getOrPut(l.a) { mutableListOf() } += l; adj.getOrPut(l.b) { mutableListOf() } += l }
        val comp = HashMap<Int, Int>()
        var bestComp = -1; var bestSize = 0
        for (start in adj.keys) {
            if (start in comp) continue
            val id = start
            var size = 0
            val stack = ArrayDeque<Int>()
            stack += start; comp[start] = id
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                for (l in adj[n].orEmpty()) {
                    size++
                    val o = if (l.a == n) l.b else l.a
                    if (o !in comp) { comp[o] = id; stack += o }
                }
            }
            if (size > bestSize) { bestSize = size; bestComp = id }
        }
        alive = alive.filter { comp[it.a] == bestComp }.toMutableList()
        // renumber
        val remap = HashMap<Int, Int>()
        val nodes = ArrayList<RoadNode>()
        fun nid(old: Int) = remap.getOrPut(old) { nodes.size.also { nodes += RoadNode(it, nodeX[old], nodeY[old]) } }
        val edges = ArrayList<RoadEdge>()
        for (l in alive.sortedWith(compareBy({ nodeY[it.a] + nodeY[it.b] }, { nodeX[it.a] + nodeX[it.b] }))) {
            val a = nid(l.a); val b = nid(l.b)
            val e = RoadEdge(edges.size, a, b, l.cls, l.name, l.bridge)
            edges += e
            nodes[a].edges += e.id; nodes[b].edges += e.id
        }
        for (e in edges) e.overWater = e.bridge
        // lights where real roads cross
        for (n in nodes) {
            if (n.degree < 3) continue
            val ranks = n.edges.map { edges[it].cls.rank }.sortedDescending()
            n.signal = ranks[0] >= 1 && ranks[1] >= 1 && !(ranks[0] == 1 && n.degree == 3 && ranks[2] == 0)
            n.phase = ((n.id * 7919) % 40).toFloat()
        }
        return RoadGraph(nodes, edges)
    }

    /** Cuts every pair of crossing segments at their crossing point. */
    private fun split() {
        val cell = 60f
        val buckets = HashMap<Long, MutableList<Int>>()
        fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
        for ((i, s) in segs.withIndex()) {
            val c0 = floor(min(s.ax, s.bx) / cell).toInt(); val c1 = floor(max(s.ax, s.bx) / cell).toInt()
            val r0 = floor(min(s.ay, s.by) / cell).toInt(); val r1 = floor(max(s.ay, s.by) / cell).toInt()
            for (r in r0..r1) for (c in c0..c1) buckets.getOrPut(key(c, r)) { mutableListOf() } += i
        }
        val seen = HashSet<Long>()
        val hit = FloatArray(2)
        for (list in buckets.values) {
            for (x in list.indices) for (y in x + 1 until list.size) {
                val i = list[x]; val j = list[y]
                val pk = if (i < j) key(i, j) else key(j, i)
                if (!seen.add(pk)) continue
                val a = segs[i]; val b = segs[j]
                if (Geo.segHit(a.ax, a.ay, a.bx, a.by, b.ax, b.ay, b.bx, b.by, hit)) {
                    a.cuts += hit[0]; b.cuts += hit[1]
                }
            }
        }
    }

    /** Dead ends that stop just short of another road are extended to meet it. */
    private fun extendDeadEnds(reach: Float) {
        // count endpoints shared with other segments
        val ends = HashMap<Long, Int>()
        fun k(x: Float, y: Float) = (floor(x / 2f).toLong() shl 32) or (floor(y / 2f).toLong() and 0xffffffffL)
        for (s in segs) { ends.merge(k(s.ax, s.ay), 1, Int::plus); ends.merge(k(s.bx, s.by), 1, Int::plus) }
        val hit = FloatArray(2)
        for (s in segs) {
            for (end in 0..1) {
                val ex = if (end == 0) s.ax else s.bx; val ey = if (end == 0) s.ay else s.by
                if ((ends[k(ex, ey)] ?: 0) > 1) continue
                val ox = if (end == 0) s.bx else s.ax; val oy = if (end == 0) s.by else s.ay
                var dx = ex - ox; var dy = ey - oy
                val l = sqrt(dx * dx + dy * dy)
                if (l < 1e-3f) continue
                dx /= l; dy /= l
                // already touching something?
                var touching = false
                for (o in segs) {
                    if (o === s) continue
                    if (Geo.distToSeg(ex, ey, o.ax, o.ay, o.bx, o.by) < 1.5f) { touching = true; break }
                }
                if (touching) continue
                var best = reach
                for (o in segs) {
                    if (o === s) continue
                    val t = Geo.rayHit(ex, ey, dx, dy, o.ax, o.ay, o.bx, o.by)
                    if (t in 0f..best) best = t
                }
                if (best < reach) {
                    val nx = ex + dx * (best + .5f); val ny = ey + dy * (best + .5f)
                    if (end == 0) { s.ax = nx; s.ay = ny } else { s.bx = nx; s.by = ny }
                }
            }
        }
        if (hit[0] < 0) return
    }
}

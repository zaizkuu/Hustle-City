package com.hustlecity.game.engine

import com.hustlecity.game.world.RoadHit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Navigation to the waypoint: the shortest way over the road network (Dijkstra over the junctions), joined to the
 * player's position and to the destination's front door, and the same way laid along the player's lane for the trail.
 */
class Gps(private val e: GameEngine) {
    /** Route polyline as x, y pairs, from the player to the door. */
    var route = FloatArray(0)
        private set
    var points = 0
        private set
    /** Meters left along the route. */
    var distance = 0f
        private set
    /** Next instruction: -1 turn left, 1 turn right, 0 go straight to the destination. */
    var turn = 0
        private set
    var turnDistance = 0f
        private set
    var roadName = ""
        private set
    val active get() = points >= 2 && e.state.player.waypoint >= 0
    /**
     * The route as it's painted on the road, x, y pairs: down the middle of the player's own lane (on the right, or the
     * left where traffic keeps left; before a turn, the lane the turn is made from), curving through the junctions,
     * from the player to the door.
     */
    var lane = FloatArray(0)
        private set
    var lanePoints = 0
        private set

    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastTarget = -1
    private var clock = 0f

    private var dist = FloatArray(0)
    private var prevNode = IntArray(0)
    private var prevEdge = IntArray(0)
    private var done = BooleanArray(0)
    private var heap = IntArray(0)
    private var heapKey = FloatArray(0)
    private var heapSize = 0
    /** Road the route follows after each point (for naming the road after a turn), -1 where off-road. */
    private var segEdge = IntArray(0)
    private val hitA = RoadHit()
    private val hitB = RoadHit()

    fun clear() { points = 0; lanePoints = 0; distance = 0f; lastTarget = -1 }

    /** Recomputes the route a couple of times a second, or right away when the destination changes. */
    fun update(dt: Float) {
        val s = e.state
        val target = e.city.building(s.player.waypoint)
        if (target == null || s.player.inBuilding >= 0) { if (target == null) clear(); return }
        val (px, py) = e.focusPoint()
        clock -= dt
        val moved = lastX.isNaN() || abs(px - lastX) + abs(py - lastY) > 6f
        if (target.id != lastTarget || (moved && clock <= 0f) || clock < -2f) {
            clock = .4f
            lastX = px; lastY = py; lastTarget = target.id
            plan(px, py, target.doorX, target.doorY, target.frontEdge)
        }
        // arrival
        if (dist2(px, py, target.doorX, target.doorY) < 14f * 14f) {
            e.toast("You have arrived at ${e.buildingName(target)}", ToastKind.GOOD)
            s.player.waypoint = -1
            clear()
            e.bump()
        }
    }

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float { val dx = ax - bx; val dy = ay - by; return dx * dx + dy * dy }

    private fun push(node: Int, key: Float) {
        var i = heapSize++
        heap[i] = node; heapKey[i] = key
        while (i > 0) {
            val p = (i - 1) / 2
            if (heapKey[p] <= heapKey[i]) break
            val tn = heap[p]; heap[p] = heap[i]; heap[i] = tn
            val tk = heapKey[p]; heapKey[p] = heapKey[i]; heapKey[i] = tk
            i = p
        }
    }

    private fun pop(): Int {
        val top = heap[0]
        heapSize--
        if (heapSize > 0) {
            heap[0] = heap[heapSize]; heapKey[0] = heapKey[heapSize]
            var i = 0
            while (true) {
                val l = i * 2 + 1; val r = l + 1
                var m = i
                if (l < heapSize && heapKey[l] < heapKey[m]) m = l
                if (r < heapSize && heapKey[r] < heapKey[m]) m = r
                if (m == i) break
                val tn = heap[m]; heap[m] = heap[i]; heap[i] = tn
                val tk = heapKey[m]; heapKey[m] = heapKey[i]; heapKey[i] = tk
                i = m
            }
        }
        return top
    }

    private fun plan(px: Float, py: Float, dx: Float, dy: Float, frontEdge: Int) {
        val g = e.city.roads
        if (g.edges.isEmpty()) { points = 0; return }
        if (!g.nearest(px, py, 400f, hitA)) { points = 0; return }
        // the door is on its building's street
        val fe = g.edges.getOrNull(frontEdge)
        if (fe != null) {
            hitB.edge = fe.id
            hitB.s = ((dx - fe.ax) * fe.ux + (dy - fe.ay) * fe.uy).coerceIn(0f, fe.len)
            hitB.x = fe.ax + fe.ux * hitB.s; hitB.y = fe.ay + fe.uy * hitB.s
        } else if (!g.nearest(dx, dy, 400f, hitB)) { points = 0; return }
        val out = ArrayList<Float>()
        val edgeOf = ArrayList<Int>()
        fun add(x: Float, y: Float, edge: Int) {
            if (out.size >= 2 && abs(out[out.size - 2] - x) < .01f && abs(out[out.size - 1] - y) < .01f) return
            out += x; out += y; edgeOf += edge
        }
        add(px, py, -1)
        add(hitA.x, hitA.y, hitA.edge)
        val ea = g.edges[hitA.edge]; val eb = g.edges[hitB.edge]
        // the stretches of road driven, for painting the route in the lane
        val legs = ArrayList<Leg>()
        if (ea.id == eb.id) {
            add(hitB.x, hitB.y, -1)
            legs += Leg(ea.id, hitA.s, hitB.s, false, false)
        } else {
            val n = g.nodes.size
            if (dist.size < n) { dist = FloatArray(n); prevNode = IntArray(n); prevEdge = IntArray(n); done = BooleanArray(n) }
            // every relaxation may push once: at most two per edge plus the seeds
            val cap = g.edges.size * 2 + 8
            if (heap.size < cap) { heap = IntArray(cap); heapKey = FloatArray(cap) }
            java.util.Arrays.fill(dist, 0, n, Float.MAX_VALUE)
            java.util.Arrays.fill(prevNode, 0, n, -1)
            java.util.Arrays.fill(done, 0, n, false)
            heapSize = 0
            fun seed(node: Int, cost: Float) { if (cost < dist[node]) { dist[node] = cost; prevEdge[node] = ea.id; push(node, cost) } }
            seed(ea.a, hitA.s)
            seed(ea.b, ea.len - hitA.s)
            val goalA = eb.a; val goalB = eb.b
            while (heapSize > 0) {
                val u = pop()
                if (done[u]) continue
                done[u] = true
                if (done[goalA] && done[goalB]) break
                for (id in g.nodes[u].edges) {
                    val ed = g.edges[id]
                    val v = ed.other(u)
                    val w = dist[u] + ed.len
                    if (!done[v] && w < dist[v] && heapSize < heap.size) {
                        dist[v] = w; prevNode[v] = u; prevEdge[v] = id
                        push(v, w)
                    }
                }
            }
            val viaA = dist[goalA] + hitB.s
            val viaB = dist[goalB] + (eb.len - hitB.s)
            val end = if (viaA <= viaB) goalA else goalB
            if (dist[end] == Float.MAX_VALUE) { points = 0; return }
            val chain = ArrayList<Int>()
            var cur = end
            while (cur >= 0) { chain += cur; cur = prevNode[cur] }
            chain.reverse()
            for ((k, c) in chain.withIndex()) {
                val nextEdge = if (k + 1 < chain.size) prevEdge[chain[k + 1]] else eb.id
                add(g.nodes[c].x, g.nodes[c].y, nextEdge)
            }
            add(hitB.x, hitB.y, -1)
            legs += Leg(ea.id, hitA.s, if (chain[0] == ea.a) 0f else ea.len, false, true)
            for (k in 0 until chain.size - 1) {
                val ed = g.edges[prevEdge[chain[k + 1]]]
                val fwd = chain[k] == ed.a
                legs += Leg(ed.id, if (fwd) 0f else ed.len, if (fwd) ed.len else 0f, true, true)
            }
            legs += Leg(eb.id, if (chain.last() == eb.a) 0f else eb.len, hitB.s, true, false)
        }
        add(dx, dy, -1)
        route = out.toFloatArray()
        segEdge = edgeOf.toIntArray()
        points = route.size / 2
        directions()
        laneRoute(px, py, dx, dy, legs)
    }

    /** A stretch of the route along road [edge], from [s0] to [s1] meters from its start, from and to a junction or not. */
    private class Leg(val edge: Int, val s0: Float, val s1: Float, val fromJunction: Boolean, val toJunction: Boolean)

    private val laneOut = ArrayList<Float>()

    /** Lays the route along the lanes: each road in the lane on the driver's side, joined by curves through the junctions. */
    private fun laneRoute(px: Float, py: Float, dx: Float, dy: Float, legs: List<Leg>) {
        val g = e.city.roads
        val left = e.city.leftHand
        val out = laneOut
        out.clear()
        fun add(x: Float, y: Float) {
            if (out.size >= 2 && abs(out[out.size - 2] - x) + abs(out[out.size - 1] - y) < .05f) return
            out += x; out += y
        }
        // (a stretch of no length, where the route starts or ends right at a junction, has no direction to go by)
        val legsUsed = legs.filter { abs(it.s1 - it.s0) > .01f }.ifEmpty { legs.take(1) }
        // to start with, the lane the player is in, if they're on their side of the first road
        var laneNo = 0
        legsUsed.firstOrNull()?.let { l ->
            val ed = g.edges[l.edge]
            val sg = if (l.s1 >= l.s0) 1f else -1f
            val side = ((px - ed.ax) * -ed.uy * sg + (py - ed.ay) * ed.ux * sg) * (if (left) -1f else 1f)
            if (side > 0f) laneNo = floor(side / ed.cls.laneWidth).toInt().coerceIn(0, ed.cls.lanes - 1)
        }
        var lastX = 0f; var lastY = 0f; var lastDx = 0f; var lastDy = 0f
        for ((i, l) in legsUsed.withIndex()) {
            val ed = g.edges[l.edge]
            val sg = if (l.s1 >= l.s0) 1f else -1f
            val ux = ed.ux * sg; val uy = ed.uy * sg
            // the lane: a turn toward the near side is made from the outer lane, toward the far side from the inner one
            legsUsed.getOrNull(i + 1)?.let { n ->
                val ne = g.edges[n.edge]
                val nsg = if (n.s1 >= n.s0) 1f else -1f
                val cross = ux * ne.uy * nsg - uy * ne.ux * nsg
                val nearSide = if (left) cross < -.5f else cross > .5f
                val farSide = if (left) cross > .5f else cross < -.5f
                if (nearSide) laneNo = ed.cls.lanes - 1 else if (farSide) laneNo = 0
            }
            val off = (laneNo.coerceIn(0, ed.cls.lanes - 1) + .5f) * ed.cls.laneWidth * (if (left) -1f else 1f)
            // right of travel in a y-down world is (-uy, ux)
            val rx = -uy * off; val ry = ux * off
            // short of the junctions at either end, where the curves take over
            var sa = l.s0; var sb = l.s1
            if (l.fromJunction) sa += sg * (if (l.s0 < ed.len * .5f) ed.clearA else ed.clearB)
            if (l.toJunction) sb -= sg * (if (l.s1 < ed.len * .5f) ed.clearA else ed.clearB)
            if ((sb - sa) * sg < 0f) { val m = (sa + sb) * .5f; sa = m; sb = m }
            val x0 = ed.ax + ed.ux * sa + rx; val y0 = ed.ay + ed.uy * sa + ry
            val x1 = ed.ax + ed.ux * sb + rx; val y1 = ed.ay + ed.uy * sb + ry
            if (i == 0) { add(px, py); add(x0, y0) } else curve(lastX, lastY, lastDx, lastDy, x0, y0, ux, uy, ::add)
            add(x1, y1)
            lastX = x1; lastY = y1; lastDx = ux; lastDy = uy
        }
        if (legsUsed.isEmpty()) add(px, py)
        add(dx, dy)
        if (lane.size < out.size) lane = FloatArray(out.size + 32)
        for (k in out.indices) lane[k] = out[k]
        lanePoints = out.size / 2
    }

    /**
     * From (x1, y1) heading (d1x, d1y) to (x2, y2) heading (d2x, d2y): a curve through the corner where the two lanes
     * would meet, or straight across when they run on (or back) in line.
     */
    private fun curve(x1: Float, y1: Float, d1x: Float, d1y: Float, x2: Float, y2: Float, d2x: Float, d2y: Float, add: (Float, Float) -> Unit) {
        val gx = x2 - x1; val gy = y2 - y1
        val gap = sqrt(gx * gx + gy * gy)
        val cr = d1x * d2y - d1y * d2x
        if (gap > .3f && abs(cr) > .17f) {
            // where the lanes cross: (x1, y1) + d1 t = (x2, y2) + d2 u, ahead of the one and behind the other
            val t = (gx * d2y - gy * d2x) / cr
            val u = (gx * d1y - gy * d1x) / cr
            if (t > 0f && u < 0f && t < gap * 2f) {
                val cx = x1 + d1x * t; val cy = y1 + d1y * t
                for (k in 1..8) {
                    val q = k / 8f; val a = (1f - q) * (1f - q); val b = 2f * q * (1f - q); val c = q * q
                    add(a * x1 + b * cx + c * x2, a * y1 + b * cy + c * y2)
                }
                return
            }
        }
        add(x2, y2)
    }

    /** Distance left and the next turn, measured along the route. */
    private fun directions() {
        var total = 0f
        for (p in 0 until points - 1) total += seg(p)
        distance = total
        turn = 0; turnDistance = total; roadName = ""
        var along = 0f
        // skip the short connector from the player to the road
        for (p in 1 until points - 1) {
            along += seg(p - 1)
            val ax = route[p * 2] - route[(p - 1) * 2]; val ay = route[p * 2 + 1] - route[(p - 1) * 2 + 1]
            val bx = route[(p + 1) * 2] - route[p * 2]; val by = route[(p + 1) * 2 + 1] - route[p * 2 + 1]
            val la = sqrt(ax * ax + ay * ay); val lb = sqrt(bx * bx + by * by)
            if (la < 2f || lb < 2f) continue
            val cross = (ax * by - ay * bx) / (la * lb)
            if (abs(cross) > .7f && p > 1) {
                turn = if (cross > 0) 1 else -1
                turnDistance = along
                roadName = e.city.roads.edges.getOrNull(segEdge.getOrElse(p) { -1 })?.name ?: ""
                return
            }
        }
    }

    private fun seg(p: Int): Float {
        val dx = route[(p + 1) * 2] - route[p * 2]; val dy = route[(p + 1) * 2 + 1] - route[p * 2 + 1]
        return sqrt(dx * dx + dy * dy)
    }

    /** The next turn in words. */
    fun instruction(): String = when {
        !active -> ""
        turn == 0 -> "Continue to your destination"
        roadName.isBlank() -> "Turn ${if (turn > 0) "right" else "left"}"
        else -> "Turn ${if (turn > 0) "right" else "left"} onto $roadName"
    }
}

package com.hustlecity.game.engine

import com.hustlecity.game.world.City
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turn-by-turn navigation to the waypoint. The streets form a grid, so the route is a shortest path over the
 * intersections (Dijkstra), joined to the player's position and to the destination's front door.
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

    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastTarget = -1
    private var clock = 0f

    private val n = City.AVENUES * City.STREETS
    private val dist = FloatArray(n)
    private val prev = IntArray(n)
    private val done = BooleanArray(n)

    private fun node(i: Int, k: Int) = k * City.AVENUES + i
    private fun nx(id: Int) = City.avenueX(id % City.AVENUES)
    private fun ny(id: Int) = City.streetY(id / City.AVENUES)

    fun clear() { points = 0; distance = 0f; lastTarget = -1 }

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
            plan(px, py, target.doorX, target.doorY, target.streetIndex)
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

    /** Where a point joins the road grid: the snapped point and the one or two intersections it can reach. */
    private class Entry(val x: Float, val y: Float, val nodes: IntArray, val costs: FloatArray)

    private fun entry(x: Float, y: Float): Entry {
        val i0 = floor(x / City.AV_SPACING).toInt().coerceIn(0, City.AVENUES - 2)
        val k0 = floor(y / City.ST_SPACING).toInt().coerceIn(0, City.STREETS - 2)
        val iN = (x / City.AV_SPACING).roundToInt().coerceIn(0, City.AVENUES - 1)
        val kN = (y / City.ST_SPACING).roundToInt().coerceIn(0, City.STREETS - 1)
        val dA = abs(x - City.avenueX(iN)); val dS = abs(y - City.streetY(kN))
        return if (dA <= dS) {
            // on (or nearest to) avenue iN, between two streets
            val yy = y.coerceIn(0f, City.HEIGHT)
            Entry(City.avenueX(iN), yy, intArrayOf(node(iN, k0), node(iN, k0 + 1)), floatArrayOf(abs(yy - City.streetY(k0)), abs(City.streetY(k0 + 1) - yy)))
        } else {
            val xx = x.coerceIn(0f, City.WIDTH)
            Entry(xx, City.streetY(kN), intArrayOf(node(i0, kN), node(i0 + 1, kN)), floatArrayOf(abs(xx - City.avenueX(i0)), abs(City.avenueX(i0 + 1) - xx)))
        }
    }

    private fun plan(px: Float, py: Float, dx: Float, dy: Float, street: Int) {
        val a = entry(px, py)
        // the door is on its building's street
        val i0 = floor(dx / City.AV_SPACING).toInt().coerceIn(0, City.AVENUES - 2)
        val bx = dx.coerceIn(0f, City.WIDTH); val by = City.streetY(street)
        val b = Entry(bx, by, intArrayOf(node(i0, street), node(i0 + 1, street)), floatArrayOf(abs(bx - City.avenueX(i0)), abs(City.avenueX(i0 + 1) - bx)))
        val out = ArrayList<Float>()
        fun add(x: Float, y: Float) {
            if (out.size >= 2 && abs(out[out.size - 2] - x) < .01f && abs(out[out.size - 1] - y) < .01f) return
            out += x; out += y
        }
        add(px, py); add(a.x, a.y)
        val sameSegment = a.nodes.contentEquals(b.nodes) || a.nodes.contentEquals(b.nodes.reversedArray())
        if (sameSegment) {
            add(b.x, b.y)
        } else {
            // Dijkstra from both ends of the start segment
            dist.fill(Float.MAX_VALUE); prev.fill(-1); done.fill(false)
            for (j in 0..1) if (a.costs[j] < dist[a.nodes[j]]) dist[a.nodes[j]] = a.costs[j]
            while (true) {
                var u = -1; var best = Float.MAX_VALUE
                for (q in 0 until n) if (!done[q] && dist[q] < best) { best = dist[q]; u = q }
                if (u < 0) break
                done[u] = true
                val ui = u % City.AVENUES; val uk = u / City.AVENUES
                fun relax(v: Int, w: Float) { if (!done[v] && dist[u] + w < dist[v]) { dist[v] = dist[u] + w; prev[v] = u } }
                if (ui > 0) relax(node(ui - 1, uk), City.AV_SPACING)
                if (ui < City.AVENUES - 1) relax(node(ui + 1, uk), City.AV_SPACING)
                if (uk > 0) relax(node(ui, uk - 1), City.ST_SPACING)
                if (uk < City.STREETS - 1) relax(node(ui, uk + 1), City.ST_SPACING)
            }
            var end = -1; var total = Float.MAX_VALUE
            for (j in 0..1) { val t = dist[b.nodes[j]] + b.costs[j]; if (t < total) { total = t; end = b.nodes[j] } }
            val chain = ArrayList<Int>()
            var cur = end
            while (cur >= 0) { chain += cur; cur = prev[cur] }
            chain.reverse()
            for (c in chain) add(nx(c), ny(c))
            add(b.x, b.y)
        }
        add(dx, dy)
        route = out.toFloatArray()
        points = route.size / 2
        directions()
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
                roadName = roadAt(route[p * 2], route[p * 2 + 1], bx, by)
                return
            }
        }
    }

    private fun seg(p: Int): Float {
        val dx = route[(p + 1) * 2] - route[p * 2]; val dy = route[(p + 1) * 2 + 1] - route[p * 2 + 1]
        return sqrt(dx * dx + dy * dy)
    }

    private fun roadAt(x: Float, y: Float, dx: Float, dy: Float): String =
        if (abs(dx) > abs(dy)) City.streetName((y / City.ST_SPACING).roundToInt().coerceIn(0, City.STREETS - 1))
        else City.avenueNames[(x / City.AV_SPACING).roundToInt().coerceIn(0, City.AVENUES - 1)]

    /** Short instruction for the banner. */
    fun instruction(): String = when {
        !active -> ""
        turn == 0 -> "Continue to your destination"
        else -> "Turn ${if (turn > 0) "right" else "left"} onto $roadName"
    }
}

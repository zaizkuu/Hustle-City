package com.hustlecity.game.world

/**
 * Where pedestrian crossings are painted: the one rule the renderer draws by and the map audit checks
 * (MapAuditTest), so paint never turns up where nobody could cross.
 *
 * A crossing runs kerb to kerb across its own road, just outside the junction. Where roads meet at a slant, or run
 * side by side, that band would lie across a second road or lead off into it, into a wall or into the river: there
 * no crossing is painted at all. Nor on a block so short that the crossings at its two ends would all but touch.
 */
object Crossings {
    /** How deep a crossing is, along its road, in meters. */
    const val DEPTH = 2.7f

    /** How crossings are painted at a junction: 0 none, 1 zebra, 2 zebra between two edge lines, 3 two thin lines. */
    fun style(city: City, n: RoadNode): Int {
        if (n.degree < 3) return 0
        return when (city.mapId) {
            Maps.NYC -> if (n.signal) 2 else 3
            Maps.CLASSIC -> if (n.signal) 1 else 3
            else -> 1
        }
    }

    private val tables = java.util.WeakHashMap<City, FloatArray>()

    /**
     * Meters along [ed] where the crossing at one end (0 = a, 1 = b) begins, or NaN when none is painted there:
     * lanes go unmarked except where they join a scramble, and so does a stub between two close junctions.
     */
    fun start(city: City, ed: RoadEdge, end: Int): Float {
        val t = synchronized(tables) { tables.getOrPut(city) { build(city) } }
        val i = ed.id * 2 + end
        return if (i in t.indices) t[i] else Float.NaN
    }

    private fun build(city: City): FloatArray {
        val g = city.roads
        val out = FloatArray((g.edges.maxOfOrNull { it.id } ?: -1) * 2 + 2) { Float.NaN }
        for (ed in g.edges) {
            if (ed.under) continue
            val s0 = ed.clearA + 3.5f; val s1 = ed.len - ed.clearB - 3.5f
            if (s1 <= s0) continue
            for (end in 0..1) {
                val node = g.nodes[if (end == 0) ed.a else ed.b]
                if (style(city, node) == 0 || (ed.cls.rank < 1 && !node.scramble)) continue
                val a = if (end == 0) ed.clearA + .5f else ed.len - ed.clearB - .5f - DEPTH
                if (a >= 0f && a + DEPTH <= ed.len && fits(city, ed, a)) out[ed.id * 2 + end] = a
            }
            val a0 = out[ed.id * 2]; val a1 = out[ed.id * 2 + 1]
            if (!a0.isNaN() && !a1.isNaN() && a1 - (a0 + DEPTH) < 3f) { out[ed.id * 2] = Float.NaN; out[ed.id * 2 + 1] = Float.NaN }
        }
        return out
    }

    /** True when a crossing starting [a] meters along [ed] lies on that road alone and has pavement to step onto at both kerbs. */
    private fun fits(city: City, ed: RoadEdge, a: Float): Boolean {
        val g = city.roads
        val hw = ed.cls.halfWidth
        val mx = ed.ax + ed.ux * (a + DEPTH / 2); val my = ed.ay + ed.uy * (a + DEPTH / 2)
        for (sd in -1..1 step 2) {
            val lx = mx + ed.uy * sd * (hw + 1.1f); val ly = my - ed.ux * sd * (hw + 1.1f)
            if (g.onAsphalt(lx, ly) || city.inWater(lx, ly)) return false
            var walled = false
            city.forBuildingsIn(lx - 1f, ly - 1f, lx + 1f, ly + 1f) { b -> if (b.solid && b.contains(lx, ly)) walled = true }
            if (walled) return false
        }
        var clear = true
        for (t in T) for (o in O) {
            val px = ed.ax + ed.ux * (a + t) + ed.uy * o * (hw - .5f); val py = ed.ay + ed.uy * (a + t) - ed.ux * o * (hw - .5f)
            g.near(px, py, 16f) { id ->
                val o2 = g.edges[id]
                if (clear && o2.id != ed.id && !o2.under && o2.name != ed.name &&
                    Geo.distToSeg(px, py, o2.ax, o2.ay, o2.bx, o2.by) < o2.cls.halfWidth - .4f) clear = false
            }
            if (!clear) return false
        }
        return true
    }

    private val T = floatArrayOf(.1f, DEPTH / 2, DEPTH - .1f)
    private val O = floatArrayOf(-1f, 0f, 1f)
}

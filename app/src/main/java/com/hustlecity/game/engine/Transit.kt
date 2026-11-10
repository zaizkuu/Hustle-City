package com.hustlecity.game.engine

import com.hustlecity.game.model.SkipKind
import com.hustlecity.game.render.Railways
import com.hustlecity.game.world.City
import com.hustlecity.game.world.RoadHit
import com.hustlecity.game.world.dist
import kotlin.math.max
import kotlin.math.roundToInt

/** A stop on one of the city's elevated lines, at the point on the street under the middle of its platform. */
class Station(val id: Int, val name: String, val line: String, val x: Float, val y: Float, val color: Int)

/** The elevated railways as a way of getting about: stations you board at on foot, a fare by distance, a ride that takes time. */
object Transit {
    /** Station names in order along each line; a line with fewer stops takes them evenly from the list. */
    private val NAMES = mapOf(
        "LRT-1" to listOf("Monumento", "Blumentritt", "Tayuman", "Doroteo Jose", "Carriedo", "Central Terminal", "United Nations", "Pedro Gil", "Vito Cruz", "Gil Puyat", "EDSA", "Baclaran"),
        "Yamanote Line" to listOf("Tokyo", "Shimbashi", "Shinagawa", "Meguro", "Ebisu", "Shibuya", "Harajuku", "Shinjuku", "Takadanobaba", "Ikebukuro", "Sugamo", "Nippori", "Ueno", "Akihabara"),
        "Chuo Line" to listOf("Nakano", "Okubo", "Yotsuya", "Ichigaya", "Iidabashi", "Suidobashi", "Ochanomizu", "Kanda"),
    )
    private var cachedFor: City? = null
    private var cached: List<Station> = emptyList()

    fun stations(city: City): List<Station> {
        if (cachedFor === city) return cached
        val out = ArrayList<Station>()
        val pt = FloatArray(4)
        for (l in Railways(city).lines) {
            val names = NAMES[l.rail.name]
            val n = l.stations.size
            for ((i, q) in l.stations.withIndex()) {
                l.at(q, pt)
                val name = when {
                    names == null -> "${l.rail.name} ${i + 1}"
                    n == 1 -> names[0]
                    l.loop -> names[i * names.size / n]
                    else -> names[i * (names.size - 1) / (n - 1)]
                }
                out += Station(out.size, name, l.rail.name, pt[0], pt[1], l.color)
            }
        }
        cachedFor = city; cached = out
        return out
    }

    /** A token, plus a little for every stop's worth of distance. */
    fun fare(a: Station, b: Station): Double = (1.0 + dist(a.x, a.y, b.x, b.y) / 500.0 * .5).let { (it * 4).roundToInt() / 4.0 }

    /** Minutes on the train, with the wait on the platform. */
    fun minutes(a: Station, b: Station): Int = max(4, 3 + (dist(a.x, a.y, b.x, b.y) / 60f).roundToInt())
}

val GameEngine.stations: List<Station> get() = Transit.stations(city)

/** The station you're standing at on foot, if any. */
fun GameEngine.nearStation(): Station? {
    val p = state.player
    if (p.inBuilding >= 0 || p.driving >= 0) return null
    return stations.filter { dist(it.x, it.y, p.x, p.y) < 30f }.minByOrNull { dist(it.x, it.y, p.x, p.y) }
}

/** Rides from the station you're at to [to] on the same line: the fare is paid, time passes, and you step off there. */
fun GameEngine.rideTrain(to: Station): Boolean {
    val from = nearStation() ?: run { toast("Walk to a station first"); return false }
    if (from.line != to.line || from.id == to.id) return false
    if (!leaveCart("You can't take the train")) return false
    if (state.player.hand.isNotEmpty()) { toast("Put your boxes down first: no freight on the train"); return false }
    if (!spend(Transit.fare(from, to), "Transport")) return false
    ui.selected = -1; ui.panel = null
    state.objectivesDone += "train"
    startSkip(SkipKind.TRAIN, Transit.minutes(from, to), "Riding the ${to.line} to ${to.name}…", cancellable = false, target = to.id)
    return true
}

/** Where you step off at [st]: on the pavement beside the street under the platform. */
fun GameEngine.stationExit(st: Station): Pair<Float, Float> {
    val hit = RoadHit()
    if (!city.roads.nearest(st.x, st.y, 40f, hit)) return st.x to st.y
    val e = city.roads.edges[hit.edge]
    val off = e.cls.halfWidth + e.cls.sidewalk * .5f
    // the nearest stretch of open pavement along the street: clear of crossings, houses and water
    for (step in 0..12) for (dir in intArrayOf(1, -1)) for (side in intArrayOf(1, -1)) {
        val along = (hit.s + dir * step * 6f).coerceIn(0f, e.len)
        val x = e.ax + e.ux * along + e.uy * off * side; val y = e.ay + e.uy * along - e.ux * off * side
        if (city.roads.nearAsphalt(x, y, .4f) || city.shore.anyWater(x, y)) continue
        var blocked = false
        city.forBuildingsIn(x - 1f, y - 1f, x + 1f, y + 1f) { b -> if (b.solid && b.contains(x, y)) blocked = true }
        if (!blocked) return x to y
    }
    return st.x to st.y
}

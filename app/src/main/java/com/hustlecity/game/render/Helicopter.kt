package com.hustlecity.game.render

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Landmark
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The police helicopter over the city: it loops around a point that wanders across the map, climbs over the towers
 * below it, and after dark sweeps the streets with a searchlight. Where it is follows from the time alone, so a
 * cinematic sees it in the same place every take; only its altitude eases between frames.
 */
class Helicopter(val city: City) {
    var x = 0f; private set
    var y = 0f; private set
    /** Height of the skids above the street. */
    var z = 0f; private set
    var heading = 0f; private set
    /** Nose down (rise per meter forward) and bank into the turn (rise per meter to the right). */
    var pitch = 0f; private set
    var roll = 0f; private set
    /** Where the searchlight falls. */
    var spotX = 0f; private set
    var spotY = 0f; private set

    private var lastT = Float.NaN
    private var alt = 0f
    private val cx = (city.bounds[0] + city.bounds[2]) * .5f
    private val cy = (city.bounds[1] + city.bounds[3]) * .5f
    private val ax = (city.bounds[2] - city.bounds[0]) * .3f
    private val ay = (city.bounds[3] - city.bounds[1]) * .3f
    private val towers = city.landmarks.filter { it.kind == Landmark.TOWER }

    private fun px(t: Float) = cx + ax * sin(t * .009f) + ORBIT * cos(t * TURN)
    private fun py(t: Float) = cy + ay * sin(t * .0071f + 1.3f) + ORBIT * sin(t * TURN)

    fun update(t: Float) {
        x = px(t); y = py(t)
        val vx = px(t + .5f) - px(t - .5f); val vy = py(t + .5f) - py(t - .5f)
        heading = atan2(vx, -vy)
        // bank by how fast the heading turns, nose down by the speed
        val v2x = px(t + 1.5f) - px(t + .5f); val v2y = py(t + 1.5f) - py(t + .5f)
        val turn = wrap(atan2(v2x, -v2y) - heading)
        roll = (turn * 1.6f).coerceIn(-.32f, .32f)
        pitch = -(sqrt(vx * vx + vy * vy) * .004f).coerceAtMost(.12f)
        // high enough to clear whatever stands below and just ahead
        var top = 0f
        city.forBuildingsIn(x - 60f, y - 60f, x + 60f, y + 60f) { b -> if (b.height > top) top = b.height }
        for (l in towers) if ((l.x - x) * (l.x - x) + (l.y - y) * (l.y - y) < 150f * 150f) top = max(top, 240f * l.size)
        val want = max(85f, top + 35f)
        alt = if (lastT.isNaN() || t < lastT || t - lastT > 3f) want else alt + (want - alt) * (1f - exp(-(t - lastT) * .6f))
        lastT = t
        z = alt
        // the searchlight sweeps from side to side a little way ahead
        val a = heading + sin(t * .45f) * .9f
        spotX = x + sin(a) * 38f; spotY = y - cos(a) * 38f
    }

    /** Turns the searchlight on ([sx], [sy]) instead, until the next [update]. */
    fun aim(sx: Float, sy: Float) { spotX = sx; spotY = sy }

    private fun wrap(a: Float): Float {
        var r = a
        while (r > Math.PI) r -= (2 * Math.PI).toFloat()
        while (r < -Math.PI) r += (2 * Math.PI).toFloat()
        return r
    }

    companion object {
        private const val ORBIT = 120f
        /** Radians per second around the loop: about once a minute. */
        private const val TURN = .1f
    }
}

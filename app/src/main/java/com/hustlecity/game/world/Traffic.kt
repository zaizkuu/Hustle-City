package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Shared lane-following agent for cars (on roads) and pedestrians (on sidewalks). */
class Agent {
    var x = 0f
    var y = 0f
    var heading = 0f
    var speed = 0f
    var cruise = 10f
    /** 0 = moving along an avenue (y axis), 1 = along a street (x axis). */
    var axis = 0
    var line = 0
    var dir = 1
    var offset = 2.5f
    var color = 0
    var length = 4.2f
    var width = 1.8f
    var height = 1.45f
    var style = 0
    var lastCross = -999
    var phase = 0f
}

class Traffic(private val rnd: Random = Random(7)) {
    val cars = ArrayList<Agent>()
    val peds = ArrayList<Agent>()
    var lightClock = 0f

    private val carColors = intArrayOf(
        c(0xFFC0392B), c(0xFFE0E0E0), c(0xFF2C3E50), c(0xFF7F8C8D), c(0xFF1F3A93), c(0xFFF2F2F2), c(0xFF8E2442),
        c(0xFF212121), c(0xFFB0B7BF), c(0xFFF2C94C), c(0xFF27AE60), c(0xFF6D4C41),
    )
    private val shirtColors = intArrayOf(
        c(0xFFEB5757), c(0xFF2F80ED), c(0xFF27AE60), c(0xFFF2C94C), c(0xFF9B51E0), c(0xFFF2994A), c(0xFFE0E0E0), c(0xFF333333), c(0xFF56CCF2),
    )

    fun ensure(carCount: Int, pedCount: Int) {
        while (cars.size < carCount) cars += Agent().also { it.lastCross = Int.MIN_VALUE }
        while (cars.size > carCount) cars.removeAt(cars.size - 1)
        while (peds.size < pedCount) peds += Agent().also { it.lastCross = Int.MIN_VALUE }
        while (peds.size > pedCount) peds.removeAt(peds.size - 1)
    }

    /** Light state for an axis at intersections: 0 green, 1 yellow, 2 red. */
    fun light(axis: Int): Int {
        val t = lightClock % 40f
        return if (axis == 0) when { t < 20f -> 0; t < 23f -> 1; else -> 2 }
        else when { t in 23f..37f -> 0; t > 37f -> 1; else -> 2 }
    }

    private fun laneCoord(a: Agent): Float = if (a.axis == 0) {
        City.avenueX(a.line) + (if (a.dir < 0) a.offset else -a.offset)
    } else {
        City.streetY(a.line) + (if (a.dir > 0) a.offset else -a.offset)
    }

    private fun spawnCar(a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean) {
        spawn(a, fx, fy, radius, initial, true)
        a.color = carColors[rnd.nextInt(carColors.size)]
        val kind = rnd.nextInt(10)
        a.style = when { kind < 6 -> 0; kind < 8 -> 1; else -> 2 }
        a.length = when (a.style) { 0 -> 4.2f + rnd.nextFloat() * .6f; 1 -> 5.2f; else -> 5.8f }
        a.width = if (a.style == 2) 2.1f else 1.85f
        a.height = when (a.style) { 0 -> 1.45f; 1 -> 1.8f; else -> 2.5f }
        a.cruise = (if (a.axis == 0) 12f else 9f) + rnd.nextFloat() * 3f
        a.speed = a.cruise * .6f
    }

    private fun spawnPed(a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean) {
        spawn(a, fx, fy, radius, initial, false)
        a.color = shirtColors[rnd.nextInt(shirtColors.size)]
        a.cruise = 1.1f + rnd.nextFloat() * .6f
        a.speed = a.cruise
        a.phase = rnd.nextFloat() * 6f
    }

    private fun spawn(a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean, car: Boolean) {
        for (attempt in 0 until 12) {
            a.axis = rnd.nextInt(2)
            a.dir = if (rnd.nextBoolean()) 1 else -1
            if (a.axis == 0) {
                val i0 = floor((fx - radius) / City.AV_SPACING).toInt().coerceIn(0, City.AVENUES - 1)
                val i1 = ceil((fx + radius) / City.AV_SPACING).toInt().coerceIn(0, City.AVENUES - 1)
                a.line = rnd.nextInt(i0, i1 + 1)
                a.offset = if (car) (if (rnd.nextBoolean()) 2.6f else 7.4f) else 12f
                a.x = laneCoord(a)
                a.y = (fy + (rnd.nextFloat() * 2f - 1f) * radius).coerceIn(0f, City.HEIGHT)
                a.heading = if (a.dir < 0) 0f else Math.PI.toFloat()
            } else {
                val k0 = floor((fy - radius) / City.ST_SPACING).toInt().coerceIn(0, City.STREETS - 1)
                val k1 = ceil((fy + radius) / City.ST_SPACING).toInt().coerceIn(0, City.STREETS - 1)
                a.line = rnd.nextInt(k0, k1 + 1)
                a.offset = if (car) 3f else 8.1f
                a.y = laneCoord(a)
                a.x = (fx + (rnd.nextFloat() * 2f - 1f) * radius).coerceIn(0f, City.WIDTH)
                a.heading = if (a.dir > 0) (Math.PI / 2).toFloat() else (-Math.PI / 2).toFloat()
            }
            a.lastCross = Int.MIN_VALUE
            val d = dist(a.x, a.y, fx, fy)
            if (initial || d > radius * .55f) return
        }
    }

    /**
     * @param fx focus point (camera target)
     * @param blockers extra obstacles as x,y pairs (player / player car)
     */
    fun update(dt: Float, fx: Float, fy: Float, radius: Float, blockerX: Float, blockerY: Float, initial: Boolean = false) {
        lightClock += dt
        for (a in cars) {
            if (initial || a.lastCross == Int.MIN_VALUE || dist(a.x, a.y, fx, fy) > radius) spawnCar(a, fx, fy, radius, initial || a.lastCross == Int.MIN_VALUE)
            stepCar(a, dt, blockerX, blockerY)
        }
        for (p in peds) {
            if (initial || p.lastCross == Int.MIN_VALUE || dist(p.x, p.y, fx, fy) > radius * .7f) spawnPed(p, fx, fy, radius * .7f, initial || p.lastCross == Int.MIN_VALUE)
            stepPed(p, dt)
        }
    }

    private fun crossSpacing(a: Agent) = if (a.axis == 0) City.ST_SPACING else City.AV_SPACING
    private fun crossMax(a: Agent) = if (a.axis == 0) City.STREETS - 1 else City.AVENUES - 1
    private fun along(a: Agent) = if (a.axis == 0) a.y else a.x

    private fun nextCrossIndex(a: Agent): Int {
        val s = along(a) / crossSpacing(a)
        return if (a.dir > 0) floor(s + 1e-3f).toInt() + 1 else ceil(s - 1e-3f).toInt() - 1
    }

    private fun decideTurn(a: Agent, cross: Int, car: Boolean) {
        val canStraight = (cross + a.dir) in 0..crossMax(a)
        val options = ArrayList<Int>(3) // 0 straight, 1 right, 2 left
        if (canStraight) { options += 0; options += 0; if (!car) options += 0 }
        // turning puts the agent onto the crossing line
        val crossAxis = 1 - a.axis
        // crossings along the new road are lines of the current axis
        val maxLine = if (crossAxis == 1) City.AVENUES - 1 else City.STREETS - 1
        for (t in 1..2) {
            val newDir = turnDir(a, t)
            if ((a.line + newDir) in 0..maxLine) options += t
        }
        val choice = if (options.isEmpty()) 1 else options[rnd.nextInt(options.size)]
        if (choice == 0) return
        val newDir = turnDir(a, choice)
        a.axis = crossAxis
        a.line = cross
        a.dir = newDir
        if (car) a.offset = if (crossAxis == 0) (if (rnd.nextBoolean()) 2.6f else 7.4f) else 3f
        else a.offset = if (crossAxis == 0) 12f else 8.1f
    }

    /** Direction along the crossing road after a right (1) or left (2) turn. */
    private fun turnDir(a: Agent, turn: Int): Int {
        return if (a.axis == 0) { if (turn == 1) -a.dir else a.dir } else { if (turn == 1) a.dir else -a.dir }
    }

    private fun stepCar(a: Agent, dt: Float, bx: Float, by: Float) {
        val sp = crossSpacing(a)
        val cross = nextCrossIndex(a)
        val crossPos = cross * sp
        val s = along(a)
        val toCross = (crossPos - s) * a.dir
        // turn decision once we reach the intersection centre
        if (toCross <= 0.5f && a.lastCross != cross * 100 + a.line) {
            a.lastCross = cross * 100 + a.line
            if (cross in 0..crossMax(a)) decideTurn(a, cross, true)
        }
        var target = a.cruise
        // traffic lights: stop line just before the crosswalk
        val stopDist = toCross - (if (a.axis == 0) City.ST_HALF + 4.5f else City.AV_HALF + 4.5f)
        if (cross in 0..crossMax(a) && stopDist in 0f..22f && light(a.axis) != 0) {
            target = min(target, (stopDist - 1f).coerceAtLeast(0f) * .9f)
        }
        // keep distance to the car ahead
        val fwdX = sin(a.heading); val fwdY = -cos(a.heading)
        for (o in cars) {
            if (o === a) continue
            val dx = o.x - a.x; val dy = o.y - a.y
            if (abs(dx) > 16f || abs(dy) > 16f) continue
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 13f && side < 1.6f) target = min(target, (ahead - 6f).coerceAtLeast(0f) * 1.2f)
        }
        run {
            val dx = bx - a.x; val dy = by - a.y
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 12f && side < 2.2f) target = min(target, (ahead - 5f).coerceAtLeast(0f))
        }
        a.speed += ((target - a.speed).coerceIn(-14f * dt, 5f * dt))
        steer(a, dt, 7f, 2.2f)
    }

    private fun stepPed(a: Agent, dt: Float) {
        val sp = crossSpacing(a)
        val cross = nextCrossIndex(a)
        val crossPos = cross * sp
        val toCross = (crossPos - along(a)) * a.dir
        val sideOffset = if (a.axis == 0) 12f else 8.1f
        if (toCross <= sideOffset && a.lastCross != cross * 100 + a.line) {
            a.lastCross = cross * 100 + a.line
            if (cross in 0..crossMax(a)) {
                if (rnd.nextFloat() < .4f) decideTurn(a, cross, false)
            } else {
                a.dir = -a.dir
            }
        }
        a.phase += dt * a.speed * 3f
        steer(a, dt, 2.5f, 5f)
    }

    private fun steer(a: Agent, dt: Float, look: Float, turnRate: Float) {
        val lc = laneCoord(a)
        val tx: Float; val ty: Float
        if (a.axis == 0) { tx = lc; ty = a.y + a.dir * look } else { tx = a.x + a.dir * look; ty = lc }
        val desired = atan2(tx - a.x, -(ty - a.y))
        var diff = desired - a.heading
        while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
        while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
        val maxTurn = turnRate * dt * (0.3f + min(1f, a.speed / 6f))
        a.heading += diff.coerceIn(-maxTurn, maxTurn)
        a.x += sin(a.heading) * a.speed * dt
        a.y -= cos(a.heading) * a.speed * dt
    }

    /** Pushes a circle out of NPC cars. Returns true on contact. */
    fun bump(x: Float, y: Float, r: Float, out: FloatArray): Boolean {
        var px = x; var py = y; var hit = false
        for (a in cars) {
            val dx = px - a.x; val dy = py - a.y
            val minD = r + a.width * .6f
            val d2 = dx * dx + dy * dy
            if (d2 < minD * minD && d2 > 1e-4f) {
                val d = sqrt(d2)
                px = a.x + dx / d * minD
                py = a.y + dy / d * minD
                hit = true
            }
        }
        out[0] = px; out[1] = py
        return hit
    }
}

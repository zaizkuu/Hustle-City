package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
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
    /** Planned move at the next intersection: -1 undecided, 0 straight, 1 right, 2 left. */
    var plan = -1
    var planCross = -1
    /** Pedestrian state: 0 walking, 1 waiting at the curb, 2 crossing, 3 entering a shop, 4 inside, 5 leaving the shop. */
    var state = 0
    var timer = 0f
    /** Personal sideways spread on the sidewalk, and a temporary side-step to pass someone. */
    var jitter = 0f
    var dodge = 0f
    var tx = 0f
    var ty = 0f
    var hidden = false
    /** Seconds a car has been held up by cross traffic (used to untangle jams in intersections). */
    var blocked = 0f
}

class Traffic(private val rnd: Random = Random(7)) {
    val cars = ArrayList<Agent>()
    /** Parked player vehicles (x, y pairs) that NPC cars stop for. */
    var obstacles = FloatArray(0)
    var obstacleCount = 0
    /** Shop doors near the camera (x, y, chance that a passer-by walks in, +1/-1 = building lies toward +y/-y), kept fresh by the engine. */
    var doors = FloatArray(0)
    var doorCount = 0
    val peds = ArrayList<Agent>()
    var lightClock = 0f
    /** Weather: cars drive slower on wet or snowy roads, pedestrians walk faster in the rain. */
    var speedFactor = 1f
    var hurry = 1f

    /** Eases the number of pedestrians toward [n]: newcomers walk in from the edge, leavers vanish off-screen. */
    fun setPedTarget(n: Int, fx: Float, fy: Float) {
        if (peds.size < n) peds += Agent().also { it.lastCross = EDGE }
        else if (peds.size > n) {
            val far = peds.filter { !it.hidden }.maxByOrNull { dist(it.x, it.y, fx, fy) } ?: return
            if (dist(far.x, far.y, fx, fy) > 60f) peds.remove(far)
        }
    }

    private companion object {
        /** Spawn at the edge of the view (not anywhere). */
        const val EDGE = Int.MIN_VALUE + 5
    }

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

    /** Seconds until traffic on [axis] may move again (0 when it already has green or yellow). */
    private fun redLeft(axis: Int): Float {
        val t = lightClock % 40f
        return if (axis == 0) (if (t >= 23f) 40f - t else 0f) else (if (t < 23f) 23f - t else 0f)
    }

    private fun laneCoord(a: Agent): Float = if (a.axis == 0) {
        City.avenueX(a.line) + (if (a.dir < 0) a.offset else -a.offset)
    } else {
        City.streetY(a.line) + (if (a.dir > 0) a.offset else -a.offset)
    }

    /** Sideways direction to the agent's right, along the cross axis. */
    private fun rightSign(a: Agent) = if (a.axis == 0) -a.dir else a.dir

    private fun pedOffset(axis: Int) = if (axis == 0) 12f else 8.1f
    private fun carOffset(axis: Int) = if (axis == 0) (if (rnd.nextBoolean()) 2.6f else 7.4f) else 3f

    private fun spawnCar(a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean) {
        spawn(a, fx, fy, radius, initial, true)
        a.color = carColors[rnd.nextInt(carColors.size)]
        val kind = rnd.nextInt(10)
        a.style = when { kind < 6 -> 0; kind < 8 -> 1; else -> 2 }
        a.length = when (a.style) { 0 -> 4.2f + rnd.nextFloat() * .6f; 1 -> 5.2f; else -> 5.8f }
        a.width = if (a.style == 2) 2.1f else 1.85f
        a.height = when (a.style) { 0 -> 1.45f; 1 -> 1.8f; else -> 2.5f }
        // city speeds: about 30-38 km/h on avenues, 25-32 km/h on side streets
        a.cruise = (if (a.axis == 0) 8.5f else 7f) + rnd.nextFloat() * 2f
        a.speed = a.cruise * .6f
        a.plan = -1
    }

    private fun spawnPed(a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean) {
        spawn(a, fx, fy, radius, initial, false)
        a.color = shirtColors[rnd.nextInt(shirtColors.size)]
        a.cruise = 1.1f + rnd.nextFloat() * .6f
        a.speed = a.cruise
        a.phase = rnd.nextFloat() * 6f
        a.jitter = (rnd.nextFloat() * 2f - 1f) * 1.1f
        a.offset = pedOffset(a.axis) + a.jitter
        a.x = if (a.axis == 0) laneCoord(a) else a.x
        a.y = if (a.axis == 1) laneCoord(a) else a.y
        a.dodge = 0f
        a.state = 0
        a.hidden = false
        a.plan = -1
    }

    private fun spawn(a: Agent, fx: Float, fy: Float, radius: Float, initial: Boolean, car: Boolean) {
        for (attempt in 0 until 12) {
            a.axis = rnd.nextInt(2)
            a.dir = if (rnd.nextBoolean()) 1 else -1
            if (a.axis == 0) {
                val i0 = floor((fx - radius) / City.AV_SPACING).toInt().coerceIn(0, City.AVENUES - 1)
                val i1 = ceil((fx + radius) / City.AV_SPACING).toInt().coerceIn(0, City.AVENUES - 1)
                a.line = rnd.nextInt(i0, i1 + 1)
                a.offset = if (car) carOffset(0) else pedOffset(0)
                a.x = laneCoord(a)
                a.y = (fy + (rnd.nextFloat() * 2f - 1f) * radius).coerceIn(0f, City.HEIGHT)
                a.heading = if (a.dir < 0) 0f else Math.PI.toFloat()
            } else {
                val k0 = floor((fy - radius) / City.ST_SPACING).toInt().coerceIn(0, City.STREETS - 1)
                val k1 = ceil((fy + radius) / City.ST_SPACING).toInt().coerceIn(0, City.STREETS - 1)
                a.line = rnd.nextInt(k0, k1 + 1)
                a.offset = if (car) carOffset(1) else pedOffset(1)
                a.y = laneCoord(a)
                a.x = (fx + (rnd.nextFloat() * 2f - 1f) * radius).coerceIn(0f, City.WIDTH)
                a.heading = if (a.dir > 0) (Math.PI / 2).toFloat() else (-Math.PI / 2).toFloat()
            }
            a.lastCross = Int.MIN_VALUE + 1
            val d = dist(a.x, a.y, fx, fy)
            if (initial || d > radius * .55f) return
        }
    }

    /**
     * @param fx focus point (camera target)
     * @param blockerX the player (or the player's car), which NPC cars stop for
     */
    fun update(dt: Float, fx: Float, fy: Float, radius: Float, blockerX: Float, blockerY: Float, initial: Boolean = false) {
        lightClock += dt
        for (a in cars) {
            if (initial || a.lastCross == Int.MIN_VALUE || dist(a.x, a.y, fx, fy) > radius) spawnCar(a, fx, fy, radius, initial || a.lastCross == Int.MIN_VALUE)
            stepCar(a, dt, blockerX, blockerY)
        }
        for (p in peds) {
            if (initial || p.lastCross == Int.MIN_VALUE || p.lastCross == EDGE || (dist(p.x, p.y, fx, fy) > radius * .7f && p.state != 4)) spawnPed(p, fx, fy, radius * .7f, initial || p.lastCross == Int.MIN_VALUE)
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

    /** Picks straight (0), right (1) or left (2) at a crossing; roads that leave the map aren't options. */
    private fun chooseTurn(a: Agent, cross: Int, car: Boolean): Int {
        val canStraight = (cross + a.dir) in 0..crossMax(a)
        val options = ArrayList<Int>(4)
        if (canStraight) { options += 0; options += 0; if (!car) options += 0 }
        val crossAxis = 1 - a.axis
        // crossings along the new road are lines of the current axis
        val maxLine = if (crossAxis == 1) City.AVENUES - 1 else City.STREETS - 1
        for (t in 1..2) if ((a.line + turnDir(a, t)) in 0..maxLine) options += t
        return if (options.isEmpty()) 1 else options[rnd.nextInt(options.size)]
    }

    private fun applyTurn(a: Agent, cross: Int, turn: Int, car: Boolean) {
        val newDir = turnDir(a, turn)
        val leaving = a.line
        a.axis = 1 - a.axis
        a.line = cross
        a.dir = newDir
        a.offset = if (car) carOffset(a.axis) else pedOffset(a.axis) + a.jitter
        // the road we just left is now the nearest crossing: go straight over it, don't turn again
        a.planCross = leaving
        a.plan = 0
    }

    /** Direction along the crossing road after a right (1) or left (2) turn. */
    private fun turnDir(a: Agent, turn: Int): Int {
        return if (a.axis == 0) { if (turn == 1) -a.dir else a.dir } else { if (turn == 1) a.dir else -a.dir }
    }

    /** Where (along the current road) the lane of the road we turn onto lies. */
    private fun turnPoint(a: Agent, cross: Int, turn: Int, offset: Float): Float {
        val newDir = turnDir(a, turn)
        return if (a.axis == 0) City.streetY(cross) + (if (newDir > 0) offset else -offset)
        else City.avenueX(cross) + (if (newDir < 0) offset else -offset)
    }

    // ------------------------------------------------------------------ cars

    private fun stepCar(a: Agent, dt: Float, bx: Float, by: Float) {
        val sp = crossSpacing(a)
        val cross = nextCrossIndex(a)
        val s = along(a)
        val toCross = (cross * sp - s) * a.dir
        val inRange = cross in 0..crossMax(a)
        // plan the turn early so the car can slow down and take a smooth arc into the new lane
        if (inRange && a.planCross != cross && toCross < 40f) {
            a.planCross = cross
            a.plan = chooseTurn(a, cross, true)
            a.timer = if (a.plan == 0) 0f else (if (a.axis == 0) 3f else if (rnd.nextBoolean()) 2.6f else 7.4f)
        }
        var target = a.cruise * speedFactor
        val turning = inRange && a.planCross == cross && a.plan > 0
        if (turning) {
            // the new lane's position along this road; start the arc a few meters before it
            val turnAt = turnPoint(a, cross, a.plan, a.timer)
            val toTurn = (turnAt - s) * a.dir
            val radius = if (a.plan == 1) 4f else 6.5f
            target = min(target, if (toTurn < 18f) (if (a.plan == 1) 5.5f else 7f) else target)
            if (toTurn <= radius) {
                val off = a.timer
                applyTurn(a, cross, a.plan, true)
                a.offset = off
            }
        }
        // traffic lights: stop line just before the crosswalk (unless already turning inside the box)
        val stopDist = toCross - (if (a.axis == 0) City.ST_HALF + 4.5f else City.AV_HALF + 4.5f)
        if (inRange && stopDist in 0f..24f && light(a.axis) != 0) {
            // on yellow, go on if too close to stop comfortably
            val canStop = light(a.axis) == 2 || stopDist > a.speed * a.speed / 16f
            if (canStop) target = min(target, (stopDist - 1f).coerceAtLeast(0f) * .9f)
        }
        val fwdX = sin(a.heading); val fwdY = -cos(a.heading)
        // keep distance to the car ahead; give way to cross traffic in the intersection, but if two cars
        // block each other for a few seconds the waiting one creeps through so the junction never locks up
        var heldByCross = false
        for (o in cars) {
            if (o === a) continue
            val dx = o.x - a.x; val dy = o.y - a.y
            if (abs(dx) > 18f || abs(dy) > 18f) continue
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead <= 0 || ahead >= 15f || side >= 1.7f + (o.width - 1.8f)) continue
            val align = fwdX * sin(o.heading) - fwdY * cos(o.heading)
            if (align < .6f) {
                if (a.blocked > 2.5f) continue
                heldByCross = true
            }
            target = min(target, (ahead - (a.length + o.length) * .5f - 2f).coerceAtLeast(0f) * 1.3f)
        }
        a.blocked = if (heldByCross && a.speed < .5f) a.blocked + dt else if (a.blocked > 2.5f && a.blocked < 6f) a.blocked + dt else 0f
        // yield to pedestrians on the crosswalk
        for (p in peds) {
            if (p.hidden) continue
            val dx = p.x - a.x; val dy = p.y - a.y
            if (abs(dx) > 12f || abs(dy) > 12f) continue
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 10f && side < 1.6f) target = min(target, (ahead - a.length * .5f - 1.5f).coerceAtLeast(0f) * 1.2f)
        }
        run {
            val dx = bx - a.x; val dy = by - a.y
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 12f && side < 2.2f) target = min(target, (ahead - 5f).coerceAtLeast(0f))
        }
        for (k in 0 until obstacleCount) {
            val dx = obstacles[k * 2] - a.x; val dy = obstacles[k * 2 + 1] - a.y
            if (abs(dx) > 14f || abs(dy) > 14f) continue
            val ahead = dx * fwdX + dy * fwdY
            val side = abs(-dx * fwdY + dy * fwdX)
            if (ahead > 0 && ahead < 12f && side < 2.2f) target = min(target, (ahead - 5.5f).coerceAtLeast(0f))
        }
        // gentle acceleration, firm braking
        val brake = if (target < a.speed - 4f) 16f else 9f
        a.speed += (target - a.speed).coerceIn(-brake * dt, 4.5f * dt)
        if (a.speed < .05f && target <= 0f) a.speed = 0f
        steer(a, dt, if (turning) 5f else 8f, 2.4f)
    }

    // ------------------------------------------------------------------ pedestrians

    private fun stepPed(a: Agent, dt: Float) {
        when (a.state) {
            3 -> { // walking in through a shop door
                if (walkTo(a, dt, a.tx, a.ty, a.cruise)) { a.state = 4; a.hidden = true; a.timer = 6f + rnd.nextFloat() * 22f }
                return
            }
            4 -> { // shopping inside
                a.timer -= dt
                if (a.timer <= 0f) {
                    a.hidden = false
                    a.state = 5
                    a.dir = if (rnd.nextBoolean()) 1 else -1
                    a.heading += Math.PI.toFloat()
                }
                return
            }
            5 -> { // back out onto the sidewalk
                val lc = laneCoord(a) + rightSign(a) * a.dodge
                val ox = if (a.axis == 0) lc else a.x
                val oy = if (a.axis == 1) lc else a.y
                if (walkTo(a, dt, ox, oy, a.cruise)) { a.state = 0; a.timer = 8f }
                return
            }
        }
        val sp = crossSpacing(a)
        val cross = nextCrossIndex(a)
        val toCross = (cross * sp - along(a)) * a.dir
        val inRange = cross in 0..crossMax(a)
        // the corner where this sidewalk meets the cross street's sidewalk
        val corner = pedOffset(1 - a.axis)
        val crossHalf = if (a.axis == 0) City.ST_HALF else City.AV_HALF
        if (a.planCross != cross && toCross < corner + 6f) {
            a.planCross = cross
            a.plan = if (!inRange) 3 else if (rnd.nextFloat() < .4f) chooseTurn(a, cross, false) else 0
            if (a.plan == 0 && !((cross + a.dir) in 0..crossMax(a))) a.plan = 1
        }
        var speed = a.cruise * hurry
        if (a.state == 2) {
            // on the crosswalk: done once past the far curb; hurry if the light is about to change
            if ((along(a) - a.tx) * a.dir > 0f) a.state = 0
            else if (redLeft(1 - a.axis) < 3f) speed = a.cruise * 1.7f
        }
        if (a.planCross == cross && a.state != 2) when (a.plan) {
            3 -> if (toCross <= corner) { a.dir = -a.dir; a.planCross = -1; a.heading += Math.PI.toFloat() } // map edge: turn around
            1, 2 -> if (toCross <= corner + a.jitter * (if (a.plan == 1) -1f else 1f)) applyTurn(a, cross, a.plan, false)
            0 -> if (inRange) {
                // cross the road on the walk signal: wait at the curb
                val cur = toCross - (crossHalf + .6f)
                val crossTime = (crossHalf * 2f + 1.5f) / a.cruise
                if (cur <= 1.2f && cur > -1f) {
                    if (redLeft(1 - a.axis) > crossTime * .7f) {
                        a.state = 2
                        a.tx = cross * sp + a.dir * (crossHalf + .5f)
                    } else {
                        a.state = 1
                        speed = 0f
                        if (cur < .4f) a.speed = 0f
                    }
                } else if (cur <= -1f && cur > -crossHalf * 2f) {
                    // already on the road (just spawned or turned): keep going
                    a.state = 2
                    a.tx = cross * sp + a.dir * (crossHalf + .5f)
                }
            }
        }
        // make room: step aside for people coming the other way, slow behind slower walkers
        var nearest = 99f
        for (o in peds) {
            if (o === a || o.hidden) continue
            val dx = o.x - a.x; val dy = o.y - a.y
            if (abs(dx) > 3f || abs(dy) > 3f) continue
            val fx = sin(a.heading); val fy = -cos(a.heading)
            val ahead = dx * fx + dy * fy
            val side = abs(-dx * fy + dy * fx)
            if (ahead <= 0f || ahead > 2.5f || side > .8f) continue
            val facing = fx * sin(o.heading) - fy * cos(o.heading)
            if (facing < 0f) a.dodge = min(a.dodge + dt * 2.2f, .9f)
            else if (o.speed < speed) { if (ahead < 1.1f) speed = min(speed, o.speed) ; a.dodge = min(a.dodge + dt * 1.2f, .9f) }
            nearest = min(nearest, ahead)
        }
        if (nearest > 2.5f) a.dodge = max(0f, a.dodge - dt * .6f)
        // drop into an open shop now and then
        if (a.state == 0) {
            a.timer -= dt
            if (a.timer <= 0f) {
                a.timer = .5f
                for (k in 0 until doorCount) {
                    val dx = doors[k * 4]; val dy = doors[k * 4 + 1]
                    if (abs(dx - a.x) < 1.4f && abs(dy - a.y) < 3.5f && rnd.nextFloat() < doors[k * 4 + 2]) {
                        a.state = 3
                        // a couple of meters past the door step is inside the building
                        a.tx = dx; a.ty = dy + doors[k * 4 + 3] * 2.4f
                        break
                    }
                }
            }
        }
        a.speed += (speed - a.speed).coerceIn(-4f * dt, 3f * dt)
        a.phase += dt * a.speed * 3f
        steer(a, dt, 2.5f, 6f, rightSign(a) * a.dodge)
    }

    /** Walks straight toward a point; true on arrival. */
    private fun walkTo(a: Agent, dt: Float, tx: Float, ty: Float, speed: Float): Boolean {
        val dx = tx - a.x; val dy = ty - a.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < .15f) return true
        a.heading = atan2(dx, -dy)
        val step = min(d, speed * dt)
        a.x += dx / d * step; a.y += dy / d * step
        a.speed = speed
        a.phase += dt * speed * 3f
        return false
    }

    private fun steer(a: Agent, dt: Float, look: Float, turnRate: Float, lateral: Float = 0f) {
        val lc = laneCoord(a) + lateral
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

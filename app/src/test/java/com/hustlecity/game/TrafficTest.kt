package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Headless traffic run: cars keep moving without crashing, pedestrians walk smoothly and use the crossings. */
class TrafficTest {
    @Test
    fun trafficFlowsSmoothly() {
        val t = Traffic(Random(3))
        t.ensure(36, 50)
        val fx = City.avenueX(3).toFloat() - 40f
        val fy = City.streetY(8).toFloat() + 20f
        t.update(0f, fx, fy, 230f, -9999f, -9999f, initial = true)
        // a few shop doors along a street near the focus
        val street = City.streetY(8) + 8.4f
        t.doors = floatArrayOf(fx - 30f, street, .3f, 1f, fx + 10f, street, .3f, 1f, fx + 50f, street, .3f, 1f)
        t.doorCount = 3
        val dt = 1f / 30f
        val px = FloatArray(t.cars.size); val py = FloatArray(t.cars.size)
        val qx = FloatArray(t.peds.size); val qy = FloatArray(t.peds.size)
        var carJumps = 0; var pedJumps = 0; var overlaps = 0; var frames = 0
        var movingSum = 0.0; var jaywalk = 0; var pedFrames = 0; var entered = 0
        val stuck = IntArray(t.cars.size)
        var maxStuck = 0
        for (f in 0 until 30 * 240) {
            for ((i, a) in t.cars.withIndex()) { px[i] = a.x; py[i] = a.y }
            for ((i, a) in t.peds.withIndex()) { qx[i] = a.x; qy[i] = a.y }
            t.update(dt, fx, fy, 230f, -9999f, -9999f)
            frames++
            for ((i, a) in t.cars.withIndex()) {
                val d = sqrt((a.x - px[i]) * (a.x - px[i]) + (a.y - py[i]) * (a.y - py[i]))
                // a respawn far away is fine; a short hop is a glitch
                if (d > 1.5f && d < 60f) carJumps++
                movingSum += a.speed
                if (a.speed < .2f) { stuck[i]++; maxStuck = maxOf(maxStuck, stuck[i]) } else stuck[i] = 0
                if (stuck[i] == 30 * 45) {
                    println("STUCK car $i at (%.1f,%.1f) axis=${a.axis} line=${a.line} dir=${a.dir} off=${a.offset} plan=${a.plan}/${a.planCross} heading=%.2f light=${t.light(a.axis)}".format(a.x, a.y, a.heading))
                    for (o in t.cars) if (o !== a && abs(o.x - a.x) < 15 && abs(o.y - a.y) < 15) println("   car (%.1f,%.1f) axis=${o.axis} dir=${o.dir} speed=%.1f heading=%.2f".format(o.x, o.y, o.speed, o.heading))
                    for (o in t.peds) if (abs(o.x - a.x) < 12 && abs(o.y - a.y) < 12) println("   ped (%.1f,%.1f) state=${o.state} hidden=${o.hidden}".format(o.x, o.y))
                }
            }
            for ((i, a) in t.peds.withIndex()) {
                if (a.hidden) continue
                val d = sqrt((a.x - qx[i]) * (a.x - qx[i]) + (a.y - qy[i]) * (a.y - qy[i]))
                if (d > .5f && d < 40f) pedJumps++
                if (a.state == 3) entered++
                pedFrames++
                // in the roadway of a street while that street's traffic has green, and not on a crossing walk
                val k = Math.round(a.y / City.ST_SPACING)
                val inStreet = abs(a.y - City.streetY(k)) < City.ST_HALF - .5f
                val avenue = Math.round(a.x / City.AV_SPACING)
                val atCorner = abs(a.x - City.avenueX(avenue)) < City.AV_HALF + City.SIDEWALK + 2f
                if (inStreet && !atCorner && a.state != 3 && a.state != 5) jaywalk++
            }
            for (i in t.cars.indices) for (j in i + 1 until t.cars.size) {
                val a = t.cars[i]; val b = t.cars[j]
                if (abs(a.x - b.x) < 1.2f && abs(a.y - b.y) < 1.2f) overlaps++
            }
        }
        val avgSpeed = movingSum / (frames * t.cars.size)
        println("car jumps=$carJumps ped jumps=$pedJumps overlaps=$overlaps avg speed=%.1f m/s longest stop=%.0f s jaywalk frames=$jaywalk/$pedFrames shop entries=$entered".format(avgSpeed, maxStuck / 30.0))
        assertTrue("cars teleport ($carJumps)", carJumps < 20)
        assertTrue("pedestrians teleport ($pedJumps)", pedJumps < 40)
        assertTrue("cars drive through each other ($overlaps)", overlaps < frames / 20)
        assertTrue("traffic is gridlocked (avg $avgSpeed)", avgSpeed > 4.0)
        assertTrue("a car waited too long (${maxStuck / 30} s)", maxStuck < 30 * 60)
        assertTrue("pedestrians walk down the middle of the street", jaywalk < pedFrames / 50)
    }
}

package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Headless traffic run on every map: cars keep moving without crashing, pedestrians walk smoothly and use the crossings. */
class TrafficTest {
    @Test
    fun trafficFlowsSmoothlyOnEveryMap() {
        for (map in Maps.all) flow(City(1337L, map.id))
    }

    private fun flow(city: City) {
        val t = Traffic(Random(3))
        t.attach(city)
        t.ensure(36, 50)
        // a busy junction near the middle of the map
        val cx = (city.bounds[0] + city.bounds[2]) / 2; val cy = (city.bounds[1] + city.bounds[3]) / 2
        val hub = city.roads.nodes.filter { it.degree >= 3 && !city.shore.anyWater(it.x, it.y) }.minByOrNull { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }!!
        val fx = hub.x; val fy = hub.y
        t.update(0f, fx, fy, 230f, -9999f, -9999f, initial = true)
        // a few shop doors near the focus
        val near = city.buildings.filter { it.solid && abs(it.doorX - fx) < 60f && abs(it.doorY - fy) < 60f }.take(3)
        t.doors = FloatArray(near.size * 5).also { d -> for ((k, b) in near.withIndex()) { d[k * 5] = b.doorX; d[k * 5 + 1] = b.doorY; d[k * 5 + 2] = .3f; d[k * 5 + 3] = -b.frontX; d[k * 5 + 4] = -b.frontY } }
        t.doorCount = near.size
        val dt = 1f / 30f
        val px = FloatArray(t.cars.size); val py = FloatArray(t.cars.size)
        val qx = FloatArray(t.peds.size); val qy = FloatArray(t.peds.size)
        var carJumps = 0; var pedJumps = 0; var overlaps = 0; var frames = 0
        var maxSpeed = 0f
        var movingSum = 0.0; var jaywalk = 0; var pedFrames = 0; var entered = 0
        val stuck = IntArray(t.cars.size)
        var maxStuck = 0
        for (f in 0 until 30 * 180) {
            for ((i, a) in t.cars.withIndex()) { px[i] = a.x; py[i] = a.y }
            for ((i, a) in t.peds.withIndex()) { qx[i] = a.x; qy[i] = a.y }
            t.update(dt, fx, fy, 230f, -9999f, -9999f)
            frames++
            for ((i, a) in t.cars.withIndex()) {
                val d = sqrt((a.x - px[i]) * (a.x - px[i]) + (a.y - py[i]) * (a.y - py[i]))
                // a respawn far away is fine; a short hop is a glitch
                if (d > 1.5f && d < 60f) carJumps++
                movingSum += a.speed
                maxSpeed = maxOf(maxSpeed, a.speed)
                if (a.speed < .2f) { stuck[i]++; maxStuck = maxOf(maxStuck, stuck[i]) } else stuck[i] = 0
            }
            for ((i, a) in t.peds.withIndex()) {
                if (a.hidden) continue
                val d = sqrt((a.x - qx[i]) * (a.x - qx[i]) + (a.y - qy[i]) * (a.y - qy[i]))
                if (d > .5f && d < 40f) pedJumps++
                if (a.state == 3) entered++
                pedFrames++
                // walking along (not crossing) but out on the asphalt
                if (a.state == 0 && city.roads.onAsphalt(a.x, a.y)) jaywalk++
            }
            for (i in t.cars.indices) for (j in i + 1 until t.cars.size) {
                val a = t.cars[i]; val b = t.cars[j]
                if (abs(a.x - b.x) < 1.2f && abs(a.y - b.y) < 1.2f) overlaps++
            }
        }
        val avgSpeed = movingSum / (frames * t.cars.size)
        println("${city.mapId}: car jumps=$carJumps ped jumps=$pedJumps overlaps=$overlaps avg speed=%.1f m/s longest stop=%.0f s jaywalk frames=$jaywalk/$pedFrames shop entries=$entered".format(avgSpeed, maxStuck / 30.0))
        assertTrue("${city.mapId}: cars teleport ($carJumps)", carJumps < 30)
        assertTrue("${city.mapId}: pedestrians teleport ($pedJumps)", pedJumps < 60)
        assertTrue("${city.mapId}: cars drive through each other ($overlaps)", overlaps < frames / 15)
        assertTrue("${city.mapId}: traffic is gridlocked (avg $avgSpeed)", avgSpeed > 2.0)
        assertTrue("${city.mapId}: cars drive at city speeds (max $maxSpeed m/s)", maxSpeed < 14f)
        assertTrue("${city.mapId}: a car waited too long (${maxStuck / 30} s)", maxStuck < 30 * 75)
        assertTrue("${city.mapId}: pedestrians walk down the middle of the street ($jaywalk/$pedFrames)", jaywalk < pedFrames / 30)
    }
}

package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** Wheel-and-pedal driving, parking and GPS routes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrivingGpsTest {
    private fun game() = GameEngine(null).also { it.newGame("T", 0, 0, 1); it.ui.tip = null; it.ui.phoneOpen = false }

    private fun run(e: GameEngine, seconds: Float) { repeat((seconds * 30).toInt()) { e.frame(1 / 30f) } }

    @Test
    fun pedalsSteerBrakeAndPark() {
        val e = game()
        val v = e.state.vehicles.first()
        e.enterVehicle(v)
        e.settings.carControls = 0
        e.input.gas = true
        run(e, 6f)
        println("top speed ${e.carSpeed} m/s")
        assertTrue("the car moves", e.carSpeed > 5f)
        assertTrue("a city car is not a race car (${e.carSpeed * 3.6f} km/h)", e.carSpeed <= v.def.maxSpeed + .01f && v.def.maxSpeed * 3.6f < 55f)
        e.input.gas = false
        e.input.brake = true
        run(e, 1.4f)
        assertTrue("brakes stop the car (${e.carSpeed})", abs(e.carSpeed) < .5f)
        // keep holding the brake to reverse
        run(e, 1.5f)
        assertTrue("holding brake reverses", e.carSpeed < -.3f)
        e.input.brake = false
        run(e, 2f)
        // steering
        val h0 = v.heading
        e.input.gas = true; e.input.steer = 1f
        run(e, 1.5f)
        e.input.gas = false; e.input.steer = 0f
        assertTrue("steering turns the car", abs(v.heading - h0) > .2f)
        e.input.brake = true
        run(e, 1.2f)
        e.input.brake = false
        assertTrue(e.parkVehicle())
        assertEquals(-1, e.state.player.driving)
        // parked at a curb (or in a lot), lined up with the road
        val onAvenueCurb = (0 until City.AVENUES).any { abs(abs(v.x - City.avenueX(it)) - (City.AV_HALF - 1.1f)) < .05f }
        val onStreetCurb = (0 until City.STREETS).any { abs(abs(v.y - City.streetY(it)) - (City.ST_HALF - 1.1f)) < .05f }
        val inLot = e.city.buildings.any { (it.kind == BKind.PARKING || it.kind == BKind.GAS) && it.contains(v.x, v.y) }
        assertTrue("parked at the curb (${v.x}, ${v.y})", onAvenueCurb || onStreetCurb || inLot)
        assertEquals(0f, ((v.heading / (Math.PI / 2).toFloat()) - Math.round(v.heading / (Math.PI / 2))).toFloat(), .01f)
    }

    @Test
    fun cantGetOutWhileMoving() {
        val e = game()
        e.enterVehicle(e.state.vehicles.first())
        e.input.gas = true
        run(e, 3f)
        assertTrue(!e.getOutOfCar())
        assertTrue(e.state.player.driving >= 0)
    }

    @Test
    fun gpsRoutesAlongTheStreetsAndArrives() {
        val e = game()
        val p = e.state.player
        val target = e.city.buildings.filter { it.kind == BKind.RETAIL }.maxByOrNull { abs(it.doorX - p.x) + abs(it.doorY - p.y) }!!
        e.setWaypoint(target.id)
        run(e, .2f)
        val gps = e.gps
        assertTrue(gps.active)
        assertTrue(gps.points >= 4)
        // the inner legs follow roads: axis-aligned
        for (q in 1 until gps.points - 2) {
            val dx = abs(gps.route[q * 2 + 2] - gps.route[q * 2]); val dy = abs(gps.route[q * 2 + 3] - gps.route[q * 2 + 1])
            assertTrue("segment $q is not along a road", dx < .01f || dy < .01f)
        }
        val manhattan = abs(target.doorX - p.x) + abs(target.doorY - p.y)
        println("route ${gps.distance} m vs manhattan $manhattan m, '${gps.instruction()}' in ${gps.turnDistance} m")
        assertTrue(gps.distance >= manhattan * .95f && gps.distance < manhattan * 1.6f + 200f)
        assertTrue(gps.instruction().isNotEmpty())
        // walk along the route: arrival clears the destination
        for (q in 0 until gps.points) {
            p.x = gps.route[q * 2]; p.y = gps.route[q * 2 + 1]
        }
        p.x = target.doorX; p.y = target.doorY
        run(e, .2f)
        assertEquals(-1, p.waypoint)
        assertTrue(e.ui.toasts.any { it.text.startsWith("You have arrived") })
    }
}

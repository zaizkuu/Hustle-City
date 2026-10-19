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
        val hit = com.hustlecity.game.world.RoadHit()
        val onRoad = e.city.roads.nearest(v.x, v.y, 30f, hit)
        val road = e.city.roads.edges[hit.edge]
        val atCurb = onRoad && abs(hit.dist - (road.cls.halfWidth - 1.1f)) < .1f
        val inLot = e.city.buildings.any { (it.kind == BKind.PARKING || it.kind == BKind.GAS) && it.contains(v.x, v.y) }
        assertTrue("parked at the curb (${v.x}, ${v.y})", atCurb || inLot)
        // lined up with the road
        val along = kotlin.math.abs(kotlin.math.sin(v.heading) * road.uy + kotlin.math.cos(v.heading) * road.ux)
        if (atCurb) assertTrue("parked along the road", along < .05f)
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

    @Test
    fun arrowKeysAndKeyboardDrive() {
        val e = game()
        e.settings.carControls = 2
        val v = e.state.vehicles.first()
        // walking with the keyboard
        assertTrue(e.onKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, true))
        assertEquals(1f, e.input.jx, 0f)
        e.onKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, false)
        assertEquals(0f, e.input.jx, 0f)
        e.enterVehicle(v)
        // up = gas, left/right steer, eased toward full lock
        e.onKey(android.view.KeyEvent.KEYCODE_DPAD_UP, true)
        assertTrue(e.input.gas)
        run(e, 2f)
        assertTrue("arrow keys drive the car", e.carSpeed > 3f)
        val h0 = v.heading
        // steer out into the road (to the right is the sidewalk and the buildings)
        e.onKey(android.view.KeyEvent.KEYCODE_A, true)
        assertEquals(-1f, e.input.steer, 0f)
        run(e, 1f)
        assertTrue("left arrow turns the car", abs(v.heading - h0) > .15f)
        e.onKey(android.view.KeyEvent.KEYCODE_A, false)
        e.onKey(android.view.KeyEvent.KEYCODE_DPAD_UP, false)
        e.onKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN, true)
        assertTrue(e.input.brake && !e.input.gas)
        run(e, 1.5f)
        assertTrue("down arrow brakes", abs(e.carSpeed) < .5f)
        e.onKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN, false)
        // letters are left alone while a menu (with text fields) is open
        e.ui.panel = com.hustlecity.game.engine.Panel.Settings
        assertTrue(!e.onKey(android.view.KeyEvent.KEYCODE_W, true))
    }
}

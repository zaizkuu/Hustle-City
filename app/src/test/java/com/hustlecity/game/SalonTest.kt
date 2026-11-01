package com.hustlecity.game

import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.Job
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.toggleWork
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A hair salon: the stylists go to the clients in the chairs (whichever chairs they pick) and cut; you, working the
 * till, stand behind it facing across it, and the cashier makes room for you rather than standing in you.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SalonTest {
    @Test
    fun stylistsCutAndYouWorkTheTill() {
        for (rot in 0..3) {
            val e = GameEngine(null)
            e.newGame("T", 0, 0, 1)
            e.state.minute = (e.state.minute / 1440) * 1440 + 11 * 60
            val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null && it.gridW in 6..12 && it.gridD in 10..20 }
            e.state.rentals += Rental(b.id, b.baseRent, 1)
            val biz = e.startBusiness(b.id, "hairdresser", "Golden Cuts")!!
            biz.tempClosed = false
            val room = biz.backRoom.coerceAtLeast(0)
            biz.furniture += listOf(PlacedItem("cash_register", 3, room + 3, rot), PlacedItem("hair_chair", 6, room + 6, rot), PlacedItem("hair_chair", 3, room + 8, rot))
            // one stylist for two chairs, and a cashier
            e.state.employees += Employee(e.state.newId(), "Kim", StaffRole.HAIRSTYLIST, .75, 18.0, 18.0, 27, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
            e.state.employees += Employee(e.state.newId(), "Lee", StaffRole.CUSTOMER_SERVICE, .75, 18.0, 18.0, 27, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
            e.state.player.inBuilding = b.id
            val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
            e.interior = sc
            e.economy.servedRate[biz.id] = 200.0
            e.toggleWork(biz)
            var seatedFrames = 0; var cutFrames = 0
            repeat(30 * 240) {
                sc.update(1 / 30f, true)
                val seated = sc.visitors.filter { it.seated }
                if (seated.isEmpty()) return@repeat
                seatedFrames++
                if (sc.staff.any { s -> s.job == Job.HAIR && seated.any { it.item == s.item } }) cutFrames++
                // you're behind the till, facing straight over it, and nobody stands where you stand
                assertTrue(sc.playerJob == Job.TILL)
                val reg = sc.items[sc.playerTill]
                val fx = sin(sc.playerHeading); val fy = -cos(sc.playerHeading)
                val (wx, wy) = when (reg.rot) { 0 -> 0f to 1f; 1 -> -1f to 0f; 2 -> 0f to -1f; else -> 1f to 0f }
                assertTrue("rot $rot: you face across the till (%.2f, %.2f)".format(fx, fy), abs(fx - wx) < .01f && abs(fy - wy) < .01f)
                for (s in sc.staff) assertTrue("rot $rot: the cashier isn't standing in you",
                    floor(s.x) != floor(sc.px) || floor(s.y) != floor(sc.py) || s.pathIdx < s.path.size)
            }
            println("rot $rot: someone in a chair ${seatedFrames} frames, being cut $cutFrames")
            assertTrue("rot $rot: clients sat down", seatedFrames > 30 * 30)
            // the stylist has to walk over when a client picks the other chair, so not every moment
            assertTrue("rot $rot: a stylist is cutting most of the time someone's in a chair ($cutFrames of $seatedFrames)", cutFrames * 10 > seatedFrames * 6)
        }
    }
}

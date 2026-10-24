package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

/** Customers inside a busy store browse, queue at the register, pay and leave without piling up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InteriorAiTest {
    private fun run(typeId: String, furniture: List<PlacedItem>, seconds: Int = 180): String {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.gridW >= 12 && it.gridD >= 9 }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, typeId, "X")!!
        biz.furniture += furniture
        val sc = InteriorScene(e, b, InteriorMode.BUSINESS)
        e.economy.servedRate[biz.id] = 90.0
        val dt = 1f / 30f
        var maxQueue = 0
        var overlapFrames = 0
        var frames = 0
        val age = HashMap<Any, Int>()
        var oldest = 0
        var served = 0
        var spawned = 0
        val seen = HashSet<Any>()
        repeat(30 * seconds) {
            sc.update(dt, true)
            frames++
            maxQueue = maxOf(maxQueue, sc.longestQueue)
            for (v in sc.visitors) { if (seen.add(v)) spawned++; age[v] = (age[v] ?: 0) + 1; oldest = maxOf(oldest, age[v]!!) }
            served = spawned - sc.visitors.size
            for (i in sc.visitors.indices) for (j in i + 1 until sc.visitors.size) {
                val a = sc.visitors[i]; val c = sc.visitors[j]
                val dx = a.x - c.x; val dy = a.y - c.y
                if (sqrt(dx * dx + dy * dy) < .25f && a.y < sc.d - .5f) overlapFrames++
            }
        }
        val report = "$typeId: spawned=$spawned left=$served longest queue=$maxQueue longest visit=${oldest / 30}s overlap frames=$overlapFrames/$frames staff=${sc.staff.size}"
        println(report)
        assertTrue("$typeId: nobody came in", spawned > 10)
        assertTrue("$typeId: customers don't leave ($served of $spawned)", served >= spawned / 2)
        assertTrue("$typeId: someone was stuck inside (${oldest / 30} s)", oldest < 30 * 120)
        // people step round each other instead of walking through (a brief squeeze at the door is fine)
        assertTrue("$typeId: customers walk through each other ($overlapFrames/$frames frames)", overlapFrames < frames / 40)
        return report
    }

    @Test
    fun shopCustomersQueueAndLeave() {
        val q = run("giftshop", listOf(
            PlacedItem("cash_register", 5, 2, 0), PlacedItem("shelf", 0, 0, 0), PlacedItem("shelf", 9, 0, 0), PlacedItem("shelf", 0, 5, 0),
        ))
        assertTrue("a queue should form at the register when busy: $q", Regex("longest queue=(\\d+)").find(q)!!.groupValues[1].toInt() >= 2)
    }

    @Test
    fun cafeCustomersOrderThenSit() {
        run("coffee", listOf(
            PlacedItem("cash_register", 5, 1, 0), PlacedItem("coffee_machine", 1, 0, 0), PlacedItem("table_set", 1, 5, 0), PlacedItem("table_set", 8, 5, 0),
        ))
    }

    @Test
    fun clubGuestsDance() {
        run("nightclub", listOf(
            PlacedItem("cocktail_bar", 1, 0, 0), PlacedItem("dance_floor", 6, 2, 0), PlacedItem("dj_booth", 6, 0, 0),
        ))
    }
}

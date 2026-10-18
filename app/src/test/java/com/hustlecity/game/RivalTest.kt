package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.BizDay
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Rival companies grow, adapt and compete. Writes a growth report to build/rivals.txt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RivalTest {
    private fun game() = GameEngine(null).also { it.newGame("T", 0, 0, 1); it.state.sandbox = true; it.state.player.money = 100_000.0 }

    @Test
    fun rivalsGrowAndAdapt() {
        val e = game()
        val s = e.state
        val sb = StringBuilder()
        val start = s.rivals.associate { it.name to it.netWorth }
        val startStores = s.rivalBusinesses.size
        val types0 = s.rivalBusinesses.groupingBy { it.typeId }.eachCount()
        sb.append("day 0: stores=$startStores  vacant retail=${e.city.buildings.count { it.kind == BKind.RETAIL && e.isVacant(it.id) }}\n")
        for (r in s.rivals) sb.append("  %-18s %-10s worth=%s stores=%d\n".format(r.name, com.hustlecity.game.engine.RivalAI.STRATEGIES[r.strategy], e.moneyShort(r.netWorth), s.rivalBusinesses.count { it.rivalId == r.id }))
        for (d in 1..180) {
            s.minute = (s.minute / 1440 + 1) * 1440 - 1
            e.simulateMinute()
            if (d % 30 == 0) {
                sb.append("day $d: stores=${s.rivalBusinesses.size} levels=${s.rivalBusinesses.groupingBy { it.level }.eachCount()} owned=${s.rivalBusinesses.count { it.owned }}\n")
                for (r in s.rivals.sortedByDescending { it.netWorth }) {
                    sb.append("  %-18s worth=%-8s cash=%-8s profit/day=%-7s stores=%d  %s\n".format(r.name, e.moneyShort(r.netWorth), e.moneyShort(r.cash), e.moneyShort(r.trend),
                        s.rivalBusinesses.count { it.rivalId == r.id }, r.log.firstOrNull() ?: ""))
                }
            }
        }
        val types1 = s.rivalBusinesses.groupingBy { it.typeId }.eachCount()
        sb.append("type mix day 0 -> 180:\n")
        for (t in Catalog.customerTypes) sb.append("  %-24s %3d -> %3d\n".format(t.name, types0[t.id] ?: 0, types1[t.id] ?: 0))
        File("build/rivals.txt").writeText(sb.toString())
        println(sb)
        val grew = s.rivals.count { it.netWorth > (start[it.name] ?: 0.0) * 1.3 }
        assertTrue("most rivals should grow ($grew of ${s.rivals.size})", grew >= s.rivals.size / 2)
        assertTrue("rivals should open new stores", s.rivals.any { r -> r.log.any { "opened" in it } } || s.rivalBusinesses.size > startStores)
        val top = s.rivals.maxOf { it.netWorth }
        assertTrue("top rival should be a real race (${e.moneyShort(top)})", top in 2_000_000.0..60_000_000.0)
    }

    @Test
    fun aggressiveRivalStartsPriceWarWithSuccessfulStore() {
        val e = game()
        val s = e.state
        // a successful player coffee shop next to a coffee shop of an aggressive rival
        val lunar = s.rivals.first { it.name == "Lunar Holdings" }
        lunar.cash = 2_000_000.0
        val rb = s.rivalBusinesses.first { it.rivalId == lunar.id && e.city.building(it.buildingId)!!.kind == BKind.RETAIL }
        val hood = e.city.building(rb.buildingId)!!.neighborhood
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.neighborhood == hood }
        s.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, rb.typeId, "Mine")!!
        biz.history += BizDay(s.day, 3000.0, 800.0, 200.0, 100.0, 0.0, 300, .7)
        var war = false
        for (d in 1..40) {
            biz.history += BizDay(s.day, 3000.0, 800.0, 200.0, 100.0, 0.0, 300, .7)
            s.minute = (s.minute / 1440 + 1) * 1440 - 1
            e.simulateMinute()
            if (s.rivalBusinesses.any { it.targetBiz == biz.id && it.priceFactor < 1.0 }) { war = true; break }
        }
        assertTrue("an aggressive rival should start a price war", war)
        assertTrue(s.messages.any { it.text.startsWith("Price war!") })
        // the price war makes the competition tougher for the player store
        val before = e.economy.rivals.competitionFor(biz)
        assertTrue(before > 1.0)
    }

    @Test
    fun losingStoresClose() {
        val e = game()
        val s = e.state
        val r = s.rivals.first()
        // a jewelry store in a poor, empty corner can't make money
        val bld = e.city.buildings.filter { e.isVacant(it.id) && it.kind == BKind.RETAIL }.minByOrNull { it.traffic }!!
        val rb = com.hustlecity.game.model.RivalBusiness(s.newId(), r.id, bld.id, "theater", "Doomed", opened = s.day - 30)
        s.rivalBusinesses += rb
        for (d in 1..30) {
            s.minute = (s.minute / 1440 + 1) * 1440 - 1
            e.simulateMinute()
            if (rb !in s.rivalBusinesses) break
        }
        assertTrue("a store that loses money every day should close", rb !in s.rivalBusinesses)
    }
}

package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.itemForTag
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.world.BKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** Prints a one-week profit report per business type to build/balance.txt (useful when tuning numbers). */
class BalanceReportTest {
    @Test
    fun probe() {
        val sb = StringBuilder()
        for (t in Catalog.businessTypes) {
            val e = GameEngine(null)
            e.newGame("P", 0, 0, 1)
            e.state.player.courses += Catalog.COURSE_IMPORT
            val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 90..160 && it.traffic in .3f..0.45f }
            e.state.rentals += Rental(b.id, b.baseRent, 1)
            val biz = e.startBusiness(b.id, t.id, "X")!!
            // furnish: one of each required + displays for products + extras
            val tags = LinkedHashSet<String>()
            t.requirements.forEach { tags += it.tag }
            t.products.forEach { Catalog.product(it.first)?.let { p -> tags += p.tag } }
            var x = 0
            var furnCost = 0.0
            for (tag in tags) {
                val id = itemForTag(tag)
                val n = if (tag == "workout") 4 else if (tag == "hair_chair") 2 else 1
                repeat(n) { biz.furniture += PlacedItem(id, x, 0, 0); x += 2; furnCost += Catalog.item(id)!!.price }
            }
            biz.tempClosed = false
            for ((pid, _) in t.products) {
                val p = Catalog.product(pid)!!
                if (!p.service) { biz.stock[pid] = 100000; biz.unitCost[pid] = p.cost }
            }
            fun emp(start: Int, end: Int) { val em = Employee(e.state.newId(), "E", t.staffRole, .5, t.staffRole.baseWage, t.staffRole.baseWage, 30, businessId = biz.id, shiftStart = start, shiftEnd = end, days = 127); e.state.employees += em }
            emp(biz.openHour, biz.openHour + 7); emp(biz.openHour + 7, biz.closeHour)
            repeat(1440 * 7) { e.simulateMinute() }
            val days = biz.history.takeLast(5)
            val rev = days.map { it.revenue }.average(); val cogs = days.map { it.cogs }.average(); val wages = days.map { it.wages }.average(); val rent = days.map { it.rent }.average()
            val cust = days.map { it.customers }.average()
            sb.append("%-26s area=%d traffic=%.2f comp=%d  cust/day=%.0f rev=%.0f cogs=%.0f wages=%.0f rent=%.0f  profit=%.0f  furniture=%.0f  sat=%.2f rep=%.2f\n".format(
                t.name, b.area, b.traffic, com.hustlecity.game.engine.BizCalc.competitors(e, biz), cust, rev, cogs, wages, rent, rev - cogs - wages - rent, furnCost, biz.lastSatisfaction, biz.reputation))
        }
        println(sb)
        java.io.File("build/balance.txt").writeText(sb.toString())
    }
}

package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.Sky
import com.hustlecity.game.model.WeatherSpan
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Weather, procedural dirt, shoplifting and the new furniture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherDirtTest {
    private fun game() = GameEngine(null).also { it.newGame("T", 0, 0, 0); it.state.sandbox = true; it.state.player.money = 500_000.0 }

    private fun force(e: GameEngine, sky: Sky, days: Int = 3) {
        e.state.weather.clear()
        e.state.weather += WeatherSpan(e.state.minute - 60, e.state.minute + days * 1440L, sky)
        e.weather.ensure()
    }

    private fun shop(e: GameEngine, type: String, items: List<PlacedItem>, stock: Map<String, Int> = emptyMap()): com.hustlecity.game.model.Business {
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.area in 90..160 && it.traffic > .3f }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, type, "Shop")!!
        biz.furniture += items
        biz.tempClosed = false
        biz.openHour = 0; biz.closeHour = 23
        for ((pid, q) in stock) { biz.stock[pid] = q; biz.unitCost[pid] = Catalog.product(pid)!!.cost }
        repeat(2) { e.state.employees += Employee(e.state.newId(), "E$it", biz.type.staffRole, .6, 15.0, 15.0, 30, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127) }
        return biz
    }

    @Test
    fun forecastPlansAheadAndFollowsSeasons() {
        val e = game()
        val s = e.state
        assertTrue(s.weather.isNotEmpty())
        assertTrue("four days planned", s.weather.last().end >= s.minute + 3 * 1440)
        // spans are contiguous
        for (i in 1 until s.weather.size) assertEquals(s.weather[i - 1].end, s.weather[i].start)
        assertEquals("April 1", e.weather.dateLabel())
        // run through a year: snow only in the cold months, warm summers
        val snowMonths = HashSet<Int>()
        var julyMax = -99.0; var janMin = 99.0
        for (d in 0 until 360) {
            s.minute = d * 1440L + 12 * 60
            e.weather.ensure()
            val m = e.weather.month()
            for (h in 0 until 24 step 3) if (e.weather.sky(d * 1440L + h * 60) == Sky.SNOW) snowMonths += m
            if (m == 6) julyMax = maxOf(julyMax, e.weather.temperature())
            if (m == 0) janMin = minOf(janMin, e.weather.temperature(d * 1440L + 5 * 60))
        }
        println("snow months=$snowMonths julyMax=$julyMax janMin=$janMin")
        assertTrue("it should snow in winter", snowMonths.isNotEmpty())
        assertTrue("no snow in summer", snowMonths.none { it in 4..8 })
        assertTrue(julyMax > 25 && janMin < 2)
        assertTrue(e.weather.forecastText().contains("Tomorrow"))
    }

    @Test
    fun rainSellsUmbrellasAndThinsTheCrowd() {
        fun day(sky: Sky): Pair<Int, Int> {
            val e = game()
            force(e, sky)
            val biz = shop(e, "giftshop", listOf(PlacedItem("cash_register", 0, 0, 0), PlacedItem("shelf", 3, 0, 0), PlacedItem("shelf", 6, 0, 0)),
                mapOf("gift_cheap" to 5000, "gift_exp" to 5000, "umbrella" to 5000))
            repeat(1440) { e.simulateMinute() }
            val last = biz.history.last()
            return last.customers to (1440.let { _ -> biz.history.size }).let { _ ->
                // umbrellas sold = drop in stock
                5000 - (biz.stock["umbrella"] ?: 0)
            }
        }
        val (sunCust, sunUmb) = day(Sky.CLEAR)
        val (rainCust, rainUmb) = day(Sky.RAIN)
        println("clear: $sunCust customers, $sunUmb umbrellas; rain: $rainCust customers, $rainUmb umbrellas")
        assertTrue("rain keeps some shoppers home", rainCust < sunCust)
        assertTrue("rain sells umbrellas", rainUmb > sunUmb * 2)
    }

    @Test
    fun cinemasFillUpInTheRain() {
        val e = game()
        val t = Catalog.type("cinema")
        force(e, Sky.CLEAR)
        val clear = e.weather.demandFactor(t)
        force(e, Sky.RAIN)
        val rain = e.weather.demandFactor(t)
        val flowers = e.weather.demandFactor(Catalog.type("florist"))
        assertTrue(rain > clear && flowers < 1.0)
    }

    @Test
    fun dirtFollowsFootTrafficAndCleanersScrubIt() {
        val e = game()
        force(e, Sky.RAIN)
        val biz = shop(e, "giftshop", listOf(PlacedItem("cash_register", 0, 0, 0), PlacedItem("shelf", 4, 0, 0), PlacedItem("trash_can", 8, 0, 0)),
            mapOf("gift_cheap" to 5000, "umbrella" to 5000))
        repeat(300) { e.simulateMinute() }
        val (w, d) = e.economy.dirt.size(biz)
        assertEquals(w * d, biz.dirt.size)
        val door = (d - 1) * w + w / 2
        val corner = 0 * w + (w - 1)
        println("cleanliness=${biz.cleanliness} door=${biz.dirt[door]} corner=${biz.dirt[corner]} max=${biz.dirt.max()}")
        assertTrue("the entrance gets dirtiest", biz.dirt[door] > biz.dirt.average() * 2)
        assertTrue("cleanliness tracks the dirt", kotlin.math.abs(biz.cleanliness - (100 - biz.dirt.average())) < 1.0)
        val before = biz.cleanliness
        e.state.employees += Employee(e.state.newId(), "Mop", StaffRole.CLEANER, .5, 13.0, 13.0, 30, businessId = biz.id, shiftStart = 0, shiftEnd = 23, days = 127)
        repeat(120) { e.simulateMinute() }
        assertTrue("a cleaner makes the store cleaner (${biz.cleanliness} vs $before)", biz.cleanliness > before)
    }

    @Test
    fun securityStopsShoplifters() {
        fun stolen(cameras: Int): Double {
            val e = game()
            e.state.player.courses += Catalog.COURSE_IMPORT
            force(e, Sky.CLEAR)
            val items = arrayListOf(PlacedItem("cash_register", 0, 0, 0), PlacedItem("electronics_table", 3, 0, 0), PlacedItem("electronics_table", 6, 0, 0))
            repeat(cameras) { items += PlacedItem(if (it == 0) "security_gate" else "security_camera", it * 2, 4, 0) }
            val biz = shop(e, "electronics", items, mapOf("headphones" to 3000, "smartphone" to 3000, "laptop" to 3000))
            e.rnd = kotlin.random.Random(42)
            repeat(1440 * 8) { e.simulateMinute() }
            return biz.history.sumOf { it.stolen }
        }
        val none = stolen(0)
        val guarded = stolen(3)
        println("stolen without security=$none with=$guarded")
        assertTrue("electronics get shoplifted", none > 0)
        assertTrue("security cuts theft", guarded < none * .5)
    }

    @Test
    fun checkoutCounterServesMoreThanRegister() {
        val e = game()
        val biz = shop(e, "supermarket", listOf(PlacedItem("cash_register", 0, 0, 0), PlacedItem("shelf", 3, 0, 0), PlacedItem("drinks_fridge", 6, 0, 0)))
        val reg = com.hustlecity.game.engine.BizCalc.registerCapacity(biz, 2)
        biz.furniture[0] = PlacedItem("checkout_counter", 0, 0, 0)
        val counter = com.hustlecity.game.engine.BizCalc.registerCapacity(biz, 2)
        assertEquals(40.0, reg, .01)
        assertEquals(60.0, counter, .01)
        // every furniture item is sold somewhere
        val sold = Catalog.places.flatMap { it.stock }.toSet()
        val unsold = Catalog.furniture.filter { it.id !in sold && it.id !in setOf("desk", "pallet") }.map { it.id }
        assertTrue("not sold anywhere: $unsold", unsold.isEmpty())
        // every requirement and every product display can be bought
        for (t in Catalog.allTypes) {
            for (r in t.requirements) assertTrue("${t.id} needs ${r.tag}", Catalog.furniture.any { r.tag in it.tags && it.id in sold })
            for ((pid, _) in t.products) {
                val p = Catalog.product(pid)!!
                assertTrue("${t.id}: no furniture for $pid (${p.tag})", Catalog.furniture.any { p.tag in it.tags && it.id in sold })
            }
        }
    }
}

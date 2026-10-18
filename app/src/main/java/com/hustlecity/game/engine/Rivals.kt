package com.hustlecity.game.engine

import com.hustlecity.game.data.BusinessTypeDef
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.Rival
import com.hustlecity.game.model.RivalBusiness
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Rival companies. Their stores earn money with the same demand model as yours (traffic, wealth, competition,
 * reputation, marketing and price), and every day each company decides whether to open, renovate, advertise,
 * start a price war, buy its buildings or close a store that loses money.
 */
class RivalAI(private val e: GameEngine) {
    /** Projected day for a store. */
    class Day(val revenue: Double, val cogs: Double, val costs: Double, val customers: Int, val lost: Int) {
        val profit get() = revenue - cogs - costs
    }

    private class Personality(val strategy: Int, val aggression: Double, val focus: List<String>)

    companion object {
        val STRATEGIES = listOf("Balanced", "Aggressive", "Premium", "Discounter", "Specialist")
        private val MARKETING_COST = mapOf(.1 to 120.0, .22 to 350.0, .35 to 800.0)
        /** Rival stores are run by managers, not by you: fewer customers than a well-run player store. */
        private const val EFFICIENCY = .6
        /** Rivals leave at least this share of shops and offices for rent. */
        private const val MIN_VACANCY = .3

        private val PERSONALITIES = mapOf(
            "Davison Group" to Personality(0, .5, listOf("supermarket", "liquor", "giftshop", "lawfirm")),
            "Lunar Holdings" to Personality(1, .85, listOf("coffee", "fastfood", "nightclub", "cinema")),
            "Brooks & Sons" to Personality(0, .4, listOf("produce", "bookstore", "florist", "travel")),
            "Vanta Retail" to Personality(2, .6, listOf("clothing", "jewelry", "electronics", "theater")),
            "Golden Fork Inc." to Personality(4, .55, listOf("fastfood", "coffee", "events")),
            "Pinnacle Ventures" to Personality(1, .75, listOf("electronics", "webdev", "lawfirm", "cinema", "design")),
            "Urban Bloom Ltd." to Personality(3, .45, listOf("florist", "giftshop", "produce")),
            "Ironclad Fitness" to Personality(4, .65, listOf("gym", "nightclub", "fastfood")),
        )

        /** Fit-out cost of a level-1 store: two of each required piece plus opening stock. */
        fun setupCost(t: BusinessTypeDef): Double =
            t.requirements.sumOf { Catalog.item(itemForTag(it.tag))?.price ?: 0.0 } * 2.0 + 6000.0 + t.dailyFee * 10

        fun upgradeCost(t: BusinessTypeDef, level: Int) = setupCost(t) * .8 * level

        /** Book value of a store's fit-out. */
        fun storeValue(t: BusinessTypeDef, level: Int) = setupCost(t) * (.6 + .4 * level) * .5
    }

    private val rnd: Random get() = e.rnd

    // ------------------------------------------------------------------ competition

    private val competition = HashMap<String, Double>()
    private var stamp = Long.MIN_VALUE
    private var version = 0

    private fun key(typeId: String, hood: Int) = if (Catalog.type(typeId).office) "$typeId@city" else "$typeId@$hood"

    /** How strongly a rival store competes: renovated stores, price cuts and ads all hurt more. */
    fun weight(rb: RivalBusiness) = .75 + .25 * rb.level + (1.0 - rb.priceFactor).coerceAtLeast(0.0) * 3.0 + rb.marketing * .6

    private fun ensureCompetition() {
        val s = e.state
        val st = s.rivalBusinesses.size * 1_000_003L + s.businesses.size * 7919L + s.day * 131L + version
        if (st == stamp) return
        stamp = st
        competition.clear()
        for (rb in s.rivalBusinesses) {
            val hood = e.city.building(rb.buildingId)?.neighborhood ?: continue
            val k = key(rb.typeId, hood)
            competition[k] = (competition[k] ?: 0.0) + weight(rb)
        }
        for (b in s.businesses) {
            if (b.type.facility) continue
            val hood = e.city.building(b.buildingId)?.neighborhood ?: continue
            val k = key(b.typeId, hood)
            competition[k] = (competition[k] ?: 0.0) + 1.0
        }
    }

    /** Weighted number of competitors a player business faces (itself excluded). */
    fun competitionFor(b: Business): Double {
        ensureCompetition()
        val hood = e.city.building(b.buildingId)?.neighborhood ?: return 0.0
        return ((competition[key(b.typeId, hood)] ?: 0.0) - 1.0).coerceAtLeast(0.0)
    }

    private fun competitionAround(typeId: String, hood: Int, self: RivalBusiness?): Double {
        ensureCompetition()
        return ((competition[key(typeId, hood)] ?: 0.0) - (self?.let { weight(it) } ?: 0.0)).coerceAtLeast(0.0)
    }

    // ------------------------------------------------------------------ store economics

    private fun marketingCost(m: Double) = MARKETING_COST.entries.firstOrNull { kotlin.math.abs(it.key - m) < .01 }?.value ?: 0.0

    /** A day of trading for a rival store (or a store it is thinking about opening). */
    fun project(t: BusinessTypeDef, bld: Building, level: Int, rep: Double, pf: Double, mkt: Double, comp: Double, owned: Boolean): Day {
        val hood = City.neighborhoods[bld.neighborhood]
        val accept = BizCalc.buyProbability(pf)
        var spend = 0.0
        var cogs = 0.0
        for ((pid, chance) in t.products) {
            val p = Catalog.product(pid) ?: continue
            spend += chance * p.price * (.85 + .15 * hood.wealth) * pf * accept
            cogs += chance * p.cost * accept
        }
        var customers = 0.0
        var lost = 0.0
        var openHours = 0
        if (t.office) {
            val staff = level * 2
            val compF = 1.0 / (1.0 + .06 * comp)
            for (h in 0 until 24) {
                if (t.curve[h] <= 0f) continue
                openHours++
                val d = t.baseDemand * (.35 + .9 * rep) * (1.0 + mkt) * t.curve[h] * compF * EFFICIENCY
                val cap = staff * .8
                customers += min(d, cap); lost += max(0.0, d - cap)
            }
            val wages = openHours * staff * t.staffRole.baseWage
            val rent = if (owned) 0.0 else bld.baseRent / 30.0
            return Day(customers * spend, 0.0, wages + rent + marketingCost(mkt), customers.toInt(), lost.toInt())
        }
        val promo = (bld.traffic + mkt * .5).coerceIn(0.0, 1.0)
        val wealth = if (t.wealthSensitive) hood.wealth.toDouble().pow(1.6) else 1.0
        val compF = 1.0 / (1.0 + .15 * comp)
        val repF = .55 + .9 * rep
        val cap = min(
            when (t.id) {
                "hairdresser" -> level * 2 * 2.2
                "gym" -> level * 18.0
                "nightclub", "cinema", "theater" -> level * 24.0
                else -> level * 22.0
            },
            bld.customerLimit.toDouble(),
        )
        for (h in 0 until 24) {
            if (t.curve[h] < .05f) continue
            openHours++
            val d = t.baseDemand * (.25 + 1.6 * promo) * t.curve[h] * wealth * compF * repF * EFFICIENCY
            customers += min(d, cap); lost += max(0.0, d - cap)
        }
        val staff = (if (t.id == "hairdresser") 2 * level else level) + (if (t.performer != null) 1 else 0)
        val wages = openHours * staff * t.staffRole.baseWage + 4 * 13.0
        val rent = if (owned) 0.0 else bld.baseRent / 30.0
        return Day(customers * spend, customers * cogs, wages + rent + t.dailyFee + marketingCost(mkt), customers.toInt(), lost.toInt())
    }

    private fun projectStore(rb: RivalBusiness): Day? {
        val bld = e.city.building(rb.buildingId) ?: return null
        val t = Catalog.type(rb.typeId)
        return project(t, bld, rb.level, rb.reputation, rb.priceFactor, rb.marketing, competitionAround(rb.typeId, bld.neighborhood, rb), rb.owned)
    }

    private fun basePrice(r: Rival) = when (r.strategy) { 2 -> 1.08; 3 -> .92; else -> 1.0 }

    // ------------------------------------------------------------------ setup

    /** Gives each company a personality and its starting chain of stores. */
    fun seed(rr: Random) {
        val s = e.state
        for ((i, pair) in Catalog.rivalCompanies.withIndex()) {
            val cash = 120_000.0 + rr.nextDouble() * 500_000.0
            val r = Rival(s.newId(), pair.first, pair.second, cash, cash)
            personalize(r, i)
            s.rivals += r
        }
        val retail = e.city.buildings.filter { it.kind == BKind.RETAIL && e.isVacant(it.id) }.shuffled(rr)
        for (b in retail.take((retail.size * .5).toInt())) openSeeded(s.rivals[rr.nextInt(s.rivals.size)], b, rr, office = false)
        val offices = e.city.buildings.filter { it.kind == BKind.OFFICE && e.isVacant(it.id) }.shuffled(rr)
        for (b in offices.take((offices.size * .3).toInt())) openSeeded(s.rivals[rr.nextInt(s.rivals.size)], b, rr, office = true)
        version++
        for (r in s.rivals) { r.netWorth = netWorth(r); r.worthHistory += r.netWorth; r.milestone = milestoneCount(r.netWorth) }
    }

    private fun personalize(r: Rival, index: Int) {
        val p = PERSONALITIES[r.name] ?: Personality(index % STRATEGIES.size, .5, emptyList())
        r.strategy = p.strategy
        r.aggression = p.aggression
        r.focus = p.focus
    }

    private fun openSeeded(r: Rival, b: Building, rr: Random, office: Boolean) {
        val pool = if (office) Catalog.officeTypes else Catalog.businessTypes
        val liked = r.focus.map { Catalog.type(it) }.filter { it in pool }
        val type = if (liked.isNotEmpty() && rr.nextDouble() < .6) liked[rr.nextInt(liked.size)] else pool[rr.nextInt(pool.size)]
        e.state.rivalBusinesses += RivalBusiness(
            e.state.newId(), r.id, b.id, type.id, Catalog.randomBusinessName(type.id, rr),
            level = if (rr.nextDouble() < .3) 2 else 1, reputation = .4 + rr.nextDouble() * .35,
            priceFactor = basePrice(r), opened = -rr.nextInt(400), owned = rr.nextDouble() < .05,
        )
    }

    /** Saves from before rivals had personalities. */
    fun migrate() {
        val s = e.state
        if (s.rivals.all { it.focus.isEmpty() }) {
            for ((i, r) in s.rivals.withIndex()) personalize(r, i)
            for (rb in s.rivalBusinesses) if (rb.priceFactor == 1.0) rb.priceFactor = s.rivals.firstOrNull { it.id == rb.rivalId }?.let { basePrice(it) } ?: 1.0
            for (r in s.rivals) { r.netWorth = netWorth(r); r.milestone = milestoneCount(r.netWorth) }
        }
        version++
    }

    fun netWorth(r: Rival): Double {
        var nw = r.cash
        for (rb in e.state.rivalBusinesses) {
            if (rb.rivalId != r.id) continue
            nw += storeValue(Catalog.type(rb.typeId), rb.level)
            if (rb.owned) nw += e.city.building(rb.buildingId)?.value ?: 0.0
        }
        return nw
    }

    // ------------------------------------------------------------------ daily turn

    fun daily() {
        val s = e.state
        version++
        for (r in s.rivals) {
            var profit = 0.0
            var revenue = 0.0
            for (rb in s.rivalBusinesses) {
                if (rb.rivalId != r.id) continue
                val day = projectStore(rb) ?: continue
                val noise = .85 + rnd.nextDouble() * .3
                val p = day.revenue * noise - day.cogs * noise - day.costs
                rb.revenue = day.revenue * noise
                rb.customers = (day.customers * noise).toInt()
                rb.profit = if (s.day - rb.opened <= 1) p else rb.profit * .7 + p * .3
                rb.losingDays = if (p < 0) rb.losingDays + 1 else 0
                // service quality follows the fit-out, prices and how busy the store is
                val busy = if (day.customers + day.lost > 0) day.customers.toDouble() / (day.customers + day.lost) else 1.0
                val target = (.38 + .1 * rb.level + (1.0 - rb.priceFactor) * .8 + busy * .12 + rnd.nextDouble() * .06).coerceIn(.2, .95)
                rb.reputation += (target - rb.reputation) * .1
                profit += p; revenue += rb.revenue
            }
            r.cash += profit
            r.trend = profit
            r.revenue = revenue
            decide(r)
            r.netWorth = netWorth(r)
            r.worthHistory += r.netWorth
            if (r.worthHistory.size > 60) r.worthHistory.removeAt(0)
            milestones(r)
        }
        version++
    }

    private fun note(r: Rival, text: String) {
        r.log.add(0, "Day ${e.state.day}: $text")
        if (r.log.size > 8) r.log.removeAt(r.log.size - 1)
    }

    private fun storesOf(r: Rival) = e.state.rivalBusinesses.filter { it.rivalId == r.id }

    /** The player's businesses in a neighborhood, for "near you" news. */
    private fun playerNear(hood: Int, typeId: String? = null) = e.state.businesses.filter {
        !it.type.facility && e.city.building(it.buildingId)?.neighborhood == hood && (typeId == null || it.typeId == typeId)
    }

    private fun decide(r: Rival) {
        val stores = storesOf(r)
        val reserve = max(40_000.0, stores.size * 2_500.0)

        // 1. out of money: sell off the worst stores
        var guard = 0
        while (r.cash < 0 && guard++ < 5) {
            val worst = storesOf(r).minByOrNull { it.profit } ?: break
            close(r, worst, "to raise cash")
        }

        // 2. close a store that keeps losing money
        stores.filter { it.losingDays >= 10 && e.state.day - it.opened >= 14 }.minByOrNull { it.profit }?.let { close(r, it, "after weeks of losses") }

        // 3. renovate a busy, profitable store
        val upgrade = storesOf(r).filter { it.level < 3 && it.profit > 150 }.maxByOrNull { rb -> projectStore(rb)?.lost ?: 0 }
        if (upgrade != null && (projectStore(upgrade)?.lost ?: 0) > 25) {
            val t = Catalog.type(upgrade.typeId)
            val cost = upgradeCost(t, upgrade.level)
            if (r.cash - cost > reserve && rnd.nextDouble() < .35 + .4 * r.aggression) {
                r.cash -= cost
                upgrade.level++
                note(r, "renovated ${upgrade.name} (size ${upgrade.level})")
                version++
            }
        }

        // 4. advertising follows profits
        for (rb in storesOf(r)) {
            val want = when {
                r.cash > 600_000 && rb.profit > 400 -> .35
                r.cash > 200_000 && rb.profit > 150 -> .22
                r.cash > 80_000 && rb.profit > 0 -> .1
                else -> 0.0
            }
            if (want != rb.marketing) { rb.marketing = want; version++ }
        }

        // 5. price wars against the player's successful stores
        priceWars(r)

        // 6. expansion
        val tries = when {
            r.cash > 1_500_000 -> 3
            r.cash > 400_000 -> 2
            else -> 1
        }
        // the emptier the city, the more readily they expand; they leave room for newcomers
        val vacancy = vacancyRate()
        val room = ((vacancy - MIN_VACANCY) / (1 - MIN_VACANCY)).coerceIn(0.0, 1.0)
        repeat(tries) {
            if (rnd.nextDouble() < (.06 + .3 * r.aggression) * (.25 + 1.5 * room) && vacancy > MIN_VACANCY) expand(r, reserve)
        }

        // 7. buy the building of a profitable store instead of paying rent
        if (r.cash > 1_200_000 && rnd.nextDouble() < .25) {
            val rented = storesOf(r).filter { !it.owned && it.profit > 200 }.maxByOrNull { e.city.building(it.buildingId)?.baseRent ?: 0.0 }
            val bld = rented?.let { e.city.building(it.buildingId) }
            if (rented != null && bld != null && r.cash - bld.value > reserve * 2) {
                r.cash -= bld.value
                rented.owned = true
                note(r, "bought ${bld.address} for ${e.moneyShort(bld.value)}")
                if (playerNear(bld.neighborhood).isNotEmpty()) e.message("Rival News", "${r.name} bought the building at ${bld.address} (${rented.name}) for ${e.moneyShort(bld.value)}.")
            }
        }
    }

    private fun close(r: Rival, rb: RivalBusiness, why: String) {
        val bld = e.city.building(rb.buildingId)
        e.state.rivalBusinesses.remove(rb)
        r.cash += storeValue(Catalog.type(rb.typeId), rb.level) * .6 + (if (rb.owned) (bld?.value ?: 0.0) * .9 else 0.0)
        version++
        note(r, "closed ${rb.name} $why")
        if (bld != null && playerNear(bld.neighborhood).isNotEmpty()) {
            e.message("Rival News", "${r.name} closed ${rb.name} at ${bld.address} $why. The space is now for rent.")
        }
    }

    private fun weekRevenue(b: Business) = b.history.takeLast(7).map { it.revenue }.average().takeIf { !it.isNaN() } ?: 0.0

    private fun priceWars(r: Rival) {
        val s = e.state
        for (rb in storesOf(r)) {
            if (rb.targetBiz >= 0) {
                val target = s.businesses.firstOrNull { it.id == rb.targetBiz }
                // give up when the fight costs too much or the target is gone
                if (target == null || rb.losingDays >= 4 || rnd.nextDouble() < .05) {
                    rb.targetBiz = -1
                    rb.priceFactor = basePrice(r)
                    version++
                    if (target != null) note(r, "ended the price war at ${rb.name}")
                }
            }
        }
        if (r.aggression < .55 || rnd.nextDouble() > r.aggression * .5) return
        // pick the player's best store that one of our stores competes with
        for (b in s.businesses.sortedByDescending { weekRevenue(it) }) {
            if (b.type.facility || weekRevenue(b) < 600) continue
            val hood = e.city.building(b.buildingId)?.neighborhood ?: continue
            val mine = storesOf(r).firstOrNull { it.typeId == b.typeId && it.targetBiz < 0 && (Catalog.type(it.typeId).office || e.city.building(it.buildingId)?.neighborhood == hood) } ?: continue
            if (s.rivalBusinesses.any { it.targetBiz == b.id }) continue
            mine.targetBiz = b.id
            mine.priceFactor = if (r.strategy == 3) .82 else .87
            version++
            note(r, "started a price war at ${mine.name}")
            e.message("Rival News", "Price war! ${r.name} slashed prices at ${mine.name} to steal customers from your ${b.name}. Lower your prices, advertise or improve the store to hold on to them.")
            return
        }
    }

    /** Shops and offices nobody rents (looked up with sets: this runs for every rival each night). */
    private fun vacantCommercial(): List<Building> {
        val taken = HashSet<Int>(e.state.rivalBusinesses.size + e.state.rentals.size)
        e.state.rivalBusinesses.forEach { taken += it.buildingId }
        e.state.rentals.forEach { taken += it.buildingId }
        return e.city.buildings.filter { (it.kind == BKind.RETAIL || it.kind == BKind.OFFICE) && it.placeId == null && it.id !in taken }
    }

    private fun vacancyRate(): Double {
        val all = e.city.buildings.count { (it.kind == BKind.RETAIL || it.kind == BKind.OFFICE) && it.placeId == null }
        return if (all == 0) 0.0 else vacantCommercial().size.toDouble() / all
    }

    private fun expand(r: Rival, reserve: Double) {
        val s = e.state
        val budget = r.cash - reserve
        if (budget < 12_000) return
        val vacant = vacantCommercial()
        if (vacant.isEmpty()) return
        val candidates = ArrayList<Building>()
        // aggressive companies go where you make money
        if (r.aggression > .6 && rnd.nextDouble() < r.aggression * .5) {
            val best = s.businesses.filter { !it.type.facility }.maxByOrNull { weekRevenue(it) }
            val hood = best?.let { e.city.building(it.buildingId)?.neighborhood }
            if (hood != null) candidates += vacant.filter { it.neighborhood == hood }.shuffled(rnd).take(8)
        }
        // premium chains look in wealthy areas, discounters on busy streets (with some randomness, scored once per building)
        val sample = when (r.strategy) {
            2 -> vacant.map { it to City.neighborhoods[it.neighborhood].wealth + rnd.nextFloat() * .3f }.sortedByDescending { it.second }.take(30).map { it.first }
            3 -> vacant.map { it to it.traffic + rnd.nextFloat() * .25f }.sortedByDescending { it.second }.take(30).map { it.first }
            else -> vacant.shuffled(rnd).take(30)
        }
        candidates += sample
        var best: Triple<Building, BusinessTypeDef, Double>? = null
        for (bld in candidates.distinctBy { it.id }) {
            val pool = if (bld.kind == BKind.OFFICE) Catalog.officeTypes else Catalog.businessTypes
            val liked = r.focus.map { Catalog.type(it) }.filter { it in pool }
            val types = (liked + pool.shuffled(rnd).take(if (r.strategy == 4) 1 else 3)).distinct()
            for (t in types) {
                val cost = setupCost(t) + bld.baseRent / 2
                if (cost > budget) continue
                if (t.sources.contains(com.hustlecity.game.data.Source.IMPORT) && r.strategy == 3) continue
                val comp = competitionAround(t.id, bld.neighborhood, null) + 1.0
                val day = project(t, bld, 1, .5, basePrice(r), 0.0, comp - 1.0, false)
                val bias = if (t.id in r.focus) 1.25 else 1.0
                // expected profit, minus the fit-out spread over three months
                val score = (day.profit - cost / 90.0) * bias
                if (day.profit > 150 && score > 0 && (best == null || score > best.third)) best = Triple(bld, t, score)
            }
        }
        val (bld, t, _) = best ?: return
        val level = if (r.cash > setupCost(t) * 12) 2 else 1
        r.cash -= setupCost(t) * (if (level == 2) 1.8 else 1.0) + bld.baseRent / 2
        val name = Catalog.randomBusinessName(t.id, rnd)
        val rb = RivalBusiness(s.newId(), r.id, bld.id, t.id, name, level = level, reputation = .5, priceFactor = basePrice(r), opened = s.day)
        s.rivalBusinesses += rb
        version++
        note(r, "opened ${t.name} \"$name\" in ${City.neighborhoods[bld.neighborhood].name}")
        val near = playerNear(bld.neighborhood, t.id)
        if (near.isNotEmpty() || (t.office && s.businesses.any { it.typeId == t.id })) {
            e.message("Rival News", "${r.name} opened a ${t.name} \"$name\" at ${bld.address}" +
                (near.firstOrNull()?.let { ", right by your ${it.name}. Expect fewer customers." } ?: ". Expect more competition for clients."))
        } else if (playerNear(bld.neighborhood).isNotEmpty()) {
            e.message("Rival News", "${r.name} opened a ${t.name} \"$name\" at ${bld.address}, ${City.neighborhoods[bld.neighborhood].name}.", notify = false)
        }
    }

    private val marks = listOf(1_000_000.0, 2_500_000.0, 5_000_000.0, 10_000_000.0, 25_000_000.0)

    private fun milestoneCount(worth: Double) = marks.count { worth >= it }

    private fun milestones(r: Rival) {
        val reached = milestoneCount(r.netWorth)
        if (reached > r.milestone) {
            r.milestone = reached
            note(r, "passed ${e.moneyShort(marks[reached - 1])} net worth")
            if (reached >= 2) e.message("Rival News", "${r.name} is now worth over ${e.moneyShort(marks[reached - 1])} with ${storesOf(r).size} businesses.")
        }
    }
}

package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.data.Req
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.model.BizDay
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.DayRecord
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.MINUTES_PER_DAY
import com.hustlecity.game.model.RivalBusiness
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure calculations about a business, shared by the simulation and the UI. */
object BizCalc {
    fun count(b: Business, tag: String) = b.furniture.count { Catalog.item(it.itemId)?.tags?.contains(tag) == true }

    fun countFn(b: Business, fn: ItemFn) = b.furniture.count { Catalog.item(it.itemId)?.fn == fn }

    fun displayCapacity(b: Business, tag: String) = b.furniture.sumOf { p ->
        val def = Catalog.item(p.itemId)
        if (def != null && tag in def.tags) def.displayUnits else 0
    }

    fun capacityFor(b: Business, productId: String): Int {
        val p = Catalog.product(productId) ?: return 0
        return if (p.service) Int.MAX_VALUE else displayCapacity(b, p.tag)
    }

    fun requirements(b: Business): List<Pair<Req, Boolean>> = b.type.requirements.map { it to (count(b, it.tag) > 0) }

    fun requirementsMet(b: Business) = b.type.requirements.all { count(b, it.tag) > 0 }

    fun isWithinHours(b: Business, hour: Int): Boolean =
        if (b.openHour <= b.closeHour) hour in b.openHour until b.closeHour else hour >= b.openHour || hour < b.closeHour

    fun isOpen(b: Business, hour: Int) = !b.tempClosed && isWithinHours(b, hour)

    /** Skill values of everyone working the floor right now (player counts as 0.55). */
    fun workers(e: GameEngine, b: Business, hour: Int, weekday: Int): List<Double> {
        val out = ArrayList<Double>()
        if (e.state.player.workingAt == b.id) out += 0.55
        for (emp in e.state.employees) {
            if (emp.businessId == b.id && emp.role == b.type.staffRole && emp.worksAt(hour, weekday)) out += emp.skill
        }
        return out
    }

    fun cleaners(e: GameEngine, b: Business, hour: Int, weekday: Int) =
        e.state.employees.count { it.businessId == b.id && it.role == StaffRole.CLEANER && it.worksAt(hour, weekday) }

    fun promotion(b: Business, building: Building) = (building.traffic + b.marketing * .5).coerceIn(0.0, 1.0)

    fun competitors(e: GameEngine, b: Business): Int {
        val hood = e.city.building(b.buildingId)?.neighborhood ?: return 0
        val rivals = e.state.rivalBusinesses.count { it.typeId == b.typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        val mine = e.state.businesses.count { it.id != b.id && it.typeId == b.typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        return rivals + mine
    }

    fun competitorsFor(e: GameEngine, typeId: String, hood: Int): Int {
        val rivals = e.state.rivalBusinesses.count { it.typeId == typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        val mine = e.state.businesses.count { it.typeId == typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        return rivals + mine
    }

    /** Potential customers per hour before capacity limits. */
    fun demand(e: GameEngine, b: Business, hour: Int): Double {
        val building = e.city.building(b.buildingId) ?: return 0.0
        val type = b.type
        val promo = promotion(b, building)
        val hood = City.neighborhoods[building.neighborhood]
        val wealth = if (type.wealthSensitive) hood.wealth.toDouble().pow(1.6) else 1.0
        val comp = 1.0 / (1.0 + .15 * competitors(e, b))
        val rep = .55 + .9 * b.reputation
        return type.baseDemand * (.25 + 1.6 * promo) * type.curve[hour] * wealth * comp * rep
    }

    /** Customers per hour the business can serve with the given workers. */
    fun capacity(b: Business, building: Building?, workers: List<Double>): Int {
        if (workers.isEmpty()) return 0
        val n = workers.size
        val skill = workers.average()
        val registers = countFn(b, ItemFn.CASH_REGISTER)
        var cap = when (b.typeId) {
            "gym" -> {
                val eq = b.furniture.sumOf { p -> Catalog.item(p.itemId)?.takeIf { "workout" in it.tags }?.capacity ?: 0 } * 1.5
                val lockers = count(b, "locker") * 16.0
                val showers = count(b, "shower") * 10.0 * 1.5
                minOf(eq, lockers, showers, n * 22.0 * (.7 + .3 * skill))
            }
            "hairdresser" -> {
                val chairs = count(b, "hair_chair")
                if (registers == 0) 0.0 else min(chairs, n) * 2.2 * (.7 + .3 * skill)
            }
            else -> min(registers, n) * 40.0 * (.6 + .4 * skill)
        }
        if (building != null) cap = min(cap, building.customerLimit.toDouble())
        return cap.roundToInt()
    }

    fun decorScore(b: Business, building: Building?): Double {
        val area = building?.area ?: 75
        val pts = b.furniture.sumOf { p -> Catalog.item(p.itemId)?.let { it.decor + it.music * 2 } ?: 0 }
        return (pts / (area / 10.0)).coerceIn(0.0, 1.0)
    }

    /** Probability a customer accepts a price, given price / market price. */
    fun buyProbability(ratio: Double): Double = when {
        ratio <= .6 -> 1.0
        ratio <= 1.0 -> 1.0 - (ratio - .6) * .25
        ratio <= 1.25 -> .9 - (ratio - 1.0) * 1.2
        ratio <= 1.6 -> .6 - (ratio - 1.25) * 1.4
        ratio <= 2.0 -> max(0.0, .11 - (ratio - 1.6) * .3)
        else -> 0.0
    }

    fun marketPrice(e: GameEngine, b: Business, productId: String): Double {
        val p = Catalog.product(productId) ?: return 0.0
        val building = e.city.building(b.buildingId)
        val wealth = building?.let { City.neighborhoods[it.neighborhood].wealth } ?: 1f
        return p.price * (.85 + .15 * wealth)
    }

    fun furnitureValue(b: Business) = b.furniture.sumOf { Catalog.item(it.itemId)?.price ?: 0.0 } + b.storage.sumOf { Catalog.item(it)?.price ?: 0.0 }

    fun stockValue(b: Business) = b.stock.entries.sumOf { (id, q) -> (b.unitCost[id] ?: Catalog.product(id)?.cost ?: 0.0) * q }
}

/** Minute-by-minute business, staff, rival and finance simulation. */
class Economy(private val e: GameEngine) {
    /** Last computed served customers per hour, per business id (for visuals and Insight). */
    val servedRate = HashMap<Int, Double>()
    val capacityNow = HashMap<Int, Int>()

    fun minute() {
        val s = e.state
        val hour = s.hour
        val weekday = s.weekday
        for (b in s.businesses) simulateBusiness(b, hour, weekday)
        if (s.minuteOfHour == 0) hourly(hour, weekday)
        // deliveries arriving
        for (b in s.businesses) {
            if (b.pendingDeliveryAt in 0..s.minute) receiveDelivery(b)
        }
        // recruits ready
        val ready = s.recruits.filter { it.readyAt <= s.minute }
        if (ready.isNotEmpty()) {
            s.recruits.removeAll(ready)
            for (r in ready) generateCandidates(r.role, r.count)
            e.message("Apex Recruitment", "Your ${ready.joinToString { it.role.label }} candidates are ready. Open the Staff app to review and hire them.")
            e.toast("New candidates are ready in the Staff app", ToastKind.GOOD)
            s.objectivesDone += "recruit_ready"
        }
    }

    private fun simulateBusiness(b: Business, hour: Int, weekday: Int) {
        val building = e.city.building(b.buildingId)
        if (!BizCalc.isOpen(b, hour) || !BizCalc.requirementsMet(b)) {
            servedRate[b.id] = 0.0; capacityNow[b.id] = 0
            return
        }
        val workers = BizCalc.workers(e, b, hour, weekday)
        val demand = BizCalc.demand(e, b, hour)
        val cap = BizCalc.capacity(b, building, workers)
        capacityNow[b.id] = cap
        val served = min(demand, cap.toDouble())
        servedRate[b.id] = served
        val lostRate = max(0.0, demand - served)
        if (lostRate > 0) {
            b.custAcc += 0.0
            if (e.rnd.nextDouble() < lostRate / 60.0) b.lostToday++
        }
        if (workers.isEmpty()) return
        b.custAcc += served / 60.0
        val waitScore = if (demand <= 0.1) 1.0 else (served / demand).coerceIn(0.0, 1.0)
        val staffSkill = workers.average()
        val interior = BizCalc.decorScore(b, building)
        while (b.custAcc >= 1.0) {
            b.custAcc -= 1.0
            serveCustomer(b, building, waitScore, staffSkill, interior)
        }
    }

    private fun serveCustomer(b: Business, building: Building?, waitScore: Double, staffSkill: Double, interior: Double) {
        val rnd = e.rnd
        var wanted = 0
        var missing = 0
        var priceScore = 0.0
        var priceSamples = 0
        var bought = false
        val products = b.type.products
        val picks = products.filter { rnd.nextDouble() < it.second }.ifEmpty { listOf(products.first()) }
        for ((pid, _) in picks) {
            val p = Catalog.product(pid) ?: continue
            wanted++
            val available = if (p.service) BizCalc.count(b, p.tag) > 0 else (b.stock[pid] ?: 0) > 0
            if (!available) { missing++; continue }
            val price = b.prices[pid] ?: p.price
            val prob = BizCalc.buyProbability(price / BizCalc.marketPrice(e, b, pid))
            priceScore += prob; priceSamples++
            if (rnd.nextDouble() < prob) {
                bought = true
                b.revenueToday += price
                b.soldToday[pid] = (b.soldToday[pid] ?: 0) + 1
                if (!p.service) {
                    b.stock[pid] = (b.stock[pid] ?: 1) - 1
                    b.cogsToday += b.unitCost[pid] ?: p.cost
                }
            }
        }
        b.customersToday++
        b.hourlyToday[e.state.hour] = b.hourlyToday[e.state.hour] + 1
        val area = building?.area ?: 75
        b.cleanliness = (b.cleanliness - 28.0 / area).coerceAtLeast(0.0)
        b.satPrice += if (priceSamples == 0) .5 else priceScore / priceSamples
        b.satAvail += if (wanted == 0) 1.0 else 1.0 - missing.toDouble() / wanted
        b.satWait += waitScore
        b.satClean += b.cleanliness / 100.0
        b.satInterior += interior
        b.satStaff += staffSkill
        b.satSamples++
        if (!bought) b.lostToday++
    }

    private fun hourly(hour: Int, weekday: Int) {
        val s = e.state
        // previous hour index for wages: employees working during the hour that just ended
        val prevHour = (hour + 23) % 24
        val prevWeekday = if (hour == 0) (weekday + 6) % 7 else weekday
        var wages = 0.0
        for (emp in s.employees) {
            if (emp.businessId >= 0 && emp.worksAt(prevHour, prevWeekday)) {
                wages += emp.wage
                emp.hoursToday++
            }
        }
        if (wages > 0) e.spend(wages, "Wages", allowDebt = true, silent = true)
        // cleaning
        for (b in s.businesses) {
            val cleaners = BizCalc.cleaners(e, b, prevHour, prevWeekday)
            if (cleaners > 0) {
                val boost = if (BizCalc.countFn(b, ItemFn.CLEANING) > 0) 1.5 else 1.0
                b.cleanliness = (b.cleanliness + cleaners * 30.0 * boost).coerceAtMost(100.0)
            }
        }
        // scheduled deliveries
        if (Catalog.COURSE_LOGISTICS in s.player.courses) {
            for (b in s.businesses) {
                val c = b.delivery ?: continue
                if (c.enabled && c.hour == hour) runContract(b)
            }
        }
        e.happinessHourly()
    }

    private fun runContract(b: Business) {
        val c = b.delivery ?: return
        var cost = 0.0
        var units = 0
        for ((pid, target) in c.targets) {
            val p = Catalog.product(pid) ?: continue
            if (p.service) continue
            val have = b.stock[pid] ?: 0
            val cap = BizCalc.capacityFor(b, pid)
            val need = (min(target, cap) - have).coerceAtLeast(0)
            if (need == 0) continue
            val boxes = ceil(need / p.unitsPerBox.toDouble()).toInt()
            val qty = min(boxes * p.unitsPerBox, cap - have)
            val price = boxes * p.unitsPerBox * p.cost * 1.12
            if (e.state.player.money < price) continue
            e.spend(price, "Inventory", silent = true)
            addStock(b, pid, qty, price / qty)
            cost += price; units += qty
        }
        if (units > 0) {
            e.spend(60.0, "Delivery", allowDebt = true, silent = true)
            e.toast("${b.name}: delivery of $units units (${e.money(cost + 60)})", ToastKind.INFO)
        }
    }

    fun addStock(b: Business, pid: String, qty: Int, unitCost: Double) {
        if (qty <= 0) return
        val have = b.stock[pid] ?: 0
        val oldCost = b.unitCost[pid] ?: unitCost
        b.unitCost[pid] = (oldCost * have + unitCost * qty) / (have + qty)
        b.stock[pid] = have + qty
        if (b.prices[pid] == null) b.prices[pid] = Catalog.product(pid)?.price ?: 1.0
    }

    private fun receiveDelivery(b: Business) {
        var refund = 0.0
        for (item in b.pendingDelivery) {
            if (item.isProduct) {
                val p = Catalog.product(item.id) ?: continue
                val room = (BizCalc.capacityFor(b, item.id) - (b.stock[item.id] ?: 0)).coerceAtLeast(0)
                val qty = min(room, item.qty)
                addStock(b, item.id, qty, p.cost)
                refund += (item.qty - qty) * p.cost
            } else {
                b.storage += item.id
            }
        }
        b.pendingDelivery.clear()
        b.pendingDeliveryAt = -1
        if (refund > 0) e.earn(refund, "Refunds")
        e.toast("Delivery arrived at ${b.name}" + if (refund > 0) " (overflow refunded ${e.money(refund)})" else "", ToastKind.GOOD)
    }

    fun generateCandidates(role: StaffRole, count: Int) {
        val s = e.state
        repeat(count) {
            val skill = (0.2 + e.rnd.nextDouble() * 0.6).coerceIn(0.1, 0.95)
            val wage = ((role.baseWage * (0.8 + skill * 0.7)) * 2).roundToInt() / 2.0
            val name = "${Catalog.firstNames.random(e.rnd)} ${Catalog.lastNames.random(e.rnd)}"
            s.candidates += Employee(s.newId(), name, role, skill, wage, wage, 19 + e.rnd.nextInt(40), expiresDay = s.day + 3)
        }
    }

    /** End of day settlement. Called when the clock reaches 00:00. */
    fun midnight() {
        val s = e.state
        val finishedDay = s.day - 1
        var sales = 0.0
        // business settlement
        for (b in s.businesses) {
            sales += b.revenueToday
            val rental = s.rentalFor(b.buildingId)
            val rent = if (rental != null && !rental.owned) rental.rentPerMonth / 30.0 else 0.0
            val marketingCost = b.campaigns.sumOf { Catalog.campaign(it.campaignId).pricePerDay }
            val sat = if (b.satSamples > 0) {
                val n = b.satSamples.toDouble()
                val f = listOf(b.satPrice / n, b.satAvail / n, b.satWait / n, b.satClean / n, b.satInterior / n, b.satStaff / n)
                b.lastFactors = f.toMutableList()
                val v = f[0] * .25 + f[1] * .2 + f[2] * .2 + f[3] * .15 + f[4] * .1 + f[5] * .1
                b.lastSatisfaction = v
                v
            } else -1.0
            if (sat >= 0 && b.customersToday >= 5) b.reputation = (b.reputation * .6 + sat * .4).coerceIn(0.0, 1.0)
            val wages = s.employees.filter { it.businessId == b.id }.sumOf { it.hoursToday * it.wage }
            b.history += BizDay(finishedDay, b.revenueToday, b.cogsToday, wages, rent, marketingCost, b.customersToday, sat)
            if (b.history.size > 30) b.history.removeAt(0)
            for (h in 0 until 24) { b.hourlyYesterday[h] = b.hourlyToday[h]; b.hourlyToday[h] = 0 }
            b.revenueToday = 0.0; b.cogsToday = 0.0; b.customersToday = 0; b.lostToday = 0
            b.soldToday.clear()
            b.satPrice = 0.0; b.satAvail = 0.0; b.satWait = 0.0; b.satClean = 0.0; b.satInterior = 0.0; b.satStaff = 0.0; b.satSamples = 0
            // marketing
            if (marketingCost > 0) e.spend(marketingCost, "Marketing", allowDebt = true, silent = true)
            b.campaigns.forEach { it.daysLeft-- }
            b.campaigns.removeAll { it.daysLeft <= 0 }
            val active = b.campaigns.sumOf { Catalog.campaign(it.campaignId).boost }.coerceAtMost(1.0)
            b.marketing = max(active, b.marketing * .65).let { if (it < .01) 0.0 else it }
        }
        if (sales > 0) {
            e.earn(sales, "Sales")
            s.player.totalEarned += sales
            s.objectivesDone += "collect"
        }
        // rent
        for (r in s.rentals) if (!r.owned) e.spend(r.rentPerMonth / 30.0, "Rent", allowDebt = true, silent = true)
        // loans
        val paidOff = ArrayList<Int>()
        for (loan in s.loans) {
            val interest = loan.remaining * loan.dailyRate
            val principal = min(loan.dailyPrincipal, loan.remaining)
            e.spend(interest, "Interest", allowDebt = true, silent = true)
            e.spend(principal, "Loan repayment", allowDebt = true, silent = true)
            loan.remaining -= principal
            loan.daysLeft--
            if (loan.remaining <= 0.5 || loan.daysLeft <= 0) paidOff += loan.id
        }
        if (paidOff.isNotEmpty()) {
            s.loans.removeAll { it.id in paidOff }
            e.message("Hudson Bank", "Congratulations! Your loan has been fully repaid.")
        }
        employeesDaily()
        rivalsDaily()
        s.candidates.removeAll { it.expiresDay < s.day }
        // ledger -> day record
        val expenses = s.ledgerToday.filterKeys { !it.startsWith("in:") }
        val income = s.ledgerToday.filterKeys { it.startsWith("in:") }.values.sum()
        val totalExp = expenses.values.sum()
        val record = DayRecord(finishedDay, income, expenses, income - totalExp, s.player.money, e.netWorth(), s.businesses.size)
        s.history += record
        if (s.history.size > 60) s.history.removeAt(0)
        s.ledgerToday.clear()
        if (s.player.money < 0) {
            s.player.happiness = (s.player.happiness - 10).coerceAtLeast(0.0)
            e.message("Hudson Bank", "Your account is overdrawn (${e.money(s.player.money)}). Sell assets or earn money quickly!")
        }
        e.ui.summary = record
        e.summaryShownAt = e.realTime
    }

    private fun employeesDaily() {
        val s = e.state
        val leadership = Catalog.COURSE_LEADERSHIP in s.player.courses
        val quitters = ArrayList<Employee>()
        for (emp in s.employees) {
            val wageRatio = emp.wage / emp.demand
            val hours = emp.weeklyHours
            var target = .55 + (wageRatio - 1.0) * 1.5
            if (hours > 50) target -= (hours - 50) * .02
            if (hours < 16 && emp.businessId >= 0) target -= .1
            if (emp.businessId < 0) target -= .15
            if (leadership) target += .15
            target = target.coerceIn(0.0, 1.0)
            emp.satisfaction = (emp.satisfaction * .7 + target * .3).coerceIn(0.0, 1.0)
            if (emp.hoursToday > 0) emp.skill = (emp.skill + (if (leadership) .006 else .004)).coerceAtMost(1.0)
            emp.hoursToday = 0
            if (emp.satisfaction < .25) emp.unhappyDays++ else emp.unhappyDays = 0
            if (emp.unhappyDays >= 3) quitters += emp
        }
        for (q in quitters) {
            s.employees.remove(q)
            e.message(q.name, "I quit. The pay and hours just weren't working for me.")
            e.toast("${q.name} quit their job", ToastKind.BAD)
        }
    }

    private fun rivalsDaily() {
        val s = e.state
        for (r in s.rivals) {
            val biz = s.rivalBusinesses.filter { it.rivalId == r.id }
            var profit = 0.0
            for (rb in biz) profit += Catalog.type(rb.typeId).baseDemand * (6 + e.rnd.nextDouble() * 8) - 150
            r.cash += profit
            r.trend = profit
            r.netWorth = r.cash + biz.size * 65000.0
            if (r.cash > 150_000 && e.rnd.nextDouble() < .22) {
                val free = e.city.buildings.filter { it.kind == BKind.RETAIL && e.isVacant(it.id) }
                if (free.isNotEmpty()) {
                    val bld = free[e.rnd.nextInt(free.size)]
                    val type = Catalog.businessTypes.filter { it.id != "electronics" || e.rnd.nextBoolean() }.random(e.rnd)
                    val name = Catalog.randomBusinessName(type.id, e.rnd)
                    s.rivalBusinesses += RivalBusiness(s.newId(), r.id, bld.id, type.id, name)
                    r.cash -= 90_000
                    e.message("Rival News", "${r.name} opened a ${type.name} \"$name\" at ${bld.address}, ${City.neighborhoods[bld.neighborhood].name}.")
                }
            } else if (biz.size > 3 && e.rnd.nextDouble() < .04) {
                val rb = biz[e.rnd.nextInt(biz.size)]
                s.rivalBusinesses.remove(rb)
                r.cash += 30_000
                e.message("Rival News", "${r.name} closed ${rb.name} at ${e.city.building(rb.buildingId)?.address}. The space is now for rent.")
            }
        }
    }

    companion object {
        fun minutesUntil(currentMinute: Long, hour: Int): Long {
            val dayStart = (currentMinute / MINUTES_PER_DAY) * MINUTES_PER_DAY
            var t = dayStart + hour * 60
            if (t <= currentMinute) t += MINUTES_PER_DAY
            return t - currentMinute
        }
    }
}

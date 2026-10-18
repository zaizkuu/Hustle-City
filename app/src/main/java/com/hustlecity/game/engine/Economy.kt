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

    /** Display space for a product (shared with every other product that uses the same kind of display). */
    fun capacityFor(b: Business, productId: String): Int {
        val p = Catalog.product(productId) ?: return 0
        return if (p.service) Int.MAX_VALUE else displayCapacity(b, p.tag)
    }

    /** Units of a product that still fit, after everything already stocked on the same displays. */
    fun roomFor(b: Business, productId: String): Int {
        val p = Catalog.product(productId) ?: return 0
        if (p.service) return 0
        if (isWarehouse(b)) return ((warehouseBoxes(b) - warehouseBoxesUsed(b)).coerceAtLeast(0) * p.unitsPerBox)
        val used = b.stock.entries.sumOf { (id, q) -> if (Catalog.product(id)?.tag == p.tag) q else 0 }
        return (displayCapacity(b, p.tag) - used).coerceAtLeast(0)
    }

    /** Whether the business takes this product: shops take what they sell, warehouses take any boxed goods. */
    fun sells(b: Business, productId: String): Boolean {
        if (isWarehouse(b)) return Catalog.product(productId)?.service == false
        return b.type.products.any { it.first == productId }
    }

    /** Boxes the back room's storage shelves hold (warehouses keep everything on their pallet racks instead). */
    fun storageBoxes(b: Business) = if (isWarehouse(b)) 0 else b.furniture.sumOf { p ->
        Catalog.item(p.itemId)?.takeIf { it.fn == ItemFn.STORAGE }?.displayUnits ?: 0
    }

    /** Boxes needed for [units] of a product (a part-full box still takes a whole space). */
    fun boxesOf(pid: String, units: Int): Int {
        val per = (Catalog.product(pid)?.unitsPerBox ?: 1).coerceAtLeast(1)
        return if (units <= 0) 0 else (units + per - 1) / per
    }

    /** Boxes on the back room's storage shelves: each product is boxed on its own. */
    fun storageBoxesUsed(b: Business) = b.backStock.entries.sumOf { (id, q) -> boxesOf(id, q) }

    /** Units of a product that still fit on the storage shelves, topping up its part-full box first. */
    fun storageRoomFor(b: Business, productId: String): Int {
        val p = Catalog.product(productId) ?: return 0
        if (p.service || isWarehouse(b)) return 0
        val per = p.unitsPerBox.coerceAtLeast(1)
        val have = b.backStock[productId] ?: 0
        val partial = if (have % per == 0) 0 else per - have % per
        return (storageBoxes(b) - storageBoxesUsed(b)).coerceAtLeast(0) * per + partial
    }

    /** Everything of a product the store has: on its displays plus boxed in the back room. */
    fun onHand(b: Business, productId: String) = (b.stock[productId] ?: 0) + (b.backStock[productId] ?: 0)

    /** Units of a product the store can take in: room on its displays, then on the back room's storage shelves. */
    fun intakeRoom(b: Business, productId: String) = roomFor(b, productId) + storageRoomFor(b, productId)

    fun isWarehouse(b: Business) = b.typeId == "warehouse"
    fun isHq(b: Business) = b.typeId == "hq"

    /** Box capacity of a warehouse (all its pallet racks). */
    fun warehouseBoxes(b: Business) = displayCapacity(b, "pallet_rack")

    fun warehouseBoxesUsed(b: Business) = b.stock.entries.sumOf { (id, q) ->
        val per = Catalog.product(id)?.unitsPerBox ?: 1
        if (q <= 0) 0 else (q + per - 1) / per
    }

    /** Managers of a role assigned to a furnished headquarters. */
    fun managers(e: GameEngine, role: StaffRole): Int {
        val hqs = e.state.businesses.filter { isHq(it) && requirementsMet(it) }.map { it.id }.toSet()
        if (hqs.isEmpty()) return 0
        // each manager needs a desk
        val desks = e.state.businesses.filter { it.id in hqs }.sumOf { count(it, "desk") }
        return minOf(desks, e.state.employees.count { it.role == role && it.businessId in hqs })
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

    /** Performers on shift (DJ, projectionists, actors) for types that need one. */
    fun performers(e: GameEngine, b: Business, hour: Int, weekday: Int): List<Double> {
        val role = b.type.performer ?: return emptyList()
        return e.state.employees.filter { it.businessId == b.id && it.role == role && it.worksAt(hour, weekday) }.map { it.skill }
    }

    /** Customers per hour the [n] busiest registers can check out (counters are faster than registers). */
    fun registerCapacity(b: Business, n: Int): Double =
        b.furniture.mapNotNull { p -> Catalog.item(p.itemId)?.takeIf { it.fn == ItemFn.CASH_REGISTER }?.let { if (it.capacity > 0) it.capacity else 40 } }
            .sortedDescending().take(n).sum().toDouble()

    /** Share of shoplifting attempts that security stops. */
    fun security(b: Business) = (count(b, "security_cam") * .3 + count(b, "security_gate") * .55).coerceAtMost(.95)

    fun cleaners(e: GameEngine, b: Business, hour: Int, weekday: Int) =
        e.state.employees.count { it.businessId == b.id && it.role == StaffRole.CLEANER && it.worksAt(hour, weekday) }

    fun promotion(b: Business, building: Building) = (building.traffic + b.marketing * .5).coerceIn(0.0, 1.0)

    fun competitors(e: GameEngine, b: Business): Int {
        val hood = e.city.building(b.buildingId)?.neighborhood ?: return 0
        val rivals = e.state.rivalBusinesses.count { it.typeId == b.typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        val mine = e.state.businesses.count { it.id != b.id && it.typeId == b.typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        return rivals + mine
    }

    /** Office firms compete for clients across the whole city. */
    fun cityCompetitors(e: GameEngine, typeId: String) =
        e.state.rivalBusinesses.count { it.typeId == typeId } + e.state.businesses.count { it.typeId == typeId }

    fun competitorsFor(e: GameEngine, typeId: String, hood: Int): Int {
        val rivals = e.state.rivalBusinesses.count { it.typeId == typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        val mine = e.state.businesses.count { it.typeId == typeId && e.city.building(it.buildingId)?.neighborhood == hood }
        return rivals + mine
    }

    /** Potential customers per hour before capacity limits. */
    fun demand(e: GameEngine, b: Business, hour: Int): Double {
        val building = e.city.building(b.buildingId) ?: return 0.0
        val type = b.type
        if (type.office) {
            // clients find firms by reputation and advertising rather than foot traffic
            val comp = 1.0 / (1.0 + .06 * e.economy.rivals.competitionFor(b))
            return type.baseDemand * (.35 + .9 * b.reputation) * (1.0 + b.marketing) * type.curve[hour] * comp * e.weather.demandFactor(type)
        }
        val promo = promotion(b, building)
        val hood = City.neighborhoods[building.neighborhood]
        val wealth = if (type.wealthSensitive) hood.wealth.toDouble().pow(1.6) else 1.0
        val comp = 1.0 / (1.0 + .15 * e.economy.rivals.competitionFor(b))
        val rep = .55 + .9 * b.reputation
        return type.baseDemand * (.25 + 1.6 * promo) * type.curve[hour] * wealth * comp * rep * e.weather.demandFactor(type)
    }

    /** Customers per hour the business can serve with the given workers. */
    fun capacity(b: Business, building: Building?, workers: List<Double>): Int = capacityRate(b, building, workers).roundToInt()

    fun capacityRate(b: Business, building: Building?, workers: List<Double>): Double {
        if (workers.isEmpty()) return 0.0
        val n = workers.size
        val skill = workers.average()
        // billable hours: one per professional at a desk, more for skilled staff
        if (b.type.office) return min(count(b, "desk"), n) * (.6 + .4 * skill) * (if (count(b, "copier") > 0) 1.05 else 1.0)
        val registers = countFn(b, ItemFn.CASH_REGISTER)
        val seats = b.furniture.sumOf { p -> Catalog.item(p.itemId)?.takeIf { "cinema_seat" in it.tags }?.capacity ?: 0 }.toDouble()
        var cap = when (b.typeId) {
            "nightclub" -> {
                val bars = count(b, "bar")
                val floor = count(b, "dance_floor") * 35.0
                minOf(min(bars, n) * 45.0 * (.6 + .4 * skill), floor)
            }
            "cinema" -> minOf(registerCapacity(b, n) * (.6 + .4 * skill), seats, count(b, "screen") * 45.0)
            "theater" -> minOf(registerCapacity(b, n) * (.6 + .4 * skill), seats, count(b, "stage") * 40.0)
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
            "produce" -> registerCapacity(b, n) * (.6 + .4 * skill) * (if (count(b, "scale") > 0) 1.15 else 1.0)
            else -> registerCapacity(b, n) * (.6 + .4 * skill)
        }
        // shopping baskets let more people shop at once
        if (building != null) cap = min(cap, building.customerLimit + 15.0 * min(3, count(b, "baskets")))
        return cap
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

    fun stockValue(b: Business) = b.stock.entries.sumOf { (id, q) -> (b.unitCost[id] ?: Catalog.product(id)?.cost ?: 0.0) * q } +
        b.backStock.entries.sumOf { (id, q) -> (b.unitCost[id] ?: Catalog.product(id)?.cost ?: 0.0) * q }
}

/** Minute-by-minute business, staff, rival and finance simulation. */
class Economy(private val e: GameEngine) {
    /** Last computed served customers per hour, per business id (for visuals and Insight). */
    val servedRate = HashMap<Int, Double>()
    val rivals = RivalAI(e)
    val dirt = DirtModel(e)
    val capacityNow = HashMap<Int, Int>()

    fun minute() {
        val s = e.state
        val hour = s.hour
        val weekday = s.weekday
        for (b in s.businesses) simulateBusiness(b, hour, weekday)
        for (b in s.businesses) if (b.backStock.isNotEmpty()) restockTick(b, hour, weekday)
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
        if (b.type.facility) return
        val building = e.city.building(b.buildingId)
        if (!BizCalc.isOpen(b, hour) || !BizCalc.requirementsMet(b)) {
            servedRate[b.id] = 0.0; capacityNow[b.id] = 0
            return
        }
        val workers = BizCalc.workers(e, b, hour, weekday)
        val demand = BizCalc.demand(e, b, hour)
        // clubs, cinemas and theaters can't run the show without their DJ, projectionist or actors
        val showOn = b.type.performer == null || BizCalc.performers(e, b, hour, weekday).isNotEmpty()
        val cap = if (showOn) BizCalc.capacityRate(b, building, workers) else 0.0
        capacityNow[b.id] = cap.roundToInt()
        val served = min(demand, cap)
        servedRate[b.id] = served
        val lostRate = max(0.0, demand - served)
        if (lostRate > 0) {
            b.lostAcc += lostRate / 60.0
            while (b.lostAcc >= 1.0) { b.lostAcc -= 1.0; b.lostToday++ }
        }
        if (workers.isEmpty()) return
        b.custAcc += served / 60.0
        val waitScore = if (demand <= 0.1) 1.0 else (served / demand).coerceIn(0.0, 1.0)
        val perf = BizCalc.performers(e, b, hour, weekday)
        // a better DJ or cast makes for happier guests
        val staffSkill = if (perf.isEmpty()) workers.average() else (workers.average() + perf.average()) / 2
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
        if (products.isEmpty()) return
        // what people want depends on the weather: umbrellas in the rain, ice cream in the heat
        val picks = products.filter { rnd.nextDouble() < (it.second * e.weather.productFactor(it.first)).coerceAtMost(.95) }.ifEmpty { listOf(products.first()) }
        for ((pid, _) in picks) {
            val p = Catalog.product(pid) ?: continue
            val available = if (p.service) BizCalc.count(b, p.tag) > 0 else (b.stock[pid] ?: 0) > 0
            // extras the store doesn't carry are no disappointment
            if (!available && pid in b.type.optional && (p.service || BizCalc.capacityFor(b, pid) == 0)) continue
            wanted++
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
        // office clients rarely visit; shops get dirty with every customer, along the routes they walk
        if (!b.type.office) dirt.onCustomer(b, area)
        // shoplifting: valuable, easy-to-pocket goods go missing unless security stops it
        if (b.type.theftRisk > 0 && rnd.nextDouble() < b.type.theftRisk * (1 - BizCalc.security(b))) {
            val stocked = b.stock.entries.filter { it.value > 0 }
            if (stocked.isNotEmpty()) {
                val (pid, q) = stocked[rnd.nextInt(stocked.size)]
                b.stock[pid] = q - 1
                b.stolenToday += b.unitCost[pid] ?: Catalog.product(pid)?.cost ?: 0.0
            }
        }
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
                dirt.clean(b, cleaners * 30.0 * boost)
            }
        }
        // the store restocks its displays from the back room when it closes for the night
        for (b in s.businesses) if (hour == b.closeHour && b.backStock.isNotEmpty()) restockFromBack(b, Int.MAX_VALUE, eager = true)
        if (hour == 6) purchasingRun()
        e.weather.hourly()
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
            val have = BizCalc.onHand(b, pid)
            val need = min((target - have).coerceAtLeast(0), BizCalc.intakeRoom(b, pid))
            if (need == 0) continue
            val r = supply(b, pid, need, 1.12)
            cost += r.first; units += r.second
        }
        if (units > 0) {
            e.spend(60.0, "Delivery", allowDebt = true, silent = true)
            e.toast("${b.name}: delivery of $units units (${e.money(cost + 60)})", ToastKind.INFO)
        }
    }

    /**
     * Moves [need] units of a product into a store: first from the player's warehouses (already paid for),
     * then bought from the wholesaler at cost × [markup]. Returns (money spent, units delivered).
     */
    fun supply(b: Business, pid: String, need: Int, markup: Double): Pair<Double, Int> {
        val p = Catalog.product(pid) ?: return 0.0 to 0
        var left = need
        var units = 0
        for (w in e.state.businesses) {
            if (left <= 0) break
            if (!BizCalc.isWarehouse(w)) continue
            val have = w.stock[pid] ?: 0
            if (have <= 0) continue
            val take = stockIn(b, pid, min(have, left), w.unitCost[pid] ?: p.cost)
            if (take <= 0) break
            w.stock[pid] = have - take
            left -= take; units += take
        }
        var cost = 0.0
        if (left > 0) {
            if (p.source == com.hustlecity.game.data.Source.IMPORT && Catalog.COURSE_IMPORT !in e.state.player.courses) return 0.0 to units
            left = min(left, BizCalc.intakeRoom(b, pid))
            val price = left * p.cost * markup
            if (left > 0 && e.state.player.money >= price) {
                e.spend(price, "Inventory", silent = true)
                stockIn(b, pid, left, price / left)
                cost = price; units += left
            }
        }
        return cost to units
    }

    /** Purchasing agents at HQ restock every store (without its own contract) each morning. */
    private fun purchasingRun() {
        val agents = BizCalc.managers(e, StaffRole.PURCHASING_AGENT)
        if (agents == 0) return
        val stores = e.state.businesses.filter { !it.type.facility && (it.delivery?.enabled != true) }.take(agents * 4)
        var total = 0.0
        var count = 0
        for (b in stores) {
            var units = 0
            for ((pid, _) in b.type.products) {
                val p = Catalog.product(pid) ?: continue
                if (p.service) continue
                val room = BizCalc.intakeRoom(b, pid)
                if (room <= 0) continue
                val r = supply(b, pid, room, 1.08)
                total += r.first; units += r.second
            }
            if (units > 0) { e.spend(30.0, "Delivery", allowDebt = true, silent = true); total += 30; count++ }
        }
        if (count > 0) e.toast("Purchasing restocked $count store(s) for ${e.money(total)}", ToastKind.INFO)
    }

    /** Puts [qty] units straight onto the displays (the caller checked they fit). */
    fun addStock(b: Business, pid: String, qty: Int, unitCost: Double) {
        if (qty <= 0) return
        blendCost(b, pid, qty, unitCost)
        b.stock[pid] = (b.stock[pid] ?: 0) + qty
    }

    /** Keeps the average purchase cost per unit over everything the store holds of a product. */
    private fun blendCost(b: Business, pid: String, qty: Int, unitCost: Double) {
        val have = BizCalc.onHand(b, pid)
        val oldCost = b.unitCost[pid] ?: unitCost
        b.unitCost[pid] = (oldCost * have + unitCost * qty) / (have + qty)
        if (b.prices[pid] == null) b.prices[pid] = Catalog.product(pid)?.price ?: 1.0
    }

    /**
     * Takes stock into a store the way Big Ambitions does: onto its displays while they have room, and what doesn't
     * fit boxed on the back room's storage shelves. Returns the units taken (the rest fits nowhere).
     */
    fun stockIn(b: Business, pid: String, qty: Int, unitCost: Double): Int {
        if (qty <= 0) return 0
        val shelf = min(qty, BizCalc.roomFor(b, pid))
        val back = min(qty - shelf, BizCalc.storageRoomFor(b, pid))
        if (shelf + back <= 0) return 0
        blendCost(b, pid, shelf + back, unitCost)
        if (shelf > 0) b.stock[pid] = (b.stock[pid] ?: 0) + shelf
        if (back > 0) b.backStock[pid] = (b.backStock[pid] ?: 0) + back
        return shelf + back
    }

    /** Boxes [qty] units on the back room's storage shelves only. Returns the units that fit. */
    fun stockBack(b: Business, pid: String, qty: Int, unitCost: Double): Int {
        val take = min(qty, BizCalc.storageRoomFor(b, pid))
        if (take <= 0) return 0
        blendCost(b, pid, take, unitCost)
        b.backStock[pid] = (b.backStock[pid] ?: 0) + take
        return take
    }

    /** People who can carry boxes out of the back room right now: staff on shift, and you while you're in the store. */
    fun crew(b: Business, hour: Int, weekday: Int): Int {
        var n = if (e.state.player.inBuilding == b.buildingId) 1 else 0
        for (emp in e.state.employees) if (emp.businessId == b.id && emp.worksAt(hour, weekday)) n++
        return n
    }

    private fun restockTick(b: Business, hour: Int, weekday: Int) {
        if (b.type.facility) return
        val crew = crew(b, hour, weekday)
        if (crew > 0) restockFromBack(b, crew)
    }

    /**
     * Carries up to [boxes] boxes from the back room to the displays: a product's box goes out once it fits (or the
     * product has sold out on the shelf), emptiest displays first. When [eager], anything that fits goes out, part
     * boxes too (the night restock at closing time). Returns the units moved.
     */
    fun restockFromBack(b: Business, boxes: Int, eager: Boolean = false): Int {
        if (b.backStock.isEmpty() || boxes <= 0) return 0
        var left = boxes
        var moved = 0
        for (pid in b.backStock.keys.sortedBy { b.stock[it] ?: 0 }) {
            val per = (Catalog.product(pid)?.unitsPerBox ?: 1).coerceAtLeast(1)
            while (left > 0) {
                val back = b.backStock[pid] ?: 0
                val room = BizCalc.roomFor(b, pid)
                if (back <= 0 || room <= 0) break
                if (!eager && (b.stock[pid] ?: 0) > 0 && room < min(per, back)) break
                val take = if (eager) min(back, room) else minOf(back, room, per)
                b.stock[pid] = (b.stock[pid] ?: 0) + take
                if (back > take) b.backStock[pid] = back - take else b.backStock.remove(pid)
                moved += take
                if (eager) break else left--
            }
            if (left <= 0) break
        }
        return moved
    }

    private fun receiveDelivery(b: Business) {
        var refund = 0.0
        for (item in b.pendingDelivery) {
            if (item.isProduct) {
                val p = Catalog.product(item.id) ?: continue
                // displays first, then the back room; only what fits nowhere is refunded
                val qty = if (BizCalc.sells(b, item.id)) stockIn(b, item.id, item.qty, p.cost) else 0
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
            // the day that just ended is used up; bill tomorrow for the campaigns still running
            b.campaigns.forEach { it.daysLeft-- }
            b.campaigns.removeAll { it.daysLeft <= 0 }
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
            val fees = if (b.tempClosed) 0.0 else b.type.dailyFee
            if (fees > 0) e.spend(fees, "Licences", allowDebt = true, silent = true)
            b.history += BizDay(finishedDay, b.revenueToday, b.cogsToday, wages, rent, marketingCost, b.customersToday, sat, fees, b.stolenToday)
            if (b.stolenToday >= 40 && BizCalc.security(b) < .3) {
                e.message("${b.name}", "Shoplifters took ${e.money(b.stolenToday)} of stock yesterday. Security cameras or anti-theft gates (Essentials Appliances) stop most of it.")
            }
            b.stolenToday = 0.0
            if (b.history.size > 30) b.history.removeAt(0)
            for (h in 0 until 24) { b.hourlyYesterday[h] = b.hourlyToday[h]; b.hourlyToday[h] = 0 }
            b.revenueToday = 0.0; b.cogsToday = 0.0; b.customersToday = 0; b.lostToday = 0
            b.soldToday.clear()
            b.satPrice = 0.0; b.satAvail = 0.0; b.satWait = 0.0; b.satClean = 0.0; b.satInterior = 0.0; b.satStaff = 0.0; b.satSamples = 0
            // marketing
            if (marketingCost > 0) e.spend(marketingCost, "Marketing", allowDebt = true, silent = true)
            val active = b.campaigns.sumOf { Catalog.campaign(it.campaignId).boost }.coerceAtMost(1.0)
            b.marketing = max(active, b.marketing * .65).let { if (it < .01) 0.0 else it }
        }
        if (sales > 0) {
            com.hustlecity.game.audio.Audio.play(com.hustlecity.game.audio.Sfx.CASH)
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
        // profit counts running the business only: loans, repayments and buying/selling assets are not income or costs
        val income = s.ledgerToday.filterKeys { it.startsWith("in:") && it !in NON_OPERATING }.values.sum()
        val operatingExp = expenses.filterKeys { it !in NON_OPERATING }.values.sum()
        val record = DayRecord(finishedDay, income, expenses, income - operatingExp, s.player.money, e.netWorth(), s.businesses.size)
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
        val hr = BizCalc.managers(e, StaffRole.HR_MANAGER)
        val hrCovers = hr > 0 && s.employees.size <= hr * 12
        val quitters = ArrayList<Employee>()
        for (emp in s.employees) {
            val wageRatio = emp.wage / emp.demand
            val hours = emp.weeklyHours
            var target = .55 + (wageRatio - 1.0) * 1.5
            if (hours > 50) target -= (hours - 50) * .02
            if (hours < 16 && emp.businessId >= 0) target -= .1
            if (emp.businessId < 0) target -= .15
            if (leadership) target += .15
            if (hrCovers) target += .1
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
            if (hr > 0 && q.businessId >= 0) {
                // HR finds a replacement with the same schedule
                val skill = (0.3 + e.rnd.nextDouble() * 0.35)
                val wage = ((q.role.baseWage * (0.85 + skill * 0.6)) * 2).roundToInt() / 2.0
                val name = "${Catalog.firstNames.random(e.rnd)} ${Catalog.lastNames.random(e.rnd)}"
                s.employees += Employee(s.newId(), name, q.role, skill, wage, wage, 20 + e.rnd.nextInt(35), businessId = q.businessId,
                    shiftStart = q.shiftStart, shiftEnd = q.shiftEnd, days = q.days, hiredDay = s.day)
                e.message("HR", "${q.name} quit. I hired $name as a replacement (${q.role.label}, ${e.money(wage)}/h) with the same schedule.")
            } else {
                e.toast("${q.name} quit their job", ToastKind.BAD)
            }
        }
    }

    private fun rivalsDaily() = rivals.daily()

    companion object {
        val NON_OPERATING = setOf("in:Loans", "Loan repayment", "Vehicles", "Real estate", "Furniture", "in:Sold assets", "in:Refunds")

        fun minutesUntil(currentMinute: Long, hour: Int): Long {
            val dayStart = (currentMinute / MINUTES_PER_DAY) * MINUTES_PER_DAY
            var t = dayStart + hour * 60
            if (t <= currentMinute) t += MINUTES_PER_DAY
            return t - currentMinute
        }
    }
}

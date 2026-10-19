package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.Source
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.model.Bond
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.DeliveryContract
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.FleetDrop
import com.hustlecity.game.model.FleetRoute
import com.hustlecity.game.model.LEASE_DAYS
import com.hustlecity.game.model.MINUTES_PER_DAY
import com.hustlecity.game.model.OwnedVehicle
import com.hustlecity.game.model.Rival
import com.hustlecity.game.model.RivalBusiness
import com.hustlecity.game.model.Ticker
import com.hustlecity.game.world.Look
import com.hustlecity.game.world.dist
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** [com.hustlecity.game.model.RivalBusiness.rivalId] of a rival store you have bought: it trades for you under its old manager. */
const val SUBSIDIARY = -1

/** The standard shifts, as (start, end) hours. */
enum class Shift(val label: String, val start: Int, val end: Int) {
    MORNING("Morning", 6, 14), AFTERNOON("Afternoon", 14, 22), GRAVEYARD("Graveyard", 22, 6);

    companion object {
        fun of(emp: Employee) = entries.firstOrNull { it.start == emp.shiftStart && it.end == emp.shiftEnd }
    }
}

/** Something a store pays for each day to keep its staff fresh and happy. [cost] is per assigned employee per day. */
class PerkDef(val id: String, val name: String, val cost: Double, val description: String)

/**
 * The running of a business empire beyond the shop floor: vans on delivery routes, automatic reordering, shifts and
 * fatigue, store managers, perks and training, rivals poaching staff, buying rival stores, lease renewals, and the
 * stock exchange and city bonds.
 */
class Operations(private val e: GameEngine) {
    companion object {
        val PERKS = listOf(
            PerkDef("coffee", "Free coffee", 3.0, "Staff tire 25% slower on shift."),
            PerkDef("health", "Health plan", 9.0, "Staff recover faster between shifts and are more satisfied."),
            PerkDef("bonus", "Bonus scheme", 6.0, "Staff are more satisfied and work 5% better."),
        )

        /** Wholesalers a route can load at, as (source id, place id, label). */
        val SOURCES = listOf(Triple("metro", "metro", "Metro Wholesale"), Triple("distro", "distro", "CityDistro Wholesale"), Triple("import", "imports", "Global Imports Co."))

        /** City bond terms: days to maturity and yearly rate. */
        val BOND_TERMS = listOf(30 to .04, 90 to .055, 180 to .07)

        /** Shares a rival has on the market; owning more than half lets you take it over. */
        const val RIVAL_SHARES = 50_000
        const val BROKERAGE = .005
        const val HEADHUNT_FEE = 2500.0

        /** Pace of a delivery van through town, in meters per minute (with stops at junctions). */
        private const val VAN_PACE = 420f

        /** How busy a district is at an hour, relative to its average: offices empty out at night, nightlife wakes up. */
        fun districtFactor(look: Look, hour: Int, weekday: Int): Double = rawFactor(look, hour, weekday) / (DISTRICT_MEAN[look] ?: 1.0)

        /** Average factor over the day for each look, so the curves shape the day without changing the total much. */
        private val DISTRICT_MEAN: Map<Look, Double> by lazy {
            Look.entries.associateWith { l ->
                var sum = 0.0; var n = 0
                for (d in 0 until 7) for (h in 8..21) { sum += rawFactor(l, h, d); n++ }
                sum / n
            }
        }

        private fun rawFactor(look: Look, hour: Int, weekday: Int): Double {
            val weekend = weekday >= 5
            return when (look) {
                Look.CBD, Look.NY_DOWNTOWN -> if (weekend) (if (hour in 10..18) .55 else .3) else when (hour) {
                    in 7..8 -> 1.2; in 12..13 -> 1.45; in 17..18 -> 1.25; in 9..16 -> 1.0; in 19..22 -> .55; else -> .25
                }
                Look.DENSE_ASIA -> (when (hour) { in 10..16 -> .9; in 17..22 -> 1.4; 23, 0, 1 -> 1.1; else -> .4 }) * (if (weekend) 1.15 else 1.0)
                Look.INDUSTRIAL -> if (weekend) .45 else if (hour in 7..17) 1.1 else .35
                else -> if (weekend) (if (hour in 10..19) 1.25 else if (hour in 20..22) .8 else .35) else when (hour) {
                    in 7..8 -> 1.1; in 9..15 -> .85; in 16..19 -> 1.3; in 20..22 -> .8; else -> .35
                }
            }
        }

        /** The busiest two hours of a district on a weekday, for listings. */
        fun peakHours(look: Look): Int = (6..22).maxByOrNull { rawFactor(look, it, 2) + rawFactor(look, it + 1, 2) } ?: 12

        fun districtNote(look: Look): String = when (look) {
            Look.CBD, Look.NY_DOWNTOWN -> "Office district: rush hours and a big lunch crowd on weekdays, quiet nights and weekends."
            Look.DENSE_ASIA -> "Nightlife district: busiest in the evening and late at night, and on weekends."
            Look.INDUSTRIAL -> "Industrial: workers on weekdays, little else."
            else -> "Residential: busy before and after work, and all day on weekends."
        }
    }

    private val s get() = e.state

    // ------------------------------------------------------------------ shifts and fatigue

    /** How well a tired employee works: 1 when fresh, down to .6 when exhausted. */
    fun fatigueFactor(emp: Employee): Double = if (emp.fatigue <= 40) 1.0 else 1.0 - (emp.fatigue - 40) / 60.0 * .4

    /** Average fatigue of the staff on shift at [b] now, 0..100. */
    fun crewFatigue(b: Business, hour: Int, weekday: Int): Double {
        var sum = 0.0; var n = 0
        for (emp in s.employees) if (emp.businessId == b.id && emp.role == b.type.staffRole && emp.worksAt(hour, weekday)) { sum += emp.fatigue; n++ }
        return if (n == 0) 0.0 else sum / n
    }

    /** Chance that a sale rung up by this crew goes wrong (undercharged). */
    fun billingErrorChance(b: Business, hour: Int, weekday: Int): Double = ((crewFatigue(b, hour, weekday) - 55) / 45).coerceIn(0.0, 1.0) * .1

    /** Chance a tired crew forgets to bring boxes out of the back room this time. */
    fun missedRestockChance(b: Business, hour: Int, weekday: Int): Double = ((crewFatigue(b, hour, weekday) - 50) / 50).coerceIn(0.0, 1.0) * .55

    fun setShift(emp: Employee, shift: Shift) {
        emp.shiftStart = shift.start; emp.shiftEnd = shift.end
        e.bump()
    }

    private fun perk(b: Business?, id: String) = b != null && id in b.perks

    /** Fatigue builds by the hour on shift (faster at night) and wears off off shift. */
    private fun fatigueHour(prevHour: Int, prevWeekday: Int) {
        for (emp in s.employees) {
            val b = s.business(emp.businessId)
            if (b != null && emp.worksAt(prevHour, prevWeekday) && !emp.training(s.minute)) {
                val night = prevHour >= 22 || prevHour < 6
                var gain = if (night) 6.0 else 4.0
                if (perk(b, "coffee")) gain *= .75
                emp.fatigue = (emp.fatigue + gain).coerceAtMost(100.0)
            } else {
                emp.fatigue = (emp.fatigue - 3.0 * (if (perk(b, "health")) 1.3 else 1.0)).coerceAtLeast(0.0)
            }
        }
    }

    // ------------------------------------------------------------------ the clock

    fun minute() {
        if (s.fleetDrops.isNotEmpty()) {
            var i = 0
            while (i < s.fleetDrops.size) {
                val d = s.fleetDrops[i]
                if (d.minute <= s.minute) { s.fleetDrops.removeAt(i); drop(d) } else i++
            }
        }
    }

    fun hourly(hour: Int, weekday: Int) {
        val prevHour = (hour + 23) % 24
        val prevWeekday = if (hour == 0) (weekday + 6) % 7 else weekday
        fatigueHour(prevHour, prevWeekday)
        for (b in s.businesses) autoReorder(b)
        for (r in s.fleet) if (r.enabled && r.hour == hour && (r.days shr weekday) and 1 == 1 && r.lastDay != s.day) runRoute(r)
    }

    fun midnight() {
        val day = s.day
        // perks are paid per employee working at the store
        var perks = 0.0
        for (b in s.businesses) {
            if (b.perks.isEmpty()) continue
            val staff = s.employees.count { it.businessId == b.id }
            perks += b.perks.sumOf { id -> PERKS.firstOrNull { it.id == id }?.cost ?: 0.0 } * staff
        }
        if (perks > 0) e.spend(perks, "Perks", allowDebt = true, silent = true)
        for (b in s.businesses) {
            if (b.billingErrorsToday >= 25) e.message(b.name, "Tired staff rang up ${e.money(b.billingErrorsToday)} of sales wrong yesterday. Shorter shifts, days off or the free coffee perk help.", notify = false)
            if (b.missedRestocksToday >= 3) e.message(b.name, "The crew was too tired to restock the displays ${b.missedRestocksToday} times yesterday.", notify = false)
            b.billingErrorsToday = 0.0; b.missedRestocksToday = 0
        }
        managersDaily()
        poaching()
        leases()
        subsidiaries()
        ensureMarket()
        marketDay()
        bondsDue()
        // drivers who are out of a job lose interest; routes whose van or driver has gone are paused
        for (r in s.fleet) {
            if (s.vehicle(r.vehicleId) == null || (r.driverId >= 0 && s.employees.none { it.id == r.driverId })) {
                if (r.enabled) e.message("Fleet", "Route ${routeName(r)} is paused: ${if (s.vehicle(r.vehicleId) == null) "its van is gone" else "its driver left"}.", notify = false)
                r.enabled = false
                if (s.employees.none { it.id == r.driverId }) r.driverId = -1
            }
        }
        if (day % 7 == 0) s.fleetDrops.removeAll { s.business(it.bizId) == null }
    }

    // ------------------------------------------------------------------ automatic reordering

    /** Sends a delivery as soon as anything falls below its threshold (at most every three hours). */
    private fun autoReorder(b: Business) {
        val c = b.delivery ?: return
        if (!c.enabled || !c.auto) return
        if (!c.byManager && Catalog.COURSE_LOGISTICS !in s.player.courses) return
        if (c.lastAuto >= 0 && s.minute - c.lastAuto < 180) return
        val low = c.minLevels.any { (pid, min) -> BizCalc.onHand(b, pid) < min && BizCalc.intakeRoom(b, pid) > 0 }
        if (!low) return
        c.lastAuto = s.minute
        e.economy.runDelivery(b, auto = true)
    }

    /** Default thresholds: reorder at a third of the target. */
    fun defaultMinLevels(c: DeliveryContract) {
        for ((pid, t) in c.targets) if (pid !in c.minLevels) c.minLevels[pid] = (t / 3).coerceAtLeast(1)
    }

    // ------------------------------------------------------------------ fleet

    fun vans(): List<OwnedVehicle> = s.vehicles.filter { it.def.cargo >= 8 }
    fun drivers(): List<Employee> = s.employees.filter { it.role == StaffRole.DRIVER }
    fun away(v: OwnedVehicle) = v.awayUntil > s.minute

    fun sourceLabel(src: String): String = if (src.startsWith("wh:")) s.business(src.removePrefix("wh:").toIntOrNull() ?: -1)?.name ?: "Warehouse"
        else SOURCES.firstOrNull { it.first == src }?.third ?: src

    fun routeName(r: FleetRoute) = "${sourceLabel(r.source)} → ${r.stops.mapNotNull { s.business(it)?.name }.joinToString(" → ").ifEmpty { "no stops" }}"

    private fun sourceBuilding(src: String): com.hustlecity.game.world.Building? =
        if (src.startsWith("wh:")) s.business(src.removePrefix("wh:").toIntOrNull() ?: -1)?.let { e.city.building(it.buildingId) }
        else SOURCES.firstOrNull { it.first == src }?.let { e.city.placeBuilding(it.second) }

    private fun sourceSells(src: String, pid: String): Boolean {
        val p = Catalog.product(pid) ?: return false
        if (p.service) return false
        return when (src) {
            "metro" -> p.source == Source.METRO
            "distro" -> p.source == Source.DISTRO
            "import" -> p.source == Source.IMPORT && Catalog.COURSE_IMPORT in s.player.courses
            else -> (s.business(src.removePrefix("wh:").toIntOrNull() ?: -1)?.stock?.get(pid) ?: 0) > 0
        }
    }

    fun newRoute(van: OwnedVehicle): FleetRoute {
        val r = FleetRoute(s.newId(), van.id)
        s.fleet += r
        e.bump()
        return r
    }

    fun assignDriver(r: FleetRoute, emp: Employee?) {
        r.driverId = emp?.id ?: -1
        if (emp != null) {
            // the driver works the morning of the run (paid by the hour like everyone else)
            emp.businessId = r.stops.firstOrNull() ?: s.businesses.firstOrNull()?.id ?: -1
            emp.shiftStart = r.hour; emp.shiftEnd = (r.hour + 5) % 24
        }
        e.bump()
    }

    /** Loads the van at its source and sends it round the stops; stock arrives store by store as it drives. */
    fun runRoute(r: FleetRoute, manual: Boolean = false): Boolean {
        val van = s.vehicle(r.vehicleId)
        val driver = s.employees.firstOrNull { it.id == r.driverId && it.role == StaffRole.DRIVER }
        fun fail(why: String): Boolean { r.lastRun = "Day ${s.day}: $why"; r.lastDay = s.day; if (manual) e.toast(why, ToastKind.BAD); e.bump(); return false }
        if (van == null) return fail("no van")
        if (driver == null) return fail("no driver assigned")
        if (driver.training(s.minute)) return fail("${driver.name} is on a training course")
        if (away(van)) return fail("the van is still out")
        if (s.player.driving == van.id) return fail("you're driving the van")
        val src = sourceBuilding(r.source) ?: return fail("can't find ${sourceLabel(r.source)}")
        val stops = r.stops.mapNotNull { s.business(it) }.filter { !it.type.facility || BizCalc.isWarehouse(it) }
        if (stops.isEmpty()) return fail("no stops on the route")
        var boxesLeft = van.def.cargo
        var cost = 0.0
        var boxesTotal = 0
        val loads = ArrayList<Triple<Business, String, Int>>()
        for (b in stops) {
            val targets = b.delivery?.targets
            for ((pid, _) in b.type.products) {
                if (boxesLeft <= 0) break
                if (!sourceSells(r.source, pid)) continue
                val p = Catalog.product(pid) ?: continue
                val target = targets?.get(pid) ?: (BizCalc.capacityFor(b, pid) + BizCalc.storageRoomFor(b, pid))
                var need = min(target - BizCalc.onHand(b, pid), BizCalc.intakeRoom(b, pid))
                if (r.source.startsWith("wh:")) need = min(need, s.business(r.source.removePrefix("wh:").toInt())?.stock?.get(pid) ?: 0)
                if (need <= 0) continue
                val boxes = min(ceil(need / p.unitsPerBox.toDouble()).toInt(), boxesLeft)
                val units = min(need, boxes * p.unitsPerBox)
                if (units <= 0) continue
                loads += Triple(b, pid, units)
                boxesLeft -= boxes; boxesTotal += boxes
            }
        }
        if (loads.isEmpty()) return fail("every stop was already stocked")
        // pay the wholesaler (a full van gets a better price), or take it out of your warehouse
        val wh = if (r.source.startsWith("wh:")) s.business(r.source.removePrefix("wh:").toInt()) else null
        val discount = if (boxesTotal >= 10) .95 else 1.0
        val unit = HashMap<String, Double>()
        for ((_, pid, units) in loads) {
            val p = Catalog.product(pid)!!
            val c = if (wh != null) (wh.unitCost[pid] ?: p.cost) else p.cost * discount
            unit[pid] = c
            if (wh == null) cost += c * units
        }
        if (wh == null && s.player.money < cost) return fail("not enough cash for the stock (${e.money(cost)})")
        if (wh == null) e.spend(cost, "Inventory", silent = true)
        else for ((_, pid, units) in loads) wh.stock[pid] = ((wh.stock[pid] ?: 0) - units).coerceAtLeast(0)
        // drive: source, stop by stop, then back to where the van is parked
        val vx = van.x; val vy = van.y
        var px = src.doorX; var py = src.doorY
        var t = s.minute + 20 + (dist(vx, vy, px, py) * 1.35f / VAN_PACE).toLong()
        var meters = dist(vx, vy, px, py) * 1.35f
        for (b in stops) {
            val bld = e.city.building(b.buildingId) ?: continue
            val leg = dist(px, py, bld.doorX, bld.doorY) * 1.35f
            meters += leg
            t += (leg / VAN_PACE).toLong() + 6
            for ((stop, pid, units) in loads) if (stop === b) s.fleetDrops += FleetDrop(t, b.id, pid, units, unit[pid] ?: 0.0, r.id)
            t += 10
            px = bld.doorX; py = bld.doorY
        }
        val back = dist(px, py, vx, vy) * 1.35f
        meters += back
        t += (back / VAN_PACE).toLong()
        van.awayUntil = t
        val fuel = meters / 1000f * .19
        e.spend(fuel, "Fuel", allowDebt = true, silent = true)
        van.condition = (van.condition - meters / 1_500_000.0).coerceAtLeast(.2)
        val back24 = "%02d:%02d".format(((t % MINUTES_PER_DAY) / 60).toInt(), (t % 60).toInt())
        r.lastRun = "Day ${s.day} %02d:00: $boxesTotal boxes for ${stops.size} stop${if (stops.size > 1) "s" else ""}".format(s.hour) +
            (if (wh == null) ", ${e.money(cost)} of stock" else " from ${wh.name}") + ", ${e.money(fuel.toDouble())} fuel. Back at $back24."
        r.lastDay = s.day
        if (manual) e.toast("${driver.name} is on the way: $boxesTotal boxes", ToastKind.GOOD, icon = "van")
        e.bump()
        return true
    }

    /** A van drops its boxes at a store: on the displays, then the back room; what still doesn't fit goes back. */
    private fun drop(d: FleetDrop) {
        val b = s.business(d.bizId) ?: return
        var left = d.qty
        if (BizCalc.isWarehouse(b)) {
            b.stock[d.pid] = (b.stock[d.pid] ?: 0) + left
            b.unitCost[d.pid] = d.unitCost
            return
        }
        left -= e.economy.stockIn(b, d.pid, left, d.unitCost)
        if (left > 0) left -= e.economy.stockBack(b, d.pid, left, d.unitCost)
        if (left > 0) e.earn(left * d.unitCost, "Refunds")
        e.bump()
    }

    // ------------------------------------------------------------------ store managers

    fun managerOf(b: Business) = s.employees.firstOrNull { it.role == StaffRole.STORE_MANAGER && it.businessId == b.id }

    /** Each store manager keeps the store staffed, the wages fair and the shelves stocked. */
    private fun managersDaily() {
        for (b in s.businesses.toList()) {
            if (b.type.facility) continue
            val mgr = managerOf(b) ?: continue
            val notes = ArrayList<String>()
            // 1. stock: a delivery contract with reorder thresholds, run by the manager
            val c = b.delivery ?: DeliveryContract().also { b.delivery = it }
            if (!c.enabled || !c.auto) {
                c.enabled = true; c.auto = true; c.byManager = true
                for ((pid, _) in b.type.products) {
                    val p = Catalog.product(pid) ?: continue
                    if (p.service) continue
                    if (pid !in c.targets) c.targets[pid] = BizCalc.capacityFor(b, pid) + BizCalc.storageRoomFor(b, pid) / 2
                }
                defaultMinLevels(c)
                notes += "set up automatic reordering"
            }
            // 2. wages: bring unhappy staff up to what they ask
            for (emp in s.employees) {
                if (emp.businessId != b.id || emp === mgr) continue
                if (emp.satisfaction < .45 && emp.wage < emp.demand) {
                    emp.wage = emp.demand
                    emp.satisfaction = (emp.satisfaction + .1).coerceAtMost(1.0)
                    notes += "raised ${emp.name} to ${e.money(emp.wage)}/h"
                }
                // a day off for anyone running on empty
                if (emp.fatigue > 80 && Integer.bitCount(emp.days) > 5) {
                    val day = (s.weekday + 1) % 7
                    if ((emp.days shr day) and 1 == 1) { emp.days = emp.days xor (1 shl day); notes += "gave ${emp.name} tomorrow off" }
                }
            }
            // 3. hiring: cover every open hour with enough people, shift by shift
            val role = b.type.staffRole
            val want = max(1, min(4, BizCalc.countFn(b, com.hustlecity.game.data.ItemFn.CASH_REGISTER)))
            val open = (0 until 24).filter { BizCalc.isWithinHours(b, it) }
            if (open.isNotEmpty()) {
                for (shift in Shift.entries) {
                    val hours = open.filter { h -> if (shift.start < shift.end) h in shift.start until shift.end else h >= shift.start || h < shift.end }
                    if (hours.size < 2) continue
                    val probe = hours[hours.size / 2]
                    val have = s.employees.count { it.businessId == b.id && it.role == role && it.worksAt(probe, s.weekday) }
                    if (have >= want) continue
                    val hire = makeCandidate(role, .35 + .35 * mgr.skill)
                    hire.businessId = b.id; hire.shiftStart = shift.start; hire.shiftEnd = shift.end; hire.days = 0b1111111; hire.hiredDay = s.day
                    s.employees += hire
                    notes += "hired ${hire.name} (${role.label}, ${shift.label.lowercase()} shift, ${e.money(hire.wage)}/h)"
                }
            }
            // a cleaner once the floor needs one
            if (b.cleanliness < 70 && s.employees.none { it.businessId == b.id && it.role == StaffRole.CLEANER }) {
                val hire = makeCandidate(StaffRole.CLEANER, .4 + .3 * mgr.skill)
                hire.businessId = b.id; hire.shiftStart = b.closeHour; hire.shiftEnd = (b.closeHour + 3) % 24; hire.days = 0b1111111; hire.hiredDay = s.day
                s.employees += hire
                notes += "hired ${hire.name} as a cleaner after closing"
            }
            if (notes.isNotEmpty()) e.message("${mgr.name} (${b.name})", "Store report: " + notes.joinToString("; ") + ".", notify = false)
        }
    }

    private fun makeCandidate(role: StaffRole, skillBase: Double): Employee {
        val skill = (skillBase + e.rnd.nextDouble() * .2).coerceIn(.1, .98)
        val wage = ((role.baseWage * (.85 + skill * .6) * s.player.rules.wages) * 2).roundToInt() / 2.0
        val name = "${Catalog.firstNames.random(e.rnd)} ${Catalog.lastNames.random(e.rnd)}"
        return Employee(s.newId(), name, role, skill, wage, wage, 20 + e.rnd.nextInt(38))
    }

    // ------------------------------------------------------------------ perks and training

    fun togglePerk(b: Business, id: String) {
        if (!b.perks.remove(id)) b.perks += id
        e.bump()
    }

    fun perkCost(b: Business): Double = b.perks.sumOf { id -> PERKS.firstOrNull { it.id == id }?.cost ?: 0.0 } * s.employees.count { it.businessId == b.id }

    /** A day-long course: better skill, but they're away from the floor until it's done. */
    fun trainCourse(emp: Employee) {
        if (emp.skill >= .99) { e.toast("${emp.name} is already an expert"); return }
        if (emp.training(s.minute)) { e.toast("${emp.name} is already on a course"); return }
        val cost = (600 + 1400 * emp.skill).roundToInt().toDouble()
        if (!e.spend(cost, "Training")) return
        emp.trainingUntil = s.minute + 8 * 60
        emp.skill = (emp.skill + .2).coerceAtMost(1.0)
        e.toast("${emp.name} is on an 8-hour course (${e.money(cost)}): skill ${(emp.skill * 100).roundToInt()}% when back", ToastKind.GOOD)
    }

    fun courseCost(emp: Employee) = (600 + 1400 * emp.skill).roundToInt().toDouble()

    // ------------------------------------------------------------------ poaching

    /** Rivals try to lure your best people away; your offers to theirs go the other way. */
    private fun poaching() {
        // offers that ran out: they take it unless they're happy where they are
        for (emp in s.employees.toList()) {
            if (emp.offerFrom < 0 || s.day <= emp.offerDay) continue
            val rival = s.rivals.firstOrNull { it.id == emp.offerFrom }
            if (rival != null && e.rnd.nextDouble() < 1.0 - emp.satisfaction * .8) {
                s.employees.remove(emp)
                s.fleet.filter { it.driverId == emp.id }.forEach { it.driverId = -1 }
                e.message(emp.name, "I've accepted ${rival.name}'s offer. Today was my last day.")
            } else {
                e.message(emp.name, "I turned down ${rival?.name ?: "the"} offer. I'm staying.", notify = false)
            }
            emp.offerFrom = -1; emp.offerWage = 0.0
        }
        for (r in s.rivals) {
            if (r.aggression < .45 || e.rnd.nextDouble() > .1 * r.aggression * s.player.rules.rivals) continue
            val target = s.employees.filter { it.offerFrom < 0 && it.skill >= .65 && it.businessId >= 0 }.maxByOrNull { it.skill * (1.2 - it.satisfaction) } ?: continue
            target.offerFrom = r.id
            target.offerWage = (max(target.wage * 1.25, target.demand * 1.2) * 2).roundToInt() / 2.0
            target.offerDay = s.day + 2
            e.message(target.name, "${r.name} offered me ${e.money(target.offerWage)}/h to join them. I need an answer by the end of day ${target.offerDay} — can you match it? (Staff app)")
        }
    }

    fun counterOffer(emp: Employee) {
        if (emp.offerFrom < 0) return
        emp.wage = emp.offerWage
        emp.satisfaction = (emp.satisfaction + .2).coerceAtMost(1.0)
        emp.offerFrom = -1; emp.offerWage = 0.0
        e.toast("${emp.name} is staying for ${e.money(emp.wage)}/h", ToastKind.GOOD)
    }

    /** Hire away one of a rival's store managers: an experienced candidate, and a weaker rival store. */
    fun headhunt(r: Rival) {
        if (Catalog.COURSE_HR !in s.player.courses) { e.toast("Take the Basic HR course first"); return }
        if (!e.spend(HEADHUNT_FEE, "Recruitment")) return
        val c = makeCandidate(StaffRole.STORE_MANAGER, .72)
        c.expiresDay = s.day + 2
        s.candidates += c
        s.rivalBusinesses.filter { it.rivalId == r.id }.maxByOrNull { it.profit }?.let { it.reputation = (it.reputation - .06).coerceAtLeast(.2) }
        e.message("Apex Recruitment", "We found ${c.name}, a store manager at ${r.name} (skill ${(c.skill * 100).roundToInt()}%), willing to move for ${e.money(c.wage)}/h. Hire them in the Staff app.")
        e.toast("${c.name} from ${r.name} is waiting in the Staff app", ToastKind.GOOD)
    }

    // ------------------------------------------------------------------ mergers and acquisitions

    /** What a rival wants for one of its stores: the fit-out, a few months of profit and a premium. */
    fun askingPrice(rb: RivalBusiness): Double {
        val owner = s.rivals.firstOrNull { it.id == rb.rivalId }
        val t = Catalog.type(rb.typeId)
        val bld = e.city.building(rb.buildingId)
        val base = RivalAI.storeValue(t, rb.level) + max(0.0, rb.profit) * 90 + (if (rb.owned) bld?.value ?: 0.0 else 0.0)
        return (base * (1.15 + (owner?.aggression ?: .5) * .35) / 100).roundToInt() * 100.0
    }

    fun buyRivalStore(rb: RivalBusiness): Boolean {
        val owner = s.rivals.firstOrNull { it.id == rb.rivalId } ?: return false
        val price = askingPrice(rb)
        if (!e.spend(price, "Acquisitions")) return false
        owner.cash += price
        rb.rivalId = SUBSIDIARY
        rb.targetBiz = -1; rb.priceFactor = 1.0; rb.losingDays = 0
        e.economy.rivals.touch()
        e.message("Mergers & Acquisitions", "You bought ${rb.name} from ${owner.name} for ${e.money(price)}. Its manager keeps it running; its profit is paid to you every night.")
        e.toast("${rb.name} is yours", ToastKind.GOOD)
        return true
    }

    fun sellSubsidiary(rb: RivalBusiness) {
        if (rb.rivalId != SUBSIDIARY) return
        val bld = e.city.building(rb.buildingId)
        val price = RivalAI.storeValue(Catalog.type(rb.typeId), rb.level) * .8 + (if (rb.owned) (bld?.value ?: 0.0) * .9 else 0.0)
        s.rivalBusinesses.remove(rb)
        e.economy.rivals.touch()
        e.earn(price, "Sold assets")
        e.toast("Sold ${rb.name} for ${e.money(price)}", ToastKind.GOOD)
    }

    fun subsidiaryStores() = s.rivalBusinesses.filter { it.rivalId == SUBSIDIARY }

    private fun subsidiaries() {
        val profit = e.economy.rivals.subsidiariesDaily()
        if (profit > 0) e.earn(profit, "Subsidiaries") else if (profit < 0) e.spend(-profit, "Subsidiaries", allowDebt = true, silent = true)
    }

    // ------------------------------------------------------------------ leases

    /** Leases come up for renewal every [LEASE_DAYS] days; the landlord raises the rent on busy, popular spots. */
    private fun leases() {
        val day = s.day
        for (r in s.rentals) {
            if (r.owned) continue
            if (r.leaseEnd <= 0) {
                val n = ((day - r.startDay) / LEASE_DAYS) + 1
                r.leaseEnd = r.startDay + n * LEASE_DAYS
            }
            val b = e.city.building(r.buildingId) ?: continue
            if (r.leaseEnd - day == 5) e.message(b.landlord, "Your lease at ${b.address} renews in 5 days. Expect the rent to go up by about ${(raiseFor(r) * 100).roundToInt()}%.", notify = false)
            if (day < r.leaseEnd) continue
            val pct = raiseFor(r) * (.85 + e.rnd.nextDouble() * .3)
            val old = r.rentPerMonth
            r.rentPerMonth = ((old * (1 + pct)) / 10).roundToInt() * 10.0
            r.leaseEnd += LEASE_DAYS
            r.renewals++
            e.message(b.landlord, "Your lease at ${b.address} has renewed for another $LEASE_DAYS days. The rent goes from ${e.money(old)} to ${e.money(r.rentPerMonth)} a month (+${(pct * 100).roundToInt()}%).")
        }
    }

    /** Expected rent rise at the next renewal: busier streets, tight markets and thriving tenants pay more. */
    fun raiseFor(r: com.hustlecity.game.model.Rental): Double {
        val b = e.city.building(r.buildingId) ?: return .03
        var pct = .025 + .06 * b.traffic + .03 * b.hood.wealth
        val biz = s.businessAt(r.buildingId)
        if (biz != null) {
            val week = biz.history.takeLast(7)
            if (week.isNotEmpty() && week.sumOf { it.revenue } / week.size > r.rentPerMonth / 30 * 6) pct += .03
        }
        pct += .01 * r.renewals.coerceAtMost(3)
        return pct.coerceIn(.02, .16) * s.player.rules.rent
    }

    // ------------------------------------------------------------------ stock exchange and bonds

    /** Lists the city's firms and every rival on the exchange (once), and any rival that appeared since. */
    fun ensureMarket() {
        if (s.market.none { !it.id.startsWith("R") }) {
            val firms = listOf(
                Ticker("MWS", "Metro Wholesale", 64.0, .0005, .016), Ticker("CDX", "CityDistro", 41.0, .0004, .019),
                Ticker("GIC", "Global Imports Co.", 88.0, .0003, .026), Ticker("APX", "Apex Recruitment", 23.0, .0006, .03),
                Ticker("HMT", "Hustle Motors", 132.0, .0004, .024), Ticker("CPW", "City Power & Light", 54.0, .0003, .009),
                Ticker("HCI", "Hustle City Index Fund", 100.0, .00045, .008),
            )
            for (t in firms) { t.history += t.price; s.market += t }
        }
        for (r in s.rivals) {
            val id = "R${r.id}"
            if (s.market.none { it.id == id }) {
                val t = Ticker(id, r.name, rivalFair(r), .0, .025, RIVAL_SHARES)
                t.history += t.price
                s.market += t
            }
        }
    }

    private fun rivalFair(r: Rival) = (r.netWorth / RIVAL_SHARES).coerceAtLeast(.5)

    private fun gauss(): Double {
        val u = e.rnd.nextDouble().coerceAtLeast(1e-9); val v = e.rnd.nextDouble()
        return sqrt(-2 * ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    /** A day of trading: firms drift on their own news, rivals follow their net worth. */
    private fun marketDay() {
        for (t in s.market) {
            if (t.id.startsWith("R")) {
                val r = s.rivals.firstOrNull { "R${it.id}" == t.id } ?: continue
                t.price = (t.price * .55 + rivalFair(r) * .45) * exp(.03 * gauss())
            } else {
                t.price *= exp(t.drift - t.vol * t.vol / 2 + t.vol * gauss())
            }
            t.price = (t.price * 100).roundToInt() / 100.0
            t.price = t.price.coerceAtLeast(.5)
            t.history += t.price
            if (t.history.size > 60) t.history.removeAt(0)
        }
        // a rival taken over or gone leaves the exchange
        s.market.removeAll { t -> t.id.startsWith("R") && s.rivals.none { "R${it.id}" == t.id } }
    }

    fun ticker(id: String) = s.market.firstOrNull { it.id == id }

    fun buyShares(t: Ticker, qty: Int): Boolean {
        val have = s.holdings[t.id] ?: 0
        val n = min(qty, t.shares - have)
        if (n <= 0) { e.toast("No more ${t.name} shares for sale"); return false }
        val cost = t.price * n * (1 + BROKERAGE)
        if (!e.spend(cost, "Securities")) return false
        s.holdings[t.id] = have + n
        s.costBasis[t.id] = (s.costBasis[t.id] ?: 0.0) + cost
        e.toast("Bought $n ${t.name} shares for ${e.money(cost)}", ToastKind.GOOD)
        return true
    }

    fun sellShares(t: Ticker, qty: Int): Boolean {
        val have = s.holdings[t.id] ?: 0
        val n = min(qty, have)
        if (n <= 0) return false
        val proceeds = t.price * n * (1 - BROKERAGE)
        val basis = (s.costBasis[t.id] ?: 0.0) * n / have
        s.costBasis[t.id] = (s.costBasis[t.id] ?: 0.0) - basis
        if (n == have) { s.holdings.remove(t.id); s.costBasis.remove(t.id) } else s.holdings[t.id] = have - n
        e.earn(proceeds, "Securities")
        e.toast("Sold $n ${t.name} shares for ${e.money(proceeds)}", ToastKind.GOOD)
        return true
    }

    fun portfolioValue() = s.holdings.entries.sumOf { (id, n) -> (ticker(id)?.price ?: 0.0) * n }

    /** Share of a rival you own, 0..1. */
    fun stake(r: Rival) = (s.holdings["R${r.id}"] ?: 0) / RIVAL_SHARES.toDouble()

    /**
     * With more than half its shares you can take a rival over: its cash joins yours and its stores become your
     * subsidiaries; the company leaves the race.
     */
    fun takeover(r: Rival): Boolean {
        if (stake(r) <= .5) return false
        val stores = s.rivalBusinesses.filter { it.rivalId == r.id }
        for (rb in stores) { rb.rivalId = SUBSIDIARY; rb.targetBiz = -1; rb.priceFactor = 1.0 }
        e.earn(r.cash.coerceAtLeast(0.0), "Acquired cash")
        s.holdings.remove("R${r.id}"); s.costBasis.remove("R${r.id}")
        s.market.removeAll { it.id == "R${r.id}" }
        for (emp in s.employees) if (emp.offerFrom == r.id) { emp.offerFrom = -1; emp.offerWage = 0.0 }
        s.rivals.remove(r)
        e.economy.rivals.touch()
        e.message("Mergers & Acquisitions", "Hostile takeover complete: ${r.name} is yours. Its ${stores.size} stores now trade as your subsidiaries.")
        e.toast("You took over ${r.name}!", ToastKind.GOOD)
        return true
    }

    fun buyBond(amount: Double, days: Int): Boolean {
        val rate = BOND_TERMS.firstOrNull { it.first == days }?.second ?: return false
        if (!e.spend(amount, "Bonds")) return false
        s.bonds += Bond(s.newId(), amount, rate, s.day, s.day + days)
        e.toast("Bought a $days-day city bond: ${e.money(amount)} at ${"%.1f".format(rate * 100)}%", ToastKind.GOOD)
        return true
    }

    /** What a bond is worth today: principal plus the interest earned so far. */
    fun bondValue(b: Bond): Double = b.principal * (1 + b.rate * (s.day - b.bought).coerceIn(0, b.matures - b.bought) / 365.0)

    private fun bondsDue() {
        val due = s.bonds.filter { s.day >= it.matures }
        for (b in due) {
            val interest = b.principal * b.rate * (b.matures - b.bought) / 365.0
            e.earn(b.principal, "Bonds")
            e.earn(interest, "Interest income")
            e.message("City Treasury", "Your ${b.matures - b.bought}-day bond matured: ${e.money(b.principal)} back plus ${e.money(interest)} interest.", notify = false)
        }
        s.bonds.removeAll(due.toSet())
    }

    fun bondsValue() = s.bonds.sumOf { bondValue(it) }
}

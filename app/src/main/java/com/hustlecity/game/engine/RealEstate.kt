package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.LEASE_DAYS
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.Rival
import com.hustlecity.game.model.RivalBusiness
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------------- selling a business

/** A rival's offer for one of your businesses: who's buying, and what they pay for the business (and the building). */
data class SaleOffer(val buyer: Rival, val price: Double, val buildingPrice: Double)

/** Average daily profit over the last week (0 for a new store). */
fun Business.weekProfit(): Double = history.takeLast(7).map { it.profit }.average().takeIf { !it.isNaN() } ?: 0.0

/**
 * What a buyer pays for a business as a going concern: the fit-out and stock at a discount, and two months of its
 * recent profit for the customers and name that come with it (more for a well-liked store).
 */
fun GameEngine.businessValue(b: Business): Double {
    val goodwill = max(0.0, b.weekProfit()) * 60 * (.8 + .4 * b.reputation)
    return ((BizCalc.furnitureValue(b) * .6 + BizCalc.stockValue(b) * .8 + goodwill) / 50).roundToInt() * 50.0
}

/** The best offer for a business, from the richest company that can pay; null when nobody can afford it. */
fun GameEngine.saleOffer(b: Business, withBuilding: Boolean): SaleOffer? {
    if (b.type.facility) return null
    val price = businessValue(b)
    val rental = state.rentalFor(b.buildingId)
    val bldPrice = if (withBuilding && rental?.owned == true) (city.building(b.buildingId)?.value ?: 0.0) * .95 else 0.0
    val buyer = state.rivals.filter { it.cash - price - bldPrice > 40_000 }.maxByOrNull { it.cash + (if (b.typeId in it.focus) 5e5 else 0.0) } ?: return null
    return SaleOffer(buyer, price, bldPrice)
}

/** Size of the fit-out a rival company would call it (1..3), from what's been spent on the furniture. */
private fun levelOf(b: Business): Int {
    val setup = RivalAI.setupCost(b.type)
    val spent = BizCalc.furnitureValue(b)
    return when { spent >= setup * 1.6 -> 3; spent >= setup * .8 -> 2; else -> 1 }
}

/**
 * Sells a business to a rival company. The store keeps trading under its name with its staff, stock and furniture.
 * A leased unit's lease goes to the buyer; a building you own is sold with it ([withBuilding]) or kept, and then the
 * buyer rents it from you.
 */
fun GameEngine.sellBusiness(b: Business, withBuilding: Boolean): Boolean {
    val offer = saleOffer(b, withBuilding) ?: run { toast("No company can afford ${b.name} right now", ToastKind.BAD); return false }
    val bld = city.building(b.buildingId) ?: return false
    val rental = state.rentalFor(b.buildingId)
    if (state.player.inBuilding == b.buildingId) exitBuilding()
    if (state.player.inBuilding == b.buildingId) return false
    // today's takings and anything still on its way are yours
    if (b.revenueToday > 0) { earn(b.revenueToday, "Sales"); state.player.totalEarned += b.revenueToday }
    val inTransit = b.pendingDelivery.sumOf { if (it.isProduct) (Catalog.product(it.id)?.cost ?: 0.0) * it.qty else Catalog.item(it.id)?.price ?: 0.0 }
    if (inTransit > 0) earn(inTransit, "Refunds")
    val buyer = offer.buyer
    val total = offer.price + offer.buildingPrice
    buyer.cash -= total
    earn(total, "Sold assets")
    val ownedByYou = rental?.owned == true
    val rb = RivalBusiness(
        state.newId(), buyer.id, b.buildingId, b.typeId, b.name, level = levelOf(b), reputation = b.reputation.coerceIn(.3, .95),
        priceFactor = economy.rivals.basePrice(buyer), opened = state.day, owned = ownedByYou && withBuilding,
    )
    state.rivalBusinesses += rb
    when {
        rental == null -> {}
        !ownedByYou || withBuilding -> state.rentals.remove(rental)
        else -> {
            // you keep the building and the buyer becomes your tenant at the going rent
            val rent = rentOf(bld)
            rental.listed = true; rental.askPct = 1.0
            signTenant(rental, buyer.name, rent)
            rb.tenantRent = rent
        }
    }
    // the staff stay with the store; routes and the scheduler forget it
    state.employees.removeAll { it.businessId == b.id }
    state.fleet.forEach { it.stops.remove(b.id) }
    state.fleetDrops.removeAll { it.bizId == b.id }
    state.businesses.remove(b)
    if (state.player.workingAt == b.id) state.player.workingAt = -1
    if (state.player.waypoint == b.buildingId) state.player.waypoint = -1
    economy.rivals.touch()
    message("Mergers & Acquisitions", "${buyer.name} bought ${b.name} for ${money(offer.price)}" +
        (if (offer.buildingPrice > 0) " and the building for ${money(offer.buildingPrice)}" else "") + "." +
        (if (ownedByYou && !withBuilding) " You keep ${bld.address}: they rent it from you for ${money(rental!!.tenantRent)} a month." else ""))
    toast("Sold ${b.name} for ${money(total)}", ToastKind.GOOD)
    bump()
    return true
}

// ---------------------------------------------------------------------- selling buildings

/** What a buyer pays for a building you own. */
fun GameEngine.buildingSalePrice(b: Building) = b.value * .9

/**
 * Sells a building you own but keep using it: your business (or home) stays, and you rent it from the new owner at
 * the going rent.
 */
fun GameEngine.sellAndLeaseBack(buildingId: Int): Boolean {
    val r = state.rentalFor(buildingId) ?: return false
    val b = city.building(buildingId) ?: return false
    if (!r.owned) return false
    if (r.tenant.isNotEmpty()) dropTenant(r, quiet = true)
    val price = buildingSalePrice(b)
    earn(price, "Sold assets")
    state.rentals.remove(r)
    val rent = rentOf(b)
    state.rentals += Rental(buildingId, rent, state.day, leaseEnd = state.day + LEASE_DAYS)
    message(b.landlord, "Thanks for the sale. You stay on at ${b.address} for ${money(rent)} a month, charged daily.", notify = false)
    toast("Sold ${b.address} for ${money(price)}; you now lease it back", ToastKind.GOOD)
    bump()
    return true
}

// ---------------------------------------------------------------------- landlord

/** Monthly rent a tenant pays at [pct] of the market rent. */
fun GameEngine.tenantRentAt(b: Building, pct: Double) = (rentOf(b) * pct / 10).roundToInt() * 10.0

/** Whether you can let a building out: you own it, live elsewhere and run nothing there. */
fun GameEngine.canLet(r: Rental) = r.owned && r.buildingId != state.player.home && state.businessAt(r.buildingId) == null

/** Offers an owned building to tenants at [pct] of the market rent (an empty building finds one in a few days). */
fun GameEngine.listForTenants(buildingId: Int, pct: Double) {
    val r = state.rentalFor(buildingId) ?: return
    if (!canLet(r)) { toast("You can only let out buildings you own, don't live in and don't run a business in"); return }
    r.listed = true
    r.askPct = pct.coerceIn(.6, 1.6)
    toast(if (r.tenant.isEmpty()) "Looking for a tenant at ${money(tenantRentAt(city.building(buildingId)!!, r.askPct))}/mo" else "New rent applies when the lease renews", ToastKind.GOOD)
    bump()
}

/** Stops looking for tenants (a sitting tenant stays until you end the tenancy). */
fun GameEngine.unlist(buildingId: Int) {
    val r = state.rentalFor(buildingId) ?: return
    r.listed = false
    bump()
}

/** Ends a tenancy early: the tenant moves out at once and gets a month's rent back as compensation. */
fun GameEngine.evictTenant(buildingId: Int) {
    val r = state.rentalFor(buildingId) ?: return
    if (r.tenant.isEmpty()) return
    val comp = r.tenantRent
    spend(comp, "Compensation", allowDebt = true, silent = true)
    val name = r.tenant
    dropTenant(r, quiet = true)
    r.listed = false
    toast("$name moved out (compensation ${money(comp)})")
    bump()
}

private fun GameEngine.signTenant(r: Rental, name: String, rent: Double) {
    r.tenant = name
    r.tenantRent = rent
    r.tenantSince = state.day
    r.tenantLeaseEnd = state.day + LEASE_DAYS
}

/** Takes the tenant out of a building, closing their store if they ran one there. */
private fun GameEngine.dropTenant(r: Rental, quiet: Boolean) {
    state.rivalBusinesses.firstOrNull { it.buildingId == r.buildingId && it.tenantRent > 0 }?.let { rb ->
        state.rivalBusinesses.remove(rb)
        economy.rivals.touch()
    }
    if (!quiet) city.building(r.buildingId)?.let { message("Tenants", "${r.tenant} moved out of ${it.address}.") }
    r.tenant = ""
    r.tenantRent = 0.0
    r.tenantLeaseEnd = 0
}

/**
 * The nightly round of your rented-out buildings: tenants pay their rent, leases renew (or, when the rent is steep,
 * the tenant leaves), and listed buildings find tenants. Shop and office tenants are rival companies opening a store.
 */
fun GameEngine.landlordDaily() {
    var income = 0.0
    for (r in state.rentals.toList()) {
        if (!r.owned) continue
        val b = city.building(r.buildingId) ?: continue
        if (r.tenant.isNotEmpty()) {
            val store = state.rivalBusinesses.firstOrNull { it.buildingId == r.buildingId }
            if (b.kind != BKind.RESIDENTIAL && (store == null || store.tenantRent <= 0)) {
                // the company closed its store (or sold it on)
                message("Tenants", "${r.tenant} gave up the lease at ${b.address}." + if (r.listed) " It's back on the market." else "")
                r.tenant = ""; r.tenantRent = 0.0; r.tenantLeaseEnd = 0
                continue
            }
            income += r.tenantRent / 30.0
            if (state.day >= r.tenantLeaseEnd) {
                val newRent = tenantRentAt(b, r.askPct)
                // most tenants stay on at a fair rent; a steep one, or a big rise, sends them looking elsewhere
                val rise = (newRent / max(1.0, r.tenantRent) - 1.0).coerceAtLeast(0.0)
                val stay = (1.3 - .55 * r.askPct - rise).coerceIn(.15, .95)
                if (!r.listed || rnd.nextDouble() > stay) {
                    message("Tenants", "${r.tenant}'s lease at ${b.address} ended and they moved out." + if (r.listed) " The place is back on the market." else "")
                    dropTenant(r, quiet = true)
                } else {
                    val old = r.tenantRent
                    r.tenantRent = newRent
                    store?.tenantRent = newRent
                    r.tenantLeaseEnd += LEASE_DAYS
                    if (newRent != old) message("Tenants", "${r.tenant} renewed at ${b.address} for ${money(newRent)} a month (was ${money(old)}).", notify = false)
                }
            }
            continue
        }
        if (!r.listed || !canLet(r)) continue
        // steep rents take longer to let; busy, wealthy streets let faster
        val appeal = (1.6 - r.askPct).coerceIn(0.0, 1.0) * (.75 + .35 * b.traffic + .25 * b.hood.wealth)
        if (rnd.nextDouble() > .42 * appeal) continue
        val rent = tenantRentAt(b, r.askPct)
        if (b.kind == BKind.RESIDENTIAL) {
            val name = if (rnd.nextDouble() < .45) "The ${Catalog.lastNames.random(rnd)} family" else "${Catalog.firstNames.random(rnd)} ${Catalog.lastNames.random(rnd)}"
            signTenant(r, name, rent)
            message("Tenants", "$name signed a $LEASE_DAYS-day lease at ${b.address} for ${money(rent)} a month.")
        } else {
            val pick = tenantStore(b, rent) ?: continue
            val (rival, type) = pick
            rival.cash -= RivalAI.setupCost(type)
            state.rivalBusinesses += RivalBusiness(
                state.newId(), rival.id, b.id, type.id, Catalog.randomBusinessName(type.id, rnd), level = 1, reputation = .5,
                priceFactor = economy.rivals.basePrice(rival), opened = state.day, tenantRent = rent,
            )
            economy.rivals.touch()
            signTenant(r, rival.name, rent)
            message("Tenants", "${rival.name} is opening a ${type.name} at ${b.address}, paying you ${money(rent)} a month.")
        }
    }
    if (income > 0) earn(income, "Rent income")
}

/** The company and store type that would do best in a building at this rent, if any would make money. */
private fun GameEngine.tenantStore(b: Building, rent: Double): Pair<Rival, com.hustlecity.game.data.BusinessTypeDef>? {
    val pool = if (b.kind == BKind.OFFICE) Catalog.officeTypes else if (b.kind == BKind.RETAIL) Catalog.businessTypes else return null
    var best: Pair<Rival, com.hustlecity.game.data.BusinessTypeDef>? = null
    var bestScore = 0.0
    for (rival in state.rivals) {
        val types = (rival.focus.map { Catalog.type(it) }.filter { it in pool } + pool.shuffled(rnd).take(3)).distinct()
        for (t in types) {
            if (rival.cash - RivalAI.setupCost(t) < 30_000) continue
            val comp = economy.rivals.competitionAround(t.id, b.neighborhood, null)
            val day = economy.rivals.project(t, b, 1, .5, economy.rivals.basePrice(rival), 0.0, comp, false, rent)
            val score = day.profit * (if (t.id in rival.focus) 1.25 else 1.0)
            if (day.profit > 60 && score > bestScore) { best = rival to t; bestScore = score }
        }
    }
    return best
}

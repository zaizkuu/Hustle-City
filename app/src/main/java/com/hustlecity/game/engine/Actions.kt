package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.data.Source
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.Campaign
import com.hustlecity.game.model.CargoItem
import com.hustlecity.game.model.DeliveryContract
import com.hustlecity.game.model.Employee
import com.hustlecity.game.model.Loan
import com.hustlecity.game.model.OwnedVehicle
import com.hustlecity.game.model.PendingRecruit
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.SkipKind
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.City
import com.hustlecity.game.world.dist
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------- real estate

fun GameEngine.rentBuilding(buildingId: Int): Boolean {
    val b = city.building(buildingId) ?: return false
    if (!isVacant(buildingId)) { toast("This building is not available"); return false }
    val rent = b.baseRent
    val deposit = rent / 2
    if (!spend(deposit, "Rent")) return false
    state.rentals += Rental(buildingId, rent, state.day)
    if (b.kind == BKind.RESIDENTIAL) {
        val oldHome = state.player.home
        // furniture moves with you; anything that doesn't fit goes to storage
        moveHomeTo(b)
        toast("You rented ${b.address}. It's your new home!", ToastKind.GOOD)
        if (oldHome >= 0 && oldHome != buildingId) {
            message("Landlord", "Your previous lease at ${city.building(oldHome)?.address} is still active. Cancel it in the Realty app if you don't need it.")
        }
    } else {
        toast("You rented ${b.address}! Go there and start your business.", ToastKind.GOOD)
        state.player.waypoint = buildingId
    }
    message(b.landlord, "Welcome! Rent for ${b.address} is ${money(rent)}/month, charged daily. Deposit received: ${money(deposit)}.", notify = false)
    bump()
    return true
}

fun GameEngine.buyBuilding(buildingId: Int): Boolean {
    val b = city.building(buildingId) ?: return false
    if (Catalog.COURSE_REAL_ESTATE !in state.player.courses) { toast("Take the Real Estate Investing course first"); return false }
    val existing = state.rentalFor(buildingId)
    if (existing?.owned == true) return false
    if (existing == null && !isVacant(buildingId)) { toast("Not for sale"); return false }
    if (!spend(b.value, "Real estate")) return false
    if (existing != null) state.rentals.remove(existing)
    state.rentals += Rental(buildingId, 0.0, state.day, owned = true)
    if (b.kind == BKind.RESIDENTIAL && state.player.home < 0) state.player.home = buildingId
    toast("You now own ${b.address}!", ToastKind.GOOD)
    bump()
    return true
}

fun GameEngine.cancelLease(buildingId: Int): Boolean {
    val r = state.rentalFor(buildingId) ?: return false
    if (state.businessAt(buildingId) != null) { toast("Close the business first (BizHub → Settings)"); return false }
    if (buildingId == state.player.home) {
        val other = state.rentals.firstOrNull { it.buildingId != buildingId && city.building(it.buildingId)?.kind == BKind.RESIDENTIAL }
        if (other == null) { toast("You can't cancel the lease on your only home"); return false }
        city.building(other.buildingId)?.let { moveHomeTo(it) }
    }
    if (r.owned) earn(city.building(buildingId)?.value?.times(.9) ?: 0.0, "Sold assets")
    state.rentals.remove(r)
    if (state.player.waypoint == buildingId) state.player.waypoint = -1
    toast(if (r.owned) "Building sold" else "Lease cancelled")
    bump()
    return true
}

// ---------------------------------------------------------------------- businesses

fun GameEngine.startBusiness(buildingId: Int, typeId: String, name: String): Business? {
    val b = city.building(buildingId) ?: return null
    if (state.rentalFor(buildingId) == null || state.businessAt(buildingId) != null) return null
    val type = Catalog.type(typeId)
    if (type.sources.contains(Source.IMPORT) && Catalog.COURSE_IMPORT !in state.player.courses) {
        toast("${type.name} needs the Import & Trade course"); return null
    }
    val biz = Business(state.newId(), name.ifBlank { Catalog.randomBusinessName(typeId, rnd) }, typeId, buildingId, state.day)
    for ((pid, _) in type.products) biz.prices[pid] = Catalog.product(pid)?.price ?: 1.0
    biz.floorColor = rnd.nextInt(FLOOR_COLORS.size)
    biz.wallColor = rnd.nextInt(WALL_COLORS.size)
    if (type.id == "coffee" || type.id == "fastfood") { biz.openHour = 7; biz.closeHour = 21 }
    if (type.id == "gym") { biz.openHour = 6; biz.closeHour = 22 }
    state.businesses += biz
    ui.bizHubSel = biz.id
    if (state.player.inBuilding == buildingId) interior = InteriorScene(this, b, InteriorMode.BUSINESS)
    message("Uncle Sal", "${biz.name} — great name! Now buy furniture: check the requirements in BizHub. Essentials Appliances and Prime Kitchen Supply sell what you need.")
    toast("${biz.name} founded!", ToastKind.GOOD)
    bump()
    return biz
}

fun GameEngine.closeBusiness(biz: Business) {
    // today's takings and anything still on a delivery truck are paid out, not lost
    if (biz.revenueToday > 0) {
        earn(biz.revenueToday, "Sales")
        state.player.totalEarned += biz.revenueToday
    }
    val inTransit = biz.pendingDelivery.sumOf { if (it.isProduct) (Catalog.product(it.id)?.cost ?: 0.0) * it.qty else Catalog.item(it.id)?.price ?: 0.0 }
    biz.pendingDelivery.clear()
    val refund = BizCalc.furnitureValue(biz) * .5 + BizCalc.stockValue(biz) * .3 + inTransit
    earn(refund, "Sold assets")
    state.employees.filter { it.businessId == biz.id }.forEach { it.businessId = -1 }
    state.businesses.remove(biz)
    if (state.player.workingAt == biz.id) state.player.workingAt = -1
    val inside = state.player.inBuilding == biz.buildingId
    if (inside) city.building(biz.buildingId)?.let { interior = InteriorScene(this, it, InteriorMode.EMPTY) }
    toast("${biz.name} closed. Assets sold for ${money(refund)}")
    bump()
}

fun GameEngine.setPrice(biz: Business, pid: String, price: Double) {
    biz.prices[pid] = (price * 100).roundToInt() / 100.0
    bump()
}

fun GameEngine.toggleWork(biz: Business) {
    val p = state.player
    if (p.workingAt == biz.id) {
        p.workingAt = -1
        toast("You stopped working")
    } else {
        if (p.inBuilding != biz.buildingId) { toast("Go inside ${biz.name} to work there"); return }
        p.workingAt = biz.id
        state.objectivesDone += "work"
        if (biz.tempClosed) toast("Your store is still Temporarily Closed — open it to serve customers", ToastKind.BAD)
        else toast("You're working the floor at ${biz.name}", ToastKind.GOOD)
    }
    bump()
}

fun GameEngine.workUntilClosing(biz: Business) {
    if (state.player.inBuilding != biz.buildingId) return
    if (biz.tempClosed || !BizCalc.isWithinHours(biz, state.hour)) { toast("${biz.name} is closed right now"); return }
    val minutes = Economy.minutesUntil(state.minute, biz.closeHour).toInt()
    state.player.workingAt = biz.id
    state.objectivesDone += "work"
    startSkip(SkipKind.WORK, minutes, "Working at ${biz.name}…", target = biz.id)
}

fun GameEngine.cleanStore(biz: Business) {
    if (biz.cleanliness >= 99) { toast("Already spotless"); return }
    startSkip(SkipKind.CLEAN, 30, "Cleaning ${biz.name}…", target = biz.id)
}

// ---------------------------------------------------------------------- cargo & unloading

/** Vehicle parked close enough to the given building to load/unload. */
fun GameEngine.vehicleNear(buildingId: Int): OwnedVehicle? {
    val b = city.building(buildingId) ?: return null
    return state.vehicles.filter { dist(it.x, it.y, b.doorX, b.doorY) < 45f }.minByOrNull { dist(it.x, it.y, b.doorX, b.doorY) }
}

/** Unloads furniture and products from hand and nearby vehicle into the current building. Returns items moved. */
fun GameEngine.unloadHere(): Int {
    val p = state.player
    val b = city.building(p.inBuilding) ?: return 0
    val biz = state.businessAt(b.id)
    val isHome = b.id == p.home
    val v = vehicleNear(b.id)
    val sources = ArrayList<MutableList<CargoItem>>()
    sources += p.hand
    if (v != null) sources += v.cargo
    var moved = 0
    var skipped = 0
    var wrongProduct: String? = null
    for (list in sources) {
        val it = list.iterator()
        while (it.hasNext()) {
            val item = it.next()
            if (item.isProduct) {
                if (biz == null) { skipped++; continue }
                val pdef = Catalog.product(item.id) ?: continue
                if (biz.type.products.none { it.first == item.id }) { skipped++; wrongProduct = pdef.name; continue }
                val room = BizCalc.roomFor(biz, item.id)
                if (room <= 0) { skipped++; continue }
                val qty = min(room, item.qty)
                economy.addStock(biz, item.id, qty, pdef.cost)
                item.qty -= qty
                moved++
                if (item.qty <= 0) it.remove() else skipped++
            } else {
                when {
                    biz != null -> { biz.storage += item.id; it.remove(); moved++ }
                    isHome -> { state.homeStorage += item.id; it.remove(); moved++ }
                    else -> skipped++
                }
            }
        }
    }
    when {
        wrongProduct != null && moved == 0 -> toast("${biz?.name} doesn't sell $wrongProduct")
        moved == 0 && skipped == 0 -> toast(if (v == null) "Nothing to unload (park your vehicle within 45 m)" else "Your vehicle is empty")
        skipped > 0 -> toast("Unloaded $moved item(s). $skipped didn't fit — place shelves/displays first", ToastKind.INFO)
        else -> toast("Unloaded $moved item(s)", ToastKind.GOOD)
    }
    bump()
    return moved
}

fun GameEngine.unloadableCount(): Int {
    val p = state.player
    val b = city.building(p.inBuilding) ?: return 0
    val v = vehicleNear(b.id)
    return p.hand.size + (v?.cargo?.size ?: 0)
}

// ---------------------------------------------------------------------- shopping

data class CartLine(val id: String, val isProduct: Boolean, var boxes: Int, val unitPrice: Double) {
    val total get() = unitPrice * boxes
}

sealed class CheckoutTarget {
    data class Vehicle(val v: OwnedVehicle) : CheckoutTarget()
    data object Hand : CheckoutTarget()
    data class Deliver(val biz: Business) : CheckoutTarget()
    data object HomeDelivery : CheckoutTarget()
}

fun cartSlots(cart: List<CartLine>) = cart.sumOf { it.boxes }

fun GameEngine.checkoutOptions(storeBuildingId: Int, cart: List<CartLine>): List<Pair<CheckoutTarget, String>> {
    val out = ArrayList<Pair<CheckoutTarget, String>>()
    val slots = cartSlots(cart)
    val v = vehicleNear(storeBuildingId)
    if (v != null) {
        val free = cargoSlotsFree(v)
        out += CheckoutTarget.Vehicle(v) to "Load into ${v.def.name} (${free} of ${v.def.cargo} slots free)"
    }
    val handFree = 2 - state.player.hand.size
    if (slots <= handFree) out += CheckoutTarget.Hand to "Carry by hand ($handFree slots)"
    val subtotal = cart.sumOf { it.total }
    val fee = deliveryFee(subtotal)
    val furnitureOnly = cart.none { it.isProduct }
    for (b in state.businesses) out += CheckoutTarget.Deliver(b) to "Deliver to ${b.name} in 2h (+${money(fee)})"
    if (furnitureOnly && state.player.home >= 0) out += CheckoutTarget.HomeDelivery to "Deliver home in 2h (+${money(fee)})"
    return out
}

fun deliveryFee(subtotal: Double) = 50.0 + subtotal * .08

fun GameEngine.checkout(storeBuildingId: Int, cart: List<CartLine>, target: CheckoutTarget): Boolean {
    if (cart.isEmpty()) return false
    val subtotal = cart.sumOf { it.total }
    val items = cart.flatMap { line ->
        List(line.boxes) {
            if (line.isProduct) CargoItem(line.id, true, Catalog.product(line.id)?.unitsPerBox ?: 1) else CargoItem(line.id, false, 1)
        }
    }
    val category = if (cart.any { it.isProduct }) "Inventory" else "Furniture"
    when (target) {
        is CheckoutTarget.Vehicle -> {
            if (cargoSlotsFree(target.v) < items.size) { toast("Not enough room in your ${target.v.def.name} (${cargoSlotsFree(target.v)} free)", ToastKind.BAD); return false }
            if (!spend(subtotal, category)) return false
            target.v.cargo += items
            toast("Loaded ${items.size} item(s) into your ${target.v.def.name}", ToastKind.GOOD)
        }
        CheckoutTarget.Hand -> {
            if (state.player.hand.size + items.size > 2) { toast("You can only carry 2 items", ToastKind.BAD); return false }
            if (!spend(subtotal, category)) return false
            state.player.hand += items
            toast("You're carrying ${items.size} item(s). Unload them at your store or home.", ToastKind.GOOD)
        }
        is CheckoutTarget.Deliver -> {
            val fee = deliveryFee(subtotal)
            if (!spend(subtotal + fee, category)) return false
            target.biz.pendingDelivery += items
            target.biz.pendingDeliveryAt = state.minute + 120
            toast("Delivery to ${target.biz.name} arrives at ${"%02d:%02d".format(((state.minute + 120) % 1440) / 60, (state.minute + 120) % 60)}", ToastKind.GOOD)
        }
        CheckoutTarget.HomeDelivery -> {
            val fee = deliveryFee(subtotal)
            if (!spend(subtotal + fee, category)) return false
            state.homeStorage += items.filter { !it.isProduct }.map { it.id }
            toast("Delivered to your home storage", ToastKind.GOOD)
        }
    }
    if (category == "Furniture") state.objectivesDone += "furniture"
    bump()
    return true
}

// ---------------------------------------------------------------------- interior design

fun GameEngine.storageList(): MutableList<String>? {
    val sc = interior ?: return null
    return when (sc.mode) {
        InteriorMode.BUSINESS -> sc.business!!.storage
        InteriorMode.HOME -> state.homeStorage
        else -> null
    }
}

fun GameEngine.commitGhost(): Boolean {
    val sc = interior ?: return false
    val id = sc.ghostId ?: return false
    if (!sc.ghostValid) { toast("Can't place it there", ToastKind.BAD); return false }
    if (sc.movingIndex >= 0) {
        val p = sc.items[sc.movingIndex]
        p.x = sc.ghostX; p.y = sc.ghostY; p.rot = sc.ghostRot
    } else {
        val storage = storageList() ?: return false
        if (!storage.remove(id)) return false
        sc.items += PlacedItem(id, sc.ghostX, sc.ghostY, sc.ghostRot)
    }
    sc.cancelGhost()
    bump()
    return true
}

fun GameEngine.storeItem(index: Int) {
    val sc = interior ?: return
    val storage = storageList() ?: return
    val p = sc.items.getOrNull(index) ?: return
    if (sc.mode == InteriorMode.BUSINESS) {
        val biz = sc.business!!
        val def = Catalog.item(p.itemId)
        sc.items.removeAt(index)
        // stock that no longer fits on displays is lost from the shelves
        if (def?.fn == ItemFn.DISPLAY) clampStock(biz)
    } else sc.items.removeAt(index)
    storage += p.itemId
    sc.selectedIndex = -1
    bump()
}

fun GameEngine.sellItem(index: Int) {
    val sc = interior ?: return
    val p = sc.items.getOrNull(index) ?: return
    val def = Catalog.item(p.itemId) ?: return
    sc.items.removeAt(index)
    sc.business?.let { clampStock(it) }
    earn(def.price * .5, "Sold assets")
    sc.selectedIndex = -1
    toast("Sold ${def.name} for ${money(def.price * .5)}")
}

fun GameEngine.sellStored(itemId: String) {
    val storage = storageList() ?: return
    val def = Catalog.item(itemId) ?: return
    if (storage.remove(itemId)) {
        earn(def.price * .5, "Sold assets")
        toast("Sold ${def.name} for ${money(def.price * .5)}")
    }
}

private fun GameEngine.clampStock(biz: Business) {
    var lostValue = 0.0
    // displays are shared by every product with the same tag: trim each tag back to its capacity
    val byTag = biz.stock.keys.groupBy { Catalog.product(it)?.tag ?: "" }
    for ((tag, pids) in byTag) {
        val cap = BizCalc.displayCapacity(biz, tag)
        var total = pids.sumOf { biz.stock[it] ?: 0 }
        for (pid in pids.sortedByDescending { biz.stock[it] ?: 0 }) {
            if (total <= cap) break
            val have = biz.stock[pid] ?: 0
            val cut = minOf(have, total - cap)
            lostValue += cut * (biz.unitCost[pid] ?: 0.0)
            biz.stock[pid] = have - cut
            total -= cut
        }
    }
    if (lostValue > 0) {
        earn(lostValue * .5, "Sold assets")
        toast("Excess stock sold off for ${money(lostValue * .5)}")
    }
}

// ---------------------------------------------------------------------- staff

fun GameEngine.requestCandidates(role: StaffRole) {
    if (Catalog.COURSE_HR !in state.player.courses) { toast("You need the Basic HR course first"); return }
    if (state.recruits.any { it.role == role }) { toast("Already searching for ${role.label} candidates"); return }
    if (!spend(250.0, "Recruitment")) return
    state.recruits += PendingRecruit(role, 3, state.minute + 120)
    state.objectivesDone += "recruit"
    toast("Apex Recruitment is searching for ${role.label} candidates (2h)", ToastKind.GOOD)
}

fun GameEngine.hire(c: Employee) {
    if (!state.candidates.remove(c)) return
    state.employees += c.copy(hiredDay = state.day)
    message(c.name, "Thank you for hiring me! Please assign me to a business in the BizHub Scheduler.")
    toast("${c.name} hired as ${c.role.label}", ToastKind.GOOD)
    bump()
}

fun GameEngine.fire(emp: Employee) {
    state.employees.remove(emp)
    spend(emp.wage * 8, "Wages", allowDebt = true, silent = true)
    toast("${emp.name} was let go (severance ${money(emp.wage * 8)})")
    bump()
}

fun GameEngine.raise(emp: Employee) {
    emp.wage = ((emp.wage * 1.1) * 2).roundToInt() / 2.0
    emp.satisfaction = (emp.satisfaction + .12).coerceAtMost(1.0)
    toast("${emp.name}'s wage is now ${money(emp.wage)}/h")
    bump()
}

fun GameEngine.train(emp: Employee) {
    if (emp.skill >= .99) { toast("${emp.name} is already an expert"); return }
    if (!spend(400.0, "Training")) return
    emp.skill = (emp.skill + .12).coerceAtMost(1.0)
    toast("${emp.name} completed training: skill ${(emp.skill * 100).roundToInt()}%", ToastKind.GOOD)
}

// ---------------------------------------------------------------------- school, bank, marketing, cars

fun GameEngine.takeCourse(courseId: String) {
    val course = Catalog.course(courseId)
    if (courseId in state.player.courses) return
    if (!spend(course.price, "Education")) return
    ui.panel = null
    startSkip(SkipKind.COURSE, course.hours * 60, "Studying: ${course.name}", cancellable = false, payload = courseId)
}

fun GameEngine.takeLoan(def: com.hustlecity.game.data.LoanDef) {
    if (state.loans.any { it.name == def.name }) { toast("You already have a ${def.name}"); return }
    if (netWorth() < def.minNetWorth) { toast("Requires a net worth of ${money(def.minNetWorth)}"); return }
    state.loans += Loan(state.newId(), def.name, def.amount, def.amount / def.days, def.dailyRate, def.days)
    earn(def.amount, "Loans")
    state.objectivesDone += "loan"
    toast("${money(def.amount)} deposited to your account", ToastKind.GOOD)
    checkObjectives()
}

fun GameEngine.repayLoan(loan: Loan) {
    if (!spend(loan.remaining, "Loan repayment")) return
    state.loans.remove(loan)
    toast("Loan repaid in full", ToastKind.GOOD)
}

fun GameEngine.startCampaign(biz: Business, campaignId: String, days: Int) {
    val def = Catalog.campaign(campaignId)
    if (def.needsCourse && Catalog.COURSE_MARKETING !in state.player.courses) { toast("Requires the Marketing Fundamentals course"); return }
    val existing = biz.campaigns.firstOrNull { it.campaignId == campaignId }
    if (existing != null) {
        // extending: the extra days are billed at midnight like the rest
        existing.daysLeft += days
    } else {
        // the first day is paid upfront, the rest daily at midnight
        if (!spend(def.pricePerDay, "Marketing")) return
        biz.campaigns += Campaign(campaignId, days)
    }
    biz.marketing = max(biz.marketing, biz.campaigns.sumOf { Catalog.campaign(it.campaignId).boost }.coerceAtMost(1.0))
    state.objectivesDone += "marketing"
    toast("${def.name} running for ${biz.name} ($days days)", ToastKind.GOOD)
    bump()
}

fun GameEngine.buyVehicle(defId: String, dealerBuildingId: Int) {
    val def = Catalog.vehicle(defId)
    if (!spend(def.price, "Vehicles")) return
    val b = city.building(dealerBuildingId) ?: return
    val (x, y) = curbSpot(b, 6f + (state.vehicles.size % 3) * 5f)
    state.vehicles += OwnedVehicle(state.newId(), defId, x, y, (Math.PI / 2).toFloat(), fuel = 1.0, condition = 1.0, color = def.color)
    toast("Your new ${def.name} is parked outside!", ToastKind.GOOD)
    bump()
}

fun GameEngine.sellVehicle(v: OwnedVehicle) {
    val value = vehicleValue(v)
    if (state.player.driving == v.id) exitVehicle()
    state.vehicles.remove(v)
    earn(value, "Sold assets")
    toast("Sold ${v.def.name} for ${money(value)}")
}

fun GameEngine.repairVehicle(v: OwnedVehicle) {
    val cost = (1.0 - v.condition) * v.def.price * .2
    if (cost < 1) { toast("Already in perfect condition"); return }
    if (!spend(cost, "Vehicles")) return
    v.condition = 1.0
    toast("${v.def.name} repaired", ToastKind.GOOD)
}

fun GameEngine.refuel(v: OwnedVehicle) {
    val liters = (1.0 - v.fuel) * 45
    val cost = liters * 1.4
    if (liters < .5) { toast("Tank is already full"); return }
    if (!spend(cost, "Fuel")) return
    v.fuel = 1.0
    toast("Refueled ${liters.roundToInt()} L for ${money(cost)}", ToastKind.GOOD)
}

fun GameEngine.roadside(v: OwnedVehicle) {
    if (!spend(150.0, "Vehicles")) return
    v.fuel = max(v.fuel, .25)
    toast("Roadside assistance added fuel", ToastKind.GOOD)
}

// ---------------------------------------------------------------------- food & needs & jobs

fun GameEngine.eat(cost: Double, hunger: Double, energy: Double = 0.0, happiness: Double = 2.0, label: String) {
    if (!spend(cost, "Food")) return
    val p = state.player
    p.hunger = (p.hunger + hunger).coerceAtMost(100.0)
    p.energy = (p.energy + energy).coerceIn(0.0, 100.0)
    p.happiness = (p.happiness + happiness).coerceIn(0.0, 100.0)
    toast("You had $label", ToastKind.GOOD)
}

fun GameEngine.eatAtHome() {
    val p = state.player
    if (state.homeFurniture.none { Catalog.item(it.itemId)?.fn == ItemFn.HOME_FRIDGE }) { toast("You need a fridge at home"); return }
    if (p.fridgeMeals <= 0) { toast("Your fridge is empty. Buy groceries at a supermarket."); return }
    p.fridgeMeals--
    p.hunger = (p.hunger + 40).coerceAtMost(100.0)
    toast("You ate a home-cooked meal (${p.fridgeMeals} left)", ToastKind.GOOD)
    bump()
}

fun GameEngine.buyGroceries() {
    if (state.player.fridgeMeals >= 12) { toast("Your fridge is full"); return }
    if (!spend(32.0, "Food")) return
    state.player.fridgeMeals = min(12, state.player.fridgeMeals + 4)
    toast("Bought groceries: fridge has ${state.player.fridgeMeals} meals", ToastKind.GOOD)
}

fun GameEngine.sleepMinutes(minutes: Int) {
    if (state.homeFurniture.none { Catalog.item(it.itemId)?.fn == ItemFn.BED }) { toast("Place a bed in your home first"); return }
    ui.panel = null
    startSkip(SkipKind.SLEEP, minutes, "Sleeping…")
}

fun GameEngine.sideJob(hours: Int, wage: Double, where: String) {
    ui.panel = null
    startSkip(SkipKind.SIDE_JOB, hours * 60, "Working a shift at $where…", amount = wage)
}

fun GameEngine.taxiTo(buildingId: Int) {
    val b = city.building(buildingId) ?: return
    val p = state.player
    if (p.inBuilding >= 0) exitBuilding()
    if (p.driving >= 0) exitVehicle()
    val d = dist(p.x, p.y, b.doorX, b.doorY)
    val fare = taxiFare(d)
    if (!spend(fare, "Taxi")) return
    ui.app = null
    ui.selected = -1
    val minutes = max(5, (d / 22f).roundToInt())
    startSkip(SkipKind.TAXI, minutes, "Taking a taxi to ${buildingName(b)}…", cancellable = false, target = b.id)
}

fun taxiFare(distance: Float) = 6.0 + distance / 100.0 * 2.6

fun GameEngine.setOpen(biz: Business, open: Boolean) {
    biz.tempClosed = !open
    bump()
}

fun GameEngine.setDelivery(biz: Business, contract: DeliveryContract?) {
    biz.delivery = contract
    bump()
}

/** Which place sells a given product. */
fun placeForSource(source: Source): String = Catalog.sourcePlaceId(source)

fun GameEngine.placeKindOf(buildingId: Int): PlaceKind? = city.building(buildingId)?.placeId?.let { Catalog.place(it).kind }

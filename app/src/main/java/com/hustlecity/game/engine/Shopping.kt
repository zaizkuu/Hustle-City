package com.hustlecity.game.engine

import com.hustlecity.game.audio.Audio
import com.hustlecity.game.audio.Sfx
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.model.CargoItem
import com.hustlecity.game.model.OwnedVehicle
import com.hustlecity.game.world.dist
import kotlin.math.min

/*
 * Walk-in shopping and carrying boxes, the way Big Ambitions does it: bare hands hold one box,
 * a hand truck four, and the flatbed carts at a store's entrance eight. Items picked off a display
 * are unpaid until you pay at the register; then you push them out to your vehicle and load the trunk.
 */

object Carrier {
    const val HANDS = 0
    const val HAND_TRUCK = 1
    const val CART = 2
}

const val HAND_TRUCK = "hand_truck"

/** How far from the store's door a store cart can be pushed: its parking lot. */
const val CART_RANGE = 55f

/** Minutes for one walk between the vehicle and the door with a load. */
const val CARRY_TRIP_MINUTES = 2

fun carrierCap(carrier: Int) = when (carrier) {
    Carrier.HAND_TRUCK -> 4
    Carrier.CART -> 8
    else -> 1
}

fun carrierName(carrier: Int) = when (carrier) {
    Carrier.HAND_TRUCK -> "Hand truck"
    Carrier.CART -> "Store cart"
    else -> "Hands"
}

fun GameEngine.carryCap() = carrierCap(state.player.carrier)
fun GameEngine.carryFree() = (carryCap() - state.player.hand.size).coerceAtLeast(0)
fun GameEngine.unpaidItems() = state.player.hand.filter { it.unpaid }
fun GameEngine.unpaidTotal() = state.player.hand.sumOf { if (it.unpaid) it.price else 0.0 }

private fun isTruck(item: CargoItem) = !item.isProduct && item.id == HAND_TRUCK

private fun GameEngine.fullMessage() = when (state.player.carrier) {
    Carrier.HANDS -> "Your hands are full. Grab a store cart by the entrance (8 boxes) or bring a hand truck (4)"
    Carrier.HAND_TRUCK -> "Your hand truck is full (4 boxes)"
    else -> "Your cart is full (8 boxes)"
}

// ---------------------------------------------------------------------- in the store

/** Takes one item off a display in a walk-in store. It's yours once you pay at the register. */
fun GameEngine.pickUp(itemId: String): Boolean {
    val def = Catalog.item(itemId) ?: return false
    if (carryFree() <= 0) { toast(fullMessage(), ToastKind.BAD, "hands"); return false }
    state.player.hand += CargoItem(itemId, false, 1, unpaid = true, price = def.price)
    Audio.play(Sfx.TAP)
    bump()
    return true
}

/** Puts an unpaid item back on its display. */
fun GameEngine.putBack(index: Int) {
    val item = state.player.hand.getOrNull(index) ?: return
    if (!item.unpaid) return
    state.player.hand.removeAt(index)
    bump()
}

fun GameEngine.putAllBack(): Int {
    val n = state.player.hand.count { it.unpaid }
    state.player.hand.removeAll { it.unpaid }
    bump()
    return n
}

/** Takes one of the store's flatbed carts; what you carry goes onto it, a hand truck folds on top. */
fun GameEngine.takeCart(storeBuildingId: Int): Boolean {
    val p = state.player
    if (p.carrier == Carrier.CART) { toast("You already have a cart"); return false }
    if (p.carrier == Carrier.HAND_TRUCK) p.hand += CargoItem(HAND_TRUCK, false)
    p.carrier = Carrier.CART
    p.cartStore = storeBuildingId
    tip("store_cart", "Store carts", "A cart holds 8 boxes. Pick items off the displays, pay at the register, then push the cart out to your vehicle and load the trunk with Manage storage. Carts have to stay in the store's parking lot.")
    Audio.play(Sfx.TAP)
    bump()
    return true
}

/** Leaves the store cart behind. A paid hand truck on it becomes what you carry with again. */
fun GameEngine.returnCart(): Boolean {
    val p = state.player
    if (p.carrier != Carrier.CART) return false
    val truck = p.hand.indexOfFirst { isTruck(it) && !it.unpaid }
    if (truck >= 0) {
        if (p.hand.size - 1 > carrierCap(Carrier.HAND_TRUCK)) { toast("Too many boxes for your hand truck. Load some into your vehicle first", ToastKind.BAD); return false }
        p.hand.removeAt(truck)
        p.carrier = Carrier.HAND_TRUCK
    } else {
        if (p.hand.size > 1) { toast("Load the boxes into your vehicle first, or put them back", ToastKind.BAD); return false }
        p.carrier = Carrier.HANDS
    }
    p.cartStore = -1
    bump()
    return true
}

/** Pays for everything picked up in this store. */
fun GameEngine.payAtRegister(): Boolean {
    val p = state.player
    val unpaid = unpaidItems()
    if (unpaid.isEmpty()) { toast("Pick something up first"); return false }
    val total = unpaid.sumOf { it.price }
    if (!spend(total, "Furniture")) return false
    unpaid.forEach { it.unpaid = false }
    if (unpaid.any { !isTruck(it) }) state.objectivesDone += "furniture"
    // a freshly bought hand truck is ready to use straight away
    if (p.carrier == Carrier.HANDS) {
        val i = p.hand.indexOfFirst { isTruck(it) }
        if (i >= 0 && p.hand.size - 1 <= carrierCap(Carrier.HAND_TRUCK)) { p.hand.removeAt(i); p.carrier = Carrier.HAND_TRUCK }
    }
    Audio.play(Sfx.CASH)
    toast("Paid ${money(total)}. Take it out to your vehicle and load it with Manage storage", ToastKind.GOOD, "receipt")
    checkObjectives()
    bump()
    return true
}

/** Delivery targets for what you picked up: your businesses, or your home's storage. */
fun GameEngine.deliveryTargets(): List<CheckoutTarget> =
    state.businesses.map { CheckoutTarget.Deliver(it) } + (if (state.player.home >= 0) listOf(CheckoutTarget.HomeDelivery) else emptyList())

/** The unpaid furniture as checkout lines (hand trucks aren't delivered: you take those with you). */
fun GameEngine.unpaidLines(): List<CartLine> =
    unpaidItems().filter { !isTruck(it) }.groupBy { it.id }.map { (id, list) -> CartLine(id, false, list.size, list.first().price) }

/** Pays at the register and has everything (except a hand truck) delivered for a fee. */
fun GameEngine.payAndDeliver(storeBuildingId: Int, target: CheckoutTarget): Boolean {
    val lines = unpaidLines()
    if (lines.isEmpty()) { toast("Nothing to deliver"); return false }
    if (!checkout(storeBuildingId, lines, target)) return false
    state.player.hand.removeAll { it.unpaid && !isTruck(it) }
    bump()
    return true
}

/** Unpaid goods stay in the store when you're taken out of it (taxi, hospital…). */
fun GameEngine.dropUnpaid() {
    state.player.hand.removeAll { it.unpaid }
}

// ---------------------------------------------------------------------- vehicles and the hand truck

/** Unfolds a hand truck from a vehicle's trunk (or your hands); what you hold goes onto it. */
fun GameEngine.useHandTruck(from: MutableList<CargoItem>, index: Int): Boolean {
    val p = state.player
    val item = from.getOrNull(index) ?: return false
    if (!isTruck(item) || item.unpaid) return false
    when (p.carrier) {
        Carrier.HAND_TRUCK -> { toast("You're already using a hand truck"); return false }
        Carrier.CART -> { toast("Return the store cart first"); return false }
    }
    from.removeAt(index)
    p.carrier = Carrier.HAND_TRUCK
    Audio.play(Sfx.TAP)
    bump()
    return true
}

/** Folds the hand truck (and the boxes on it) into a vehicle. */
fun GameEngine.stowHandTruck(v: OwnedVehicle): Boolean {
    val p = state.player
    if (p.carrier != Carrier.HAND_TRUCK) return false
    val need = p.hand.size + 1
    if (cargoSlotsFree(v) < need) { toast("The ${v.def.name} needs $need free slots for the hand truck and its boxes", ToastKind.BAD); return false }
    v.cargo += p.hand
    v.cargo += CargoItem(HAND_TRUCK, false)
    p.hand.clear()
    p.carrier = Carrier.HANDS
    bump()
    return true
}

/** Moves one box from the trunk onto whatever you carry with. */
fun GameEngine.takeOne(v: OwnedVehicle, index: Int): Boolean {
    val item = v.cargo.getOrNull(index) ?: return false
    if (isTruck(item) && state.player.carrier == Carrier.HANDS && state.player.hand.isEmpty()) return useHandTruck(v.cargo, index)
    return takeFromTrunk(v, index)
}

/** Moves one carried box into the trunk. */
fun GameEngine.stowOne(v: OwnedVehicle, index: Int): Boolean {
    val p = state.player
    val item = p.hand.getOrNull(index) ?: return false
    if (item.unpaid) { toast("Pay for it first", ToastKind.BAD); return false }
    if (cargoSlotsFree(v) <= 0) { toast("The ${v.def.name} is full", ToastKind.BAD); return false }
    p.hand.removeAt(index)
    v.cargo += item
    bump()
    return true
}

/** Fills your hands, hand truck or cart from the trunk. */
fun GameEngine.takeAll(v: OwnedVehicle): Int {
    var n = 0
    var i = 0
    while (carryFree() > 0 && i < v.cargo.size) {
        if (isTruck(v.cargo[i])) { i++; continue }
        state.player.hand += v.cargo.removeAt(i)
        n++
    }
    if (n == 0) toast(if (v.cargo.none { !isTruck(it) }) "The trunk is empty" else fullMessage())
    bump()
    return n
}

/** Sells a carried (paid) box back for half its price. */
fun GameEngine.sellCarried(index: Int) {
    val p = state.player
    val item = p.hand.getOrNull(index) ?: return
    if (item.unpaid) return
    val value = if (item.isProduct) (Catalog.product(item.id)?.cost ?: 0.0) * item.qty * .5 else (Catalog.item(item.id)?.price ?: 0.0) * .5
    p.hand.removeAt(index)
    earn(value, "Sold assets")
    toast("Sold for ${money(value)}")
    bump()
}

/**
 * Gets ready to drive [v]: what you carry goes into its trunk (a hand truck folds in too, a store cart is
 * left behind). Returns false when it doesn't all fit.
 */
fun GameEngine.loadForDriving(v: OwnedVehicle): Boolean {
    val p = state.player
    val truck = p.carrier == Carrier.HAND_TRUCK
    val need = p.hand.size + if (truck) 1 else 0
    if (need > cargoSlotsFree(v)) {
        toast("Not enough room in the ${v.def.name} for what you're carrying ($need needed, ${cargoSlotsFree(v)} free)", ToastKind.BAD, "cargo_boxes")
        return false
    }
    val n = p.hand.size
    val cart = p.carrier == Carrier.CART
    v.cargo += p.hand
    if (truck) v.cargo += CargoItem(HAND_TRUCK, false)
    p.hand.clear()
    p.carrier = Carrier.HANDS
    p.cartStore = -1
    if (n > 0 || truck || cart) {
        toast(buildString {
            append(if (n > 0) "Loaded $n box(es) into the trunk" else if (truck) "Folded the hand truck into the trunk" else "Left the store cart")
            if (cart && n > 0) append(" and left the cart")
            else if (truck && n > 0) append(" with the hand truck")
        }, ToastKind.INFO, "cargo_boxes")
    }
    return true
}

/** A store cart can't come along in a taxi or into another building; an empty one is simply left. */
fun GameEngine.leaveCart(why: String): Boolean {
    val p = state.player
    if (p.carrier != Carrier.CART) return true
    if (p.hand.any { !isTruck(it) }) {
        toast("$why with a store cart. Load the boxes into your vehicle first", ToastKind.BAD)
        return false
    }
    return returnCart()
}

/** Keeps a store cart inside its store's parking lot. */
fun GameEngine.cartAllows(x: Float, y: Float): Boolean {
    val p = state.player
    if (p.carrier != Carrier.CART) return true
    val store = city.building(p.cartStore) ?: return true
    return dist(x, y, store.doorX, store.doorY) <= CART_RANGE
}

/**
 * Carries a vehicle's cargo into one of your buildings, trip by trip: bare hands take a box per trip,
 * a hand truck four. Returns boxes moved.
 */
fun GameEngine.carryIn(buildingId: Int, v: OwnedVehicle): Int {
    val moved = unloadInto(buildingId, false, v)
    if (moved <= 0) return 0
    val per = if (state.player.carrier == Carrier.HANDS) 1 else carryCap()
    val trips = (moved + per - 1) / per
    if (state.player.carrier == Carrier.HANDS && trips >= 3 && v.cargo.none { isTruck(it) })
        message("Uncle Sal", "That was $trips trips back and forth! A hand truck from Essentials Appliances or OfficePlus carries 4 boxes at a time.")
    startSkip(com.hustlecity.game.model.SkipKind.WAIT, trips * CARRY_TRIP_MINUTES, "Carrying $moved box(es) inside ($trips trip${if (trips == 1) "" else "s"})…")
    return moved
}

/** Minutes [carryIn] would take for [boxes]. */
fun GameEngine.carryMinutes(boxes: Int): Int {
    val per = if (state.player.carrier == Carrier.HANDS) 1 else carryCap()
    return (boxes + per - 1) / per * CARRY_TRIP_MINUTES
}

// ---------------------------------------------------------------------- stocking your own store

/**
 * Boxes you carry that can go onto item [index] of the store you're in: products it sells, onto a display of
 * their kind with room left, or onto a storage shelf in the back room.
 */
fun GameEngine.boxesFor(index: Int): List<CargoItem> {
    val sc = interior ?: return emptyList()
    val biz = sc.business ?: return emptyList()
    val def = sc.items.getOrNull(index)?.let { Catalog.item(it.itemId) } ?: return emptyList()
    return state.player.hand.filter { item ->
        if (!item.isProduct || item.unpaid || item.qty <= 0 || !BizCalc.sells(biz, item.id)) return@filter false
        val p = Catalog.product(item.id) ?: return@filter false
        when {
            def.fn == ItemFn.STORAGE -> BizCalc.storageRoomFor(biz, item.id) > 0
            def.fn == ItemFn.DISPLAY && p.tag in def.tags -> BizCalc.roomFor(biz, item.id) > 0
            else -> false
        }
    }
}

/**
 * Unpacks the boxes you carry onto display [index] of your store (it fills in item by item), or puts them on a storage
 * shelf. As much as fits goes on; the rest stays in the box. Returns the units stocked.
 */
fun GameEngine.stockFromHands(index: Int): Int {
    val sc = interior ?: return 0
    val biz = sc.business ?: return 0
    val def = sc.items.getOrNull(index)?.let { Catalog.item(it.itemId) } ?: return 0
    val boxes = boxesFor(index)
    if (boxes.isEmpty()) return 0
    sc.shelf?.focusOn(index)
    var moved = 0
    for (item in boxes) {
        val p = Catalog.product(item.id) ?: continue
        val qty = if (def.fn == ItemFn.STORAGE) economy.stockBack(biz, item.id, item.qty, p.cost)
        else min(item.qty, BizCalc.roomFor(biz, item.id)).also { economy.addStock(biz, item.id, it, p.cost) }
        item.qty -= qty
        moved += qty
    }
    val emptied = state.player.hand.count { it.isProduct && it.qty <= 0 }
    state.player.hand.removeAll { it.isProduct && it.qty <= 0 }
    if (moved > 0) {
        Audio.play(Sfx.TAP)
        toast(if (def.fn == ItemFn.STORAGE) "Put $emptied box(es) on the storage shelf" else "Stocked $moved on the ${def.name}" +
            if (emptied < boxes.size) " (it's full: the rest stays in the box)" else "", ToastKind.GOOD, "cargo_boxes")
    }
    bump()
    return moved
}

/**
 * Walks to the nearest display (then storage shelf) that takes a box you carry and stocks it, and keeps going
 * until the boxes are unpacked or nothing has room. Returns false when no display takes what you carry.
 */
fun GameEngine.stockShelves(): Boolean {
    val sc = interior ?: return false
    if (sc.business == null) return false
    val target = sc.items.indices.filter { boxesFor(it).isNotEmpty() }.minByOrNull { i ->
        val p = sc.items[i]
        val storage = Catalog.item(p.itemId)?.fn == ItemFn.STORAGE
        (if (storage) 1000f else 0f) + kotlin.math.abs(p.x + .5f - sc.px) + kotlin.math.abs(p.y + .5f - sc.py)
    } ?: return false
    sc.inspect = target
    sc.walkToItem(target) { if (stockFromHands(target) > 0) stockShelves() }
    return true
}

/** Why you can't restock display [index] of your store by hand right now, or null when you can. */
fun GameEngine.restockProblem(index: Int): String? {
    val sc = interior ?: return "You're not in your store"
    val biz = sc.business ?: return "You're not in your store"
    val def = sc.items.getOrNull(index)?.let { Catalog.item(it.itemId) } ?: return "Nothing to restock"
    if (boxesFor(index).isNotEmpty()) return null
    val tag = BizCalc.displayTag(biz, def) ?: return "This isn't a display for your products"
    val pids = biz.type.products.map { it.first }.filter { Catalog.product(it)?.tag == tag }
    if (pids.all { BizCalc.roomFor(biz, it) <= 0 }) return "The ${def.name} is full"
    if (BizCalc.backStockFor(biz, tag) <= 0) return if (BizCalc.storageBoxes(biz) == 0) "No stock in the back room: put storage shelves there, or bring boxes in"
        else "No boxes for the ${def.name} in the back room"
    if (sc.storageShelfFor() < 0) return "No storage shelf to take a box from"
    return null
}

/**
 * Restocks display [index] of your store by hand: unpacks the boxes you carry onto it, or walks to the back room,
 * takes as many boxes of its kind off a storage shelf as you can carry, brings them over and unpacks them (the items
 * fill in as you do). Returns false (with the reason in a toast) when there's nothing to restock it with.
 */
fun GameEngine.restockDisplay(index: Int): Boolean {
    val sc = interior ?: return false
    val biz = sc.business ?: return false
    val def = sc.items.getOrNull(index)?.let { Catalog.item(it.itemId) } ?: return false
    restockProblem(index)?.let { toast(it); return false }
    sc.inspect = index
    if (boxesFor(index).isNotEmpty()) {
        if (sc.playerNear(index)) stockFromHands(index) else sc.walkToItem(index) { stockFromHands(index) }
        return true
    }
    val tag = BizCalc.displayTag(biz, def) ?: return false
    val shelfIdx = sc.storageShelfFor()
    sc.walkToItem(shelfIdx) {
        if (BizCalc.backStockFor(biz, tag) <= 0) { toast("Someone already took those boxes out"); return@walkToItem }
        sc.playerBox = true
        Audio.play(Sfx.TAP)
        sc.walkToItem(index) {
            sc.shelf?.focusOn(index)
            sc.shelf?.playerStocked()
            val moved = economy.restockKind(biz, tag, if (state.player.carrier == Carrier.HANDS) 1 else carryCap())
            sc.playerBox = false
            if (moved > 0) {
                Audio.play(Sfx.TAP)
                toast("Restocked $moved on the ${def.name}", ToastKind.GOOD, "cargo_boxes")
            } else toast("The ${def.name} is already full")
            bump()
        }
    }
    bump()
    return true
}

/** How the player model holds things, for [com.hustlecity.game.render.PersonPainter]: 0 nothing, 1 a box, 2 hand truck, 3 cart. */
fun GameEngine.carryPose(): Int = when (state.player.carrier) {
    Carrier.HAND_TRUCK -> 2
    Carrier.CART -> 3
    else -> if (state.player.hand.isNotEmpty()) 1 else 0
}

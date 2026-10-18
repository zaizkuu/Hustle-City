package com.hustlecity.game.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceDef
import com.hustlecity.game.engine.Carrier
import com.hustlecity.game.engine.CheckoutTarget
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.HAND_TRUCK
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.Panel
import com.hustlecity.game.engine.carrierName
import com.hustlecity.game.engine.carryCap
import com.hustlecity.game.engine.deliveryFee
import com.hustlecity.game.engine.deliveryTargets
import com.hustlecity.game.engine.payAndDeliver
import com.hustlecity.game.engine.payAtRegister
import com.hustlecity.game.engine.pickUp
import com.hustlecity.game.engine.putAllBack
import com.hustlecity.game.engine.putBack
import com.hustlecity.game.engine.returnCart
import com.hustlecity.game.engine.takeCart
import com.hustlecity.game.engine.unpaidItems
import com.hustlecity.game.engine.unpaidLines
import com.hustlecity.game.engine.unpaidTotal
import com.hustlecity.game.model.CargoItem
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.ItemThumb
import com.hustlecity.game.ui.KeyValue
import com.hustlecity.game.ui.ProductThumb
import com.hustlecity.game.ui.icons.GIcon
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT

/** A carried box: the product's own illustration, or a thumbnail of the furniture inside. */
@Composable
fun CargoThumb(item: CargoItem, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val p = if (item.isProduct) Catalog.product(item.id) else null
    if (p != null) ProductThumb(p, size, modifier) else ItemThumb(item.id, size, modifier)
}

/** Walks to the checkout (if needed) and pays there. */
private fun pay(e: GameEngine, sc: InteriorScene) {
    val reg = sc.registerIndex
    sc.inspect = reg
    if (reg < 0 || sc.playerNear(reg)) e.payAtRegister() else sc.walkToItem(reg) { e.payAtRegister() }
}

private fun grabCart(e: GameEngine, sc: InteriorScene) {
    val rack = sc.rackIndex
    sc.inspect = rack
    if (rack < 0 || sc.playerNear(rack)) e.takeCart(sc.building.id) else sc.walkToItem(rack) { e.takeCart(sc.building.id) }
}

private fun pick(e: GameEngine, sc: InteriorScene, index: Int, id: String) {
    if (sc.playerNear(index)) e.pickUp(id) else sc.walkToItem(index) { e.pickUp(id) }
}

/** What you carry with, as a header line: icon, name and n/capacity. */
@Composable
fun CarrierLine(e: GameEngine, color: Color = Color.White) {
    val p = e.state.player
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(if (p.carrier == Carrier.HANDS) "hands" else "hand_truck", 16.dp, tint = color)
        Text(" ${carrierName(p.carrier).uppercase()} ${p.hand.size}/${e.carryCap()}", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** The shopping controls in the interior panel of a walk-in store. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopControls(e: GameEngine, sc: InteriorScene) {
    val p = e.state.player
    val full = Modifier.fillMaxWidth()
    CarrierLine(e)
    if (p.hand.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((i, item) in p.hand.withIndex()) {
                Box {
                    CargoThumb(item, 34.dp, Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .9f)).padding(2.dp))
                    if (item.unpaid) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(15.dp).clip(RoundedCornerShape(3.dp)).background(HC.Red).clickable { e.putBack(i) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Put back", tint = Color.White, modifier = Modifier.size(11.dp)) }
                    }
                }
            }
        }
    }
    if (p.carrier == Carrier.CART) {
        HButton("Return cart", full, small = true, color = HC.GlassLight, enabled = p.hand.size <= 1 || p.hand.any { it.id == HAND_TRUCK && !it.unpaid }) { e.returnCart() }
    } else if (sc.rackIndex >= 0) {
        HButton("Grab a cart (8 boxes)", full, small = true) { grabCart(e, sc) }
    }
    val unpaid = e.unpaidItems()
    if (unpaid.isNotEmpty()) {
        Text("${unpaid.size} unpaid · ${e.money(e.unpaidTotal())}", color = HC.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        HButton("Pay ${e.money(e.unpaidTotal())}", full, color = HC.Green) { pay(e, sc) }
        if (e.deliveryTargets().isNotEmpty() && e.unpaidLines().isNotEmpty())
            HButton("Pay & deliver", full, small = true, color = HC.Teal) { e.ui.panel = Panel.ShopDeliver(sc.building.id) }
        HButton("Put it all back", full, small = true, color = HC.GlassLight) { e.putAllBack() }
    } else if (p.hand.isEmpty()) {
        Text("Tap anything on display to look at it.", color = Color.White.copy(alpha = .75f), fontSize = 10.sp)
    }
    sc.building.placeId?.let { pid -> HButton("Store directory", full, small = true, color = HC.Purple) { e.ui.panel = Panel.Place(sc.building.id, pid) } }
}

/** Card for whatever you tapped in a walk-in store: an item on display, the checkout or the cart rack. */
@Composable
fun ShopItemCard(e: GameEngine, modifier: Modifier = Modifier) {
    val sc = e.interior ?: return
    if (!sc.walkIn || sc.inspect < 0 || e.ui.designer) return
    val index = sc.inspect
    val p = e.state.player
    Column(
        modifier.widthIn(max = 370.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF7F8FA)),
    ) {
        val sale = sc.forSale[index]
        val title = when {
            sale != null -> Catalog.item(sale)?.name ?: sale
            index == sc.registerIndex -> "Checkout"
            else -> "Store carts"
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sc.playerWalking) Text("walking… ", style = HT.small, color = HC.InkSoft)
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).background(HC.Red).clickable { sc.inspect = -1; e.bump() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        when {
            sale != null -> {
                val def = Catalog.item(sale) ?: return@Column
                Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
                    ItemThumb(def.id, 64.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(e.money(def.price), style = HT.title, color = HC.Green)
                        Text(def.description, style = HT.small, color = HC.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val stats = buildList {
                            if (def.id != HAND_TRUCK) add("${def.w}×${def.d} tiles")
                            if (def.capacity > 0) add("serves ${def.capacity}/h")
                            if (def.displayUnits > 0) add("holds ${def.displayUnits}")
                            if (def.decor > 0 || def.music > 0) add("interior +${def.decor + def.music * 2}")
                        }
                        if (stats.isNotEmpty()) Text(stats.joinToString(" · "), style = HT.small, color = HC.Ink, modifier = Modifier.padding(top = 2.dp))
                        val users = Catalog.businessTypes.filter { t ->
                            t.requirements.any { it.tag in def.tags } || t.products.any { Catalog.product(it.first)?.tag in def.tags }
                        }
                        if (users.isNotEmpty()) Text("Used by " + users.joinToString { it.name }, style = HT.small, color = HC.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val have = p.hand.count { it.id == def.id && it.unpaid }
                Row(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CarrierLine(e, HC.Ink)
                    if (have > 0) Text("  $have picked", style = HT.small, color = HC.InkSoft)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    if (have > 0) HButton("Put back", small = true, color = HC.GlassLight) {
                        val i = p.hand.indexOfLast { it.id == def.id && it.unpaid }
                        if (i >= 0) e.putBack(i)
                    }
                    val verb = when (p.carrier) { Carrier.CART -> "Add to cart"; Carrier.HAND_TRUCK -> "Load on hand truck"; else -> "Pick up" }
                    HButton(verb, small = true, color = HC.Blue) { pick(e, sc, index, def.id) }
                }
            }
            index == sc.registerIndex -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val unpaid = e.unpaidItems()
                if (unpaid.isEmpty()) Text("Pick items off the displays, then pay here. The cashier rings you up.", style = HT.small, color = HC.InkSoft)
                else {
                    for ((id, list) in unpaid.groupBy { it.id }) {
                        KeyValue("${Catalog.item(id)?.name ?: id} ×${list.size}", e.money(list.sumOf { it.price }), bold = false)
                    }
                    KeyValue("Total", e.money(e.unpaidTotal()))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HButton("Pay ${e.money(e.unpaidTotal())}", color = HC.Green) { pay(e, sc) }
                        if (e.deliveryTargets().isNotEmpty() && e.unpaidLines().isNotEmpty())
                            HButton("Pay & deliver", small = true, color = HC.Teal) { e.ui.panel = Panel.ShopDeliver(sc.building.id) }
                    }
                }
            }
            else -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Free flatbed carts: each holds 8 boxes, but it has to stay in the store's parking lot. Push it out to your vehicle and load the trunk.", style = HT.small, color = HC.InkSoft)
                if (p.carrier == Carrier.CART) HButton("Return cart", small = true, color = HC.GlassLight) { e.returnCart() }
                else HButton("Take a cart", small = true) { grabCart(e, sc) }
            }
        }
    }
}

/** The store directory: everything on sale here, with a button that walks you to it. */
@Composable
fun StoreDirectory(e: GameEngine, buildingId: Int, place: PlaceDef) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(place.stock.first()) }
    val sc = e.interior?.takeIf { it.building.id == buildingId }
    Modal("${place.name} · Store directory", { e.ui.panel = null }, headerExtra = { DirectorySearch(query) { query = it } }) {
        Text("Everything here is on display in the store. Find it, pick it up and pay at the checkout.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.weight(1f)) {
            val list = place.stock.mapNotNull { Catalog.item(it) }.filter { query.isBlank() || it.name.contains(query, true) }
            LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { def ->
                    Column(
                        Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFD9DDE2))
                            .border(2.dp, if (selected == def.id) HC.Blue else Color.Transparent, RoundedCornerShape(5.dp))
                            .clickable { selected = def.id }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(def.name, color = HC.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
                        ItemThumb(def.id, 60.dp)
                        Text(e.money(def.price), color = HC.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val def = Catalog.item(selected)
            if (def != null) {
                Column(Modifier.width(210.dp).fillMaxHeight().padding(start = 10.dp).verticalScroll(rememberScrollState())) {
                    ItemThumb(def.id, 104.dp, Modifier.align(Alignment.CenterHorizontally))
                    Text(def.name, style = HT.title, color = HC.Ink)
                    Text(e.money(def.price), style = HT.bodyBold, color = HC.Green)
                    Text(def.description, style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(vertical = 4.dp))
                    if (def.id != HAND_TRUCK) KeyValue("Size", "${def.w}×${def.d} tiles", bold = false)
                    if (def.capacity > 0) KeyValue("Customer capacity", "${def.capacity}", bold = false)
                    if (def.displayUnits > 0) KeyValue("Holds", "${def.displayUnits} units", bold = false)
                    if (def.decor > 0 || def.music > 0) KeyValue("Interior score", "+${def.decor + def.music * 2}", bold = false)
                    val users = Catalog.businessTypes.filter { t ->
                        t.requirements.any { it.tag in def.tags } || t.products.any { Catalog.product(it.first)?.tag in def.tags }
                    }
                    if (users.isNotEmpty()) Text("Used by: " + users.joinToString { it.name }, style = HT.small, color = HC.Ink, modifier = Modifier.padding(vertical = 4.dp))
                    val index = sc?.forSale?.entries?.firstOrNull { it.value == def.id }?.key ?: -1
                    HButton("Find it in the store", Modifier.fillMaxWidth().padding(top = 6.dp), color = HC.Blue, enabled = index >= 0) {
                        sc?.let { it.inspect = index; it.walkToItem(index) }
                        e.ui.panel = null
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectorySearch(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.width(190.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEDF0F4)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon("search", 15.dp, tint = HC.InkSoft)
        Box(Modifier.padding(start = 6.dp)) {
            if (query.isEmpty()) Text("Search, e.g. \"Shelf\"", color = HC.InkSoft, fontSize = 12.sp)
            androidx.compose.foundation.text.BasicTextField(query, onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = HC.Ink))
        }
    }
}

/** Pays at the checkout and has the furniture delivered to one of your places. */
@Composable
fun ShopDeliverPanel(e: GameEngine, storeBuildingId: Int) {
    val lines = e.unpaidLines()
    val subtotal = lines.sumOf { it.total }
    val fee = deliveryFee(subtotal)
    Modal("Pay & deliver", { e.ui.panel = null }, maxWidth = 460.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (l in lines) KeyValue("${Catalog.item(l.id)?.name ?: l.id} ×${l.boxes}", e.money(l.total), bold = false)
            KeyValue("Delivery (2 hours)", e.money(fee), bold = false)
            KeyValue("Total", e.money(subtotal + fee))
            if (e.state.player.hand.any { it.unpaid && it.id == HAND_TRUCK }) Text("A hand truck isn't delivered: pay for it at the register and take it with you.", style = HT.small, color = HC.InkSoft)
            for (t in e.deliveryTargets()) {
                val label = when (t) {
                    is CheckoutTarget.Deliver -> "Deliver to ${t.biz.name}"
                    else -> "Deliver to your home storage"
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Color(0xFFEDF3FD)).clickable {
                        if (e.payAndDeliver(storeBuildingId, t)) e.ui.panel = null
                    }.padding(10.dp),
                ) { Text(label, style = HT.bodyBold, color = HC.Blue) }
            }
        }
    }
}

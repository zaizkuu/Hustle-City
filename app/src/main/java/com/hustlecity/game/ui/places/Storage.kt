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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.engine.Carrier
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.HAND_TRUCK
import com.hustlecity.game.engine.carrierName
import com.hustlecity.game.engine.carryCap
import com.hustlecity.game.engine.carryIn
import com.hustlecity.game.engine.carryMinutes
import com.hustlecity.game.engine.returnCart
import com.hustlecity.game.engine.sellCarried
import com.hustlecity.game.engine.sellFromTrunk
import com.hustlecity.game.engine.stowHandTruck
import com.hustlecity.game.engine.stowInTrunk
import com.hustlecity.game.engine.stowOne
import com.hustlecity.game.engine.takeAll
import com.hustlecity.game.engine.takeOne
import com.hustlecity.game.engine.unloadDestination
import com.hustlecity.game.engine.useHandTruck
import com.hustlecity.game.model.CargoItem
import com.hustlecity.game.ui.Chip
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.WhiteCard
import com.hustlecity.game.ui.icons.GIcon
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT

/**
 * Manage storage, as in Big Ambitions: the vehicle's cargo slots on one side, what you carry with
 * (hands, hand truck or store cart) on the other. Tap a box to move it across.
 */
@Composable
fun ManageStoragePanel(e: GameEngine, vehicleId: Int) {
    val v = e.state.vehicle(vehicleId) ?: run { e.ui.panel = null; return }
    val p = e.state.player
    var selling by remember { mutableStateOf(false) }
    val dest = e.unloadDestination(v)
    Modal("Manage storage · ${v.def.name}", { e.ui.panel = null }, maxWidth = 820.dp, headerExtra = {
        Chip("Sell mode", selling, color = HC.Red, icon = "money") { selling = !selling }
    }) {
        val boxes = v.cargo.count { it.id != HAND_TRUCK }
        if (dest != null && boxes > 0) {
            WhiteCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), padding = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Parked by ${e.buildingName(dest)}", style = HT.bodyBold, color = HC.Ink)
                        Text(
                            if (p.carrier == Carrier.HANDS) "Bare hands carry 1 box per trip. A hand truck carries 4." else "Your ${carrierName(p.carrier).lowercase()} carries ${e.carryCap()} boxes per trip.",
                            style = HT.small, color = HC.InkSoft,
                        )
                    }
                    HButton("Carry all inside (~${e.carryMinutes(boxes)} min)", small = true, color = HC.Orange) { if (e.carryIn(dest.id, v) > 0) e.ui.panel = null }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StorageGrid("CARGO", "cargo_boxes", v.cargo, v.def.cargo, selling, Modifier.weight(1.3f).fillMaxHeight()) { i ->
                if (selling) e.sellFromTrunk(v, i) else e.takeOne(v, i)
            }
            StorageGrid(carrierName(p.carrier).uppercase(), if (p.carrier == Carrier.HANDS) "hands" else "hand_truck", p.hand, e.carryCap(), selling, Modifier.weight(1f).fillMaxHeight()) { i ->
                if (selling) e.sellCarried(i) else e.stowOne(v, i)
            }
        }
        StorageActions(e, v)
        Text(if (selling) "Sell mode: tap a box to sell it back for half its price." else "Tap a box to move it between the trunk and what you're carrying.",
            style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageActions(e: GameEngine, v: com.hustlecity.game.model.OwnedVehicle) {
    val p = e.state.player
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HButton("Load all into trunk", small = true, color = HC.Teal, enabled = p.hand.any { !it.unpaid }) { e.stowInTrunk(v) }
        HButton("Take all", small = true, enabled = v.cargo.any { it.id != HAND_TRUCK }) { e.takeAll(v) }
        val truck = v.cargo.indexOfFirst { !it.isProduct && it.id == HAND_TRUCK }
        when {
            p.carrier == Carrier.HAND_TRUCK -> HButton("Fold hand truck into trunk", small = true, color = HC.GlassLight) { e.stowHandTruck(v) }
            p.carrier == Carrier.HANDS && truck >= 0 -> HButton("Use hand truck", small = true, color = HC.Orange) { e.useHandTruck(v.cargo, truck) }
            p.carrier == Carrier.CART -> HButton("Return store cart", small = true, color = HC.GlassLight, enabled = p.hand.size <= 1) { e.returnCart() }
        }
    }
}

/** One side of Manage storage: a grid of slots, filled ones showing what's in the box. */
@Composable
private fun StorageGrid(
    title: String, icon: String, items: List<CargoItem>, cap: Int, selling: Boolean, modifier: Modifier, onTap: (Int) -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(6.dp)).background(HC.GlassDark).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            GIcon(icon, 16.dp)
            Text("  $title", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("${items.size}/$cap", color = if (items.size >= cap) HC.Yellow else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        // a slot per box (at least the capacity, more when an old save carries extra)
        val slots = maxOf(cap, items.size)
        LazyVerticalGrid(GridCells.Adaptive(66.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(slots) { i ->
                val item = items.getOrNull(i)
                Column(
                    Modifier.aspectRatio(.82f).clip(RoundedCornerShape(5.dp))
                        .background(if (item == null) Color(0x22FFFFFF) else if (selling) Color(0xFFFBE3E3) else Color(0xFFEFE3CF))
                        .border(1.dp, if (item?.unpaid == true) HC.Red else Color(0x33FFFFFF), RoundedCornerShape(5.dp))
                        .clickable(enabled = item != null) { onTap(i) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (item != null) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            CargoThumb(item, 38.dp)
                            if (item.isProduct) Text("${item.qty}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(HC.Ink).padding(horizontal = 3.dp))
                        }
                        Text(item.name, color = HC.Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp)
                    } else {
                        Box(Modifier.size(20.dp).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

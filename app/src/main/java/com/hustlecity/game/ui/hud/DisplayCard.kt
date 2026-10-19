package com.hustlecity.game.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.ItemFn
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.boxesFor
import com.hustlecity.game.engine.restockDisplay
import com.hustlecity.game.engine.restockProblem
import com.hustlecity.game.engine.stockFromHands
import com.hustlecity.game.ui.Bar
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.ItemThumb
import com.hustlecity.game.ui.ProductThumb
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT

/**
 * Card for a display or storage shelf you tapped in your own store: what it holds, what waits for it in the back
 * room, and a Restock button that has you carry boxes over and unpack them.
 */
@Composable
fun DisplayCard(e: GameEngine, modifier: Modifier = Modifier) {
    val sc = e.interior ?: return
    val biz = sc.business ?: return
    if (sc.mode != InteriorMode.BUSINESS || sc.inspect < 0 || e.ui.designer) return
    val index = sc.inspect
    val placed = sc.items.getOrNull(index) ?: return
    val def = Catalog.item(placed.itemId) ?: return
    val storage = def.fn == ItemFn.STORAGE
    val tag = BizCalc.displayTag(biz, def)
    if (!storage && tag == null) return
    Column(
        modifier.widthIn(max = 340.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF7F8FA)),
    ) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(def.name, style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sc.playerWalking) Text("walking… ", style = HT.small, color = HC.InkSoft)
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).background(HC.Red).clickable { sc.inspect = -1; e.bump() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            ItemThumb(def.id, 52.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (storage) {
                    val used = BizCalc.storageBoxesUsed(biz)
                    val cap = BizCalc.storageBoxes(biz)
                    Text("Back room: $used / $cap boxes", style = HT.bodyBold, color = HC.Ink)
                    Bar(used / cap.coerceAtLeast(1).toFloat(), if (used >= cap) HC.Orange else HC.Teal, Modifier.fillMaxWidth(), track = HC.Line, height = 7.dp)
                    if (biz.backStock.isEmpty()) Text("Empty. Boxes that don't fit on your displays are kept here, and your staff restock the displays from it.", style = HT.small, color = HC.InkSoft)
                    for ((pid, q) in biz.backStock.entries.sortedByDescending { it.value }.take(5)) {
                        val p = Catalog.product(pid) ?: continue
                        ProductLine(p, "${BizCalc.boxesOf(pid, q)} box(es) · $q")
                    }
                } else {
                    val shelf = sc.shelf
                    val on = shelf?.unitsOf(index) ?: 0
                    val cap = def.displayUnits
                    Text("On this display: $on / $cap", style = HT.bodyBold, color = if (on == 0) HC.Red else HC.Ink)
                    Bar(on / cap.coerceAtLeast(1).toFloat(), if (on < cap * .25f) HC.Orange else HC.Green, Modifier.fillMaxWidth(), track = HC.Line, height = 7.dp)
                    for ((pid, q) in shelf?.contents(index).orEmpty().take(4)) {
                        val p = Catalog.product(pid) ?: continue
                        ProductLine(p, "$q")
                    }
                    val back = BizCalc.backStockFor(biz, tag!!)
                    Text(if (back > 0) "In the back room: $back" else "Nothing for it in the back room", style = HT.small, color = HC.InkSoft)
                }
            }
        }
        Row(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (storage) {
                val carried = e.boxesFor(index).size
                Text(if (carried > 0) "You carry $carried box(es) for it" else "Carry boxes in to store them here", style = HT.small, color = HC.InkSoft, modifier = Modifier.weight(1f))
                if (carried > 0) HButton("Put boxes here", small = true, color = HC.Orange) {
                    if (sc.playerNear(index)) e.stockFromHands(index) else sc.walkToItem(index) { e.stockFromHands(index) }
                }
            } else {
                val problem = e.restockProblem(index)
                Text(problem ?: if (e.boxesFor(index).isNotEmpty()) "Unpack the boxes you carry" else "Fetch boxes from the back room", style = HT.small,
                    color = if (problem == null) HC.InkSoft else HC.Orange, modifier = Modifier.weight(1f), maxLines = 2)
                HButton("Restock", small = true, color = HC.Blue, enabled = problem == null) { e.restockDisplay(index) }
            }
        }
    }
}

@Composable
private fun ProductLine(p: com.hustlecity.game.data.ProductDef, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProductThumb(p, 18.dp)
        Text(" ${p.name}", style = HT.small, color = HC.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(4.dp))
        Text(value, style = HT.small, color = HC.InkSoft)
    }
}

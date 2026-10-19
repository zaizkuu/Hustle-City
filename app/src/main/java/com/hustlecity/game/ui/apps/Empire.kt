package com.hustlecity.game.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.Confirm
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Operations
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.model.FleetRoute
import com.hustlecity.game.model.MINUTES_PER_DAY
import com.hustlecity.game.model.Ticker
import com.hustlecity.game.ui.CardTitle
import com.hustlecity.game.ui.Chip
import com.hustlecity.game.ui.EmptyNote
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.KeyValue
import com.hustlecity.game.ui.Stepper
import com.hustlecity.game.ui.VehicleThumb
import com.hustlecity.game.ui.WhiteCard
import com.hustlecity.game.ui.icons.GIcon
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT
import kotlin.math.roundToInt

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

private fun clockOf(minute: Long) = "%02d:%02d".format(((minute % MINUTES_PER_DAY) / 60).toInt(), (minute % 60).toInt())

// ------------------------------------------------------------------ fleet

/** Vans, drivers and their standing delivery routes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FleetApp(e: GameEngine) {
    val s = e.state
    val ops = e.ops
    Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(250.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Vans")
                val vans = ops.vans()
                if (vans.isEmpty()) {
                    Text("No delivery vehicle yet. A Bulldog Pickup, Ranger SUV, Freighter Van or Hauler Box Truck from a car dealership can run routes.",
                        style = HT.small, color = HC.InkSoft)
                    HButton("Pin car dealer", small = true, modifier = Modifier.padding(top = 6.dp)) {
                        e.city.placeBuilding("motors")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") }
                    }
                }
                for (v in vans) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleThumb(v.defId, 34.dp)
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(v.def.name, style = HT.bodyBold, color = HC.Ink, maxLines = 1)
                            Text("${v.def.cargo} boxes · " + if (ops.away(v)) "out until ${clockOf(v.awayUntil)}" else "parked", style = HT.small,
                                color = if (ops.away(v)) HC.Orange else HC.InkSoft)
                        }
                        HButton("Route", small = true, color = HC.Green) { ops.newRoute(v) }
                    }
                }
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Drivers")
                val drivers = ops.drivers()
                if (drivers.isEmpty()) Text("Hire Delivery Drivers at Apex Recruitment (needs Basic HR).", style = HT.small, color = HC.InkSoft)
                for (d in drivers) {
                    val route = s.fleet.firstOrNull { it.driverId == d.id }
                    Column(Modifier.padding(vertical = 3.dp)) {
                        Text("${d.name} · ${e.money(d.wage)}/h", style = HT.bodyBold, color = HC.Ink)
                        Text(if (route != null) "Drives ${ops.routeName(route)}" else "No route yet", style = HT.small, color = if (route != null) HC.InkSoft else HC.Orange, maxLines = 2)
                    }
                }
                HButton("Pin recruiter", small = true, modifier = Modifier.padding(top = 6.dp)) {
                    e.city.placeBuilding("recruit")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") }
                }
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Reorder when low")
                val stores = s.businesses.filter { !it.type.facility }
                if (stores.isEmpty()) Text("No stores yet.", style = HT.small, color = HC.InkSoft)
                for (b in stores) {
                    val c = b.delivery
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = HT.bodyBold, color = HC.Ink, maxLines = 1)
                            Text(when {
                                c == null || !c.enabled -> "No delivery contract"
                                c.byManager -> "Run by the store manager"
                                c.auto -> "Reorders below threshold"
                                else -> "Daily delivery only"
                            }, style = HT.small, color = HC.InkSoft)
                        }
                        if (c != null && c.enabled) Switch(c.auto, { on -> c.auto = on; ops.defaultMinLevels(c); e.bump() },
                            colors = SwitchDefaults.colors(checkedTrackColor = HC.Green))
                    }
                }
                Text("Set thresholds per product in BizHub → Deliveries.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.fleet.isEmpty()) WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Delivery routes")
                EmptyNote("No routes yet. Tap ROUTE next to a van to plan one: where it loads (a wholesaler or your warehouse), which stores it visits, and when. A full van pays wholesale price, 5% off from 10 boxes.")
            }
            for (r in s.fleet.toList()) RouteCard(e, r)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteCard(e: GameEngine, r: FleetRoute) {
    val s = e.state
    val ops = e.ops
    WhiteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon("van", 18.dp, Modifier.padding(end = 6.dp), tint = HC.Ink)
            Text(ops.routeName(r), style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f), maxLines = 2)
            Switch(r.enabled, { r.enabled = it; e.bump() }, colors = SwitchDefaults.colors(checkedTrackColor = HC.Green))
        }
        Text("VAN", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
        FlowRow { for (v in ops.vans()) Chip("${v.def.name} (${v.def.cargo})", v.id == r.vehicleId, HC.Blue) { r.vehicleId = v.id; e.bump() } }
        Text("DRIVER", style = HT.label, color = HC.InkSoft)
        FlowRow {
            Chip("None", r.driverId < 0, HC.Red) { ops.assignDriver(r, null) }
            for (d in ops.drivers()) Chip(d.name, d.id == r.driverId, HC.Purple) { ops.assignDriver(r, d) }
        }
        Text("LOADS AT", style = HT.label, color = HC.InkSoft)
        FlowRow {
            for ((id, _, label) in Operations.SOURCES) {
                if (id == "import" && Catalog.COURSE_IMPORT !in s.player.courses) continue
                Chip(label, r.source == id, HC.Teal) { r.source = id; e.bump() }
            }
            for (w in s.businesses.filter { BizCalc.isWarehouse(it) }) Chip(w.name, r.source == "wh:${w.id}", HC.Teal) { r.source = "wh:${w.id}"; e.bump() }
        }
        Text("STOPS, IN ORDER", style = HT.label, color = HC.InkSoft)
        FlowRow {
            for (b in s.businesses.filter { !it.type.facility }) {
                val idx = r.stops.indexOf(b.id)
                Chip(if (idx >= 0) "${idx + 1}. ${b.name}" else b.name, idx >= 0, HC.Orange) {
                    if (idx >= 0) r.stops.removeAt(idx) else r.stops += b.id
                    e.bump()
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Leaves at", style = HT.body, color = HC.Ink, modifier = Modifier.width(70.dp))
            Stepper("%02d:00".format(r.hour), { r.hour = (r.hour + 23) % 24; e.bump() }, { r.hour = (r.hour + 1) % 24; e.bump() })
            Spacer(Modifier.width(10.dp))
            for (d in 0 until 7) {
                val on = (r.days shr d) and 1 == 1
                Box(
                    Modifier.padding(1.dp).size(22.dp).clip(RoundedCornerShape(4.dp)).background(if (on) HC.Blue else Color(0xFFE3E8EE))
                        .clickable { r.days = r.days xor (1 shl d); e.bump() },
                    contentAlignment = Alignment.Center,
                ) { Text(DAY_LETTERS[d], color = if (on) Color.White else HC.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (r.lastRun.isNotEmpty()) Text("Last run: ${r.lastRun}", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 4.dp))
        val onBoard = s.fleetDrops.filter { it.routeId == r.id }
        if (onBoard.isNotEmpty()) Text("On the way: ${onBoard.sumOf { it.qty }} units, next drop at ${clockOf(onBoard.minOf { it.minute })}", style = HT.small, color = HC.Orange)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton("Run now", small = true, color = HC.Green) { ops.runRoute(r, manual = true) }
            HButton("Delete", small = true, color = HC.Red) {
                e.ui.confirm = Confirm("Delete this route?", ops.routeName(r), "Delete") { s.fleet.remove(r); e.bump() }
            }
        }
    }
}

// ------------------------------------------------------------------ markets

/** Shares in the city's firms and your rivals, and city bonds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketsApp(e: GameEngine) {
    val s = e.state
    val ops = e.ops
    LaunchedEffect(Unit) { ops.ensureMarket(); e.bump() }
    var bondDays by remember { mutableIntStateOf(90) }
    var lot by remember { mutableStateOf(100) }
    Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(250.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Portfolio")
                val value = ops.portfolioValue()
                val basis = s.costBasis.values.sum()
                KeyValue("Shares", e.money(value))
                KeyValue("Paid", e.money(basis), bold = false)
                KeyValue("Gain", (if (value >= basis) "+" else "") + e.money(value - basis), if (value >= basis) HC.Green else HC.Red)
                KeyValue("City bonds", e.money(ops.bondsValue()))
                Text("Prices move once a day. Each trade costs ${(Operations.BROKERAGE * 100).toString().trimEnd('0').trimEnd('.')}% brokerage. Rival shares follow their net worth: own more than half and you can take them over.",
                    style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("City bonds")
                FlowRow { for ((d, rate) in Operations.BOND_TERMS) Chip("$d days · ${"%.1f".format(rate * 100)}%", bondDays == d, HC.Teal) { bondDays = d } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (amt in listOf(1_000.0, 10_000.0, 100_000.0)) HButton("Buy ${e.moneyShort(amt)}", small = true, color = HC.Green, modifier = Modifier.padding(bottom = 6.dp)) { ops.buyBond(amt, bondDays) }
                }
                for (b in s.bonds) KeyValue("${e.moneyShort(b.principal)} · ${"%.1f".format(b.rate * 100)}%", "day ${b.matures}", bold = false)
                Text("Safe money: paid back with interest when they mature. They count toward your net worth meanwhile.", style = HT.small, color = HC.InkSoft)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trade in lots of", style = HT.small, color = Color.White, modifier = Modifier.padding(end = 6.dp))
                for (n in listOf(10, 100, 1000, 5000)) Chip("$n", lot == n, HC.Blue) { lot = n }
            }
            for (t in s.market.toList().sortedBy { if (it.id.startsWith("R")) 1 else 0 }) TickerRow(e, t, lot)
        }
    }
}

@Composable
private fun TickerRow(e: GameEngine, t: Ticker, lot: Int) {
    val s = e.state
    val ops = e.ops
    val prev = t.history.getOrNull(t.history.size - 2) ?: t.price
    val change = if (prev > 0) (t.price - prev) / prev else 0.0
    val have = s.holdings[t.id] ?: 0
    val rival = if (t.id.startsWith("R")) s.rivals.firstOrNull { "R${it.id}" == t.id } else null
    WhiteCard(Modifier.fillMaxWidth(), padding = 9.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                Text(t.name, style = HT.bodyBold, color = HC.Ink, maxLines = 1)
                Text(if (rival != null) "Rival · ${(ops.stake(rival) * 100).roundToInt()}% yours" else t.id, style = HT.small, color = if (rival != null) HC.Purple else HC.InkSoft)
            }
            Spark(t.history, if (change >= 0) HC.Green else HC.Red, Modifier.width(90.dp).height(30.dp))
            Column(Modifier.width(86.dp).padding(start = 8.dp)) {
                Text(e.money(t.price), style = HT.bodyBold, color = HC.Ink)
                Text((if (change >= 0) "+" else "") + "%.1f%%".format(change * 100), style = HT.small, color = if (change >= 0) HC.Green else HC.Red)
            }
            Column(Modifier.weight(1f)) {
                if (have > 0) Text("$have owned · ${e.moneyShort(have * t.price)}", style = HT.small, color = HC.Ink)
            }
            HButton("Buy", small = true, color = HC.Green) { ops.buyShares(t, lot) }
            Spacer(Modifier.width(4.dp))
            HButton("Sell", small = true, color = HC.Red, enabled = have > 0) { ops.sellShares(t, lot) }
            if (rival != null && ops.stake(rival) > .5) {
                Spacer(Modifier.width(4.dp))
                HButton("Take over", small = true, color = HC.Purple) {
                    e.ui.confirm = Confirm("Take over ${rival.name}?", "Its ${e.moneyShort(rival.cash)} cash and ${s.rivalBusinesses.count { it.rivalId == rival.id }} stores become yours.", "Take over") { ops.takeover(rival) }
                }
            }
        }
    }
}

/** A small line chart of recent prices. */
@Composable
private fun Spark(values: List<Double>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max().coerceAtLeast(lo + 1e-6)
        val path = Path()
        for ((i, v) in values.withIndex()) {
            val x = i * size.width / (values.size - 1)
            val y = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Color(0xFFE3E7EC), Offset(0f, size.height), Offset(size.width, size.height), 1f)
        drawPath(path, color, style = Stroke(2.5f))
    }
}

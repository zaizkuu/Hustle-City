package com.hustlecity.game.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.Source
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.Confirm
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Objectives
import com.hustlecity.game.engine.cancelLease
import com.hustlecity.game.engine.closeBusiness
import com.hustlecity.game.engine.setDelivery
import com.hustlecity.game.engine.setOpen
import com.hustlecity.game.engine.setPrice
import com.hustlecity.game.model.Business
import com.hustlecity.game.model.DeliveryContract
import com.hustlecity.game.ui.Bar
import com.hustlecity.game.ui.CardTitle
import com.hustlecity.game.ui.Chip
import com.hustlecity.game.ui.Dot
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.KeyValue
import com.hustlecity.game.ui.LabeledBar
import com.hustlecity.game.ui.ProductThumb
import com.hustlecity.game.ui.Stepper
import com.hustlecity.game.ui.WhiteCard
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT
import com.hustlecity.game.ui.toColor
import com.hustlecity.game.world.City
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun BizHubApp(e: GameEngine) {
    val s = e.state
    if (s.businesses.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WhiteCard(Modifier.width(420.dp)) {
                Text("No businesses yet", style = HT.title, color = HC.Ink)
                Text("Rent a retail building in the Realty app, go inside and pick a business type. Your stores will show up here.", style = HT.body, color = HC.InkSoft, modifier = Modifier.padding(vertical = 10.dp))
                HButton("Open Realty", color = HC.Orange) { e.ui.app = AppId.REALTY }
            }
        }
        return
    }
    val biz = s.business(e.ui.bizHubSel) ?: s.businesses.first().also { e.ui.bizHubSel = it.id }
    val building = e.city.building(biz.buildingId)!!
    Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // left column
        WhiteCard(Modifier.width(220.dp).fillMaxHeight()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(58.dp).clip(CircleShape).background(biz.type.color.toColor()), contentAlignment = Alignment.Center) {
                    Text(biz.type.emoji, fontSize = 28.sp)
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val idx = s.businesses.indexOf(biz)
                    Icon(Icons.Filled.ChevronLeft, null, tint = HC.Ink, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).background(HC.CardAlt).clickable {
                        e.ui.bizHubSel = s.businesses[(idx - 1 + s.businesses.size) % s.businesses.size].id
                    })
                    Text(biz.name, style = HT.bodyBold, color = HC.Ink, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = HC.Ink, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).background(HC.CardAlt).clickable {
                        e.ui.bizHubSel = s.businesses[(idx + 1) % s.businesses.size].id
                    })
                }
                Text(biz.type.name.uppercase(), style = HT.label, color = HC.InkSoft, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                if (!biz.type.facility) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(if (biz.tempClosed) "TEMPORARILY CLOSED" else "OPEN", style = HT.label, color = if (biz.tempClosed) HC.Red else HC.Green, modifier = Modifier.weight(1f))
                    Switch(!biz.tempClosed, { e.setOpen(biz, it) }, colors = SwitchDefaults.colors(checkedTrackColor = HC.Green))
                }
                Text("REQUIREMENTS", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                for ((req, ok) in BizCalc.requirements(biz)) {
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ok) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null, tint = if (ok) HC.Green else HC.InkSoft, modifier = Modifier.size(16.dp))
                        Text(" Requires at least one ${req.label}.", style = HT.small, color = if (ok) HC.InkSoft else HC.Ink)
                    }
                }
                val alerts = Objectives.alerts(e).filter { it.businessId == biz.id }
                if (alerts.isNotEmpty()) {
                    Text("ALERTS", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    for (a in alerts) Text("• " + a.text.removePrefix("${biz.name}: "), style = HT.small, color = if (a.severe) HC.Red else HC.Ink, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        // right side
        Column(Modifier.weight(1f)) {
            val tabs = if (biz.type.facility) listOf(building.address, if (BizCalc.isWarehouse(biz)) "Storage" else "Team", "Schedule", "Settings")
            else listOf(building.address, "Insight", "Inventory & Pricing", "Schedule", "Deliveries", "Marketing", "Settings")
            if (e.ui.bizHubTab >= tabs.size) e.ui.bizHubTab = 0
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                for ((i, t) in tabs.withIndex()) {
                    val sel = e.ui.bizHubTab == i
                    Column(Modifier.clickable { e.ui.bizHubTab = i }.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t, color = if (sel) Color.White else Color.White.copy(alpha = .6f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.padding(top = 3.dp).height(2.dp).width(40.dp).background(if (sel) Color.White else Color.Transparent))
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(top = 6.dp)) {
                if (biz.type.facility) when (e.ui.bizHubTab) {
                    0 -> OverviewTab(e, biz)
                    1 -> if (BizCalc.isWarehouse(biz)) StorageTab(e, biz) else TeamTab(e, biz)
                    2 -> ScheduleTab(e, biz)
                    else -> SettingsTab(e, biz)
                } else when (e.ui.bizHubTab) {
                    0 -> OverviewTab(e, biz)
                    1 -> InsightTab(e, biz)
                    2 -> InventoryTab(e, biz)
                    3 -> ScheduleTab(e, biz)
                    4 -> DeliveriesTab(e, biz)
                    5 -> MarketingTab(e, biz)
                    else -> SettingsTab(e, biz)
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(e: GameEngine, biz: Business) {
    val b = e.city.building(biz.buildingId)!!
    val rental = e.state.rentalFor(b.id)
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WhiteCard(Modifier.weight(1f)) {
            CardTitle("Building")
            KeyValue("Address", b.address)
            KeyValue("Neighborhood", City.neighborhoods[b.neighborhood].name)
            KeyValue("Size", "${b.area} m² (${b.sizeClass})")
            KeyValue("Traffic index", "${(b.traffic * 100).roundToInt()}%")
            KeyValue("Customer limit", "${b.customerLimit} per hour")
            KeyValue("Parking spots", "${b.parking}")
            KeyValue(if (rental?.owned == true) "Ownership" else "Rent", if (rental?.owned == true) "Owned" else "${e.money(rental?.rentPerMonth ?: 0.0)}/month")
            KeyValue("Competitors in area", "${BizCalc.competitors(e, biz)}")
            HButton("Set waypoint", small = true, modifier = Modifier.padding(top = 6.dp)) { e.setWaypoint(b.id) }
        }
        WhiteCard(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)).background(biz.type.color.toColor()), contentAlignment = Alignment.Center) { Text(biz.type.emoji, fontSize = 22.sp) }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(biz.type.name.uppercase(), style = HT.label, color = HC.InkSoft)
                    Text(biz.name, style = HT.title, color = HC.Ink)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Opening hours", style = HT.bodyBold, color = HC.Ink)
            for (d in com.hustlecity.game.model.DAY_NAMES) KeyValue(d, "%02d:00 - %02d:00".format(biz.openHour, biz.closeHour), bold = false)
            KeyValue("Open since", "Day ${biz.openedDay}")
        }
    }
}

@Composable
private fun InsightTab(e: GameEngine, biz: Business) {
    val b = e.city.building(biz.buildingId)!!
    val s = e.state
    var sevenDays by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Promotion", Icons.Filled.Campaign)
                val promo = BizCalc.promotion(biz, b).toFloat()
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(HC.Blue).padding(8.dp)) {
                    Bar(promo, Color.White, Modifier.fillMaxWidth(.85f).align(Alignment.CenterStart), track = Color(0x55FFFFFF), height = 8.dp)
                    Text("${(promo * 100).roundToInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterEnd))
                }
                Spacer(Modifier.height(6.dp))
                LabeledBar("Traffic index", b.traffic, HC.Orange)
                LabeledBar("Marketing", biz.marketing.toFloat(), HC.Purple)
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Satisfaction", Icons.Filled.EmojiEmotions)
                if (biz.lastSatisfaction < 0 || biz.lastFactors.size < 6) {
                    Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFC9CED5)), contentAlignment = Alignment.Center) {
                        Text("NOT ENOUGH DATA", style = HT.label, color = HC.Ink)
                    }
                } else {
                    LabeledBar("Overall", biz.lastSatisfaction.toFloat(), HC.Green)
                    val names = listOf("Price", "Availability", "Wait time", "Cleanliness", "Interior", "Staff")
                    for (i in names.indices) LabeledBar(names[i], biz.lastFactors[i].toFloat(), if (biz.lastFactors[i] < .5) HC.Red else HC.Blue)
                }
                LabeledBar("Reputation", biz.reputation.toFloat(), HC.Teal)
                LabeledBar("Cleanliness now", (biz.cleanliness / 100).toFloat(), if (biz.cleanliness < 40) HC.Red else HC.Green)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Customer capacity", Icons.Filled.Groups)
                val workers = BizCalc.workers(e, biz, s.hour, s.weekday)
                val cap = BizCalc.capacity(biz, b, workers.ifEmpty { listOf(.5) })
                Row {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Capacity per hour", style = HT.small, color = HC.InkSoft)
                        Text("${if (workers.isEmpty()) 0 else cap}", style = HT.big, color = if (workers.isEmpty()) HC.Red else HC.Ink)
                        Text(if (workers.isEmpty()) "nobody working now" else "${workers.size} working now", style = HT.small, color = HC.InkSoft)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Building limit", style = HT.small, color = HC.InkSoft)
                        Text("${b.customerLimit}", style = HT.big, color = HC.Ink)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Demand now", style = HT.small, color = HC.InkSoft)
                        Text("${BizCalc.demand(e, biz, s.hour).roundToInt()}", style = HT.big, color = HC.Ink)
                    }
                }
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle("Customers over time", Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f))
                    Chip("Today", !sevenDays) { sevenDays = false }
                    Chip("7 days", sevenDays) { sevenDays = true }
                }
                val values: List<Int> = if (sevenDays) {
                    val h = biz.history.takeLast(7).map { it.customers }
                    if (h.isEmpty()) listOf(0, 0) else h + biz.customersToday
                } else biz.hourlyToday.toList().mapIndexed { i, v -> if (i <= s.hour) v else (biz.hourlyYesterday[i]) }
                LineChart(values.map { it.toFloat() }, Modifier.fillMaxWidth().height(110.dp), split = if (sevenDays) -1 else s.hour)
                Text(if (sevenDays) "Customers per day (last point = today so far)" else "Customers per hour — solid: today, faded: yesterday", style = HT.small, color = HC.InkSoft)
                Spacer(Modifier.height(4.dp))
                KeyValue("Customers today", "${biz.customersToday}")
                KeyValue("Lost customers today", "${biz.lostToday}", HC.Red)
                KeyValue("Sales today", e.money(biz.revenueToday), HC.Green)
                // weather and security
                val sky = e.weather.sky()
                val wf = e.weather.demandFactor(biz.type)
                KeyValue("Weather now", "${sky.icon} ${sky.label}, ${e.weather.temperature().toInt()}°C · " + (if (wf >= 1.005) "+${((wf - 1) * 100).toInt()}%" else if (wf <= .995) "−${((1 - wf) * 100).toInt()}%" else "±0%") + " customers",
                    if (wf < .95) HC.Red else if (wf > 1.05) HC.Green else HC.Ink)
                if (biz.type.theftRisk > 0) {
                    val stolen = biz.history.lastOrNull()?.stolen ?: 0.0
                    KeyValue("Shoplifting yesterday", e.money(stolen) + " (security ${(BizCalc.security(biz) * 100).toInt()}%)", if (stolen > 0) HC.Red else HC.Ink)
                }
            }
        }
    }
}

@Composable
fun LineChart(values: List<Float>, modifier: Modifier, split: Int = -1) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mx = max(values.max(), 4f)
        val stepX = size.width / (values.size - 1)
        for (g in 0..4) {
            val y = size.height - g / 4f * size.height
            drawLine(Color(0xFFE3E7EC), Offset(0f, y), Offset(size.width, y), 1f)
        }
        val path = Path(); val faded = Path()
        for ((i, v) in values.withIndex()) {
            val x = i * stepX
            val y = size.height - v / mx * (size.height - 6)
            if (split < 0 || i <= split) { if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            if (split >= 0 && i >= split) { if (i == split) faded.moveTo(x, y) else faded.lineTo(x, y) }
        }
        drawPath(path, Color(0xFF2F80ED), style = Stroke(3f))
        if (split >= 0) drawPath(faded, Color(0x662F80ED), style = Stroke(2f))
        for ((i, v) in values.withIndex()) {
            if (split >= 0 && i > split) continue
            drawCircle(Color(0xFF2F80ED), 4f, Offset(i * stepX, size.height - v / mx * (size.height - 6)))
        }
    }
}

@Composable
private fun InventoryTab(e: GameEngine, biz: Business) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((pid, _) in biz.type.products) {
            val p = Catalog.product(pid) ?: continue
            val stock = biz.stock[pid] ?: 0
            val cap = BizCalc.capacityFor(biz, pid)
            val price = biz.prices[pid] ?: p.price
            val market = BizCalc.marketPrice(e, biz, pid)
            val prob = BizCalc.buyProbability(price / market)
            WhiteCard(Modifier.fillMaxWidth(), padding = 9.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductThumb(p, 40.dp)
                    Column(Modifier.weight(1.3f).padding(start = 8.dp)) {
                        Text(p.name, style = HT.bodyBold, color = HC.Ink)
                        if (p.service) Text("Service · needs ${Catalog.item(com.hustlecity.game.engine.itemForTag(p.tag))?.name ?: p.tag}", style = HT.small, color = HC.InkSoft)
                        else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.source.label, style = HT.small, color = HC.InkSoft)
                                Icon(Icons.Filled.Place, null, tint = HC.Blue, modifier = Modifier.size(16.dp).clickable {
                                    e.city.placeBuilding(Catalog.sourcePlaceId(p.source))?.let { e.state.player.waypoint = it.id; e.toast("Waypoint: ${p.source.label}") }
                                })
                            }
                        }
                    }
                    Column(Modifier.weight(1.2f)) {
                        if (p.service) Text(if (BizCalc.count(biz, p.tag) > 0) "Available" else "Missing equipment", style = HT.small, color = if (BizCalc.count(biz, p.tag) > 0) HC.Green else HC.Red)
                        else if (cap == 0) Text("No display: needs ${Catalog.item(com.hustlecity.game.engine.itemForTag(p.tag))?.name}", style = HT.small, color = HC.Red)
                        else {
                            val shared = biz.type.products.count { (other, _) -> Catalog.product(other)?.tag == p.tag } > 1
                            val free = BizCalc.roomFor(biz, pid)
                            Text("Stock $stock · ${free} free" + if (shared) " (shared)" else " / $cap", style = HT.small, color = if (stock == 0) HC.Red else HC.Ink)
                            Bar(stock / cap.toFloat(), if (stock < cap * .2f) HC.Orange else HC.Green, Modifier.fillMaxWidth().padding(end = 10.dp), track = HC.Line, height = 7.dp)
                        }
                        Text("Sold today: ${biz.soldToday[pid] ?: 0}", style = HT.small, color = HC.InkSoft)
                    }
                    Column(Modifier.weight(.9f)) {
                        if (!p.service) Text("Cost ${e.money(biz.unitCost[pid] ?: p.cost)}", style = HT.small, color = HC.InkSoft)
                        Text("Market ${e.money(market)}", style = HT.small, color = HC.InkSoft)
                        Text("Buy chance ${(prob * 100).roundToInt()}%", style = HT.small, color = if (prob < .5) HC.Red else HC.Green, fontWeight = FontWeight.Bold)
                    }
                    Stepper(e.money(price), onMinus = { e.setPrice(biz, pid, max(.1, price - priceStep(price))) }, onPlus = { e.setPrice(biz, pid, price + priceStep(price)) })
                }
            }
        }
        Text("Tip: price near the market price keeps customers happy. Each product needs a matching display (shelf, fridge, machine…) to be stocked.", style = HT.small, color = Color.White)
    }
}

private fun priceStep(price: Double) = when { price < 5 -> .1; price < 20 -> .5; price < 100 -> 1.0; else -> 10.0 }

@Composable
private fun ScheduleTab(e: GameEngine, biz: Business) {
    val s = e.state
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!biz.type.facility) WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Opening hours (every day)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opens", style = HT.body, color = HC.Ink, modifier = Modifier.width(60.dp))
                    Stepper("%02d:00".format(biz.openHour), { biz.openHour = (biz.openHour + 23) % 24; e.bump() }, { biz.openHour = (biz.openHour + 1) % 24; e.bump() })
                    Spacer(Modifier.width(20.dp))
                    Text("Closes", style = HT.body, color = HC.Ink, modifier = Modifier.width(60.dp))
                    Stepper("%02d:00".format(biz.closeHour), { biz.closeHour = (biz.closeHour + 23) % 24; e.bump() }, { biz.closeHour = (biz.closeHour + 1) % 24; e.bump() })
                }
                Text("Staff coverage today (${biz.type.staffRole.label})", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth()) {
                    for (h in 0 until 24) {
                        val open = BizCalc.isWithinHours(biz, h)
                        val n = s.employees.count { it.businessId == biz.id && it.role == biz.type.staffRole && it.worksAt(h, s.weekday) }
                        val col = when { !open -> Color(0xFFE3E7EC); n == 0 -> HC.Red; n == 1 -> HC.GreenLight; else -> HC.Green }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.padding(horizontal = 1.dp).fillMaxWidth().height(18.dp).background(col))
                            if (h % 3 == 0) Text("$h", fontSize = 8.sp, color = HC.InkSoft)
                        }
                    }
                }
                biz.type.performer?.let { perf ->
                    Text("${perf.label} coverage (required to serve customers)", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    Row(Modifier.fillMaxWidth()) {
                        for (h in 0 until 24) {
                            val open = BizCalc.isWithinHours(biz, h)
                            val n = s.employees.count { it.businessId == biz.id && it.role == perf && it.worksAt(h, s.weekday) }
                            val col = when { !open -> Color(0xFFE3E7EC); n == 0 -> HC.Red; else -> HC.Purple }
                            Box(Modifier.weight(1f).padding(horizontal = 1.dp).height(12.dp).background(col))
                        }
                    }
                }
                Text("Red = open but nobody scheduled (you can work these hours yourself).", style = HT.small, color = HC.InkSoft)
            }
            val assigned = s.employees.filter { it.businessId == biz.id }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Scheduled employees")
                if (assigned.isEmpty()) Text("Nobody assigned yet.", style = HT.small, color = HC.InkSoft)
                for (emp in assigned) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${emp.name} · ${emp.role.label}", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                            Text("${emp.weeklyHours}h/week", style = HT.small, color = if (emp.weeklyHours > 50) HC.Red else HC.InkSoft)
                            Spacer(Modifier.width(8.dp))
                            HButton("Unassign", small = true, color = HC.Red) { emp.businessId = -1; e.bump() }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Stepper("%02d:00".format(emp.shiftStart), { emp.shiftStart = (emp.shiftStart + 23) % 24; e.bump() }, { emp.shiftStart = (emp.shiftStart + 1) % 24; e.bump() })
                            Text(" → ", color = HC.Ink)
                            Stepper("%02d:00".format(emp.shiftEnd), { emp.shiftEnd = (emp.shiftEnd + 23) % 24; e.bump() }, { emp.shiftEnd = (emp.shiftEnd + 1) % 24; e.bump() })
                            Spacer(Modifier.width(10.dp))
                            for (d in 0 until 7) {
                                val on = (emp.days shr d) and 1 == 1
                                Box(
                                    Modifier.padding(1.dp).size(22.dp).clip(RoundedCornerShape(4.dp)).background(if (on) HC.Blue else Color(0xFFE3E8EE))
                                        .clickable { emp.days = emp.days xor (1 shl d); e.bump() },
                                    contentAlignment = Alignment.Center,
                                ) { Text(listOf("M", "T", "W", "T", "F", "S", "S")[d], color = if (on) Color.White else HC.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
        WhiteCard(Modifier.weight(1f)) {
            CardTitle("Assign employees")
            val free = s.employees.filter {
                it.businessId != biz.id && when {
                    BizCalc.isHq(biz) -> it.role.atHeadquarters || it.role == StaffRole.CLEANER
                    biz.type.facility -> it.role == StaffRole.CLEANER
                    else -> it.role == biz.type.staffRole || it.role == biz.type.performer || it.role == StaffRole.CLEANER
                }
            }
            if (free.isEmpty()) {
                Text("No available ${biz.type.staffRole.label}${biz.type.performer?.let { ", ${it.label}" } ?: ""} or Cleaner employees. Hire them at Apex Recruitment (needs the Basic HR course).", style = HT.small, color = HC.InkSoft)
                HButton("Pin recruiter", small = true, modifier = Modifier.padding(top = 6.dp)) { e.city.placeBuilding("recruit")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") } }
            }
            for (emp in free) {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(emp.name, style = HT.bodyBold, color = HC.Ink)
                        Text("${emp.role.label} · skill ${(emp.skill * 100).roundToInt()}% · ${e.money(emp.wage)}/h" + (s.business(emp.businessId)?.let { " · at ${it.name}" } ?: ""), style = HT.small, color = HC.InkSoft)
                    }
                    HButton("Assign", small = true, color = HC.Green) {
                        emp.businessId = biz.id
                        emp.shiftStart = biz.openHour
                        emp.shiftEnd = if (biz.closeHour - biz.openHour > 9) biz.openHour + 8 else biz.closeHour
                        e.toast("${emp.name} scheduled at ${biz.name}")
                        e.bump()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveriesTab(e: GameEngine, biz: Business) {
    val hasCourse = Catalog.COURSE_LOGISTICS in e.state.player.courses
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!hasCourse) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Scheduled deliveries")
                Text("Complete the Logistics Management course at Hustle Business School to have wholesalers restock this store automatically every day. Until then, drive to a wholesaler and bring the boxes yourself (or pay for a one-time delivery at checkout).",
                    style = HT.body, color = HC.InkSoft)
                HButton("Pin business school", small = true, modifier = Modifier.padding(top = 8.dp)) { e.city.placeBuilding("school")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") } }
            }
            return@Column
        }
        val contract = biz.delivery
        WhiteCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Daily restock contract", modifier = Modifier.weight(1f))
                Switch(contract?.enabled == true, { on ->
                    val c = contract ?: DeliveryContract().also { dc -> biz.type.products.forEach { (pid, _) -> if (Catalog.product(pid)?.service == false) dc.targets[pid] = BizCalc.capacityFor(biz, pid) } }
                    c.enabled = on
                    e.setDelivery(biz, c)
                }, colors = SwitchDefaults.colors(checkedTrackColor = HC.Green))
            }
            if (contract != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Delivery time", style = HT.body, color = HC.Ink, modifier = Modifier.width(110.dp))
                    Stepper("%02d:00".format(contract.hour), { contract.hour = (contract.hour + 23) % 24; e.bump() }, { contract.hour = (contract.hour + 1) % 24; e.bump() })
                }
                var est = 0.0
                for ((pid, target) in contract.targets) {
                    val p = Catalog.product(pid) ?: continue
                    val cap = BizCalc.capacityFor(biz, pid)
                    est += (target - (biz.stock[pid] ?: 0)).coerceAtLeast(0) * p.cost * 1.12
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(p, 30.dp)
                        Text(" ${p.name}", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                        Text("stock ${biz.stock[pid] ?: 0} / cap $cap   restock to ", style = HT.small, color = HC.InkSoft)
                        Stepper("$target", { contract.targets[pid] = (target - 10).coerceAtLeast(0); e.bump() }, { contract.targets[pid] = (target + 10).coerceAtMost(max(cap, 10)); e.bump() })
                    }
                }
                Text("Estimated cost right now: ${e.money(est)} + \$60 delivery fee. Wholesale price +12%.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            } else Text("Turn on to set up daily deliveries.", style = HT.small, color = HC.InkSoft)
        }
    }
}

@Composable
private fun MarketingTab(e: GameEngine, biz: Business) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("Marketing", Icons.Filled.Campaign)
            LabeledBar("Marketing", biz.marketing.toFloat(), HC.Purple)
            if (biz.campaigns.isEmpty()) Text("No active campaigns. Marketing fades without campaigns.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(vertical = 6.dp))
            for (c in biz.campaigns) {
                val def = Catalog.campaign(c.campaignId)
                KeyValue(def.name, "${c.daysLeft} days left · ${e.money(def.pricePerDay)}/day", bold = false)
            }
            Text("Start new campaigns at Buzz Marketing Agency in Midtown.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            HButton("Pin Buzz Marketing", small = true, modifier = Modifier.padding(top = 6.dp)) { e.city.placeBuilding("buzz")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") } }
        }
    }
}

@Composable
private fun SettingsTab(e: GameEngine, biz: Business) {
    var name by remember(biz.id) { mutableStateOf(biz.name) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("Business name")
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(name, { name = it.take(28) }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = HC.Ink),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(HC.CardAlt).padding(10.dp))
                Spacer(Modifier.width(8.dp))
                HButton("Rename", small = true) { if (name.isNotBlank()) { biz.name = name.trim(); e.bump() } }
            }
        }
        WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("Close business")
            Text("Sells all furniture at 50% and remaining stock at 30%. Employees become unassigned. The lease stays active until you end it in Realty.", style = HT.small, color = HC.InkSoft)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HButton("Close business", color = HC.Red, small = true) {
                    e.ui.confirm = Confirm("Close ${biz.name}?", "This can't be undone.", "Close") { e.closeBusiness(biz) }
                }
                HButton("Close & end lease", color = HC.RedDark, small = true) {
                    e.ui.confirm = Confirm("Close ${biz.name} and end the lease?", "This can't be undone.", "Close") {
                        val bid = biz.buildingId
                        e.closeBusiness(biz)
                        if (e.state.player.inBuilding == bid) e.exitBuilding()
                        e.cancelLease(bid)
                    }
                }
            }
        }
        Row { Dot(HC.Green); Text("  Sources: ${biz.type.sources.joinToString { it.label }.ifEmpty { "services only" }}", style = HT.small, color = Color.White) }
        if (biz.type.sources.contains(Source.IMPORT)) Text("Imported goods come from Global Imports Co. in Industry City.", style = HT.small, color = Color.White)
    }
}

@Composable
private fun StorageTab(e: GameEngine, biz: Business) {
    val used = BizCalc.warehouseBoxesUsed(biz)
    val cap = BizCalc.warehouseBoxes(biz)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("Storage")
            LabeledBar("Boxes", if (cap == 0) 0f else used / cap.toFloat(), if (used >= cap) HC.Red else HC.Blue, "$used / $cap")
            KeyValue("Stock value", e.money(BizCalc.stockValue(biz)))
            Text("Delivery contracts and purchasing agents take stock from here first, at no markup. Order 6+ boxes at a wholesaler for 10% off, then deliver here or unload your vehicle inside.",
                style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
        }
        val items = biz.stock.filter { it.value > 0 }.toList().sortedByDescending { it.second }
        if (items.isNotEmpty()) WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("Products")
            for ((pid, qty) in items) {
                val p = Catalog.product(pid) ?: continue
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductThumb(p, 30.dp)
                    Text(" ${p.name}", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                    Text("$qty units · ${(qty + p.unitsPerBox - 1) / p.unitsPerBox} boxes", style = HT.small, color = HC.InkSoft)
                }
            }
        }
    }
}

@Composable
private fun TeamTab(e: GameEngine, biz: Business) {
    val s = e.state
    val desks = BizCalc.count(biz, "desk")
    val hr = BizCalc.managers(e, StaffRole.HR_MANAGER)
    val agents = BizCalc.managers(e, StaffRole.PURCHASING_AGENT)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("Management team")
            KeyValue("Office desks", "$desks")
            KeyValue("HR Managers working", "$hr")
            KeyValue("Purchasing Agents working", "$agents")
            Text("Each manager needs a desk here. Hire them at Apex Recruitment, then assign them in the Schedule tab.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
        }
        WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("HR")
            Text(
                if (hr == 0) "No HR Manager yet. HR raises staff satisfaction and replaces anyone who quits with the same schedule."
                else "Covering ${minOf(s.employees.size, hr * 12)} of ${s.employees.size} employees (12 each). Satisfaction +10%" +
                    (if (s.employees.size > hr * 12) " — needs another HR Manager to apply." else ".") + " Quitting staff are replaced automatically.",
                style = HT.body, color = HC.Ink,
            )
        }
        WhiteCard(Modifier.fillMaxWidth()) {
            CardTitle("Purchasing")
            val stores = s.businesses.filter { !it.type.facility && it.delivery?.enabled != true }
            Text(
                if (agents == 0) "No Purchasing Agent yet. Agents restock your stores every morning at 06:00, from your warehouses first, then wholesale at +8%."
                else "Restocking ${minOf(stores.size, agents * 4)} of ${stores.size} stores every morning at 06:00 (4 per agent). Stores with their own delivery contract are skipped.",
                style = HT.body, color = HC.Ink,
            )
            for (b in stores.take(agents * 4)) Text("• ${b.type.emoji} ${b.name}", style = HT.small, color = HC.InkSoft)
        }
    }
}

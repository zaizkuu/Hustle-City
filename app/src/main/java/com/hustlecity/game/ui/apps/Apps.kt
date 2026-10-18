package com.hustlecity.game.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.Confirm
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.buyBuilding
import com.hustlecity.game.engine.cancelLease
import com.hustlecity.game.engine.fire
import com.hustlecity.game.engine.hire
import com.hustlecity.game.engine.raise
import com.hustlecity.game.engine.rentBuilding
import com.hustlecity.game.engine.repayLoan
import com.hustlecity.game.engine.train
import com.hustlecity.game.model.Employee
import com.hustlecity.game.ui.AppIconTile
import com.hustlecity.game.ui.Bar
import com.hustlecity.game.ui.CardTitle
import com.hustlecity.game.ui.Chip
import com.hustlecity.game.ui.Dot
import com.hustlecity.game.ui.EmptyNote
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.KeyValue
import com.hustlecity.game.ui.LabeledBar
import com.hustlecity.game.ui.VehicleThumb
import com.hustlecity.game.ui.WhiteCard
import com.hustlecity.game.ui.hud.Avatar
import com.hustlecity.game.ui.hud.openApp
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT
import com.hustlecity.game.ui.toColor
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.City
import kotlin.math.max
import kotlin.math.roundToInt

private val DAYS_SHORT = listOf("M", "T", "W", "T", "F", "S", "S")

fun shiftText(emp: Employee): String {
    val days = (0 until 7).filter { (emp.days shr it) and 1 == 1 }
    val dayText = when {
        days.size == 7 -> "every day"
        days == listOf(0, 1, 2, 3, 4) -> "Mon–Fri"
        days == listOf(5, 6) -> "weekends"
        else -> days.joinToString("") { DAYS_SHORT[it] }
    }
    return "%02d:00–%02d:00, %s".format(emp.shiftStart, emp.shiftEnd, dayText)
}

@Composable
fun PhoneAppScreen(e: GameEngine, app: AppId) {
    val s = e.state
    Column(Modifier.fillMaxSize().background(HC.AppBg).clickable(enabled = false) {}) {
        BoxWithConstraints(Modifier.fillMaxWidth().background(HC.AppHeader)) {
            val wide = maxWidth > 740.dp
            Row(Modifier.fillMaxWidth().height(if (wide) 60.dp else 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(app.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(if (wide) 130.dp else 100.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    for (id in AppId.entries) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 3.dp)) {
                            AppIconTile(id, if (wide) 30.dp else 28.dp, showLabel = wide, badge = if (id == AppId.CONTACTS) s.messages.count { !it.read } else 0) { openApp(e, id) }
                            Box(Modifier.size(4.dp).clip(CircleShape).background(if (id == app) Color.White else Color.Transparent))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${e.money(s.player.money)}  ▲ ${e.moneyShort(e.pendingEarnings())}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${s.dayName} (Day ${s.day})  ${s.clock}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (app) {
                AppId.PERSONA -> PersonaApp(e)
                AppId.CONTACTS -> ContactsApp(e)
                AppId.RIVALS -> RivalsApp(e)
                AppId.STAFF -> StaffApp(e)
                AppId.BIZHUB -> BizHubApp(e)
                AppId.FINANCE -> FinanceApp(e)
                AppId.REALTY -> RealtyApp(e)
                AppId.MAPS -> {}
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("HUSTLE CITY · ${app.title.uppercase()}", color = Color.White.copy(alpha = .7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(4.dp)).clickable { e.ui.app = null }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(" CLOSE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------------ persona

@Composable
fun PersonaApp(e: GameEngine) {
    val s = e.state
    val p = s.player
    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WhiteCard(Modifier.width(230.dp)) {
            Avatar(p, 88.dp, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(p.name, style = HT.title, color = HC.Ink, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Entrepreneur · Rank #${e.rank()} of ${s.rivals.size + 1}", style = HT.small, color = HC.InkSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            KeyValue("Net worth", e.money(e.netWorth()), HC.Green)
            KeyValue("Cash", e.money(p.money), if (p.money < 0) HC.Red else HC.Ink)
            KeyValue("Total sales", e.money(p.totalEarned))
            KeyValue("Businesses", "${s.businesses.size}")
            KeyValue("Employees", "${s.employees.size}")
            KeyValue("Days in the city", "${s.day}")
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Needs")
                LabeledBar("Energy", (p.energy / 100).toFloat(), HC.Energy)
                LabeledBar("Hunger", (p.hunger / 100).toFloat(), HC.Hunger)
                LabeledBar("Happiness", (p.happiness / 100).toFloat(), HC.Green)
                Text("Sleep at home to restore energy, eat at home or at restaurants. Happiness grows with a nicer home, car and furniture.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Education")
                for (c in Catalog.courses) {
                    val done = c.id in p.courses
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (done) HC.Green else HC.InkSoft, modifier = Modifier.size(16.dp))
                        Text("  ${c.name}", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                        Text(if (done) "Completed" else "${e.money(c.price)} · ${c.hours}h", style = HT.small, color = HC.InkSoft)
                    }
                }
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Home & vehicles")
                val home = e.city.building(p.home)
                if (home != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏠 ${home.address}, ${City.neighborhoods[home.neighborhood].name} · ${home.area} m²", style = HT.body, color = HC.Ink, modifier = Modifier.weight(1f))
                        HButton("Pin", small = true) { e.setWaypoint(home.id) }
                    }
                }
                Text("Fridge: ${p.fridgeMeals} meals", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(vertical = 4.dp))
                for (v in s.vehicles) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleThumb(v.defId, 48.dp)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(v.def.name, style = HT.bodyBold, color = HC.Ink)
                            Text("Fuel ${(v.fuel * 100).roundToInt()}% · Condition ${(v.condition * 100).roundToInt()}% · Cargo ${v.cargo.size}/${v.def.cargo}", style = HT.small, color = HC.InkSoft)
                            if (v.cargo.isNotEmpty()) Text(v.cargo.joinToString { it.label }, style = HT.small, color = HC.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HButton("Locate", small = true) {
                            e.ui.app = AppId.MAPS
                            e.camera.mapX = v.x; e.camera.mapY = v.y; e.camera.mapDist = 350f
                        }
                    }
                }
                if (p.hand.isNotEmpty()) Text("Carrying: ${p.hand.joinToString { it.label }}", style = HT.small, color = HC.Orange)
            }
        }
    }
}

// ------------------------------------------------------------------ contacts

@Composable
fun ContactsApp(e: GameEngine) {
    val msgs = e.state.messages.asReversed()
    LaunchedEffect(msgs.size) { e.state.messages.forEach { it.read = true } }
    if (msgs.isEmpty()) { EmptyNote("No messages yet."); return }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        items(msgs.toList()) { m ->
            WhiteCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    val col = Catalog.rivalCompanies[(m.from.hashCode() and 0x7fffffff) % Catalog.rivalCompanies.size].second.toColor()
                    Box(Modifier.size(34.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                        Text(m.from.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Row {
                            Text(m.from, style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                            Text("Day ${m.day}, %02d:%02d".format(m.minute / 60, m.minute % 60), style = HT.small, color = HC.InkSoft)
                        }
                        Text(m.text, style = HT.body, color = HC.Ink)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ------------------------------------------------------------------ rivals

@Composable
fun RivalsApp(e: GameEngine) {
    val s = e.state
    var open by remember { mutableIntStateOf(-1) }
    data class RowData(val id: Int, val name: String, val color: Color, val worth: Double, val count: Int, val trend: Double)
    val rows = (s.rivals.map { r -> RowData(r.id, r.name, r.color.toColor(), r.netWorth, s.rivalBusinesses.count { it.rivalId == r.id }, r.trend) } +
        RowData(-1, "${s.player.name} (You)", HC.Green, e.netWorth(), s.businesses.size, s.history.lastOrNull()?.profit ?: 0.0)).sortedByDescending { it.worth }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Spacer(Modifier.height(6.dp)) }
        items(rows.size) { idx ->
            val r = rows[idx]
            WhiteCard(Modifier.fillMaxWidth().clickable { open = if (open == r.id) -2 else r.id }, padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${idx + 1}", style = HT.title, color = if (r.id == -1) HC.Green else HC.Ink, modifier = Modifier.width(44.dp))
                    Dot(r.color, 12.dp)
                    Text("  ${r.name}", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                    Text("${r.count} businesses", style = HT.small, color = HC.InkSoft, modifier = Modifier.width(100.dp))
                    Text(e.moneyShort(r.worth), style = HT.bodyBold, color = HC.Ink, modifier = Modifier.width(90.dp))
                    Icon(if (r.trend >= 0) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, null, tint = if (r.trend >= 0) HC.Green else HC.Red)
                }
                if (open == r.id && r.id >= 0) {
                    val list = s.rivalBusinesses.filter { it.rivalId == r.id }
                    for (rb in list.take(40)) {
                        val b = e.city.building(rb.buildingId) ?: continue
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${Catalog.type(rb.typeId).emoji}  ${rb.name}", style = HT.body, color = HC.Ink, modifier = Modifier.weight(1f))
                            Text("${b.address} · ${City.neighborhoods[b.neighborhood].name}", style = HT.small, color = HC.InkSoft, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.Place, null, tint = HC.Blue, modifier = Modifier.size(20.dp).clickable { e.setWaypoint(b.id); e.toast("Waypoint set") })
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ------------------------------------------------------------------ staff

@Composable
fun StaffApp(e: GameEngine) {
    val s = e.state
    var tab by remember { mutableIntStateOf(if (s.employees.isEmpty() && s.candidates.isNotEmpty()) 1 else 0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            Chip("Employees (${s.employees.size})", tab == 0) { tab = 0 }
            Chip("Candidates (${s.candidates.size})", tab == 1) { tab = 1 }
            Spacer(Modifier.weight(1f))
            val wages = s.employees.filter { it.businessId >= 0 }.sumOf { it.wage * it.weeklyHours } / 7
            Text("Daily payroll ≈ ${e.money(wages)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (tab == 0) {
            if (s.employees.isEmpty()) {
                WhiteCard(Modifier.fillMaxWidth()) {
                    EmptyNote("No employees yet. Complete Basic HR at Hustle Business School, then search for candidates at Apex Recruitment.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        HButton("Pin business school", small = true) { e.city.placeBuilding("school")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") } }
                        HButton("Pin recruiter", small = true) { e.city.placeBuilding("recruit")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") } }
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.employees.toList(), key = { it.id }) { emp ->
                    WhiteCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${emp.name}, ${emp.age}", style = HT.bodyBold, color = HC.Ink)
                                Text(emp.role.label, style = HT.small, color = HC.InkSoft)
                                val biz = s.business(emp.businessId)
                                Text(if (biz != null) "${biz.name} · ${shiftText(emp)} (${emp.weeklyHours}h/week)" else "Unassigned — assign in BizHub → Schedule", style = HT.small,
                                    color = if (biz != null) HC.Ink else HC.Red)
                            }
                            Column(Modifier.width(150.dp)) {
                                Text("Skill", style = HT.small, color = HC.InkSoft)
                                Bar(emp.skill.toFloat(), HC.Blue, Modifier.fillMaxWidth(), track = HC.Line, height = 7.dp)
                                Text("Satisfaction", style = HT.small, color = HC.InkSoft)
                                Bar(emp.satisfaction.toFloat(), if (emp.satisfaction < .35) HC.Red else HC.Green, Modifier.fillMaxWidth(), track = HC.Line, height = 7.dp)
                            }
                            Column(Modifier.width(90.dp).padding(start = 10.dp)) {
                                Text("${e.money(emp.wage)}/h", style = HT.bodyBold, color = HC.Ink)
                                Text("wants ${e.money(emp.demand)}", style = HT.small, color = HC.InkSoft)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HButton("Raise", small = true, color = HC.Green) { e.raise(emp) }
                                HButton("Train", small = true) { e.train(emp) }
                                HButton("Fire", small = true, color = HC.Red) {
                                    e.ui.confirm = Confirm("Fire ${emp.name}?", "They receive a severance of 8 hours of wages.", "Fire") { e.fire(emp) }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (s.candidates.isEmpty()) {
                WhiteCard(Modifier.fillMaxWidth()) {
                    EmptyNote(if (s.recruits.isNotEmpty()) "Apex Recruitment is searching… candidates arrive in about 2 hours." else "No candidates. Visit Apex Recruitment in the Garment District to start a search.")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.candidates.toList(), key = { it.id }) { c ->
                    WhiteCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${c.name}, ${c.age}", style = HT.bodyBold, color = HC.Ink)
                                Text("${c.role.label} · offer expires end of day ${c.expiresDay}", style = HT.small, color = HC.InkSoft)
                            }
                            Column(Modifier.width(150.dp)) {
                                Text("Skill ${(c.skill * 100).roundToInt()}%", style = HT.small, color = HC.InkSoft)
                                Bar(c.skill.toFloat(), HC.Blue, Modifier.fillMaxWidth(), track = HC.Line, height = 7.dp)
                            }
                            Text("${e.money(c.wage)}/h", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.padding(horizontal = 12.dp))
                            HButton("Hire", color = HC.Green, small = true) { e.hire(c) }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ finance

@Composable
fun FinanceApp(e: GameEngine) {
    val s = e.state
    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Overview")
                KeyValue("Cash", e.money(s.player.money), if (s.player.money < 0) HC.Red else HC.Ink)
                KeyValue("Net worth", e.money(e.netWorth()), HC.Green)
                KeyValue("Pending sales (paid at midnight)", e.money(e.pendingEarnings()), HC.Green)
                KeyValue("Debt", e.money(s.loans.sumOf { it.remaining }), HC.Red)
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Today so far")
                val inc = s.ledgerToday.filterKeys { it.startsWith("in:") }
                val exp = s.ledgerToday.filterKeys { !it.startsWith("in:") }
                if (inc.isEmpty() && exp.isEmpty()) Text("No transactions yet today.", style = HT.small, color = HC.InkSoft)
                for ((k, v) in inc) KeyValue(k.removePrefix("in:"), "+" + e.money(v), HC.Green, bold = false)
                for ((k, v) in exp.toList().sortedByDescending { it.second }) KeyValue(k, "-" + e.money(v), HC.Red, bold = false)
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Loans")
                if (s.loans.isEmpty()) Text("No active loans. Visit Hudson Bank in Lower Manhattan to borrow.", style = HT.small, color = HC.InkSoft)
                for (loan in s.loans.toList()) {
                    Text(loan.name, style = HT.bodyBold, color = HC.Ink)
                    Text("${e.money(loan.remaining)} left · ${loan.daysLeft} days · ${"%.2f".format(loan.dailyRate * 100)}%/day", style = HT.small, color = HC.InkSoft)
                    HButton("Repay ${e.money(loan.remaining)}", small = true, modifier = Modifier.padding(vertical = 4.dp)) { e.repayLoan(loan) }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Daily operating profit (last 14 days)")
                val hist = s.history.takeLast(14)
                if (hist.isEmpty()) Text("Your first day summary arrives at midnight.", style = HT.small, color = HC.InkSoft)
                else ProfitChart(hist.map { it.profit }, hist.map { it.cash }, Modifier.fillMaxWidth().height(150.dp))
            }
            val last = s.history.lastOrNull()
            if (last != null) WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Day ${last.day} breakdown")
                KeyValue("Income", e.money(last.revenue), HC.Green)
                for ((k, v) in last.expenses.toList().sortedByDescending { it.second }) KeyValue(k, "-" + e.money(v), HC.Red, bold = false)
                KeyValue("Operating profit", e.money(last.profit), if (last.profit >= 0) HC.Green else HC.Red)
                Text("Loans, repayments, furniture, vehicles and property count as investments, not as running costs.", style = HT.small, color = HC.InkSoft)
            }
            if (s.businesses.isNotEmpty()) WhiteCard(Modifier.fillMaxWidth()) {
                CardTitle("Businesses (yesterday)")
                for (b in s.businesses) {
                    val d = b.history.lastOrNull()
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("${b.type.emoji} ${b.name}", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                        if (d == null) Text("no data yet", style = HT.small, color = HC.InkSoft)
                        else {
                            val profit = d.revenue - d.cogs - d.wages - d.rent - d.marketing
                            Text("${d.customers} customers · revenue ${e.money(d.revenue)} · profit ", style = HT.small, color = HC.InkSoft)
                            Text(e.money(profit), style = HT.small, color = if (profit >= 0) HC.Green else HC.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfitChart(values: List<Double>, cash: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val maxAbs = max(values.maxOf { kotlin.math.abs(it) }, 1.0)
        val zeroY = size.height / 2
        val bw = size.width / n
        drawLine(Color(0xFFCCD2D9), Offset(0f, zeroY), Offset(size.width, zeroY), 1f)
        for ((i, v) in values.withIndex()) {
            val hgt = (v / maxAbs * (size.height / 2 - 4)).toFloat()
            val top = if (hgt >= 0) zeroY - hgt else zeroY
            drawRect(if (v >= 0) Color(0xFF27AE60) else Color(0xFFEB5757), Offset(i * bw + bw * .18f, top), Size(bw * .64f, kotlin.math.abs(hgt)))
        }
        if (cash.size >= 2) {
            val minC = cash.min(); val maxC = cash.max()
            val range = max(maxC - minC, 1.0)
            val path = Path()
            for ((i, c) in cash.withIndex()) {
                val x = i * bw + bw / 2
                val y = (size.height - 4 - (c - minC) / range * (size.height - 8)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF2F80ED), style = Stroke(3f))
        }
    }
}

// ------------------------------------------------------------------ realty

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealtyApp(e: GameEngine) {
    val s = e.state
    var tab by remember { mutableIntStateOf(0) }
    var kind by remember { mutableStateOf(BKind.RETAIL) }
    var hood by remember { mutableIntStateOf(-1) }
    var sort by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        FlowRow {
            Chip("For rent", tab == 0) { tab = 0 }
            Chip("My properties (${s.rentals.size})", tab == 1) { tab = 1 }
            Spacer(Modifier.width(16.dp))
            if (tab == 0) {
                Chip("Retail", kind == BKind.RETAIL, HC.Orange) { kind = BKind.RETAIL }
                Chip("Residential", kind == BKind.RESIDENTIAL, HC.Orange) { kind = BKind.RESIDENTIAL }
                Spacer(Modifier.width(16.dp))
                Chip("Cheapest", sort == 0, HC.Teal) { sort = 0 }
                Chip("Best traffic", sort == 1, HC.Teal) { sort = 1 }
                Chip("Largest", sort == 2, HC.Teal) { sort = 2 }
            }
        }
        if (tab == 0) {
            FlowRow {
                Chip("All areas", hood == -1, HC.Purple) { hood = -1 }
                for (n in City.neighborhoods) Chip(n.name, hood == n.id, n.color.toColor()) { hood = n.id }
            }
            val list = remember(s.rentals.size, s.rivalBusinesses.size, kind, hood, sort) {
                e.city.buildings.filter { it.kind == kind && e.isVacant(it.id) && (hood == -1 || it.neighborhood == hood) }.let { l ->
                    when (sort) { 0 -> l.sortedBy { it.baseRent }; 1 -> l.sortedByDescending { it.traffic }; else -> l.sortedByDescending { it.area } }
                }.take(80)
            }
            if (list.isEmpty()) EmptyNote("Nothing available with these filters.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { b ->
                    WhiteCard(Modifier.fillMaxWidth(), padding = 9.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1.3f)) {
                                Text(b.address, style = HT.bodyBold, color = HC.Ink)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Dot(City.neighborhoods[b.neighborhood].color.toColor(), 8.dp)
                                    Text(" ${City.neighborhoods[b.neighborhood].name}", style = HT.small, color = HC.InkSoft)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${b.area} m² (${b.sizeClass})", style = HT.small, color = HC.Ink)
                                if (kind == BKind.RETAIL) Text("Traffic ${(b.traffic * 100).roundToInt()}% · ${b.customerLimit} cust/h", style = HT.small, color = HC.InkSoft)
                                else Text("${b.parking} parking spots", style = HT.small, color = HC.InkSoft)
                            }
                            Column(Modifier.weight(.8f)) {
                                Text("${e.money(b.baseRent)}/mo", style = HT.bodyBold, color = HC.Ink)
                                Text("deposit ${e.money(b.baseRent / 2)}", style = HT.small, color = HC.InkSoft)
                            }
                            Icon(Icons.Filled.Place, null, tint = HC.Blue, modifier = Modifier.size(26.dp).clickable { e.setWaypoint(b.id); e.toast("Waypoint set: ${b.address}") })
                            Spacer(Modifier.width(6.dp))
                            HButton("Rent", small = true, color = HC.Green) { e.rentBuilding(b.id) }
                            if (Catalog.COURSE_REAL_ESTATE in s.player.courses) {
                                Spacer(Modifier.width(6.dp))
                                HButton("Buy ${e.moneyShort(b.value)}", small = true, color = HC.Purple) { e.buyBuilding(b.id) }
                            }
                        }
                    }
                }
            }
        } else {
            if (s.rentals.isEmpty()) EmptyNote("You don't rent anything yet.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.rentals.toList(), key = { it.buildingId }) { r ->
                    val b = e.city.building(r.buildingId) ?: return@items
                    val biz = s.businessAt(b.id)
                    WhiteCard(Modifier.fillMaxWidth(), padding = 9.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.address, style = HT.bodyBold, color = HC.Ink)
                                Text("${b.kind.label} · ${b.area} m² · ${City.neighborhoods[b.neighborhood].name}", style = HT.small, color = HC.InkSoft)
                                Text(
                                    when {
                                        b.id == s.player.home -> "Your home"
                                        biz != null -> "${biz.type.emoji} ${biz.name}"
                                        b.kind == BKind.RETAIL -> "Empty — go inside to start a business"
                                        else -> "Residential"
                                    }, style = HT.small, color = HC.Ink,
                                )
                            }
                            Text(if (r.owned) "OWNED" else "${e.money(r.rentPerMonth)}/mo", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.padding(horizontal = 10.dp))
                            Icon(Icons.Filled.Place, null, tint = HC.Blue, modifier = Modifier.size(26.dp).clickable { e.setWaypoint(b.id); e.toast("Waypoint set") })
                            Spacer(Modifier.width(6.dp))
                            if (Catalog.COURSE_REAL_ESTATE in s.player.courses && !r.owned) {
                                HButton("Buy", small = true, color = HC.Purple) { e.buyBuilding(b.id) }
                                Spacer(Modifier.width(6.dp))
                            }
                            HButton(if (r.owned) "Sell" else "End lease", small = true, color = HC.Red) {
                                e.ui.confirm = Confirm(if (r.owned) "Sell ${b.address}?" else "End lease at ${b.address}?", "This can't be undone.", "Confirm") { e.cancelLease(b.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

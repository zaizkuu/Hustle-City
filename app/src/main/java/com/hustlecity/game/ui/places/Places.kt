package com.hustlecity.game.ui.places

import com.hustlecity.game.ui.icons.GIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceDef
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.data.Source
import com.hustlecity.game.data.StaffRole
import com.hustlecity.game.engine.rentOf
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.CartLine
import com.hustlecity.game.engine.Confirm
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Panel
import com.hustlecity.game.engine.Screen
import com.hustlecity.game.engine.buyBuilding
import com.hustlecity.game.engine.buyGroceries
import com.hustlecity.game.engine.buyVehicle
import com.hustlecity.game.engine.cartSlots
import com.hustlecity.game.engine.checkout
import com.hustlecity.game.engine.checkoutOptions
import com.hustlecity.game.engine.loadFromWarehouse
import com.hustlecity.game.engine.vehicleNear
import com.hustlecity.game.engine.eat
import com.hustlecity.game.engine.refuel
import com.hustlecity.game.engine.rentBuilding
import com.hustlecity.game.engine.repairVehicle
import com.hustlecity.game.engine.repayLoan
import com.hustlecity.game.engine.requestCandidates
import com.hustlecity.game.engine.sellVehicle
import com.hustlecity.game.engine.sideJob
import com.hustlecity.game.engine.sleepMinutes
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.engine.startCampaign
import com.hustlecity.game.engine.takeCourse
import com.hustlecity.game.engine.takeLoan
import com.hustlecity.game.model.MINUTES_PER_DAY
import com.hustlecity.game.ui.CardTitle
import com.hustlecity.game.ui.Chip
import com.hustlecity.game.ui.Dot
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.KeyValue
import com.hustlecity.game.ui.ProductThumb
import com.hustlecity.game.ui.Stepper
import com.hustlecity.game.ui.VehicleThumb
import com.hustlecity.game.ui.WhiteCard
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT
import com.hustlecity.game.ui.toColor
import com.hustlecity.game.world.City
import com.hustlecity.game.world.dist
import kotlin.math.roundToInt

/** Most boxes one wholesale order can hold. */
private const val ORDER_MAX = 24

@Composable
fun PanelHost(e: GameEngine, panel: Panel) {
    when (panel) {
        is Panel.Place -> {
            val b = e.city.building(panel.buildingId) ?: return
            val place = Catalog.place(panel.placeId)
            when (place.kind) {
                PlaceKind.FURNITURE_STORE -> StoreDirectory(e, b.id, place)
                PlaceKind.WHOLESALER, PlaceKind.IMPORTER -> Wholesale(e, b.id, place)
                PlaceKind.CAR_DEALER -> Dealer(e, b.id, place)
                PlaceKind.GAS_STATION -> GasStation(e, b.id, place)
                PlaceKind.SCHOOL -> School(e, place)
                PlaceKind.RECRUITER -> Recruiter(e, place)
                PlaceKind.BANK -> Bank(e, place)
                PlaceKind.MARKETING -> Marketing(e, place)
                PlaceKind.HOSPITAL -> Hospital(e, place)
            }
        }
        is Panel.StartBusiness -> StartBusiness(e, panel.buildingId)
        is Panel.RivalShop -> RivalShop(e, panel.buildingId)
        is Panel.Listing -> Listing(e, panel.buildingId)
        Panel.Settings -> SettingsPanel(e)
        Panel.Help -> HelpPanel(e)
        Panel.Sleep -> SleepPanel(e)
        is Panel.Warehouse -> WarehousePanel(e, panel.businessId)
        is Panel.Trunk -> ManageStoragePanel(e, panel.vehicleId)
        is Panel.ShopDeliver -> ShopDeliverPanel(e, panel.buildingId)
    }
}

/** Centered white modal with a title bar and a red close button, over a dark scrim. */
@Composable
fun Modal(
    title: String,
    onClose: () -> Unit,
    maxWidth: Dp = 820.dp,
    headerExtra: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(HC.Scrim).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(10.dp).widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight(.94f)
                .shadow(10.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF7F8FA)),
        ) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = HT.title, color = HC.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                headerExtra()
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)).background(HC.Red).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(HC.Line))
            Column(Modifier.weight(1f).fillMaxWidth().padding(10.dp), content = content)
        }
    }
}

private fun close(e: GameEngine) { e.ui.panel = null }

// ------------------------------------------------------------------ shopping helpers

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.width(200.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEDF0F4)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = HC.InkSoft, modifier = Modifier.size(16.dp))
        Box(Modifier.padding(start = 6.dp)) {
            if (query.isEmpty()) Text("e.g. \"Shelf\"", color = HC.InkSoft, fontSize = 12.sp)
            BasicTextField(query, onChange, singleLine = true, textStyle = TextStyle(fontSize = 12.sp, color = HC.Ink))
        }
    }
}

@Composable
private fun OrderBar(e: GameEngine, storeBuildingId: Int, cart: MutableList<CartLine>, unitLabel: String) {
    var showOptions by remember { mutableStateOf(false) }
    val discount = com.hustlecity.game.engine.bulkDiscount(cart)
    val total = cart.sumOf { it.total } * (1 - discount)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(HC.GlassDark)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 8.dp)) {
                Text("ORDER", style = HT.label, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon("cargo_boxes", 11.dp, Modifier.padding(end = 3.dp), tint = Color.White.copy(alpha = .8f))
                    Text("${cartSlots(cart)}/$ORDER_MAX $unitLabel", color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                }
            }
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cart.toList()) { line ->
                    Row(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF2D06B)).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val name = if (line.isProduct) Catalog.product(line.id)?.name else Catalog.item(line.id)?.name
                        Text("$name ×${line.boxes}", color = HC.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.padding(start = 6.dp).size(16.dp).clip(RoundedCornerShape(3.dp)).background(HC.Red).clickable {
                            val i = cart.indexOf(line)
                            if (i >= 0) { if (line.boxes > 1) cart[i] = line.copy(boxes = line.boxes - 1) else cart.removeAt(i) }
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(if (discount > 0) "Bulk discount −${(discount * 100).roundToInt()}%" else if (unitLabel == "boxes") "6+ boxes: 10% off" else "Unpaid total", color = if (discount > 0) HC.GreenLight else Color.White, fontSize = 10.sp)
                Text(e.money(total), color = HC.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            HButton("Checkout", color = HC.Green, enabled = cart.isNotEmpty()) { showOptions = true }
        }
    }
    if (showOptions) {
        Box(Modifier.fillMaxSize().background(HC.Scrim).clickable { showOptions = false }, contentAlignment = Alignment.Center) {
            Column(Modifier.width(400.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable(enabled = false) {}.padding(14.dp)) {
                Text("How do you want to take it?", style = HT.title, color = HC.Ink)
                Text("Total ${e.money(total)} · ${cartSlots(cart)} slot(s)", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
                val opts = e.checkoutOptions(storeBuildingId, cart)
                if (opts.isEmpty()) Text("Park a vehicle near the entrance (45 m) or start a business to get deliveries.", style = HT.body, color = HC.Red)
                for ((target, label) in opts) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEDF3FD)).clickable {
                            if (e.checkout(storeBuildingId, cart.toList(), target)) { cart.clear(); showOptions = false }
                        }.padding(10.dp),
                    ) { Text(label, style = HT.bodyBold, color = HC.Blue) }
                }
                Text("Tip: a vehicle holds more boxes. Unload it inside your store or home.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun addToCart(e: GameEngine, cart: MutableList<CartLine>, id: String, isProduct: Boolean, price: Double) {
    if (cartSlots(cart) >= ORDER_MAX) { e.toast("One order holds up to $ORDER_MAX boxes"); return }
    val idx = cart.indexOfFirst { it.id == id }
    if (idx >= 0) cart[idx] = cart[idx].copy(boxes = cart[idx].boxes + 1) else cart += CartLine(id, isProduct, 1, price)
}

// ------------------------------------------------------------------ wholesale

@Composable
private fun Wholesale(e: GameEngine, buildingId: Int, place: PlaceDef) {
    val cart = remember { mutableStateListOf<CartLine>() }
    val source = when (place.id) { "metro" -> Source.METRO; "distro" -> Source.DISTRO; else -> Source.IMPORT }
    val locked = source == Source.IMPORT && Catalog.COURSE_IMPORT !in e.state.player.courses
    val myTypes = e.state.businesses.map { it.typeId }.toSet()
    Modal("${place.name} — Wholesale", { close(e) }) {
        if (locked) {
            Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon("lock", 16.dp, Modifier.padding(end = 6.dp), tint = HC.Red)
                Text("Importing requires the Import & Trade course at Hustle Business School.", style = HT.bodyBold, color = HC.Red)
            }
        }
        val products = Catalog.products.filter { it.source == source }
            .sortedByDescending { p -> Catalog.businessTypes.any { it.id in myTypes && it.products.any { pr -> pr.first == p.id } } }
        LazyVerticalGrid(GridCells.Adaptive(240.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { p ->
                val boxPrice = p.cost * p.unitsPerBox
                val usedBy = Catalog.businessTypes.filter { t -> t.products.any { it.first == p.id } }
                val mine = usedBy.any { it.id in myTypes }
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(if (mine) Color(0xFFE6F4EC) else Color.White).border(1.dp, HC.Line, RoundedCornerShape(6.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductThumb(p, 46.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(p.name, style = HT.bodyBold, color = HC.Ink)
                        Text("Box of ${p.unitsPerBox} · ${e.money(p.cost)}/unit", style = HT.small, color = HC.InkSoft)
                        Text("Sells ~${e.money(p.price)} · ${usedBy.joinToString { it.name }}", style = HT.small, color = HC.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(e.money(boxPrice), style = HT.bodyBold, color = HC.Ink)
                        HButton("+ Box", small = true, enabled = !locked) { addToCart(e, cart, p.id, true, boxPrice) }
                    }
                }
            }
        }
        OrderBar(e, buildingId, cart, "boxes")
    }
}

// ------------------------------------------------------------------ car dealer

private val PAINTS = listOf(0, 0xFFC0392B.toInt(), 0xFF212121.toInt(), 0xFFF2F2F2.toInt(), 0xFF1F3A93.toInt(), 0xFFF2C94C.toInt(), 0xFF27AE60.toInt(), 0xFF8E2442.toInt())

@Composable
private fun Dealer(e: GameEngine, buildingId: Int, place: PlaceDef) {
    var paint by remember { mutableIntStateOf(0) }
    Modal(place.name, { close(e) }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Text("PAINT", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(end = 8.dp))
                for ((i, col) in PAINTS.withIndex()) {
                    Box(
                        Modifier.padding(end = 6.dp).size(24.dp).clip(CircleShape).background(if (col == 0) Color(0xFFDDE2E8) else Color(col))
                            .border(if (paint == i) 3.dp else 1.dp, if (paint == i) HC.Blue else Color(0x33000000), CircleShape).clickable { paint = i },
                        contentAlignment = Alignment.Center,
                    ) { if (col == 0) Text("F", fontSize = 10.sp, color = HC.Ink, fontWeight = FontWeight.Bold) }
                }
                Text(if (paint == 0) "Factory color" else "Custom paint (free)", style = HT.small, color = HC.InkSoft)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Catalog.vehicles) { v ->
                    WhiteCard(Modifier.width(190.dp), padding = 8.dp) {
                        VehicleThumb(v.id, 110.dp, Modifier.align(Alignment.CenterHorizontally))
                        Text(v.name, style = HT.bodyBold, color = HC.Ink)
                        Text(e.money(v.price), style = HT.title, color = HC.Green)
                        KeyValue("Cargo", "${v.cargo} boxes", bold = false)
                        KeyValue("Top speed", "${(v.maxSpeed * 3.6f).roundToInt()} km/h", bold = false)
                        KeyValue("Happiness", "+${v.happiness}", bold = false)
                        HButton("Buy", Modifier.fillMaxWidth().padding(top = 4.dp), color = HC.Green) {
                            e.ui.confirm = Confirm("Buy a ${v.name}?", "${e.money(v.price)}. It will be parked outside.", "Buy") { e.buyVehicle(v.id, buildingId, PAINTS[paint]) }
                        }
                    }
                }
            }
            Text("YOUR VEHICLES", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (e.state.vehicles.isEmpty()) Text("You don't own a vehicle.", style = HT.small, color = HC.InkSoft)
            for (v in e.state.vehicles.toList()) {
                WhiteCard(Modifier.fillMaxWidth().padding(bottom = 6.dp), padding = 8.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VehicleThumb(v.defId, 48.dp)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(v.def.name, style = HT.bodyBold, color = HC.Ink)
                            Text("Condition ${(v.condition * 100).roundToInt()}% · value ${e.money(e.vehicleValue(v))}", style = HT.small, color = HC.InkSoft)
                        }
                        HButton("Repair ${e.money((1 - v.condition) * v.def.price * .2)}", small = true, enabled = v.condition < .99) { e.repairVehicle(v) }
                        Spacer(Modifier.width(6.dp))
                        HButton("Sell", small = true, color = HC.Red) {
                            e.ui.confirm = Confirm("Sell your ${v.def.name}?", "You'll get ${e.money(e.vehicleValue(v))}." + (if (v.cargo.isNotEmpty()) " Its cargo is sold off at half price." else ""), "Sell") { e.sellVehicle(v) }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ gas station

@Composable
private fun GasStation(e: GameEngine, buildingId: Int, place: PlaceDef) {
    val b = e.city.building(buildingId) ?: return
    Modal(place.name, { close(e) }, maxWidth = 520.dp) {
        val near = e.state.vehicles.filter { dist(it.x, it.y, b.cx, b.cy) < 40f }
        if (near.isEmpty()) Text("Drive or park a vehicle at the pumps to refuel.", style = HT.body, color = HC.InkSoft)
        for (v in near) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                VehicleThumb(v.defId, 44.dp)
                Text("  ${v.def.name} · fuel ${(v.fuel * 100).roundToInt()}%", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                HButton("Fill up ${e.money((1 - v.fuel) * 45 * 1.4)}", small = true, color = HC.Orange) { e.refuel(v) }
            }
        }
        Text("SHOP", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton("Snack ${e.moneyWhole(3.0)}", small = true) { e.eat(3.0, 12.0, label = "a snack") }
            HButton("Coffee ${e.moneyWhole(3.0)}", small = true) { e.eat(3.0, 3.0, energy = 8.0, label = "a coffee") }
        }
    }
}

// ------------------------------------------------------------------ school, recruiter, bank, marketing, hospital

@Composable
private fun School(e: GameEngine, place: PlaceDef) {
    Modal(place.name, { close(e) }) {
        Text("Courses take time — the clock fast-forwards while you study.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(bottom = 6.dp))
        LazyVerticalGrid(GridCells.Adaptive(260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Catalog.courses) { c ->
                val done = c.id in e.state.player.courses
                WhiteCard(padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GIcon("place_school", 16.dp, Modifier.padding(end = 6.dp), tint = HC.Ink)
                        Text(c.name, style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                        if (done) Icon(Icons.Filled.CheckCircle, null, tint = HC.Green, modifier = Modifier.size(18.dp))
                    }
                    Text(c.description, style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(vertical = 4.dp), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.money(c.price)} · ${c.hours} hours", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.weight(1f))
                        HButton(if (done) "Completed" else "Enroll", small = true, enabled = !done, color = HC.Green) { e.takeCourse(c.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Recruiter(e: GameEngine, place: PlaceDef) {
    Modal(place.name, { close(e) }, maxWidth = 640.dp) {
        if (Catalog.COURSE_HR !in e.state.player.courses) {
            Text("You need to complete the Basic HR course before you can hire staff.", style = HT.bodyBold, color = HC.Red)
            HButton("Pin Hustle Business School", small = true, modifier = Modifier.padding(top = 8.dp)) {
                e.city.placeBuilding("school")?.let { e.state.player.waypoint = it.id; e.toast("Waypoint set") }; close(e)
            }
            return@Modal
        }
        Text("Each search costs ${e.moneyWhole(250.0)} and delivers 3 candidates in about 2 hours. Check the Staff app to hire them.", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (role in StaffRole.entries) {
                val pending = e.state.recruits.any { it.role == role }
                WhiteCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(role.label, style = HT.bodyBold, color = HC.Ink)
                            Text(
                                when (role) {
                                    StaffRole.CUSTOMER_SERVICE -> "Runs the cash register in shops, cafés and restaurants."
                                    StaffRole.CLEANER -> "Keeps stores clean (+30% cleanliness per hour)."
                                    StaffRole.GYM_TRAINER -> "Required to serve gym customers."
                                    StaffRole.HAIRSTYLIST -> "Cuts and colors hair at hairdresser chairs."
                                    StaffRole.HR_MANAGER -> "Works at your Headquarters: +10% staff satisfaction for up to 12 employees and hires replacements when someone quits."
                                    StaffRole.PURCHASING_AGENT -> "Works at your Headquarters: restocks up to 4 stores every morning at 06:00, from your warehouse first."
                                    StaffRole.DJ -> "Plays the DJ booth. A nightclub can't let anyone in without a DJ on shift."
                                    StaffRole.PROJECTIONIST -> "Runs the films. A cinema needs one on shift to sell tickets."
                                    StaffRole.ACTOR -> "Performs on stage. A theater needs a cast on shift to put on a show."
                                    StaffRole.LAWYER -> "Bills legal work by the hour at a Law Firm. Needs an Office Desk."
                                    StaffRole.PROGRAMMER -> "Builds websites for clients at a Web Development Agency. Needs an Office Desk."
                                    StaffRole.GRAPHIC_DESIGNER -> "Designs logos and posters at a Graphic Designer studio. Needs an Office Desk."
                                    StaffRole.TRAVEL_AGENT -> "Books trips for clients at a Travel Agency. Needs an Office Desk."
                                    StaffRole.EVENT_PLANNER -> "Organizes parties and conferences at an Event Planning Agency. Needs an Office Desk."
                                    StaffRole.STORE_MANAGER -> "Runs one store for you: hires staff to cover its hours, keeps wages fair and reorders stock before it runs out."
                                    StaffRole.DRIVER -> "Drives one of your vans on a daily delivery route (Fleet app), from a wholesaler or your warehouse to your stores."
                                } + " Typical wage ~${e.money(role.baseWage)}/h.", style = HT.small, color = HC.InkSoft,
                            )
                        }
                        HButton(if (pending) "Searching…" else "Search ${e.moneyWhole(250.0)}", small = true, enabled = !pending, color = HC.Purple) { e.requestCandidates(role) }
                    }
                }
            }
            HButton("Open Staff app", small = true) { close(e); e.ui.app = AppId.STAFF }
        }
    }
}

@Composable
private fun Bank(e: GameEngine, place: PlaceDef) {
    Modal(place.name, { close(e) }, maxWidth = 680.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your net worth: ${e.money(e.netWorth())}", style = HT.bodyBold, color = HC.Ink)
            for (l in Catalog.loans) {
                val has = e.state.loans.any { it.name == l.name }
                val eligible = e.netWorth() >= l.minNetWorth
                WhiteCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${l.name} — ${e.money(l.amount)}", style = HT.bodyBold, color = HC.Ink)
                            Text("${l.days} days · ${"%.2f".format(l.dailyRate * 100)}% interest per day · ≈${e.money(l.amount / l.days + l.amount * l.dailyRate)}/day at first", style = HT.small, color = HC.InkSoft)
                            if (l.minNetWorth > 0) Text("Requires net worth ${e.money(l.minNetWorth)}", style = HT.small, color = if (eligible) HC.Green else HC.Red)
                        }
                        HButton(if (has) "Active" else "Take loan", small = true, enabled = !has && eligible, color = HC.Green) { e.takeLoan(l) }
                    }
                }
            }
            for (loan in e.state.loans.toList()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${loan.name}: ${e.money(loan.remaining)} left, ${loan.daysLeft} days", style = HT.body, color = HC.Ink, modifier = Modifier.weight(1f))
                    HButton("Repay all", small = true) { e.repayLoan(loan) }
                }
            }
        }
    }
}

@Composable
private fun Marketing(e: GameEngine, place: PlaceDef) {
    var sel by remember { mutableIntStateOf(e.state.businesses.firstOrNull()?.id ?: -1) }
    var days by remember { mutableIntStateOf(3) }
    Modal(place.name, { close(e) }, maxWidth = 720.dp) {
        if (e.state.businesses.isEmpty()) { Text("You need a business before you can advertise.", style = HT.body, color = HC.InkSoft); return@Modal }
        FlowRowChips(e, sel) { sel = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Text("Duration", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.width(80.dp))
            Stepper("$days days", { days = (days - 1).coerceAtLeast(1) }, { days = (days + 1).coerceAtMost(30) })
        }
        val biz = e.state.business(sel)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in Catalog.campaigns) {
                val locked = c.needsCourse && Catalog.COURSE_MARKETING !in e.state.player.courses
                WhiteCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GIcon("place_marketing", 16.dp, Modifier.padding(end = 6.dp), tint = HC.Ink)
                                Text("${c.name} · +${(c.boost * 100).roundToInt()}% marketing", style = HT.bodyBold, color = HC.Ink)
                            }
                            Text(c.description + if (locked) " Requires Marketing Fundamentals." else "", style = HT.small, color = if (locked) HC.Red else HC.InkSoft)
                        }
                        Text("${e.money(c.pricePerDay)}/day", style = HT.bodyBold, color = HC.Ink, modifier = Modifier.padding(horizontal = 8.dp))
                        HButton("Start", small = true, enabled = !locked && biz != null, color = HC.Orange) { biz?.let { e.startCampaign(it, c.id, days) } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(e: GameEngine, sel: Int, onSel: (Int) -> Unit) {
    FlowRow {
        for (b in e.state.businesses) Chip(b.name, b.id == sel, icon = b.type.icon) { onSel(b.id) }
    }
}

@Composable
private fun Hospital(e: GameEngine, place: PlaceDef) {
    Modal(place.name, { close(e) }, maxWidth = 520.dp) {
        Text("Feeling worn out? Rest in our recovery ward.", style = HT.body, color = HC.InkSoft)
        HButton("Rest 2 hours — ${e.moneyWhole(200.0)} (+25 energy)", Modifier.padding(top = 10.dp)) {
            if (e.spend(200.0, "Medical")) {
                close(e)
                e.startSkip(com.hustlecity.game.model.SkipKind.REST, 120, "Resting at City Hospital…")
            }
        }
    }
}

// ------------------------------------------------------------------ rival shop

@Composable
private fun RivalShop(e: GameEngine, buildingId: Int) {
    val rb = e.state.rivalBusinessAt(buildingId) ?: return
    val type = Catalog.type(rb.typeId)
    val owner = e.state.rivals.firstOrNull { it.id == rb.rivalId }
    val mineNow = rb.rivalId == com.hustlecity.game.engine.SUBSIDIARY
    Modal(rb.name, { close(e) }, maxWidth = 640.dp) {
        Text(if (mineNow) "${type.name} · your subsidiary, run by its manager" else "${type.name} · owned by ${owner?.name}", style = HT.body, color = HC.InkSoft)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mineNow) {
                Text("Yesterday: ${e.money(rb.revenue)} sales, ${e.money(rb.profit)} profit (paid to you nightly)", style = HT.small, color = HC.Ink, modifier = Modifier.weight(1f))
                HButton("Sell", small = true, color = HC.Red) {
                    e.ui.confirm = Confirm("Sell ${rb.name}?", "You get its fit-out back at 80% of book value; the shop goes up for rent.", "Sell") { e.ops.sellSubsidiary(rb); close(e) }
                }
            } else if (owner != null) {
                val ask = e.ops.askingPrice(rb)
                Text("${owner.name} would sell it for ${e.money(ask)} (its fit-out, a few months' profit and a premium). It keeps trading for you.", style = HT.small, color = HC.Ink, modifier = Modifier.weight(1f))
                HButton("Buy out", small = true, color = HC.Purple, enabled = e.state.player.money >= ask) {
                    e.ui.confirm = Confirm("Buy ${rb.name} for ${e.money(ask)}?", "Its profit is paid to you every night.", "Buy") { if (e.ops.buyRivalStore(rb)) close(e) }
                }
            }
        }
        val mine = e.state.businesses.filter { it.typeId == rb.typeId && e.city.building(it.buildingId)?.neighborhood == e.city.building(buildingId)?.neighborhood }
        if (mine.isNotEmpty()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon("swords", 16.dp, Modifier.padding(end = 6.dp), tint = HC.Red)
            Text("Competes with your ${mine.joinToString { it.name }}", style = HT.bodyBold, color = HC.Red)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            CardTitle("Buy", modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (type.id) {
                    "fastfood" -> { HButton("Burger meal ${e.moneyWhole(9.0)}", small = true) { e.eat(9.0, 45.0, label = "a burger meal") }; HButton("Fries ${e.moneyWhole(3.0)}", small = true) { e.eat(3.0, 15.0, label = "fries") } }
                    "coffee" -> { HButton("Coffee & croissant ${e.moneyWhole(6.0)}", small = true) { e.eat(6.0, 15.0, energy = 12.0, label = "coffee and a croissant") }; HButton("Espresso ${e.moneyWhole(3.0)}", small = true) { e.eat(3.0, 2.0, energy = 8.0, label = "an espresso") } }
                    "supermarket", "produce" -> { HButton("Groceries (4 meals) ${e.moneyWhole(32.0)}", small = true) { e.buyGroceries() }; HButton("Snack ${e.moneyWhole(3.0)}", small = true) { e.eat(3.0, 12.0, label = "a snack") } }
                    "liquor" -> HButton("Drink ${e.moneyWhole(8.0)}", small = true) { e.eat(8.0, 2.0, energy = -5.0, happiness = 6.0, label = "a drink") }
                    "bookstore" -> HButton("A good book ${e.moneyWhole(15.0)}", small = true) { e.eat(15.0, 0.0, happiness = 5.0, label = "a new book") }
                    "clothing" -> HButton("New outfit ${e.moneyWhole(60.0)}", small = true) { e.eat(60.0, 0.0, happiness = 8.0, label = "a new outfit") }
                    "jewelry" -> HButton("Watch ${e.moneyWhole(520.0)}", small = true) { e.eat(520.0, 0.0, happiness = 15.0, label = "a fancy watch") }
                    "electronics" -> HButton("Headphones ${e.moneyWhole(59.0)}", small = true) { e.eat(59.0, 0.0, happiness = 6.0, label = "new headphones") }
                    "florist" -> HButton("Bouquet ${e.moneyWhole(22.0)}", small = true) { e.eat(22.0, 0.0, happiness = 4.0, label = "a bouquet") }
                    "giftshop" -> HButton("Souvenir ${e.moneyWhole(12.0)}", small = true) { e.eat(12.0, 0.0, happiness = 3.0, label = "a souvenir") }
                    "gym" -> HButton("Workout ${e.moneyWhole(18.0)} (1h)", small = true) {
                        if (e.spend(18.0, "Personal")) { close(e); e.startSkip(com.hustlecity.game.model.SkipKind.WORKOUT, 60, "Working out…") }
                    }
                    "hairdresser" -> HButton("Haircut ${e.moneyWhole(28.0)}", small = true) { e.eat(28.0, 0.0, happiness = 6.0, label = "a fresh haircut") }
                }
            }
            CardTitle("Side job", modifier = Modifier.padding(top = 14.dp))
            val wage = 16.0 + (type.baseDemand / 20.0).roundToInt()
            Text("Work a shift for ${owner?.name} at ${e.money(wage)}/hour. Paid at the end of the shift. Tiring!", style = HT.small, color = HC.InkSoft, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (h in listOf(2, 4, 8)) HButton("$h hours", small = true, color = HC.Teal) { e.sideJob(h, wage, rb.name) }
            }
        }
    }
}

// ------------------------------------------------------------------ start business

@Composable
private fun StartBusiness(e: GameEngine, buildingId: Int) {
    val b = e.city.building(buildingId) ?: return
    var typeId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    Modal(b.address, { close(e) }, maxWidth = 980.dp) {
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(210.dp).verticalScroll(rememberScrollState())) {
                WhiteCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.People, null, tint = HC.Ink, modifier = Modifier.size(14.dp)); Text(" ${b.customerLimit}   ", style = HT.small, color = HC.Ink)
                        Icon(Icons.Filled.DirectionsCar, null, tint = HC.Ink, modifier = Modifier.size(14.dp)); Text(" ${b.parking}", style = HT.small, color = HC.Ink)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Dot(b.hood.color.toColor(), 9.dp)
                        Text(" ${b.hood.name}", style = HT.small, color = HC.Ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${b.kind.label.uppercase()} BUILDING", style = HT.label, color = HC.InkSoft)
                    Text(b.address, style = HT.title, color = HC.Ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SquareFoot, null, tint = HC.Ink, modifier = Modifier.size(14.dp)); Text(" ${b.area} m² (${b.sizeClass})", style = HT.small, color = HC.Ink)
                    }
                    Text("Traffic index ${(b.traffic * 100).roundToInt()}%", style = HT.small, color = HC.Ink)
                    Text("Interior ${b.gridW}×${b.gridD} tiles", style = HT.small, color = HC.Ink)
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Select the business you want to start", style = HT.title, color = HC.Ink, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null, tint = HC.Orange, modifier = Modifier.size(14.dp))
                    Text(" Check the business type to ensure you have access to all the inventory sources.", style = HT.small, color = HC.InkSoft)
                }
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = when (b.kind) {
                        com.hustlecity.game.world.BKind.WAREHOUSE -> Catalog.facilityTypes.filter { it.buildingKind == "WAREHOUSE" }
                        com.hustlecity.game.world.BKind.OFFICE -> Catalog.facilityTypes.filter { it.buildingKind == "OFFICE" } + Catalog.officeTypes
                        else -> Catalog.businessTypes
                    }
                    items(types) { t ->
                        val sel = typeId == t.id
                        val importLocked = t.sources.contains(Source.IMPORT) && Catalog.COURSE_IMPORT !in e.state.player.courses
                        Column(
                            Modifier.width(150.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (sel) Color(0xFF3B5B8C) else Color(0xFF4A515C))
                                .border(3.dp, if (sel) HC.Blue else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable(enabled = !importLocked) {
                                    typeId = t.id
                                    if (name.isBlank()) name = Catalog.randomBusinessName(t.id, e.rnd)
                                }
                                .padding(7.dp).verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GIcon(t.icon, 20.dp)
                                Text(" " + t.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 14.sp)
                            }
                            val srcText = if (t.sources.isEmpty()) "Services" else t.sources.joinToString { if (it == Source.IMPORT) "Importer" else "Wholesaler" }
                            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                val srcColor = if (importLocked) Color(0xFFFF7B7B) else Color.White
                                Text("Source: $srcText ", color = srcColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                GIcon(if (importLocked) "lock" else "check", 11.dp, tint = srcColor)
                            }
                            Text(if (t.office) "Firms in the city: ${BizCalc.cityCompetitors(e, t.id)}" else "Competitors nearby: ${BizCalc.competitorsFor(e, t.id, b.neighborhood)}",
                                color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                            if (!t.facility) Text("Staff: ${t.staffRole.label}" + (t.performer?.let { " + ${it.label}" } ?: ""), color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
                            if (t.dailyFee > 0) Text("Licences ${e.money(t.dailyFee)}/day", color = Color(0xFFFFC27B), fontSize = 10.sp)
                            Text("Primary products", color = Color.White.copy(alpha = .6f), fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                            Text(t.primaryProducts.joinToString(", "), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 12.sp)
                            Text("Needs: " + t.requirements.joinToString { it.label }, color = Color.White.copy(alpha = .7f), fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp,
                                modifier = Modifier.padding(top = 3.dp))
                            if (sel) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                GIcon("check", 12.dp, Modifier.padding(end = 3.dp), tint = HC.GreenLight)
                                Text("SELECTED", color = HC.GreenLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HButton("Cancel", color = HC.Red) { close(e) }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE6E9ED)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (name.isEmpty()) Text("Name your business", color = HC.InkSoft, fontSize = 13.sp)
                    BasicTextField(name, { name = it.take(28) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = HC.Ink))
                }
                Icon(Icons.Filled.Casino, null, tint = HC.InkSoft, modifier = Modifier.size(20.dp).clickable { name = Catalog.randomBusinessName(typeId ?: "giftshop", e.rnd) })
            }
            Spacer(Modifier.width(10.dp))
            HButton("Start business", color = HC.Green, enabled = typeId != null) {
                val t = typeId ?: return@HButton
                if (e.startBusiness(buildingId, t, name) != null) close(e)
            }
        }
    }
}

// ------------------------------------------------------------------ listing

@Composable
private fun Listing(e: GameEngine, buildingId: Int) {
    val b = e.city.building(buildingId) ?: return
    Modal(b.address, { close(e) }, maxWidth = 520.dp) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            KeyValue("Type", b.kind.label)
            KeyValue("Neighborhood", b.hood.name)
            KeyValue("Size", "${b.area} m² (${b.sizeClass})")
            if (b.kind == com.hustlecity.game.world.BKind.RETAIL) {
                KeyValue("Traffic index", "${(b.traffic * 100).roundToInt()}%")
                KeyValue("Customer limit", "${b.customerLimit}/hour")
            }
            KeyValue("Parking", "${b.parking}")
            KeyValue("Landlord", b.landlord)
            KeyValue("Rent", "${e.money(e.rentOf(b))}/month (charged daily)")
            KeyValue("Deposit", e.money(e.rentOf(b) / 2))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HButton("Rent", color = HC.Green, enabled = e.isVacant(b.id)) { if (e.rentBuilding(b.id)) close(e) }
                if (Catalog.COURSE_REAL_ESTATE in e.state.player.courses) HButton("Buy ${e.moneyShort(b.value)}", color = HC.Purple) { if (e.buyBuilding(b.id)) close(e) }
                HButton("Waypoint") { e.setWaypoint(b.id) }
            }
        }
    }
}

// ------------------------------------------------------------------ settings, help, sleep

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(e: GameEngine) {
    val st = e.settings
    Modal("Settings", { st.save(); close(e) }, maxWidth = 560.dp) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("GRAPHICS QUALITY", style = HT.label, color = HC.InkSoft)
            FlowRow {
                Chip("Low (fastest)", st.quality == 0) { st.quality = 0; e.traffic.ensure(st.carCount, st.pedCount) }
                Chip("Medium", st.quality == 1) { st.quality = 1; e.traffic.ensure(st.carCount, st.pedCount) }
                Chip("High", st.quality == 2) { st.quality = 2; e.traffic.ensure(st.carCount, st.pedCount) }
            }
            Text("RENDERER", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            FlowRow {
                Chip("HD (GPU lighting, smooth edges)", st.hd) { st.hd = true }
                Chip("Classic (no GPU)", !st.hd) { st.hd = false }
            }
            Text("3D RESOLUTION", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            FlowRow {
                Chip("Auto (keeps it smooth)", st.resolution == 0) { st.resolution = 0 }
                for (pct in intArrayOf(100, 85, 70, 55)) Chip("$pct%", st.resolution == pct) { st.resolution = pct }
            }
            if (st.gpuFailed) Text("HD graphics aren't supported on this device, so the classic renderer is used.", style = HT.small, color = HC.Red)
            Text("FRAME RATE", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            FlowRow {
                Chip("30 FPS (battery saver)", st.fpsCap == 30) { st.fpsCap = 30 }
                Chip("60+ FPS", st.fpsCap == 60) { st.fpsCap = 60 }
            }
            Text("AUDIO", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            VolumeRow("Radio music", st.musicVolume) { st.musicVolume = it; st.radioOn = it > 0f; st.applyAudio() }
            VolumeRow("Sound effects", st.sfxVolume) { st.sfxVolume = it; st.applyAudio() }
            Text("RADIO STATION", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            FlowRow {
                for ((i, station) in com.hustlecity.game.audio.Audio.stations.withIndex()) {
                    Chip(station.name, st.station == i && st.radioOn) { st.station = i; st.radioOn = true; if (st.musicVolume <= 0f) st.musicVolume = .5f; st.applyAudio() }
                }
            }
            ToggleRow("Show FPS counter", st.showFps) { st.showFps = it }
            ToggleRow("Pause the clock while menus are open", st.pauseInMenus) { st.pauseInMenus = it }
            ToggleRow("Left-handed controls", st.leftHanded) { st.leftHanded = it }
            ToggleRow("Show the minimap", st.minimap) { st.minimap = it }
            Text("CAR CONTROLS", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 6.dp))
            Row {
                Chip("Steering wheel & pedals", st.carControls == 0) { st.carControls = 0 }
                Chip("Arrow keys", st.carControls == 2) { st.carControls = 2 }
                Chip("Joystick", st.carControls == 1) { st.carControls = 1 }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HButton("Save game", color = HC.Green) { e.save(showToast = true) }
                if (e.hasGame) HButton("How to play", color = HC.Blue) { st.save(); e.ui.panel = com.hustlecity.game.engine.Panel.Help }
                HButton("Main menu", color = HC.Orange) {
                    st.save(); e.save(); close(e)
                    e.ui.app = null
                    e.ui.screen = Screen.MENU
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HT.body, color = HC.Ink, modifier = Modifier.width(120.dp))
        androidx.compose.material3.Slider(value, onChange, Modifier.weight(1f))
        Text("${(value * 100).roundToInt()}%", style = HT.small, color = HC.InkSoft, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HT.body, color = HC.Ink, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = HC.Green))
    }
}

@Composable
fun HelpPanel(e: GameEngine) {
    Modal("How to play", { close(e) }, maxWidth = 720.dp) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for ((t, body) in HELP) {
                Text(t, style = HT.bodyBold, color = HC.Ink, modifier = Modifier.padding(top = 8.dp))
                Text(e.localize(body), style = HT.body, color = HC.InkSoft)
            }
        }
    }
}

private val HELP = listOf(
    "Moving around" to "Use the joystick to walk. Walk up to a door and tap ENTER. Near your car, tap GET IN — the joystick then steers and accelerates, the red pedal brakes and reverses. Drag the city to look around, pinch to zoom and twist with two fingers to rotate. Taxis (from any building card or Maps) save time.",
    "Your needs" to "Energy, hunger and happiness are shown top-left. Sleep at home, eat from your fridge (buy groceries at supermarkets) or at restaurants and cafés. Let energy hit zero and you'll wake up in hospital with a bill.",
    "Starting a business" to "1) Rent a retail building in the Realty app. 2) Go inside and pick a business type. 3) Buy the required furniture (BizHub lists requirements) at Essentials Appliances, Prime Kitchen Supply, Lux Interiors or OfficePlus: walk around the store, pick things off the displays and pay at the checkout. 4) Push your cart out to your car and load it with MANAGE STORAGE (or pay for delivery), drive to your store, carry the boxes in and place them with the Interior Designer. 5) Buy stock at Metro Wholesale or CityDistro, unload it. 6) Turn off Temporarily Closed and start working the register.",
    "Carrying boxes" to "Bare hands hold 1 box. Stores lend you a flatbed cart by the entrance that holds 8, but it has to stay in their parking lot. A hand truck (Essentials Appliances, OfficePlus) carries 4, folds into any vehicle and takes one cargo slot. Next to your vehicle, MANAGE STORAGE moves boxes between the trunk and what you carry. Parked by your store or home, CARRY INSIDE takes a trip per load, so a hand truck saves a lot of time.",
    "Customers" to "Demand depends on the building's traffic index, marketing, the hour of the day, competitors in the neighborhood and your reputation. Capacity depends on registers/equipment and staff. Price products near market price — BizHub shows the chance a customer accepts your price.",
    "Money" to "Sales are collected at midnight. Rent, wages, loan payments and marketing are charged then too. The daily summary and the Finance app show where your money goes.",
    "Staff" to "Take Basic HR at Hustle Business School, then pay Apex Recruitment for a candidate search. Hire in the Staff app and schedule shifts in BizHub → Schedule. Unhappy staff (low pay, long weeks) will quit.",
    "Growing" to "Logistics Management unlocks automatic daily deliveries. Marketing Fundamentals unlocks bigger campaigns at Buzz Marketing. Import & Trade unlocks electronics and jewelry stores. Real Estate Investing lets you buy buildings. Beat the rivals in the Rivals app!",
    "Warehouses" to "Rent a warehouse (Realty → Warehouse), open it, and fill it with pallet racks from OfficePlus Supply or Essentials Appliances. Buy 6+ boxes at a wholesaler for 10% off and deliver them there. Delivery contracts and purchasing agents use warehouse stock first, and you can load boxes into your vehicle from inside.",
    "Headquarters" to "Rent an office and open your Headquarters with office desks. Hire HR Managers (happier staff, automatic replacements) and Purchasing Agents (each restocks 4 stores every morning) at Apex Recruitment, then assign them to HQ in BizHub → Schedule.",
    "Nightlife and shows" to "Nightclubs need a Cocktail Bar, a Dance Floor and a DJ Booth, and a DJ on shift. Cinemas need a screen, seat rows, a concession stand and a projectionist; theaters need a stage, seats and actors. Cinemas and theaters pay a daily licence fee, but sell tickets all evening.",
    "Office firms" to "Office buildings can host a Law Firm, Web Development Agency, Graphic Designer, Travel Agency or Event Planning Agency. Each professional needs an Office Desk and bills clients by the hour. Clients come from reputation and advertising, not foot traffic, and firms compete across the whole city.",
    "Rivals" to "Rival companies run their own chains. They open stores where the money is, renovate busy ones, advertise, buy their buildings and close losers. Aggressive rivals open next to your best stores and start price wars, which makes them tougher competition. Watch their moves in the Rivals app.",
    "Driving" to "Get in next to your car. Drag the steering wheel left or right, hold GAS to go and BRAKE to slow down; keep holding BRAKE at a standstill to reverse. PARK pulls over to the curb (or lines up in a parking lot) and gets you out; GET OUT leaves the car where it stands. In Settings you can switch to arrow keys (◀ ▶ to steer, ▲ gas, ▼ brake) or joystick driving. With a keyboard, the arrow keys (or W A S D) walk and drive.",
    "GPS and maps" to "Tap Navigate on any building (in the street or in the Maps app) to set a destination. A blue line on the road and on the minimap shows the way, and the banner at the top gives the next turn and the distance. Tap the minimap to open the full map.",
    "Weather" to "The weather follows the seasons (the game starts in April). Rain, storms and snow keep walk-in shoppers at home but fill cinemas, cafés and bookstores. Umbrellas sell in the rain, ice cream and cold drinks in the heat, coffee and coat checks in the cold. Tap the weather in the top bar for a three-day forecast.",
    "Cleaning" to "Every customer tracks a little dirt along the routes people walk, more near the door when it's wet, and now and then spills something. Trash cans catch litter nearby. Cleaners scrub the dirtiest spots first, and a cleaning station makes them faster.",
    "Security" to "Shoplifters target jewelry, electronics, clothing and liquor stores. Security cameras stop about 30% of thefts each and anti-theft gates about 55%. BizHub Insight shows what was stolen yesterday.",
    "Furniture" to "Checkout counters serve 60 customers per hour (registers 40). Shopping baskets let more people shop at once. Pizza ovens, ice cream freezers, shoe and magazine racks add optional products. A digital scale speeds up fruit & vegetable stores, and a copier helps office firms.",
    "Winning and losing" to "Stay out of deep debt: three midnights below -\$5,000 and the bank declares you bankrupt. Reach #1 in Rivals with a \$5,000,000 net worth to own Hustle City, then keep building as long as you like.",
    "Radio" to "Your phone has a radio with four stations. Tap the station name to switch, or change volumes in Settings.",
)

@Composable
fun SleepPanel(e: GameEngine) {
    Modal("Sleep", { close(e) }, maxWidth = 460.dp) {
        val s = e.state
        Text("Energy ${s.player.energy.roundToInt()}%. Sleeping restores ~13% energy per hour.", style = HT.body, color = HC.InkSoft)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (h in listOf(2, 4, 8)) HButton("$h hours", small = true) { e.sleepMinutes(h * 60) }
        }
        val untilSeven = ((7 * 60 - (s.minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY).toInt()
        HButton("Until 07:00 (${untilSeven / 60}h ${untilSeven % 60}m)", Modifier.padding(top = 8.dp), color = HC.Purple) { e.sleepMinutes(untilSeven) }
    }
}

// ------------------------------------------------------------------ warehouse loading

@Composable
private fun WarehousePanel(e: GameEngine, businessId: Int) {
    val w = e.state.business(businessId) ?: return
    val v = e.vehicleNear(w.buildingId)
    Modal("${w.name} — Load vehicle", { close(e) }, maxWidth = 640.dp) {
        Text(
            if (v == null) "Park a vehicle within 45 m of the entrance to load boxes."
            else "${v.def.name}: ${v.cargo.size}/${v.def.cargo} slots used. Tap a product to load one box.",
            style = HT.body, color = if (v == null) HC.Red else HC.InkSoft,
        )
        val stock = w.stock.filter { it.value > 0 }.toList()
        if (stock.isEmpty()) Text("The warehouse is empty. Buy stock at a wholesaler and deliver it here or unload it from your vehicle.", style = HT.body, color = HC.InkSoft, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((pid, qty) in stock) {
                val p = Catalog.product(pid) ?: continue
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductThumb(p, 36.dp)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(p.name, style = HT.bodyBold, color = HC.Ink)
                        Text("$qty units · ${(qty + p.unitsPerBox - 1) / p.unitsPerBox} boxes", style = HT.small, color = HC.InkSoft)
                    }
                    HButton("Load box", small = true, enabled = v != null && e.cargoSlotsFree(v) > 0) { e.loadFromWarehouse(w, pid) }
                }
            }
        }
    }
}

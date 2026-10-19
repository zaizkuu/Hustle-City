package com.hustlecity.game.ui.hud

import com.hustlecity.game.ui.icons.GIcon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.audio.Audio
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.BizCalc
import com.hustlecity.game.engine.FLOOR_COLORS
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.Objectives
import com.hustlecity.game.engine.Panel
import com.hustlecity.game.engine.SHIRT_COLORS
import com.hustlecity.game.engine.SKIN_TONES
import com.hustlecity.game.engine.ToastKind
import com.hustlecity.game.engine.WALL_COLORS
import com.hustlecity.game.engine.cleanStore
import com.hustlecity.game.engine.commitGhost
import com.hustlecity.game.engine.eatAtHome
import com.hustlecity.game.engine.itemDims
import com.hustlecity.game.engine.refuel
import com.hustlecity.game.engine.roadside
import com.hustlecity.game.engine.sellItem
import com.hustlecity.game.engine.sellStored
import com.hustlecity.game.engine.setOpen
import com.hustlecity.game.engine.storageList
import com.hustlecity.game.engine.storeItem
import com.hustlecity.game.engine.taxiFare
import com.hustlecity.game.engine.taxiTo
import com.hustlecity.game.engine.toggleWork
import com.hustlecity.game.engine.Carrier
import com.hustlecity.game.engine.HAND_TRUCK
import com.hustlecity.game.engine.carrierName
import com.hustlecity.game.engine.carryCap
import com.hustlecity.game.engine.carryIn
import com.hustlecity.game.engine.carryMinutes
import com.hustlecity.game.engine.vehicleNear
import com.hustlecity.game.engine.unloadInto
import com.hustlecity.game.engine.unloadDestination
import com.hustlecity.game.engine.unloadableCount
import com.hustlecity.game.engine.workUntilClosing
import com.hustlecity.game.model.PlayerState
import com.hustlecity.game.ui.AppIconTile
import com.hustlecity.game.ui.Bar
import com.hustlecity.game.ui.GlassPanel
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.ItemThumb
import com.hustlecity.game.ui.PanelHeader
import com.hustlecity.game.ui.SectionLabel
import com.hustlecity.game.ui.toColor
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.City
import com.hustlecity.game.world.dist
import kotlin.math.hypot
import kotlin.math.roundToInt

private val shadowText = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1.5f), 3f))

// ------------------------------------------------------------------ top bar

@Composable
fun Avatar(p: PlayerState, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val skin = SKIN_TONES[p.skin % SKIN_TONES.size].toColor()
    val shirt = SHIRT_COLORS[p.shirt % SHIRT_COLORS.size].toColor()
    Canvas(modifier.size(size).clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(Color(0xFF8FA3BB), Color(0xFF5E6E84))))) {
        val w = this.size.width; val h = this.size.height
        drawRoundRect(shirt, Offset(w * .18f, h * .68f), Size(w * .64f, h * .5f), CornerRadius(w * .2f))
        drawRect(skin, Offset(w * .43f, h * .55f), Size(w * .14f, h * .16f))
        drawCircle(skin, w * .22f, Offset(w * .5f, h * .42f))
        drawArc(Color(0xFF3B2A20), 180f, 180f, true, Offset(w * .28f, h * .19f), Size(w * .44f, h * .3f))
        drawCircle(Color(0xFF2B2B2B), w * .025f, Offset(w * .42f, h * .43f))
        drawCircle(Color(0xFF2B2B2B), w * .025f, Offset(w * .58f, h * .43f))
        drawRoundRect(Color(0xFF6B3E2E), Offset(w * .43f, h * .52f), Size(w * .14f, h * .025f), CornerRadius(4f))
    }
}

@Composable
private fun NeedBar(icon: String, color: Color, value: Float, barWidth: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            GIcon(icon, 13.dp)
        }
        Spacer(Modifier.width(4.dp))
        Bar(value, if (value < .2f) HC.Red else color, Modifier.width(barWidth), height = 6.dp)
    }
}

@Composable
private fun SquareButton(icon: String, badge: Int = 0, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 5.dp).size(44.dp).clip(RoundedCornerShape(6.dp)).background(HC.Glass).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GIcon(icon, 22.dp)
        if (badge > 0) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(9.dp).clip(CircleShape).background(HC.Red))
    }
}

@Composable
fun TopBar(e: GameEngine) {
    val s = e.state
    val p = s.player
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 790.dp
        val tiny = maxWidth < 680.dp
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p, 44.dp, Modifier.clickable { e.ui.app = AppId.PERSONA })
            Spacer(Modifier.width(5.dp))
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(HC.Glass).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val bw = if (tiny) 26.dp else if (compact) 38.dp else 62.dp
                NeedBar("energy", HC.Energy, (p.energy / 100).toFloat(), bw)
                NeedBar("hunger", HC.Hunger, (p.hunger / 100).toFloat(), bw)
                NeedBar("happiness", HC.Happy, (p.happiness / 100).toFloat(), bw)
                Box(Modifier.width(1.dp).height(26.dp).background(Color(0x40FFFFFF)))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { e.ui.app = AppId.FINANCE }) {
                    GIcon("money", 16.dp)
                    Text(" " + e.moneyShort(p.money), color = if (p.money < 0) HC.Red else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ArrowDropUp, null, tint = HC.GreenLight, modifier = Modifier.size(20.dp))
                    Text(e.moneyShort(e.pendingEarnings()), color = HC.GreenLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.width(1.dp).height(26.dp).background(Color(0x40FFFFFF)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!tiny) GIcon("calendar", 15.dp)
                    val dayName = if (compact) s.dayName.take(3) else s.dayName
                    Text(" $dayName (Day ${s.day})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon("clock", 15.dp)
                    Text(" ${s.clock}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                // weather: tap for the forecast
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { e.ui.info = "Weather" to e.weather.forecastText() }.padding(horizontal = 2.dp)) {
                    val sky = e.weather.sky()
                    GIcon(sky.icon, 17.dp)
                    if (!tiny) Text(" ${e.weather.temperature().roundToInt()}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val paused = e.speedIndex == 0
                    GIcon(
                        if (paused) "play" else "pause", 28.dp,
                        tint = if (paused) HC.Red else Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { e.speedIndex = if (paused) 1 else 0; e.bump() }.padding(4.dp),
                    )
                    Row(
                        Modifier.clip(RoundedCornerShape(4.dp)).clickable { e.speedIndex = when (e.speedIndex) { 1 -> 2; 2 -> 3; else -> 1 }; e.bump() }.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GIcon("fast_forward", 20.dp, tint = if (e.speedIndex >= 2) HC.Yellow else Color.White)
                        Text(when (e.speedIndex) { 2 -> "2x"; 3 -> "4x"; else -> "1x" }, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val unread = s.messages.count { !it.read }
            SquareButton("bell", unread) { e.ui.app = AppId.CONTACTS }
            if (!tiny) SquareButton("help") { e.ui.panel = Panel.Help }
            SquareButton("settings") { e.ui.panel = Panel.Settings }
        }
    }
}

// ------------------------------------------------------------------ objectives

@Composable
fun ObjectivesPanel(e: GameEngine, modifier: Modifier = Modifier) {
    val open = e.ui.objectivesOpen
    val screenH = LocalConfiguration.current.screenHeightDp
    val maxBody = (screenH - 58 - 28 - 165).coerceIn(70, 320).dp
    GlassPanel(modifier) {
        PanelHeader("Objectives", Modifier.clickable { e.ui.objectivesOpen = !open }) {
            Icon(if (open) Icons.Filled.KeyboardDoubleArrowLeft else Icons.Filled.KeyboardDoubleArrowRight, null, tint = HC.HeaderText, modifier = Modifier.size(16.dp))
        }
        if (!open) return@GlassPanel
        Column(Modifier.heightIn(max = maxBody).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
            val gIdx = Objectives.currentGroup(e)
            val group = Objectives.groups[gIdx]
            SectionLabel(group.title)
            for (o in group.items) {
                val done = Objectives.isDone(e, o)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val t = e.objectiveTarget(o.target)
                        if (t != null) { e.state.player.waypoint = t; e.toast("Waypoint set: ${e.city.building(t)?.let { e.buildingName(it) }}") }
                    }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    CheckBox(done)
                    Text(
                        o.text, color = if (done) Color.White.copy(alpha = .55f) else Color.White, fontSize = 11.sp, lineHeight = 14.sp,
                        textDecoration = if (done) TextDecoration.LineThrough else null, modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
            val alerts = Objectives.alerts(e)
            if (alerts.isNotEmpty()) {
                SectionLabel("Alerts")
                for (a in alerts.take(12)) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (a.businessId >= 0) { e.ui.bizHubSel = a.businessId; e.ui.app = AppId.BIZHUB }
                        }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        CheckBox(false)
                        Text(a.text, color = if (a.severe) Color(0xFFFF7B7B) else Color.White, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f).padding(start = 7.dp))
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(HC.Orange), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckBox(done: Boolean) {
    Box(
        Modifier.padding(top = 1.dp).size(15.dp).clip(RoundedCornerShape(3.dp)).background(if (done) HC.Green else Color(0x55FFFFFF)),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
}

// ------------------------------------------------------------------ phone

@Composable
fun PhonePanel(e: GameEngine, modifier: Modifier = Modifier, reserveTop: Int = 0) {
    val unread = e.state.messages.count { !it.read }
    val candidates = e.state.candidates.size
    if (!e.ui.phoneOpen) {
        Box(
            modifier.size(52.dp).shadow(4.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(HC.GlassDark).clickable { e.ui.phoneOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(28.dp))
            if (unread + candidates > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(10.dp).clip(CircleShape).background(HC.Red))
        }
        return
    }
    val screenH = LocalConfiguration.current.screenHeightDp
    Column(
        modifier.width(168.dp).heightIn(max = (screenH - 66 - reserveTop).coerceAtLeast(150).dp).shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF232830)).padding(5.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF6C7682)))
        }
        Column(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF8D97A4))) {
            PanelHeader("HustlePhone 1.0", Modifier.clickable { e.ui.phoneOpen = false }) {
                Icon(Icons.Filled.ExpandMore, null, tint = HC.HeaderText, modifier = Modifier.size(16.dp))
            }
            RadioWidget(e)
            val apps = AppId.entries
            Column(Modifier.padding(vertical = 2.dp).verticalScroll(rememberScrollState())) {
                for (row in 0 until 4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0 until 2) {
                            val id = apps[row * 2 + col]
                            val badge = when (id) { AppId.CONTACTS -> unread; AppId.STAFF -> candidates; else -> 0 }
                            AppIconTile(id, 30.dp, badge = badge) { openApp(e, id) }
                        }
                    }
                }
            }
        }
    }
}

fun openApp(e: GameEngine, id: AppId) {
    if (id == AppId.MAPS) {
        e.tip("map", "Maps", "Drag to pan and pinch to zoom. Use the filters on the right to show neighborhoods, stores for rent or rival businesses. Tap a building to set a waypoint or call a taxi.")
        val (fx, fy) = e.focusPoint()
        e.camera.mapX = fx; e.camera.mapY = fy; e.camera.mapDist = 650f
        e.ui.selected = -1
    }
    if (id == AppId.BIZHUB && e.ui.bizHubSel < 0) e.ui.bizHubSel = e.state.businesses.firstOrNull()?.id ?: -1
    e.ui.app = id
}

@Composable
private fun RadioWidget(e: GameEngine) {
    val st = e.settings
    val on = st.radioOn && st.musicVolume > 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon("music", 10.dp, Modifier.padding(end = 3.dp))
            Text(
                if (on) "${Audio.stations[Audio.station].name}: ${Audio.nowPlaying}" else "Radio off · tap to tune in", color = Color.White, fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable {
                    st.station = (Audio.station + 1) % Audio.stations.size
                    st.radioOn = true
                    st.applyAudio(); st.save(); e.bump()
                },
            )
            Icon(
                if (on) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White,
                modifier = Modifier.size(20.dp).clickable {
                    st.radioOn = !on
                    if (st.radioOn && st.musicVolume <= 0f) st.musicVolume = .5f
                    st.applyAudio(); st.save(); e.bump()
                },
            )
            Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { Audio.nextTrack(); e.bump() })
        }
        Bar(Audio.trackProgress, Color.White, Modifier.fillMaxWidth().padding(top = 2.dp), track = Color(0x44FFFFFF), height = 2.dp)
    }
}

// ------------------------------------------------------------------ joystick & actions

@Composable
fun Joystick(e: GameEngine, modifier: Modifier = Modifier) {
    if (e.skip != null) return
    var knob by remember { mutableStateOf(Offset.Zero) }
    val size = 124.dp
    Box(
        modifier.size(size).clip(CircleShape).background(Color(0x33FFFFFF)).border(2.dp, Color(0x66FFFFFF), CircleShape)
            .pointerInput(Unit) {
                val r = this.size.width / 2f
                fun update(pos: Offset) {
                    var d = pos - Offset(r, r)
                    val len = d.getDistance()
                    if (len > r) d *= r / len
                    knob = d
                    e.input.jx = d.x / r
                    e.input.jy = d.y / r
                }
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown()
                        update(down.position)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            update(ch.position)
                            ch.consume()
                        }
                    } finally {
                        // also runs when the joystick leaves the screen mid-drag
                        knob = Offset.Zero
                        e.input.jx = 0f; e.input.jy = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }.size(50.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xEEFFFFFF), Color(0xAAD5DCE5)))),
        )
    }
}

@Composable
fun ActionButtons(e: GameEngine, modifier: Modifier = Modifier) {
    val p = e.state.player
    if (e.skip != null) return
    if (p.driving >= 0) return
    Row(modifier.padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        e.nearDoor?.let { b ->
            val label = if (b.kind == BKind.GAS) "Fuel Up" else e.buildingName(b)
            HButton("Enter $label", color = HC.Green) { e.enterBuilding(b) }
        }
        e.nearVehicle?.let { v ->
            HButton("Get in ${v.def.name}", icon = Icons.Filled.DirectionsCar) { e.enterVehicle(v); e.ui.phoneOpen = false }
            // Manage storage: move boxes between the trunk and your hands, hand truck or store cart
            HButton("Manage storage (${v.cargo.size}/${v.def.cargo})", color = HC.Orange, icon = Icons.Filled.Inventory2) { e.ui.panel = Panel.Trunk(v.id) }
            val dest = e.unloadDestination(v)
            val boxes = v.cargo.count { it.id != HAND_TRUCK }
            if (dest != null && boxes > 0) HButton("Carry $boxes inside (~${e.carryMinutes(boxes)} min)", color = HC.GlassLight, small = true) { e.carryIn(dest.id, v) }
        }
    }
}

/** What you're carrying, and with what: bare hands (1 box), a hand truck (4) or a store cart (8). */
@Composable
fun CarryingChip(e: GameEngine, modifier: Modifier = Modifier) {
    val p = e.state.player
    val hand = p.hand
    if (hand.isEmpty() && p.carrier == Carrier.HANDS) return
    Column(
        modifier.widthIn(max = 260.dp).shadow(4.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(HC.GlassDark).padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(if (p.carrier == Carrier.HANDS) "hands" else "hand_truck", 16.dp)
            Text(" Carrying ${hand.size}/${e.carryCap()} · ${carrierName(p.carrier)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (hand.isNotEmpty()) Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (item in hand.take(8)) com.hustlecity.game.ui.places.CargoThumb(item, 22.dp, Modifier.clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .85f)).padding(1.dp))
        }
        if (p.carrier == Carrier.CART) Text("Store carts stay in the parking lot", color = Color.White.copy(alpha = .65f), fontSize = 9.sp)
    }
}

@Composable
fun LocationLabel(e: GameEngine, modifier: Modifier = Modifier) {
    if (e.state.player.driving >= 0) return
    val (x, y) = e.focusPoint()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(e.city.describeLocation(x, y), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = shadowText)
        Text(e.city.neighborhoodAt(x, y).name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = shadowText)
    }
}

// ------------------------------------------------------------------ building card

@Composable
fun BuildingCard(e: GameEngine, modifier: Modifier = Modifier, avoidPhone: Boolean = true) {
    val b = e.city.building(e.ui.selected) ?: return
    val s = e.state
    val right = if (avoidPhone && e.ui.phoneOpen) 176.dp else 0.dp
    Column(modifier.padding(end = right).width(250.dp).shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(HC.GlassDark)) {
        Row(Modifier.fillMaxWidth().background(HC.Header).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(b.address.uppercase(), style = HT.label, color = HC.HeaderText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(30.dp).background(HC.Red).clickable { e.ui.selected = -1 }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.padding(10.dp)) {
            if (b.kind != BKind.PARK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon("parking_p", 13.dp)
                    Text(" ${b.parking}   ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    GIcon("area", 13.dp)
                    Text(" ${"%,d".format(b.area)} m² (${b.sizeClass})   ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    GIcon("customers", 13.dp)
                    Text(" ${b.customerLimit}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
            }
            val biz = s.businessAt(b.id)
            val rival = s.rivalBusinessAt(b.id)
            val rental = s.rentalFor(b.id)
            val place = b.placeId?.let { Catalog.place(it) }
            val typeLine: String
            val nameLine: String?
            when {
                place != null -> { typeLine = place.kind.name.replace('_', ' '); nameLine = place.name }
                biz != null -> { typeLine = biz.type.name; nameLine = biz.name }
                rival != null -> { typeLine = Catalog.type(rival.typeId).name; nameLine = rival.name }
                else -> { typeLine = b.kind.label; nameLine = null }
            }
            Text(typeLine.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (nameLine != null) Text(nameLine.uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text("NEIGHBORHOOD: ${b.hood.name.uppercase()}", color = Color.White.copy(alpha = .8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("TRAFFIC INDEX: ${(b.traffic * 100).roundToInt()}%", color = Color.White.copy(alpha = .8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            val status: Pair<String, Color>? = when {
                b.id == s.player.home -> "YOUR HOME" to HC.Orange
                rental?.owned == true -> "OWNED BY YOU" to HC.Orange
                rental != null -> "RENTED BY YOU" to HC.Orange
                biz != null -> null
                rival != null -> "OWNED BY ${s.rivals.firstOrNull { it.id == rival.rivalId }?.name?.uppercase()}" to Color(0xFFBFC7D1)
                e.isVacant(b.id) -> "FOR RENT · ${e.money(b.baseRent)}/MONTH" to HC.GreenLight
                b.solid && place == null -> "BUILDING OWNED BY ${b.landlord.uppercase()}" to Color(0xFFBFC7D1)
                else -> null
            }
            if (status != null) Text(status.first, color = status.second, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
            if (biz != null) {
                val open = BizCalc.isOpen(biz, s.hour)
                Text(
                    if (open) "OPEN · CLOSING AT %02d:00".format(biz.closeHour) else if (biz.tempClosed) "TEMPORARILY CLOSED" else "CLOSED · OPENS %02d:00".format(biz.openHour),
                    color = if (open) HC.GreenLight else HC.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            val (fx, fy) = e.focusPoint()
            val d = dist(fx, fy, b.doorX, b.doorY)
            val near = s.player.driving < 0 && (b.distToDoor(s.player.x, s.player.y) < 8f || (b.kind == BKind.GAS && dist(s.player.x, s.player.y, b.cx, b.cy) < 18f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (near && b.kind != BKind.PARK && b.kind != BKind.PARKING) HButton("Enter", color = HC.Green, small = true) { e.enterBuilding(b) }
                HButton(if (s.player.waypoint == b.id) "Stop GPS" else "Navigate", small = true, icon = Icons.Filled.Place) { e.setWaypoint(b.id) }
                if (d > 60f) HButton("Taxi ${e.money(taxiFare(d))}", color = HC.Yellow, textColor = HC.Ink, small = true) { e.taxiTo(b.id) }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (biz != null) HButton("Open in BizHub", small = true, color = HC.Purple) { e.ui.bizHubSel = biz.id; e.ui.app = AppId.BIZHUB }
                if (e.isVacant(b.id)) HButton("View listing", small = true, color = HC.Green) { e.ui.panel = Panel.Listing(b.id) }
            }
            Text("${d.roundToInt()} m away", color = Color.White.copy(alpha = .6f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ------------------------------------------------------------------ interior

@Composable
fun InteriorPanel(e: GameEngine, modifier: Modifier = Modifier) {
    val sc = e.interior ?: return
    val b = sc.building
    val right = if (e.ui.phoneOpen && !e.ui.designer) 176.dp else 0.dp
    val screenH = LocalConfiguration.current.screenHeightDp
    Column(
        modifier.padding(end = right).width(196.dp).heightIn(max = (screenH - 66).dp).shadow(6.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp)).background(HC.GlassDark),
    ) {
        PanelHeader(e.buildingName(b))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val full = Modifier.fillMaxWidth()
            val unload = e.unloadableCount()
            // cargo first: what can be brought in, or why not
            val ownPlace = sc.mode == InteriorMode.BUSINESS || sc.mode == InteriorMode.HOME
            if (ownPlace && unload > 0) {
                val carried = e.state.player.hand.count { it.id != HAND_TRUCK && !it.unpaid }
                if (carried > 0) HButton("Put down what you carry ($carried)", full, color = HC.Orange, icon = Icons.Filled.Inventory2) { e.unloadInto(b.id, true, null) }
                e.vehicleNear(b.id)?.let { v ->
                    val boxes = v.cargo.count { it.id != HAND_TRUCK }
                    if (boxes > 0) HButton("Carry in from ${v.def.name} ($boxes, ~${e.carryMinutes(boxes)} min)", full, small = true, color = HC.Orange) { e.carryIn(b.id, v) }
                }
            } else if (ownPlace) {
                e.state.vehicles.firstOrNull { it.cargo.isNotEmpty() }?.let { far ->
                    Text("Your ${far.def.name} has ${far.cargo.size} item(s) but is parked too far away. Park within 45 m of the entrance to unload.",
                        color = HC.Yellow, fontSize = 10.sp)
                }
            }
            when (sc.mode) {
                InteriorMode.BUSINESS -> if (sc.business!!.type.facility) {
                    val biz = sc.business
                    HButton("Open in BizHub", full, small = true) { e.ui.bizHubSel = biz.id; e.ui.app = AppId.BIZHUB }
                    HButton("Toggle walls", full, small = true, color = HC.GlassLight) { sc.showWalls = !sc.showWalls; e.bump() }
                    HButton("Interior designer", full, small = true, color = HC.GlassLight) {
                        e.ui.designer = !e.ui.designer; sc.selectedIndex = -1; sc.cancelGhost(); e.bump()
                        if (e.ui.designer) e.tip("designer", "Interior designer", "Tap an item from storage, then tap the floor to position it. Rotate it and press Place. Tap placed furniture to move, rotate, store or sell it. You can also repaint the floor and walls.")
                    }
                    if (BizCalc.isWarehouse(biz)) {
                        Text("${BizCalc.warehouseBoxesUsed(biz)} / ${BizCalc.warehouseBoxes(biz)} boxes stored", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        HButton("Load vehicle", full, small = true, color = HC.Teal) { e.ui.panel = Panel.Warehouse(biz.id) }
                    }
                } else {
                    val biz = sc.business
                    HButton("Open in BizHub", full, small = true) { e.ui.bizHubSel = biz.id; e.ui.app = AppId.BIZHUB }
                    HButton("Toggle walls", full, small = true, color = HC.GlassLight) { sc.showWalls = !sc.showWalls; e.bump() }
                    HButton("Interior designer", full, small = true, color = HC.GlassLight) {
                        e.ui.designer = !e.ui.designer; sc.selectedIndex = -1; sc.cancelGhost(); e.bump()
                        if (e.ui.designer) e.tip("designer", "Interior designer", "Tap an item from storage, then tap the floor to position it. Rotate it and press Place. Tap placed furniture to move, rotate, store or sell it. You can also repaint the floor and walls.")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (biz.tempClosed) "TEMPORARILY CLOSED" else "OPEN", color = if (biz.tempClosed) HC.Red else HC.GreenLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Hours %02d:00–%02d:00".format(biz.openHour, biz.closeHour), color = Color.White.copy(alpha = .7f), fontSize = 10.sp)
                        }
                        Switch(!biz.tempClosed, { e.setOpen(biz, it) }, colors = SwitchDefaults.colors(checkedTrackColor = HC.Green))
                    }
                    val working = e.state.player.workingAt == biz.id
                    HButton(if (working) "Stop working" else "Start working", full, small = true, color = if (working) HC.Red else HC.Green) { e.toggleWork(biz) }
                    HButton("Work until %02d:00".format(biz.closeHour), full, small = true, color = HC.Teal) { e.workUntilClosing(biz) }
                    HButton("Clean (${biz.cleanliness.roundToInt()}%)", full, small = true, color = HC.GlassLight) { e.cleanStore(biz) }
                }
                InteriorMode.HOME -> {
                    HButton("Sleep", full, small = true) { e.ui.panel = Panel.Sleep }
                    HButton("Eat (${e.state.player.fridgeMeals} meals)", full, small = true, color = HC.Green) { e.eatAtHome() }
                    HButton("Interior designer", full, small = true, color = HC.GlassLight) {
                        e.ui.designer = !e.ui.designer; sc.selectedIndex = -1; sc.cancelGhost(); e.bump()
                        if (e.ui.designer) e.tip("designer", "Interior designer", "Tap an item from storage, then tap the floor to position it. Rotate it and press Place. Tap placed furniture to move, rotate, store or sell it. You can also repaint the floor and walls.")
                    }
                    HButton("Toggle walls", full, small = true, color = HC.GlassLight) { sc.showWalls = !sc.showWalls; e.bump() }
                }
                InteriorMode.EMPTY -> HButton("Start a business", full, small = true, color = HC.Green) { e.ui.panel = Panel.StartBusiness(b.id) }
                InteriorMode.PLACE -> if (sc.walkIn) com.hustlecity.game.ui.places.ShopControls(e, sc)
                    else HButton("Shop / services", full, small = true, color = HC.Green) { e.ui.panel = Panel.Place(b.id, b.placeId!!) }
                InteriorMode.RIVAL -> HButton("Shop / side job", full, small = true, color = HC.Green) { e.ui.panel = Panel.RivalShop(b.id) }
            }
            HButton("Exit building", full, small = true, color = HC.Red) { e.exitBuilding(); e.ui.phoneOpen = true }
        }
    }
}

@Composable
fun DesignerBar(e: GameEngine, modifier: Modifier = Modifier) {
    val sc = e.interior ?: return
    val storage = e.storageList()
    GlassPanel(modifier.padding(8.dp).widthIn(max = 760.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(HC.Header).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("INTERIOR DESIGNER", style = HT.label, color = HC.HeaderText, modifier = Modifier.weight(1f))
            val biz = sc.business
            if (biz != null) {
                Text("FLOOR ", style = HT.label, color = HC.HeaderText)
                for ((i, col) in FLOOR_COLORS.withIndex()) Swatch(col.toColor(), biz.floorColor == i) { biz.floorColor = i; e.bump() }
                Text("  WALL ", style = HT.label, color = HC.HeaderText)
                for ((i, col) in WALL_COLORS.withIndex()) Swatch(col.toColor(), biz.wallColor == i) { biz.wallColor = i; e.bump() }
            }
            Spacer(Modifier.width(8.dp))
            HButton("Done", small = true, color = HC.Green) { e.ui.designer = false; sc.cancelGhost(); sc.selectedIndex = -1; e.bump() }
        }
        Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            val ghost = sc.ghostId
            val sel = sc.items.getOrNull(sc.selectedIndex)
            when {
                ghost != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemThumb(ghost, 40.dp)
                    Text(Catalog.item(ghost)?.name ?: ghost, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Tap the floor to move", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                    HButton("Rotate", small = true, color = HC.GlassLight) { sc.rotateGhost(); e.bump() }
                    HButton("Place", small = true, color = HC.Green, enabled = sc.ghostValid) { e.commitGhost() }
                    HButton("Cancel", small = true, color = HC.Red) { sc.cancelGhost(); e.bump() }
                }
                sel != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val def = Catalog.item(sel.itemId)
                    ItemThumb(sel.itemId, 40.dp)
                    Text(def?.name ?: sel.itemId, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    HButton("Move", small = true) { sc.startMoving(sc.selectedIndex); e.bump() }
                    HButton("Rotate", small = true, color = HC.GlassLight) {
                        val nr = (sel.rot + 1) % 4
                        if (sc.canPlace(sel.itemId, sel.x, sel.y, nr, sc.selectedIndex)) sel.rot = nr else e.toast("Not enough room to rotate here")
                        e.bump()
                    }
                    if (storage != null) HButton("Store", small = true, color = HC.Orange) { e.storeItem(sc.selectedIndex) }
                    HButton("Sell ${e.money((def?.price ?: 0.0) * .5)}", small = true, color = HC.Red) { e.sellItem(sc.selectedIndex) }
                }
                storage.isNullOrEmpty() -> Text(
                    "Storage is empty. Buy furniture at Essentials Appliances, Prime Kitchen Supply or Lux Interiors, then unload it here. Tap placed items to move or sell them.",
                    color = Color.White.copy(alpha = .85f), fontSize = 12.sp,
                )
                else -> androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val grouped = storage.groupingBy { it }.eachCount().toList()
                    items(grouped.size) { i ->
                        val (id, count) = grouped[i]
                        Row(
                            Modifier.width(150.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x33FFFFFF)).clickable { sc.startPlacing(id); e.bump() }.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ItemThumb(id, 34.dp)
                            Column(Modifier.padding(start = 4.dp)) {
                                Text("${Catalog.item(id)?.name}${if (count > 1) " ×$count" else ""}", color = Color.White, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
                                Text("SELL ${e.moneyShort((Catalog.item(id)?.price ?: 0.0) * .5)}", color = Color(0xFFFF8A8A), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { e.sellStored(id) })
                            }
                        }
                    }
                }
            }
        }
        if (sc.ghostId != null) {
            val def = Catalog.item(sc.ghostId!!)
            if (def != null) {
                val (fw, fd) = itemDims(def, sc.ghostRot)
                Text("Size ${fw}×$fd tiles · ${if (sc.ghostValid) "fits here" else "blocked"}", color = if (sc.ghostValid) HC.GreenLight else HC.Red, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 1.dp).size(16.dp).clip(CircleShape).background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) HC.Blue else Color(0x55000000), CircleShape).clickable(onClick = onClick),
    )
}

// ------------------------------------------------------------------ toasts, summary, skip, dialogs

@Composable
fun Toasts(e: GameEngine, modifier: Modifier = Modifier) {
    if (e.ui.toasts.isEmpty()) return
    val right = if (e.ui.phoneOpen && e.ui.app == null) 0.dp else 0.dp
    Column(modifier.padding(end = right).widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (t in e.ui.toasts.toList()) {
            val color = when (t.kind) {
                ToastKind.SAVE, ToastKind.GOOD -> HC.Green
                ToastKind.BAD -> HC.RedDark
                ToastKind.INFO -> HC.GlassDark
            }
            Row(
                Modifier.shadow(3.dp, RoundedCornerShape(5.dp)).clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = .85f)))).clickable { e.ui.toasts.remove(t) }.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                t.icon?.let { GIcon(it, 15.dp, Modifier.padding(end = 6.dp)) }
                Text(t.text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun DaySummaryCard(e: GameEngine, modifier: Modifier = Modifier) {
    val r = e.ui.summary ?: return
    Column(
        modifier.width(300.dp).shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color(0xE65A6370)).clickable { e.ui.summary = null },
    ) {
        PanelHeader("Day ${r.day} summary")
        Column(Modifier.padding(10.dp)) {
            SummaryRow("TOTAL PROFIT", e.money(r.profit), if (r.profit >= 0) HC.GreenLight else Color(0xFFFF8A8A))
            SummaryRow("INCOME", e.money(r.revenue), Color.White)
            SummaryRow("EXPENSES", e.money(r.expenses.values.sum()), Color.White)
            SummaryRow("BUSINESSES", "${r.businesses}", Color.White)
            SummaryRow("NET WORTH", e.money(r.netWorth), Color.White)
        }
    }
}

@Composable
private fun SummaryRow(k: String, v: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(v, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SkipOverlay(e: GameEngine) {
    val job = e.skip ?: return
    val s = e.state
    Box(Modifier.fillMaxSize().background(Color(0xB0101318)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.clock, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Text("${s.dayName} (Day ${s.day})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(job.title, color = Color.White.copy(alpha = .8f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            val progress = ((s.minute - job.start).toFloat() / (job.end - job.start).coerceAtLeast(1)).coerceIn(0f, 1f)
            Bar(progress, HC.Blue, Modifier.padding(top = 10.dp).width(240.dp), height = 6.dp)
            if (job.cancellable) HButton("Cancel", Modifier.padding(top = 14.dp).width(240.dp), color = HC.Red) { e.cancelSkip() }
        }
    }
}

@Composable
fun Dialogs(e: GameEngine) {
    val confirm = e.ui.confirm
    if (confirm != null) {
        Box(Modifier.fillMaxSize().background(HC.Scrim).clickable { e.ui.confirm = null }, contentAlignment = Alignment.Center) {
            Column(Modifier.width(360.dp).shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(HC.Card).clickable(enabled = false) {}.padding(16.dp)) {
                Text(confirm.title, style = HT.title, color = HC.Ink)
                Text(confirm.text, style = HT.body, color = HC.InkSoft, modifier = Modifier.padding(vertical = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    HButton("Cancel", color = Color(0xFF8A939E)) { e.ui.confirm = null }
                    Spacer(Modifier.width(8.dp))
                    HButton(confirm.confirmLabel, color = HC.Red) { e.ui.confirm = null; confirm.action() }
                }
            }
        }
    }
    val info = e.ui.info
    if (info != null) {
        Box(Modifier.fillMaxSize().background(HC.Scrim).clickable { e.ui.info = null }, contentAlignment = Alignment.Center) {
            Column(Modifier.width(380.dp).shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(HC.Card).padding(16.dp)) {
                Text(info.first, style = HT.title, color = HC.Ink)
                Text(info.second, style = HT.body, color = HC.InkSoft, modifier = Modifier.padding(vertical = 10.dp))
                HButton("OK", Modifier.align(Alignment.End)) { e.ui.info = null }
            }
        }
    }
}

/** Distance helper used by cards. */
fun distanceTo(e: GameEngine, x: Float, y: Float): Float {
    val (fx, fy) = e.focusPoint()
    return hypot(x - fx, y - fy)
}

// ------------------------------------------------------------------ tips & endings

@Composable
fun TipCard(e: GameEngine) {
    val tip = e.ui.tip ?: return
    Row(
        Modifier.padding(bottom = 6.dp).widthIn(max = 460.dp).shadow(6.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
            .background(Color(0xF22F80ED)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon("lightbulb", 24.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(tip.first, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(tip.second, color = Color.White.copy(alpha = .92f), fontSize = 11.sp, lineHeight = 14.sp)
        }
        HButton("Got it", small = true, color = Color(0xFF1C64C8)) { e.ui.tip = null }
    }
}

@Composable
fun EndingOverlay(e: GameEngine) {
    val kind = e.ui.ending ?: return
    val s = e.state
    val win = kind == "victory"
    Box(Modifier.fillMaxSize().background(Color(0xD9101318)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 520.dp).shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(HC.Card).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GIcon(if (win) "trophy" else "chart_down", 52.dp, tint = if (win) HC.Orange else HC.Red)
            Text(if (win) "You own Hustle City!" else "Bankrupt", style = HT.big, color = if (win) HC.Green else HC.Red)
            Text(
                if (win) "You're the richest entrepreneur in town, ahead of every rival. Uncle Sal is speechless."
                else "Hudson Bank closed your accounts after three days deep in the red. Every empire has a rough start.",
                style = HT.body, color = HC.InkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                EndStat("Days", "${s.day}")
                EndStat("Peak net worth", e.moneyShort(maxOf(s.peakNetWorth, e.netWorth())))
                EndStat("Total sales", e.moneyShort(s.player.totalEarned))
                EndStat("Rank", "#${e.rank()}")
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (win) {
                    HButton("Keep building", color = HC.Green) { e.ui.ending = null; e.speedIndex = 1; e.bump() }
                } else {
                    HButton("New game", color = HC.Blue) { e.ui.ending = null; e.ui.screen = com.hustlecity.game.engine.Screen.NEW_GAME }
                    HButton("Keep playing (sandbox)", color = HC.GlassLight) {
                        s.sandbox = true; s.gameOver = false; s.debtDays = 0
                        e.ui.ending = null; e.speedIndex = 1; e.bump()
                    }
                }
            }
        }
    }
}

@Composable
private fun EndStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = HT.title, color = HC.Ink)
        Text(label, style = HT.small, color = HC.InkSoft)
    }
}

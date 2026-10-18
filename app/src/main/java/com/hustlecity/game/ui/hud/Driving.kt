package com.hustlecity.game.ui.hud

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.refuel
import com.hustlecity.game.engine.roadside
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.City
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------ driving controls

/** Car controls: steering wheel, gas and brake pedals, a speedometer and the Park / Get out buttons. */
@Composable
fun BoxScope.DrivingControls(e: GameEngine) {
    if (e.skip != null) return
    val v = e.state.vehicle(e.state.player.driving) ?: return
    val left = e.settings.leftHanded
    if (e.settings.carControls == 0) {
        SteeringWheel(e, Modifier.align(if (left) Alignment.BottomEnd else Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 14.dp))
        Pedals(e, Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(end = if (left) 0.dp else 72.dp, start = if (left) 72.dp else 0.dp, bottom = 12.dp))
    } else {
        Joystick(e, Modifier.align(if (left) Alignment.BottomEnd else Alignment.BottomStart).padding(18.dp))
        Pedal("BRAKE\nREVERSE", HC.RedDark, 86.dp, 86.dp, { e.input.brake = it },
            Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(end = if (left) 0.dp else 76.dp, start = if (left) 76.dp else 0.dp, bottom = 18.dp))
    }
    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            if (e.atGasStation) HButton("Refuel ${e.money((1 - v.fuel) * 45 * 1.4)}", color = HC.Orange, small = true) { e.refuel(v) }
            if (v.fuel <= 0.0) HButton("Roadside \$150", color = HC.Red, small = true) { e.roadside(v) }
            HButton("Park", color = HC.Green, small = true) { if (e.parkVehicle()) e.ui.phoneOpen = true }
            HButton("Get out", color = HC.Blue, small = true) { if (e.getOutOfCar()) e.ui.phoneOpen = true }
        }
        Dashboard(e)
    }
}

@Composable
private fun Dashboard(e: GameEngine) {
    val v = e.state.vehicle(e.state.player.driving) ?: return
    val kmh = abs(e.carSpeed) * 3.6f
    val maxKmh = (v.def.maxSpeed * 3.6f / 10f).roundToInt() * 10f + 10f
    val gear = when { e.carSpeed < -.3f -> "R"; abs(e.carSpeed) < .3f -> "N"; else -> "D" }
    Row(
        Modifier.shadow(5.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xE61B1F25)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // speedometer dial
        Box(Modifier.size(width = 72.dp, height = 44.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.width * .46f
                val cx = size.width / 2; val cy = size.height * .92f
                drawArc(Color(0x33FFFFFF), 180f, 180f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                val frac = (kmh / maxKmh).coerceIn(0f, 1f)
                drawArc(if (frac > .85f) Color(0xFFEB5757) else Color(0xFF6FCF97), 180f, 180f * frac, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                val a = Math.toRadians(180.0 + 180.0 * frac)
                drawLine(Color.White, Offset(cx, cy), Offset(cx + cos(a).toFloat() * r * .82f, cy + sin(a).toFloat() * r * .82f), 2.5.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White, 3.dp.toPx(), Offset(cx, cy))
            }
        }
        Column(Modifier.padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${kmh.roundToInt()}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(" km/h", color = Color(0xAAFFFFFF), fontSize = 9.sp, modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (gear == "R") HC.Red else Color(0x33FFFFFF)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text(gear, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(v.def.name.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⛽", fontSize = 9.sp)
                com.hustlecity.game.ui.Bar(v.fuel.toFloat(), if (v.fuel < .15) HC.Red else HC.Orange, Modifier.width(54.dp).padding(start = 3.dp), height = 4.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔧", fontSize = 9.sp)
                com.hustlecity.game.ui.Bar(v.condition.toFloat(), if (v.condition < .3) HC.Red else HC.GreenLight, Modifier.width(54.dp).padding(start = 3.dp), height = 4.dp)
            }
            Text("📦 ${v.cargo.size}/${v.def.cargo}", color = Color(0xCCFFFFFF), fontSize = 9.sp)
        }
    }
}

/** Drag left or right to steer; the wheel springs back when you let go. */
@Composable
private fun SteeringWheel(e: GameEngine, modifier: Modifier) {
    var steer by remember { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(steer, tween(60), label = "wheel")
    Box(
        modifier.size(136.dp).pointerInput(Unit) {
            val reach = size.width * .42f
            awaitEachGesture {
                try {
                    val down = awaitFirstDown()
                    down.consume()
                    val start = down.position.x
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        steer = ((ch.position.x - start) / reach).coerceIn(-1f, 1f)
                        e.input.steer = steer
                        ch.consume()
                    }
                } finally {
                    steer = 0f
                    e.input.steer = 0f
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 - 6.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Color(0x33000000), r + 4.dp.toPx(), center)
            rotate(shown * 110f, center) {
                drawCircle(Color(0xFF2B2F36), r, center, style = Stroke(14.dp.toPx()))
                drawCircle(Color(0x55FFFFFF), r + 6.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                // spokes and hub
                drawLine(Color(0xFF3A3F47), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 10.dp.toPx())
                drawLine(Color(0xFF3A3F47), center, Offset(center.x, center.y + r), 10.dp.toPx())
                drawCircle(Color(0xFF4A505A), r * .3f, center)
                drawCircle(Color(0xFFEB5757), 4.dp.toPx(), Offset(center.x, center.y - r))
            }
        }
        Text("◀  STEER  ▶", color = Color(0xCCFFFFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Pedals(e: GameEngine, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Pedal("BRAKE\n(hold to\nreverse)", HC.RedDark, 66.dp, 92.dp, { e.input.brake = it })
        Pedal("GAS", HC.Green, 74.dp, 118.dp, { e.input.gas = it })
    }
}

/** A hold-to-press pedal. */
@Composable
private fun Pedal(label: String, color: Color, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, set: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.size(w, h).clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = if (pressed) 1f else .8f), color.copy(alpha = if (pressed) .85f else .55f))))
            .border(2.dp, Color.White.copy(alpha = if (pressed) .9f else .5f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    try {
                        awaitFirstDown().consume()
                        pressed = true
                        set(true)
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.none { it.pressed }) break
                        }
                    } finally {
                        pressed = false
                        set(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}

// ------------------------------------------------------------------ GPS

/** Turn-by-turn directions while a destination is set. */
@Composable
fun GpsBanner(e: GameEngine, modifier: Modifier = Modifier) {
    val gps = e.gps
    if (!gps.active || e.state.player.inBuilding >= 0) return
    val dest = e.city.building(e.state.player.waypoint) ?: return
    Row(
        modifier.widthIn(max = 380.dp).shadow(5.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color(0xF01C64C8)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(when (gps.turn) { -1 -> "↰"; 1 -> "↱"; else -> "🏁" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(start = 8.dp).weight(1f, fill = false)) {
            Text(
                if (gps.turn == 0) "${meters(gps.distance)} to ${e.buildingName(dest)}" else "In ${meters(gps.turnDistance)}: ${gps.instruction()}",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text("${e.buildingName(dest)} · ${meters(gps.distance)} · ${eta(e, gps.distance)}", color = Color(0xDDFFFFFF), fontSize = 10.sp, maxLines = 1)
        }
        Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp).clip(CircleShape).clickable { e.state.player.waypoint = -1; e.gps.clear(); e.bump() }.padding(4.dp))
    }
}

private fun meters(m: Float) = if (m >= 1000) "%.1f km".format(m / 1000f) else "${(m / 10f).roundToInt() * 10} m"

private fun eta(e: GameEngine, m: Float): String {
    val speed = if (e.state.player.driving >= 0) 10f else 4.5f
    val s = (m / speed).roundToInt()
    return if (s < 60) "${s}s" else "${s / 60} min"
}

// ------------------------------------------------------------------ minimap

/** A small map that turns with the camera: roads, your businesses, the GPS route and your destination. Tap to open Maps. */
@Composable
fun MiniMap(e: GameEngine, modifier: Modifier = Modifier) {
    val size = 128.dp
    Box(
        modifier.size(size).shadow(6.dp, CircleShape).clip(CircleShape).background(Color(0xFF39404A))
            .border(3.dp, Color(0xDDFFFFFF), CircleShape).clickable { e.ui.app = AppId.MAPS },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            e.ui.frameTime // redraw every frame
            val s = e.state
            val (fx, fy) = e.focusPoint()
            val scale = size.toPx() / 260f // pixels per meter: about 260 m across
            val cxp = this.size.width / 2; val cyp = this.size.height / 2
            val yawDeg = Math.toDegrees(e.camera.yaw.toDouble()).toFloat()
            fun sx(x: Float) = cxp + (x - fx) * scale
            fun sy(y: Float) = cyp + (y - fy) * scale
            val range = 190f
            rotate(yawDeg, Offset(cxp, cyp)) {
                // blocks
                drawRect(Color(0xFF8D949C), Offset(sx(-City.AV_HALF - City.SIDEWALK), sy(-City.ST_HALF - City.SIDEWALK)),
                    Size((City.WIDTH + 2 * (City.AV_HALF + City.SIDEWALK)) * scale, (City.HEIGHT + 2 * (City.ST_HALF + City.SIDEWALK)) * scale))
                for (b in e.city.buildings) {
                    if (abs(b.cx - fx) > range || abs(b.cy - fy) > range) continue
                    val col = when {
                        b.id == s.player.waypoint -> Color(0xFF2F80ED)
                        s.businessAt(b.id) != null -> Color(0xFF27AE60)
                        b.id == s.player.home -> Color(0xFF6FCF97)
                        b.kind == BKind.PARK -> Color(0xFF6DA35C)
                        b.kind == BKind.PARKING || b.kind == BKind.GAS -> Color(0xFF6B7078)
                        b.placeId != null -> Color(0xFFE0B04A)
                        else -> Color(0xFF59606B)
                    }
                    drawRect(col, Offset(sx(b.minX), sy(b.minY)), Size(b.width * scale, b.depth * scale))
                }
                // roads
                val road = Color(0xFF2B3036)
                for (i in 0 until City.AVENUES) {
                    val x = City.avenueX(i)
                    if (abs(x - fx) > range + 20) continue
                    drawRect(road, Offset(sx(x - City.AV_HALF), sy(fy - range)), Size(City.AV_HALF * 2 * scale, range * 2 * scale))
                }
                for (k in 0 until City.STREETS) {
                    val y = City.streetY(k)
                    if (abs(y - fy) > range + 20) continue
                    drawRect(road, Offset(sx(fx - range), sy(y - City.ST_HALF)), Size(range * 2 * scale, City.ST_HALF * 2 * scale))
                }
                // GPS route
                val gps = e.gps
                if (gps.active) {
                    val p = Path()
                    for (q in 0 until gps.points) {
                        val x = sx(gps.route[q * 2]); val y = sy(gps.route[q * 2 + 1])
                        if (q == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, Color(0xFF56B4FF), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                // owned cars
                for (v in s.vehicles) if (v.id != s.player.driving) drawCircle(Color(0xFF27AE60), 3.dp.toPx(), Offset(sx(v.x), sy(v.y)))
                // destination pin
                e.city.building(s.player.waypoint)?.let { w ->
                    val px = sx(w.doorX).coerceIn(cxp - this.size.width * .45f, cxp + this.size.width * .45f)
                    val py = sy(w.doorY).coerceIn(cyp - this.size.height * .45f, cyp + this.size.height * .45f)
                    drawCircle(Color.White, 6.dp.toPx(), Offset(px, py))
                    drawCircle(Color(0xFF2F80ED), 4.5.dp.toPx(), Offset(px, py))
                }
            }
            // player arrow (the map turns with the camera, so the arrow turns relative to it)
            val heading = (if (s.player.driving >= 0) s.vehicle(s.player.driving)?.heading else s.player.heading) ?: 0f
            rotate(Math.toDegrees((heading + e.camera.yaw).toDouble()).toFloat(), Offset(cxp, cyp)) {
                translate(cxp, cyp) {
                    val a = Path()
                    val u = 7.dp.toPx()
                    a.moveTo(0f, -u * 1.3f); a.lineTo(u, u); a.lineTo(0f, u * .45f); a.lineTo(-u, u); a.close()
                    drawPath(a, Color.White)
                    drawPath(a, Color(0xFF27AE60), style = Stroke(1.5.dp.toPx()))
                }
            }
            // north marker on the rim
            val na = Math.toRadians((yawDeg - 90).toDouble())
            val nr = this.size.width / 2 - 9.dp.toPx()
            drawCircle(Color(0xFFEB5757), 7.dp.toPx(), Offset(cxp + cos(na).toFloat() * nr, cyp + sin(na).toFloat() * nr))
        }
        Text("N", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).offset {
                // over the red north dot on the rim
                val na = Math.toRadians(Math.toDegrees(e.camera.yaw.toDouble()) - 90)
                val nr = (size / 2 - 9.dp).toPx()
                androidx.compose.ui.unit.IntOffset((cos(na) * nr).roundToInt(), (sin(na) * nr).roundToInt())
            })
    }
}

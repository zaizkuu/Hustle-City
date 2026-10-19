package com.hustlecity.game.ui.apps

import com.hustlecity.game.ui.icons.GIcon
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.ui.Dot
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.Renderers
import com.hustlecity.game.ui.hud.BuildingCard
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.toColor
import com.hustlecity.game.world.City

@Composable
fun MapsOverlay(e: GameEngine, @Suppress("UNUSED_PARAMETER") r: Renderers) {
    var query by remember { mutableStateOf("") }
    val f = e.ui.mapFilters
    Box(Modifier.fillMaxSize()) {
        // selected building
        if (e.ui.selected >= 0) {
            BuildingCard(e, Modifier.align(Alignment.TopStart).padding(10.dp), avoidPhone = false)
        } else {
            Text(
                "Drag to pan · pinch to zoom · tap a building",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(5.dp)).background(HC.Glass).padding(8.dp),
            )
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(8.dp).width(250.dp).fillMaxHeight().shadow(6.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)).background(HC.GlassDark),
        ) {
            Text("MAP FILTERS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().background(Color(0xFF3A414C)).padding(10.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0xFF59616D)).padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Find a place…", color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 13.sp))
                    }
                    Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                if (query.length >= 2) {
                    val q = query.lowercase()
                    val hits = e.city.buildings.filter { b -> e.buildingName(b).lowercase().contains(q) || b.address.lowercase().contains(q) }.take(8)
                    for (b in hits) {
                        Text(
                            "${e.buildingName(b)} · ${b.address}", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                e.camera.mapX = b.cx; e.camera.mapY = b.cy; e.camera.mapDist = 320f
                                e.ui.selected = b.id
                                query = ""
                            }.padding(vertical = 6.dp),
                        )
                    }
                }
                FilterRow("Enable / disable all", null, f.values.any { it }) { on ->
                    for (n in e.city.neighborhoods) f["hood:${n.id}"] = on
                    for (t in Catalog.customerTypes) f["type:${t.id}"] = on
                    f["rent"] = on
                }
                FilterRow("Shops & services", null, f["places"] != false) { f["places"] = it }
                FilterRow("For rent", HC.Green, f["rent"] == true) { f["rent"] = it }
                Section("Neighborhoods")
                for (n in e.city.neighborhoods) {
                    FilterRow(n.name, n.color.toColor(), f["hood:${n.id}"] == true, onGo = {
                        // fly to the middle of the neighborhood
                        val inHood = e.city.buildings.filter { it.hood.id == n.id }
                        if (inHood.isNotEmpty()) {
                            e.camera.mapX = inHood.sumOf { it.cx.toDouble() }.toFloat() / inHood.size
                            e.camera.mapY = inHood.sumOf { it.cy.toDouble() }.toFloat() / inHood.size
                        }
                        e.camera.mapDist = 700f
                    }) { f["hood:${n.id}"] = it }
                }
                Section("Active businesses")
                for (t in Catalog.customerTypes) FilterRow(t.name, null, f["type:${t.id}"] == true, icon = t.icon) { f["type:${t.id}"] = it }
                Spacer(Modifier.height(8.dp))
            }
            HButton("Close maps", Modifier.fillMaxWidth().padding(8.dp), color = HC.Orange) { e.ui.app = null; e.ui.selected = -1 }
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t.uppercase(), color = HC.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun FilterRow(label: String, dot: Color?, on: Boolean, onGo: (() -> Unit)? = null, icon: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dot != null) Dot(dot, 10.dp)
        if (icon != null) GIcon(icon, 16.dp)
        Text(label.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        if (onGo != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp).clickable(onClick = onGo))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = HC.Green), modifier = Modifier.padding(0.dp))
    }
}

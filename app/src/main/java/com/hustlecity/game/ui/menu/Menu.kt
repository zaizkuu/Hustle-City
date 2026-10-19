package com.hustlecity.game.ui.menu

import com.hustlecity.game.ui.icons.GIcon
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.SHIRT_COLORS
import com.hustlecity.game.engine.SKIN_TONES
import com.hustlecity.game.engine.Screen
import com.hustlecity.game.model.GameRules
import com.hustlecity.game.model.PlayerState
import com.hustlecity.game.ui.Chip
import com.hustlecity.game.ui.GameScreen
import com.hustlecity.game.ui.HButton
import com.hustlecity.game.ui.Renderers
import com.hustlecity.game.ui.WorldCanvas
import com.hustlecity.game.ui.hud.Avatar
import com.hustlecity.game.ui.hud.Dialogs
import com.hustlecity.game.ui.places.SettingsPanel
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT
import com.hustlecity.game.ui.theme.HustleTheme
import com.hustlecity.game.ui.toColor
import com.hustlecity.game.render.MapPreview
import com.hustlecity.game.world.City
import com.hustlecity.game.world.MapDef
import com.hustlecity.game.world.Maps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Composable
fun HustleCityApp(e: GameEngine, onExit: () -> Unit) {
    HustleTheme {
        @Suppress("UNUSED_VARIABLE") val tick = e.ui.tick
        BackHandler { handleBack(e, onExit) }
        when (e.ui.screen) {
            Screen.GAME -> if (e.hasGame) GameScreen(e) else MainMenu(e)
            Screen.MENU -> MainMenu(e)
            Screen.NEW_GAME -> NewGameScreen(e)
        }
    }
}

private fun handleBack(e: GameEngine, onExit: () -> Unit) {
    val ui = e.ui
    when {
        ui.screen == Screen.NEW_GAME -> ui.screen = Screen.MENU
        ui.screen == Screen.MENU -> if (ui.info != null) ui.info = null else if (ui.panel != null) { e.settings.save(); ui.panel = null } else onExit()
        e.skip != null -> e.cancelSkip()
        ui.confirm != null -> ui.confirm = null
        ui.info != null -> ui.info = null
        ui.panel != null -> { e.settings.save(); ui.panel = null }
        ui.designer -> { ui.designer = false; e.interior?.cancelGhost() }
        ui.app != null -> { ui.app = null; ui.selected = -1 }
        ui.selected >= 0 -> ui.selected = -1
        else -> ui.panel = com.hustlecity.game.engine.Panel.Settings
    }
    e.bump()
}

private val titleStyle = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(2f, 3f), 8f))

@Composable
fun MainMenu(e: GameEngine) {
    if (!e.hasGame) e.prepareMenuBackground()
    val renderers = remember { Renderers(e) }
    val hasSave = remember(e.ui.screen) { e.saves.hasSave() }
    Box(Modifier.fillMaxSize()) {
        WorldCanvas(e, renderers, interactive = false)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE015171C), Color(0x6015171C), Color(0x0015171C)))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 48.dp).width(320.dp)) {
            Text("HUSTLE", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black, style = titleStyle, lineHeight = 52.sp)
            Text("CITY", color = HC.Yellow, fontSize = 52.sp, fontWeight = FontWeight.Black, style = titleStyle, lineHeight = 52.sp)
            Text("Start with nothing. Build an empire.", color = Color.White.copy(alpha = .85f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
            if (e.hasGame) {
                HButton("Continue", Modifier.fillMaxWidth().padding(bottom = 8.dp), color = HC.Green) { e.camera.yaw = 0f; e.ui.screen = Screen.GAME }
            } else if (hasSave) {
                HButton("Continue", Modifier.fillMaxWidth().padding(bottom = 8.dp), color = HC.Green) {
                    if (!e.loadGame()) {
                        e.ui.info = "Couldn't load your save" to
                            "The save file is damaged or from an incompatible version. It was kept as save-damaged.json. Start a new game to keep playing."
                    }
                }
            }
            HButton("New game", Modifier.fillMaxWidth().padding(bottom = 8.dp)) { e.ui.screen = Screen.NEW_GAME }
            HButton("Settings", Modifier.fillMaxWidth(), color = HC.GlassLight) { e.ui.panel = com.hustlecity.game.engine.Panel.Settings }
            Text("v1.6.0 beta · A business life sim for Android", color = Color.White.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
        }
        if (e.ui.panel == com.hustlecity.game.engine.Panel.Settings) SettingsPanel(e)
        Dialogs(e)
    }
}

@Composable
fun NewGameScreen(e: GameEngine) {
    var name by remember { mutableStateOf("") }
    var skin by remember { mutableIntStateOf(0) }
    var shirt by remember { mutableIntStateOf(0) }
    var difficulty by remember { mutableIntStateOf(1) }
    var seed by remember { mutableIntStateOf(1337) }
    var mapId by remember { mutableStateOf(Maps.CLASSIC) }
    var rules by remember { mutableStateOf(GameRules.NORMAL) }
    val renderers = remember { Renderers(e) }
    Box(Modifier.fillMaxSize()) {
        WorldCanvas(e, renderers, interactive = false)
        Box(Modifier.fillMaxSize().background(Color(0x99101318)), contentAlignment = Alignment.Center) {
            Row(
                Modifier.padding(12.dp).width(760.dp).shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F8FA)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // who you are
                Column(Modifier.width(232.dp).verticalScroll(rememberScrollState())) {
                    Text("NEW GAME", style = HT.label, color = HC.InkSoft)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(PlayerState(name = name, skin = skin, shirt = shirt), 64.dp)
                        Text(name.ifBlank { "Your name" }, style = HT.title, color = HC.Ink, maxLines = 2, modifier = Modifier.padding(start = 10.dp))
                    }
                    Row(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Color(0xFFE6E9ED)).padding(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            if (name.isEmpty()) Text("Enter your name", color = HC.InkSoft, fontSize = 15.sp)
                            BasicTextField(name, { name = it.take(20) }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = HC.Ink))
                        }
                        GIcon("dice", 20.dp, tint = HC.InkSoft, modifier = Modifier.clickable {
                            name = "${Catalog.firstNames.random()} ${Catalog.lastNames.random()}"
                        })
                    }
                    Text("SKIN TONE", style = HT.label, color = HC.InkSoft)
                    Row(Modifier.padding(vertical = 6.dp)) { SKIN_TONES.forEachIndexed { i, c -> Swatch(c.toColor(), skin == i) { skin = i } } }
                    Text("OUTFIT", style = HT.label, color = HC.InkSoft)
                    Row(Modifier.padding(vertical = 6.dp)) { SHIRT_COLORS.forEachIndexed { i, c -> Swatch(c.toColor(), shirt == i) { shirt = i } } }
                }
                // where and how hard
                Column(Modifier.weight(1f)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text("CITY", style = HT.label, color = HC.InkSoft)
                        Row(Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (m in Maps.all) CityCard(m, mapId == m.id) { mapId = m.id }
                        }
                        Text(Maps.byId(mapId).blurb, style = HT.small, color = HC.InkSoft)
                        if (mapId == Maps.CLASSIC) Row(Modifier.padding(top = 6.dp)) {
                            Chip("Classic layout", seed == 1337) { seed = 1337 }
                            Chip("Random city", seed != 1337) { seed = (1000..999_999).random() }
                        }
                        Text("DIFFICULTY", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Chip("Easy · \$20,000", difficulty == 0, HC.Green) { difficulty = 0; rules = GameRules.EASY }
                            Chip("Normal · \$5,000", difficulty == 1) { difficulty = 1; rules = GameRules.NORMAL }
                            Chip("Hard · \$1,500", difficulty == 2, HC.Red) { difficulty = 2; rules = GameRules.HARD }
                            Chip("Custom", difficulty == 3, HC.Purple) { difficulty = 3 }
                        }
                        Text(when (difficulty) {
                            0 -> "More customers, cheaper rent and staff, softer loans and gentler rivals."
                            2 -> "Fewer customers, dearer rent and staff, steep loans and hungry rivals."
                            3 -> "Set every rule yourself."
                            else -> "The city as it is."
                        }, style = HT.small, color = HC.InkSoft)
                        if (difficulty == 3) Column(Modifier.padding(top = 4.dp)) {
                            RuleSlider("Starting cash", rules.startMoney, 0.0..50000.0, { "\$" + "%,d".format(it.toInt()) }) { rules = rules.copy(startMoney = Math.round(it / 500) * 500.0) }
                            RuleSlider("Customers", rules.demand, .5..1.5, ::pct) { rules = rules.copy(demand = snap(it)) }
                            RuleSlider("Rent", rules.rent, .5..1.5, ::pct) { rules = rules.copy(rent = snap(it)) }
                            RuleSlider("Wages", rules.wages, .5..1.5, ::pct) { rules = rules.copy(wages = snap(it)) }
                            RuleSlider("Loan interest", rules.interest, .25..2.0, ::pct) { rules = rules.copy(interest = snap(it)) }
                            RuleSlider("Rivals", rules.rivals, 0.0..2.0, { if (it < .025) "Off" else pct(it) }) { rules = rules.copy(rivals = snap(it)) }
                            RuleSlider("Tiredness", rules.needs, 0.0..1.5, { if (it < .025) "Off" else pct(it) }) { rules = rules.copy(needs = snap(it)) }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HButton("Back", color = HC.GlassLight) { e.ui.screen = Screen.MENU }
                        HButton("Start your hustle", color = HC.Green, modifier = Modifier.weight(1f)) {
                            e.newGame(name, skin, shirt, difficulty, if (mapId == Maps.CLASSIC) seed.toLong() else 1337L, mapId, rules)
                        }
                    }
                }
            }
        }
    }
}

private fun pct(v: Double) = "${Math.round(v * 100)}%"
private fun snap(v: Double) = Math.round(v * 20) / 20.0

/** One rule of a custom game: its name, a slider and the current value. */
@Composable
private fun RuleSlider(label: String, value: Double, range: ClosedFloatingPointRange<Double>, format: (Double) -> String, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HT.body, color = HC.Ink, modifier = Modifier.width(96.dp))
        Slider(
            value.toFloat(), { onChange(it.toDouble()) }, Modifier.weight(1f),
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            colors = SliderDefaults.colors(thumbColor = HC.Blue, activeTrackColor = HC.Blue, inactiveTrackColor = Color(0xFFD5DAE0)),
        )
        Text(format(value), style = HT.bodyBold, color = HC.Ink, modifier = Modifier.width(64.dp).padding(start = 8.dp))
    }
}

/** Thumbnails of every city, drawn once in the background and kept for the session. */
internal object CityThumbs {
    val cache = ConcurrentHashMap<String, ImageBitmap>()
    fun get(id: String): ImageBitmap = cache.getOrPut(id) { MapPreview.render(City(1337L, id), 320).asImageBitmap() }
}

private val CITY_TAGS = mapOf(
    Maps.CLASSIC to "The original grid",
    "nyc" to "Manhattan & Brooklyn",
    "manila" to "Bay, river & jeepneys",
    "tokyo" to "Keep left · Yamanote loop",
    "seoul" to "Han River & hanok",
)

/** A city to start in: its map, name and a one-line hook. */
@Composable
private fun CityCard(m: MapDef, selected: Boolean, onClick: () -> Unit) {
    val thumb by produceState(CityThumbs.cache[m.id], m.id) {
        if (value == null) value = withContext(Dispatchers.Default) { runCatching { CityThumbs.get(m.id) }.getOrNull() }
    }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.width(132.dp).clip(shape).background(if (selected) Color(0xFFE3EEFF) else Color(0xFFECEEF1))
            .border(if (selected) 2.dp else 1.dp, if (selected) HC.Blue else Color(0x1F000000), shape).clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF27445C)), contentAlignment = Alignment.Center) {
            val img = thumb
            if (img != null) Image(img, contentDescription = m.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("Drawing map…", style = HT.small, color = Color.White.copy(alpha = .7f))
        }
        Text(m.title, style = HT.bodyBold, color = HC.Ink, modifier = Modifier.padding(top = 5.dp))
        Text(CITY_TAGS[m.id] ?: "", style = HT.small, color = HC.InkSoft, maxLines = 1)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 4.dp).size(25.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) HC.Blue else Color(0x33000000), CircleShape).clickable(onClick = onClick),
    )
}

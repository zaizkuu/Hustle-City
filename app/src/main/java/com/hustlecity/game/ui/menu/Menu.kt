package com.hustlecity.game.ui.menu

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.SHIRT_COLORS
import com.hustlecity.game.engine.SKIN_TONES
import com.hustlecity.game.engine.Screen
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
            Text("v0.1 · A business life sim for Android", color = Color.White.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
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
    val renderers = remember { Renderers(e) }
    Box(Modifier.fillMaxSize()) {
        WorldCanvas(e, renderers, interactive = false)
        Box(Modifier.fillMaxSize().background(Color(0x99101318)), contentAlignment = Alignment.Center) {
            Row(
                Modifier.padding(16.dp).width(640.dp).shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F8FA)).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(PlayerState(name = name, skin = skin, shirt = shirt), 120.dp)
                    Text(name.ifBlank { "Your name" }, style = HT.title, color = HC.Ink, modifier = Modifier.padding(top = 8.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("NEW GAME", style = HT.label, color = HC.InkSoft)
                    Row(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Color(0xFFE6E9ED)).padding(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            if (name.isEmpty()) Text("Enter your name", color = HC.InkSoft, fontSize = 15.sp)
                            BasicTextField(name, { name = it.take(20) }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = HC.Ink))
                        }
                        Text("🎲", fontSize = 16.sp, modifier = Modifier.clickable {
                            name = "${Catalog.firstNames.random()} ${Catalog.lastNames.random()}"
                        })
                    }
                    Text("SKIN TONE", style = HT.label, color = HC.InkSoft)
                    Row(Modifier.padding(vertical = 6.dp)) { SKIN_TONES.forEachIndexed { i, c -> Swatch(c.toColor(), skin == i) { skin = i } } }
                    Text("OUTFIT", style = HT.label, color = HC.InkSoft)
                    Row(Modifier.padding(vertical = 6.dp)) { SHIRT_COLORS.forEachIndexed { i, c -> Swatch(c.toColor(), shirt == i) { shirt = i } } }
                    Text("STARTING CASH", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Chip("Easy · \$20,000", difficulty == 0, HC.Green) { difficulty = 0 }
                        Chip("Normal · \$5,000", difficulty == 1) { difficulty = 1 }
                        Chip("Hard · \$1,500", difficulty == 2, HC.Red) { difficulty = 2 }
                    }
                    Text("CITY", style = HT.label, color = HC.InkSoft, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Chip("Classic layout", seed == 1337) { seed = 1337 }
                        Chip("Random city", seed != 1337) { seed = (1000..999_999).random() }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HButton("Back", color = HC.GlassLight) { e.ui.screen = Screen.MENU }
                        HButton("Start your hustle", color = HC.Green, modifier = Modifier.weight(1f)) {
                            e.newGame(name, skin, shirt, difficulty, seed.toLong())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 6.dp).size(30.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) HC.Blue else Color(0x33000000), CircleShape).clickable(onClick = onClick),
    )
}

package com.hustlecity.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.render.Thumbnails
import com.hustlecity.game.ui.theme.HC
import com.hustlecity.game.ui.theme.HT

fun Int.toColor() = Color(this)

/** The game's own glyph for a phone app. */
fun appGlyph(id: AppId): String = when (id) {
    AppId.PERSONA -> "app_persona"
    AppId.CONTACTS -> "app_contacts"
    AppId.RIVALS -> "app_rivals"
    AppId.STAFF -> "app_staff"
    AppId.BIZHUB -> "app_bizhub"
    AppId.FINANCE -> "app_finance"
    AppId.REALTY -> "app_realty"
    AppId.FLEET -> "app_fleet"
    AppId.MARKETS -> "app_markets"
    AppId.MAPS -> "app_maps"
}

@Composable
fun AppIconTile(id: AppId, size: Dp, showLabel: Boolean = true, labelColor: Color = Color.White, badge: Int = 0, onClick: () -> Unit) {
    val (top, bottom) = HC.app[id.name] ?: (HC.Blue to HC.BlueDark)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { com.hustlecity.game.audio.Audio.play(com.hustlecity.game.audio.Sfx.TAP); onClick() }.padding(2.dp)) {
        Box {
            Box(
                Modifier.size(size).shadow(3.dp, RoundedCornerShape(size * .26f)).clip(RoundedCornerShape(size * .26f))
                    .background(Brush.verticalGradient(listOf(top, bottom))),
                contentAlignment = Alignment.Center,
            ) {
                com.hustlecity.game.ui.icons.GIcon(appGlyph(id), size * .56f)
            }
            if (badge > 0) {
                Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(HC.Red), contentAlignment = Alignment.Center) {
                    Text(if (badge > 9) "9+" else "$badge", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showLabel) Text(id.title, color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Translucent slate panel used across the HUD. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, color: Color = HC.Glass, corner: Dp = 6.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(corner)).background(color), content = content)
}

/** Light header bar with dark uppercase title, like the objective / phone headers. */
@Composable
fun PanelHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().background(HC.Header).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = HT.label, color = HC.HeaderText, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

@Composable
fun HButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HC.Blue,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    small: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = if (enabled) color else Color(0xFF9AA2AC)
    Row(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 1f), bg.darken(.88f))))
            .border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(5.dp))
            .clickable(enabled = enabled) { com.hustlecity.game.audio.Audio.play(com.hustlecity.game.audio.Sfx.TAP); onClick() }
            .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 5.dp else 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(if (small) 14.dp else 16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text.uppercase(), color = textColor, fontSize = if (small) 10.sp else 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp, maxLines = 1)
    }
}

fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)

@Composable
fun WhiteCard(modifier: Modifier = Modifier, padding: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.shadow(4.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(HC.Card).padding(padding),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = HC.Ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text.uppercase(), style = HT.label, color = HC.Ink)
    }
}

/** Rounded progress bar. */
@Composable
fun Bar(value: Float, color: Color, modifier: Modifier = Modifier, track: Color = Color(0x33FFFFFF), height: Dp = 6.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun LabeledBar(label: String, value: Float, color: Color, valueText: String = "${(value * 100).toInt()}%") {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HT.small, color = HC.Ink, modifier = Modifier.width(96.dp))
        Bar(value, color, Modifier.weight(1f), track = HC.Line, height = 10.dp)
        Text(valueText, style = HT.small, color = color.darken(.85f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = HC.Ink, bold: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, style = HT.body, color = HC.InkSoft, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) HT.bodyBold else HT.body, color = valueColor)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, color: Color = HC.Blue, icon: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.padding(end = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) color else Color(0xFFE8ECF1))
            .clickable { com.hustlecity.game.audio.Audio.play(com.hustlecity.game.audio.Sfx.TAP); onClick() }.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) com.hustlecity.game.ui.icons.GIcon(icon, 13.dp, Modifier.padding(end = 4.dp), tint = if (selected) Color.White else HC.Ink)
        Text(text, color = if (selected) Color.White else HC.Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun ItemThumb(id: String, size: Dp, modifier: Modifier = Modifier) {
    val px = (size.value * 2.5f).toInt().coerceIn(48, 320)
    val bmp = remember(id, px) { Thumbnails.item(id, px).asImageBitmap() }
    Image(bmp, null, modifier.size(size))
}

@Composable
fun VehicleThumb(id: String, size: Dp, modifier: Modifier = Modifier) {
    val px = (size.value * 2.5f).toInt().coerceIn(48, 360)
    val bmp = remember(id, px) { Thumbnails.vehicle(id, px).asImageBitmap() }
    Image(bmp, null, modifier.size(size))
}

/** A product's own illustration (flowers, cans, bottles, phones…), or a colored box for products without one. */
@Composable
fun ProductThumb(p: com.hustlecity.game.data.ProductDef, size: Dp, modifier: Modifier = Modifier) {
    val id = "product_${p.id}"
    if (com.hustlecity.game.ui.icons.GameIcons.has(id)) com.hustlecity.game.ui.icons.GIcon(id, size, modifier)
    else ProductThumb(p.color, size, modifier)
}

@Composable
fun ProductThumb(color: Int, size: Dp, modifier: Modifier = Modifier) {
    val px = (size.value * 2.5f).toInt().coerceIn(48, 240)
    val bmp = remember(color, px) { Thumbnails.productBox(color, px).asImageBitmap() }
    Image(bmp, null, modifier.size(size))
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−", onMinus)
        Text(value, style = HT.bodyBold, color = HC.Ink, textAlign = TextAlign.Center, modifier = Modifier.width(62.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        StepBtn("+", onPlus)
    }
}

@Composable
private fun StepBtn(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE3E8EE)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = HC.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SectionLabel(text: String, color: Color = Color.White.copy(alpha = .75f), modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(1.dp).background(color))
        Text("  $text  ", color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.dp).background(color))
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = HT.body, color = HC.InkSoft, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(24.dp))
}

val PaddingSmall = PaddingValues(8.dp)

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun TitleText(text: String, style: TextStyle = HT.title, color: Color = HC.Ink) = Text(text, style = style, color = color)

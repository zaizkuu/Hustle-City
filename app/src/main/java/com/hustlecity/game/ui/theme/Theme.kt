package com.hustlecity.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HC {
    val Glass = Color(0xE6424A56)
    val GlassDark = Color(0xF02F353E)
    val GlassLight = Color(0xCC5A6370)
    val Header = Color(0xFFD7DCE2)
    val HeaderText = Color(0xFF2B3036)
    val Ink = Color(0xFF2B3036)
    val InkSoft = Color(0xFF6B7480)
    val Card = Color(0xFFFFFFFF)
    val CardAlt = Color(0xFFF3F5F8)
    val Line = Color(0xFFE1E5EA)
    val Blue = Color(0xFF2F80ED)
    val BlueDark = Color(0xFF1C64C8)
    val Green = Color(0xFF27AE60)
    val GreenLight = Color(0xFF6FCF97)
    val Red = Color(0xFFEB5757)
    val RedDark = Color(0xFFC94040)
    val Orange = Color(0xFFF2994A)
    val Yellow = Color(0xFFF2C94C)
    val Purple = Color(0xFF9B51E0)
    val Teal = Color(0xFF2BB3A6)
    val Energy = Color(0xFFF2994A)
    val Hunger = Color(0xFFEB5757)
    val Happy = Color(0xFF6FCF97)
    val AppBg = Brush.verticalGradient(listOf(Color(0xFF8D97A5), Color(0xFF6B7584), Color(0xFF59626F)))
    val AppHeader = Brush.verticalGradient(listOf(Color(0xFF7E95B5), Color(0xFF6A7F9D)))
    val Scrim = Color(0xB3101318)

    val app = mapOf(
        "PERSONA" to (Color(0xFF8A939E) to Color(0xFF6B737D)),
        "CONTACTS" to (Color(0xFF7ED67E) to Color(0xFF45B24A)),
        "RIVALS" to (Color(0xFF52C7C1) to Color(0xFF2A9D96)),
        "STAFF" to (Color(0xFFB48CF0) to Color(0xFF8A5BD8)),
        "BIZHUB" to (Color(0xFFF07070) to Color(0xFFD94848)),
        "FINANCE" to (Color(0xFF5AA8F5) to Color(0xFF2F80ED)),
        "REALTY" to (Color(0xFFFFB264) to Color(0xFFF2853A)),
        "FLEET" to (Color(0xFF7FA4C8) to Color(0xFF4F7499)),
        "MARKETS" to (Color(0xFF3DD6A3) to Color(0xFF15A37A)),
        "MAPS" to (Color(0xFFFFDC5C) to Color(0xFFF2B630)),
    )
}

object HT {
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    val small = TextStyle(fontSize = 11.sp)
    val body = TextStyle(fontSize = 13.sp)
    val bodyBold = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val big = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun HustleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = HC.Blue, secondary = HC.Green, background = Color(0xFF15171C), surface = Color(0xFF2F353E)),
        content = content,
    )
}

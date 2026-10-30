package com.hustlecity.game.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The game screen's size in the interface's (scaled) dp, for panels that size themselves to the space left. */
val LocalScreenSize = staticCompositionLocalOf { DpSize(800.dp, 400.dp) }

/** The screen height the HUD is laid out for: a phone in landscape shows all of it at its own size. */
private const val DESIGN_HEIGHT_DP = 600f

/**
 * How much larger than the platform's own dp the interface is drawn. Phones are left as they are; on tablets and
 * monitors the HUD grows with the screen so it keeps the proportions it has on a phone, instead of shrinking into
 * the corners of a big empty screen. [sizePct] is the player's Interface size setting (100 = fit the screen).
 */
fun uiScale(screenHeightDp: Float, sizePct: Int): Float =
    (screenHeightDp / DESIGN_HEIGHT_DP).coerceAtLeast(1f) * sizePct.coerceIn(70, 160) / 100f

/** Lays [content] out at the interface scale for this screen, and provides [LocalScreenSize]. */
@Composable
fun ScaledUi(sizePct: Int, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val scale = uiScale(maxHeight.value, sizePct)
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * scale, base.fontScale),
            LocalScreenSize provides DpSize(maxWidth / scale, maxHeight / scale),
        ) { content() }
    }
}

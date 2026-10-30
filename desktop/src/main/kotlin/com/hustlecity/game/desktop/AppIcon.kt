package com.hustlecity.game.desktop

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * The app icon, drawn from the same shapes as the Android launcher icon (app/src/main/res/drawable): a skyline and a
 * dollar coin on a blue gradient tile with rounded corners.
 */
object AppIcon {
    /** The launcher's 108-unit canvas shows its middle 76 units inside the tile. */
    private const val SIZE = 76f
    private const val INSET = 16f

    private val foreground = listOf(
        0xFFE8EEF6 to "M30,76 L30,46 L40,46 L40,76 Z",
        0xFFFFFFFF to "M42,76 L42,34 L54,34 L54,76 Z",
        0xFFE8EEF6 to "M56,76 L56,40 L66,40 L66,76 Z",
        0xFFCFDAE8 to "M68,76 L68,52 L78,52 L78,76 Z",
        0xFFF2994A to "M72,38 m-11,0 a11,11 0 1,0 22,0 a11,11 0 1,0 -22,0",
        0xFFFFFFFF to "M70.5,30 h3 v2.2 c2.2,0.4 3.6,1.8 3.8,3.8 h-3 c-0.2,-1 -1,-1.5 -2.3,-1.5 c-1.4,0 -2.2,0.6 -2.2,1.4 " +
            "c0,0.9 0.8,1.2 2.6,1.6 c2.9,0.6 5,1.5 5,4.2 c0,2.1 -1.6,3.6 -3.9,4 V48 h-3 v-2.3 c-2.5,-0.4 -4.1,-1.9 -4.2,-4.1 h3 " +
            "c0.1,1.1 1.1,1.8 2.6,1.8 c1.5,0 2.4,-0.6 2.4,-1.5 c0,-0.9 -0.8,-1.3 -2.7,-1.7 c-2.8,-0.6 -4.8,-1.5 -4.8,-4 c0,-2 1.5,-3.5 3.7,-3.9 Z",
        0xFF27AE60 to "M26,78 h56 v4 h-56 Z",
    )

    val vector: ImageVector by lazy {
        ImageVector.Builder("HustleCity", 256.dp, 256.dp, SIZE, SIZE).apply {
            addPath(addPathNodes("M10,0 h56 a10,10 0 0,1 10,10 v56 a10,10 0 0,1 -10,10 h-56 a10,10 0 0,1 -10,-10 v-56 a10,10 0 0,1 10,-10 Z"),
                fill = Brush.verticalGradient(listOf(Color(0xFF2F80ED), Color(0xFF1B3F8B)), 0f, SIZE))
            group(translationX = -INSET, translationY = -INSET + 2f) {
                for ((color, path) in foreground) addPath(addPathNodes(path), fill = SolidColor(Color(color)))
            }
        }.build()
    }
}

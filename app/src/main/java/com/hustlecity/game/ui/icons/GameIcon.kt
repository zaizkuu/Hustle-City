package com.hustlecity.game.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Layer color that takes the icon's tint (white glyphs on pins and app tiles, dark glyphs on light cards). */
const val TINT = 0L

/**
 * One shape of an icon: SVG path data on a 24×24 grid. [color] is ARGB (e.g. 0xFFE5484D) or [TINT]; [alpha]
 * fades the layer (secondary detail on glyphs); [stroke] > 0 draws an outline of that width instead of a fill;
 * [evenOdd] cuts holes where shapes overlap.
 */
class L(val d: String, val color: Long = TINT, val alpha: Float = 1f, val stroke: Float = 0f, val evenOdd: Boolean = false) {
    val path: Path by lazy {
        PathParser().parsePathString(d).toPath().also { if (evenOdd) it.fillType = PathFillType.EvenOdd }
    }
}

/** A game icon: [layers] drawn in order. Glyph icons use only [TINT] layers; color icons carry their own colors. */
class GameIcon(val id: String, val layers: List<L>) {
    val colored: Boolean get() = layers.any { it.color != TINT }
}

fun icon(id: String, vararg layers: L) = GameIcon(id, layers.toList())

/** A themed list of icons, filled in by its own file. */
interface IconSet {
    val icons: List<GameIcon>
}

/** Every icon in the game, by id. Unknown ids fall back to a neutral dot so a typo never crashes the UI. */
object GameIcons {
    private val sets: List<IconSet> get() = listOf(PlaceIcons, UiIcons, ProductIconsA, ProductIconsB)
    val all: Map<String, GameIcon> by lazy { sets.flatMap { it.icons }.associateBy { it.id } }
    private val fallback = icon("missing", L("M12 7a5 5 0 1 0 0 10a5 5 0 1 0 0-10z"))

    operator fun get(id: String): GameIcon = all[id] ?: fallback
    fun has(id: String) = id in all
}

/** Draws an icon at [size]; [TINT] layers use [tint]. */
@Composable
fun GIcon(icon: GameIcon, size: Dp, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            for (l in icon.layers) {
                val c = if (l.color == TINT) tint else Color(l.color.toInt())
                drawPath(
                    l.path, c, alpha = l.alpha,
                    style = if (l.stroke > 0f) Stroke(l.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill,
                )
            }
        }
    }
}

@Composable
fun GIcon(id: String, size: Dp, modifier: Modifier = Modifier, tint: Color = Color.White) = GIcon(GameIcons[id], size, modifier, tint)

/** A glyph on a colored badge: round like the map pins, or a rounded square like the BizPhone apps. */
@Composable
fun IconBadge(id: String, size: Dp, color: Color, modifier: Modifier = Modifier, round: Boolean = true, tint: Color = Color.White, glyph: Float = .6f) {
    val shape = if (round) CircleShape else RoundedCornerShape(size * .26f)
    Box(
        modifier.size(size).shadow(size * .06f, shape).clip(shape)
            .background(Brush.verticalGradient(listOf(lighten(color, .12f), darken(color, .82f)))),
        contentAlignment = Alignment.Center,
    ) { GIcon(id, size * glyph, tint = tint) }
}

private fun lighten(c: Color, f: Float) = Color(c.red + (1f - c.red) * f, c.green + (1f - c.green) * f, c.blue + (1f - c.blue) * f, c.alpha)
private fun darken(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

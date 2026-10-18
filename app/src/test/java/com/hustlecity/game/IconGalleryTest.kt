package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.hustlecity.game.ui.icons.GameIcons
import com.hustlecity.game.ui.icons.IconCanvas
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every game icon through the in-game renderer into contact sheets (build/snapshots/icons_*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconGalleryTest {
    private val outDir = File("build/snapshots").apply { mkdirs() }
    private val pinColors = intArrayOf(0xFFE8833A.toInt(), 0xFF2F80ED.toInt(), 0xFF27AE60.toInt(), 0xFF9B51E0.toInt(), 0xFFEB5757.toInt(), 0xFF00897B.toInt())

    @Test
    fun everyProductAndBusinessHasItsOwnIcon() {
        val missing = com.hustlecity.game.data.Catalog.products.map { "product_${it.id}" }.filterNot { GameIcons.has(it) } +
            com.hustlecity.game.data.Catalog.businessTypes.map { "biz_${it.id}" }.filterNot { GameIcons.has(it) }
        assertTrue("missing icons: $missing", missing.isEmpty())
    }

    @Test
    fun everyIconParsesAndRenders() {
        val icons = GameIcons.all.values.toList()
        assertTrue("no icons registered", icons.isNotEmpty())
        val cols = 10
        val cell = 150
        for ((page, chunk) in icons.chunked(80).withIndex()) {
            val rows = (chunk.size + cols - 1) / cols
            val bmp = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(0xFFCFD5DB.toInt())
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; color = 0xFF1D2228.toInt(); textAlign = Paint.Align.CENTER }
            val card = Paint(Paint.ANTI_ALIAS_FLAG)
            for ((i, ic) in chunk.withIndex()) {
                val x = (i % cols) * cell + cell / 2f
                val y = (i / cols) * cell + 62f
                if (ic.colored) {
                    card.color = 0xFFFFFFFF.toInt()
                    canvas.drawRoundRect(x - 50f, y - 50f, x + 50f, y + 50f, 10f, 10f, card)
                    IconCanvas.draw(canvas, ic, x, y, 84f)
                } else {
                    IconCanvas.pin(canvas, ic.id, x, y - 6f, 40f, pinColors[i % pinColors.size])
                }
                canvas.drawText(ic.id, x, y + 78f, text)
            }
            File(outDir, "icons_$page.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

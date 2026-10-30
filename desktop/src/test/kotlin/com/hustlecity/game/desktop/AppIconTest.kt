package com.hustlecity.game.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders [AppIcon] into the installer's icon files: build/icons/hustle-city.ico (16 to 256 pixels) and .png.
 * When the icon changes, copy them over desktop/icons/.
 */
class AppIconTest {
    private val sizes = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    private fun render(size: Int): Image =
        ImageComposeScene(size, size, Density(1f)) {
            Image(rememberVectorPainter(AppIcon.vector), null, Modifier.fillMaxSize())
        }.use { it.render() }

    @Test
    fun exportIcons() {
        val dir = File(File(System.getProperty("snapshots.dir") ?: "build/snapshots").parentFile, "icons").apply { mkdirs() }
        val images = sizes.map { it to render(it) }
        File(dir, "hustle-city.png").writeBytes(images.last().second.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        File(dir, "hustle-city.ico").writeBytes(ico(images))
        // the tile is opaque in the middle and see-through in the rounded corners
        val bmp = org.jetbrains.skia.Bitmap.makeFromImage(images.last().second)
        assertTrue(bmp.getColor(128, 128) ushr 24 == 255)
        assertTrue(bmp.getColor(0, 0) ushr 24 == 0)
    }

    /** A Windows icon: 32-bit bitmaps for the small sizes, PNG for 256 (as Windows' own icons are stored). */
    private fun ico(images: List<Pair<Int, Image>>): ByteArray {
        val blobs = images.map { (size, img) -> if (size >= 256) img.encodeToData(EncodedImageFormat.PNG)!!.bytes else dib(size, img) }
        val out = ByteArrayOutputStream()
        val head = ByteBuffer.allocate(6 + 16 * images.size).order(ByteOrder.LITTLE_ENDIAN)
        head.putShort(0).putShort(1).putShort(images.size.toShort())
        var offset = 6 + 16 * images.size
        for ((i, pair) in images.withIndex()) {
            val size = pair.first
            head.put((if (size >= 256) 0 else size).toByte()).put((if (size >= 256) 0 else size).toByte())
            head.put(0).put(0).putShort(1).putShort(32).putInt(blobs[i].size).putInt(offset)
            offset += blobs[i].size
        }
        out.write(head.array())
        for (b in blobs) out.write(b)
        return out.toByteArray()
    }

    /** BITMAPINFOHEADER, bottom-up BGRA pixels and an (all-clear) AND mask. */
    private fun dib(size: Int, img: Image): ByteArray {
        val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
        val maskRow = ((size + 31) / 32) * 4
        val buf = ByteBuffer.allocate(40 + size * size * 4 + maskRow * size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(40).putInt(size).putInt(size * 2).putShort(1).putShort(32).putInt(0).putInt(size * size * 4)
            .putInt(0).putInt(0).putInt(0).putInt(0)
        for (y in size - 1 downTo 0) for (x in 0 until size) {
            val c = bmp.getColor(x, y) // unpremultiplied ARGB
            buf.put((c and 255).toByte()).put((c shr 8 and 255).toByte()).put((c shr 16 and 255).toByte()).put((c ushr 24).toByte())
        }
        return buf.array()
    }
}

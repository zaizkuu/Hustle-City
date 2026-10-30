package android.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** Desktop stand-in for Android's Bitmap: an RGBA Skia bitmap. */
class Bitmap internal constructor(val skia: org.jetbrains.skia.Bitmap) {
    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }
    enum class CompressFormat { PNG, JPEG, WEBP }

    val width: Int get() = skia.width
    val height: Int get() = skia.height

    fun getPixel(x: Int, y: Int): Int = skia.getColor(x, y)

    /** Copies a block of pixels as ARGB ints (unpremultiplied), like Android's. */
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bytes = skia.readPixels(info, width * 4, x, y) ?: error("can't read the bitmap's pixels")
        var k = 0
        for (row in 0 until height) {
            var o = offset + row * stride
            for (col in 0 until width) {
                val b = bytes[k].toInt() and 255; val g = bytes[k + 1].toInt() and 255
                val r = bytes[k + 2].toInt() and 255; val a = bytes[k + 3].toInt() and 255
                pixels[o++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                k += 4
            }
        }
    }

    fun recycle() { skia.close() }

    fun eraseColor(color: Int) { skia.erase(color) }

    fun compress(format: CompressFormat, quality: Int, out: java.io.OutputStream): Boolean {
        val image = org.jetbrains.skia.Image.makeFromBitmap(skia)
        val data = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG) ?: return false
        out.write(data.bytes)
        return true
    }

    companion object {
        @JvmStatic fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            val bmp = org.jetbrains.skia.Bitmap()
            bmp.allocPixels(ImageInfo(width, height, ColorType.N32, ColorAlphaType.PREMUL))
            bmp.erase(0)
            return Bitmap(bmp)
        }
    }
}

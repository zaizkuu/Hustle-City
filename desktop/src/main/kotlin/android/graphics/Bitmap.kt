package android.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** Desktop stand-in for Android's Bitmap: an RGBA Skia bitmap. */
class Bitmap private constructor(val skia: org.jetbrains.skia.Bitmap) {
    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }
    enum class CompressFormat { PNG, JPEG, WEBP }

    val width: Int get() = skia.width
    val height: Int get() = skia.height

    fun getPixel(x: Int, y: Int): Int = skia.getColor(x, y)

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

package android.graphics

import java.io.InputStream

/** Desktop stand-in for Android's BitmapFactory: decodes PNG and JPEG with Skia. */
object BitmapFactory {
    @JvmStatic fun decodeStream(stream: InputStream): Bitmap? = decodeByteArray(stream.readBytes())

    @JvmStatic fun decodeByteArray(data: ByteArray): Bitmap? = runCatching {
        Bitmap(org.jetbrains.skia.Bitmap.makeFromImage(org.jetbrains.skia.Image.makeFromEncoded(data)))
    }.getOrNull()

    @JvmStatic fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? =
        decodeByteArray(data.copyOfRange(offset, offset + length))
}

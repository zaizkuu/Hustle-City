package androidx.compose.ui.graphics

/** Desktop counterparts of Compose's Android interop: the game's Android graphics stand-ins wrap Skia objects. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = skia.asComposeImageBitmap()

fun Path.asAndroidPath(): android.graphics.Path = android.graphics.Path(asSkiaPath())

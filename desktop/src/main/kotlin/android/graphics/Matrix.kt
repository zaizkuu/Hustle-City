package android.graphics

import org.jetbrains.skia.Matrix33

/** Desktop stand-in for Android's Matrix: a 2D affine transform. */
class Matrix {
    // | a c e |
    // | b d f |
    private var a = 1f; private var b = 0f; private var c = 0f; private var d = 1f; private var e = 0f; private var f = 0f

    fun reset() { a = 1f; b = 0f; c = 0f; d = 1f; e = 0f; f = 0f }

    fun setScale(sx: Float, sy: Float) { reset(); a = sx; d = sy }

    fun setTranslate(dx: Float, dy: Float) { reset(); e = dx; f = dy }

    fun postTranslate(dx: Float, dy: Float): Boolean { e += dx; f += dy; return true }

    fun postScale(sx: Float, sy: Float): Boolean { a *= sx; c *= sx; e *= sx; b *= sy; d *= sy; f *= sy; return true }

    fun preScale(sx: Float, sy: Float): Boolean { a *= sx; b *= sx; c *= sy; d *= sy; return true }

    fun preTranslate(dx: Float, dy: Float): Boolean { e += a * dx + c * dy; f += b * dx + d * dy; return true }

    fun mapPoints(pts: FloatArray) {
        for (i in 0 until pts.size / 2) {
            val x = pts[i * 2]; val y = pts[i * 2 + 1]
            pts[i * 2] = a * x + c * y + e
            pts[i * 2 + 1] = b * x + d * y + f
        }
    }

    internal fun toSkia() = Matrix33(a, c, e, b, d, f, 0f, 0f, 1f)
}

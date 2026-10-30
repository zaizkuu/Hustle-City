package android.graphics

/** Desktop stand-in for Android's RectF. */
class RectF(
    @JvmField var left: Float = 0f,
    @JvmField var top: Float = 0f,
    @JvmField var right: Float = 0f,
    @JvmField var bottom: Float = 0f,
) {
    constructor(r: RectF) : this(r.left, r.top, r.right, r.bottom)

    fun set(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom
    }

    fun set(r: RectF) = set(r.left, r.top, r.right, r.bottom)
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) * .5f
    fun centerY(): Float = (top + bottom) * .5f
    fun isEmpty(): Boolean = left >= right || top >= bottom
    fun contains(x: Float, y: Float): Boolean = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun inset(dx: Float, dy: Float) { left += dx; right -= dx; top += dy; bottom -= dy }

    internal fun toSkia() = org.jetbrains.skia.Rect.makeLTRB(left, top, right, bottom)

    override fun toString() = "RectF($left, $top, $right, $bottom)"

    companion object {
        @JvmStatic fun intersects(a: RectF, b: RectF): Boolean =
            a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    }
}

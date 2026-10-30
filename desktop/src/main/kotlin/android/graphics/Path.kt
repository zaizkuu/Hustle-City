package android.graphics

/** Desktop stand-in for Android's Path, backed by a Skia path. */
class Path(val skia: org.jetbrains.skia.Path = org.jetbrains.skia.Path()) {
    enum class Direction { CW, CCW }

    fun moveTo(x: Float, y: Float) { skia.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { skia.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { skia.quadTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { skia.cubicTo(x1, y1, x2, y2, x3, y3) }
    fun close() { skia.closePath() }
    fun reset() { skia.reset() }
    fun rewind() { skia.rewind() }
    val isEmpty: Boolean get() = skia.isEmpty

    fun addCircle(x: Float, y: Float, radius: Float, dir: Direction) { skia.addCircle(x, y, radius, dir.skia) }

    fun addRect(left: Float, top: Float, right: Float, bottom: Float, dir: Direction) {
        skia.addRect(org.jetbrains.skia.Rect.makeLTRB(left, top, right, bottom), dir.skia)
    }

    /** Writes this path transformed by [matrix] into [dst], leaving this one as it is (unless [dst] is this). */
    fun transform(matrix: Matrix, dst: Path) { skia.transform(matrix.toSkia(), dst.skia) }

    fun transform(matrix: Matrix) = transform(matrix, this)

    private val Direction.skia get() =
        if (this == Direction.CW) org.jetbrains.skia.PathDirection.CLOCKWISE else org.jetbrains.skia.PathDirection.COUNTER_CLOCKWISE
}

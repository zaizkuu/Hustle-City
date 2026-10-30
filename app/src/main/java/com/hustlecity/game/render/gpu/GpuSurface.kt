package com.hustlecity.game.render.gpu

/**
 * Where the world view sends its 3D geometry in HD mode: the platform's GPU view ([GlWorldView] on Android). The game
 * thread fills an [acquire]d frame and [submit]s it; the platform's GL thread draws the most recent one.
 */
interface GpuSurface {
    /** Frames the GPU actually drew per second (what's on screen). */
    val glFps: Float
    /** Fraction of the view's size the 3D is rendered at; the display scales it up. */
    val renderScale: Float

    /** Renders at [scale] × the view size (call on the main thread). */
    fun setRenderScale(scale: Float)

    /** The frame the game thread may fill now. */
    fun acquire(): GpuFrame

    /** Publishes the filled frame and asks the GL thread to draw it. */
    fun submit()

    /**
     * Draws the latest finished 3D picture onto [canvas] ([w] × [h] pixels), under the labels and weather the world
     * view draws next. Android's GL view sits under the Compose layer and shows itself, so there it does nothing.
     */
    fun present(canvas: android.graphics.Canvas, w: Int, h: Int) {}
}

/**
 * Three frames rotating between the game thread, which fills one, and the GL thread, which draws one, so neither
 * ever waits for or touches the other's data.
 */
class FrameQueue {
    private val frames = arrayOf(GpuFrame(), GpuFrame(), GpuFrame())
    private val lock = Any()
    private var build = 0
    private var pending = -1
    private var spare = 1
    private var draw = 2
    private var version = 0L

    /** The frame the game thread may fill now. */
    fun acquire(): GpuFrame = frames[build]

    /** Publishes the filled frame for [take]. */
    fun submit() {
        frames[build].version = ++version
        synchronized(lock) {
            if (pending >= 0) { val t = pending; pending = build; build = t }
            else { pending = build; build = spare; spare = -1 }
        }
    }

    /** The newest published frame (the GL thread's side). */
    fun take(): GpuFrame {
        synchronized(lock) {
            if (pending >= 0) { spare = draw; draw = pending; pending = -1 }
            return frames[draw]
        }
    }
}

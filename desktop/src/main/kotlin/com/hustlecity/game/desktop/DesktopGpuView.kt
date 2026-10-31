package com.hustlecity.game.desktop

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.gpu.FrameQueue
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.render.gpu.GpuRenderer
import com.hustlecity.game.render.gpu.GpuSurface
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The HD renderer on the desktop. Frames go to the [DesktopGl] thread, which draws them with the same [GpuRenderer]
 * as Android into a multisampled framebuffer and reads the picture back; [present] then draws the newest picture
 * under the labels and weather of the game window.
 */
class DesktopGpuView(private val e: GameEngine, private val quality: Int, private val onFailure: () -> Unit) : GpuSurface {
    private val queue = FrameQueue()
    private val drawQueued = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var gpu: GpuRenderer? = null
    private var scaleChosen = false

    // GL thread only
    private var samples = 0
    private var fbo = 0
    private var color = 0
    private var depth = 0
    private var resolveFbo = 0
    private var resolveColor = 0
    private var targetW = 0
    private var targetH = 0
    private var pixels: ByteBuffer? = null
    private var bytes = ByteArray(0)
    private var errors = 0

    private val imageLock = Any()
    private var image: Image? = null
    private val paint = org.jetbrains.skia.Paint()

    override val glFps: Float get() = gpu?.fps ?: 0f
    override val glErrors: Int get() = gpu?.glErrors ?: 0

    /** The renderer is running with the photo materials. */
    val photoMaterials: Boolean get() = gpu?.photoMaterials == true

    @Volatile override var renderScale = 1f
        private set

    override fun setRenderScale(scale: Float) { renderScale = scale.coerceIn(.4f, 1f) }

    init {
        DesktopGl.post {
            if (closed) return@post
            if (!DesktopGl.ready) { fail(); return@post }
            try {
                samples = minOf(when (quality) { 2 -> 4; 1 -> 2; else -> 0 }, DesktopGl.maxSamples)
                gpu = GpuRenderer(quality).also { it.init() }
                System.err.println("HD renderer started: quality $quality, ${samples}x MSAA, photo materials ${if (photoMaterials) "on" else "off"}")
            } catch (t: Throwable) {
                System.err.println("HD renderer failed to start: $t")
                fail()
            }
        }
    }

    private fun fail() = javax.swing.SwingUtilities.invokeLater(onFailure)

    override fun acquire(): GpuFrame = queue.acquire()

    override fun submit() {
        if (!scaleChosen) {
            scaleChosen = true
            val f = queue.acquire()
            setRenderScale(initialScale(f.width * f.height / 1e6f))
        }
        queue.submit()
        if (drawQueued.compareAndSet(false, true)) DesktopGl.post { drawQueued.set(false); drawLatest() }
    }

    /**
     * A desktop GPU shades far more pixels than a phone, but every frame is also copied back from it, so the
     * automatic resolution starts from a budget per quality level and the frame-rate governor takes it from there.
     */
    private fun initialScale(megapixels: Float): Float {
        val fixed = e.settings.resolution
        if (fixed > 0) return fixed / 100f
        val budget = when (quality) { 0 -> 2.1f; 1 -> 3.7f; else -> 8.3f }
        return sqrt(budget / megapixels.coerceAtLeast(.1f)).coerceIn(.5f, 1f)
    }

    private fun drawLatest() {
        if (closed) return
        // a frame that fails is dropped; a GPU that keeps failing (a driver reset, out of memory) hands over to the
        // classic renderer instead of leaving the world black
        try {
            drawFrame()
            errors = 0
        } catch (t: Throwable) {
            System.err.println("HD frame failed: $t")
            if (++errors >= 3) {
                System.err.println("HD graphics keep failing: switching to the classic renderer")
                closed = true
                fail()
            }
        }
    }

    private fun drawFrame() {
        val g = gpu ?: return
        val f = queue.take()
        val w = (f.width * renderScale).roundToInt().coerceAtLeast(16)
        val h = (f.height * renderScale).roundToInt().coerceAtLeast(16)
        ensureTargets(w, h)
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo)
        g.target = fbo
        g.resize(w, h)
        g.draw(f)
        if (samples > 0) {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, fbo)
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, resolveFbo)
            GL33C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL33C.GL_COLOR_BUFFER_BIT, GL33C.GL_NEAREST)
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, resolveFbo)
        }
        val buf = pixels!!
        buf.clear()
        GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0)
        GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, 4)
        GL33C.glReadPixels(0, 0, w, h, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, buf)
        buf.get(bytes, 0, w * h * 4)
        val img = Image.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), bytes, w * 4)
        val old = synchronized(imageLock) { image.also { image = img } }
        old?.close()
    }

    /** (Re)creates the framebuffers the frames are drawn into at [w] × [h]. */
    private fun ensureTargets(w: Int, h: Int) {
        if (w == targetW && h == targetH) return
        deleteTargets()
        targetW = w; targetH = h
        fbo = GL33C.glGenFramebuffers()
        color = GL33C.glGenRenderbuffers()
        depth = GL33C.glGenRenderbuffers()
        GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, color)
        if (samples > 0) GL33C.glRenderbufferStorageMultisample(GL33C.GL_RENDERBUFFER, samples, GL33C.GL_RGBA8, w, h)
        else GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_RGBA8, w, h)
        GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, depth)
        if (samples > 0) GL33C.glRenderbufferStorageMultisample(GL33C.GL_RENDERBUFFER, samples, GL33C.GL_DEPTH_COMPONENT24, w, h)
        else GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_DEPTH_COMPONENT24, w, h)
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo)
        GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_RENDERBUFFER, color)
        GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL33C.GL_RENDERBUFFER, depth)
        val status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER)
        check(status == GL33C.GL_FRAMEBUFFER_COMPLETE) { "framebuffer incomplete: $status" }
        if (samples > 0) {
            resolveFbo = GL33C.glGenFramebuffers()
            resolveColor = GL33C.glGenRenderbuffers()
            GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, resolveColor)
            GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_RGBA8, w, h)
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, resolveFbo)
            GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_RENDERBUFFER, resolveColor)
        }
        pixels?.let { MemoryUtil.memFree(it) }
        pixels = MemoryUtil.memAlloc(w * h * 4)
        if (bytes.size < w * h * 4) bytes = ByteArray(w * h * 4)
    }

    private fun deleteTargets() {
        if (fbo != 0) { GL33C.glDeleteFramebuffers(fbo); GL33C.glDeleteRenderbuffers(color); GL33C.glDeleteRenderbuffers(depth) }
        if (resolveFbo != 0) { GL33C.glDeleteFramebuffers(resolveFbo); GL33C.glDeleteRenderbuffers(resolveColor) }
        fbo = 0; color = 0; depth = 0; resolveFbo = 0; resolveColor = 0
        targetW = 0; targetH = 0
    }

    override fun present(canvas: android.graphics.Canvas, w: Int, h: Int) {
        synchronized(imageLock) {
            val img = image ?: return
            val c = canvas.skia
            // GL rows run bottom up
            c.save()
            c.translate(0f, h.toFloat())
            c.scale(1f, -1f)
            c.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
                SamplingMode.LINEAR, paint, true)
            c.restore()
        }
    }

    /** Stops drawing and frees the GL objects. */
    fun close() {
        closed = true
        DesktopGl.post {
            gpu?.release()
            gpu = null
            deleteTargets()
            pixels?.let { MemoryUtil.memFree(it) }
            pixels = null
        }
        synchronized(imageLock) { image?.close(); image = null }
    }
}

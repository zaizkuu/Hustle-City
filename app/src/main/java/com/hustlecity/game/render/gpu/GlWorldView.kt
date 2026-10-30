package com.hustlecity.game.render.gpu

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * Hosts the GPU renderer on Android. The game thread fills [acquire]d frames and [submit]s them; the GL thread draws
 * the most recent one with a [GpuRenderer]. [onFailure] runs on the main thread if GLES 3 can't be used, so the caller
 * can fall back to the Canvas renderer.
 */
class GlWorldView(
    context: Context,
    /** 0 low, 1 medium, 2 high: picks MSAA samples and the shader variant. */
    private val quality: Int = 1,
    private val onFailure: () -> Unit,
) : GLSurfaceView(context), GpuSurface {
    private val queue = FrameQueue()
    private val renderer = Renderer()

    override val glFps: Float get() = renderer.gpu?.fps ?: 0f

    private var layoutW = 0
    private var layoutH = 0
    /** Fraction of the view's size the 3D is rendered at; the display scales it up for free. */
    override var renderScale = 1f
        private set

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaChooser(when (quality) { 2 -> 4; 1 -> 2; else -> 0 }))
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun setRenderScale(scale: Float) {
        val sc = scale.coerceIn(.4f, 1f)
        renderScale = sc
        if (layoutW > 0 && layoutH > 0) holder.setFixedSize((layoutW * sc).toInt().coerceAtLeast(16), (layoutH * sc).toInt().coerceAtLeast(16))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutW = w; layoutH = h
        setRenderScale(renderScale)
    }

    override fun acquire(): GpuFrame = queue.acquire()

    override fun submit() {
        queue.submit()
        requestRender()
    }

    companion object {
        fun supported(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        }
    }

    /**
     * Picks an RGB888 + 24-bit depth config with 4x MSAA, falling back to fewer samples, a 16-bit depth and finally
     * whatever the device offers, so choosing a config never crashes the GL thread.
     */
    private class MsaaChooser(private val maxSamples: Int) : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            for (samples in intArrayOf(4, 2, 0)) for (depth in intArrayOf(24, 16)) {
                if (samples > maxSamples) continue
                val spec = intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT_KHR */,
                    EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0, EGL10.EGL_SAMPLES, samples, EGL10.EGL_NONE,
                )
                pick(egl, display, spec)?.let { return it }
            }
            pick(egl, display, intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE))?.let { return it }
            val n = IntArray(1)
            egl.eglGetConfigs(display, null, 0, n)
            val all = arrayOfNulls<EGLConfig>(maxOf(1, n[0]))
            egl.eglGetConfigs(display, all, all.size, n)
            return all.first()!!
        }

        private fun pick(egl: EGL10, display: EGLDisplay, spec: IntArray): EGLConfig? {
            val n = IntArray(1)
            if (!egl.eglChooseConfig(display, spec, null, 0, n) || n[0] <= 0) return null
            val configs = arrayOfNulls<EGLConfig>(n[0])
            if (!egl.eglChooseConfig(display, spec, configs, n[0], n)) return null
            return configs.firstOrNull()
        }
    }

    private inner class Renderer : GLSurfaceView.Renderer {
        @Volatile var gpu: GpuRenderer? = null
        private var failed = false

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            try {
                gpu = GpuRenderer(quality).also { it.init() }
                failed = false
            } catch (t: Throwable) {
                Log.e("HustleGL", "GL init failed", t)
                fail()
            }
        }

        private fun fail() {
            if (failed) return
            failed = true
            Handler(Looper.getMainLooper()).post(onFailure)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            gpu?.resize(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (failed) return
            gpu?.draw(queue.take())
        }
    }
}

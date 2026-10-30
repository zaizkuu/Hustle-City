package com.hustlecity.game.desktop

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.LinkedBlockingQueue

/**
 * The desktop's GL thread: one long-lived thread owning an OpenGL 3.3 core context on a hidden GLFW window. The HD
 * renderer draws into framebuffers on it and reads the pictures back for the game window, which Compose draws with
 * its own graphics API (Direct3D on Windows). Everything GL runs as a task on this thread.
 */
object DesktopGl {
    private val tasks = LinkedBlockingQueue<() -> Unit>()
    private var window = MemoryUtil.NULL

    /** Set when no context could be made; the game then uses the classic renderer. */
    @Volatile var failure: Throwable? = null
        private set

    /** What the context runs on, for the log. */
    @Volatile var renderer: String = ""
        private set

    /** The most multisampling the context offers. */
    @Volatile var maxSamples = 0
        private set

    /**
     * HD graphics need the context on a thread of our own, which macOS's windowing doesn't allow, and can be turned
     * off with -Dhustle.gpu=false.
     */
    val supported: Boolean =
        System.getProperty("hustle.gpu") != "false" && !System.getProperty("os.name").orEmpty().lowercase().startsWith("mac")

    private val thread: Thread by lazy {
        Thread(::loop, "HustleGL").apply { isDaemon = true; start() }
    }

    /** Runs [task] on the GL thread with the context current. Tasks run in order. */
    fun post(task: () -> Unit) {
        thread
        tasks.put(task)
    }

    private fun loop() {
        try {
            createContext()
        } catch (t: Throwable) {
            failure = t
            System.err.println("HD graphics unavailable: $t")
        }
        while (true) {
            val task = tasks.take()
            try {
                task()
            } catch (t: Throwable) {
                System.err.println("GL task failed: $t")
                t.printStackTrace()
            }
        }
    }

    private fun createContext() {
        GLFWErrorCallback.createPrint(System.err).set()
        check(GLFW.glfwInit()) { "GLFW couldn't start" }
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
        window = GLFW.glfwCreateWindow(16, 16, "Hustle City GL", MemoryUtil.NULL, MemoryUtil.NULL)
        check(window != MemoryUtil.NULL) { "no OpenGL 3.3 context" }
        GLFW.glfwMakeContextCurrent(window)
        GL.createCapabilities()
        // a core context draws nothing without a vertex array object; the renderer's attribute setup goes into this one
        GL33C.glBindVertexArray(GL33C.glGenVertexArrays())
        maxSamples = GL33C.glGetInteger(GL33C.GL_MAX_SAMPLES)
        renderer = "${GL33C.glGetString(GL33C.GL_RENDERER)} (OpenGL ${GL33C.glGetString(GL33C.GL_VERSION)})"
        System.err.println("HD graphics: $renderer")
    }

    /** True once the context is up (call on the GL thread). */
    val ready: Boolean get() = window != MemoryUtil.NULL
}

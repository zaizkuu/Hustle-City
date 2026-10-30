package com.hustlecity.game.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.nativeCanvas
import com.hustlecity.game.desktop.DesktopGl
import com.hustlecity.game.desktop.DesktopGpuView
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.gpu.GpuSurface

// The desktop side of the world view's platform seam; the Android side is app/src/main/java/.../ui/GpuHost.kt.

/** True when HD graphics can be tried here; if the GL context then fails, the game falls back to classic. */
@Composable
fun rememberGpuSupported(): Boolean = remember { DesktopGl.supported && DesktopGl.failure == null }

/** Starts the desktop GPU renderer while HD graphics are on; [surface] holds it while it's running. */
@Composable
fun GpuHost(e: GameEngine, quality: Int, surface: Array<GpuSurface?>) {
    DisposableEffect(Unit) {
        val view = DesktopGpuView(e, quality) { e.settings.gpuFailed = true }
        surface[0] = view
        onDispose {
            if (surface[0] === view) surface[0] = null
            view.close()
        }
    }
}

private var wrapped: android.graphics.Canvas? = null

/** The Skia canvas behind a Compose canvas, wrapped in the Android Canvas stand-in the game's renderers draw on. */
val androidx.compose.ui.graphics.Canvas.gameCanvas: android.graphics.Canvas
    get() {
        val native = nativeCanvas
        wrapped?.let { if (it.skia === native) return it }
        return android.graphics.Canvas(native).also { wrapped = it }
    }

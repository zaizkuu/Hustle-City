package com.hustlecity.game.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.gpu.GlWorldView
import com.hustlecity.game.render.gpu.GpuSurface

// The Android side of the world view's platform seam. The desktop build leaves this file out and brings its own
// versions of these three declarations (desktop/src/main/kotlin/com/hustlecity/game/ui/DesktopGpuHost.kt).

/** True when this device can run the HD renderer. */
@Composable
fun rememberGpuSupported(): Boolean {
    val context = LocalContext.current
    return remember { android.os.Build.FINGERPRINT != "robolectric" && GlWorldView.supported(context) }
}

/** The GL view that draws the 3D world under the Compose layer; [surface] holds it while it's on screen. */
@Composable
fun GpuHost(e: GameEngine, quality: Int, surface: Array<GpuSurface?>) {
    AndroidView(factory = { ctx ->
        GlWorldView(ctx, quality) { e.settings.gpuFailed = true }.also {
            surface[0] = it
            val dm = ctx.resources.displayMetrics
            it.setRenderScale(ResolutionGovernor.initialScale(e, dm.widthPixels, dm.heightPixels))
        }
    }, modifier = Modifier.fillMaxSize(), onRelease = { surface[0] = null })
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            val view = surface[0] as? GlWorldView
            when (ev) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> view?.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> view?.onResume()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
}

/** The platform canvas behind a Compose canvas, which the game's renderers draw on. */
val androidx.compose.ui.graphics.Canvas.gameCanvas: android.graphics.Canvas get() = nativeCanvas

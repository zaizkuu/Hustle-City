package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Desktop stand-in for the Activity back handler: the window sends the Escape key here as "back", and the most
 * recently composed enabled handler gets it, as on Android.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val handler = { current() }
        if (enabled) BackDispatcher.add(handler)
        onDispose { BackDispatcher.remove(handler) }
    }
}

object BackDispatcher {
    private val handlers = ArrayList<() -> Unit>()

    internal fun add(h: () -> Unit) = synchronized(handlers) { handlers += h }
    internal fun remove(h: () -> Unit) = synchronized(handlers) { handlers.remove(h) }

    /** Runs the newest back handler; false when nothing handles back. */
    fun dispatch(): Boolean {
        val h = synchronized(handlers) { handlers.lastOrNull() } ?: return false
        h()
        return true
    }
}

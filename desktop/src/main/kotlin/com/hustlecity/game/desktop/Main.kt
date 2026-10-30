package com.hustlecity.game.desktop

import android.content.Context
import androidx.activity.compose.BackDispatcher
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.hustlecity.game.Platform
import com.hustlecity.game.audio.Audio
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.Screen
import com.hustlecity.game.ui.menu.HustleCityApp
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

fun main() {
    Platform.isDesktop = true
    val engine = GameEngine(Context(dataDir()))
    engine.settings.applyAudio()
    Audio.start()

    application {
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1280.dp, 760.dp))
        fun quit() {
            persist(engine)
            Audio.stop()
            exitApplication()
        }
        Window(
            onCloseRequest = ::quit,
            state = windowState,
            title = "Hustle City",
            icon = rememberVectorPainter(AppIcon.vector),
            onPreviewKeyEvent = { onKey(engine, it) },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(800, 480)
                window.addWindowListener(object : WindowAdapter() {
                    // keys held when the window loses focus never get their key-up
                    override fun windowDeactivated(ev: WindowEvent) = engine.resetInput()
                    override fun windowIconified(ev: WindowEvent) = persist(engine)
                })
            }
            HustleCityApp(engine, onExit = ::quit)
        }
    }
}

/** Saves the settings and the game, as Android does when the app goes to the background. */
private fun persist(engine: GameEngine) {
    engine.settings.save()
    if (engine.hasGame) engine.save()
}

private fun onKey(engine: GameEngine, event: KeyEvent): Boolean {
    val down = event.type == KeyEventType.KeyDown
    if (!down && event.type != KeyEventType.KeyUp) return false
    if (event.key == Key.Escape) {
        // back, as on Android; at the top of the main menu it does nothing rather than quitting
        if (!down) return true
        val ui = engine.ui
        if (ui.screen == Screen.MENU && ui.panel == null && ui.info == null) return true
        return BackDispatcher.dispatch()
    }
    return Keys.handle(engine, event.key, down)
}

/** Where saves and settings live: %APPDATA%\HustleCity on Windows. */
private fun dataDir(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home")
    val dir = when {
        os.startsWith("windows") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "HustleCity")
        os.startsWith("mac") -> File(home, "Library/Application Support/HustleCity")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "hustle-city")
    }
    dir.mkdirs()
    return dir
}

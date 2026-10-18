package com.hustlecity.game

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hustlecity.game.audio.Audio
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.ui.menu.HustleCityApp

/** Keeps one engine per process so configuration changes never restart the game. */
object EngineHolder {
    private var engine: GameEngine? = null
    fun get(context: Context): GameEngine = engine ?: GameEngine(context.applicationContext).also { engine = it }
    fun current(): GameEngine? = engine
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        val engine = EngineHolder.get(this)
        setContent { HustleCityApp(engine, onExit = { finish() }) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        EngineHolder.current()?.settings?.applyAudio()
        Audio.start()
    }

    override fun onPause() {
        super.onPause()
        Audio.stop()
        EngineHolder.current()?.let {
            it.settings.save()
            if (it.hasGame) it.save()
        }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

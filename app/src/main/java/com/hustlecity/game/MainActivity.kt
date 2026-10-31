package com.hustlecity.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    /** Where the songs the player picks go once the system's file picker returns. */
    private var songsPicked: ((List<Song>) -> Unit)? = null

    /** The system file picker for music, keeping read access to what's picked so it plays after a restart too. */
    private val pickSongs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val songs = uris.mapNotNull { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: return@mapNotNull null
            Song(name.substringBeforeLast('.'), uri.toString())
        }
        songsPicked?.invoke(songs)
        songsPicked = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Platform.pickSongs = { done -> songsPicked = done; pickSongs.launch(arrayOf("audio/*")) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        val engine = EngineHolder.get(this)
        setContent { HustleCityApp(engine, onExit = { finish() }) }
    }

    /** Arrow keys and W A S D on a keyboard walk and drive. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val down = event.action == android.view.KeyEvent.ACTION_DOWN
        val up = event.action == android.view.KeyEvent.ACTION_UP
        if ((down || up) && EngineHolder.current()?.onKey(event.keyCode, down) == true) return true
        return super.dispatchKeyEvent(event)
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
        EngineHolder.current()?.pauseMusic()
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

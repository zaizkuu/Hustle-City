package com.hustlecity.game.desktop

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import com.hustlecity.game.engine.GameEngine

/** Hands the window's keys to the engine as the Android key codes it understands. */
object Keys {
    private val codes = mapOf(
        Key.DirectionUp to KeyEvent.KEYCODE_DPAD_UP,
        Key.DirectionDown to KeyEvent.KEYCODE_DPAD_DOWN,
        Key.DirectionLeft to KeyEvent.KEYCODE_DPAD_LEFT,
        Key.DirectionRight to KeyEvent.KEYCODE_DPAD_RIGHT,
        Key.W to KeyEvent.KEYCODE_W,
        Key.A to KeyEvent.KEYCODE_A,
        Key.S to KeyEvent.KEYCODE_S,
        Key.D to KeyEvent.KEYCODE_D,
        Key.E to KeyEvent.KEYCODE_E,
        Key.F to KeyEvent.KEYCODE_F,
        Key.P to KeyEvent.KEYCODE_P,
        Key.Spacebar to KeyEvent.KEYCODE_SPACE,
    )

    fun handle(engine: GameEngine, key: Key, down: Boolean): Boolean {
        val code = codes[key] ?: return false
        return engine.onKey(code, down)
    }
}

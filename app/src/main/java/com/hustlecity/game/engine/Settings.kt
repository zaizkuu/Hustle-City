package com.hustlecity.game.engine

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Player preferences, persisted in SharedPreferences. */
class Settings(context: Context?) {
    private val prefs = context?.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = low, 1 = medium, 2 = high. */
    var quality by mutableIntStateOf(prefs?.getInt("quality", 1) ?: 1)
    var fpsCap by mutableIntStateOf(prefs?.getInt("fpsCap", 60) ?: 60)
    var showFps by mutableStateOf(prefs?.getBoolean("showFps", false) ?: false)
    var pauseInMenus by mutableStateOf(prefs?.getBoolean("pauseInMenus", true) ?: true)
    var leftHanded by mutableStateOf(prefs?.getBoolean("leftHanded", false) ?: false)

    fun save() {
        prefs?.edit()
            ?.putInt("quality", quality)
            ?.putInt("fpsCap", fpsCap)
            ?.putBoolean("showFps", showFps)
            ?.putBoolean("pauseInMenus", pauseInMenus)
            ?.putBoolean("leftHanded", leftHanded)
            ?.apply()
    }

    val carCount get() = when (quality) { 0 -> 16; 1 -> 28; else -> 44 }
    val pedCount get() = when (quality) { 0 -> 18; 1 -> 36; else -> 64 }
    val shadows get() = quality >= 1
    val detailedWindows get() = quality >= 1
}

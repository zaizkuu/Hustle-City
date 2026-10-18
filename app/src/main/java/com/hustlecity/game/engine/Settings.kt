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
    var musicVolume by mutableStateOf(prefs?.getFloat("musicVolume", .55f) ?: .55f)
    var sfxVolume by mutableStateOf(prefs?.getFloat("sfxVolume", .8f) ?: .8f)
    var radioOn by mutableStateOf(prefs?.getBoolean("radioOn", true) ?: true)
    var station by mutableIntStateOf(prefs?.getInt("station", 0) ?: 0)
    /** Car controls: 0 = steering wheel and pedals, 1 = joystick. */
    var carControls by mutableIntStateOf(prefs?.getInt("carControls", 0) ?: 0)
    var minimap by mutableStateOf(prefs?.getBoolean("minimap", true) ?: true)

    /** Pushes the audio preferences to the mixer. */
    fun applyAudio() {
        com.hustlecity.game.audio.Audio.musicVolume = musicVolume
        com.hustlecity.game.audio.Audio.sfxVolume = sfxVolume
        com.hustlecity.game.audio.Audio.radioOn = radioOn
        if (com.hustlecity.game.audio.Audio.station != station) com.hustlecity.game.audio.Audio.setStation(station)
    }

    fun save() {
        prefs?.edit()
            ?.putInt("quality", quality)
            ?.putInt("fpsCap", fpsCap)
            ?.putBoolean("showFps", showFps)
            ?.putBoolean("pauseInMenus", pauseInMenus)
            ?.putBoolean("leftHanded", leftHanded)
            ?.putFloat("musicVolume", musicVolume)
            ?.putFloat("sfxVolume", sfxVolume)
            ?.putBoolean("radioOn", radioOn)
            ?.putInt("station", station)
            ?.putInt("carControls", carControls)
            ?.putBoolean("minimap", minimap)
            ?.apply()
    }

    val carCount get() = when (quality) { 0 -> 16; 1 -> 28; else -> 44 }
    val pedCount get() = when (quality) { 0 -> 18; 1 -> 36; else -> 64 }
    val shadows get() = quality >= 1
    val detailedWindows get() = quality >= 1
}

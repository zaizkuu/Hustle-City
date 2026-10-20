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
    /** Car controls: 0 = steering wheel and pedals, 1 = joystick, 2 = arrow keys. */
    var carControls by mutableIntStateOf(prefs?.getInt("carControls", 0) ?: 0)
    var minimap by mutableStateOf(prefs?.getBoolean("minimap", true) ?: true)
    /** HD graphics: the GPU renderer (lighting, depth, anti-aliasing) instead of the classic Canvas renderer. */
    var hd by mutableStateOf(prefs?.getBoolean("hd", true) ?: true)
    /** 3D render resolution in percent of the screen, or 0 for automatic (adapts to keep the frame rate up). */
    var resolution by mutableIntStateOf(prefs?.getInt("resolution", 0) ?: 0)
    /** Set when the GPU renderer couldn't start on this device. */
    var gpuFailed by mutableStateOf(false)

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
            ?.putBoolean("hd", hd)
            ?.putInt("resolution", resolution)
            ?.apply()
    }

    /** Scale on how many people walk the streets (not saved): above 1 for filming, where frame rate doesn't matter. */
    var crowds = 1f
    /** Scale on how many cars drive them, likewise. */
    var busyRoads = 1f

    val carCount get() = ((when (quality) { 0 -> 16; 1 -> 28; else -> 44 }) * busyRoads).toInt()
    val pedCount get() = ((when (quality) { 0 -> 18; 1 -> 36; else -> 64 }) * crowds).toInt()
    val shadows get() = quality >= 1
    /** Sun shadow map size for the GPU renderer (0 = no real-time shadows). */
    val shadowMapSize get() = when (quality) { 0 -> 0; 1 -> 1024; else -> 2048 }
    /** Pixels (in millions) the automatic resolution starts from: about what a phone of this class can shade. */
    val pixelBudget get() = when (quality) { 0 -> .7f; 1 -> 1.15f; else -> 2.1f }
    val detailedWindows get() = quality >= 1
}

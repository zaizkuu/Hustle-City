package com.hustlecity.game.engine

import android.content.Context
import com.hustlecity.game.model.GameState
import kotlinx.serialization.json.Json
import java.io.File

class SaveManager(context: Context?) {
    private val dir: File? = context?.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowSpecialFloatingPointValues = true }

    private val file get() = dir?.let { File(it, "save.json") }

    fun hasSave(): Boolean = file?.exists() == true

    fun save(state: GameState): Boolean {
        val f = file ?: return false
        return runCatching {
            val tmp = File(f.parentFile, "save.json.tmp")
            tmp.writeText(json.encodeToString(GameState.serializer(), state))
            if (!tmp.renameTo(f)) {
                f.delete(); tmp.renameTo(f)
            }
        }.isSuccess
    }

    fun load(): GameState? {
        val f = file ?: return null
        if (!f.exists()) return null
        val state = runCatching { json.decodeFromString(GameState.serializer(), f.readText()) }.getOrNull()
        if (state == null) {
            // keep the damaged file for inspection, and stop offering Continue
            runCatching { f.renameTo(File(f.parentFile, "save-damaged.json")) }
        }
        return state
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)
    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text)
}

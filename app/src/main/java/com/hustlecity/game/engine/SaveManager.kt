package com.hustlecity.game.engine

import android.content.Context
import com.hustlecity.game.model.GameState
import kotlinx.serialization.json.Json
import java.io.File

/** What a save slot holds, for the menu: who, where and how far along. */
class SlotInfo(val slot: Int, val name: String, val mapId: String, val day: Int, val money: Double, val savedAt: Long)

/**
 * Saved games, one file per slot. Slot 0 is save.json, the only file older versions wrote, so their save shows up as
 * the first slot; the others are save2.json and save3.json.
 */
class SaveManager(context: Context?) {
    private val dir: File? = context?.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowSpecialFloatingPointValues = true }

    /** The slot the running game saves to. */
    var slot = 0

    private fun file(i: Int) = dir?.let { File(it, if (i == 0) "save.json" else "save${i + 1}.json") }

    fun hasSave(): Boolean = (0 until SLOTS).any { file(it)?.exists() == true }

    /** Every slot in order, null where it's empty (or its file can't be read). */
    fun slots(): List<SlotInfo?> = (0 until SLOTS).map { i ->
        val f = file(i)?.takeIf { it.exists() } ?: return@map null
        runCatching { json.decodeFromString(GameState.serializer(), f.readText()) }.getOrNull()
            ?.let { SlotInfo(i, it.player.name, it.mapId, it.day, it.player.money, f.lastModified()) }
    }

    /** The slot played last, or -1 with no saves. */
    fun latest(): Int = (0 until SLOTS).filter { file(it)?.exists() == true }.maxByOrNull { file(it)!!.lastModified() } ?: -1

    /** The first empty slot, or -1 when all are taken. */
    fun firstFree(): Int = (0 until SLOTS).firstOrNull { file(it)?.exists() != true } ?: -1

    fun delete(i: Int) { file(i)?.delete() }

    fun save(state: GameState): Boolean {
        val f = file(slot) ?: return false
        return runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(GameState.serializer(), state))
            if (!tmp.renameTo(f)) {
                f.delete(); tmp.renameTo(f)
            }
        }.isSuccess
    }

    fun load(i: Int = slot): GameState? {
        val f = file(i) ?: return null
        if (!f.exists()) return null
        val state = runCatching { json.decodeFromString(GameState.serializer(), f.readText()) }.getOrNull()
        if (state == null) {
            // keep the damaged file for inspection, and stop offering it
            runCatching { f.renameTo(File(f.parentFile, "save-damaged.json")) }
        } else slot = i
        return state
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)
    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text)

    companion object { const val SLOTS = 3 }
}

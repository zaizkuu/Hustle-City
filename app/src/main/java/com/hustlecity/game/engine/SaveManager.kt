package com.hustlecity.game.engine

import android.content.Context
import com.hustlecity.game.model.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What a save slot's card shows without loading the whole game. */
@Serializable
data class SlotInfo(
    val slot: Int,
    val name: String = "",
    val mapId: String = "classic",
    val day: Int = 1,
    val money: Double = 0.0,
    val netWorth: Double = 0.0,
    val businesses: Int = 0,
    /** Wall-clock time of the save (ms since the epoch). */
    val savedAt: Long = 0L,
)

/**
 * Saves games into [SLOTS] slots (save_1.json …), each with a small .meta.json beside it for the slot cards. The game
 * in play saves to [slot]. The single save.json of older versions moves into slot 1 the first time it's seen.
 */
class SaveManager(context: Context?) {
    companion object {
        const val SLOTS = 6
    }

    private val dir: File? = context?.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowSpecialFloatingPointValues = true }

    /** Slot the game in play saves to. */
    var slot = 1

    init {
        dir?.let { d ->
            val legacy = File(d, "save.json")
            val first = File(d, "save_1.json")
            if (legacy.exists() && !first.exists()) runCatching { legacy.renameTo(first) }
        }
    }

    private fun file(slot: Int) = dir?.let { File(it, "save_$slot.json") }
    private fun metaFile(slot: Int) = dir?.let { File(it, "save_$slot.meta.json") }

    fun exists(slot: Int): Boolean = file(slot)?.exists() == true

    fun hasSave(): Boolean = (1..SLOTS).any { exists(it) }

    /** The slot saved most recently, or null when there are none. */
    fun latestSlot(): Int? = (1..SLOTS).filter { exists(it) }.maxByOrNull { info(it)?.savedAt?.takeIf { t -> t > 0 } ?: file(it)!!.lastModified() }

    /** The first empty slot, or null when every slot is taken. */
    fun freeSlot(): Int? = (1..SLOTS).firstOrNull { !exists(it) }

    /** Card details of a slot, or null when it's empty. Saves from before slots get theirs built from the game itself. */
    fun info(slot: Int): SlotInfo? {
        if (!exists(slot)) return null
        val m = metaFile(slot)
        if (m != null && m.exists()) runCatching { return json.decodeFromString(SlotInfo.serializer(), m.readText()) }
        val s = runCatching { json.decodeFromString(GameState.serializer(), file(slot)!!.readText()) }.getOrNull() ?: return SlotInfo(slot)
        val info = SlotInfo(slot, s.player.name, s.mapId, s.day, s.player.money, s.player.money, s.businesses.size, file(slot)!!.lastModified())
        runCatching { m?.writeText(json.encodeToString(SlotInfo.serializer(), info)) }
        return info
    }

    fun save(state: GameState, info: SlotInfo? = null): Boolean {
        val f = file(slot) ?: return false
        return runCatching {
            val tmp = File(f.parentFile, "save_$slot.json.tmp")
            tmp.writeText(json.encodeToString(GameState.serializer(), state))
            if (!tmp.renameTo(f)) {
                f.delete(); tmp.renameTo(f)
            }
            if (info != null) metaFile(slot)?.writeText(json.encodeToString(SlotInfo.serializer(), info.copy(slot = slot)))
        }.isSuccess
    }

    /** Loads a slot and makes it the one the game saves to. */
    fun load(slot: Int = this.slot): GameState? {
        val f = file(slot) ?: return null
        if (!f.exists()) return null
        val state = runCatching { json.decodeFromString(GameState.serializer(), f.readText()) }.getOrNull()
        if (state == null) {
            // keep the damaged file for inspection, and stop offering it
            runCatching { f.renameTo(File(f.parentFile, "save_$slot-damaged.json")) }
            runCatching { metaFile(slot)?.delete() }
        } else this.slot = slot
        return state
    }

    fun delete(slot: Int) {
        runCatching { file(slot)?.delete() }
        runCatching { metaFile(slot)?.delete() }
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)
    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text)
}

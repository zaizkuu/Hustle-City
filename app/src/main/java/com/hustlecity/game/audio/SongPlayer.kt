package com.hustlecity.game.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.hustlecity.game.Song

/**
 * Plays the player's own songs through a store's speakers: the list shuffled, one after another, skipping any file
 * that won't open. Android decodes with its MediaPlayer; the desktop build has its own MediaPlayer that reads MP3 and WAV.
 */
object SongPlayer {
    /** Set by the engine, to open content:// URIs on Android. */
    @Volatile var context: Context? = null
    @Volatile var nowPlaying: String? = null
        private set

    private var player: MediaPlayer? = null
    private var queue: List<Song> = emptyList()
    private var index = 0
    private var volume = 1f
    private var failures = 0

    val playing: Boolean get() = player != null

    /** Starts the list (shuffled) unless it's already playing these songs; then just sets the volume. */
    @Synchronized
    fun play(songs: List<Song>, vol: Float) {
        volume = vol
        if (songs.isEmpty()) { stop(); return }
        if (player != null && queue.size == songs.size && queue.toSet() == songs.toSet()) { player?.setVolume(vol, vol); return }
        stop()
        queue = songs.shuffled()
        index = 0
        failures = 0
        start()
    }

    @Synchronized
    fun next() {
        if (queue.isEmpty()) return
        release()
        index = (index + 1) % queue.size
        start()
    }

    @Synchronized
    fun stop() {
        release()
        queue = emptyList()
        nowPlaying = null
    }

    private fun release() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    private fun start() {
        val song = queue.getOrNull(index) ?: return
        val mp = MediaPlayer()
        player = mp
        nowPlaying = song.name
        try {
            val ctx = context
            if (ctx != null && (song.ref.startsWith("content:") || song.ref.startsWith("file:"))) mp.setDataSource(ctx, Uri.parse(song.ref))
            else mp.setDataSource(song.ref)
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener { if (player === it) { failures = 0; it.start() } }
            mp.setOnCompletionListener { if (player === it) next() }
            mp.setOnErrorListener { p, _, _ -> if (player === p) skip(song); true }
            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.w("HustleMusic", "can't play ${song.name}: $t")
            skip(song)
        }
    }

    /** A song that won't play: on to the next, unless none of them do. */
    private fun skip(song: Song) {
        Log.w("HustleMusic", "skipping ${song.name}")
        if (++failures >= queue.size) { stop(); return }
        next()
    }
}

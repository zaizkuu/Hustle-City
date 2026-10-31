package com.hustlecity.game

/** A music file on the player's device: its name to show, and where it is (a content:// URI on Android, a path on Windows). */
data class Song(val name: String, val ref: String)

/** What the game runs on, set by the platform's entry point before the engine starts. */
object Platform {
    /** The Windows (desktop) build: a keyboard and mouse, a big screen, and a window to quit. */
    @Volatile var isDesktop = false

    /**
     * Opens the platform's file picker for music; [done] gets the songs chosen (none if cancelled). Each platform's
     * entry point installs its own; without one there's nothing to pick.
     */
    @Volatile var pickSongs: (done: (List<Song>) -> Unit) -> Unit = { it(emptyList()) }
}

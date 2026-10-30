package com.hustlecity.game

/** What the game runs on, set by the platform's entry point before the engine starts. */
object Platform {
    /** The Windows (desktop) build: a keyboard and mouse, a big screen, and a window to quit. */
    @Volatile var isDesktop = false
}

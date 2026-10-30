package android.content

import java.io.File

/**
 * Desktop stand-in for Android's Context: the game only asks it for a folder to keep its save in and for
 * [SharedPreferences], which are stored as properties files in that folder.
 */
open class Context(val filesDir: File) {
    private val prefs = HashMap<String, SharedPreferences>()

    val applicationContext: Context get() = this

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        synchronized(prefs) { prefs.getOrPut(name) { FileSharedPreferences(File(filesDir, "$name.properties")) } }

    companion object {
        const val MODE_PRIVATE = 0
    }
}

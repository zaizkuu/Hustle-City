package android.content

import java.io.File
import java.util.Properties

/** Desktop stand-in for Android's SharedPreferences (the subset the game uses). */
interface SharedPreferences {
    fun getInt(key: String, defValue: Int): Int
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getString(key: String, defValue: String?): String?
    fun contains(key: String): Boolean
    fun edit(): Editor

    interface Editor {
        fun putInt(key: String, value: Int): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putString(key: String, value: String?): Editor
        fun remove(key: String): Editor
        fun apply()
        fun commit(): Boolean
    }
}

/** Preferences kept in a properties file, written in full on every apply. */
internal class FileSharedPreferences(private val file: File) : SharedPreferences {
    private val values = Properties().also { p -> runCatching { if (file.exists()) file.inputStream().use { p.load(it) } } }

    private fun get(key: String): String? = synchronized(values) { values.getProperty(key) }

    override fun getInt(key: String, defValue: Int) = get(key)?.toIntOrNull() ?: defValue
    override fun getFloat(key: String, defValue: Float) = get(key)?.toFloatOrNull() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = get(key)?.toBooleanStrictOrNull() ?: defValue
    override fun getString(key: String, defValue: String?) = get(key) ?: defValue
    override fun contains(key: String) = get(key) != null

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, String?>()
        override fun putInt(key: String, value: Int) = also { changes[key] = value.toString() }
        override fun putFloat(key: String, value: Float) = also { changes[key] = value.toString() }
        override fun putBoolean(key: String, value: Boolean) = also { changes[key] = value.toString() }
        override fun putString(key: String, value: String?) = also { changes[key] = value }
        override fun remove(key: String) = also { changes[key] = null }
        override fun apply() { commit() }
        override fun commit(): Boolean = synchronized(values) {
            for ((k, v) in changes) if (v == null) values.remove(k) else values.setProperty(k, v)
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.outputStream().use { values.store(it, "Hustle City settings") }
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }.isSuccess
        }
    }
}

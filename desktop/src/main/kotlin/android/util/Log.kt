package android.util

/** Desktop stand-in for Android's Log: writes to standard error. */
object Log {
    @JvmStatic fun d(tag: String, msg: String): Int = print("D", tag, msg, null)
    @JvmStatic fun i(tag: String, msg: String): Int = print("I", tag, msg, null)
    @JvmStatic fun w(tag: String, msg: String): Int = print("W", tag, msg, null)
    @JvmStatic fun w(tag: String, msg: String, t: Throwable?): Int = print("W", tag, msg, t)
    @JvmStatic fun e(tag: String, msg: String): Int = print("E", tag, msg, null)
    @JvmStatic fun e(tag: String, msg: String, t: Throwable?): Int = print("E", tag, msg, t)

    private fun print(level: String, tag: String, msg: String, t: Throwable?): Int {
        System.err.println("$level/$tag: $msg")
        t?.printStackTrace()
        return 0
    }
}

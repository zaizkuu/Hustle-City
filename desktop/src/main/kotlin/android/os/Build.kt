package android.os

/** Desktop stand-in for Android's Build. The desktop behaves like a recent Android release. */
object Build {
    const val FINGERPRINT = "desktop"
    const val MODEL = "Desktop"

    object VERSION {
        const val SDK_INT = 35
    }
}

package android.util

/**
 * Stub of android.util.Log for unit tests that don't use Robolectric.
 * Prevents RuntimeException when production code calls Log.d/TAG/etc.
 */
object Log {
    private const val MAX_TAG_LENGTH = 23

    @JvmStatic
    fun d(tag: String, msg: String): Int {
        println("DEBUG [$tag] $msg")
        return 0
    }

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int {
        println("DEBUG [$tag] $msg")
        tr.printStackTrace()
        return 0
    }

    @JvmStatic
    fun e(tag: String, msg: String): Int {
        println("ERROR [$tag] $msg")
        return 0
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int {
        println("ERROR [$tag] $msg")
        tr.printStackTrace()
        return 0
    }

    @JvmStatic
    fun w(tag: String, msg: String): Int {
        println("WARN [$tag] $msg")
        return 0
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int {
        println("WARN [$tag] $msg")
        tr.printStackTrace()
        return 0
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int {
        println("INFO [$tag] $msg")
        return 0
    }

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean = true

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String = android.util.Log.getStackTraceString(tr)
}

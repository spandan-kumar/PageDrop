package app.pagedrop.update

/**
 * A release number such as 0.9.10: up to four dot-separated integers. A leading "v" and anything after the first
 * "-" or "+" (0.9.6-test) is ignored, so 0.9.6-test and 0.9.6 are the same number.
 */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = (parts.getOrElse(i) { 0 }).compareTo(other.parts.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?) = other is AppVersion && compareTo(other) == 0
    override fun hashCode() = parts.dropLastWhile { it == 0 }.hashCode()
    override fun toString() = parts.joinToString(".")

    fun isNewerThan(other: AppVersion) = this > other

    companion object {
        fun parse(text: String?): AppVersion? {
            val core = text?.trim()?.removePrefix("v")?.removePrefix("V")?.substringBefore('-')?.substringBefore('+')
                ?: return null
            val pieces = core.split('.')
            if (pieces.size !in 1..4) return null
            val numbers = pieces.map { p ->
                if (p.isEmpty() || p.length > 6 || !p.all { it in '0'..'9' }) return null
                p.toInt()
            }
            return AppVersion(numbers)
        }
    }
}

package app.pagedrop.update

/** What the Updates section of Settings shows. Only the last check time and the ready APK survive a restart. */
sealed interface UpdateStatus {
    data class Idle(val lastCheckedMs: Long) : UpdateStatus
    data object Checking : UpdateStatus
    data class Downloading(val version: String) : UpdateStatus
    data class WaitingForWifi(val version: String) : UpdateStatus
    data class NeedsPermission(val version: String) : UpdateStatus
    data class Ready(val version: String) : UpdateStatus
    data class Installing(val version: String) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/** The one line of plain words under "Version x" in Settings. */
fun UpdateStatus.describe(nowMs: Long): String = when (this) {
    is UpdateStatus.Idle ->
        if (lastCheckedMs > 0) "Up to date · checked ${relativeTime(lastCheckedMs, nowMs)}" else "Not checked yet"
    UpdateStatus.Checking -> "Checking for updates…"
    is UpdateStatus.Downloading -> "Downloading $version…"
    is UpdateStatus.WaitingForWifi -> "$version is available. It downloads when you're on Wi-Fi."
    is UpdateStatus.NeedsPermission -> "$version is available. PageDrop needs your permission to install it."
    is UpdateStatus.Ready -> "$version is ready to install"
    is UpdateStatus.Installing -> "Installing $version…"
    is UpdateStatus.Failed -> message
}

internal fun relativeTime(thenMs: Long, nowMs: Long): String {
    val minutes = ((nowMs - thenMs).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        minutes < 60 * 24 -> (minutes / 60).let { if (it == 1L) "1 hour ago" else "$it hours ago" }
        else -> (minutes / (60 * 24)).let { if (it == 1L) "1 day ago" else "$it days ago" }
    }
}

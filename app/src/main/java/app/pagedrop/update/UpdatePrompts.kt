package app.pagedrop.update

/** When the in-app update cards show. Pure, so the rules are testable. */
object UpdatePrompts {
    /**
     * The version to show the "available, allow updates" card for, or null for no card. Needs a self-update build,
     * the switch on, an update waiting on the install permission, and no "Not now" for this version or a newer one.
     */
    fun allowCardVersion(
        selfUpdateBuild: Boolean,
        autoUpdate: Boolean,
        status: UpdateStatus,
        dismissedVersion: String?,
    ): String? {
        if (!selfUpdateBuild || !autoUpdate) return null
        val version = (status as? UpdateStatus.NeedsPermission)?.version ?: return null
        val shown = AppVersion.parse(version) ?: return null
        val dismissed = AppVersion.parse(dismissedVersion)
        if (dismissed != null && !shown.isNewerThan(dismissed)) return null
        return version
    }

    const val EXPLANATION =
        "PageDrop updates itself from its GitHub page and only installs versions signed by the same developer " +
            "as this copy. Android will ask you once to allow this."

    fun releaseNotesUrl(version: String) = "https://github.com/spandan-kumar/PageDrop/releases/tag/v$version"
}

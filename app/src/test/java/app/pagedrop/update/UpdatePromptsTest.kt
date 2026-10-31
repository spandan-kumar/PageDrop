package app.pagedrop.update

import app.pagedrop.data.NullSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptsTest {
    private val waiting = UpdateStatus.NeedsPermission("0.9.7")

    private fun card(
        build: Boolean = true,
        enabled: Boolean = true,
        status: UpdateStatus = waiting,
        dismissed: String? = null,
    ) = UpdatePrompts.allowCardVersion(build, enabled, status, dismissed)

    @Test fun shownWhenAnUpdateWaitsOnTheInstallPermission() = assertEquals("0.9.7", card())

    @Test fun hiddenForOtherStatuses() {
        for (s in listOf(UpdateStatus.Idle(0), UpdateStatus.Checking, UpdateStatus.Ready("0.9.7"), UpdateStatus.Failed("x"))) {
            assertNull("$s", card(status = s))
        }
    }

    @Test fun notNowHidesItForThatVersionButNotForANewerOne() {
        assertNull(card(dismissed = "0.9.7"))
        assertNull(card(dismissed = "0.9.8"))
        assertEquals("0.9.8", card(status = UpdateStatus.NeedsPermission("0.9.8"), dismissed = "0.9.7"))
    }

    @Test fun neverWithoutSelfUpdateOrWithTheSwitchOff() {
        assertNull(card(build = false))
        assertNull(card(enabled = false))
    }

    @Test fun dismissalIsRemembered() {
        val prefs = NullSharedPreferences()
        UpdateSettings(prefs).dismissCardFor("0.9.7")
        assertEquals("0.9.7", UpdateSettings(prefs).dismissedVersion.value)
    }

    @Test fun updatedToIsNotShownOnTheFirstLaunch() {
        val settings = UpdateSettings(NullSharedPreferences())
        settings.noteLaunch(20, "0.9.6")
        assertNull(settings.announcedVersion.value)
        settings.noteLaunch(20, "0.9.6")
        assertNull(settings.announcedVersion.value)
    }

    @Test fun updatedToShowsOnceAfterTheVersionCodeIncreases() {
        val prefs = NullSharedPreferences()
        val settings = UpdateSettings(prefs)
        settings.noteLaunch(20, "0.9.6")
        settings.noteLaunch(21, "0.9.7")
        assertEquals("0.9.7", settings.announcedVersion.value)
        // It survives a restart until dismissed, then never returns.
        assertEquals("0.9.7", UpdateSettings(prefs).announcedVersion.value)
        settings.dismissAnnouncement()
        settings.noteLaunch(21, "0.9.7")
        assertNull(settings.announcedVersion.value)
        assertNull(UpdateSettings(prefs).announcedVersion.value)
    }

    @Test fun aDowngradeIsNotAnnounced() {
        val settings = UpdateSettings(NullSharedPreferences())
        settings.noteLaunch(21, "0.9.7")
        settings.noteLaunch(20, "0.9.6")
        assertNull(settings.announcedVersion.value)
    }

    @Test fun whatsNewOpensTheReleasePageForTheTag() {
        assertEquals(
            "https://github.com/spandan-kumar/PageDrop/releases/tag/v0.9.7",
            UpdatePrompts.releaseNotesUrl("0.9.7")
        )
        assertTrue(UpdatePrompts.EXPLANATION.startsWith("PageDrop updates itself from its GitHub page"))
    }
}

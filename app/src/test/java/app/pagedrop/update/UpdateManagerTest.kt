package app.pagedrop.update

import app.pagedrop.data.NullSharedPreferences
import app.pagedrop.discover.FakeHttp
import app.pagedrop.discover.tempDir
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateManagerTest {
    private val apkBytes = "pretend this is an apk".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(apkBytes).toHex()
    private val installedFacts = ApkFacts("app.pagedrop", 20, setOf("cert"))
    private val newFacts = ApkFacts("app.pagedrop", 21, setOf("cert"))

    private fun releaseJson(tag: String) =
        checkNotNull(javaClass.getResourceAsStream("/update/latest_release.json")).readBytes().decodeToString()
            .replace("v0.9.6", tag)

    private class FakeInstaller(var allowed: Boolean = true) : ApkInstaller {
        val installed = mutableListOf<String>()
        override fun canInstall() = allowed
        override fun install(file: File, version: String) { installed += version }
    }

    private class FakeNotifier : UpdateNotifier {
        val posted = mutableListOf<String>()
        override fun readyToInstall(version: String, confirm: android.content.Intent) { posted += version }
    }

    private inner class Rig(
        tag: String = "v0.9.7",
        enabledBuild: Boolean = true,
        var metered: Boolean = false,
        facts: ApkFacts? = newFacts,
        sha256File: String = "$sha  PageDrop-$tag.apk\n",
        val installer: FakeInstaller = FakeInstaller(),
    ) {
        val http = FakeHttp().apply {
            on("api.github.com", releaseJson(tag))
            on(".apk.sha256", sha256File)
            streamBytes = mapOf(".apk" to apkBytes)
        }
        val settings = UpdateSettings(NullSharedPreferences())
        val notifier = FakeNotifier()
        val dir = File(tempDir(), "updates")
        val manager = UpdateManager(
            enabledBuild = enabledBuild,
            currentVersionName = "0.9.6",
            currentVersionCode = 20,
            http = http,
            settings = settings,
            verifier = { ApkVerifier(installedFacts, { facts }) },
            installer = installer,
            notifier = notifier,
            isMetered = { metered },
            dir = dir,
            now = { 1_000L },
        )
        fun run(user: Boolean, installAfter: Boolean = false) = runBlocking { manager.run(user, installAfter) }
    }

    @Test fun aBuildWithoutSelfUpdateNeverTouchesTheNetwork() {
        val rig = Rig(enabledBuild = false)
        rig.run(user = true); rig.run(user = false)
        assertTrue(rig.http.requests.isEmpty())
        assertEquals(UpdateStatus.Idle(0), rig.manager.status.value)
    }

    @Test fun upToDateRecordsTheCheckAndDownloadsNothing() {
        val rig = Rig(tag = "v0.9.6")
        rig.run(user = true)
        assertEquals(UpdateStatus.Idle(1_000L), rig.manager.status.value)
        assertEquals(1, rig.http.requests.size)
        assertTrue(rig.http.requests.single().first.contains("api.github.com/repos/spandan-kumar/PageDrop/releases/latest"))
    }

    @Test fun aSuffixedSameVersionIsNotAnUpdate() {
        val rig = Rig(tag = "v0.9.6-test")
        rig.run(user = true)
        assertTrue(rig.manager.status.value is UpdateStatus.Idle)
    }

    @Test fun buttonCheckDownloadsVerifiesAndWaitsForInstall() {
        val rig = Rig()
        rig.run(user = true)
        assertEquals(UpdateStatus.Ready("0.9.7"), rig.manager.status.value)
        assertEquals("0.9.7", rig.settings.readyVersion)
        assertTrue(rig.installer.installed.isEmpty())
        assertEquals(listOf("PageDrop-0.9.7.apk"), rig.dir.list()!!.toList())

        runBlocking { rig.manager.installReady() }
        assertEquals(listOf("0.9.7"), rig.installer.installed)
        assertEquals(UpdateStatus.Installing("0.9.7"), rig.manager.status.value)
    }

    @Test fun scheduledRunInstallsWithoutAsking() {
        val rig = Rig()
        rig.run(user = false)
        assertEquals(listOf("0.9.7"), rig.installer.installed)
    }

    @Test fun scheduledRunOnAMeteredNetworkDoesNotDownload() {
        val rig = Rig(metered = true)
        rig.run(user = false)
        assertEquals(UpdateStatus.WaitingForWifi("0.9.7"), rig.manager.status.value)
        assertFalse(rig.http.requests.any { it.first.endsWith(".apk") })
        assertTrue(rig.installer.installed.isEmpty())
    }

    @Test fun theButtonDownloadsOnAnyNetwork() {
        val rig = Rig(metered = true)
        rig.run(user = true)
        assertEquals(UpdateStatus.Ready("0.9.7"), rig.manager.status.value)
    }

    @Test fun doesNotDownloadWhileInstallsAreNotAllowed() {
        val rig = Rig(installer = FakeInstaller(allowed = false))
        rig.run(user = false)
        assertEquals(UpdateStatus.NeedsPermission("0.9.7"), rig.manager.status.value)
        assertTrue(rig.notifier.posted.isEmpty()) // no cold notification from the scheduler path
        assertEquals("0.9.7", rig.settings.permissionVersion)
        assertFalse(rig.http.requests.any { it.first.endsWith(".apk") })

        rig.run(user = true)
        assertTrue(rig.notifier.posted.isEmpty())
        assertFalse(rig.http.requests.any { it.first.endsWith(".apk") })

        rig.installer.allowed = true
        assertTrue(rig.manager.permissionMayHaveChanged())
        assertEquals(null, rig.settings.permissionVersion)

        // Back from the allow screen: download if needed and install straight away.
        rig.run(user = true, installAfter = true)
        assertEquals(listOf("0.9.7"), rig.installer.installed)
    }

    @Test fun theWaitingUpdateSurvivesARestart() {
        val rig = Rig(installer = FakeInstaller(allowed = false))
        rig.run(user = false)
        val restarted = UpdateManager(
            true, "0.9.6", 20, rig.http, rig.settings, { ApkVerifier(installedFacts, { null }) },
            rig.installer, rig.notifier, { false }, rig.dir,
        )
        assertEquals(UpdateStatus.NeedsPermission("0.9.7"), restarted.status.value)
    }

    @Test fun nothingIsPostedWhenAnUpdateInstallsNormally() {
        val rig = Rig()
        rig.run(user = false)
        assertTrue(rig.notifier.posted.isEmpty())
    }

    @Test fun reusesAVerifiedDownloadForTheSameVersion() {
        val rig = Rig()
        rig.run(user = true)
        val downloads = { rig.http.requests.count { it.first.endsWith(".apk") } }
        assertEquals(1, downloads())
        rig.run(user = true)
        rig.run(user = false)
        assertEquals(1, downloads())
    }

    @Test fun aWrongChecksumDeletesTheFileAndInstallsNothing() {
        val rig = Rig(sha256File = "${"0".repeat(64)}  PageDrop-v0.9.7.apk\n")
        rig.run(user = false)
        val status = rig.manager.status.value as UpdateStatus.Failed
        assertTrue(status.message, status.message.contains("checksum"))
        assertTrue(rig.installer.installed.isEmpty())
        assertTrue(rig.dir.listFiles().orEmpty().isEmpty())
        assertEquals(null, rig.settings.readyVersion)
    }

    @Test fun aDifferentSigningKeyIsRejected() {
        val rig = Rig(facts = ApkFacts("app.pagedrop", 21, setOf("other-cert")))
        rig.run(user = true)
        assertTrue((rig.manager.status.value as UpdateStatus.Failed).message.contains("same key"))
        assertTrue(rig.dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun offlineIsQuietForScheduledRunsAndPlainForTheButton() {
        val rig = Rig()
        rig.http.routes.clear()
        rig.http.fail("api.github.com", java.io.IOException("offline"))
        rig.run(user = false)
        assertEquals(UpdateStatus.Idle(0), rig.manager.status.value)
        assertEquals(null, rig.settings.lastError)

        rig.run(user = true)
        assertEquals(
            "Couldn't check for updates. Check your connection and try again.",
            (rig.manager.status.value as UpdateStatus.Failed).message
        )
    }

    @Test fun rateLimitOrServerErrorsAreHandledQuietly() {
        val rig = Rig()
        rig.http.routes.clear()
        rig.http.on("api.github.com", "{\"message\":\"rate limit\"}", code = 403)
        rig.run(user = false)
        assertEquals(UpdateStatus.Idle(0), rig.manager.status.value)
    }

    @Test fun anOversizedDownloadIsRefused() {
        val rig = Rig()
        rig.http.streamContentLength = UpdateManager.MAX_BYTES + 1
        rig.run(user = true)
        assertTrue(rig.manager.status.value is UpdateStatus.Failed)
        assertTrue(rig.dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun installFailureIsStoredInPlainWords() {
        val rig = Rig()
        rig.run(user = true)
        rig.manager.onInstallFailed("INSTALL_FAILED_INSUFFICIENT_STORAGE")
        assertEquals(
            UpdateStatus.Failed("The install failed: INSTALL_FAILED_INSUFFICIENT_STORAGE"),
            rig.manager.status.value
        )
    }
}

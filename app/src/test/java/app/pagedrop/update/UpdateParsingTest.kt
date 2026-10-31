package app.pagedrop.update

import app.pagedrop.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParsingTest {
    private fun v(s: String) = AppVersion.parse(s)!!

    @Test fun newerPatchMinorAndMajor() {
        assertTrue(v("0.9.7").isNewerThan(v("0.9.6")))
        assertTrue(v("1.0.0").isNewerThan(v("0.9.9")))
        assertTrue(v("0.10.0").isNewerThan(v("0.9.9")))
    }

    @Test fun comparesNumericallyNotAsText() {
        assertTrue(v("0.9.10").isNewerThan(v("0.9.9")))
        assertFalse(v("0.9.9").isNewerThan(v("0.9.10")))
    }

    @Test fun sameOrOlderIsNotNewer() {
        assertFalse(v("0.9.6").isNewerThan(v("0.9.6")))
        assertFalse(v("0.9.5").isNewerThan(v("0.9.6")))
        assertFalse(v("1.0").isNewerThan(v("1.0.0")))
    }

    @Test fun suffixesAndLeadingVAreIgnored() {
        assertEquals(v("0.9.6"), v("0.9.6-test"))
        assertEquals(v("0.9.6"), v("v0.9.6"))
        assertFalse(v("0.9.6-test").isNewerThan(v("0.9.6")))
        assertEquals("0.9.6", v("v0.9.6+build5").toString())
    }

    @Test fun garbageIsRejected() {
        for (bad in listOf("", "latest", "v", "1..2", "1.x.3", "1.2.3.4.5", "-1.0", "1.-2", "99999999.0", " ")) {
            assertNull("'$bad'", AppVersion.parse(bad))
        }
        assertNull(AppVersion.parse(null))
    }

    private fun fixture() = checkNotNull(javaClass.getResourceAsStream("/update/latest_release.json")).readBytes().decodeToString()

    @Test fun parsesTheRealReleaseFixture() {
        val release = ReleaseParser.parse(fixture())!!
        assertEquals(v("0.9.6"), release.version)
        assertEquals("v0.9.6", release.tag)
        assertEquals("PageDrop-v0.9.6.apk", release.apkName)
        assertEquals("https://github.com/spandan-kumar/PageDrop/releases/download/v0.9.6/PageDrop-v0.9.6.apk", release.apkUrl)
        assertEquals(release.apkUrl + ".sha256", release.sha256Url)
    }

    @Test fun rejectsDraftsPrereleasesAndMissingAssets() {
        assertNull(ReleaseParser.parse(fixture().replace("\"draft\": false", "\"draft\": true")))
        assertNull(ReleaseParser.parse(fixture().replace("\"prerelease\": false", "\"prerelease\": true")))
        assertNull(ReleaseParser.parse(fixture().replace("PageDrop-v0.9.6.apk.sha256", "other.txt")))
        assertNull(ReleaseParser.parse(fixture().replace("\"tag_name\": \"v0.9.6\"", "\"tag_name\": \"nightly\"")))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("[]"))
        assertNull(ReleaseParser.parse("{\"tag_name\":\"v1.0.0\"}"))
    }

    @Test fun rejectsAssetsFromOtherHosts() {
        val evil = fixture().replace("https://github.com/spandan-kumar/PageDrop/", "https://evil.example/x/")
        assertNull(ReleaseParser.parse(evil))
    }

    private val hash = "a".repeat(32) + "0123456789".repeat(3) + "ab"

    @Test fun parsesSha256sumOutput() {
        assertEquals(hash, ReleaseParser.parseSha256("$hash  PageDrop-v0.9.6.apk\n", "PageDrop-v0.9.6.apk"))
        assertEquals(hash, ReleaseParser.parseSha256("${hash.uppercase()} *PageDrop-v0.9.6.apk", "PageDrop-v0.9.6.apk"))
        assertEquals(hash, ReleaseParser.parseSha256("\n$hash\n", "PageDrop-v0.9.6.apk"))
    }

    @Test fun rejectsBadShaFiles() {
        assertNull(ReleaseParser.parseSha256("", "a.apk"))
        assertNull(ReleaseParser.parseSha256("tooshort  a.apk", "a.apk"))
        assertNull(ReleaseParser.parseSha256("${"z".repeat(64)}  a.apk", "a.apk"))
        assertNull(ReleaseParser.parseSha256("$hash  other.apk", "a.apk"))
        assertNotNull(ReleaseParser.parseSha256("$hash  a.apk", "a.apk"))
    }

    @Test fun selfUpdateIsOffUnlessTheBuildPropertyIsTrue() {
        // build.gradle.kts hands the property value to the tests; absent means false.
        val property = System.getProperty("pagedrop.selfUpdate")
        assertNotNull(property)
        assertEquals(property.toBoolean(), BuildConfig.SELF_UPDATE)
    }

    @Test fun statusTextIsPlainWords() {
        val now = 10_000_000_000L
        assertEquals("Up to date · checked 3 hours ago", UpdateStatus.Idle(now - 3 * 3_600_000L).describe(now))
        assertEquals("Up to date · checked just now", UpdateStatus.Idle(now - 5_000L).describe(now))
        assertEquals("Not checked yet", UpdateStatus.Idle(0).describe(now))
        assertEquals("Downloading 0.9.7…", UpdateStatus.Downloading("0.9.7").describe(now))
        assertEquals("0.9.7 is ready to install", UpdateStatus.Ready("0.9.7").describe(now))
        assertEquals("1 day ago", relativeTime(now - 25 * 3_600_000L, now))
    }
}

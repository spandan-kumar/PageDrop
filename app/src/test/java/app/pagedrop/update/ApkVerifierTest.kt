package app.pagedrop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkVerifierTest {
    private val installed = ApkFacts("app.pagedrop", 20, setOf("cert-a"))
    private val file = File.createTempFile("update", ".apk").apply { writeText("apk bytes"); deleteOnExit() }
    private val goodSha = sha256Hex(file)

    private fun verify(facts: ApkFacts?, sha: String = goodSha): Verdict =
        ApkVerifier(installed, { facts }).verify(file, sha)

    private fun assertRejected(verdict: Verdict, containing: String) {
        assertTrue("$verdict", verdict is Verdict.Reject)
        assertTrue((verdict as Verdict.Reject).reason, verdict.reason.contains(containing))
    }

    @Test fun acceptsANewerBuildSignedWithTheSameKey() {
        val facts = ApkFacts("app.pagedrop", 21, setOf("cert-a"))
        assertEquals(Verdict.Accept(facts), verify(facts))
    }

    @Test fun hashIsComparedIgnoringCase() {
        assertTrue(verify(ApkFacts("app.pagedrop", 21, setOf("cert-a")), goodSha.uppercase()) is Verdict.Accept)
    }

    @Test fun rejectsAHashMismatch() =
        assertRejected(verify(ApkFacts("app.pagedrop", 21, setOf("cert-a")), "0".repeat(64)), "checksum")

    @Test fun rejectsAnotherPackage() =
        assertRejected(verify(ApkFacts("evil.app", 21, setOf("cert-a"))), "isn't PageDrop")

    @Test fun rejectsTheSameOrAnOlderVersionCode() {
        assertRejected(verify(ApkFacts("app.pagedrop", 20, setOf("cert-a"))), "isn't newer")
        assertRejected(verify(ApkFacts("app.pagedrop", 19, setOf("cert-a"))), "isn't newer")
    }

    @Test fun rejectsADifferentSigningCertificate() {
        assertRejected(verify(ApkFacts("app.pagedrop", 21, setOf("cert-b"))), "same key")
        assertRejected(verify(ApkFacts("app.pagedrop", 21, setOf("cert-a", "cert-b"))), "same key")
        assertRejected(verify(ApkFacts("app.pagedrop", 21, emptySet())), "same key")
    }

    @Test fun rejectsAFileThatIsNotAnApk() = assertRejected(verify(null), "valid app package")

    @Test fun aThrowingInspectorIsARejection() {
        val verdict = ApkVerifier(installed, { throw IllegalStateException("boom") }).verify(file, goodSha)
        assertRejected(verdict, "valid app package")
    }
}

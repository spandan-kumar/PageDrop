package app.pagedrop.update

import java.io.File
import java.security.MessageDigest

/** What matters about an APK file (or the installed app) for deciding whether it is a genuine PageDrop update. */
data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    /** Lower-case hex SHA-256 of each signing certificate. */
    val certSha256: Set<String>,
)

/** Reads [ApkFacts] from an APK file. Null when it isn't a readable APK. */
fun interface ApkInspector {
    fun inspect(file: File): ApkFacts?
}

sealed interface Verdict {
    data class Accept(val facts: ApkFacts) : Verdict
    /** [reason] is plain language, shown to the user. */
    data class Reject(val reason: String) : Verdict
}

/** Accepts a downloaded APK only if it matches its published hash and is a newer build of this app, signed by the same key. */
class ApkVerifier(
    private val installed: ApkFacts,
    private val inspector: ApkInspector,
    private val hasher: (File) -> String = ::sha256Hex,
) {
    fun verify(file: File, expectedSha256: String): Verdict {
        if (!hasher(file).equals(expectedSha256, ignoreCase = true)) {
            return Verdict.Reject("The download didn't match its checksum, so it was discarded.")
        }
        val facts = runCatching { inspector.inspect(file) }.getOrNull()
            ?: return Verdict.Reject("The download isn't a valid app package, so it was discarded.")
        if (facts.packageName != installed.packageName) {
            return Verdict.Reject("The download isn't PageDrop, so it was discarded.")
        }
        if (facts.versionCode <= installed.versionCode) {
            return Verdict.Reject("The download isn't newer than the version you have, so it was discarded.")
        }
        if (facts.certSha256.isEmpty() || facts.certSha256 != installed.certSha256) {
            return Verdict.Reject("The download isn't signed with the same key as this app, so it was discarded.")
        }
        return Verdict.Accept(facts)
    }
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().toHex()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

package app.pagedrop.update

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import app.pagedrop.BuildConfig
import app.pagedrop.discover.DiscoverHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Starts the system install of a verified APK. */
interface ApkInstaller {
    fun canInstall(): Boolean
    fun install(file: File, version: String)
}

interface UpdateNotifier {
    fun needsPermission(version: String)
}

/**
 * Checks GitHub Releases for a newer PageDrop, downloads and verifies it, and hands it to the system installer.
 * Only does anything when [enabledBuild] (BuildConfig.SELF_UPDATE) is true; otherwise it never touches the network.
 */
@Singleton
class UpdateManager internal constructor(
    private val enabledBuild: Boolean,
    private val currentVersionName: String,
    private val http: DiscoverHttp,
    private val settings: UpdateSettings,
    private val verifier: () -> ApkVerifier,
    private val installer: ApkInstaller,
    private val notifier: UpdateNotifier,
    private val isMetered: () -> Boolean,
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context, http: DiscoverHttp, settings: UpdateSettings) : this(
        enabledBuild = BuildConfig.SELF_UPDATE,
        currentVersionName = BuildConfig.VERSION_NAME,
        http = http,
        settings = settings,
        verifier = {
            val inspector = AndroidApkInspector(context)
            ApkVerifier(inspector.installed(), inspector)
        },
        installer = AndroidApkInstaller(context),
        notifier = AndroidUpdateNotifier(context),
        isMetered = {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered ?: true
        },
        dir = File(context.cacheDir, "updates"),
    )

    private val mutex = Mutex()
    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private fun initialStatus(): UpdateStatus {
        val ready = settings.readyVersion
        val readyNewer = ready != null &&
            AppVersion.parse(ready)?.let { v -> AppVersion.parse(currentVersionName)?.let(v::isNewerThan) } == true
        return when {
            readyNewer -> UpdateStatus.Ready(ready!!)
            settings.lastError != null -> UpdateStatus.Failed(settings.lastError!!)
            else -> UpdateStatus.Idle(settings.lastCheckedMs)
        }
    }

    /**
     * One check. A scheduled run ([userInitiated] false) installs what it finds, only downloads on an unmetered
     * network and stays quiet about errors; a run from the button downloads on any network and stops at "ready".
     */
    suspend fun run(userInitiated: Boolean) {
        if (!enabledBuild) return
        mutex.withLock { withContext(Dispatchers.IO) { runLocked(userInitiated) } }
    }

    private suspend fun runLocked(userInitiated: Boolean) {
        val before = _status.value
        val current = AppVersion.parse(currentVersionName) ?: return
        if (userInitiated) _status.value = UpdateStatus.Checking
        val release = try {
            fetchRelease()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            if (userInitiated) fail("Couldn't check for updates. Check your connection and try again.")
            else _status.value = before.takeIf { it !is UpdateStatus.Checking } ?: UpdateStatus.Idle(settings.lastCheckedMs)
            return
        }
        settings.lastCheckedMs = now()
        val version = release.version.toString()
        if (!release.version.isNewerThan(current)) {
            settings.clearReady()
            settings.lastError = null
            clearCache(keep = null)
            _status.value = UpdateStatus.Idle(settings.lastCheckedMs)
            return
        }
        if (!installer.canInstall()) {
            _status.value = UpdateStatus.NeedsPermission(version)
            if (!userInitiated) notifier.needsPermission(version)
            return
        }
        val file = File(dir, "PageDrop-$version.apk")
        val reusable = settings.readyVersion == version && file.isFile &&
            settings.readySha256?.let { runCatching { verifier().verify(file, it) }.getOrNull() } is Verdict.Accept
        if (!reusable) {
            if (!userInitiated && isMetered()) {
                _status.value = UpdateStatus.WaitingForWifi(version)
                return
            }
            settings.clearReady()
            _status.value = UpdateStatus.Downloading(version)
            try {
                val sha = download(release, file)
                when (val verdict = verifier().verify(file, sha)) {
                    is Verdict.Reject -> {
                        file.delete()
                        fail(verdict.reason)
                        return
                    }
                    is Verdict.Accept -> settings.setReady(version, sha)
                }
            } catch (e: CancellationException) {
                file.delete()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed: ${e.message}")
                file.delete()
                if (userInitiated) fail("Couldn't download $version. Try again later.")
                else _status.value = UpdateStatus.Idle(settings.lastCheckedMs)
                return
            }
        }
        settings.lastError = null
        clearCache(keep = file)
        _status.value = UpdateStatus.Ready(version)
        if (!userInitiated) startInstall(version, file)
    }

    /** The Install button: re-verifies the cached APK and hands it to the system installer. */
    suspend fun installReady() {
        if (!enabledBuild) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val version = settings.readyVersion ?: return@withContext
                val sha = settings.readySha256
                val file = File(dir, "PageDrop-$version.apk")
                if (!installer.canInstall()) {
                    _status.value = UpdateStatus.NeedsPermission(version)
                    return@withContext
                }
                val ok = sha != null && file.isFile && runCatching { verifier().verify(file, sha) }.getOrNull() is Verdict.Accept
                if (!ok) {
                    file.delete()
                    settings.clearReady()
                    fail("The downloaded update is no longer valid. Check for updates again.")
                    return@withContext
                }
                startInstall(version, file)
            }
        }
    }

    private fun startInstall(version: String, file: File) {
        _status.value = UpdateStatus.Installing(version)
        try {
            installer.install(file, version)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start install: ${e.message}")
            fail("Couldn't start the install. Try again from Settings.")
        }
    }

    /** The system wants the user to confirm; the Install button works again. */
    fun onInstallNeedsConfirmation() {
        settings.readyVersion?.let { _status.value = UpdateStatus.Ready(it) }
    }

    fun onInstallCancelled() = onInstallNeedsConfirmation()

    fun onInstallFailed(message: String?) {
        settings.clearReady()
        fail(message?.takeIf { it.isNotBlank() }?.let { "The install failed: $it" } ?: "The install failed.")
    }

    /** Returns from the system "allow installs" screen: if it's now allowed the next check can go ahead. */
    fun permissionMayHaveChanged(): Boolean {
        if (_status.value is UpdateStatus.NeedsPermission && installer.canInstall()) {
            _status.value = UpdateStatus.Idle(settings.lastCheckedMs)
            return true
        }
        return false
    }

    private fun fail(message: String) {
        settings.lastError = message
        _status.value = UpdateStatus.Failed(message)
    }

    private fun fetchRelease(): ReleaseInfo {
        val reply = http.withTimeout(20_000).get(LATEST_URL, accept = "application/vnd.github+json")
        if (!reply.ok) throw IOException("GitHub answered HTTP ${reply.code}")
        return ReleaseParser.parse(reply.body) ?: throw IOException("No usable release found")
    }

    /** Downloads the APK to [target] and returns the hash its .sha256 asset publishes. */
    private suspend fun download(release: ReleaseInfo, target: File): String {
        val shaReply = http.withTimeout(20_000).get(release.sha256Url)
        if (!shaReply.ok) throw IOException("Checksum answered HTTP ${shaReply.code}")
        val sha = ReleaseParser.parseSha256(shaReply.body, release.apkName) ?: throw IOException("Unreadable checksum")
        dir.mkdirs()
        val part = File(dir, target.name + ".part")
        try {
            http.open(release.apkUrl).use { stream ->
                if (stream.code != 200) throw IOException("Download answered HTTP ${stream.code}")
                if (!stream.finalUrl.startsWith("https://")) throw IOException("Download left HTTPS")
                if (stream.contentLength > MAX_BYTES) throw IOException("Download too large")
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = stream.input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("Download too large")
                        out.write(buf, 0, n)
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("Couldn't store the download")
        } finally {
            part.delete()
        }
        return sha
    }

    /** Deletes everything in the update cache except [keep]. */
    private fun clearCache(keep: File?) {
        dir.listFiles()?.forEach { if (it != keep) it.delete() }
    }

    companion object {
        private const val TAG = "UpdateManager"
        const val LATEST_URL = "https://api.github.com/repos/spandan-kumar/PageDrop/releases/latest"
        const val MAX_BYTES = 100L * 1024 * 1024
    }
}

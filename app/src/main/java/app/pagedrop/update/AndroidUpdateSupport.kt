package app.pagedrop.update

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import app.pagedrop.R
import app.pagedrop.BuildConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updateManager(): UpdateManager
    fun updateSettings(): UpdateSettings
}

/** Reads package name, version code and signing certificates from an APK file or from the installed app. */
@Suppress("DEPRECATION")
class AndroidApkInspector(private val context: Context) : ApkInspector {
    private val pm get() = context.packageManager

    override fun inspect(file: File): ApkFacts? = pm.getPackageArchiveInfo(file.path, flags())?.let(::facts)

    fun installed(): ApkFacts = facts(pm.getPackageInfo(context.packageName, flags()))

    private fun flags() =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private fun facts(info: PackageInfo): ApkFacts {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val certs = signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }
        return ApkFacts(info.packageName, PackageInfoCompat.getLongVersionCode(info), certs.toSet())
    }
}

/** Installs through a PackageInstaller session; the result arrives at [InstallResultReceiver]. */
class AndroidApkInstaller(private val context: Context) : ApkInstaller {
    override fun canInstall() = context.packageManager.canRequestPackageInstalls()

    override fun install(file: File, version: String) {
        val installer = context.packageManager.packageInstaller
        // A half-finished session from an earlier attempt would only hold space.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            // Honoured once PageDrop is the installer of record, i.e. from the update after the first self-update.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("PageDrop-$version.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(InstallResultReceiver.ACTION).setClass(context, InstallResultReceiver::class.java)
                // Mutable: the system fills in the status extras.
                val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }
}

class AndroidUpdateNotifier(private val context: Context) : UpdateNotifier {
    override fun needsPermission(version: String) {
        val intent = allowInstallsIntent(context)
        notify(
            NOTIFICATION_PERMISSION,
            "Allow PageDrop to install updates",
            "PageDrop $version is available.",
            PendingIntent.getActivity(context, NOTIFICATION_PERMISSION, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
    }

    /** [confirm] is the system's install confirmation screen; tapping the notification opens it. */
    fun readyToInstall(version: String, confirm: Intent) {
        notify(
            NOTIFICATION_READY,
            "PageDrop $version is ready to install",
            "Tap to finish updating.",
            PendingIntent.getActivity(context, NOTIFICATION_READY, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
    }

    private fun notify(id: Int, title: String, text: String, tap: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "pagedrop_updates"
        const val NOTIFICATION_READY = 2001
        const val NOTIFICATION_PERMISSION = 2002
    }
}

/** Opens Android's "Install unknown apps" switch for PageDrop. */
fun allowInstallsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Receives the PackageInstaller result: asks for confirmation, or records the outcome. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.SELF_UPDATE) return
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, UpdateEntryPoint::class.java)
        val manager = entry.updateManager()
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)) ?: return
                manager.onInstallNeedsConfirmation()
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (inForeground(context)) {
                    runCatching { context.startActivity(confirm) }
                        .onFailure { Log.w(TAG, "Couldn't open the install screen: ${it.message}") }
                } else {
                    AndroidUpdateNotifier(context).readyToInstall(entry.updateSettings().readyVersion ?: "", confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The new version replaces this process.
            PackageInstaller.STATUS_FAILURE_ABORTED -> manager.onInstallCancelled()
            else -> {
                Log.w(TAG, "Install failed, status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                manager.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            }
        }
    }

    private fun inForeground(context: Context): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    companion object {
        const val ACTION = "app.pagedrop.update.INSTALL_RESULT"
        private const val TAG = "InstallResult"
    }
}

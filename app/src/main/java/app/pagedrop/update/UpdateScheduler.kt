package app.pagedrop.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.pagedrop.BuildConfig
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the scheduler asks of WorkManager; a fake in tests. */
interface UpdateWork {
    fun schedulePeriodic()
    fun cancelPeriodic()
    fun runNow(installAfter: Boolean)
}

/**
 * Keeps the daily check scheduled exactly when it should be: only in builds with self-update, and only while
 * "Update automatically" is on. In other builds it does nothing at all.
 */
@Singleton
class UpdateScheduler internal constructor(
    private val selfUpdateBuild: Boolean,
    private val enabled: () -> Boolean,
    private val work: UpdateWork,
) {
    @Inject
    constructor(@ApplicationContext context: Context, settings: UpdateSettings) :
        this(BuildConfig.SELF_UPDATE, { settings.enabled.value }, WorkManagerUpdateWork(context))

    /** Call on app start and whenever the switch changes. */
    fun sync() {
        if (!selfUpdateBuild) return
        if (enabled()) work.schedulePeriodic() else work.cancelPeriodic()
    }

    /** A check started by the "Check for updates" button. */
    fun checkNow(installAfter: Boolean = false) {
        if (selfUpdateBuild) work.runNow(installAfter)
    }
}

internal class WorkManagerUpdateWork(private val context: Context) : UpdateWork {
    private val manager get() = WorkManager.getInstance(context)
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS).setConstraints(connected).build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelPeriodic() {
        manager.cancelUniqueWork(PERIODIC)
    }

    override fun runNow(installAfter: Boolean) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(connected)
            .setInputData(workDataOf(UpdateWorker.KEY_USER to true, UpdateWorker.KEY_INSTALL to installAfter))
            .build()
        manager.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PERIODIC = "pagedrop_update_check"
        const val NOW = "pagedrop_update_check_now"
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(applicationContext, UpdateEntryPoint::class.java).updateManager()
        // Failures are recorded by the manager and retried at the next run, so the work itself always succeeds.
        manager.run(
            userInitiated = inputData.getBoolean(KEY_USER, false),
            installAfter = inputData.getBoolean(KEY_INSTALL, false),
        )
        return Result.success()
    }

    companion object {
        const val KEY_USER = "user_initiated"
        const val KEY_INSTALL = "install_after"
    }
}

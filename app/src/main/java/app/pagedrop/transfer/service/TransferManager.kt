package app.pagedrop.transfer.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TransferManager {
    private const val UNIQUE_WORK_NAME = "pagedrop_transfer"

    fun enqueueTransfer(context: Context, bookIds: List<Int>) {
        val inputData = Data.Builder()
            .putIntArray(TransferWorker.KEY_BOOK_IDS, bookIds.toIntArray())
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                30,
                TimeUnit.SECONDS
            )
            .addTag("transfer")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }
}

package app.pagedrop.transfer.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.pagedrop.data.KindleSettings
import app.pagedrop.data.local.database.AppDatabase
import app.pagedrop.data.local.database.Book
import app.pagedrop.transfer.sftp.KindleSftpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@HiltWorker
class TransferWorker @Inject constructor(
    @ApplicationContext private val appContext: Context,
    params: WorkerParameters,
    private val database: AppDatabase,
    private val kindleSettings: KindleSettings
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "TransferWorker"
        const val KEY_BOOK_IDS = "book_ids"
        const val KEY_RESULT = "result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_ERROR = "error"
    }

    override suspend fun doWork(): Result {
        val bookIds = inputData.getIntArray(KEY_BOOK_IDS)?.toList() ?: return Result.failure()

        val books = bookIds.mapNotNull { database.bookDao().getBookById(it) }
        if (books.isEmpty()) return Result.failure()

        setForeground(createForegroundInfo("Preparing..."))

        try {
            val thumbs = prepareThumbnails(books)
            val host = kindleSettings.host
            val port = kindleSettings.port
            val user = kindleSettings.username
            val pass = kindleSettings.password
            val dir = kindleSettings.targetDirectory
            val rescan = kindleSettings.triggerRescan

            val result = KindleSftpClient.transferBooks(
                books = books,
                host = host,
                port = port,
                user = user,
                pass = pass,
                directory = dir,
                triggerRescan = rescan,
                thumbnailBytes = thumbs
            ) { current, total, message ->
                val progress = if (total > 0) current * 100 / total else 0
                setProgress(workDataOf("progress" to progress, "message" to message))
                setForeground(createForegroundInfo(message))
            }

            if (result.isSuccess) {
                val timestamp = System.currentTimeMillis()
                books.forEach { database.bookDao().updateLastTransferred(it.uid, timestamp) }

                val notification = NotificationHelper.buildCompleteNotification(appContext, books.size)
                NotificationManagerCompat.from(appContext).notify(
                    NotificationHelper.COMPLETE_NOTIFICATION_ID,
                    notification.build()
                )
                Result.success(workDataOf(KEY_RESULT to RESULT_SUCCESS))
            } else {
                val error = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                val notification = NotificationHelper.buildErrorNotification(appContext, error, books.firstOrNull()?.title ?: "")
                NotificationManagerCompat.from(appContext).notify(
                    NotificationHelper.PROGRESS_NOTIFICATION_ID,
                    notification.build()
                )
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transfer failed: ${e.message}", e)
            val notification = NotificationHelper.buildErrorNotification(appContext, e.localizedMessage ?: "Error", "")
            NotificationManagerCompat.from(appContext).notify(
                NotificationHelper.PROGRESS_NOTIFICATION_ID,
                notification.build()
            )
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = NotificationHelper.buildProgressNotification(appContext, 0, 1, message).build()
        return ForegroundInfo(NotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
    }

    private fun prepareThumbnails(books: List<Book>): Map<Int, ByteArray> {
        val thumbs = mutableMapOf<Int, ByteArray>()
        books.forEach { book ->
            book.coverPath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val source = BitmapFactory.decodeFile(path) ?: return@let
                        val scaled = Bitmap.createScaledBitmap(source, 330, 430, true)
                        if (scaled != source) source.recycle()
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        scaled.recycle()
                        thumbs[book.uid] = out.toByteArray()
                    }
                } catch (_: Exception) { }
            }
        }
        return thumbs
    }
}

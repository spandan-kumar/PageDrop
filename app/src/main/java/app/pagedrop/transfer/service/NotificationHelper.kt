package app.pagedrop.transfer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.pagedrop.R
import app.pagedrop.ui.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "pagedrop_transfer"
    const val PROGRESS_NOTIFICATION_ID = 1001
    const val COMPLETE_NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Book Transfers",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows transfer progress and completion status"
            setShowBadge(false)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun buildProgressNotification(
        context: Context,
        current: Int,
        total: Int,
        bookName: String
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sending to Kindle")
            .setContentText(bookName)
            .setContentIntent(pendingIntent)
            .setProgress(total, current, false)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    fun buildCompleteNotification(
        context: Context,
        count: Int
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Transfer complete")
            .setContentText("$count book${if (count != 1) "s" else ""} sent to Kindle")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(true)
    }

    fun buildErrorNotification(
        context: Context,
        errorMessage: String,
        bookName: String
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Transfer failed")
            .setContentText("$bookName: $errorMessage")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
    }
}

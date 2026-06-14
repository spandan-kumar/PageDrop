package app.pagedrop.transfer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import app.pagedrop.data.local.database.Book
import app.pagedrop.transfer.hotspot.HotspotHelper
import app.pagedrop.transfer.server.BookServer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that runs the [BookServer] embedded HTTP server
 * and shows a persistent notification with the server URL.
 *
 * Activities communicate via [TransferBinder] to set queued books.
 */
@AndroidEntryPoint
class TransferService : Service() {

    companion object {
        private const val TAG = "TransferService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "pagedrop_transfer"
        private const val CHANNEL_NAME = "PageDrop Transfer"

        fun startIntent(context: Context): Intent {
            return Intent(context, TransferService::class.java)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, TransferService::class.java)
        }

        fun startService(context: Context) {
            context.startForegroundService(startIntent(context))
        }

        fun stopService(context: Context) {
            context.stopService(stopIntent(context))
        }
    }

    @Inject lateinit var bookServer: BookServer
    @Inject lateinit var hotspotHelper: HotspotHelper

    private val binder = TransferBinder()

    inner class TransferBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TransferService created")

        createNotificationChannel()
        startBookServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = hotspotHelper.getServerUrl(BookServer.PORT)
        val notification = buildNotification(url)
        startForeground(NOTIFICATION_ID, notification)
        Log.i(TAG, "TransferService started — server at $url")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBookServer()
        Log.d(TAG, "TransferService destroyed")
    }

    // ── Public API for bound activities ──────────────────────

    /**
     * Set the list of books available for download on the server.
     */
    fun setQueuedBooks(books: List<Book>) {
        bookServer.setQueuedBooks(books)
    }

    /**
     * Clear all queued books from the server.
     */
    fun clearQueue() {
        bookServer.clearQueue()
    }

    /**
     * Get the current server URL.
     */
    fun getServerUrl(): String {
        return hotspotHelper.getServerUrl(BookServer.PORT)
    }

    // ── Private helpers ──────────────────────────────────────

    private fun startBookServer() {
        try {
            bookServer.start()
            Log.i(TAG, "BookServer started on port ${BookServer.PORT}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BookServer", e)
        }
    }

    private fun stopBookServer() {
        try {
            bookServer.stop()
            Log.i(TAG, "BookServer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BookServer", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when PageDrop is ready for wireless book transfer"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(url: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PageDrop ready")
            .setContentText("Open on Kindle: $url")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()
    }
}

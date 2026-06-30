package app.pagedrop.transfer.sftp

import android.content.Context
import android.util.Log
import app.pagedrop.data.local.database.Book
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object KindleSftpClient {
    private const val TAG = "KindleSftpClient"

    /**
     * Tests connection to the Kindle SSH/SFTP server.
     */
    suspend fun testConnection(
        host: String,
        port: Int,
        user: String,
        pass: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var session: Session? = null
        try {
            if (host.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("IP address cannot be empty"))
            }
            Log.d(TAG, "Testing SSH connection to $user@$host:$port")
            val jsch = JSch()
            session = jsch.getSession(user, host, port)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(5000) // 5s timeout
            Log.d(TAG, "SSH Connection test successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SSH Connection test failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            session?.disconnect()
        }
    }

    /**
     * Transfers selected books to the Kindle via SFTP, then triggers a library rescan.
     */
    suspend fun transferBooks(
        books: List<Book>,
        host: String,
        port: Int,
        user: String,
        pass: String,
        directory: String,
        triggerRescan: Boolean,
        onProgress: (Int, Int, String) -> Unit // (currentBookIndex, totalBooks, statusMessage)
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftpChannel: ChannelSftp? = null
        try {
            if (host.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("IP address cannot be empty"))
            }

            onProgress(0, books.size, "Connecting to Kindle...")
            val jsch = JSch()
            session = jsch.getSession(user, host, port)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000) // 10s timeout

            onProgress(0, books.size, "Opening SFTP channel...")
            sftpChannel = session.openChannel("sftp") as ChannelSftp
            sftpChannel.connect(10000)

            // Ensure destination directory ends with /
            val destDir = if (directory.endsWith("/")) directory else "$directory/"

            books.forEachIndexed { index, book ->
                onProgress(index, books.size, "Sending: ${book.title}")
                val file = File(book.filePath)
                if (!file.exists()) {
                    throw java.io.FileNotFoundException("File not found: ${book.filePath}")
                }
                
                // Upload file
                file.inputStream().use { inputStream ->
                    sftpChannel.put(inputStream, destDir + book.fileName)
                }
            }

            if (triggerRescan) {
                onProgress(books.size, books.size, "Triggering Kindle library rescan...")
                val execChannel = session.openChannel("exec") as ChannelExec
                // Trigger Kindle library rescan via lipc-set-prop
                execChannel.setCommand("lipc-set-prop com.lab126.amznAssetsMgrService reScan 1")
                execChannel.connect()
                
                // Wait briefly for execution to complete
                var elapsed = 0
                while (!execChannel.isClosed && elapsed < 2000) {
                    Thread.sleep(100)
                    elapsed += 100
                }
                execChannel.disconnect()
            }

            onProgress(books.size, books.size, "Transfer completed successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SFTP transfer failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            sftpChannel?.disconnect()
            session?.disconnect()
        }
    }
}

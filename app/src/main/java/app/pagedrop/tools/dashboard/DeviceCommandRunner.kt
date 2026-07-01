package app.pagedrop.tools.dashboard

import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object DeviceCommandRunner {
    private const val TAG = "DeviceCommandRunner"
    private const val EXEC_TIMEOUT_MS = 5000

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    data class DeviceStatus(
        val firmwareVersion: String?,
        val serialNumber: String?,
        val freeSpace: String?,
        val uptime: String?,
        val batteryLevel: String?,
        val whispernetBlocked: Boolean?,
        val otaBlocked: Boolean?,
        val koreaderInstalled: Boolean
    )

    suspend fun runCommand(
        host: String,
        port: Int,
        user: String,
        pass: String,
        command: String,
        timeoutMs: Int = EXEC_TIMEOUT_MS
    ): CommandResult = withContext(Dispatchers.IO) {
        var session: Session? = null
        var execChannel: ChannelExec? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, host, port)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10_000)

            execChannel = session.openChannel("exec") as ChannelExec
            execChannel.setCommand(command)
            execChannel.connect()

            val stdout = BufferedReader(InputStreamReader(execChannel.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(execChannel.errStream)).readText()

            var elapsed = 0
            while (!execChannel.isClosed && elapsed < timeoutMs) {
                Thread.sleep(100)
                elapsed += 100
            }

            CommandResult(
                exitCode = execChannel.exitStatus,
                stdout = stdout.trim(),
                stderr = stderr.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command → ${e.message}")
            CommandResult(exitCode = -1, stdout = "", stderr = e.message ?: "Unknown error")
        } finally {
            execChannel?.disconnect()
            session?.disconnect()
        }
    }

    suspend fun getDeviceStatus(
        host: String,
        port: Int,
        user: String,
        pass: String
    ): DeviceStatus = withContext(Dispatchers.IO) {
        val firmware = runCommand(host, port, user, pass, "cat /etc/prettyversion.txt 2>/dev/null || cat /etc/version.txt 2>/dev/null")
        val serial = runCommand(host, port, user, pass, "cat /proc/usid 2>/dev/null || cat /var/local/java/prefs/com.amazon.ebook.framework/prefs 2>/dev/null | grep -i serial || echo ''")
        val disk = runCommand(host, port, user, pass, "df -h /mnt/us 2>/dev/null | tail -1")
        val uptimeCmd = runCommand(host, port, user, pass, "cat /proc/uptime 2>/dev/null | awk '{print int(\$1)}'")
        val battery = runCommand(host, port, user, pass, "gasgauge-info -s 2>/dev/null || cat /sys/class/power_supply/bq27510/capacity 2>/dev/null || echo ''")
        val whispernet = runCommand(host, port, user, pass, "test -f /etc/upstart/whispernet.conf && echo 'active' || echo 'blocked'")
        val ota = runCommand(host, port, user, pass, "test -d /etc/uks && echo 'blocked' || echo 'active'")
        val koreader = runCommand(host, port, user, pass, "test -d /mnt/us/koreader && echo 'yes' || echo ''")

        fun parseSpace(dfLine: String): String? {
            if (dfLine.isBlank()) return null
            val parts = dfLine.split(Regex("\\s+"))
            return if (parts.size >= 4) "${parts[3]}/${parts[1]}" else null
        }

        DeviceStatus(
            firmwareVersion = firmware.stdout.ifBlank { null },
            serialNumber = serial.stdout.ifBlank { null },
            freeSpace = parseSpace(disk.stdout),
            uptime = uptimeCmd.stdout.toLongOrNull()?.let { formatUptime(it) },
            batteryLevel = battery.stdout.trim().ifBlank { null }?.let {
                it.replace("%", "").toIntOrNull()?.let { l -> "$l%" } ?: it
            },
            whispernetBlocked = whispernet.stdout.contains("blocked"),
            otaBlocked = ota.stdout.contains("blocked"),
            koreaderInstalled = koreader.stdout.contains("yes")
        )
    }

    suspend fun triggerRescan(
        host: String, port: Int, user: String, pass: String
    ): CommandResult = runCommand(
        host, port, user, pass,
        "lipc-set-prop com.lab126.amznAssetsMgrService reScan 1"
    )

    suspend fun rebootDevice(
        host: String, port: Int, user: String, pass: String
    ): CommandResult = runCommand(
        host, port, user, pass, "reboot"
    )

    suspend fun toggleWhispernetBlock(
        host: String, port: Int, user: String, pass: String
    ): CommandResult {
        val current = runCommand(host, port, user, pass, "test -f /etc/upstart/whispernet.conf && echo 'on' || echo 'off'")
        return if (current.stdout.contains("on")) {
            runCommand(host, port, user, pass, "mv /etc/upstart/whispernet.conf /etc/upstart/whispernet.conf.disabled")
        } else {
            runCommand(host, port, user, pass, "mv /etc/upstart/whispernet.conf.disabled /etc/upstart/whispernet.conf")
        }
    }

    suspend fun toggleOtaBlock(
        host: String, port: Int, user: String, pass: String
    ): CommandResult {
        val current = runCommand(host, port, user, pass, "test -d /etc/uks && echo 'blocked' || echo 'active'")
        return if (current.stdout.contains("blocked")) {
            runCommand(host, port, user, pass, "mv /etc/uks.disabled /etc/uks 2>/dev/null; echo 'restored'")
        } else {
            runCommand(host, port, user, pass, "mv /etc/uks /etc/uks.disabled 2>/dev/null; echo 'blocked'")
        }
    }

    // ── Repair recipes ──

    data class RepairRecipe(
        val id: String,
        val name: String,
        val description: String,
        val command: String,
        val dangerous: Boolean = false
    )

    val repairRecipes = listOf(
        RepairRecipe(
            id = "fix_stuck_1pct",
            name = "Fix 'Stuck at 1%' downloads",
            description = "Clears corrupted download cache and restarts the download manager.",
            command = "rm -rf /mnt/us/.active-content-data /var/local/cc.db 2>/dev/null; " +
                    "restart framework 2>/dev/null || lipc-set-prop com.lab126.appmgrd start app://com.lab126.booklet.downloader 2>/dev/null"
        ),
        RepairRecipe(
            id = "fix_corrupt_thumbnails",
            name = "Rebuild Thumbnail Cache",
            description = "Clears corrupt thumbnail cache so Kindle regenerates cover art.",
            command = "rm -rf /mnt/us/system/thumbnails/* 2>/dev/null; " +
                    "lipc-set-prop com.lab126.amznAssetsMgrService reScan 1"
        ),
        RepairRecipe(
            id = "restart_framework",
            name = "Restart Kindle Framework",
            description = "Restarts the Kindle UI / Java framework. Screen will flash briefly.",
            command = "restart framework",
            dangerous = true
        ),
        RepairRecipe(
            id = "fix_whispernet_sync",
            name = "Fix WhisperSync/Sync Errors",
            description = "Clears sync cache and restarts communication services.",
            command = "rm -rf /var/local/keystore /var/local/cc.db 2>/dev/null; " +
                    "restart todo 2>/dev/null; " +
                    "lipc-set-prop com.lab126.whispernet enable 1 2>/dev/null"
        ),
        RepairRecipe(
            id = "fix_jailbreak",
            name = "Reinstall Hotfix",
            description = "Re-applies the jailbreak hotfix bridge. Safe to run repeatedly.",
            command = "test -f /mnt/us/MRJ-thf.bin && " +
                    "lipc-set-prop com.lab126.ota startUpdate 1 2>/dev/null || " +
                    "echo 'Hotfix bin not found in /mnt/us'"
        )
    )

    suspend fun runRepair(
        recipe: RepairRecipe,
        host: String, port: Int, user: String, pass: String
    ): CommandResult = runCommand(host, port, user, pass, recipe.command)
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val mins = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

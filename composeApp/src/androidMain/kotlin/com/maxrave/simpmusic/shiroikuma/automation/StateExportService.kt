package com.maxrave.simpmusic.shiroikuma.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.documentfile.provider.DocumentFile
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.shiroikuma.SkBackup
import com.maxrave.simpmusic.shiroikuma.SkBackupCat
import com.maxrave.simpmusic.shiroikuma.SkBackupDir
import com.maxrave.simpmusic.shiroikuma.skHumanSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where the 保存復元 export actually runs.
 *
 * **Not in the receiver.** `goAsync()` does not extend the broadcast window — a manifest receiver
 * must reach `finish()` within ~10 s in the foreground and ~60 s in the background, and overrunning
 * it raises an ANR against *this* app and kills the process mid-export: nothing replies, the archive
 * is left half-written, and the caller waits forever on a dead process. Our `downloads` category can
 * be gigabytes, so the receiver does nothing but gate the request and start this service.
 *
 * A partial wakelock is held around the write because EMUI otherwise dozes the CPU with the screen
 * off and the export stops part-way with no crash, no ANR and no log.
 */
class StateExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Must happen within 5 s of the service starting or the system kills us for it.
        startForeground(NOTIF_ID, notification())
        val i = intent ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        val replyAction = i.getStringExtra("reply_action")
        val replyPackage = i.getStringExtra("reply_package")
        val replyId = i.getStringExtra("reply_id")
        val progressAction = i.getStringExtra("progress_action")
        val pathOverride = i.getStringExtra("path")
        val items = i.getStringExtra("items")

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            if (!replied.compareAndSet(false, true)) return
            if (replyAction.isNullOrBlank() || replyPackage.isNullOrBlank()) return
            sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra("reply_id", replyId)
                    putExtra("result", result)
                },
            )
        }

        if (!ExportControl.begin(replyId)) {
            reply("ERROR:export already running")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            val wakeLock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ongakuots:export")
                    .apply { runCatching { acquire(30 * 60 * 1000L) } }
            try {
                val cats = resolveCategories(items)
                val name = SkBackup.timestampedName()
                var lastSent = 0L

                fun progress(p: SkBackup.Progress) {
                    if (progressAction.isNullOrBlank() || replyPackage.isNullOrBlank()) return
                    val now = System.currentTimeMillis()
                    // At most one every 500 ms, and always the first — the caller also treats every
                    // one as a heartbeat, so a long single step still has to tick.
                    if (now - lastSent < 500L && p.index != 1) return
                    lastSent = now
                    sendBroadcast(
                        Intent(progressAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra("reply_id", replyId)
                            putExtra("app", APP_LABEL)
                            putExtra("item", p.cat.id)
                            putExtra("text", "区分 ${p.index}/${p.total} — ${p.cat.label}")
                            putExtra("current", p.index.toLong())
                            putExtra("total", p.total.toLong())
                            putExtra("unit", "区分")
                            putExtra("bytes", p.bytes)
                        },
                    )
                }

                val written = writeArchive(pathOverride, name, cats, ::progress)
                reply("OK:${written.path}|${written.bytes}|${skHumanSize(written.bytes)}|${written.count} categories")
            } catch (e: SkBackup.Cancelled) {
                reply("ERROR:cancelled")
            } catch (e: Exception) {
                Logger.w(TAG, "Automation export failed: ${e.message}")
                reply("ERROR:${e.message ?: e.javaClass.simpleName}")
            } finally {
                ExportControl.end()
                runCatching { if (wakeLock.isHeld) wakeLock.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private class Written(
        val path: String,
        val bytes: Long,
        val count: Int,
    )

    /**
     * Directory precedence, per the contract: the `path` extra → our configured SAF folder →
     * `ERROR:no-directory`.
     *
     * Writing to an arbitrary absolute path needs All-Files-Access, which this app does not declare
     * — a music player has no business holding it. So the grant is **checked rather than discovered
     * by failing**: with it we honour `path`; without it we fall back to the configured folder, and
     * only when there is none do we answer the exact string the caller keys its
     * 「全ファイルアクセスを許可」 repair button on.
     */
    private suspend fun writeArchive(
        pathOverride: String?,
        name: String,
        cats: List<SkBackupCat>,
        onProgress: (SkBackup.Progress) -> Unit,
    ): Written {
        val canUseAbsolutePath =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

        if (!pathOverride.isNullOrBlank() && canUseAbsolutePath) {
            val dir = File(pathOverride).apply { mkdirs() }
            if (!dir.isDirectory) error("could not create $pathOverride")
            val part = File(dir, "$name.part")
            val finalFile = File(dir, name)
            runCatching { part.delete() }
            try {
                val count = FileOutputStream(part).use { out -> SkBackup.export(cats, out, ExportControl::isCancelled, onProgress) }
                if (!part.renameTo(finalFile)) error("could not finish writing $name")
                return Written(finalFile.absolutePath, finalFile.length(), count)
            } catch (e: Exception) {
                runCatching { part.delete() }
                throw e
            }
        }

        val tree = SkBackupDir.uri()
        if (tree == null) {
            if (!pathOverride.isNullOrBlank()) error("no-storage-access")
            error("no-directory")
        }
        val dir = DocumentFile.fromTreeUri(this, tree) ?: error("no-directory")
        dir.findFile("$name.part")?.delete()
        val part = dir.createFile("application/zip", "$name.part") ?: error("could not create the file")
        try {
            val count =
                contentResolver.openOutputStream(part.uri).use { out: OutputStream? ->
                    requireNotNull(out) { "could not write to the backup folder" }
                    SkBackup.export(cats, out, ExportControl::isCancelled, onProgress)
                }
            if (!part.renameTo(name)) error("could not finish writing $name")
            val written = dir.findFile(name)
            return Written(
                path = written?.uri?.toString() ?: name,
                bytes = written?.length() ?: 0L,
                count = count,
            )
        } catch (e: Exception) {
            runCatching { part.delete() }
            throw e
        }
    }

    /** `items` absent or empty means our **default set**, not everything — §`LIST_CATEGORIES`. */
    private fun resolveCategories(items: String?): List<SkBackupCat> {
        if (items.isNullOrBlank()) return SkBackupCat.entries.filter { it.defaultSelected }
        val ids = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val unknown = ids.filter { SkBackupCat.byId(it) == null }
        if (unknown.isNotEmpty()) error("unknown category in items: ${unknown.joinToString(",")}")
        return SkBackupCat.entries.filter { it.id in ids }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Backup export", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while 白い熊 音楽乙 writes a backup for 白い熊 自由作業盤."
                },
            )
        }
        return Notification
            .Builder(this, CHANNEL)
            .setContentTitle(APP_LABEL)
            .setContentText("Writing a backup…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "SkAutomation"
        private const val CHANNEL = "ongakuots_export"
        private const val NOTIF_ID = 0x0873
        private const val APP_LABEL = "白い熊 音楽乙"

        fun start(
            context: Context,
            intent: Intent,
        ) {
            val svc =
                Intent(context, StateExportService::class.java).apply {
                    putExtra("path", intent.getStringExtra("path"))
                    putExtra("items", intent.getStringExtra("items"))
                    putExtra("progress_action", intent.getStringExtra("progress_action"))
                    putExtra("reply_action", intent.getStringExtra("reply_action"))
                    putExtra("reply_package", intent.getStringExtra("reply_package"))
                    putExtra("reply_id", intent.getStringExtra("reply_id"))
                }
            androidx.core.content.ContextCompat.startForegroundService(context, svc)
        }
    }
}

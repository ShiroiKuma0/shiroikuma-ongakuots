package com.maxrave.simpmusic.shiroikuma.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.shiroikuma.OngakuUiState
import com.maxrave.simpmusic.shiroikuma.SkBackup
import com.maxrave.simpmusic.shiroikuma.SkBackupCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data-door export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day someone restores it.
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to the
 * binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it on every exit — leaking one would hold the caller's file open indefinitely, and a caller
 * cannot checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // FIRST, unconditionally, before any check that could return. The system started us with
        // startForegroundService, so EVERY path out of this method — including the ones that reject
        // the request — must have gone foreground, or Android raises
        // ForegroundServiceDidNotStartInTimeException. A caller retrying with a stale job id would
        // otherwise crash the very app it is trying to back up.
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) ?: false
        startForeground(NOTIF_ID, notification(importing))

        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        // Taking it out of the map makes this method the descriptor's owner, and from here every
        // exit has to close it. `handedOff` is that bookkeeping: it goes true only once the coroutine
        // below owns the descriptor through `fd.use`, so the busy guard refusing, an exception, or
        // anything else returning early all close it on the way out. One flag rather than a guard per
        // failure — the window is what leaks, not any particular way of leaving it. The descriptor
        // belongs to the CALLER: leaking one holds 応用管理's backup file open for the life of this
        // process, and a file that is still open cannot be checksummed or encrypted.
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)
        var handedOff = false
        try {
            val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
            val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
            val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)
            val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)

            val replied = AtomicBoolean(false)
            // A `val` lambda rather than a local `fun`: AGP lint has been seen to crash analysing a
            // capturing local function beside a capturing anonymous object in the same file, which is
            // also why the counting stream below is a named class. Behaviour is identical.
            val reply: (String) -> Unit = { result ->
                // Exactly one terminal answer per job, whatever path got here — a synchronous failure
                // and an asynchronous success must never both fire. The same guard the broadcast
                // contract has carried since the first sister app.
                if (replied.compareAndSet(false, true)) {
                    AutomationJobs.finish(jobId)
                    if (!replyAction.isNullOrBlank() && !replyPackage.isNullOrBlank()) {
                        sendBroadcast(
                            Intent(replyAction).apply {
                                setPackage(replyPackage)
                                // Without this a caller that has been backgrounded never hears the
                                // answer, and on a clean phone it may not have been launched at all.
                                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                // The job id is the correlation id and goes out under BOTH names:
                                // the door's callers key on `job_id`, while every relay the family
                                // already had — 自由作業盤's panel included — was written against
                                // §1's `reply_id`. One name only means a reply nobody can match.
                                putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                                putExtra("reply_id", jobId)
                                putExtra(AutomationProvider.KEY_RESULT, result)
                            },
                        )
                    }
                }
            }

            // The data door shares the app's ONE backup guard with the broadcast export. Not for
            // tidiness: SkBackup.import CLOSES the Room database and replaces the file underneath it,
            // so an import running beside an export would hand the export a database that vanished
            // mid-read. Two archives at once is also simply two answers to a question that has one.
            if (!ExportControl.begin(jobId)) {
                reply("ERROR:another export or import is already running")
                return stop(startId)
            }

            scope.launch {
                // EMUI force-releases a foreground service's wakelock seconds after it starts and
                // then starves the process, so a long export simply stops part-way with no crash, no
                // ANR and no log. The lock is what keeps the CPU alive with the screen off.
                val wakeLock =
                    (getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ongakuots:automation-data")
                        .apply { runCatching { acquire(30 * 60 * 1000L) } }
                val progress = ProgressSender(progressAction, replyPackage, jobId, importing)
                val heartbeat = launch { progress.beat(this) }
                try {
                    fd.use { open ->
                        if (importing) runImport(open, reply) else runExport(jobId, open, items, progress, reply)
                    }
                } catch (e: SkBackup.Cancelled) {
                    reply("ERROR:cancelled")
                } catch (t: Throwable) {
                    Logger.w(TAG, "Automation data job failed: ${t.message}")
                    reply("ERROR:${t.message ?: t.javaClass.simpleName}")
                } finally {
                    heartbeat.cancel()
                    ExportControl.end()
                    runCatching { if (wakeLock.isHeld) wakeLock.release() }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
            handedOff = true
            return START_NOT_STICKY
        } finally {
            if (!handedOff) {
                runCatching { fd.close() }
                AutomationJobs.finish(jobId)
            }
        }
    }

    /**
     * Write the archive straight into the caller's descriptor.
     *
     * The bytes are counted **on the way out** rather than stat'ed afterwards: the caller owns the
     * file and this app may not be able to see it at all — it can be an anonymous pipe, or a
     * descriptor into a directory this app cannot list.
     */
    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: ProgressSender,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items) ?: run { reply("ERROR:unknown category in items: $items"); return }
        val counting =
            ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
                CountingSink(out).also { sink ->
                    SkBackup.export(
                        cats = cats,
                        out = sink,
                        // Either cancel path unwinds it: the door's own job id, and the broadcast
                        // CANCEL_EXPORT that 白い熊 fires from the panel without knowing which door
                        // started the run. Polled at category and file boundaries, never mid-write.
                        cancelled = { AutomationJobs.isCancelled(jobId) || ExportControl.isCancelled() },
                        onProgress = progress::send,
                    )
                }
            }
        if (AutomationJobs.isCancelled(jobId) || ExportControl.isCancelled()) reply("ERROR:cancelled")
        else reply("OK:${counting.count}|${cats.size} categories")
    }

    /**
     * Spool the archive to disk, prove it is whole, and only then let it touch anything.
     *
     * **This is where this app departs from the family reference, and it has to.** The reference
     * reads the archive into a `ByteArray` — right for an app whose backup is a settings dump, fatal
     * here, where the `downloads` category is the ExoPlayer store and an archive can run to
     * gigabytes. And [SkBackup.import] writes each entry as it streams past, so a truncated archive
     * does not fail cleanly: it half-restores, replacing the database and leaving the download index
     * describing chunks that were never written. A half-restored app is worse than one that refused.
     *
     * So the descriptor is spooled into `cacheDir` and checked for the end-of-central-directory
     * signature `50 4b 05 06` — the same test the contract names as how a complete ZIP is told from
     * a truncated one — before a single entry is unpacked. The spool is deleted in a `finally`
     * whatever happens.
     *
     * **Cancellation stops at the spool, deliberately.** Once entries are being written there is no
     * safe boundary to unwind on: the database has already been closed and replaced. Aborting there
     * would leave exactly the half-restored app this method exists to prevent.
     */
    private suspend fun runImport(
        fd: ParcelFileDescriptor,
        reply: (String) -> Unit,
    ) {
        val spool = File.createTempFile("automation-import", ".zip", cacheDir)
        try {
            var read = 0L
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                spool.outputStream().use { sink -> read = input.copyTo(sink) }
            }
            if (read == 0L) {
                reply("ERROR:empty archive")
                return
            }
            if (!endsWithCentralDirectory(spool)) {
                reply("ERROR:archive is truncated")
                return
            }
            val result = spool.inputStream().use { SkBackup.import(it) }
            if (result.categories.isEmpty()) {
                reply("ERROR:archive carries no categories this app understands")
                return
            }
            flushPendingWrites(result)
            // 応用管理 force-stops us straight after this, and must: a running process writes its
            // cached state back out at orderly shutdown and would silently undo the import that just
            // happened. That guarantee lives on its side so that forty-two apps do not each have to
            // remember it — which is also why nothing here restarts the app the way the in-app
            // Import panel does.
            reply("OK:${result.categories.size} restored")
        } finally {
            runCatching { spool.delete() }
        }
    }

    /**
     * Make the import durable before saying it worked.
     *
     * **The force-stop that follows a successful import is a SIGKILL, and a write still in flight
     * does not survive one.** Most of what [SkBackup.import] does is synchronous file copying and is
     * already on disk by the time it returns. The `ui` category is not: it goes through
     * `OngakuUiState.importJson`, whose `update` sets the in-memory value and lets the DataStore
     * write follow on its own scope — deliberately, because that is what makes the theme page
     * repaint on the next frame instead of after a round-trip. Right for a slider, wrong for a
     * restore. Without this the theme would come back silently unrestored, with every other category
     * correct and nothing anywhere reporting a failure.
     */
    private suspend fun flushPendingWrites(result: SkBackup.ImportResult) {
        if (SkBackupCat.UI !in result.categories) return
        runCatching { getKoin().get<OngakuUiState>().persistNow() }
            .onFailure { Logger.w(TAG, "Could not flush the restored theme: ${it.message}") }
    }

    /**
     * True when the file ends in a real end-of-central-directory record.
     *
     * Searched backwards over the last 64 KB rather than only the final 22 bytes, because the record
     * is followed by an optional comment of up to 65 535 bytes. A ZIP that lacks it entirely was cut
     * off before it was closed.
     */
    private fun endsWithCentralDirectory(file: File): Boolean =
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len < 22) return false
                val window = minOf(len, 65_557L).toInt()
                val buf = ByteArray(window)
                raf.seek(len - window)
                raf.readFully(buf)
                for (i in window - 4 downTo 0) {
                    if (buf[i] == 0x50.toByte() &&
                        buf[i + 1] == 0x4b.toByte() &&
                        buf[i + 2] == 0x05.toByte() &&
                        buf[i + 3] == 0x06.toByte()
                    ) {
                        return true
                    }
                }
                false
            }
        }.getOrDefault(false)

    /** `items` absent or empty means our **default set**, not everything — §`LIST_CATEGORIES`. */
    private fun resolve(items: String?): List<SkBackupCat>? {
        if (items.isNullOrBlank()) return SkBackupCat.entries.filter { it.defaultSelected }
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { SkBackupCat.byId(it) }
        return if (found.size == wanted.size) SkBackupCat.entries.filter { it in found } else null
    }

    /** Counts what actually reaches the caller's descriptor. A named class, not an anonymous one. */
    private class CountingSink(
        private val sink: OutputStream,
    ) : OutputStream() {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            sink.write(b)
            count++
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            sink.write(b, off, len)
            count += len
        }

        override fun flush() = sink.flush()
    }

    /**
     * Progress, and the heartbeat that has to exist beside it.
     *
     * **A throttle is not a heartbeat — they are opposite problems.** [SkBackup.export] calls back
     * once per CATEGORY, not per file, so `downloads` (the whole ExoPlayer store) ticks once and then
     * says nothing for however long a few gigabytes take to zip. §3 presumes an app silent for two
     * minutes is dead and fails its slot, so a correctly-implemented 500 ms throttle still starves
     * the caller. The last true line is therefore re-sent on a timer: repeating a real number is
     * honest, where inventing a moving one could not be told apart from progress.
     */
    private inner class ProgressSender(
        private val action: String?,
        private val replyPackage: String?,
        private val jobId: String,
        importing: Boolean,
    ) {
        @Volatile
        private var last: SkBackup.Progress? = null

        @Volatile
        private var lastSentAt = 0L
        private val verb = if (importing) "復元" else "書き出し"

        fun send(p: SkBackup.Progress) {
            last = p
            val now = System.currentTimeMillis()
            // At most one every 500 ms, and always the first.
            if (now - lastSentAt < 500L && p.index != 1) return
            emit(p, now)
        }

        suspend fun beat(scope: CoroutineScope) {
            while (scope.isActive) {
                delay(HEARTBEAT_MS)
                val p = last
                val now = System.currentTimeMillis()
                if (now - lastSentAt >= HEARTBEAT_MS) {
                    if (p != null) emit(p, now) else emitPlain("$verb 中…", now)
                }
            }
        }

        private fun emit(
            p: SkBackup.Progress,
            now: Long,
        ) {
            broadcast(now) {
                putExtra("item", p.cat.id)
                putExtra("text", "区分 ${p.index}/${p.total} — ${p.cat.label}")
                putExtra("current", p.index.toLong())
                putExtra("total", p.total.toLong())
                putExtra("unit", "区分")
                putExtra("bytes", p.bytes)
            }
        }

        private fun emitPlain(
            text: String,
            now: Long,
        ) {
            broadcast(now) { putExtra("text", text) }
        }

        private fun broadcast(
            now: Long,
            fill: Intent.() -> Unit,
        ) {
            if (action.isNullOrBlank() || replyPackage.isNullOrBlank()) return
            lastSentAt = now
            sendBroadcast(
                Intent(action).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra("reply_id", jobId)
                    putExtra("app", APP_LABEL)
                    fill()
                },
            )
        }
    }

    private fun notification(importing: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Automation data", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while 白い熊 応用管理 backs this app's data up or puts it back."
                },
            )
        }
        return Notification
            .Builder(this, CHANNEL)
            .setContentTitle(APP_LABEL)
            .setContentText(if (importing) "Restoring data…" else "Handing over a backup…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SkAutomation"
        private const val CHANNEL = "ongakuots_automation_data"
        private const val NOTIF_ID = 0x0874
        private const val APP_LABEL = "白い熊 音楽乙"
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"
        private const val HEARTBEAT_MS = 10_000L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and the
         * copy's lifetime stops being ours to reason about. Handing it through a map keyed by the job
         * id keeps exactly one open descriptor with exactly one owner — this service, which closes it
         * on every exit.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /**
         * Returns null when the service was started, or the reason it could not be.
         *
         * **A provider `call()` is a BACKGROUND start.** From API 31 the system may refuse one
         * outright with `ForegroundServiceStartNotAllowedException` unless the app is exempt from
         * battery optimisation — and 白い熊 freezes apps aggressively, so the app being asked is
         * often precisely the one least likely to be exempt. If that throws, the descriptor is
         * already in [HANDOVER] and no service will ever arrive to drain it, so it is closed and
         * removed here rather than held open for the life of the process.
         */
        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ): String? {
            HANDOVER[jobId] = fd
            return try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                        putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION),
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE),
                        )
                        putExtra(
                            AutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION),
                        )
                    },
                )
                null
            } catch (t: Throwable) {
                HANDOVER.remove(jobId)?.let { pfd -> runCatching { pfd.close() } }
                Logger.w(TAG, "Could not start the automation data service: ${t.message}")
                t.message ?: t.javaClass.simpleName
            }
        }
    }
}

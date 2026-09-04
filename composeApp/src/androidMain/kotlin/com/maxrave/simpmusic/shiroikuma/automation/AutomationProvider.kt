package com.maxrave.simpmusic.shiroikuma.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.maxrave.simpmusic.shiroikuma.SkBackup
import com.maxrave.simpmusic.shiroikuma.SkBackupCat

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of contract v2.
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was a shared secret, which cannot
 * survive the wipe this feature exists to recover from. A provider gets the caller's identity from
 * the framework for free — see [AutomationCallers] for what is actually checked, and why a
 * `shiroikuma.*` prefix would have been strictly weaker than the token it replaced.
 *
 * **And a list needs a synchronous answer.** 白い熊 応用管理 draws a row per installed app before
 * any export exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. [call] validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and
 * die silently if this process were killed. The bytes go through a descriptor the caller opened, and
 * the terminal answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## One hazard particular to this app
 *
 * **A provider's `onCreate` runs BEFORE `Application.onCreate`** — that is the whole reason androidx
 * Startup is shaped the way it is — and this app resolves its backup engine, its database and its
 * theme state out of **Koin**, which `SimpMusicApplication.onCreate` starts. A `call()` arriving in
 * the same breath as process start can therefore be dispatched on a binder thread while the main
 * thread is still inside `startKoin`. So **nothing in this class touches Koin**: [describe] reads
 * only the manifest and a common enum, and everything else is handed to [AutomationDataService],
 * whose `onStartCommand` is queued behind `Application.onCreate` on the main thread and is
 * therefore always late enough. This matters precisely in the case the contract exists for — a
 * freshly installed app that has never been launched, where a provider call is what starts the
 * process.
 */
class AutomationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` carrying our stack trace, which tells 白い熊 nothing and tells a misbehaving
     * caller rather more than it should.
     */
    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches. A token is ignored unless this app asks for one — see
        // AutomationAuth.refuse, which is the single place both questions are answered.
        AutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }
            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw a
     * row **before** an export exists, and at restore must judge compatibility before streaming tens
     * of megabytes into an app that would reject them — which it cannot do if the header is buried
     * inside an encrypted archive.
     *
     * `contains` lists the **default** set, not the whole catalogue, so it says what a plain backup
     * of this app would actually hold. `downloads` is absent for exactly that reason: it is large,
     * re-downloadable, and opt-in.
     *
     * `requires_launch_first` is false and means it. Everything an import touches — the DataStore
     * file, the Room database, the ExoPlayer download store — is created on demand by the Koin
     * graph that `Application.onCreate` builds whether or not an Activity was ever shown.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val cats = SkBackupCat.entries.filter { it.defaultSelected }
        val json =
            """
            {"app_id":"${ctx.packageName}",
             "version_code":${@Suppress("DEPRECATION") pkg.versionCode},
             "version_name":"${pkg.versionName}",
             "format":$FORMAT,
             "min_format_readable":$MIN_FORMAT_READABLE,
             "requires_launch_first":false,
             "contains":[${cats.joinToString(",") { "\"${it.label}\"" }}]}
            """.trimIndent().replace("\n", "")
        return "OK:$json"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when `call()` returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service to
     * remember.
     */
    private fun start(
        ctx: Context,
        extras: Bundle?,
        importing: Boolean,
    ): Bundle {
        @Suppress("DEPRECATION")
        val fd =
            extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
                ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        // The start can be REFUSED. A provider call() is a background start, and from API 31 the
        // system may decline it unless this app is exempt from battery optimisation — which, on a
        // phone where apps are frozen aggressively, is the normal state rather than the exception.
        // Answering OK anyway would hand the caller a job id for work that will never run, and
        // leave its descriptor held open by a service that never arrived. So the failure is
        // reported, the job is dropped, and the descriptor is closed on the way (inside start).
        AutomationDataService.start(ctx, jobId, dup, importing, extras)?.let { why ->
            AutomationJobs.finish(jobId)
            return fail("ERROR:could not start the data service: $why")
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(
        u: Uri,
        p: Array<String>?,
        s: String?,
        a: Array<String>?,
        o: String?,
    ): Cursor? = throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("automation is call() only")

    override fun delete(
        uri: Uri,
        s: String?,
        a: Array<String>?,
    ): Int = throw UnsupportedOperationException("automation is call() only")

    override fun update(
        u: Uri,
        v: ContentValues?,
        s: String?,
        a: Array<String>?,
    ): Int = throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * This app's archive format — tied to [SkBackup.VERSION] rather than restated, so the number
         * in the manifest `<meta-data>`, the number in the header and the number written into
         * `manifest.json` inside the ZIP cannot drift apart.
         */
        const val FORMAT = SkBackup.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * restore be refused at discovery time rather than halfway through. `SkBackup.import` ignores
         * entries it does not recognise, so every archive this app has ever written is still
         * readable — hence 1.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}

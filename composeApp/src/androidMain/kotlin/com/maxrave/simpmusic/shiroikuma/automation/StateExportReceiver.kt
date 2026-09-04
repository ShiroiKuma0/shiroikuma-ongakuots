package com.maxrave.simpmusic.shiroikuma.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maxrave.simpmusic.shiroikuma.SkBackupCat

/**
 * The 保存復元 wire contract: the three actions 白い熊 自由作業盤 fires at this app.
 *
 * The receiver itself does **no work**. It checks the switch and the token, and either answers
 * instantly (LIST_CATEGORIES), flips a flag (CANCEL_EXPORT), or hands off to
 * [StateExportService] and returns (EXPORT_STATE) — because a manifest receiver that outstays the
 * broadcast window is ANR'd and killed mid-export, which loses the archive *and* the reply.
 *
 * No `android:permission` on the receiver, and under contract v2 no token either by default: this
 * is the **unauthenticated** half of the surface, deliberately, because it only ever writes where it
 * was told to and reports what it did. Everything that moves data through a caller-supplied
 * descriptor lives behind [AutomationProvider], which knows who is calling.
 */
class StateExportReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        val pkg = app.packageName
        val token = intent.getStringExtra("token")

        when (intent.action) {
            "$pkg.action.EXPORT_STATE" -> {
                val replyAction = intent.getStringExtra("reply_action")
                val replyPackage = intent.getStringExtra("reply_package")
                val replyId = intent.getStringExtra("reply_id")

                // One gate, one place — AutomationAuth.refuse. It still answers "automation
                // disabled" and "bad token" as distinct lines because they debug differently, and a
                // silent drop here would leave the caller waiting out its whole timeout. A token
                // sent when this app does not ask for one is ignored inside refuse, never refused.
                AutomationAuth.refuse(app, token)?.let {
                    reply(app, replyAction, replyPackage, replyId, it)
                    return
                }
                if (replyAction.isNullOrBlank() || replyPackage.isNullOrBlank() || replyId.isNullOrBlank()) return

                StateExportService.start(app, intent)
            }

            "$pkg.action.LIST_CATEGORIES" -> {
                val replyAction = intent.getStringExtra("reply_action")
                val replyPackage = intent.getStringExtra("reply_package")
                val replyId = intent.getStringExtra("reply_id")
                AutomationAuth.refuse(app, token)?.let {
                    reply(app, replyAction, replyPackage, replyId, it)
                    return
                }
                // `id<TAB>label`, with the third field (parent) empty and the fourth carrying the
                // default. Ours is a flat list, so only `downloads` needs the fourth — large,
                // re-downloadable, and therefore opt-in rather than opt-out.
                val lines =
                    SkBackupCat.entries.joinToString("\n") { cat ->
                        if (cat.defaultSelected) "${cat.id}\t${cat.label}" else "${cat.id}\t${cat.label}\t\toff"
                    }
                reply(app, replyAction, replyPackage, replyId, "OK:$lines")
            }

            "$pkg.action.CANCEL_EXPORT" -> {
                // Fire-and-forget: no reply of its own — the one terminal reply belongs to the
                // export it stopped. Safe to send at any time; when nothing is running it is a
                // silent no-op, because the caller fires it without knowing how far we got.
                if (AutomationAuth.refuse(app, token) != null) return
                ExportControl.requestCancel(intent.getStringExtra("reply_id"))
            }
        }
    }

    /**
     * A fresh broadcast, never a binder. EMUI will not reliably carry a live `ResultReceiver` /
     * `PendingIntent` / `Messenger` into another app's manifest receiver and may drop the whole
     * broadcast, and it severs the ordered-broadcast result channel between third-party apps.
     * `FLAG_INCLUDE_STOPPED_PACKAGES` is what lets a backgrounded caller hear us at all.
     */
    private fun reply(
        context: Context,
        action: String?,
        pkg: String?,
        replyId: String?,
        result: String,
    ) {
        if (action.isNullOrBlank() || pkg.isNullOrBlank()) return
        context.sendBroadcast(
            Intent(action).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra("reply_id", replyId)
                putExtra("result", result)
            },
        )
    }
}

package com.maxrave.simpmusic.shiroikuma.automation

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate on the 保存復元 automation: a master switch that is **on**, and a token that is **off**.
 *
 * ## What changed in contract v2, and why it had to
 *
 * v1 shipped this app closed — `automation_enabled` defaulted to false, and a caller also had to
 * present a 48-character secret 白い熊 had pasted from this page into 自由作業盤's roster.
 *
 * That is the wrong shape for where the family is going. **A pasted secret cannot survive a wipe**,
 * and the case this whole contract now exists to serve is 白い熊 応用管理 restoring apps *and their
 * data* onto a clean phone, where nothing has been configured and nobody has pasted anything. A gate
 * that only works once the phone is already set up is no gate for setting the phone up.
 *
 * So the switch defaults **on**, the token became an extra a caller may be asked for rather than the
 * gate, and the thing that actually protects the data door is the caller's identity and signing
 * certificate — see [AutomationCallers].
 *
 * ## Where the values live
 *
 * All three sit in their **own** SharedPreferences file, which is deliberately not one of the things
 * [com.maxrave.simpmusic.shiroikuma.SkBackup] exports. A token that travelled inside a backup ZIP
 * would be handed to anyone the archive was ever shared with, and would come back on a restore to a
 * different device still granting access there.
 */
object AutomationAuth {
    private const val PREFS = "ongakuots_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Default **true**. It stays a switch rather than being removed because it is the only way to
     * close this app off, and a feature that can be turned on but never off is one 白い熊 cannot
     * retreat from.
     */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(
        context: Context,
        on: Boolean,
    ) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()

    /** Default **false** — 「Use authorization token?」 is an extra, not the gate. */
    fun requireToken(context: Context): Boolean = prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(
        context: Context,
        on: Boolean,
    ) = prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, on).apply()

    /**
     * **The whole gate, in one place.** Returns null to proceed, or the exact `ERROR:` line to
     * answer with.
     *
     * Written once rather than as two checks at each entry point, because that is how "disabled" and
     * "bad token" drift apart across forty-two apps — and because the idempotency rule below has to
     * hold at every door, not at the ones somebody remembered.
     *
     * **A token handed to an app that does not require one is IGNORED. It is never an error.**
     * Tokens live in task arguments and workspace variables that outlive the setting they were
     * pasted for, so a caller still sending one — because it was configured last year, or because
     * another app on the batch does want one — must be served. Refusing it would turn "白い熊 turned
     * a switch off" into "half the batch mysteriously fails", which is precisely the friction the
     * switch exists to remove.
     */
    fun refuse(
        context: Context,
        candidate: String?,
    ): String? =
        when {
            !enabled(context) -> "ERROR:automation disabled"
            requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
            else -> null
        }

    /**
     * 24 random bytes, hex. Generated lazily on first read rather than when the switch is turned on,
     * so the settings row always has something to show.
     */
    fun token(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        p.edit().putString(KEY_TOKEN, hex).apply()
        return hex
    }

    fun regenerate(context: Context): String {
        prefs(context).edit().remove(KEY_TOKEN).apply()
        return token(context)
    }

    /**
     * Constant-time, so a wrong token cannot be found one character at a time by timing it. Kept for
     * the case where the token *is* required — it is simply no longer consulted otherwise.
     */
    fun isTokenValid(
        context: Context,
        candidate: String?,
    ): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — enough to recognise which token a row is showing, not enough to use. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}

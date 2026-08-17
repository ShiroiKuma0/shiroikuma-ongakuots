package com.maxrave.simpmusic.shiroikuma.automation

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate on the 保存復元 automation: a master switch, default OFF, and a token 白い熊 copies into
 * 白い熊 自由作業盤's roster.
 *
 * Both live in their **own** SharedPreferences file, which is deliberately not one of the things the
 * backup exports. A token that travelled inside a backup ZIP would be handed to anyone the archive
 * was ever shared with, and would come back on a restore to a different device still granting access
 * there.
 */
object AutomationAuth {
    private const val PREFS = "ongakuots_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        on: Boolean,
    ) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()

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

    /** Constant-time, so a wrong token cannot be found one character at a time by timing it. */
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

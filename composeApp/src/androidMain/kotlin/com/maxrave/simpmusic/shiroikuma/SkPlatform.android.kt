package com.maxrave.simpmusic.shiroikuma

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.documentfile.provider.DocumentFile
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

private fun ctx(): Context = getKoin().get<Context>().applicationContext

/** Where imported font files live. Kept out of the cache, and carried by the backup's `fonts`. */
internal fun skFontsDir(): File = File(ctx().filesDir, "shiroikuma_fonts").apply { mkdirs() }

/**
 * The font store. The app's own bundled Poppins is always the first choice (id `""`); everything
 * else is a file adopted through the picker, listed by its own file name.
 *
 * Resolution is cached, because [SkFonts.family] is called once per row of the font picker and once
 * per text style in [com.maxrave.simpmusic.ui.theme.typo] — reading and parsing a font file on each
 * of those would be felt.
 */
actual object SkFonts {
    private val cache = HashMap<String, FontFamily?>()

    actual fun choices(): List<SkFontChoice> =
        buildList {
            add(SkFontChoice("", "App default (Poppins)"))
            skFontsDir()
                .listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name.lowercase() }
                ?.forEach { add(SkFontChoice(it.name, it.nameWithoutExtension)) }
        }

    actual fun label(id: String): String =
        if (id.isBlank()) "App default (Poppins)" else File(id).nameWithoutExtension

    actual fun family(id: String): FontFamily? {
        if (id.isBlank()) return null
        synchronized(cache) {
            if (cache.containsKey(id)) return cache[id]
            val file = File(skFontsDir(), id)
            val fam =
                if (!file.isFile) {
                    null
                } else {
                    runCatching {
                        FontFamily(Typeface(android.graphics.Typeface.createFromFile(file)))
                    }.onFailure { Logger.w("SkFonts", "Cannot load $id: ${it.message}") }.getOrNull()
                }
            cache[id] = fam
            return fam
        }
    }

    actual fun delete(id: String): Boolean {
        if (id.isBlank()) return false
        val ok = File(skFontsDir(), id).delete()
        invalidate()
        return ok
    }

    actual fun invalidate() {
        synchronized(cache) { cache.clear() }
    }
}

/**
 * The backup folder — a SAF tree the user grants once. Device-local by design: it is a grant on this
 * install, so it is never itself part of an export.
 */
actual object SkBackupDir {
    private const val PREFS = "ongakuots_backup"
    private const val KEY_DIR = "backup_dir"

    fun uri(): Uri? =
        ctx()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DIR, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun set(uri: Uri?) {
        ctx()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (uri == null) remove(KEY_DIR) else putString(KEY_DIR, uri.toString()) }
            .apply()
    }

    actual fun label(): String? {
        val u = uri() ?: return null
        val doc = runCatching { DocumentFile.fromTreeUri(ctx(), u) }.getOrNull()
        return doc?.name ?: u.lastPathSegment ?: u.toString()
    }

    actual fun isSet(): Boolean = uri() != null

    actual suspend fun latest(): SkLatestBackup? =
        withContext(Dispatchers.IO) {
            val u = uri() ?: return@withContext null
            val dir = runCatching { DocumentFile.fromTreeUri(ctx(), u) }.getOrNull() ?: return@withContext null
            dir
                .listFiles()
                .filter { it.isFile && SkBackup.isBackupFileName(it.name.orEmpty()) }
                .maxByOrNull { it.lastModified() }
                ?.let { SkLatestBackup(it.name!!, it.length()) }
        }
}

/**
 * "Import a font file…". The chosen file is copied into the app's own font store rather than being
 * referenced in place — a SAF grant on someone else's file does not survive a reboot, and a font the
 * app cannot open after a restart is worse than no font at all.
 */
@Composable
actual fun rememberSkFontImporter(onImported: (String) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onImported)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val context = ctx()
            val name =
                runCatching {
                    DocumentFile.fromSingleUri(context, uri)?.name
                }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
            val target = File(skFontsDir(), name)
            val ok =
                runCatching {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input)
                        target.outputStream().use { input.copyTo(it) }
                    }
                    true
                }.getOrElse {
                    Logger.w("SkFonts", "Font import failed: ${it.message}")
                    false
                }
            if (ok) {
                SkFonts.invalidate()
                callback(name)
            }
        }
    return {
        launcher.launch(
            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/font-sfnt", "*/*"),
        )
    }
}

/** Restart into a clean process — the only honest way to adopt a restored DataStore and database. */
internal fun skRestartApp() {
    val context = ctx()
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    context.startActivity(Intent.makeRestartActivityTask(intent?.component))
    Runtime.getRuntime().exit(0)
}

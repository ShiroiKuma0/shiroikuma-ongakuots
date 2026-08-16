package com.maxrave.simpmusic.shiroikuma

import android.content.Context
import com.maxrave.common.DB_NAME
import com.maxrave.common.DOWNLOAD_EXOPLAYER_FOLDER
import com.maxrave.common.EXOPLAYER_DB_NAME
import com.maxrave.common.SETTINGS_FILENAME
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The 白い熊 音楽乙 backup — one category ZIP holding everything the app lets you set.
 *
 * The family shape (the 保存復元 contract and seventeen sister apps): `manifest.json` describing the
 * archive, then one entry or entry tree per category. Import **merges** — a category absent from an
 * archive is skipped rather than cleared, so a settings-only backup never wipes the library.
 *
 * The entry names for the three upstream categories are deliberately the ones **upstream's own
 * backup already uses** (`<settings>.preferences_pb`, the database file, the ExoPlayer database and
 * `download/`). That costs nothing and buys interoperability in both directions: an archive written
 * here restores through SimpMusic's own Restore, and one written by stock SimpMusic restores here.
 *
 * The engine is headless on purpose — [export] takes an [OutputStream] and a progress callback and
 * knows nothing about Compose — so the Export/Import panel and (later) the automation receiver drive
 * one implementation rather than two.
 */
object SkBackup {
    const val FORMAT = "shiroikuma-ongakuots-backup"
    const val VERSION = 1

    /** The mandatory family naming convention: `<english-app-name>_<stamp>.zip`, nothing else. */
    const val EXPORT_PREFIX = "shiroikuma-ongakuots_"

    /** Thrown out of the write loop when a cancel lands; the partial file is removed by the caller. */
    class Cancelled : Exception("cancelled")

    /** Progress as the export walks its categories. [index] is a 1-based POSITION, per the contract. */
    class Progress(
        val cat: SkBackupCat,
        val index: Int,
        val total: Int,
    )

    private fun ctx(): Context = getKoin().get<Context>().applicationContext

    fun timestampedName(now: Date = Date()): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now) + ".zip"

    fun isBackupFileName(name: String): Boolean = name.startsWith(EXPORT_PREFIX) && name.endsWith(".zip")

    // ---- export ---------------------------------------------------------------------------------

    /**
     * Write [cats] into [out], returning how many categories were actually written. [cancelled] is
     * polled at every category and every file boundary, so a cancel unwinds at a safe point rather
     * than tearing a half-written entry.
     */
    suspend fun export(
        cats: List<SkBackupCat>,
        out: OutputStream,
        cancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
    ): Int =
        withContext(Dispatchers.IO) {
            val context = ctx()
            val uiState: OngakuUiState = getKoin().get()
            val chosen = cats.ifEmpty { SkBackupCat.entries.filter { it.defaultSelected } }
            var written = 0

            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(
                    buildString {
                        append("{\n")
                        append("  \"format\": \"$FORMAT\",\n")
                        append("  \"version\": $VERSION,\n")
                        append("  \"app\": \"shiroikuma.ongakuots\",\n")
                        append("  \"created_ts\": ${System.currentTimeMillis()},\n")
                        append("  \"categories\": [${chosen.joinToString(", ") { "\"${it.id}\"" }}]\n")
                        append("}\n")
                    }.toByteArray(),
                )
                zip.closeEntry()

                chosen.forEachIndexed { i, cat ->
                    if (cancelled()) throw Cancelled()
                    onProgress(Progress(cat, i + 1, chosen.size))
                    when (cat) {
                        SkBackupCat.UI -> {
                            zip.putNextEntry(ZipEntry("ui.json"))
                            zip.write(uiState.exportJson().toByteArray())
                            zip.closeEntry()
                        }

                        SkBackupCat.FONTS -> writeDir(zip, skFontsDir(), "fonts/", cancelled)

                        SkBackupCat.SETTINGS -> {
                            val f = File(context.filesDir, "datastore/$SETTINGS_FILENAME.preferences_pb")
                            if (f.isFile) writeFile(zip, f, "$SETTINGS_FILENAME.preferences_pb")
                        }

                        SkBackupCat.LIBRARY -> {
                            val repo: CommonRepository = getKoin().get()
                            repo.databaseDaoCheckpoint()
                            val db = File(repo.getDatabasePath())
                            if (db.isFile) writeFile(zip, db, DB_NAME)
                        }

                        SkBackupCat.DOWNLOADS -> {
                            val exo = context.getDatabasePath(EXOPLAYER_DB_NAME)
                            if (exo.isFile) writeFile(zip, exo, EXOPLAYER_DB_NAME)
                            writeTree(zip, File(context.filesDir, DOWNLOAD_EXOPLAYER_FOLDER), DOWNLOAD_EXOPLAYER_FOLDER, cancelled)
                        }
                    }
                    written++
                }
            }
            written
        }

    private fun writeFile(
        zip: ZipOutputStream,
        file: File,
        entry: String,
    ) {
        zip.putNextEntry(ZipEntry(entry))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeDir(
        zip: ZipOutputStream,
        dir: File,
        prefix: String,
        cancelled: () -> Boolean,
    ) {
        dir.listFiles()?.filter { it.isFile }?.forEach {
            if (cancelled()) throw Cancelled()
            writeFile(zip, it, prefix + it.name)
        }
    }

    private fun writeTree(
        zip: ZipOutputStream,
        dir: File,
        prefix: String,
        cancelled: () -> Boolean,
    ) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            if (cancelled()) throw Cancelled()
            if (f.isFile) writeFile(zip, f, "$prefix/${f.name}") else writeTree(zip, f, "$prefix/${f.name}", cancelled)
        }
    }

    // ---- import ---------------------------------------------------------------------------------

    class ImportResult(
        val categories: List<SkBackupCat>,
        /** True when something was restored that only a fresh process will pick up. */
        val needsRestart: Boolean,
    )

    /**
     * Read an archive and merge it in. Unknown entries are ignored rather than treated as an error —
     * an archive from a newer build must still restore the parts this one understands.
     */
    suspend fun import(input: InputStream): ImportResult =
        withContext(Dispatchers.IO) {
            val context = ctx()
            val uiState: OngakuUiState = getKoin().get()
            val found = LinkedHashSet<SkBackupCat>()
            var needsRestart = false
            var downloadsCleared = false

            ZipInputStream(input.buffered()).use { zip ->
                var e: ZipEntry? = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name == "manifest.json" || e.isDirectory -> Unit

                        name == "ui.json" -> {
                            uiState.importJson(zip.readBytes().toString(Charsets.UTF_8))
                            found += SkBackupCat.UI
                        }

                        name.startsWith("fonts/") -> {
                            val target = File(skFontsDir(), File(name).name)
                            target.outputStream().use { zip.copyTo(it) }
                            found += SkBackupCat.FONTS
                            SkFonts.invalidate()
                        }

                        name == "$SETTINGS_FILENAME.preferences_pb" -> {
                            val target = File(context.filesDir, "datastore/$SETTINGS_FILENAME.preferences_pb")
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            found += SkBackupCat.SETTINGS
                            needsRestart = true
                        }

                        name == DB_NAME -> {
                            val repo: CommonRepository = getKoin().get()
                            repo.databaseDaoCheckpoint()
                            repo.closeDatabase()
                            FileOutputStream(repo.getDatabasePath()).use { zip.copyTo(it) }
                            found += SkBackupCat.LIBRARY
                            needsRestart = true
                        }

                        name == EXOPLAYER_DB_NAME -> {
                            FileOutputStream(context.getDatabasePath(EXOPLAYER_DB_NAME)).use { zip.copyTo(it) }
                            found += SkBackupCat.DOWNLOADS
                            needsRestart = true
                        }

                        name.startsWith("$DOWNLOAD_EXOPLAYER_FOLDER/") -> {
                            val root = File(context.filesDir, DOWNLOAD_EXOPLAYER_FOLDER)
                            // The download store is an index plus its chunks: merging a restored set
                            // into a live one leaves the index describing files that are not there.
                            if (!downloadsCleared) {
                                root.deleteRecursively()
                                downloadsCleared = true
                            }
                            val target = File(context.filesDir, name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            found += SkBackupCat.DOWNLOADS
                            needsRestart = true
                        }

                        else -> Logger.d("SkBackup", "Unhandled entry: $name")
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
            ImportResult(found.toList(), needsRestart)
        }
}

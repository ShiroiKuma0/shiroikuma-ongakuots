package com.maxrave.simpmusic.shiroikuma

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** One pickable font. [id] is what the theme stores; [label] is drawn in the face it names. */
data class SkFontChoice(
    val id: String,
    val label: String,
)

/** The newest archive already sitting in the backup folder. */
data class SkLatestBackup(
    val name: String,
    val size: Long,
)

/**
 * One selectable part of the backup, in the family's category-ZIP shape: `manifest.json` plus one
 * entry (or entry tree) per category. Import MERGES — a category absent from an archive is left
 * alone rather than cleared, so a settings-only backup never wipes the library.
 */
enum class SkBackupCat(
    val id: String,
    val label: String,
    val about: String,
    val defaultSelected: Boolean = true,
) {
    UI("ui", "白い熊 音楽乙 UI", "Colours, fonts, sizes and shapes — everything on this page"),
    FONTS("fonts", "Imported fonts", "The font files adopted through “Import a font file…”"),
    SETTINGS("settings", "App settings", "Everything under Settings: playback, quality, lyrics, proxy, accounts"),
    LIBRARY("library", "Library & history", "Playlists, liked songs, followed artists, listening history"),
    DOWNLOADS(
        "downloads",
        "Downloaded audio",
        "The downloaded media itself — large, and re-downloadable",
        defaultSelected = false,
    ),
    ;

    companion object {
        fun byId(id: String): SkBackupCat? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The font store: the app's own bundled face, plus whatever has been imported through the picker.
 *
 * Deliberately not composable — a family is resolved from a file, which needs no composition — so
 * the same call works from a preview row, from [typo] and from the backup engine.
 */
expect object SkFonts {
    /** Every pickable font, the bundled default first, then imported files by name. */
    fun choices(): List<SkFontChoice>

    /** The label for a stored id, or "App default" when nothing is chosen. */
    fun label(id: String): String

    /** The family for a stored id; null means "use the app's own bundled face". */
    fun family(id: String): FontFamily?

    /** Forget an imported font. Returns false if it was not ours to delete. */
    fun delete(id: String): Boolean

    /** Drop cached families after an import, a delete or a restore. */
    fun invalidate()
}

/** The backup folder, as the UI page needs to describe it before the panel is even opened. */
expect object SkBackupDir {
    /** The chosen folder as a human path, or null when none is set. */
    fun label(): String?

    fun isSet(): Boolean

    /** The newest backup already in the folder — the page's "last backup" line. */
    suspend fun latest(): SkLatestBackup?
}

/**
 * The Export / Import panel. Every part of it is platform work — the folder picker, the archive, the
 * file store — so the panel itself is an expect composable rather than a common one driven through a
 * dozen callbacks.
 *
 * [onFinished] is the close-the-whole-chain signal: after a SUCCESSFUL export or import, dismissing
 * the result dialog closes the panel *and* the UI page under it. A failure leaves both open, because
 * what failed is what you were about to fix.
 */
@Composable
expect fun SkExportImportPanel(
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
)

/**
 * Returns a lambda that opens the platform file picker and adopts the chosen font file, calling back
 * with its new id. Must be called from composition — it registers an activity-result launcher.
 */
@Composable
expect fun rememberSkFontImporter(onImported: (String) -> Unit): () -> Unit

/**
 * The 保存復元 automation gate, as the UI page needs it.
 *
 * The contract puts these two controls **inside the Export/Import section**, directly under the
 * export rows — not in a section of their own — so every sister app looks the same and 白い熊 finds
 * the switch where backup lives.
 */
expect object SkAutomation {
    fun enabled(): Boolean

    fun setEnabled(on: Boolean)

    /** Generated lazily on first read, so the row always has something to show. */
    fun token(): String

    fun regenerate(): String

    /** `80922d8c…4c49a87c` — enough to recognise, not enough to use. */
    fun abbreviated(token: String): String
}

/** Copy to the clipboard and say so — the token row's whole interaction. */
@Composable
expect fun rememberSkCopier(): (label: String, text: String) -> Unit

package com.maxrave.simpmusic.shiroikuma

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.maxrave.simpmusic.shiroikuma.automation.ExportControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Export / Import panel, in the Kōjiki sheet format: the whole thing inside one bordered rounded
 * box, a centred title over a dim intro, the backup folder as its own tappable box, thin accent
 * hairlines around a flat checklist, and the ArcaneChat button bar at the foot — Cancel alone on the
 * left, Import and Export grouped right, all three fully round pills.
 *
 * The folder box is **warn red until a directory is set** and house yellow once it is, matching the
 * red row on the UI page behind it.
 *
 * [onFinished] is the close-the-whole-chain signal: after a SUCCESSFUL export or import, dismissing
 * the result dialog closes this panel *and* the UI page under it. A failure ("Export failed…", "No
 * categories selected.") leaves everything open, because what failed is what you were about to fix.
 */
@Composable
actual fun SkExportImportPanel(
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = ui.c(ColorSlot.ACCENT)
    val warn = ui.c(ColorSlot.WARN)

    var dirLabel by remember { mutableStateOf(SkBackupDir.label()) }
    var latest by remember { mutableStateOf<SkLatestBackup?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PanelResult?>(null) }
    val selected = remember { SkBackupCat.entries.filter { it.defaultSelected }.map { it.id }.toMutableStateList() }

    // The folder is asked for its newest archive as the panel opens — the same question 白い熊 would
    // otherwise open a file manager to answer.
    LaunchedEffect(dirLabel) {
        latest = runCatching { SkBackupDir.latest() }.getOrNull()
    }

    val pickDir =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                SkBackupDir.set(uri)
                dirLabel = SkBackupDir.label()
            }
        }

    val pickImport =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            busy = true
            progress = "Reading the archive…"
            scope.launch {
                val r =
                    runCatching {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Could not read that file." }
                            SkBackup.import(input)
                        }
                    }
                busy = false
                progress = ""
                result =
                    r.fold(
                        onSuccess = { imported ->
                            if (imported.categories.isEmpty()) {
                                PanelResult(false, "Import failed", "Nothing in that file was recognised.", isImport = true)
                            } else {
                                PanelResult(
                                    ok = true,
                                    title = "Import finished",
                                    body =
                                        "Restored ${imported.categories.size} " +
                                            (if (imported.categories.size == 1) "category" else "categories") + ":\n" +
                                            imported.categories.joinToString("\n") { "· ${it.label}" },
                                    isImport = true,
                                    needsRestart = imported.needsRestart,
                                )
                            }
                        },
                        onFailure = { PanelResult(false, "Import failed", it.message ?: "Unknown error", isImport = true) },
                    )
            }
        }

    fun runExport() {
        val cats = SkBackupCat.entries.filter { it.id in selected }
        if (cats.isEmpty()) {
            result = PanelResult(false, "Nothing to export", "No categories selected.")
            return
        }
        val treeUri = SkBackupDir.uri()
        if (treeUri == null) {
            result = PanelResult(false, "Export failed", "No backup folder set.")
            return
        }
        if (!ExportControl.begin(null)) {
            result = PanelResult(false, "Export failed", "An export is already running.")
            return
        }
        busy = true
        scope.launch {
            val r =
                runCatching {
                    withContext(Dispatchers.IO) {
                        val name = SkBackup.timestampedName()
                        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: error("Backup folder is unavailable.")
                        dir.findFile("$name.part")?.delete()
                        // Written as .part and renamed only once closed: a killed export must never
                        // leave behind something a later restore would take for a real backup.
                        val part = dir.createFile("application/zip", "$name.part") ?: error("Could not create the file.")
                        try {
                            val count =
                                context.contentResolver.openOutputStream(part.uri).use { out ->
                                    requireNotNull(out) { "Could not write to the backup folder." }
                                    SkBackup.export(cats, out, ExportControl::isCancelled) { p ->
                                        progress = "区分 ${p.index}/${p.total} — ${p.cat.label}"
                                    }
                                }
                            if (!part.renameTo(name)) error("Could not finish writing $name.")
                            val bytes = dir.findFile(name)?.length() ?: 0L
                            Triple(name, bytes, count)
                        } catch (e: Exception) {
                            runCatching { part.delete() }
                            throw e
                        }
                    }
                }
            ExportControl.end()
            busy = false
            progress = ""
            result =
                r.fold(
                    onSuccess = { (name, bytes, count) ->
                        latest = SkLatestBackup(name, bytes)
                        PanelResult(true, "Export finished", "$name\n${skHumanSize(bytes)} · $count categories")
                    },
                    onFailure = {
                        if (it is SkBackup.Cancelled) {
                            PanelResult(false, "Export cancelled", "The partial file was removed.")
                        } else {
                            PanelResult(false, "Export failed", it.message ?: "Unknown error")
                        }
                    },
                )
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ui.c(ColorSlot.BG))
            .border(2.dp, accent, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                "Export / Import",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ui.c(ColorSlot.TEXT),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Back up everything settable in 白い熊 音楽乙 as one ZIP, or restore it. " +
                    "Importing merges — a category missing from an archive is left alone.",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = ui.c(ColorSlot.TEXT_DIM),
            )

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, if (dirLabel == null) warn else accent, RoundedCornerShape(10.dp))
                    .clickable { pickDir.launch(null) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        "Backup folder",
                        fontSize = 10.sp,
                        color = if (dirLabel == null) warn else ui.c(ColorSlot.TEXT_DIM),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dirLabel ?: "Not set — tap to choose a folder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (dirLabel == null) warn else ui.c(ColorSlot.TEXT),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                latest?.let { "Last backup: ${it.name} · ${skHumanSize(it.size)}" }
                    ?: if (dirLabel == null) "No folder set, so nothing can be exported yet." else "No backup in this folder yet.",
                fontSize = 10.sp,
                color = if (dirLabel == null) warn else ui.c(ColorSlot.TEXT_DIM),
            )

            Spacer(Modifier.height(12.dp))
            SkAccentHairline()
            CheckRow("Select all", bold = true, about = null, checked = selected.size == SkBackupCat.entries.size) { on ->
                selected.clear()
                if (on) selected.addAll(SkBackupCat.entries.map { it.id })
            }
            SkBackupCat.entries.forEach { cat ->
                CheckRow(cat.label, bold = false, about = cat.about, checked = cat.id in selected) { on ->
                    if (on) selected.add(cat.id) else selected.remove(cat.id)
                }
            }
            SkAccentHairline()

            Spacer(Modifier.height(14.dp))
            // ArcaneChat button bar: Cancel alone left, the two actions grouped right.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // While an export is running, Cancel STOPS it rather than only closing the panel:
                // the contract requires exactly one unwind path, and it is the one that deletes the
                // partial file. With nothing running it is the ordinary dismiss.
                SkPill(if (busy) "Stop" else "Cancel") {
                    if (busy) ExportControl.requestCancel() else onDismiss()
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkPill("Import", enabled = !busy) {
                        pickImport.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                    SkPill("Export", enabled = !busy && dirLabel != null) { runExport() }
                }
            }
            if (busy) {
                Spacer(Modifier.height(8.dp))
                Text(
                    progress.ifBlank { "Working…" },
                    fontSize = 11.sp,
                    color = ui.c(ColorSlot.TEXT_DIM),
                )
            }
        }
    }

    result?.let { r ->
        ResultDialog(r, onRestart = { skRestartApp() }) {
            result = null
            // Only success collapses the chain; a failure leaves the panel up to be fixed.
            if (r.ok) {
                onDismiss()
                onFinished()
            }
        }
    }
}

private class PanelResult(
    val ok: Boolean,
    val title: String,
    val body: String,
    val isImport: Boolean = false,
    val needsRestart: Boolean = false,
)

/** Black fill, yellow border, yellow title — and it never reports a success it did not have. */
@Composable
private fun ResultDialog(
    r: PanelResult,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    val accent = ui.c(ColorSlot.ACCENT)
    SkAlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                r.title,
                fontWeight = FontWeight.Bold,
                color = if (r.ok) accent else ui.c(ColorSlot.WARN),
            )
        },
        text = { Text(r.body, fontSize = 13.sp, color = ui.c(ColorSlot.TEXT)) },
        confirmButton = {
            // A restored database or settings file is only picked up by a fresh process. Either
            // choice closes the whole chain; "Restart now" simply does it immediately.
            TextButton(onClick = { if (r.ok && r.needsRestart) onRestart() else onClose() }) {
                Text(
                    when {
                        r.ok && r.needsRestart -> "Restart now"
                        else -> "OK"
                    },
                    color = accent,
                )
            }
        },
        dismissButton =
            if (r.ok && r.needsRestart) {
                { TextButton(onClick = onClose) { Text("Later", color = ui.c(ColorSlot.TEXT_DIM)) } }
            } else {
                null
            },
    )
}

@Composable
private fun CheckRow(
    label: String,
    bold: Boolean,
    about: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val ui = LocalOngakuUi.current
    val accent = ui.c(ColorSlot.ACCENT)
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) accent.copy(alpha = 0.22f) else Color.Transparent)
                .border(1.dp, accent, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", fontSize = 10.sp, color = accent)
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = ui.c(ColorSlot.TEXT),
            )
            if (about != null) Text(about, fontSize = 10.sp, color = ui.c(ColorSlot.TEXT_DIM))
        }
    }
}

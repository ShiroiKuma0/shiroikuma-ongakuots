package com.maxrave.simpmusic.shiroikuma

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Desktop stubs.
 *
 * This fork ships **Android only** — `/upstream-new-version` never builds `:desktopApp`, and the
 * 白い熊 音楽乙 UI page is an Android surface. These actuals exist so the `jvm` target of
 * `:composeApp` still compiles if upstream's desktop build is ever run from this tree; they are
 * deliberately inert rather than half-working, so nothing here can quietly write a backup that the
 * Android side would then be asked to restore.
 */
actual object SkFonts {
    actual fun choices(): List<SkFontChoice> = listOf(SkFontChoice("", "App default (Poppins)"))

    actual fun label(id: String): String = "App default (Poppins)"

    actual fun family(id: String): FontFamily? = null

    actual fun delete(id: String): Boolean = false

    actual fun invalidate() = Unit
}

actual object SkBackupDir {
    actual fun label(): String? = null

    actual fun isSet(): Boolean = false

    actual suspend fun latest(): SkLatestBackup? = null
}

@Composable
actual fun SkExportImportPanel(
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Export / Import", fontSize = 16.sp, color = ui.c(ColorSlot.TEXT))
        Text(
            "Not available on desktop — this fork is built for Android.",
            fontSize = 12.sp,
            color = ui.c(ColorSlot.TEXT_DIM),
        )
        SkPill("Close", onClick = onDismiss)
    }
}

@Composable
actual fun rememberSkFontImporter(onImported: (String) -> Unit): () -> Unit = {}

actual object SkAutomation {
    actual fun enabled(): Boolean = false

    actual fun setEnabled(on: Boolean) = Unit

    actual fun token(): String = ""

    actual fun regenerate(): String = ""

    actual fun abbreviated(token: String): String = token
}

@Composable
actual fun rememberSkCopier(): (String, String) -> Unit = { _, _ -> }

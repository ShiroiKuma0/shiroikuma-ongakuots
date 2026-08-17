package com.maxrave.simpmusic.shiroikuma

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.ArrowForwardIos
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.Home
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.Settings
import com.maxrave.simpmusic.ui.icon.SimpIcons
import kotlin.math.roundToInt
import org.koin.compose.koinInject

// ---- page metrics ------------------------------------------------------------------------------

/**
 * How far each nesting level is pushed in. Deliberately generous — 24dp a step against the 16dp of a
 * conventional settings list — because the requirement is that the level a row sits at is readable
 * at a glance, without reading the row.
 */
private fun indent(level: Int) = (16 + level * 24).dp

// ---- the page ----------------------------------------------------------------------------------

/**
 * **白い熊 音楽乙 UI** — the fork's configuration hub, in the kxkb UI-page format: bold headings
 * underlined only as wide as their own text, thin full-width hairlines between top-level sections,
 * deeply indented rows, and no wasted vertical space anywhere inside a section.
 *
 * Everything the house look is built from is settable here, and **every control previews itself** —
 * the colours, type, shape and icon rows all land on the live app the moment they move, because the
 * page edits the very [OngakuUiState] the whole app renders from.
 *
 * Reached from Settings, and by **long-pressing the settings cog** on the home screen.
 */
@Composable
fun OngakuUiScreen(
    navController: NavController,
    innerPadding: PaddingValues,
) {
    val state: OngakuUiState = koinInject()
    val ui = LocalOngakuUi.current

    var pickingColor by remember { mutableStateOf<ColorSlot?>(null) }
    var pickingFont by remember { mutableStateOf(false) }
    var showExim by remember { mutableStateOf(false) }
    var dirLabel by remember { mutableStateOf(SkBackupDir.label()) }
    var latest by remember { mutableStateOf<SkLatestBackup?>(null) }
    var automationOn by remember { mutableStateOf(SkAutomation.enabled()) }
    var token by remember { mutableStateOf(SkAutomation.token()) }
    val copy = rememberSkCopier()

    // The folder is queried for its newest archive as the page opens — the same question 白い熊 would
    // otherwise open a file manager to answer — and again whenever the panel closes.
    LaunchedEffect(showExim) {
        dirLabel = SkBackupDir.label()
        latest = runCatching { SkBackupDir.latest() }.getOrNull()
    }

    val importFont = rememberSkFontImporter { id ->
        SkFonts.invalidate()
        state.edit { it.copy(fontId = id) }
    }

    Column(Modifier.fillMaxSize().background(ui.c(ColorSlot.BG)).padding(innerPadding)) {
        SkTopBar(title = "白い熊 音楽乙 UI") { navController.navigateUp() }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            // ---- Export / Import ----------------------------------------------------------------
            item { SectionHeader("Export / Import", first = true) }
            item {
                NavRow(
                    1,
                    "Export / Import…",
                    if (dirLabel != null) {
                        "Back up everything settable in this app as one ZIP, or restore it."
                    } else {
                        "No backup folder set yet — tap to choose one."
                    },
                    warn = dirLabel == null,
                ) { showExim = true }
            }
            item {
                InfoRow(
                    2,
                    "Backup folder",
                    dirLabel ?: "Not set",
                    warn = dirLabel == null,
                )
            }
            item {
                InfoRow(
                    2,
                    "Last backup",
                    latest?.let { "${it.name}  ·  ${skHumanSize(it.size)}" }
                        ?: if (dirLabel == null) "No folder set, so nothing can be exported yet." else "None in this folder yet.",
                    warn = dirLabel == null,
                )
            }

            // Per the 保存復元 contract the automation controls belong in THIS section, directly
            // under the export rows — not in a section of their own — so every sister app looks the
            // same and the switch is found where backup lives.
            item {
                SwitchRow(
                    1,
                    "Automation export",
                    "Let 白い熊 自由作業盤 trigger this app's export over the token-gated intent.",
                    automationOn,
                ) { on ->
                    SkAutomation.setEnabled(on)
                    automationOn = on
                }
            }
            item {
                TokenRow(
                    2,
                    token,
                    enabled = automationOn,
                    onCopy = { copy("Automation token", token) },
                    onRegenerate = {
                        token = SkAutomation.regenerate()
                        copy("New automation token", token)
                    },
                )
            }

            // ---- Theme ---------------------------------------------------------------------------
            item { SectionHeader("Theme") }
            item {
                SwitchRow(
                    1,
                    "白い熊 音楽乙 UI",
                    "Off, the app falls back to its stock Material chrome — every edit below is kept.",
                    ui.enabled,
                ) { on -> state.edit { it.copy(enabled = on) } }
            }
            item {
                ActionRow(
                    1,
                    "Everything on this page",
                    "Reset",
                    "Back to stock black-yellow: every colour, font, size and shape.",
                ) { state.resetAll() }
            }

            // ---- Colours -------------------------------------------------------------------------
            item { SectionHeader("Colours") }
            item { ColorPreview() }
            ColorGroup.entries.forEach { group ->
                item { GroupLabel(1, group.label) }
                ColorSlot.of(group).forEach { slot ->
                    item { ColorRow(2, slot, ui.color(slot)) { pickingColor = slot } }
                }
            }

            // ---- Typography ----------------------------------------------------------------------
            item { SectionHeader("Typography") }
            item { TypePreview() }
            item { FontRow(1, ui.fontId) { pickingFont = true } }
            item {
                SliderRow(1, "Text size", ui.fontScalePct, "${ui.fontScalePct} %", 60f..200f, step = 5) { v ->
                    state.edit { it.copy(fontScalePct = v) }
                }
            }
            item {
                SliderRow(1, "Weight", ui.fontWeight, weightLabel(ui.fontWeight), 100f..900f, step = 100) { v ->
                    state.edit { it.copy(fontWeight = v) }
                }
            }

            // ---- Shape & lines -------------------------------------------------------------------
            item { SectionHeader("Shape & lines") }
            item { ShapePreview() }
            item {
                SliderRow(1, "Corner roundness", ui.cornerRadiusDp, cornerLabel(ui.cornerRadiusDp), 0f..32f) { v ->
                    state.edit { it.copy(cornerRadiusDp = v) }
                }
            }
            item {
                SliderRow(1, "Border thickness", ui.borderWidthDp, borderLabel(ui.borderWidthDp), 0f..8f) { v ->
                    state.edit { it.copy(borderWidthDp = v) }
                }
            }

            // ---- Icons & density -----------------------------------------------------------------
            item { SectionHeader("Icons & density") }
            item { IconPreview() }
            item {
                SliderRow(1, "Icon size", ui.iconSizeDp, "${ui.iconSizeDp} dp", 12f..44f) { v ->
                    state.edit { it.copy(iconSizeDp = v) }
                }
            }
            item {
                SliderRow(1, "Row spacing", ui.rowPaddingDp, "${ui.rowPaddingDp} dp", 0f..20f) { v ->
                    state.edit { it.copy(rowPaddingDp = v) }
                }
            }
        }
    }

    if (showExim) {
        SkExportImportPanelHost(
            onDismiss = { showExim = false },
            onFinished = {
                showExim = false
                navController.navigateUp()
            },
        )
    }

    pickingColor?.let { slot ->
        SkColorPickerDialog(
            slot = slot,
            initial = ui.color(slot),
            recent = ui.recent,
            onDismiss = { pickingColor = null },
            onConfirm = { c ->
                state.edit { it.withColor(slot, c) }
                pickingColor = null
            },
        )
    }
    if (pickingFont) {
        SkFontPickerDialog(
            current = ui.fontId,
            onDismiss = { pickingFont = false },
            onPick = { id ->
                state.edit { it.copy(fontId = id) }
                pickingFont = false
            },
            onImport = {
                pickingFont = false
                importFont()
            },
            onDelete = { id ->
                SkFonts.delete(id)
                SkFonts.invalidate()
                if (ui.fontId == id) state.edit { it.copy(fontId = "") }
                pickingFont = false
            },
        )
    }
}

/**
 * The panel is drawn over a scrim so the page beneath does not take taps, and centred rather than
 * anchored — it is a dialog in everything but the platform window.
 */
@Composable
private fun SkExportImportPanelHost(
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    Box(
        Modifier
            .fillMaxSize()
            .background(ui.c(ColorSlot.BG).copy(alpha = 0.86f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Swallow taps that land on the panel itself rather than the scrim behind it.
        Box(Modifier.clickable(enabled = false) {}) {
            SkExportImportPanel(onDismiss = onDismiss, onFinished = onFinished)
        }
    }
}

// ---- structure ---------------------------------------------------------------------------------

@Composable
private fun SkTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                SimpIcons.ArrowBackIosNew,
                null,
                Modifier.size(ui.iconSizeDp.dp).clickable(onClick = onBack),
                tint = ui.c(ColorSlot.ACCENT),
            )
            Text(
                title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = ui.c(ColorSlot.TEXT),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = ui.c(ColorSlot.DIVIDER))
    }
}

/**
 * A top-level heading: bold, large, underlined **only as wide as its own text** ([IntrinsicSize.Min]
 * sizes the column to the single line), each section but the first opened by a thin full-width
 * hairline. This is the kxkb UI page's heading, and the only place the page spends vertical space.
 */
@Composable
private fun SectionHeader(
    title: String,
    first: Boolean = false,
) {
    val ui = LocalOngakuUi.current
    Column(Modifier.fillMaxWidth()) {
        if (!first) {
            HorizontalDivider(
                Modifier.padding(top = 18.dp),
                thickness = 1.dp,
                color = ui.c(ColorSlot.DIVIDER),
            )
        }
        Column(
            Modifier
                .padding(start = 16.dp, top = if (first) 10.dp else 16.dp, end = 16.dp, bottom = 6.dp)
                .width(IntrinsicSize.Min),
        ) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ui.c(ColorSlot.ACCENT),
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(3.dp))
            HorizontalDivider(thickness = 2.dp, color = ui.c(ColorSlot.ACCENT))
        }
    }
}

/** A sub-heading inside a section: its rows sit one level deeper again. */
@Composable
private fun GroupLabel(
    level: Int,
    text: String,
) {
    val ui = LocalOngakuUi.current
    Text(
        text,
        Modifier.fillMaxWidth().padding(start = indent(level), end = 16.dp, top = 8.dp, bottom = 2.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = ui.c(ColorSlot.TEXT_DIM),
    )
}

/**
 * The one row shape. Vertical padding is the theme's own density control, so the page tightens or
 * opens as it is edited — the setting previews itself by *being* the thing it changes.
 */
@Composable
private fun RowScaffold(
    level: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val pad = LocalOngakuUi.current.rowPaddingDp.dp
    val base = Modifier.fillMaxWidth()
    Row(
        modifier =
            (if (onClick != null) base.clickable(onClick = onClick) else base)
                .padding(start = indent(level), end = 16.dp, top = pad, bottom = pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// ---- rows --------------------------------------------------------------------------------------

@Composable
private fun ColorRow(
    level: Int,
    slot: ColorSlot,
    value: SkRgba,
    onClick: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    val shape = RoundedCornerShape(ui.cornerRadiusDp.dp)
    RowScaffold(level, onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(slot.label, fontSize = 15.sp, color = ui.c(ColorSlot.TEXT))
            Text(
                "${slot.about}  ·  ${value.hex()}",
                fontSize = 11.sp,
                color = ui.c(ColorSlot.TEXT_DIM),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The swatch is itself a live preview of the border and corner values edited further down.
        Box(
            Modifier
                .size(26.dp)
                .clip(shape)
                .background(value.toColor())
                .border(ui.borderWidthDp.dp.coerceAtLeast(1.dp), ui.c(ColorSlot.BORDER), shape),
        )
    }
}

@Composable
private fun SliderRow(
    level: Int,
    label: String,
    value: Int,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    val ui = LocalOngakuUi.current
    val pad = ui.rowPaddingDp.dp
    Column(Modifier.fillMaxWidth().padding(start = indent(level), end = 16.dp, top = pad, bottom = pad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 15.sp, color = ui.c(ColorSlot.TEXT))
            Text(valueText, fontSize = 12.sp, color = ui.c(ColorSlot.TEXT_DIM))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v -> onChange((v / step).roundToInt() * step) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(26.dp),
            colors = skSliderColors(),
        )
    }
}

@Composable
private fun FontRow(
    level: Int,
    fontId: String,
    onClick: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    RowScaffold(level, onClick = onClick) {
        Text("Font", Modifier.weight(1f), fontSize = 15.sp, color = ui.c(ColorSlot.TEXT))
        // Named in its own glyphs, so the row is already the preview.
        Text(
            SkFonts.label(fontId),
            fontFamily = SkFonts.family(fontId) ?: FontFamily.Default,
            fontSize = 15.sp,
            color = ui.c(ColorSlot.TEXT_DIM),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row that opens something. [warn] paints it the theme's warn red — an unset backup folder. */
@Composable
private fun NavRow(
    level: Int,
    label: String,
    about: String,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    RowScaffold(level, onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = ui.c(ColorSlot.TEXT))
            Text(
                about,
                fontSize = 11.sp,
                color = if (warn) ui.c(ColorSlot.WARN) else ui.c(ColorSlot.TEXT_DIM),
            )
        }
        Icon(SimpIcons.ArrowForwardIos, null, Modifier.size(14.dp), tint = ui.c(ColorSlot.TEXT_DIM))
    }
}

/** A read-only fact. Red while the thing it reports is missing. */
@Composable
private fun InfoRow(
    level: Int,
    label: String,
    value: String,
    warn: Boolean = false,
) {
    val ui = LocalOngakuUi.current
    RowScaffold(level) {
        Text(label, Modifier.width(96.dp), fontSize = 12.sp, color = ui.c(ColorSlot.TEXT_DIM))
        Text(
            value,
            Modifier.weight(1f),
            fontSize = 12.sp,
            color = if (warn) ui.c(ColorSlot.WARN) else ui.c(ColorSlot.TEXT),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The automation token: tap the row to copy the whole thing, Regenerate on the right. Only the
 * abbreviation is ever shown — enough to tell which token a row is holding, not enough to use.
 */
@Composable
private fun TokenRow(
    level: Int,
    token: String,
    enabled: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    val dim = ui.c(ColorSlot.TEXT_DIM)
    RowScaffold(level, onClick = { if (enabled) onCopy() }) {
        Column(Modifier.weight(1f)) {
            Text("Token", fontSize = 15.sp, color = if (enabled) ui.c(ColorSlot.TEXT) else dim)
            Text(
                if (enabled) "${SkAutomation.abbreviated(token)}  ·  tap to copy" else "Turn automation on to use the token",
                fontSize = 11.sp,
                color = dim,
            )
        }
        Text(
            "Regenerate",
            Modifier.clickable(enabled = enabled) { onRegenerate() },
            fontSize = 12.sp,
            color = if (enabled) ui.c(ColorSlot.ACCENT) else dim,
        )
    }
}

/** A row whose right-hand side is a verb rather than a control. */
@Composable
private fun ActionRow(
    level: Int,
    label: String,
    verb: String,
    about: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    val dim = ui.c(ColorSlot.TEXT_DIM)
    RowScaffold(level, onClick = { if (enabled) onClick() }) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = if (enabled) ui.c(ColorSlot.TEXT) else dim)
            Text(about, fontSize = 11.sp, color = dim)
        }
        Text(verb, fontSize = 12.sp, color = if (enabled) ui.c(ColorSlot.ACCENT) else dim)
    }
}

@Composable
private fun SwitchRow(
    level: Int,
    label: String,
    about: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val ui = LocalOngakuUi.current
    RowScaffold(level, onClick = { onChange(!checked) }) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = ui.c(ColorSlot.TEXT))
            Text(about, fontSize = 11.sp, color = ui.c(ColorSlot.TEXT_DIM))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = ui.c(ColorSlot.ON_ACCENT),
                    checkedTrackColor = ui.c(ColorSlot.ACCENT),
                    uncheckedThumbColor = ui.c(ColorSlot.TEXT_DIM),
                    uncheckedTrackColor = ui.c(ColorSlot.SURFACE_HI),
                    uncheckedBorderColor = ui.c(ColorSlot.BORDER),
                ),
        )
    }
}

// ---- previews ----------------------------------------------------------------------------------

/** The frame every preview sits in: indented like a row, drawn with the live border and corner. */
@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    val ui = LocalOngakuUi.current
    val shape = RoundedCornerShape(ui.cornerRadiusDp.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = indent(1), end = 16.dp, top = 4.dp, bottom = 6.dp)
            .clip(shape)
            .background(ui.c(ColorSlot.SURFACE))
            .border(ui.borderWidthDp.dp, ui.c(ColorSlot.BORDER), shape)
            .padding(10.dp),
    ) { content() }
}

@Composable
private fun ColorPreview() {
    val ui = LocalOngakuUi.current
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Song title", fontSize = 15.sp, color = ui.c(ColorSlot.TEXT))
            Text("Artist · Album · 3:41", fontSize = 12.sp, color = ui.c(ColorSlot.TEXT_DIM))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SimpIcons.Favorite, null, Modifier.size(14.dp), tint = ui.c(ColorSlot.FAVOURITE))
                Text("Accent", fontSize = 12.sp, color = ui.c(ColorSlot.ACCENT))
                Text("Disabled", fontSize = 12.sp, color = ui.c(ColorSlot.TEXT_DISABLED))
                Text("Warning", fontSize = 12.sp, color = ui.c(ColorSlot.WARN))
            }
            // The seek bar, so the three player colours are visible where they actually land.
            Box(Modifier.fillMaxWidth().height(4.dp).background(ui.c(ColorSlot.PROGRESS_TRACK))) {
                Box(Modifier.fillMaxWidth(0.45f).height(4.dp).background(ui.c(ColorSlot.PROGRESS)))
            }
            Text("Lyric line being sung", fontSize = 12.sp, color = ui.c(ColorSlot.LYRIC_ACTIVE))
        }
    }
}

@Composable
private fun TypePreview() {
    val ui = LocalOngakuUi.current
    val family = SkFonts.family(ui.fontId) ?: FontFamily.Default
    val s = ui.fontScalePct / 100f
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "白い熊 音楽乙",
                fontFamily = family,
                fontSize = (19 * s).sp,
                fontWeight = FontWeight(ui.titleWeight),
                color = ui.c(ColorSlot.TEXT),
            )
            Text(
                "Now playing — a song, an artist, an album.",
                fontFamily = family,
                fontSize = (14 * s).sp,
                fontWeight = FontWeight(ui.fontWeight),
                color = ui.c(ColorSlot.TEXT),
            )
            Text(
                "0123456789  ·  the quick brown fox",
                fontFamily = family,
                fontSize = (11 * s).sp,
                fontWeight = FontWeight(ui.fontWeight),
                color = ui.c(ColorSlot.TEXT_DIM),
            )
        }
    }
}

@Composable
private fun ShapePreview() {
    val ui = LocalOngakuUi.current
    val shape = RoundedCornerShape(ui.cornerRadiusDp.dp)
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(34.dp)
                    .weight(1f)
                    .clip(shape)
                    .background(ui.c(ColorSlot.BG))
                    .border(ui.borderWidthDp.dp, ui.c(ColorSlot.BORDER), shape),
                contentAlignment = Alignment.Center,
            ) { Text("Outlined", fontSize = 12.sp, color = ui.c(ColorSlot.TEXT)) }
            Box(
                Modifier.height(34.dp).weight(1f).clip(shape).background(ui.c(ColorSlot.ACCENT)),
                contentAlignment = Alignment.Center,
            ) { Text("Filled", fontSize = 12.sp, color = ui.c(ColorSlot.ON_ACCENT)) }
        }
    }
}

@Composable
private fun IconPreview() {
    val ui = LocalOngakuUi.current
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(SimpIcons.Home, SimpIcons.Search, SimpIcons.Favorite, SimpIcons.Settings).forEach { icon ->
                Icon(icon, null, Modifier.size(ui.iconSizeDp.dp), tint = ui.c(ColorSlot.ACCENT))
            }
        }
    }
}

// ---- labels ------------------------------------------------------------------------------------

private fun weightLabel(w: Int) =
    when (w) {
        100 -> "100 Thin"
        200 -> "200 Extra light"
        300 -> "300 Light"
        400 -> "400 Regular"
        500 -> "500 Medium"
        600 -> "600 Semi bold"
        700 -> "700 Bold"
        800 -> "800 Extra bold"
        else -> "900 Black"
    }

private fun borderLabel(dp: Int) = if (dp == 0) "0 dp — no borders" else "$dp dp"

private fun cornerLabel(dp: Int) = if (dp == 0) "0 dp — square" else "$dp dp"

fun skHumanSize(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> "${(bytes / 1e8).roundToInt() / 10.0} GB"
        bytes >= 1_000_000 -> "${(bytes / 1e5).roundToInt() / 10.0} MB"
        bytes >= 1_000 -> "${(bytes / 1e3).roundToInt()} kB"
        else -> "$bytes B"
    }

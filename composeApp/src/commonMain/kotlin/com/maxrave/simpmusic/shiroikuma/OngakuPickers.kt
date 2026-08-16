package com.maxrave.simpmusic.shiroikuma

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The house colour picker: four 0–255 channel sliders (R, G, B, A) under a live preview, with a row
 * of one-click boxes **above** them prefilled with the colours picked before — so the palette 白い熊
 * is actually building is one tap away instead of being dialled in again on every row.
 *
 * The slot's own black-yellow default always leads that row, so "put it back" never needs the hex.
 */
@Composable
fun SkColorPickerDialog(
    slot: ColorSlot,
    initial: SkRgba,
    recent: List<SkRgba>,
    onDismiss: () -> Unit,
    onConfirm: (SkRgba) -> Unit,
) {
    val ui = LocalOngakuUi.current
    var r by remember { mutableIntStateOf(initial.r) }
    var g by remember { mutableIntStateOf(initial.g) }
    var b by remember { mutableIntStateOf(initial.b) }
    var a by remember { mutableIntStateOf(initial.a) }
    val current = SkRgba(r, g, b, a)
    fun set(c: SkRgba) {
        r = c.r; g = c.g; b = c.b; a = c.a
    }

    val swatches = remember(recent, slot) { (listOf(slot.default) + recent).distinct().take(24) }

    SkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(slot.label, fontWeight = FontWeight.Bold, color = ui.c(ColorSlot.TEXT))
                Text(slot.about, fontSize = 11.sp, color = ui.c(ColorSlot.TEXT_DIM))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recent", fontSize = 11.sp, color = ui.c(ColorSlot.TEXT_DIM))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    swatches.forEach { c ->
                        val picked = c == current
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.toColor())
                                .border(
                                    if (picked) 2.dp else 1.dp,
                                    if (picked) ui.c(ColorSlot.ACCENT) else ui.c(ColorSlot.BORDER),
                                    RoundedCornerShape(6.dp),
                                ).clickable { set(c) },
                        )
                    }
                }
                // The preview sits directly over the sliders, so a channel's effect is seen where
                // the eye already is.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(current.toColor())
                        .border(1.dp, ui.c(ColorSlot.BORDER), RoundedCornerShape(8.dp)),
                )
                SkChannelSlider("R", r) { r = it }
                SkChannelSlider("G", g) { g = it }
                SkChannelSlider("B", b) { b = it }
                SkChannelSlider("A", a) { a = it }
                Text(current.hex(), fontSize = 12.sp, color = ui.c(ColorSlot.TEXT_DIM))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text("Apply", color = ui.c(ColorSlot.ACCENT))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { set(slot.default) }) {
                    Text("Default", color = ui.c(ColorSlot.TEXT_DIM))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = ui.c(ColorSlot.TEXT_DIM))
                }
            }
        },
    )
}

@Composable
private fun SkChannelSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    val ui = LocalOngakuUi.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.width(14.dp), fontSize = 12.sp, color = ui.c(ColorSlot.TEXT_DIM))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = skSliderColors(),
        )
        Text(
            value.toString(),
            Modifier.width(30.dp),
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            color = ui.c(ColorSlot.TEXT_DIM),
        )
    }
}

/** Every slider in the fork, in the theme's own colours rather than Material's primary. */
@Composable
fun skSliderColors(): SliderColors {
    val ui = LocalOngakuUi.current
    return SliderDefaults.colors(
        thumbColor = ui.c(ColorSlot.SLIDER_THUMB),
        activeTrackColor = ui.c(ColorSlot.PROGRESS),
        inactiveTrackColor = ui.c(ColorSlot.PROGRESS_TRACK),
    )
}

/**
 * The font picker. Every option is drawn **in its own glyphs**, so the list is the preview — the
 * only honest way to choose a typeface. "Import a font file…" hands off to the platform picker.
 */
@Composable
fun SkFontPickerDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val ui = LocalOngakuUi.current
    val choices = remember(current) { SkFonts.choices() }
    SkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font", fontWeight = FontWeight.Bold, color = ui.c(ColorSlot.TEXT)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { c ->
                    SkFontOptionRow(
                        label = c.label,
                        id = c.id,
                        selected = current == c.id,
                        // Only imported faces can be removed; the bundled one has no file.
                        deletable = c.id.isNotBlank(),
                        onPick = onPick,
                        onDelete = onDelete,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport) {
                Text("Import a font file…", color = ui.c(ColorSlot.ACCENT))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ui.c(ColorSlot.TEXT_DIM))
            }
        },
    )
}

@Composable
private fun SkFontOptionRow(
    label: String,
    id: String,
    selected: Boolean,
    deletable: Boolean,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val ui = LocalOngakuUi.current
    Row(
        Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            Modifier.weight(1f),
            // The point of the row: the name is set in the face it names.
            fontFamily = SkFonts.family(id) ?: FontFamily.Default,
            fontSize = 16.sp,
            color = if (selected) ui.c(ColorSlot.ACCENT) else ui.c(ColorSlot.TEXT),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) Text("●", fontSize = 12.sp, color = ui.c(ColorSlot.ACCENT))
        if (deletable) {
            Text(
                "Remove",
                Modifier.clickable { onDelete(id) },
                fontSize = 11.sp,
                color = ui.c(ColorSlot.TEXT_DIM),
            )
        }
    }
}

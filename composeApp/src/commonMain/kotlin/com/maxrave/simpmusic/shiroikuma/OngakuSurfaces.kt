package com.maxrave.simpmusic.shiroikuma

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/**
 * The house dialog: the theme's own background, a border in the theme's border colour at the
 * theme's own thickness, and the theme's corner radius. Every dialog this fork raises goes through
 * it, so one added later comes out black-and-yellow by construction rather than by being remembered.
 */
@Composable
fun SkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val ui = LocalOngakuUi.current
    val shape = RoundedCornerShape(ui.cornerRadiusDp.dp)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        // The border sits on the caller's modifier chain rather than under it, so a call site that
        // adds its own sizing still gets the outline.
        modifier = modifier.border(ui.borderWidthDp.dp.coerceAtLeast(1.dp), ui.c(ColorSlot.BORDER), shape),
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = ui.c(ColorSlot.MENU_BG),
        titleContentColor = ui.c(ColorSlot.TEXT),
        textContentColor = ui.c(ColorSlot.TEXT),
        properties = properties,
    )
}

/**
 * ArcaneChat's action pill: fully round, a 1.5dp accent stroke, the theme background inside and
 * accent text. Disabled drops the whole thing to the dim colour rather than fading it, so a pill
 * that cannot be pressed still reads as a pill.
 */
@Composable
fun SkPill(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ui = LocalOngakuUi.current
    val shape = RoundedCornerShape(50.dp)
    val tint = if (enabled) ui.c(ColorSlot.ACCENT) else ui.c(ColorSlot.TEXT_DISABLED)
    Box(
        Modifier
            .clip(shape)
            .background(ui.c(ColorSlot.BG))
            .border(1.5.dp, tint, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

/** The thin accent rule the panel's checklist is bracketed by. */
@Composable
fun SkAccentHairline() {
    HorizontalDivider(thickness = 1.dp, color = LocalOngakuUi.current.c(ColorSlot.DIVIDER))
}

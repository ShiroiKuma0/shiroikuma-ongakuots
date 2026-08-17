package com.maxrave.simpmusic.shiroikuma

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
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

/**
 * The content colour for rows that resolve it by hand.
 *
 * A dozen list items across `AdapterItems` and `FullWidthItems` write
 * `if (forceDark) Color.White else colorScheme.onSurface` themselves, and that first branch is a
 * literal no colour scheme can reach — it is why the track lists stayed white-on-grey while the
 * rest of the app was black-yellow. This is the same expression with our slot in front of it.
 */
@Composable
fun skContentColor(forceDark: Boolean): Color {
    val ui = LocalOngakuUi.current
    return when {
        ui.enabled -> ui.c(ColorSlot.TEXT)
        forceDark -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/**
 * The content colour on the player and its sheets.
 *
 * The player screen writes `Color.White` at three dozen sites — titles, transport icons, the like
 * control — because it was designed to sit on artwork, where white is the only safe answer. With our
 * theme on the artwork backdrop is replaced by flat black, so white is no longer the safe answer and
 * the house yellow is; with it off, this is exactly the literal it replaces.
 */
@Composable
fun skOnPlayer(): Color {
    val ui = LocalOngakuUi.current
    return if (ui.enabled) ui.c(ColorSlot.TEXT) else Color.White
}

/** The player's own backdrop — flat, so nothing on it has to fight an artwork tint. */
@Composable
fun skPlayerBackdrop(fallback: Color): Color {
    val ui = LocalOngakuUi.current
    return if (ui.enabled) ui.c(ColorSlot.PLAYER_BG) else fallback
}

/** The lyrics sheet's own backdrop — `#242424` upstream, black here. */
@Composable
fun skLyricsBackground(): Color {
    val ui = LocalOngakuUi.current
    return if (ui.enabled) ui.c(ColorSlot.SURFACE) else Color(0xFF242424)
}

/**
 * The house card frame. Material's `ElevatedCard` has no `border` parameter — it separates itself
 * from the background by a tonal-elevation tint, which on a flat black theme is invisible. So the
 * cards are framed instead: the border colour, at the theme's own thickness.
 */
@Composable
fun Modifier.skCardFrame(shape: Shape): Modifier {
    val ui = LocalOngakuUi.current
    return if (ui.enabled && ui.frames && ui.borderWidthDp > 0) {
        this.border(ui.borderWidthDp.dp, ui.c(ColorSlot.BORDER), shape)
    } else {
        this
    }
}

/**
 * Left and right rules only.
 *
 * The player sheet is a column suspended over the library behind it; on a black theme its edges were
 * invisible, so it read as content floating in the middle of the screen rather than as a panel. Two
 * vertical rules give it sides without boxing it in top and bottom, where it runs off-screen.
 */
@Composable
fun Modifier.skSideBorders(): Modifier {
    val ui = LocalOngakuUi.current
    if (!ui.enabled || !ui.sideRules || ui.borderWidthDp <= 0) return this
    val color = ui.c(ColorSlot.BORDER)
    val width = ui.borderWidthDp.dp
    // drawWithContent, NOT drawBehind: everything inside this container paints an opaque background
    // of its own — artwork, sheets, list surfaces — so rules drawn behind it were simply covered,
    // and showed only in the gaps where nothing happened to paint. Drawing them after the content
    // is what makes them edges of the band rather than something underneath it.
    return this.drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(color = color, topLeft = Offset.Zero, size = Size(w, size.height))
        drawRect(color = color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
    }
}

/**
 * The house dialog frame, for the dialogs this fork did not author.
 *
 * Material 3 gives an `AlertDialog` a `containerColor` and a `shape` but no border, and there is no
 * global hook for one — so upstream's dialogs get it through their `modifier` instead. Our own go
 * through [SkAlertDialog], which does the same thing.
 */
@Composable
fun Modifier.skDialogFrame(): Modifier {
    val ui = LocalOngakuUi.current
    if (!ui.enabled || !ui.frames) return this
    val shape = RoundedCornerShape(ui.cornerRadiusDp.dp)
    return this.border(ui.borderWidthDp.dp.coerceAtLeast(1.dp), ui.c(ColorSlot.BORDER), shape)
}

/**
 * The house frame as a [BorderStroke], for surfaces that take one directly.
 *
 * `BasicAlertDialog` is only a window — the look belongs to the `Surface` inside it, and a border on
 * the window would trace the wrong rectangle. `Surface` has its own `border` parameter, so it gets
 * the stroke and keeps its own shape.
 */
@Composable
fun skDialogBorder(): BorderStroke? {
    val ui = LocalOngakuUi.current
    return if (ui.enabled && ui.frames) BorderStroke(ui.borderWidthDp.dp.coerceAtLeast(1.dp), ui.c(ColorSlot.BORDER)) else null
}

/** The unavailable transport control — `Color.DarkGray` upstream, our disabled text slot here. */
@Composable
fun skDisabledOnPlayer(): Color {
    val ui = LocalOngakuUi.current
    return if (ui.enabled) ui.c(ColorSlot.TEXT_DISABLED) else Color.DarkGray
}

/**
 * A traced action control: black inside, the border colour around it — the same treatment as the
 * player's transport button, applied to the round and pill-shaped actions on the album, artist and
 * playlist pages.
 *
 * Upstream fills these solid (a white pill, a white-at-12% circle). Tinting the fill with the accent
 * gives a solid yellow lozenge, which is not what the house look is; tracing them keeps the page
 * black and lets the border carry the shape (白い熊, 2026-08-16).
 */
@Composable
fun Modifier.skTracedAction(
    shape: Shape,
    stockFill: Color,
): Modifier {
    val ui = LocalOngakuUi.current
    return if (ui.enabled && ui.tracedActions) {
        this
            .background(ui.c(ColorSlot.BG), shape)
            .border(ui.borderWidthDp.dp.coerceAtLeast(1.dp), ui.c(ColorSlot.BORDER), shape)
    } else {
        this.background(stockFill, shape)
    }
}

/** Content drawn on a traced action — yellow on our black, black on upstream's white fill. */
@Composable
fun skOnAction(): Color {
    val ui = LocalOngakuUi.current
    return if (ui.enabled && ui.tracedActions) ui.c(ColorSlot.TEXT) else Color.Black
}

package com.maxrave.simpmusic.shiroikuma

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A colour as the four channels the picker edits. Stored channel-wise rather than as a packed Int so
 * the serialised form stays readable in an exported backup, and so alpha is a first-class field —
 * every chrome colour on the 白い熊 音楽乙 UI page is alpha-settable from its own slider.
 */
@Serializable
@Immutable
data class SkRgba(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int = 255,
) {
    fun toColor(): Color = Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), a.coerceIn(0, 255))

    /** `#AARRGGBB`, the form shown under a colour row and typed into the hex box. */
    fun hex(): String {
        fun h(v: Int): String {
            val s = v.coerceIn(0, 255).toString(16).uppercase()
            return if (s.length == 1) "0$s" else s
        }
        return "#${h(a)}${h(r)}${h(g)}${h(b)}"
    }

    companion object {
        fun parse(s: String?): SkRgba? {
            val h = s?.trim()?.removePrefix("#") ?: return null
            if (h.length != 8 && h.length != 6) return null
            val v = h.toLongOrNull(16) ?: return null
            val rgb = SkRgba(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
            return if (h.length == 8) rgb.copy(a = ((v shr 24) and 0xFF).toInt()) else rgb
        }
    }
}

/** Black — the house background. */
private val BLACK = SkRgba(0, 0, 0)

/** `#FFFF00` — the house yellow, the same one the launcher icon is traced in. */
private val YELLOW = SkRgba(255, 255, 0)

/** White. Used by exactly one slot, and deliberately. */
private val WHITE = SkRgba(255, 255, 255)

/** The one red on the page: an unset backup folder, and any failure. */
val SkWarnRed = SkRgba(255, 82, 82)

/**
 * The sections the colour rows are grouped under on the UI page. The page draws one [GroupLabel]
 * per entry, in this order, and the rows under it are indented a further level — so which group a
 * colour belongs to is readable without reading the row.
 */
enum class ColorGroup(
    val label: String,
) {
    SURFACES("Surfaces"),
    TEXT("Text"),
    ACCENT("Accent"),
    LINES("Lines & dividers"),
    PLAYER("Player"),
    SEMANTIC("Semantic"),
    CAR("Android Auto"),
}

/**
 * Every chrome colour the page exposes, with the black-yellow value it starts at and a one-line
 * description of what it actually paints — the page shows that under the row label, so a slot is
 * chosen by what it does rather than guessed from its name.
 *
 * The house look is the **default**, not an option layered over one: each entry carries a concrete
 * black-yellow value, which is what makes the whole look editable *and* resettable from the page.
 */
enum class ColorSlot(
    val id: String,
    val group: ColorGroup,
    val label: String,
    val about: String,
    val default: SkRgba,
) {
    // ---- surfaces --------------------------------------------------------------------------------
    BG("bg", ColorGroup.SURFACES, "Background", "The window behind everything", BLACK),
    SURFACE("surface", ColorGroup.SURFACES, "Surface", "Cards, sheets and raised panels", BLACK),
    SURFACE_HI("surface_hi", ColorGroup.SURFACES, "Raised surface", "Pressed rows, search field, chips", SkRgba(20, 20, 20)),
    MENU_BG("menu_bg", ColorGroup.SURFACES, "Menu & dialog", "Popups, dialogs and bottom sheets", BLACK),
    NAV_BG("nav_bg", ColorGroup.SURFACES, "Navigation bar", "Behind the bottom tabs and the mini player", BLACK),
    PLAYER_BG("player_bg", ColorGroup.SURFACES, "Player background", "The now-playing backdrop", BLACK),

    // ---- text ------------------------------------------------------------------------------------
    TEXT("text", ColorGroup.TEXT, "Text", "Primary labels and titles", YELLOW),
    TEXT_DIM("text_dim", ColorGroup.TEXT, "Dim text", "Artist lines, helper and hint text", SkRgba(190, 190, 0)),
    TEXT_DISABLED("text_disabled", ColorGroup.TEXT, "Disabled text", "Rows and controls that cannot be used", SkRgba(110, 110, 0)),

    // ---- accent ----------------------------------------------------------------------------------
    ACCENT("accent", ColorGroup.ACCENT, "Accent", "Active controls, highlights, selection", YELLOW),
    ON_ACCENT("on_accent", ColorGroup.ACCENT, "On accent", "Text and icons drawn on the accent", BLACK),
    SELECTION("selection", ColorGroup.ACCENT, "Selected item", "The tint behind a selected row or tab", SkRgba(255, 255, 0, 51)),

    // ---- lines -----------------------------------------------------------------------------------
    BORDER("border", ColorGroup.LINES, "Border", "Control outlines and dialog frames", YELLOW),
    DIVIDER("divider", ColorGroup.LINES, "Divider", "Thin separators between rows and sections", SkRgba(255, 255, 0, 102)),

    // ---- player ----------------------------------------------------------------------------------
    PROGRESS("progress", ColorGroup.PLAYER, "Played", "The elapsed part of a seek bar", YELLOW),
    PROGRESS_TRACK("progress_track", ColorGroup.PLAYER, "Remaining", "The part of the track not played yet", SkRgba(255, 255, 0, 77)),
    SLIDER_THUMB("slider_thumb", ColorGroup.PLAYER, "Slider handle", "The grab handle on seek and volume", YELLOW),

    // ---- semantic --------------------------------------------------------------------------------
    FAVOURITE("favourite", ColorGroup.SEMANTIC, "Favourite", "The like heart when it is filled", YELLOW),
    // White on purpose: the lyrics themselves are the house yellow, so the line being sung is
    // marked by going white rather than by the rest going faint (白い熊, 2026-08-16).
    LYRIC_ACTIVE("lyric_active", ColorGroup.SEMANTIC, "Active lyric", "The line currently being sung", WHITE),
    WARN("warn", ColorGroup.SEMANTIC, "Warning", "Failures, and an unset backup folder", SkWarnRed),
    SHIMMER_BG("shimmer_bg", ColorGroup.SEMANTIC, "Placeholder", "The block shown while content loads", SkRgba(26, 26, 26)),
    SHIMMER_LINE("shimmer_line", ColorGroup.SEMANTIC, "Placeholder sheen", "The sweep across a loading block", SkRgba(46, 46, 46)),

    // ---- Android Auto ----------------------------------------------------------------------------
    CAR_PRIMARY("car_primary", ColorGroup.CAR, "Auto accent", "Primary colour the car host is asked for", YELLOW),
    CAR_SECONDARY("car_secondary", ColorGroup.CAR, "Auto secondary", "Secondary colour the car host is asked for", SkRgba(190, 190, 0)),
    ;

    companion object {
        fun byId(id: String): ColorSlot? = entries.firstOrNull { it.id == id }

        fun of(group: ColorGroup): List<ColorSlot> = entries.filter { it.group == group }
    }
}

/**
 * The 白い熊 音楽乙 UI theme — every chrome attribute the UI page exposes, in one settable,
 * persisted, exportable block.
 *
 * Colours live in a map keyed by [ColorSlot.id] rather than as one field per slot: a slot that has
 * never been touched simply isn't in the map and falls back to its black-yellow default, so adding a
 * slot later needs no migration and an exported theme stays readable.
 *
 * [enabled] is the one escape hatch — off, the app falls back to upstream's seed-derived Material
 * chrome, and every edit made here is kept, so the fork's look can be compared against stock without
 * losing the work.
 */
@Serializable
@Immutable
data class OngakuUi(
    val enabled: Boolean = true,
    val colors: Map<String, SkRgba> = emptyMap(),
    /** Id of the chosen font in the font store, or "" for the app's own bundled Poppins. */
    val fontId: String = "",
    /** Percentage applied to every text style, so the whole app scales together. */
    val fontScalePct: Int = 100,
    /** 100..900 in the usual hundreds; titles take this plus 200, clamped. */
    val fontWeight: Int = 400,
    /** Corner radius in dp. 0 is fully square. */
    val cornerRadiusDp: Int = 8,
    /** Border thickness in dp. 0 removes borders entirely. */
    val borderWidthDp: Int = 1,
    /** Icon edge length in dp. */
    val iconSizeDp: Int = 24,
    /** Vertical padding inside a settings row in dp — the UI page's own density control. */
    val rowPaddingDp: Int = 2,
    /** Colours picked before, newest first — the one-click boxes above the RGBA sliders. */
    val recent: List<SkRgba> = emptyList(),
) {
    fun color(slot: ColorSlot): SkRgba = colors[slot.id] ?: slot.default

    fun c(slot: ColorSlot): Color = color(slot).toColor()

    fun withColor(
        slot: ColorSlot,
        value: SkRgba,
    ): OngakuUi =
        copy(
            colors = colors + (slot.id to value),
            recent = (listOf(value) + recent).distinct().take(24),
        )

    /** Back to the black-yellow default for one slot, keeping every other edit. */
    fun resetColor(slot: ColorSlot): OngakuUi = copy(colors = colors - slot.id)

    val titleWeight: Int get() = (fontWeight + 200).coerceIn(100, 900)

    /**
     * Our chrome, expressed as a Material 3 [ColorScheme] so that every stock SimpMusic composable —
     * which reads `MaterialTheme.colorScheme` and nothing of ours — is restyled without being
     * touched. [base] is what the app would otherwise have rendered; the fields we have no opinion
     * about (error, scrim, inverse tiers) are inherited from it.
     */
    fun applyTo(base: ColorScheme): ColorScheme {
        if (!enabled) return base
        val bg = c(ColorSlot.BG)
        val text = c(ColorSlot.TEXT)
        val dim = c(ColorSlot.TEXT_DIM)
        val accent = c(ColorSlot.ACCENT)
        val onAccent = c(ColorSlot.ON_ACCENT)
        val surface = c(ColorSlot.SURFACE)
        val surfaceHi = c(ColorSlot.SURFACE_HI)
        val border = c(ColorSlot.BORDER)
        val divider = c(ColorSlot.DIVIDER)
        return base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = surfaceHi,
            onPrimaryContainer = text,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = surfaceHi,
            onSecondaryContainer = text,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = surfaceHi,
            onTertiaryContainer = text,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceHi,
            onSurfaceVariant = dim,
            // Material lightens an elevated surface by blending `surfaceTint` into it. With the
            // accent there, every card, sheet and app bar drifted off black towards the tint — the
            // grey that survived the first pass. Pinning it to the surface makes the overlay a
            // no-op, so a raised surface is raised by its border, not by turning grey.
            surfaceTint = surface,
            surfaceBright = surfaceHi,
            surfaceDim = bg,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            // Material 3 draws an AlertDialog's container from surfaceContainerHigh, so this is
            // what makes every dialog in the app black rather than a raised grey.
            surfaceContainerHigh = c(ColorSlot.MENU_BG),
            surfaceContainerHighest = surfaceHi,
            outline = border,
            outlineVariant = divider,
        )
    }

    companion object {
        /** Lenient on purpose: an archive written by an older build must still restore. */
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(s: String?): OngakuUi {
            if (s.isNullOrBlank()) return OngakuUi()
            return runCatching { json.decodeFromString<OngakuUi>(s) }.getOrElse { OngakuUi() }
        }

        fun toJson(ui: OngakuUi): String = json.encodeToString(ui)

        /** The DataStore key the whole theme is persisted under, and the backup's `ui` category. */
        const val KEY = "shiroikuma_ongakuots_ui"
    }
}

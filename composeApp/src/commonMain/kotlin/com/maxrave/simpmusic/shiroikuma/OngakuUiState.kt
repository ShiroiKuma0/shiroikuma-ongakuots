package com.maxrave.simpmusic.shiroikuma

import androidx.compose.runtime.staticCompositionLocalOf
import com.maxrave.domain.manager.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The live 白い熊 音楽乙 theme.
 *
 * One Koin singleton owns it, so the page that edits it and the whole app that renders from it read
 * the same [StateFlow]. Writes land in the in-memory value **first** and are persisted after — which
 * is what makes every control on the page preview itself: moving a slider repaints the app on the
 * next frame rather than after a DataStore round-trip.
 *
 * Persistence is one JSON string under [OngakuUi.KEY] in the app's existing DataStore. That keeps
 * the whole theme in a single key: nothing to migrate when a slot is added, and the backup's `ui`
 * category is simply that string.
 */
class OngakuUiState(
    private val dataStore: DataStoreManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _ui = MutableStateFlow(OngakuUi())
    val ui: StateFlow<OngakuUi> = _ui.asStateFlow()

    init {
        scope.launch {
            dataStore.getString(OngakuUi.KEY).collect { raw ->
                val loaded = OngakuUi.fromJson(raw).sane()
                // Ignore the echo of our own write; anything else (a restore, another process) wins.
                if (loaded != _ui.value) _ui.value = loaded
            }
        }
    }

    fun update(next: OngakuUi) {
        val sane = next.sane()
        _ui.value = sane
        scope.launch {
            dataStore.putString(OngakuUi.KEY, OngakuUi.toJson(sane))
            // The two Android Auto slots are mirrored into plain `#AARRGGBB` keys as well. The car
            // app lives in :media3, which has no business parsing a Compose theme — one string each
            // way is the whole contract. See media3's carapp/SkCarColors.kt.
            dataStore.putString(KEY_CAR_PRIMARY, sane.color(ColorSlot.CAR_PRIMARY).hex())
            dataStore.putString(KEY_CAR_SECONDARY, sane.color(ColorSlot.CAR_SECONDARY).hex())
        }
    }

    fun edit(block: (OngakuUi) -> OngakuUi) = update(block(_ui.value))

    /** The whole theme as it is stored — what the backup's `ui` category holds. */
    fun exportJson(): String = OngakuUi.toJson(_ui.value)

    /** Restore from a backup. Keeps the recent-colour list, which is device-local scratch. */
    fun importJson(raw: String) {
        val incoming = OngakuUi.fromJson(raw).sane()
        update(incoming.copy(recent = (_ui.value.recent + incoming.recent).distinct().take(24)))
    }

    /** Back to stock black-yellow, every slot and every metric. Recent colours survive. */
    fun resetAll() = update(OngakuUi(recent = _ui.value.recent))

    companion object {
        /** Must match `SkCarColors.KEY_PRIMARY` / `KEY_SECONDARY` in `:media3`. */
        const val KEY_CAR_PRIMARY = "shiroikuma_car_primary"
        const val KEY_CAR_SECONDARY = "shiroikuma_car_secondary"
    }
}

/**
 * Clamp everything a hand-edited or older archive could carry out of range. Colours need no
 * clamping — [SkRgba.toColor] coerces each channel as it converts.
 */
fun OngakuUi.sane(): OngakuUi =
    copy(
        fontScalePct = fontScalePct.coerceIn(60, 200),
        fontWeight = (fontWeight / 100).coerceIn(1, 9) * 100,
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 32),
        borderWidthDp = borderWidthDp.coerceIn(0, 8),
        iconSizeDp = iconSizeDp.coerceIn(12, 44),
        rowPaddingDp = rowPaddingDp.coerceIn(0, 20),
        colors = colors.filterKeys { ColorSlot.byId(it) != null },
        recent = recent.take(24),
    )

/**
 * The theme, for everything that draws with it. Defaulted rather than nullable so a preview or a
 * composable reached outside `AppTheme` still renders in the house look instead of crashing.
 */
val LocalOngakuUi = staticCompositionLocalOf { OngakuUi() }

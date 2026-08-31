package com.maxrave.simpmusic.shiroikuma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.maxrave.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.map
import org.koin.mp.KoinPlatform.getKoin

/**
 * The fork theme, for surfaces that render OUTSIDE the app's own composition.
 *
 * A Glance widget runs on the launcher's process boundary: it has a composition of its own, but not
 * ours, so [LocalOngakuUi] — which `AppTheme` provides — is simply absent there. `SkCarColors` in
 * `:media3` has the same problem and solves it by mirroring two slots into plain DataStore keys;
 * a widget needs the whole palette, so it reads the one JSON blob the UI page already persists
 * under [OngakuUi.KEY] instead of growing a mirror key per slot.
 *
 * Returns [OngakuUi] defaults until the first read lands, which is the same value a fresh install
 * has, so a widget never flashes a half-applied theme.
 */
@Composable
fun rememberOngakuUiForWidget(): State<OngakuUi> {
    val store = remember { runCatching { getKoin().get<DataStoreManager>() }.getOrNull() }
    val flow =
        remember(store) {
            store?.getString(OngakuUi.KEY)?.map { OngakuUi.fromJson(it) }
        }
    return flow?.collectAsState(initial = OngakuUi.fromJson(null))
        ?: remember { androidx.compose.runtime.mutableStateOf(OngakuUi.fromJson(null)) }
}

/**
 * The widget's content colour — the fork's TEXT slot, or upstream's white with the theme off.
 *
 * A function rather than a hoisted value because a widget's leaf composables are top-level private
 * functions taking no theme parameter, and threading one through every signature would be a bigger
 * change to upstream's code than the colour itself is worth.
 */
@Composable
fun skWidgetOn(): androidx.compose.ui.graphics.Color {
    val ui by rememberOngakuUiForWidget()
    return if (ui.enabled) ui.c(ColorSlot.TEXT) else androidx.compose.ui.graphics.Color.White
}

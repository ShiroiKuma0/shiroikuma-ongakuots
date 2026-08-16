package com.maxrave.simpmusic.shiroikuma

import org.koin.dsl.module

/**
 * The fork's own Koin module.
 *
 * Kept separate from `viewModelModule` on purpose: that one is unloaded and reloaded on every
 * `MainActivity.onCreate`, and [OngakuUiState] must outlive an activity recreation — it owns the
 * DataStore collector the whole app's chrome is rendered from, and rebuilding it on every rotation
 * would flash the stock theme while the theme is re-read.
 */
val shiroikumaModule =
    module {
        single { OngakuUiState(get()) }
    }

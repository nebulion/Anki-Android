// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.ichi2.compose.theme.AnkiDroidColors
import com.ichi2.compose.theme.Dimensions
import com.ichi2.compose.theme.LocalAnkiDroidColors
import com.ichi2.compose.theme.LocalDimensions
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.eInkColorScheme

/**
 * Wraps [content] in Mudita's [ThemeMMD]: black and white only, Lato typography, ripples disabled.
 *
 * Every MMD screen in the fork is wrapped in this, never in a bare `ThemeMMD`, so the gaps in
 * MMD 1.0.2 are fixed in exactly one place. [tokens] selects the measurement profile; see
 * [MmdTokens] and `docs/mmd/eink-design.md`.
 */
@Composable
fun MmdTheme(
    tokens: MmdTokens = MmdTokens.Library,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMmdTokens provides tokens,
        LocalDimensions provides Dimensions(),
        LocalAnkiDroidColors provides AnkiDroidColors(fabContainer = Color.Black, onFab = Color.White),
    ) {
        ThemeMMD(colorScheme = mmdColorScheme, content = content)
    }
}

/**
 * [eInkColorScheme] with the six roles MMD 1.0.2 leaves [Color.Unspecified] filled with white.
 *
 * Unfilled, `TextFieldMMD`, `TimeInputMMD`, `CardMMD` and `SearchBarMMD` draw no container at all,
 * and a scrolled `TopAppBarMMD` loses its background.
 */
val mmdColorScheme: ColorScheme =
    eInkColorScheme.copy(
        surfaceBright = Color.White,
        surfaceDim = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color.White,
        surfaceContainerLowest = Color.White,
    )

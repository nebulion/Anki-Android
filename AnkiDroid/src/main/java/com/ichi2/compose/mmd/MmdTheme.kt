// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.ichi2.compose.theme.AnkiDroidColors
import com.ichi2.compose.theme.Dimensions
import com.ichi2.compose.theme.LocalAnkiDroidColors
import com.ichi2.compose.theme.LocalDimensions
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.eInkColorScheme
import com.mudita.mmd.eInkTypography

/**
 * Wraps [content] in Mudita's [ThemeMMD]: black and white only, MMD's type scale in the phone's
 * system font, ripples disabled.
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
        ThemeMMD(colorScheme = mmdColorScheme, typography = mmdTypography, content = content)
    }
}

/**
 * [eInkTypography]'s sizes, weights and line heights in the system font instead of the Lato that
 * MMD bundles: the owner wants the phone's own font wherever there is a choice.
 */
val mmdTypography: Typography =
    with(eInkTypography) {
        fun TextStyle.inSystemFont() = copy(fontFamily = FontFamily.Default)
        Typography(
            displayLarge = displayLarge.inSystemFont(),
            displayMedium = displayMedium.inSystemFont(),
            displaySmall = displaySmall.inSystemFont(),
            headlineLarge = headlineLarge.inSystemFont(),
            headlineMedium = headlineMedium.inSystemFont(),
            headlineSmall = headlineSmall.inSystemFont(),
            titleLarge = titleLarge.inSystemFont(),
            titleMedium = titleMedium.inSystemFont(),
            titleSmall = titleSmall.inSystemFont(),
            bodyLarge = bodyLarge.inSystemFont(),
            bodyMedium = bodyMedium.inSystemFont(),
            bodySmall = bodySmall.inSystemFont(),
            labelLarge = labelLarge.inSystemFont(),
            labelMedium = labelMedium.inSystemFont(),
            labelSmall = labelSmall.inSystemFont(),
        )
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

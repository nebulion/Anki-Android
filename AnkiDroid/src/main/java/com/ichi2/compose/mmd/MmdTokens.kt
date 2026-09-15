// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every measurement where the MMD library and Mudita's own Kompakt apps disagree, in one place.
 *
 * Kit components never hard-code these; they read [LocalMmdTokens]. Swapping the whole app between
 * profiles is one argument: `MmdTheme(tokens = MmdTokens.KompaktSystem) { … }`.
 *
 * Values that both sources agree on (black/white, Lato, 3dp header rule) are not tokens.
 */
@Immutable
data class MmdTokens(
    /** Minimum height of a list or sheet row. */
    val rowMinHeight: Dp,
    /** Horizontal padding at the screen edge. */
    val edgePadding: Dp,
    /** Where a row label starts when the row has a leading icon. */
    val labelInsetWithIcon: Dp,
    val leadingIconSize: Dp,
    /** Size of the trailing line chevron on drill-down rows. */
    val chevronSize: Dp,
    /** Dotted row divider: dot length and gap. Snapped to whole pixels when drawn. */
    val dividerDash: Dp,
    val dividerGap: Dp,
    /** Header actions: touch target and glyph. */
    val headerActionTouchTarget: Dp,
    val headerActionGlyph: Dp,
    /** Centred panels (P5). */
    val panelCornerRadius: Dp,
    val panelBorder: Dp,
) {
    companion object {
        /**
         * Derived from `mudita/MMD` source wherever it specifies a value: 56dp rows (the
         * `RadioButtonMMD` usage sample), 8dp corners (`ButtonDefaultsMMD`, `CardDefaultsMMD`),
         * 3dp borders (`DividerDefaultsMMD.Thickness`). Where MMD is silent (row dividers, chevrons)
         * it takes the Kompakt pattern. Denser than the system apps, so more decks fit on a 601dp
         * screen. **The default.**
         */
        val Library =
            MmdTokens(
                rowMinHeight = 56.dp,
                edgePadding = 16.dp,
                labelInsetWithIcon = 56.dp,
                leadingIconSize = 24.dp,
                chevronSize = 24.dp,
                dividerDash = 2.dp,
                dividerGap = 2.dp,
                headerActionTouchTarget = 48.dp,
                headerActionGlyph = 32.dp,
                panelCornerRadius = 8.dp,
                panelBorder = 3.dp,
            )

        /**
         * Measured on the Kompakt's own apps (MuditaOS K 1.6.0): Settings rows are 85px (64dp) with
         * the label at 64dp and a ≈16dp-tall chevron; Mudita Chess panels use 16dp corners.
         * See `docs/mmd/eink-design.md` → Calibration.
         */
        val KompaktSystem =
            Library.copy(
                rowMinHeight = 64.dp,
                labelInsetWithIcon = 64.dp,
                chevronSize = 32.dp,
                panelCornerRadius = 16.dp,
            )
    }
}

/** The token profile in effect. Provided by [MmdTheme]; defaults to [MmdTokens.Library]. */
val LocalMmdTokens = staticCompositionLocalOf { MmdTokens.Library }

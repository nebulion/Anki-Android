// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How a confirmation (pattern P5) is presented. */
enum class ConfirmStyle {
    /** 3dp top rule, title, body and full-width stacked buttons, anchored to the bottom. Kompakt Notes. */
    BottomPanel,

    /** A bordered panel in the middle of the screen, buttons side by side. Mudita Chess info dialogs. */
    CentredPanel,
}

/**
 * Every measurement where the MMD library and Mudita's own Kompakt apps disagree, in one place.
 *
 * Kit components never hard-code these; they read [LocalMmdTokens]. Swapping the whole app between
 * profiles is one argument: `MmdTheme(tokens = MmdTokens.KompaktSystem) { … }`.
 *
 * Values that both sources agree on (black/white, 3dp header rule, no dim) are not tokens.
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
    /** Solid and outlined buttons in panels. */
    val buttonHeight: Dp,
    val buttonCornerRadius: Dp,
    /** Panels: outer margin and spacing between stacked buttons. */
    val panelMargin: Dp,
    val panelCornerRadius: Dp,
    val panelBorder: Dp,
    val confirmStyle: ConfirmStyle,
) {
    companion object {
        /**
         * Derived from `mudita/MMD` source wherever it specifies a value: 56dp rows (the
         * `RadioButtonMMD` usage sample), 8dp corners (`ButtonDefaultsMMD`, `CardDefaultsMMD`),
         * 3dp borders (`DividerDefaultsMMD.Thickness`). Where MMD is silent (row dividers, chevrons,
         * confirmations) it takes the Kompakt pattern. Buttons are 48dp rather than MMD's 32dp
         * minimum, because a mistap on E Ink costs a full repaint. Denser than the system apps, so
         * more decks fit on a 601dp screen. **The default.**
         */
        val Library =
            MmdTokens(
                rowMinHeight = 56.dp,
                edgePadding = 16.dp,
                labelInsetWithIcon = 60.dp,
                leadingIconSize = 28.dp,
                chevronSize = 28.dp,
                dividerDash = 2.dp,
                dividerGap = 2.dp,
                headerActionTouchTarget = 48.dp,
                headerActionGlyph = 28.dp,
                buttonHeight = 48.dp,
                buttonCornerRadius = 8.dp,
                panelMargin = 16.dp,
                panelCornerRadius = 8.dp,
                panelBorder = 3.dp,
                confirmStyle = ConfirmStyle.BottomPanel,
            )

        /**
         * Measured on the Kompakt's own apps (MuditaOS K 1.6.0): Settings rows 85px (64dp) with the
         * label at 64dp and a ≈16dp-tall chevron; Notes header glyphs ≈28dp; the Notes delete
         * confirmation's buttons 75px (56dp) tall with ≈9dp corners and 12dp margins; Mudita Chess
         * panels 16dp corners. See `docs/mmd/eink-design.md` → Calibration.
         */
        val KompaktSystem =
            Library.copy(
                rowMinHeight = 64.dp,
                labelInsetWithIcon = 64.dp,
                chevronSize = 32.dp,
                headerActionGlyph = 28.dp,
                buttonHeight = 56.dp,
                buttonCornerRadius = 9.dp,
                panelMargin = 12.dp,
                panelCornerRadius = 16.dp,
            )
    }
}

/** The token profile in effect. Provided by [MmdTheme]; defaults to [MmdTokens.Library]. */
val LocalMmdTokens = staticCompositionLocalOf { MmdTokens.Library }

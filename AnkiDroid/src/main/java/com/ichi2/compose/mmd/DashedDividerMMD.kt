// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The dotted hairline Mudita draws between list rows and between menu rows.
 *
 * MMD's own `DropdownMenuMMD` KDoc separates menu items with `DashedDivider()`, which MMD 1.0.2
 * does not ship, so it lives here under MMD's name.
 *
 * Measured on the Kompakt's Settings list: a 1px line of ~2.5px dots with ~2.5px gaps, pure
 * black on white, starting at the row label. Everything is snapped to whole device pixels so the
 * panel is never handed an anti-aliased grey edge.
 *
 * The 3dp solid rule under headers stays `HorizontalDividerMMD`; this is only for rows.
 */
@Composable
fun DashedDividerMMD(
    modifier: Modifier = Modifier,
    dash: Dp = DashedDividerDefaults.Dash,
    gap: Dp = DashedDividerDefaults.Gap,
    color: Color = MaterialTheme.colorScheme.outline,
) {
    // one device pixel tall, whatever the density
    val onePixel = with(LocalDensity.current) { (1 / density).dp }
    Canvas(modifier.fillMaxWidth().height(onePixel)) {
        val dashPx =
            dash
                .toPx()
                .roundToInt()
                .coerceAtLeast(1)
                .toFloat()
        val gapPx =
            gap
                .toPx()
                .roundToInt()
                .coerceAtLeast(1)
                .toFloat()
        // centre of the pixel row, so a 1px stroke covers exactly one row of pixels
        val y = floor(size.height / 2) + 0.5f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
            cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx)),
        )
    }
}

/** From the active [MmdTokens] profile; calibrated against Kompakt Settings (~2.5px dots and gaps). */
object DashedDividerDefaults {
    val Dash: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.dividerDash

    val Gap: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.dividerGap
}

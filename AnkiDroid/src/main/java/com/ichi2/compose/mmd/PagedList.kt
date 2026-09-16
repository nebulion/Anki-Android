// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD

/**
 * A list that pages instead of scrolling, with MMD's permanent scrollbar.
 *
 * Read `LazyMMD.kt` before changing how this is used:
 * - `LazyColumnMMD` pages **by item** (`scrollToItem` in steps of [scrollStep]). Emit one item per
 *   row; a single tall item cannot be scrolled at all, and hidden items still count.
 * - The list is drawn at `alpha = 0` until its items are counted.
 * - MMD composes the scrollbar only while the list can scroll. While it cannot, an inactive one is
 *   drawn in its place, so the bar is always there and rows never shift when the list grows.
 */
@Composable
fun PagedList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    scrollStep: Int = PagedListDefaults.SCROLL_STEP,
    isScrollbarVisible: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val canScroll by remember(state) { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    Row(modifier) {
        LazyColumnMMD(
            modifier = Modifier.weight(1f),
            state = state,
            scrollStep = scrollStep,
            isScrollbarVisible = isScrollbarVisible,
            content = content,
        )
        if (isScrollbarVisible && !canScroll) {
            InactiveScrollbar()
        }
    }
}

/**
 * MMD's scrollbar with nothing to scroll: the same column, track and arrows, but the track is empty
 * and nothing reacts to a tap. Drawn here because MMD composes its own only while a list can scroll.
 */
@Composable
private fun InactiveScrollbar() {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.width(PagedListDefaults.ScrollbarGutter).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScrollArrow(pointingUp = true, color = ink)
        Box(
            Modifier
                .width(PagedListDefaults.ScrollbarTrackWidth)
                .weight(1f)
                .border(
                    width = PagedListDefaults.ScrollbarTrackBorder,
                    color = ink,
                    shape = RoundedCornerShape(PagedListDefaults.ScrollbarTrackWidth / 2),
                ),
        )
        ScrollArrow(pointingUp = false, color = ink)
    }
}

/** One of the scrollbar's page arrows: a filled triangle, the size MMD gives its own. */
@Composable
private fun ScrollArrow(
    pointingUp: Boolean,
    color: Color,
) {
    Canvas(Modifier.size(PagedListDefaults.ScrollbarArrowSize)) {
        val halfWidth = size.width * 0.3f
        val halfHeight = size.height * 0.18f
        val centreX = size.width / 2
        val centreY = size.height / 2
        val tip = if (pointingUp) centreY - halfHeight else centreY + halfHeight
        val base = if (pointingUp) centreY + halfHeight else centreY - halfHeight
        val triangle =
            Path().apply {
                moveTo(centreX, tip)
                lineTo(centreX + halfWidth, base)
                lineTo(centreX - halfWidth, base)
                close()
            }
        drawPath(triangle, color)
    }
}

object PagedListDefaults {
    /** Items per page turn. MMD's default of 4 made the Loop fork's Settings feel slow; 6 did not. */
    const val SCROLL_STEP = 6

    /**
     * Width of MMD's scrollbar column: its 24dp page arrows (`LazyDefaultsMMD.navigateIconSize`)
     * with 8dp padding either side (`LazyMMD.kt`). Matches the Kompakt's Settings, whose rows end
     * 40dp from the edge (`docs/mmd/eink-design.md` → Calibration).
     */
    val ScrollbarGutter = 40.dp

    /** The track's width and border, from `LazyDefaultsMMD.sliderBackgroundWidth`/`...BorderWidth`. */
    val ScrollbarTrackWidth = 8.dp
    val ScrollbarTrackBorder = 1.dp

    /** The page arrows, from `LazyDefaultsMMD.navigateIconSize`. */
    val ScrollbarArrowSize = 24.dp
}

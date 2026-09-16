// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.lazy.LazyDefaultsMMD

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
 * MMD's scrollbar with nothing to scroll: the library's own track and arrows, sized and coloured
 * from `LazyDefaultsMMD`, with the empty track and the **dotted** arrows MMD draws for a control
 * that cannot be used. Drawn here because MMD composes its own scrollbar only while a list can
 * scroll, and a bar that comes and goes shifts every row beside it.
 */
@Composable
private fun InactiveScrollbar() {
    Column(
        // `LazyMMD.kt`'s own scrollbar column: 8dp either side of a 24dp arrow, so 40dp in all
        Modifier.fillMaxHeight().padding(horizontal = PagedListDefaults.ScrollbarSidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScrollArrow(pointingUp = true)
        Box(
            Modifier
                .width(PagedListDefaults.ScrollbarTrackWidth)
                .weight(1f)
                // MMD's own slider colours are internal; they are the theme's surface and its ink
                .background(MaterialTheme.colorScheme.surface, LazyDefaultsMMD.sliderBackgroundCorners)
                .border(
                    width = LazyDefaultsMMD.sliderBackgroundBorderWidth,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = LazyDefaultsMMD.sliderBackgroundCorners,
                ),
        )
        ScrollArrow(pointingUp = false)
    }
}

/** One of the scrollbar's page arrows, in MMD's dotted form: the shape it uses for "not now". */
@Composable
private fun ScrollArrow(pointingUp: Boolean) {
    Icon(
        painter =
            painterResource(
                if (pointingUp) {
                    com.mudita.mmd.R.drawable.chevron_dotted_up
                } else {
                    com.mudita.mmd.R.drawable.chevron_dotted_down
                },
            ),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier =
            Modifier
                .padding(vertical = PagedListDefaults.ScrollbarArrowPadding)
                .size(PagedListDefaults.ScrollbarArrowSize),
    )
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

    /** The gap either side of an arrow, and above and below it, in `LazyMMD.kt`'s own scrollbar. */
    val ScrollbarSidePadding = 8.dp
    val ScrollbarArrowPadding = 16.dp

    /**
     * The track's width, from `LazyDefaultsMMD.sliderBackgroundWidth`, which is internal to MMD.
     * Its border width, corners and colours are read from `LazyDefaultsMMD` itself.
     */
    val ScrollbarTrackWidth = 8.dp

    /** The page arrows, from `LazyDefaultsMMD.navigateIconSize`. */
    val ScrollbarArrowSize = 24.dp
}

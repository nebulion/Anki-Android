// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD

/**
 * A list that pages instead of scrolling, with MMD's permanent scrollbar.
 *
 * Read `LazyMMD.kt` before changing how this is used:
 * - `LazyColumnMMD` pages **by item** (`scrollToItem` in steps of [scrollStep]). Emit one item per
 *   row; a single tall item cannot be scrolled at all, and hidden items still count.
 * - The list is drawn at `alpha = 0` until its items are counted.
 * - MMD composes the scrollbar only while the list can scroll. While it cannot, this keeps the
 *   scrollbar's width empty instead, so rows do not shift left when the list grows past one page.
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
            Spacer(Modifier.width(PagedListDefaults.ScrollbarGutter))
        }
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
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mudita.mmd.components.lazy.LazyColumnMMD

/**
 * A list that pages instead of scrolling, with MMD's permanent scrollbar.
 *
 * Read `LazyMMD.kt` before changing how this is used:
 * - `LazyColumnMMD` pages **by item** (`scrollToItem` in steps of [scrollStep]). Emit one item per
 *   row; a single tall item cannot be scrolled at all, and hidden items still count.
 * - The list is drawn at `alpha = 0` until its items are counted.
 * - The scrollbar (~40dp gutter) is composed only while the list can scroll, so content beside
 *   the list must not assume a fixed width.
 */
@Composable
fun PagedList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    scrollStep: Int = PagedListDefaults.SCROLL_STEP,
    isScrollbarVisible: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyColumnMMD(
        modifier = modifier,
        state = state,
        scrollStep = scrollStep,
        isScrollbarVisible = isScrollbarVisible,
        content = content,
    )
}

object PagedListDefaults {
    /** Items per page turn. MMD's default of 4 made the Loop fork's Settings feel slow; 6 did not. */
    const val SCROLL_STEP = 6
}

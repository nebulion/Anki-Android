// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts web content (the flashcard, deck options, statistics, card info) inside an MMD screen.
 *
 * HTML cannot page like `LazyColumnMMD`, so web content scrolls continuously (exception E1), but
 * the E Ink hazards are removed: no overscroll glow or stretch, and scrollbars do not fade in
 * and out. Page-level CSS (`assets/mmd-*.css`) handles colour and `scroll-behavior`.
 */
@Composable
fun <T : View> WebContent(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {},
) {
    AndroidView(
        factory = { context -> factory(context).also { it.applyEinkScrolling() } },
        modifier = modifier,
        update = update,
    )
}

/** Applies [applyEinkScrolling] to a view and, for a wrapper layout, to the WebViews inside it. */
fun View.applyEinkScrolling() {
    overScrollMode = View.OVER_SCROLL_NEVER
    isScrollbarFadingEnabled = false
    isVerticalFadingEdgeEnabled = false
    isHorizontalFadingEdgeEnabled = false
    if (this is ViewGroup && this !is WebView) {
        for (i in 0 until childCount) getChildAt(i).applyEinkScrolling()
    }
}

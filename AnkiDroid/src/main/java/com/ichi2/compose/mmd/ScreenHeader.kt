// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

/**
 * Every MMD screen's header: a bold title, actions that are always visible, and the heavy 3dp rule.
 *
 * `TopAppBarMMD(showDivider = true)` draws a 1dp line in MMD 1.0.2 (the 3dp height is applied as
 * a width), so the divider is turned off and drawn here with [HorizontalDividerMMD].
 *
 * There is no overflow menu: an in-screen menu is a header action opening a `MenuPanel` (P3).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    ScreenHeader(
        title = {
            TextMMD(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/** [ScreenHeader] with a composed title, e.g. the study screen's counts with one of them bold. */
@Composable
fun ScreenHeader(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier) {
        TopAppBarMMD(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            showDivider = false,
        )
        HorizontalDividerMMD()
    }
}

/**
 * A header icon: a 48dp touch target around a 32dp black glyph from the app's own drawables.
 *
 * MMD ships no icon set and the project does not use `material-icons`.
 */
@Composable
fun HeaderAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(HeaderActionDefaults.TouchTarget),
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(HeaderActionDefaults.Glyph),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

object HeaderActionDefaults {
    val TouchTarget: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.headerActionTouchTarget

    val Glyph: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.headerActionGlyph
}

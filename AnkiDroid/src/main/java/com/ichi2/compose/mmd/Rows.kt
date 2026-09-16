// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * A row that drills down to another page, marked with a thin line chevron.
 *
 * Kompakt Settings uses a line `>` (≈8×16dp glyph, ≈2dp stroke), not MMD's filled triangle, which
 * is reserved for the scrollbar arrows.
 */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
) {
    RowScaffold(
        modifier = modifier.clickable(onClick = onClick),
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_chevron_right_24),
            contentDescription = null,
            modifier = Modifier.size(RowDefaults.ChevronSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A boolean setting. The whole row toggles; the switch itself is inert so there is one touch
 * target. `SwitchMMD` shows state by inversion: black track and white thumb when on.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    RowScaffold(
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
    ) {
        SwitchMMD(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A setting and its current value; tapping opens a `ChoiceSheet` or a panel.
 *
 * Kompakt Settings writes the value **under** the title rather than beside it, with the title bold
 * and a chevron at the right (its "Screen Timeout / After 5 min of inactivity"). A long value then
 * has the width of the row, which a right-aligned one does not.
 */
@Composable
fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    RowScaffold(
        modifier = modifier.clickable(onClick = onClick),
        title = title,
        subtitle = value,
        leadingIcon = leadingIcon,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_chevron_right_24),
            contentDescription = null,
            modifier = Modifier.size(RowDefaults.ChevronSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A row that performs an action directly, e.g. "Check database". */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
) {
    RowScaffold(
        modifier = modifier.clickable(onClick = onClick),
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
    ) {}
}

/** A bold heading above a group of rows. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 12.dp),
    )
}

/**
 * The solid 2dp rule between groups of rows: after a top-level deck and its subdecks, or between the
 * groups of the settings. Inside a group, rows are separated by the dotted [RowDivider].
 *
 * Kompakt Settings has no text headings on its root page, so a group is marked by this line alone.
 */
@Composable
fun GroupDivider() {
    Box(Modifier.fillMaxWidth().height(GroupDividerThickness).background(MaterialTheme.colorScheme.onSurface))
}

private val GroupDividerThickness = 2.dp

/**
 * The dotted row divider, inset to where the label starts, as in Kompakt Settings.
 * Pass whether the rows above use a leading icon.
 */
@Composable
fun RowDivider(hasLeadingIcon: Boolean = false) {
    DashedDividerMMD(
        Modifier.padding(
            start = if (hasLeadingIcon) RowDefaults.LabelInsetWithIcon else RowDefaults.EdgePadding,
            end = RowDefaults.EdgePadding,
        ),
    )
}

/** Subtitles exist, but in regular weight and black — never small grey text. */
@Composable
private fun RowScaffold(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    @DrawableRes leadingIcon: Int?,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = RowDefaults.MinHeight)
                .padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.width(RowDefaults.LabelInsetWithIcon - RowDefaults.EdgePadding)) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    modifier = Modifier.size(RowDefaults.LeadingIconSize),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            TextMMD(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (subtitle != null) FontWeight.Bold else FontWeight.Normal,
            )
            if (subtitle != null) {
                TextMMD(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
        trailing()
    }
}

/** Row measurements from the active [MmdTokens] profile. */
object RowDefaults {
    val MinHeight: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.rowMinHeight

    val EdgePadding: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.edgePadding

    val LabelInsetWithIcon: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.labelInsetWithIcon

    val LeadingIconSize: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.leadingIconSize

    val ChevronSize: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.chevronSize
}

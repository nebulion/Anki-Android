// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ActionRow
import com.ichi2.compose.mmd.GroupDivider
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.NavRow
import com.ichi2.compose.mmd.PageLoading
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.ValueRow

/** A row of a settings page. */
sealed interface SettingsEntry {
    /** A heading above the rows that follow, for a page whose groups need a name. */
    data class Section(
        val title: String,
    ) : SettingsEntry

    /** The solid rule that starts a new group of rows, as on the Kompakt's own Settings root. */
    data object Group : SettingsEntry

    /** Does something straight away, e.g. "Check database". */
    data class Action(
        val title: String,
        val subtitle: String? = null,
        @DrawableRes val icon: Int? = null,
        val onClick: () -> Unit,
    ) : SettingsEntry

    /** Opens another page. */
    data class Page(
        val title: String,
        val subtitle: String? = null,
        @DrawableRes val icon: Int? = null,
        val onClick: () -> Unit,
    ) : SettingsEntry

    /** A setting that is on or off. */
    data class Switch(
        val title: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val subtitle: String? = null,
    ) : SettingsEntry

    /** A setting and its value, written under it; tapping opens a sheet or a panel. */
    data class Value(
        val title: String,
        val value: String,
        val onClick: () -> Unit,
    ) : SettingsEntry
}

/** Whether this entry is a row of its own, rather than a heading or a group rule. */
private val SettingsEntry.isRow: Boolean
    get() = this !is SettingsEntry.Section && this != SettingsEntry.Group

/** The row's leading icon, which also decides where its dotted line starts. */
private val SettingsEntry.leadingIcon: Int?
    get() =
        when (this) {
            is SettingsEntry.Action -> icon
            is SettingsEntry.Page -> icon
            else -> null
        }

/**
 * A settings page (pattern P1): headed sections of rows, each one a heading, an action, another
 * page, a switch or a value.
 *
 * The fork has one settings screen rather than a settings screen beside a "More" menu: the owner's
 * call (2026-09-15) was to fold More into Settings and make the home header's action a gear.
 *
 * [entries] is null while a page is still reading its values out of the collection, shown as a line
 * of text: rows that appear one by one, or switches that flick as each value arrives, are a repaint
 * each.
 */
@Composable
fun SettingsScreenMMD(
    title: String,
    entries: List<SettingsEntry>?,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = title,
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = onBack,
                )
            },
        )
        if (entries == null) {
            PageLoading(Modifier.weight(1f))
            return@Column
        }
        PagedList(Modifier.weight(1f)) {
            itemsIndexed(entries) { index, entry ->
                // a row's dotted line is left out where a solid group rule or a heading follows it
                val next = entries.getOrNull(index + 1)
                val isLastInGroup = next == null || next is SettingsEntry.Group || next is SettingsEntry.Section
                Column {
                    when (entry) {
                        is SettingsEntry.Section -> SectionTitle(entry.title)
                        SettingsEntry.Group -> GroupDivider()
                        is SettingsEntry.Action ->
                            ActionRow(
                                title = entry.title,
                                subtitle = entry.subtitle,
                                leadingIcon = entry.icon,
                                onClick = entry.onClick,
                            )
                        is SettingsEntry.Page ->
                            NavRow(
                                title = entry.title,
                                subtitle = entry.subtitle,
                                leadingIcon = entry.icon,
                                onClick = entry.onClick,
                            )
                        is SettingsEntry.Switch ->
                            SwitchRow(
                                title = entry.title,
                                subtitle = entry.subtitle,
                                checked = entry.checked,
                                onCheckedChange = entry.onCheckedChange,
                            )
                        is SettingsEntry.Value -> ValueRow(title = entry.title, value = entry.value, onClick = entry.onClick)
                    }
                    if (entry.isRow && !isLastInGroup) {
                        RowDivider(hasLeadingIcon = entry.leadingIcon != null)
                    }
                }
            }
        }
    }
}

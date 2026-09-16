// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ActionRow
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.NavRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.ValueRow
import com.mudita.mmd.components.text.TextMMD

/** A row of a settings page. */
sealed interface SettingsEntry {
    /** A heading above the rows that follow. */
    data class Section(
        val title: String,
    ) : SettingsEntry

    /** Does something straight away, e.g. "Check database". */
    data class Action(
        val title: String,
        val subtitle: String? = null,
        val onClick: () -> Unit,
    ) : SettingsEntry

    /** Opens another page. */
    data class Page(
        val title: String,
        val subtitle: String? = null,
        val onClick: () -> Unit,
    ) : SettingsEntry

    /** A setting that is on or off. */
    data class Switch(
        val title: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val subtitle: String? = null,
    ) : SettingsEntry

    /** A setting whose value is shown on the right; tapping opens a sheet or a panel. */
    data class Value(
        val title: String,
        val value: String,
        val onClick: () -> Unit,
        val subtitle: String? = null,
    ) : SettingsEntry
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
    footer: String? = null,
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
            TextMMD(
                text = stringResource(R.string.dialog_processing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
            )
            return@Column
        }
        PagedList(Modifier.weight(1f)) {
            itemsIndexed(entries) { index, entry ->
                Column {
                    when (entry) {
                        is SettingsEntry.Section -> SectionTitle(entry.title)
                        is SettingsEntry.Action -> {
                            ActionRow(title = entry.title, subtitle = entry.subtitle, onClick = entry.onClick)
                            RowDivider()
                        }
                        is SettingsEntry.Page -> {
                            NavRow(title = entry.title, subtitle = entry.subtitle, onClick = entry.onClick)
                            RowDivider()
                        }
                        is SettingsEntry.Switch -> {
                            SwitchRow(
                                title = entry.title,
                                subtitle = entry.subtitle,
                                checked = entry.checked,
                                onCheckedChange = entry.onCheckedChange,
                            )
                            RowDivider()
                        }
                        is SettingsEntry.Value -> {
                            ValueRow(title = entry.title, subtitle = entry.subtitle, value = entry.value, onClick = entry.onClick)
                            RowDivider()
                        }
                    }
                }
            }
            if (footer != null) {
                item {
                    TextMMD(
                        text = footer,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

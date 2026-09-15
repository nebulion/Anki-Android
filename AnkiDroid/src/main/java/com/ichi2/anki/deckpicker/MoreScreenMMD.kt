// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.NavRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader

/** One entry of the More page: a label and what it opens. */
data class MoreEntry(
    val title: String,
    val onClick: () -> Unit,
)

/**
 * The More page (pattern P1): the collection-wide actions the old toolbar menu, drawer and floating
 * button offered, as drill-down rows. Each row opens an existing flow.
 */
@Composable
fun MoreScreenMMD(
    title: String,
    entries: List<MoreEntry>,
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
        PagedList(Modifier.weight(1f)) {
            items(entries, key = { it.title }) { entry ->
                Column {
                    NavRow(title = entry.title, onClick = entry.onClick)
                    RowDivider()
                }
            }
        }
    }
}

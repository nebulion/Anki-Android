// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.R
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * Several choices from a long list, on a page of its own rather than a sheet that a swipe can
 * dismiss (owner, 2026-09-17): the X at the top left leaves without changing anything, the tick at
 * the top right applies the choices. Rows page like every other list.
 *
 * @param depth how far to indent an option, e.g. a subdeck under its deck
 */
@Composable
fun <T> MultiChoicePage(
    title: String,
    options: List<T>,
    checked: Set<T>,
    label: (T) -> String,
    onDone: (Set<T>) -> Unit,
    onClose: () -> Unit,
    depth: (T) -> Int = { 0 },
) {
    var picked by remember { mutableStateOf(checked) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // the window runs under the status bar: pad the page below it, keeping the white behind it
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
            ScreenHeader(
                title = title,
                navigationIcon = {
                    HeaderAction(icon = R.drawable.close_icon, contentDescription = stringResource(R.string.close), onClick = onClose)
                },
                actions = {
                    HeaderAction(
                        icon = R.drawable.ic_done,
                        contentDescription = stringResource(R.string.dialog_ok),
                        onClick = {
                            onDone(picked)
                            onClose()
                        },
                    )
                },
            )
            PagedList(Modifier.weight(1f)) {
                options.forEachIndexed { index, option ->
                    item {
                        Column {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = SheetDefaults.RowHeight)
                                        .clickable { picked = if (option in picked) picked - option else picked + option }
                                        .padding(start = RowDefaults.EdgePadding + INDENT * depth(option), end = RowDefaults.EdgePadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CheckboxMMD(checked = option in picked, onCheckedChange = null)
                                TextMMD(
                                    text = label(option),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                            if (index != options.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

private val INDENT = 24.dp

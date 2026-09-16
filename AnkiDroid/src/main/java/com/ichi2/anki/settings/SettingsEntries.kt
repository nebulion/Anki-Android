// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.NumberPanel
import com.ichi2.compose.mmd.TextPanel

// The builders below are composable because each one owns the state of its own editor: the panel or
// the sheet a row opens, and the value shown on the row while it is open. A page therefore builds
// its entries during composition, in a fixed order, and gets recomposition for free when a value
// changes. Editors are dialogs and sheets, so they are emitted here rather than inside the list.

/** A switch over a value that reads and writes without waiting, i.e. a preference. */
@Composable
fun switchEntry(
    title: String,
    subtitle: String? = null,
    get: () -> Boolean,
    set: (Boolean) -> Unit,
): SettingsEntry {
    var checked by remember { mutableStateOf(get()) }
    return SettingsEntry.Switch(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = {
            checked = it
            set(it)
        },
    )
}

/** A whole number, shown on the row and typed into a [NumberPanel]. */
@Composable
fun numberEntry(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    subtitle: String? = null,
    body: String? = null,
    display: (Int) -> String = { it.toString() },
    onValue: (Int) -> Unit,
): SettingsEntry {
    var current by remember { mutableStateOf(value) }
    var isEditing by remember { mutableStateOf(false) }
    if (isEditing) {
        NumberPanel(
            title = title,
            body = body,
            value = current,
            min = min,
            max = max,
            confirmLabel = stringResource(R.string.dialog_ok),
            dismissLabel = stringResource(R.string.dialog_cancel),
            invalidMessage = stringResource(R.string.mmd_settings_number_range, min, max),
            onConfirm = {
                current = it
                onValue(it)
            },
            onDismiss = { isEditing = false },
        )
    }
    return SettingsEntry.Value(
        title = title,
        subtitle = subtitle,
        value = display(current),
        onClick = { isEditing = true },
    )
}

/** One of a list of values, shown on the row and chosen in a [ChoiceSheet]. */
@Composable
fun <T> choiceEntry(
    title: String,
    options: List<T>,
    value: T,
    label: (T) -> String,
    subtitle: String? = null,
    onSelect: (T) -> Unit,
): SettingsEntry {
    var current by remember { mutableStateOf(value) }
    var isChoosing by remember { mutableStateOf(false) }
    if (isChoosing) {
        ChoiceSheet(
            title = title,
            options = options,
            selected = current,
            label = label,
            onSelect = {
                current = it
                onSelect(it)
            },
            onDismissRequest = { isChoosing = false },
        )
    }
    return SettingsEntry.Value(
        title = title,
        subtitle = subtitle,
        value = label(current),
        onClick = { isChoosing = true },
    )
}

/** One line of text, shown on the row and typed into a [TextPanel]. */
@Composable
fun textEntry(
    title: String,
    value: String?,
    emptyValue: String,
    subtitle: String? = null,
    body: String? = null,
    validate: (String) -> String? = { null },
    onValue: (String) -> Unit,
): SettingsEntry {
    var current by remember { mutableStateOf(value.orEmpty()) }
    var isEditing by remember { mutableStateOf(false) }
    if (isEditing) {
        TextPanel(
            title = title,
            body = body,
            value = current,
            confirmLabel = stringResource(R.string.dialog_ok),
            dismissLabel = stringResource(R.string.dialog_cancel),
            validate = validate,
            onConfirm = {
                current = it
                onValue(it)
            },
            onDismiss = { isEditing = false },
        )
    }
    return SettingsEntry.Value(
        title = title,
        subtitle = subtitle,
        value = current.ifEmpty { emptyValue },
        onClick = { isEditing = true },
    )
}

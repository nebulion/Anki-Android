// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * The bottom-anchored surface for menus (P3) and choices (P4).
 *
 * Static: it opens fully expanded (`skipPartiallyExpanded`) and has no drag handle, because a
 * handle advertises a drag that only causes a run of partial repaints. `ModalBottomSheetMMD`
 * already defaults to a transparent scrim. A 3dp rule marks the top edge, as in Mudita Chess's
 * `GameMenuDialog`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MmdSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheetMMD(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetMMDState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        HorizontalDividerMMD()
        content()
    }
}

/** One row of a [MenuPanel]. [value] is shown right-aligned, e.g. the current setting. */
data class MenuItem(
    val label: String,
    val value: String? = null,
    val onClick: () -> Unit,
)

/**
 * An in-screen menu (pattern P3): a centred bold title and rows separated by dashed hairlines.
 * Opened from a header action. Tapping a row dismisses the menu, then runs the row's action.
 */
@Composable
fun MenuPanel(
    title: String,
    items: List<MenuItem>,
    onDismissRequest: () -> Unit,
) {
    MmdSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(title)
        items.forEachIndexed { index, item ->
            if (index > 0) DashedDividerMMD(Modifier.padding(horizontal = SheetDefaults.RowPadding))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SheetDefaults.RowHeight)
                        .clickable {
                            onDismissRequest()
                            item.onClick()
                        }.padding(horizontal = SheetDefaults.RowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextMMD(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (item.value != null) {
                    TextMMD(text = item.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * A single choice from a list (pattern P4): radio rows, selecting one commits and dismisses.
 *
 * The radio button is inert (`onClick = null`); the whole row carries the selection. An inert
 * `RadioButtonMMD` measures 24dp, so the label is inset 16dp from it.
 */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    MmdSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(title)
        SheetRows(options) { index, option ->
            if (index > 0) DashedDividerMMD(Modifier.padding(horizontal = SheetDefaults.RowPadding))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SheetDefaults.RowHeight)
                        .clickable {
                            onSelect(option)
                            onDismissRequest()
                        }.padding(horizontal = SheetDefaults.RowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButtonMMD(selected = option == selected, onClick = null)
                TextMMD(
                    text = label(option),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/**
 * Several choices from a list: checkbox rows, committed with a solid Done button.
 */
@Composable
fun <T> MultiChoiceSheet(
    title: String,
    options: List<T>,
    checked: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    doneLabel: String,
    onDone: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    MmdSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(title)
        SheetRows(options) { index, option ->
            if (index > 0) DashedDividerMMD(Modifier.padding(horizontal = SheetDefaults.RowPadding))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SheetDefaults.RowHeight)
                        .clickable { onToggle(option) }
                        .padding(horizontal = SheetDefaults.RowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckboxMMD(checked = option in checked, onCheckedChange = null)
                TextMMD(
                    text = label(option),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        PanelActions {
            PanelPrimaryAction(
                label = doneLabel,
                onClick = {
                    onDone()
                    onDismissRequest()
                },
                modifier = Modifier.weight(1f).padding(SheetDefaults.RowPadding),
            )
        }
    }
}

/**
 * A sheet's rows. A short list is laid out as plain rows, so the sheet is only as tall as its
 * choices; a lazy list takes its whole maximum height even for two rows. A long list pages inside
 * a bounded height with MMD's scrollbar.
 */
@Composable
private fun <T> SheetRows(
    options: List<T>,
    row: @Composable (index: Int, option: T) -> Unit,
) {
    if (options.size <= SheetDefaults.MAX_UNPAGED_ROWS) {
        Column { options.forEachIndexed { index, option -> row(index, option) } }
    } else {
        LazyColumnMMD(modifier = Modifier.heightIn(max = SheetDefaults.MaxListHeight)) {
            itemsIndexed(options) { index, option -> Column { row(index, option) } }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}

object SheetDefaults {
    /** Sheet rows use the list row height of the active [MmdTokens] profile. */
    val RowHeight: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.rowMinHeight

    val RowPadding: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.edgePadding

    /** Up to this many rows a sheet sizes to its rows; more page inside [MaxListHeight]. */
    const val MAX_UNPAGED_ROWS = 6

    /** Bounds the paged list inside a sheet; a lazy list cannot measure in unbounded height. */
    val MaxListHeight: Dp = 420.dp
}

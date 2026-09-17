// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ichi2.anki.R
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
        HorizontalDividerMMD(thickness = PanelDefaults.RuleThickness, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(PanelDefaults.RuleGap))
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
 * An in-screen menu (pattern P3, zeroheight "Menus"): a white box with a 2dp black outline and
 * rounded corners that drops down from the header's right-hand actions, its items separated by
 * dotted lines. No title, no animation, no shadow. Tapping an item dismisses the menu, then runs the
 * item's action; tapping outside dismisses it.
 *
 * @param title the menu's name for accessibility; menus show no title
 */
@Composable
fun MenuPanel(
    title: String,
    items: List<MenuItem>,
    onDismissRequest: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
                    .statusBarsPadding()
                    .padding(top = MenuDefaults.TopOffset, end = MenuDefaults.EndMargin),
            contentAlignment = Alignment.TopEnd,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(min = MenuDefaults.MinWidth, max = MenuDefaults.MaxWidth)
                        .width(IntrinsicSize.Max)
                        .semantics { paneTitle = title }
                        .clip(MenuDefaults.Shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(MenuDefaults.Border, MaterialTheme.colorScheme.onSurface, MenuDefaults.Shape)
                        // swallows taps between items, which would otherwise reach the dismissing box
                        .clickable(interactionSource = null, indication = null) {}
                        .padding(vertical = 8.dp),
            ) {
                items.forEachIndexed { index, item ->
                    if (index > 0) DashedDividerMMD()
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = SheetDefaults.RowHeight)
                                .clickable {
                                    onDismissRequest()
                                    item.onClick()
                                }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextMMD(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.value != null) {
                            TextMMD(
                                text = item.value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

object MenuDefaults {
    /** Below the header: its bar and 3dp rule. */
    val TopOffset: Dp = 60.dp
    val EndMargin: Dp = 8.dp
    val MinWidth: Dp = 200.dp
    val MaxWidth: Dp = 300.dp
    val Border: Dp = 2.dp
    val Shape = RoundedCornerShape(20.dp)
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
        SheetTitle(title, onDismissRequest)
        SheetRows(options) { _, option ->
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
        SheetTitle(title, onDismissRequest)
        SheetRows(options) { _, option ->
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

/** A sheet's title, bold on the left, and the X that closes the sheet on the right. */
@Composable
private fun SheetTitle(
    text: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = SheetDefaults.RowPadding, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.close_icon),
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
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

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.customstudy

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.EXTEND_NEW
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.EXTEND_REV
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_AHEAD
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_FORGOT
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_PREVIEW
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_TAGS
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.MultiChoiceSheet
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelBody
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.PanelTitle
import com.ichi2.compose.mmd.panelTextFieldColors
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * Custom study as MMD surfaces, one at a time: a menu of the six kinds, a panel asking for the
 * amount, and for 'study by tag' a sheet of the deck's tags. [CustomStudyViewModel] decides
 * everything; this only shows it.
 *
 * @param chooseTagsLabel "Choose tags", which the backend only sentence-cases for a Fragment
 * @param onRun runs the custom study; the host shows progress and reports the result
 * @param onUnavailable the chosen kind has nothing to study, e.g. no new cards to extend by
 */
@Composable
fun CustomStudyFlow(
    viewModel: CustomStudyViewModel,
    chooseTagsLabel: String,
    onRun: (option: ContextMenuOption, amount: Int, tags: List<String>) -> Unit,
    onUnavailable: () -> Unit,
    onDismiss: () -> Unit,
) {
    // null: the menu; otherwise the kind whose amount is being asked for
    var option by rememberSaveable { mutableStateOf<ContextMenuOption?>(null) }
    // set once the amount for 'study by tag' is accepted and the tags are being chosen
    var tagAmount by rememberSaveable { mutableStateOf<Int?>(null) }

    when (val chosen = option) {
        null ->
            CustomStudyMenu(
                viewModel = viewModel,
                onSelect = { option = it },
                onUnavailable = onUnavailable,
                onDismiss = onDismiss,
            )
        else -> {
            val amount = tagAmount
            if (amount == null) {
                AmountPanel(
                    viewModel = viewModel,
                    option = chosen,
                    chooseTagsLabel = chooseTagsLabel,
                    onSubmit = { value ->
                        if (chosen == STUDY_TAGS) tagAmount = value else onRun(chosen, value, emptyList())
                    },
                    onDismiss = onDismiss,
                )
            } else {
                TagSheet(
                    viewModel = viewModel,
                    title = chooseTagsLabel,
                    onDone = { tags -> onRun(chosen, amount, tags) },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun CustomStudyMenu(
    viewModel: CustomStudyViewModel,
    onSelect: (ContextMenuOption) -> Unit,
    onUnavailable: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // the menu waits for the backend's defaults rather than filling in its availability line by line
    val availability by produceState<Map<ContextMenuOption, Pair<Boolean, String?>>?>(null, viewModel) {
        value = ContextMenuOption.entries.associateWith { viewModel.isAvailable(it) to viewModel.availabilityLabel(it) }
    }
    val loaded = availability ?: return
    // MenuPanel reports a dismissal before running the chosen item. Choosing an item replaces this
    // menu in the same frame, so the effect below only ever runs for a tap outside or back.
    var isDismissed by remember { mutableStateOf(false) }
    if (isDismissed) LaunchedEffect(Unit) { onDismiss() }
    MenuPanel(
        title = with(context) { TR.sentenceCase.customStudy },
        items =
            ContextMenuOption.entries.map { option ->
                val (isAvailable, label) = loaded.getValue(option)
                MenuItem(label = option.getTitle(context.resources), value = label) {
                    if (isAvailable) onSelect(option) else onUnavailable()
                }
            },
        onDismissRequest = { isDismissed = true },
    )
}

@Composable
private fun AmountPanel(
    viewModel: CustomStudyViewModel,
    option: ContextMenuOption,
    chooseTagsLabel: String,
    onSubmit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val defaultAmount by produceState<Int?>(null, option) { value = viewModel.defaultAmount(option) }
    val initial = defaultAmount ?: return
    val availabilityLabel by produceState<String?>(null, option) { value = viewModel.availabilityLabel(option) }

    var amount by rememberSaveable(option) { mutableStateOf(initial.toString()) }
    var problem by remember { mutableStateOf<CustomStudyViewModel.AmountProblem?>(null) }
    // the amount `problem` was worked out for: nothing is submitted before its check finishes
    var checkedAmount by remember { mutableStateOf<String?>(null) }
    // 'review ahead' checks the backend as the amount changes, the stored default included; a newer
    // amount cancels the older check
    LaunchedEffect(option, amount) {
        problem = viewModel.amountProblem(option, amount)
        checkedAmount = amount
    }

    PanelDialog(onDismissRequest = onDismiss) {
        PanelTitle(option.getTitle(context.resources))
        availabilityLabel?.let { PanelBody(it) }
        PanelBody(stringResource(option.descriptionRes))
        if (option == STUDY_TAGS) {
            CardStateChoice(viewModel)
        }
        TextFieldMMD(
            value = amount,
            onValueChange = { amount = viewModel.acceptAmountInput(option, amount, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = problem != null,
            suffix =
                if (option == STUDY_AHEAD) {
                    { TextMMD(text = pluralStringResource(R.plurals.set_due_date_label_suffix, amount.toIntOrNull() ?: 0)) }
                } else {
                    null
                },
            supportingText = problem?.let { { TextMMD(text = it.message()) } },
            keyboardOptions =
                KeyboardOptions(
                    // limits can be lowered, and the number keyboard has no minus sign
                    keyboardType = if (option == EXTEND_NEW || option == EXTEND_REV) KeyboardType.Text else KeyboardType.Number,
                ),
            colors = panelTextFieldColors(),
        )
        PanelActions {
            PanelSecondaryAction(
                label = stringResource(R.string.dialog_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            if (checkedAmount == amount && viewModel.canSubmit(amount, problem)) {
                PanelPrimaryAction(
                    label =
                        when (option) {
                            STUDY_TAGS -> chooseTagsLabel
                            STUDY_AHEAD -> stringResource(R.string.dialog_positive_create)
                            else -> stringResource(R.string.dialog_ok)
                        },
                    onClick = { amount.toIntOrNull()?.let(onSubmit) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Which cards 'study by tag' picks from, as radio rows inside the panel: no second window. */
@Composable
private fun CardStateChoice(viewModel: CustomStudyViewModel) {
    if (viewModel.selectedCardStateIndex < 0) viewModel.selectedCardStateIndex = 0
    var selected by rememberSaveable { mutableStateOf(viewModel.selectedCardStateIndex) }
    CustomStudyCardState.entries.forEachIndexed { index, state ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = index == selected, role = Role.RadioButton) {
                        selected = index
                        viewModel.selectedCardStateIndex = index
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButtonMMD(selected = index == selected, onClick = null)
            TextMMD(
                text = state.labelProducer(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun TagSheet(
    viewModel: CustomStudyViewModel,
    title: String,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tags by produceState<Pair<List<String>, Set<String>>?>(null, viewModel) { value = viewModel.tags() }
    val (names, suggested) = tags ?: return
    // with no tags to choose from, 'study by tag' is simply the card state and the amount
    if (names.isEmpty()) {
        LaunchedEffect(Unit) { onDone(emptyList()) }
        return
    }
    var checked by rememberSaveable { mutableStateOf(suggested.toList()) }
    MultiChoiceSheet(
        title = title,
        options = names,
        checked = checked.toSet(),
        label = { it },
        onToggle = { tag -> checked = if (tag in checked) checked - tag else checked + tag },
        doneLabel = stringResource(R.string.dialog_positive_create),
        onDone = { onDone(checked) },
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun CustomStudyViewModel.AmountProblem.message(): String =
    when (this) {
        is CustomStudyViewModel.AmountProblem.BelowMinimum -> stringResource(R.string.minimum_value_is, minimum)
        CustomStudyViewModel.AmountProblem.NoCardsMatched -> TR.customStudyNoCardsMatchedTheCriteriaYou()
    }

/** Line 2 of the amount panel, e.g. "Review forgotten cards" */
private val ContextMenuOption.descriptionRes: Int
    get() =
        when (this) {
            EXTEND_NEW -> R.string.custom_study_new_extend
            EXTEND_REV -> R.string.custom_study_rev_extend
            STUDY_FORGOT -> R.string.custom_study_forgotten
            STUDY_AHEAD -> R.string.custom_study_ahead_description
            STUDY_PREVIEW -> R.string.custom_study_preview
            STUDY_TAGS -> R.string.custom_study_tags
        }

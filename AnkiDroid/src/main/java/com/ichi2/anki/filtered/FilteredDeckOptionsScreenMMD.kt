// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.filtered

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.ValueRow
import com.ichi2.compose.mmd.panelTextFieldColors
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import java.util.Locale

/** What the form reports back; each maps onto a [FilteredDeckOptionsViewModel] call. */
interface FilteredDeckOptionsActions {
    fun onDeckNameChange(name: String)

    fun onSearchChange(
        index: FilterIndex,
        search: String,
    )

    fun onLimitChange(
        index: FilterIndex,
        limit: String,
    )

    fun onCardsOptionsChange(
        index: FilterIndex,
        position: Int,
    )

    fun onSecondFilterStatusChange(isEnabled: Boolean)

    fun onRescheduleChange(isEnabled: Boolean)

    fun onRescheduleDelayChange(
        target: RescheduleDelay,
        amount: String,
    )

    fun onAllowEmptyChange(isEnabled: Boolean)

    fun onBuild()

    fun onBack()
}

/**
 * The options of a filtered deck, as one form: its name, one or two filters, rescheduling, and
 * Build. [state] is null while the deck's options load, shown as a line of text.
 *
 * The old screen's "search in browser" and "show excluded cards" buttons are gone with the card
 * browser (Phase 1), and so is its help link, which needed a web browser.
 */
@Composable
fun FilteredDeckOptionsScreenMMD(
    state: FilteredDeckOptions?,
    actions: FilteredDeckOptionsActions,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = state?.title.orEmpty(),
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = actions::onBack,
                )
            },
        )
        if (state == null) {
            TextMMD(
                text = stringResource(R.string.dialog_processing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(RowDefaults.EdgePadding),
            )
            return@Column
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
        ) {
            FormField(
                value = state.name,
                label = TR.deckConfigNamePrompt(),
                error =
                    when (state.nameInputError) {
                        FilteredNameInputError.Empty -> stringResource(R.string.empty_cram_label)
                        FilteredNameInputError.AlreadyExists -> stringResource(R.string.error_name_exists)
                        null -> null
                    },
                onValueChange = actions::onDeckNameChange,
            )

            SectionTitle(TR.actionsFilter())
            FilterFields(state, FilterIndex.First, state.filter1State, actions)
            SwitchRow(
                title = TR.decksEnableSecondFilter(),
                checked = state.isSecondFilterEnabled,
                onCheckedChange = actions::onSecondFilterStatusChange,
            )
            val filter2 = state.filter2State
            if (state.isSecondFilterEnabled && filter2 != null) {
                FilterFields(state, FilterIndex.Second, filter2, actions)
            }

            SectionTitle(TR.actionsOptions())
            SwitchRow(
                title = TR.decksRescheduleCardsBasedOnMyAnswers(),
                checked = state.shouldReschedule,
                onCheckedChange = actions::onRescheduleChange,
            )
            if (!state.shouldReschedule) {
                TextMMD(
                    text = TR.decksZeroMinutesHint(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
                )
                DelayField(state.delayAgain, stringResource(R.string.filtered_option_delay_again), RescheduleDelay.Again, actions)
                DelayField(state.delayHard, stringResource(R.string.filtered_option_delay_hard), RescheduleDelay.Hard, actions)
                DelayField(state.delayGood, stringResource(R.string.filtered_option_delay_good), RescheduleDelay.Good, actions)
            }
            SwitchRow(
                title = TR.decksCreateEvenIfEmpty(),
                checked = state.allowEmpty,
                onCheckedChange = actions::onAllowEmptyChange,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(RowDefaults.EdgePadding),
        ) {
            if (state.isBuildingBrowserSearch) {
                // building can take a moment; a line of text rather than a spinner
                TextMMD(text = stringResource(R.string.dialog_processing), style = MaterialTheme.typography.bodyLarge)
            } else if (state.isBuildingAllowed) {
                // while a field has an error the button waits; the error is shown at the field
                PanelPrimaryAction(
                    label = if (state.id == null) TR.decksBuild() else TR.actionsRebuild(),
                    onClick = actions::onBuild,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A filter's search, its limit and the order cards are selected by. */
@Composable
private fun ColumnScope.FilterFields(
    state: FilteredDeckOptions,
    index: FilterIndex,
    filter: SearchTermState,
    actions: FilteredDeckOptionsActions,
) {
    FormField(
        value = filter.search,
        label = TR.actionsSearch(),
        error = null,
        onValueChange = { actions.onSearchChange(index, it) },
    )
    FormField(
        value = filter.limit,
        label = TR.decksLimitTo(),
        error =
            when (filter.error) {
                SearchInputError.Empty -> stringResource(R.string.empty_cram_label)
                SearchInputError.NotANumber -> TR.errorsInvalidInputEmpty()
                null -> null
            },
        keyboardType = KeyboardType.Number,
        // the backend allows at most 5 digits
        onValueChange = { if (it.length <= MAX_LIMIT_DIGITS) actions.onLimitChange(index, it) },
    )
    if (state.cardOptions.isNotEmpty()) {
        var isChoosing by rememberSaveable(index) { mutableStateOf(false) }
        val selectedBy = TR.decksCardsSelectedBy().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        ValueRow(
            title = selectedBy,
            value = state.cardOptions.getOrElse(filter.index) { "" },
            onClick = { isChoosing = true },
        )
        if (isChoosing) {
            ChoiceSheet(
                title = selectedBy,
                options = state.cardOptions.indices.toList(),
                selected = filter.index,
                label = { state.cardOptions[it] },
                onSelect = { actions.onCardsOptionsChange(index, it) },
                onDismissRequest = { isChoosing = false },
            )
        }
    }
}

@Composable
private fun DelayField(
    value: String,
    label: String,
    target: RescheduleDelay,
    actions: FilteredDeckOptionsActions,
) {
    FormField(
        value = value,
        label = label,
        error = if (value.toIntOrNull() == null) TR.errorsInvalidInputEmpty() else null,
        suffix = TR.schedulingSeconds(),
        keyboardType = KeyboardType.Number,
        onValueChange = { actions.onRescheduleDelayChange(target, it) },
    )
}

@Composable
private fun FormField(
    value: String,
    label: String,
    error: String?,
    onValueChange: (String) -> Unit,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    TextFieldMMD(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
        label = { TextMMD(text = label) },
        singleLine = true,
        isError = error != null,
        suffix = suffix?.let { { TextMMD(text = it) } },
        supportingText = error?.let { { TextMMD(text = it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = panelTextFieldColors(),
    )
}

private const val MAX_LIMIT_DIGITS = 5

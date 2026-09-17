// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.filtered

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.NumberPanel
import com.ichi2.compose.mmd.PageLoading
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.TextPanel
import com.ichi2.compose.mmd.ValueRow
import com.mudita.mmd.components.text.TextMMD

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

/** A value being changed: opens its panel or sheet. */
private sealed interface Edit {
    data object Name : Edit

    data class Deck(
        val index: FilterIndex,
    ) : Edit

    data class Cards(
        val index: FilterIndex,
    ) : Edit

    data class Limit(
        val index: FilterIndex,
    ) : Edit

    data class Order(
        val index: FilterIndex,
    ) : Edit

    data class Delay(
        val target: RescheduleDelay,
    ) : Edit
}

/**
 * The options of a filtered deck as settings rows: its name, one or two filters, rescheduling, and
 * Build. [state] is null while the deck's options load, shown as a line of text.
 *
 * A filter is picked rather than typed (owner, 2026-09-16): the deck the cards come from, and which
 * cards, from a list of common searches or a custom one. The rows page instead of scrolling.
 *
 * The old screen's "search in browser" and "show excluded cards" buttons are gone with the card
 * browser (Phase 1), and so is its help link, which needed a web browser.
 *
 * @param deckNames the decks a filter can take cards from, filtered decks excluded
 */
@Composable
fun FilteredDeckOptionsScreenMMD(
    state: FilteredDeckOptions?,
    actions: FilteredDeckOptionsActions,
    deckNames: List<String> = emptyList(),
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
            PageLoading(Modifier.weight(1f))
            return@Column
        }
        var edit by remember { mutableStateOf<Edit?>(null) }
        val labels = FilterLabels.current()

        PagedList(Modifier.weight(1f), canGrow = true) {
            item { ValueRow(title = TR.deckConfigNamePrompt(), value = state.name, onClick = { edit = Edit.Name }) }
            state.nameInputError?.let { error ->
                warning(
                    when (error) {
                        FilteredNameInputError.Empty -> labels.empty
                        FilteredNameInputError.AlreadyExists -> labels.nameExists
                    },
                )
            }

            item { SectionTitle(TR.actionsFilter()) }
            filterRows(state, FilterIndex.First, state.filter1State, labels) { edit = it }
            item {
                SwitchRow(
                    title = TR.decksEnableSecondFilter(),
                    checked = state.isSecondFilterEnabled,
                    onCheckedChange = actions::onSecondFilterStatusChange,
                )
            }
            val filter2 = state.filter2State
            if (state.isSecondFilterEnabled && filter2 != null) {
                filterRows(state, FilterIndex.Second, filter2, labels) { edit = it }
            }

            item { SectionTitle(TR.actionsOptions()) }
            item {
                SwitchRow(
                    title = TR.decksRescheduleCardsBasedOnMyAnswers(),
                    checked = state.shouldReschedule,
                    onCheckedChange = actions::onRescheduleChange,
                )
            }
            if (!state.shouldReschedule) {
                item {
                    TextMMD(
                        text = TR.decksZeroMinutesHint(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
                    )
                }
                delayRow(state.delayAgain, labels.delayAgain, RescheduleDelay.Again, labels) { edit = it }
                delayRow(state.delayHard, labels.delayHard, RescheduleDelay.Hard, labels) { edit = it }
                delayRow(state.delayGood, labels.delayGood, RescheduleDelay.Good, labels) { edit = it }
            }
            item {
                SwitchRow(
                    title = TR.decksCreateEvenIfEmpty(),
                    checked = state.allowEmpty,
                    onCheckedChange = actions::onAllowEmptyChange,
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(RowDefaults.EdgePadding),
        ) {
            if (state.isBuildingBrowserSearch) {
                // building can take a moment; a line of text rather than a spinner
                TextMMD(text = stringResource(R.string.mmd_building_deck), style = MaterialTheme.typography.bodyLarge)
            } else if (state.isBuildingAllowed) {
                // while a value has an error the button waits; the error is shown under it
                PanelPrimaryAction(
                    label = if (state.id == null) TR.decksBuild() else TR.actionsRebuild(),
                    onClick = actions::onBuild,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        edit?.let { current -> EditPanel(current, state, actions, deckNames, labels) { edit = null } }
    }
}

/** Labels read once per screen: string resources and the locale-aware "cards selected by". */
private data class FilterLabels(
    val empty: String,
    val nameExists: String,
    val invalid: String,
    val allDecks: String,
    val cards: String,
    val customSearch: String,
    val selectedBy: String,
    val delayAgain: String,
    val delayHard: String,
    val delayGood: String,
    val presets: Map<FilterPreset, String>,
) {
    companion object {
        @Composable
        fun current(): FilterLabels {
            val locale = LocalConfiguration.current.locales[0]
            val context = androidx.compose.ui.platform.LocalContext.current
            return FilterLabels(
                empty = stringResource(R.string.empty_cram_label),
                nameExists = stringResource(R.string.error_name_exists),
                invalid = TR.errorsInvalidInputEmpty(),
                allDecks = with(context) { TR.sentenceCase.allDecks },
                cards = stringResource(R.string.mmd_filter_cards),
                customSearch = stringResource(R.string.mmd_filter_custom),
                selectedBy = TR.decksCardsSelectedBy().replaceFirstChar { it.titlecase(locale) },
                delayAgain = stringResource(R.string.filtered_option_delay_again),
                delayHard = stringResource(R.string.filtered_option_delay_hard),
                delayGood = stringResource(R.string.filtered_option_delay_good),
                presets = FilterPreset.entries.associateWith { stringResource(it.labelRes) },
            )
        }
    }
}

/** A filter: the deck, which cards, how many and in what order. */
private fun LazyListScope.filterRows(
    state: FilteredDeckOptions,
    index: FilterIndex,
    filter: SearchTermState,
    labels: FilterLabels,
    onEdit: (Edit) -> Unit,
) {
    item {
        Column {
            ValueRow(title = TR.decksDeck(), value = searchDeck(filter.search) ?: labels.allDecks, onClick = { onEdit(Edit.Deck(index)) })
            RowDivider()
        }
    }
    item {
        Column {
            val preset = presetOf(filter.search)
            ValueRow(
                title = labels.cards,
                value = preset?.let { labels.presets.getValue(it) } ?: searchCards(filter.search),
                onClick = { onEdit(Edit.Cards(index)) },
            )
            RowDivider()
        }
    }
    item {
        Column {
            ValueRow(title = TR.decksLimitTo(), value = filter.limit, onClick = { onEdit(Edit.Limit(index)) })
            if (state.cardOptions.isNotEmpty()) RowDivider()
        }
    }
    filter.error?.let { error ->
        warning(
            when (error) {
                SearchInputError.Empty -> labels.empty
                SearchInputError.NotANumber -> labels.invalid
            },
        )
    }
    if (state.cardOptions.isNotEmpty()) {
        item {
            ValueRow(
                title = labels.selectedBy,
                value = state.cardOptions.getOrElse(filter.index) { "" },
                onClick = { onEdit(Edit.Order(index)) },
            )
        }
    }
}

private fun LazyListScope.delayRow(
    value: String,
    label: String,
    target: RescheduleDelay,
    labels: FilterLabels,
    onEdit: (Edit) -> Unit,
) {
    item { ValueRow(title = label, value = "$value ${TR.schedulingSeconds()}", onClick = { onEdit(Edit.Delay(target)) }) }
    if (value.toIntOrNull() == null) warning(labels.invalid)
}

private fun LazyListScope.warning(text: String) {
    item {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
        )
    }
}

@Composable
private fun EditPanel(
    edit: Edit,
    state: FilteredDeckOptions,
    actions: FilteredDeckOptionsActions,
    deckNames: List<String>,
    labels: FilterLabels,
    onDismiss: () -> Unit,
) {
    val ok = stringResource(R.string.dialog_ok)
    val cancel = stringResource(R.string.dialog_cancel)

    fun filter(index: FilterIndex) = if (index == FilterIndex.First) state.filter1State else state.filter2State ?: SearchTermState()

    when (edit) {
        Edit.Name ->
            TextPanel(
                title = TR.deckConfigNamePrompt(),
                body = null,
                value = state.name,
                confirmLabel = ok,
                dismissLabel = cancel,
                validate = { if (it.isBlank()) labels.empty else null },
                onConfirm = {
                    actions.onDeckNameChange(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        is Edit.Deck -> {
            val search = filter(edit.index).search
            // null stands for all decks
            ChoiceSheet(
                title = TR.decksDeck(),
                options = listOf<String?>(null) + deckNames,
                selected = searchDeck(search),
                label = { it ?: labels.allDecks },
                onSelect = { deck -> actions.onSearchChange(edit.index, buildSearch(deck, searchCards(search))) },
                onDismissRequest = onDismiss,
            )
        }
        is Edit.Cards -> {
            val search = filter(edit.index).search
            // null stands for a custom search, typed next
            var isTypingCustom by remember { mutableStateOf(false) }
            if (isTypingCustom) {
                TextPanel(
                    title = labels.customSearch,
                    body = null,
                    value = searchCards(search),
                    confirmLabel = ok,
                    dismissLabel = cancel,
                    validate = { null },
                    onConfirm = {
                        actions.onSearchChange(edit.index, buildSearch(searchDeck(search), it))
                        onDismiss()
                    },
                    onDismiss = onDismiss,
                )
            } else {
                ChoiceSheet(
                    title = labels.cards,
                    options = FilterPreset.entries + listOf<FilterPreset?>(null),
                    selected = presetOf(search) ?: if (searchCards(search).isEmpty()) FilterPreset.Due else null,
                    label = { it?.let { preset -> labels.presets.getValue(preset) } ?: labels.customSearch },
                    onSelect = { preset ->
                        if (preset == null) {
                            isTypingCustom = true
                        } else {
                            actions.onSearchChange(edit.index, buildSearch(searchDeck(search), preset.search))
                        }
                    },
                    onDismissRequest = { if (!isTypingCustom) onDismiss() },
                )
            }
        }
        is Edit.Limit ->
            NumberPanel(
                title = TR.decksLimitTo(),
                body = null,
                value = filter(edit.index).limit.toIntOrNull() ?: 100,
                min = 1,
                max = MAX_LIMIT,
                confirmLabel = ok,
                dismissLabel = cancel,
                invalidMessage = stringResource(R.string.mmd_settings_number_range, 1, MAX_LIMIT),
                onConfirm = {
                    actions.onLimitChange(edit.index, it.toString())
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        is Edit.Order ->
            ChoiceSheet(
                title = labels.selectedBy,
                options = state.cardOptions.indices.toList(),
                selected = filter(edit.index).index,
                label = { state.cardOptions[it] },
                onSelect = { actions.onCardsOptionsChange(edit.index, it) },
                onDismissRequest = onDismiss,
            )
        is Edit.Delay -> {
            val value =
                when (edit.target) {
                    RescheduleDelay.Again -> state.delayAgain
                    RescheduleDelay.Hard -> state.delayHard
                    RescheduleDelay.Good -> state.delayGood
                }
            NumberPanel(
                title =
                    when (edit.target) {
                        RescheduleDelay.Again -> labels.delayAgain
                        RescheduleDelay.Hard -> labels.delayHard
                        RescheduleDelay.Good -> labels.delayGood
                    },
                body = TR.schedulingSeconds(),
                value = value.toIntOrNull() ?: 0,
                min = 0,
                max = MAX_DELAY_SECONDS,
                confirmLabel = ok,
                dismissLabel = cancel,
                invalidMessage = stringResource(R.string.mmd_settings_number_range, 0, MAX_DELAY_SECONDS),
                onConfirm = {
                    actions.onRescheduleDelayChange(edit.target, it.toString())
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/** The backend allows at most 5 digits. */
private const val MAX_LIMIT = 99999
private const val MAX_DELAY_SECONDS = 99999

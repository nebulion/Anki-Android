// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.compose.mmd.ActionRow
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.LoadingIndicator
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.MultiChoicePage
import com.ichi2.compose.mmd.MultiChoiceSheet
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.TextPanel
import com.ichi2.compose.mmd.ValueRow
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * The card browser (owner's layout A, 2026-09-17).
 *
 * The first page is the filters, each a row showing what it is set to, and a button that shows the
 * cards they find. The second page lists those cards, a page at a time, under a sort row. Tapping a
 * card edits it; select mode ticks cards instead. The menu acts on every card shown, or on the ticked
 * ones in select mode, and each change offers Undo.
 */
class BrowseFragment : ComposeHostFragment() {
    private val viewModel: BrowseViewModel by viewModels()
    private val messages = MessageHostState()

    override fun onResume() {
        super.onResume()
        // a note edited from here may have changed its row, or no longer match the filters
        viewModel.reload()
    }

    @Composable
    override fun ScreenContent() {
        BackHandler(enabled = viewModel.isShowingCards) { viewModel.showFilters() }
        Column(Modifier.fillMaxSize()) {
            if (viewModel.isShowingCards) CardsPage() else FiltersPage()
            MessageHost(messages)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Filters

    private enum class Editing { Text, Deck, State, Flag, Tag, NoteType, Added }

    @Composable
    private fun ColumnScope.FiltersPage() {
        val context = LocalContext.current
        var editing by rememberSaveable { mutableStateOf<Editing?>(null) }
        val filters = viewModel.filters
        val options = viewModel.options
        val any = stringResource(R.string.mmd_browse_any)
        val flagNames by produceState(emptyMap<Flag, String>()) { value = Flag.queryDisplayNames(context) }
        val stateLabels = StateFilter.entries.associateWith { stateLabel(it) }
        val flagLabels = FlagFilter.entries.associateWith { flagLabel(it, flagNames) }

        fun <T> summary(
            chosen: Collection<T>,
            label: (T) -> String,
        ) = if (chosen.isEmpty()) any else chosen.joinToString(", ") { label(it) }

        ScreenHeader(
            title = stringResource(R.string.bottom_nav_browse),
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = { requireActivity().finish() },
                )
            },
        )
        val noTags = stringResource(R.string.mmd_browse_none_here)
        val textTitle = stringResource(R.string.mmd_browse_text)
        val stateTitle = stringResource(R.string.mmd_browse_state)
        val addedTitle = stringResource(R.string.mmd_browse_added)
        val flagTitle = ViewerAction.FLAG_MENU.title(context)
        val addedValue = addedLabel(filters.added)
        val clearLabel = stringResource(R.string.mmd_browse_clear)
        PagedList(Modifier.weight(1f), canGrow = true) {
            filterRow(textTitle, filters.text.ifBlank { any }) { editing = Editing.Text }
            filterRow(TR.decksDeck(), summary(filters.decks) { it }) { editing = Editing.Deck }
            filterRow(stateTitle, summary(filters.states) { stateLabels.getValue(it) }) { editing = Editing.State }
            filterRow(flagTitle, summary(filters.flags) { flagLabels.getValue(it) }) { editing = Editing.Flag }
            filterRow(
                TR.editingTags(),
                if (filters.tags.isEmpty() && options?.tags?.isEmpty() == true) noTags else summary(filters.tags) { it },
            ) { if (options?.tags?.isNotEmpty() == true) editing = Editing.Tag }
            filterRow(TR.notetypesNotetype(), summary(filters.noteTypes) { it }) { editing = Editing.NoteType }
            filterRow(addedTitle, addedValue) { editing = Editing.Added }
            if (!filters.isEmpty) {
                item { ActionRow(title = clearLabel, onClick = { viewModel.setFilters(BrowseFilters()) }) }
            }
        }
        viewModel.error?.let { error ->
            TextMMD(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding),
            )
        }
        val count = viewModel.count
        PanelPrimaryAction(
            label = if (count == null) stringResource(R.string.mmd_browse_counting) else stringResource(R.string.mmd_browse_show, count),
            onClick = { viewModel.showCards() },
            enabled = count != null && count > 0,
            modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
        )

        val close = { editing = null }
        when (editing) {
            Editing.Text ->
                TextPanel(
                    title = textTitle,
                    body = stringResource(R.string.mmd_browse_text_body),
                    value = filters.text,
                    confirmLabel = stringResource(R.string.dialog_ok),
                    dismissLabel = stringResource(R.string.dialog_cancel),
                    validate = { null },
                    onConfirm = {
                        viewModel.setFilters(filters.copy(text = it.trim()))
                        close()
                    },
                    onDismiss = close,
                )
            // long lists: a page of their own, which a swipe can't dismiss (owner, 2026-09-17)
            Editing.Deck ->
                MultiChoicePage(
                    title = TR.decksDeck(),
                    options = options?.decks.orEmpty(),
                    checked = filters.decks,
                    label = { it.substringAfterLast("::") },
                    depth = { it.split("::").size - 1 },
                    onDone = { viewModel.setFilters(viewModel.filters.copy(decks = it)) },
                    onClose = close,
                )
            Editing.State ->
                FilterSheet(stateTitle, StateFilter.entries, filters.states, { stateLabels.getValue(it) }, close) {
                    viewModel.setFilters(viewModel.filters.copy(states = it))
                }
            Editing.Flag ->
                FilterSheet(flagTitle, options?.flags.orEmpty(), filters.flags, { flagLabels.getValue(it) }, close) {
                    viewModel.setFilters(viewModel.filters.copy(flags = it))
                }
            Editing.Tag ->
                MultiChoicePage(
                    title = TR.editingTags(),
                    options = options?.tags.orEmpty(),
                    checked = filters.tags,
                    label = { it },
                    onDone = { viewModel.setFilters(viewModel.filters.copy(tags = it)) },
                    onClose = close,
                )
            Editing.NoteType ->
                FilterSheet(TR.notetypesNotetype(), options?.noteTypes.orEmpty(), filters.noteTypes, { it }, close) {
                    viewModel.setFilters(viewModel.filters.copy(noteTypes = it))
                }
            Editing.Added -> {
                val addedLabels = AddedFilter.entries.associateWith { addedLabel(it) }
                ChoiceSheet(
                    title = addedTitle,
                    options = AddedFilter.entries,
                    selected = filters.added,
                    label = { addedLabels.getValue(it) },
                    onSelect = { viewModel.setFilters(viewModel.filters.copy(added = it)) },
                    onDismissRequest = close,
                )
            }
            null -> Unit
        }
    }

    private fun LazyListScope.filterRow(
        title: String,
        value: String,
        onClick: () -> Unit,
    ) {
        item {
            Column {
                ValueRow(title = title, value = value, onClick = onClick)
                RowDivider()
            }
        }
    }

    /** Several choices of one filter, applied together with Done. */
    @Composable
    private fun <T> FilterSheet(
        title: String,
        options: List<T>,
        chosen: Set<T>,
        label: (T) -> String,
        onDismiss: () -> Unit,
        onDone: (Set<T>) -> Unit,
    ) {
        var picked by remember { mutableStateOf(chosen) }
        MultiChoiceSheet(
            title = title,
            options = options,
            checked = picked,
            label = label,
            onToggle = { picked = if (it in picked) picked - it else picked + it },
            doneLabel = stringResource(R.string.dialog_ok),
            onDone = { onDone(picked) },
            onDismissRequest = onDismiss,
        )
    }

    @Composable
    private fun stateLabel(state: StateFilter): String =
        stringResource(
            when (state) {
                StateFilter.New -> R.string.mmd_filter_new
                StateFilter.Learning -> R.string.mmd_browse_state_learning
                StateFilter.Review -> R.string.mmd_browse_state_review
                StateFilter.Due -> R.string.mmd_filter_due
                StateFilter.Suspended -> R.string.mmd_filter_suspended
                StateFilter.Buried -> R.string.mmd_browse_state_buried
            },
        )

    @Composable
    private fun flagLabel(
        flag: FlagFilter,
        names: Map<Flag, String>,
    ): String =
        if (flag == FlagFilter.NoFlag) {
            stringResource(R.string.mmd_browse_no_flag)
        } else {
            names[Flag.entries.first { it.code == flag.value }] ?: flag.name
        }

    @Composable
    private fun addedLabel(added: AddedFilter): String =
        stringResource(
            when (added) {
                AddedFilter.AnyTime -> R.string.mmd_browse_any
                AddedFilter.Today -> R.string.mmd_filter_added_today
                AddedFilter.Week -> R.string.mmd_browse_added_week
                AddedFilter.Month -> R.string.mmd_browse_added_month
                AddedFilter.Year -> R.string.mmd_browse_added_year
            },
        )

    // ---------------------------------------------------------------------------------------------
    // Cards

    private enum class Confirm { Reset, Delete }

    @Composable
    private fun ColumnScope.CardsPage() {
        var isMenuShown by rememberSaveable { mutableStateOf(false) }
        var isChoosingSort by rememberSaveable { mutableStateOf(false) }
        var confirming by rememberSaveable { mutableStateOf<Confirm?>(null) }
        val ids = viewModel.cardIds
        val selecting = viewModel.isSelecting
        val targets = viewModel.targets.size

        ScreenHeader(
            title =
                if (selecting) {
                    stringResource(R.string.mmd_browse_selected, viewModel.selected.size)
                } else {
                    stringResource(R.string.mmd_browse_cards_title, viewModel.count ?: 0)
                },
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = viewModel::showFilters,
                )
            },
            actions = {
                HeaderAction(
                    icon = if (selecting) R.drawable.close_icon else R.drawable.ic_baseline_check_box_24,
                    contentDescription = stringResource(if (selecting) R.string.close else R.string.mmd_browse_select),
                    onClick = viewModel::toggleSelecting,
                    enabled = !ids.isNullOrEmpty(),
                )
                HeaderAction(
                    icon = R.drawable.ic_more_vertical,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    onClick = { isMenuShown = true },
                    enabled = !ids.isNullOrEmpty(),
                )
            },
        )
        val order = stringResource(if (viewModel.isReversed) R.string.mmd_browse_descending else R.string.mmd_browse_ascending)
        ValueRow(
            title = stringResource(R.string.mmd_browse_sort),
            value = "${sortLabel(viewModel.sort)}, $order",
            onClick = { isChoosingSort = true },
        )
        RowDivider()
        if (ids == null) {
            LoadingIndicator(stringResource(R.string.mmd_browse_searching), Modifier.weight(1f))
        } else {
            PagedList(Modifier.weight(1f), canGrow = true) {
                items(ids, key = { it }) { cardId ->
                    CardRow(cardId, selecting = selecting, isSelected = cardId in viewModel.selected)
                }
            }
        }

        if (isMenuShown) {
            MenuPanel(
                title = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                items =
                    buildList {
                        if (selecting) add(MenuItem(getString(R.string.mmd_browse_select_all)) { viewModel.toggleAll() })
                        if (targets > 0) {
                            add(MenuItem(getString(R.string.mmd_browse_act_reset, targets)) { confirming = Confirm.Reset })
                            add(
                                MenuItem(getString(R.string.mmd_browse_act_suspend, targets)) {
                                    act(R.string.mmd_browse_suspended, viewModel::suspend)
                                },
                            )
                            add(
                                MenuItem(getString(R.string.mmd_browse_act_unsuspend, targets)) {
                                    act(R.string.mmd_browse_unsuspended, viewModel::unsuspend)
                                },
                            )
                            add(MenuItem(getString(R.string.mmd_browse_act_delete, targets)) { confirming = Confirm.Delete })
                        }
                    },
                onDismissRequest = { isMenuShown = false },
            )
        }

        if (isChoosingSort) {
            SortSheet(onDismiss = { isChoosingSort = false })
        }

        when (confirming) {
            Confirm.Reset ->
                ConfirmPanel(
                    title = getString(R.string.reset_card_dialog_title),
                    body = getString(R.string.mmd_browse_reset_body, targets),
                    confirmLabel = getString(R.string.reset),
                    dismissLabel = getString(R.string.dialog_cancel),
                    onConfirm = {
                        confirming = null
                        act(R.string.mmd_browse_was_reset, viewModel::resetProgress)
                    },
                    onDismiss = { confirming = null },
                )
            Confirm.Delete ->
                ConfirmPanel(
                    title = getString(R.string.mmd_browse_delete),
                    body = getString(R.string.mmd_browse_delete_body, targets),
                    confirmLabel = getString(R.string.mmd_browse_delete),
                    dismissLabel = getString(R.string.dialog_cancel),
                    onConfirm = {
                        confirming = null
                        act(R.string.mmd_browse_deleted, viewModel::deleteNotes)
                    },
                    onDismiss = { confirming = null },
                )
            null -> Unit
        }
    }

    /** The column to sort by, then which way. */
    @Composable
    private fun SortSheet(onDismiss: () -> Unit) {
        var column by remember { mutableStateOf<BrowseSort?>(null) }
        val sortLabels = BrowseSort.entries.associateWith { sortLabel(it) }
        val ascending = stringResource(R.string.mmd_browse_ascending)
        val descending = stringResource(R.string.mmd_browse_descending)
        val chosen = column
        if (chosen == null) {
            ChoiceSheet(
                title = stringResource(R.string.mmd_browse_sort),
                options = BrowseSort.entries,
                selected = viewModel.sort,
                label = { sortLabels.getValue(it) },
                onSelect = { column = it },
                // choosing a column dismisses the sheet too; only a real dismissal closes
                onDismissRequest = { if (column == null) onDismiss() },
            )
        } else {
            ChoiceSheet(
                title = sortLabels.getValue(chosen),
                options = listOf(false, true),
                selected = if (chosen == viewModel.sort) viewModel.isReversed else null,
                label = { if (it) descending else ascending },
                onSelect = { viewModel.setSort(chosen, it) },
                onDismissRequest = onDismiss,
            )
        }
    }

    @Composable
    private fun sortLabel(sort: BrowseSort): String =
        if (sort == BrowseSort.Deck) {
            TR.decksDeck()
        } else {
            stringResource(
                when (sort) {
                    BrowseSort.SortField -> R.string.mmd_browse_sort_field
                    BrowseSort.Due -> R.string.mmd_browse_sort_due
                    BrowseSort.Added -> R.string.mmd_browse_sort_added
                    BrowseSort.Modified -> R.string.mmd_browse_sort_modified
                    BrowseSort.Interval -> R.string.mmd_browse_sort_interval
                    BrowseSort.Ease -> R.string.mmd_browse_sort_ease
                    BrowseSort.Reviews -> R.string.mmd_browse_sort_reviews
                    else -> R.string.mmd_browse_sort_lapses
                },
            )
        }

    @Composable
    private fun CardRow(
        cardId: CardId,
        selecting: Boolean,
        isSelected: Boolean,
    ) {
        val row by produceState<BrowseRow?>(null, cardId, viewModel.version) { value = viewModel.row(cardId) }
        val suspended = stringResource(R.string.mmd_filter_suspended)
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (selecting) viewModel.toggle(cardId) else editNote(cardId) }
                        .padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    CheckboxMMD(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(end = 16.dp))
                }
                Column(Modifier.weight(1f)) {
                    TextMMD(text = row?.question.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    TextMMD(
                        text = row?.let { if (it.isSuspended) "${it.detail} · $suspended" else it.detail }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            RowDivider()
        }
    }

    /** Runs [action] on the cards it targets and reports it with Undo. */
    private fun act(
        message: Int,
        action: () -> Unit,
    ) {
        val count = viewModel.targets.size
        action()
        messages.show(getString(message, count), TR.undoUndo(), viewModel::undo)
    }

    private fun editNote(cardId: CardId) {
        startActivity(NoteEditorDestination(cardId = cardId).toIntent(requireContext()))
    }

    companion object {
        /** The browser, with [deckId] chosen as the deck filter, or no filter without one. */
        fun getIntent(
            context: Context,
            deckId: DeckId? = null,
        ): Intent =
            SingleFragmentActivity.getIntent(
                context,
                fragmentClass = BrowseFragment::class,
                arguments = Bundle().apply { deckId?.let { putLong(BrowseViewModel.ARG_DECK_ID, it) } },
            )
    }
}

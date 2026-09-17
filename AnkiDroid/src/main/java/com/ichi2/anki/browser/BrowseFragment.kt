// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.LoadingIndicator
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.panelTextFieldColors
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * The plain card browser: search, tick cards (or all of them), then reset, suspend, unsuspend or
 * delete them from the menu; edit a single ticked card's note. Every change offers Undo.
 */
class BrowseFragment : ComposeHostFragment() {
    private val viewModel: BrowseViewModel by viewModels()
    private val messages = MessageHostState()

    override fun onResume() {
        super.onResume()
        // a note edited from here may have changed its row
        if (viewModel.cardIds != null) viewModel.runSearch()
    }

    @Composable
    override fun ScreenContent() {
        var isMenuShown by rememberSaveable { mutableStateOf(false) }
        var confirming by rememberSaveable { mutableStateOf<Confirm?>(null) }
        val ids = viewModel.cardIds
        val selected = viewModel.selected

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.bottom_nav_browse),
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = { requireActivity().finish() },
                    )
                },
                actions = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_check_box_24,
                        contentDescription = stringResource(R.string.mmd_browse_select_all),
                        onClick = viewModel::toggleAll,
                        enabled = !ids.isNullOrEmpty(),
                    )
                    HeaderAction(
                        icon = R.drawable.ic_more_vertical,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                        onClick = { isMenuShown = true },
                        enabled = selected.isNotEmpty(),
                    )
                },
            )
            TextFieldMMD(
                value = viewModel.search,
                onValueChange = { viewModel.search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
                label = { TextMMD(text = stringResource(R.string.mmd_browse_search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.runSearch() }),
                colors = panelTextFieldColors(),
            )
            TextMMD(
                text =
                    viewModel.error
                        ?: stringResource(R.string.mmd_browse_count, ids?.size ?: 0, selected.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
            )
            RowDivider()
            if (ids == null) {
                LoadingIndicator(stringResource(R.string.mmd_browse_searching), Modifier.weight(1f))
            } else {
                PagedList(Modifier.weight(1f).imePadding(), canGrow = true) {
                    items(ids, key = { it }) { cardId ->
                        CardRow(cardId, isSelected = cardId in selected)
                    }
                }
            }
            MessageHost(messages)
        }

        if (isMenuShown) {
            MenuPanel(
                title = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                items =
                    buildList {
                        if (selected.size == 1) {
                            add(MenuItem(getString(R.string.mmd_editor_edit_note)) { editNote(selected.first()) })
                        }
                        add(MenuItem(getString(R.string.reset_card_dialog_title)) { confirming = Confirm.Reset })
                        add(MenuItem(getString(R.string.mmd_browse_suspend)) { act(R.string.mmd_browse_suspended, viewModel::suspend) })
                        add(
                            MenuItem(
                                getString(R.string.mmd_browse_unsuspend),
                            ) { act(R.string.mmd_browse_unsuspended, viewModel::unsuspend) },
                        )
                        add(MenuItem(getString(R.string.mmd_browse_delete)) { confirming = Confirm.Delete })
                    },
                onDismissRequest = { isMenuShown = false },
            )
        }

        when (confirming) {
            Confirm.Reset ->
                ConfirmPanel(
                    title = getString(R.string.reset_card_dialog_title),
                    body = getString(R.string.reset_body, selected.size),
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
                    body = getString(R.string.mmd_browse_delete_body, selected.size),
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

    @Composable
    private fun CardRow(
        cardId: CardId,
        isSelected: Boolean,
    ) {
        val row by produceState<BrowseRow?>(null, cardId, viewModel.version) { value = viewModel.row(cardId) }
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggle(cardId) }
                        .padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckboxMMD(checked = isSelected, onCheckedChange = null)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    TextMMD(
                        text = row?.title.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                    )
                    TextMMD(
                        text =
                            row
                                ?.let {
                                    if (it.isSuspended) "${it.detail} · ${getString(R.string.mmd_filter_suspended)}" else it.detail
                                }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            RowDivider()
        }
    }

    /** Runs [action] on the ticked cards and reports it with Undo. */
    private fun act(
        message: Int,
        action: () -> Unit,
    ) {
        val count = viewModel.selected.size
        action()
        messages.show(getString(message, count), TR.undoUndo(), viewModel::undo)
    }

    private fun editNote(cardId: CardId) {
        startActivity(NoteEditorDestination(cardId = cardId).toIntent(requireContext()))
    }

    private enum class Confirm { Reset, Delete }

    companion object {
        /** The browser, searching [deckId]'s cards, or every card without one. */
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

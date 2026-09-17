// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpage

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsViewModel
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyFlow
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel
import com.ichi2.anki.export.ExportFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.PanelTitle
import com.ichi2.compose.mmd.panelTextFieldColors
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/**
 * The page of the selected deck: counts and Study, or the congrats / empty state, with the deck's
 * actions in a menu. Replaces the study options screen and the deck list's long-press menu.
 */
class DeckPageFragment :
    ComposeHostFragment(),
    ChangeManager.Subscriber {
    private val viewModel: StudyOptionsViewModel by viewModels()
    private val customStudyViewModel: CustomStudyViewModel by viewModels()
    private val messages = MessageHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChangeManager.subscribe(this)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.refreshData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        if (isAdded) viewModel.refreshData()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.flowOfDeckPage.collectAsStateWithLifecycle()
        var isMenuShown by rememberSaveable { mutableStateOf(false) }
        var isRenameShown by rememberSaveable { mutableStateOf(false) }
        var isDeleteShown by rememberSaveable { mutableStateOf(false) }
        var isCustomStudyShown by rememberSaveable { mutableStateOf(false) }
        val openCustomStudy = {
            customStudyViewModel.deckId = viewModel.selectedDeckId
            isCustomStudyShown = true
        }

        DeckPageScreenMMD(
            state = state,
            messages = messages,
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
            deckOptionsLabel = TR.sentenceCase.deckOptions,
            customStudyLabel = TR.sentenceCase.customStudy,
            onDeckOptions = ::openDeckOptions,
            onMenu = { isMenuShown = true },
            onStudy = { navigate(ReviewDeckDestination.CurrentDeck) },
            onUnbury = { viewModel.unbury() },
            onCustomStudy = openCustomStudy,
        )

        if (isMenuShown) {
            MenuPanel(
                title = state.deckNameOrEmpty(),
                items =
                    menuItems(
                        onCustomStudy = openCustomStudy,
                        onRename = { isRenameShown = true },
                        onDelete = { isDeleteShown = true },
                    ),
                onDismissRequest = { isMenuShown = false },
            )
        }
        if (isCustomStudyShown) {
            CustomStudyFlow(
                viewModel = customStudyViewModel,
                chooseTagsLabel = TR.sentenceCase.chooseTags,
                onRun = { option, amount, tags ->
                    isCustomStudyShown = false
                    runCustomStudy(option, amount, tags)
                },
                onUnavailable = { messages.show(getString(R.string.studyoptions_no_cards_due)) },
                onDismiss = { isCustomStudyShown = false },
            )
        }
        if (isRenameShown) {
            RenamePanel(onDismiss = { isRenameShown = false })
        }
        if (isDeleteShown) {
            ConfirmPanel(
                title = getString(R.string.delete_deck_title),
                body = viewModel.deleteMessage(requireContext()),
                confirmLabel = getString(R.string.dialog_positive_delete),
                dismissLabel = getString(R.string.dialog_cancel),
                onConfirm = {
                    isDeleteShown = false
                    deleteDeck()
                },
                onDismiss = { isDeleteShown = false },
            )
        }
    }

    private fun menuItems(
        onCustomStudy: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit,
    ): List<MenuItem> {
        val context = requireContext()
        val isFiltered = viewModel.isFilteredDeck
        return buildList {
            if (isFiltered) {
                add(MenuItem(TR.actionsRebuild()) { rebuildFiltered() })
                add(MenuItem(getString(R.string.empty_cram_label)) { emptyFiltered() })
            } else {
                add(MenuItem(TR.sentenceCase.customStudy, onClick = onCustomStudy))
            }
            if (!isFiltered) {
                add(MenuItem(getString(R.string.mmd_editor_add_note)) { addNote() })
            }
            add(MenuItem(with(context) { TR.sentenceCase.renameDeck }, onClick = onRename))
            add(MenuItem(getString(R.string.export_deck)) { exportDeck() })
            if (viewModel.haveBuried) {
                add(MenuItem(TR.studyingUnbury()) { viewModel.unbury() })
            }
            add(MenuItem(with(context) { TR.sentenceCase.deleteDeck }, onClick = onDelete))
        }
    }

    @Composable
    private fun RenamePanel(onDismiss: () -> Unit) {
        var name by rememberSaveable { mutableStateOf(viewModel.deckFullName ?: "") }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        PanelDialog(onDismissRequest = onDismiss) {
            PanelTitle(with(requireContext()) { TR.sentenceCase.renameDeck })
            TextFieldMMD(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> { TextMMD(text = message) } },
                colors = panelTextFieldColors(),
            )
            PanelActions {
                PanelSecondaryAction(label = getString(R.string.dialog_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                PanelPrimaryAction(
                    label = getString(R.string.rename),
                    onClick = {
                        launchCatchingTask {
                            when (val result = viewModel.renameDeck(name)) {
                                StudyOptionsViewModel.RenameResult.Renamed -> {
                                    onDismiss()
                                    messages.show(getString(R.string.deck_renamed))
                                }
                                StudyOptionsViewModel.RenameResult.Unchanged -> onDismiss()
                                is StudyOptionsViewModel.RenameResult.Invalid -> error = result.message(requireContext())
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    private fun openDeckOptions() {
        val deckId = viewModel.selectedDeckId
        Timber.i("DeckPage: opening deck options for %d", deckId)
        val intent =
            if (viewModel.isFilteredDeck) {
                FilteredDeckOptionsFragment.getIntent(requireContext(), did = deckId)
            } else {
                DeckOptions.getIntent(requireContext(), deckId)
            }
        startActivity(intent)
    }

    /**
     * Runs a custom study chosen in [CustomStudyFlow]. The host activity then reopens the deck page
     * for the deck custom study selected: a new "Custom Study Session", or this deck with raised limits.
     */
    private fun runCustomStudy(
        option: ContextMenuOption,
        amount: Int,
        tags: List<String>,
    ) {
        requireActivity().launchCatchingTask(
            // net.ankiweb.rsdroid.BackendException: No cards matched the criteria you provided.
            skipCrashReport = { it is BackendException },
        ) {
            val action =
                withProgress {
                    withCol { decks.select(customStudyViewModel.deckId) }
                    customStudyViewModel.customStudy(option, amount, tagsToInclude = tags)
                }
            requireActivity().supportFragmentManager.setFragmentResult(
                CustomStudyAction.REQUEST_KEY,
                bundleOf(CustomStudyAction.BUNDLE_KEY to action.ordinal),
            )
        }
    }

    private fun exportDeck() {
        startActivity(ExportFragment.getIntent(requireContext(), viewModel.selectedDeckId))
    }

    private fun addNote() {
        startActivity(NoteEditorDestination(deckId = viewModel.selectedDeckId).toIntent(requireContext()))
    }

    private fun rebuildFiltered() =
        launchCatchingTask {
            withProgress(R.string.rebuild_filtered_deck) { viewModel.rebuildCram() }
        }

    private fun emptyFiltered() =
        launchCatchingTask {
            withProgress(R.string.empty_filtered_deck) { viewModel.emptyCram() }
        }

    private fun deleteDeck() =
        launchCatchingTask {
            withProgress(R.string.delete_deck) { viewModel.deleteDeck() }
            requireActivity().finish()
        }
}

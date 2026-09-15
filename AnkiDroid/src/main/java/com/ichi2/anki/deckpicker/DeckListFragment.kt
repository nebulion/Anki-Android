// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.showImportDialog
import com.ichi2.compose.mmd.ComposeHostFragment

/** The Decks tab of [DeckPicker]: [DeckListScreenMMD] over the home activity's [DeckPickerViewModel]. */
class DeckListFragment : ComposeHostFragment() {
    private val viewModel: DeckPickerViewModel by activityViewModels()

    private val home: DeckPicker
        get() = requireActivity() as DeckPicker

    @Composable
    override fun ScreenContent() {
        val deckList by viewModel.flowOfDeckList.collectAsStateWithLifecycle()
        val isInitialState by viewModel.flowOfDeckListInInitialState.collectAsStateWithLifecycle()
        val studiedToday by viewModel.flowOfStudiedTodayStats.collectAsStateWithLifecycle()
        val menuState by viewModel.flowOfOptionsMenuState.collectAsStateWithLifecycle()

        DeckListScreenMMD(
            state =
                DeckListUiState(
                    decks = deckList.data,
                    studiedToday = studiedToday,
                    isEmptyCollection = isInitialState,
                    syncState = menuState?.syncIcon ?: SyncIconState.Normal,
                    undoLabel = menuState?.takeIf { it.undoAvailable }?.undoLabel,
                ),
            onUndo = { home.undo() },
            onSync = { home.onSyncPressed() },
            onDeckClick = { home.openDeck(it) },
            onToggleExpand = { viewModel.toggleDeckExpand(it) },
            onImport = { home.showImportDialog() },
        )
    }
}

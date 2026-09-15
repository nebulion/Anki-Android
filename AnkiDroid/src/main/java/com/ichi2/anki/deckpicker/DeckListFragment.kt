// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.isLoggedIn
import com.ichi2.anki.showImportDialog
import com.ichi2.compose.mmd.ComposeHostFragment

/** The home screen's deck list: [DeckListScreenMMD] over the home activity's [DeckPickerViewModel]. */
class DeckListFragment : ComposeHostFragment() {
    private val viewModel: DeckPickerViewModel by activityViewModels()

    private val home: DeckPicker
        get() = requireActivity() as DeckPicker

    @Composable
    override fun ScreenContent() {
        val deckList by viewModel.flowOfDeckList.collectAsStateWithLifecycle()
        val isInitialState by viewModel.flowOfDeckListInInitialState.collectAsStateWithLifecycle()
        val menuState by viewModel.flowOfOptionsMenuState.collectAsStateWithLifecycle()
        val syncProgress by viewModel.flowOfSyncProgress.collectAsStateWithLifecycle()

        DeckListScreenMMD(
            state =
                DeckListUiState(
                    decks = deckList.data,
                    isEmptyCollection = isInitialState,
                    // read when the menu state recomposes this, which happens on return from the login screen
                    isLoggedIn = isLoggedIn(),
                    syncState = menuState?.syncIcon ?: SyncIconState.Normal,
                    undoLabel = menuState?.takeIf { it.undoAvailable }?.undoLabel,
                    syncProgress = syncProgress,
                ),
            onUndo = { home.undo() },
            onSync = { home.onSyncPressed() },
            onStatistics = { home.openStatistics() },
            onMore = { home.openMore() },
            onDeckClick = { home.openDeck(it) },
            onDeckLongPress = { home.studyDeck(it) },
            onToggleExpand = { viewModel.toggleDeckExpand(it) },
            onSignIn = { home.loginToSyncServer() },
            onImport = { home.showImportDialog() },
        )
    }
}

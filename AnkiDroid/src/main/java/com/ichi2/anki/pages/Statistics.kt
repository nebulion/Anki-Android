// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.withProgress
import com.ichi2.compose.mmd.HeaderAction

/**
 * The backend's graphs page. The header names the deck being shown and its one action picks a
 * different one; `assets/mmd-halftone.js` dithers the graphs so they read on E Ink.
 *
 * Saving the graphs as a PDF is gone: printing has no place on the Kompakt.
 */
class Statistics : PageFragment() {
    override val pagePath: String = "graphs"

    @Composable
    override fun RowScope.HeaderActions() {
        HeaderAction(
            icon = R.drawable.id_arrow_drop_down,
            contentDescription = TR.actionsDecks(),
            onClick = { startDeckSelection(allowAll = false, allowFiltered = false, skipEmptyDefault = true) },
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        registerDeckSelectedHandler(action = ::onDeckSelected)
        requireActivity().launchCatchingTask {
            withProgress {
                val deckName = savedInstanceState?.getString(KEY_DECK_NAME, null) ?: withCol { decks.current().name }
                changeDeck(deckName)
            }
        }
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        changeDeck(deck.name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DECK_NAME, title)
    }

    /**
     * Shows [selectedDeckName]'s graphs. The backend has no API for this, so the page's own search
     * box is filled in and told it changed. See issue #3394 in the Anki repository.
     **/
    private fun changeDeck(selectedDeckName: String) {
        title = selectedDeckName
        val javascriptCode =
            """
            var textBox = document.getElementById("statisticsSearchText");
            textBox.value = "deck:\"$selectedDeckName\"";
            textBox.dispatchEvent(new Event("input", { bubbles: true }));
            textBox.dispatchEvent(new Event("change"));
            """.trimIndent()
        webViewLayout.evaluateJavascript(javascriptCode, null)
    }

    companion object {
        private const val KEY_DECK_NAME = "key_deck_name"
    }
}

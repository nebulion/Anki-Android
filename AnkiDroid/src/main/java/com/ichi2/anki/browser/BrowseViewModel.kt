// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.search.BrowserRow
import anki.search.SearchNode
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.backend.stripHTML
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/** A card in the list: its sort field, then its deck and when it is due. */
data class BrowseRow(
    val title: String,
    val detail: String,
    val isSuspended: Boolean,
)

/**
 * The plain card browser (owner, 2026-09-17): a search, the cards it finds, a selection, and a few
 * actions on the selection. A stand-in until the browser is redesigned.
 *
 * Only card ids are held; a row's text is read when it is shown, so a deck of many thousand cards
 * costs one search.
 */
class BrowseViewModel(
    savedState: SavedStateHandle,
) : ViewModel() {
    var search by mutableStateOf("")

    /** null while searching. */
    var cardIds: List<CardId>? by mutableStateOf(null)
        private set

    var selected: Set<CardId> by mutableStateOf(emptySet())
        private set

    /** Why the search failed, e.g. a mistyped search. */
    var error: String? by mutableStateOf(null)
        private set

    /** Bumped after every change, so shown rows are read again. */
    var version by mutableIntStateOf(0)
        private set

    /** The first search, of the deck the browser was opened for. */
    val initialSearch =
        viewModelScope.launch {
            val deckId = savedState.get<DeckId>(ARG_DECK_ID)
            search =
                if (deckId == null) {
                    "deck:*"
                } else {
                    withCol { buildSearchString(listOf(SearchNode.newBuilder().setDeck(decks.name(deckId)).build())) }
                }
            find()
        }

    fun runSearch() = viewModelScope.launch { find() }

    private suspend fun find() {
        cardIds = null
        error = null
        val query = search
        val found =
            try {
                withCol {
                    backend.setActiveBrowserColumns(COLUMNS)
                    findCards(query)
                }
            } catch (e: BackendException) {
                Timber.i(e, "Browse: search failed")
                error = e.localizedMessage
                emptyList()
            }
        cardIds = found
        selected = selected intersect found.toSet()
        version++
    }

    fun toggle(cardId: CardId) {
        selected = if (cardId in selected) selected - cardId else selected + cardId
    }

    /** Selects every card found, or none when all are already selected. */
    fun toggleAll() {
        val all = cardIds.orEmpty()
        selected = if (all.isNotEmpty() && selected.size == all.size) emptySet() else all.toSet()
    }

    suspend fun row(cardId: CardId): BrowseRow =
        withCol {
            val row = browserRowForId(cardId)
            val cells = row.cellsList.map { stripHTML(it.text).trim() }
            BrowseRow(
                title = cells.getOrElse(0) { "" },
                detail = cells.drop(1).filter { it.isNotEmpty() }.joinToString(" · "),
                isSuspended = row.color == BrowserRow.Color.COLOR_SUSPENDED,
            )
        }

    fun resetProgress() = change { sched.forgetCards(it) }

    fun suspend() = change { sched.suspendCards(it).changes }

    fun unsuspend() = change { sched.unsuspendCards(it) }

    fun deleteNotes() = change { removeNotes(cardIds = it).changes }

    fun undo() =
        viewModelScope.launch {
            undoableOp { undo() }
            find()
        }

    private fun change(op: com.ichi2.anki.libanki.Collection.(List<CardId>) -> OpChanges) =
        viewModelScope.launch {
            val ids = selected.toList()
            if (ids.isEmpty()) return@launch
            Timber.i("Browse: changing %d cards", ids.size)
            undoableOp { op(ids) }
            find()
        }

    companion object {
        const val ARG_DECK_ID = "deckId"

        /** Sort field, deck and due, for a row's title and detail. */
        private val COLUMNS = listOf("noteFld", "deck", "cardDue")
    }
}

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
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.backend.stripHTML
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/** A card in the list: its question, then its deck and when it is due. */
data class BrowseRow(
    val question: String,
    val detail: String,
    val isSuspended: Boolean,
)

/** What each filter can be set to, given the other filters: only tags and note types that occur. */
data class FilterOptions(
    val decks: List<String>,
    val tags: List<String>,
    val noteTypes: List<String>,
    val flags: List<FlagFilter>,
)

/**
 * The card browser (owner's layout A, 2026-09-17): filters that narrow the cards down, then the
 * cards, sorted, with actions on all of them or on those ticked in select mode.
 *
 * Only card ids are held; a row's text is read when it is shown, so a collection of many thousand
 * cards costs one search.
 */
class BrowseViewModel(
    savedState: SavedStateHandle,
) : ViewModel() {
    var filters by mutableStateOf(BrowseFilters())
        private set

    /** null until first worked out. */
    var options: FilterOptions? by mutableStateOf(null)
        private set

    /** How many cards the filters find; null while counting. */
    var count: Int? by mutableStateOf(null)
        private set

    /** Why the search failed. */
    var error: String? by mutableStateOf(null)
        private set

    var isShowingCards by mutableStateOf(false)
        private set

    var sort by mutableStateOf(BrowseSort.SortField)
        private set

    var isReversed by mutableStateOf(false)
        private set

    /** The cards shown, sorted; null while searching. */
    var cardIds: List<CardId>? by mutableStateOf(null)
        private set

    var isSelecting by mutableStateOf(false)
        private set

    var selected: Set<CardId> by mutableStateOf(emptySet())
        private set

    /** Bumped after every change, so shown rows are read again. */
    var version by mutableIntStateOf(0)
        private set

    private var refreshJob: Job? = null

    /** The first count, with the deck the browser was opened for already chosen. */
    val initialLoad: Job =
        viewModelScope.launch {
            savedState.get<DeckId>(ARG_DECK_ID)?.let { deckId ->
                filters = filters.copy(decks = setOf(withCol { decks.name(deckId) }))
            }
            refresh()
        }

    fun setFilters(new: BrowseFilters): Job {
        filters = new
        refreshJob?.cancel()
        return viewModelScope.launch { refresh() }.also { refreshJob = it }
    }

    /** Opens the list of cards the filters find. */
    fun showCards(): Job {
        isShowingCards = true
        return viewModelScope.launch { findCards() }
    }

    /** Back from the cards to the filters. */
    fun showFilters() {
        isShowingCards = false
        isSelecting = false
        selected = emptySet()
        refreshJob = viewModelScope.launch { refresh() }
    }

    fun setSort(
        sort: BrowseSort,
        reversed: Boolean,
    ): Job {
        this.sort = sort
        isReversed = reversed
        return viewModelScope.launch { findCards() }
    }

    fun toggleSelecting() {
        isSelecting = !isSelecting
        selected = emptySet()
    }

    fun toggle(cardId: CardId) {
        selected = if (cardId in selected) selected - cardId else selected + cardId
    }

    /** Ticks every card shown, or none when all already are. */
    fun toggleAll() {
        val all = cardIds.orEmpty()
        selected = if (all.isNotEmpty() && selected.size == all.size) emptySet() else all.toSet()
    }

    /** The cards an action applies to: the ticked ones while selecting, otherwise every card shown. */
    val targets: List<CardId>
        get() = if (isSelecting) cardIds.orEmpty().filter { it in selected } else cardIds.orEmpty()

    suspend fun row(cardId: CardId): BrowseRow =
        withCol {
            val row = browserRowForId(cardId)
            val cells = row.cellsList.map { stripHTML(it.text).replace(WHITESPACE, " ").trim() }
            BrowseRow(
                question = cells.getOrElse(0) { "" },
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
            findCards()
        }

    /** Rows may have changed elsewhere, e.g. a note edited from here. */
    fun reload() {
        viewModelScope.launch { if (isShowingCards) findCards() else refresh() }
    }

    private fun change(op: Collection.(List<CardId>) -> OpChanges) =
        viewModelScope.launch {
            val ids = targets
            if (ids.isEmpty()) return@launch
            Timber.i("Browse: changing %d cards", ids.size)
            undoableOp { op(ids) }
            findCards()
        }

    /** Works out the count and each filter's choices for the current filters. */
    private suspend fun refresh() {
        count = null
        error = null
        val current = filters
        try {
            val (found, newOptions) = withCol { findCards(searchFor(current)).size to filterOptions(current) }
            count = found
            options = newOptions
        } catch (e: BackendException) {
            Timber.i(e, "Browse: counting failed")
            error = e.localizedMessage
            count = 0
        }
    }

    private suspend fun findCards() {
        cardIds = null
        error = null
        val current = filters
        val found =
            try {
                withCol {
                    backend.setActiveBrowserColumns(COLUMNS)
                    findCards(searchFor(current), sortOrder(sort, isReversed))
                }
            } catch (e: BackendException) {
                Timber.i(e, "Browse: search failed")
                error = e.localizedMessage
                emptyList()
            }
        cardIds = found
        count = found.size
        selected = selected intersect found.toSet()
        version++
    }

    companion object {
        const val ARG_DECK_ID = "deckId"

        /** Question, deck and due: a row's text and its detail line. */
        private val COLUMNS = listOf("question", "deck", "cardDue")

        private val WHITESPACE = Regex("\\s+")
    }
}

/**
 * The choices for each filter: every deck, and only the tags, note types and flags found on the
 * cards the *other* filters match, plus whatever is already chosen so it can be unchosen.
 */
private fun Collection.filterOptions(filters: BrowseFilters): FilterOptions {
    // short `in (…)` lists: a whole collection's ids in one statement is slow to parse
    fun <T> inChunks(
        ids: List<Long>,
        query: (String) -> List<T>,
    ): List<T> = ids.chunked(900).flatMap { chunk -> query(chunk.joinToString(",")) }

    val tagNotes = findNotes(searchFor(filters, except = FilterPart.Tag))
    val tags =
        inChunks(tagNotes) { db.queryStringList("select tags from notes where id in ($it)") }
            .flatMap { it.split(' ') }
            .filter { it.isNotEmpty() }
            .toSet() + filters.tags

    val typeNotes = findNotes(searchFor(filters, except = FilterPart.NoteType))
    val noteTypes =
        inChunks(typeNotes) { db.queryLongList("select distinct mid from notes where id in ($it)") }
            .toSet()
            .mapNotNull { notetypes.get(it)?.name }
            .toSet() + filters.noteTypes

    val flagCards = findCards(searchFor(filters, except = FilterPart.Flag))
    val flagValues = inChunks(flagCards) { db.queryLongList("select distinct flags & 7 from cards where id in ($it)") }.toSet()
    val flags = FlagFilter.entries.filter { it.value.toLong() in flagValues || it in filters.flags }

    return FilterOptions(
        // in deck list order, so subdecks follow their deck
        decks = decks.allNamesAndIds(skipEmptyDefault = true).map { it.name },
        tags = tags.sortedWith(String.CASE_INSENSITIVE_ORDER),
        noteTypes = noteTypes.sortedWith(String.CASE_INSENSITIVE_ORDER),
        flags = flags,
    )
}

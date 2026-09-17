// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import anki.i18n.FormatTimespanRequest
import anki.search.SearchNode
import anki.stats.GraphPreferences
import anki.stats.GraphsResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.stats.RevlogRange
import com.ichi2.anki.stats.StatisticsScreenMMD
import com.ichi2.anki.stats.StatsDeck
import com.ichi2.anki.stats.StatsFormat
import com.ichi2.anki.stats.dayDetails
import com.ichi2.compose.mmd.ComposeHostFragment

/**
 * Statistics as a native page: the backend's graphs data drawn with MMD rows and E Ink charts.
 * The header names the deck being shown; a row on the page picks another deck or the whole collection.
 *
 * It was the backend's web page, restyled; see `StatisticsScreenMMD`. Saving the graphs as a PDF
 * stays gone: printing has no place on the Kompakt.
 */
class Statistics : ComposeHostFragment() {
    /** The deck shown, or null for the whole collection. */
    private var deckId: Long? by mutableStateOf(null)

    /** The header's title: the deck's name, or "Collection". */
    @VisibleForTesting
    internal var title: String by mutableStateOf("")

    private var revlogRange by mutableStateOf(RevlogRange.Year)
    private var data: GraphsResponse? by mutableStateOf(null)
    private var prefs: GraphPreferences? by mutableStateOf(null)

    /** "All decks" (the collection), then every deck but filtered ones, in deck list order. */
    private var decks: List<StatsDeck> by mutableStateOf(emptyList())

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        registerDeckSelectedHandler(action = ::onDeckSelected)
        savedInstanceState?.getString(KEY_REVLOG_RANGE)?.let { revlogRange = RevlogRange.valueOf(it) }
        launchCatchingTask {
            decks =
                listOf(StatsDeck(null, TR.statisticsRangeCollection())) +
                withCol { decks.allNamesAndIds(skipEmptyDefault = true, includeFiltered = false).map { StatsDeck(it.id, it.name) } }
            prefs = withCol { backend.getGraphPreferences() }
            val saved = savedInstanceState?.getLong(KEY_DECK_ID, NO_DECK) ?: withCol { decks.current().id }
            if (saved == COLLECTION) {
                showCollection()
            } else {
                showDeck(saved.takeIf { it != NO_DECK } ?: withCol { decks.current().id })
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val locale = LocalConfiguration.current.locales[0]
        val fmt =
            remember(locale) {
                StatsFormat(TR, locale) { seconds ->
                    CollectionManager.getBackend().formatTimespan(seconds.toFloat(), FormatTimespanRequest.Context.PRECISE)
                }
            }
        val search = searchFor(deckId)
        LaunchedEffect(search, revlogRange) {
            if (search == null) return@LaunchedEffect
            // the loading indicator shows while a deck or history range loads, rather than numbers
            // that no longer match the header
            data = null
            data = withCol { backend.graphs(search, revlogRange.days) }
        }
        StatisticsScreenMMD(
            title = title,
            data = data.takeIf { prefs != null },
            prefs = prefs ?: GraphPreferences.getDefaultInstance(),
            revlogRange = revlogRange,
            fmt = fmt,
            locale = locale,
            decks = decks,
            selectedDeckId = if (deckId == COLLECTION) null else deckId,
            onDeckSelected = { deck ->
                launchCatchingTask { if (deck.id == null) showCollection() else showDeck(deck.id, deck.name) }
            },
            onRevlogRangeChange = { revlogRange = it },
            onPrefsChange = ::savePrefs,
            dayDetails = { daysAgo -> withCol { dayDetails(daysAgo, search.orEmpty()) } },
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        )
    }

    /** The search for [deckId]'s cards, or "" for the collection; null until a deck is chosen. */
    private var searches by mutableStateOf(mapOf<Long, String>())

    private fun searchFor(deckId: Long?): String? = if (deckId == null) null else searches[deckId]

    /** The header action: one deck with its subdecks, or the whole collection; not a filtered deck. */
    @VisibleForTesting
    internal fun showDeckPicker() {
        startDeckSelection(allowAll = true, allowFiltered = false, skipEmptyDefault = true)
    }

    @VisibleForTesting
    internal fun onDeckSelected(deck: SelectableDeck?) {
        when (deck) {
            null -> return
            SelectableDeck.AllDecks -> launchCatchingTask { showCollection() }
            is SelectableDeck.Deck -> launchCatchingTask { showDeck(deck.deckId, deck.name) }
        }
    }

    private suspend fun showDeck(
        id: Long,
        name: String? = null,
    ) {
        val (deckName, search) =
            withCol {
                val deckName = name ?: decks.name(id)
                deckName to buildSearchString(listOf(SearchNode.newBuilder().setDeck(deckName).build()))
            }
        title = deckName
        searches = searches + (id to search)
        deckId = id
    }

    private fun showCollection() {
        title = TR.statisticsRangeCollection()
        searches = searches + (COLLECTION to "")
        deckId = COLLECTION
    }

    private fun savePrefs(updated: GraphPreferences) {
        prefs = updated
        launchCatchingTask { withCol { backend.setGraphPreferences(updated) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        deckId?.let { outState.putLong(KEY_DECK_ID, it) }
        outState.putString(KEY_REVLOG_RANGE, revlogRange.name)
    }

    companion object {
        private const val KEY_DECK_ID = "key_deck_id"
        private const val KEY_REVLOG_RANGE = "key_revlog_range"

        /** [deckId] for the whole collection; deck ids are positive. */
        private const val COLLECTION = -1L
        private const val NO_DECK = 0L
    }
}

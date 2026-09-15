// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Context
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.time.SECONDS_PER_DAY
import com.ichi2.anki.common.time.TIME_HOUR
import com.ichi2.anki.common.time.TIME_MINUTE
import com.ichi2.anki.deckpage.DeckPageUiState
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import timber.log.Timber
import kotlin.math.round

/** State for the deck page: the selected deck's counts, description and actions. */
class StudyOptionsViewModel : ViewModel() {
    val flowOfDeckPage: StateFlow<DeckPageUiState>
        field = MutableStateFlow<DeckPageUiState>(DeckPageUiState.Loading)

    var isFilteredDeck: Boolean = false
        private set

    var haveBuried: Boolean = false
        private set

    var selectedDeckId: DeckId = 0L
        private set

    /** Full name of the selected deck, e.g. `Language::Verbs`. */
    var deckFullName: String? = null
        private set

    private var cardCount: Int = 0

    /** Reloads the deck page from the collection. */
    fun refreshData(): Job =
        viewModelScope.launch {
            if (!CollectionManager.isOpenUnsafe()) return@launch
            val state = withCol { deckPageState() }
            flowOfDeckPage.value = state
        }

    suspend fun rebuildCram() {
        Timber.d("DeckPage: rebuild filtered deck")
        undoableOp { sched.rebuildFilteredDeck(decks.selected()) }
        refreshData().join()
    }

    suspend fun emptyCram() {
        Timber.d("DeckPage: empty filtered deck")
        undoableOp { sched.emptyFilteredDeck(decks.selected()) }
        refreshData().join()
    }

    fun unbury(): Job =
        viewModelScope.launch {
            undoableOp<OpChanges> { sched.unburyDeck(decks.getCurrentId()) }
            refreshData().join()
        }

    /** Deletes the selected deck, its subdecks and their cards. */
    suspend fun deleteDeck() {
        val deckId = selectedDeckId
        Timber.i("DeckPage: deleting deck %d", deckId)
        undoableOp { decks.remove(listOf(deckId)) }
    }

    /** The confirmation text for [deleteDeck]. */
    fun deleteMessage(context: Context): String {
        val name = deckFullName ?: ""
        val html =
            if (isFilteredDeck) {
                context.getString(R.string.delete_cram_deck_message, name)
            } else {
                context.resources.getQuantityString(R.plurals.delete_deck_message, cardCount, name, cardCount)
            }
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }

    sealed interface RenameResult {
        data object Renamed : RenameResult

        /** The name normalises to the current one. */
        data object Unchanged : RenameResult

        class Invalid(
            val message: (Context) -> String,
        ) : RenameResult
    }

    /** Renames the selected deck to [newName], or explains why it cannot. */
    suspend fun renameDeck(newName: String): RenameResult {
        val deckId = selectedDeckId
        val name = newName.trim()
        if (!Decks.isValidDeckName(name)) {
            return RenameResult.Invalid { it.getString(R.string.invalid_deck_name) }
        }
        val existing = withCol { decks.idForName(name) }
        if (existing != null && existing != deckId) {
            return RenameResult.Invalid { it.getString(R.string.error_name_exists) }
        }
        return try {
            val changed = withCol { decks.rename(deckId, name).deck }
            refreshData().join()
            if (changed) RenameResult.Renamed else RenameResult.Unchanged
        } catch (e: BackendDeckIsFilteredException) {
            Timber.w(e)
            RenameResult.Invalid { e.localizedMessage ?: e.message ?: "" }
        }
    }

    /**
     * See https://github.com/ankitects/anki/blob/b05c9d15986ab4e33daa2a47a947efb066bb69b6/qt/aqt/overview.py#L226-L272
     */
    private fun Collection.deckPageState(): DeckPageUiState {
        val deck = decks.current()
        val deckId = deck.id
        val counts = sched.counts()
        val fullName = deck.getString("name")
        val isDynamic = deck.isFiltered
        val numberOfCards = decks.cardCount(deckId, includeSubdecks = true)

        selectedDeckId = decks.selected()
        isFilteredDeck = isDynamic
        haveBuried = sched.haveBuried()
        deckFullName = fullName
        cardCount = numberOfCards

        val displayName = Decks.basename(fullName)
        val totalDue = counts.new + counts.lrn + counts.rev
        return when {
            numberOfCards == 0 && !isDynamic -> DeckPageUiState.Empty(deckName = displayName)
            totalDue == 0 ->
                DeckPageUiState.Congrats(
                    deckName = displayName,
                    message = congratsMessage(),
                    canUnbury = haveBuried,
                    canCustomStudy = !isDynamic,
                )
            else ->
                DeckPageUiState.Study(
                    deckName = displayName,
                    description = if (isDynamic) null else plainDescription(deck.description, deck.descriptionAsMarkdown),
                    newCount = counts.new,
                    learnCount = counts.lrn,
                    reviewCount = counts.rev,
                    isFiltered = isDynamic,
                )
        }
    }

    /** The deck description as plain text: the E Ink page shows no HTML, images or links. */
    private fun Collection.plainDescription(
        description: String,
        isMarkdown: Boolean,
    ): String? {
        if (description.isBlank()) return null
        @Suppress("DEPRECATION")
        val html = if (isMarkdown) renderMarkdown(description, sanitize = true) else description
        return HtmlCompat
            .fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
            .ifBlank { null }
    }

    // based on https://github.com/ankitects/anki/blob/9b4dd54312de8798a3f2bee07892bb3a488d1f9b/ts/routes/congrats/lib.ts#L8C17-L8C34
    private fun Collection.congratsMessage(): String {
        val secsUntilNextLearn = sched.congratulationsInfo().secsUntilNextLearn
        val resources = AnkiDroidApp.appResources
        if (secsUntilNextLearn >= SECONDS_PER_DAY) {
            return resources.getString(R.string.studyoptions_congrats_finished)
        }
        val (unit, amount) =
            when {
                secsUntilNextLearn < TIME_MINUTE -> "seconds" to secsUntilNextLearn.toDouble()
                secsUntilNextLearn < TIME_HOUR -> "minutes" to secsUntilNextLearn / TIME_MINUTE
                else -> "hours" to secsUntilNextLearn / TIME_HOUR
            }
        return resources.getString(R.string.studyoptions_congrats_next_due_in, TR.schedulingNextLearnDue(unit, round(amount).toInt()))
    }
}

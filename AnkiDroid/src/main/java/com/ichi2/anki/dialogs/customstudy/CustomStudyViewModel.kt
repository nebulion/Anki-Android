// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs.customstudy

import android.widget.AdapterView
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.copy
import anki.scheduler.customStudyRequest
import anki.search.SearchNode
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.EXTEND_NEW
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.EXTEND_REV
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_AHEAD
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_FORGOT
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_PREVIEW
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption.STUDY_TAGS
import com.ichi2.anki.dialogs.customstudy.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import com.ichi2.utils.coMeasureTime
import timber.log.Timber

/**
 * Everything custom study decides, apart from how it looks (`CustomStudyMMD.kt`): which options are
 * available, what each one suggests, whether an amount can be used, and the backend request.
 *
 * Custom study either
 * 1. raises today's new or review limit of the deck, or
 * 2. creates a filtered "Custom Study Session" deck to study outside the usual schedule.
 *
 * Upstream: [customstudy.py](https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py)
 */
class CustomStudyViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /**
     * The index of the selected [CustomStudyCardState] for [ContextMenuOption.STUDY_TAGS], or
     * [AdapterView.INVALID_POSITION] if there's no selection.
     */
    var selectedCardStateIndex: Int = AdapterView.INVALID_POSITION
        get() = savedStateHandle.get<Int>(KEY_CARDS_SELECTION_INDEX) ?: AdapterView.INVALID_POSITION
        set(value) {
            field = value
            savedStateHandle[KEY_CARDS_SELECTION_INDEX] = value
        }

    /** The [DeckId] of the [Deck] the custom study is for. */
    var deckId: DeckId
        get() = savedStateHandle.get<DeckId>(KEY_DID) ?: error("Deck id was not provided!")
        set(value) {
            if (savedStateHandle.get<DeckId>(KEY_DID) != value) defaults = null
            savedStateHandle[KEY_DID] = value
        }

    /*
     * Translates the user's selection into a specific study type.
     * This prevents the app from "forgetting" user's choice (e.g., Due Cards)
     * even if the tag selection is skipped.
     */
    val selectedKind: CramKind
        get() =
            if (selectedCardStateIndex != AdapterView.INVALID_POSITION) {
                CustomStudyCardState.entries[selectedCardStateIndex].kind
            } else {
                CramKind.CRAM_KIND_NEW
            }

    /** The backend's defaults for [deckId]; loading them can take over a second. */
    private var defaults: CustomStudyDefaults? = null

    suspend fun loadDefaults(): CustomStudyDefaults =
        defaults ?: coMeasureTime("loadCustomStudyDefaults") {
            withCol { sched.customStudyDefaults(deckId).toDomainModel() }
        }.also { defaults = it }

    /** Whether [option] can be used for this deck, e.g. there are new cards to extend by. */
    suspend fun isAvailable(option: ContextMenuOption): Boolean = option.checkAvailability?.invoke(loadDefaults()) ?: true

    /** How many cards an 'extend' option has to offer: `Available new cards: 1 (2 in subdecks)` */
    suspend fun availabilityLabel(option: ContextMenuOption): String? =
        when (option) {
            EXTEND_NEW -> loadDefaults().labelForNewQueueAvailable()
            EXTEND_REV -> loadDefaults().labelForReviewQueueAvailable()
            STUDY_FORGOT, STUDY_AHEAD, STUDY_PREVIEW, STUDY_TAGS -> null
        }

    /** The amount suggested for [option]: the backend's for the limits, the last one used otherwise. */
    suspend fun defaultAmount(option: ContextMenuOption): Int {
        val prefs = AnkiDroidApp.sharedPrefs()
        return when (option) {
            EXTEND_NEW -> loadDefaults().extendNew.initialValue
            EXTEND_REV -> loadDefaults().extendReview.initialValue
            STUDY_FORGOT -> prefs.getInt(PREF_FORGOTTEN_DAYS, 1)
            STUDY_AHEAD -> prefs.getInt(PREF_AHEAD_DAYS, 1)
            STUDY_PREVIEW -> prefs.getInt(PREF_PREVIEW_DAYS, 1)
            // not upstream (as of Anki 25.02)
            STUDY_TAGS -> prefs.getInt(PREF_AMOUNT_OF_CARDS, 100)
        }
    }

    /**
     * The text of the amount field after the user proposes [proposed], given it was [previous].
     *
     * Limits may be lowered, so they take a sign. 'Review ahead' takes at most five digits and no
     * leading zero: "05" keeps "0".
     */
    fun acceptAmountInput(
        option: ContextMenuOption,
        previous: String,
        proposed: String,
    ): String =
        when (option) {
            EXTEND_NEW, EXTEND_REV -> if (proposed.matches(SIGNED_NUMBER)) proposed else previous
            STUDY_AHEAD ->
                when {
                    !proposed.all(Char::isDigit) -> previous
                    proposed.length > 1 && proposed.startsWith("0") -> previous
                    else -> proposed.take(MAX_AHEAD_DIGITS)
                }
            STUDY_FORGOT, STUDY_PREVIEW, STUDY_TAGS -> if (proposed.all(Char::isDigit)) proposed else previous
        }

    /** Why [amount] cannot be used for [option], or null if it can (or nothing is entered yet). */
    suspend fun amountProblem(
        option: ContextMenuOption,
        amount: String,
    ): AmountProblem? {
        if (option != STUDY_AHEAD) return null
        val days = amount.toIntOrNull() ?: return null
        if (days == 0) return AmountProblem.BelowMinimum(1)
        return if (hasCardsDueWithin(days)) null else AmountProblem.NoCardsMatched
    }

    /** Whether [amount] can be submitted for [option], given its [problem]. */
    fun canSubmit(
        amount: String,
        problem: AmountProblem?,
    ): Boolean {
        val value = amount.toIntOrNull()
        return value != null && value != 0 && problem == null
    }

    /** Whether the deck has cards due in the next [days] days, as 'review ahead' would select them */
    suspend fun hasCardsDueWithin(days: Int): Boolean =
        withCol {
            val search =
                listOf(
                    SearchNode.newBuilder().setDeck(decks.name(deckId)).build(),
                    // prop:due<=days
                    SearchNode.newBuilder().setDueInDays(days).build(),
                )
            findCards(buildSearchString(search)).isNotEmpty()
        }

    /** The deck's tags, and which of them the backend suggests including, for 'study by tag'. */
    suspend fun tags(): Pair<List<String>, Set<String>> {
        val tags = loadDefaults().tags
        return tags.map { it.name } to tags.filter { it.include }.map { it.name }.toSet()
    }

    /**
     * Runs [option] with [amount], and remembers the amount as the next suggestion.
     * [tagsToInclude] and [tagsToExclude] apply only to [ContextMenuOption.STUDY_TAGS].
     */
    suspend fun customStudy(
        option: ContextMenuOption,
        amount: Int,
        tagsToInclude: List<String> = emptyList(),
        tagsToExclude: List<String> = emptyList(),
    ): CustomStudyAction {
        Timber.i("Custom study: %s; input = %d", option, amount)
        val cramKind = selectedKind
        val request =
            customStudyRequest {
                deckId = this@CustomStudyViewModel.deckId
                when (option) {
                    EXTEND_NEW -> newLimitDelta = amount
                    EXTEND_REV -> reviewLimitDelta = amount
                    STUDY_FORGOT -> forgotDays = amount
                    STUDY_AHEAD -> reviewAheadDays = amount
                    STUDY_PREVIEW -> previewDays = amount
                    STUDY_TAGS -> {
                        // https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/customstudy.py#L169-L177
                        cram =
                            cram.copy {
                                kind = cramKind
                                cardLimit = amount
                                this.tagsToInclude.addAll(tagsToInclude)
                                this.tagsToExclude.addAll(tagsToExclude)
                            }
                    }
                }
            }
        undoableOp { sched.customStudy(request) }
        defaults = null

        // save the amount as the next suggestion (not in upstream)
        AnkiDroidApp.sharedPrefs().edit {
            when (option) {
                STUDY_FORGOT -> putInt(PREF_FORGOTTEN_DAYS, amount)
                STUDY_AHEAD -> putInt(PREF_AHEAD_DAYS, amount)
                STUDY_PREVIEW -> putInt(PREF_PREVIEW_DAYS, amount)
                STUDY_TAGS -> putInt(PREF_AMOUNT_OF_CARDS, amount)
                // the backend suggests the last extension itself
                EXTEND_NEW, EXTEND_REV -> {}
            }
        }
        return when (option) {
            EXTEND_NEW, EXTEND_REV -> CustomStudyAction.EXTEND_STUDY_LIMITS
            STUDY_FORGOT, STUDY_AHEAD, STUDY_PREVIEW, STUDY_TAGS -> CustomStudyAction.CUSTOM_STUDY_SESSION
        }
    }

    /** Why an amount cannot be used. */
    sealed interface AmountProblem {
        data class BelowMinimum(
            val minimum: Int,
        ) : AmountProblem

        data object NoCardsMatched : AmountProblem
    }

    companion object {
        const val KEY_DID = "key_did"
        private const val KEY_CARDS_SELECTION_INDEX = "key_cards_selection_index"

        private const val PREF_FORGOTTEN_DAYS = "forgottenDays"
        private const val PREF_AHEAD_DAYS = "aheadDays"
        private const val PREF_PREVIEW_DAYS = "previewDays"
        private const val PREF_AMOUNT_OF_CARDS = "amountOfCards"

        private const val MAX_AHEAD_DIGITS = 5
        private val SIGNED_NUMBER = Regex("-?\\d*")
    }
}

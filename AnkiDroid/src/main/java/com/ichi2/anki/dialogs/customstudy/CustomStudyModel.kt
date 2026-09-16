// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// Moved out of CustomStudyDialog.kt when the dialog became MMD panels (CustomStudyMMD.kt).

package com.ichi2.anki.dialogs.customstudy

import android.content.res.Resources
import android.os.Bundle
import android.os.Parcelable
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import com.ichi2.anki.CollectionManager.TR
import kotlinx.parcelize.Parcelize

/**
 * Represents actions for managing custom study sessions and extending study limits.
 * These actions are passed between fragments and activities via the FragmentResult API.
 */
enum class CustomStudyAction {
    EXTEND_STUDY_LIMITS,
    CUSTOM_STUDY_SESSION,
    ;

    companion object {
        const val REQUEST_KEY = "CustomStudyDialog"
        const val BUNDLE_KEY = "action"

        /** Extracts a [CustomStudyAction] from a [Bundle] */
        fun fromBundle(bundle: Bundle): CustomStudyAction =
            bundle.getInt(CustomStudyAction.BUNDLE_KEY).let { actionOrdinal ->
                entries.first { it.ordinal == actionOrdinal }
            }
    }
}

/**
 * Context menu options shown in the custom study dialog.
 *
 * @param checkAvailability Whether the menu option is available
 */
enum class ContextMenuOption(
    val getTitle: Resources.() -> String,
    val checkAvailability: ((CustomStudyDefaults) -> Boolean)? = null,
) {
    /** Increase today's new card limit */
    EXTEND_NEW({ TR.customStudyIncreaseTodaysNewCardLimit() }, checkAvailability = { it.extendNew.isUsable }),

    /** Increase today's review card limit */
    EXTEND_REV({ TR.customStudyIncreaseTodaysReviewCardLimit() }, checkAvailability = { it.extendReview.isUsable }),

    /** Review forgotten cards */
    STUDY_FORGOT({ TR.customStudyReviewForgottenCards() }),

    /** Review ahead */
    STUDY_AHEAD({ TR.customStudyReviewAhead() }),

    /** Preview new cards */
    STUDY_PREVIEW({ TR.customStudyPreviewNewCards() }),

    /** Limit to particular tags */
    STUDY_TAGS({ TR.customStudyStudyByCardStateOrTag() }),
}

@Parcelize
enum class CustomStudyCardState(
    val labelProducer: () -> String,
    val kind: CramKind,
) : Parcelable {
    NewCardsOnly({ TR.customStudyNewCardsOnly() }, CramKind.CRAM_KIND_NEW),
    DueCardsOnly({ TR.customStudyDueCardsOnly() }, CramKind.CRAM_KIND_DUE),
    ReviewCardsRandom({ TR.customStudyAllReviewCardsInRandomOrder() }, CramKind.CRAM_KIND_REVIEW),
    AllCardsRandom({ TR.customStudyAllCardsInRandomOrderDont() }, CramKind.CRAM_KIND_ALL),
}

/**
 * Default values for extending deck limits, and default tag selection
 *
 * Adapter which documents [anki.scheduler.CustomStudyDefaultsResponse]
 *
 * Upstream: [sched.proto: CustomStudyDefaultsResponse](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyDefaultsResponse+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 */
class CustomStudyDefaults(
    val extendNew: ExtendLimits,
    val extendReview: ExtendLimits,
    @Suppress("unused")
    val tags: List<CustomStudyDefaultsResponse.Tag>,
) {
    /** Available new cards: 1 (2 in subdecks) */
    fun labelForNewQueueAvailable(): String = TR.customStudyAvailableNewCards2(extendNew.labelForCountWithChildren())

    /** Available review cards: 1 (2 in subdecks) */
    fun labelForReviewQueueAvailable(): String = TR.customStudyAvailableReviewCards2(extendReview.labelForCountWithChildren())

    /**
     * Data displayed to a user wanting to temporarily extend the daily limits of
     * either new/review cards for a deck
     *
     * Displays `Available new cards: 1 (2 in subdecks)` when a limit is reached with remaining cards:
     * ```
     * Deck (1)
     *   Deck::Child1 (1)
     *   Deck::Child2 (1)
     * ```
     */
    class ExtendLimits(
        /** The initial value to display in the input dialog */
        val initialValue: Int,
        /**
         * The number of pending cards in only the parent deck
         *
         * **Example**
         * Returns **1** when a limit is reached with remaining cards:
         * ```
         * Deck (1)
         *   Deck::Child1 (1)
         *   Deck::Child2 (1)
         * ```
         */
        val available: Int,
        /**
         * The sum of cards in only the child decks
         *
         * **Example**
         * * Returns **2** when a limit is reached  with remaining cards:
         * ```
         * Deck (1)
         *   Deck::Child1 (1)
         *   Deck::Child2 (1)
         * ```
         */
        val availableInChildren: Int,
    ) {
        /**
         * **Temporarily Disabled** - logic may be incorrect
         *
         * "Extend" only has an effect if there are pending cards in the target deck
         *
         * The number of pending cards in child decks is only informative
         */
        val isUsable
            get() = true // TODO: Confirm `available > 0` is correct; user feedback states subdecks are taken into account

        /**
         * A string representing the count of cards which have exceeded a deck limit
         *
         * `123 (456 in subdecks)` or `123`
         *
         * For use in either
         * [net.ankiweb.rsdroid.Translations.customStudyAvailableReviewCards2] or
         * [net.ankiweb.rsdroid.Translations.customStudyAvailableNewCards2]
         *
         */
        fun labelForCountWithChildren(): String =
            if (availableInChildren == 0) {
                available.toString()
            } else {
                "$available ${TR.customStudyAvailableChildCount(availableInChildren)}"
            }
    }

    companion object {
        fun CustomStudyDefaultsResponse.toDomainModel(): CustomStudyDefaults =
            CustomStudyDefaults(
                extendNew =
                    ExtendLimits(
                        initialValue = extendNew,
                        available = availableNew,
                        availableInChildren = availableNewInChildren,
                    ),
                extendReview =
                    ExtendLimits(
                        initialValue = extendReview,
                        available = availableReview,
                        availableInChildren = availableReviewInChildren,
                    ),
                tags = this.tagsList,
            )
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.widget.AdapterView
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.customStudyDefaultsResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.ContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState
import com.ichi2.anki.dialogs.customstudy.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel.AmountProblem
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.testutils.isJsonEqual
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Custom study's behaviour. The panels in `CustomStudyMMD.kt` only show what [CustomStudyViewModel]
 * decides, so the behaviour the old dialog's tests covered is asserted on the view model.
 */
@RunWith(AndroidJUnit4::class)
class CustomStudyDialogTest : RobolectricTest() {
    @Test
    fun `new custom study decks have expected structure - issue 6289`() =
        runTest {
            // we need a non-empty deck to custom study
            addBasicNote()
            val viewModel = viewModelFor()

            viewModel.customStudy(ContextMenuOption.STUDY_PREVIEW, viewModel.defaultAmount(ContextMenuOption.STUDY_PREVIEW))

            val customStudy = col.decks.current()
            assertThat("Custom Study should be filtered", customStudy.isFiltered)

            // remove timestamps to allow us to compare JSON
            customStudy.remove("id")
            customStudy.remove("mod")
            customStudy.remove("name")

            @Language("json")
            val expected =
                """
                {
                    "browserCollapsed": true,
                    "collapsed": true,
                    "delays": null,
                    "desc": "",
                    "dyn": 1,
                    "lrnToday": [0, 0],
                    "newToday": [0, 0],
                    "previewDelay": 10,
                    "previewAgainSecs": 60,
                    "previewHardSecs": 600,
                    "previewGoodSecs": 0,
                    "resched": false,
                    "revToday": [0, 0],
                    "separate": true,
                    "terms": [
                        ["is:new added:1 deck:Default", 99999, 5]
                    ],
                    "timeToday": [0, 0],
                    "usn": -1
                }
                """.trimIndent()
            assertThat(customStudy, isJsonEqual(expected))
        }

    @Test
    fun `previous value for 'increase new card limit' is suggested`() =
        runTest {
            // add cards to be sure we can extend successfully. Needs to be > 20
            repeat(23) { addBasicNote() }
            assertThat("'new' default value", defaultsOfDefaultDeck.extendNew.initialValue, equalTo(0))

            viewModelFor().customStudy(ContextMenuOption.EXTEND_NEW, 1)

            assertThat("'new' updated value", defaultsOfDefaultDeck.extendNew.initialValue, equalTo(1))
            assertThat("the next suggestion", viewModelFor().defaultAmount(ContextMenuOption.EXTEND_NEW), equalTo(1))
        }

    @Test
    fun `previous value for 'increase review card limit' is suggested`() =
        runTest {
            // Reduce review limit to 0, so we can successfully extend with just 1 review card.
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { rev.perDay = 0 }
            addRevBasicNoteDueToday("Review", "Today")
            assertThat("'review' default value", defaultsOfDefaultDeck.extendReview.initialValue, equalTo(0))

            viewModelFor().customStudy(ContextMenuOption.EXTEND_REV, 1)

            assertThat("'review' updated value", defaultsOfDefaultDeck.extendReview.initialValue, equalTo(1))
            assertThat("the next suggestion", viewModelFor().defaultAmount(ContextMenuOption.EXTEND_REV), equalTo(1))
        }

    @Test
    fun `creating a tags custom session uses selected card state`() =
        runTest {
            val testDeckId = addDeck("A")
            val n1 = addNoteToDeckA { addTag("testTag") }
            val n2 = addNoteToDeckA { addTag("anotherTag") }
            val n3 = addNoteToDeckA { addTag("testTag") }
            // target this specific card for custom studying
            n3.firstCard().update {
                due = col.sched.today
                queue = QueueType.Rev
                type = CardType.Rev
            }
            col.updateCard(n3.firstCard())
            assertThat(col.findCards("is:due"), equalTo(listOf(n3.firstCard().id)))
            // make sure there isn't a 'Custom Study Session' already present
            assertNull(col.decks.customStudySession)

            val viewModel = viewModelFor(testDeckId)
            viewModel.selectedCardStateIndex = CustomStudyCardState.DueCardsOnly.ordinal
            viewModel.customStudy(
                ContextMenuOption.STUDY_TAGS,
                viewModel.defaultAmount(ContextMenuOption.STUDY_TAGS),
                tagsToInclude = listOf("testTag"),
            )

            val customStudyDeck = col.decks.customStudySession
            assertNotNull(customStudyDeck)
            assertThat(col.decks.cardCount(customStudyDeck.id), equalTo(1))
            assertThat(n1.firstCard().did, equalTo(testDeckId))
            assertThat(n2.firstCard().did, equalTo(testDeckId))
            assertThat(n3.firstCard().did, equalTo(customStudyDeck.id))
            assertThat(n3.firstCard().oDid, equalTo(testDeckId))
        }

    @Test
    fun `the tags offered for 'study by tag' are the deck's`() =
        runTest {
            val deckId = addDeck("A")
            addNoteToDeckA { addTag("testTag") }

            val (names, _) = viewModelFor(deckId).tags()

            assertThat(names, equalTo(listOf("testTag")))
        }

    private fun addNoteToDeckA(setup: Note.() -> Unit): Note =
        addBasicNote().update {
            moveToDeck("A", false)
            setup()
        }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    fun `'increase new limit' is shown when there are new cards`() =
        runTest {
            CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(customStudyDefaultsResponse { availableNew = 1 }))
            assertTrue(viewModelFor().isAvailable(ContextMenuOption.EXTEND_NEW))
        }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    fun `'increase new limit' is not shown when there are no new cards`() =
        runTest {
            CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(customStudyDefaultsResponse { availableNew = 0 }))
            assertFalse(viewModelFor().isAvailable(ContextMenuOption.EXTEND_NEW))
        }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    fun `'increase review limit' is shown when there are new cards`() =
        runTest {
            CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(customStudyDefaultsResponse { availableReview = 1 }))
            assertTrue(viewModelFor().isAvailable(ContextMenuOption.EXTEND_REV))
        }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    fun `'increase review limit' is not shown when there are no new cards`() =
        runTest {
            CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(customStudyDefaultsResponse { availableReview = 0 }))
            assertFalse(viewModelFor().isAvailable(ContextMenuOption.EXTEND_REV))
        }

    @Test
    fun `selectedKind maps selected card state index to cram kind`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        CustomStudyCardState.entries.forEachIndexed { index, cardState ->
            viewModel.selectedCardStateIndex = index
            assertThat(viewModel.selectedKind, equalTo(cardState.kind))
        }
    }

    @Test
    fun `selectedKind defaults to new cards when no card state is selected`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        viewModel.selectedCardStateIndex = AdapterView.INVALID_POSITION

        assertThat(viewModel.selectedKind, equalTo(CramKind.CRAM_KIND_NEW))
    }

    @Test
    fun `'review ahead' rejects leading zeros and more than 5 digits`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())
        val ahead = ContextMenuOption.STUDY_AHEAD

        assertEquals("0", viewModel.acceptAmountInput(ahead, previous = "0", proposed = "05"), "a digit typed after '0' is rejected")
        assertEquals("12345", viewModel.acceptAmountInput(ahead, previous = "", proposed = "123456"))
        assertEquals("1", viewModel.acceptAmountInput(ahead, previous = "1", proposed = "1a"), "only digits")
    }

    @Test
    fun `limits can be lowered with a negative amount`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        assertEquals("-5", viewModel.acceptAmountInput(ContextMenuOption.EXTEND_NEW, previous = "-", proposed = "-5"))
        assertEquals(
            "5",
            viewModel.acceptAmountInput(ContextMenuOption.STUDY_FORGOT, previous = "5", proposed = "-5"),
            "days are not negative",
        )
    }

    @Test
    fun `'review ahead' disables Create when no cards are due in the period`() =
        runTest {
            // a card due tomorrow matches 'review ahead by 1 day'
            val card = addBasicNote().firstCard()
            card.update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 1
            }
            val viewModel = viewModelFor()

            val matching = viewModel.amountProblem(ContextMenuOption.STUDY_AHEAD, "1")
            assertNull(matching)
            assertTrue(viewModel.canSubmit("1", matching), "enabled when a card matches")

            val zero = viewModel.amountProblem(ContextMenuOption.STUDY_AHEAD, "0")
            assertFalse(viewModel.canSubmit("0", zero), "disabled for 0 days")

            card.update { due = col.sched.today + 30 }
            val nothing = viewModel.amountProblem(ContextMenuOption.STUDY_AHEAD, "1")
            assertEquals(AmountProblem.NoCardsMatched, nothing)
            assertFalse(viewModel.canSubmit("1", nothing), "disabled when nothing matches")
        }

    @Test
    fun `'review ahead' search does not treat the deck name as a pattern`() =
        runTest {
            // '_' is a single-character wildcard in a search: "A_B" must not match "AXB"
            val emptyDeckId = addDeck("A_B")
            addDeck("AXB")
            addNoteDueTomorrow(deckName = "AXB")

            assertFalse(viewModelFor(emptyDeckId).hasCardsDueWithin(1), "A_B has no cards")
        }

    @Test
    fun `'review ahead' search escapes the deck name`() =
        runTest {
            // '\' starts an escape sequence in a search
            val deckId = addDeck("""A\B""")
            addNoteDueTomorrow(deckName = """A\B""")

            assertTrue(viewModelFor(deckId).hasCardsDueWithin(1))
        }

    @Test
    fun `'review ahead' warns when 0 days is entered`() =
        runTest {
            val viewModel = viewModelFor()

            assertEquals(AmountProblem.BelowMinimum(1), viewModel.amountProblem(ContextMenuOption.STUDY_AHEAD, "0"))
            assertNull(viewModel.amountProblem(ContextMenuOption.STUDY_AHEAD, ""), "an empty field is not an error yet")
        }

    @Test
    fun `'review ahead' validates the default value on open`() =
        runTest {
            // the default is 1 day; the only card is due in 30 days
            addBasicNote().firstCard().update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 30
            }
            val viewModel = viewModelFor()
            val default = viewModel.defaultAmount(ContextMenuOption.STUDY_AHEAD).toString()

            assertEquals(AmountProblem.NoCardsMatched, viewModel.amountProblem(ContextMenuOption.STUDY_AHEAD, default))
        }

    /** Adds a note to [deckName] whose card is due tomorrow */
    private fun addNoteDueTomorrow(deckName: String) {
        addBasicNote().update { moveToDeck(deckName, false) }.firstCard().update {
            queue = QueueType.Rev
            type = CardType.Rev
            due = col.sched.today + 1
        }
    }

    private fun viewModelFor(deckId: DeckId = Consts.DEFAULT_DECK_ID) =
        CustomStudyViewModel(SavedStateHandle()).apply { this.deckId = deckId }

    private fun mockCollectionWithSchedulerReturning(response: CustomStudyDefaultsResponse) =
        mockk<Collection>(relaxed = true) {
            every { sched } returns
                mockk<Scheduler> {
                    every { customStudyDefaults(Consts.DEFAULT_DECK_ID) } returns response
                }
        }

    /** The current backend value of the custom study defaults for the default deck */
    private val defaultsOfDefaultDeck
        get() = col.sched.customStudyDefaults(Consts.DEFAULT_DECK_ID).toDomainModel()
}

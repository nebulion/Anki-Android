// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.StudyOptionsViewModel.RenameResult
import com.ichi2.anki.deckpage.DeckPageUiState
import com.ichi2.anki.deckpage.deckNameOrEmpty
import com.ichi2.testutils.ensureOpsExecuted
import kotlinx.coroutines.joinAll
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class StudyOptionsViewModelTest : RobolectricTest() {
    private val viewModel = StudyOptionsViewModel()

    private val state get() = viewModel.flowOfDeckPage.value

    @Test
    fun `initial state is Loading`() {
        assertIs<DeckPageUiState.Loading>(state)
    }

    @Test
    fun `refreshData - empty deck shows Empty state`() =
        runTest {
            col
            viewModel.refreshData().join()
            assertIs<DeckPageUiState.Empty>(state)
        }

    @Test
    fun `refreshData - deck with due cards shows Study state`() =
        runTest {
            addBasicNote("Front", "Back")

            viewModel.refreshData().join()

            val study = assertIs<DeckPageUiState.Study>(state)
            assertEquals(1, study.newCount)
            assertEquals(0, study.learnCount)
            assertEquals(0, study.reviewCount)
            assertFalse(study.isFiltered)
            assertEquals(1, study.totalCards, "total cards")
            assertEquals(1, study.totalNewCards, "total new cards")
            assertEquals(0, study.buriedCount, "buried")
        }

    @Test
    fun `refreshData - regular deck is not filtered`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            assertFalse(viewModel.isFilteredDeck)
        }

    @Test
    fun `refreshData - congrats state when no cards due`() =
        runTest {
            addBasicNote()
            withCol {
                while (sched.card != null) {
                    val card = sched.card!!
                    sched.answerCard(card, Rating.EASY)
                }
            }

            viewModel.refreshData().join()

            val congrats = assertIs<DeckPageUiState.Congrats>(state)
            assertTrue(congrats.message.isNotBlank(), "congrats message is shown")
            assertTrue(congrats.canCustomStudy)
        }

    @Test
    fun `refreshData - state flow emits updates`() =
        runTest {
            col
            viewModel.flowOfDeckPage.test {
                assertIs<DeckPageUiState.Loading>(awaitItem())

                viewModel.refreshData().join()
                assertIs<DeckPageUiState.Empty>(awaitItem())

                addBasicNote()
                viewModel.refreshData().join()
                assertIs<DeckPageUiState.Study>(awaitItem())
            }
        }

    @Test
    fun `refreshData - multiple cards counted correctly`() =
        runTest {
            repeat(5) { addBasicNote("Front $it", "Back $it") }

            viewModel.refreshData().join()

            assertEquals(5, assertIs<DeckPageUiState.Study>(state).newCount)
        }

    @Test
    fun `refreshData - deck name is the last name component`() =
        runTest {
            val deckId = addDeck("Language::Verbs")
            withCol { decks.select(deckId) }

            viewModel.refreshData().join()

            assertIs<DeckPageUiState.Empty>(state)
            assertEquals("Verbs", state.deckNameOrEmpty())
            assertEquals("Language::Verbs", viewModel.deckFullName)
        }

    @Test
    fun `rebuildCram - selects a filtered deck`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            viewModel.rebuildCram()

            assertTrue(viewModel.isFilteredDeck)
        }

    @Test
    fun `emptyCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            ensureOpsExecuted(1) {
                viewModel.emptyCram()
            }
        }

    @Test
    fun `rebuildCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            ensureOpsExecuted(1) {
                viewModel.rebuildCram()
            }
        }

    @Test
    fun `unbury - is undoable`() =
        runTest {
            addBasicNote()
            withCol {
                val card = sched.card!!
                sched.buryCards(listOf(card.id), true)
            }

            ensureOpsExecuted(1) {
                viewModel.unbury().join()
            }
        }

    @Test
    fun `haveBuried - false when no buried cards`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            assertFalse(viewModel.haveBuried)
        }

    @Test
    fun `refreshData - buried cards are not counted as due`() =
        runTest {
            addBasicNote("Front1", "Back1")
            addBasicNote("Front2", "Back2")
            withCol {
                val card = sched.card!!
                sched.buryCards(listOf(card.id), true)
            }

            viewModel.refreshData().join()

            assertTrue(viewModel.haveBuried, "expected buried cards")
            val study = assertIs<DeckPageUiState.Study>(state)
            assertEquals(1, study.newCount)
            assertTrue(study.buriedCount > 0, "the buried card is reported")
        }

    @Test
    fun `renameDeck - renames the selected deck`() =
        runTest {
            val deckId = addDeck("Old name")
            withCol { decks.select(deckId) }
            viewModel.refreshData().join()

            val result = viewModel.renameDeck("New name")

            assertIs<RenameResult.Renamed>(result)
            assertEquals("New name", withCol { decks.name(deckId) })
        }

    @Test
    fun `renameDeck - refuses a name another deck has`() =
        runTest {
            addDeck("Taken")
            val deckId = addDeck("Mine")
            withCol { decks.select(deckId) }
            viewModel.refreshData().join()

            val result = viewModel.renameDeck("Taken")

            assertIs<RenameResult.Invalid>(result)
            assertEquals("Mine", withCol { decks.name(deckId) })
        }

    @Test
    fun `renameDeck - refuses a blank name`() =
        runTest {
            val deckId = addDeck("Mine")
            withCol { decks.select(deckId) }
            viewModel.refreshData().join()

            assertIs<RenameResult.Invalid>(viewModel.renameDeck("   "))
        }

    @Test
    fun `deleteDeck - removes the selected deck`() =
        runTest {
            val deckId = addDeck("Doomed")
            withCol { decks.select(deckId) }
            viewModel.refreshData().join()

            viewModel.deleteDeck()

            assertEquals(null, withCol { decks.getLegacy(deckId) })
        }

    @Test
    fun `refreshData - returns early without throwing when collection is closed`() =
        runTest {
            withNullCollection {
                viewModel.refreshData().join()
            }
        }

    @Test
    fun `refreshData - state stays Loading when collection is closed from the start`() =
        runTest {
            withNullCollection {
                assertIs<DeckPageUiState.Loading>(state)

                viewModel.refreshData().join()

                assertIs<DeckPageUiState.Loading>(state)
            }
        }

    @Test
    fun `refreshData - does not clobber a populated state when collection becomes closed`() =
        runTest {
            addBasicNote("Front", "Back")
            viewModel.refreshData().join()
            val populatedState = assertIs<DeckPageUiState.Study>(state)
            val populatedDeckId = viewModel.selectedDeckId
            val populatedIsFiltered = viewModel.isFilteredDeck
            val populatedHaveBuried = viewModel.haveBuried

            withNullCollection {
                viewModel.refreshData().join()
                assertEquals(populatedState, state)
                assertEquals(populatedDeckId, viewModel.selectedDeckId)
                assertEquals(populatedIsFiltered, viewModel.isFilteredDeck)
                assertEquals(populatedHaveBuried, viewModel.haveBuried)
            }
        }

    @Test
    fun `refreshData - flowOfDeckPage emits no extra value when collection is closed`() =
        runTest {
            withNullCollection {
                viewModel.flowOfDeckPage.test {
                    assertIs<DeckPageUiState.Loading>(awaitItem())
                    viewModel.refreshData().join()
                    expectNoEvents()
                }
            }
        }

    @Test
    fun `refreshData - safe under multiple rapid calls when collection is closed`() =
        runTest {
            withNullCollection {
                val jobs = (1..10).map { viewModel.refreshData() }
                jobs.joinAll()

                assertIs<DeckPageUiState.Loading>(state)
            }
        }

    @Test
    fun `refreshData - resumes correctly after the collection becomes available again`() =
        runTest {
            withNullCollection {
                viewModel.refreshData().join()
                assertIs<DeckPageUiState.Loading>(state)
            }

            addBasicNote("Front", "Back")
            viewModel.refreshData().join()

            assertIs<DeckPageUiState.Study>(state)
        }
}

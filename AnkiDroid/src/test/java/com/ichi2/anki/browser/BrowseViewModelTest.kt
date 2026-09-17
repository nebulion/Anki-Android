// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.QueueType
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The browser's filters combine, its choices follow the other filters, and actions hit the right cards. */
@RunWith(AndroidJUnit4::class)
class BrowseViewModelTest : RobolectricTest() {
    private suspend fun browser(deckId: Long? = null): BrowseViewModel =
        BrowseViewModel(SavedStateHandle(buildMap { deckId?.let { put(BrowseViewModel.ARG_DECK_ID, it) } }))
            .also { it.initialLoad.join() }

    private fun addCard(
        front: String,
        deckId: Long,
        vararg tags: String,
    ) = addBasicNote(front, "back")
        .update { tags.forEach { this.tags.add(it) } }
        .also { note -> note.cards().forEach { it.update { did = deckId } } }

    @Test
    fun `opened for a deck, that deck is the filter`() =
        runTest {
            val italian = addDeck("Italian")
            addCard("ciao", italian)
            addCard("hello", 1)

            val browser = browser(italian)
            assertEquals(setOf("Italian"), browser.filters.decks)
            assertEquals(1, browser.count)
        }

    @Test
    fun `choices within a filter widen it, different filters narrow each other`() =
        runTest {
            val italian = addDeck("Italian")
            val spanish = addDeck("Spanish")
            addCard("ciao", italian, "verbs")
            addCard("hola", spanish, "nouns")
            addCard("hello", 1, "verbs")
            val browser = browser()

            browser.setFilters(BrowseFilters(decks = setOf("Italian", "Spanish"))).join()
            assertEquals(2, browser.count)

            browser.setFilters(browser.filters.copy(tags = setOf("verbs"))).join()
            assertEquals(1, browser.count)
        }

    @Test
    fun `only the chosen decks' tags are offered`() =
        runTest {
            val italian = addDeck("Italian")
            addCard("ciao", italian, "verbs")
            addCard("hello", 1, "english")
            val browser = browser(italian)

            assertEquals(listOf("verbs"), browser.options!!.tags)
        }

    @Test
    fun `text is searched as text, not as a filter`() =
        runTest {
            addCard("deck:nothing here", 1)
            addCard("other", 1)
            val browser = browser()

            browser.setFilters(BrowseFilters(text = "deck:nothing")).join()
            assertEquals(1, browser.count)
        }

    @Test
    fun `cards are sorted, and the order can be reversed`() =
        runTest {
            addCard("b", 1)
            addCard("a", 1)
            addCard("c", 1)
            val browser = browser()
            browser.showCards().join()

            browser.setSort(BrowseSort.SortField, reversed = false).join()
            val ascending = browser.cardIds!!.map { withCol { getCard(it).note(this).fields[0] } }
            assertEquals(listOf("a", "b", "c"), ascending)

            browser.setSort(BrowseSort.SortField, reversed = true).join()
            assertEquals(listOf("c", "b", "a"), browser.cardIds!!.map { withCol { getCard(it).note(this).fields[0] } })
        }

    @Test
    fun `without selecting, reset acts on every card shown, and undo brings the progress back`() =
        runTest {
            repeat(3) { addCard("front $it", 1) }
            col.sched.answerCard(col.sched.card!!, Rating.GOOD)
            val browser = browser()
            browser.showCards().join()

            assertEquals(3, browser.targets.size)
            browser.resetProgress().join()
            assertTrue(withCol { findCards("-is:new") }.isEmpty(), "every card is new again")

            browser.undo().join()
            assertTrue(withCol { findCards("-is:new") }.isNotEmpty(), "undo restores the studied card")
        }

    @Test
    fun `while selecting, actions hit only the ticked cards`() =
        runTest {
            addCard("one", 1)
            addCard("two", 1)
            val browser = browser()
            browser.showCards().join()
            browser.toggleSelecting()
            val first = browser.cardIds!!.first()

            browser.toggle(first)
            browser.suspend().join()
            assertEquals(QueueType.Suspended, withCol { getCard(first).queue })
            assertEquals(1, withCol { findCards("is:suspended") }.size)
        }
}

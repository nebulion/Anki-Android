// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.QueueType
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The plain browser finds a deck's cards and resets, suspends and deletes the ticked ones. */
@RunWith(AndroidJUnit4::class)
class BrowseViewModelTest : RobolectricTest() {
    private suspend fun browser(deckId: Long?): BrowseViewModel =
        BrowseViewModel(SavedStateHandle(buildMap { deckId?.let { put(BrowseViewModel.ARG_DECK_ID, it) } }))
            .also { it.initialSearch.join() }

    @Test
    fun `opened for a deck, it finds that deck's cards, and opened alone, every card`() =
        runTest {
            val deckId = addDeck("Italian")
            addBasicNote("in deck", "back").cards().single().update { did = deckId }
            addBasicNote("elsewhere", "back")

            assertEquals(1, browser(deckId).cardIds!!.size)
            assertEquals(2, browser(null).cardIds!!.size)
        }

    @Test
    fun `select all resets every card found, and undo brings the progress back`() =
        runTest {
            repeat(3) { addBasicNote("front $it", "back") }
            col.sched.answerCard(col.sched.card!!, anki.scheduler.CardAnswer.Rating.GOOD)
            col.sched.answerCard(col.sched.card!!, anki.scheduler.CardAnswer.Rating.GOOD)
            val browser = browser(null)

            browser.toggleAll()
            assertEquals(3, browser.selected.size)
            browser.resetProgress().join()
            assertTrue(withCol { findCards("-is:new") }.isEmpty(), "every card is new again")

            browser.undo().join()
            assertTrue(withCol { findCards("-is:new") }.isNotEmpty(), "undo restores the studied cards")
        }

    @Test
    fun `suspend and delete act only on the ticked card`() =
        runTest {
            addBasicNote("one", "back")
            addBasicNote("two", "back")
            val browser = browser(null)
            val first = browser.cardIds!!.first()

            browser.toggle(first)
            browser.suspend().join()
            assertEquals(QueueType.Suspended, withCol { getCard(first).queue })
            assertEquals(1, withCol { findCards("is:suspended") }.size)

            browser.deleteNotes().join()
            assertEquals(1, browser.cardIds!!.size)
            assertTrue(browser.selected.isEmpty())
        }
}

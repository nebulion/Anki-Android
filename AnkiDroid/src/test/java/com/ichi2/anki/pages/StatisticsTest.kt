// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.pages

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.StatisticsDestination
import com.ichi2.anki.common.destinations.launchActivity
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.model.SelectableDeck
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The deck being shown is the screen header's title and the deck picker is one of its actions. Both
 * live in Compose, and the project has no Compose UI test dependencies, so these assert the
 * fragment's own state instead of matching views.
 */
@RunWith(AndroidJUnit4::class)
class StatisticsTest : RobolectricTest() {
    private fun FragmentActivity.statistics() = supportFragmentManager.findFragmentById(R.id.fragment_container) as Statistics

    @Test
    fun `shows 'Default' deck when collection is empty`() =
        runTest {
            launchActivity<SingleFragmentActivity>(StatisticsDestination).use { scenario ->
                advanceUntilIdle()
                scenario.onActivity { activity ->
                    assertEquals("Default", activity.statistics().title)
                }
            }
        }

    @Test
    fun `changing decks shows new deck name`() =
        runTest {
            val testDeckName1 = "TestDeckName1"
            val testDeckName2 = "TestDeckName2"
            val testDeck1 = addDeck(testDeckName1)
            withCol { decks.select(testDeck1) }
            val testDeck2 = addDeck(testDeckName2)

            launchActivity<SingleFragmentActivity>(StatisticsDestination).use { scenario ->
                advanceUntilIdle()
                scenario.onActivity { activity ->
                    val statistics = activity.statistics()
                    assertEquals(testDeckName1, statistics.title, "the current deck is shown")

                    statistics.onDeckSelected(SelectableDeck.Deck(testDeck2, testDeckName2))
                }
                advanceUntilIdle()
                scenario.onActivity { activity ->
                    assertEquals(testDeckName2, activity.statistics().title, "the selected deck is shown")
                }
            }
        }

    @Test
    fun `uses expected constraints for decks list selection dialog`() =
        runTest {
            // 'All decks' shows the whole collection; filtered decks can't be chosen, and the 'Default'
            // deck is offered whether or not it is empty
            launchActivity<SingleFragmentActivity>(StatisticsDestination).use { scenario ->
                advanceUntilIdle()
                scenario.onActivity { activity ->
                    activity.statistics().showDeckPicker()
                    advanceRobolectricLooper()

                    val deckSelectionDialog = activity.supportFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG)
                    assertNotNull(deckSelectionDialog)
                    assertTrue(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_ALLOW_ALL, false))
                    assertFalse(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_ALLOW_FILTERED, true))
                    assertTrue(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_SKIP_EMPTY_DEFAULT, false))
                }
            }
        }

    @Test
    fun `'All decks' shows the collection`() =
        runTest {
            launchActivity<SingleFragmentActivity>(StatisticsDestination).use { scenario ->
                advanceUntilIdle()
                scenario.onActivity { activity -> activity.statistics().onDeckSelected(SelectableDeck.AllDecks) }
                advanceUntilIdle()
                scenario.onActivity { activity ->
                    assertEquals(col.tr.statisticsRangeCollection(), activity.statistics().title)
                }
            }
        }
}

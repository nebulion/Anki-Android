// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.cardStateCustomizer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * The study screen is Compose, so these tests drive [ReviewerViewModel] directly instead of
 * clicking the answer buttons: what they check is the WebView's custom scheduler round trip.
 */
@RunWith(AndroidJUnit4::class)
class ReviewerFragmentTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    /** The collection is shared between tests: review a deck which only contains this test's cards */
    private var testDeckId: DeckId = 0

    @Before
    fun setUp() {
        testContext.sharedPrefs().edit {
            putBoolean("newReviewer", true)
            putBoolean("newReviewerOptions", true)
        }
        testDeckId = col.decks.addNormalDeckWithName("ReviewerFragmentTest-${UUID.randomUUID()}").id
        col.decks.select(testDeckId)
    }

    @After
    fun tearDown() {
        col.decks.remove(listOf(testDeckId))
        col.cardStateCustomizer = ""
    }

    @Test
    fun testCustomSchedulerWithCustomData() = testCustomSchedulerWithCustomData(schedulerDelayMs = 0)

    /**
     * Issue 17298: a card must not be answered until the custom scheduler has
     * completed (statesMutated).
     */
    @Test
    fun testCustomSchedulerWithCustomDataAndSlowScheduler() = testCustomSchedulerWithCustomData(schedulerDelayMs = 5000)

    private fun testCustomSchedulerWithCustomData(schedulerDelayMs: Long) {
        val delayJs =
            if (schedulerDelayMs > 0) {
                "await new Promise(resolve => setTimeout(resolve, $schedulerDelayMs));"
            } else {
                ""
            }
        col.cardStateCustomizer =
            """
            $delayJs
            states.good.normal.review.easeFactor = 3.0;
            states.good.normal.review.scheduledDays = 123;
            customData.good.c += 1;
            """
        val card = addCardToTestDeck()
        card.moveToReviewQueue()
        col.backend.updateCards(
            listOf(
                card
                    .toBackendCard()
                    .toBuilder()
                    .setCustomData("""{"c":1}""")
                    .build(),
            ),
            true,
        )

        withReviewer { viewModel ->
            var cardFromDb = col.getCard(card.id).toBackendCard()
            assertThat(cardFromDb.easeFactor, equalTo(card.factor))
            assertThat(cardFromDb.interval, equalTo(card.ivl))
            assertThat(cardFromDb.customData, equalTo("""{"c":1}"""))

            showAnswer(viewModel)
            viewModel.answerCard(Rating.GOOD)
            // Answering runs on the IO dispatcher
            waitUntil(message = { "The review of card ${card.id} was not saved" }) {
                col.getCard(card.id).reps == card.reps + 1
            }

            cardFromDb = col.getCard(card.id).toBackendCard()
            assertThat(cardFromDb.easeFactor, equalTo(3000))
            assertThat(cardFromDb.interval, equalTo(123))
            assertThat(cardFromDb.customData, equalTo("""{"c":2}"""))
        }
    }

    @Test
    fun testCustomSchedulerWithRuntimeError() {
        // Issue 15035 - runtime errors weren't handled
        col.cardStateCustomizer = "states.this_is_not_defined.normal.review = 12;"
        addCardToTestDeck()

        withReviewer { viewModel ->
            showAnswer(viewModel)
        }
    }

    private fun addCardToTestDeck(): Card = addNoteUsingBasicNoteType("foo", "bar").firstCard(col).update { did = testDeckId }

    private fun withReviewer(block: (ReviewerViewModel) -> Unit) {
        ActivityScenario.launch<CardViewerActivity>(ReviewerFragment.getIntent(testContext)).use { scenario ->
            lateinit var viewModel: ReviewerViewModel
            scenario.onActivity { activity ->
                viewModel =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<ReviewerFragment>()
                        .single()
                        .viewModel
            }
            block(viewModel)
        }
    }

    /**
     * Shows the answer once the card has loaded, then waits until it is shown: that needs the
     * custom scheduler to have run inside the WebView, which can be slow on CI.
     */
    private fun showAnswer(viewModel: ReviewerViewModel) {
        waitUntil(timeout = 30.seconds, message = { "The card did not load" }) {
            viewModel.currentCard.isCompleted
        }
        viewModel.onShowAnswer()
        waitUntil(timeout = 30.seconds, message = { "The answer was not shown" }) {
            viewModel.showingAnswer.value
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun checkWebView() = ensureWebViewIsSupported()
    }
}

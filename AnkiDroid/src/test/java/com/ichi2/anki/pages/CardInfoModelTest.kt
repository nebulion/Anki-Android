// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The native card info page shows the rows the backend's web page showed, from the same data. */
@RunWith(AndroidJUnit4::class)
class CardInfoModelTest : RobolectricTest() {
    @Test
    fun `a new card lists its position and no history`() {
        val card = addBasicNote().firstCard()

        val info = col.cardInfo(card.id, ZoneOffset.UTC)

        val labels = info.facts.map { it.label }
        assertEquals(col.tr.cardStatsAdded(), labels.first())
        assertTrue(col.tr.cardStatsNewCardPosition() in labels)
        assertTrue(col.tr.cardStatsFirstReview() !in labels)
        assertEquals("0", info.facts.value(col.tr.cardStatsReviewCount()))
        assertEquals(card.id.toString(), info.facts.value(col.tr.cardStatsCardId()))
        assertTrue(info.reviews.isEmpty())
    }

    @Test
    fun `an answered card lists the answer in its history`() {
        addBasicNote()
        val card = col.sched.card!!
        col.sched.answerCard(card, Rating.GOOD)

        val info = col.cardInfo(card.id, ZoneOffset.UTC)

        assertEquals("1", info.facts.value(col.tr.cardStatsReviewCount()))
        assertTrue(col.tr.cardStatsFirstReview() in info.facts.map { it.label })
        val review = info.reviews.single()
        assertEquals(col.tr.cardStatsReviewLogTypeLearn(), review.kind)
        assertEquals(col.tr.studyingGood(), review.rating)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} @ \d{2}:\d{2}""").matches(review.date), review.date)
    }

    private fun List<CardFact>.value(label: String) = single { it.label == label }.value
}

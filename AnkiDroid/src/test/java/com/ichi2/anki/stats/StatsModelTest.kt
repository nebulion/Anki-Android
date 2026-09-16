// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.i18n.FormatTimespanRequest
import anki.scheduler.CardAnswer.Rating
import anki.stats.GraphPreferences.Weekday
import anki.stats.GraphsResponse
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Every section of the native statistics page, on an empty collection and on a studied one. */
@RunWith(AndroidJUnit4::class)
class StatsModelTest : RobolectricTest() {
    private val fmt by lazy {
        StatsFormat(col.tr, Locale.US) { col.backend.formatTimespan(it.toFloat(), FormatTimespanRequest.Context.PRECISE) }
    }

    private fun graphs(days: Int = 365): GraphsResponse = col.backend.graphs("", days)

    @Test
    fun `an empty collection has no data in any graph`() {
        val data = graphs()
        assertEquals(listOf(col.tr.statisticsTodayNoCards()), todayLines(data, fmt))
        assertNull(futureDue(data, fmt, GraphRange.Month, includeBacklog = true).chart)
        assertNull(reviews(data, fmt, GraphRange.Month, showTime = false).chart)
        assertNull(cardCounts(data, fmt, separateInactive = true).chart)
        assertNull(intervals(data.intervals, fmt, IntervalRange.Percentile95, fsrs = false).chart)
        assertNull(ease(data, fmt).chart)
        assertNull(hours(data, fmt, GraphRange.Year))
        assertNull(buttons(data, fmt, GraphRange.Year).chart)
        assertNull(added(data, fmt, GraphRange.Month).chart)
        assertNull(calendar(data, fmt, 2026, Weekday.SUNDAY, RevlogRange.Year, ZonedDateTime.now(), Locale.US).days)
        assertEquals(5, trueRetention(data, fmt, RetentionMode.Summary, RevlogRange.Year).size)
    }

    /**
     * The tests fix the app's clock in 2020 while the backend logs reviews at the real time, so the
     * review graphs are not checked against numbers here: only that each one is worked out for
     * every range without failing. Cards added and due are checked exactly.
     */
    @Test
    fun `studying works out every graph for every range`() {
        repeat(3) { addBasicNote("front $it", "back") }
        repeat(2) { col.sched.answerCard(col.sched.card!!, Rating.GOOD) }
        col.sched.answerCard(col.sched.card!!, Rating.AGAIN)

        for (revlogRange in RevlogRange.entries) {
            val data = graphs(revlogRange.days)
            todayLines(data, fmt)
            for (range in GraphRange.entries) {
                assertNotNull(futureDue(data, fmt, range, includeBacklog = true).chart, "future due $range")
                assertEquals(3.0, added(data, fmt, range).chart!!.bars.sumOf { it.total }, "added $range")
                reviews(data, fmt, range, showTime = false)
                reviews(data, fmt, range, showTime = true)
                hours(data, fmt, range)
                buttons(data, fmt, range)
            }
            assertEquals(fmt.number(3), cardCounts(data, fmt, separateInactive = true).table.last().value)
            for (range in IntervalRange.entries) intervals(data.intervals, fmt, range, fsrs = false)
            ease(data, fmt)
            for (range in PercentageRange.entries) {
                difficulty(data, fmt, range)
                retrievability(data, fmt, range)
            }
            calendar(data, fmt, ZonedDateTime.now().year, Weekday.MONDAY, revlogRange, ZonedDateTime.now(), Locale.US)
            for (mode in RetentionMode.entries) trueRetention(data, fmt, mode, revlogRange)
        }
    }
}

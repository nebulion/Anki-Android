// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import anki.stats.GraphPreferences.Weekday
import anki.stats.GraphsResponse
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Today's summary and the month calendar, from hand-made graph data. */
class StatsViewsTest {
    // Wednesday 16 September 2026, noon
    private val now = ZonedDateTime.of(2026, 9, 16, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun graphs(reviewsByDaysAgo: Map<Int, Int>): GraphsResponse =
        GraphsResponse
            .newBuilder()
            .setReviews(
                GraphsResponse.ReviewCountsAndTimes.newBuilder().putAllCount(
                    reviewsByDaysAgo
                        .map { (ago, n) ->
                            -ago to
                                GraphsResponse.ReviewCountsAndTimes.Reviews
                                    .newBuilder()
                                    .setMature(n)
                                    .build()
                        }.toMap(),
                ),
            ).build()

    @Test
    fun `today is null until something is studied, then counts its answers`() {
        assertNull(todaySummary(GraphsResponse.getDefaultInstance()))

        val data =
            GraphsResponse
                .newBuilder()
                .setToday(
                    GraphsResponse.Today
                        .newBuilder()
                        .setAnswerCount(40)
                        .setAnswerMillis(600_000)
                        .setCorrectCount(30)
                        .setMatureCount(20)
                        .setMatureCorrect(18),
                ).build()
        val today = todaySummary(data)!!
        assertEquals(40, today.cards)
        assertEquals(10, today.minutes)
        assertEquals(15, today.secondsPerCard)
        assertEquals(10, today.again)
        assertEquals(25.0, today.againPercent)
        assertEquals(18 to 20, today.matureCorrect to today.matureTotal)
    }

    @Test
    fun `a month lays out its days from the first day of the week`() {
        val september = calendarMonth(graphs(emptyMap()), YearMonth.of(2026, 9), Weekday.SUNDAY, RevlogRange.Year, now, Locale.US)
        // 1 September 2026 is a Tuesday: two blanks before it
        assertEquals(
            listOf(null, null, 1),
            september.weeks
                .first()
                .take(3)
                .map { it?.day },
        )
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), september.weekdayLabels)
        assertTrue(september.weeks.all { it.size == 7 })
        assertEquals(30, september.weeks.flatten().count { it != null })

        val monday = calendarMonth(graphs(emptyMap()), YearMonth.of(2026, 9), Weekday.MONDAY, RevlogRange.Year, now, Locale.US)
        assertEquals(
            listOf(null, 1),
            monday.weeks
                .first()
                .take(2)
                .map { it?.day },
        )
    }

    @Test
    fun `the busiest day is darkest and days without reviews are white`() {
        val month = calendarMonth(graphs(mapOf(0 to 100, 1 to 25)), YearMonth.of(2026, 9), Weekday.SUNDAY, RevlogRange.Year, now, Locale.US)
        val days =
            month.weeks
                .flatten()
                .filterNotNull()
                .associateBy { it.day }
        assertEquals(1f, days.getValue(16).shade)
        assertEquals(0.6f, days.getValue(15).shade, 0.001f)
        assertEquals(0f, days.getValue(14).shade)
        assertTrue(days.getValue(17).isFuture)
        assertEquals(2, month.daysStudied)
        assertEquals(16, month.daysSoFar)
        assertEquals(125, month.reviews)
    }

    @Test
    fun `months outside the history are not shown`() {
        val data = graphs(mapOf(0 to 1))
        assertEquals(
            YearMonth.of(2026, 9),
            calendarMonth(data, YearMonth.of(2027, 1), Weekday.SUNDAY, RevlogRange.Year, now, Locale.US).month,
        )
        assertEquals(
            YearMonth.of(2025, 9),
            calendarMonth(data, YearMonth.of(2020, 1), Weekday.SUNDAY, RevlogRange.Year, now, Locale.US).month,
        )
        assertEquals(
            YearMonth.of(2026, 9),
            calendarMonth(data, YearMonth.of(2020, 1), Weekday.SUNDAY, RevlogRange.All, now, Locale.US).first,
        )
    }
}

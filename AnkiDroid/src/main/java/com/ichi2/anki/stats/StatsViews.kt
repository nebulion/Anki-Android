// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import anki.stats.GraphPreferences.Weekday
import anki.stats.GraphsResponse
import com.ichi2.anki.libanki.Collection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The owner's chosen layouts for Today and the calendar (2026-09-16), worked out from the same
 * `GraphsResponse` numbers the backend's page uses.
 */

/** Today as three big numbers and rows, like the deck page. Null when nothing was studied today. */
data class TodaySummary(
    val cards: Int,
    val minutes: Int,
    val secondsPerCard: Int,
    val again: Int,
    /** 0–100 */
    val againPercent: Double,
    val matureCorrect: Int,
    val matureTotal: Int,
    val learn: Int,
    val review: Int,
    val relearn: Int,
    val filtered: Int,
)

fun todaySummary(data: GraphsResponse): TodaySummary? {
    val today = data.today
    if (today.answerCount == 0) return null
    val seconds = today.answerMillis / 1000.0
    val again = today.answerCount - today.correctCount
    return TodaySummary(
        cards = today.answerCount,
        minutes = (seconds / 60).roundToInt(),
        secondsPerCard = (seconds / today.answerCount).roundToInt(),
        again = again,
        againPercent = again * 100.0 / today.answerCount,
        matureCorrect = today.matureCorrect,
        matureTotal = today.matureCount,
        learn = today.learnCount,
        review = today.reviewCount,
        relearn = today.relearnCount,
        filtered = today.earlyReviewCount,
    )
}

/** A day of the month view: its reviews, and how dark to draw it. */
data class MonthDay(
    val day: Int,
    val reviews: Int,
    /** 0 for no reviews, otherwise 0.2–1: the square root of its share of the busiest day, as the page shades it. */
    val shade: Float,
    val isFuture: Boolean,
)

/** One month of the calendar, as a grid of weeks. */
data class CalendarMonth(
    val month: YearMonth,
    /** The earliest and latest months that can be shown. */
    val first: YearMonth,
    val last: YearMonth,
    /** Narrow names of the weekdays, from the first day of the week. */
    val weekdayLabels: List<String>,
    /** The grid, a row per week; null where a week row has days of another month. */
    val weeks: List<List<MonthDay?>>,
    val daysStudied: Int,
    /** Days of the month up to today. */
    val daysSoFar: Int,
    val reviews: Int,
)

fun calendarMonth(
    data: GraphsResponse,
    month: YearMonth,
    firstDayOfWeek: Weekday,
    revlogRange: RevlogRange,
    now: ZonedDateTime,
    locale: Locale,
): CalendarMonth {
    val today = now.toLocalDate()
    // a review's day number is days from today; the day starts at the rollover hour
    val counts = mutableMapOf<LocalDate, Int>()
    for ((day, r) in data.reviews.countMap) {
        val date = now.plusDays(day.toLong()).minusHours(data.rolloverHour.toLong()).toLocalDate()
        counts.merge(date, r.learn + r.relearn + r.mature + r.filtered + r.young, Int::plus)
    }
    val busiest = counts.values.maxOrNull() ?: 0
    val last = YearMonth.from(today)
    val first =
        when (revlogRange) {
            RevlogRange.Year -> YearMonth.from(today.minusYears(1))
            RevlogRange.All -> counts.keys.minOrNull()?.let(YearMonth::from) ?: last
        }
    val shown = month.coerceIn(first, last)
    val firstDay = firstDayOfWeek.toDayOfWeekOrSunday()

    val lead = (shown.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cells: List<MonthDay?> =
        List(lead) { null } +
            (1..shown.lengthOfMonth()).map { d ->
                val date = shown.atDay(d)
                val reviews = counts[date] ?: 0
                MonthDay(
                    day = d,
                    reviews = reviews,
                    shade = if (reviews == 0 || busiest == 0) 0f else (0.2 + 0.8 * sqrt(reviews.toDouble() / busiest)).toFloat(),
                    isFuture = date.isAfter(today),
                )
            }
    val daysSoFar = if (shown == last) today.dayOfMonth else shown.lengthOfMonth()
    return CalendarMonth(
        month = shown,
        first = first,
        last = last,
        weekdayLabels = (0 until 7).map { firstDay.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) },
        weeks = cells.chunked(7).map { week -> week + List(7 - week.size) { null } },
        daysStudied = cells.count { it != null && it.reviews > 0 },
        daysSoFar = daysSoFar,
        reviews = cells.sumOf { it?.reviews ?: 0 },
    )
}

private fun YearMonth.coerceIn(
    min: YearMonth,
    max: YearMonth,
): YearMonth =
    if (isBefore(min)) {
        min
    } else if (isAfter(max)) {
        max
    } else {
        this
    }

private fun Weekday.toDayOfWeekOrSunday(): DayOfWeek =
    when (this) {
        Weekday.MONDAY -> DayOfWeek.MONDAY
        Weekday.FRIDAY -> DayOfWeek.FRIDAY
        Weekday.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

/** What was studied on one day: every answer that day, whatever deck its card is in now. */
data class DayDetails(
    val reviews: Int,
    val seconds: Int,
    val again: Int,
    val learn: Int,
    val review: Int,
    val relearn: Int,
    val filtered: Int,
    /** Deck names with their reviews, busiest first. */
    val decks: List<Pair<String, Int>>,
)

/**
 * The answers given [daysAgo] days before today, a day starting when the collection's day does,
 * for the cards [search] finds ("" for the whole collection). A card in a filtered deck counts
 * towards its home deck. Rescheduling and manual changes are not answers and are left out.
 */
fun Collection.dayDetails(
    daysAgo: Int,
    search: String,
): DayDetails {
    val end = (sched.dayCutoff - daysAgo * SECONDS_PER_DAY) * 1000
    val start = end - SECONDS_PER_DAY * 1000
    val cards = if (search.isBlank()) null else findCards(search).toHashSet()
    var reviews = 0
    var millis = 0L
    var again = 0
    val kinds = IntArray(4)
    val byDeck = mutableMapOf<Long, Int>()
    db
        .query(
            "select r.cid, r.ease, r.type, r.time, case when c.odid != 0 then c.odid else c.did end " +
                "from revlog r join cards c on c.id = r.cid where r.id >= ? and r.id < ? and r.ease > 0 and r.type < 4",
            start,
            end,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (cards != null && cursor.getLong(0) !in cards) continue
                reviews++
                if (cursor.getInt(1) == 1) again++
                kinds[cursor.getInt(2)]++
                millis += cursor.getLong(3)
                byDeck.merge(cursor.getLong(4), 1, Int::plus)
            }
        }
    return DayDetails(
        reviews = reviews,
        seconds = (millis / 1000).toInt(),
        again = again,
        learn = kinds[0],
        review = kinds[1],
        relearn = kinds[2],
        filtered = kinds[3],
        decks = byDeck.entries.sortedByDescending { it.value }.map { decks.name(it.key) to it.value },
    )
}

private const val SECONDS_PER_DAY = 86_400L

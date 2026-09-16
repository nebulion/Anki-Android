// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import anki.i18n.FormatTimespanRequest
import anki.stats.CardStatsResponse
import anki.stats.RevlogEntry.ReviewKind
import com.ichi2.anki.libanki.Collection
import net.ankiweb.rsdroid.Translations
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** One fact about a card, e.g. "Added" and "2026-09-16". */
data class CardFact(
    val label: String,
    val value: String,
)

/** One answer or other change in a card's history, newest first. */
data class CardReview(
    /** "2026-09-16 @ 14:03" */
    val date: String,
    /** "Review", "Learn", "Manual" … */
    val kind: String,
    /** The button pressed, e.g. "Good"; empty where no button was pressed. */
    val rating: String,
    /** The interval given, e.g. "12 days"; empty for a reschedule with none. */
    val interval: String,
    /** "250%", or with FSRS "D:42%"; empty where the backend recorded none. */
    val ease: String,
    /** "8.2 seconds" */
    val timeTaken: String,
)

data class CardInfo(
    val facts: List<CardFact>,
    val reviews: List<CardReview>,
)

/**
 * The card info page's content, worked out the way the backend's own page does
 * (`ts/routes/card-info/CardStats.svelte` and `Revlog.svelte`, Anki 26.05): the same rows in the
 * same order, under the same conditions, with the same units.
 *
 * Durations come from the backend's formatter, so they read exactly as elsewhere in Anki.
 */
fun Collection.cardInfo(
    cardId: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): CardInfo {
    val stats = backend.cardStats(cardId)
    return CardInfo(
        facts = cardFacts(stats, tr, zone) { seconds, precise -> timeSpan(seconds, precise) },
        reviews = stats.revlogList.map { cardReview(it, tr, zone) { seconds, precise -> timeSpan(seconds, precise) } },
    )
}

private fun Collection.timeSpan(
    seconds: Double,
    precise: Boolean,
): String =
    backend.formatTimespan(
        seconds.toFloat(),
        if (precise) FormatTimespanRequest.Context.PRECISE else FormatTimespanRequest.Context.INTERVALS,
    )

/** @param timeSpan formats a number of seconds; `precise` keeps two decimals, as the web page's default */
internal fun cardFacts(
    stats: CardStatsResponse,
    tr: Translations,
    zone: ZoneId,
    timeSpan: (seconds: Double, precise: Boolean) -> String,
): List<CardFact> =
    buildList {
        fun add(
            label: String,
            value: Any,
        ) = add(CardFact(label, value.toString()))

        add(tr.cardStatsAdded(), date(stats.added, zone))
        if (stats.hasFirstReview()) add(tr.cardStatsFirstReview(), date(stats.firstReview, zone))
        if (stats.hasLatestReview()) add(tr.cardStatsLatestReview(), date(stats.latestReview, zone))
        if (stats.hasDueDate()) add(tr.statisticsDueDate(), date(stats.dueDate, zone))
        if (stats.hasDuePosition()) add(tr.cardStatsNewCardPosition(), stats.duePosition)
        if (stats.interval != 0) add(tr.cardStatsInterval(), timeSpan(stats.interval * DAY, true))

        if (stats.hasMemoryState()) {
            val stability = stats.memoryState.stability
            var stabilityText = timeSpan(stability * DAY, false)
            // over a month, the page also gives the stability in days
            if (stability > 31) stabilityText += " (${tr.schedulingTimeSpanDays(stability.roundToInt())})"
            add(tr.cardStatsFsrsStability(), stabilityText)
            add(tr.cardStatsFsrsDifficulty(), percent((stats.memoryState.difficulty - 1.0) / 9.0 * 100.0))
            if (stats.hasFsrsRetrievability() && stats.fsrsRetrievability != 0f) {
                add(tr.cardStatsFsrsRetrievability(), percent(stats.fsrsRetrievability * 100.0))
            }
        } else if (stats.ease != 0) {
            add(tr.cardStatsEase(), "${formatNumber(stats.ease / 10.0)}%")
        }

        add(tr.cardStatsReviewCount(), stats.reviews)
        add(tr.cardStatsLapseCount(), stats.lapses)
        if (stats.totalSecs != 0f) {
            add(tr.cardStatsAverageTime(), timeSpan(stats.averageSecs.toDouble(), true))
            add(tr.cardStatsTotalTime(), timeSpan(stats.totalSecs.toDouble(), true))
        }

        add(tr.cardStatsCardTemplate(), stats.cardType)
        add(tr.cardStatsNoteType(), stats.notetype)
        add(tr.cardStatsDeckName(), if (stats.hasOriginalDeck()) "${stats.deck} (${stats.originalDeck})" else stats.deck)
        add(tr.cardStatsPreset(), stats.preset)
        add(tr.cardStatsCardId(), stats.cardId)
        add(tr.cardStatsNoteId(), stats.noteId)
        if (stats.customData.isNotEmpty()) add(tr.cardStatsCustomData(), customData(stats.customData))
    }

internal fun cardReview(
    entry: CardStatsResponse.StatsRevlogEntry,
    tr: Translations,
    zone: ZoneId,
    timeSpan: (seconds: Double, precise: Boolean) -> String,
): CardReview {
    val time = Instant.ofEpochSecond(entry.time).atZone(zone)
    return CardReview(
        date = "${time.format(DATE)} @ ${time.format(TIME)}",
        kind =
            when (entry.reviewKind) {
                ReviewKind.LEARNING -> tr.cardStatsReviewLogTypeLearn()
                ReviewKind.REVIEW -> tr.cardStatsReviewLogTypeReview()
                ReviewKind.RELEARNING -> tr.cardStatsReviewLogTypeRelearn()
                ReviewKind.FILTERED -> tr.cardStatsReviewLogTypeFiltered()
                ReviewKind.MANUAL -> tr.cardStatsReviewLogTypeManual()
                ReviewKind.RESCHEDULED -> tr.cardStatsReviewLogTypeRescheduled()
                else -> ""
            },
        rating =
            when (entry.buttonChosen) {
                1 -> tr.studyingAgain()
                2 -> tr.studyingHard()
                3 -> tr.studyingGood()
                4 -> tr.studyingEasy()
                else -> ""
            },
        interval = if (entry.interval == 0) "" else timeSpan(entry.interval.toDouble(), true),
        ease = easeOrDifficulty(entry.ease),
        timeTaken = timeSpan(entry.takenSecs.toDouble(), true),
    )
}

/** A revlog's ease is per mille; at 110% or below it is an FSRS difficulty shifted up by 10%. */
private fun easeOrDifficulty(ease: Int): String {
    if (ease == 0) return ""
    val asPercent = ease / 10.0
    return if (asPercent <= 110) "D:${(asPercent - 10).roundToInt()}%" else "${asPercent.roundToInt()}%"
}

/** `{"v":"1"}` is shown as `v=1`; data that is not a JSON object is shown as it is. */
private fun customData(json: String): String =
    runCatching {
        val obj = JSONObject(json)
        obj.keys().asSequence().joinToString(" ") { "$it=${obj.get(it)}" }
    }.getOrDefault(json)

private fun date(
    epochSeconds: Long,
    zone: ZoneId,
): String = Instant.ofEpochSecond(epochSeconds).atZone(zone).format(DATE)

private fun percent(value: Double) = "${value.roundToInt()}%"

/** 250.0 → "250", 252.5 → "252.5", as JavaScript prints a number. */
private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private const val DAY = 86_400.0
private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

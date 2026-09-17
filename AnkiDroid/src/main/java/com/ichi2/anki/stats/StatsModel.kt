// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import anki.stats.GraphPreferences.Weekday
import anki.stats.GraphsResponse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The statistics page's content, worked out from `GraphsResponse` the way the backend's graphs
 * page does it (the `.ts` files in `ts/routes/graphs`, Anki 26.05): the same buckets, ranges,
 * summaries and wording. Only the drawing differs; see `StatisticsScreenMMD`.
 */

/** The 1-month / 3-month / 1-year / all-time choice most graphs have. */
enum class GraphRange { Month, ThreeMonths, Year, AllTime }

/** How much review history is loaded: the page's "1 year" or "all history". */
enum class RevlogRange(
    val days: Int,
) {
    Year(365),
    All(0),
}

/** The intervals and stability graphs' range. */
enum class IntervalRange { Month, Percentile50, Percentile95, All }

/** The difficulty and retrievability graphs' range; the value is the quantile kept. */
enum class PercentageRange(
    val quantile: Double?,
) {
    Percentile50(0.75),
    Percentile95(0.975),
    Percentile100(1.0),
    All(null),
}

/** What the true retention table shows. */
enum class RetentionMode { Young, Mature, Summary }

private const val Y_TICKS = 5.0
private const val X_TICKS = 7.0
private const val MAX_BARS = 70

// ---------------------------------------------------------------------------------------------
// Today

fun todayLines(
    data: GraphsResponse,
    fmt: StatsFormat,
): List<String> {
    val tr = fmt.tr
    val today = data.today
    if (today.answerCount == 0) return listOf(tr.statisticsTodayNoCards())
    val secs = today.answerMillis / 1000.0
    val unit = minOf(StatsFormat.naturalUnit(secs), StatsFormat.TimeUnit.Minutes)
    val studied =
        tr.statisticsStudiedToday(
            // the translation's selector, not text: "seconds" or "minutes"
            if (unit == StatsFormat.TimeUnit.Seconds) "seconds" else "minutes",
            (secs / today.answerCount).roundToInt(),
            (secs / unit.seconds).roundToInt(),
            today.answerCount,
        )
    val again = today.answerCount - today.correctCount
    val againText = "${tr.statisticsTodayAgainCount()} $again (${fmt.number(again.toDouble() / today.answerCount * 100)}%)"
    val types = tr.statisticsTodayTypeCounts(today.learnCount, today.reviewCount, today.relearnCount, today.earlyReviewCount)
    val mature =
        if (today.matureCount != 0) {
            tr.statisticsTodayCorrectMature(
                today.matureCorrect,
                today.matureCount,
                today.matureCorrect.toDouble() / today.matureCount * 100,
            )
        } else {
            tr.statisticsTodayNoMatureCards()
        }
    return listOf(studied, againText, types, mature)
}

// ---------------------------------------------------------------------------------------------
// Histograms: future due, added, intervals, stability, ease, difficulty, retrievability

/**
 * `histogram-graph.ts`: bars of [values], a left axis rounded up, and for [showArea] the running
 * total as a line against the right axis.
 */
private fun <T> histogram(
    fmt: StatsFormat,
    bins: List<Bin<T>>,
    values: List<Double>,
    domain: Pair<Double, Double>,
    showArea: Boolean,
    xTickLabel: (Double) -> String = { fmt.number(it) },
    detail: (bin: Bin<T>, value: Double, cumulative: Double, percent: Double) -> List<String>,
): BarChart {
    val total = values.sum()
    val cumulative = values.runningFold(0.0) { acc, v -> acc + v }
    val yMax = nice(0.0 to (values.maxOrNull() ?: 0.0)).second
    val areaMax = if (total > 0) nice(0.0 to total).second else 0.0
    val overlay =
        if (showArea && bins.isNotEmpty() && cumulative.last() != 0.0) {
            Overlay(
                points = cumulative.mapIndexed { i, c -> (if (i == 0) bins[0].x0 else bins[i - 1].x1) to c / areaMax },
                ticks = ticks(0.0, areaMax, Y_TICKS).map { Tick(it / areaMax, fmt.number(it)) },
            )
        } else {
            null
        }
    return BarChart(
        xDomain = domain,
        bars =
            bins.mapIndexed { i, bin ->
                val c = cumulative[i + 1]
                Bar(bin.x0, bin.x1, listOf(values[i]), detail(bin, values[i], c, if (showArea && total > 0) c / total * 100 else 0.0))
            },
        xTicks = ticks(domain.first, domain.second, X_TICKS).map { Tick(it, xTickLabel(it)) },
        yMax = yMax,
        yTicks = ticks(0.0, yMax, Y_TICKS).map { Tick(it, fmt.number(it)) },
        overlay = overlay,
    )
}

private fun Map<Int, Int>.sortedEntries() = entries.sortedBy { it.key }

private fun List<Map.Entry<Int, Int>>.binSum() = sumOf { it.value }.toDouble()

fun futureDue(
    data: GraphsResponse,
    fmt: StatsFormat,
    range: GraphRange,
    includeBacklog: Boolean,
): ChartAndTable {
    val tr = fmt.tr
    val dueCounts = data.futureDue.futureDueMap
    val entries = dueCounts.sortedEntries().filter { includeBacklog || it.key >= 0 }
    if (entries.isEmpty()) return ChartAndTable(null, emptyList())
    val xMin = if (includeBacklog) entries.first().key.toDouble() else 0.0
    val xMax =
        when (range) {
            GraphRange.Month -> 31.0
            GraphRange.ThreeMonths -> 90.0
            GraphRange.Year -> 365.0
            GraphRange.AllTime -> entries.last().key.toDouble()
        }
    val desiredBars = minOf(MAX_BARS.toDouble(), xMax - xMin)
    val bins = bin(entries, xMin to xMax, ticks(xMin, xMax, desiredBars)) { it.key.toDouble() }
    val values = bins.map { it.items.binSum() }
    val total = values.sum()
    if (total == 0.0) return ChartAndTable(null, emptyList())

    val chart =
        histogram(fmt, bins, values, xMin to xMax, showArea = true) { bin, value, cumulative, _ ->
            val end = if (bin.x1 == xMax) bin.x1 + 1 else bin.x1
            listOf(
                fmt.dayLabel(bin.x0.toInt(), end.toInt()),
                tr.statisticsCardsDue(value.toInt()),
                "${tr.statisticsRunningTotal()}: ${fmt.number(cumulative)}",
            )
        }
    val periodDays = (xMax - xMin).coerceAtLeast(1.0)
    return ChartAndTable(
        chart,
        listOf(
            StatRow(tr.statisticsTotal(), tr.statisticsReviews(total.toInt())),
            StatRow(tr.statisticsAverage(), tr.statisticsReviewsPerDay((total / periodDays).roundToInt())),
            StatRow(tr.statisticsDueTomorrow(), tr.statisticsReviews(dueCounts[1] ?: 0)),
            StatRow(tr.statisticsDailyLoad(), tr.statisticsReviewsPerDay(data.futureDue.dailyLoad)),
        ),
    )
}

fun added(
    data: GraphsResponse,
    fmt: StatsFormat,
    range: GraphRange,
): ChartAndTable {
    val tr = fmt.tr
    val entries = data.added.addedMap.sortedEntries()
    if (entries.isEmpty()) return ChartAndTable(null, emptyList())
    val xMin =
        when (range) {
            GraphRange.Month -> -31.0
            GraphRange.ThreeMonths -> -90.0
            GraphRange.Year -> -365.0
            GraphRange.AllTime -> entries.first().key.toDouble()
        }
    val xMax = 1.0
    val desiredBars = minOf(MAX_BARS.toDouble(), abs(xMin))
    val bins = bin(entries, xMin to xMax, ticks(xMin, xMax, desiredBars)) { it.key.toDouble() }
    val values = bins.map { it.items.binSum() }
    val totalInPeriod = values.sum()
    if (totalInPeriod == 0.0) return ChartAndTable(null, emptyList())

    val chart =
        histogram(fmt, bins, values, xMin to xMax, showArea = true) { bin, value, cumulative, _ ->
            listOf(
                fmt.dayLabel(bin.x0.toInt(), bin.x1.toInt()),
                tr.statisticsCards(value.toInt()),
                "${tr.statisticsRunningTotal()}: ${tr.statisticsCards(cumulative.toInt())}",
            )
        }
    val periodDays = abs(xMin).coerceAtLeast(1.0)
    return ChartAndTable(
        chart,
        listOf(
            StatRow(tr.statisticsTotal(), tr.statisticsCards(totalInPeriod.toInt())),
            StatRow(tr.statisticsAverage(), tr.statisticsCardsPerDay((totalInPeriod / periodDays).roundToInt())),
        ),
    )
}

/** `intervals.ts`, for the review intervals or, with [fsrs], the stability. */
fun intervals(
    intervals: GraphsResponse.Intervals,
    fmt: StatsFormat,
    range: IntervalRange,
    fsrs: Boolean,
): ChartAndTable {
    val tr = fmt.tr
    val all =
        buildList {
            for ((interval, count) in intervals.intervalsMap) repeat(count) { add(interval) }
        }.sorted()
    if (all.isEmpty()) return ChartAndTable(null, emptyList())

    var xMax: Double = all.last().toDouble()
    var niceNecessary = false
    when (range) {
        IntervalRange.Month -> xMax = minOf(xMax, 30.0)
        IntervalRange.Percentile50 -> {
            xMax = quantileSorted(all, 0.5)!!
            niceNecessary = true
        }
        IntervalRange.Percentile95 -> {
            xMax = quantileSorted(all, 0.95)!!
            niceNecessary = true
        }
        IntervalRange.All -> niceNecessary = true
    }
    xMax += 1
    val increment: (Double) -> Double = if (fsrs) ({ it }) else ({ it + 1 })
    val desiredBars = minOf(MAX_BARS.toDouble(), xMax)
    val prescale = if (niceNecessary) nice(0.0 to xMax) else 0.0 to xMax
    val domain = increment(prescale.first) to increment(prescale.second)
    val baseTicks = ticks(domain.first, domain.second, desiredBars)
    val thresholds =
        baseTicks.flatMapIndexed { idx, x ->
            val shifted = x - (baseTicks[0] - 1)
            if (idx == baseTicks.lastIndex) listOf(shifted, x + 1) else listOf(shifted)
        }
    val bins = bin(all, domain, thresholds) { it.toDouble() }
    val values = bins.map { it.items.size.toDouble() }
    if (values.sum() == 0.0) return ChartAndTable(null, emptyList())

    val chart =
        histogram(fmt, bins, values, domain, showArea = true) { bin, value, _, percent ->
            listOf(
                intervalLabel(tr, bin.x0.toInt(), bin.x1.toInt(), value.toInt(), fsrs),
                "${tr.statisticsRunningTotal()}: ‎${fmt.number(percent, 1)}%",
            )
        }
    val median = (quantileSorted(all, 0.5) ?: 0.0).roundToInt()
    return ChartAndTable(
        chart,
        listOf(
            StatRow(
                if (fsrs) tr.statisticsMedianStability() else tr.statisticsMedianInterval(),
                fmt.timeSpan(median * 86_400.0),
            ),
        ),
    )
}

private fun intervalLabel(
    tr: net.ankiweb.rsdroid.Translations,
    daysStart: Int,
    daysEnd: Int,
    cards: Int,
    fsrs: Boolean,
): String =
    if (daysEnd - daysStart <= 1) {
        if (fsrs) tr.statisticsStabilityDaySingle(cards, daysStart) else tr.statisticsIntervalsDaySingle(cards, daysStart)
    } else if (fsrs) {
        tr.statisticsStabilityDayRange(cards, daysStart, daysEnd - 1)
    } else {
        tr.statisticsIntervalsDayRange(cards, daysStart, daysEnd - 1)
    }

/**
 * `getAdjustedScaleAndTicks`: a rounded domain whose ticks start at [min]. With [minBinSize], bins
 * are never narrower than 1 (the percentage graphs); ease has no such floor.
 */
private fun adjustedScaleAndTicks(
    min: Double,
    max: Double,
    desiredBars: Double,
    minBinSize: Boolean,
): Pair<Pair<Double, Double>, List<Double>> {
    val prescale = nice(min to max)
    var tickList = ticks(prescale.first, prescale.second, desiredBars)
    val minOffset = min - prescale.first
    var tickSize = if (tickList.size >= 2) tickList[1] - tickList[0] else Double.NaN
    if (minBinSize && tickSize < 1) {
        tickList = generateSequence(min) { it + 1 }.takeWhile { it < max }.toList()
        tickSize = 1.0
    }
    if (minOffset == 0.0 || (minOffset % tickSize != 0.0 && tickSize % minOffset != 0.0)) {
        return prescale to tickList
    }
    return (prescale.first + minOffset to prescale.second + minOffset) to tickList.map { it + minOffset }
}

private fun percentHistogram(
    fmt: StatsFormat,
    map: Map<Int, Int>,
    min: Double,
    max: Double,
    minBinSize: Boolean,
    detail: (bin: Bin<Map.Entry<Int, Int>>, cards: Int) -> String,
): BarChart? {
    val entries = map.sortedEntries()
    val (domain, thresholds) = adjustedScaleAndTicks(min, max, 20.0, minBinSize)
    val bins = bin(entries, domain, thresholds) { it.key.toDouble() }
    val values = bins.map { it.items.binSum() }
    if (values.sum() == 0.0) return null
    return histogram(
        fmt,
        bins,
        values,
        domain,
        showArea = false,
        xTickLabel = { "${fmt.number(it, 0)}%" },
    ) { bin, value, _, _ -> listOf(detail(bin, value.toInt())) }
}

fun ease(
    data: GraphsResponse,
    fmt: StatsFormat,
): ChartAndTable {
    val eases = data.eases.easesMap
    if (eases.isEmpty()) return ChartAndTable(null, emptyList())
    val chart =
        percentHistogram(fmt, eases, 130.0, eases.keys.max() + 1.0, minBinSize = false) { bin, cards ->
            val percent =
                if (bin.x1.toInt() - bin.x0.toInt() <= 10) "${jsNumber(bin.x0)}%" else "${jsNumber(bin.x0)}%-${jsNumber(bin.x1)}%"
            fmt.tr.statisticsCardEaseTooltip(cards, percent)
        }
    return ChartAndTable(chart, listOf(StatRow(fmt.tr.statisticsMedianEase(), "${fmt.number(data.eases.average.toDouble(), 0)}%")))
}

/** `percentageRangeMinMax` */
private fun percentRangeMinMax(
    map: Map<Int, Int>,
    range: PercentageRange,
): Pair<Double, Double> {
    val quantile = range.quantile ?: return 0.0 to 100.0

    fun easeQuantile(q: Double): Int? {
        var count = map.values.sum() * q
        for ((key, value) in map.sortedEntries()) {
            count -= value
            if (count <= 0) return key
        }
        return null
    }
    return (easeQuantile(1 - quantile) ?: 0).toDouble() to (easeQuantile(quantile) ?: 0).toDouble()
}

fun difficulty(
    data: GraphsResponse,
    fmt: StatsFormat,
    range: PercentageRange,
): ChartAndTable {
    val map = data.difficulty.easesMap
    if (map.isEmpty()) return ChartAndTable(null, emptyList())
    val (min, max) = percentRangeMinMax(map, range)
    val chart =
        percentHistogram(fmt, map, min, max, minBinSize = true) { bin, cards ->
            fmt.tr.statisticsCardDifficultyTooltip(cards, "${jsNumber(bin.x0)}%-${jsNumber(bin.x1)}%")
        }
    return ChartAndTable(
        chart,
        listOf(StatRow(fmt.tr.statisticsMedianDifficulty(), "${fmt.number(data.difficulty.average.toDouble(), 0)}%")),
    )
}

fun retrievability(
    data: GraphsResponse,
    fmt: StatsFormat,
    range: PercentageRange,
): ChartAndTable {
    val tr = fmt.tr
    val source = data.retrievability
    val map = source.retrievabilityMap
    if (map.isEmpty()) return ChartAndTable(null, emptyList())
    val (min, max) = percentRangeMinMax(map, range)
    val chart =
        percentHistogram(fmt, map, min, max, minBinSize = true) { bin, cards ->
            tr.statisticsRetrievabilityTooltip(cards, "${jsNumber(bin.x0)}%-${jsNumber(bin.x1)}%")
        }
    return ChartAndTable(
        chart,
        listOf(
            StatRow(tr.statisticsAverageRetrievability(), "${fmt.number(source.average.toDouble(), 0)}%"),
            StatRow(
                tr.statisticsEstimatedTotalKnowledge(),
                "${tr.statisticsCards(source.sumByCard.roundToInt())} / ${tr.statisticsNotes(source.sumByNote.roundToInt())}",
            ),
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Reviews

/** Segments from the bottom of a reviews bar, with the page's darkest series lowest. */
private enum class ReviewKind(
    val fill: Fill,
) {
    Mature(Fill.Solid),
    Young(Fill.Hatch),
    Relearn(Fill.Cross),
    Learn(Fill.Dots),
    Filtered(Fill.Outline),
}

private fun GraphsResponse.ReviewCountsAndTimes.Reviews.byKind() =
    listOf(mature.toDouble(), young.toDouble(), relearn.toDouble(), learn.toDouble(), filtered.toDouble())

fun reviews(
    data: GraphsResponse,
    fmt: StatsFormat,
    range: GraphRange,
    showTime: Boolean,
): ChartAndTable {
    val tr = fmt.tr
    val countMap = data.reviews.countMap
    if (countMap.isEmpty()) return ChartAndTable(null, emptyList())
    val xMax = 1.0
    var xMin =
        when (range) {
            GraphRange.Month -> -30.0
            GraphRange.ThreeMonths -> -89.0
            GraphRange.Year -> -364.0
            GraphRange.AllTime -> countMap.keys.min().toDouble()
        }
    val desiredBars = minOf(MAX_BARS.toDouble(), abs(xMin))
    val originalXMin = xMin
    var thresholds = ticks(xMin, xMax, desiredBars)
    if (range == GraphRange.AllTime && thresholds.size >= 2) {
        val spacing = thresholds[1] - thresholds[0]
        val partial = thresholds[0] - xMin
        if (spacing > 0 && partial > 0 && partial < spacing) {
            xMin = thresholds[0] - spacing
            thresholds = ticks(xMin, xMax, desiredBars)
        }
    }
    if (range == GraphRange.Year || range == GraphRange.AllTime) {
        thresholds = thresholds.map { minOf(it + 1, 1.0) }.distinct().sorted()
    }
    val domain = xMin to xMax
    val source = if (showTime) data.reviews.timeMap else countMap
    val bins = bin(source.entries.sortedBy { it.key }, domain, thresholds) { it.key.toDouble() }
    val studiedDays = bins.sumOf { it.items.size }
    if (studiedDays == 0) return ChartAndTable(null, emptyList())

    val totals =
        bins.map { bin ->
            bin.items
                .fold(DoubleArray(5)) { acc, entry ->
                    entry.value.byKind().forEachIndexed { i, v -> acc[i] += v }
                    acc
                }.toList()
        }
    val barTotals = totals.map { it.sum() }
    val cumulative = barTotals.runningFold(0.0) { acc, v -> acc + v }
    val total = cumulative.last()

    fun tickLabel(n: Double): String =
        if (showTime) {
            fmt.timeSpan(n / 1000, short = true, maxUnit = StatsFormat.TimeUnit.Hours)
        } else if (n.roundToInt().toDouble() != n) {
            ""
        } else {
            fmt.number(n)
        }

    fun valueLabel(n: Double): String =
        if (showTime) fmt.timeSpan(n / 1000, maxUnit = StatsFormat.TimeUnit.Hours) else tr.statisticsReviews(n.roundToInt())

    val yMax = nice(0.0 to (barTotals.maxOrNull() ?: 0.0)).second
    val areaMax = if (total > 0) nice(0.0 to total).second else 0.0
    val kindLabels =
        listOf(
            ReviewKind.Filtered to tr.statisticsCountsFilteredCards(),
            ReviewKind.Learn to tr.statisticsCountsLearningCards(),
            ReviewKind.Relearn to tr.statisticsCountsRelearningCards(),
            ReviewKind.Young to tr.statisticsCountsYoungCards(),
            ReviewKind.Mature to tr.statisticsCountsMatureCards(),
        )
    val bars =
        bins.mapIndexed { i, bin ->
            val startDay = if (i == 0) originalXMin.toInt() else bin.x0.toInt()
            val endDay = if (bin.x1 == 0.0) 1 else bin.x1.toInt()
            val lines =
                buildList {
                    add("${fmt.dayLabel(startDay, endDay)}: ${valueLabel(barTotals[i])}")
                    for ((kind, label) in kindLabels) add("$label: ${valueLabel(totals[i][kind.ordinal])}")
                    add("${tr.statisticsRunningTotal()}: ${valueLabel(cumulative[i + 1])}")
                }
            Bar(bin.x0, bin.x1, totals[i], lines)
        }
    val chart =
        BarChart(
            xDomain = domain,
            bars = bars,
            xTicks = ticks(domain.first, domain.second, X_TICKS).map { Tick(it, fmt.number(it)) },
            yMax = yMax,
            yTicks = ticks(0.0, yMax, Y_TICKS).map { Tick(it, tickLabel(it)) },
            fills = ReviewKind.entries.map { it.fill },
            overlay =
                if (total > 0) {
                    Overlay(
                        points = cumulative.mapIndexed { i, c -> (if (i == 0) bins[0].x0 else bins[i - 1].x1) to c / areaMax },
                        ticks = ticks(0.0, areaMax, Y_TICKS).map { Tick(it / areaMax, tickLabel(it)) },
                    )
                } else {
                    null
                },
        )

    val periodDays = -originalXMin + 1
    val studiedPercent = studiedDays / periodDays * 100
    val periodAverage = total / periodDays
    val studiedAverage = total / studiedDays
    val table =
        buildList {
            val percentText =
                when {
                    studiedPercent < 99.5 -> fmt.number(studiedPercent)
                    studiedPercent < 99.95 -> fmt.number(studiedPercent, 1)
                    studiedPercent < 100 -> fmt.number(studiedPercent, 2)
                    else -> "100"
                }
            val studiedText = tr.statisticsAmountOfTotalWithPercentage(studiedDays, periodDays.toInt(), percentText)
            add(StatRow(tr.statisticsDaysStudied(), studiedText))
            if (showTime) {
                add(StatRow(tr.statisticsTotal(), valueLabel(total)))
                add(StatRow(tr.statisticsAverageOverPeriod(), tr.statisticsMinutesPerDay((periodAverage / 1000 / 60).roundToInt())))
                if (studiedPercent < 100) {
                    add(
                        StatRow(
                            tr.statisticsAverageForDaysStudied(),
                            tr.statisticsMinutesPerDay((studiedAverage / 1000 / 60).roundToInt()),
                        ),
                    )
                }
                val totalReviews =
                    countMap.entries
                        .filter { it.key >= domain.first && it.key <= domain.second }
                        .sumOf { it.value.byKind().sum() }
                val totalSecs = total / 1000
                add(
                    StatRow(
                        tr.statisticsAverageAnswerTimeLabel(),
                        tr.statisticsAverageAnswerTime(totalSecs / totalReviews, totalReviews * 60 / totalSecs),
                    ),
                )
            } else {
                add(StatRow(tr.statisticsTotal(), tr.statisticsReviews(total.roundToInt())))
                add(StatRow(tr.statisticsAverageOverPeriod(), tr.statisticsReviewsPerDay(periodAverage.roundToInt())))
                if (studiedPercent < 100) {
                    add(StatRow(tr.statisticsAverageForDaysStudied(), tr.statisticsReviewsPerDay(studiedAverage.roundToInt())))
                }
            }
        }
    // the legend: which pattern is which kind of review
    val legend = kindLabels.reversed().map { (kind, label) -> StatRow(label, "", kind.fill) }
    return ChartAndTable(chart, legend + table)
}

// ---------------------------------------------------------------------------------------------
// Card counts

private data class CountRow(
    val label: String,
    val count: Int,
    val show: Boolean,
    val fill: Fill,
)

/** The card counts: one horizontal bar split by kind, then a row per kind with its share. */
fun cardCounts(
    data: GraphsResponse,
    fmt: StatsFormat,
    separateInactive: Boolean,
): ChartAndTable {
    val tr = fmt.tr
    val counts = if (separateInactive) data.cardCounts.excludingInactive else data.cardCounts.includingInactive
    val rows =
        listOf(
            CountRow(tr.statisticsCountsNewCards(), counts.newCards, true, Fill.Outline),
            CountRow(tr.statisticsCountsLearningCards(), counts.learn, true, Fill.Dots),
            CountRow(tr.statisticsCountsRelearningCards(), counts.relearn, true, Fill.Cross),
            CountRow(tr.statisticsCountsYoungCards(), counts.young, true, Fill.Hatch),
            CountRow(tr.statisticsCountsMatureCards(), counts.mature, true, Fill.Solid),
            CountRow(tr.statisticsCountsSuspendedCards(), counts.suspended, separateInactive, Fill.BackHatch),
            CountRow(tr.statisticsCountsBuriedCards(), counts.buried, separateInactive, Fill.Grid),
        )
    val sum = rows.sumOf { it.count }
    val xMax = maxOf(1, sum).toDouble()
    val table =
        rows.filter { it.show }.map {
            StatRow(it.label, "${fmt.number(it.count)} · ${fmt.number(it.count / xMax * 100, 2)}%", it.fill)
        } + StatRow(tr.statisticsCountsTotalCards(), fmt.number(sum))
    val chart =
        if (sum == 0) {
            null
        } else {
            BarChart(
                xDomain = 0.0 to 1.0,
                bars = listOf(Bar(0.0, 1.0, rows.map { it.count.toDouble() }, emptyList())),
                xTicks = emptyList(),
                yMax = sum.toDouble(),
                yTicks = emptyList(),
                fills = rows.map { it.fill },
            )
        }
    return ChartAndTable(chart, table)
}

// ---------------------------------------------------------------------------------------------
// Hours and answer buttons

private fun <T> GraphRange.pick(
    month: T,
    threeMonths: T,
    year: T,
    allTime: T,
): T =
    when (this) {
        GraphRange.Month -> month
        GraphRange.ThreeMonths -> threeMonths
        GraphRange.Year -> year
        GraphRange.AllTime -> allTime
    }

fun hours(
    data: GraphsResponse,
    fmt: StatsFormat,
    range: GraphRange,
): BarChart? {
    val tr = fmt.tr
    val source = data.hours
    val hours = range.pick(source.oneMonthList, source.threeMonthsList, source.oneYearList, source.allTimeList)
    val max = hours.maxOfOrNull { it.total } ?: 0
    if (max == 0) return null
    val yMax = nice(0.0 to max.toDouble()).second
    return BarChart(
        xDomain = 0.0 to hours.size.toDouble(),
        bars =
            hours.mapIndexed { hour, h ->
                val percent = if (h.total != 0) h.correct.toDouble() / h.total * 100 else 0.0
                Bar(
                    hour.toDouble(),
                    hour + 1.0,
                    listOf(h.total.toDouble()),
                    listOf(
                        tr.statisticsHoursRange(hour, hour + 1),
                        tr.statisticsHoursReviews(h.total),
                        tr.statisticsHoursCorrectReviews(percent, h.correct),
                    ),
                )
            },
        // the page hides odd hours on a narrow screen
        xTicks = hours.indices.filter { it % 2 == 0 }.map { Tick(it + 0.5, it.toString()) },
        yMax = yMax,
        yTicks = ticks(0.0, yMax, Y_TICKS).map { Tick(it, fmt.number(it)) },
        overlay =
            Overlay(
                points = hours.mapIndexed { hour, h -> if (h.total > 0) hour + 0.5 to h.correct.toDouble() / h.total else null },
                ticks = ticks(0.0, 1.0, Y_TICKS).map { Tick(it, "${(it * 100).roundToInt()}%") },
            ),
        barGap = 0.1,
    )
}

/** The answer buttons: for learning, young and mature cards, how often each button was pressed. */
fun buttons(
    data: GraphsResponse,
    fmt: StatsFormat,
    range: GraphRange,
): ChartAndTable {
    val tr = fmt.tr
    val source = data.buttons
    val counts = range.pick(source.oneMonth, source.threeMonths, source.oneYear, source.allTime)
    val groups =
        listOf(
            tr.statisticsCountsLearningCards() to counts.learningList,
            tr.statisticsCountsYoungCards() to counts.youngList,
            tr.statisticsCountsMatureCards() to counts.matureList,
        )
    val max = groups.maxOf { (_, list) -> list.maxOrNull() ?: 0 }
    if (max == 0) return ChartAndTable(null, emptyList())
    val buttonNames = listOf(tr.studyingAgain(), tr.studyingHard(), tr.studyingGood(), tr.studyingEasy())
    // d3's band scales: four buttons with an outer padding of one step and an inner one of 0.1
    val step = 1.0 / (4 - 0.1 + 2)
    val bars = mutableListOf<Bar>()
    val table = mutableListOf<StatRow>()
    groups.forEachIndexed { g, (label, list) ->
        val total = list.sum()
        val correct = list.drop(1).sum()
        val percent = if (total != 0) fmt.number(correct.toDouble() / total * 100) else "0"
        val correctText = tr.statisticsHoursCorrect(correct, total, percent)
        table += StatRow(label, correctText)
        list.forEachIndexed { b, count ->
            val x0 = g + step + b * step
            val pressedPercent = if (total != 0) fmt.number(count.toDouble() / total * 100) else "0"
            bars +=
                Bar(
                    x0,
                    x0 + step * 0.9,
                    listOf(count.toDouble()),
                    listOf(
                        "$label · ${tr.statisticsAnswerButtonsButtonNumber()}: ${b + 1} (${buttonNames.getOrElse(b) { "" }})",
                        "${tr.statisticsAnswerButtonsButtonPressed()}: ${fmt.number(count)} ($pressedPercent%)",
                        "$correctText ${tr.statisticsHoursCorrectInfo()}",
                    ),
                )
        }
    }
    val chart =
        BarChart(
            xDomain = 0.0 to 3.0,
            bars = bars,
            xTicks = groups.mapIndexed { g, (label, _) -> Tick(g + 0.5, label) },
            yMax = max.toDouble(),
            yTicks = ticks(0.0, max.toDouble(), Y_TICKS).map { Tick(it, fmt.number(it)) },
        )
    return ChartAndTable(chart, table)
}

// ---------------------------------------------------------------------------------------------
// Calendar

/**
 * `calendar.ts`: a year of days, one column per week. The page shades a day by the square root of
 * its count against the busiest day; here that becomes one of four square sizes.
 */
fun calendar(
    data: GraphsResponse,
    fmt: StatsFormat,
    year: Int,
    firstDayOfWeek: Weekday,
    revlogRange: RevlogRange,
    now: ZonedDateTime,
    locale: Locale,
): Calendar {
    val maxYear = now.year
    val minYear =
        when (revlogRange) {
            RevlogRange.Year -> maxYear - 1
            RevlogRange.All -> 2000
        }
    val targetYear = year.coerceIn(minYear, maxYear)
    val first = firstDayOfWeek.toDayOfWeek()
    val weekdayLabels = (0 until 7).map { first.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }

    val counts =
        data.reviews.countMap.mapValues { (_, r) -> r.learn + r.relearn + r.mature + r.filtered + r.young }
    val maxCount = counts.values.maxOrNull() ?: 0
    if (maxCount == 0) return Calendar(targetYear, minYear, maxYear, weekdayLabels, null)

    val yearStart = LocalDate.of(targetYear, 1, 1)
    val byDate = mutableMapOf<LocalDate, Pair<Int, Int>>() // date -> (day offset, count)
    for ((day, count) in counts) {
        val date = now.plusDays(day.toLong()).minusHours(data.rolloverHour.toLong()).toLocalDate()
        if (date.year == targetYear) byDate[date] = day to count
    }
    val today = now.toLocalDate()
    val oneYearAgo = now.minusYears(1).toLocalDate()
    val days =
        (0 until 365).mapNotNull { i ->
            val date = yearStart.plusDays(i.toLong())
            val reviewed = byDate[date]
            if (reviewed == null && (date.isAfter(today) || (revlogRange == RevlogRange.Year && date.isBefore(oneYearAgo)))) {
                return@mapNotNull null
            }
            val count = reviewed?.second ?: 0
            val weekday = ((date.dayOfWeek.value - first.value) + 7) % 7
            // d3's `timeSunday.count(yearStart, date)`: week starts after the first of January, up to the date
            val week =
                (1..ChronoUnit.DAYS.between(yearStart, date)).count { yearStart.plusDays(it).dayOfWeek == first }
            CalendarDay(
                week = week,
                weekday = weekday,
                level = if (count == 0) 0 else ceil(sqrt(count.toDouble() / maxCount) * 4).toInt().coerceIn(1, 4),
                detail = "${date.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale),
                )}: ${fmt.tr.statisticsReviews(count)}",
            )
        }
    return Calendar(targetYear, minYear, maxYear, weekdayLabels, days)
}

private fun Weekday.toDayOfWeek(): DayOfWeek =
    when (this) {
        Weekday.MONDAY -> DayOfWeek.MONDAY
        Weekday.FRIDAY -> DayOfWeek.FRIDAY
        Weekday.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

// ---------------------------------------------------------------------------------------------
// True retention

/** One period of the true retention table, e.g. "Today" and its figures in the chosen mode. */
fun trueRetention(
    data: GraphsResponse,
    fmt: StatsFormat,
    mode: RetentionMode,
    revlogRange: RevlogRange,
): List<StatRow> {
    val tr = fmt.tr
    val stats = data.trueRetention
    val periods =
        buildList {
            add(tr.statisticsTrueRetentionToday() to stats.today)
            add(tr.statisticsTrueRetentionYesterday() to stats.yesterday)
            add(tr.statisticsTrueRetentionWeek() to stats.week)
            add(tr.statisticsTrueRetentionMonth() to stats.month)
            add(tr.statisticsTrueRetentionYear() to stats.year)
            if (revlogRange == RevlogRange.All) add(tr.statisticsTrueRetentionAllTime() to stats.allTime)
        }

    fun retention(
        passed: Int,
        failed: Int,
    ): String =
        if (passed + failed ==
            0
        ) {
            tr.statisticsTrueRetentionNotApplicable()
        } else {
            fmt.percentOneDecimal(passed.toDouble() / (passed + failed))
        }

    return periods.map { (title, r) ->
        val value =
            when (mode) {
                RetentionMode.Summary -> {
                    val passed = r.youngPassed + r.maturePassed
                    val failed = r.youngFailed + r.matureFailed
                    listOf(
                        "${tr.statisticsTrueRetentionYoung()} ${retention(r.youngPassed, r.youngFailed)}",
                        "${tr.statisticsTrueRetentionMature()} ${retention(r.maturePassed, r.matureFailed)}",
                        "${tr.statisticsTrueRetentionTotal()} ${retention(passed, failed)}",
                        "${tr.statisticsTrueRetentionCount()} ${fmt.number(passed + failed)}",
                    ).joinToString(" · ")
                }
                RetentionMode.Young -> single(tr, fmt, r.youngPassed, r.youngFailed, ::retention)
                RetentionMode.Mature -> single(tr, fmt, r.maturePassed, r.matureFailed, ::retention)
            }
        StatRow(title, value)
    }
}

private fun single(
    tr: net.ankiweb.rsdroid.Translations,
    fmt: StatsFormat,
    passed: Int,
    failed: Int,
    retention: (Int, Int) -> String,
): String =
    listOf(
        "${tr.statisticsTrueRetentionPass()} ${fmt.number(passed)}",
        "${tr.statisticsTrueRetentionFail()} ${fmt.number(failed)}",
        "${tr.statisticsTrueRetentionRetention()} ${retention(passed, failed)}",
    ).joinToString(" · ")

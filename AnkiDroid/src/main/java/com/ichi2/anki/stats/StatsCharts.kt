// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

/**
 * How a bar segment is filled. The backend's page tells series apart by colour; on E Ink they are
 * told apart by pattern, with the darkest pattern for the series the page drew darkest.
 */
enum class Fill {
    Solid,
    Hatch,
    Dots,
    BackHatch,
    Outline,
    Cross,
    Grid,
}

/** A labelled position on an axis. */
data class Tick(
    val at: Double,
    val label: String,
)

/**
 * One bar from [x0] to [x1] in data units. [stack] holds its segments from the bottom up, each
 * drawn with the [BarChart.fills] entry at the same index. [detail] is what the page showed on
 * hover, shown here when the bar is tapped.
 */
data class Bar(
    val x0: Double,
    val x1: Double,
    val stack: List<Double>,
    val detail: List<String>,
) {
    val total: Double get() = stack.sum()
}

/**
 * A line over the bars, against its own axis at the right: the running total of a histogram, or the
 * share of correct answers by hour. [points] are in x data units and a 0–1 fraction of the height;
 * a null point breaks the line.
 */
data class Overlay(
    val points: List<Pair<Double, Double>?>,
    val ticks: List<Tick>,
)

data class BarChart(
    val xDomain: Pair<Double, Double>,
    val bars: List<Bar>,
    val xTicks: List<Tick>,
    /** The top of the left axis; bars are drawn against it. */
    val yMax: Double,
    val yTicks: List<Tick>,
    val fills: List<Fill> = listOf(Fill.Solid),
    val overlay: Overlay? = null,
    /** Gap between bars as a fraction of each bar's width; 0 leaves the page's 1px. */
    val barGap: Double = 0.0,
)

/** A graph's summary under the chart, e.g. "Total / 1,204 reviews". */
data class StatRow(
    val label: String,
    val value: String,
    /** The pattern of the series this row describes, drawn as a swatch; null for none. */
    val fill: Fill? = null,
    /** This row's share of the whole, drawn as a solid bar under it; null for none. */
    val fraction: Double? = null,
)

data class ChartAndTable(
    /** null when the page would show "No data". */
    val chart: BarChart?,
    val table: List<StatRow>,
)

/** One day of the calendar: its column (week) and row (weekday) and how much was reviewed. */
data class CalendarDay(
    val week: Int,
    val weekday: Int,
    /** 0 for no reviews, else 1–4, from the square root of the count as the page shades it. */
    val level: Int,
    val detail: String,
)

data class Calendar(
    val year: Int,
    val minYear: Int,
    val maxYear: Int,
    /** Narrow names of the seven weekdays, starting from the first day of the week. */
    val weekdayLabels: List<String>,
    /** null when there are no reviews at all. */
    val days: List<CalendarDay>?,
)

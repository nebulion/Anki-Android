// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/*
 * Charts drawn for E Ink: black on white, no animation, no hover. A series is a pattern rather
 * than a colour; a tap selects a bar or day, which is outlined, and its numbers are shown as text
 * under the chart by the caller.
 */

private val Black = Color.Black
private val White = Color.White

/** Fills [rect] with [fill]'s pattern in black. Every segment but a solid one gets an outline. */
fun DrawScope.drawFill(
    rect: Rect,
    fill: Fill,
) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val hairline = 1.dp.toPx()
    val spacing = 4.dp.toPx()
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        when (fill) {
            Fill.Solid -> drawRect(Black, rect.topLeft, rect.size)
            Fill.Hatch, Fill.BackHatch, Fill.Cross -> {
                val forward = fill != Fill.BackHatch
                val backward = fill != Fill.Hatch
                var offset = -rect.height
                while (offset < rect.width + rect.height) {
                    if (forward) {
                        drawLine(
                            Black,
                            Offset(rect.left + offset, rect.bottom),
                            Offset(rect.left + offset + rect.height, rect.top),
                            hairline,
                        )
                    }
                    if (backward) {
                        drawLine(
                            Black,
                            Offset(rect.left + offset, rect.top),
                            Offset(rect.left + offset + rect.height, rect.bottom),
                            hairline,
                        )
                    }
                    offset += spacing
                }
            }
            Fill.Dots -> {
                val dot = 1.5.dp.toPx()
                var y = rect.top + spacing / 2
                while (y < rect.bottom) {
                    var x = rect.left + spacing / 2
                    while (x < rect.right) {
                        drawRect(Black, Offset(x - dot / 2, y - dot / 2), Size(dot, dot))
                        x += spacing * 0.75f
                    }
                    y += spacing * 0.75f
                }
            }
            Fill.Grid -> {
                var x = rect.left
                while (x < rect.right) {
                    drawLine(Black, Offset(x, rect.top), Offset(x, rect.bottom), hairline)
                    x += spacing
                }
                var y = rect.top
                while (y < rect.bottom) {
                    drawLine(Black, Offset(rect.left, y), Offset(rect.right, y), hairline)
                    y += spacing
                }
            }
            Fill.Outline -> Unit
        }
    }
    if (fill != Fill.Solid) {
        drawRect(Black, rect.topLeft, rect.size, style = Stroke(hairline))
    }
}

/** A small square of [fill], used as the legend beside a table row. */
@Composable
fun FillSwatch(
    fill: Fill,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(16.dp)) { drawFill(Rect(Offset.Zero, size), fill) }
}

/**
 * A bar chart with a left axis, an x axis and, if [BarChart.overlay] is set, a line against a right
 * axis. [selected] is outlined; [onSelect] receives the index of the tapped bar.
 */
@Composable
fun BarChartView(
    chart: BarChart,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodySmall.copy(color = Black)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .pointerInput(chart) {
                detectTapGestures { tap ->
                    val plot = plotArea(size.width.toFloat(), size.height.toFloat(), chart.overlay != null)
                    val x = chart.xDomain.first + (tap.x - plot.left) / plot.width * (chart.xDomain.second - chart.xDomain.first)
                    val index =
                        chart.bars.indexOfFirst { x >= it.x0 && x < it.x1 }.takeIf { it >= 0 }
                            ?: chart.bars
                                .withIndex()
                                .minByOrNull { (_, bar) ->
                                    min(kotlin.math.abs(bar.x0 - x), kotlin.math.abs(bar.x1 - x))
                                }?.index
                    index?.let(onSelect)
                }
            },
    ) {
        val plot = plotArea(size.width, size.height, chart.overlay != null)
        val (d0, d1) = chart.xDomain
        val span = (d1 - d0).takeIf { it > 0 } ?: 1.0

        fun xAt(value: Double) = plot.left + ((value - d0) / span * plot.width).toFloat()

        fun yAt(value: Double) = plot.bottom - (value / chart.yMax.coerceAtLeast(1e-9) * plot.height).toFloat()

        // left axis labels and faint guides
        for (tick in chart.yTicks) {
            val y = yAt(tick.at)
            drawLine(Black, Offset(plot.left, y), Offset(plot.right, y), 0.5f, pathEffect = DottedGuide)
            drawLabel(measurer, tick.label, style, Offset(plot.left - 4.dp.toPx(), y), alignEnd = true)
        }

        // bars
        val onePixel = 1f
        chart.bars.forEachIndexed { index, bar ->
            val left = xAt(bar.x0)
            val right = xAt(bar.x1)
            val gap = max(onePixel, ((right - left) * chart.barGap).toFloat())
            val barLeft = left + gap / 2
            val barRight = right - gap / 2
            var base = 0.0
            bar.stack.forEachIndexed { i, value ->
                if (value > 0) {
                    val top = yAt(base + value)
                    drawFill(Rect(barLeft, top, max(barRight, barLeft + onePixel), yAt(base)), chart.fills.getOrElse(i) { Fill.Solid })
                    base += value
                }
            }
            if (index == selected) {
                drawRect(
                    Black,
                    Offset(left - 2.dp.toPx(), plot.top),
                    Size(right - left + 4.dp.toPx(), plot.height),
                    style = Stroke(1.5.dp.toPx(), pathEffect = SelectionDash),
                )
            }
        }

        // the running total or correct share, white-edged so it reads over black bars
        chart.overlay?.let { overlay ->
            val path = Path()
            var drawing = false
            for (point in overlay.points) {
                if (point == null) {
                    drawing = false
                    continue
                }
                val offset = Offset(xAt(point.first), plot.bottom - (point.second * plot.height).toFloat())
                if (drawing) path.lineTo(offset.x, offset.y) else path.moveTo(offset.x, offset.y)
                drawing = true
            }
            drawPath(path, White, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Black, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            for (tick in overlay.ticks) {
                val y = plot.bottom - (tick.at * plot.height).toFloat()
                drawLabel(measurer, tick.label, style, Offset(plot.right + 4.dp.toPx(), y), alignEnd = false)
            }
        }

        // x axis
        drawLine(Black, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), 1.dp.toPx())
        var lastLabelEnd = Float.NEGATIVE_INFINITY
        for (tick in chart.xTicks) {
            val x = xAt(tick.at)
            val layout = measurer.measure(tick.label, style)
            val left = x - layout.size.width / 2f
            // labels that would overlap the previous one are left out
            if (left < lastLabelEnd + 4.dp.toPx() || left < 0 || left + layout.size.width > size.width) continue
            drawLine(Black, Offset(x, plot.bottom), Offset(x, plot.bottom + 3.dp.toPx()), 1.dp.toPx())
            drawText(layout, topLeft = Offset(left, plot.bottom + 4.dp.toPx()))
            lastLabelEnd = left + layout.size.width
        }
    }
}

/** One bar across the width, split by kind: the card counts, which the page draws as a pie. */
@Composable
fun StackedBarView(
    chart: BarChart,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        val bar = chart.bars.single()
        var left = 0f
        bar.stack.forEachIndexed { i, value ->
            if (value > 0) {
                val width = (value / chart.yMax * size.width).toFloat()
                drawFill(Rect(left, 0f, left + width, size.height), chart.fills[i])
                left += width
            }
        }
        drawRect(Black, Offset.Zero, size, style = Stroke(1.dp.toPx()))
    }
}

/**
 * The review calendar: a column per week and a row per weekday. A day with reviews is a black
 * square, larger for more reviews; a day without is a dot. [onSelect] receives the tapped day.
 */
@Composable
fun CalendarView(
    calendar: Calendar,
    selected: CalendarDay?,
    onSelect: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = calendar.days ?: return
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodySmall.copy(color = Black)
    val columns = CALENDAR_WEEKS + CALENDAR_LABEL_COLUMNS
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight * 0.6f)
            .pointerInput(calendar) {
                detectTapGestures { tap ->
                    val cell = size.width / columns.toFloat()
                    val week = ((tap.x / cell) - CALENDAR_LABEL_COLUMNS).toInt()
                    val weekday = (tap.y / (size.height / 7f)).toInt()
                    days.firstOrNull { it.week == week && it.weekday == weekday }?.let(onSelect)
                }
            },
    ) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / 7
        val cell = min(cellWidth, cellHeight)
        calendar.weekdayLabels.forEachIndexed { row, label ->
            drawLabel(
                measurer,
                label,
                style,
                Offset(CALENDAR_LABEL_COLUMNS * cellWidth - 2.dp.toPx(), (row + 0.5f) * cellHeight),
                alignEnd = true,
            )
        }
        for (day in days) {
            val centre = Offset((CALENDAR_LABEL_COLUMNS + day.week + 0.5f) * cellWidth, (day.weekday + 0.5f) * cellHeight)
            val side = if (day.level == 0) 1.5.dp.toPx() else (cell - 1f) * (day.level + 1) / 5f
            drawRect(Black, Offset(centre.x - side / 2, centre.y - side / 2), Size(side, side))
            if (day == selected) {
                drawRect(
                    Black,
                    Offset(centre.x - cellWidth / 2 - 1f, centre.y - cellHeight / 2 - 1f),
                    Size(cellWidth + 2f, cellHeight + 2f),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
    }
}

private const val CALENDAR_WEEKS = 54
private const val CALENDAR_LABEL_COLUMNS = 2

private val ChartHeight = 200.dp
private val DottedGuide = PathEffect.dashPathEffect(floatArrayOf(2f, 6f))
private val SelectionDash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))

/** The plotting rectangle inside a chart of [width] × [height] pixels, leaving room for labels. */
private fun androidx.compose.ui.unit.Density.plotArea(
    width: Float,
    height: Float,
    hasRightAxis: Boolean,
): Rect {
    val left = 40.dp.toPx()
    val right = if (hasRightAxis) 40.dp.toPx() else 8.dp.toPx()
    return Rect(left, 8.dp.toPx(), width - right, height - 22.dp.toPx())
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    anchor: Offset,
    alignEnd: Boolean,
) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, style)
    val x = if (alignEnd) anchor.x - layout.size.width else anchor.x
    drawText(layout, topLeft = Offset(x, anchor.y - layout.size.height / 2f))
}

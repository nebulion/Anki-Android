// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import anki.stats.CardStatsResponse.StatsRevlogEntry
import anki.stats.RevlogEntry.ReviewKind
import com.ichi2.anki.stats.ticks
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * The FSRS forgetting curve on the card info page, ported from the backend page
 * (`ts/routes/card-info/forgetting-curve.ts`, Anki 26.05): how likely the card was to be recalled
 * over time since it was first learnt, reset by each review, then projected ahead. Drawn in black:
 * the past solid, the projection dashed, the desired retention dotted.
 */

/** How much of the card's history the curve shows. */
enum class CurveRange(
    val maxDays: Double,
) {
    Week(7.0),
    Month(30.0),
    Year(365.0),
    AllTime(Double.POSITIVE_INFINITY),
}

data class CurvePoint(
    /** Seconds since the epoch. */
    val time: Double,
    val daysSinceFirstLearn: Double,
    /** 0–100 */
    val retrievability: Double,
)

private const val MIN_POINTS = 1000
private const val DAY = 86_400.0

private fun forgettingCurve(
    stability: Double,
    daysElapsed: Double,
    decay: Double,
): Double {
    val factor = 0.9.pow(1 / -decay) - 1
    return (daysElapsed / stability * factor + 1.0).pow(-decay)
}

private fun StatsRevlogEntry.countsForCurve(): Boolean =
    reviewKind != ReviewKind.MANUAL &&
        reviewKind != ReviewKind.RESCHEDULED &&
        (reviewKind != ReviewKind.FILTERED || ease != 0)

/** `filterRevlog`: the reviews since the card was last reset, newest first, that move the curve. */
fun curveRevlog(revlog: List<StatsRevlogEntry>): List<StatsRevlogEntry> =
    revlog.takeWhile { !(it.reviewKind == ReviewKind.MANUAL && it.ease == 0) && it.hasMemoryState() }.filter { it.countsForCurve() }

/** `decay`: FSRS-6's parameter 21, or the older versions' fixed decays. */
fun curveDecay(params: List<Float>): Double =
    when {
        params.isEmpty() -> 0.1542
        params.size < 21 -> 0.5
        else -> params[20].toDouble()
    }

/** `calculateMaxDays`: from the first review to a little past the next due one, capped by [range]. */
fun curveMaxDays(
    revlog: List<StatsRevlogEntry>,
    range: CurveRange,
    now: Double,
): Double {
    if (revlog.isEmpty()) return 0.0
    val daysSinceFirstLearn = (now - revlog.last().time) / DAY
    val daysSinceLastReview = (now - revlog.first().time) / DAY
    val lastScheduledDays = revlog.first().interval / DAY
    val previewDays = max(lastScheduledDays * 1.5 - daysSinceLastReview, lastScheduledDays * 0.5)
    return min(daysSinceFirstLearn + previewDays, range.maxDays)
}

/** `prepareData`: the curve's points, oldest first. */
fun curvePoints(
    revlog: List<StatsRevlogEntry>,
    maxDays: Double,
    decay: Double,
    now: Double,
): List<CurvePoint> {
    if (revlog.isEmpty()) return emptyList()
    val data = mutableListOf<CurvePoint>()
    val step = min(maxDays / MIN_POINTS, 1.0).coerceAtLeast(1e-3)
    var lastReviewTime = 0.0
    var lastStability = 0.0
    var daysSinceFirstLearn = 0.0

    revlog.reversed().forEachIndexed { index, entry ->
        val reviewTime = entry.time.toDouble()
        if (index == 0) {
            lastReviewTime = reviewTime
            lastStability = entry.memoryState.stability.toDouble()
            data += CurvePoint(reviewTime, 0.0, 100.0)
            return@forEachIndexed
        }
        val totalDaysElapsed = (reviewTime - lastReviewTime) / DAY
        var elapsed = 0.0
        while (elapsed < totalDaysElapsed - step) {
            elapsed += step
            data +=
                CurvePoint(
                    lastReviewTime + elapsed * DAY,
                    data.last().daysSinceFirstLearn + step,
                    forgettingCurve(lastStability, elapsed, decay) * 100,
                )
        }
        daysSinceFirstLearn += totalDaysElapsed
        data += CurvePoint(lastReviewTime + totalDaysElapsed * DAY, daysSinceFirstLearn, 100.0)
        lastReviewTime = reviewTime
        lastStability = entry.memoryState.stability.toDouble()
    }

    val daysSinceLastReview = (now - lastReviewTime) / DAY
    var elapsed = 0.0
    while (elapsed < daysSinceLastReview - step) {
        elapsed += step
        data +=
            CurvePoint(
                lastReviewTime + elapsed * DAY,
                data.last().daysSinceFirstLearn + step,
                forgettingCurve(lastStability, elapsed, decay) * 100,
            )
    }
    daysSinceFirstLearn += daysSinceLastReview
    data += CurvePoint(now, daysSinceFirstLearn, forgettingCurve(lastStability, daysSinceLastReview, decay) * 100)

    val previewDays = maxDays - daysSinceLastReview
    var previewElapsed = 0.0
    while (previewElapsed < previewDays) {
        previewElapsed += step
        data +=
            CurvePoint(
                now + previewElapsed * DAY,
                data.last().daysSinceFirstLearn + step,
                forgettingCurve(lastStability, elapsed + previewElapsed, decay) * 100,
            )
    }
    return data.filter { it.daysSinceFirstLearn <= maxDays }
}

/** The curve: past solid, projection dashed, the desired retention as a dotted line across. */
@Composable
fun ForgettingCurveView(
    points: List<CurvePoint>,
    desiredRetention: Double,
    now: Double,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodySmall.copy(color = Color.Black)
    val showTime = points.last().time - points.first().time < 2 * DAY
    val dateFormat =
        (if (showTime) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) else DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val left = 40.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val t0 = points.first().time
        val t1 = points.last().time
        // never the whole height at 100%, so a flat curve still has a scale
        val yMin = max(0.0, 100 - 1.2 * (100 - points.minOf { it.retrievability })).coerceAtMost(90.0)

        fun xAt(time: Double) = left + ((time - t0) / (t1 - t0) * (right - left)).toFloat()

        fun yAt(r: Double) = bottom - ((r - yMin) / (100 - yMin) * (bottom - top)).toFloat()

        for (tick in ticks(yMin, 100.0, 5.0)) {
            val y = yAt(tick)
            val layout = measurer.measure("${tick.toInt()}%", style)
            drawText(layout, topLeft = Offset(left - 4.dp.toPx() - layout.size.width, y - layout.size.height / 2f))
        }
        val desiredY = desiredRetention * 100
        if (desiredY > yMin) {
            drawLine(
                Color.Black,
                Offset(left, yAt(desiredY)),
                Offset(right, yAt(desiredY)),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)),
            )
        }

        fun path(part: List<CurvePoint>) =
            Path().apply {
                part.forEachIndexed { i, p ->
                    if (i ==
                        0
                    ) {
                        moveTo(xAt(p.time), yAt(p.retrievability))
                    } else {
                        lineTo(xAt(p.time), yAt(p.retrievability))
                    }
                }
            }
        drawPath(path(points.filter { it.time <= now }), Color.Black, style = Stroke(2.dp.toPx()))
        drawPath(
            path(points.filter { it.time >= now }),
            Color.Black,
            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )

        drawLine(Color.Black, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        listOf(t0, t1).forEachIndexed { i, time ->
            val layout = measurer.measure(dateFormat.format(Instant.ofEpochMilli((time * 1000).toLong())), style)
            val x = if (i == 0) left else right - layout.size.width
            drawText(layout, topLeft = Offset(x, bottom + 4.dp.toPx()))
        }
    }
}

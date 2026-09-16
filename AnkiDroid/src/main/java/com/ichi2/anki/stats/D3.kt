// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/*
 * The few d3 functions the backend's graphs page uses to bucket its data (d3-array `ticks`,
 * `tickIncrement`, `bin`, `quantile`; d3-scale `linear.nice`). Ported so the native statistics
 * page draws the same bars from the same numbers.
 */

private val E10 = sqrt(50.0)
private val E5 = sqrt(10.0)
private val E2 = sqrt(2.0)

/** d3-array's `tickSpec`: the first and last tick index and the increment between ticks. */
private fun tickSpec(
    start: Double,
    stop: Double,
    count: Double,
): Triple<Long, Long, Double> {
    val step = (stop - start) / max(0.0, count)
    val power = floor(log10(step))
    val error = step / 10.0.pow(power)
    val factor =
        when {
            error >= E10 -> 10.0
            error >= E5 -> 5.0
            error >= E2 -> 2.0
            else -> 1.0
        }
    var i1: Long
    var i2: Long
    var inc: Double
    if (power < 0) {
        inc = 10.0.pow(-power) / factor
        i1 = (start * inc).roundToLong()
        i2 = (stop * inc).roundToLong()
        if (i1 / inc < start) ++i1
        if (i2 / inc > stop) --i2
        inc = -inc
    } else {
        inc = 10.0.pow(power) * factor
        i1 = (start / inc).roundToLong()
        i2 = (stop / inc).roundToLong()
        if (i1 * inc < start) ++i1
        if (i2 * inc > stop) --i2
    }
    if (i2 < i1 && count >= 0.5 && count < 2) return tickSpec(start, stop, count * 2)
    return Triple(i1, i2, inc)
}

/** d3's `ticks(start, stop, count)`: about [count] round values from [start] to [stop]. */
fun ticks(
    start: Double,
    stop: Double,
    count: Double,
): List<Double> {
    if (count <= 0 || start.isNaN() || stop.isNaN()) return emptyList()
    if (start == stop) return listOf(start)
    val reverse = stop < start
    val (i1, i2, inc) = if (reverse) tickSpec(stop, start, count) else tickSpec(start, stop, count)
    if (i2 < i1) return emptyList()
    val n = (i2 - i1 + 1).toInt()
    val values = List(n) { i -> if (inc < 0) (i1 + i) / -inc else (i1 + i) * inc }
    return if (reverse) values.reversed() else values
}

/** d3's `tickIncrement`: positive for a step of at least 1, otherwise minus its reciprocal. */
fun tickIncrement(
    start: Double,
    stop: Double,
    count: Double,
): Double = tickSpec(start, stop, count).third

/** d3-scale's `linear.nice(count)`: widens the domain to round values. */
fun nice(
    domain: Pair<Double, Double>,
    count: Double = 10.0,
): Pair<Double, Double> {
    var (start, stop) = domain
    val reversed = stop < start
    if (reversed) start = stop.also { stop = start }
    var previousStep: Double? = null
    repeat(10) {
        val step = tickIncrement(start, stop, count)
        when {
            step == previousStep -> return if (reversed) stop to start else start to stop
            step > 0 -> {
                start = floor(start / step) * step
                stop = ceil(stop / step) * step
            }
            step < 0 -> {
                start = ceil(start * step) / step
                stop = floor(stop * step) / step
            }
            else -> return if (reversed) stop to start else start to stop
        }
        previousStep = step
    }
    return if (reversed) stop to start else start to stop
}

/** One d3 bin: the values in `[x0, x1)`; the last bin also holds values equal to its `x1`. */
data class Bin<T>(
    val x0: Double,
    val x1: Double,
    val items: List<T>,
)

/**
 * d3-array's `bin()` with an explicit domain and thresholds. Values outside [domain] are dropped;
 * thresholds outside it are ignored.
 */
fun <T> bin(
    data: Iterable<T>,
    domain: Pair<Double, Double>,
    thresholds: List<Double>,
    value: (T) -> Double,
): List<Bin<T>> {
    val (x0, x1) = domain
    var a = 0
    var b = thresholds.size
    while (a < b && thresholds[a] <= x0) ++a
    while (b > a && thresholds[b - 1] > x1) --b
    val tz = thresholds.subList(a, b)
    val m = tz.size
    val buckets = List(m + 1) { mutableListOf<T>() }
    for (item in data) {
        val x = value(item)
        if (x0 <= x && x <= x1) buckets[bisectRight(tz, x)].add(item)
    }
    return List(m + 1) { i ->
        Bin(
            x0 = if (i > 0) tz[i - 1] else x0,
            x1 = if (i < m) tz[i] else x1,
            items = buckets[i],
        )
    }
}

private fun bisectRight(
    sorted: List<Double>,
    x: Double,
): Int {
    var lo = 0
    var hi = sorted.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (x < sorted[mid]) hi = mid else lo = mid + 1
    }
    return lo
}

/** d3-array's `quantile` over values already sorted ascending. */
fun quantileSorted(
    sorted: List<Int>,
    p: Double,
): Double? {
    if (sorted.isEmpty()) return null
    if (p <= 0 || sorted.size < 2) return sorted.first().toDouble()
    if (p >= 1) return sorted.last().toDouble()
    val i = (sorted.size - 1) * p
    val i0 = floor(i).toInt()
    val value0 = sorted[i0].toDouble()
    val value1 = sorted[i0 + 1].toDouble()
    return value0 + (value1 - value0) * (i - i0)
}

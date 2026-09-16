// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import net.ankiweb.rsdroid.Translations
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Numbers, durations and day ranges as the backend's graphs page writes them (`@tslib/i18n` and
 * `@tslib/time`, Anki 26.05).
 *
 * @param preciseSpan the backend's own formatter for a duration in seconds with two decimals, used
 * wherever the page shows one in the largest natural unit. The generated translations only take
 * whole numbers, so capped or short durations are rounded.
 */
class StatsFormat(
    val tr: Translations,
    private val locale: Locale,
    private val preciseSpan: (seconds: Double) -> String,
) {
    /** `localizedNumber(n, precision)`: rounded to [precision] decimals, grouped for the locale. */
    fun number(
        n: Double,
        precision: Int = 2,
    ): String {
        val factor = 10.0.pow(precision)
        val rounded = (n * factor).roundToInt() / factor
        return NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }.format(rounded)
    }

    fun number(n: Int): String = number(n.toDouble())

    /** "90.5%", with one decimal, as `Intl.NumberFormat({style: "percent"})` */
    fun percentOneDecimal(fraction: Double): String =
        NumberFormat
            .getPercentInstance(locale)
            .apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }.format(fraction)

    /** `timeSpan(seconds, short, precise, maxUnit)` */
    fun timeSpan(
        seconds: Double,
        short: Boolean = false,
        precise: Boolean = true,
        maxUnit: TimeUnit = TimeUnit.Years,
    ): String {
        val natural = naturalUnit(seconds)
        val unit = minOf(natural, maxUnit)
        if (!short && precise && unit == natural) return preciseSpan(seconds)
        val amount = (seconds / unit.seconds).roundToInt()
        return if (short) {
            when (unit) {
                TimeUnit.Seconds -> tr.statisticsElapsedTimeSeconds(amount)
                TimeUnit.Minutes -> tr.statisticsElapsedTimeMinutes(amount)
                TimeUnit.Hours -> tr.statisticsElapsedTimeHours(amount)
                TimeUnit.Days -> tr.statisticsElapsedTimeDays(amount)
                TimeUnit.Months -> tr.statisticsElapsedTimeMonths(amount)
                TimeUnit.Years -> tr.statisticsElapsedTimeYears(amount)
            }
        } else {
            when (unit) {
                TimeUnit.Seconds -> tr.schedulingTimeSpanSeconds(amount)
                TimeUnit.Minutes -> tr.schedulingTimeSpanMinutes(amount)
                TimeUnit.Hours -> tr.schedulingTimeSpanHours(amount)
                TimeUnit.Days -> tr.schedulingTimeSpanDays(amount)
                TimeUnit.Months -> tr.schedulingTimeSpanMonths(amount)
                TimeUnit.Years -> tr.schedulingTimeSpanYears(amount)
            }
        }
    }

    /** `dayLabel(daysStart, daysEnd)`: "In 3 days", "5–7 days ago" … */
    fun dayLabel(
        daysStart: Int,
        daysEnd: Int,
    ): String {
        val larger = max(abs(daysStart), abs(daysEnd))
        val smaller = min(abs(daysStart), abs(daysEnd))
        return if (larger - smaller <= 1) {
            if (daysStart >= 0) tr.statisticsInDaysSingle(daysStart) else tr.statisticsDaysAgoSingle(-daysStart)
        } else if (daysStart >= 0) {
            tr.statisticsInDaysRange(daysStart, daysEnd - 1)
        } else {
            tr.statisticsDaysAgoRange(abs(daysEnd - 1), -daysStart)
        }
    }

    enum class TimeUnit(
        val seconds: Double,
    ) {
        Seconds(1.0),
        Minutes(60.0),
        Hours(3_600.0),
        Days(86_400.0),
        Months(86_400.0 * 365 / 12),
        Years(86_400.0 * 365),
    }

    companion object {
        fun naturalUnit(seconds: Double): TimeUnit {
            val secs = abs(seconds)
            return when {
                secs < TimeUnit.Minutes.seconds -> TimeUnit.Seconds
                secs < TimeUnit.Hours.seconds -> TimeUnit.Minutes
                secs < TimeUnit.Days.seconds -> TimeUnit.Hours
                secs < TimeUnit.Months.seconds -> TimeUnit.Days
                secs < TimeUnit.Years.seconds -> TimeUnit.Months
                else -> TimeUnit.Years
            }
        }
    }
}

/** 130.0 → "130", 132.5 → "132.5": a number as JavaScript prints it in a template string. */
internal fun jsNumber(value: Double): String = if (value % 1.0 == 0.0 && abs(value) < 1e15) value.toLong().toString() else value.toString()

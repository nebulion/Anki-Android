// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckoptions

import anki.deck_config.DeckConfigsForUpdate.CurrentDeck.Limits
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Values the deck options page edits as text or across scopes, ported from the backend page
 * (`steps.ts`, `TabbedValue.svelte` and `DailyLimits.svelte`, Anki 26.05).
 */

// ---------------------------------------------------------------------------------------------
// Learning steps: minutes as "1m 10m 1d"

private const val MINUTE = 60.0
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val MONTH = DAY * 365 / 12
private const val YEAR = DAY * 365

/** `stepsToString`: [1f, 10f, 1440f] → "1m 10m 1d". */
fun stepsToString(steps: List<Float>): String = steps.joinToString(" ") { minutesToString(it.toDouble()) }

/** `stringToSteps`: "1m 10m 1d" → [1f, 10f, 1440f]. Unreadable and zero steps are dropped. */
fun stringToSteps(text: String): List<Float> = text.split(" ").map(::stringToMinutes).filter { it != 0f }

private fun minutesToString(step: Double): String {
    val secs = step * 60
    // the largest unit that divides the step exactly, never months or years
    val (unitSeconds, suffix) =
        listOf(DAY to "d", HOUR to "h", MINUTE to "m", 1.0 to "s")
            .firstOrNull { (unit, _) -> unit <= naturalUnitSeconds(secs) && abs(secs - (secs / unit).roundToLong() * unit) < 1e-9 }
            ?: (1.0 to "s")
    return "${(secs / unitSeconds).roundToLong()}$suffix"
}

private fun naturalUnitSeconds(secs: Double): Double =
    when {
        abs(secs) < MINUTE -> 1.0
        abs(secs) < HOUR -> MINUTE
        abs(secs) < DAY -> HOUR
        abs(secs) < MONTH -> DAY
        abs(secs) < YEAR -> DAY
        else -> DAY
    }

private fun stringToMinutes(text: String): Float {
    val match = Regex("""(\d+)(.*)""").find(text) ?: return 0f
    val (number, suffix) = match.destructured
    val unit =
        when (suffix) {
            "s" -> 1.0
            "h" -> HOUR
            "d" -> DAY
            else -> MINUTE
        }
    val seconds = unit * (number.toLongOrNull() ?: return 0f)
    // representable as negative i32 seconds in a revlog
    return (min(seconds, 2.0e0 * (1L shl 30)) / 60).toFloat()
}

/** Whether [text] is a list of steps the page would accept: numbers each with an optional unit. */
fun isValidSteps(text: String): Boolean = text.isBlank() || text.trim().split(Regex("\\s+")).all { Regex("""\d+[smhd]?""").matches(it) }

// ---------------------------------------------------------------------------------------------
// Daily limits: the preset's, this deck's, or today only

/** Which of the page's tabs a daily limit is set on; a later tab overrides the ones before. */
enum class LimitScope { Preset, Deck, Today }

enum class LimitKind { New, Review }

/** The tab the page opens on: the last one that has a value. */
fun Limits.scope(kind: LimitKind): LimitScope =
    when (kind) {
        LimitKind.New ->
            when {
                newTodayActive && hasNewToday() -> LimitScope.Today
                hasNew() -> LimitScope.Deck
                else -> LimitScope.Preset
            }
        LimitKind.Review ->
            when {
                reviewTodayActive && hasReviewToday() -> LimitScope.Today
                hasReview() -> LimitScope.Deck
                else -> LimitScope.Preset
            }
    }

/** The value shown for [kind]: the one on its current tab. */
fun DeckOptionsState.limitValue(kind: LimitKind): Int =
    when (kind) {
        LimitKind.New ->
            when (limits.scope(kind)) {
                LimitScope.Today -> limits.newToday
                LimitScope.Deck -> limits.new
                LimitScope.Preset -> current.newPerDay
            }
        LimitKind.Review ->
            when (limits.scope(kind)) {
                LimitScope.Today -> limits.reviewToday
                LimitScope.Deck -> limits.review
                LimitScope.Preset -> current.reviewsPerDay
            }
    }

/**
 * Sets [kind]'s limit to [value] on [scope], as choosing a tab does: the tabs after it are cleared,
 * so they no longer override it; the ones before keep their values.
 */
fun DeckOptionsState.setLimit(
    kind: LimitKind,
    scope: LimitScope,
    value: Int,
) {
    when (kind) {
        LimitKind.New -> {
            if (scope == LimitScope.Preset) updateConfig { newPerDay = value }
            updateLimits {
                when (scope) {
                    LimitScope.Preset -> clearNew().clearNewToday()
                    LimitScope.Deck -> setNew(value).clearNewToday()
                    LimitScope.Today -> setNewToday(value).setNewTodayActive(true)
                }
            }
        }
        LimitKind.Review -> {
            if (scope == LimitScope.Preset) updateConfig { reviewsPerDay = value }
            updateLimits {
                when (scope) {
                    LimitScope.Preset -> clearReview().clearReviewToday()
                    LimitScope.Deck -> setReview(value).clearReviewToday()
                    LimitScope.Today -> setReviewToday(value).setReviewTodayActive(true)
                }
            }
        }
    }
}

/** The desired retention in use: this deck's own if it has one, else the preset's. */
val DeckOptionsState.effectiveDesiredRetention: Float
    get() = if (limits.hasDesiredRetention()) limits.desiredRetention else current.desiredRetention

/** Sets the desired retention for the preset, or for this deck only. */
fun DeckOptionsState.setDesiredRetention(
    deckOnly: Boolean,
    value: Float,
) {
    if (deckOnly) {
        updateLimits { desiredRetention = value }
    } else {
        updateConfig { desiredRetention = value }
        updateLimits { clearDesiredRetention() }
    }
}

// ---------------------------------------------------------------------------------------------
// FSRS parameters as text

/** "0.4, 1.2, …" */
fun paramsToString(params: List<Float>): String = params.joinToString(", ")

/** Null if [text] is not a comma-separated list of numbers; an empty text is an empty list. */
fun stringToParams(text: String): List<Float>? {
    if (text.isBlank()) return emptyList()
    return text.split(",").map { it.trim().toFloatOrNull() ?: return null }
}

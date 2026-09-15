// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

/**
 * A card count short enough for the deck list's fixed-width count columns: up to 999 as is, then
 * `1.2k`, `12k`, `123k`, `1.2m`, `12m`.
 *
 * Rounds down, so a count is never shown as more than it is (`1,999` is `1.9k`, not `2k`).
 */
fun compactCount(count: Int): String =
    when {
        count < 1_000 -> count.toString()
        count < 10_000 -> "${oneDecimal(count, 1_000)}k"
        count < 1_000_000 -> "${count / 1_000}k"
        count < 10_000_000 -> "${oneDecimal(count, 1_000_000)}m"
        else -> "${count / 1_000_000}m"
    }

/** [count] in [unit]s with one decimal rounded down, dropping a trailing `.0`. */
private fun oneDecimal(
    count: Int,
    unit: Int,
): String {
    val tenths = count / (unit / 10)
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.annotation.StringRes
import com.ichi2.anki.R

/**
 * A sync in progress, shown on the home screen in place of the deck list rather than in a dialog.
 *
 * @see com.ichi2.anki.withSyncProgress
 */
data class SyncProgress(
    /** What the backend last reported, e.g. `28 MB/141 MB`; `null` before its first report. */
    val detail: String? = null,
    /** A normal sync's changes so far, shown as a table instead of [detail]. */
    val changes: SyncChanges? = null,
    /** How much is done, `0..1`, when the size of the work is known; `null` shows a moving bar. */
    val fraction: Float? = null,
    @StringRes val cancelLabel: Int = R.string.dialog_cancel,
    /** Stops the sync; `null` when this step cannot be cancelled. */
    val cancel: (() -> Unit)? = null,
)

/** How many changes a normal sync has sent to AnkiWeb and received from it. */
data class SyncChanges(
    val sentChanged: Int,
    val receivedChanged: Int,
    val sentDeleted: Int,
    val receivedDeleted: Int,
) {
    companion object {
        /**
         * Reads the backend's translated lines, e.g. `Added/modified: 3↑ 12↓` and `Removed: 0↑ 1↓`:
         * the first number went up to AnkiWeb, the second came down. Null if either line has no
         * two numbers, e.g. a translation that writes them differently.
         */
        fun parse(
            added: String,
            removed: String,
        ): SyncChanges? {
            fun upDown(line: String) =
                Regex("\\d+")
                    .findAll(line)
                    .map { it.value.toInt() }
                    .toList()
                    .takeIf { it.size == 2 }
            val (sentChanged, receivedChanged) = upDown(added) ?: return null
            val (sentDeleted, receivedDeleted) = upDown(removed) ?: return null
            return SyncChanges(sentChanged, receivedChanged, sentDeleted, receivedDeleted)
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

/**
 * A deck deleted on its deck page, waiting for the home screen to report it with Undo.
 *
 * The deck page closes as soon as its deck is gone, so it cannot show the message itself; the home
 * screen takes this when it next resumes. Kept in the process rather than in an activity result
 * because on the Kompakt ("Don't keep activities") the home screen is rebuilt on return.
 */
object PendingDeckDeletion {
    private var pending: DeckDeletionResult? = null

    fun post(result: DeckDeletionResult) {
        pending = result
    }

    /** The waiting deletion, once: a second call returns null. */
    fun take(): DeckDeletionResult? = pending.also { pending = null }
}

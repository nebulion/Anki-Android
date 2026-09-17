// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId

/**
 * Opens the card editor: to edit the note of [cardId], or, without one, to add notes, to [deckId]
 * if given.
 */
data class NoteEditorDestination(
    val cardId: CardId? = null,
    val deckId: DeckId? = null,
) : Destination()

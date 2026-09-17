// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.noteeditor.NoteEditorViewModel.SaveResult
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The card editor adds and edits notes as the deleted note editor did. */
@RunWith(AndroidJUnit4::class)
class NoteEditorViewModelTest : RobolectricTest() {
    private suspend fun editor(
        cardId: Long? = null,
        deckId: Long? = null,
    ): NoteEditorViewModel {
        val args =
            buildMap {
                cardId?.let { put(NoteEditorViewModel.ARG_CARD_ID, it) }
                deckId?.let { put(NoteEditorViewModel.ARG_DECK_ID, it) }
            }
        return NoteEditorViewModel(SavedStateHandle(args)).also { it.state.first { state -> state != null } }
    }

    @Test
    fun `a note is added to the chosen deck and the fields are cleared for the next`() =
        runTest {
            val deckId = addDeck("Target")
            val editor = editor(deckId = deckId)
            assertEquals(deckId, editor.state.value!!.deckId, "the deck the page was opened from")

            editor.setField(0, "front")
            editor.setField(1, "line one\nline two")
            editor.setTags("verbs  italian")
            val result = editor.save()

            assertEquals(SaveResult.Added(1), result)
            val note = withCol { getNote(findNotes("").single()) }
            assertEquals(listOf("front", "line one<br>line two"), note.fields)
            assertEquals(listOf("italian", "verbs"), note.tags.sorted())
            assertEquals(deckId, withCol { note.cards(this).single().did })
            assertEquals(listOf("", ""), editor.state.value!!.fields)
            assertFalse(editor.hasUnsavedChanges())
        }

    @Test
    fun `an empty first field is refused`() =
        runTest {
            val editor = editor()
            editor.setField(1, "only the back")

            val result = editor.save()

            assertEquals(SaveResult.Refused(col.tr.addingTheFirstFieldIsEmpty()), result)
            assertEquals(0, col.noteCount())
            assertTrue(editor.hasUnsavedChanges(), "typed text is kept after a refusal")
        }

    @Test
    fun `editing a note saves its fields and closes`() =
        runTest {
            val card = addBasicNote("before", "back<br>two").firstCard()
            val editor = editor(cardId = card.id)
            assertFalse(editor.state.value!!.isAdding)
            assertEquals(listOf("before", "back\ntwo"), editor.state.value!!.fields, "line breaks show as new lines")
            assertFalse(editor.hasUnsavedChanges())

            editor.setField(0, "after")
            assertTrue(editor.hasUnsavedChanges())

            assertIs<SaveResult.Updated>(editor.save())
            assertEquals(listOf("after", "back<br>two"), withCol { getNote(card.nid).fields })
        }

    @Test
    fun `saving an unchanged note changes nothing`() =
        runTest {
            val card = addBasicNote("front", "back").firstCard()
            val editor = editor(cardId = card.id)

            assertIs<SaveResult.Unchanged>(editor.save())
        }

    @Test
    fun `line breaks convert both ways`() {
        assertEquals("a<br>b", NoteEditorViewModel.toStorage("a\r\nb"))
        assertEquals("a\nb\nc\nd", NoteEditorViewModel.toDisplay("a<br>b<br/>c<BR />d"))
    }
}

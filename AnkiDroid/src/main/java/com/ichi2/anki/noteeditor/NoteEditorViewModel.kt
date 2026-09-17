// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.notes.NoteFieldsCheckResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Adds a note, or edits the note of a card: its note type and deck, one text per field, and its
 * tags. Formatting is kept as the field's HTML; only line breaks are shown as new lines, as the old
 * editor did with "replace newlines" on.
 *
 * The rules follow the editor this fork deleted in Phase 1 (`NoteEditorFragment`,
 * `NoteFieldsCheckResult`): the fields are checked before adding (an empty first field or a cloze
 * mistake is refused, a duplicate is allowed and marked), and a note is added to the deck and with
 * the note type the backend suggests unless one is chosen.
 */
class NoteEditorViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /** The card whose note is edited, or null to add notes. */
    private val cardId: CardId? = savedStateHandle.get<Long>(ARG_CARD_ID)?.takeIf { it != 0L }

    /** The deck to add to, if the page was opened from one. */
    private val requestedDeckId: DeckId? = savedStateHandle.get<Long>(ARG_DECK_ID)?.takeIf { it != 0L }

    data class NameId(
        val name: String,
        val id: Long,
    )

    data class State(
        val isAdding: Boolean,
        val noteTypes: List<NameId>,
        val noteTypeId: NoteTypeId,
        val decks: List<NameId>,
        val deckId: DeckId,
        val fieldNames: List<String>,
        /** The fields as shown: HTML line breaks are new lines. */
        val fields: List<String>,
        val tags: String,
        /** Whether the first field matches another note of this type. */
        val isDuplicate: Boolean = false,
    ) {
        val noteTypeName: String get() = noteTypes.firstOrNull { it.id == noteTypeId }?.name.orEmpty()
        val deckName: String get() = decks.firstOrNull { it.id == deckId }?.name.orEmpty()
    }

    sealed interface SaveResult {
        /** A note was added with [cardCount] cards; the fields are cleared for the next note. */
        data class Added(
            val cardCount: Int,
        ) : SaveResult

        data object Updated : SaveResult

        data object Unchanged : SaveResult

        /** The note can't be saved, e.g. "The first field is empty"; null for a check the app doesn't know. */
        data class Refused(
            val message: String?,
        ) : SaveResult
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** What was loaded or last saved, to tell whether leaving would lose anything. */
    private var baseline: State? = null
    private var duplicateCheck: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.value =
            withCol {
                val noteTypes = notetypes.allNamesAndIds().map { NameId(it.name, it.id) }.toList()
                val decks = decks.allNamesAndIds(includeFiltered = false).map { NameId(it.name, it.id) }
                if (cardId != null) {
                    val card = getCard(cardId)
                    val note = card.note(this)
                    State(
                        isAdding = false,
                        noteTypes = noteTypes,
                        noteTypeId = note.noteTypeId,
                        decks = decks,
                        deckId = card.currentDeckId(),
                        fieldNames = note.notetype.fieldsNames,
                        fields = note.fields.map(::toDisplay),
                        tags = note.stringTags(this).trim(),
                    )
                } else {
                    val defaults = defaultsForAdding()
                    val deckId = requestedDeckId?.takeIf { id -> decks.any { it.id == id } } ?: defaults.deckId
                    val noteTypeId = defaults.notetypeId
                    State(
                        isAdding = true,
                        noteTypes = noteTypes,
                        noteTypeId = noteTypeId,
                        decks = decks,
                        deckId = deckId,
                        fieldNames = notetypes.get(noteTypeId)!!.fieldsNames,
                        fields = notetypes.get(noteTypeId)!!.fieldsNames.map { "" },
                        tags = "",
                    )
                }
            }
        baseline = _state.value
    }

    fun setField(
        index: Int,
        text: String,
    ) {
        _state.update { it?.copy(fields = it.fields.toMutableList().also { fields -> fields[index] = text }) }
        if (index == 0) checkDuplicate()
    }

    fun setTags(text: String) = _state.update { it?.copy(tags = text) }

    fun setDeck(deckId: DeckId) = _state.update { it?.copy(deckId = deckId) }

    /** Adding only: the fields keep their text by position, as the old editor kept them. */
    fun setNoteType(noteTypeId: NoteTypeId) {
        val current = _state.value ?: return
        if (!current.isAdding || current.noteTypeId == noteTypeId) return
        viewModelScope.launch {
            val (names, deckId) =
                withCol {
                    notetypes.get(noteTypeId)!!.fieldsNames to (defaultDeckForNoteType(noteTypeId) ?: current.deckId)
                }
            _state.update {
                it?.copy(
                    noteTypeId = noteTypeId,
                    fieldNames = names,
                    fields = names.indices.map { i -> current.fields.getOrElse(i) { "" } },
                    deckId = if (current.decks.any { d -> d.id == deckId }) deckId else current.deckId,
                )
            }
            checkDuplicate()
        }
    }

    /** Whether leaving now would lose typed text or a changed choice. */
    fun hasUnsavedChanges(): Boolean {
        val current = _state.value ?: return false
        val base = baseline ?: return false
        return if (current.isAdding) {
            current.fields.any { it.isNotEmpty() }
        } else {
            current.copy(isDuplicate = false) != base.copy(isDuplicate = false)
        }
    }

    suspend fun save(): SaveResult {
        val current = _state.value ?: return SaveResult.Unchanged
        return if (current.isAdding) add(current) else update(current)
    }

    private suspend fun add(current: State): SaveResult {
        val note = withCol { buildNote(current) }
        val check = withCol { note.fieldsCheck(this) }
        if (check != NoteFieldsCheckResponse.State.NORMAL && check != NoteFieldsCheckResponse.State.DUPLICATE) {
            return SaveResult.Refused(refusal(check))
        }
        val changes = undoableOp { addNote(note, current.deckId) }
        Timber.i("NoteEditor: added note %d with %d cards", note.id, changes.count)
        // ready for the next note: same type, deck and tags
        _state.update { it?.copy(fields = it.fields.map { "" }, isDuplicate = false) }
        baseline = _state.value
        return SaveResult.Added(changes.count)
    }

    private suspend fun update(current: State): SaveResult {
        val id = cardId ?: return SaveResult.Unchanged
        if (!hasUnsavedChanges()) return SaveResult.Unchanged
        val base = baseline
        if (base != null && base.deckId != current.deckId) {
            undoableOp { setDeck(listOf(id), current.deckId) }
        }
        undoableOp {
            val note = getCard(id).note(this)
            current.fields.forEachIndexed { i, text -> note.fields[i] = toStorage(text) }
            note.setTagsFromStr(this, current.tags)
            updateNote(note)
        }
        baseline = current
        return SaveResult.Updated
    }

    private fun Collection.buildNote(current: State): Note =
        newNote(notetypes.get(current.noteTypeId)!!).apply {
            current.fields.forEachIndexed { i, text -> fields[i] = toStorage(text) }
            setTagsFromStr(this@buildNote, current.tags)
        }

    private fun checkDuplicate() {
        duplicateCheck?.cancel()
        val current = _state.value ?: return
        if (!current.isAdding) return
        duplicateCheck =
            viewModelScope.launch {
                val state = withCol { buildNote(current).fieldsCheck(this) }
                _state.update { it?.copy(isDuplicate = state == NoteFieldsCheckResponse.State.DUPLICATE) }
            }
    }

    companion object {
        const val ARG_CARD_ID = "cardId"
        const val ARG_DECK_ID = "deckId"

        /** Shown text → field HTML: new lines become `<br>`. */
        fun toStorage(text: String): String = text.replace("\r\n", "\n").replace("\n", "<br>")

        /** Field HTML → shown text: `<br>`, `<br/>` and `<br />` become new lines. */
        fun toDisplay(html: String): String = html.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")

        /**
         * `checkNoteFieldsResponse`: why a check refuses a note. Null for one that doesn't (normal or
         * duplicate), and for a check this app doesn't know.
         */
        fun refusal(state: NoteFieldsCheckResponse.State): String? =
            when (state) {
                NoteFieldsCheckResponse.State.NORMAL, NoteFieldsCheckResponse.State.DUPLICATE -> null
                NoteFieldsCheckResponse.State.EMPTY -> TR.addingTheFirstFieldIsEmpty()
                NoteFieldsCheckResponse.State.MISSING_CLOZE -> TR.addingYouHaveAClozeDeletionNote()
                NoteFieldsCheckResponse.State.NOTETYPE_NOT_CLOZE -> TR.addingClozeOutsideClozeNotetype()
                NoteFieldsCheckResponse.State.FIELD_NOT_CLOZE -> TR.addingClozeOutsideClozeField()
                NoteFieldsCheckResponse.State.UNRECOGNIZED -> null
            }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelBody
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.ValueRow
import com.ichi2.compose.mmd.panelTextFieldColors
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * The card editor: adds notes, or edits the note of the card being studied. A form page (the
 * note type, the deck, a text box per field and the tags) with Save in the header.
 *
 * After adding, the page stays open with the fields cleared for the next note, as Anki's own Add
 * window does; after editing, it closes. Leaving with typed text asks first.
 *
 * Not here: images, audio, drawing, image occlusion and the formatting toolbar. A field's
 * formatting shows as its HTML.
 */
class NoteEditorFragment : ComposeHostFragment() {
    private val viewModel: NoteEditorViewModel by viewModels()
    private val messages = MessageHostState()

    private var isConfirmingDiscard by mutableStateOf(false)

    /** Why the note couldn't be added, shown in a panel. */
    private var refusal: String? by mutableStateOf(null)

    private val onBack =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.hasUnsavedChanges()) isConfirmingDiscard = true else close()
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBack)
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        var chooser by rememberSaveable { mutableStateOf<Chooser?>(null) }

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title =
                    stringResource(if (state?.isAdding != false) R.string.mmd_editor_add_note else R.string.mmd_editor_edit_note),
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                },
                actions = {
                    if (state != null) {
                        HeaderAction(icon = R.drawable.ic_done, contentDescription = TR.actionsSave(), onClick = ::save)
                    }
                },
            )
            val current = state
            if (current == null) {
                TextMMD(
                    text = stringResource(R.string.dialog_processing),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
                )
                return@Column
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            ) {
                if (current.isAdding) {
                    ValueRow(title = TR.notetypesNotetype(), value = current.noteTypeName, onClick = { chooser = Chooser.NoteType })
                    RowDivider()
                }
                ValueRow(title = TR.decksDeck(), value = current.deckName, onClick = { chooser = Chooser.Deck })
                current.fieldNames.forEachIndexed { index, name ->
                    TextFieldMMD(
                        value = current.fields.getOrElse(index) { "" },
                        onValueChange = { viewModel.setField(index, it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
                        label = { TextMMD(text = name) },
                        singleLine = false,
                        minLines = 2,
                        supportingText =
                            if (index == 0 && current.isDuplicate) {
                                { TextMMD(text = stringResource(R.string.mmd_editor_duplicate)) }
                            } else {
                                null
                            },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = panelTextFieldColors(),
                    )
                }
                TextFieldMMD(
                    value = current.tags,
                    onValueChange = viewModel::setTags,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
                    label = { TextMMD(text = TR.editingTags()) },
                    singleLine = true,
                    supportingText = { TextMMD(text = stringResource(R.string.mmd_editor_tags_hint)) },
                    colors = panelTextFieldColors(),
                )
            }
            MessageHost(messages)
        }

        when (chooser) {
            Chooser.NoteType ->
                state?.let { current ->
                    ChoiceSheet(
                        title = TR.notetypesNotetype(),
                        options = current.noteTypes,
                        selected = current.noteTypes.firstOrNull { it.id == current.noteTypeId },
                        label = { it.name },
                        onSelect = { viewModel.setNoteType(it.id) },
                        onDismissRequest = { chooser = null },
                    )
                }
            Chooser.Deck ->
                state?.let { current ->
                    ChoiceSheet(
                        title = TR.decksDeck(),
                        options = current.decks,
                        selected = current.decks.firstOrNull { it.id == current.deckId },
                        label = { it.name },
                        onSelect = { viewModel.setDeck(it.id) },
                        onDismissRequest = { chooser = null },
                    )
                }
            null -> Unit
        }
        if (isConfirmingDiscard) {
            ConfirmPanel(
                title = TR.addingDiscardCurrentInput(),
                body = null,
                confirmLabel = stringResource(R.string.discard),
                dismissLabel = with(requireContext()) { TR.sentenceCase.keepEditing },
                onConfirm = {
                    isConfirmingDiscard = false
                    close()
                },
                onDismiss = { isConfirmingDiscard = false },
            )
        }
        refusal?.let { message ->
            PanelDialog(onDismissRequest = { refusal = null }) {
                PanelBody(message)
                PanelActions { PanelPrimaryAction(label = stringResource(R.string.dialog_ok), onClick = { refusal = null }) }
            }
        }
    }

    private enum class Chooser { NoteType, Deck }

    private fun save() =
        launchCatchingTask {
            when (val result = viewModel.save()) {
                is NoteEditorViewModel.SaveResult.Added ->
                    // the backend's text ends with a full stop
                    messages.show(TR.importingCardsAdded(result.cardCount).removeSuffix("."))
                NoteEditorViewModel.SaveResult.Updated, NoteEditorViewModel.SaveResult.Unchanged -> close()
                is NoteEditorViewModel.SaveResult.Refused -> refusal = result.message ?: getString(R.string.something_wrong)
            }
        }

    private fun close() {
        onBack.isEnabled = false
        requireActivity().finish()
    }
}

/** Opens the card editor: to edit the note of a card, or to add notes, to a deck if one is given. */
fun NoteEditorDestination.toIntent(context: Context): Intent =
    SingleFragmentActivity.getIntent(
        context,
        fragmentClass = NoteEditorFragment::class,
        arguments =
            Bundle().apply {
                cardId?.let { putLong(NoteEditorViewModel.ARG_CARD_ID, it) }
                deckId?.let { putLong(NoteEditorViewModel.ARG_DECK_ID, it) }
            },
    )

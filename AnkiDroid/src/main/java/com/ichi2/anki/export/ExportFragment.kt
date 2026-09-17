// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.exportApkgPackage
import com.ichi2.anki.exportCollectionPackage
import com.ichi2.anki.exportSelectedCards
import com.ichi2.anki.exportSelectedNotes
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.ValueRow
import java.io.File

/**
 * The export page (P1): what to export, which deck, and the format's switches, above Export.
 *
 * When the file is written, the host activity offers to save it with the system file picker.
 * Exporting a selection of notes or cards is gone with the card browser that made the selection.
 */
class ExportFragment : ComposeHostFragment() {
    private val initialDeckId: DeckId?
        get() = arguments?.getLong(ARG_DECK_ID, NO_DECK)?.takeIf { it != NO_DECK }

    @Composable
    override fun ScreenContent() {
        // a deck given by the caller means its deck package, as desktop Anki does
        var format by rememberSaveable { mutableStateOf(if (initialDeckId != null) ExportFormat.Apkg else ExportFormat.Collection) }
        var switchedOn by rememberSaveable(format) { mutableStateOf(format.defaults.map { it.name }) }
        var deckId by rememberSaveable { mutableLongStateOf(initialDeckId ?: ALL_DECKS_ID) }
        var isChoosingFormat by rememberSaveable { mutableStateOf(false) }
        var isChoosingDeck by rememberSaveable { mutableStateOf(false) }

        val allDecks = TR.sentenceCase.allDecks
        val decks by produceState(listOf(DeckNameId(allDecks, ALL_DECKS_ID))) {
            value = listOf(DeckNameId(allDecks, ALL_DECKS_ID)) + withCol { decks.allNamesAndIds(false) }
        }

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = TR.actionsExport(),
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                },
            )
            PagedList(Modifier.weight(1f), canGrow = true) {
                item {
                    Column {
                        ValueRow(
                            title = TR.exportingExportFormat().withoutMarkup(),
                            value = format.label(requireContext()),
                            onClick = { isChoosingFormat = true },
                        )
                        RowDivider()
                    }
                }
                if (format.hasDeckChoice) {
                    item {
                        Column {
                            ValueRow(
                                title = TR.exportingInclude().withoutMarkup(),
                                value = decks.firstOrNull { it.id == deckId }?.name ?: allDecks,
                                onClick = { isChoosingDeck = true },
                            )
                            RowDivider()
                        }
                    }
                }
                items(format.options, key = { it.name }) { option ->
                    Column {
                        SwitchRow(
                            title = option.label(),
                            checked = option.name in switchedOn,
                            onCheckedChange = { on -> switchedOn = if (on) switchedOn + option.name else switchedOn - option.name },
                        )
                        RowDivider()
                    }
                }
            }
            PanelPrimaryAction(
                label = TR.actionsExport(),
                onClick = { export(format, deckId, switchedOn.map(ExportOption::valueOf).toSet(), decks) },
                modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
            )
        }

        if (isChoosingFormat) {
            ChoiceSheet(
                title = TR.exportingExportFormat().withoutMarkup(),
                options = ExportFormat.entries,
                selected = format,
                label = { it.label(requireContext()) },
                onSelect = { format = it },
                onDismissRequest = { isChoosingFormat = false },
            )
        }
        if (isChoosingDeck) {
            ChoiceSheet(
                title = TR.exportingInclude().withoutMarkup(),
                options = decks,
                selected = decks.firstOrNull { it.id == deckId },
                label = { it.name },
                onSelect = { deckId = it.id },
                onDismissRequest = { isChoosingDeck = false },
            )
        }
    }

    private fun export(
        format: ExportFormat,
        deckId: DeckId,
        options: Set<ExportOption>,
        decks: List<DeckNameId>,
    ) {
        val activity = requireAnkiActivity()
        val prefix =
            when (format) {
                ExportFormat.Collection -> TR.exportingCollection()
                // named after the deck, as desktop Anki does
                else -> Filename.sanitize(decks.firstOrNull { it.id == deckId }?.name ?: TR.exportingAllDecks()).value
            }
        val path = File(exportRoot(), "$prefix-${getTimestamp(TimeManager.time)}.${format.extension}").path
        val limit = exportLimitFor(deckId)
        when (format) {
            ExportFormat.Collection ->
                activity.exportCollectionPackage(
                    exportPath = path,
                    withMedia = ExportOption.IncludeMedia in options,
                    legacy = ExportOption.Legacy in options,
                )
            ExportFormat.Apkg ->
                activity.exportApkgPackage(
                    exportPath = path,
                    withScheduling = ExportOption.IncludeSchedule in options,
                    withDeckConfigs = ExportOption.IncludeDeckConfigs in options,
                    withMedia = ExportOption.IncludeMedia in options,
                    limit = limit,
                    legacy = ExportOption.Legacy in options,
                )
            ExportFormat.Notes ->
                activity.exportSelectedNotes(
                    exportPath = path,
                    withHtml = ExportOption.IncludeHtml in options,
                    withTags = ExportOption.IncludeTags in options,
                    withDeck = ExportOption.IncludeDeck in options,
                    withNotetype = ExportOption.IncludeNotetype in options,
                    withGuid = ExportOption.IncludeGuid in options,
                    limit = limit,
                )
            ExportFormat.Cards ->
                activity.exportSelectedCards(
                    exportPath = path,
                    withHtml = ExportOption.IncludeHtml in options,
                    limit = limit,
                )
        }
    }

    private fun exportRoot() = File(requireActivity().externalCacheDir, "export").also { it.mkdirs() }

    /** The backend's labels carry bold tags and a trailing colon, e.g. `<b>Export format</b>:` */
    private fun String.withoutMarkup(): String =
        HtmlCompat
            .fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
            .removeSuffix(":")

    companion object {
        private const val ARG_DECK_ID = "arg_deck_id"
        private const val NO_DECK = -1L

        /** The export page; with [deckId], starting on that deck's package. */
        fun getIntent(
            context: Context,
            deckId: DeckId? = null,
        ): Intent =
            SingleFragmentActivity.getIntent(
                context = context,
                fragmentClass = ExportFragment::class,
                arguments = bundleOf(ARG_DECK_ID to (deckId ?: NO_DECK)),
            )
    }
}

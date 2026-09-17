// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import anki.import_export.CsvMetadata.DupeResolution
import anki.import_export.ImportAnkiPackageOptions
import anki.import_export.ImportAnkiPackageUpdateCondition
import anki.import_export.ImportResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.withProgress
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.GroupDivider
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.InfoRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.TextBlock
import com.ichi2.compose.mmd.TextPage
import com.ichi2.compose.mmd.ValueRow
import com.ichi2.compose.mmd.paragraphs
import com.mudita.mmd.components.text.TextMMD
import java.io.File

/**
 * Importing an `.apkg`/`.colpkg` into the collection, as a native page: the backend's import
 * options (`ts/routes/import-anki-package`, Anki 26.05) as settings rows, Import, then the result as
 * the backend's log page shows it (`ts/routes/import-page`): an overview of how many notes were
 * added, updated, skipped or failed, and each note with what happened to it.
 *
 * It was the backend's web page. The log's "Show" buttons, which open the card browser, are gone
 * with the browser.
 */
class AnkiPackageImporterFragment : ComposeHostFragment() {
    private val path: String by lazy { requireArguments().getString(KEY_FILE_PATH).orEmpty() }

    private var options: ImportAnkiPackageOptions? by mutableStateOf(null)
    private var result: ImportResponse? by mutableStateOf(null)
    private var error: String? by mutableStateOf(null)

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            if (options == null) options = withCol { backend.getImportAnkiPackagePresets() }
        }
        var isHelpShown by rememberSaveable { mutableStateOf(false) }
        var choosing by rememberSaveable { mutableStateOf<String?>(null) }

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = TR.actionsImport(),
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                },
                actions = {
                    if (result == null) {
                        HeaderAction(icon = R.drawable.ic_help_black_24dp, contentDescription = stringResource(R.string.help), onClick = {
                            isHelpShown = true
                        })
                    }
                },
            )
            val done = result
            val current = options
            when {
                error != null -> Line(error.orEmpty())
                done != null -> ResultList(done, Modifier.weight(1f))
                current == null -> Line(stringResource(R.string.dialog_processing))
                else -> {
                    PagedList(Modifier.weight(1f)) {
                        item { InfoRow(title = TR.importingFile(), value = File(path).name) }
                        item {
                            Column {
                                GroupDivider()
                                SectionTitle(TR.importingImportOptions())
                            }
                        }
                        item {
                            SwitchRow(
                                title = TR.importingAlsoImportProgress(),
                                checked = current.withScheduling,
                                onCheckedChange = { options = current.toBuilder().setWithScheduling(it).build() },
                            )
                        }
                        item {
                            SwitchRow(
                                title = TR.importingWithDeckConfigs(),
                                checked = current.withDeckConfigs,
                                onCheckedChange = { options = current.toBuilder().setWithDeckConfigs(it).build() },
                            )
                        }
                        item {
                            Column {
                                GroupDivider()
                                SectionTitle(TR.importingUpdates())
                            }
                        }
                        item {
                            SwitchRow(
                                title = TR.importingMergeNotetypes(),
                                checked = current.mergeNotetypes,
                                onCheckedChange = { options = current.toBuilder().setMergeNotetypes(it).build() },
                            )
                        }
                        item {
                            Column {
                                ValueRow(title = TR.importingUpdateNotes(), value = conditionLabel(current.updateNotes), onClick = {
                                    choosing = "notes"
                                })
                                RowDivider()
                            }
                        }
                        item {
                            ValueRow(title = TR.importingUpdateNotetypes(), value = conditionLabel(current.updateNotetypes), onClick = {
                                choosing = "notetypes"
                            })
                        }
                    }
                    PanelPrimaryAction(
                        label = TR.actionsImport(),
                        onClick = ::import,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(RowDefaults.EdgePadding),
                    )
                }
            }
        }

        val current = options
        if (current != null && choosing != null) {
            val isNotes = choosing == "notes"
            ChoiceSheet(
                title = if (isNotes) TR.importingUpdateNotes() else TR.importingUpdateNotetypes(),
                options = UPDATE_CONDITIONS,
                selected = if (isNotes) current.updateNotes else current.updateNotetypes,
                label = ::conditionLabel,
                onSelect = {
                    options =
                        if (isNotes) current.toBuilder().setUpdateNotes(it).build() else current.toBuilder().setUpdateNotetypes(it).build()
                },
                onDismissRequest = { choosing = null },
            )
        }
        if (isHelpShown) {
            TextPage(
                title = TR.importingImportOptions(),
                blocks =
                    listOf(
                        TR.importingAlsoImportProgress() to TR.importingIncludeReviewsHelp(),
                        TR.importingWithDeckConfigs() to TR.importingWithDeckConfigsHelp(),
                        TR.importingMergeNotetypes() to TR.importingMergeNotetypesHelp(),
                        TR.importingUpdateNotes() to TR.importingUpdateNotesHelp(),
                        TR.importingUpdateNotetypes() to TR.importingUpdateNotetypesHelp(),
                    ).flatMap { (title, help) -> listOf(TextBlock.Heading(title)) + paragraphs(help) },
                onClose = { isHelpShown = false },
            )
        }
    }

    private fun import() {
        val chosen = options ?: return
        launchCatchingTask {
            try {
                val response =
                    requireActivity().withProgress(extractProgress = { if (progress.hasImporting()) text = progress.importing }) {
                        withCol { importAnkiPackage(path, chosen) }
                    }
                undoableOp { response.changes }
                result = response
            } catch (e: Exception) {
                error = e.localizedMessage ?: e.toString()
            }
        }
    }

    @Composable
    private fun Line(text: String) {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
        )
    }

    /** The overview, then each note and what happened to it. */
    @Composable
    private fun ResultList(
        response: ImportResponse,
        modifier: Modifier,
    ) {
        val summaries = remember(response) { importSummaries(response.log) }
        PagedList(modifier) {
            item { SectionTitle(TR.importingOverview()) }
            item { Line(TR.importingNotesFoundInFile2(response.log.foundNotes)) }
            summaries.filter { it.notes.isNotEmpty() }.forEach { summary ->
                item { InfoRow(title = summary.count(summary.notes.size), value = summary.action) }
            }
            val rows = summaries.flatMap { summary -> summary.notes.map { summary to it } }
            if (rows.isNotEmpty()) {
                item {
                    Column {
                        GroupDivider()
                        SectionTitle(TR.importingDetails())
                    }
                }
                items(rows) { (summary, note) ->
                    Column {
                        InfoRow(title = note.first, value = "${summary.action} · ${note.second}")
                        RowDivider()
                    }
                }
            }
        }
    }

    companion object {
        private const val KEY_FILE_PATH = "filePath"

        private val UPDATE_CONDITIONS =
            listOf(
                ImportAnkiPackageUpdateCondition.IMPORT_ANKI_PACKAGE_UPDATE_CONDITION_IF_NEWER,
                ImportAnkiPackageUpdateCondition.IMPORT_ANKI_PACKAGE_UPDATE_CONDITION_ALWAYS,
                ImportAnkiPackageUpdateCondition.IMPORT_ANKI_PACKAGE_UPDATE_CONDITION_NEVER,
            )

        private fun conditionLabel(condition: ImportAnkiPackageUpdateCondition): String =
            when (condition) {
                ImportAnkiPackageUpdateCondition.IMPORT_ANKI_PACKAGE_UPDATE_CONDITION_ALWAYS -> TR.importingUpdateAlways()
                ImportAnkiPackageUpdateCondition.IMPORT_ANKI_PACKAGE_UPDATE_CONDITION_NEVER -> TR.importingUpdateNever()
                else -> TR.importingUpdateIfNewer()
            }

        fun getIntent(
            context: Context,
            filePath: String,
        ): Intent {
            val arguments = Bundle().apply { putString(KEY_FILE_PATH, filePath) }
            return SingleFragmentActivity.getIntent(context, AnkiPackageImporterFragment::class, arguments)
        }
    }
}

/**
 * One line of the import log's overview (`getSummaries`): what happened to some notes, how to count
 * them, and the notes, each as its first field and the reason.
 */
data class ImportSummary(
    val action: String,
    val count: (Int) -> String,
    /** (fields joined with commas, reason) */
    val notes: List<Pair<String, String>>,
)

fun importSummaries(log: ImportResponse.Log): List<ImportSummary> {
    fun List<ImportResponse.Note>.with(reason: String) = map { it.fieldsList.joinToString(",") to reason }

    val added = log.newList.with(TR.importingAddedNewNote()).toMutableList()
    val skipped = log.duplicateList.with(TR.importingExistingNoteSkipped()).toMutableList()
    val updated = log.updatedList.with(TR.importingNoteUpdatedAsFileHadNewer()).toMutableList()
    val failed =
        log.conflictingList.with(TR.importingNoteSkippedUpdateDueToNotetype2()) +
            log.missingNotetypeList.with(TR.importingNoteSkippedDueToMissingNotetype()) +
            log.missingDeckList.with(TR.importingNoteSkippedDueToMissingDeck()) +
            log.emptyFirstFieldList.with(TR.importingNoteSkippedDueToEmptyFirstField())
    // notes matched on their first field join the queue their resolution puts them in
    when (log.dupeResolution) {
        DupeResolution.DUPLICATE -> added += log.firstFieldMatchList.with(TR.importingDuplicateNoteAdded())
        DupeResolution.PRESERVE -> skipped += log.firstFieldMatchList.with(TR.importingExistingNoteSkipped())
        else -> updated += log.firstFieldMatchList.with(TR.importingNoteUpdatedAsFileHadNewer())
    }
    return listOf(
        ImportSummary(TR.addingAdded(), TR::importingNotesAdded, added),
        ImportSummary(TR.importingSkipped(), TR::importingExistingNotesSkipped, skipped),
        ImportSummary(TR.importingUpdated(), TR::importingNotesUpdated, updated),
        ImportSummary(TR.importingSkipped(), TR::importingNotesFailed, failed),
    )
}

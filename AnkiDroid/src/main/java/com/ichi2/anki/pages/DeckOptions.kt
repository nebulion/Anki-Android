// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import anki.collection.ComputeParamsProgress
import anki.collection.OpChanges
import anki.deck_config.UpdateDeckConfigsMode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.ProgressContext
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.deckoptions.DeckOptionsScreenMMD
import com.ichi2.anki.deckoptions.DeckOptionsState
import com.ichi2.anki.deckoptions.FsrsActions
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.updateDeckConfigsRaw
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelBody
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.TextPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Deck options as a native page: the backend's options for the deck, edited as settings rows and
 * written with Save. See `DeckOptionsScreenMMD` for the page and `DeckOptionsState` for the rules.
 *
 * It was the backend's web page, restyled. The FSRS simulator and the "help me decide" workload
 * graph are not rebuilt; everything else is here.
 */
class DeckOptions : ComposeHostFragment() {
    private val deckId: DeckId by lazy { requireArguments().getLong(KEY_DECK_ID) }

    private var state: DeckOptionsState? by mutableStateOf(null)
    private var isMenuShown by mutableStateOf(false)
    private var isConfirmingDiscard by mutableStateOf(false)

    /** Asking for a preset's name: the menu item that asked, and the name to start from. */
    private var namePrompt: Pair<String, String>? by mutableStateOf(null)
    private var onNameEntered: (String) -> Unit = {}
    private var isConfirmingRemove by mutableStateOf(false)

    /** A result or a refusal, shown in a panel. */
    private var message: String? by mutableStateOf(null)

    private val onBack =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (state?.isModified() == true) {
                    isConfirmingDiscard = true
                } else {
                    close()
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBack)
        launchCatchingTask {
            state = withCol { DeckOptionsState(deckId, backend.getDeckConfigsForUpdate(deckId)) }
        }
    }

    @Composable
    override fun ScreenContent() {
        val current = state
        DeckOptionsScreenMMD(
            state = current,
            tr = TR,
            fsrsActions = fsrsActions,
            onSave = { save(UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL) },
            onMenu = { isMenuShown = true },
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        )
        if (current == null) return

        if (isMenuShown) {
            MenuPanel(
                title = TR.cardStatsPreset(),
                items =
                    listOf(
                        MenuItem(TR.deckConfigAddGroup()) { promptForName(TR.deckConfigAddGroup(), "") { current.addConfig(it) } },
                        MenuItem(TR.deckConfigCloneGroup()) {
                            promptForName(TR.deckConfigCloneGroup(), current.currentName) { current.cloneConfig(it) }
                        },
                        MenuItem(TR.deckConfigRenameGroup()) {
                            promptForName(TR.deckConfigRenameGroup(), current.currentName) { current.setCurrentName(it) }
                        },
                        MenuItem(TR.deckConfigRemoveGroup()) {
                            if (current.defaultConfigSelected()) {
                                message = TR.schedulingTheDefaultConfigurationCantBeRemoved()
                            } else {
                                isConfirmingRemove = true
                            }
                        },
                        MenuItem(
                            TR.deckConfigSaveToAllSubdecks(),
                        ) { save(UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_APPLY_TO_CHILDREN) },
                    ),
                onDismissRequest = { isMenuShown = false },
            )
        }
        namePrompt?.let { (title, initial) ->
            TextPanel(
                title = title,
                body = TR.deckConfigNamePrompt(),
                value = initial,
                confirmLabel = stringResource(R.string.dialog_ok),
                dismissLabel = stringResource(R.string.dialog_cancel),
                validate = { null },
                onConfirm = { text ->
                    namePrompt = null
                    text.trim().takeIf { it.isNotEmpty() }?.let(onNameEntered)
                },
                onDismiss = { namePrompt = null },
            )
        }
        if (isConfirmingRemove) {
            val fullSync = if (current.removalWillForceFullSync()) TR.deckConfigWillRequireFullSync() + " " else ""
            ConfirmPanel(
                title = TR.deckConfigRemoveGroup(),
                body = (fullSync + TR.deckConfigConfirmRemoveName(current.currentName)).replace(Regex("\\s+"), " "),
                confirmLabel = stringResource(R.string.dialog_positive_delete),
                dismissLabel = stringResource(R.string.dialog_cancel),
                onConfirm = {
                    isConfirmingRemove = false
                    current.removeCurrentConfig()
                },
                onDismiss = { isConfirmingRemove = false },
            )
        }
        if (isConfirmingDiscard) {
            ConfirmPanel(
                title = TR.cardTemplatesDiscardChanges(),
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
        message?.let { text ->
            PanelDialog(onDismissRequest = { message = null }) {
                PanelBody(text)
                PanelActions { PanelPrimaryAction(label = stringResource(R.string.dialog_ok), onClick = { message = null }) }
            }
        }
    }

    private fun promptForName(
        title: String,
        initial: String,
        onName: (String) -> Unit,
    ) {
        onNameEntered = onName
        namePrompt = title to initial
    }

    private fun close() {
        onBack.isEnabled = false
        requireActivity().finish()
    }

    /** Writes the options, then closes the page, as the web page's Save did. */
    private fun save(mode: UpdateDeckConfigsMode) {
        val request = state?.dataForSaving(mode) ?: return
        launchCatchingTask {
            val output =
                requireActivity().withProgress(
                    extractProgress = { text = toProgressText() ?: getString(R.string.dialog_processing) },
                ) {
                    withContext(Dispatchers.IO) { withCol { updateDeckConfigsRaw(request.toByteArray()) } }
                }
            undoableOp { OpChanges.parseFrom(output) }
            close()
        }
    }

    private val fsrsActions =
        object : FsrsActions {
            override fun optimize() {
                val current = state ?: return
                if (current.presetAssignmentsChanged) {
                    message = TR.deckConfigPleaseSaveYourChangesFirst()
                    return
                }
                val config = current.current
                val params = current.fsrsParams()
                launchCatchingTask {
                    val response =
                        requireActivity().withProgress(extractProgress = { toProgressText()?.let { text = it } }) {
                            withCol {
                                backend.computeFsrsParams(
                                    search = paramSearch(current),
                                    currentParams = params,
                                    ignoreRevlogsBeforeMs = ignoreRevlogsBeforeMs(config.ignoreRevlogsBeforeDate),
                                    numOfRelearningSteps = relearningStepsInDay(config.relearnStepsList),
                                    healthCheck = current.fsrsHealthCheck,
                                )
                            }
                        }
                    val alreadyOptimal =
                        (
                            params.isNotEmpty() &&
                                params.indices.all { i -> "%.4f".format(params[i]) == "%.4f".format(response.paramsList.getOrNull(i)) }
                        ) ||
                            response.paramsCount == 0
                    val lines =
                        listOfNotNull(
                            if (alreadyOptimal) {
                                if (response.fsrsItems != 0) TR.deckConfigFsrsParamsOptimal() else TR.deckConfigFsrsParamsNoReviews()
                            } else {
                                null
                            },
                            if (response.hasHealthCheckPassed()) {
                                if (response.healthCheckPassed) TR.deckConfigFsrsGoodFit() else TR.deckConfigFsrsBadFitWarning()
                            } else {
                                null
                            },
                        )
                    if (!alreadyOptimal) current.updateConfig { clearFsrsParams6().addAllFsrsParams6(response.paramsList) }
                    if (lines.isNotEmpty()) message = lines.joinToString("\n\n")
                }
            }

            override fun evaluate() {
                val current = state ?: return
                if (current.presetAssignmentsChanged) {
                    message = TR.deckConfigPleaseSaveYourChangesFirst()
                    return
                }
                val config = current.current
                launchCatchingTask {
                    val response =
                        requireActivity().withProgress(extractProgress = { toProgressText()?.let { text = it } }) {
                            withCol {
                                backend.evaluateParamsLegacy(
                                    params = current.fsrsParams(),
                                    search = paramSearch(current),
                                    ignoreRevlogsBeforeMs = ignoreRevlogsBeforeMs(config.ignoreRevlogsBeforeDate),
                                )
                            }
                        }
                    message =
                        "Log loss: ${"%.4f".format(response.logLoss)}, RMSE(bins): ${"%.2f".format(response.rmseBins * 100)}%. " +
                        TR.deckConfigSmallerIsBetter()
                }
            }

            override fun saveAndOptimizeAll() = save(UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS)
        }

    companion object {
        private const val KEY_DECK_ID = "deckId"

        fun getIntent(
            context: Context,
            deckId: DeckId,
        ): Intent =
            SingleFragmentActivity.getIntent(
                context,
                fragmentClass = DeckOptions::class,
                arguments = Bundle().apply { putLong(KEY_DECK_ID, deckId) },
            )
    }
}

/** The search the parameters are optimised on: the preset's own, or its cards not suspended. */
private fun paramSearch(state: DeckOptionsState): String =
    state.current.paramSearch.ifEmpty { "preset:\"${state.currentNameForSearch}\" -is:suspended" }

/** "2024-01-31" as milliseconds since the epoch at UTC midnight, as `new Date()` reads it; 0 if unset. */
internal fun ignoreRevlogsBeforeMs(date: String): Long =
    runCatching {
        LocalDate
            .parse(date)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrDefault(0L)

/** How many relearning steps fit in the first day: the steps before their total reaches a day. */
internal fun relearningStepsInDay(steps: List<Float>): Int {
    var accumulated = 0f
    var count = 0
    for (step in steps) {
        accumulated += step
        if (accumulated >= 1440) break
        count++
    }
    return count
}

/**
 * Returns a string indicating progress, such as:
 *
 * ```
 * Optimizing preset 1/20
 * 5.2% of 1000 reviews
 * ```
 *
 * @return the above string, or `null` if a string could not be generated
 */
private fun ProgressContext.toProgressText(): String? =
    when {
        progress.hasComputeParams() -> progress.computeParams.toProgressText()
        progress.hasComputeMemory() -> progress.computeMemory.label // Updating cards: X/Y...
        else -> null
    }

private fun ComputeParamsProgress.toProgressText(): String {
    val label =
        TR.deckConfigOptimizingPreset(
            currentCount = currentPreset,
            totalCount = totalPresets,
        )
    val pct = if (total > 0) (current.toDouble() / total.toDouble() * 100.0) else 0.0
    val reviewsLabel =
        TR.deckConfigPercentOfReviews(
            pct = "%.1f".format(pct),
            reviews = reviews,
        )
    return label + "\n" + reviewsLabel
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.mediacheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelBody
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.PanelTitle
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.ScreenHeader
import com.mudita.mmd.components.text.TextMMD

/**
 * Check media (P1): the backend's report as text, the two fixes it may offer, and the media trash
 * in the header's menu. Every result is a panel whose OK closes the page.
 *
 * The report was a WebView; it is plain text with line breaks, so it is now text.
 */
class MediaCheckFragment : ComposeHostFragment() {
    private val viewModel: MediaCheckViewModel by viewModels()

    /** A finished operation: its title (if any) and message; OK closes the page. */
    private var result by mutableStateOf<Pair<String?, String>?>(null)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeProgress(viewModel) { progress -> getString(progress.messageRes) }
        // a rebuilt page keeps its view model; a page restored after the process died has none
        if (viewModel.mediaCheckResult.value == null) viewModel.checkMedia()
    }

    @Composable
    override fun ScreenContent() {
        val response by viewModel.mediaCheckResult.collectAsStateWithLifecycle()
        var isMenuShown by rememberSaveable { mutableStateOf(false) }
        var isConfirmingDelete by rememberSaveable { mutableStateOf(false) }

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = TR.sentenceCase.checkMediaTitle,
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                },
                actions = {
                    if (response?.haveTrash == true) {
                        HeaderAction(
                            icon = R.drawable.ic_more_vertical,
                            contentDescription = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                            onClick = { isMenuShown = true },
                        )
                    }
                },
            )
            TextMMD(
                text = response?.report.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(RowDefaults.EdgePadding),
            )
            val current = response
            if (current != null && (current.missingCount != 0 || current.unusedCount != 0)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (current.missingCount != 0) {
                        PanelSecondaryAction(
                            label = TR.sentenceCase.tagMissing,
                            onClick = ::tagMissing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (current.unusedCount != 0) {
                        PanelPrimaryAction(
                            label = TR.sentenceCase.checkMediaDeleteUnused,
                            onClick = { isConfirmingDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (isMenuShown) {
            MenuPanel(
                title = TR.sentenceCase.checkMediaTitle,
                items =
                    listOf(
                        MenuItem(TR.sentenceCase.restoreDeleted, onClick = ::restoreTrash),
                        MenuItem(TR.sentenceCase.emptyTrash, onClick = ::emptyTrash),
                    ),
                onDismissRequest = { isMenuShown = false },
            )
        }
        if (isConfirmingDelete) {
            ConfirmPanel(
                title = TR.sentenceCase.checkMediaDeleteUnused,
                body = TR.mediaCheckDeleteUnusedConfirm(),
                confirmLabel = getString(R.string.dialog_positive_delete),
                dismissLabel = getString(R.string.dialog_cancel),
                onConfirm = {
                    isConfirmingDelete = false
                    deleteUnused()
                },
                onDismiss = { isConfirmingDelete = false },
            )
        }
        result?.let { (title, message) ->
            PanelDialog(onDismissRequest = {}, dismissOnClickOutside = false) {
                title?.let { PanelTitle(it) }
                PanelBody(message)
                PanelActions {
                    PanelPrimaryAction(label = getString(R.string.dialog_ok), onClick = { requireActivity().finish() })
                }
            }
        }
    }

    private fun tagMissing() =
        launchCatchingTask {
            viewModel.tagMissing(TR.mediaCheckMissingMediaTag()).join()
            result = getString(R.string.check_media_tags_added) to TR.browsingNotesUpdated(viewModel.taggedFiles)
        }

    private fun deleteUnused() =
        launchCatchingTask {
            viewModel.deleteUnusedMedia().join()
            result =
                getString(R.string.delete_media_result_title) to
                resources.getQuantityString(R.plurals.delete_media_result_message, viewModel.deletedFiles, viewModel.deletedFiles)
        }

    private fun restoreTrash() =
        launchCatchingTask {
            viewModel.restoreTrash().join()
            result = null to TR.mediaCheckTrashRestored()
        }

    private fun emptyTrash() =
        launchCatchingTask {
            viewModel.deleteTrash().join()
            result = null to TR.mediaCheckTrashEmptied()
        }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, MediaCheckFragment::class)
    }
}

@get:StringRes
private val MediaCheckProgress.messageRes: Int
    get() =
        when (this) {
            MediaCheckProgress.CHECKING_MEDIA -> R.string.check_media_message
            MediaCheckProgress.ADDING_TAGS -> R.string.check_media_adding_missing_tag
            MediaCheckProgress.DELETING_MEDIA -> R.string.delete_media_message
        }

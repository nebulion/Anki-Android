// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel.ExportReadyParams
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelDialogFragment
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.PanelTitle
import timber.log.Timber

/**
 * The export is written: offer to save it with the system file picker.
 *
 * There is no Share: on the Kompakt the share sheet has almost nowhere to send a file, and the
 * picker is where a file can be kept or moved to a computer.
 */
class ExportReadyDialog : PanelDialogFragment() {
    private val exportPath
        get() = requireArguments().requireString(KEY_EXPORT_PATH)

    @Composable
    override fun PanelContent() {
        PanelTitle(getString(R.string.export_ready_title))
        PanelActions {
            PanelSecondaryAction(
                label = getString(R.string.dialog_cancel),
                onClick = ::dismiss,
                modifier = Modifier.weight(1f),
            )
            PanelPrimaryAction(
                label = getString(R.string.export_choice_save_to),
                onClick = {
                    parentFragmentManager.setFragmentResult(
                        REQUEST_EXPORT_SAVE,
                        Bundle().apply { putString(KEY_EXPORT_PATH, exportPath) },
                    )
                    dismiss()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    companion object {
        const val REQUEST_EXPORT_SAVE = "request_export_save"
        const val KEY_EXPORT_PATH = "key_export_path"

        fun newInstance(exportPath: String) =
            ExportReadyDialog().apply {
                arguments = Bundle().apply { putString(KEY_EXPORT_PATH, exportPath) }
            }
    }
}

internal fun AnkiActivity.handleExportReadyRequest(params: ExportReadyParams) {
    runCatching {
        Timber.i("Attempting to show ExportReadyDialog...")
        val dialog = ExportReadyDialog.newInstance(params.exportPath)
        dialog.show(supportFragmentManager, "ExportReadyDialog")
    }.onFailure { exception ->
        if (exception !is IllegalStateException) throw exception
        Timber.w(
            exception,
            "Failed to show ExportReadyDialog, activity is likely paused.",
        )
    }.onSuccess {
        Timber.i("ExportReadyDialog is displayed, clearing any stored requests...")
        exportReadyViewModel.clearExportReadyRequest()
    }
}

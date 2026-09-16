// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.activityViewModels
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.dialogs.ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM
import com.ichi2.anki.dialogs.ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelBody
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.PanelTitle
import com.ichi2.compose.mmd.applyPanelWindow
import com.ichi2.compose.mmd.panelView
import timber.log.Timber
import java.net.URLDecoder

@NeedsTest("integration test: ImportDialog => DeckPicker")
class ImportDialog : AsyncDialogFragment() {
    private val importViewModel: ImportViewModel by activityViewModels()

    private val dialogType: Type
        get() = Type.fromCode(requireArguments().getInt(IMPORT_DIALOG_TYPE_KEY))

    private val packagePath: String
        get() = requireArguments().getString(IMPORT_DIALOG_PACKAGE_PATH_KEY)!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = panelView { ImportConfirmation() }

    override fun onStart() {
        super.onStart()
        applyPanelWindow()
    }

    /** Adding to the collection, or replacing it, asks first (P5). */
    @Composable
    private fun ImportConfirmation() {
        val displayFileName = filenameFromPath(convertToDisplayName(packagePath))
        val (message, confirmLabel, confirm) =
            when (dialogType) {
                DIALOG_IMPORT_ADD_CONFIRM ->
                    Triple(
                        res().getString(R.string.import_dialog_message_add, displayFileName),
                        getString(R.string.import_message_add),
                        { importViewModel.triggerImportAdd(packagePath) },
                    )
                DIALOG_IMPORT_REPLACE_CONFIRM ->
                    Triple(
                        res().getString(R.string.import_message_replace_confirm, displayFileName),
                        getString(R.string.dialog_positive_replace),
                        { importViewModel.triggerImportReplace(packagePath) },
                    )
            }
        PanelTitle(getString(R.string.import_title))
        PanelBody(message)
        PanelActions {
            PanelSecondaryAction(label = getString(R.string.dialog_cancel), onClick = ::dismiss, modifier = Modifier.weight(1f))
            PanelPrimaryAction(
                label = confirmLabel,
                onClick = {
                    confirm()
                    activity?.dismissAllDialogFragments()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    private fun convertToDisplayName(name: String): String {
        // ImportUtils URLEncodes names, which isn't great for display.
        // NICE_TO_HAVE: Pass in the DisplayFileName closer to the source of the bad data, rather than fixing it here.
        return try {
            URLDecoder.decode(name, "UTF-8")
        } catch (e: Exception) {
            Timber.w(e, "Failed to convert filename to displayable string")
            name
        }
    }

    override val notificationMessage: String
        get() {
            return res().getString(R.string.import_interrupted)
        }

    override val notificationTitle: String
        get() {
            return res().getString(R.string.import_title)
        }

    enum class Type(
        val code: Int,
    ) {
        DIALOG_IMPORT_ADD_CONFIRM(0),
        DIALOG_IMPORT_REPLACE_CONFIRM(1),
        ;

        companion object {
            fun fromCode(code: Int) = Type.entries.first { code == it.code }
        }
    }

    companion object {
        const val IMPORT_DIALOG_TYPE_KEY = "dialogType"
        const val IMPORT_DIALOG_PACKAGE_PATH_KEY = "packagePath"

        /**
         * A set of dialogs which deal with importing a file
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         * @param packagePath the path of the package to import
         */
        @CheckResult
        fun newInstance(
            dialogType: Type,
            packagePath: String,
        ): ImportDialog =
            ImportDialog().apply {
                arguments =
                    Bundle().apply {
                        putInt(IMPORT_DIALOG_TYPE_KEY, dialogType.code)
                        putString(IMPORT_DIALOG_PACKAGE_PATH_KEY, packagePath)
                    }
            }

        private fun filenameFromPath(path: String): String = path.split("/").toTypedArray()[path.split("/").toTypedArray().size - 1]
    }
}

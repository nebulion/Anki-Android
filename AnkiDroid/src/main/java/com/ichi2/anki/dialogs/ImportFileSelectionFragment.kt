// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.core.os.BundleCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent.LinkClicked
import com.ichi2.anki.common.analytics.LinkAction
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.MimeTypeUtils
import com.ichi2.compose.mmd.PanelChoice
import com.ichi2.compose.mmd.PanelDialogFragment
import com.ichi2.compose.mmd.PanelTitle
import kotlinx.parcelize.Parcelize
import timber.log.Timber

class ImportFileSelectionFragment : PanelDialogFragment() {
    @Composable
    override fun PanelContent() {
        val entries = importEntries(options())
        PanelTitle(TR.actionsImport())
        entries.forEachIndexed { index, entry ->
            PanelChoice(label = getString(entry.titleRes), isFirst = index == 0) {
                Analytics.send(LinkClicked(entry.analyticsId))
                dismiss()
                openImportFilePicker(
                    activity = requireAnkiActivity(),
                    fileType = entry.type,
                    multiple = entry.multiple,
                    mimeType = entry.mimeType,
                    extraMimes = entry.extraMimes,
                )
            }
        }
    }

    private fun options(): ImportOptions? =
        arguments?.let { args ->
            args.classLoader = this@ImportFileSelectionFragment::class.java.classLoader
            BundleCompat.getParcelable(args, ARG_IMPORT_OPTIONS, ImportOptions::class.java)
        }

    @VisibleForTesting
    internal class ImportEntry(
        @StringRes val titleRes: Int,
        val analyticsId: LinkAction,
        val type: ImportFileType,
        val multiple: Boolean = false,
        val mimeType: String = "*/*",
        val extraMimes: Array<String>? = null,
    )

    @Parcelize
    data class ImportOptions(
        val importColpkg: Boolean,
        val importApkg: Boolean,
        val importTextFile: Boolean,
    ) : Parcelable

    enum class ImportFileType {
        APKG,
        COLPKG,
        CSV,
    }

    interface ApkgImportResultLauncherProvider {
        fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent>
    }

    interface CsvImportResultLauncherProvider {
        fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent>
    }

    companion object {
        private const val ARG_IMPORT_OPTIONS = "arg_import_options"

        /** The kinds of file [options] allows, in the order they are offered. */
        @VisibleForTesting
        internal fun importEntries(options: ImportOptions?): List<ImportEntry> {
            if (options == null) return emptyList()
            return buildList {
                if (options.importApkg) {
                    add(ImportEntry(R.string.import_deck_package, LinkAction.IMPORT_APKG_FILE, ImportFileType.APKG))
                }
                if (options.importColpkg) {
                    add(ImportEntry(R.string.import_collection_package, LinkAction.IMPORT_COLPKG_FILE, ImportFileType.COLPKG))
                }
                if (options.importTextFile) {
                    add(
                        ImportEntry(
                            R.string.import_csv,
                            LinkAction.IMPORT_CSV_FILE,
                            ImportFileType.CSV,
                            multiple = false,
                            mimeType = "*/*",
                            extraMimes = MimeTypeUtils.CSV_TSV_MIME_TYPES,
                        ),
                    )
                }
            }
        }

        fun newInstance(options: ImportOptions) =
            ImportFileSelectionFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_IMPORT_OPTIONS, options) }
            }

        /**
         * Builds the [Intent] used to launch the system file picker.
         */
        @VisibleForTesting
        internal fun buildImportFilePickerIntent(
            multiple: Boolean = false,
            mimeType: String = "*/*",
            extraMimes: Array<String>? = null,
        ): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra("android.content.extra.SHOW_ADVANCED", true)
                putExtra("android.content.extra.FANCY", true)
                putExtra("android.content.extra.SHOW_FILESIZE", true)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                extraMimes?.let { putExtra(Intent.EXTRA_MIME_TYPES, it) }
            }

        /**
         * Calls through the system with an [Intent] to pick a file to be imported.
         */
        fun openImportFilePicker(
            activity: AnkiActivity,
            fileType: ImportFileType,
            multiple: Boolean = false,
            mimeType: String = "*/*",
            extraMimes: Array<String>? = null,
        ) {
            Timber.d("openImportFilePicker() delegating to file picker intent")
            val intent = buildImportFilePickerIntent(multiple, mimeType, extraMimes)

            try {
                if (
                    (fileType == ImportFileType.APKG || fileType == ImportFileType.COLPKG) &&
                    activity is ApkgImportResultLauncherProvider
                ) {
                    activity.getApkgFileImportResultLauncher().launch(intent)
                } else if (fileType == ImportFileType.CSV && activity is CsvImportResultLauncherProvider) {
                    activity.getCsvFileImportResultLauncher().launch(intent)
                } else {
                    Timber.w("Activity($activity) can't handle requested import: $fileType")
                }
            } catch (ex: ActivityNotFoundException) {
                Timber.w("No activity to handle openImportFilePicker request")
                activity.showSnackbar(R.string.activity_start_failed)
            }
        }
    }
}

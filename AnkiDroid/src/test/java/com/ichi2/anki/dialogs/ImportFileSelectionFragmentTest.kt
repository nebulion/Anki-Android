// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Intent
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.Companion.buildImportFilePickerIntent
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.utils.MimeTypeUtils
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportFileSelectionFragmentTest : RobolectricTest() {
    @Test
    fun `APKG picker intent disallows multiple files`() {
        val intent = buildImportFilePickerIntent()
        assertThat(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), equalTo(false))
    }

    @Test
    fun `COLPKG picker intent disallows multiple files`() {
        val intent = buildImportFilePickerIntent()
        assertThat(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), equalTo(false))
    }

    @Test
    fun `restore backup picker intent disallows multiple files`() {
        val intent = buildImportFilePickerIntent()
        assertThat(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), equalTo(false))
    }

    @Test
    fun `CSV picker intent disallows multiple files and carries csv mime types`() {
        val intent = buildImportFilePickerIntent(extraMimes = MimeTypeUtils.CSV_TSV_MIME_TYPES)

        assertThat(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), equalTo(false))
        assertThat(
            "CSV picker must pass CSV/TSV mime types as EXTRA_MIME_TYPES",
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList(),
            equalTo(MimeTypeUtils.CSV_TSV_MIME_TYPES.toList()),
        )
    }

    @Test
    fun `picker intent uses ACTION_OPEN_DOCUMENT with openable category`() {
        val intent = buildImportFilePickerIntent()
        assertThat(intent.action, equalTo(Intent.ACTION_OPEN_DOCUMENT))
        assertThat(
            "Intent must request an openable document",
            intent.categories?.contains(Intent.CATEGORY_OPENABLE),
            equalTo(true),
        )
    }

    @Test
    fun `dialog with all import options shows three entries`() {
        assertThat(
            labelsFor(ImportOptions(importColpkg = true, importApkg = true, importTextFile = true)),
            equalTo(
                listOf(
                    targetContext.getString(R.string.import_deck_package),
                    importCollectionPackageLabel,
                    importCsvLabel,
                ),
            ),
        )
    }

    @Test
    fun `dialog with only colpkg option shows only colpkg entry (restore backup scenario)`() {
        assertThat(
            labelsFor(ImportOptions(importColpkg = true, importApkg = false, importTextFile = false)),
            equalTo(listOf(importCollectionPackageLabel)),
        )
    }

    @Test
    fun `dialog with only apkg option shows only apkg entry`() {
        assertThat(
            labelsFor(ImportOptions(importColpkg = false, importApkg = true, importTextFile = false)),
            equalTo(listOf(targetContext.getString(R.string.import_deck_package))),
        )
    }

    @Test
    fun `dialog with only csv option shows only csv entry`() {
        assertThat(
            labelsFor(ImportOptions(importColpkg = false, importApkg = false, importTextFile = true)),
            equalTo(listOf(importCsvLabel)),
        )
    }

    /** The labels of the choices the panel offers for [options], in order */
    private fun labelsFor(options: ImportOptions): List<String> =
        ImportFileSelectionFragment.importEntries(options).map { targetContext.getString(it.titleRes) }

    private val importCollectionPackageLabel = targetContext.getString(R.string.import_collection_package)

    private val importCsvLabel = targetContext.getString(R.string.import_csv)
}

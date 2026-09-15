// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.compose.runtime.Composable
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.showImportDialog
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.ComposeHostFragment

/** The More tab of [DeckPicker]: [MoreScreenMMD] with the home screen's collection-wide actions. */
class MoreTabFragment : ComposeHostFragment() {
    private val home: DeckPicker
        get() = requireActivity() as DeckPicker

    @Composable
    override fun ScreenContent() {
        MoreScreenMMD(title = getString(R.string.bottom_nav_more), entries = entries())
    }

    private fun entries(): List<MoreEntry> {
        // Only some labels have a Fragment overload; inside `with(requireContext())` those are ambiguous
        val emptyCards = with(requireContext()) { TR.sentenceCase.emptyCards }
        return listOf(
            MoreEntry(TR.sentenceCase.createDeck) { home.showCreateDeckDialog() },
            MoreEntry(getString(R.string.new_dynamic_deck)) { home.showCreateFilteredDeckDialog() },
            MoreEntry(TR.actionsImport()) { home.showImportDialog() },
            MoreEntry(TR.actionsExport()) { home.exportCollection() },
            MoreEntry(getString(R.string.menu_my_account)) { home.openAccount() },
            MoreEntry(TR.sentenceCase.checkDatabase) { home.confirmCheckDatabase() },
            MoreEntry(TR.sentenceCase.checkMediaAction) { home.mediaCheck() },
            MoreEntry(emptyCards) { home.showEmptyCardsDialog() },
            MoreEntry(getString(R.string.menu_create_backup)) { home.createBackup() },
            MoreEntry(getString(R.string.backup_restore)) { home.confirmRestoreBackup() },
            MoreEntry(getString(R.string.settings)) { home.openSettings() },
        )
    }
}

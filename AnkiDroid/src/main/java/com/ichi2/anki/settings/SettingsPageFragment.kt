// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.utils.VersionUtils

/**
 * One page of the settings. The root page also carries what used to be the More page: the owner's
 * call (2026-09-15) was that the home screen's second action is a gear, not a menu, so the
 * collection's actions live in settings beside the preferences they belong with.
 */
enum class SettingsPage(
    @StringRes val titleRes: Int,
) {
    Root(R.string.settings),
    Reviewing(R.string.pref_cat_reviewing),
    StudyScreen(R.string.mmd_settings_study_screen),
    Gestures(R.string.pref_cat_gestures),
    Accessibility(R.string.accessibility),
    EInk(R.string.mmd_settings_eink),
    Maintenance(R.string.mmd_settings_maintenance),
    Backups(R.string.button_backup),
    Sync(R.string.pref_cat_sync),
    CustomSyncServer(R.string.custom_sync_server_title),
    Notifications(R.string.notification_pref),
    General(R.string.pref_cat_general),
    Advanced(R.string.pref_cat_advanced),
}

/**
 * A settings page, hosted over the deck list by [DeckPicker]. One Fragment class serves every page:
 * which one it shows is an argument, so opening a sub-page is an ordinary back-stack push and the
 * pages survive the activity being recreated.
 */
class SettingsPageFragment : ComposeHostFragment() {
    internal val page: SettingsPage
        get() = SettingsPage.valueOf(requireArguments().getString(ARG_PAGE) ?: SettingsPage.Root.name)

    internal val home: DeckPicker
        get() = requireActivity() as DeckPicker

    @Composable
    override fun ScreenContent() {
        SettingsScreenMMD(
            title = getString(page.titleRes),
            entries = entries(),
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
            footer = if (page == SettingsPage.Root) getString(R.string.mmd_version, VersionUtils.pkgVersionName) else null,
        )
    }

    /** Null while the page is still reading values out of the collection. */
    @Composable
    private fun entries(): List<SettingsEntry>? =
        when (page) {
            SettingsPage.Root -> rootEntries()
            SettingsPage.Reviewing -> reviewingEntries()
            SettingsPage.StudyScreen -> studyScreenEntries()
            SettingsPage.Gestures -> gestureEntries()
            SettingsPage.Accessibility -> accessibilityEntries()
            SettingsPage.EInk -> einkEntries()
            SettingsPage.Maintenance -> maintenanceEntries()
            SettingsPage.Backups -> backupEntries()
            SettingsPage.Sync -> syncEntries()
            SettingsPage.CustomSyncServer -> customSyncServerEntries()
            SettingsPage.Notifications -> notificationEntries()
            SettingsPage.General -> generalEntries()
            SettingsPage.Advanced -> advancedEntries()
        }

    internal fun openPage(page: SettingsPage) = home.openSettings(page)

    companion object {
        private const val ARG_PAGE = "settingsPage"

        fun newInstance(page: SettingsPage) =
            SettingsPageFragment().apply {
                arguments = bundleOf(ARG_PAGE to page.name)
            }
    }
}

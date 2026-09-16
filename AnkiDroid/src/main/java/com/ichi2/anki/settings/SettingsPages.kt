// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PendingIntentCompat
import androidx.core.os.LocaleListCompat
import anki.config.Preferences.BackupLimits
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.setDayOffset
import com.ichi2.anki.services.BootService.Companion.scheduleNotification
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.showImportDialog
import com.ichi2.anki.ui.eink.MmdKitGalleryFragment
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.ifNullOrEmpty
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.utils.LanguageUtil
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// The entries of each settings page. Every page is a list of `SettingsEntry`; the helpers in
// `SettingsEntries.kt` own the editors. Pages whose values live in the collection rather than in
// preferences read them once, through `loadFromCollection`, and return null until they arrive.

/** Reads a value out of the collection once, for a page that cannot be drawn without it. */
@Composable
private fun <T> loadFromCollection(load: suspend () -> T): T? {
    var value by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(Unit) { value = load() }
    return value
}

// ************************************* The root page *************************************** //

@Composable
internal fun SettingsPageFragment.rootEntries(): List<SettingsEntry> {
    // Only some backend labels have a Fragment overload; inside `with(context)` those are ambiguous
    val checkDatabase = TR.sentenceCase.checkDatabase
    return listOf(
        SettingsEntry.Section(getString(R.string.mmd_section_study)),
        SettingsEntry.Page(getString(R.string.pref_cat_reviewing)) { openPage(SettingsPage.Reviewing) },
        SettingsEntry.Page(getString(R.string.mmd_settings_study_screen)) { openPage(SettingsPage.StudyScreen) },
        SettingsEntry.Page(getString(R.string.mmd_settings_gestures)) { openPage(SettingsPage.Gestures) },
        SettingsEntry.Page(getString(R.string.accessibility)) { openPage(SettingsPage.Accessibility) },
        SettingsEntry.Page(getString(R.string.mmd_settings_eink)) { openPage(SettingsPage.EInk) },
        SettingsEntry.Section(getString(R.string.mmd_section_collection)),
        SettingsEntry.Action(TR.sentenceCase.createDeck) { home.showCreateDeckDialog() },
        SettingsEntry.Action(getString(R.string.new_dynamic_deck)) { home.showCreateFilteredDeckDialog() },
        SettingsEntry.Action(TR.actionsImport()) { home.showImportDialog() },
        SettingsEntry.Action(TR.actionsExport()) { home.exportCollection() },
        SettingsEntry.Page(getString(R.string.mmd_settings_maintenance), subtitle = checkDatabase) {
            openPage(SettingsPage.Maintenance)
        },
        SettingsEntry.Page(getString(R.string.button_backup)) { openPage(SettingsPage.Backups) },
        SettingsEntry.Section(getString(R.string.mmd_section_account)),
        SettingsEntry.Action(
            title = TR.sentenceCase.ankiWebAccount,
            subtitle = Prefs.username.ifNullOrEmpty { getString(R.string.sync_account_summ_logged_out) },
        ) { home.openAccount() },
        SettingsEntry.Page(getString(R.string.pref_cat_sync)) { openPage(SettingsPage.Sync) },
        SettingsEntry.Section(getString(R.string.mmd_section_app)),
        SettingsEntry.Page(getString(R.string.notification_pref)) { openPage(SettingsPage.Notifications) },
        SettingsEntry.Page(getString(R.string.pref_cat_general)) { openPage(SettingsPage.General) },
        SettingsEntry.Page(getString(R.string.pref_cat_advanced)) { openPage(SettingsPage.Advanced) },
    )
}

// **************************************** Study ******************************************** //

/** The scheduler's own settings, which live in the collection. */
private class SchedulingValues(
    val dayOffset: Int,
    val learnAheadMinutes: Int,
    val timeboxMinutes: Int,
)

@Composable
internal fun SettingsPageFragment.reviewingEntries(): List<SettingsEntry>? {
    val values =
        loadFromCollection {
            SchedulingValues(
                dayOffset = CollectionPreferences.getDayOffset(),
                learnAheadMinutes = CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES),
                timeboxMinutes = CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
            )
        } ?: return null
    return listOf(
        SettingsEntry.Section(getString(R.string.pref_cat_scheduling)),
        numberEntry(
            title = getString(R.string.day_offset_with_description),
            value = values.dayOffset,
            min = 0,
            max = 23,
            display = { hour -> "%02d:00".format(hour) },
        ) { hour -> launchCatchingTask { setDayOffset(requireContext(), hour) } },
        numberEntry(
            title = getString(R.string.learn_cutoff),
            subtitle = getString(R.string.mmd_settings_minutes),
            value = values.learnAheadMinutes,
            min = 0,
            max = 999,
        ) { minutes ->
            launchCatchingTask { CollectionPreferences.setLearnAheadLimit(minutes.toDuration(DurationUnit.MINUTES)) }
        },
        numberEntry(
            title = getString(R.string.time_limit),
            subtitle = getString(R.string.mmd_settings_minutes),
            value = values.timeboxMinutes,
            min = 0,
            max = 999,
        ) { minutes ->
            launchCatchingTask { CollectionPreferences.setTimeboxTimeLimit(minutes.toDuration(DurationUnit.MINUTES)) }
        },
        switchEntry(
            title = getString(R.string.pref_keep_screen_on),
            subtitle = getString(R.string.pref_keep_screen_on_summ),
            get = { Prefs.keepScreenOn },
            set = { Prefs.keepScreenOn = it },
        ),
    )
}

/** What the study screen shows. The three backend values are stored in the collection. */
private class StudyScreenValues(
    val showRemainingCount: Boolean,
    val showPlayButtons: Boolean,
    val showIntervals: Boolean,
)

@Composable
internal fun SettingsPageFragment.studyScreenEntries(): List<SettingsEntry>? {
    val values =
        loadFromCollection {
            StudyScreenValues(
                showRemainingCount = CollectionPreferences.getShowRemainingDueCounts(),
                showPlayButtons = !CollectionPreferences.getHidePlayAudioButtons(),
                showIntervals = CollectionPreferences.getShowIntervalOnButtons(),
            )
        } ?: return null
    return listOf(
        switchEntry(
            title = TR.preferencesShowRemainingCardCount(),
            get = { values.showRemainingCount },
            set = { value -> launchCatchingTask { CollectionPreferences.setShowRemainingDueCounts(value) } },
        ),
        switchEntry(
            title = TR.preferencesShowPlayButtonsOnCardsWith(),
            get = { values.showPlayButtons },
            set = { value -> launchCatchingTask { CollectionPreferences.setHideAudioPlayButtons(!value) } },
        ),
        SettingsEntry.Section(getString(R.string.answer_buttons)),
        switchEntry(
            title = TR.preferencesShowNextReviewTimeAboveAnswer(),
            get = { values.showIntervals },
            set = { value -> launchCatchingTask { CollectionPreferences.setShowIntervalsOnButtons(value) } },
        ),
        switchEntry(
            title = getString(R.string.hide_hard_and_easy),
            get = { Prefs.hideHardAndEasyButtons },
            set = { Prefs.hideHardAndEasyButtons = it },
        ),
    )
}

@Composable
internal fun SettingsPageFragment.gestureEntries(): List<SettingsEntry> =
    listOf(
        switchEntry(
            title = getString(R.string.gestures),
            subtitle = getString(R.string.gestures_summ),
            get = { Prefs.areGesturesEnabled },
            set = { Prefs.areGesturesEnabled = it },
        ),
        switchEntry(
            title = getString(R.string.gestures_corner_touch),
            subtitle = getString(R.string.gestures_corner_touch_summary),
            get = { Prefs.isNinePointTapEnabled },
            set = { Prefs.isNinePointTapEnabled = it },
        ),
        numberEntry(
            title = getString(R.string.swipe_sensitivity),
            value = Prefs.swipeSensitivityPercent,
            min = 20,
            max = 180,
            display = { percent -> getString(R.string.percentage, percent.toString()) },
        ) { percent -> Prefs.swipeSensitivityPercent = percent },
    )

@Composable
internal fun SettingsPageFragment.accessibilityEntries(): List<SettingsEntry> =
    listOf(
        numberEntry(
            title = getString(R.string.card_zoom),
            value = Prefs.cardZoom,
            min = 10,
            max = 300,
            display = { percent -> getString(R.string.percentage, percent.toString()) },
        ) { percent -> Prefs.cardZoom = percent },
        numberEntry(
            title = getString(R.string.image_zoom),
            value = Prefs.imageZoom,
            min = 50,
            max = 300,
            display = { percent -> getString(R.string.percentage, percent.toString()) },
        ) { percent -> Prefs.imageZoom = percent },
        numberEntry(
            title = getString(R.string.pref_double_tap_time_interval),
            subtitle = getString(R.string.pref_double_tap_time_interval_summary),
            value = Prefs.doubleTapInterval,
            min = 0,
            max = 1000,
            display = { ms -> getString(R.string.pref_milliseconds, ms.toString()) },
        ) { ms -> Prefs.doubleTapInterval = ms },
    )

@Composable
internal fun SettingsPageFragment.einkEntries(): List<SettingsEntry> =
    listOf(
        switchEntry(
            title = getString(R.string.mmd_eink_flash_title),
            subtitle = getString(R.string.mmd_eink_flash_summary),
            get = { Prefs.isEinkRefreshEnabled },
            set = { Prefs.isEinkRefreshEnabled = it },
        ),
        numberEntry(
            title = getString(R.string.mmd_eink_interval_title),
            value = Prefs.einkRefreshInterval,
            min = 1,
            max = 100,
        ) { answers -> Prefs.einkRefreshInterval = answers },
    )

// ************************************** Collection ***************************************** //

@Composable
internal fun SettingsPageFragment.maintenanceEntries(): List<SettingsEntry> {
    // `emptyCards` has no Fragment overload, so it needs a Context receiver of its own
    val emptyCards = with(requireContext()) { TR.sentenceCase.emptyCards }
    return listOf(
        SettingsEntry.Action(TR.sentenceCase.checkDatabase) { home.confirmCheckDatabase() },
        SettingsEntry.Action(TR.sentenceCase.checkMediaAction) { home.mediaCheck() },
        SettingsEntry.Action(emptyCards) { home.showEmptyCardsDialog() },
    )
}

@Composable
internal fun SettingsPageFragment.backupEntries(): List<SettingsEntry>? {
    val limits = loadFromCollection { withCol { backend.getPreferences().backups } } ?: return null

    fun setLimits(block: BackupLimits.Builder.() -> Unit) =
        launchCatchingTask {
            withCol {
                val preferences = backend.getPreferences()
                val backups =
                    preferences.backups
                        .toBuilder()
                        .apply(block)
                        .build()
                backend.setPreferences(preferences.toBuilder().setBackups(backups).build())
            }
        }

    return listOf(
        SettingsEntry.Action(getString(R.string.menu_create_backup)) { home.createBackup() },
        SettingsEntry.Action(getString(R.string.backup_restore)) { home.confirmRestoreBackup() },
        SettingsEntry.Section(getString(R.string.pref_cat_advanced)),
        numberEntry(
            title = getString(R.string.pref__minutes_between_automatic_backups__title),
            value = limits.minimumIntervalMins,
            min = 5,
            max = 99999,
        ) { minutes -> setLimits { minimumIntervalMins = minutes } },
        numberEntry(
            title = getString(R.string.pref__daily_backups_to_keep__title),
            value = limits.daily,
            min = 0,
            max = 99999,
        ) { count -> setLimits { daily = count } },
        numberEntry(
            title = getString(R.string.pref__weekly_backups_to_keep__title),
            value = limits.weekly,
            min = 0,
            max = 99999,
        ) { count -> setLimits { weekly = count } },
        numberEntry(
            title = getString(R.string.pref__monthly_backups_to_keep__title),
            value = limits.monthly,
            min = 0,
            max = 99999,
        ) { count -> setLimits { monthly = count } },
    )
}

// ***************************************** Sync ******************************************** //

@Composable
internal fun SettingsPageFragment.syncEntries(): List<SettingsEntry> {
    val isLoggedIn = !Prefs.username.isNullOrEmpty()
    var isConfirmingOneWaySync by remember { mutableStateOf(false) }
    if (isConfirmingOneWaySync) {
        ConfirmPanel(
            title = getString(R.string.one_way_sync_title),
            body = TR.preferencesOnNextSyncForceChangesIn(),
            confirmLabel = getString(R.string.dialog_ok),
            dismissLabel = getString(R.string.dialog_cancel),
            onConfirm = {
                isConfirmingOneWaySync = false
                launchCatchingTask {
                    withCol { modSchema(check = false) }
                    home.messages.show(getString(R.string.one_way_sync_confirmation))
                }
            },
            onDismiss = { isConfirmingOneWaySync = false },
        )
    }
    return listOf(
        choiceEntry(
            title = getString(R.string.sync_fetch_missing_media),
            options = ShouldFetchMedia.entries,
            value = Prefs.shouldFetchMedia,
            label = { getString(it.labelRes) },
            onSelect = { Prefs.shouldFetchMedia = it },
        ),
        switchEntry(
            title = getString(R.string.automatic_sync_choice),
            subtitle = getString(R.string.automatic_sync_choice_summ),
            get = { Prefs.isAutoSyncEnabled },
            set = { Prefs.isAutoSyncEnabled = it },
        ),
        switchEntry(
            title = getString(R.string.sync_status_badge),
            subtitle = getString(R.string.sync_status_badge_summ),
            get = { Prefs.displaySyncStatus },
            set = { Prefs.displaySyncStatus = it },
        ),
        switchEntry(
            title = getString(R.string.metered_sync_title),
            subtitle = getString(R.string.metered_sync_summary),
            get = { Prefs.allowSyncOnMeteredConnections },
            set = { Prefs.allowSyncOnMeteredConnections = it },
        ),
        SettingsEntry.Section(getString(R.string.pref_cat_advanced)),
        numberEntry(
            title = TR.preferencesNetworkTimeout(),
            subtitle = getString(R.string.mmd_settings_seconds),
            value = Prefs.networkTimeoutSecs,
            min = 30,
            max = 99999,
        ) { seconds -> Prefs.networkTimeoutSecs = seconds },
        SettingsEntry.Action(
            title = getString(R.string.one_way_sync_title),
            // Nothing to force in one direction until there is an account to force it to
            subtitle = if (isLoggedIn) TR.preferencesOnNextSyncForceChangesIn() else getString(R.string.sync_account_summ_logged_out),
        ) { if (isLoggedIn) isConfirmingOneWaySync = true },
        SettingsEntry.Page(
            title = getString(R.string.custom_sync_server_title),
            subtitle = customSyncServerSummary(),
        ) { openPage(SettingsPage.CustomSyncServer) },
    )
}

/** The label of each media option, as `sync_media_entries` lists them. */
private val ShouldFetchMedia.labelRes: Int
    get() =
        when (this) {
            ShouldFetchMedia.ALWAYS -> R.string.sync_media_always
            ShouldFetchMedia.ONLY_UNMETERED -> R.string.sync_media_only_unmetered
            ShouldFetchMedia.NEVER -> R.string.sync_media_never
        }

private fun SettingsPageFragment.customSyncServerSummary(): String =
    Prefs.customSyncUri
        ?.takeIf { it.isNotEmpty() && Prefs.isCustomSyncEnabled }
        ?: getString(R.string.custom_sync_server_summary_none_of_the_two_servers_used)

@Composable
internal fun SettingsPageFragment.customSyncServerEntries(): List<SettingsEntry> =
    listOf(
        switchEntry(
            title = getString(R.string.custom_sync_server_title),
            get = { Prefs.isCustomSyncEnabled },
            set = {
                Prefs.isCustomSyncEnabled = it
                forgetCurrentSyncUri()
            },
        ),
        textEntry(
            title = getString(R.string.custom_sync_server_base_url_title),
            value = Prefs.customSyncUri,
            emptyValue = getString(R.string.mmd_settings_not_set),
            validate = { url ->
                if (url.isEmpty() ||
                    url.toHttpUrlOrNull() != null
                ) {
                    null
                } else {
                    getString(R.string.mmd_settings_invalid_url)
                }
            },
            onValue = { url ->
                Prefs.customSyncUri = url
                forgetCurrentSyncUri()
            },
        ),
        textEntry(
            title = getString(R.string.custom_sync_certificate_title),
            value = Prefs.customSyncCertificate,
            emptyValue = getString(R.string.mmd_settings_not_set),
            // An empty certificate unsets it in the backend, which is not an error
            validate = { pem ->
                if (pem.isEmpty() || CollectionManager.updateCustomCertificate(pem)) {
                    null
                } else {
                    getString(R.string.dialog_invalid_custom_certificate)
                }
            },
            onValue = { pem ->
                Prefs.customSyncCertificate = pem
                home.messages.show(getString(R.string.dialog_updated_custom_certificate))
            },
        ),
    )

/**
 * Drops the sync URL in use, so the next sync resolves it again. The old settings screen did this
 * from a `SharedPreferences` listener; here the two rows that matter say so themselves.
 */
private fun forgetCurrentSyncUri() {
    Prefs.currentSyncUri = null
}

// ****************************************** App ******************************************** //

@Composable
internal fun SettingsPageFragment.notificationEntries(): List<SettingsEntry> {
    val labels = resources.getStringArray(R.array.notification_minimum_cards_due_labels)
    val values = resources.getStringArray(R.array.notification_minimum_cards_due_values)
    return listOf(
        choiceEntry(
            title = getString(R.string.notification_pref_title),
            options = values.toList(),
            value = Prefs.notificationMinimumCardsDue ?: values.last(),
            label = { value ->
                val label = labels[values.indexOf(value).coerceAtLeast(0)]
                if (label.contains("%d")) label.format(value.toInt()) else label
            },
            onSelect = { value ->
                Prefs.notificationMinimumCardsDue = value
                onNotificationThresholdChanged(value)
            },
        ),
        switchEntry(
            title = getString(R.string.notification_minimum_cards_due_vibrate),
            get = { Prefs.notificationVibrate },
            set = { Prefs.notificationVibrate = it },
        ),
        switchEntry(
            title = getString(R.string.notification_minimum_cards_due_blink),
            get = { Prefs.notificationBlink },
            set = { Prefs.notificationBlink = it },
        ),
    )
}

@Composable
internal fun SettingsPageFragment.generalEntries(): List<SettingsEntry> {
    val sortedLanguages = remember { LanguageUtil.APP_LANGUAGES.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val systemTag = LanguageUtil.SYSTEM_LANGUAGE_TAG
    val tags = remember(sortedLanguages) { listOf(systemTag) + sortedLanguages.values }
    val names =
        remember(sortedLanguages) {
            mapOf(systemTag to getString(R.string.language_system)) +
                sortedLanguages.entries.associate { (name, tag) -> tag to name }
        }
    return listOf(
        choiceEntry(
            title = getString(R.string.language),
            options = tags,
            value = Prefs.language ?: systemTag,
            label = { tag -> names[tag] ?: tag },
            onSelect = { tag -> setLanguage(tag) },
        ),
        switchEntry(
            title = getString(R.string.exit_via_double_tap_back),
            subtitle = getString(R.string.exit_via_double_tap_back_summ),
            get = { Prefs.exitViaDoubleTapBack },
            set = { Prefs.exitViaDoubleTapBack = it },
        ),
    )
}

@Composable
internal fun SettingsPageFragment.advancedEntries(): List<SettingsEntry> =
    buildList {
        add(SettingsEntry.Section(getString(R.string.pref_cat_workarounds)))
        add(
            switchEntry(
                title = getString(R.string.software_render),
                subtitle = getString(R.string.software_render_summ),
                get = { Prefs.isSoftwareRenderEnabled },
                set = { Prefs.isSoftwareRenderEnabled = it },
            ),
        )
        add(
            switchEntry(
                title = getString(R.string.use_input_tag),
                subtitle = getString(R.string.use_input_tag_summ),
                get = { Prefs.isHtmlTypeAnswerEnabled },
                set = { Prefs.isHtmlTypeAnswerEnabled = it },
            ),
        )
        add(
            switchEntry(
                title = getString(R.string.type_in_answer_focus),
                subtitle = getString(R.string.type_in_answer_focus_summ),
                get = { Prefs.autoFocusTypeAnswer },
                set = { Prefs.autoFocusTypeAnswer = it },
            ),
        )
        add(
            switchEntry(
                title = getString(R.string.pref_fixed_port_title),
                subtitle = getString(R.string.pref_fixed_port_summary),
                get = { Prefs.useFixedPortInReviewer },
                set = { Prefs.useFixedPortInReviewer = it },
            ),
        )
        add(
            switchEntry(
                title = getString(R.string.allow_dangerous_js_api_title),
                subtitle = getString(R.string.allow_dangerous_js_api_summ),
                get = { Prefs.allowDangerousJsApi },
                set = { Prefs.allowDangerousJsApi = it },
            ),
        )
        if (BuildConfig.DEBUG) {
            add(
                SettingsEntry.Page(title = "MMD kit gallery") {
                    startActivity(SingleFragmentActivity.getIntent(requireContext(), MmdKitGalleryFragment::class))
                },
            )
        }
    }

// ************************************ Actions the rows run ********************************** //

/** Sets the app's language, as [com.ichi2.anki.preferences.GeneralSettingsFragment] did. */
private fun setLanguage(tag: String) {
    Prefs.language = tag
    LanguageUtil.setDefaultBackendLanguages(tag)
    runBlocking { CollectionManager.discardBackend() }
    val localeCode = tag.takeIf { it != LanguageUtil.SYSTEM_LANGUAGE_TAG }
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeCode))
}

/**
 * Schedules or cancels the due-cards notification for a new threshold, as
 * [com.ichi2.anki.preferences.NotificationsSettingsFragment] did.
 */
private fun SettingsPageFragment.onNotificationThresholdChanged(value: String) {
    val context = requireContext()
    if (value.toInt() < PENDING_NOTIFICATIONS_ONLY) {
        scheduleNotification(com.ichi2.anki.common.time.TimeManager.time, context)
        return
    }
    val intent =
        PendingIntentCompat.getBroadcast(
            context,
            0,
            Intent(context, NotificationService::class.java),
            0,
            false,
        ) ?: return
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(intent)
}

private const val PENDING_NOTIFICATIONS_ONLY = 1000000

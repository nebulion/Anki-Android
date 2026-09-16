// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>

// usage of 'this' in constructors when class is non-final - weak warning
// should be OK as this is only non-final for tests
@file:Suppress("LeakingThis")

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.SQLException
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.text.util.Linkify
import android.view.KeyEvent
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.collection.OpChanges
import anki.sync.SyncStatusResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.DBError
import com.ichi2.anki.InitialActivity.StartupFailure.DatabaseLocked
import com.ichi2.anki.InitialActivity.StartupFailure.DirectoryNotAccessible
import com.ichi2.anki.InitialActivity.StartupFailure.DiskFull
import com.ichi2.anki.InitialActivity.StartupFailure.FutureAnkidroidVersion
import com.ichi2.anki.InitialActivity.StartupFailure.SDCardNotMounted
import com.ichi2.anki.InitialActivity.StartupFailure.StorageUndecided
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.android.back.exitViaDoubleTapBackCallback
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.common.destinations.StatisticsDestination
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.deckpicker.DeckDeletionResult
import com.ichi2.anki.deckpicker.DeckListFragment
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.deckpicker.DeckPickerViewModel.StartupResponse
import com.ichi2.anki.deckpicker.EmptyCardsResult
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerBackupNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DeckPickerNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.EmptyCardsDialogFragment
import com.ichi2.anki.dialogs.FatalErrorDialog
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ApkgImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.CsvImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportViewModel
import com.ichi2.anki.dialogs.SchedulerUpgradeDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.SettingsPage
import com.ichi2.anki.settings.SettingsPageFragment
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.sync.launchCatchingRequiringOneWaySyncDiscardUndo
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.MmdTheme
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.Permissions
import com.ichi2.utils.VersionUtils
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.showDialogIfWebViewOutdated
import com.ichi2.utils.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Translations
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The entry point for AnkiDroid: the home screen.
 *
 * The deck list fills the screen; its header opens Statistics and the More page. Tapping a deck
 * studies it; a long press opens its deck page, where it is managed.
 *
 * Responsibilities:
 * * Setup/upgrades of the application: [handleStartup]
 * * Error handling [handleDbLocked]
 * * Controlling syncs, blocking the UI and displaying progress while syncing
 * * The collection-wide actions offered on the More tab: backups, import, 'check media' etc...
 *   * General handler for error/global dialogs (search for 'as DeckPicker')
 *   * Such as import: [ImportViewModel]
 */
open class DeckPicker :
    AnkiActivity(),
    SyncErrorDialogListener,
    OnRequestPermissionsResultCallback,
    ChangeManager.Subscriber,
    ImportColpkgListener,
    BaseSnackbarBuilderProvider,
    ApkgImportResultLauncherProvider,
    CsvImportResultLauncherProvider {
    val viewModel: DeckPickerViewModel by viewModels()

    private val importViewModel: ImportViewModel by viewModels()

    private lateinit var binding: ActivityHomescreenBinding

    /** Short messages ("3 cards deleted", "Updated to…") shown at the bottom of the home screen. */
    val messages = MessageHostState()

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = binding.messageBar
    }

    /** Whether media is syncing in the background: Sync then shows its progress instead of syncing again. */
    private var isMediaSyncRunning = false

    // flag keeping track of when the app has been paused
    var activityPaused = false
        private set

    /** `false` when [onCreate] stopped early on a failed start, so there is no UI to refresh. */
    private var isUiCreated = false

    @VisibleForTesting
    val dueTree: DeckNode?
        get() = viewModel.dueTree

    /**
     * Flag to indicate whether the activity will perform a sync in its onResume.
     * Since syncing closes the database, this flag allows us to avoid doing any
     * work in onResume that might use the database and go straight to syncing.
     */
    private var syncOnResume = false

    /**
     * Whether this instance restores a destroyed home screen rather than opening it. With "Don't keep
     * activities" on, that happens on every return; start-up work must not repeat.
     */
    private var isRecreated = false

    private val loginForSyncLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    syncOnResume = true
                }
            },
        )

    private val requestPathUpdateLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                // The collection path was inaccessible on startup so just close the activity and let user restart
                finish()
            },
        )

    private val apkgFileImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    lifecycleScope.launch {
                        withProgress(message = getString(R.string.import_preparing_file)) {
                            withContext(Dispatchers.IO) {
                                onSelectedPackageToImport(it.data!!)
                            }
                        }
                    }
                }
            },
        )

    private val csvImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    Timber.w("CSV import is not available in the MMD fork")
                }
            },
        )

    private val exitAndSyncBackCallback =
        object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                // can't use launchCatchingTask because any errors
                // would need to be shown in the UI
                lifecycleScope
                    .launch {
                        automaticSync(runInBackground = true)
                    }.invokeOnCompletion {
                        finish()
                    }
            }
        }

    /**
     * Back from a settings page returns to the deck list before it can exit the app. The exit callbacks
     * are added after the fragment manager's own, so they would otherwise run first.
     */
    private val closeSettingsBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                supportFragmentManager.popBackStack()
            }
        }

    private inner class DeckPickerActivityResultCallback(
        private val callback: (result: ActivityResult) -> Unit,
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result.resultCode == RESULT_MEDIA_EJECTED) {
                onSdCardNotMounted()
                return
            }
            callback(result)
        }
    }

    // stored for testing purposes
    @VisibleForTesting
    var createMenuJob: Job? = null

    init {
        ChangeManager.subscribe(this)
    }

    // ----------------------------------------------------------------------------
    // ANDROID ACTIVITY METHODS
    // ----------------------------------------------------------------------------

    /** Called when the activity is first created.  */
    @Throws(SQLException::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        binding = ActivityHomescreenBinding.inflate(layoutInflater)

        isRecreated = savedInstanceState != null
        // The extra stays on the activity's intent, so a rebuilt home screen would sync again
        if (!isRecreated && intent.hasExtra(INTENT_SYNC_FROM_LOGIN)) {
            Timber.d("launched from login: syncing")
            syncOnResume = true
            intent.removeExtra(INTENT_SYNC_FROM_LOGIN)
        }

        setViewBinding(binding)
        // TODO This method is run on every activity recreation, which can happen often.
        //  It seems that the original idea was for this to only run once, on app start.
        //  This method triggers backups, sync, and may re-show dialogs
        //  that may have been dismissed. Make this run only once?
        handleStartup()

        registerReceiver()

        onBackPressedDispatcher.addCallback(this, exitAndSyncBackCallback)
        onBackPressedDispatcher.addCallback(this, exitViaDoubleTapBackCallback())
        onBackPressedDispatcher.addCallback(this, closeSettingsBackCallback)

        setupContent(savedInstanceState)

        with(this) { showDialogIfWebViewOutdated() }

        // If a review reminder deserialization error has recently occurred
        // (ex. on app boot, when the app opened, etc.), inform the user via a dialog
        ReviewRemindersDatabase.checkDeserializationErrors(this)

        setupFlows()
        isUiCreated = true
    }

    private fun setupContent(savedInstanceState: Bundle?) {
        // The content starts below the status bar; the message strip below it clears the navigation
        // bar. The screens hosted here must not pad for the system bars again.
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeContainer) { view, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            WindowInsetsCompat
                .Builder(insets)
                .setInsets(systemBars() or displayCutout(), Insets.NONE)
                .build()
        }
        binding.messageBar.setContent {
            MmdTheme {
                Box(Modifier.navigationBarsPadding()) {
                    MessageHost(messages)
                }
            }
        }
        if (savedInstanceState == null) {
            supportFragmentManager.commit { replace(R.id.home_container, DeckListFragment()) }
        }
        supportFragmentManager.addOnBackStackChangedListener {
            closeSettingsBackCallback.isEnabled = supportFragmentManager.backStackEntryCount > 0
        }
        closeSettingsBackCallback.isEnabled = supportFragmentManager.backStackEntryCount > 0
    }

    /**
     * Opens a settings page over the deck list; back returns to the page before it, then to the
     * decks. Settings hold the collection's actions as well as its preferences, so this is also how
     * importing, exporting and maintenance are reached.
     */
    fun openSettings(page: SettingsPage = SettingsPage.Root) {
        Timber.i("DeckPicker:: opening settings page %s", page)
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.home_container, SettingsPageFragment.newInstance(page), SETTINGS_FRAGMENT_TAG)
            addToBackStack(SETTINGS_FRAGMENT_TAG)
        }
    }

    fun openStatistics() {
        Timber.i("DeckPicker:: Statistics selected")
        navigate(StatisticsDestination)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        fun onDeckDeleted(result: DeckDeletionResult) {
            messages.show(result.toHumanReadableString(), getString(R.string.undo), ::undo)
        }

        fun onCardsEmptied(result: EmptyCardsResult) {
            messages.show(result.toHumanReadableString(), getString(R.string.undo), ::undo)
        }

        fun onDeckCountsChanged(unit: Unit) {
            updateDeckList()
        }

        fun onDestinationChanged(destination: Destination) {
            startActivity(destination.toIntent(this))
        }

        fun onExportDeck(deckId: DeckId) {
            ExportDialogFragment.newInstance(deckId).show(supportFragmentManager, "exportOptions")
        }

        fun onPromptUserToUpdateScheduler(op: Unit) {
            SchedulerUpgradeDialog(
                activity = this,
                onUpgrade = {
                    launchCatchingRequiringOneWaySyncDiscardUndo {
                        this@DeckPicker.withProgress { withCol { sched.upgradeToV2() } }
                        showThemedToast(this@DeckPicker, TR.schedulingUpdateDone(), false)
                    }
                },
                onCancel = {
                    onBackPressedDispatcher.onBackPressed()
                },
            ).showDialog()
        }

        fun onDecksReloaded(param: Unit) {
            hideProgressBar()
        }

        fun onStartupResponse(response: StartupResponse) {
            Timber.d("onStartupResponse: %s", response)
            when (response) {
                is StartupResponse.Success -> {
                    // Set flowOfStartupResponse to null after handling so it isn't re-emitted on resume.
                    // Must stay here: clearing in ViewModel would break cold start (collector is only active at RESUMED).
                    viewModel.flowOfStartupResponse.value = null
                    showStartupScreensAndDialogs(sharedPrefs(), 0)
                }
                is StartupResponse.FatalError -> handleStartupFailure(response.failure)
            }
        }

        fun onError(errorMessage: String) {
            AlertDialog
                .Builder(this)
                .setTitle(R.string.vague_error)
                .setMessage(errorMessage)
                .show()
        }

        fun onMediaSyncWorkChanged(workInfos: List<WorkInfo>) {
            isMediaSyncRunning = workInfos.lastOrNull()?.state == WorkInfo.State.RUNNING
        }

        viewModel.deckDeletedNotification.launchCollectionInLifecycleScope(::onDeckDeleted)
        viewModel.emptyCardsNotification.launchCollectionInLifecycleScope(::onCardsEmptied)
        viewModel.flowOfDeckCountsChanged.launchCollectionInLifecycleScope(::onDeckCountsChanged)
        viewModel.flowOfDestination.launchCollectionInLifecycleScope(::onDestinationChanged)
        viewModel.flowOfNavigate.launchCollectionInLifecycleScope { navigate(it) }
        viewModel.flowOfExportDeck.launchCollectionInLifecycleScope(::onExportDeck)
        viewModel.onError.launchCollectionInLifecycleScope(::onError)
        viewModel.flowOfPromptUserToUpdateScheduler.launchCollectionInLifecycleScope(::onPromptUserToUpdateScheduler)
        viewModel.flowOfDecksReloaded.launchCollectionInLifecycleScope(::onDecksReloaded)
        viewModel.flowOfStartupResponse.filterNotNull().launchCollectionInLifecycleScope(::onStartupResponse)
        WorkManager
            .getInstance(this)
            .getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)
            .launchCollectionInLifecycleScope(::onMediaSyncWorkChanged)
        // navigation should be done on RESUMED
        importViewModel.importAddFlow.launchCollectionInLifecycleScope(Lifecycle.State.RESUMED, ::importAdd)
        importViewModel.importReplaceFlow.launchCollectionInLifecycleScope(Lifecycle.State.RESUMED, ::importReplace)
    }

    /**
     * @see DeckPickerViewModel.handleStartup
     */
    private fun handleStartup() {
        val context = appContext

        val environment: AnkiDroidEnvironment =
            object : AnkiDroidEnvironment {
                override val preferences: SharedPreferences
                    get() = context.sharedPrefs()

                override fun initializeAnkiDroidFolder(): Boolean = CollectionHelper.isCurrentAnkiDroidDirAccessible(context)
            }

        viewModel.handleStartup(environment = environment)
    }

    @VisibleForTesting
    fun handleStartupFailure(failure: StartupFailure) {
        when (failure) {
            is SDCardNotMounted -> {
                Timber.i("SD card not mounted")
                onSdCardNotMounted()
            }
            is DirectoryNotAccessible -> {
                Timber.i("AnkiDroid directory inaccessible")
                if (ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(this)) {
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL)
                } else {
                    showDirectoryNotAccessibleDialog()
                }
            }
            is FutureAnkidroidVersion -> {
                Timber.i("Displaying database versioning")
                showDatabaseErrorDialog(DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION)
            }
            is DatabaseLocked -> {
                Timber.i("Displaying database locked error")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
            }
            is StartupFailure.InitializationError -> FatalErrorDialog.build(this, failure).show()
            is DiskFull -> displayNoStorageError()
            is DBError -> displayDatabaseFailure(CustomExceptionData.fromException(failure.exception))
            is StorageUndecided -> {
                // unreachable: Undecided requires PREF_COLLECTION_PATH to be unset, which only
                // happens if ensureCollectionPathSet failed at startup; getStartupFailureType
                // then returns InitializationError (fatalError) before checking the decision
                // TODO: #19552 - replace with the storage setup flow
                Timber.w("storage setup flow (#19552) not implemented; showing load-failure options")
                CrashReportService.sendExceptionReport(
                    IllegalStateException("StorageUndecided reached without a startup failure"),
                    "DeckPicker::handleStartupFailure",
                )
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
            }
        }
    }

    private fun showDirectoryNotAccessibleDialog() {
        val contentView =
            TextView(this).apply {
                autoLinkMask = Linkify.WEB_URLS
                linksClickable = true
                text =
                    getString(
                        R.string.directory_inaccessible_info,
                        getString(R.string.link_full_storage_access),
                    )
            }
        AlertDialog.Builder(this).show {
            title(R.string.directory_inaccessible)
            customView(
                contentView,
                paddingTop = 16.dp.toPx(this@DeckPicker),
                paddingStart = 32.dp.toPx(this@DeckPicker),
                paddingEnd = 32.dp.toPx(this@DeckPicker),
            )
            positiveButton(R.string.open_settings) {
                requestPathUpdateLauncher.navigate(PreferencesDestination.Advanced)
            }
        }
    }

    private fun displayDatabaseFailure(exceptionData: CustomExceptionData? = null) {
        Timber.i("Displaying database failure")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, exceptionData)
    }

    private fun displayNoStorageError() {
        Timber.i("Displaying no storage error")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DISK_FULL)
    }

    // ----------------------------------------------------------------------------
    // DECKS TAB
    // ----------------------------------------------------------------------------

    /** Selects [deckId] and opens its deck page. */
    fun openDeck(deckId: DeckId) {
        Timber.i("DeckPicker:: Selected deck with id %d", deckId)
        launchCatchingTask {
            viewModel.selectDeck(deckId).join()
            navigate(StudyOptionsDestination)
        }
    }

    /**
     * Selects [deckId] and starts studying it straight away (a long press in the deck list).
     * A deck with nothing to study opens its deck page instead, which says why.
     */
    fun studyDeck(deckId: DeckId) {
        Timber.i("DeckPicker:: Study deck %d from the deck list", deckId)
        launchCatchingTask {
            viewModel.selectDeck(deckId).join()
            val hasCardsToStudy = withCol { sched.deckDueTree().find(deckId)?.hasCardsReadyToStudy() == true }
            if (hasCardsToStudy) {
                navigate(ReviewDeckDestination.CurrentDeck)
            } else {
                navigate(StudyOptionsDestination)
            }
        }
    }

    /** Sync, or show the progress of a media sync which is already running. */
    fun onSyncPressed() {
        Timber.i("DeckPicker:: Sync button pressed")
        if (isMediaSyncRunning) {
            launchCatchingTask { monitorMediaSync(this@DeckPicker) }
        } else {
            sync()
        }
    }

    fun undo() {
        Timber.i("DeckPicker:: Undo button pressed")
        launchCatchingTask {
            undoAndShowSnackbar()
        }
    }

    // ----------------------------------------------------------------------------
    // MORE TAB
    // ----------------------------------------------------------------------------

    /**
     * Displays a dialog for creating a new deck.
     *
     * @see CreateDeckDialog
     */
    fun showCreateDeckDialog() {
        val createDeckDialog =
            CreateDeckDialog(
                context = this@DeckPicker,
                title = TR.sentenceCase.createDeck,
                deckDialogType = CreateDeckDialog.DeckDialogType.DECK,
                parentId = null,
            )
        createDeckDialog.onNewDeckCreated = ::onDeckCreated
        createDeckDialog.showDialog()
    }

    /** Selects a deck created from the home screen, so its deck page and Study follow the new deck. */
    private fun onDeckCreated(deckId: DeckId) =
        launchCatchingTask {
            viewModel.selectDeck(deckId).join()
            updateDeckList()
            refreshMenuState()
        }

    fun showCreateFilteredDeckDialog() {
        startActivity(FilteredDeckOptionsFragment.getIntent(this))
    }

    fun exportCollection() {
        ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
    }

    fun confirmCheckDatabase() {
        Timber.i("DeckPicker:: Check database selected")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
    }

    fun showEmptyCardsDialog() {
        Timber.i("DeckPicker:: Empty cards selected")
        EmptyCardsDialogFragment().show(supportFragmentManager, EmptyCardsDialogFragment.TAG)
    }

    fun createBackup() {
        launchCatchingTask {
            withProgress(message = TR.profilesCreatingBackup()) {
                performBackupInBackground(true)
            }
            showThemedToast(this@DeckPicker, TR.profilesBackupCreated(), false)
        }
    }

    fun confirmRestoreBackup() {
        Timber.i("DeckPicker:: Restore from backup selected")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
    }

    fun openAccount() {
        startActivity(AccountActivity.getIntent(this))
    }

    private fun showMediaCheckDialog() {
        Timber.i("showing media check dialog")
        AlertDialog.Builder(this).show {
            title(text = TR.sentenceCase.checkMediaTitle)
            message(text = getString(R.string.check_media_warning))
            positiveButton(R.string.dialog_ok) {
                Timber.i("Starting media check")
                startActivity(MediaCheckFragment.getIntent(this@DeckPicker))
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    // ----------------------------------------------------------------------------
    // STATE
    // ----------------------------------------------------------------------------

    override fun onResume() {
        activityPaused = false
        // stop onResume() processing the message.
        // we need to process the message after `loadDeckCounts` is added in refreshState
        // As `loadDeckCounts` is cancelled in `migrate()`
        val message = dialogHandler.popMessage()
        super.onResume()
        if (isUiCreated) {
            refreshState()
        }
        message?.let { dialogHandler.sendStoredMessage(it) }
    }

    fun refreshState() {
        if (syncOnResume) {
            syncOnResume = false
            Timber.i("Performing Sync on Resume")
            Permissions.requestNotificationPermissionsForSyncing(this)
            sync()
        } else {
            updateDeckList()
            // studying leaves changes to send; the owner wants that sync quiet, unlike the one when
            // the app opens. At most one every AUTOMATIC_SYNC_MINIMAL_INTERVAL.
            launchCatchingTask { automaticSync(runInBackground = true, ignoreInterval = false) }
        }
        // Update sync status (if we've come back from a screen)
        refreshMenuState()
    }

    /** Recomputes the Decks header: whether Undo shows, and the sync badge. */
    private fun refreshMenuState() {
        createMenuJob = launchCatchingTask { viewModel.refreshMenuState() }
    }

    /** Callers written for the old toolbar menu use this to ask for a fresh undo label and sync badge. */
    override fun invalidateOptionsMenu() {
        super.invalidateOptionsMenu()
        if (isUiCreated) refreshMenuState()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        importColpkgListener?.let {
            if (it is DatabaseRestorationListener) {
                outState.getString("dbRestorationPath", it.newAnkiDroidDirectory.absolutePath)
            }
        }
        outState.putSerializable("mediaUsnOnConflict", mediaUsnOnConflict)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString("dbRestorationPath")?.let { path ->
            val path = File(path)
            CollectionHelper.ankiDroidDirectoryOverride = path
            importColpkgListener = DatabaseRestorationListener(this, path)
        }
        mediaUsnOnConflict = savedInstanceState.getSerializableCompat("mediaUsnOnConflict")
    }

    override fun onPause() {
        activityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        viewModel.loadDeckCounts?.cancel()
        super.onPause()
    }

    /**
     * Performs a sync if the conditions are met, e.g. user is logged in, there are changes,
     * and auto sync is enabled.
     * @param runInBackground run it in [SyncWorker] (quiet) instead of on screen with a progress bar
     * @param ignoreInterval sync even if the last one was recent; set when leaving the app
     * @return whether a sync was performed or not.
     */
    private suspend fun automaticSync(
        runInBackground: Boolean = false,
        ignoreInterval: Boolean = runInBackground,
    ): Boolean {
        /**
         * @return whether there are collection changes to be sync.
         *
         * It DOES NOT include if there are media to be synced.
         */
        suspend fun areThereChangesToSync(): Boolean {
            val auth = syncAuth() ?: return false
            val status =
                withContext(Dispatchers.IO) {
                    CollectionManager.getBackend().syncStatus(auth)
                }.required

            return when (status) {
                SyncStatusResponse.Required.NO_CHANGES,
                SyncStatusResponse.Required.UNRECOGNIZED,
                null,
                -> false
                SyncStatusResponse.Required.FULL_SYNC,
                SyncStatusResponse.Required.NORMAL_SYNC,
                -> true
            }
        }

        fun syncIntervalPassed(): Boolean =
            (TimeManager.time.intTimeMS() - Prefs.lastSyncTime) > AUTOMATIC_SYNC_MINIMAL_INTERVAL.inWholeMilliseconds

        when {
            !Prefs.isAutoSyncEnabled -> Timber.d("autoSync: not enabled")
            MeteredSyncPolicy.shouldBlock() -> Timber.d("autoSync: blocked by metered connection")
            !NetworkUtils.isOnline -> Timber.d("autoSync: offline")
            !ignoreInterval && !syncIntervalPassed() -> Timber.d("autoSync: interval not passed")
            !isLoggedIn() -> Timber.d("autoSync: not logged in")
            !areThereChangesToSync() -> {
                Timber.d("autoSync: no collection changes to sync. Syncing media if set")
                if (shouldFetchMedia()) {
                    val auth = syncAuth() ?: return false
                    SyncMediaWorker.start(this, auth)
                }
                setLastSyncTimeToNow()
            }
            else -> {
                if (runInBackground) {
                    Timber.i("autoSync: starting background")
                    val auth = syncAuth() ?: return false
                    SyncWorker.start(this, auth, shouldFetchMedia())
                } else {
                    Timber.i("autoSync: starting foreground")
                    sync()
                }
                return true
            }
        }
        return false
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_B -> {
                if (event.isShiftPressed && event.isCtrlPressed) {
                    // shortcut SHIFT + CTRL + B
                    Timber.i("Create backup from keypress")
                    createBackup()
                } else if (event.isCtrlPressed) {
                    // Shortcut: CTRL + B
                    Timber.i("show restore backup dialog from keypress")
                    confirmRestoreBackup()
                }
                return true
            }
            KeyEvent.KEYCODE_Y -> {
                Timber.i("Sync from keypress")
                sync()
                return true
            }
            KeyEvent.KEYCODE_S -> {
                Timber.i("Study from keypress")
                navigate(ReviewDeckDestination.CurrentDeck)
                return true
            }
            KeyEvent.KEYCODE_T -> {
                Timber.i("Open Statistics from keypress")
                openStatistics()
                return true
            }
            KeyEvent.KEYCODE_C -> {
                Timber.i("Check database from keypress")
                confirmCheckDatabase()
                return true
            }
            KeyEvent.KEYCODE_D -> {
                Timber.i("Create Deck from keypress")
                showCreateDeckDialog()
                return true
            }
            KeyEvent.KEYCODE_F -> {
                Timber.i("Create Filtered Deck from keypress")
                showCreateFilteredDeckDialog()
                return true
            }
            KeyEvent.KEYCODE_P -> {
                Timber.i("Open Settings from keypress")
                openSettings()
                return true
            }
            KeyEvent.KEYCODE_M -> {
                Timber.i("Check media from keypress")
                showMediaCheckDialog()
                return true
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    // Shortcut: CTRL + E
                    Timber.i("Show export dialog from keypress")
                    exportCollection()
                    return true
                }
            }
            KeyEvent.KEYCODE_I -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    // Shortcut: CTRL + Shift + I
                    Timber.i("Show import dialog from keypress")
                    showImportDialog()
                    return true
                }
            }
            else -> {}
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Perform the following tasks:
     * Automatic backup
     * Automatic sync
     */
    private fun onFinishedStartup() {
        if (isRecreated) {
            Timber.d("Home screen recreated: skipping the start-up sync and backup prompt")
            return
        }
        launchCatchingTask {
            if (!automaticSync()) {
                BackupPromptDialog.showIfAvailable(this@DeckPicker)
            }
        }
    }

    private fun showCollectionErrorDialog() {
        dialogHandler.sendMessage(CollectionLoadingErrorDialog().toMessage())
    }

    private fun showStartupScreensAndDialogs(
        preferences: SharedPreferences,
        skip: Int,
    ) {
        if (!BackupManager.enoughDiscSpace(CollectionHelper.getCurrentAnkiDroidDirectory(this))) {
            Timber.i("Not enough space to do backup")
            showDialogFragment(DeckPickerNoSpaceLeftDialog.newInstance())
        } else if (preferences.getBoolean("noSpaceLeft", false)) {
            Timber.i("No space left")
            showDialogFragment(DeckPickerBackupNoSpaceLeftDialog.newInstance())
            preferences.edit { remove("noSpaceLeft") }
        } else if (InitialActivity.performSetupFromFreshInstallOrClearedPreferences(preferences)) {
            onFinishedStartup()
        } else if (skip < 2 && !InitialActivity.isLatestVersion(preferences)) {
            Timber.i("AnkiDroid is being updated and a collection already exists.")

            // For upgrades, we check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            val current = VersionUtils.pkgVersionCode
            Timber.i("Current AnkiDroid version: %s", current)
            val previous: Long =
                if (preferences.contains(DeckPickerViewModel.UPGRADE_VERSION_KEY)) {
                    // Upgrading currently installed app
                    viewModel.getPreviousVersion(preferences, current)
                } else {
                    // Fresh install
                    current
                }
            preferences.edit { putLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, current) }

            val upgradedPreferences = InitialActivity.upgradePreferences(this, previous)
            // Integrity check loads asynchronously and then restart deck picker when finished
            if (upgradedPreferences) {
                Timber.i("Updated preferences with no integrity check - restarting activity")
                // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                // proceed
                ActivityCompat.recreate(this)
                return
            }

            // The fork has no changelog screen: record the upgrade and say so
            InitialActivity.setUpgradedToLatestVersion(preferences)
            messages.show(resources.getString(R.string.updated_version, VersionUtils.pkgVersionName))
            showStartupScreensAndDialogs(preferences, 2)
        } else {
            // This is the main call when there is nothing special required
            Timber.i("No startup screens required")
            onFinishedStartup()
        }
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     */
    override fun showSyncErrorDialog(dialogType: SyncErrorDialog.Type) {
        showSyncErrorDialog(dialogType, "")
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     * @param message text to show
     */
    override fun showSyncErrorDialog(
        dialogType: SyncErrorDialog.Type,
        message: String?,
    ) {
        val newFragment: AsyncDialogFragment = newInstance(dialogType, message)
        showAsyncDialogFragment(newFragment, NotificationChannel.SYNC)
    }

    // Callback method to submit error report
    fun sendErrorReport() {
        CrashReportService.sendExceptionReport(RuntimeException(), "DeckPicker.sendErrorReport")
    }

    // Callback method to handle repairing deck
    fun repairCollection() {
        Timber.i("Repairing the Collection")
        // TODO: doesn't work on null collection-only on non-openable(is this still relevant with withCol?)
        launchCatchingTask(resources.getString(R.string.deck_repair_error)) {
            Timber.d("doInBackgroundRepairCollection")
            val result =
                withProgress(resources.getString(R.string.backup_repair_deck_progress)) {
                    Timber.i("RepairCollection: Closing collection")
                    CollectionManager.ensureClosed()
                    val colFile =
                        CollectionManager.collectionPathInValidFolder().requireDiskBasedCollection().colDb
                    BackupManager.repairCollection(colFile)
                }
            if (!result) {
                showThemedToast(this@DeckPicker, resources.getString(R.string.deck_repair_error), true)
                showCollectionErrorDialog()
            }
        }
    }

    // Callback method to handle database integrity check
    override fun integrityCheck() {
        // #5852 - We were having issues with integrity checks where the users had run out of space.
        // display a dialog box if we don't have the space
        val status = CollectionIntegrityStorageCheck.createInstance(this)
        if (status.shouldWarnOnIntegrityCheck()) {
            Timber.d("Displaying File Size confirmation")
            AlertDialog.Builder(this).show {
                title(text = TR.sentenceCase.checkDatabase)
                message(text = status.getWarningDetails(this@DeckPicker))
                positiveButton(R.string.integrity_check_continue_anyway) {
                    performIntegrityCheck()
                }
                negativeButton(R.string.dialog_cancel)
            }
        } else {
            performIntegrityCheck()
        }
    }

    private fun performIntegrityCheck() {
        Timber.i("performIntegrityCheck()")
        handleDatabaseCheck()
    }

    override fun mediaCheck() {
        showMediaCheckDialog()
    }

    open fun handleDbLocked() {
        Timber.i("Displaying Database Locked")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
    }

    fun restoreFromBackup(path: String) {
        importColpkg(path)
    }

    // Helper function to check if there are any saved stacktraces
    fun hasErrorFiles(): Boolean {
        for (file in fileList()) {
            if (file.endsWith(".stacktrace")) {
                return true
            }
        }
        return false
    }

    /** In the conflict case, we need to store the USN received from the initial sync, and reuse
     it after the user has decided. */
    var mediaUsnOnConflict: Int? = null

    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     */
    override fun sync(conflict: ConflictResolution?) {
        val hkey = Prefs.hkey
        if (hkey.isNullOrEmpty()) {
            Timber.w("User not logged in")
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_USER_NOT_LOGGED_IN_SYNC)
            return
        }

        MeteredSyncPolicy.confirmThen(
            // After selecting 'upload/download', the user has already accepted the metered warning.
            skipPrompt = conflict != null,
            // TODO: why is this needed? 1f91b2868d
            onDialogShown = ::refreshState,
        ) {
            handleNewSync(conflict, shouldFetchMedia())
        }
    }

    override fun loginToSyncServer() {
        val intent = AccountActivity.getIntent(this, forResult = true)
        loginForSyncLauncher.launch(intent)
    }

    // Callback to import a file -- adding it to existing collection
    fun importAdd(importPath: String) {
        Timber.d("importAdd() for file %s", importPath)
        startActivity(AnkiPackageImporterFragment.getIntent(this, importPath))
    }

    // Callback to import a file -- replacing the existing collection
    fun importReplace(importPath: String) {
        Timber.d("importReplace() for file %s", importPath)
        importColpkg(importPath)
    }

    /**
     * Refresh the deck picker when the SD card is inserted.
     */
    override val broadcastsActions =
        super.broadcastsActions +
            mapOf(
                SdCardReceiver.MEDIA_MOUNT
                    to { ActivityCompat.recreate(this) },
            )

    /**
     * @see DeckPickerViewModel.updateDeckList
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun updateDeckList() {
        launchCatchingTask {
            withProgress { viewModel.updateDeckList().join() }
        }
    }

    override fun onAttachedToWindow() {
        window.setFormat(PixelFormat.RGBA_8888)
    }

    /**
     * The number of decks which are visible to the user (excluding decks if the parent is collapsed).
     * Not the total number of decks
     */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val visibleDeckCount: Int
        get() = viewModel.flowOfDeckList.value.data.size

    override val shortcuts
        get(): ShortcutGroup =
            ShortcutGroup(
                listOf(
                    shortcut("Y", R.string.pref_cat_sync),
                    shortcut("S", Translations::decksStudyDeck),
                    shortcut("T", R.string.open_statistics),
                    shortcut("C") { this.sentenceCase.checkDatabase },
                    shortcut("D") { sentenceCase.createDeck },
                    shortcut("F", R.string.new_dynamic_deck),
                    shortcut("P", R.string.open_settings),
                    shortcut("M") { this.sentenceCase.checkMediaAction },
                    shortcut("Ctrl+E", R.string.export_collection),
                    shortcut("Ctrl+Shift+I", Translations::actionsImport),
                ),
                R.string.deck_picker_group,
            )

    companion object {
        /**
         * Result codes from other activities
         */
        const val RESULT_MEDIA_EJECTED = 202

        /**
         * If passed into the intent, the user should have been logged in and DeckPicker
         * should sync immediately.
         *
         * This is for the 'download existing collection from AnkiWeb' use case
         */
        const val INTENT_SYNC_FROM_LOGIN = "syncFromLogin"

        /**
         * Available options performed by other activities (request codes for onActivityResult())
         */
        @VisibleForTesting
        const val REQUEST_STORAGE_PERMISSION = 0

        /**
         * Minimum delay between automatic syncs.
         *
         * Skips the automatic sync if this time has not elapsed.
         */
        private val AUTOMATIC_SYNC_MINIMAL_INTERVAL: Duration = 10.minutes

        private const val SETTINGS_FRAGMENT_TAG = "settings"

        /**
         * Builds an intent for [DeckPicker]
         */
        fun getIntent(
            context: Context,
            autoSync: Boolean = false,
        ) = Intent(context, DeckPicker::class.java).apply {
            if (autoSync) {
                putExtra(INTENT_SYNC_FROM_LOGIN, true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (intent.hasExtra(INTENT_SYNC_FROM_LOGIN)) {
            Timber.i("Sync requested from Login")
            this.syncOnResume = true
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        lifecycleScope.launch { viewModel.refreshMenuState() }
        if (changes.studyQueues && handler !== this && handler !== viewModel) {
            if (!activityPaused) {
                // No need to update while the activity is paused, because `onResume` calls `refreshState` that calls `updateDeckList`.
                updateDeckList()
            }
        }
    }

    override fun onImportColpkg(colpkgPath: String?) {
        launchCatchingTask {
            // as the current collection is closed before importing a new collection, make sure the
            // new collection is open before the code to update the DeckPicker ui runs
            withCol { }
            refreshMenuState()
            updateDeckList()
            importColpkgListener?.onImportColpkg(colpkgPath)
        }
    }

    override fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent> = apkgFileImportResultLauncher

    override fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent> = csvImportResultLauncher
}

class CollectionLoadingErrorDialog :
    DialogHandlerMessage(
        WhichDialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG,
        "CollectionLoadErrorDialog",
    ) {
    override fun handleAsyncMessage(activity: AnkiActivity) {
        // Collection could not be opened
        activity.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }

    override fun toMessage() = emptyMessage(this.what)
}

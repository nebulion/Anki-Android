// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import anki.collection.opChanges
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.deckpicker.DeckListFragment
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.HomeTab
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.defaultConfig
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.ext.addBasicNoteWithOp
import com.ichi2.testutils.revokeWritePermissions
import com.ichi2.testutils.withWritePermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@KotlinCleanup("SPMockBuilder")
@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var qualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<String> = listOf("normal")
    }

    @Before
    fun before() {
        RuntimeEnvironment.setQualifiers(qualifiers)
    }

    @Test
    fun `receiving opExecuted call doesn't crash if ViewModel is not yet initialized`() {
        // Instantiate DeckPicker directly to simulate a state where the object exists,
        // but Android lifecycle callback (onCreate) have not yet executed.
        DeckPicker()

        assertDoesNotThrow { ChangeManager.notifySubscribers(opChanges { studyQueues = true }, null) }
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom201to292() {
        val newVersion = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion.toLong()))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPickerViewModel.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.1")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion =
                    deckPicker.viewModel.getPreviousVersion(preferences, newVersion.toLong())
                assertEquals(0, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom202to292() {
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.UPGRADE_VERSION_KEY, 20900203))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPickerViewModel.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.2")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(40, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom281to291() {
        val prevVersion = 20800301 // 2.8.1
        val newVersion: Long = 20900301 // 2.9.1
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.UPGRADE_VERSION_KEY, 20900203))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion.toLong(), previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    fun getPreviousVersionUpgradeFrom291to292() {
        val prevVersion: Long = 20900301 // 2.9.1
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion, previousVersion)
            }
        }
        verify(editor, never()).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
    }

    @Test
    fun limitAppliedAfterReview() {
        val sched = col.sched
        val dconf = col.decks.defaultConfig
        assertNotNull(dconf)
        dconf.new.perDay = 10
        col.decks.save(dconf)
        for (i in 0..10) {
            addBasicNote("Which card is this ?", i.toString())
        }
        // This set a card as current card
        sched.card
        ensureCollectionLoadIsSynchronous()

        deckPicker {
            assertEquals(
                10,
                dueTree!!
                    .children[0]
                    .newCount
                    .toLong(),
            )
        }
    }

    @Test
    fun confirmDeckDeletionDeletesEmptyDeck() {
        val did = addDeck("Hello World")
        assertThat("Deck was added", col.decks.count(), equalTo(2))
        deckPicker {
            viewModel.deleteDeck(did).join()
            assertThat("deck was deleted", col.decks.count(), equalTo(1))
        }
    }

    @Test
    fun databaseLockedTest() {
        // don't call .onCreate
        val deckPicker = Robolectric.buildActivity(DeckPickerEx::class.java, Intent()).get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DatabaseLocked)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
        )
    }

    /** Until the storage setup flow exists (#19552), the user gets recovery options, not a crash */
    @Test
    fun `storage undecided shows load-failure options rather than crashing`() {
        // don't call .onCreate
        val deckPicker = Robolectric.buildActivity(DeckPickerEx::class.java, Intent()).get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.StorageUndecided)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED),
        )
    }

    @Test
    fun databaseLockedWithPermissionIntegrationTest() {
        try {
            BackendEmulatingOpenConflict.enable()
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "A specific dialog for a conflict should be shown",
                d.databaseErrorDialog,
                equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
            )
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    @Ignore("Flaky. Try to unflake now we're using coroutines")
    fun databaseLockedNoPermissionIntegrationTest() {
        // no permissions -> grant permissions -> db locked
        try {
            InitialActivityWithConflictTest.setupForDefault()
            BackendEmulatingOpenConflict.enable()

            deckPickerEx {
                // grant permissions
                InitialActivityWithConflictTest.setupForDatabaseConflict()
                onStoragePermissionGranted()
                assertThat(
                    "A specific dialog for a conflict should be shown",
                    databaseErrorDialog,
                    equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
                )
            }
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun doNotShowOptionsMenuWhenCollectionInaccessible() =
        withNullCollection {
            deckPicker {
                viewModel.refreshMenuState()
                assertThat(
                    "Options menu not displayed when collection is inaccessible",
                    viewModel.optionsMenuState,
                    equalTo(null),
                )
            }
        }

    @Test
    fun showOptionsMenuWhenCollectionAccessible() =
        withWritePermissions {
            deckPicker {
                viewModel.refreshMenuState()
                assertThat(
                    "Options menu displayed when collection is accessible",
                    viewModel.optionsMenuState,
                    notNullValue(),
                )
            }
        }

    @Test
    fun onResumeLoadCollectionFailureWithInaccessibleCollection() {
        revokeWritePermissions()
        withNullCollection {
            deckPicker {
                // Neither collection, not its models will be initialized without storage permission

                // assert: Lazy Collection initialization CollectionTask.LoadCollectionComplete fails
                assertFailsWith<Exception> { getColUnsafe }
            }
        }
    }

    @Test
    fun onResumeLoadCollectionSuccessWithAccessibleCollection() =
        withWritePermissions {
            deckPicker {
                assertThat(
                    "Collection initialization ensured by CollectionTask.LoadCollectionComplete",
                    getColUnsafe,
                    notNullValue(),
                )
                assertThat(
                    "Collection Models Loaded",
                    getColUnsafe.notetypes,
                    notNullValue(),
                )
            }
        }

    @Test
    fun `home starts on the Decks tab`() =
        deckPicker {
            advanceRobolectricLooper()
            assertThat(selectedTab, equalTo(HomeTab.DECKS))
            assertThat(
                supportFragmentManager.findFragmentById(R.id.home_tab_container),
                instanceOf(DeckListFragment::class.java),
            )
        }

    @Test
    fun `back from the More tab returns to Decks`() =
        deckPicker {
            selectTab(HomeTab.MORE)
            advanceRobolectricLooper()

            onBackPressedDispatcher.onBackPressed()

            assertThat("back selects Decks rather than exiting", selectedTab, equalTo(HomeTab.DECKS))
            assertThat("the app is still open", isFinishing, equalTo(false))
        }

    @Test
    fun `Alt number shortcuts select tabs`() =
        deckPicker {
            listOf(
                KeyEvent.KEYCODE_3 to HomeTab.MORE,
                KeyEvent.KEYCODE_1 to HomeTab.DECKS,
            ).forEach { (keyCode, tab) ->
                val handled = dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_ALT_ON))

                assertThat("Alt shortcut is handled", handled, equalTo(true))
                assertThat(selectedTab, equalTo(tab))
            }
        }

    @Test
    fun `tab shortcuts are registered in keyboard shortcut help`() =
        deckPicker {
            val tabShortcuts = shortcuts.shortcuts.filter { it.shortcut.startsWith("Alt+") }

            assertThat(
                tabShortcuts.associate { it.shortcut to it.label },
                equalTo(
                    mapOf(
                        "Alt+1" to "Decks",
                        "Alt+2" to "Statistics",
                        "Alt+3" to "More",
                    ),
                ),
            )
        }

    @Test
    fun `tapping a deck selects it and opens its deck page`() =
        deckPicker {
            val did = addDeck("Tapped")

            openDeck(did)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertThat("the tapped deck is selected", col.decks.selected(), equalTo(did))
            assertThat(
                "the deck page opens",
                shadowOf(this).nextStartedActivity.component!!.className,
                equalTo(SingleFragmentActivity::class.java.name),
            )
        }

    @Test
    fun `undo label is updated after undoableOp call`() =
        deckPicker {
            fun waitForMenu() = ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            fun undoLabel() = viewModel.optionsMenuState?.undoLabel.toString()

            // enqueue two actions, neither of which affect the study queues
            val note = addBasicNoteWithOp()
            note.updateOp { this.fields[0] = "baz" }

            waitForMenu()
            assertThat(undoLabel(), containsString("Update Note"))
            undoAndShowSnackbar()
            waitForMenu()
            assertThat(undoLabel(), containsString("Add Note"))
        }

    @Test
    fun `snackbars rest above the bottom bar`() =
        deckPicker {
            val snackbar = showSnackbar("test")

            assertThat(snackbar?.anchorView, equalTo(findViewById<View>(R.id.bottom_bar)))
        }

    @Test
    fun `startup response is cleared after handling so it does not re-run on resume`() =
        deckPicker {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertThat(
                "startup response cleared after handling so it does not re-run on resume",
                viewModel.flowOfStartupResponse.value,
                nullValue(),
            )
        }

    /** Regression test for [#20712](https://github.com/ankidroid/Anki-Android/issues/20712) */
    @Test
    fun `SQLiteDatabaseCorruptException in runCatching shows database error dialog`() =
        deckPickerEx {
            runCatching { throw SQLiteDatabaseCorruptException() }
            assertThat(databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED))
        }

    @Test
    fun `creating a deck selects it`() =
        deckPicker {
            showCreateDeckDialog()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.input = "My Deck"
            dialog.performPositiveClick()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val newDeckId = col.decks.byName("My Deck")!!.id
            assertThat(
                "the newly created deck should become the current deck",
                col.decks.current().id,
                equalTo(newDeckId),
            )
        }

    enum class CollectionType(
        val assetFile: String,
        private val deckName: String,
    ) {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16"),
        SCHEMA_V_250(
            "schema250.anki2",
            "ThisIsSchema250",
        ),
        ;

        fun isCollection(col: com.ichi2.anki.libanki.Collection): Boolean = col.decks.byName(deckName) != null
    }

    internal class DeckPickerEx : DeckPicker() {
        var databaseErrorDialog: DatabaseErrorDialogType? = null

        override fun showDatabaseErrorDialog(
            errorDialogType: DatabaseErrorDialogType,
            exceptionData: DatabaseErrorDialog.CustomExceptionData?,
        ) {
            databaseErrorDialog = errorDialogType
        }

        fun onStoragePermissionGranted() {
            onRequestPermissionsResult(
                REQUEST_STORAGE_PERMISSION,
                arrayOf(""),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
        }
    }
}

fun RobolectricTest.deckPicker(
    exposeTestData: Boolean = false,
    function: suspend DeckPicker.() -> Unit,
) = runTest {
    val deckPicker =
        startActivityNormallyOpenCollectionWithIntent(
            if (exposeTestData) DeckPickerTest.DeckPickerEx::class.java else DeckPicker::class.java,
            Intent(),
        )
    function(deckPicker)
}

/**
 * Runs [function], providing it access to test-only properties from [DeckPickerTest.DeckPickerEx]
 *
 * @see DeckPickerTest.DeckPickerEx
 */
internal fun RobolectricTest.deckPickerEx(function: suspend DeckPickerTest.DeckPickerEx.() -> Unit) =
    deckPicker(exposeTestData = true) {
        function(this as DeckPickerTest.DeckPickerEx)
    }

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertTrue

/**
 * Tests for [selectStoragePermissions]
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class) // no point in Application init if we don't use it
class SelectStoragePermissionsTest {
    @Config(sdk = [BEFORE_Q])
    @Test
    fun startupBeforeQ() {
        val expectedPermissions =
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.INTERNET,
            )

        // force a safe startup before Q
        assertThat(
            selectStoragePermissions(canManageExternalStorage = false).permissions.asIterable(),
            contains(*expectedPermissions),
        )
        assertThat(
            selectStoragePermissions(canManageExternalStorage = true).permissions.asIterable(),
            contains(*expectedPermissions),
        )
    }

    @Config(sdk = [Q])
    @Test
    fun startupQ() {
        assertThat(selectStoragePermissions(canManageExternalStorage = false), equalTo(StoragePermissionSet.LEGACY_ACCESS))
        assertThat(selectStoragePermissions(canManageExternalStorage = true), equalTo(StoragePermissionSet.LEGACY_ACCESS))
    }

    @SuppressLint("InlinedApi")
    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `Android 11 - After upgrade from AnkiDroid 2 15 (with MANAGE_EXTERNAL_STORAGE)`() {
        // after an upgrade, all we need is READ/WRITE. Once we reinstall, we need MANAGE_EXTERNAL_STORAGE
        val expectedPermissions =
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.INTERNET,
            )

        selectStoragePermissions(
            canManageExternalStorage = true,
            currentFolderIsAccessibleAndLegacy = true,
        ).let {
            assertThat(
                it.permissions.asIterable(),
                contains(*expectedPermissions),
            )
        }
    }

    @SuppressLint("InlinedApi")
    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `Android 11 - After reinstall (with MANAGE_EXTERNAL_STORAGE)`() {
        val permissions =
            selectStoragePermissions(
                canManageExternalStorage = true,
                currentFolderIsAccessibleAndLegacy = false,
            )

        assertTrue(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE in permissions.permissions)
    }

    @Config(sdk = [R_OR_AFTER])
    @Test
    fun startupAfterQWithoutManageExternalStorage() {
        assertThat(
            selectStoragePermissions(canManageExternalStorage = false),
            equalTo(StoragePermissionSet.APP_PRIVATE),
        )
    }

    /*
     * MMD fork: the fork is installed beside AnkiDroid 2.24.1, whose real collection lives in the
     * public ~/AnkiDroid folder. The fork must never select public storage, whatever the device
     * or manifest allows, so two apps can never open the same collection.
     */

    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `fork - app-private storage is selected while no collection path is set`() {
        context.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            assertThat(selectStoragePermissions(context), equalTo(StoragePermissionSet.APP_PRIVATE))
            assertThat(selectAnkiDroidFolder(context), equalTo(AnkiDroidFolder.APP_PRIVATE))
        }
    }

    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `fork - a public collection path never selects public storage`() {
        context.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, "/storage/emulated/0/AnkiDroid")
        }
        withManageExternalStorageInManifest {
            assertThat(selectStoragePermissions(context), equalTo(StoragePermissionSet.APP_PRIVATE))
            assertThat(selectAnkiDroidFolder(context), equalTo(AnkiDroidFolder.APP_PRIVATE))
        }
    }

    @Config(sdk = [BEFORE_Q])
    @Test
    fun `fork - legacy devices also use app-private storage`() {
        context.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        assertThat(selectStoragePermissions(context), equalTo(StoragePermissionSet.APP_PRIVATE))
    }

    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `fork - the default directory is never the public AnkiDroid folder`() {
        context.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            assertThat(
                getDefaultAnkiDroidDirectory(context),
                not(equalTo(File(Environment.getExternalStorageDirectory(), "AnkiDroid"))),
            )
        }
    }

    @Config(sdk = [R_OR_AFTER])
    @Test // #13574: app-private storage can be accessed without storage permissions
    fun `app-private collection path requires no storage permissions`() {
        context.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, File(context.filesDir, "AnkiDroid").path)
        }
        withManageExternalStorageInManifest {
            assertThat(selectStoragePermissions(context), equalTo(StoragePermissionSet.APP_PRIVATE))
        }
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /**
     * Helper for [com.ichi2.anki.selectStoragePermissions], making `currentFolderIsAccessibleAndLegacy` optional
     */
    private fun selectStoragePermissions(
        canManageExternalStorage: Boolean,
        currentFolderIsAccessibleAndLegacy: Boolean = false,
    ): StoragePermissionSet =
        com.ichi2.anki.selectStoragePermissions(
            canManageExternalStorage = canManageExternalStorage,
            currentFolderIsAccessibleAndLegacy = currentFolderIsAccessibleAndLegacy,
        )

    companion object {
        const val BEFORE_Q = Build.VERSION_CODES.Q - 1
        const val Q = Build.VERSION_CODES.Q
        const val R_OR_AFTER = Build.VERSION_CODES.R
    }
}

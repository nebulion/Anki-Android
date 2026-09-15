// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.ui.windows.permissions

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreenForPermission

/**
 * Base class for a permissions page shown on the [PermissionsBottomSheet]
 */
abstract class PermissionsFragment(
    @LayoutRes contentLayoutId: Int,
) : Fragment(contentLayoutId) {
    /**
     * All the [PermissionsItem]s in the fragment.
     * Must be called ONLY AFTER [onCreateView]
     */
    val permissionsItems: List<PermissionsItem>
        by lazy { view?.allViews?.filterIsInstance<PermissionsItem>()?.toList() ?: emptyList() }

    protected fun hasAllPermissions() = permissionsItems.all { it.areGranted }

    override fun onResume() {
        super.onResume()
        permissionsItems.forEach { it.updateSwitchCheckedStatus() }
        setFragmentResult(
            PERMISSIONS_FRAGMENT_RESULT_KEY,
            Bundle().apply { putBoolean(HAS_ALL_PERMISSIONS_KEY, hasAllPermissions()) },
        )
    }

    /**
     * If these permissions are already granted, open the OS settings to allow the user to disable them, as
     * it is impossible to programmatically revoke a permission. If the permissions have not been granted,
     * execute the callback.
     */
    protected fun PermissionsItem.revokeIfGrantedOnClickElse(callback: () -> Unit) {
        setOnPermissionsRequested { areAlreadyGranted ->
            if (areAlreadyGranted) {
                showToastAndOpenAppSettingsScreenForPermission(permissions.singleOrNull(), R.string.revoke_permissions)
            } else {
                callback()
            }
        }
    }

    companion object {
        const val PERMISSIONS_FRAGMENT_RESULT_KEY = "PERMISSION_FRAGMENT_RESULT"
        const val HAS_ALL_PERMISSIONS_KEY = "HAS_ALL_PERMISSIONS"
    }
}

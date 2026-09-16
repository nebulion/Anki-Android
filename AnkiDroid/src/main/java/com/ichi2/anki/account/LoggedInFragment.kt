// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import timber.log.Timber

/** The AnkiWeb account once it is signed in, as [LoggedInScreenMMD]. */
class LoggedInFragment : ComposeHostFragment() {
    private val viewModel: LoggedInViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        var isConfirmingLogOut by rememberSaveable { mutableStateOf(false) }
        if (isConfirmingLogOut) {
            // logging out drops the key and forces the media to resync, so it asks first (P5)
            ConfirmPanel(
                title = TR.sentenceCase.logOut,
                body = null,
                confirmLabel = TR.sentenceCase.logOut,
                dismissLabel = getString(R.string.dialog_cancel),
                onConfirm = {
                    isConfirmingLogOut = false
                    logOut()
                },
                onDismiss = { isConfirmingLogOut = false },
            )
        }
        LoggedInScreenMMD(
            username = Prefs.username.orEmpty(),
            onLogOut = { isConfirmingLogOut = true },
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        )
    }

    private fun logOut() {
        Timber.i("Logging out")
        viewModel.onLogout()
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.commit {
            replace(R.id.fragment_container, LoginFragment())
        }
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
}

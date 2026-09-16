// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.account

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.account.AccountActivity.Companion.START_FROM_DECKPICKER
import com.ichi2.anki.getEndpoint
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.utils.Permissions
import timber.log.Timber

/**
 * Logging in to AnkiWeb.
 *
 * The screen is [LoginScreenMMD]; [LoginViewModel] still does the signing in, so what the backend
 * is asked and what is stored are unchanged.
 */
class LoginFragment : ComposeHostFragment() {
    private val viewModel: LoginViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var isLoggingIn by rememberSaveable { mutableStateOf(false) }
        var loginError by rememberSaveable { mutableStateOf<String?>(null) }
        var isAskingToSync by rememberSaveable { mutableStateOf(false) }
        // whether the fields are filled in, and which of them is empty, stay in the view model
        val canLogIn by viewModel.loginButtonEnabled.collectAsState()
        val usernameError by viewModel.userNameError.collectAsState()
        val passwordError by viewModel.passwordError.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Success -> {
                        Timber.i("Login Successful")
                        isLoggingIn = false
                        loginError = null
                        // the sync prompt and pressing sync both want a sync as soon as this returns
                        if (arguments?.getBoolean(START_FROM_DECKPICKER) == true) {
                            requireActivity().setResult(RESULT_OK)
                            requireActivity().finish()
                        } else {
                            isAskingToSync = true
                        }
                    }
                    is LoginState.Error -> {
                        isLoggingIn = false
                        // the backend's message may name the account, so it is shown, not logged
                        loginError = state.exception.message
                    }
                    is LoginState.Idle -> {}
                }
            }
        }

        if (isAskingToSync) {
            ConfirmPanel(
                title = getString(R.string.login_successful),
                body = getString(R.string.sync_now),
                confirmLabel = getString(R.string.button_sync),
                dismissLabel = getString(R.string.dialog_continue),
                onConfirm = {
                    isAskingToSync = false
                    openDeckPickerAndSync()
                },
                onDismiss = {
                    isAskingToSync = false
                    showLoggedInView()
                },
            )
        }

        LoginScreenMMD(
            username = username,
            password = password,
            usernameError = usernameError?.toHumanReadableString(requireContext()),
            passwordError = passwordError?.toHumanReadableString(requireContext()),
            loginError = loginError,
            canLogIn = canLogIn,
            isLoggingIn = isLoggingIn,
            onUsernameChange = {
                username = it
                loginError = null
                viewModel.onTextChanged(username, password)
            },
            onPasswordChange = {
                password = it
                loginError = null
                viewModel.onTextChanged(username, password)
            },
            onLogIn = {
                isLoggingIn = true
                loginError = null
                viewModel.handleLogin(username.trim(), password, getEndpoint())
            },
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        )
    }

    /** @see LoggedInFragment */
    private fun showLoggedInView() {
        Timber.i("Showing LoggedIn view")
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragmentManager.commit {
            replace(R.id.fragment_container, LoggedInFragment())
        }
        Permissions.requestNotificationPermissionsForSyncing(requireActivity())
    }

    /** @see DeckPicker.onNewIntent */
    private fun openDeckPickerAndSync() {
        Timber.i("Opening Deck Picker for Sync")
        val intent = DeckPicker.getIntent(requireContext(), autoSync = true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }
}

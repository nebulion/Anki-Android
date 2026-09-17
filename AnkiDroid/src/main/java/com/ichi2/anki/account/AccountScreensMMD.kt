// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.panelTextFieldColors
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * Logging in to AnkiWeb: two fields and a button.
 *
 * The Kompakt has no browser, so the "sign up", "reset password" and "lost email" links the phone
 * cannot open are gone; an account is made on another device. There is no logo either: a decorative
 * image is one more thing to repaint on a screen that already has a keyboard over it.
 *
 * While signing in, the button is replaced by a line of text rather than a spinner.
 */
@Composable
fun LoginScreenMMD(
    username: String,
    password: String,
    usernameError: String?,
    passwordError: String?,
    loginError: String?,
    canLogIn: Boolean,
    isLoggingIn: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogIn: () -> Unit,
    onBack: () -> Unit,
) {
    AccountScreen(onBack = onBack) {
        TextFieldMMD(
            value = username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { TextMMD(text = stringResource(R.string.username)) },
            singleLine = true,
            enabled = !isLoggingIn,
            isError = usernameError != null,
            supportingText = usernameError?.let { message -> { TextMMD(text = message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            colors = panelTextFieldColors(),
        )
        TextFieldMMD(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { TextMMD(text = stringResource(R.string.password)) },
            singleLine = true,
            enabled = !isLoggingIn,
            isError = passwordError != null,
            supportingText = passwordError?.let { message -> { TextMMD(text = message) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canLogIn) onLogIn() }),
            colors = panelTextFieldColors(),
        )
        if (loginError != null) {
            TextMMD(text = loginError, style = MaterialTheme.typography.bodyMedium)
        }
        if (isLoggingIn) {
            TextMMD(text = stringResource(R.string.sign_in), style = MaterialTheme.typography.bodyLarge)
        } else if (canLogIn) {
            val logIn = with(LocalContext.current) { TR.sentenceCase.logIn }
            PanelPrimaryAction(label = logIn, onClick = onLogIn, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The account once it is signed in: who is signed in, and the way out. */
@Composable
fun LoggedInScreenMMD(
    username: String,
    onLogOut: () -> Unit,
    onBack: () -> Unit,
) {
    AccountScreen(onBack = onBack) {
        TextMMD(text = stringResource(R.string.logged_as), style = MaterialTheme.typography.bodyMedium)
        TextMMD(
            text = username,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        val logOut = with(LocalContext.current) { TR.sentenceCase.logOut }
        PanelPrimaryAction(label = logOut, onClick = onLogOut, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * What both account screens share: the header, and a form that scrolls when the keyboard covers it.
 *
 * [AccountActivity] is a subclass of `SingleFragmentActivity`, which pads only its own content, so
 * these screens keep themselves clear of the system bars.
 */
@Composable
private fun AccountScreen(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = with(LocalContext.current) { TR.sentenceCase.ankiWebAccount },
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = onBack,
                )
            },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = RowDefaults.EdgePadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

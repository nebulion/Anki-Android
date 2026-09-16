/*
 * Copyright (c) 2022 Ali Ahnaf <aliahnaf327@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see http://www.gnu.org/licenses/>.
 *
 */

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.account.LoginError
import com.ichi2.anki.account.LoginViewModel
import com.ichi2.anki.settings.Prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The login screen offers its button only once both fields are filled in. The screen itself is
 * Compose (`LoginScreenMMD`) and reads this state, so the rule is asserted where it lives.
 */
@RunWith(AndroidJUnit4::class)
class MyAccountTest : RobolectricTest() {
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Prefs.username = ""
        Prefs.hkey = ""
        viewModel = LoginViewModel()
    }

    @Test
    fun testLoginEmailPasswordProvided() {
        viewModel.onTextChanged("random.email@example.com", "randomStrongPassword")

        assertTrue(viewModel.loginButtonEnabled.value)
    }

    @Test
    fun testLoginFailsNoEmailProvided() {
        viewModel.onTextChanged("", "randomStrongPassword")

        assertFalse(viewModel.loginButtonEnabled.value)
    }

    @Test
    fun testLoginFailsNoPasswordProvided() {
        viewModel.onTextChanged("random.email@example.com", "")

        assertFalse(viewModel.loginButtonEnabled.value)
    }

    @Test
    fun `leaving a field empty names it`() {
        viewModel.onUserNameFocusChange(hasFocus = false, userName = "")
        viewModel.onPasswordFocusChange(hasFocus = false, password = "")

        assertEquals(LoginError.EMPTY_USERNAME, viewModel.userNameError.value)
        assertEquals(LoginError.EMPTY_PASSWORD, viewModel.passwordError.value)
    }
}

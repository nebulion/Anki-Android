/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.testutils.HamcrestUtils
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlsSettingsFragmentTest : RobolectricTest() {
    @Test
    fun `XML keys match the Enum keys`() {
        for (screen in ControlPreferenceScreen.entries) {
            val xmlKeys =
                PreferenceTestUtils.getKeysFromXml(targetContext, screen.xmlRes, excludeCategories = true).toMutableList().apply {
                    remove("binding_STATISTICS")
                }
            val enumKeys = screen.getActions().map { it.preferenceKey } - menuOnlyActionKeys

            assertThat(xmlKeys, HamcrestUtils.containsInAnyOrder(enumKeys))
        }
    }

    companion object {
        /**
         * Study-screen menu entries that have no key binding in the controls screen: the submenu
         * parents, the flag setters (bindings toggle flags instead), deck options and reset progress.
         */
        private val menuOnlyActionKeys =
            listOf(
                ViewerAction.FLAG_MENU,
                ViewerAction.BURY_MENU,
                ViewerAction.SUSPEND_MENU,
                ViewerAction.FLAG_RED,
                ViewerAction.FLAG_ORANGE,
                ViewerAction.FLAG_GREEN,
                ViewerAction.FLAG_BLUE,
                ViewerAction.FLAG_PINK,
                ViewerAction.FLAG_TURQUOISE,
                ViewerAction.FLAG_PURPLE,
                ViewerAction.DECK_OPTIONS,
                ViewerAction.RESET_PROGRESS,
            ).map { it.preferenceKey }
    }
}

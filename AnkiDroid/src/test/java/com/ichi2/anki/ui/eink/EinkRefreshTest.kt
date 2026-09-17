// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.eink

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** The screen flashes once every so many taps and swipes, and never while flashing is off. */
@RunWith(AndroidJUnit4::class)
class EinkRefreshTest : RobolectricTest() {
    @Test
    fun `every nth action flashes`() {
        Prefs.isEinkRefreshEnabled = true
        Prefs.einkRefreshActions = 3
        EinkRefresh.flashNow(null) // restarts the count

        val flashes = (1..9).map { EinkRefresh.countAction() }

        assertEquals(listOf(false, false, true, false, false, true, false, false, true), flashes)
    }

    @Test
    fun `nothing flashes while turned off`() {
        Prefs.isEinkRefreshEnabled = false
        Prefs.einkRefreshActions = 1

        assertEquals(List(5) { false }, (1..5).map { EinkRefresh.countAction() })
    }
}

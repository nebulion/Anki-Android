// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutils

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.android.Animations
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class EmptyApplicationTest {
    @Test
    fun `animations are off even when the system allows them`() {
        val context = getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

        assertThat(Animations.areAnimationsEnabled(context), equalTo(false))
    }
}

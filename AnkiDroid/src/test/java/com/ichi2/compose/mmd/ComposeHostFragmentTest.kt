// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.launchFragmentInContainer
import com.mudita.mmd.components.text.TextMMD
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ComposeHostFragmentTest : RobolectricTest() {
    /**
     * Inside `ComposeView(…).apply { setContent { … } }`, an unqualified `Content()` binds to
     * `ComposeView.Content()`, which invokes itself: a StackOverflowError on the Kompakt.
     */
    @Test
    fun `composes the subclass content without recursing`() {
        CountingFragment.composed = 0

        launchFragmentInContainer<CountingFragment>()
            .onFragment { fragment ->
                shadowOf(Looper.getMainLooper()).idle()
                assertThat((fragment.requireView() as ComposeView).hasComposition, equalTo(true))
            }.close()

        assertThat(CountingFragment.composed, greaterThan(0))
    }
}

class CountingFragment : ComposeHostFragment() {
    @Composable
    override fun ScreenContent() {
        composed++
        TextMMD(text = "content")
    }

    companion object {
        var composed = 0
    }
}

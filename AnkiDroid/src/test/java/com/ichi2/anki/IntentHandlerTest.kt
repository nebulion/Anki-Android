// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.IntentHandler.Companion.getLaunchType
import com.ichi2.anki.IntentHandler.Companion.getReviewDeckIntent
import com.ichi2.anki.IntentHandler.LaunchType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class IntentHandlerTest {
    @Test
    fun `COPY_DEBUG_INFO without clip_data does not crash`() {
        val intent = Intent("com.ichi2.anki.COPY_DEBUG_INFO")

        assertDoesNotThrow {
            Robolectric.buildActivity(IntentHandler::class.java, intent).create()
        }
    }

    // COULD_BE_BETTER: We're testing class internals here, would like to see these tests be replaced with
    // higher-level tests at a later date when we better extract dependencies
    @Test
    fun viewIntentReturnsView() {
        var intent = Intent(Intent.ACTION_VIEW, "content://invalid".toUri())
        var expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.FILE_IMPORT))

        intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "content://invalid".toUri())
        expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.FILE_IMPORT))
    }

    @Test
    fun syncIntentReturnsSync() {
        val intent = Intent("com.ichi2.anki.DO_SYNC")

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.SYNC))
    }

    @Test
    fun reviewIntentReturnsReview() {
        val intent = getReviewDeckIntent(Mockito.mock(Context::class.java), 1)

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.REVIEW))
    }

    @Test
    fun mainIntentStartsApp() {
        val intent = Intent(Intent.ACTION_MAIN)

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.DEFAULT_START_APP_IF_NEW))
    }

    @Test
    fun `shared plain text starts the app`() {
        // the MMD fork has no note editor: shared text without a file does not import or add a note
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Some shared text content")
            }

        assertThat(getLaunchType(intent), equalTo(LaunchType.DEFAULT_START_APP_IF_NEW))
    }

    @Test
    fun viewWithNoDataPerformsDefaultAction() {
        // #6312 - Smart Launcher double-tap launches us with this. No data at all in the intent
        // so we can only perform the default action
        val intent = Intent(Intent.ACTION_VIEW)

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.DEFAULT_START_APP_IF_NEW))
    }
}

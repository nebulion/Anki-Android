// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.ui.windows.reviewer

import androidx.test.core.app.ActivityScenario
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.ext.clear
import org.junit.After
import org.junit.Test

class StudyScreenScreenshotTest : ScreenshotTest() {
    @After
    override fun tearDown() {
        super.tearDown()
        Prefs.clear()
    }

    @Test
    fun captureScreenshot(
        @TestParameter showAnswerButtons: Boolean,
    ) {
        Prefs.showAnswerButtons = showAnswerButtons

        ActivityScenario
            .launch<CardViewerActivity>(
                ReviewerFragment.getIntent(targetContext),
            ).use { scenario ->
                scenario.onActivity {
                    captureScreen("bttns=$showAnswerButtons")
                }
            }
    }
}

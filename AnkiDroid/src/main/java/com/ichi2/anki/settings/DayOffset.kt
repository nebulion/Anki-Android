// SPDX-License-Identifier: GPL-3.0-or-later
// Moved from ReviewingSettingsFragment.kt (Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>)

package com.ichi2.anki.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
import anki.config.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.services.BootService.Companion.scheduleNotification
import com.ichi2.widget.DayRolloverAlarm
import timber.log.Timber

/** Sets the hour that the collection rolls over to the next day  */
@VisibleForTesting
@NeedsTest("ensure Start of Next Day is handled by the scheduler")
suspend fun setDayOffset(
    context: Context,
    hours: Int,
) {
    val prefs = withCol { getPreferences() }
    val newPrefs = prefs.copy { scheduling = prefs.scheduling.copy { rollover = hours } }

    undoableOp {
        setPreferences(newPrefs)
    }
    if (!Prefs.newReviewRemindersEnabled) scheduleNotification(TimeManager.time, context)
    DayRolloverAlarm.scheduleNext(context)
    Timber.i("set day offset: '%d'", hours)
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.content.Context
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.common.utils.ext.allDecksCounts
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The due-cards count behind the legacy 'cards due' notification.
 *
 * The MMD fork has no home-screen widgets; the name and storage (`smallWidgetStatus` in
 * [com.ichi2.anki.MetaDB]) are kept so the notification keeps working unchanged.
 */
object WidgetStatus {
    private var updateJob: Job? = null

    /** Refreshes the stored due count and reschedules the legacy notification, if it is enabled. */
    fun updateInBackground(context: Context) {
        if (Prefs.newReviewRemindersEnabled) {
            Timber.d("WidgetStatus.update(): new review reminders do not use the stored status")
            return
        }
        val notificationEnabled =
            context
                .sharedPrefs()
                .getString(context.getString(R.string.pref_notifications_minimum_cards_due_key), "1000001")!!
                .toInt() < 1000000
        val canExecuteTask = updateJob == null || updateJob?.isActive == false
        if (notificationEnabled && canExecuteTask) {
            Timber.d("WidgetStatus.update(): updating")
            updateJob = launchUpdateJob()
        } else {
            Timber.d("WidgetStatus.update(): already running or not enabled")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun launchUpdateJob(): Job =
        GlobalScope.launch {
            try {
                updateStatus()
                Timber.v("launchUpdateJob completed")
            } catch (exc: java.lang.Exception) {
                Timber.w(exc, "failure in widget status update")
            }
        }

    private suspend fun updateStatus() {
        if (!SdCard.isMounted) {
            Timber.w("updateStatus failed: no SD Card")
            return
        }
        widgetRepository.storeSmallWidgetStatus(queryStatus())
        widgetNotificationScheduler.scheduleNotification()
    }

    fun fetchDue(): Int = widgetRepository.dueCardsCount()

    private suspend fun queryStatus(): SmallWidgetStatus =
        withCol {
            val total = sched.allDecksCounts()
            val eta = sched.eta(total, false)
            SmallWidgetStatus(total.count(), eta)
        }
}

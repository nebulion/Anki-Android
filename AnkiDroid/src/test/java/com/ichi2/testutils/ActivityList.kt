// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.CheckResult
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.IntentHandler.Companion.getReviewDeckIntent
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.ActivityList.ActivityLaunchParam.Companion.get
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.util.function.Function

object ActivityList {
    // TODO: This needs a test to ensure that all activities are valid with the given intents
    // Otherwise, ActivityStartupUnderBackup and other classes could be flaky
    @CheckResult
    fun allActivitiesAndIntents(): List<ActivityLaunchParam> =
        listOf(
            get(DeckPicker::class.java),
            // IntentHandler has unhandled intents
            get(IntentHandler::class.java) { ctx: Context ->
                getReviewDeckIntent(
                    ctx,
                    1L,
                )
            },
            get(StudyOptionsActivity::class.java),
            get(PreferencesActivity::class.java),
            get(SingleFragmentActivity::class.java),
            get(ConfigAwareSingleFragmentActivity::class.java),
            get(CardViewerActivity::class.java),
            get(AccountActivity::class.java),
        )

    class ActivityLaunchParam(
        var activity: Class<out Activity>,
        private var intentBuilder: Function<Context, Intent>,
    ) {
        val simpleName: String = activity.simpleName

        fun build(context: Context): ActivityController<out Activity> =
            Robolectric
                .buildActivity(activity, buildIntent(context))

        fun buildIntent(context: Context): Intent = intentBuilder.apply(context)

        val className: String = activity.name

        companion object {
            operator fun get(
                clazz: Class<out Activity>,
                i: Function<Context, Intent> = Function { Intent() },
            ): ActivityLaunchParam = ActivityLaunchParam(clazz, i)
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.themes

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.Theme
import timber.log.Timber

/**
 * Helper methods to configure things related to AnkiDroid's themes
 *
 * The MMD fork has exactly one theme: [DayTheme.EINK], black on white, never night mode.
 */
object Themes {
    const val ALPHA_ICON_ENABLED_LIGHT = 255 // 100%
    const val ALPHA_ICON_DISABLED_LIGHT = 76 // 31%

    val currentTheme: Theme = DayTheme.EINK

    /** Always `false`: the E Ink theme is the only theme. */
    val isNightTheme: Boolean get() = false

    fun setTheme(context: Context) {
        updateCurrentTheme(context)
        context.setTheme(currentTheme.styleResId)
    }

    /**
     * @param savedInstanceState the bundle provided to [Activity.onCreate]
     */
    fun setTheme(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        val tv = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
        val hadLauncherSplash = tv.resourceId == R.drawable.launch_screen

        // If the decor view already exists, `windowBackground` can no longer be updated by setTheme
        // `hadLauncherSplash` is exempt: its window background is replaced below.
        // Exclude recreation: the decor view can exist before `onCreate`, but the framework
        // refreshes `windowBackground`.
        val isRecreation = savedInstanceState != null
        if (!isRecreation && !hadLauncherSplash && activity.window.peekDecorView() != null) {
            val message =
                "Decor view was initialized before setTheme(): windowBackground is stale. " +
                    "Move window access (e.g. enableEdgeToEdge()) after super.onCreate()"
            if (BuildConfig.DEBUG) throw IllegalStateException(message) else Timber.w(message)
        }

        setTheme(activity as Context)

        if (hadLauncherSplash) {
            activity.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
            val replacement =
                if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                    tv.data.toDrawable()
                } else {
                    AppCompatResources.getDrawable(activity, tv.resourceId)
                }
            activity.window.setBackgroundDrawable(replacement)
        }
    }

    /** Keeps AppCompat out of night mode, whatever the system setting. */
    @Suppress("UNUSED_PARAMETER")
    fun updateCurrentTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}

@Suppress("deprecation", "API35 properly handle edge-to-edge")
fun FragmentActivity.setTransparentStatusBar() {
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    window.statusBarColor = Color.TRANSPARENT
}

fun FragmentActivity.setTransparentBackground() {
    window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
}

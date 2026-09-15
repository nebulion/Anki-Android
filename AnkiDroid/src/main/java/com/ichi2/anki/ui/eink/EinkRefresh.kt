// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from the Loop Habit Tracker MMD fork (EinkRefresh.kt, GPL-3.0-or-later).

package com.ichi2.anki.ui.eink

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import com.ichi2.anki.settings.Prefs

/**
 * Clears E Ink ghosting by flashing the window black for [FLASH_MS].
 *
 * A heuristic, not a panel command. The Kompakt exposes a `meink` service and
 * `kompakt-sdk.jar`'s `MeinkManager`, but every method sets a persistent display mode; none is a
 * one-shot refresh, and app access to the service is unverified. A full-screen maximum-delta
 * repaint pushes most EPD drivers into a fuller waveform on their own, which is what InkOS and
 * other E Ink launchers do. It needs no permission or hidden API, so it survives OS updates.
 *
 * On by default: the study screen flashes every [Prefs.einkRefreshInterval] (12) answers. The
 * setting to change or disable it arrives with the Compose settings (Phase 5: Settings → E Ink).
 */
@MainThread
object EinkRefresh {
    private const val FLASH_MS = 100L

    private val handler = Handler(Looper.getMainLooper())
    private var pending = 0

    /** Records one thing the user did that repainted part of the panel, e.g. answering a card. */
    fun onChange(activity: Activity?) {
        if (!Prefs.isEinkRefreshEnabled) return
        if (++pending < Prefs.einkRefreshInterval) return
        pending = 0
        flash(activity)
    }

    /** Flashes now if enabled, and restarts the count: for changes that repaint most of the screen. */
    fun flashNow(activity: Activity?) {
        if (!Prefs.isEinkRefreshEnabled) return
        pending = 0
        flash(activity)
    }

    /** Flashes regardless of the count and the setting. */
    fun flash(activity: Activity?) {
        val root = activity?.window?.decorView as? ViewGroup ?: return
        handler.post {
            val overlay =
                View(activity).apply {
                    setBackgroundColor(Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            root.addView(overlay)
            overlay.bringToFront()
            handler.postDelayed({ root.removeView(overlay) }, FLASH_MS)
        }
    }
}

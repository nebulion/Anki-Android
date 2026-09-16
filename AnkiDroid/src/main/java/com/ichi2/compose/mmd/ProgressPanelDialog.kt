// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import android.app.Activity
import android.graphics.Color
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable

/**
 * The app's blocking progress window: a [Panel] with a line of text and, if the operation can be
 * cancelled from a button, that button. It replaces `android.app.ProgressDialog`, whose spinner
 * repaints the panel continuously.
 *
 * [setMessage] redraws at most once a second, however often the backend reports progress; the
 * latest message is shown when the next second starts. See `eink-design.md` → motion.
 */
class ProgressPanelDialog(
    activity: Activity,
    private val cancelLabel: String? = null,
    private val onCancelClick: (() -> Unit)? = null,
) : ComponentDialog(activity) {
    private var shownMessage by mutableStateOf("")
    private var lastShownAt = 0L
    private var pending: String? = null
    private val showPending =
        Runnable {
            pending?.let { display(it) }
            pending = null
        }

    init {
        setContentView(
            ComposeView(context).apply {
                setContent {
                    MmdTheme {
                        Panel {
                            PanelBody(shownMessage)
                            if (cancelLabel != null && onCancelClick != null) {
                                PanelActions {
                                    PanelSecondaryAction(label = cancelLabel, onClick = onCancelClick, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            },
        )
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0f)
            setWindowAnimations(0)
        }
    }

    /** Shows [text], or holds it until a second has passed since the last change. */
    fun setMessage(text: String) {
        if (text == shownMessage) return
        val wait = MIN_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastShownAt)
        if (wait <= 0) {
            window?.decorView?.removeCallbacks(showPending)
            display(text)
        } else {
            val wasPending = pending != null
            pending = text
            if (!wasPending) window?.decorView?.postDelayed(showPending, wait)
        }
    }

    private fun display(text: String) {
        shownMessage = text
        lastShownAt = SystemClock.elapsedRealtime()
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1_000L
    }
}

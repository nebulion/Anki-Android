// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.snackbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.compose.mmd.Message
import com.ichi2.compose.mmd.MessageDefaults
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.MmdTheme

/** A screen that shows its own MMD message bar, such as the home screen's strip above the decks. */
interface MessageHostProvider {
    val messages: MessageHostState
}

/**
 * Shows [message] in the MMD message bar of the screen [this] view is on: the screen's own, or
 * one added to the bottom of its `CoordinatorLayout` the first time a message arrives.
 *
 * A Material snackbar slides in and out over the screen, which on E Ink is a run of repaints; the
 * message bar appears and disappears in one.
 *
 * @return false if the view is on no screen that can show one
 */
fun View.showInMessageBar(message: Message): Boolean {
    (context.findActivity() as? MessageHostProvider)?.let {
        it.messages.show(message)
        return true
    }
    val root = nearestCoordinatorLayout() ?: return false
    val state = root.getTag(R.id.mmd_message_host) as? MessageHostState ?: addMessageBar(root)
    state.show(message)
    return true
}

/** The message a configured [Snackbar] would have shown: its text, action and duration. */
fun Snackbar.toMessage(text: CharSequence): Message {
    val action = view.findViewById<Button>(com.google.android.material.R.id.snackbar_action)
    val label = action?.takeIf { it.isVisible && !it.text.isNullOrEmpty() }?.text?.toString()
    return Message(
        text = text.toString(),
        actionLabel = label,
        onAction = label?.let { { action.performClick() } },
        durationMillis =
            when (duration) {
                Snackbar.LENGTH_SHORT -> SHORT_MILLIS
                Snackbar.LENGTH_LONG -> MessageDefaults.DURATION_MILLIS
                Snackbar.LENGTH_INDEFINITE -> Long.MAX_VALUE
                else -> duration.toLong()
            },
    )
}

/**
 * Routes `showThemedToast` to the message bar of the activity [context] belongs to, if it has a
 * screen that can show one. Toasts sent with the application's context stay toasts.
 */
fun routeToastToMessageBar(
    context: Context,
    text: String,
    shortLength: Boolean,
): Boolean {
    val activity = context.findActivity()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return false
    // Looking a view up creates the window's decor view. Before the activity has set its theme (e.g.
    // the 'cannot open during a backup' toast) that is too early, and there is no screen to show on.
    val decor = activity.window?.peekDecorView() ?: return false
    val screen = decor.findViewById<View>(R.id.root_layout) ?: decor.takeIf { activity is MessageHostProvider }
    return screen?.showInMessageBar(
        Message(text = text, durationMillis = if (shortLength) SHORT_MILLIS else MessageDefaults.DURATION_MILLIS),
    ) ?: false
}

/** The message bar's state for [activity], if a message has been shown on it; for tests. */
fun messageBarState(activity: Activity): MessageHostState? =
    (activity as? MessageHostProvider)?.messages
        ?: (activity.findViewById<View>(R.id.root_layout) as? CoordinatorLayout)?.getTag(R.id.mmd_message_host) as? MessageHostState

private fun addMessageBar(root: CoordinatorLayout): MessageHostState {
    val state = MessageHostState()
    val bar =
        ComposeView(root.context).apply {
            setContent { MmdTheme { Box(Modifier.navigationBarsPadding()) { MessageHost(state) } } }
        }
    root.addView(
        bar,
        CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        },
    )
    root.setTag(R.id.mmd_message_host, state)
    return state
}

private fun View.nearestCoordinatorLayout(): CoordinatorLayout? {
    var view: View? = this
    while (view != null) {
        if (view is CoordinatorLayout) return view
        view = view.parent as? View
    }
    return null
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val SHORT_MILLIS = 2_000L

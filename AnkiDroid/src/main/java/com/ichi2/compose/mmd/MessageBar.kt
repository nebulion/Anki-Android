// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.snackbar.SnackbarMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.delay

/** A transient message with an optional action, shown by [MessageHost]. */
@Immutable
data class Message(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMillis: Long = MessageDefaults.DURATION_MILLIS,
)

/** Holds the one message currently shown. A new message replaces the previous one outright. */
@Stable
class MessageHostState {
    var current: Message? by mutableStateOf(null)
        private set

    fun show(message: Message) {
        current = message
    }

    fun show(
        text: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) = show(Message(text, actionLabel, onAction))

    fun dismiss() {
        current = null
    }
}

/**
 * Shows [MessageHostState.current] as an MMD snackbar.
 *
 * Every colour is stated: MMD 1.0.2 defaults the action to `inversePrimary`, which
 * `eInkColorScheme` makes white on a white container. Its built-in divider is 1dp, so it is
 * turned off and the 3dp rule is drawn above instead. No enter/exit animation.
 */
@Composable
fun MessageHost(
    state: MessageHostState,
    modifier: Modifier = Modifier,
) {
    val message = state.current ?: return
    LaunchedEffect(message) {
        delay(message.durationMillis)
        if (state.current === message) state.dismiss()
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Column(modifier.fillMaxWidth()) {
        HorizontalDividerMMD()
        SnackbarMMD(
            action =
                message.actionLabel?.let { label ->
                    {
                        TextButton(onClick = {
                            state.dismiss()
                            message.onAction?.invoke()
                        }) {
                            TextMMD(text = label, fontWeight = FontWeight.Bold, color = ink)
                        }
                    }
                },
            shape = RectangleShape,
            containerColor = paper,
            contentColor = ink,
            actionContentColor = ink,
            dismissActionContentColor = ink,
            dividerColor = ink,
            showDivider = false,
        ) {
            TextMMD(text = message.text, style = MaterialTheme.typography.bodyMedium, color = ink)
        }
    }
}

object MessageDefaults {
    const val DURATION_MILLIS = 4_000L
}

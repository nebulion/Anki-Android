// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
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
 * Shows [MessageHostState.current] as an MMD snackbar (zeroheight "Snackbar"): a full-width white
 * strip at the bottom with a 3dp black rule and 2dp of white across its top, at least 64dp tall,
 * the message in Medium 21sp on 25sp lines. With an action (e.g. Undo) it ends in an outlined
 * button; without one, in a close X. No animation.
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
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDividerMMD(thickness = PanelDefaults.RuleThickness, color = ink)
        Spacer(Modifier.height(PanelDefaults.RuleGap))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextMMD(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp, lineHeight = 25.sp),
                fontWeight = FontWeight.Medium,
                color = ink,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            val label = message.actionLabel
            if (label != null) {
                OutlinedButtonMMD(
                    onClick = {
                        state.dismiss()
                        message.onAction?.invoke()
                    },
                    modifier = Modifier.padding(start = 12.dp).heightIn(min = 48.dp),
                    shape = PanelDefaults.ButtonShape,
                ) {
                    TextMMD(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = ink)
                }
            } else {
                IconButton(onClick = state::dismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.close_icon),
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(28.dp),
                        tint = ink,
                    )
                }
            }
        }
    }
}

object MessageDefaults {
    const val DURATION_MILLIS = 4_000L
}

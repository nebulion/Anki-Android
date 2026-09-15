// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.progress_indicator.LinearProgressIndicatorMMD

/**
 * Blocking progress for long operations (sync, check database, import).
 *
 * No indeterminate spinner: a spinner repaints continuously and ghosts. The panel shows static
 * text, and a determinate bar only when [progress] is known. Callers must throttle updates to at
 * most one per second.
 */
@Composable
fun ProgressPanel(
    message: String,
    progress: Float? = null,
) {
    PanelDialog(onDismissRequest = {}, dismissOnClickOutside = false) {
        PanelBody(message)
        if (progress != null) {
            LinearProgressIndicatorMMD(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

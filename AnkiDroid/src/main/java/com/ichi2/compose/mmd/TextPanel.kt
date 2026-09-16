// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * Asks for one line of text in a [PanelDialog].
 *
 * [validate] returns the reason the text cannot be used, or null to accept it, so a bad sync URL or
 * certificate is refused in the panel instead of being stored and failing at the next sync.
 */
@Composable
fun TextPanel(
    title: String,
    body: String?,
    value: String,
    confirmLabel: String,
    dismissLabel: String,
    validate: (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(value) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    PanelDialog(onDismissRequest = onDismiss) {
        PanelTitle(title)
        if (body != null) PanelBody(body)
        TextFieldMMD(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { message -> { TextMMD(text = message) } },
            colors = panelTextFieldColors(),
        )
        PanelActions {
            PanelSecondaryAction(label = dismissLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
            PanelPrimaryAction(
                label = confirmLabel,
                onClick = {
                    val entered = text.trim()
                    val reason = validate(entered)
                    if (reason != null) {
                        error = reason
                    } else {
                        onConfirm(entered)
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

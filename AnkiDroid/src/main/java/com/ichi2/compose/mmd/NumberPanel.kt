// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * Asks for one whole number in a [PanelDialog], rejecting anything outside [min]..[max].
 *
 * A slider or a spinner needs a repaint for every step, so every number in this app is typed.
 * The value is validated before the panel closes: `toIntOrNull` on a field the user can put
 * anything into is the difference between a rejected entry and a crash.
 */
@Composable
fun NumberPanel(
    title: String,
    body: String?,
    value: Int,
    min: Int,
    max: Int,
    confirmLabel: String,
    dismissLabel: String,
    invalidMessage: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    var isInvalid by rememberSaveable { mutableStateOf(false) }
    PanelDialog(onDismissRequest = onDismiss) {
        PanelTitle(title)
        if (body != null) PanelBody(body)
        TextFieldMMD(
            value = text,
            onValueChange = {
                text = it
                isInvalid = false
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            isError = isInvalid,
            supportingText = if (isInvalid) ({ TextMMD(text = invalidMessage) }) else null,
            colors = panelTextFieldColors(),
        )
        PanelActions {
            PanelSecondaryAction(label = dismissLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
            PanelPrimaryAction(
                label = confirmLabel,
                onClick = {
                    val entered = text.trim().toIntOrNull()
                    if (entered == null || entered < min || entered > max) {
                        isInvalid = true
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

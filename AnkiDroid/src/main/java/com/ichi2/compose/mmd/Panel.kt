// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from the Loop Habit Tracker MMD fork (PanelMMD.kt, GPL-3.0-or-later).

package com.ichi2.compose.mmd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldColorsMMD
import com.mudita.mmd.components.text_field.TextFieldDefaultsMMD

/**
 * The centred panel every confirmation and small form sits in (pattern P5).
 *
 * MMD ships no dialog, so this container is ours, but everything about it is MMD: white fill,
 * 3dp black border (`DividerDefaultsMMD.Thickness`), 8dp corners
 * (`ButtonDefaultsMMD.buttonCornerRadius`), no elevation.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(PanelDefaults.Margin)
                .background(MaterialTheme.colorScheme.surface, PanelDefaults.Shape)
                .border(PanelDefaults.Border, MaterialTheme.colorScheme.onSurface, PanelDefaults.Shape)
                .padding(PanelDefaults.Padding),
    ) {
        content()
    }
}

/**
 * Shows [Panel] over the screen as a window with **no grey dim and no window animation**.
 *
 * Android dims behind every dialog by default: a flat mid-grey over the whole screen, which the
 * panel cannot render and which ghosts on repaint.
 */
@Composable
fun PanelDialog(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnClickOutside = dismissOnClickOutside,
                usePlatformDefaultWidth = false,
            ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        Panel(content = content)
    }
}

/** A destructive or committing action asks first. One solid button commits; the other is outlined. */
@Composable
fun ConfirmPanel(
    title: String,
    body: String?,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PanelDialog(onDismissRequest = onDismiss) {
        PanelTitle(title)
        if (body != null) PanelBody(body)
        PanelActions {
            PanelSecondaryAction(label = dismissLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
            PanelPrimaryAction(label = confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

/** A panel's heading, bold, in MMD's title role. */
@Composable
fun PanelTitle(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** Body copy, regular weight. Never small grey text. */
@Composable
fun PanelBody(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun PanelActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** The one action that commits: solid black, label knocked out in white. */
@Composable
fun PanelPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonMMD(onClick = onClick, modifier = modifier) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Everything beside it: black outline, no fill. */
@Composable
fun PanelSecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButtonMMD(onClick = onClick, modifier = modifier) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** `TextFieldMMD` colours with the container stated, rather than left to MMD's unspecified role. */
@Composable
fun panelTextFieldColors(): TextFieldColorsMMD {
    val white = MaterialTheme.colorScheme.surface
    return TextFieldDefaultsMMD.colors(
        focusedContainerColor = white,
        unfocusedContainerColor = white,
        disabledContainerColor = white,
        errorContainerColor = white,
    )
}

object PanelDefaults {
    val Margin: Dp = 16.dp
    val Padding: Dp = 16.dp

    val Border: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.panelBorder

    val Shape: RoundedCornerShape
        @Composable @ReadOnlyComposable
        get() = RoundedCornerShape(LocalMmdTokens.current.panelCornerRadius)
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from the Loop Habit Tracker MMD fork (PanelMMD.kt, GPL-3.0-or-later).

package com.ichi2.compose.mmd

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldColorsMMD
import com.mudita.mmd.components.text_field.TextFieldDefaultsMMD

/**
 * An MMD dialog (zeroheight "Dialog"): anchored to the bottom of the screen at full width, a 3dp
 * black rule and 2dp of white across its top, then 24dp above the content, 12dp at the sides and
 * below. The heading and supporting text are centred; the buttons are stacked full width with the
 * solid one on top. No border, no corners, no elevation.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDividerMMD(thickness = PanelDefaults.RuleThickness, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(PanelDefaults.RuleGap))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/**
 * Shows [Panel] as a window at the bottom of the screen with **no grey dim and no window
 * animation**: Android dims behind every dialog by default, a flat mid-grey the panel cannot render
 * and which ghosts on repaint.
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
            window?.setGravity(Gravity.BOTTOM)
        }
        Panel(content = content)
    }
}

/**
 * A destructive or committing action asks first (pattern P5): the MMD standard dialog, its heading,
 * the explanation, then the committing action as a solid button above the escape as an outlined one.
 */
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
            PanelSecondaryAction(label = dismissLabel, onClick = onDismiss)
            PanelPrimaryAction(label = confirmLabel, onClick = onConfirm)
        }
    }
}

/** A dialog's heading: Black 25sp on 28sp lines, centred. */
@Composable
fun PanelTitle(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp, lineHeight = 28.sp),
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

/** A dialog's supporting text: Medium 21sp on 25sp lines, centred. Never small grey text. */
@Composable
fun PanelBody(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp, lineHeight = 25.sp),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * What a dialog's buttons are laid out in. The weight a caller gives a button is ignored: every
 * button is full width.
 */
interface PanelActionsScope {
    @Suppress("UNUSED_PARAMETER")
    fun Modifier.weight(weight: Float): Modifier = this
}

private object PanelActionsScopeInstance : PanelActionsScope

/**
 * A dialog's buttons, stacked full width 12dp apart below 24dp of space. Callers write the escape
 * first and the committing action last; the last is placed on top, as MMD puts the solid button
 * above the outlined one.
 */
@Composable
fun PanelActions(content: @Composable PanelActionsScope.() -> Unit) {
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    Layout(
        content = { PanelActionsScopeInstance.content() },
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }.reversed()
        val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height + gap
            }
        }
    }
}

/** The one action that commits: solid black, bold label knocked out in white. */
@Composable
fun PanelPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonMMD(
        onClick = onClick,
        modifier = modifier.heightIn(min = PanelDefaults.ButtonHeight),
        shape = PanelDefaults.ButtonShape,
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/** Everything beside it: black outline, no fill. */
@Composable
fun PanelSecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButtonMMD(
        onClick = onClick,
        modifier = modifier.heightIn(min = PanelDefaults.ButtonHeight),
        shape = PanelDefaults.ButtonShape,
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
    val Padding: Dp = 16.dp

    /** The black rule across a dialog's or sheet's top, and the white gap under it. */
    val RuleThickness: Dp = 3.dp
    val RuleGap: Dp = 2.dp

    val Margin: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.panelMargin

    val Border: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.panelBorder

    val Shape: RoundedCornerShape
        @Composable @ReadOnlyComposable
        get() = RoundedCornerShape(LocalMmdTokens.current.panelCornerRadius)

    val ButtonHeight: Dp
        @Composable @ReadOnlyComposable
        get() = LocalMmdTokens.current.buttonHeight

    val ButtonShape: RoundedCornerShape
        @Composable @ReadOnlyComposable
        get() = RoundedCornerShape(LocalMmdTokens.current.buttonCornerRadius)
}

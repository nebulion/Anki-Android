// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import com.mudita.mmd.components.text.TextMMD

/**
 * A [DialogFragment] drawn as an MMD [Panel]: for the dialogs a View-based activity shows, which
 * cannot host a Compose [PanelDialog] of their own.
 *
 * Like [PanelDialog], the window has no grey dim and no animation. Dialogs that must extend another
 * base class use [panelView] and [applyPanelWindow] directly.
 */
abstract class PanelDialogFragment : DialogFragment() {
    /** The panel's content: typically [PanelTitle], [PanelBody] and [PanelActions]. */
    @Composable
    protected abstract fun PanelContent()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = panelView { PanelContent() }

    override fun onStart() {
        super.onStart()
        applyPanelWindow()
    }
}

/** The view of a panel dialog: [content] in an MMD [Panel]. Return it from `onCreateView`. */
fun DialogFragment.panelView(content: @Composable () -> Unit): View =
    ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { MmdTheme { Panel { content() } } }
    }

/** Removes the window's background, dim and animation so only the panel shows. Call from `onStart`. */
fun DialogFragment.applyPanelWindow() {
    dialog?.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        setDimAmount(0f)
        setWindowAnimations(0)
    }
}

/** One choice in a panel's list, separated from the next by a dashed hairline. */
@Composable
fun PanelChoice(
    label: String,
    isFirst: Boolean,
    onClick: () -> Unit,
) {
    Column {
        if (!isFirst) DashedDividerMMD()
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SheetDefaults.RowHeight)
                    .clickable(onClick = onClick)
                    .padding(vertical = 16.dp),
        )
    }
}

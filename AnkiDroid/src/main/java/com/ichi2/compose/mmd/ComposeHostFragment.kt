// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

/**
 * A Fragment whose whole UI is an MMD Compose screen.
 *
 * AnkiDroid navigates between Fragments hosted in `SingleFragmentActivity`; keeping that shell
 * leaves navigation, lifecycle and back-stack code untouched while the screen itself is Compose.
 */
abstract class ComposeHostFragment : Fragment() {
    @Composable
    protected abstract fun Content()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { MmdTheme { Content() } }
        }
}

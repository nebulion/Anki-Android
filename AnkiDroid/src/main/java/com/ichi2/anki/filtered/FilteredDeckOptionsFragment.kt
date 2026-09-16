// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.filtered

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelBody
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelTitle
import kotlinx.coroutines.launch

/**
 * The screen where a filtered deck is built, or rebuilt after changing its options.
 *
 * The screen is [FilteredDeckOptionsScreenMMD]; [FilteredDeckOptionsViewModel] holds the state and
 * builds the deck.
 */
class FilteredDeckOptionsFragment :
    ComposeHostFragment(),
    FilteredDeckOptionsActions {
    private val viewModel by viewModels<FilteredDeckOptionsViewModel>()

    /** Asks before leaving with unsaved changes (P5). */
    private var isConfirmingDiscard by mutableStateOf(false)

    private val discardBackHandler =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                isConfirmingDiscard = true
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, discardBackHandler)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hasUnsavedChanges.collect { discardBackHandler.isEnabled = it }
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()

        when (val current = state) {
            is Initializing -> {
                FilteredDeckOptionsScreenMMD(state = null, actions = this)
                // the deck's options could not be read: nothing to edit, so the only way is out
                current.throwable?.let { error ->
                    PanelDialog(onDismissRequest = {}, dismissOnClickOutside = false) {
                        PanelTitle(getString(R.string.import_title_error))
                        PanelBody(error.toString())
                        PanelActions {
                            PanelPrimaryAction(label = getString(R.string.dialog_exit), onClick = { requireActivity().finish() })
                        }
                    }
                }
            }
            // built: the deck page behind this screen shows the deck
            DeckBuilt -> LaunchedEffect(Unit) { requireActivity().finish() }
            is FilteredDeckOptions -> {
                FilteredDeckOptionsScreenMMD(state = current, actions = this)
                current.throwable?.let { error ->
                    PanelDialog(onDismissRequest = viewModel::clearError) {
                        PanelTitle(getString(R.string.import_title_error))
                        PanelBody(error.message.orEmpty())
                        PanelActions {
                            PanelPrimaryAction(label = getString(R.string.dialog_ok), onClick = viewModel::clearError)
                        }
                    }
                }
                // the fork has no card browser to show a search in
                if (current.browserQuery != null) LaunchedEffect(current.browserQuery) { viewModel.clearSearchInBrowser() }
            }
        }

        if (isConfirmingDiscard) {
            ConfirmPanel(
                title = TR.cardTemplatesDiscardChanges(),
                body = null,
                confirmLabel = getString(R.string.discard),
                dismissLabel = with(requireContext()) { TR.sentenceCase.keepEditing },
                onConfirm = {
                    isConfirmingDiscard = false
                    requireActivity().finish()
                },
                onDismiss = { isConfirmingDiscard = false },
            )
        }
    }

    override fun onDeckNameChange(name: String) = viewModel.onDeckNameChange(name)

    override fun onSearchChange(
        index: FilterIndex,
        search: String,
    ) = viewModel.onSearchChange(index, search)

    override fun onLimitChange(
        index: FilterIndex,
        limit: String,
    ) = viewModel.onLimitChange(index, limit)

    override fun onCardsOptionsChange(
        index: FilterIndex,
        position: Int,
    ) = viewModel.onCardsOptionsChange(index, position)

    override fun onSecondFilterStatusChange(isEnabled: Boolean) = viewModel.onSecondFilterStatusChange(isEnabled)

    override fun onRescheduleChange(isEnabled: Boolean) = viewModel.onRescheduleChange(isEnabled)

    override fun onRescheduleDelayChange(
        target: RescheduleDelay,
        amount: String,
    ) = viewModel.onRescheduleDelayChange(target, amount)

    override fun onAllowEmptyChange(isEnabled: Boolean) = viewModel.onAllowEmptyChange(isEnabled)

    override fun onBuild() = viewModel.build()

    override fun onBack() = requireActivity().onBackPressedDispatcher.onBackPressed()

    companion object {
        const val ARG_DECK_ID = "arg_deck_id"
        const val ARG_SEARCH = "arg_search"
        const val ARG_SEARCH_2 = "arg_search_2"

        /**
         * Starts a [ConfigAwareSingleFragmentActivity] containing this fragment. If [search] or
         * [search2] are provided, they will be used as the default search text.
         * @param did the [DeckId] of a filtered deck. If it's non-zero, load and modify its settings
         * otherwise build a new deck and derive settings from the current deck.
         */
        fun getIntent(
            context: Context,
            did: DeckId = 0,
            search: String? = null,
            search2: String? = null,
        ): Intent =
            ConfigAwareSingleFragmentActivity.getIntent(
                context = context,
                fragmentClass = FilteredDeckOptionsFragment::class,
                arguments =
                    Bundle().apply {
                        putLong(ARG_DECK_ID, did)
                        putString(ARG_SEARCH, search)
                        putString(ARG_SEARCH_2, search2)
                    },
            )
    }
}

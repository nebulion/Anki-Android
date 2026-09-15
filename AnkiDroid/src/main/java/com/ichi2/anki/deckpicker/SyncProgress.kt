// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.annotation.StringRes
import com.ichi2.anki.R

/**
 * A sync in progress, shown on the home screen in place of the deck list rather than in a dialog.
 *
 * @see com.ichi2.anki.withSyncProgress
 */
data class SyncProgress(
    /** What the backend last reported, e.g. `12 added` or `28 MB/141 MB`; `null` before its first report. */
    val detail: String? = null,
    /** How much is done, `0..1`, when the size of the work is known; `null` shows a moving bar. */
    val fraction: Float? = null,
    @StringRes val cancelLabel: Int = R.string.dialog_cancel,
    /** Stops the sync; `null` when this step cannot be cancelled. */
    val cancel: (() -> Unit)? = null,
)

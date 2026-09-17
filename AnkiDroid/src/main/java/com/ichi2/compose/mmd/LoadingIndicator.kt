// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.delay

/**
 * The MMD inline loading indicator (zeroheight "Loading Indicator"): a 24dp symbol above a short
 * bold label, centred in the space the content will take. Static: a spinning symbol repaints the
 * E Ink screen continuously.
 */
@Composable
fun LoadingIndicator(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_loading_static),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        TextMMD(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * A page that is still loading (owner, 2026-09-17: no more "Processing…"): blank at first, since
 * most pages load within a moment and a label shown only to be replaced is two repaints on E Ink;
 * the [LoadingIndicator] after [delayMillis].
 */
@Composable
fun PageLoading(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.mmd_loading),
    delayMillis: Long = 500,
) {
    val isShown by produceState(false) {
        delay(delayMillis)
        value = true
    }
    if (isShown) {
        LoadingIndicator(label, modifier)
    } else {
        Box(modifier.fillMaxSize())
    }
}

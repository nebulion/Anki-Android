// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckId
import com.ichi2.compose.mmd.DashedDividerMMD
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.ScreenHeader
import com.mudita.mmd.components.badge.BadgeMMD
import com.mudita.mmd.components.badge.BadgedBoxMMD
import com.mudita.mmd.components.progress_indicator.LinearProgressIndicatorMMD
import com.mudita.mmd.components.text.TextMMD

/** Everything the deck list shows. */
data class DeckListUiState(
    val decks: List<DisplayDeckNode> = emptyList(),
    /** `null` until the collection has loaded; `true` when it has no cards yet. */
    val isEmptyCollection: Boolean? = null,
    /** Sync is offered only to a signed-in user; before that, the empty collection offers sign-in. */
    val isLoggedIn: Boolean = false,
    val syncState: SyncIconState = SyncIconState.Normal,
    /** A sync in progress replaces the deck list; `null` when not syncing. */
    val syncProgress: SyncProgress? = null,
)

/**
 * The home screen: one row per deck under a header that also opens Statistics and the More page.
 * Tapping a deck starts studying it; holding it opens its page; the chevron expands or collapses
 * subdecks. A solid line separates top-level decks, a dashed line the subdecks inside one.
 * While syncing, the list gives way to the sync's progress.
 */
@Composable
fun DeckListScreenMMD(
    state: DeckListUiState,
    onSync: () -> Unit,
    onStatistics: () -> Unit,
    onMore: () -> Unit,
    onDeckClick: (DeckId) -> Unit,
    onDeckLongPress: (DeckId) -> Unit,
    onToggleExpand: (DeckId) -> Unit,
    onSignIn: () -> Unit,
    onImport: () -> Unit,
) {
    val isSyncing = state.syncProgress != null
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = TR.actionsDecks(),
            actions = {
                if (state.isLoggedIn) {
                    SyncAction(state.syncState, enabled = !isSyncing, onSync)
                }
                HeaderAction(
                    icon = R.drawable.ic_bar_chart_black,
                    contentDescription = TR.statisticsTitle(),
                    onClick = onStatistics,
                    enabled = !isSyncing,
                )
                HeaderAction(
                    icon = R.drawable.ic_more_vertical,
                    contentDescription = stringResource(R.string.bottom_nav_more),
                    onClick = onMore,
                    enabled = !isSyncing,
                )
            },
        )
        if (state.isLoggedIn && state.syncState == SyncIconState.OneWay && !isSyncing) {
            TextMMD(
                text = stringResource(R.string.sync_menu_title_one_way_sync),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
            )
            DashedDividerMMD()
        }
        when {
            state.syncProgress != null -> Syncing(state.syncProgress, Modifier.weight(1f))
            state.isEmptyCollection == null -> Spacer(Modifier.weight(1f))
            state.isEmptyCollection -> FirstRun(state.isLoggedIn, onSignIn, onImport, Modifier.weight(1f))
            else ->
                PagedList(Modifier.weight(1f)) {
                    itemsIndexed(state.decks, key = { _, deck -> deck.did }) { index, deck ->
                        Column {
                            DeckRow(deck, onDeckClick, onDeckLongPress, onToggleExpand)
                            val next = state.decks.getOrNull(index + 1)
                            if (next == null || next.depth <= TOP_LEVEL_DEPTH) {
                                DeckGroupDivider()
                            } else {
                                DashedDividerMMD(Modifier.padding(horizontal = RowDefaults.EdgePadding))
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun SyncAction(
    syncState: SyncIconState,
    enabled: Boolean,
    onSync: () -> Unit,
) {
    val description = stringResource(R.string.button_sync)
    if (syncState == SyncIconState.PendingChanges && enabled) {
        BadgedBoxMMD(badge = { BadgeMMD() }) {
            HeaderAction(icon = R.drawable.ic_sync, contentDescription = description, onClick = onSync)
        }
    } else {
        HeaderAction(icon = R.drawable.ic_sync, contentDescription = description, onClick = onSync, enabled = enabled)
    }
}

/**
 * One deck: a chevron when it has subdecks and its name, bold while it has cards to do (owner's
 * call, 2026-09-15: no counts and no indent in the list; the deck page carries the numbers).
 * Tap starts studying (or opens the page when nothing is due); a long press opens the deck page.
 */
@Composable
private fun DeckRow(
    deck: DisplayDeckNode,
    onDeckClick: (DeckId) -> Unit,
    onDeckLongPress: (DeckId) -> Unit,
    onToggleExpand: (DeckId) -> Unit,
) {
    val hasCardsToStudy = deck.newCount + deck.lrnCount + deck.revCount > 0
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowDefaults.MinHeight)
                .combinedClickable(
                    onClickLabel = TR.decksStudyDeck(),
                    onLongClickLabel = deck.lastDeckNameComponent,
                    onLongClick = { onDeckLongPress(deck.did) },
                    onClick = { onDeckClick(deck.did) },
                ).padding(end = RowDefaults.EdgePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(ChevronTouch).clickable(enabled = deck.canCollapse) { onToggleExpand(deck.did) },
            contentAlignment = Alignment.Center,
        ) {
            if (deck.canCollapse) {
                Icon(
                    painter =
                        painterResource(
                            if (deck.collapsed) R.drawable.ic_expand_more_black_24dp_xml else R.drawable.ic_expand_less_black_24dp,
                        ),
                    contentDescription = if (deck.collapsed) TR.browsingSidebarExpand() else TR.browsingSidebarCollapse(),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        TextMMD(
            text = deck.lastDeckNameComponent,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = if (deck.filtered) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (hasCardsToStudy) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The solid line after a top-level deck and its subdecks. */
@Composable
private fun DeckGroupDivider() {
    Box(Modifier.fillMaxWidth().height(DeckGroupDividerThickness).background(MaterialTheme.colorScheme.onSurface))
}

/**
 * The sync in progress, in place of the deck list: a title, a bar that fills as work completes
 * (or keeps moving while the amount is unknown), the backend's latest report and Cancel.
 */
@Composable
private fun Syncing(
    progress: SyncProgress,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextMMD(text = stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
        if (progress.fraction != null) {
            LinearProgressIndicatorMMD(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            val transition = rememberInfiniteTransition(label = "sync")
            val moving by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = SYNC_BAR_CYCLE_MS, easing = LinearEasing)),
                label = "sync",
            )
            LinearProgressIndicatorMMD(progress = { moving }, modifier = Modifier.fillMaxWidth())
        }
        if (progress.detail != null) {
            TextMMD(text = progress.detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (progress.cancel != null) {
            PanelSecondaryAction(
                label = stringResource(progress.cancelLabel),
                onClick = progress.cancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The first screen of a new install: sign in to download an AnkiWeb collection, or import a file. */
@Composable
private fun FirstRun(
    isLoggedIn: Boolean,
    onSignIn: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextMMD(text = stringResource(R.string.no_cards_placeholder_title), style = MaterialTheme.typography.titleMedium)
        if (isLoggedIn) {
            PanelPrimaryAction(label = TR.actionsImport(), onClick = onImport, modifier = Modifier.fillMaxWidth())
        } else {
            PanelPrimaryAction(label = stringResource(R.string.not_logged_in_title), onClick = onSignIn, modifier = Modifier.fillMaxWidth())
            PanelSecondaryAction(label = TR.actionsImport(), onClick = onImport, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * [DisplayDeckNode.depth] of a deck with no parent ("A" is 0, "A::B" is 1). The first draft used 1,
 * which gave first-level subdecks solid separators and no indent.
 */
private const val TOP_LEVEL_DEPTH = 0

/** One sweep of the sync bar while the amount of work is unknown. */
private const val SYNC_BAR_CYCLE_MS = 2_000

private val ChevronTouch = 48.dp
private val DeckGroupDividerThickness = 2.dp

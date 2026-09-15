// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
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
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.ScreenHeader
import com.mudita.mmd.components.badge.BadgeMMD
import com.mudita.mmd.components.badge.BadgedBoxMMD
import com.mudita.mmd.components.text.TextMMD

/** Everything the Decks tab shows. */
data class DeckListUiState(
    val decks: List<DisplayDeckNode> = emptyList(),
    /** "Studied N cards in M minutes today" */
    val studiedToday: String = "",
    /** `null` until the collection has loaded; `true` when it has no cards yet. */
    val isEmptyCollection: Boolean? = null,
    val syncState: SyncIconState = SyncIconState.Normal,
    /** What Undo would undo, or `null` when there is nothing to undo. */
    val undoLabel: String? = null,
)

/**
 * The Decks tab: one row per deck. Tapping a deck opens its page; the chevron expands or collapses
 * subdecks. There is no long-press: every deck action lives on the deck page.
 */
@Composable
fun DeckListScreenMMD(
    state: DeckListUiState,
    onUndo: () -> Unit,
    onSync: () -> Unit,
    onDeckClick: (DeckId) -> Unit,
    onToggleExpand: (DeckId) -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = TR.actionsDecks(),
            actions = {
                if (state.undoLabel != null) {
                    HeaderAction(icon = R.drawable.ic_undo_white, contentDescription = state.undoLabel, onClick = onUndo)
                }
                SyncAction(state.syncState, onSync)
            },
        )
        syncSubtitle(state.syncState)?.let { subtitle ->
            TextMMD(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
            )
            DashedDividerMMD()
        }
        when (state.isEmptyCollection) {
            null -> Spacer(Modifier.weight(1f))
            true -> EmptyCollection(onImport, Modifier.weight(1f))
            false -> {
                PagedList(Modifier.weight(1f)) {
                    items(state.decks, key = { it.did }) { deck ->
                        Column {
                            DeckRow(deck, onDeckClick, onToggleExpand)
                            DashedDividerMMD(Modifier.padding(horizontal = RowDefaults.EdgePadding))
                        }
                    }
                }
                if (state.studiedToday.isNotBlank()) {
                    TextMMD(
                        text = state.studiedToday,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncAction(
    syncState: SyncIconState,
    onSync: () -> Unit,
) {
    val description = stringResource(R.string.button_sync)
    if (syncState == SyncIconState.PendingChanges) {
        BadgedBoxMMD(badge = { BadgeMMD() }) {
            HeaderAction(icon = R.drawable.ic_sync, contentDescription = description, onClick = onSync)
        }
    } else {
        HeaderAction(icon = R.drawable.ic_sync, contentDescription = description, onClick = onSync)
    }
}

@Composable
private fun syncSubtitle(syncState: SyncIconState): String? =
    when (syncState) {
        SyncIconState.OneWay -> stringResource(R.string.sync_menu_title_one_way_sync)
        SyncIconState.NotLoggedIn -> stringResource(R.string.sync_menu_title_no_account)
        SyncIconState.Normal, SyncIconState.PendingChanges -> null
    }

/** One deck: indent by depth, chevron when it has subdecks, name (italic if filtered), three counts. */
@Composable
private fun DeckRow(
    deck: DisplayDeckNode,
    onDeckClick: (DeckId) -> Unit,
    onToggleExpand: (DeckId) -> Unit,
) {
    val indent = DepthIndent * (deck.depth - 1).coerceAtLeast(0)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowDefaults.MinHeight)
                .clickable { onDeckClick(deck.did) }
                .padding(start = indent, end = RowDefaults.EdgePadding),
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Count(deck.newCount)
        Count(deck.lrnCount)
        Count(deck.revCount)
    }
}

/** A count, right-aligned in a fixed column so numbers line up; zero shows nothing. */
@Composable
private fun Count(value: Int) {
    TextMMD(
        text = if (value == 0) "" else value.toString(),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.End,
        modifier = Modifier.width(CountWidth),
    )
}

@Composable
private fun EmptyCollection(
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextMMD(text = stringResource(R.string.no_cards_placeholder_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        PanelPrimaryAction(label = TR.actionsImport(), onClick = onImport, modifier = Modifier.fillMaxWidth())
    }
}

private val DepthIndent = 16.dp
private val ChevronTouch = 48.dp
private val CountWidth = 44.dp

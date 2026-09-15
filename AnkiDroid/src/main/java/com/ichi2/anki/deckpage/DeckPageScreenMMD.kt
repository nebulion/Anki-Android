// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.compose.mmd.DashedDividerMMD
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.PanelDefaults
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.ScreenHeader
import com.mudita.mmd.components.text.TextMMD

/** What the deck page shows. `null` fields are still loading. */
sealed interface DeckPageUiState {
    data object Loading : DeckPageUiState

    /** Cards are due: counts and the Study button. */
    data class Study(
        val deckName: String,
        val description: String?,
        val newCount: Int,
        val learnCount: Int,
        val reviewCount: Int,
        val isFiltered: Boolean,
    ) : DeckPageUiState

    /** Nothing due today. */
    data class Congrats(
        val deckName: String,
        val message: String,
        val canUnbury: Boolean,
        val canCustomStudy: Boolean,
    ) : DeckPageUiState

    /** The deck has no cards. */
    data class Empty(
        val deckName: String,
    ) : DeckPageUiState
}

/**
 * A deck's page (pattern P1): replaces the study options screen and the deck long-press menu.
 * Header actions open the deck options and the deck menu; the body is counts and one solid Study button.
 */
@Composable
fun DeckPageScreenMMD(
    state: DeckPageUiState,
    messages: MessageHostState,
    onBack: () -> Unit,
    deckOptionsLabel: String,
    customStudyLabel: String,
    onDeckOptions: () -> Unit,
    onMenu: () -> Unit,
    onStudy: () -> Unit,
    onUnbury: () -> Unit,
    onCustomStudy: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = state.deckNameOrEmpty(),
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = onBack,
                )
            },
            actions = {
                HeaderAction(
                    icon = R.drawable.ic_tune_white,
                    contentDescription = deckOptionsLabel,
                    onClick = onDeckOptions,
                    enabled = state !is DeckPageUiState.Loading,
                )
                HeaderAction(
                    icon = R.drawable.ic_more_vertical,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    onClick = onMenu,
                    enabled = state !is DeckPageUiState.Loading,
                )
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                DeckPageUiState.Loading -> Unit
                is DeckPageUiState.Study -> StudyBody(state, onStudy)
                is DeckPageUiState.Congrats -> CongratsBody(state, customStudyLabel, onUnbury, onCustomStudy)
                is DeckPageUiState.Empty ->
                    TextMMD(text = stringResource(R.string.empty_deck), style = MaterialTheme.typography.bodyLarge)
            }
        }
        MessageHost(messages)
    }
}

@Composable
private fun StudyBody(
    state: DeckPageUiState.Study,
    onStudy: () -> Unit,
) {
    CountRow(TR.actionsNew(), state.newCount)
    DashedDividerMMD()
    CountRow(TR.schedulingLearning(), state.learnCount)
    DashedDividerMMD()
    CountRow(TR.studyingToReview(), state.reviewCount)
    PanelPrimaryAction(
        label = stringResource(R.string.studyoptions_start),
        onClick = onStudy,
        modifier = Modifier.fillMaxWidth().heightIn(min = PanelDefaults.ButtonHeight),
    )
    val description = if (state.isFiltered) stringResource(R.string.dyn_deck_desc) else state.description
    if (!description.isNullOrBlank()) {
        TextMMD(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CountRow(
    label: String,
    count: Int,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextMMD(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CongratsBody(
    state: DeckPageUiState.Congrats,
    customStudyLabel: String,
    onUnbury: () -> Unit,
    onCustomStudy: () -> Unit,
) {
    TextMMD(text = state.message, style = MaterialTheme.typography.bodyLarge)
    if (state.canUnbury) {
        PanelSecondaryAction(label = TR.studyingUnbury(), onClick = onUnbury, modifier = Modifier.fillMaxWidth())
    }
    if (state.canCustomStudy) {
        PanelPrimaryAction(label = customStudyLabel, onClick = onCustomStudy, modifier = Modifier.fillMaxWidth())
    }
}

fun DeckPageUiState.deckNameOrEmpty(): String =
    when (this) {
        DeckPageUiState.Loading -> ""
        is DeckPageUiState.Study -> deckName
        is DeckPageUiState.Congrats -> deckName
        is DeckPageUiState.Empty -> deckName
    }

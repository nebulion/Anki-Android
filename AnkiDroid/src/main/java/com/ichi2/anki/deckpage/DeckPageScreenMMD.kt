// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.ichi2.compose.mmd.TextPage
import com.ichi2.compose.mmd.paragraphs
import com.mudita.mmd.components.text.TextMMD
import java.text.NumberFormat

/** What the deck page shows. `null` fields are still loading. */
sealed interface DeckPageUiState {
    data object Loading : DeckPageUiState

    /** Cards are due: today's counts, the Study button and the deck's totals. */
    data class Study(
        val deckName: String,
        val description: String?,
        val newCount: Int,
        val learnCount: Int,
        val reviewCount: Int,
        val isFiltered: Boolean,
        /** Cards in the deck and its subdecks. */
        val totalCards: Int = 0,
        /** New cards not studied yet, beyond today's limit too. */
        val totalNewCards: Int = 0,
        /** Cards buried until tomorrow; not counted in the three counts. */
        val buriedCount: Int = 0,
        /** When no learning card is due now: the backend's sentence for when the next one is, or `null`. */
        val nextLearnDue: String? = null,
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
        var isDescriptionShown by rememberSaveable { mutableStateOf(false) }
        // the page fits the screen, so it does not scroll (nothing in the app scrolls continuously)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                DeckPageUiState.Loading -> Unit
                is DeckPageUiState.Study -> StudyBody(state, onStudy) { isDescriptionShown = true }
                is DeckPageUiState.Congrats -> CongratsBody(state, customStudyLabel, onUnbury, onCustomStudy)
                is DeckPageUiState.Empty ->
                    TextMMD(text = stringResource(R.string.empty_deck), style = MaterialTheme.typography.bodyLarge)
            }
        }
        MessageHost(messages)
        if (isDescriptionShown && state is DeckPageUiState.Study) {
            TextPage(
                title = stringResource(R.string.deck_description_field_hint),
                blocks = paragraphs(state.descriptionText()),
                onClose = { isDescriptionShown = false },
            )
        }
    }
}

@Composable
private fun DeckPageUiState.Study.descriptionText(): String =
    if (isFiltered) stringResource(R.string.dyn_deck_desc) else description.orEmpty()

/**
 * Owner's layout "A" (2026-09-15): today's three counts as large tiles, Study, then the deck's
 * totals and when the next learning card comes, so the page carries information instead of space.
 */
@Composable
private fun StudyBody(
    state: DeckPageUiState.Study,
    onStudy: () -> Unit,
    onDescription: () -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        CountTile(state.newCount, TR.actionsNew(), Modifier.weight(1f))
        CountTile(state.learnCount, TR.schedulingLearning(), Modifier.weight(1f))
        CountTile(state.reviewCount, TR.studyingToReview(), Modifier.weight(1f))
    }
    if (state.buriedCount > 0) {
        TextMMD(
            text = pluralStringResource(R.plurals.studyoptions_buried_count, state.buriedCount, state.buriedCount),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelPrimaryAction(
        label = stringResource(R.string.studyoptions_start),
        onClick = onStudy,
        modifier = Modifier.fillMaxWidth().heightIn(min = PanelDefaults.ButtonHeight),
    )
    Column {
        InfoRow(stringResource(R.string.studyoptions_total_label), state.totalCards)
        DashedDividerMMD()
        InfoRow(stringResource(R.string.studyoptions_total_new_label), state.totalNewCards)
    }
    if (state.nextLearnDue != null) {
        TextMMD(text = state.nextLearnDue, style = MaterialTheme.typography.bodyMedium)
    }
    // the description is on its own page (owner, 2026-09-16: inline it read too heavily)
    if (state.descriptionText().isNotBlank()) {
        PanelSecondaryAction(
            label = stringResource(R.string.deck_description_field_hint),
            onClick = onDescription,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One of today's counts: the number large and bold over its label. */
@Composable
private fun CountTile(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextMMD(
            text = NumberFormat.getIntegerInstance().format(count),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** A deck total: label on the left, value on the right. */
@Composable
private fun InfoRow(
    label: String,
    value: Int,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextMMD(
            text = NumberFormat.getIntegerInstance().format(value),
            style = MaterialTheme.typography.bodyLarge,
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

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.PanelDefaults
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.panelTextFieldColors
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/** Everything [ReviewerScreenMMD] draws. Actions are callbacks. */
data class ReviewerUiState(
    /** `null` hides the counts: the collection's "show remaining card count" is off. */
    val counts: StudyCounts? = null,
    val isAnswerShown: Boolean = false,
    val nextTimes: AnswerButtonsNextTime? = null,
    /** `null` when there is nothing to undo. */
    val undoLabel: String? = null,
    val hasMedia: Boolean = false,
    /** Name of the card's flag, `null` when the card is unflagged. */
    val flagName: String? = null,
    val isMarked: Boolean = false,
    val showAnswerButtons: Boolean = true,
    val hideHardAndEasy: Boolean = false,
    val showTypeAnswer: Boolean = false,
    val autoFocusTypeAnswer: Boolean = false,
)

/**
 * The study screen: a header with the counts and actions, the card, and the answer row.
 *
 * [card] is a `WebContent` hosting the fragment's `SafeWebViewLayout`, so the JS gesture bridge is
 * unchanged. Nothing is coloured: the flag is named, and the current queue's count is bold.
 */
@Composable
fun ReviewerScreenMMD(
    state: ReviewerUiState,
    typedAnswer: String,
    messages: MessageHostState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onReplay: () -> Unit,
    onMenu: () -> Unit,
    onShowAnswer: () -> Unit,
    onRate: (Rating) -> Unit,
    onTypedAnswerChange: (String) -> Unit,
    onTypeAnswerFocusChange: (Boolean) -> Unit,
    card: @Composable (Modifier) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .imePadding(),
    ) {
        ScreenHeader(
            title = { state.counts?.let { StudyCountsText(it) } },
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = onBack,
                )
            },
            actions = {
                HeaderAction(
                    icon = R.drawable.ic_undo_white,
                    contentDescription = state.undoLabel ?: TR.undoUndo(),
                    onClick = onUndo,
                    enabled = state.undoLabel != null,
                )
                if (state.hasMedia) {
                    HeaderAction(
                        icon = R.drawable.ic_replay,
                        contentDescription = stringResource(R.string.replay_media),
                        onClick = onReplay,
                    )
                }
                HeaderAction(
                    icon = R.drawable.ic_more_vertical,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    onClick = onMenu,
                )
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            card(Modifier.fillMaxSize())
            CardIndicators(
                flagName = state.flagName,
                isMarked = state.isMarked,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (state.showTypeAnswer) {
            TypeAnswerField(
                value = typedAnswer,
                onValueChange = onTypedAnswerChange,
                onDone = onShowAnswer,
                onFocusChange = onTypeAnswerFocusChange,
                autoFocus = state.autoFocusTypeAnswer,
            )
        }
        if (state.showAnswerButtons) {
            AnswerRow(state = state, onShowAnswer = onShowAnswer, onRate = onRate)
        }
        MessageHost(messages)
    }
}

/** `12 · 3 · 40`: new, learning, review. The queue the current card came from is bold. */
@Composable
private fun StudyCountsText(counts: StudyCounts) {
    val style = MaterialTheme.typography.titleLarge
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Counts.Queue.NEW to counts.new,
            Counts.Queue.LRN to counts.learn,
            Counts.Queue.REV to counts.review,
        ).forEachIndexed { index, (queue, count) ->
            if (index > 0) TextMMD(text = " · ", style = style)
            TextMMD(
                text = count,
                style = style,
                fontWeight = if (queue == counts.activeQueue) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** A small black glyph and the flag's name over the card's top-left corner; never a colour. */
@Composable
private fun CardIndicators(
    flagName: String?,
    isMarked: Boolean,
    modifier: Modifier = Modifier,
) {
    if (flagName == null && !isMarked) return
    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (flagName != null) {
            Glyph(R.drawable.ic_flag_transparent)
            TextMMD(text = flagName, style = MaterialTheme.typography.bodyMedium)
        }
        if (isMarked) Glyph(R.drawable.ic_star)
    }
}

@Composable
private fun Glyph(
    @DrawableRes icon: Int,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TypeAnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    autoFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    TextFieldMMD(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = { TextMMD(text = stringResource(R.string.type_answer_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = panelTextFieldColors(),
    )
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

/** "Show answer" as one full-width solid button; after flipping, the four ratings. */
@Composable
private fun AnswerRow(
    state: ReviewerUiState,
    onShowAnswer: () -> Unit,
    onRate: (Rating) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = AnswerRowTopGap, bottom = AnswerRowBottomGap)
                .height(AnswerRowHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!state.isAnswerShown) {
            ButtonMMD(
                onClick = onShowAnswer,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = PanelDefaults.ButtonShape,
            ) {
                TextMMD(
                    text = stringResource(R.string.show_answer),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            val times = state.nextTimes
            AnswerButton(stringResource(R.string.ease_button_again), times?.again, solid = false) { onRate(Rating.AGAIN) }
            if (!state.hideHardAndEasy) {
                AnswerButton(stringResource(R.string.ease_button_hard), times?.hard, solid = false) { onRate(Rating.HARD) }
            }
            AnswerButton(stringResource(R.string.ease_button_good), times?.good, solid = true) { onRate(Rating.GOOD) }
            if (!state.hideHardAndEasy) {
                AnswerButton(stringResource(R.string.ease_button_easy), times?.easy, solid = false) { onRate(Rating.EASY) }
            }
        }
    }
}

/** One rating: the label and the next interval, both in regular weight. Good is the solid one. */
@Composable
private fun RowScope.AnswerButton(
    label: String,
    nextTime: String?,
    solid: Boolean,
    onClick: () -> Unit,
) {
    val modifier = Modifier.weight(1f).fillMaxHeight()
    val padding = PaddingValues(horizontal = 4.dp)
    val content: @Composable RowScope.() -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextMMD(text = label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (nextTime != null) {
                TextMMD(text = nextTime, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
    if (solid) {
        ButtonMMD(onClick = onClick, modifier = modifier, shape = PanelDefaults.ButtonShape, contentPadding = padding, content = content)
    } else {
        OutlinedButtonMMD(
            onClick = onClick,
            modifier = modifier,
            shape = PanelDefaults.ButtonShape,
            contentPadding = padding,
            content = content,
        )
    }
}

private val AnswerRowHeight = 56.dp

/** Space between the card and the answer row. */
private val AnswerRowTopGap = 12.dp

/** Space below the answer row, so the buttons do not sit on the screen's bottom edge. */
private val AnswerRowBottomGap = 24.dp

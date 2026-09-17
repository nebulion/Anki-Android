/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.GroupDivider
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.InfoRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.ValueRow
import com.mudita.mmd.components.text.TextMMD
import timber.log.Timber

/**
 * Card info as a native page: the card's facts, then its review history, as rows like Settings.
 *
 * It was the backend's web page. [cardInfo] works out the same rows from the same backend call, so
 * nothing is lost but the web view: fonts, spacing and scrolling now match the rest of the app.
 * Each review is one row (date, then kind · rating · interval · ease · time) instead of a six-column
 * table, which does not fit the Kompakt's width.
 */
class CardInfoFragment : ComposeHostFragment() {
    private sealed interface Loaded {
        data class Info(
            val info: CardInfo,
        ) : Loaded

        /** The card was deleted, or its stats could not be read. */
        data object Missing : Loaded
    }

    @Composable
    override fun ScreenContent() {
        val cardId = requireArguments().getLong(KEY_CARD_ID)
        val loaded by produceState<Loaded?>(null, cardId) {
            value =
                try {
                    Loaded.Info(withCol { cardInfo(cardId) })
                } catch (e: Exception) {
                    Timber.w(e, "card info unavailable")
                    Loaded.Missing
                }
        }

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = requireArguments().getString(KEY_TITLE).orEmpty(),
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                },
            )
            when (val current = loaded) {
                null -> Message(stringResource(R.string.dialog_processing))
                Loaded.Missing -> Message(TR.cardStatsNoCard())
                is Loaded.Info -> CardInfoList(current.info, Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
        )
    }

    @Composable
    private fun CardInfoList(
        info: CardInfo,
        modifier: Modifier,
    ) {
        PagedList(modifier) {
            // one item per row: the list pages by item
            itemsIndexed(info.facts) { index, fact ->
                Column {
                    InfoRow(title = fact.label, value = fact.value)
                    if (index != info.facts.lastIndex) RowDivider()
                }
            }
            if (info.curveRevlog.isNotEmpty()) {
                item {
                    Column {
                        GroupDivider()
                        SectionTitle(TR.cardStatsFsrsForgettingCurveTitle())
                    }
                }
                item { ForgettingCurve(info) }
            }
            if (info.reviews.isNotEmpty()) {
                item {
                    Column {
                        GroupDivider()
                        SectionTitle(TR.cardStatsReviewCount())
                    }
                }
                itemsIndexed(info.reviews) { index, review ->
                    Column {
                        InfoRow(
                            title = review.date,
                            value =
                                listOf(review.kind, review.rating, review.interval, review.ease, review.timeTaken)
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" · "),
                        )
                        if (index != info.reviews.lastIndex) RowDivider()
                    }
                }
            }
        }
    }

    /** The curve, with a choice of how much history it covers once there is more than a week. */
    @Composable
    private fun ForgettingCurve(info: CardInfo) {
        val now = remember { TimeManager.time.intTimeMS() / 1000.0 }
        val allDays = remember(info) { curveMaxDays(info.curveRevlog, CurveRange.AllTime, now) }
        val default =
            when {
                allDays > 365 -> CurveRange.AllTime
                allDays > 30 -> CurveRange.Year
                allDays > 7 -> CurveRange.Month
                else -> CurveRange.Week
            }
        var range by rememberSaveable { mutableStateOf(default) }
        var isChoosing by rememberSaveable { mutableStateOf(false) }
        val points =
            remember(info, range) {
                curvePoints(info.curveRevlog, curveMaxDays(info.curveRevlog, range, now), info.decay, now)
            }
        val labels =
            mapOf(
                CurveRange.Week to TR.cardStatsFsrsForgettingCurveFirstWeek(),
                CurveRange.Month to TR.cardStatsFsrsForgettingCurveFirstMonth(),
                CurveRange.Year to TR.cardStatsFsrsForgettingCurveFirstYear(),
                CurveRange.AllTime to TR.cardStatsFsrsForgettingCurveAllTime(),
            )
        val choices =
            CurveRange.entries.filter {
                it == CurveRange.Week || it == CurveRange.Month || (it == CurveRange.Year && allDays > 30) ||
                    (it == CurveRange.AllTime && allDays > 365)
            }
        Column {
            if (allDays > 7) {
                ValueRow(title = TR.statisticsTrueRetentionRange(), value = labels.getValue(range), onClick = { isChoosing = true })
            }
            ForgettingCurveView(
                points = points,
                desiredRetention = info.desiredRetention,
                now = now,
                locale = LocalConfiguration.current.locales[0],
                modifier = Modifier.padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
            )
            TextMMD(
                text = TR.cardStatsFsrsForgettingCurveDesiredRetention() + " " + "${(info.desiredRetention * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = RowDefaults.EdgePadding),
            )
        }
        if (isChoosing) {
            ChoiceSheet(
                title = TR.statisticsTrueRetentionRange(),
                options = choices,
                selected = range,
                label = labels::getValue,
                onSelect = { range = it },
                onDismissRequest = { isChoosing = false },
            )
        }
    }

    companion object {
        const val KEY_CARD_ID = "cardId"
        const val KEY_TITLE = "title"
    }
}

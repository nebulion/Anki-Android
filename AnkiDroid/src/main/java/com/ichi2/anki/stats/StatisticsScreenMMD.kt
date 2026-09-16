// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import anki.stats.GraphPreferences
import anki.stats.GraphsResponse
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.GroupDivider
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.InfoRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.ValueRow
import com.mudita.mmd.components.text.TextMMD
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The statistics page, drawn natively from the backend's `graphs` data: the same sections as the
 * backend's web page, in the same order, each a heading, its options as rows, its chart and its
 * summary rows. Tapping a chart shows the numbers the web page showed on hover.
 *
 * [data] is null until the first load; a reload keeps the old graphs on screen until the new ones
 * arrive, as the web page did.
 */
@Composable
fun StatisticsScreenMMD(
    title: String,
    data: GraphsResponse?,
    prefs: GraphPreferences,
    revlogRange: RevlogRange,
    fmt: StatsFormat,
    locale: Locale,
    onRevlogRangeChange: (RevlogRange) -> Unit,
    onPrefsChange: (GraphPreferences) -> Unit,
    onPickDeck: () -> Unit,
    onBack: () -> Unit,
) {
    val tr = fmt.tr
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = title,
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = onBack,
                )
            },
            actions = {
                HeaderAction(icon = R.drawable.id_arrow_drop_down, contentDescription = tr.actionsDecks(), onClick = onPickDeck)
            },
        )
        if (data == null) {
            TextMMD(
                text = stringResource(R.string.dialog_processing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
            )
            return@Column
        }

        val labels = remember(fmt) { rangeLabels(fmt) }
        var historySheet by rememberSaveable { mutableStateOf(false) }
        // one open chooser at a time, named by the section that opened it
        var openChooser by rememberSaveable { mutableStateOf<String?>(null) }
        var futureRange by rememberSaveable { mutableStateOf(GraphRange.Month) }
        var reviewsRange by rememberSaveable { mutableStateOf(GraphRange.Month) }
        var reviewsTime by rememberSaveable { mutableStateOf(false) }
        var intervalRange by rememberSaveable { mutableStateOf(IntervalRange.Percentile95) }
        var stabilityRange by rememberSaveable { mutableStateOf(IntervalRange.Percentile95) }
        var difficultyRange by rememberSaveable { mutableStateOf(PercentageRange.All) }
        var retrievabilityRange by rememberSaveable { mutableStateOf(PercentageRange.All) }
        var retentionMode by rememberSaveable { mutableStateOf(RetentionMode.Summary) }
        var hoursRange by rememberSaveable { mutableStateOf(GraphRange.Year) }
        var buttonsRange by rememberSaveable { mutableStateOf(GraphRange.Year) }
        var addedRange by rememberSaveable { mutableStateOf(GraphRange.Month) }
        var calendarYear by rememberSaveable { mutableStateOf(ZonedDateTime.now().year) }
        // the graphs that follow the history range, as the page's `followRevlog`: all time with all
        // history; back to a year when the history is cut to one
        LaunchedEffect(revlogRange) {
            fun follow(range: GraphRange) =
                when {
                    revlogRange == RevlogRange.All -> GraphRange.AllTime
                    range == GraphRange.AllTime -> GraphRange.Year
                    else -> range
                }
            reviewsRange = follow(reviewsRange)
            hoursRange = follow(hoursRange)
            buttonsRange = follow(buttonsRange)
        }
        val revlogRanges = if (revlogRange == RevlogRange.All) GraphRange.entries else GraphRange.entries - GraphRange.AllTime

        val yearTitle = stringResource(R.string.mmd_stats_year)
        val rangeTitle = tr.statisticsTrueRetentionRange()
        val noData = tr.statisticsNoData()
        // the bucketing only re-runs when its inputs change, not on every redraw
        val today = remember(data) { todayLines(data, fmt) }
        val future =
            remember(data, futureRange, prefs.futureDueShowBacklog) {
                futureDue(data, fmt, futureRange, prefs.futureDueShowBacklog)
            }
        val calendar =
            remember(data, calendarYear, prefs.calendarFirstDayOfWeek, revlogRange) {
                calendar(data, fmt, calendarYear, prefs.calendarFirstDayOfWeek, revlogRange, ZonedDateTime.now(), locale)
            }
        val reviewsModel = remember(data, reviewsRange, reviewsTime) { reviews(data, fmt, reviewsRange, reviewsTime) }
        val counts = remember(data, prefs.cardCountsSeparateInactive) { cardCounts(data, fmt, prefs.cardCountsSeparateInactive) }
        val intervalsModel = remember(data, intervalRange) { intervals(data.intervals, fmt, intervalRange, fsrs = false) }
        val stabilityModel =
            remember(data, stabilityRange) { if (data.fsrs) intervals(data.stability, fmt, stabilityRange, fsrs = true) else null }
        val easeModel = remember(data) { if (data.fsrs) null else ease(data, fmt) }
        val difficultyModel = remember(data, difficultyRange) { if (data.fsrs) difficulty(data, fmt, difficultyRange) else null }
        val retrievabilityModel =
            remember(data, retrievabilityRange) { if (data.fsrs) retrievability(data, fmt, retrievabilityRange) else null }
        val retention = remember(data, retentionMode, revlogRange) { trueRetention(data, fmt, retentionMode, revlogRange) }
        val hoursModel = remember(data, hoursRange) { ChartAndTable(hours(data, fmt, hoursRange), emptyList()) }
        val buttonsModel = remember(data, buttonsRange) { buttons(data, fmt, buttonsRange) }
        val addedModel = remember(data, addedRange) { added(data, fmt, addedRange) }
        val historyTitle = stringResource(R.string.mmd_stats_history)
        val historyLabel = if (revlogRange == RevlogRange.Year) tr.statisticsRange1YearHistory() else tr.statisticsRangeAllHistory()

        PagedList(Modifier.weight(1f)) {
            item { ValueRow(title = historyTitle, value = historyLabel, onClick = { historySheet = true }) }

            section(tr.statisticsTodayTitle(), null) {
                today.forEach { line -> item { BodyLine(line) } }
            }

            section(tr.statisticsFutureDueTitle(), tr.statisticsFutureDueSubtitle()) {
                if (data.futureDue.haveBacklog) {
                    item {
                        SwitchRow(
                            title = tr.statisticsBacklogCheckbox(),
                            checked = prefs.futureDueShowBacklog,
                            onCheckedChange = { onPrefsChange(prefs.toBuilder().setFutureDueShowBacklog(it).build()) },
                        )
                    }
                }
                rangeRow("future", rangeTitle, labels.getValue(futureRange)) { openChooser = it }
                chartAndTable(future, noData)
            }

            section(tr.statisticsCalendarTitle(), null) {
                rangeRow("calendar", yearTitle, calendar.year.toString()) { openChooser = it }
                if (calendar.days == null) {
                    item { BodyLine(noData) }
                } else {
                    item {
                        var selected by remember(calendar) { mutableStateOf<CalendarDay?>(null) }
                        Column(Modifier.padding(horizontal = RowDefaults.EdgePadding)) {
                            CalendarView(calendar, selected, onSelect = { selected = it })
                            TextMMD(
                                text = selected?.detail ?: stringResource(R.string.mmd_stats_tap_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            section(
                tr.statisticsReviewsTitle(),
                if (reviewsTime) tr.statisticsReviewsTimeSubtitle() else tr.statisticsReviewsCountSubtitle(),
            ) {
                item {
                    SwitchRow(title = tr.statisticsReviewsTimeCheckbox(), checked = reviewsTime, onCheckedChange = { reviewsTime = it })
                }
                rangeRow("reviews", rangeTitle, labels.getValue(reviewsRange)) { openChooser = it }
                chartAndTable(reviewsModel, noData)
            }

            section(tr.statisticsCountsTitle(), null) {
                item {
                    SwitchRow(
                        title = tr.statisticsCountsSeparateSuspendedBuriedCards(),
                        checked = prefs.cardCountsSeparateInactive,
                        onCheckedChange = { onPrefsChange(prefs.toBuilder().setCardCountsSeparateInactive(it).build()) },
                    )
                }
                counts.chart?.let { chart ->
                    item { StackedBarView(chart, Modifier.padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp)) }
                }
                table(counts.table)
            }

            section(tr.statisticsIntervalsTitle(), tr.statisticsIntervalsSubtitle()) {
                rangeRow("intervals", rangeTitle, intervalLabel(intervalRange, labels, tr)) { openChooser = it }
                chartAndTable(intervalsModel, noData)
            }

            stabilityModel?.let { model ->
                section(tr.statisticsCardStabilityTitle(), tr.statisticsCardStabilitySubtitle()) {
                    rangeRow("stability", rangeTitle, intervalLabel(stabilityRange, labels, tr)) { openChooser = it }
                    chartAndTable(model, noData)
                }
            }
            easeModel?.let { model ->
                section(tr.statisticsCardEaseTitle(), tr.statisticsCardEaseSubtitle()) { chartAndTable(model, noData) }
            }
            difficultyModel?.let { model ->
                section(tr.statisticsCardDifficultyTitle(), tr.statisticsCardDifficultySubtitle2()) {
                    rangeRow("difficulty", rangeTitle, percentLabel(difficultyRange, tr)) { openChooser = it }
                    chartAndTable(model, noData)
                }
            }
            retrievabilityModel?.let { model ->
                section(tr.statisticsCardRetrievabilityTitle(), tr.statisticsRetrievabilitySubtitle()) {
                    rangeRow("retrievability", rangeTitle, percentLabel(retrievabilityRange, tr)) { openChooser = it }
                    chartAndTable(model, noData)
                }
            }

            section(tr.statisticsTrueRetentionTitle(), tr.statisticsTrueRetentionSubtitle()) {
                rangeRow("retention", rangeTitle, retentionLabel(retentionMode, tr)) { openChooser = it }
                table(retention)
            }

            section(tr.statisticsHoursTitle(), tr.statisticsHoursSubtitle()) {
                rangeRow("hours", rangeTitle, labels.getValue(hoursRange)) { openChooser = it }
                chartAndTable(hoursModel, noData)
            }

            section(tr.statisticsAnswerButtonsTitle(), tr.statisticsAnswerButtonsSubtitle()) {
                rangeRow("buttons", rangeTitle, labels.getValue(buttonsRange)) { openChooser = it }
                chartAndTable(buttonsModel, noData)
            }

            section(tr.statisticsAddedTitle(), tr.statisticsAddedSubtitle()) {
                rangeRow("added", rangeTitle, labels.getValue(addedRange)) { openChooser = it }
                chartAndTable(addedModel, noData)
            }
        }

        if (historySheet) {
            ChoiceSheet(
                title = stringResource(R.string.mmd_stats_history),
                options = RevlogRange.entries,
                selected = revlogRange,
                label = { if (it == RevlogRange.Year) tr.statisticsRange1YearHistory() else tr.statisticsRangeAllHistory() },
                onSelect = onRevlogRangeChange,
                onDismissRequest = { historySheet = false },
            )
        }

        @Composable
        fun <T> chooser(
            key: String,
            options: List<T>,
            selected: T,
            label: (T) -> String,
            onSelect: (T) -> Unit,
        ) {
            if (openChooser == key) {
                ChoiceSheet(
                    title = if (key == "calendar") yearTitle else rangeTitle,
                    options = options,
                    selected = selected,
                    label = label,
                    onSelect = onSelect,
                    onDismissRequest = { openChooser = null },
                )
            }
        }
        chooser("calendar", (calendar.maxYear downTo calendar.minYear).toList(), calendar.year, { it.toString() }) { calendarYear = it }
        chooser("future", GraphRange.entries, futureRange, labels::getValue) { futureRange = it }
        chooser("reviews", revlogRanges, reviewsRange, labels::getValue) { reviewsRange = it }
        chooser("intervals", IntervalRange.entries, intervalRange, { intervalLabel(it, labels, tr) }) { intervalRange = it }
        chooser("stability", IntervalRange.entries, stabilityRange, { intervalLabel(it, labels, tr) }) { stabilityRange = it }
        chooser("difficulty", PercentageRange.entries, difficultyRange, { percentLabel(it, tr) }) { difficultyRange = it }
        chooser("retrievability", PercentageRange.entries, retrievabilityRange, { percentLabel(it, tr) }) { retrievabilityRange = it }
        chooser("retention", RetentionMode.entries, retentionMode, { retentionLabel(it, tr) }) { retentionMode = it }
        chooser("hours", revlogRanges, hoursRange, labels::getValue) { hoursRange = it }
        chooser("buttons", revlogRanges, buttonsRange, labels::getValue) { buttonsRange = it }
        chooser("added", GraphRange.entries, addedRange, labels::getValue) { addedRange = it }
    }
}

/** A graph's section: a solid rule, its title and subtitle, then [content]'s items. */
private fun LazyListScope.section(
    title: String,
    subtitle: String?,
    content: LazyListScope.() -> Unit,
) {
    item {
        Column {
            GroupDivider()
            SectionTitle(title)
            subtitle?.let { BodyLine(it) }
        }
    }
    content()
}

private fun LazyListScope.rangeRow(
    key: String,
    title: String,
    value: String,
    open: (String) -> Unit,
) {
    item { ValueRow(title = title, value = value, onClick = { open(key) }) }
}

/** The chart, the tapped bar's numbers under it, then the summary rows. */
private fun LazyListScope.chartAndTable(
    content: ChartAndTable,
    noData: String,
) {
    val chart = content.chart
    if (chart == null) {
        item { BodyLine(noData) }
        return
    }
    item {
        var selected by remember(chart) { mutableStateOf<Int?>(null) }
        Column(Modifier.padding(horizontal = RowDefaults.EdgePadding)) {
            BarChartView(chart, selected, onSelect = { selected = it })
            val lines = selected?.let { chart.bars.getOrNull(it)?.detail }
            TextMMD(
                text = lines?.joinToString("\n") ?: stringResource(R.string.mmd_stats_tap_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    table(content.table)
}

private fun LazyListScope.table(rows: List<StatRow>) {
    rows.forEachIndexed { index, row ->
        item {
            Column {
                if (row.fill != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = RowDefaults.EdgePadding)) {
                        FillSwatch(row.fill)
                        InfoRow(title = row.label, value = row.value.ifEmpty { " " })
                    }
                } else {
                    InfoRow(title = row.label, value = row.value)
                }
                if (index != rows.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun BodyLine(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
    )
}

/** "1 month", "3 months", "1 year", "All time", as the page's range radios. */
private fun rangeLabels(fmt: StatsFormat): Map<GraphRange, String> =
    mapOf(
        GraphRange.Month to fmt.timeSpan(StatsFormat.TimeUnit.Months.seconds),
        GraphRange.ThreeMonths to fmt.timeSpan(3 * StatsFormat.TimeUnit.Months.seconds),
        GraphRange.Year to fmt.timeSpan(StatsFormat.TimeUnit.Years.seconds),
        GraphRange.AllTime to fmt.tr.statisticsRangeAllTime(),
    )

private fun intervalLabel(
    range: IntervalRange,
    labels: Map<GraphRange, String>,
    tr: net.ankiweb.rsdroid.Translations,
): String =
    when (range) {
        IntervalRange.Month -> labels.getValue(GraphRange.Month)
        IntervalRange.Percentile50 -> "50%"
        IntervalRange.Percentile95 -> "95%"
        IntervalRange.All -> tr.statisticsRangeAllTime()
    }

private fun percentLabel(
    range: PercentageRange,
    tr: net.ankiweb.rsdroid.Translations,
): String =
    when (range) {
        PercentageRange.Percentile50 -> "50%"
        PercentageRange.Percentile95 -> "95%"
        PercentageRange.Percentile100 -> "100%"
        PercentageRange.All -> tr.statisticsRangeAllTime()
    }

private fun retentionLabel(
    mode: RetentionMode,
    tr: net.ankiweb.rsdroid.Translations,
): String =
    when (mode) {
        RetentionMode.Young -> tr.statisticsTrueRetentionYoung()
        RetentionMode.Mature -> tr.statisticsTrueRetentionMature()
        RetentionMode.Summary -> tr.statisticsTrueRetentionAll()
    }

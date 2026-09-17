// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import anki.stats.GraphPreferences
import anki.stats.GraphsResponse
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.InfoRow
import com.ichi2.compose.mmd.NavRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.ValueRow
import com.mudita.mmd.components.text.TextMMD
import net.ankiweb.rsdroid.Translations
import java.time.ZonedDateTime
import java.util.Locale

/** A statistics section, which has a page of its own. */
enum class StatsSection {
    Today,
    FutureDue,
    Calendar,
    Reviews,
    CardCounts,
    Intervals,
    Stability,
    Ease,
    Difficulty,
    Retrievability,
    TrueRetention,
    Hours,
    Buttons,
    Added,
}

/**
 * The statistics, drawn natively from the backend's `graphs` data, laid out as Settings is
 * (owner, 2026-09-16): a list of the backend page's sections, each with a one-line summary, each
 * opening a page of its own with its options, its chart and its summary rows.
 *
 * A chart's numbers are read a bar at a time: the page opens on the latest bar with data, and the
 * arrows either side of its numbers step to the previous or next bar with data. Tapping a bar also
 * selects it, but bars can be a few pixels wide, so the arrows are the dependable way on a small
 * E Ink screen.
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
    var section by rememberSaveable { mutableStateOf<StatsSection?>(null) }
    BackHandler(enabled = section != null) { section = null }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = section?.let { sectionTitle(it, tr) } ?: title,
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = { if (section != null) section = null else onBack() },
                )
            },
            actions = {
                if (section == null) {
                    HeaderAction(icon = R.drawable.id_arrow_drop_down, contentDescription = tr.actionsDecks(), onClick = onPickDeck)
                }
            },
        )
        if (data == null) {
            BodyLine(stringResource(R.string.dialog_processing))
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
        var calendarYear by rememberSaveable { mutableIntStateOf(ZonedDateTime.now().year) }
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

        fun summary(model: ChartAndTable?): String? =
            model?.table?.firstOrNull { it.value.isNotEmpty() }?.let { "${it.label}: ${it.value}" }

        val available =
            StatsSection.entries.filter {
                when (it) {
                    StatsSection.Stability, StatsSection.Difficulty, StatsSection.Retrievability -> data.fsrs
                    StatsSection.Ease -> !data.fsrs
                    else -> true
                }
            }

        PagedList(Modifier.weight(1f)) {
            when (section) {
                null -> {
                    item {
                        Column {
                            ValueRow(title = historyTitle, value = historyLabel, onClick = { historySheet = true })
                            RowDivider()
                        }
                    }
                    available.forEachIndexed { index, entry ->
                        item {
                            Column {
                                NavRow(
                                    title = sectionTitle(entry, tr),
                                    subtitle =
                                        when (entry) {
                                            StatsSection.Today -> today.firstOrNull()
                                            StatsSection.FutureDue -> summary(future)
                                            StatsSection.Calendar -> calendar.year.toString()
                                            StatsSection.Reviews -> summary(reviewsModel)
                                            StatsSection.CardCounts -> counts.table.lastOrNull()?.let { "${it.label}: ${it.value}" }
                                            StatsSection.Intervals -> summary(intervalsModel)
                                            StatsSection.Stability -> summary(stabilityModel)
                                            StatsSection.Ease -> summary(easeModel)
                                            StatsSection.Difficulty -> summary(difficultyModel)
                                            StatsSection.Retrievability -> summary(retrievabilityModel)
                                            StatsSection.TrueRetention, StatsSection.Hours, StatsSection.Buttons -> null
                                            StatsSection.Added -> summary(addedModel)
                                        },
                                    onClick = { section = entry },
                                )
                                if (index != available.lastIndex) RowDivider()
                            }
                        }
                    }
                }
                StatsSection.Today -> today.forEach { line -> item { BodyLine(line) } }
                StatsSection.FutureDue -> {
                    subtitle(tr.statisticsFutureDueSubtitle())
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
                StatsSection.Calendar -> {
                    rangeRow("calendar", yearTitle, calendar.year.toString()) { openChooser = it }
                    if (calendar.days == null) {
                        item { BodyLine(noData) }
                    } else {
                        item { CalendarWithDetail(calendar) }
                    }
                }
                StatsSection.Reviews -> {
                    subtitle(if (reviewsTime) tr.statisticsReviewsTimeSubtitle() else tr.statisticsReviewsCountSubtitle())
                    item {
                        SwitchRow(title = tr.statisticsReviewsTimeCheckbox(), checked = reviewsTime, onCheckedChange = { reviewsTime = it })
                    }
                    rangeRow("reviews", rangeTitle, labels.getValue(reviewsRange)) { openChooser = it }
                    chartAndTable(reviewsModel, noData)
                }
                StatsSection.CardCounts -> {
                    item {
                        SwitchRow(
                            title = tr.statisticsCountsSeparateSuspendedBuriedCards(),
                            checked = prefs.cardCountsSeparateInactive,
                            onCheckedChange = { onPrefsChange(prefs.toBuilder().setCardCountsSeparateInactive(it).build()) },
                        )
                    }
                    table(counts.table)
                }
                StatsSection.Intervals -> {
                    subtitle(tr.statisticsIntervalsSubtitle())
                    rangeRow("intervals", rangeTitle, intervalLabel(intervalRange, labels, tr)) { openChooser = it }
                    chartAndTable(intervalsModel, noData)
                }
                StatsSection.Stability -> {
                    subtitle(tr.statisticsCardStabilitySubtitle())
                    rangeRow("stability", rangeTitle, intervalLabel(stabilityRange, labels, tr)) { openChooser = it }
                    stabilityModel?.let { chartAndTable(it, noData) }
                }
                StatsSection.Ease -> {
                    subtitle(tr.statisticsCardEaseSubtitle())
                    easeModel?.let { chartAndTable(it, noData) }
                }
                StatsSection.Difficulty -> {
                    subtitle(tr.statisticsCardDifficultySubtitle2())
                    rangeRow("difficulty", rangeTitle, percentLabel(difficultyRange, tr)) { openChooser = it }
                    difficultyModel?.let { chartAndTable(it, noData) }
                }
                StatsSection.Retrievability -> {
                    subtitle(tr.statisticsRetrievabilitySubtitle())
                    rangeRow("retrievability", rangeTitle, percentLabel(retrievabilityRange, tr)) { openChooser = it }
                    retrievabilityModel?.let { chartAndTable(it, noData) }
                }
                StatsSection.TrueRetention -> {
                    subtitle(tr.statisticsTrueRetentionSubtitle())
                    rangeRow("retention", rangeTitle, retentionLabel(retentionMode, tr)) { openChooser = it }
                    table(retention)
                }
                StatsSection.Hours -> {
                    subtitle(tr.statisticsHoursSubtitle())
                    rangeRow("hours", rangeTitle, labels.getValue(hoursRange)) { openChooser = it }
                    chartAndTable(hoursModel, noData)
                }
                StatsSection.Buttons -> {
                    subtitle(tr.statisticsAnswerButtonsSubtitle())
                    rangeRow("buttons", rangeTitle, labels.getValue(buttonsRange)) { openChooser = it }
                    chartAndTable(buttonsModel, noData)
                }
                StatsSection.Added -> {
                    subtitle(tr.statisticsAddedSubtitle())
                    rangeRow("added", rangeTitle, labels.getValue(addedRange)) { openChooser = it }
                    chartAndTable(addedModel, noData)
                }
            }
        }

        if (historySheet) {
            ChoiceSheet(
                title = historyTitle,
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

private fun sectionTitle(
    section: StatsSection,
    tr: Translations,
): String =
    when (section) {
        StatsSection.Today -> tr.statisticsTodayTitle()
        StatsSection.FutureDue -> tr.statisticsFutureDueTitle()
        StatsSection.Calendar -> tr.statisticsCalendarTitle()
        StatsSection.Reviews -> tr.statisticsReviewsTitle()
        StatsSection.CardCounts -> tr.statisticsCountsTitle()
        StatsSection.Intervals -> tr.statisticsIntervalsTitle()
        StatsSection.Stability -> tr.statisticsCardStabilityTitle()
        StatsSection.Ease -> tr.statisticsCardEaseTitle()
        StatsSection.Difficulty -> tr.statisticsCardDifficultyTitle()
        StatsSection.Retrievability -> tr.statisticsCardRetrievabilityTitle()
        StatsSection.TrueRetention -> tr.statisticsTrueRetentionTitle()
        StatsSection.Hours -> tr.statisticsHoursTitle()
        StatsSection.Buttons -> tr.statisticsAnswerButtonsTitle()
        StatsSection.Added -> tr.statisticsAddedTitle()
    }

private fun LazyListScope.subtitle(text: String) {
    item { BodyLine(text) }
}

private fun LazyListScope.rangeRow(
    key: String,
    title: String,
    value: String,
    open: (String) -> Unit,
) {
    item {
        Column {
            ValueRow(title = title, value = value, onClick = { open(key) })
            RowDivider()
        }
    }
}

/** The chart, the selected bar's numbers between step arrows, then the summary rows. */
private fun LazyListScope.chartAndTable(
    content: ChartAndTable,
    noData: String,
) {
    val chart = content.chart
    if (chart == null) {
        item { BodyLine(noData) }
    } else {
        item { ChartWithDetail(chart) }
    }
    table(content.table)
}

@Composable
private fun ChartWithDetail(chart: BarChart) {
    val withData = chart.bars.indices.filter { chart.bars[it].total > 0 }
    var selected by remember(chart) { mutableStateOf(withData.lastOrNull()) }
    Column(Modifier.padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp)) {
        BarChartView(chart, selected, onSelect = { selected = it })
        val current = selected
        StepRow(
            text =
                current
                    ?.let {
                        chart.bars
                            .getOrNull(it)
                            ?.detail
                            ?.joinToString("\n")
                    }.orEmpty(),
            previous = current?.let { c -> withData.lastOrNull { it < c } },
            next = current?.let { c -> withData.firstOrNull { it > c } },
            onStep = { selected = it },
        )
    }
}

@Composable
private fun CalendarWithDetail(calendar: Calendar) {
    val reviewed =
        calendar.days
            .orEmpty()
            .filter { it.level > 0 }
            .sortedWith(compareBy({ it.week }, { it.weekday }))
    var selected by remember(calendar) { mutableStateOf(reviewed.lastOrNull()) }
    Column(Modifier.padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp)) {
        CalendarView(calendar, selected, onSelect = { selected = it })
        val index = reviewed.indexOf(selected)
        StepRow(
            text = selected?.detail.orEmpty(),
            previous = if (index > 0) index - 1 else null,
            next = if (index >= 0 && index < reviewed.lastIndex) index + 1 else null,
            onStep = { selected = reviewed[it] },
        )
    }
}

/** The selected bar's or day's numbers, with arrows to the previous and next one that has data. */
@Composable
private fun StepRow(
    text: String,
    previous: Int?,
    next: Int?,
    onStep: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(pointsLeft = true, target = previous, onStep = onStep)
        TextMMD(
            text = text.ifEmpty { " " },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        StepButton(pointsLeft = false, target = next, onStep = onStep)
    }
}

@Composable
private fun StepButton(
    pointsLeft: Boolean,
    target: Int?,
    onStep: (Int) -> Unit,
) {
    IconButton(onClick = { target?.let(onStep) }, enabled = target != null, modifier = Modifier.size(48.dp)) {
        // with nowhere to step, no arrow rather than a grey one
        if (target != null) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_chevron_right_24),
                contentDescription = stringResource(if (pointsLeft) R.string.mmd_stats_previous else R.string.mmd_stats_next),
                modifier = Modifier.size(28.dp).rotate(if (pointsLeft) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun LazyListScope.table(rows: List<StatRow>) {
    rows.forEachIndexed { index, row ->
        item {
            Column {
                if (row.fill != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = RowDefaults.EdgePadding)) {
                        FillSwatch(row.fill, Modifier.size(28.dp))
                        InfoRow(title = row.label, value = row.value.ifEmpty { " " })
                    }
                } else {
                    InfoRow(title = row.label, value = row.value)
                }
                row.fraction?.let { ShareBar(it) }
                if (index != rows.lastIndex) RowDivider()
            }
        }
    }
}

/** A row's share of the whole: a solid black bar in an outlined track. */
@Composable
private fun ShareBar(fraction: Double) {
    Box(
        Modifier
            .padding(start = RowDefaults.EdgePadding, end = RowDefaults.EdgePadding, bottom = 12.dp)
            .fillMaxWidth()
            .height(12.dp)
            .border(1.dp, MaterialTheme.colorScheme.onSurface),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun BodyLine(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp),
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
    tr: Translations,
): String =
    when (range) {
        IntervalRange.Month -> labels.getValue(GraphRange.Month)
        IntervalRange.Percentile50 -> "50%"
        IntervalRange.Percentile95 -> "95%"
        IntervalRange.All -> tr.statisticsRangeAllTime()
    }

private fun percentLabel(
    range: PercentageRange,
    tr: Translations,
): String =
    when (range) {
        PercentageRange.Percentile50 -> "50%"
        PercentageRange.Percentile95 -> "95%"
        PercentageRange.Percentile100 -> "100%"
        PercentageRange.All -> tr.statisticsRangeAllTime()
    }

private fun retentionLabel(
    mode: RetentionMode,
    tr: Translations,
): String =
    when (mode) {
        RetentionMode.Young -> tr.statisticsTrueRetentionYoung()
        RetentionMode.Mature -> tr.statisticsTrueRetentionMature()
        RetentionMode.Summary -> tr.statisticsTrueRetentionAll()
    }

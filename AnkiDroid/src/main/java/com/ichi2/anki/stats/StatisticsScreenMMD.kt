// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import anki.stats.GraphPreferences
import anki.stats.GraphsResponse
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.GroupDivider
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.InfoRow
import com.ichi2.compose.mmd.LoadingIndicator
import com.ichi2.compose.mmd.NavRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.TextBlock
import com.ichi2.compose.mmd.TextPage
import com.ichi2.compose.mmd.ValueRow
import com.ichi2.compose.mmd.paragraphs
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Translations
import java.time.LocalDate
import java.time.YearMonth
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

/** A deck statistics can be shown for; a null id is the whole collection. */
data class StatsDeck(
    val id: Long?,
    /** The full name, e.g. `Italian::Verbs`. */
    val name: String,
)

/**
 * The statistics, drawn natively from the backend's `graphs` data, laid out as Settings is
 * (owner, 2026-09-16): a list of the backend page's sections, each with a one-line summary, each
 * opening a page of its own with its options, its chart and its summary rows.
 *
 * The deck is a row at the top that opens a page of decks to choose from.
 *
 * A chart's numbers are read a bar at a time: the page opens on the latest bar with data, and the
 * arrows either side of its numbers step to the previous or next bar with data. Tapping a bar also
 * selects it, but bars can be a few pixels wide, so the arrows are the dependable way on a small
 * E Ink screen.
 *
 * [data] is null while the graphs load, shown as the MMD loading indicator.
 */
@Composable
fun StatisticsScreenMMD(
    title: String,
    data: GraphsResponse?,
    prefs: GraphPreferences,
    revlogRange: RevlogRange,
    fmt: StatsFormat,
    locale: Locale,
    decks: List<StatsDeck>,
    selectedDeckId: Long?,
    onDeckSelected: (StatsDeck) -> Unit,
    onRevlogRangeChange: (RevlogRange) -> Unit,
    onPrefsChange: (GraphPreferences) -> Unit,
    dayDetails: suspend (daysAgo: Int) -> DayDetails,
    onBack: () -> Unit,
) {
    val tr = fmt.tr
    var section by rememberSaveable { mutableStateOf<StatsSection?>(null) }
    var isChoosingDeck by rememberSaveable { mutableStateOf(false) }
    var isAboutShown by rememberSaveable { mutableStateOf(false) }
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
                if (section != null) {
                    HeaderAction(icon = R.drawable.ic_help_black_24dp, contentDescription = stringResource(R.string.help), onClick = {
                        isAboutShown = true
                    })
                }
            },
        )
        val loadingLabel = stringResource(R.string.mmd_loading_statistics)
        if (data == null) {
            LoadingIndicator(loadingLabel, Modifier.weight(1f))
            if (isChoosingDeck) DeckPage(decks, selectedDeckId, tr, onDeckSelected) { isChoosingDeck = false }
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
        var calendarMonthShown by rememberSaveable { mutableStateOf(YearMonth.now()) }
        var shownDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }
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

        val rangeTitle = tr.statisticsTrueRetentionRange()
        val noData = tr.statisticsNoData()
        val historyTitle = stringResource(R.string.mmd_stats_history)
        val historyLabel = if (revlogRange == RevlogRange.Year) tr.statisticsRange1YearHistory() else tr.statisticsRangeAllHistory()

        // A section's numbers are worked out off the main thread, and only while its page is open.
        // Worked out on the main thread for every section at once, a large collection froze the
        // page, back button included.
        val today = inBackground(section == StatsSection.Today, data) { Optional(todaySummary(data)) }
        val future =
            inBackground(section == StatsSection.FutureDue, data, futureRange, prefs.futureDueShowBacklog) {
                futureDue(data, fmt, futureRange, prefs.futureDueShowBacklog)
            }
        val calendar =
            inBackground(section == StatsSection.Calendar, data, calendarMonthShown, prefs.calendarFirstDayOfWeek, revlogRange) {
                calendarMonth(data, calendarMonthShown, prefs.calendarFirstDayOfWeek, revlogRange, ZonedDateTime.now(), locale)
            }
        val reviewsModel =
            inBackground(section == StatsSection.Reviews, data, reviewsRange, reviewsTime) { reviews(data, fmt, reviewsRange, reviewsTime) }
        val counts =
            inBackground(section == StatsSection.CardCounts, data, prefs.cardCountsSeparateInactive) {
                cardCounts(data, fmt, prefs.cardCountsSeparateInactive)
            }
        val intervalsModel =
            inBackground(
                section == StatsSection.Intervals,
                data,
                intervalRange,
            ) { intervals(data.intervals, fmt, intervalRange, fsrs = false) }
        val stabilityModel =
            inBackground(
                section == StatsSection.Stability,
                data,
                stabilityRange,
            ) { intervals(data.stability, fmt, stabilityRange, fsrs = true) }
        val easeModel = inBackground(section == StatsSection.Ease, data) { ease(data, fmt) }
        val difficultyModel =
            inBackground(section == StatsSection.Difficulty, data, difficultyRange) { difficulty(data, fmt, difficultyRange) }
        val retrievabilityModel =
            inBackground(
                section == StatsSection.Retrievability,
                data,
                retrievabilityRange,
            ) { retrievability(data, fmt, retrievabilityRange) }
        val retention =
            inBackground(section == StatsSection.TrueRetention, data, retentionMode, revlogRange) {
                trueRetention(data, fmt, retentionMode, revlogRange)
            }
        val hoursModel =
            inBackground(section == StatsSection.Hours, data, hoursRange) { ChartAndTable(hours(data, fmt, hoursRange), emptyList()) }
        val buttonsModel = inBackground(section == StatsSection.Buttons, data, buttonsRange) { buttons(data, fmt, buttonsRange) }
        val addedModel = inBackground(section == StatsSection.Added, data, addedRange) { added(data, fmt, addedRange) }

        val available =
            StatsSection.entries.filter {
                when (it) {
                    StatsSection.Stability, StatsSection.Difficulty, StatsSection.Retrievability -> data.fsrs
                    StatsSection.Ease -> !data.fsrs
                    else -> true
                }
            }

        // owner, 2026-09-17: pages drew their options first and their chart and scrollbar later
        val isPending =
            when (section) {
                null -> false
                StatsSection.Today -> today == null
                StatsSection.FutureDue -> future == null
                StatsSection.Calendar -> calendar == null
                StatsSection.Reviews -> reviewsModel == null
                StatsSection.CardCounts -> counts == null
                StatsSection.Intervals -> intervalsModel == null
                StatsSection.Stability -> stabilityModel == null
                StatsSection.Ease -> easeModel == null
                StatsSection.Difficulty -> difficultyModel == null
                StatsSection.Retrievability -> retrievabilityModel == null
                StatsSection.TrueRetention -> retention == null
                StatsSection.Hours -> hoursModel == null
                StatsSection.Buttons -> buttonsModel == null
                StatsSection.Added -> addedModel == null
            }
        if (isPending) {
            LoadingIndicator(loadingLabel, Modifier.weight(1f))
        } else {
            PagedList(Modifier.weight(1f)) {
                when (section) {
                    null -> {
                        item {
                            Column {
                                ValueRow(title = tr.decksDeck(), value = title, onClick = { isChoosingDeck = true })
                                RowDivider()
                            }
                        }
                        item {
                            Column {
                                ValueRow(title = historyTitle, value = historyLabel, onClick = { historySheet = true })
                                GroupDivider()
                            }
                        }
                        available.forEachIndexed { index, entry ->
                            item {
                                Column {
                                    NavRow(title = sectionTitle(entry, tr), onClick = { section = entry })
                                    if (index != available.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
                    StatsSection.Today ->
                        when {
                            today == null -> loading(loadingLabel)
                            today.value == null -> item { BodyLine(tr.statisticsTodayNoCards()) }
                            else -> todayRows(today.value, tr, fmt)
                        }
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
                        future?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.Calendar -> {
                        if (calendar == null) {
                            loading(loadingLabel)
                        } else {
                            item {
                                MonthCalendar(
                                    month = calendar,
                                    locale = locale,
                                    onMonth = { calendarMonthShown = it },
                                    onDay = { shownDay = calendar.month.atDay(it.day) },
                                )
                            }
                        }
                    }
                    StatsSection.Reviews -> {
                        subtitle(if (reviewsTime) tr.statisticsReviewsTimeSubtitle() else tr.statisticsReviewsCountSubtitle())
                        item {
                            SwitchRow(title = tr.statisticsReviewsTimeCheckbox(), checked = reviewsTime, onCheckedChange = {
                                reviewsTime =
                                    it
                            })
                        }
                        rangeRow("reviews", rangeTitle, labels.getValue(reviewsRange)) { openChooser = it }
                        reviewsModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.CardCounts -> {
                        item {
                            SwitchRow(
                                title = tr.statisticsCountsSeparateSuspendedBuriedCards(),
                                checked = prefs.cardCountsSeparateInactive,
                                onCheckedChange = { onPrefsChange(prefs.toBuilder().setCardCountsSeparateInactive(it).build()) },
                            )
                        }
                        counts?.let { table(it.table) } ?: loading(loadingLabel)
                    }
                    StatsSection.Intervals -> {
                        subtitle(tr.statisticsIntervalsSubtitle())
                        rangeRow("intervals", rangeTitle, intervalLabel(intervalRange, labels, tr)) { openChooser = it }
                        intervalsModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.Stability -> {
                        subtitle(tr.statisticsCardStabilitySubtitle())
                        rangeRow("stability", rangeTitle, intervalLabel(stabilityRange, labels, tr)) { openChooser = it }
                        stabilityModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.Ease -> {
                        subtitle(tr.statisticsCardEaseSubtitle())
                        easeModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.Difficulty -> {
                        subtitle(tr.statisticsCardDifficultySubtitle2())
                        rangeRow("difficulty", rangeTitle, percentLabel(difficultyRange, tr)) { openChooser = it }
                        difficultyModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.Retrievability -> {
                        subtitle(tr.statisticsRetrievabilitySubtitle())
                        rangeRow("retrievability", rangeTitle, percentLabel(retrievabilityRange, tr)) { openChooser = it }
                        retrievabilityModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.TrueRetention -> {
                        subtitle(tr.statisticsTrueRetentionSubtitle())
                        rangeRow("retention", rangeTitle, retentionLabel(retentionMode, tr)) { openChooser = it }
                        retention?.let { table(it) } ?: loading(loadingLabel)
                    }
                    StatsSection.Hours -> {
                        subtitle(tr.statisticsHoursSubtitle())
                        rangeRow("hours", rangeTitle, labels.getValue(hoursRange)) { openChooser = it }
                        hoursModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.Buttons -> {
                        subtitle(tr.statisticsAnswerButtonsSubtitle())
                        rangeRow("buttons", rangeTitle, labels.getValue(buttonsRange)) { openChooser = it }
                        buttonsModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
                    StatsSection.Added -> {
                        subtitle(tr.statisticsAddedSubtitle())
                        rangeRow("added", rangeTitle, labels.getValue(addedRange)) { openChooser = it }
                        addedModel?.let { chartAndTable(it, noData) } ?: loading(loadingLabel)
                    }
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
        if (isChoosingDeck) DeckPage(decks, selectedDeckId, tr, onDeckSelected) { isChoosingDeck = false }

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
                    title = rangeTitle,
                    options = options,
                    selected = selected,
                    label = label,
                    onSelect = onSelect,
                    onDismissRequest = { openChooser = null },
                )
            }
        }
        shownDay?.let { date ->
            // a review's day counts from the day the collection's day starts, as the calendar does
            val today = ZonedDateTime.now().minusHours(data.rolloverHour.toLong()).toLocalDate()
            DayPage(
                date,
                java.time.temporal.ChronoUnit.DAYS
                    .between(date, today)
                    .toInt(),
                dayDetails,
                fmt,
                locale,
                tr,
            ) {
                shownDay = null
            }
        }
        if (isAboutShown) {
            section?.let { shown ->
                TextPage(title = sectionTitle(shown, tr), blocks = aboutBlocks(shown, tr), onClose = { isAboutShown = false })
            }
        }
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

/**
 * [compute]'s result, worked out on a background thread while [active]; null until first worked
 * out, and while not [active]. Worked out again when a key changes, keeping the last result on screen
 * meanwhile, so an option change repaints the page once rather than blanking it first.
 */
@Composable
private fun <T> inBackground(
    active: Boolean,
    vararg keys: Any?,
    compute: () -> T,
): T? {
    val result by produceState<T?>(null, active, *keys) {
        value = if (active) withContext(Dispatchers.Default) { compute() } else null
    }
    return result
}

private fun LazyListScope.loading(label: String) {
    item { LoadingIndicator(label, Modifier.height(240.dp)) }
}

/**
 * Choosing the deck the statistics are for, on a page of its own: all decks first, then every
 * deck by its place in the deck list, subdecks indented. Choosing one closes the page.
 */
@Composable
private fun DeckPage(
    decks: List<StatsDeck>,
    selectedId: Long?,
    tr: Translations,
    onSelect: (StatsDeck) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // the window runs under the status bar: pad the page below it, keeping the white behind it
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
            ScreenHeader(
                title = tr.decksDeck(),
                navigationIcon = {
                    HeaderAction(icon = R.drawable.close_icon, contentDescription = stringResource(R.string.close), onClick = onClose)
                },
            )
            PagedList(Modifier.weight(1f)) {
                items(decks.size) { index ->
                    val deck = decks[index]
                    val depth = if (deck.id == null) 0 else deck.name.split("::").size - 1
                    Column {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = RowDefaults.MinHeight)
                                    .selectable(selected = deck.id == selectedId, role = Role.RadioButton) {
                                        onSelect(deck)
                                        onClose()
                                    }.padding(start = RowDefaults.EdgePadding + (depth * 20).dp, end = RowDefaults.EdgePadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButtonMMD(selected = deck.id == selectedId, onClick = null)
                            TextMMD(
                                text = deck.name.substringAfterLast("::"),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                        if (index == 0) {
                            GroupDivider()
                        } else if (index != decks.lastIndex) {
                            RowDivider()
                        }
                    }
                }
            }
        }
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

/** `Optional` for a background result that can itself be null: null means still being worked out. */
private data class Optional<T>(
    val value: T?,
)

/** Today (owner's layout A): cards, minutes and seconds per card large, then the rest as rows. */
private fun LazyListScope.todayRows(
    today: TodaySummary,
    tr: Translations,
    fmt: StatsFormat,
) {
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 16.dp)) {
            BigNumber(fmt.number(today.cards), stringResource(R.string.mmd_filter_cards), Modifier.weight(1f))
            BigNumber(fmt.number(today.minutes), stringResource(R.string.mmd_today_minutes), Modifier.weight(1f))
            BigNumber("${today.secondsPerCard}s", stringResource(R.string.mmd_today_per_card), Modifier.weight(1f))
        }
    }
    item {
        Column {
            GroupDivider()
            InfoRow(title = tr.studyingAgain(), value = "${fmt.number(today.again)} · ${fmt.number(today.againPercent, 0)}%")
            RowDivider()
        }
    }
    item {
        Column {
            InfoRow(
                title = stringResource(R.string.mmd_today_mature),
                value =
                    if (today.matureTotal == 0) {
                        tr.statisticsTodayNoMatureCards()
                    } else {
                        stringResource(
                            R.string.mmd_today_of,
                            fmt.number(today.matureCorrect),
                            fmt.number(today.matureTotal),
                            fmt.number(today.matureCorrect * 100.0 / today.matureTotal, 0),
                        )
                    },
            )
            RowDivider()
        }
    }
    item {
        InfoRow(
            title = stringResource(R.string.mmd_today_by_kind),
            value = tr.statisticsTodayTypeCounts(today.learn, today.review, today.relearn, today.filtered),
        )
    }
}

/** A number large and bold over its label, as the deck page's counts. */
@Composable
private fun BigNumber(
    number: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextMMD(text = number, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, maxLines = 1)
        TextMMD(text = label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/**
 * A month of the calendar (owner's layout A): a square per day, darker the more reviews it had,
 * as the backend's page shades it; each square shows the day and, when there were any, its reviews,
 * in white on the darker squares. Arrows change the month; a key underneath explains the shades.
 * Tapping a day opens what was studied on it.
 */
@Composable
private fun MonthCalendar(
    month: CalendarMonth,
    locale: Locale,
    onMonth: (YearMonth) -> Unit,
    onDay: (MonthDay) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(pointsLeft = true, enabled = month.month.isAfter(month.first)) { onMonth(month.month.minusMonths(1)) }
            TextMMD(
                text =
                    month.month.format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("LLLL yyyy", locale),
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow(pointsLeft = false, enabled = month.month.isBefore(month.last)) { onMonth(month.month.plusMonths(1)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            month.weekdayLabels.forEach {
                TextMMD(text = it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        month.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(46.dp).padding(2.dp)) {
                        if (day != null) DaySquare(day, ink, onClick = { onDay(day) })
                    }
                }
            }
        }
        // the key: what the shades mean
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextMMD(text = stringResource(R.string.mmd_calendar_fewer), style = MaterialTheme.typography.bodySmall)
            listOf(0f, 0.2f, 0.5f, 0.75f, 1f).forEach { shade ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(20.dp)
                        .background(shadeColor(shade))
                        .border(1.dp, ink),
                )
            }
            TextMMD(text = stringResource(R.string.mmd_calendar_more), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DaySquare(
    day: MonthDay,
    ink: Color,
    onClick: () -> Unit,
) {
    val onShade = if (day.shade > 0.55f) Color.White else Color.Black
    Box(
        Modifier
            .fillMaxSize()
            .background(shadeColor(day.shade))
            .then(if (day.isFuture) Modifier else Modifier.border(1.dp, ink).clickable(onClick = onClick)),
    ) {
        TextMMD(
            text = day.day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = onShade,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
        )
        if (day.reviews > 0) {
            TextMMD(
                text = day.reviews.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = onShade,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            )
        }
    }
}

/** White for no reviews, then greys to black for the busiest day. */
private fun shadeColor(shade: Float): Color {
    val level = 1f - shade.coerceIn(0f, 1f)
    return Color(level, level, level)
}

@Composable
private fun MonthArrow(
    pointsLeft: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        if (enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_chevron_right_24),
                contentDescription = stringResource(if (pointsLeft) R.string.mmd_stats_previous else R.string.mmd_stats_next),
                modifier = Modifier.size(28.dp).rotate(if (pointsLeft) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A section's About page: what it shows, in plain words, and Anki's own help where it has some. */
@Composable
private fun aboutBlocks(
    section: StatsSection,
    tr: Translations,
): List<TextBlock> {
    val about =
        stringResource(
            when (section) {
                StatsSection.Today -> R.string.mmd_about_today
                StatsSection.FutureDue -> R.string.mmd_about_future_due
                StatsSection.Calendar -> R.string.mmd_about_calendar
                StatsSection.Reviews -> R.string.mmd_about_reviews
                StatsSection.CardCounts -> R.string.mmd_about_card_counts
                StatsSection.Intervals -> R.string.mmd_about_intervals
                StatsSection.Stability -> R.string.mmd_about_stability
                StatsSection.Ease -> R.string.mmd_about_ease
                StatsSection.Difficulty -> R.string.mmd_about_difficulty
                StatsSection.Retrievability -> R.string.mmd_about_retrievability
                StatsSection.TrueRetention -> R.string.mmd_about_true_retention
                StatsSection.Hours -> R.string.mmd_about_hours
                StatsSection.Buttons -> R.string.mmd_about_buttons
                StatsSection.Added -> R.string.mmd_about_added
            },
        )
    return buildList {
        add(TextBlock.Heading(stringResource(R.string.mmd_about_what)))
        addAll(paragraphs(about))
        if (section == StatsSection.TrueRetention) {
            add(TextBlock.Heading(tr.statisticsTrueRetentionTitle()))
            addAll(paragraphs(tr.statisticsTrueRetentionTooltip().replace(Regex("<[^>]+>"), "")))
        }
    }
}

/**
 * What was studied on one day, on a page of its own (owner, 2026-09-17): how many reviews and how
 * long, how many were Again, the kinds of card, and the decks studied, busiest first.
 */
@Composable
private fun DayPage(
    date: LocalDate,
    daysAgo: Int,
    load: suspend (daysAgo: Int) -> DayDetails,
    fmt: StatsFormat,
    locale: Locale,
    tr: Translations,
    onClose: () -> Unit,
) {
    val details by produceState<DayDetails?>(null, daysAgo) { value = load(daysAgo) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // the window runs under the status bar: pad the page below it, keeping the white behind it
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
            ScreenHeader(
                title =
                    date.format(
                        java.time.format.DateTimeFormatter
                            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                            .withLocale(locale),
                    ),
                navigationIcon = {
                    HeaderAction(icon = R.drawable.close_icon, contentDescription = stringResource(R.string.close), onClick = onClose)
                },
            )
            val current = details
            when {
                current == null -> LoadingIndicator(stringResource(R.string.mmd_loading_statistics), Modifier.weight(1f))
                current.reviews == 0 -> BodyLine(stringResource(R.string.mmd_day_no_reviews))
                else ->
                    PagedList(Modifier.weight(1f)) {
                        item {
                            Column {
                                InfoRow(title = tr.statisticsReviews(current.reviews), value = fmt.timeSpan(current.seconds.toDouble()))
                                RowDivider()
                            }
                        }
                        item {
                            Column {
                                InfoRow(
                                    title = tr.studyingAgain(),
                                    value = "${fmt.number(current.again)} · ${fmt.number(current.again * 100.0 / current.reviews, 0)}%",
                                )
                                RowDivider()
                            }
                        }
                        item {
                            InfoRow(
                                title = stringResource(R.string.mmd_today_by_kind),
                                value = tr.statisticsTodayTypeCounts(current.learn, current.review, current.relearn, current.filtered),
                            )
                        }
                        item { SectionTitle(stringResource(R.string.mmd_day_decks)) }
                        current.decks.forEachIndexed { index, (name, count) ->
                            item {
                                Column {
                                    InfoRow(title = name, value = fmt.number(count))
                                    if (index != current.decks.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
            }
        }
    }
}

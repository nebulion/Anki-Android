// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckoptions

import anki.deck_config.DeckConfig.Config.AnswerAction
import anki.deck_config.DeckConfig.Config.LeechAction
import anki.deck_config.DeckConfig.Config.NewCardGatherPriority
import anki.deck_config.DeckConfig.Config.NewCardInsertOrder
import anki.deck_config.DeckConfig.Config.NewCardSortOrder
import anki.deck_config.DeckConfig.Config.QuestionAction
import anki.deck_config.DeckConfig.Config.ReviewCardOrder
import anki.deck_config.DeckConfig.Config.ReviewMix
import net.ankiweb.rsdroid.Translations
import kotlin.math.min
import kotlin.math.roundToInt

/** A row of the deck options page. */
sealed interface OptionEntry {
    /** A section's heading; its help lists what each of the section's settings does. */
    data class Heading(
        val title: String,
        val help: List<Help>,
    ) : OptionEntry

    data class Switch(
        val title: String,
        val checked: Boolean,
        val onChange: (Boolean) -> Unit,
    ) : OptionEntry

    /** A setting and its value; tapping opens [editor]. */
    data class Value(
        val title: String,
        val value: String,
        val editor: Editor,
    ) : OptionEntry

    data class Action(
        val title: String,
        val subtitle: String?,
        val onClick: () -> Unit,
    ) : OptionEntry

    /** The page's yellow and red notes, e.g. "Your maximum interval is very short". */
    data class Warning(
        val text: String,
    ) : OptionEntry
}

data class Help(
    val title: String,
    val text: String,
)

/** What a [OptionEntry.Value] opens. */
sealed interface Editor {
    val title: String
    val help: String?

    data class Integer(
        override val title: String,
        override val help: String?,
        val value: Int,
        val min: Int,
        val max: Int,
        val onSet: (Int) -> Unit,
    ) : Editor

    /** A decimal; with [percent], typed and shown as a percentage of 1. */
    data class Decimal(
        override val title: String,
        override val help: String?,
        val value: Float,
        val min: Float,
        val max: Float,
        val percent: Boolean,
        val onSet: (Float) -> Unit,
    ) : Editor

    data class Text(
        override val title: String,
        override val help: String?,
        val value: String,
        /** A message for text that can't be used, or null. */
        val validate: (String) -> String?,
        val onSet: (String) -> Unit,
    ) : Editor

    data class Choice(
        override val title: String,
        override val help: String?,
        val options: List<Pair<Int, String>>,
        val selected: Int,
        val onSet: (Int) -> Unit,
    ) : Editor

    /** A daily limit: which scope it is set on, and the number. */
    data class Limit(
        override val title: String,
        override val help: String?,
        val kind: LimitKind,
    ) : Editor

    /** The desired retention: the preset's or this deck's, and the percentage. */
    data class Retention(
        override val title: String,
        override val help: String?,
    ) : Editor
}

/** Labels the entries need that the backend doesn't translate. */
data class OptionLabels(
    val invalidSteps: String,
    val invalidDate: String,
    val invalidParams: String,
    val search: String,
)

/** The FSRS actions, which the host runs with progress and reports in a panel. */
interface FsrsActions {
    fun optimize()

    fun evaluate()

    fun saveAndOptimizeAll()
}

/**
 * The page's rows in the backend page's order: daily limits, new cards, lapses, display order,
 * FSRS, burying, audio, timer, auto advance, easy days, advanced. Settings the page hides for the
 * scheduler in use are left out here too.
 */
fun optionEntries(
    state: DeckOptionsState,
    tr: Translations,
    labels: OptionLabels,
    fsrsActions: FsrsActions,
): List<OptionEntry> =
    buildList {
        val config = state.current
        val fsrs = state.fsrs

        fun value(
            title: String,
            value: String,
            editor: Editor,
        ) = add(OptionEntry.Value(title, value, editor))

        fun int(
            title: String,
            help: String,
            value: Int,
            min: Int = 0,
            max: Int = 9999,
            set: (Int) -> Unit,
        ) = value(title, value.toString(), Editor.Integer(title, help, value, min, max, set))

        fun decimal(
            title: String,
            help: String,
            value: Float,
            min: Float,
            max: Float,
            percent: Boolean = false,
            set: (Float) -> Unit,
        ) = value(title, formatDecimal(value, percent), Editor.Decimal(title, help, value, min, max, percent, set))

        fun <T> choice(
            title: String,
            help: String,
            options: List<Pair<T, String>>,
            selected: T,
            number: (T) -> Int,
            fromNumber: (Int) -> T,
            set: (T) -> Unit,
        ) = value(
            title,
            options.firstOrNull { it.first == selected }?.second.orEmpty(),
            Editor.Choice(title, help, options.map { number(it.first) to it.second }, number(selected)) { set(fromNumber(it)) },
        )

        fun switch(
            title: String,
            checked: Boolean,
            set: (Boolean) -> Unit,
        ) = add(OptionEntry.Switch(title, checked, set))

        fun warning(text: String) {
            if (text.isNotEmpty()) add(OptionEntry.Warning(text))
        }

        // ------------------------------------------------------------------ daily limits
        val v3Extra = "\n\n" + tr.deckConfigLimitDeckV3() + "\n\n" + tr.deckConfigTabDescription()
        val newHelp = tr.deckConfigNewLimitTooltip() + v3Extra
        val reviewHelp = tr.deckConfigReviewLimitTooltip() + "\n\n" + tr.deckConfigLimitInterdayBoundByReviews() + v3Extra
        val ignoreHelp = tr.deckConfigAffectsEntireCollection() + "\n\n" + tr.deckConfigNewCardsIgnoreReviewLimitTooltip()
        val parentHelp = tr.deckConfigAffectsEntireCollection() + "\n\n" + tr.deckConfigApplyAllParentLimitsTooltip()
        add(
            OptionEntry.Heading(
                tr.deckConfigDailyLimits(),
                listOf(
                    Help(tr.schedulingNewCardsday(), newHelp),
                    Help(tr.schedulingMaximumReviewsday(), reviewHelp),
                    Help(tr.deckConfigNewCardsIgnoreReviewLimit(), ignoreHelp),
                    Help(tr.deckConfigApplyAllParentLimits(), parentHelp),
                ),
            ),
        )
        val newValue = state.limitValue(LimitKind.New)
        val reviewValue = state.limitValue(LimitKind.Review)
        value(
            tr.schedulingNewCardsday(),
            limitText(tr, newValue, state.limits.scope(LimitKind.New)),
            Editor.Limit(tr.schedulingNewCardsday(), newHelp, LimitKind.New),
        )
        value(
            tr.schedulingMaximumReviewsday(),
            limitText(tr, reviewValue, state.limits.scope(LimitKind.Review)),
            Editor.Limit(tr.schedulingMaximumReviewsday(), reviewHelp, LimitKind.Review),
        )
        val expected = min(9999, newValue * 10)
        warning(if (expected > reviewValue) tr.deckConfigReviewsTooLow(newValue, expected) else "")
        switch(tr.deckConfigNewCardsIgnoreReviewLimit(), state.newCardsIgnoreReviewLimit) { state.newCardsIgnoreReviewLimit = it }
        switch(tr.deckConfigApplyAllParentLimits(), state.applyAllParentLimits) { state.applyAllParentLimits = it }

        // ------------------------------------------------------------------ new cards
        val lastLearnStepDays = config.learnStepsList.lastOrNull()?.let { it / 60 / 24 } ?: 0f
        add(
            OptionEntry.Heading(
                tr.schedulingNewCards(),
                listOfNotNull(
                    Help(tr.deckConfigLearningSteps(), tr.deckConfigLearningStepsTooltip()),
                    Help(tr.schedulingGraduatingInterval(), tr.deckConfigGraduatingIntervalTooltip()).takeUnless { fsrs },
                    Help(tr.schedulingEasyInterval(), tr.deckConfigEasyIntervalTooltip()).takeUnless { fsrs },
                    Help(tr.deckConfigNewInsertionOrder(), tr.deckConfigNewInsertionOrderTooltip()),
                ),
            ),
        )
        steps(tr.deckConfigLearningSteps(), tr.deckConfigLearningStepsTooltip(), config.learnStepsList, labels) { steps ->
            state.updateConfig { clearLearnSteps().addAllLearnSteps(steps) }
        }
        warning(if (fsrs && lastLearnStepDays >= 1) tr.deckConfigStepsTooLargeForFsrs() else "")
        if (!fsrs) {
            int(tr.schedulingGraduatingInterval(), tr.deckConfigGraduatingIntervalTooltip(), config.graduatingIntervalGood) {
                state.updateConfig { graduatingIntervalGood = it }
            }
            warning(if (lastLearnStepDays > config.graduatingIntervalGood) tr.deckConfigLearningStepAboveGraduatingInterval() else "")
            int(tr.schedulingEasyInterval(), tr.deckConfigEasyIntervalTooltip(), config.graduatingIntervalEasy) {
                state.updateConfig { graduatingIntervalEasy = it }
            }
            warning(if (config.graduatingIntervalGood > config.graduatingIntervalEasy) tr.deckConfigGoodAboveEasy() else "")
        }
        choice(
            tr.deckConfigNewInsertionOrder(),
            tr.deckConfigNewInsertionOrderTooltip(),
            listOf(
                NewCardInsertOrder.NEW_CARD_INSERT_ORDER_DUE to tr.deckConfigNewInsertionOrderSequential(),
                NewCardInsertOrder.NEW_CARD_INSERT_ORDER_RANDOM to tr.deckConfigNewInsertionOrderRandom(),
            ),
            config.newCardInsertOrder,
            { it.number },
            { NewCardInsertOrder.forNumber(it) },
        ) { state.updateConfig { newCardInsertOrder = it } }
        warning(
            if (config.newCardInsertOrder ==
                NewCardInsertOrder.NEW_CARD_INSERT_ORDER_RANDOM
            ) {
                tr.deckConfigNewInsertionOrderRandomWithV3()
            } else {
                ""
            },
        )

        // ------------------------------------------------------------------ lapses
        val lastRelearnStepDays = config.relearnStepsList.lastOrNull()?.let { it / 60 / 24 } ?: 0f
        add(
            OptionEntry.Heading(
                tr.schedulingLapses(),
                listOfNotNull(
                    Help(tr.deckConfigRelearningSteps(), tr.deckConfigRelearningStepsTooltip()),
                    Help(tr.schedulingMinimumInterval(), tr.deckConfigMinimumIntervalTooltip()).takeUnless { fsrs },
                    Help(tr.schedulingLeechThreshold(), tr.deckConfigLeechThresholdTooltip()),
                    Help(tr.schedulingLeechAction(), tr.deckConfigLeechActionTooltip()),
                ),
            ),
        )
        steps(tr.deckConfigRelearningSteps(), tr.deckConfigRelearningStepsTooltip(), config.relearnStepsList, labels) { steps ->
            state.updateConfig { clearRelearnSteps().addAllRelearnSteps(steps) }
        }
        warning(if (fsrs && lastRelearnStepDays >= 1) tr.deckConfigStepsTooLargeForFsrs() else "")
        if (!fsrs) {
            int(tr.schedulingMinimumInterval(), tr.deckConfigMinimumIntervalTooltip(), config.minimumLapseInterval, min = 1) {
                state.updateConfig { minimumLapseInterval = it }
            }
        }
        warning(if (!fsrs && lastRelearnStepDays > config.minimumLapseInterval) tr.deckConfigRelearningStepsAboveMinimumInterval() else "")
        int(tr.schedulingLeechThreshold(), tr.deckConfigLeechThresholdTooltip(), config.leechThreshold, min = 1) {
            state.updateConfig { leechThreshold = it }
        }
        choice(
            tr.schedulingLeechAction(),
            tr.deckConfigLeechActionTooltip(),
            listOf(
                LeechAction.LEECH_ACTION_SUSPEND to tr.actionsSuspendCard(),
                LeechAction.LEECH_ACTION_TAG_ONLY to tr.schedulingTagOnly(),
            ),
            config.leechAction,
            { it.number },
            { LeechAction.forNumber(it) },
        ) { state.updateConfig { leechAction = it } }

        // ------------------------------------------------------------------ display order
        val currentDeckNote = "\n\n" + tr.deckConfigDisplayOrderWillUseCurrentDeck()
        add(
            OptionEntry.Heading(
                tr.deckConfigOrderingTitle(),
                listOf(
                    Help(tr.deckConfigNewGatherPriority(), tr.deckConfigNewGatherPriorityTooltip2() + currentDeckNote),
                    Help(tr.deckConfigNewCardSortOrder(), tr.deckConfigNewCardSortOrderTooltip2() + currentDeckNote),
                    Help(tr.deckConfigNewReviewPriority(), tr.deckConfigNewReviewPriorityTooltip() + currentDeckNote),
                    Help(tr.deckConfigInterdayStepPriority(), tr.deckConfigInterdayStepPriorityTooltip() + currentDeckNote),
                    Help(tr.deckConfigReviewSortOrder(), tr.deckConfigReviewSortOrderTooltip() + currentDeckNote),
                ),
            ),
        )
        choice(
            tr.deckConfigNewGatherPriority(),
            tr.deckConfigNewGatherPriorityTooltip2() + currentDeckNote,
            listOf(
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_DECK to tr.deckConfigNewGatherPriorityDeck(),
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_DECK_THEN_RANDOM_NOTES to
                    tr.deckConfigNewGatherPriorityDeckThenRandomNotes(),
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_LOWEST_POSITION to tr.deckConfigNewGatherPriorityPositionLowestFirst(),
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_HIGHEST_POSITION to tr.deckConfigNewGatherPriorityPositionHighestFirst(),
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_RANDOM_NOTES to tr.deckConfigNewGatherPriorityRandomNotes(),
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_RANDOM_CARDS to tr.deckConfigNewGatherPriorityRandomCards(),
            ),
            config.newCardGatherPriority,
            { it.number },
            { NewCardGatherPriority.forNumber(it) },
        ) { gather ->
            state.updateConfig {
                newCardGatherPriority = gather
                // a sort order the gather order rules out falls back to the first
                if (newCardSortOrder in DeckOptionsState.disabledSortOrders(gather)) newCardSortOrder = NewCardSortOrder.forNumber(0)
            }
        }
        val disabledSorts = DeckOptionsState.disabledSortOrders(config.newCardGatherPriority)
        choice(
            tr.deckConfigNewCardSortOrder(),
            tr.deckConfigNewCardSortOrderTooltip2() + currentDeckNote,
            listOf(
                NewCardSortOrder.NEW_CARD_SORT_ORDER_TEMPLATE to tr.deckConfigSortOrderTemplateThenGather(),
                NewCardSortOrder.NEW_CARD_SORT_ORDER_NO_SORT to tr.deckConfigSortOrderGather(),
                NewCardSortOrder.NEW_CARD_SORT_ORDER_TEMPLATE_THEN_RANDOM to tr.deckConfigSortOrderCardTemplateThenRandom(),
                NewCardSortOrder.NEW_CARD_SORT_ORDER_RANDOM_NOTE_THEN_TEMPLATE to tr.deckConfigSortOrderRandomNoteThenTemplate(),
                NewCardSortOrder.NEW_CARD_SORT_ORDER_RANDOM_CARD to tr.deckConfigSortOrderRandom(),
            ).filter { it.first !in disabledSorts },
            config.newCardSortOrder,
            { it.number },
            { NewCardSortOrder.forNumber(it) },
        ) { state.updateConfig { newCardSortOrder = it } }
        val mixes =
            listOf(
                ReviewMix.REVIEW_MIX_MIX_WITH_REVIEWS to tr.deckConfigReviewMixMixWithReviews(),
                ReviewMix.REVIEW_MIX_AFTER_REVIEWS to tr.deckConfigReviewMixShowAfterReviews(),
                ReviewMix.REVIEW_MIX_BEFORE_REVIEWS to tr.deckConfigReviewMixShowBeforeReviews(),
            )
        choice(
            tr.deckConfigNewReviewPriority(),
            tr.deckConfigNewReviewPriorityTooltip() + currentDeckNote,
            mixes,
            config.newMix,
            { it.number },
            { ReviewMix.forNumber(it) },
        ) { state.updateConfig { newMix = it } }
        choice(
            tr.deckConfigInterdayStepPriority(),
            tr.deckConfigInterdayStepPriorityTooltip() + currentDeckNote,
            mixes,
            config.interdayLearningMix,
            { it.number },
            { ReviewMix.forNumber(it) },
        ) { state.updateConfig { interdayLearningMix = it } }
        choice(
            tr.deckConfigReviewSortOrder(),
            tr.deckConfigReviewSortOrderTooltip() + currentDeckNote,
            reviewOrderChoices(tr, fsrs),
            config.reviewOrder,
            { it.number },
            { ReviewCardOrder.forNumber(it) },
        ) { state.updateConfig { reviewOrder = it } }

        // ------------------------------------------------------------------ FSRS
        val retentionHelp = tr.deckConfigDesiredRetentionTooltip() + "\n\n" + tr.deckConfigDesiredRetentionTooltip2()
        val paramsHelp = tr.deckConfigWeightsTooltip2() + "\n\n" + tr.deckConfigComputeOptimalWeightsTooltip2()
        val healthHelp =
            tr.deckConfigAffectsEntireCollection() + "\n\n" + tr.deckConfigHealthCheckTooltip1() + "\n\n" +
                tr.deckConfigHealthCheckTooltip2()
        add(
            OptionEntry.Heading(
                "FSRS",
                listOf(
                    Help("FSRS", tr.deckConfigFsrsTooltip()),
                    Help(tr.deckConfigDesiredRetention(), retentionHelp),
                    Help(tr.deckConfigWeights(), paramsHelp),
                    Help(tr.deckConfigRescheduleCardsOnChange(), tr.deckConfigRescheduleCardsOnChangeTooltip()),
                    Help(tr.deckConfigHealthCheck(), healthHelp),
                ),
            ),
        )
        switch("FSRS", fsrs) { state.fsrs = it }
        if (fsrs) {
            val retention = state.effectiveDesiredRetention
            value(
                tr.deckConfigDesiredRetention(),
                formatDecimal(retention, percent = true) +
                    " · " + if (state.limits.hasDesiredRetention()) tr.deckConfigDeckOnly() else tr.deckConfigSharedPreset(),
                Editor.Retention(tr.deckConfigDesiredRetention(), retentionHelp),
            )
            val rounded = (retention * 100).roundToInt() / 100f
            warning(
                when {
                    rounded < 0.8f -> tr.deckConfigDesiredRetentionTooLow()
                    rounded > 0.95f -> tr.deckConfigDesiredRetentionTooHigh()
                    else -> ""
                },
            )
            val params = state.fsrsParams()
            value(
                tr.deckConfigWeights(),
                paramsToString(params),
                Editor.Text(tr.deckConfigWeights(), paramsHelp, paramsToString(config.fsrsParams6List), {
                    if (stringToParams(it) == null) labels.invalidParams else null
                }) { text -> state.updateConfig { clearFsrsParams6().addAllFsrsParams6(stringToParams(text).orEmpty()) } },
            )
            val defaultSearch = "preset:\"${state.currentNameForSearch}\" -is:suspended"
            value(
                labels.search,
                config.paramSearch.ifEmpty { defaultSearch },
                Editor.Text(labels.search, null, config.paramSearch, { null }) { state.updateConfig { paramSearch = it } },
            )
            switch(tr.deckConfigRescheduleCardsOnChange(), state.fsrsReschedule) { state.fsrsReschedule = it }
            if (state.fsrsReschedule) warning(tr.deckConfigRescheduleCardsWarning())
            switch(tr.deckConfigSlowSuffix(tr.deckConfigHealthCheck()), state.fsrsHealthCheck) { state.fsrsHealthCheck = it }
            add(OptionEntry.Action(tr.deckConfigOptimizeButton(), null, fsrsActions::optimize))
            if (state.legacyEvaluate) add(OptionEntry.Action(tr.deckConfigEvaluateButton(), null, fsrsActions::evaluate))
            warning(if (state.daysSinceLastOptimization > 30) tr.deckConfigTimeToOptimize() else "")
            add(OptionEntry.Action(tr.deckConfigSaveAndOptimize(), null, fsrsActions::saveAndOptimizeAll))
        }

        // ------------------------------------------------------------------ burying
        val priority = "\n\n" + tr.deckConfigBuryPriorityTooltip()
        add(
            OptionEntry.Heading(
                tr.deckConfigBuryTitle(),
                listOf(
                    Help(tr.deckConfigBuryNewSiblings(), tr.deckConfigBuryNewTooltip() + priority),
                    Help(tr.deckConfigBuryReviewSiblings(), tr.deckConfigBuryReviewTooltip() + priority),
                    Help(tr.deckConfigBuryInterdayLearningSiblings(), tr.deckConfigBuryInterdayLearningTooltip() + priority),
                ),
            ),
        )
        switch(tr.deckConfigBuryNewSiblings(), config.buryNew) { v -> state.updateConfig { buryNew = v } }
        switch(tr.deckConfigBuryReviewSiblings(), config.buryReviews) { v -> state.updateConfig { buryReviews = v } }
        switch(tr.deckConfigBuryInterdayLearningSiblings(), config.buryInterdayLearning) { v ->
            state.updateConfig { buryInterdayLearning = v }
        }

        // ------------------------------------------------------------------ audio
        add(
            OptionEntry.Heading(
                tr.deckConfigAudioTitle(),
                listOf(
                    Help(tr.deckConfigDisableAutoplay(), tr.deckConfigDisableAutoplayTooltip()),
                    Help(tr.deckConfigSkipQuestionWhenReplaying(), tr.deckConfigAlwaysIncludeQuestionAudioTooltip()),
                ),
            ),
        )
        switch(tr.deckConfigDisableAutoplay(), config.disableAutoplay) { v -> state.updateConfig { disableAutoplay = v } }
        switch(tr.deckConfigSkipQuestionWhenReplaying(), config.skipQuestionWhenReplayingAnswer) { v ->
            state.updateConfig { skipQuestionWhenReplayingAnswer = v }
        }

        // ------------------------------------------------------------------ timer
        add(
            OptionEntry.Heading(
                tr.deckConfigTimerTitle(),
                listOf(
                    Help(tr.deckConfigMaximumAnswerSecs(), tr.deckConfigMaximumAnswerSecsTooltip()),
                    Help(tr.schedulingShowAnswerTimer(), tr.deckConfigShowAnswerTimerTooltip()),
                    Help(tr.deckConfigStopTimerOnAnswer(), tr.deckConfigStopTimerOnAnswerTooltip()),
                ),
            ),
        )
        int(tr.deckConfigMaximumAnswerSecs(), tr.deckConfigMaximumAnswerSecsTooltip(), config.capAnswerTimeToSecs, min = 1, max = 7200) {
            state.updateConfig { capAnswerTimeToSecs = it }
        }
        warning(if (config.capAnswerTimeToSecs > 600) tr.deckConfigMaximumAnswerSecsAboveRecommended() else "")
        switch(tr.schedulingShowAnswerTimer(), config.showTimer) { v -> state.updateConfig { showTimer = v } }
        switch(tr.deckConfigStopTimerOnAnswer(), config.stopTimerOnAnswer) { v -> state.updateConfig { stopTimerOnAnswer = v } }

        // ------------------------------------------------------------------ auto advance
        add(
            OptionEntry.Heading(
                tr.actionsAutoAdvance(),
                listOf(
                    Help(tr.deckConfigSecondsToShowQuestion(), tr.deckConfigSecondsToShowQuestionTooltip3()),
                    Help(tr.deckConfigSecondsToShowAnswer(), tr.deckConfigSecondsToShowAnswerTooltip2()),
                    Help(tr.deckConfigWaitForAudio(), tr.deckConfigWaitForAudioTooltip2()),
                    Help(tr.deckConfigQuestionAction(), tr.deckConfigQuestionActionToolTip()),
                    Help(tr.deckConfigAnswerAction(), tr.deckConfigAnswerActionTooltip2()),
                ),
            ),
        )
        decimal(
            tr.deckConfigSecondsToShowQuestion(),
            tr.deckConfigSecondsToShowQuestionTooltip3(),
            config.secondsToShowQuestion,
            0f,
            9999f,
        ) {
            state.updateConfig { secondsToShowQuestion = it }
        }
        decimal(tr.deckConfigSecondsToShowAnswer(), tr.deckConfigSecondsToShowAnswerTooltip2(), config.secondsToShowAnswer, 0f, 9999f) {
            state.updateConfig { secondsToShowAnswer = it }
        }
        switch(tr.deckConfigWaitForAudio(), config.waitForAudio) { v -> state.updateConfig { waitForAudio = v } }
        choice(
            tr.deckConfigQuestionAction(),
            tr.deckConfigQuestionActionToolTip(),
            listOf(
                QuestionAction.QUESTION_ACTION_SHOW_ANSWER to tr.deckConfigQuestionActionShowAnswer(),
                QuestionAction.QUESTION_ACTION_SHOW_REMINDER to tr.deckConfigQuestionActionShowReminder(),
            ),
            config.questionAction,
            { it.number },
            { QuestionAction.forNumber(it) },
        ) { state.updateConfig { questionAction = it } }
        choice(
            tr.deckConfigAnswerAction(),
            tr.deckConfigAnswerActionTooltip2(),
            listOf(
                AnswerAction.ANSWER_ACTION_BURY_CARD to tr.studyingBuryCard(),
                AnswerAction.ANSWER_ACTION_ANSWER_AGAIN to tr.deckConfigAnswerAgain(),
                AnswerAction.ANSWER_ACTION_ANSWER_GOOD to tr.deckConfigAnswerGood(),
                AnswerAction.ANSWER_ACTION_ANSWER_HARD to tr.deckConfigAnswerHard(),
                AnswerAction.ANSWER_ACTION_SHOW_REMINDER to tr.deckConfigShowReminder(),
            ),
            config.answerAction,
            { it.number },
            { AnswerAction.forNumber(it) },
        ) { state.updateConfig { answerAction = it } }

        // ------------------------------------------------------------------ easy days
        add(OptionEntry.Heading(tr.deckConfigEasyDaysTitle(), emptyList()))
        val days =
            listOf(
                tr.deckConfigEasyDaysMonday(),
                tr.deckConfigEasyDaysTuesday(),
                tr.deckConfigEasyDaysWednesday(),
                tr.deckConfigEasyDaysThursday(),
                tr.deckConfigEasyDaysFriday(),
                tr.deckConfigEasyDaysSaturday(),
                tr.deckConfigEasyDaysSunday(),
            )
        val levels =
            listOf(0 to tr.deckConfigEasyDaysMinimum(), 50 to tr.deckConfigEasyDaysReduced(), 100 to tr.deckConfigEasyDaysNormal())
        val percentages = config.easyDaysPercentagesList
        days.forEachIndexed { day, name ->
            val percent = ((percentages.getOrNull(day) ?: 1f) * 100).roundToInt()
            value(
                name,
                levels.firstOrNull { it.first == percent }?.second ?: "$percent%",
                Editor.Choice(name, null, levels, percent) { chosen ->
                    state.updateConfig { setEasyDaysPercentages(day, chosen / 100f) }
                },
            )
        }
        warning(if (percentages.none { it == 1f }) tr.deckConfigEasyDaysNoNormalDays() else "")

        // ------------------------------------------------------------------ advanced
        add(
            OptionEntry.Heading(
                tr.deckConfigAdvancedTitle(),
                listOfNotNull(
                    Help(tr.schedulingMaximumInterval(), tr.deckConfigMaximumIntervalTooltip()),
                    Help(tr.schedulingStartingEase(), tr.deckConfigStartingEaseTooltip()).takeUnless { fsrs },
                    Help(tr.schedulingEasyBonus(), tr.deckConfigEasyBonusTooltip()).takeUnless { fsrs },
                    Help(tr.schedulingIntervalModifier(), tr.deckConfigIntervalModifierTooltip()).takeUnless { fsrs },
                    Help(tr.schedulingHardInterval(), tr.deckConfigHardIntervalTooltip()).takeUnless { fsrs },
                    Help(tr.schedulingNewInterval(), tr.deckConfigNewIntervalTooltip()).takeUnless { fsrs },
                    Help(tr.deckConfigHistoricalRetention(), tr.deckConfigHistoricalRetentionTooltip()).takeIf { fsrs },
                    Help(tr.deckConfigIgnoreBefore(), tr.deckConfigIgnoreBeforeTooltip2()).takeIf { fsrs },
                    Help(tr.deckConfigCustomScheduling(), tr.deckConfigCustomSchedulingTooltip()),
                ),
            ),
        )
        int(tr.schedulingMaximumInterval(), tr.deckConfigMaximumIntervalTooltip(), config.maximumReviewInterval, min = 1, max = 365 * 100) {
            state.updateConfig { maximumReviewInterval = it }
        }
        warning(if (config.maximumReviewInterval < 180) tr.deckConfigTooShortMaximumInterval() else "")
        if (!fsrs) {
            decimal(tr.schedulingStartingEase(), tr.deckConfigStartingEaseTooltip(), config.initialEase, 1.31f, 5f) {
                state.updateConfig { initialEase = it }
            }
            decimal(tr.schedulingEasyBonus(), tr.deckConfigEasyBonusTooltip(), config.easyMultiplier, 1f, 5f) {
                state.updateConfig { easyMultiplier = it }
            }
            decimal(tr.schedulingIntervalModifier(), tr.deckConfigIntervalModifierTooltip(), config.intervalMultiplier, 0.5f, 2f) {
                state.updateConfig { intervalMultiplier = it }
            }
            decimal(tr.schedulingHardInterval(), tr.deckConfigHardIntervalTooltip(), config.hardMultiplier, 0.5f, 1.3f) {
                state.updateConfig { hardMultiplier = it }
            }
            decimal(tr.schedulingNewInterval(), tr.deckConfigNewIntervalTooltip(), config.lapseMultiplier, 0f, 1f) {
                state.updateConfig { lapseMultiplier = it }
            }
        } else {
            decimal(
                tr.deckConfigHistoricalRetention(),
                tr.deckConfigHistoricalRetentionTooltip(),
                config.historicalRetention,
                0.5f,
                1f,
                percent = true,
            ) { state.updateConfig { historicalRetention = it } }
            value(
                tr.deckConfigIgnoreBefore(),
                config.ignoreRevlogsBeforeDate.ifEmpty { "1970-01-01" },
                Editor.Text(tr.deckConfigIgnoreBefore(), tr.deckConfigIgnoreBeforeTooltip2(), config.ignoreRevlogsBeforeDate, {
                    if (it.isEmpty() || Regex("""\d{4}-\d{2}-\d{2}""").matches(it)) null else labels.invalidDate
                }) { state.updateConfig { ignoreRevlogsBeforeDate = it } },
            )
        }
        value(
            tr.deckConfigCustomScheduling(),
            state.cardStateCustomizer
                .lineSequence()
                .firstOrNull()
                .orEmpty(),
            Editor.Text(tr.deckConfigCustomScheduling(), tr.deckConfigCustomSchedulingTooltip(), state.cardStateCustomizer, { null }) {
                state.cardStateCustomizer = it
            },
        )
    }

private fun MutableList<OptionEntry>.steps(
    title: String,
    help: String,
    steps: List<Float>,
    labels: OptionLabels,
    set: (List<Float>) -> Unit,
) {
    val text = stepsToString(steps)
    add(
        OptionEntry.Value(
            title,
            text,
            Editor.Text(title, help, text, { if (isValidSteps(it)) null else labels.invalidSteps }) { set(stringToSteps(it.trim())) },
        ),
    )
}

/** "20 · Preset", "5 · This deck" … */
private fun limitText(
    tr: Translations,
    value: Int,
    scope: LimitScope,
): String = "$value · ${scopeLabel(tr, scope)}"

fun scopeLabel(
    tr: Translations,
    scope: LimitScope,
): String =
    when (scope) {
        LimitScope.Preset -> tr.deckConfigSharedPreset()
        LimitScope.Deck -> tr.deckConfigDeckOnly()
        LimitScope.Today -> tr.deckConfigTodayOnly()
    }

/** 2.5 → "2.5", 0.9 as a percentage → "90%". */
fun formatDecimal(
    value: Float,
    percent: Boolean,
): String {
    if (percent) {
        val pct = (value * 1000).roundToInt() / 10.0
        return (if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()) + "%"
    }
    val rounded = (value * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun reviewOrderChoices(
    tr: Translations,
    fsrs: Boolean,
): List<Pair<ReviewCardOrder, String>> {
    val difficulty =
        listOf(
            ReviewCardOrder.REVIEW_CARD_ORDER_EASE_ASCENDING to
                if (fsrs) tr.deckConfigSortOrderDescendingDifficulty() else tr.deckConfigSortOrderAscendingEase(),
            ReviewCardOrder.REVIEW_CARD_ORDER_EASE_DESCENDING to
                if (fsrs) tr.deckConfigSortOrderAscendingDifficulty() else tr.deckConfigSortOrderDescendingEase(),
        ).let { if (fsrs) it.reversed() else it }
    val retrievability =
        if (fsrs) {
            listOf(
                ReviewCardOrder.REVIEW_CARD_ORDER_RETRIEVABILITY_ASCENDING to tr.deckConfigSortOrderRetrievabilityAscending(),
                ReviewCardOrder.REVIEW_CARD_ORDER_RETRIEVABILITY_DESCENDING to tr.deckConfigSortOrderRetrievabilityDescending(),
            )
        } else {
            emptyList()
        }
    return listOf(
        ReviewCardOrder.REVIEW_CARD_ORDER_DAY to tr.deckConfigSortOrderDueDateThenRandom(),
        ReviewCardOrder.REVIEW_CARD_ORDER_DAY_THEN_DECK to tr.deckConfigSortOrderDueDateThenDeck(),
        ReviewCardOrder.REVIEW_CARD_ORDER_DECK_THEN_DAY to tr.deckConfigSortOrderDeckThenDueDate(),
        ReviewCardOrder.REVIEW_CARD_ORDER_INTERVALS_ASCENDING to tr.deckConfigSortOrderAscendingIntervals(),
        ReviewCardOrder.REVIEW_CARD_ORDER_INTERVALS_DESCENDING to tr.deckConfigSortOrderDescendingIntervals(),
    ) + difficulty + retrievability +
        listOf(
            ReviewCardOrder.REVIEW_CARD_ORDER_RELATIVE_OVERDUENESS to tr.decksRelativeOverdueness(),
            ReviewCardOrder.REVIEW_CARD_ORDER_RANDOM to tr.deckConfigSortOrderRandom(),
            ReviewCardOrder.REVIEW_CARD_ORDER_ADDED to tr.decksOrderAdded(),
            ReviewCardOrder.REVIEW_CARD_ORDER_REVERSE_ADDED to tr.decksLatestAddedFirst(),
        )
}

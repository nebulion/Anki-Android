// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.android.AnkiShakeDetector
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.dialogs.showDeckOptionsSelectionDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.eink.EinkRefresh
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.window
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.MmdTheme
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelBody
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.PanelTitle
import com.ichi2.compose.mmd.WebContent
import com.squareup.seismic.ShakeDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * The study screen. The UI is [ReviewerScreenMMD]; this fragment owns the card's WebView, the
 * key/gesture bindings and the dialogs that are still Views (tags, set due date, reset progress).
 */
class ReviewerFragment :
    CardViewerFragment(),
    DispatchKeyEventListener,
    TagsDialogListener,
    ShakeDetector.Listener {
    override val viewModel: ReviewerViewModel by viewModels()

    private var safeWebViewLayout: SafeWebViewLayout? = null
    override val webViewLayout: SafeWebViewLayout
        get() = requireNotNull(safeWebViewLayout) { "the view is not created" }

    private lateinit var bindingMap: BindingMap<ReviewerBinding, ViewerAction>
    private var shakeDetector: AnkiShakeDetector? = null
    private lateinit var tagsDialogFactory: TagsDialogFactory

    private val messages = MessageHostState()
    private val isHtmlTypeAnswerEnabled by lazy { Prefs.isHtmlTypeAnswerEnabled }
    private var typedAnswer by mutableStateOf("")
    private var isTypeAnswerFocused = false
    private var timeboxMessage by mutableStateOf<String?>(null)
    private var flagNames by mutableStateOf<Map<Flag, String>>(emptyMap())

    override fun onLoadInitialHtml(): String =
        stdHtml(
            context = requireContext(),
            extraJsAssets = listOf("scripts/ankidroid-reviewer.js"),
        )

    override fun onStart() {
        super.onStart()
        if (!requireActivity().isChangingConfigurations) {
            shakeDetector?.start()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopAutoAdvance()
            shakeDetector?.stop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val webView = SafeWebViewLayout(requireContext()).also { safeWebViewLayout = it }
        return ComposeView(requireContext()).apply {
            // focusable, so key events and motion controllers reach the bindings
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { MmdTheme { ReviewerContent(webView) } }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupBindings(view)
        setupTypeAnswer()
        setupActions()
        setupResetProgress()
        setupTimebox()

        viewModel.finishResultFlow.collectIn(lifecycleScope) { result ->
            requireActivity().run {
                setResult(result)
                finish()
            }
        }

        viewModel.statesMutationEvalFlow.collectIn(lifecycleScope) { eval ->
            // Completion is signaled by `statesMutated`
            webViewLayout.evaluateJavascript(eval) { result ->
                // eval failed, usually a syntax error
                // Note: this is `"null"`, not null
                if ("null" == result) {
                    viewModel.onStateMutationCallback()
                }
            }
        }

        viewModel.showingAnswer.collectIn(lifecycleScope) {
            resetZoom()
            // focus on the whole screen so motion controllers are captured by the bindings
            view.requestFocus()
        }

        viewModel.navigateFlow.collectIn(lifecycleScope) { destination ->
            if (destination is DeckOptionsDestination && destination.options.size > 1) {
                requireContext().showDeckOptionsSelectionDialog(destination.options) { selectedOption ->
                    Timber.i("Deck options target selected: ${selectedOption.deckId}")
                    navigate(
                        destination.copy(
                            deckId = selectedOption.deckId,
                            isFiltered = selectedOption.isFiltered,
                        ),
                    )
                }
                return@collectIn
            }
            navigate(destination)
        }

        // E Ink: every few answers, flash the panel to clear ghosting
        viewModel.answerFeedbackFlow.collectIn(lifecycleScope) {
            EinkRefresh.onChange(activity)
        }

        lifecycleScope.launch {
            flagNames = Flag.queryDisplayNames(requireContext())
        }

        if (Prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        safeWebViewLayout = null
    }

    @Composable
    private fun ReviewerContent(webView: SafeWebViewLayout) {
        val counts by viewModel.countsFlow.collectAsStateWithLifecycle()
        val isAnswerShown by viewModel.showingAnswer.collectAsStateWithLifecycle()
        val nextTimes by viewModel.answerButtonsNextTimeFlow.collectAsStateWithLifecycle()
        val undoLabel by viewModel.undoLabelFlow.collectAsStateWithLifecycle()
        val hasMedia by viewModel.hasMediaFlow.collectAsStateWithLifecycle()
        val flag by viewModel.flagFlow.collectAsStateWithLifecycle()
        val isMarked by viewModel.isMarkedFlow.collectAsStateWithLifecycle()
        val typeAnswer by viewModel.typeAnswerFlow.collectAsStateWithLifecycle()
        val canBuryNote by viewModel.canBuryNoteFlow.collectAsStateWithLifecycle()
        val canSuspendNote by viewModel.canSuspendNoteFlow.collectAsStateWithLifecycle()
        val isAutoAdvanceEnabled by viewModel.isAutoAdvanceEnabledFlow.collectAsStateWithLifecycle()
        var showCounts by remember { mutableStateOf(true) }
        var isMenuShown by rememberSaveable { mutableStateOf(false) }
        var isFlagSheetShown by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            showCounts = CollectionPreferences.getShowRemainingDueCounts()
        }

        ReviewerScreenMMD(
            state =
                ReviewerUiState(
                    counts = counts.takeIf { showCounts },
                    isAnswerShown = isAnswerShown,
                    nextTimes = nextTimes,
                    undoLabel = undoLabel,
                    hasMedia = hasMedia,
                    flagName = flagNames[flag].takeIf { flag != Flag.NONE },
                    isMarked = isMarked,
                    showAnswerButtons = Prefs.showAnswerButtons,
                    hideHardAndEasy = Prefs.hideHardAndEasyButtons,
                    showTypeAnswer = typeAnswer != null && !isHtmlTypeAnswerEnabled,
                    autoFocusTypeAnswer = Prefs.autoFocusTypeAnswer,
                ),
            typedAnswer = typedAnswer,
            messages = messages,
            onBack = { requireActivity().finish() },
            onUndo = { viewModel.executeAction(ViewerAction.UNDO) },
            onReplay = { viewModel.executeAction(ViewerAction.PLAY_MEDIA) },
            onMenu = { isMenuShown = true },
            onShowAnswer = viewModel::onShowAnswer,
            onRate = viewModel::answerCard,
            onTypedAnswerChange = { typedAnswer = it },
            onTypeAnswerFocusChange = { isTypeAnswerFocused = it },
            card = { modifier -> WebContent(factory = { webView }, modifier = modifier) },
        )

        if (isMenuShown) {
            val context = requireContext()
            MenuPanel(
                title = getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                items =
                    buildList {
                        add(
                            MenuItem(
                                label = ViewerAction.FLAG_MENU.title(context),
                                value = flagNames[flag].takeIf { flag != Flag.NONE },
                            ) { isFlagSheetShown = true },
                        )
                        val markLabel = if (isMarked) getString(R.string.menu_unmark_note) else ViewerAction.MARK.title(context)
                        add(MenuItem(markLabel) { viewModel.executeAction(ViewerAction.MARK) })
                        add(MenuItem(ViewerAction.BURY_CARD.title(context)) { viewModel.executeAction(ViewerAction.BURY_CARD) })
                        if (canBuryNote) {
                            add(MenuItem(ViewerAction.BURY_NOTE.title(context)) { viewModel.executeAction(ViewerAction.BURY_NOTE) })
                        }
                        add(MenuItem(ViewerAction.SUSPEND_CARD.title(context)) { viewModel.executeAction(ViewerAction.SUSPEND_CARD) })
                        if (canSuspendNote) {
                            add(MenuItem(ViewerAction.SUSPEND_NOTE.title(context)) { viewModel.executeAction(ViewerAction.SUSPEND_NOTE) })
                        }
                        add(MenuItem(ViewerAction.CARD_INFO.title(context)) { viewModel.executeAction(ViewerAction.CARD_INFO) })
                        add(MenuItem(ViewerAction.DECK_OPTIONS.title(context)) { viewModel.executeAction(ViewerAction.DECK_OPTIONS) })
                        val autoAdvanceLabel = if (isAutoAdvanceEnabled) R.string.disable_auto_advance else R.string.enable_auto_advance
                        add(MenuItem(getString(autoAdvanceLabel)) { viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE) })
                    },
                onDismissRequest = { isMenuShown = false },
            )
        }

        if (isFlagSheetShown) {
            ChoiceSheet(
                title = ViewerAction.FLAG_MENU.title(requireContext()),
                options = Flag.entries,
                selected = flag,
                label = { flagNames[it] ?: it.name },
                onSelect = { viewModel.executeAction(it.setFlagAction()) },
                onDismissRequest = { isFlagSheetShown = false },
            )
        }

        timeboxMessage?.let { message ->
            PanelDialog(onDismissRequest = {}, dismissOnClickOutside = false) {
                PanelTitle(getString(R.string.timebox_reached_title))
                PanelBody(message)
                PanelActions {
                    PanelSecondaryAction(
                        label = CollectionManager.TR.studyingFinish(),
                        onClick = {
                            Timber.i("ReviewerFragment: Timebox 'Finish'")
                            timeboxMessage = null
                            requireActivity().finish()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PanelPrimaryAction(
                        label = CollectionManager.TR.studyingContinue(),
                        onClick = {
                            Timber.i("ReviewerFragment: Timebox 'Continue'")
                            timeboxMessage = null
                            viewModel.onPageFinished(false)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    private fun setupTypeAnswer() {
        if (isHtmlTypeAnswerEnabled && Prefs.autoFocusTypeAnswer) {
            viewModel.typeAnswerFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { typeInAnswer ->
                if (typeInAnswer == null) return@collectIn
                webViewLayout.focusOnWebView()
                // `evaluateJavascript()` doesn't trigger the IME unless the WebView
                // has been touched before, so ´loadUrl()` is used instead.
                webViewLayout.loadUrl("javascript:document.getElementById('typeans')?.focus();")
            }
        }

        viewModel.onCardUpdatedFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            typedAnswer = ""
        }

        viewModel.onTypedAnswerResultFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { request ->
                if (isHtmlTypeAnswerEnabled) {
                    val script = """document.getElementById("typeans").value;"""
                    webViewLayout.evaluateJavascript(script) { callback ->
                        // the returned string comes with surrounding `"`, so remove it once
                        request.complete(callback.removeSurrounding("\""))
                    }
                } else {
                    request.complete(typedAnswer)
                }
            }
    }

    private fun resetZoom() {
        webViewLayout.settings.loadWithOverviewMode = false
        webViewLayout.settings.loadWithOverviewMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || isTypeAnswerFocused) {
            return false
        }
        return bindingMap.onKeyDown(event)
    }

    override fun hearShake() {
        bindingMap.onGesture(Gesture.SHAKE)
    }

    private fun setupBindings(view: View) {
        bindingMap = BindingMap(sharedPrefs(), ViewerAction.entries, viewModel)
        view.setOnGenericMotionListener { _, event ->
            bindingMap.onGenericMotionEvent(event)
        }
        if (bindingMap.isBound(Gesture.SHAKE)) {
            shakeDetector = AnkiShakeDetector.createInstance(requireContext(), this)
            shakeDetector?.start()
        }
    }

    private fun setupResetProgress() {
        viewModel.resetProgressFlow
            .flowWithLifecycle(lifecycle)
            .onEach {
                showDialogFragment(ForgetCardsDialog())
            }.launchIn(lifecycleScope)
        // TODO handle 'Reset progress' in the ViewModel instead of the activity, once
        //  a mechanism of showing a progress bar if the operation takes too long is implemented
        registerOnForgetHandler { listOf(viewModel.getCardId()) }
    }

    private fun setupTimebox() {
        viewModel.timeBoxReachedFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { timebox ->
            Timber.i("ReviewerFragment: Timebox reached (reps %d - secs %d)", timebox.reps, timebox.secs)
            viewModel.stopAutoAdvance()
            val minutes = (timebox.secs / 60f).roundToInt()
            timeboxMessage =
                CollectionManager.TR.studyingCardStudiedIn(timebox.reps) + " " + CollectionManager.TR.studyingMinute(minutes)
        }
    }

    private fun setupActions() {
        viewModel.actionFeedbackFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { message ->
                messages.show(message)
            }

        viewModel.editNoteTagsFlow.collectIn(lifecycleScope) { noteId ->
            val dialogFragment =
                tagsDialogFactory.newTagsDialog().withArguments(
                    requireContext(),
                    TagsDialog.DialogType.EDIT_TAGS,
                    listOf(noteId),
                )
            showDialogFragment(dialogFragment)
        }

        viewModel.setDueDateFlow.collectIn(lifecycleScope) { cardId ->
            val dialogFragment = SetDueDateDialog.newInstance(this, listOf(cardId))
            showDialogFragment(dialogFragment)
        }

        viewModel.pageUpFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            webViewLayout.pageUp()
        }

        viewModel.pageDownFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            webViewLayout.pageDown()
        }
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) = viewModel.onEditedTags(selectedTags)

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): CardViewerWebViewClient = ReviewerWebViewClient(savedInstanceState)

    private inner class ReviewerWebViewClient(
        savedInstanceState: Bundle?,
    ) : CardViewerWebViewClient(savedInstanceState) {
        private var scale: Float = if (!isRobolectric) webViewLayout.scale else 1F
        private var isScrolling: Boolean = false
        private var isScrollingJob: Job? = null
        private val gestureParser by lazy {
            GestureParser(
                scope = lifecycleScope,
                isDoubleTapEnabled = bindingMap.isBound(Gesture.DOUBLE_TAP),
            )
        }
        private var hasShownUnsupportedFeatureWarning = false

        init {
            webViewLayout.setOnScrollChangeListener { _, _, _, _, _ ->
                isScrolling = true
                isScrollingJob?.cancel()
                isScrollingJob =
                    lifecycleScope.launch {
                        delay(300)
                        isScrolling = false
                    }
            }
        }

        override fun handleUrl(
            webView: WebView,
            url: Uri,
        ): Boolean {
            return when (url.scheme) {
                "gesture" -> {
                    if (isScrolling) return true
                    gestureParser.parse(url, scale, webView) { gesture ->
                        if (gesture == null) return@parse
                        Timber.v("ReviewerFragment::onGesture %s", gesture)
                        bindingMap.onGesture(gesture)
                    }
                    true
                }
                "ankidroid" -> {
                    when (url.host) {
                        "show-answer" -> viewModel.onShowAnswer()
                    }
                    true
                }
                "signal" -> {
                    if (hasShownUnsupportedFeatureWarning) return true
                    hasShownUnsupportedFeatureWarning = true
                    messages.show(getString(R.string.feature_not_supported_by_study_screen))
                    true
                }
                else -> super.handleUrl(webView, url)
            }
        }

        override fun onScaleChanged(
            view: WebView?,
            oldScale: Float,
            newScale: Float,
        ) {
            super.onScaleChanged(view, oldScale, newScale)
            scale = newScale
        }

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            super.onPageFinished(view, url)
            Prefs.cardZoom.let {
                if (it == 100) return@let
                val scale = it / 100.0
                val script = """document.body.style.zoom = `$scale`;"""
                view?.evaluateJavascript(script, null)
            }
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = CardViewerActivity.getIntent(context, ReviewerFragment::class)
    }
}

/** The menu action that sets this flag on the current card. */
private fun Flag.setFlagAction(): ViewerAction =
    when (this) {
        Flag.NONE -> ViewerAction.UNSET_FLAG
        Flag.RED -> ViewerAction.FLAG_RED
        Flag.ORANGE -> ViewerAction.FLAG_ORANGE
        Flag.GREEN -> ViewerAction.FLAG_GREEN
        Flag.BLUE -> ViewerAction.FLAG_BLUE
        Flag.PINK -> ViewerAction.FLAG_PINK
        Flag.TURQUOISE -> ViewerAction.FLAG_TURQUOISE
        Flag.PURPLE -> ViewerAction.FLAG_PURPLE
    }

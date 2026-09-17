// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckoptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ActionRow
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.GroupDivider
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.NumberPanel
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.PanelActions
import com.ichi2.compose.mmd.PanelDialog
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.PanelTitle
import com.ichi2.compose.mmd.RowDefaults
import com.ichi2.compose.mmd.RowDivider
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.TextBlock
import com.ichi2.compose.mmd.TextPage
import com.ichi2.compose.mmd.TextPanel
import com.ichi2.compose.mmd.ValueRow
import com.ichi2.compose.mmd.panelTextFieldColors
import com.ichi2.compose.mmd.paragraphs
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import net.ankiweb.rsdroid.Translations

/**
 * The deck options as a settings page: the preset in use, then each section of the backend's deck
 * options page as a heading and rows. A value opens a panel or sheet to change it; a heading's
 * help button opens the section's help on a page of its own.
 *
 * Nothing is written until Save, as on the web page. [state] is null while the options load.
 */
@Composable
fun DeckOptionsScreenMMD(
    state: DeckOptionsState?,
    tr: Translations,
    fsrsActions: FsrsActions,
    onSave: () -> Unit,
    onMenu: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = state?.currentDeckName.orEmpty(),
            navigationIcon = {
                HeaderAction(
                    icon = R.drawable.ic_baseline_arrow_back_24,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = onBack,
                )
            },
            actions = {
                if (state != null) {
                    HeaderAction(icon = R.drawable.ic_done, contentDescription = tr.deckConfigSaveButton(), onClick = onSave)
                    HeaderAction(
                        icon = R.drawable.ic_more_vertical,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                        onClick = onMenu,
                    )
                }
            },
        )
        if (state == null) {
            TextMMD(
                text = stringResource(R.string.dialog_processing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(RowDefaults.EdgePadding),
            )
            return@Column
        }

        val labels =
            OptionLabels(
                invalidSteps = stringResource(R.string.mmd_options_invalid_steps),
                invalidDate = stringResource(R.string.mmd_options_invalid_date),
                invalidParams = stringResource(R.string.mmd_options_invalid_params),
                search = tr.actionsSearch(),
            )
        // read so the rows are rebuilt after every change
        val version = state.version
        val entries = remember(state, version) { optionEntries(state, tr, labels, fsrsActions) }
        var editor by remember { mutableStateOf<Editor?>(null) }
        var help by remember { mutableStateOf<OptionEntry.Heading?>(null) }
        var isChoosingPreset by rememberSaveable { mutableStateOf(false) }

        PagedList(Modifier.weight(1f)) {
            item {
                val current = state.configList.first { it.isCurrent }
                ValueRow(
                    title = tr.cardStatsPreset(),
                    value = "${current.name} (${tr.deckConfigUsedByDecks(current.useCount)})",
                    onClick = { isChoosingPreset = true },
                )
            }
            itemsIndexed(entries) { index, entry ->
                val next = entries.getOrNull(index + 1)
                Column {
                    when (entry) {
                        is OptionEntry.Heading -> HeadingRow(entry) { help = entry }
                        is OptionEntry.Switch -> SwitchRow(title = entry.title, checked = entry.checked, onCheckedChange = entry.onChange)
                        is OptionEntry.Value -> ValueRow(title = entry.title, value = entry.value, onClick = { editor = entry.editor })
                        is OptionEntry.Action -> ActionRow(title = entry.title, subtitle = entry.subtitle, onClick = entry.onClick)
                        is OptionEntry.Warning ->
                            TextMMD(
                                text = entry.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.EdgePadding, vertical = 4.dp),
                            )
                    }
                    if (entry !is OptionEntry.Heading && entry !is OptionEntry.Warning && next != null && next !is OptionEntry.Heading) {
                        RowDivider()
                    }
                }
            }
        }

        if (isChoosingPreset) {
            ChoiceSheet(
                title = tr.cardStatsPreset(),
                options = state.configList,
                selected = state.configList.first { it.isCurrent },
                label = { "${it.name} (${tr.deckConfigUsedByDecks(it.useCount)})" },
                onSelect = { state.setCurrentIndex(it.index) },
                onDismissRequest = { isChoosingPreset = false },
            )
        }
        help?.let { heading -> HelpPage(heading) { help = null } }
        editor?.let { current -> EditorPanel(current, state, tr) { editor = null } }
    }
}

@Composable
private fun HeadingRow(
    heading: OptionEntry.Heading,
    onHelp: () -> Unit,
) {
    Column {
        GroupDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(heading.title, Modifier.weight(1f))
            if (heading.help.isNotEmpty()) {
                // a small glyph beside the heading text, in a full-size touch target
                IconButton(onClick = onHelp, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_help_black_24dp),
                        contentDescription = stringResource(R.string.help),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** A section's help on a page of its own: each setting's name, then what it does. */
@Composable
private fun HelpPage(
    heading: OptionEntry.Heading,
    onClose: () -> Unit,
) {
    TextPage(
        title = heading.title,
        blocks = heading.help.flatMap { listOf(TextBlock.Heading(it.title)) + paragraphs(plainHelp(it.text)) },
        onClose = onClose,
    )
}

@Composable
private fun EditorPanel(
    editor: Editor,
    state: DeckOptionsState,
    tr: Translations,
    onDismiss: () -> Unit,
) {
    val ok = stringResource(R.string.dialog_ok)
    val cancel = stringResource(R.string.dialog_cancel)
    when (editor) {
        is Editor.Integer ->
            NumberPanel(
                title = editor.title,
                body = null,
                value = editor.value,
                min = editor.min,
                max = editor.max,
                confirmLabel = ok,
                dismissLabel = cancel,
                invalidMessage = stringResource(R.string.mmd_settings_number_range, editor.min, editor.max),
                onConfirm = {
                    editor.onSet(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        is Editor.Decimal -> {
            val invalid =
                stringResource(
                    R.string.mmd_options_decimal_range,
                    formatDecimal(editor.min, editor.percent),
                    formatDecimal(editor.max, editor.percent),
                )
            TextPanel(
                title = editor.title,
                body = null,
                value = formatDecimal(editor.value, editor.percent).removeSuffix("%"),
                confirmLabel = ok,
                dismissLabel = cancel,
                validate = { text -> if (parseDecimal(text, editor) == null) invalid else null },
                onConfirm = { text ->
                    parseDecimal(text, editor)?.let(editor.onSet)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        is Editor.Text ->
            TextPanel(
                title = editor.title,
                body = null,
                value = editor.value,
                confirmLabel = ok,
                dismissLabel = cancel,
                validate = editor.validate,
                onConfirm = {
                    editor.onSet(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        is Editor.Choice ->
            ChoiceSheet(
                title = editor.title,
                options = editor.options,
                selected = editor.options.firstOrNull { it.first == editor.selected },
                label = { it.second },
                onSelect = { editor.onSet(it.first) },
                onDismissRequest = onDismiss,
            )
        is Editor.Limit ->
            ScopedNumberPanel(
                title = editor.title,
                scopes = LimitScope.entries.map { it to scopeLabel(tr, it) },
                initialScope = state.limits.scope(editor.kind),
                initialValue = state.limitValue(editor.kind).toString(),
                parse = { it.toIntOrNull()?.takeIf { n -> n in 0..9999 } },
                invalidMessage = stringResource(R.string.mmd_settings_number_range, 0, 9999),
                onConfirm = { scope, value ->
                    state.setLimit(editor.kind, scope, value)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        is Editor.Retention -> {
            val range = Editor.Decimal(editor.title, null, 0f, 0.7f, 0.99f, percent = true) {}
            ScopedNumberPanel(
                title = editor.title,
                scopes = listOf(false to tr.deckConfigSharedPreset(), true to tr.deckConfigDeckOnly()),
                initialScope = state.limits.hasDesiredRetention(),
                initialValue = formatDecimal(state.effectiveDesiredRetention, percent = true).removeSuffix("%"),
                parse = { parseDecimal(it, range) },
                invalidMessage = stringResource(R.string.mmd_options_decimal_range, "70", "99"),
                onConfirm = { deckOnly, value ->
                    state.setDesiredRetention(deckOnly, value)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/** A number and which scope it applies to, e.g. a daily limit for the preset, this deck or today. */
@Composable
private fun <S, V> ScopedNumberPanel(
    title: String,
    scopes: List<Pair<S, String>>,
    initialScope: S,
    initialValue: String,
    parse: (String) -> V?,
    invalidMessage: String,
    onConfirm: (S, V) -> Unit,
    onDismiss: () -> Unit,
) {
    var scopeIndex by rememberSaveable { mutableIntStateOf(scopes.indexOfFirst { it.first == initialScope }.coerceAtLeast(0)) }
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val parsed = parse(text)
    PanelDialog(onDismissRequest = onDismiss) {
        PanelTitle(title)
        scopes.forEachIndexed { index, (_, label) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = index == scopeIndex, role = Role.RadioButton) { scopeIndex = index },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButtonMMD(selected = index == scopeIndex, onClick = null)
                TextMMD(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
            }
        }
        TextFieldMMD(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = parsed == null,
            supportingText = if (parsed == null) ({ TextMMD(text = invalidMessage) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = panelTextFieldColors(),
        )
        PanelActions {
            PanelSecondaryAction(label = stringResource(R.string.dialog_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            if (parsed != null) {
                PanelPrimaryAction(
                    label = stringResource(R.string.dialog_ok),
                    onClick = { onConfirm(scopes[scopeIndex].first, parsed) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A decimal typed into [editor]; for a percentage, "90" is 0.9. Null if unreadable or out of range. */
internal fun parseDecimal(
    text: String,
    editor: Editor.Decimal,
): Float? {
    val number =
        text
            .trim()
            .removeSuffix("%")
            .replace(',', '.')
            .toFloatOrNull() ?: return null
    val value = if (editor.percent) number / 100 else number
    // compared at the precision shown, so the limits themselves can be typed
    val epsilon = if (editor.percent) 0.0005f else 0.005f
    return value.takeIf { it >= editor.min - epsilon && it <= editor.max + epsilon }?.coerceIn(editor.min, editor.max)
}

/** The backend's help is Markdown; links keep their text and emphasis marks are dropped. */
internal fun plainHelp(markdown: String): String =
    markdown
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""(?<![*\w])\*([^*\n]+)\*(?!\*)"""), "$1")
        .replace(Regex("""<[^>]+>"""), "")

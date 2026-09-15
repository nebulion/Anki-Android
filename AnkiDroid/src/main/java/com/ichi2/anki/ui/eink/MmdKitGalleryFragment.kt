// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.eink

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.mmd.ActionRow
import com.ichi2.compose.mmd.ChoiceSheet
import com.ichi2.compose.mmd.ComposeHostFragment
import com.ichi2.compose.mmd.ConfirmPanel
import com.ichi2.compose.mmd.DashedDividerMMD
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.MenuItem
import com.ichi2.compose.mmd.MenuPanel
import com.ichi2.compose.mmd.MessageHost
import com.ichi2.compose.mmd.MessageHostState
import com.ichi2.compose.mmd.NavRow
import com.ichi2.compose.mmd.PagedList
import com.ichi2.compose.mmd.PanelPrimaryAction
import com.ichi2.compose.mmd.PanelSecondaryAction
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.SectionTitle
import com.ichi2.compose.mmd.SwitchRow
import com.ichi2.compose.mmd.ValueRow
import com.mudita.mmd.components.text.TextMMD

/**
 * Debug-only screen showing every kit component, for checking the kit on the Kompakt panel
 * next to the calibration captures of Mudita's own apps. Opened from Developer options.
 */
class MmdKitGalleryFragment : ComposeHostFragment() {
    @Composable
    override fun ScreenContent() {
        val messages = remember { MessageHostState() }
        var showMenu by rememberSaveable { mutableStateOf(false) }
        var showChoice by rememberSaveable { mutableStateOf(false) }
        var showConfirm by rememberSaveable { mutableStateOf(false) }
        var switchOn by rememberSaveable { mutableStateOf(false) }
        var choice by rememberSaveable { mutableStateOf("Four") }
        val choices = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight")
        val type = MaterialTheme.typography
        val typeScale =
            listOf(
                "headlineLarge 28" to type.headlineLarge,
                "titleLarge 24" to type.titleLarge,
                "titleMedium 20" to type.titleMedium,
                "bodyLarge 20" to type.bodyLarge,
                "bodyMedium 18" to type.bodyMedium,
                "titleSmall 16" to type.titleSmall,
                "bodySmall 15" to type.bodySmall,
                "labelSmall 14" to type.labelSmall,
            )

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "MMD kit",
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = "Back",
                        onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                },
                actions = {
                    HeaderAction(
                        icon = R.drawable.ic_more_vertical,
                        contentDescription = "Menu",
                        onClick = { showMenu = true },
                    )
                },
            )
            PagedList(Modifier.weight(1f)) {
                item { SectionTitle("Rows") }
                item { NavRow(title = "Drill-down row", subtitle = "Regular-weight subtitle", onClick = {}) }
                item { DashedDividerMMD(Modifier.padding(horizontal = 16.dp)) }
                item { SwitchRow(title = "Switch row", checked = switchOn, onCheckedChange = { switchOn = it }) }
                item { DashedDividerMMD(Modifier.padding(horizontal = 16.dp)) }
                item { ValueRow(title = "Choice sheet", value = choice, onClick = { showChoice = true }) }
                item { DashedDividerMMD(Modifier.padding(horizontal = 16.dp)) }
                item { ActionRow(title = "Show message", onClick = { messages.show("Message shown", "Undo") {} }) }
                item { DashedDividerMMD(Modifier.padding(horizontal = 16.dp)) }
                item { ActionRow(title = "Confirm panel", onClick = { showConfirm = true }) }
                item { SectionTitle("Buttons") }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PanelSecondaryAction(label = "Again", onClick = {}, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        PanelPrimaryAction(label = "Good", onClick = {}, modifier = Modifier.weight(1f))
                    }
                }
                item { SectionTitle("Type scale") }
                items(typeScale) { (name, style) ->
                    TextMMD(text = name, style = style, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                item { SectionTitle("Paging") }
                items((1..30).toList()) { n ->
                    Column {
                        TextMMD(
                            text = "Item $n",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                        DashedDividerMMD(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            MessageHost(messages)
        }

        if (showMenu) {
            MenuPanel(
                title = "Menu",
                items =
                    listOf(
                        MenuItem("Custom study") { messages.show("Custom study") },
                        MenuItem("Rename") { messages.show("Rename") },
                        MenuItem("Delete") { showConfirm = true },
                    ),
                onDismissRequest = { showMenu = false },
            )
        }
        if (showChoice) {
            ChoiceSheet(
                title = "Choose",
                options = choices,
                selected = choice,
                label = { it },
                onSelect = { choice = it },
                onDismissRequest = { showChoice = false },
            )
        }
        if (showConfirm) {
            ConfirmPanel(
                title = "Delete deck?",
                body = "This deletes the deck and its cards.",
                confirmLabel = "Delete",
                dismissLabel = "Cancel",
                onConfirm = {
                    showConfirm = false
                    messages.show("Deleted")
                },
                onDismiss = { showConfirm = false },
            )
        }
    }
}

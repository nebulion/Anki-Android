// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckoptions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import anki.deck_config.DeckConfig
import anki.deck_config.DeckConfig.Config.NewCardGatherPriority
import anki.deck_config.DeckConfig.Config.NewCardSortOrder
import anki.deck_config.DeckConfigsForUpdate
import anki.deck_config.DeckConfigsForUpdate.CurrentDeck.Limits
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.UpdateDeckConfigsRequest
import java.text.Collator
import java.util.Locale

/**
 * The deck options being edited: a port of the backend page's `DeckOptionsState`
 * (`ts/routes/deck-options/lib.ts`, Anki 26.05). It holds every preset, which one the deck uses,
 * the deck's own limits and the collection-wide switches, and builds the save request from them.
 *
 * Changes are made through [updateConfig] and the setters; each bumps [version], which the screen
 * reads so it redraws.
 */
class DeckOptionsState(
    private val targetDeckId: Long,
    data: DeckConfigsForUpdate,
    locale: Locale = Locale.getDefault(),
) {
    /** A preset and the number of decks using it, not counting the deck being edited. */
    private class ConfigWithCount(
        var config: DeckConfig,
        var useCount: Int,
    )

    /** A preset as the preset picker lists it. */
    data class ConfigListEntry(
        val index: Int,
        val name: String,
        val useCount: Int,
        val isCurrent: Boolean,
    )

    val defaults: DeckConfig.Config = data.defaults.config
    val currentDeckName: String = data.currentDeck.name
    val legacyEvaluate: Boolean = data.fsrsLegacyEvaluate
    val daysSinceLastOptimization: Int = data.daysSinceLastFsrsOptimize

    private val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    private val configs: MutableList<ConfigWithCount> =
        data.allConfigList.map { ConfigWithCount(it.config, it.useCount) }.toMutableList()
    private var selectedIndex = 0
    private val modifiedConfigs = mutableSetOf<Long>()
    private val removedConfigs = mutableListOf<Long>()
    private var schemaModified = data.schemaModified
    private val loadedPresets = mutableSetOf<Long>()

    /** Bumped on every change, so a screen reading it redraws. */
    var version by mutableIntStateOf(0)
        private set

    var limits: Limits = if (data.currentDeck.hasLimits()) data.currentDeck.limits else Limits.getDefaultInstance()
        private set
    var cardStateCustomizer: String = data.cardStateCustomizer
        set(value) {
            field = value
            changed()
        }
    var newCardsIgnoreReviewLimit: Boolean = data.newCardsIgnoreReviewLimit
        set(value) {
            field = value
            changed()
        }
    var applyAllParentLimits: Boolean = data.applyAllParentLimits
        set(value) {
            field = value
            changed()
        }
    var fsrs: Boolean = data.fsrs
        set(value) {
            field = value
            changed()
        }
    var fsrsReschedule: Boolean = false
        set(value) {
            field = value
            changed()
        }
    var fsrsHealthCheck: Boolean = data.fsrsHealthCheck
        set(value) {
            field = value
            changed()
        }

    /** Whether a preset was switched, added or removed since loading: optimising needs a save first. */
    var presetAssignmentsChanged = false
        private set

    private val original: Snapshot

    init {
        selectedIndex = configs.indexOfFirst { it.config.id == data.currentDeck.configId }.coerceAtLeast(0)
        sortConfigs()
        // the current deck is counted again at display time
        configs[selectedIndex].useCount -= 1
        normalizeCurrent()
        loadedPresets += configs[selectedIndex].config.id
        original = snapshot()
    }

    /** The options of the preset the deck uses. */
    val current: DeckConfig.Config get() = configs[selectedIndex].config.config

    val currentName: String get() = configs[selectedIndex].config.name

    /** The current preset's name escaped for a search, e.g. in `preset:"…"`. */
    val currentNameForSearch: String get() = currentName.replace(Regex("""([\\"])"""), """\\$1""")

    val configList: List<ConfigListEntry>
        get() =
            configs.mapIndexed { index, c ->
                ConfigListEntry(index, c.config.name, c.useCount + if (index == selectedIndex) 1 else 0, index == selectedIndex)
            }

    /** Changes the current preset's options. */
    fun updateConfig(change: DeckConfig.Config.Builder.() -> Unit) {
        val outer = configs[selectedIndex].config
        val updated =
            outer.config
                .toBuilder()
                .apply(change)
                .build()
        if (updated != outer.config) {
            configs[selectedIndex].config = outer.toBuilder().setConfig(updated).build()
            if (outer.id != 0L) modifiedConfigs += outer.id
            changed()
        }
    }

    fun updateLimits(change: Limits.Builder.() -> Unit) {
        limits = limits.toBuilder().apply(change).build()
        changed()
    }

    fun setCurrentIndex(index: Int) {
        selectedIndex = index
        presetAssignmentsChanged = true
        normalizeCurrent()
        markCurrentPresetAsLoaded()
        changed()
    }

    fun setCurrentName(name: String) {
        if (currentName == name) return
        val outer = configs[selectedIndex].config
        configs[selectedIndex].config = outer.toBuilder().setName(uniqueName(name)).build()
        if (outer.id != 0L) modifiedConfigs += outer.id
        sortConfigs()
        changed()
    }

    /** Adds a preset with the default options and makes it current. */
    fun addConfig(name: String) = addConfigFrom(name, defaults)

    /** Adds a copy of the current preset and makes it current. */
    fun cloneConfig(name: String) = addConfigFrom(name, current)

    private fun addConfigFrom(
        name: String,
        source: DeckConfig.Config,
    ) {
        val config =
            DeckConfig
                .newBuilder()
                .setId(0)
                .setName(uniqueName(name))
                .setConfig(source)
                .build()
        configs += ConfigWithCount(config, 0)
        selectedIndex = configs.lastIndex
        presetAssignmentsChanged = true
        sortConfigs()
        normalizeCurrent()
        changed()
    }

    /** Removing a saved preset changes the collection's schema, which needs a one-way sync. */
    fun removalWillForceFullSync(): Boolean = !schemaModified && configs[selectedIndex].config.id != 0L

    fun defaultConfigSelected(): Boolean = configs[selectedIndex].config.id == DEFAULT_CONFIG_ID

    /** @throws IllegalStateException for the default preset, which can't be removed */
    fun removeCurrentConfig() {
        val id = configs[selectedIndex].config.id
        check(id != DEFAULT_CONFIG_ID) { "can't remove default config" }
        if (id != 0L) {
            removedConfigs += id
            schemaModified = true
        }
        configs.removeAt(selectedIndex)
        setCurrentIndex((selectedIndex - 1).coerceAtLeast(0))
    }

    /** The save request; the current preset comes last, as the backend requires. */
    fun dataForSaving(mode: UpdateDeckConfigsMode): UpdateDeckConfigsRequest {
        val modifiedOthers =
            configs
                .map { it.config }
                .filterIndexed { index, c -> index != selectedIndex && (c.id == 0L || c.id in modifiedConfigs) }
        return UpdateDeckConfigsRequest
            .newBuilder()
            .setTargetDeckId(targetDeckId)
            .addAllConfigs(modifiedOthers + configs[selectedIndex].config)
            .addAllRemovedConfigIds(removedConfigs)
            .setMode(mode)
            .setCardStateCustomizer(cardStateCustomizer)
            .setLimits(limits)
            .setNewCardsIgnoreReviewLimit(newCardsIgnoreReviewLimit)
            .setApplyAllParentLimits(applyAllParentLimits)
            .setFsrs(fsrs)
            .setFsrsReschedule(fsrsReschedule)
            .setFsrsHealthCheck(fsrsHealthCheck)
            .build()
    }

    /** Whether anything differs from what was loaded, so leaving should ask first. */
    fun isModified(): Boolean = snapshot() != original

    /** The FSRS parameters in use: the newest version the preset has. */
    fun fsrsParams(config: DeckConfig.Config = current): List<Float> =
        when {
            config.fsrsParams6Count > 0 -> config.fsrsParams6List
            config.fsrsParams5Count > 0 -> config.fsrsParams5List
            else -> config.fsrsParams4List
        }

    private fun changed() {
        version++
    }

    /**
     * What the page's components change on their own when a preset is first shown: easy days
     * always has seven values, and a new-card sort order the gather order rules out falls back to
     * the first. The original is patched too, so that alone doesn't count as a change.
     */
    private fun normalizeCurrent() {
        val config = current
        val builder = config.toBuilder()
        if (config.easyDaysPercentagesCount != 7) {
            builder.clearEasyDaysPercentages().addAllEasyDaysPercentages(defaults.easyDaysPercentagesList)
        }
        if (config.newCardSortOrder in disabledSortOrders(config.newCardGatherPriority) ||
            config.newCardSortOrder == NewCardSortOrder.UNRECOGNIZED
        ) {
            builder.newCardSortOrder = NewCardSortOrder.forNumber(0)
        }
        val normalized = builder.build()
        if (normalized != config) {
            val outer = configs[selectedIndex].config
            configs[selectedIndex].config = outer.toBuilder().setConfig(normalized).build()
        }
    }

    private fun markCurrentPresetAsLoaded() {
        val config = configs[selectedIndex].config
        if (config.id == 0L || !loadedPresets.add(config.id)) return
        val index = original.configs.indexOfFirst { it.id == config.id }
        if (index != -1) original.configs[index] = config
    }

    private fun uniqueName(name: String): String =
        if (configs.any { it.config.name == name }) name + System.currentTimeMillis() / 1000 else name

    private fun sortConfigs() {
        val currentName = configs[selectedIndex].config.name
        configs.sortWith { a, b -> collator.compare(a.config.name, b.config.name) }
        selectedIndex = configs.indexOfFirst { it.config.name == currentName }
    }

    /** Everything a save would write, for [isModified]. */
    private data class Snapshot(
        val configs: MutableList<DeckConfig>,
        val cardStateCustomizer: String,
        val limits: Limits,
        val newCardsIgnoreReviewLimit: Boolean,
        val applyAllParentLimits: Boolean,
        val fsrs: Boolean,
        val fsrsReschedule: Boolean,
        val currentConfig: DeckConfig.Config,
    )

    private fun snapshot() =
        Snapshot(
            configs.map { it.config }.toMutableList(),
            cardStateCustomizer,
            limits,
            newCardsIgnoreReviewLimit,
            applyAllParentLimits,
            fsrs,
            fsrsReschedule,
            current,
        )

    companion object {
        const val DEFAULT_CONFIG_ID = 1L

        /** New-card sort orders that make no difference for a gather order (`DisplayOrder.svelte`). */
        fun disabledSortOrders(gather: NewCardGatherPriority): List<NewCardSortOrder> =
            when (gather) {
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_RANDOM_NOTES ->
                    listOf(
                        NewCardSortOrder.NEW_CARD_SORT_ORDER_TEMPLATE_THEN_RANDOM,
                        NewCardSortOrder.NEW_CARD_SORT_ORDER_RANDOM_NOTE_THEN_TEMPLATE,
                    )
                NewCardGatherPriority.NEW_CARD_GATHER_PRIORITY_RANDOM_CARDS ->
                    listOf(
                        NewCardSortOrder.NEW_CARD_SORT_ORDER_TEMPLATE_THEN_RANDOM,
                        NewCardSortOrder.NEW_CARD_SORT_ORDER_RANDOM_NOTE_THEN_TEMPLATE,
                        NewCardSortOrder.NEW_CARD_SORT_ORDER_RANDOM_CARD,
                    )
                else -> emptyList()
            }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckoptions

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.UpdateDeckConfigsMode
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.updateDeckConfigsRaw
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The native deck options page edits and saves what the backend's page did. */
@RunWith(AndroidJUnit4::class)
class DeckOptionsStateTest : RobolectricTest() {
    private fun load(deckId: Long = Consts.DEFAULT_DECK_ID) = DeckOptionsState(deckId, col.backend.getDeckConfigsForUpdate(deckId))

    private fun DeckOptionsState.save(mode: UpdateDeckConfigsMode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL) {
        col.updateDeckConfigsRaw(dataForSaving(mode).toByteArray())
    }

    @Test
    fun `a loaded page has no changes`() {
        val state = load()
        assertFalse(state.isModified())
        assertEquals(1, state.configList.size)
        assertEquals(1, state.configList.single().useCount, "the default deck uses the default preset")
    }

    @Test
    fun `a preset change is saved`() {
        val state = load()
        state.updateConfig { newPerDay = 42 }
        assertTrue(state.isModified())

        state.save()

        assertEquals(42, load().current.newPerDay)
    }

    @Test
    fun `a limit for this deck only overrides the preset's`() {
        val state = load()
        state.setLimit(LimitKind.New, LimitScope.Deck, 7)
        assertEquals(LimitScope.Deck, state.limits.scope(LimitKind.New))
        assertEquals(7, state.limitValue(LimitKind.New))

        state.save()

        val reloaded = load()
        assertEquals(LimitScope.Deck, reloaded.limits.scope(LimitKind.New))
        assertEquals(7, reloaded.limitValue(LimitKind.New))

        reloaded.setLimit(LimitKind.New, LimitScope.Preset, 9)
        assertEquals(LimitScope.Preset, reloaded.limits.scope(LimitKind.New), "choosing the preset clears the deck's own limit")
        assertEquals(9, reloaded.current.newPerDay)
    }

    @Test
    fun `presets can be added, renamed and removed, but not the default`() {
        val state = load()
        state.addConfig("Zeta")
        assertEquals(listOf("Default", "Zeta"), state.configList.map { it.name })
        assertEquals("Zeta", state.currentName)

        state.setCurrentName("Alpha")
        assertEquals(listOf("Alpha", "Default"), state.configList.map { it.name }, "presets stay in name order")

        state.removeCurrentConfig()
        assertEquals(listOf("Default"), state.configList.map { it.name })
        assertTrue(state.defaultConfigSelected())
        assertFailsWith<IllegalStateException> { state.removeCurrentConfig() }
    }

    @Test
    fun `a new preset is used by the deck once saved`() {
        val state = load()
        state.cloneConfig("Copy")
        state.updateConfig { leechThreshold = 3 }

        state.save()

        val reloaded = load()
        assertEquals("Copy", reloaded.currentName)
        assertEquals(3, reloaded.current.leechThreshold)
    }

    @Test
    fun `the desired retention can be set for this deck only`() {
        val state = load()
        state.setDesiredRetention(deckOnly = true, value = 0.85f)
        assertEquals(0.85f, state.effectiveDesiredRetention)
        assertTrue(state.limits.hasDesiredRetention())

        state.setDesiredRetention(deckOnly = false, value = 0.8f)
        assertFalse(state.limits.hasDesiredRetention())
        assertEquals(0.8f, state.current.desiredRetention)
    }

    @Test
    fun `learning steps are written as the page writes them`() {
        assertEquals("1m 10m 1d", stepsToString(listOf(1f, 10f, 1440f)))
        assertEquals("30s 2h", stepsToString(listOf(0.5f, 120f)))
        assertEquals(listOf(1f, 10f, 1440f, 0.5f), stringToSteps("1m 10m 1d 30s"))
        assertEquals(listOf(5f), stringToSteps("5  0"), "zero and empty steps are dropped")
        assertTrue(isValidSteps("1m 10m 1d"))
        assertFalse(isValidSteps("1 minute"))
    }

    @Test
    fun `decimals are read within their range`() {
        val retention = Editor.Decimal("", null, 0.9f, 0.7f, 0.99f, percent = true) {}
        assertEquals(0.9f, parseDecimal("90", retention))
        assertEquals(0.99f, parseDecimal("99", retention))
        assertEquals(null, parseDecimal("100", retention))
        val ease = Editor.Decimal("", null, 2.5f, 1.31f, 5f, percent = false) {}
        assertEquals(2.5f, parseDecimal("2,5", ease))
        assertEquals(null, parseDecimal("abc", ease))
    }
}

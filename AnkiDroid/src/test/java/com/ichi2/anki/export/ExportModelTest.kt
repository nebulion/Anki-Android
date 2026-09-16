// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.export.ExportOption.IncludeDeck
import com.ichi2.anki.export.ExportOption.IncludeDeckConfigs
import com.ichi2.anki.export.ExportOption.IncludeGuid
import com.ichi2.anki.export.ExportOption.IncludeHtml
import com.ichi2.anki.export.ExportOption.IncludeMedia
import com.ichi2.anki.export.ExportOption.IncludeNotetype
import com.ichi2.anki.export.ExportOption.IncludeSchedule
import com.ichi2.anki.export.ExportOption.IncludeTags
import com.ichi2.anki.export.ExportOption.Legacy
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The export page's choices, as the old export dialog's tests had them. */
class ExportModelTest {
    @Test
    fun `collection export options are initialized correctly`() {
        assertEquals(listOf(IncludeMedia, Legacy), ExportFormat.Collection.options)
        assertEquals(setOf(IncludeMedia), ExportFormat.Collection.defaults, "media on, legacy off")
        assertFalse(ExportFormat.Collection.hasDeckChoice, "a collection is exported whole")
    }

    @Test
    fun `apkg export options are initialized correctly`() {
        assertEquals(listOf(IncludeSchedule, IncludeDeckConfigs, IncludeMedia, Legacy), ExportFormat.Apkg.options)
        assertEquals(setOf(IncludeSchedule, IncludeMedia), ExportFormat.Apkg.defaults, "scheduling and media on")
        assertTrue(ExportFormat.Apkg.hasDeckChoice)
    }

    @Test
    fun `plain text exports offer their own options`() {
        assertEquals(listOf(IncludeHtml, IncludeTags, IncludeDeck, IncludeNotetype, IncludeGuid), ExportFormat.Notes.options)
        assertEquals(setOf(IncludeHtml, IncludeTags), ExportFormat.Notes.defaults)
        assertEquals(listOf(IncludeHtml), ExportFormat.Cards.options)
        assertEquals(setOf(IncludeHtml), ExportFormat.Cards.defaults)
    }

    @Test
    fun `Legacy export checkbox(default false) is shown only for collection and apkg`() {
        val withLegacy = ExportFormat.entries.filter { Legacy in it.options }
        assertEquals(listOf(ExportFormat.Collection, ExportFormat.Apkg), withLegacy)
        ExportFormat.entries.forEach { assertFalse(Legacy in it.defaults, "$it: legacy is off by default") }
    }

    @Test
    fun `'All decks' exports the whole collection, a deck only itself`() {
        assertTrue(exportLimitFor(ALL_DECKS_ID).hasWholeCollection())
        assertEquals(42L, exportLimitFor(42L).deckId)
    }
}

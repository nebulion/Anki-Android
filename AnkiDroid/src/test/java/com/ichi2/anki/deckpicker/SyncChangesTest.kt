// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The sync screen's table reads the backend's change lines: up is sent, down is received. */
class SyncChangesTest {
    @Test
    fun `reads sent and received from the backend's lines`() {
        assertEquals(
            SyncChanges(sentChanged = 3, receivedChanged = 12, sentDeleted = 0, receivedDeleted = 1),
            SyncChanges.parse("Added/modified: 3↑ 12↓", "Removed: 0↑ 1↓"),
        )
    }

    @Test
    fun `a line without two numbers gives no table`() {
        assertNull(SyncChanges.parse("Connecting…", "Removed: 0↑ 1↓"))
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import org.junit.Test
import kotlin.test.assertEquals

class CompactCountTest {
    @Test
    fun `counts below a thousand are shown as is`() {
        assertEquals("0", compactCount(0))
        assertEquals("7", compactCount(7))
        assertEquals("999", compactCount(999))
    }

    @Test
    fun `thousands keep one decimal below ten thousand`() {
        assertEquals("1k", compactCount(1_000))
        assertEquals("1.2k", compactCount(1_250))
        assertEquals("9.9k", compactCount(9_999))
    }

    @Test
    fun `larger thousands drop the decimal`() {
        assertEquals("10k", compactCount(10_000))
        assertEquals("123k", compactCount(123_456))
        assertEquals("999k", compactCount(999_999))
    }

    @Test
    fun `millions`() {
        assertEquals("1m", compactCount(1_000_000))
        assertEquals("1.2m", compactCount(1_234_567))
        assertEquals("12m", compactCount(12_345_678))
    }

    @Test
    fun `counts are rounded down, never up`() {
        assertEquals("1.9k", compactCount(1_999))
        assertEquals("19k", compactCount(19_999))
    }
}

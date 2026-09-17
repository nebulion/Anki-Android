// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.stats

import org.junit.Test
import kotlin.test.assertEquals

/** The d3 port gives the values d3 itself gives, so bars match the backend's graphs page. */
class D3Test {
    @Test
    fun `ticks match d3`() {
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0), ticks(0.0, 10.0, 7.0))
        assertEquals((-30..1).map { it.toDouble() }, ticks(-30.0, 1.0, 30.0))
        assertEquals(listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0), ticks(0.0, 1.0, 5.0))
        assertEquals(listOf(5.0), ticks(5.0, 5.0, 10.0))
        assertEquals(emptyList(), ticks(0.0, 10.0, 0.0))
    }

    @Test
    fun `nice rounds the domain outwards`() {
        assertEquals(0.0 to 40.0, nice(0.0 to 37.0))
        assertEquals(0.0 to 1000.0, nice(0.0 to 912.0))
        assertEquals(130.0 to 260.0, nice(130.0 to 251.0))
    }

    @Test
    fun `bin puts the upper bound in the last bin`() {
        val bins = bin(listOf(0, 1, 2, 3, 4), 0.0 to 3.0, listOf(1.0, 2.0)) { it.toDouble() }
        assertEquals(listOf(listOf(0), listOf(1), listOf(2, 3)), bins.map { it.items })
        assertEquals(listOf(0.0 to 1.0, 1.0 to 2.0, 2.0 to 3.0), bins.map { it.x0 to it.x1 })
    }

    @Test
    fun `bin ignores thresholds outside the domain`() {
        val bins = bin(listOf(5), 0.0 to 10.0, listOf(-5.0, 0.0, 5.0, 15.0)) { it.toDouble() }
        assertEquals(listOf(0.0 to 5.0, 5.0 to 10.0), bins.map { it.x0 to it.x1 })
        assertEquals(listOf(emptyList(), listOf(5)), bins.map { it.items })
    }

    @Test
    fun `quantile interpolates`() {
        assertEquals(2.5, quantileSorted(listOf(1, 2, 3, 4), 0.5))
        assertEquals(4.0, quantileSorted(listOf(1, 2, 3, 4), 1.0))
        assertEquals(null, quantileSorted(emptyList(), 0.5))
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.testutils.AnkiTest

/** Sets [flag] on the first card of [n]. Previously in the deleted `CardBrowserTest`. */
fun AnkiTest.flagCardForNote(
    n: Note,
    flag: Flag,
) {
    n.firstCard().update {
        setUserFlag(flag.code)
    }
}

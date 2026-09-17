// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.filtered

import androidx.annotation.StringRes
import com.ichi2.anki.R

/*
 * A filtered deck's search, split into the two things a person picks: which deck the cards come
 * from, and which cards. The search stays a plain Anki search underneath, so anything typed as a
 * custom search keeps working.
 */

/** Cards a filter can pick without typing a search. */
enum class FilterPreset(
    @StringRes val labelRes: Int,
    val search: String,
) {
    Due(R.string.mmd_filter_due, "is:due"),
    New(R.string.mmd_filter_new, "is:new"),
    ForgottenToday(R.string.mmd_filter_forgotten_today, "rated:1:1"),
    ReviewedToday(R.string.mmd_filter_reviewed_today, "rated:1"),
    AddedToday(R.string.mmd_filter_added_today, "added:1"),
    Flagged(R.string.mmd_filter_flagged, "-flag:0"),
    Marked(R.string.mmd_filter_marked, "tag:marked"),
    Suspended(R.string.mmd_filter_suspended, "is:suspended"),
}

private val deckClause = Regex("""(?:^|\s)(-?)deck:("(?:[^"\\]|\\.)*"|\S+)""")

/** The deck a search is limited to, e.g. `Italian::Verbs` for `deck:"Italian::Verbs" is:due`; null for none. */
fun searchDeck(search: String): String? {
    val match = deckClause.find(search) ?: return null
    if (match.groupValues[1] == "-") return null
    return match.groupValues[2]
        .removeSurrounding("\"")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

/** The search without its deck clause: which cards, e.g. `is:due`. */
fun searchCards(search: String): String = search.replace(deckClause, " ").trim().replace(Regex("\\s+"), " ")

/** A search from a deck (null for all decks) and the cards to pick from it. */
fun buildSearch(
    deck: String?,
    cards: String,
): String {
    val deckPart = deck?.let { "deck:\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
    return listOfNotNull(deckPart, cards.trim().ifEmpty { null }).joinToString(" ")
}

/** The preset a search's cards part is, or null for a custom search. */
fun presetOf(search: String): FilterPreset? = searchCards(search).let { cards -> FilterPreset.entries.firstOrNull { it.search == cards } }

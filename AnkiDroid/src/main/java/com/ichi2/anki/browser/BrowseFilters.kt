// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import anki.search.SearchNode
import anki.search.SearchNode.CardState
import anki.search.SearchNode.Flag
import anki.search.SearchNode.Group.Joiner
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.SortOrder

/*
 * The browser's filters (owner's layout A, 2026-09-17): narrow the cards down by deck, kind of card,
 * flag, tag, note type and when they were added, and by text in their fields. Choices within one
 * filter widen it (Italian *or* Spanish); different filters narrow each other (Italian *and*
 * suspended). Text search only looks at what the cards say, never at the filter words Anki's search
 * understands, so "deck:x" typed there is looked for as text.
 */

/** A kind of card, as Anki's search knows it. */
enum class StateFilter(
    val node: CardState,
) {
    New(CardState.CARD_STATE_NEW),
    Learning(CardState.CARD_STATE_LEARN),
    Review(CardState.CARD_STATE_REVIEW),
    Due(CardState.CARD_STATE_DUE),
    Suspended(CardState.CARD_STATE_SUSPENDED),
    Buried(CardState.CARD_STATE_BURIED),
}

/** A flag, or no flag, in the order Anki lists them; [value] is the card's stored flag. */
enum class FlagFilter(
    val node: Flag,
    val value: Int,
) {
    NoFlag(Flag.FLAG_NONE, 0),
    Red(Flag.FLAG_RED, 1),
    Orange(Flag.FLAG_ORANGE, 2),
    Green(Flag.FLAG_GREEN, 3),
    Blue(Flag.FLAG_BLUE, 4),
    Pink(Flag.FLAG_PINK, 5),
    Turquoise(Flag.FLAG_TURQUOISE, 6),
    Purple(Flag.FLAG_PURPLE, 7),
}

/** How recently the cards were added; null days is any time. */
enum class AddedFilter(
    val days: Int?,
) {
    AnyTime(null),
    Today(1),
    Week(7),
    Month(30),
    Year(365),
}

/** What a list of cards can be sorted by, as the backend's browser column keys. */
enum class BrowseSort(
    val columnKey: String,
) {
    SortField("noteFld"),
    Due("cardDue"),
    Added("noteCrt"),
    Modified("cardMod"),
    Deck("deck"),
    Interval("cardIvl"),
    Ease("cardEase"),
    Reviews("cardReps"),
    Lapses("cardLapses"),
}

/** One of the filters, to leave out when working out another filter's choices. */
enum class FilterPart { Deck, State, Flag, Tag, NoteType, Added, Text }

data class BrowseFilters(
    /** Full deck names; a deck includes its subdecks. */
    val decks: Set<String> = emptySet(),
    val states: Set<StateFilter> = emptySet(),
    val flags: Set<FlagFilter> = emptySet(),
    val tags: Set<String> = emptySet(),
    val noteTypes: Set<String> = emptySet(),
    val added: AddedFilter = AddedFilter.AnyTime,
    val text: String = "",
) {
    val isEmpty get() = this == BrowseFilters()

    /** The search nodes for every filter that is set, except [except]; all must match. */
    fun nodes(except: FilterPart? = null): List<SearchNode> =
        buildList {
            fun anyOf(
                part: FilterPart,
                nodes: List<SearchNode>,
            ) {
                if (part == except || nodes.isEmpty()) return
                add(nodes.singleOrNull() ?: nodes.or())
            }
            anyOf(FilterPart.Deck, decks.map { node { setDeck(it) } })
            anyOf(FilterPart.State, states.map { node { setCardState(it.node) } })
            anyOf(FilterPart.Flag, flags.map { node { setFlag(it.node) } })
            anyOf(FilterPart.Tag, tags.map { node { setTag(it) } })
            anyOf(FilterPart.NoteType, noteTypes.map { node { setNote(it) } })
            added.days?.let { days -> anyOf(FilterPart.Added, listOf(node { setAddedInDays(days) })) }
            // each word must appear somewhere in the card, as in Anki's own search
            anyOf(
                FilterPart.Text,
                text
                    .split(WHITESPACE)
                    .filter { it.isNotEmpty() }
                    .map { node { setLiteralText(it) } }
                    .and(),
            )
        }

    private fun List<SearchNode>.and(): List<SearchNode> = if (size <= 1) this else listOf(group(Joiner.AND))

    private fun List<SearchNode>.or(): SearchNode = group(Joiner.OR)

    private fun List<SearchNode>.group(joiner: Joiner) =
        node {
            setGroup(
                SearchNode.Group
                    .newBuilder()
                    .addAllNodes(this@group)
                    .setJoiner(joiner),
            )
        }

    private fun node(build: SearchNode.Builder.() -> Unit): SearchNode = SearchNode.newBuilder().apply(build).build()

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}

/** The search string for [filters] (every card when none is set), leaving out [except]. */
fun Collection.searchFor(
    filters: BrowseFilters,
    except: FilterPart? = null,
): String {
    val nodes = filters.nodes(except)
    return if (nodes.isEmpty()) "deck:*" else buildSearchString(nodes)
}

/** The backend's sort order for [sort]. */
fun Collection.sortOrder(
    sort: BrowseSort,
    reverse: Boolean,
): SortOrder = SortOrder.BuiltinColumnSortKind(allBrowserColumns().first { it.key == sort.columnKey }, reverse)

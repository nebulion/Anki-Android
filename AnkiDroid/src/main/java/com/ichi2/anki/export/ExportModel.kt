// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import android.content.Context
import anki.generic.Empty
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ankicommon.R
import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.ui.internationalization.toSentenceCase

/** What can be exported, in the order desktop Anki lists them. */
enum class ExportFormat(
    val extension: String,
) {
    /** The whole collection: `.colpkg` */
    Collection("colpkg"),

    /** A deck, or all of them: `.apkg` */
    Apkg("apkg"),

    /** Notes in plain text: `.txt` */
    Notes("txt"),

    /** Cards in plain text: `.txt` */
    Cards("txt"),
    ;

    /** Whether a deck is chosen; a collection is always exported whole. */
    val hasDeckChoice: Boolean
        get() = this != Collection

    /** The switches this format offers, in the order they are shown. */
    val options: List<ExportOption>
        get() =
            when (this) {
                Collection -> listOf(ExportOption.IncludeMedia, ExportOption.Legacy)
                Apkg ->
                    listOf(
                        ExportOption.IncludeSchedule,
                        ExportOption.IncludeDeckConfigs,
                        ExportOption.IncludeMedia,
                        ExportOption.Legacy,
                    )
                Notes ->
                    listOf(
                        ExportOption.IncludeHtml,
                        ExportOption.IncludeTags,
                        ExportOption.IncludeDeck,
                        ExportOption.IncludeNotetype,
                        ExportOption.IncludeGuid,
                    )
                Cards -> listOf(ExportOption.IncludeHtml)
            }

    /** The options switched on when this format is chosen, as desktop Anki has them. */
    val defaults: Set<ExportOption>
        get() = options.filter { it.isOnByDefault }.toSet()

    /** e.g. `Anki deck package (.apkg)` */
    fun label(context: Context): String =
        when (this) {
            Collection -> TR.exportingAnkiCollectionPackage().toSentenceCase(context, R.string.sentence_anki_collection_package)
            Apkg -> TR.exportingAnkiDeckPackage().toSentenceCase(context, R.string.sentence_anki_deck_package)
            Notes -> TR.exportingNotesInPlainText().toSentenceCase(context, R.string.sentence_notes_in_plain_text)
            Cards -> TR.exportingCardsInPlainText().toSentenceCase(context, R.string.sentence_cards_in_plain_text)
        } + " (.$extension)"
}

/** One switch of the export page. */
enum class ExportOption(
    val isOnByDefault: Boolean,
    val label: () -> String,
) {
    IncludeSchedule(true, { TR.exportingIncludeSchedulingInformation() }),
    IncludeDeckConfigs(false, { TR.exportingIncludeDeckConfigs() }),
    IncludeMedia(true, { TR.exportingIncludeMedia() }),
    Legacy(false, { TR.exportingSupportOlderAnkiVersions() }),
    IncludeHtml(true, { TR.exportingIncludeHtmlAndMediaReferences() }),
    IncludeTags(true, { TR.exportingIncludeTags() }),
    IncludeDeck(false, { TR.exportingIncludeDeck() }),
    IncludeNotetype(false, { TR.exportingIncludeNotetype() }),
    IncludeGuid(false, { TR.exportingIncludeGuid() }),
}

/** Limits an export to [deckId], or to the whole collection for [ALL_DECKS_ID]. */
fun exportLimitFor(deckId: DeckId): ExportLimit =
    if (deckId == ALL_DECKS_ID) {
        exportLimit { wholeCollection = Empty.getDefaultInstance() }
    } else {
        exportLimit { this.deckId = deckId }
    }

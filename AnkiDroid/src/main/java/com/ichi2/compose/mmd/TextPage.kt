// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.mmd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.R
import com.mudita.mmd.components.text.TextMMD

/** A block of a [TextPage]: a bold heading, or a paragraph. */
sealed interface TextBlock {
    data class Heading(
        val text: String,
    ) : TextBlock

    data class Paragraph(
        val text: String,
    ) : TextBlock
}

/**
 * Text to read on a page of its own: a header with a close (X) action at the top left, and the text
 * in a [PagedList] that turns a page at a time instead of scrolling. For help and descriptions,
 * which were panels whose text scrolled inside them.
 *
 * The text is split into paragraphs, and a long paragraph into sentences, because the list pages
 * by item and one item taller than the screen could never be turned past.
 */
@Composable
fun TextPage(
    title: String,
    blocks: List<TextBlock>,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            ScreenHeader(
                title = title,
                navigationIcon = {
                    HeaderAction(icon = R.drawable.close_icon, contentDescription = stringResource(R.string.close), onClick = onClose)
                },
            )
            PagedList(Modifier.weight(1f)) {
                items(blocks.flatMap(::pageSized)) { block ->
                    when (block) {
                        is TextBlock.Heading ->
                            TextMMD(
                                text = block.text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(start = RowDefaults.EdgePadding, top = 16.dp, end = 8.dp),
                            )
                        is TextBlock.Paragraph ->
                            TextMMD(
                                text = block.text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier =
                                    Modifier.fillMaxWidth().padding(
                                        start = RowDefaults.EdgePadding,
                                        top = 8.dp,
                                        end = 8.dp,
                                        bottom = 4.dp,
                                    ),
                            )
                    }
                }
            }
        }
    }
}

/** Paragraphs from plain text: split at line breaks, blank lines dropped. */
fun paragraphs(text: String): List<TextBlock> =
    text
        .split(Regex("\n+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { TextBlock.Paragraph(it) }

private const val MAX_PARAGRAPH_CHARS = 400

/** A paragraph too long for one item is split at sentence ends into pieces of at most about 400 characters. */
private fun pageSized(block: TextBlock): List<TextBlock> {
    if (block !is TextBlock.Paragraph || block.text.length <= MAX_PARAGRAPH_CHARS) return listOf(block)
    val pieces = mutableListOf<String>()
    var current = StringBuilder()
    for (sentence in block.text.split(Regex("(?<=[.!?。])\\s+"))) {
        if (current.isNotEmpty() && current.length + sentence.length > MAX_PARAGRAPH_CHARS) {
            pieces += current.toString()
            current = StringBuilder()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(sentence)
    }
    if (current.isNotEmpty()) pieces += current.toString()
    return pieces.map { TextBlock.Paragraph(it) }
}

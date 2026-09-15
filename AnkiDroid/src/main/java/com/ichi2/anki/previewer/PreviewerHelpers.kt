// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.previewer

import android.content.Context
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.themes.Themes
import org.intellij.lang.annotations.Language

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` and `ankidroid-cardviewer.js` are added
 *
 * Aimed to be used only for reviewing/previewing cards
 *
 * @param extraJsAssets paths of additional Javascript assets
 * in the `android_assets` folder to be included
 */
@Language("HTML")
fun stdHtml(
    context: Context = appContext,
    extraJsAssets: List<String> = emptyList(),
    nightMode: Boolean = false,
): String {
    val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"
    // E Ink: always black on white, whatever the theme; mmd-card.css forces the rest
    val colors = ":root { --canvas: #FFFFFF; --fg: #000000; }"

    val jsAssets: List<String> =
        listOf(
            "backend/js/jquery.min.js",
            "backend/js/mathjax.js",
            "backend/js/vendor/mathjax/tex-chtml-full.js",
            "backend/js/reviewer.js",
            "scripts/ankidroid-cardviewer.js",
        ) + extraJsAssets
    val jsTxt =
        jsAssets.joinToString("\n") {
            """<script src="file:///android_asset/$it"></script>"""
        }

    return """
        <!DOCTYPE html>
        <html dir="$languageDirectionality" data-bs-theme="light">
        <head>
            <title>AnkiDroid</title>
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/root-vars.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/ankidroid.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/mmd-card.css">
            <style>
                $colors
            </style>
        </head>
        <body class="${bodyClass()}">
            <div id="qa" dir="auto"></div>
            $jsTxt
        </body>
        </html>
        """.trimIndent()
}

/**
 * "mathjax-rendered" is a legacy class kept only to support old note types.
 *
 * @return body classes used when showing a card
 */
fun bodyClassForCardOrd(
    cardOrd: CardOrdinal,
    nightMode: Boolean = Themes.isNightTheme,
): String = "card card${cardOrd + 1} ${bodyClass(nightMode)} mathjax-rendered"

private fun bodyClass(nightMode: Boolean = Themes.isNightTheme): String = if (nightMode) "nightMode night_mode" else ""

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.net.Uri
import java.io.File

/**
 * Result codes returned when the study screen finishes.
 *
 * Previously in the legacy `AbstractFlashcardViewer`, deleted in the MMD fork.
 */
object ViewerResult {
    const val RESULT_DEFAULT = 50
    const val RESULT_NO_MORE_CARDS = 52
}

/**
 * @param mediaDir media directory path on SD card
 * @return path converted to file URL, properly UTF-8 URL encoded
 */
fun getMediaBaseUrl(mediaDir: File): String {
    // Use android.net.Uri class to ensure whole path is properly encoded
    // File.toURL() does not work here, and URLEncoder class is not directly usable
    // with existing slashes
    if (mediaDir.absolutePath.isNotEmpty()) {
        val mediaDirUri = Uri.fromFile(mediaDir)
        return "$mediaDirUri/"
    }
    return ""
}

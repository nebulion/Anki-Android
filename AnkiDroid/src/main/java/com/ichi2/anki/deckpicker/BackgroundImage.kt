// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.CheckResult
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.getSizeOfBitmapFromCollection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

const val BITMAP_BYTES_PER_PIXEL = 4

object BackgroundImage {
    /*
     * RecordingCanvas.MAX_BITMAP_SIZE is @hide
     * https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/RecordingCanvas.java;l=49;drc=ed769e0aede2a840ea8cdff87ce593eb6ea8a7c6;bpv=1;bpt=1?q=%22trying%20to%20draw%20too%20large%22
     *
     * WARN: This skips a test for "ro.hwui.max_texture_allocation_size"
     * The actual size may be larger, this is a minimum
     */
    const val MAX_BITMAP_SIZE: Long = 100 * 1024 * 1024

    /**
     * The name of the file in the collection folder representing the background the user selected
     * for [DeckPicker].
     */
    const val FILENAME = "DeckPickerBackground.png"

    fun shouldBeShown(context: Context) = Prefs.isBackgroundEnabled && getImageFile(context) != null

    /** Outcome of resolving the user's [DeckPicker] background. */
    sealed interface ResolveResult {
        /** Either disabled, or no usable image file is present. */
        data object None : ResolveResult

        /** A drawable ready to assign to a view. */
        data class Ready(
            val drawable: Drawable,
        ) : ResolveResult

        /** Resolving failed in a way the user should be told about. */
        sealed interface Failure : ResolveResult {
            fun message(context: Context): String
        }

        /** The bitmap is too large to draw safely. */
        data object TooLarge : Failure {
            override fun message(context: Context): String = context.getString(R.string.background_image_too_large)
        }

        /** Decoding failed for some other reason. */
        data class Failed(
            val cause: String?,
        ) : Failure {
            override fun message(context: Context): String = context.getString(R.string.failed_to_apply_background_image, cause)
        }
    }

    /**
     * Resolves the configured [DeckPicker] background, applying all OOM/size guards.
     *
     * Performs disk I/O and bitmap decoding.
     *
     * This method does not throw.
     */
    @CheckResult
    suspend fun resolve(context: Context): ResolveResult {
        if (!Prefs.isBackgroundEnabled) {
            Timber.d("No DeckPicker background preference")
            return ResolveResult.None
        }
        val imgFile =
            getImageFile(context) ?: run {
                Timber.d("No DeckPicker background image")
                return ResolveResult.None
            }

        // 15450 - guard against decoded dimensions that would crash Canvas.
        val size = context.getSizeOfBitmapFromCollection(FILENAME) ?: return ResolveResult.None
        if (size.width <= 0 || size.height <= 0) {
            Timber.w("Decoding background image for dimensions info failed")
            return ResolveResult.None
        }
        if (size.width.toLong() * size.height * BITMAP_BYTES_PER_PIXEL > MAX_BITMAP_SIZE) {
            Timber.w("DeckPicker background image dimensions too large")
            return ResolveResult.TooLarge
        }

        return try {
            Timber.i("Loading background image selected by user")
            val drawable =
                withContext(Dispatchers.IO) {
                    // 6608 - OOM should be catchable here.
                    Drawable.createFromPath(imgFile.absolutePath)
                }
            if (drawable != null) ResolveResult.Ready(drawable) else ResolveResult.None
        } catch (e: OutOfMemoryError) {
            Timber.w(e, "Failed to load background - OOM")
            ResolveResult.TooLarge
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to load background")
            ResolveResult.Failed(cause = e.localizedMessage)
        }
    }

    /** @return a [File] referencing the image, or `null` if the file does not exist */
    private fun getImageFile(context: Context): File? {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        val imgFile = File(currentAnkiDroidDirectory, FILENAME)
        if (!imgFile.exists()) {
            return null
        }
        return imgFile
    }
}

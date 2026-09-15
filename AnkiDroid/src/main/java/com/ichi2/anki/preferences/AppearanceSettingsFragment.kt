// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences

import android.content.ActivityNotFoundException
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.deckpicker.BackgroundImage.FileSizeResult
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

class AppearanceSettingsFragment : SettingsFragment() {
    private var backgroundImage: Preference? = null
    private var removeBackgroundPref: Preference? = null
    override val preferenceResource: Int
        get() = R.xml.preferences_appearance
    override val analyticsScreenNameConstant: String
        get() = "prefs.appearance"

    override fun initSubscreen() {
        preferenceScreen.title = TR.preferencesAppearance()

        // Configure background
        backgroundImage = requirePreference<Preference>("deckPickerBackground")
        backgroundImage!!.title = TR.sentenceCase.selectImage
        removeBackgroundPref = requirePreference<Preference>("removeWallPaper")
        backgroundImage!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                try {
                    backgroundImageResultLauncher.launch("image/*")
                } catch (ex: ActivityNotFoundException) {
                    Timber.w(ex, "No app found to handle background preference change request")
                    activity?.showSnackbar(R.string.activity_start_failed)
                }
                true
            }
        removeBackgroundPref?.setOnPreferenceClickListener {
            showRemoveBackgroundImageDialog()
            true
        }

        // Initially update visibility based on whether a background exists
        updateRemoveBackgroundVisibility()

        // Show estimate time
        // Represents the collection pref "estTime": i.e.
        // whether the buttons should indicate the duration of the interval if we click on them.
        requirePreference<SwitchPreferenceCompat>(R.string.show_estimates_preference).apply {
            launchCatchingTask { isChecked = CollectionPreferences.getShowIntervalOnButtons() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setShowIntervalsOnButtons(newValue) }
            }
        }
        // Show progress
        // Represents the collection pref "dueCounts": i.e.
        // whether the remaining number of cards should be shown.
        requirePreference<SwitchPreferenceCompat>(R.string.show_progress_preference).apply {
            launchCatchingTask { isChecked = CollectionPreferences.getShowRemainingDueCounts() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setShowRemainingDueCounts(newValue) }
            }
        }

        // Show play buttons on cards with audio
        // Note: Stored inverted in the collection as HIDE_AUDIO_PLAY_BUTTONS
        requirePreference<SwitchPreferenceCompat>(R.string.show_audio_play_buttons_key).apply {
            title = CollectionManager.TR.preferencesShowPlayButtonsOnCardsWith()
            launchCatchingTask { isChecked = !CollectionPreferences.getHidePlayAudioButtons() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setHideAudioPlayButtons(!newValue) }
            }
        }

        setupNewStudyScreenSettings()
    }

    private fun updateRemoveBackgroundVisibility() {
        removeBackgroundPref?.isVisible = BackgroundImage.shouldBeShown(requireContext())
    }

    private fun showRemoveBackgroundImageDialog() {
        AlertDialog.Builder(requireContext()).show {
            title(R.string.remove_background_image)
            positiveButton(R.string.dialog_remove) {
                if (BackgroundImage.remove(requireContext())) {
                    showSnackbar(R.string.background_image_removed)
                    updateRemoveBackgroundVisibility()
                } else {
                    showSnackbar(R.string.error_deleting_image)
                }
            }
            negativeButton(R.string.dialog_keep)
        }
    }

    private val backgroundImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { selectedImage ->
            if (selectedImage == null) {
                if (BackgroundImage.shouldBeShown(requireContext())) {
                    showRemoveBackgroundImageDialog()
                } else {
                    showSnackbar(R.string.no_image_selected)
                }
                return@registerForActivityResult
            }
            // handling file may result in exception
            try {
                when (val sizeResult = BackgroundImage.validateBackgroundImageFileSize(this, selectedImage)) {
                    is FileSizeResult.FileTooLarge -> {
                        showThemedToast(requireContext(), getString(R.string.image_max_size_allowed, sizeResult.maxMB), false)
                    }
                    is FileSizeResult.UncompressedBitmapTooLarge -> {
                        showThemedToast(
                            requireContext(),
                            getString(R.string.image_dimensions_too_large, sizeResult.width, sizeResult.height),
                            false,
                        )
                    }
                    is FileSizeResult.OK -> {
                        BackgroundImage.import(this, selectedImage)
                        updateRemoveBackgroundVisibility()
                    }
                }
            } catch (e: OutOfMemoryError) {
                Timber.w(e)
                showSnackbar(getString(R.string.error_selecting_image, e.localizedMessage))
            } catch (e: Exception) {
                Timber.w(e)
                showSnackbar(getString(R.string.error_selecting_image, e.localizedMessage))
            }
        }

    private fun setupNewStudyScreenSettings() {
        if (!Prefs.isNewStudyScreenEnabled) return
        for (key in legacyStudyScreenSettings) {
            val keyString = getString(key)
            findPreference<Preference>(keyString)?.isVisible = false
        }
    }

    companion object {
        val legacyStudyScreenSettings =
            listOf(
                R.string.study_screen_category_key,
                R.string.custom_buttons_link_preference,
                R.string.fullscreen_mode_preference,
                R.string.center_vertically_preference,
                R.string.show_estimates_preference,
                R.string.answer_buttons_position_preference,
                R.string.show_topbar_preference,
                R.string.show_eta_preference,
                R.string.show_audio_play_buttons_key,
                R.string.show_deck_title_key,
            )
    }
}

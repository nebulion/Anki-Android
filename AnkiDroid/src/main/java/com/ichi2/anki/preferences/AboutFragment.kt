// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences

import android.os.Bundle
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentAboutBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.scheduling.Fsrs
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.IntentUtil
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.copyToClipboard
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.ankiweb.rsdroid.BuildConfig as BackendBuildConfig

class AboutFragment : Fragment(R.layout.fragment_about) {
    @VisibleForTesting
    val binding by viewBinding(FragmentAboutBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        binding.buildDate.text =
            SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM yyyy"))
                .format(Date(BuildConfig.BUILD_TIME))

        binding.version.text = pkgVersionName
        binding.backendVersion.text =
            "(anki " + BackendBuildConfig.ANKI_DESKTOP_VERSION + " / " + BackendBuildConfig.ANKI_COMMIT_HASH.subSequence(0, 8) + ")"
        binding.fsrsVersion.text = Fsrs.displayVersion ?.let { version ->
            "($version)"
        } ?: ""

        // Contributors text
        val contributorsLink = getString(R.string.link_contributors)
        val contributingGuideLink = getString(R.string.link_contribution)
        binding.contributorsDescription.apply {
            text = getString(R.string.about_contributors_description, contributorsLink, contributingGuideLink).parseAsHtml()
            movementMethod = LinkMovementMethod.getInstance()
        }

        // License text
        val gplLicenseLink = getString(R.string.licence_wiki)
        val agplLicenseLink = getString(R.string.link_agpl_wiki)
        val sourceCodeLink = getString(R.string.link_source)
        val dependencyLicenseLink = getString(R.string.dependency_license_wiki)
        binding.licenseDescription.apply {
            text =
                (
                    getString(R.string.license_description, gplLicenseLink, agplLicenseLink, sourceCodeLink) + "<br>" +
                        getString(R.string.other_licenses, dependencyLicenseLink)
                ).parseAsHtml()
            movementMethod = LinkMovementMethod.getInstance()
        }

        // Donate text
        if (BuildConfig.SHOW_DONATE_LINKS) {
            val donateLink = getString(R.string.link_opencollective_donate)
            binding.donateDescription.apply {
                text = getString(R.string.donate_description, donateLink).parseAsHtml()
                movementMethod = LinkMovementMethod.getInstance()
            }
        } else {
            binding.aboutDonateTitle.isVisible = false
            binding.donateDescription.isVisible = false
        }

        binding.rateAnkiDroid.setOnClickListener {
            IntentUtil.tryOpenIntent(requireAnkiActivity(), AnkiDroidApp.getMarketIntent(requireContext()))
        }

        binding.copyDebugInfo.text = TR.sentenceCase.copyDebugInfo
        binding.copyDebugInfo.setOnClickListener { copyDebugInfo() }
    }

    /**
     * Copies debug info (from [DebugInfoService.getDebugInfo]) to the clipboard
     */
    private fun copyDebugInfo() {
        launchCatchingTask {
            val debugInfo =
                withContext(Dispatchers.IO) {
                    DebugInfoService.getDebugInfo(requireContext())
                }
            requireContext().copyToClipboard(
                debugInfo,
                failureMessageId = R.string.about_ankidroid_error_copy_debug_info,
            )
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki

import android.view.View
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.core.view.allViews
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.Before
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File

interface ScreenshotTestCategory

/**
 * Base class for [roborazzi](https://github.com/takahirom/roborazzi) screenshot tests
 */
@RunWith(RobolectricTestParameterInjector::class)
@Category(ScreenshotTestCategory::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest : RobolectricTest() {
    companion object {
        /**
         * Fixed `versionName` reported to screenshot tests, so screens which display
         * the app version do not change on each release.
         */
        const val STABLE_VERSION_NAME = "X.Y.Z-screenshot"
    }

    var fileNamePrefix = ""

    /** The MMD fork has one theme */
    enum class ThemeConfig { EINK, }

    enum class DeviceConfig { PHONE, TABLET, FOLDABLE, DESKTOP, KOMPAKT }

    @TestParameter(valuesProvider = ThemeProvider::class)
    lateinit var theme: ThemeConfig

    @TestParameter(valuesProvider = DeviceProvider::class)
    lateinit var device: DeviceConfig

    @Before
    open fun applyGlobalConfig() {
        stabilizeVersionName()
        applyDeviceConfig()
        applyThemeConfig()
    }

    /** Reports [STABLE_VERSION_NAME] as the `versionName` shown on-screen */
    private fun stabilizeVersionName() {
        shadowOf(targetContext.packageManager)
            .getInternalMutablePackageInfo(targetContext.packageName)
            .versionName = STABLE_VERSION_NAME
    }

    protected open fun applyDeviceConfig() {
        when (device) {
            DeviceConfig.PHONE -> setPhoneQualifiers()
            DeviceConfig.TABLET -> {
                setTabletQualifiers()
                fileNamePrefix += "tablet_"
            }
            DeviceConfig.FOLDABLE -> {
                setFoldableQualifiers()
                fileNamePrefix += "foldable_"
            }
            DeviceConfig.DESKTOP -> {
                setDesktopQualifiers()
                fileNamePrefix += "desktop_"
            }
            DeviceConfig.KOMPAKT -> {
                setKompaktQualifiers()
                fileNamePrefix += "kompakt_"
            }
        }
    }

    /** The E Ink theme is always applied; file names keep the `eink_` prefix */
    protected open fun applyThemeConfig() {
        fileNamePrefix += "${theme.name.lowercase()}_"
    }

    /** Pixel-class phone in portrait */
    protected fun setPhoneQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.MediumPhone)

    protected fun setTabletQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.MediumTablet)

    protected fun setFoldableQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.Pixel9ProFold)

    protected fun setDesktopQualifiers() = RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.MediumDesktop)

    /** Mudita Kompakt, the MMD fork's target: 480×800px at 213dpi (tvdpi) = 360×601dp portrait */
    protected fun setKompaktQualifiers() = RuntimeEnvironment.setQualifiers("w360dp-h601dp-port-tvdpi")

    /**
     * Captures a screenshot to `build/outputs/roborazzi/<TestClass>/<name>.png`.
     *
     * Writes to /diffs/ if there is an issue.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    protected fun captureScreen(name: String) {
        // Note: this.javaClass should not be used inside a lambda, as 'this' will be unnamed
        val classDir = "build/outputs/roborazzi/${this.javaClass.simpleName}"
        val diffDir = File("$classDir/diffs")
        // baseline is always in the root for the class, copied to /diffs/ if a change occurred
        val fileName = "$fileNamePrefix$name.png"
        val baseline = File(classDir, fileName)
        disableScrollbarFading()
        captureScreenRoboImage(
            filePath = baseline.path,
            roborazziOptions = provideRoborazziContext().options.withCompareOutputDir(diffDir.path),
        )

        // copy the baseline into /diffs (if it exists)
        // /diffs/ is used so 'clean' baselines are not mixed with diffs to inspect
        val diffWritten =
            File(diffDir, "${name}_compare.png").exists() ||
                File(diffDir, "${name}_actual.png").exists()
        if (diffWritten && baseline.isFile) {
            baseline.copyTo(File(diffDir, baseline.name), overwrite = true)
        }
    }

    /** [View.disableScrollbarFading] for every window */
    private fun disableScrollbarFading() {
        val windowManager = targetContext.getSystemService<WindowManager>()
        val windows = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views
        windows.forEach { it.disableScrollbarFading() }
    }

    class ThemeProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context?): List<ThemeConfig> {
            // -Ptheme is ignored: there is only one theme
            return ThemeConfig.entries
        }
    }

    class DeviceProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context?): List<DeviceConfig> {
            val requestedDevice = System.getProperty("screenshot.device") ?: "phone"
            val requestedDevices = requestedDevice.split(",").map { it.trim().lowercase() }
            if ("all" in requestedDevices) {
                return DeviceConfig.entries
            }
            return DeviceConfig.entries.filter { requestedDevices.contains(it.name.lowercase()) }
        }
    }
}

fun View.disableScrollbarFading() {
    allViews
        .filter { it.isScrollbarFadingEnabled }
        .forEach { it.isScrollbarFadingEnabled = false }
}

/** Sets the directory for _actual.png and _compare.png */
@OptIn(ExperimentalRoborazziApi::class)
private fun RoborazziOptions.withCompareOutputDir(dir: String): RoborazziOptions =
    copy(compareOptions = compareOptions.copy(outputDirectoryPath = dir))

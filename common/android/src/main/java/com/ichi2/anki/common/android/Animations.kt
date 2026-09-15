// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki.common.android

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * Animation related functionality for the app.
 */
object Animations {
    /**
     * Always `false`: the MMD fork targets an E Ink panel, where every animation frame is a
     * partial refresh that ghosts. Motion is off regardless of system or app settings.
     */
    @Suppress("UNUSED_PARAMETER", "SameReturnValue")
    fun areAnimationsEnabled(context: Context): Boolean = false
}

/**
 * Whether animations should not be displayed. Always `true` on the MMD fork.
 *
 * @see animationEnabled
 */
fun FragmentActivity.animationDisabled(): Boolean = !Animations.areAnimationsEnabled(this)

/**
 * Whether animations should be displayed. Always `false` on the MMD fork.
 *
 * @see animationDisabled
 */
fun FragmentActivity.animationEnabled(): Boolean = !animationDisabled()

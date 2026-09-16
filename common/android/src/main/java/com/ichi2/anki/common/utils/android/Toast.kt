// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.common.utils.android

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Set by the app to show toasts in its own message bar where the screen has one; returns true if it
 * did. A toast is a system window that fades in and out, which E Ink repaints several times.
 */
var themedToastRouter: ((context: Context, text: String, shortLength: Boolean) -> Boolean)? = null

fun showThemedToast(
    context: Context,
    text: String,
    shortLength: Boolean,
) {
    if (themedToastRouter?.invoke(context, text, shortLength) == true) return
    Toast.makeText(context, text, if (shortLength) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
}

fun showThemedToast(
    context: Context,
    text: CharSequence,
    shortLength: Boolean,
) {
    showThemedToast(context, text.toString(), shortLength)
}

fun showThemedToast(
    context: Context,
    @StringRes textResource: Int,
    shortLength: Boolean,
) {
    showThemedToast(context, context.getString(textResource), shortLength)
}

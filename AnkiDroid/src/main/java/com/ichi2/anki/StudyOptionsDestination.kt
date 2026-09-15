// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.deckpage.DeckPageFragment

/** Builds the [Intent] that opens the selected deck's page. */
fun StudyOptionsDestination.toIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, DeckPageFragment::class)

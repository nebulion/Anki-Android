// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.pages.Statistics
import com.mudita.mmd.components.nav_bar.NavigationBarItemMMD
import com.mudita.mmd.components.nav_bar.NavigationBarMMD
import com.mudita.mmd.components.text.TextMMD

/** The home screen's tabs, in bottom-bar order. */
enum class HomeTab(
    @DrawableRes val icon: Int,
    /** Fragment tag, used to find and reuse the tab's fragment. */
    val tag: String,
) {
    DECKS(R.drawable.ic_list_black, "home_tab_decks"),
    STATISTICS(R.drawable.ic_bar_chart_black, "home_tab_statistics"),
    MORE(R.drawable.ic_menu_24, "home_tab_more"),
    ;

    fun createFragment(): Fragment =
        when (this) {
            DECKS -> DeckListFragment()
            STATISTICS -> Statistics().apply { arguments = bundleOf(Statistics.ARG_HIDE_BACK_BUTTON to true) }
            MORE -> MoreTabFragment()
        }

    @Composable
    fun label(): String =
        when (this) {
            DECKS -> TR.actionsDecks()
            STATISTICS -> TR.statisticsTitle()
            MORE -> stringResource(R.string.bottom_nav_more)
        }
}

/** The MMD bottom bar switching between [HomeTab]s. */
@Composable
fun HomeBottomBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
) {
    NavigationBarMMD {
        HomeTab.entries.forEach { tab ->
            NavigationBarItemMMD(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painter = painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { TextMMD(text = tab.label()) },
            )
        }
    }
}

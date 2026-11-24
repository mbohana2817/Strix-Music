/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.blazify.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.strix_nav_home,
        iconIdActive = R.drawable.strix_nav_home,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.strix_nav_search,
        iconIdActive = R.drawable.strix_nav_search,
        route = "search_input"
    )

    object ListenTogether : Screens(
        titleId = R.string.together,
        iconIdInactive = R.drawable.group_outlined,
        iconIdActive = R.drawable.group_filled,
        route = "listen_together"
    )

    object Yours : Screens(
        titleId = R.string.settings,
        iconIdInactive = R.drawable.settings,
        iconIdActive = R.drawable.settings,
        route = "settings"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.strix_nav_library,
        iconIdActive = R.drawable.strix_nav_library,
        route = "library"
    )

    companion object {
        val MainScreens = listOf(Home, Search, ListenTogether, Library, Yours)
    }
}

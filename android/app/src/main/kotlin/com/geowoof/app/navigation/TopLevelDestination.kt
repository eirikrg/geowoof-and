package com.geowoof.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.geowoof.app.R
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.feature.map.navigation.MapRoute
import com.geowoof.feature.profile.navigation.ProfileRoute
import com.geowoof.feature.search.navigation.SearchRoute
import com.geowoof.feature.social.navigation.CommunityRoute
import kotlin.reflect.KClass

/** Destinations of the main navigation (docs/decisions/0013), in display order. */
enum class TopLevelDestination(
    @param:DrawableRes val selectedIcon: Int,
    @param:DrawableRes val unselectedIcon: Int,
    @param:StringRes val label: Int,
    val route: KClass<*>,
) {
    Map(GeoWoofIcons.MapFilled, GeoWoofIcons.Map, R.string.destination_map, MapRoute::class),
    Community(GeoWoofIcons.GroupsFilled, GeoWoofIcons.Groups, R.string.destination_community, CommunityRoute::class),
    Search(GeoWoofIcons.Search, GeoWoofIcons.Search, R.string.destination_search, SearchRoute::class),
    Profile(GeoWoofIcons.PersonFilled, GeoWoofIcons.Person, R.string.destination_profile, ProfileRoute::class),
}

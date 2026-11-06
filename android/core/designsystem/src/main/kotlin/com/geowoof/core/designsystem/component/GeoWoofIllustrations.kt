package com.geowoof.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.geowoof.core.designsystem.R

/** The illustrations of the app, from docs/design/illustrations. */
object GeoWoofIllustrations {
    @DrawableRes val EmptyBlocked: Int = R.drawable.core_designsystem_illustration_empty_blocked

    @DrawableRes val EmptyFeed: Int = R.drawable.core_designsystem_illustration_empty_feed

    @DrawableRes val EmptyFriends: Int = R.drawable.core_designsystem_illustration_empty_friends

    @DrawableRes val EmptyMessages: Int = R.drawable.core_designsystem_illustration_empty_messages

    @DrawableRes val EmptyNotifications: Int = R.drawable.core_designsystem_illustration_empty_notifications

    @DrawableRes val EmptyPets: Int = R.drawable.core_designsystem_illustration_empty_pets

    @DrawableRes val EmptyPhotos: Int = R.drawable.core_designsystem_illustration_empty_photos

    @DrawableRes val EmptyRequests: Int = R.drawable.core_designsystem_illustration_empty_requests

    @DrawableRes val EmptySaved: Int = R.drawable.core_designsystem_illustration_empty_saved

    @DrawableRes val EmptySearch: Int = R.drawable.core_designsystem_illustration_empty_search

    @DrawableRes val EmptyWalks: Int = R.drawable.core_designsystem_illustration_empty_walks

    @DrawableRes val ErrorConnection: Int = R.drawable.core_designsystem_illustration_error_connection

    @DrawableRes val FirstPet: Int = R.drawable.core_designsystem_illustration_first_pet

    @DrawableRes val PermissionLocation: Int = R.drawable.core_designsystem_illustration_permission_location

    @DrawableRes val PermissionNotifications: Int = R.drawable.core_designsystem_illustration_permission_notifications

    @DrawableRes val WalkDone: Int = R.drawable.core_designsystem_illustration_walk_done

    @DrawableRes val WelcomeHero: Int = R.drawable.core_designsystem_illustration_welcome_hero
}

/**
 * An illustration on its own soft background, which is the same in both themes: in the dark one it is
 * dimmed so that it does not dazzle. Decorative: what it means has to be said by the text next to it.
 */
@Composable
fun GeoWoofIllustration(@DrawableRes illustration: Int, modifier: Modifier = Modifier) {
    val isOnDark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE
    Image(
        painter = painterResource(illustration),
        contentDescription = null,
        modifier = modifier,
        alpha = if (isOnDark) ALPHA_ON_DARK else 1f,
    )
}

private const val ALPHA_ON_DARK = 0.88f

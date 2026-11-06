package com.geowoof.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.navigation.NavBackStackEntry

private const val OUT_MILLIS = 90
private const val IN_MILLIS = 210
private const val INITIAL_SCALE = 0.97f

/**
 * "Fade through" of Material: what leaves goes quickly, and what arrives grows a little. The same for
 * every screen, because tabs have no direction to slide in and a list that opens has no need of one.
 */
internal val screenEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(IN_MILLIS, delayMillis = OUT_MILLIS)) +
        scaleIn(tween(IN_MILLIS, delayMillis = OUT_MILLIS), initialScale = INITIAL_SCALE)
}

internal val screenExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(OUT_MILLIS))
}

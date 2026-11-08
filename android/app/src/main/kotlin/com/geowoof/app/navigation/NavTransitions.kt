package com.geowoof.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/*
 * How one screen gives way to another, the same in the main shell, in signing in and in the onboarding: the three
 * NavHosts take these four, and nothing else defines one.
 *
 * Opening a screen brings it in from the side over the one there was, which moves back a little. Closing it shrinks
 * it and fades it over the previous one, which is already there underneath: it is how Android itself goes back
 * between apps and activities since Android 14. While the back gesture is being made (predictive back), Navigation
 * follows the finger with these same transitions, so the gesture and the button look the same, and a gesture that
 * is not finished puts the screen back. Tabs of the bottom bar have no forward or back: between them it is the fade
 * through of Material, as before.
 */

/** The easings of Material 3 motion: emphasized, and decelerated for what arrives. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private const val OPEN_MILLIS = 400
private const val OPEN_FADE_MILLIS = 150
private const val CLOSE_MILLIS = 350

/** What arrives starts a quarter of the screen away, and what stays behind moves a tenth. */
private const val ARRIVING_OFFSET = 4
private const val BEHIND_OFFSET = 10

/** A screen that closes ends at this size, as the system does with the back gesture. */
private const val CLOSED_SCALE = 0.9f

private const val TAB_OUT_MILLIS = 90
private const val TAB_IN_MILLIS = 210
private const val TAB_INITIAL_SCALE = 0.97f

internal val screenEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isBetweenTabs()) {
        tabEnter()
    } else {
        slideInHorizontally(tween(OPEN_MILLIS, easing = EmphasizedDecelerate)) { it / ARRIVING_OFFSET } +
            fadeIn(tween(OPEN_FADE_MILLIS, easing = LinearEasing))
    }
}

/** The screen stays under the new one, a little to the side, until it covers it. */
internal val screenExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isBetweenTabs()) {
        tabExit()
    } else {
        slideOutHorizontally(tween(OPEN_MILLIS, easing = EmphasizedDecelerate)) { -it / BEHIND_OFFSET }
    }
}

/** Back where it was, and whole from the start: the one that closes shrinks over it. */
internal val screenPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isBetweenTabs()) {
        tabEnter()
    } else {
        slideInHorizontally(tween(CLOSE_MILLIS, easing = Emphasized)) { -it / BEHIND_OFFSET }
    }
}

/**
 * It shrinks with the finger from the start of the gesture, and only fades in its second half: while the
 * person is still deciding, it is still there.
 */
internal val screenPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isBetweenTabs()) {
        tabExit()
    } else {
        scaleOut(tween(CLOSE_MILLIS, easing = Emphasized), targetScale = CLOSED_SCALE) +
            slideOutHorizontally(tween(CLOSE_MILLIS, easing = Emphasized)) { it / BEHIND_OFFSET } +
            fadeOut(tween(CLOSE_MILLIS / 2, delayMillis = CLOSE_MILLIS / 2, easing = LinearEasing))
    }
}

/** Fade through: what leaves goes quickly, and what arrives grows a little. */
private fun tabEnter(): EnterTransition = fadeIn(tween(TAB_IN_MILLIS, delayMillis = TAB_OUT_MILLIS)) +
    scaleIn(tween(TAB_IN_MILLIS, delayMillis = TAB_OUT_MILLIS), initialScale = TAB_INITIAL_SCALE)

private fun tabExit(): ExitTransition = fadeOut(tween(TAB_OUT_MILLIS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isBetweenTabs() =
    initialState.destination.isTab() && targetState.destination.isTab()

private fun NavDestination.isTab() = TopLevelDestination.entries.any { hasRoute(it.route) }

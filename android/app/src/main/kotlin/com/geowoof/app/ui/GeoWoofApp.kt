package com.geowoof.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.geowoof.app.AppUiState
import com.geowoof.app.R
import com.geowoof.app.navigation.AuthNavHost
import com.geowoof.app.navigation.GeoWoofNavHost
import com.geowoof.app.navigation.OnboardingNavHost
import com.geowoof.app.navigation.TopLevelDestination
import com.geowoof.core.designsystem.component.FittedText
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User
import com.geowoof.core.ui.Inbox
import com.geowoof.core.ui.LocalInbox
import com.geowoof.feature.chat.navigation.navigateToConversations
import com.geowoof.feature.notifications.navigation.navigateToNotifications

/** Root of the UI: the session decides between the signed-out flow, the onboarding and the main shell. */
@Composable
fun GeoWoofApp(
    uiState: AppUiState,
    onFinishFirstPetStep: () -> Unit,
    onConsumeLink: () -> Unit,
    onAskedForNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SessionGate(
        uiState = uiState,
        signedOut = { AuthNavHost() },
        onboarding = { user ->
            OnboardingNavHost(
                emailVerified = user.emailVerified,
                offerFirstPet = uiState.offerFirstPet,
                onFinishFirstPetStep = onFinishFirstPetStep,
            )
        },
        main = {
            MainShell(
                pendingRequests = uiState.pendingRequests,
                unreadMessages = uiState.unreadMessages,
                unreadNotifications = uiState.unreadNotifications,
                pendingLink = uiState.pendingLink,
                onConsumeLink = onConsumeLink,
                asksForNotifications = uiState.asksForNotifications,
                onAskedForNotifications = onAskedForNotifications,
            )
        },
        modifier = modifier,
    )
}

/**
 * Shows one graph per session stage. Each graph has its own NavController, so signing in or out
 * never leaves screens of the previous stage in the back stack.
 */
@Composable
internal fun SessionGate(
    uiState: AppUiState,
    signedOut: @Composable () -> Unit,
    onboarding: @Composable (User) -> Unit,
    main: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (val session = uiState.sessionState) {
            // The splash screen stays visible until the stored session has been read.
            SessionState.Loading -> Unit

            SessionState.SignedOut -> signedOut()

            is SessionState.SignedIn ->
                if (session.user.needsOnboarding || uiState.offerFirstPet) onboarding(session.user) else main()
        }
    }
}

/**
 * Main shell: Material 3 navigation bar on compact windows and navigation rail on medium and
 * expanded windows (NavigationSuiteScaffold picks it from the window size class). Screens opened
 * from a destination, such as settings, use the whole window. [pendingRequests] are the people waiting for
 * an answer to follow a private profile, which are answered from the profile.
 */
@Composable
fun MainShell(
    pendingRequests: Int,
    unreadMessages: Int,
    unreadNotifications: Int,
    modifier: Modifier = Modifier,
    pendingLink: String? = null,
    onConsumeLink: () -> Unit = {},
    asksForNotifications: Boolean = false,
    onAskedForNotifications: () -> Unit = {},
    appState: GeoWoofAppState = rememberGeoWoofAppState(),
    navHost: @Composable (NavHostController) -> Unit = { GeoWoofNavHost(navController = it) },
) {
    val currentDestination = appState.currentDestination
    val inbox = Inbox(
        unreadMessages = unreadMessages,
        unreadNotifications = unreadNotifications,
        onOpenMessages = { appState.navController.navigateToConversations() },
        onOpenNotifications = { appState.navController.navigateToNotifications() },
        asksForNotifications = asksForNotifications,
        onAskedForNotifications = onAskedForNotifications,
    )
    val showNavigation = currentDestination == null ||
        TopLevelDestination.entries.any { currentDestination.isInHierarchyOf(it) }
    val layoutType = if (showNavigation) {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    } else {
        NavigationSuiteType.None
    }
    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination.isInHierarchyOf(destination)
                item(
                    selected = selected,
                    onClick = { appState.navigateToTopLevelDestination(destination) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (destination == TopLevelDestination.Profile) {
                                    ProfileBadge(pendingRequests, unreadNotifications)
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (selected) destination.selectedIcon else destination.unselectedIcon,
                                ),
                                // The label already describes the item for accessibility services.
                                contentDescription = null,
                            )
                        }
                    },
                    label = { FittedText(stringResource(destination.label)) },
                )
            }
        },
    ) {
        // The bar already leaves room for the system bar under it. If the screens did it as well, a strip of the
        // background would be left between them and the bar (2026-11-08): their Scaffolds leave out what is consumed.
        Box(Modifier.consumeWindowInsets(layoutType.coveredInsets())) {
            CompositionLocalProvider(LocalInbox provides inbox) {
                navHost(appState.navController)
                OpenPendingLink(pendingLink, appState.navController, onConsumeLink)
            }
        }
    }
}

/** The system bar on the side of the window that the navigation takes: below a bar, beside a rail. */
@Composable
private fun NavigationSuiteType.coveredInsets(): WindowInsets = when (this) {
    NavigationSuiteType.None -> WindowInsets(0)

    NavigationSuiteType.NavigationBar,
    NavigationSuiteType.ShortNavigationBarCompact,
    NavigationSuiteType.ShortNavigationBarMedium,
    -> WindowInsets.systemBars.only(WindowInsetsSides.Bottom)

    else -> WindowInsets.systemBars.only(WindowInsetsSides.Start)
}

/**
 * Opens a tapped notification on top of what was there. It goes after the NavHost, which is what sets the graph:
 * the link may be waiting since before the first composition, if the activity has just been created again.
 */
@Composable
private fun OpenPendingLink(link: String?, navController: NavHostController, onConsume: () -> Unit) {
    val currentOnConsume by rememberUpdatedState(onConsume)
    LaunchedEffect(link, navController) {
        if (link != null) {
            link.toUri().takeIf(navController.graph::hasDeepLink)?.let(navController::navigate)
            currentOnConsume()
        }
    }
}

/**
 * What is waiting in the profile, which is where the bell is. It shows the largest number and does not add them up,
 * because a request to follow you is also a notification and one request would say 2. TalkBack hears each one.
 */
@Composable
internal fun ProfileBadge(pendingRequests: Int, unreadNotifications: Int, modifier: Modifier = Modifier) {
    val count = maxOf(pendingRequests, unreadNotifications)
    if (count == 0) return
    val description = listOfNotNull(
        pendingRequests.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.pending_follow_requests, it, it)
        },
        unreadNotifications.takeIf { it > 0 }?.let {
            pluralStringResource(com.geowoof.core.ui.R.plurals.core_ui_unread_notifications, it, it)
        },
    ).joinToString()
    Badge(
        modifier = modifier.semantics { contentDescription = description },
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
    ) {
        Text(
            if (count > MAX_BADGE_COUNT) {
                stringResource(com.geowoof.core.ui.R.string.core_ui_many_unread, MAX_BADGE_COUNT)
            } else {
                count.toString()
            },
        )
    }
}

private const val MAX_BADGE_COUNT = 99

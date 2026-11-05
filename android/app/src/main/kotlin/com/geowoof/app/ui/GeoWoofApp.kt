package com.geowoof.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavHostController
import com.geowoof.app.AppUiState
import com.geowoof.app.R
import com.geowoof.app.navigation.AuthNavHost
import com.geowoof.app.navigation.GeoWoofNavHost
import com.geowoof.app.navigation.OnboardingNavHost
import com.geowoof.app.navigation.TopLevelDestination
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User

/** Root of the UI: the session decides between the signed-out flow, the onboarding and the main shell. */
@Composable
fun GeoWoofApp(uiState: AppUiState, onFinishFirstPetStep: () -> Unit, modifier: Modifier = Modifier) {
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
        main = { MainShell(pendingRequests = uiState.pendingRequests) },
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
    modifier: Modifier = Modifier,
    appState: GeoWoofAppState = rememberGeoWoofAppState(),
    navHost: @Composable (NavHostController) -> Unit = { GeoWoofNavHost(navController = it) },
) {
    val currentDestination = appState.currentDestination
    val showNavigation = currentDestination == null ||
        TopLevelDestination.entries.any { currentDestination.isInHierarchyOf(it) }
    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = if (showNavigation) {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
        } else {
            NavigationSuiteType.None
        },
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination.isInHierarchyOf(destination)
                item(
                    selected = selected,
                    onClick = { appState.navigateToTopLevelDestination(destination) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (destination == TopLevelDestination.Profile && pendingRequests > 0) {
                                    PendingRequestsBadge(pendingRequests)
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
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        navHost(appState.navController)
    }
}

@Composable
private fun PendingRequestsBadge(count: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.pending_follow_requests, count, count)
    Badge(modifier = modifier.semantics { contentDescription = description }) { Text(count.toString()) }
}

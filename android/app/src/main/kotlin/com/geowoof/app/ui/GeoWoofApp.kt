package com.geowoof.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geowoof.app.navigation.AuthNavHost
import com.geowoof.app.navigation.GeoWoofNavHost
import com.geowoof.app.navigation.OnboardingNavHost
import com.geowoof.app.navigation.TopLevelDestination
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User

/** Root of the UI: the session decides between the signed-out flow, the onboarding and the main shell. */
@Composable
fun GeoWoofApp(sessionState: SessionState, modifier: Modifier = Modifier) {
    SessionGate(
        sessionState = sessionState,
        signedOut = { AuthNavHost() },
        onboarding = { user -> OnboardingNavHost(emailVerified = user.emailVerified) },
        main = { MainShell() },
        modifier = modifier,
    )
}

/**
 * Shows one graph per session stage. Each graph has its own NavController, so signing in or out
 * never leaves screens of the previous stage in the back stack.
 */
@Composable
internal fun SessionGate(
    sessionState: SessionState,
    signedOut: @Composable () -> Unit,
    onboarding: @Composable (User) -> Unit,
    main: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (sessionState) {
            // The splash screen stays visible until the stored session has been read.
            SessionState.Loading -> Unit

            SessionState.SignedOut -> signedOut()

            is SessionState.SignedIn -> if (sessionState.user.needsOnboarding) onboarding(sessionState.user) else main()
        }
    }
}

/**
 * Main shell: Material 3 navigation bar on compact windows and navigation rail on medium and
 * expanded windows (NavigationSuiteScaffold picks it from the window size class). Screens opened
 * from a destination, such as settings, use the whole window.
 */
@Composable
fun MainShell(modifier: Modifier = Modifier, appState: GeoWoofAppState = rememberGeoWoofAppState()) {
    val currentDestination = appState.currentDestination
    val showNavigation =
        currentDestination == null || TopLevelDestination.entries.any { currentDestination.isInHierarchyOf(it) }
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
                        Icon(
                            painter = painterResource(
                                if (selected) destination.selectedIcon else destination.unselectedIcon,
                            ),
                            // The label already describes the item for accessibility services.
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        GeoWoofNavHost(navController = appState.navController)
    }
}

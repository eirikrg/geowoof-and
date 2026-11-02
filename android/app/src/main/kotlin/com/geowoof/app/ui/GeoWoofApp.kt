package com.geowoof.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geowoof.app.navigation.GeoWoofNavHost
import com.geowoof.app.navigation.TopLevelDestination

/**
 * App shell: Material 3 navigation bar on compact windows and navigation rail on medium and
 * expanded windows (NavigationSuiteScaffold picks it from the window size class).
 */
@Composable
fun GeoWoofApp(modifier: Modifier = Modifier, appState: GeoWoofAppState = rememberGeoWoofAppState()) {
    val currentDestination = appState.currentDestination
    NavigationSuiteScaffold(
        modifier = modifier,
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

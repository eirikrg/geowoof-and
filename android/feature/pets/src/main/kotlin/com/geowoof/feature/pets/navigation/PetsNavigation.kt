package com.geowoof.feature.pets.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import androidx.navigation.toRoute
import com.geowoof.feature.pets.detail.PetDetailScreen
import com.geowoof.feature.pets.editor.PetEditorScreen
import com.geowoof.feature.pets.health.HealthEditorScreen
import com.geowoof.feature.pets.health.HealthScreen
import kotlinx.serialization.Serializable

// The ViewModels read the route arguments from SavedStateHandle by property name.

/** Without [petId], the form creates a new pet. */
@Serializable
data class PetEditorRoute(val petId: String? = null)

@Serializable
data class PetDetailRoute(val petId: String)

/** All the health of a pet. Its link opens it from a reminder. */
@Serializable
data class PetHealthRoute(val petId: String)

/** Without [eventId], the form adds something new to the health of the pet. */
@Serializable
data class HealthEventRoute(val petId: String, val eventId: String? = null)

/** The optional last step of the onboarding. */
@Serializable
data object FirstPetRoute

fun NavController.navigateToPet(petId: String, navOptions: NavOptions? = null) =
    navigate(PetDetailRoute(petId), navOptions)

fun NavController.navigateToPetEditor(petId: String? = null, navOptions: NavOptions? = null) =
    navigate(PetEditorRoute(petId), navOptions ?: navOptions { launchSingleTop = true })

fun NavController.navigateToFirstPet(navOptions: NavOptions? = null) = navigate(FirstPetRoute, navOptions)

fun NavController.navigateToPetHealth(petId: String, navOptions: NavOptions? = null) =
    navigate(PetHealthRoute(petId), navOptions ?: navOptions { launchSingleTop = true })

fun NavController.navigateToHealthEvent(petId: String, eventId: String? = null) =
    navigate(HealthEventRoute(petId, eventId), navOptions { launchSingleTop = true })

/** The pet detail with its weight history and its health, and the forms to create or edit a pet and its health. */
fun NavGraphBuilder.petScreens(navController: NavController, onOpenWalks: (petId: String) -> Unit) {
    composable<PetDetailRoute> {
        PetDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { petId -> navController.navigateToPetEditor(petId) },
            onOpenWalks = onOpenWalks,
            onOpenHealth = { petId -> navController.navigateToPetHealth(petId) },
            onAddHealth = { petId -> navController.navigateToHealthEvent(petId) },
            onDone = { navController.popBackStack() },
        )
    }
    composable<PetHealthRoute>(
        deepLinks = listOf(navDeepLink<PetHealthRoute>(basePath = "geowoof://pet-health")),
    ) { entry ->
        val petId = entry.toRoute<PetHealthRoute>().petId
        HealthScreen(
            onBack = { navController.popBackStack() },
            onAdd = { navController.navigateToHealthEvent(petId) },
            onOpenEvent = { eventId -> navController.navigateToHealthEvent(petId, eventId) },
        )
    }
    composable<HealthEventRoute> {
        HealthEditorScreen(
            onDone = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
    composable<PetEditorRoute> {
        PetEditorScreen(
            onDone = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
}

/** Optional: [onDone] runs whether the user adds a pet or skips the step. */
fun NavGraphBuilder.firstPetScreen(onDone: () -> Unit) {
    composable<FirstPetRoute> {
        PetEditorScreen(onDone = onDone, onSkip = onDone)
    }
}

package com.geowoof.feature.pets.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.geowoof.feature.pets.detail.PetDetailScreen
import com.geowoof.feature.pets.editor.PetEditorScreen
import kotlinx.serialization.Serializable

// The ViewModels read the route arguments from SavedStateHandle by property name.

/** Without [petId], the form creates a new pet. */
@Serializable
data class PetEditorRoute(val petId: String? = null)

@Serializable
data class PetDetailRoute(val petId: String)

/** The optional last step of the onboarding. */
@Serializable
data object FirstPetRoute

fun NavController.navigateToPet(petId: String, navOptions: NavOptions? = null) =
    navigate(PetDetailRoute(petId), navOptions)

fun NavController.navigateToPetEditor(petId: String? = null, navOptions: NavOptions? = null) =
    navigate(PetEditorRoute(petId), navOptions ?: navOptions { launchSingleTop = true })

fun NavController.navigateToFirstPet(navOptions: NavOptions? = null) = navigate(FirstPetRoute, navOptions)

/** The pet detail with its weight history, and the form to create or edit a pet. */
fun NavGraphBuilder.petScreens(navController: NavController, onOpenWalks: (petId: String) -> Unit) {
    composable<PetDetailRoute> {
        PetDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { petId -> navController.navigateToPetEditor(petId) },
            onOpenWalks = onOpenWalks,
            onDone = { navController.popBackStack() },
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

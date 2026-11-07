package com.geowoof.feature.map

import com.geowoof.core.model.LiveVisibility

@Suppress("LongParameterList")
internal class MapActions(
    val onStart: () -> Unit,
    val onDismissPetPicker: () -> Unit,
    val onTogglePet: (String) -> Unit,
    val onConfirmPets: (onlyPetId: String?) -> Unit,
    val onAddPet: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onFinish: () -> Unit,
    val onCancel: () -> Unit,
    val onReview: () -> Unit,
    val onRequestPermissions: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenLocationSettings: () -> Unit,
    val onDismissPrompt: () -> Unit,
    val onChangeVisibility: () -> Unit,
    val onChooseVisibility: (LiveVisibility) -> Unit,
    val onDismissVisibility: () -> Unit,
    val onDismissError: () -> Unit,
    val onMyLocation: () -> Unit = {},
    val onAskForLocation: () -> Unit = {},
    val onBackToWalk: () -> Unit = {},
)

package com.geowoof.feature.map

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
)

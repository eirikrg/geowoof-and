package com.geowoof.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Opens the system Photo Picker, which needs no storage permission: the app only receives the
 * picture that the user chooses. Returns the function that opens it.
 */
@Composable
fun rememberPhotoPicker(onPick: (contentUri: String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPick(it.toString()) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** The same for several pictures, up to [maxItems]. */
@Composable
fun rememberMultiplePhotoPicker(maxItems: Int, onPick: (contentUris: List<String>) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems)) { uris ->
            onPick(uris.map { it.toString() })
        }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

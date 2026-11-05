package com.geowoof.feature.social.create

import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.UserSummary

/** What the form can do, so that its composables do not take a long list of callbacks. */
@Suppress("LongParameterList")
internal class CreatePostActions(
    val onBack: () -> Unit = {},
    val onPickPhotos: () -> Unit = {},
    val onRemovePhoto: (contentUri: String) -> Unit = {},
    val onSelectVisibility: (PostVisibility) -> Unit = {},
    val onTogglePet: (petId: String) -> Unit = {},
    val onChooseMention: (UserSummary) -> Unit = {},
    val onPublish: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

package com.geowoof.feature.social.post

import com.geowoof.core.model.Post

/** What a card can do, so that its composables do not take a long list of callbacks. */
@Suppress("LongParameterList")
internal class PostCardActions(
    val onOpenPost: (Post) -> Unit = {},
    val onOpenUser: (userId: String) -> Unit = {},
    val onToggleLike: (Post) -> Unit = {},
    val onToggleRepost: (Post) -> Unit = {},
    val onToggleSave: (Post) -> Unit = {},
    val onShowDialog: (PostDialog) -> Unit = {},
)

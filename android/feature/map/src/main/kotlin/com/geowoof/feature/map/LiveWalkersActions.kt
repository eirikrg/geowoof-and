package com.geowoof.feature.map

@Suppress("LongParameterList")
internal class LiveWalkersActions(
    val onOpenList: () -> Unit,
    val onDismissList: () -> Unit,
    val onWalkerClick: (String) -> Unit,
    val onDismissWalker: () -> Unit,
    val onOpenProfile: (String) -> Unit,
    val onFollow: () -> Unit,
    val onConfirmFollowBack: () -> Unit,
    val onDismissFollowBack: () -> Unit,
    val onDismissError: () -> Unit,
)

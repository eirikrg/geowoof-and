package com.geowoof.feature.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.geowoof.core.designsystem.component.HeroBandHeight
import com.geowoof.core.designsystem.component.HeroPhotoFrame
import com.geowoof.core.designsystem.component.heroTopBarColors
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Profile
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.InboxActions
import com.geowoof.core.ui.RemoteAvatar

/**
 * At the start of the profile, the bar says the username. As the page goes up, the photo flies from the band to the
 * bar, and the name appears next to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTopBar(
    uiState: ProfileUiState,
    actions: ProfileActions,
    collapse: ProfileCollapse,
    modifier: Modifier = Modifier,
) {
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    var landing by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = modifier.onGloballyPositioned { barOrigin = it.positionInRoot() }) {
        ProfileBar(uiState, actions, collapse, onPlaceLanding = { landing = it })
        val profile = uiState.profile
        if (profile != null && !uiState.isUnavailable) {
            FlyingPhoto(profile, collapse, landing = { landing - barOrigin })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBar(
    uiState: ProfileUiState,
    actions: ProfileActions,
    collapse: ProfileCollapse,
    onPlaceLanding: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors = heroTopBarColors(),
        title = { ProfileBarTitle(uiState, collapse, onPlaceLanding) },
        navigationIcon = {
            actions.onBack?.let { onBack ->
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(GeoWoofIcons.ArrowBack),
                        contentDescription = stringResource(R.string.feature_profile_back),
                    )
                }
            }
        },
        actions = {
            actions.onOpenSettings?.let { onOpenSettings ->
                InboxActions()
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(GeoWoofIcons.Settings),
                        contentDescription = stringResource(R.string.feature_profile_settings),
                    )
                }
            }
            if (uiState.hasMenu) {
                ProfileMenu(followsYou = uiState.relationship?.followsYou == true, onShowDialog = actions.onShowDialog)
            }
        },
    )
}

/** The two titles are on top of each other, and each one is only for TalkBack while it can be seen. */
@Composable
private fun ProfileBarTitle(
    uiState: ProfileUiState,
    collapse: ProfileCollapse,
    onPlaceLanding: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = uiState.profile?.takeUnless { uiState.isUnavailable }
    val username = profile?.username?.let { stringResource(R.string.feature_profile_username, it) }
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text = username ?: stringResource(R.string.feature_profile_profile_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer {
                    val leaving = (collapse.progress / TITLE_LEAVES_AT).coerceAtMost(1f)
                    alpha = 1f - leaving
                    translationY = -TitleTravel.toPx() * leaving
                }
                .then(if (collapse.isCollapsed) Modifier.clearAndSetSemantics {} else Modifier),
        )
        if (username != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier = Modifier
                        .size(LandedPhotoSize)
                        .onGloballyPositioned { onPlaceLanding(it.boundsInRoot().center) },
                )
                CollapsedTitle(
                    name = uiState.profile.displayName.takeUnless { uiState.isRestricted },
                    username = username,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .graphicsLayer {
                            val arriving = ((collapse.progress - TITLE_ARRIVES_FROM) / (1f - TITLE_ARRIVES_FROM))
                                .coerceIn(0f, 1f)
                            alpha = arriving
                            translationY = TitleTravel.toPx() * (1f - arriving)
                        }
                        .then(if (collapse.isCollapsed) Modifier else Modifier.clearAndSetSemantics {}),
                )
            }
        }
    }
}

/** With a very large font there is no room for two lines in the bar. */
@Composable
private fun CollapsedTitle(name: String?, username: String, modifier: Modifier = Modifier) {
    val fitsTwoLines = LocalDensity.current.fontScale <= LARGE_FONT_SCALE
    Column(
        modifier = modifier.padding(start = GeoWoofTheme.spacing.medium),
        verticalArrangement = Arrangement.Center,
    ) {
        if (name != null) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (name == null || fitsTwoLines) {
            Text(
                text = username,
                style = if (name == null) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = if (name == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The photo of the profile. It is over the bar and the page so that it can go from one to the other. It goes up with
 * the scroll, as if it were part of the page, while it gets smaller and goes to its place in the bar. It takes no room
 * in the bar, and it does not stop the touches on what is under it.
 */
@Composable
private fun BoxScope.FlyingPhoto(profile: Profile, collapse: ProfileCollapse, landing: () -> Offset) {
    var bar by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { bar = it }
            .wrapContentSize(Alignment.TopStart, unbounded = true),
    ) {
        HeroPhotoFrame(
            modifier = Modifier.graphicsLayer {
                val progress = collapse.progress
                val startX = bar.width / 2f
                val startY = bar.height + HeroBandHeight.toPx()
                val end = landing()
                translationX = lerp(startX, end.x, FastOutSlowInEasing.transform(progress)) - size.width / 2
                translationY = lerp(startY, end.y, progress) - size.height / 2
                val scale = lerp(1f, LandedPhotoSize.toPx() / size.width, progress)
                scaleX = scale
                scaleY = scale
            },
        ) {
            RemoteAvatar(
                image = profile.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = stringResource(R.string.feature_profile_photo),
                size = ProfilePhotoSize,
                variant = ImageVariant.Medium,
            )
        }
    }
}

private val ProfileUiState.hasMenu: Boolean
    get() = profile != null &&
        !isUnavailable &&
        relationship != null &&
        relationship.status != RelationshipStatus.Self &&
        relationship.status != RelationshipStatus.Blocked

@Composable
private fun ProfileMenu(followsYou: Boolean, onShowDialog: (ProfileDialog) -> Unit, modifier: Modifier = Modifier) {
    var isOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { isOpen = true }) {
            Icon(
                painter = painterResource(GeoWoofIcons.MoreVert),
                contentDescription = stringResource(R.string.feature_profile_more_options),
            )
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            if (followsYou) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_profile_remove_follower)) },
                    leadingIcon = { Icon(painterResource(GeoWoofIcons.Close), contentDescription = null) },
                    onClick = {
                        isOpen = false
                        onShowDialog(ProfileDialog.ConfirmRemoveFollower)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_profile_report)) },
                leadingIcon = { Icon(painterResource(GeoWoofIcons.Flag), contentDescription = null) },
                onClick = {
                    isOpen = false
                    onShowDialog(ProfileDialog.Report)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_profile_block)) },
                leadingIcon = { Icon(painterResource(GeoWoofIcons.Block), contentDescription = null) },
                onClick = {
                    isOpen = false
                    onShowDialog(ProfileDialog.ConfirmBlock)
                },
            )
        }
    }
}

internal val ProfilePhotoSize = 104.dp
private val LandedPhotoSize = 40.dp
private val TitleTravel = 12.dp
private const val TITLE_LEAVES_AT = 0.35f
private const val TITLE_ARRIVES_FROM = 0.55f
private const val LARGE_FONT_SCALE = 1.3f

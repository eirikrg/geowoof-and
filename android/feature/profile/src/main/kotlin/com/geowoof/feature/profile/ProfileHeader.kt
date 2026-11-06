package com.geowoof.feature.profile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.component.HeroBand
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.RemoteAvatar

/** Who it is: their photo and, unless the profile is restricted, their name and city. */
@Composable
internal fun ProfileHeader(uiState: ProfileUiState, profile: Profile, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HeroBand(photoSize = AvatarSize) { photoModifier ->
            RemoteAvatar(
                image = profile.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = stringResource(R.string.feature_profile_photo),
                size = AvatarSize,
                variant = ImageVariant.Medium,
                modifier = photoModifier,
            )
        }
        ProfileDetails(uiState, profile, Modifier.padding(horizontal = spacing.large, vertical = spacing.small))
    }
}

@Composable
private fun ProfileDetails(uiState: ProfileUiState, profile: Profile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.extraSmall),
    ) {
        if (!uiState.isRestricted) {
            Text(
                text = profile.displayName,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        if (profile.isPrivate) PrivateLabel()
        // Friends already say so on their button.
        if (uiState.relationship?.followsYou == true && uiState.relationship.status != RelationshipStatus.Friends) {
            Text(
                text = stringResource(R.string.feature_profile_follows_you),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        profile.city?.let { city ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(GeoWoofIcons.LocationOn),
                    contentDescription = null,
                    modifier = Modifier.size(SmallIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        profile.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun PrivateLabel(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(GeoWoofIcons.Lock),
            contentDescription = null,
            modifier = Modifier.size(SmallIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.feature_profile_private),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** It wraps instead of cutting the words when the text is very large. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileCounters(
    counts: ProfileCounts?,
    canOpenLists: Boolean,
    onOpen: (PeopleListKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GeoWoofTheme.spacing.large, vertical = GeoWoofTheme.spacing.small),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Counter(counts?.followers, R.string.feature_profile_followers, canOpenLists, {
                onOpen(PeopleListKind.Followers)
            })
            Counter(counts?.following, R.string.feature_profile_following, canOpenLists, {
                onOpen(PeopleListKind.Following)
            })
            Counter(counts?.friends, R.string.feature_profile_friends, canOpenLists, {
                onOpen(PeopleListKind.Friends)
            })
        }
    }
}

@Composable
private fun Counter(
    value: Int?,
    @StringRes label: Int,
    canOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = canOpen, role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value?.toString() ?: stringResource(R.string.feature_profile_count_unknown),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What can be done with this person now. The server decides, because the other person can change it too. */
@Composable
internal fun RelationshipButton(
    relationship: Relationship,
    isActing: Boolean,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.CenterHorizontally),
    ) {
        if (relationship.status == RelationshipStatus.Friends) {
            Button(onClick = actions.onSendMessage) {
                ButtonIcon(GeoWoofIcons.ChatBubble)
                Text(stringResource(R.string.feature_profile_send_message))
            }
        }
        when (relationship.status) {
            RelationshipStatus.Self -> FilledTonalButton(onClick = actions.onEditProfile) {
                Text(stringResource(R.string.feature_profile_edit))
            }

            RelationshipStatus.None -> if (relationship.followsYou) {
                Button(
                    onClick = { actions.onShowDialog(ProfileDialog.ConfirmFollowBack) },
                    enabled = !isActing,
                ) {
                    ButtonIcon(GeoWoofIcons.PersonAdd)
                    Text(stringResource(R.string.feature_profile_follow_back))
                }
            } else {
                Button(onClick = actions.onFollow, enabled = !isActing) {
                    ButtonIcon(GeoWoofIcons.PersonAdd)
                    Text(stringResource(R.string.feature_profile_follow))
                }
            }

            RelationshipStatus.Requested -> OutlinedButton(onClick = actions.onUnfollow, enabled = !isActing) {
                Text(stringResource(R.string.feature_profile_requested))
            }

            RelationshipStatus.Following, RelationshipStatus.Friends -> FilledTonalButton(
                onClick = { actions.onShowDialog(ProfileDialog.ConfirmUnfollow) },
                enabled = !isActing,
            ) {
                ButtonIcon(GeoWoofIcons.Check)
                Text(
                    stringResource(
                        if (relationship.status == RelationshipStatus.Friends) {
                            R.string.feature_profile_is_friend
                        } else {
                            R.string.feature_profile_is_following
                        },
                    ),
                )
            }

            RelationshipStatus.Blocked -> Unit
        }
    }
}

@Composable
private fun ButtonIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier.padding(end = GeoWoofTheme.spacing.small),
    )
}

/** Someone wants to follow this private profile, and it can be answered from here. */
@Composable
internal fun IncomingRequest(
    name: String,
    isActing: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = stringResource(R.string.feature_profile_wants_to_follow, name),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            Button(onClick = onAccept, enabled = !isActing) { Text(stringResource(R.string.feature_profile_accept)) }
            OutlinedButton(onClick = onReject, enabled = !isActing) {
                Text(stringResource(R.string.feature_profile_reject))
            }
        }
    }
}

/** In your own profile, the way to the requests when there are any. */
@Composable
internal fun RequestsRow(received: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(stringResource(R.string.feature_profile_requests_title)) },
        leadingContent = { Icon(painterResource(GeoWoofIcons.GroupAdd), contentDescription = null) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
            ) {
                if (received > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ) {
                        Text(received.toString())
                    }
                }
                Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null)
            }
        },
    )
}

private val AvatarSize = 104.dp
private val SmallIconSize = 16.dp

package com.geowoof.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.LivePet
import com.geowoof.core.model.LiveWalker
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.ui.RemoteAvatar

/** Whoever cannot use the markers (TalkBack, or a map without its key) gets the same walkers here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveWalkersListSheet(
    walkers: List<LiveWalker>,
    actions: LiveWalkersActions,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = actions.onDismissList, modifier = modifier) {
        Text(
            text = stringResource(R.string.feature_map_live_list_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = GeoWoofTheme.spacing.large)
                .semantics { heading() },
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(walkers, key = LiveWalker::userId) { walker ->
                ListItem(
                    headlineContent = { Text(walker.displayName) },
                    supportingContent = { Text(walker.pets.joinToString { it.name }) },
                    leadingContent = {
                        RemoteAvatar(
                            image = walker.pets.firstOrNull()?.avatar,
                            fallbackIcon = GeoWoofIcons.Pets,
                            contentDescription = null,
                            size = AvatarSize,
                        )
                    },
                    modifier = Modifier.clickable { actions.onWalkerClick(walker.userId) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveWalkerSheet(
    uiState: LiveWalkersUiState,
    walker: LiveWalker,
    actions: LiveWalkersActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    ModalBottomSheet(onDismissRequest = actions.onDismissWalker, modifier = modifier) {
        Column(
            modifier = Modifier.padding(start = spacing.large, end = spacing.large, bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Owner(walker)
            walker.pets.forEach { pet -> PetLine(pet) }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                FilledTonalButton(onClick = { actions.onOpenProfile(walker.userId) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.feature_map_live_view_profile))
                }
                FollowButton(uiState, actions, Modifier.weight(1f))
            }
        }
    }
    if (uiState.isConfirmingFollowBack) ConfirmFollowBack(walker, actions)
}

@Composable
private fun Owner(walker: LiveWalker, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
    ) {
        RemoteAvatar(
            image = walker.avatar,
            fallbackIcon = GeoWoofIcons.Person,
            contentDescription = null,
            size = AvatarSize,
        )
        Column {
            Text(
                text = walker.displayName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.feature_map_live_username, walker.username),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PetLine(pet: LivePet, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
    ) {
        RemoteAvatar(image = pet.avatar, fallbackIcon = GeoWoofIcons.Pets, contentDescription = null, size = AvatarSize)
        Text(pet.name, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Undoing it is in their profile: here it is only what the map can offer at a glance. */
@Composable
private fun FollowButton(uiState: LiveWalkersUiState, actions: LiveWalkersActions, modifier: Modifier = Modifier) {
    val relationship = uiState.relationship ?: return
    when (relationship.status) {
        RelationshipStatus.None -> Button(
            onClick = actions.onFollow,
            enabled = !uiState.isChangingFollow,
            modifier = modifier,
        ) {
            val label = if (relationship.followsYou) {
                R.string.feature_map_live_follow_back
            } else {
                R.string.feature_map_live_follow
            }
            Text(stringResource(label))
        }

        RelationshipStatus.Requested -> SettledFollow(R.string.feature_map_live_requested, modifier)

        RelationshipStatus.Following -> SettledFollow(R.string.feature_map_live_following, modifier)

        RelationshipStatus.Friends -> SettledFollow(R.string.feature_map_live_friends, modifier)

        RelationshipStatus.Self, RelationshipStatus.Blocked -> Unit
    }
}

@Composable
private fun SettledFollow(label: Int, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = {}, enabled = false, modifier = modifier.fillMaxWidth()) {
        Text(stringResource(label))
    }
}

@Composable
private fun ConfirmFollowBack(walker: LiveWalker, actions: LiveWalkersActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissFollowBack,
        title = { Text(stringResource(R.string.feature_map_live_follow_back_title, walker.displayName)) },
        text = { Text(stringResource(R.string.feature_map_live_follow_back_message, walker.displayName)) },
        confirmButton = {
            TextButton(onClick = actions.onConfirmFollowBack) {
                Text(stringResource(R.string.feature_map_live_follow_back))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissFollowBack) {
                Text(stringResource(R.string.feature_map_cancel))
            }
        },
    )
}

private val AvatarSize = 40.dp

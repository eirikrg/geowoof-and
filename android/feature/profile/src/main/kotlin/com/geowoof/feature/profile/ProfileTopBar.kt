package com.geowoof.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.ui.InboxActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTopBar(uiState: ProfileUiState, actions: ProfileActions, modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                uiState.profile?.username?.let { stringResource(R.string.feature_profile_username, it) }
                    ?: stringResource(R.string.feature_profile_profile_title),
            )
        },
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

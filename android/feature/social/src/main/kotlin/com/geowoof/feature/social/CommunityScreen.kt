package com.geowoof.feature.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme

@Composable
fun CommunityScreen(
    onOpenRequests: () -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    CommunityContent(
        uiState = uiState,
        onOpenRequests = onOpenRequests,
        onOpenFriends = onOpenFriends,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityContent(
    uiState: CommunityUiState,
    onOpenRequests: () -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_social_community_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenRequests),
                headlineContent = { Text(stringResource(R.string.feature_social_requests_title)) },
                leadingContent = { Icon(painterResource(GeoWoofIcons.GroupAdd), contentDescription = null) },
                trailingContent = if (uiState.requestCount > 0) {
                    { Badge { Text(uiState.requestCount.toString()) } }
                } else {
                    null
                },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenFriends),
                headlineContent = { Text(stringResource(R.string.feature_social_friends_title)) },
                supportingContent = {
                    Text(
                        pluralStringResource(
                            R.plurals.feature_social_friend_count,
                            uiState.friendCount,
                            uiState.friendCount,
                        ),
                    )
                },
                leadingContent = { Icon(painterResource(GeoWoofIcons.Groups), contentDescription = null) },
                trailingContent = { Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null) },
            )
            HorizontalDivider()
            EmptyState(
                icon = GeoWoofIcons.Groups,
                title = stringResource(R.string.feature_social_community_empty_title),
                message = stringResource(R.string.feature_social_community_empty_message),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun CommunityPreview() {
    GeoWoofTheme(dynamicColor = false) {
        CommunityContent(uiState = CommunityUiState(friendCount = 3, requestCount = 1), onOpenRequests = {
        }, onOpenFriends = {})
    }
}

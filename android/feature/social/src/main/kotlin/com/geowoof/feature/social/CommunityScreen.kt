package com.geowoof.feature.social

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/** The destination of the feed, which arrives in phase 6. Friends and followers are in the profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_social_community_title)) }) },
    ) { innerPadding ->
        EmptyState(
            icon = GeoWoofIcons.Groups,
            title = stringResource(R.string.feature_social_community_empty_title),
            message = stringResource(R.string.feature_social_community_empty_message),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun CommunityPreview() {
    GeoWoofTheme(dynamicColor = false) {
        CommunityScreen()
    }
}

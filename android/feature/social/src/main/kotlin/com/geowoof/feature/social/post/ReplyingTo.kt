package com.geowoof.feature.social.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.feature.social.R

/** Whom the field answers, over it, and how to stop answering. */
@Composable
internal fun ReplyingTo(target: ReplyTarget, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.large, end = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.feature_social_replying_to, target.username),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onCancel) {
            Icon(
                painter = painterResource(GeoWoofIcons.Close),
                contentDescription = stringResource(R.string.feature_social_cancel_reply),
            )
        }
    }
}

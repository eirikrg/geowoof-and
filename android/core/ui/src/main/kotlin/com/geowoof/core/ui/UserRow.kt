package com.geowoof.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.UserSummary

private val UserAvatarSize = 48.dp

/** A person in a list. [trailing] holds what can be done with them from the list. */
@Composable
fun UserRow(
    user: UserSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(user.displayName, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                text = listOfNotNull("@${user.username}", user.city).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            RemoteAvatar(
                image = user.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = null,
                size = UserAvatarSize,
            )
        },
        trailingContent = trailing,
    )
}

@PreviewLightDark
@Composable
private fun UserRowPreview() {
    GeoWoofTheme(dynamicColor = false) {
        UserRow(UserSummary("1", "bea", "Bea", "Madrid", null), onClick = {})
    }
}

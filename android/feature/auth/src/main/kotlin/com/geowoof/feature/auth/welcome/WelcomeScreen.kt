package com.geowoof.feature.auth.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.GoogleSignInButton
import com.geowoof.core.ui.MaxFormWidth
import com.geowoof.core.ui.rememberGoogleSignInLauncher
import com.geowoof.feature.auth.R

@Composable
internal fun WelcomeScreen(
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val google = rememberGoogleSignInLauncher(
        serverClientId = uiState.googleClientId,
        onIdToken = viewModel::signInWithGoogle,
        onUnavailable = viewModel::onGoogleUnavailable,
    )
    WelcomeContent(
        uiState = uiState,
        onSignUp = onSignUp,
        onSignIn = onSignIn,
        onContinueWithGoogle = google?.let { it::launch },
        modifier = modifier,
    )
}

@Composable
internal fun WelcomeContent(
    uiState: WelcomeUiState,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onContinueWithGoogle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.extraLarge, vertical = spacing.huge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Spacer(Modifier.height(spacing.huge))
            Icon(
                painter = painterResource(GeoWoofIcons.Pets),
                contentDescription = null,
                modifier = Modifier.size(LogoSize),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.feature_auth_welcome_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.feature_auth_welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.large))
            Column(
                modifier = Modifier.widthIn(max = MaxFormWidth),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                Button(
                    onClick = onSignUp,
                    enabled = !uiState.isSigningInWithGoogle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonHeight),
                ) {
                    Text(stringResource(R.string.feature_auth_create_account))
                }
                OutlinedButton(
                    onClick = onSignIn,
                    enabled = !uiState.isSigningInWithGoogle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonHeight),
                ) {
                    Text(stringResource(R.string.feature_auth_sign_in))
                }
                if (onContinueWithGoogle != null) {
                    GoogleSignInButton(onClick = onContinueWithGoogle, loading = uiState.isSigningInWithGoogle)
                }
                FormError(uiState.error)
            }
        }
    }
}

private val LogoSize = 72.dp
private val ButtonHeight = 48.dp

@PreviewLightDark
@PreviewFontScale
@Composable
private fun WelcomePreview() {
    GeoWoofTheme(dynamicColor = false) {
        WelcomeContent(uiState = WelcomeUiState(googleClientId = "id"), onSignUp = {
        }, onSignIn = {}, onContinueWithGoogle = {})
    }
}

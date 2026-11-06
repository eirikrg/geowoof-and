package com.geowoof.feature.auth.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.GeoWoofIllustration
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.MinButtonHeight
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            HeroOverActions(
                screenHeight = maxHeight,
                hero = { Hero(topPadding = innerPadding.calculateTopPadding()) },
                actions = {
                    Column(
                        modifier = Modifier
                            .widthIn(max = MaxFormWidth)
                            .padding(horizontal = spacing.extraLarge)
                            .padding(
                                top = spacing.extraLarge,
                                bottom = innerPadding.calculateBottomPadding() + spacing.large,
                            ),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    ) {
                        Button(
                            onClick = onSignUp,
                            enabled = !uiState.isSigningInWithGoogle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinButtonHeight),
                        ) {
                            Text(stringResource(R.string.feature_auth_create_account))
                        }
                        OutlinedButton(
                            onClick = onSignIn,
                            enabled = !uiState.isSigningInWithGoogle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinButtonHeight),
                        ) {
                            Text(stringResource(R.string.feature_auth_sign_in))
                        }
                        if (onContinueWithGoogle != null) {
                            GoogleSignInButton(onClick = onContinueWithGoogle, loading = uiState.isSigningInWithGoogle)
                        }
                        FormError(uiState.error)
                    }
                },
            )
        }
    }
}

/**
 * The actions take what they need at the bottom and the hero the rest of the screen, without going
 * below [MinHeroHeight] or what it holds: with a big font on a small screen, it all scrolls.
 */
@Composable
private fun HeroOverActions(
    screenHeight: Dp,
    hero: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(hero, actions),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) { (heroParts, actionParts), constraints ->
        val free = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val actionsPlaced = actionParts.first().measure(free.copy(minWidth = 0))
        val hero = heroParts.first()
        val heroHeight = maxOf(
            screenHeight.roundToPx() - actionsPlaced.height,
            MinHeroHeight.roundToPx(),
            hero.minIntrinsicHeight(constraints.maxWidth),
        )
        val heroPlaced = hero.measure(Constraints.fixed(constraints.maxWidth, heroHeight))
        layout(constraints.maxWidth, heroHeight + actionsPlaced.height) {
            heroPlaced.place(0, 0)
            actionsPlaced.place((constraints.maxWidth - actionsPlaced.width) / 2, heroHeight)
        }
    }
}

/** The scene and what the app is for, on the color of the brand and reaching the top of the screen. */
@Composable
private fun Hero(topPadding: Dp, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(top = topPadding, start = spacing.extraLarge, end = spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            GeoWoofIllustration(
                illustration = GeoWoofIllustrations.WelcomeHero,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.small))
            Text(
                text = stringResource(R.string.feature_auth_welcome_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.feature_auth_welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val MinHeroHeight = 340.dp
private val HeroShape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun WelcomePreview() {
    GeoWoofTheme(dynamicColor = false) {
        WelcomeContent(uiState = WelcomeUiState(googleClientId = "id"), onSignUp = {
        }, onSignIn = {}, onContinueWithGoogle = {})
    }
}

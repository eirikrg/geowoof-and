package com.geowoof.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.component.GeoWoofLogoHead
import com.geowoof.core.designsystem.component.IconBadge
import com.geowoof.core.designsystem.component.TrailProgress
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError

/**
 * Layout of the account forms: top app bar with optional back navigation and [actions], a
 * scrollable column limited to a comfortable reading width, and room for the keyboard.
 */
@Composable
fun FormScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    FormLayout(topBarTitle = title, modifier = modifier, onBack = onBack, actions = actions, content = content)
}

/**
 * A form that opens with a big [title] and what it is for, instead of a title in the top bar: the ones
 * someone sees when arriving at the app. [lead] goes above the title, the head of the logo unless it
 * says something else, like the progress of the onboarding. The part of [message] equal to [emphasis]
 * goes in bold.
 */
@Composable
fun IntroFormScaffold(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    emphasis: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    lead: @Composable () -> Unit = { GeoWoofLogoHead(contentDescription = null, Modifier.height(LogoHeight)) },
    content: @Composable ColumnScope.() -> Unit,
) {
    FormLayout(topBarTitle = null, modifier = modifier, onBack = onBack, actions = actions) {
        lead()
        Column(
            modifier = Modifier.padding(bottom = GeoWoofTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (message != null) {
                Text(
                    text = message.withBold(emphasis),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormLayout(
    topBarTitle: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = GeoWoofTheme.spacing
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { topBarTitle?.let { Text(it) } },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(GeoWoofIcons.ArrowBack),
                                contentDescription = stringResource(R.string.core_ui_back),
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large, vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MaxFormWidth),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                content = content,
            )
        }
    }
}

private fun String.withBold(part: String?): AnnotatedString = buildAnnotatedString {
    val start = if (part.isNullOrEmpty()) -1 else indexOf(part)
    if (start < 0) {
        append(this@withBold)
    } else {
        append(substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        append(substring(start + part.orEmpty().length))
    }
}

/** An icon that says what a form is about, above its title in an [IntroFormScaffold]. */
@Composable
fun FormIntroIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    IconBadge(
        icon = icon,
        modifier = modifier,
        size = IntroIconSize,
        iconSize = IntroIconGlyphSize,
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

/** The steps of the onboarding, in the order someone goes through them. */
enum class OnboardingStep { VerifyEmail, ChooseUsername, FirstPet }

/** How far along the onboarding someone is. */
@Composable
fun OnboardingProgress(step: OnboardingStep, modifier: Modifier = Modifier) {
    TrailProgress(step = step.ordinal + 1, total = OnboardingStep.entries.size, modifier = modifier)
}

/** The line with "or" between two ways of doing the same thing. */
@Composable
fun FormOrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.core_ui_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** Error of the last attempt, announced by TalkBack when it appears. */
@Composable
fun FormError(error: AppError?, modifier: Modifier = Modifier) {
    if (error != null) {
        Text(
            text = error.userMessage(),
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Confirmation of the last action (for example, "We sent you a new code"), announced by TalkBack. */
@Composable
fun FormNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Maximum width of form content, so fields stay readable on tablets and in landscape. */
val MaxFormWidth = 480.dp

private val LogoHeight = 72.dp
private val IntroIconSize = 64.dp
private val IntroIconGlyphSize = 32.dp

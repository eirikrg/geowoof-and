package com.geowoof.feature.social.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.LocalPhoto
import com.geowoof.core.ui.UserRow
import com.geowoof.core.ui.rememberMultiplePhotoPicker
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.social.R

@Composable
internal fun CreatePostScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggester.suggestions.collectAsStateWithLifecycle()
    val pickPhotos = rememberMultiplePhotoPicker(CreatePostUiState.MAX_PHOTOS, viewModel::onAddPhotos)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isPublished) {
        if (uiState.isPublished) currentOnDone()
    }
    CreatePostContent(
        uiState = uiState,
        text = viewModel.text,
        suggestions = suggestions,
        actions = CreatePostActions(
            onBack = onBack,
            onPickPhotos = pickPhotos,
            onRemovePhoto = viewModel::onRemovePhoto,
            onSelectVisibility = viewModel::onSelectVisibility,
            onTogglePet = viewModel::onTogglePet,
            onChooseMention = viewModel.suggester::onChoose,
            onPublish = viewModel::onPublish,
            onDismissError = viewModel::onErrorShown,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreatePostContent(
    uiState: CreatePostUiState,
    text: TextFieldState,
    suggestions: List<UserSummary>,
    actions: CreatePostActions,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.userMessage()
    val currentOnDismissError by rememberUpdatedState(actions.onDismissError)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbars.showSnackbar(errorMessage)
            currentOnDismissError()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_social_create_post)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_social_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val spacing = GeoWoofTheme.spacing
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            PhotosSection(uiState, actions)
            OutlinedTextField(
                state = text,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.feature_social_post_text)) },
                inputTransformation = InputTransformation.maxLength(MAX_TEXT_LENGTH),
            )
            suggestions.forEach { user -> UserRow(user = user, onClick = { actions.onChooseMention(user) }) }
            VisibilitySection(uiState.visibility, actions.onSelectVisibility)
            PetsSection(uiState, actions.onTogglePet)
            Button(
                onClick = actions.onPublish,
                enabled = uiState.canPublish,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PublishHeight),
            ) {
                Text(
                    stringResource(
                        if (uiState.isPublishing) {
                            R.string.feature_social_publishing
                        } else {
                            R.string.feature_social_publish
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PhotosSection(uiState: CreatePostUiState, actions: CreatePostActions, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        if (uiState.photos.isEmpty()) {
            Surface(
                onClick = actions.onPickPhotos,
                enabled = uiState.canAddPhotos,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(EMPTY_PHOTOS_RATIO),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.small, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(GeoWoofIcons.AddAPhoto),
                        contentDescription = null,
                        modifier = Modifier.size(EmptyPhotosIconSize),
                    )
                    Text(
                        stringResource(R.string.feature_social_add_photos),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            items(uiState.photos, key = ChosenPhoto::contentUri) { photo ->
                ChosenPhotoItem(photo, enabled = !uiState.isPublishing, onRemove = actions.onRemovePhoto)
            }
        }
        FilledTonalButton(onClick = actions.onPickPhotos, enabled = uiState.canAddPhotos) {
            Icon(
                painter = painterResource(GeoWoofIcons.AddAPhoto),
                contentDescription = null,
                modifier = Modifier.padding(end = spacing.small),
            )
            Text(stringResource(R.string.feature_social_add_photos))
        }
    }
}

@Composable
private fun ChosenPhotoItem(
    photo: ChosenPhoto,
    enabled: Boolean,
    onRemove: (contentUri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(PhotoSize)
            .clip(RoundedCornerShape(PhotoCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        LocalPhoto(contentUri = photo.contentUri, modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = { onRemove(photo.contentUri) },
            enabled = enabled,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                painter = painterResource(GeoWoofIcons.Close),
                contentDescription = stringResource(R.string.feature_social_remove_photo),
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(PhotoCorner),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilitySection(
    selected: PostVisibility,
    onSelect: (PostVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small)) {
        SectionTitle(stringResource(R.string.feature_social_who_sees))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PostVisibility.entries.forEachIndexed { index, visibility ->
                SegmentedButton(
                    selected = visibility == selected,
                    onClick = { onSelect(visibility) },
                    shape = SegmentedButtonDefaults.itemShape(index, PostVisibility.entries.size),
                    label = {
                        Text(
                            stringResource(
                                if (visibility == PostVisibility.Everyone) {
                                    R.string.feature_social_visibility_everyone
                                } else {
                                    R.string.feature_social_visibility_friends
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetsSection(uiState: CreatePostUiState, onToggle: (petId: String) -> Unit, modifier: Modifier = Modifier) {
    if (uiState.pets.isEmpty()) return
    val spacing = GeoWoofTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionTitle(stringResource(R.string.feature_social_pets_in_post))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            uiState.pets.forEach { pet ->
                FilterChip(
                    selected = pet.id in uiState.selectedPetIds,
                    onClick = { onToggle(pet.id) },
                    label = { Text(pet.name) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.semantics { heading() }) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

private const val MAX_TEXT_LENGTH = 2000
private val PhotoSize = 96.dp
private val PhotoCorner = 8.dp

@PreviewLightDark
@PreviewFontScale
@Composable
private fun CreatePostPreview() {
    GeoWoofTheme(dynamicColor = false) {
        CreatePostContent(
            uiState = CreatePostUiState(),
            text = TextFieldState(),
            suggestions = emptyList(),
            actions = CreatePostActions(),
        )
    }
}

private val EmptyPhotosIconSize = 40.dp
private val PublishHeight = 52.dp
private const val EMPTY_PHOTOS_RATIO = 1.8f

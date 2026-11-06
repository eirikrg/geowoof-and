package com.geowoof.feature.map.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.WalkLimits
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.ui.PickedPhotoImage
import com.geowoof.core.ui.RouteCanvas
import com.geowoof.core.ui.WalkStatsGrid
import com.geowoof.core.ui.labelRes
import com.geowoof.core.ui.rememberPhotoPicker
import com.geowoof.core.ui.toLines
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.map.R
import java.time.Instant

@Composable
internal fun WalkSummaryScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WalkSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) currentOnDone()
    }
    val pickPhoto = rememberPhotoPicker(viewModel::onAddPhoto)
    WalkSummaryContent(
        uiState = uiState,
        noteText = viewModel.noteText,
        actions = WalkSummaryActions(
            onBack = onBack,
            onVisibilityChange = viewModel::onVisibilityChange,
            onAddPhoto = pickPhoto,
            onRemovePhoto = viewModel::onRemovePhoto,
            onSave = viewModel::onSaveClick,
            onRequestDiscard = viewModel::onRequestDiscard,
            onConfirmDiscard = viewModel::onConfirmDiscard,
            onDismissDiscard = viewModel::onDismissDiscard,
            onErrorShown = viewModel::onErrorShown,
        ),
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
internal class WalkSummaryActions(
    val onBack: () -> Unit,
    val onVisibilityChange: (WalkVisibility) -> Unit,
    val onAddPhoto: () -> Unit,
    val onRemovePhoto: (String) -> Unit,
    val onSave: () -> Unit,
    val onRequestDiscard: () -> Unit,
    val onConfirmDiscard: () -> Unit,
    val onDismissDiscard: () -> Unit,
    val onErrorShown: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalkSummaryContent(
    uiState: WalkSummaryUiState,
    noteText: TextFieldState,
    actions: WalkSummaryActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    val snackbars = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.userMessage()
    val currentOnErrorShown by rememberUpdatedState(actions.onErrorShown)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbars.showSnackbar(errorMessage)
            currentOnErrorShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_map_summary_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_map_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.feature_map_summary_cheer),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                if (uiState.petNames.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.feature_map_summary_with, uiState.petNames.joinToString()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            uiState.walk?.let { walk -> RoutePreview(walk) }
            uiState.stats?.let { stats -> WalkStatsGrid(stats) }
            if (uiState.canSave) {
                WalkDetailsForm(uiState, noteText, actions)
                Button(
                    onClick = actions.onSave,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SaveButtonHeight),
                ) {
                    Text(stringResource(R.string.feature_map_save))
                }
            } else if (uiState.walk != null) {
                Text(
                    text = stringResource(R.string.feature_map_too_short),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(
                onClick = actions.onRequestDiscard,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.feature_map_discard))
            }
        }
    }
    if (uiState.isConfirmingDiscard) {
        AlertDialog(
            onDismissRequest = actions.onDismissDiscard,
            title = { Text(stringResource(R.string.feature_map_discard_title)) },
            text = { Text(stringResource(R.string.feature_map_discard_message)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDiscard) {
                    Text(stringResource(R.string.feature_map_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDiscard) {
                    Text(stringResource(R.string.feature_map_keep))
                }
            },
        )
    }
}

@Composable
private fun RoutePreview(walk: ActiveWalk, modifier: Modifier = Modifier) {
    val lines = remember(walk.segments) { walk.segments.toLines() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RoutePreviewHeight)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        RouteCanvas(
            lines = lines,
            contentDescription = stringResource(R.string.feature_map_route),
            modifier = Modifier.matchParentSize(),
            padding = GeoWoofTheme.spacing.extraLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.WalkDetailsForm(
    uiState: WalkSummaryUiState,
    noteText: TextFieldState,
    actions: WalkSummaryActions,
) {
    val spacing = GeoWoofTheme.spacing
    FormTextField(
        state = noteText,
        label = stringResource(R.string.feature_map_note),
        isError = uiState.isNoteTooLong,
        supportingText = stringResource(R.string.feature_map_note_limit, WalkLimits.NOTE_MAX_LENGTH).takeIf {
            uiState.isNoteTooLong
        },
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = NOTE_MAX_LINES),
    )
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text = stringResource(R.string.feature_map_photos),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            items(uiState.photoUris, key = { it }) { uri ->
                Box(contentAlignment = Alignment.TopEnd) {
                    PickedPhotoImage(contentUri = uri, modifier = Modifier.size(PhotoSize))
                    IconButton(onClick = { actions.onRemovePhoto(uri) }) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.Close),
                            contentDescription = stringResource(R.string.feature_map_remove_photo),
                        )
                    }
                }
            }
            if (uiState.photoUris.size < WalkLimits.MAX_PHOTOS) {
                item {
                    Surface(
                        onClick = actions.onAddPhoto,
                        modifier = Modifier.size(PhotoSize),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(painterResource(GeoWoofIcons.AddAPhoto), contentDescription = null)
                            Text(
                                text = stringResource(R.string.feature_map_add_photo),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text = stringResource(R.string.feature_map_visibility),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        val options = WalkVisibility.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, visibility ->
                SegmentedButton(
                    selected = uiState.visibility == visibility,
                    onClick = { actions.onVisibilityChange(visibility) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(visibility.labelRes()))
                }
            }
        }
    }
}

private val RoutePreviewHeight = 200.dp
private val PhotoSize = 96.dp
private const val NOTE_MAX_LINES = 5

@PreviewLightDark
@Composable
private fun WalkSummaryPreview() {
    GeoWoofTheme(dynamicColor = false) {
        WalkSummaryContent(
            uiState = WalkSummaryUiState(
                walk = ActiveWalk(
                    id = "walk",
                    startedAt = Instant.EPOCH,
                    endedAt = Instant.EPOCH.plusSeconds(600),
                    timeZone = "Europe/Madrid",
                    petIds = listOf("luna"),
                    status = ActiveWalkStatus.Finished,
                    currentSegment = 0,
                    segments = listOf(
                        RouteSegment(
                            listOf(RoutePoint(GeoPoint(40.4, -3.7), 0), RoutePoint(GeoPoint(40.41, -3.69), 600_000)),
                        ),
                    ),
                ),
                stats = WalkStats(1234, 600, 500, 1.5, 648),
                petNames = listOf("Luna"),
            ),
            noteText = TextFieldState(),
            actions = WalkSummaryActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}

private val SaveButtonHeight = 52.dp

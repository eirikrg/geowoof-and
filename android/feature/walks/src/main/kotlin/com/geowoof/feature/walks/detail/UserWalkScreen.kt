package com.geowoof.feature.walks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geowoof.core.common.AppConfig
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.UserWalk
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.ui.RouteCanvas
import com.geowoof.core.ui.WalkPhotoImage
import com.geowoof.core.ui.WalkStatsGrid
import com.geowoof.core.ui.formattedIn
import com.geowoof.core.ui.toLines
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.walks.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class UserWalkUiState(
    val isLoading: Boolean = true,
    val walk: UserWalk? = null,
    val petNames: String = "",
    /** Without a Maps key a real map would be blank, so a drawing of the route replaces it. */
    val showsMap: Boolean = false,
    val error: AppError? = null,
)

/** A walk of someone else. It only comes from the server, which decides what can be seen (docs/decisions/0009). */
@HiltViewModel
class UserWalkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val people: PeopleRepository,
    config: AppConfig,
) : ViewModel() {
    private val walkId: String = checkNotNull(savedStateHandle["walkId"])
    private val state = MutableStateFlow(UserWalkUiState(showsMap = config.isMapsConfigured))

    val uiState: StateFlow<UserWalkUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = people.walk(walkId)) {
                is Outcome.Failure -> state.update { it.copy(isLoading = false, error = result.error) }

                is Outcome.Success -> {
                    val pets = (people.pets(result.value.ownerId) as? Outcome.Success)?.value.orEmpty()
                    val names = result.value.walk.petIds.mapNotNull { id -> pets.find { it.id == id }?.name }
                    state.update { it.copy(isLoading = false, walk = result.value, petNames = names.joinToString()) }
                }
            }
        }
    }
}

@Composable
internal fun UserWalkScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserWalkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UserWalkContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    ) { mapModifier ->
        if (uiState.showsMap) {
            WalkRouteMap(route = uiState.walk?.route, onMapReady = {}, modifier = mapModifier)
        } else {
            val lines = remember(uiState.walk) { uiState.walk?.route?.toLines().orEmpty() }
            RouteCanvas(
                lines = lines,
                contentDescription = stringResource(R.string.feature_walks_route),
                modifier = mapModifier,
                padding = GeoWoofTheme.spacing.extraLarge,
            )
        }
    }
}

/** The map is a slot: it needs Play services, which the tests of this screen do not have. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserWalkContent(
    uiState: UserWalkUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_walks_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_walks_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        val walk = uiState.walk
        when {
            walk != null -> UserWalkDetails(walk, uiState.petNames, map, contentModifier)

            uiState.error != null -> Column(
                modifier = contentModifier.padding(GeoWoofTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(uiState.error.userMessage())
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.feature_walks_retry)) }
            }

            else -> Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun UserWalkDetails(
    userWalk: UserWalk,
    petNames: String,
    map: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    val walk = userWalk.walk
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (userWalk.route.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HiddenRouteHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(spacing.large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.feature_walks_route_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MapHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                map(Modifier.fillMaxSize())
            }
        }
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(walk.startedAt.formattedIn(walk.timeZone), style = MaterialTheme.typography.titleLarge)
            if (petNames.isNotEmpty()) Text(petNames, style = MaterialTheme.typography.bodyLarge)
            WalkStatsGrid(walk.stats)
            walk.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (walk.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    items(walk.photos) { photo -> WalkPhotoImage(photo, modifier = Modifier.size(PhotoSize)) }
                }
            }
        }
    }
}

private val MapHeight = 280.dp
private val HiddenRouteHeight = 120.dp
private val PhotoSize = 120.dp

private val PREVIEW_WALK = UserWalk(
    walk = Walk(
        id = "1",
        startedAt = Instant.parse("2026-11-05T10:00:00Z"),
        endedAt = Instant.parse("2026-11-05T10:30:00Z"),
        timeZone = "Europe/Madrid",
        petIds = listOf("luna"),
        visibility = WalkVisibility.Everyone,
        note = "Por el Retiro",
        stats = WalkStats(3120, 1800, 1700, 1.8, 545),
        previewPolylines = emptyList(),
        photos = emptyList(),
        syncStatus = WalkSyncStatus.Synced,
        syncErrorCode = null,
        updatedAt = Instant.parse("2026-11-05T10:31:00Z"),
    ),
    ownerId = "bea",
    route = listOf(
        RouteSegment(listOf(RoutePoint(GeoPoint(40.41, -3.68), 0), RoutePoint(GeoPoint(40.42, -3.67), 8000))),
    ),
)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun UserWalkPreview() {
    GeoWoofTheme(dynamicColor = false) {
        UserWalkContent(
            uiState = UserWalkUiState(isLoading = false, walk = PREVIEW_WALK, petNames = "Luna"),
            onBack = {},
            onRetry = {},
        ) { mapModifier ->
            RouteCanvas(lines = PREVIEW_WALK.route.toLines(), contentDescription = null, modifier = mapModifier)
        }
    }
}

@PreviewLightDark
@Composable
private fun UserWalkHiddenRoutePreview() {
    GeoWoofTheme(dynamicColor = false) {
        UserWalkContent(
            uiState = UserWalkUiState(
                isLoading = false,
                walk = PREVIEW_WALK.copy(route = emptyList()),
                petNames = "Luna",
            ),
            onBack = {},
            onRetry = {},
        ) {}
    }
}

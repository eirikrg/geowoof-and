package com.geowoof.feature.profile.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.FollowRequest
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.UserRow
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.profile.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class FollowRequestsUiState(
    val received: List<FollowRequest> = emptyList(),
    val sent: List<FollowRequest> = emptyList(),
    val isRefreshing: Boolean = true,
    /** The request being answered, whose buttons wait for the server. */
    val answeringId: String? = null,
    val error: AppError? = null,
)

/** What Room already has stays on screen while the list refreshes (docs/decisions/0009). */
@HiltViewModel
class FollowRequestsViewModel @Inject constructor(private val social: SocialRepository) : ViewModel() {
    private val status = MutableStateFlow(RefreshStatus())
    private val answeringId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FollowRequestsUiState> =
        combine(social.receivedRequests, social.sentRequests, status, answeringId) {
                received,
                sent,
                current,
                answering,
            ->
            FollowRequestsUiState(received, sent, current.isRefreshing, answering, current.error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FollowRequestsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            status.value = RefreshStatus(isRefreshing = true)
            status.value = RefreshStatus(error = (social.refreshRequests() as? Outcome.Failure)?.error)
        }
    }

    fun onAccept(requestId: String) = answer(requestId) { social.acceptRequest(requestId) }

    fun onReject(requestId: String) = answer(requestId) { social.rejectRequest(requestId) }

    fun onCancel(requestId: String) = answer(requestId) { social.cancelRequest(requestId) }

    private fun answer(requestId: String, call: suspend () -> Outcome<*>) {
        if (answeringId.value != null) return
        answeringId.value = requestId
        viewModelScope.launch {
            val failure = call() as? Outcome.Failure
            answeringId.value = null
            if (failure != null) status.update { it.copy(error = failure.error) }
        }
    }

    fun onErrorShown() = status.update { it.copy(error = null) }

    private data class RefreshStatus(val isRefreshing: Boolean = false, val error: AppError? = null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
internal fun FollowRequestsScreen(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowRequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FollowRequestsContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onOpenUser = onOpenUser,
        onAccept = viewModel::onAccept,
        onReject = viewModel::onReject,
        onCancel = viewModel::onCancel,
        onDismissError = viewModel::onErrorShown,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowRequestsContent(
    uiState: FollowRequestsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    onAccept: (requestId: String) -> Unit,
    onReject: (requestId: String) -> Unit,
    onCancel: (requestId: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = errorSnackbar(uiState.error, onDismissError)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_profile_requests_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_profile_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.feature_profile_tab_received, uiState.received.size)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.feature_profile_tab_sent, uiState.sent.size)) },
                )
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val requests = if (selectedTab == 0) uiState.received else uiState.sent
                // A pull needs something that scrolls, also when the list is empty.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (requests.isEmpty() && !uiState.isRefreshing) {
                        item {
                            EmptyState(
                                icon = GeoWoofIcons.GroupAdd,
                                title = stringResource(R.string.feature_profile_no_requests_title),
                                message = stringResource(
                                    if (selectedTab == 0) {
                                        R.string.feature_profile_no_received_message
                                    } else {
                                        R.string.feature_profile_no_sent_message
                                    },
                                ),
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    items(requests, key = FollowRequest::id) { request ->
                        UserRow(
                            user = request.user,
                            onClick = { onOpenUser(request.user.id) },
                            trailing = {
                                if (request.direction == RequestDirection.Received) {
                                    ReceivedActions(request, uiState.answeringId == null, onAccept, onReject)
                                } else {
                                    TextButton(
                                        onClick = { onCancel(request.id) },
                                        enabled = uiState.answeringId == null,
                                    ) {
                                        Text(stringResource(R.string.feature_profile_cancel_request))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedActions(
    request: FollowRequest,
    isEnabled: Boolean,
    onAccept: (requestId: String) -> Unit,
    onReject: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.extraSmall)) {
        IconButton(onClick = { onReject(request.id) }, enabled = isEnabled) {
            Icon(
                painter = painterResource(GeoWoofIcons.Close),
                contentDescription = stringResource(R.string.feature_profile_reject_request, request.user.displayName),
            )
        }
        FilledIconButton(onClick = { onAccept(request.id) }, enabled = isEnabled) {
            Icon(
                painter = painterResource(GeoWoofIcons.Check),
                contentDescription = stringResource(R.string.feature_profile_accept_request, request.user.displayName),
            )
        }
    }
}

@Composable
private fun errorSnackbar(error: AppError?, onDismiss: () -> Unit): SnackbarHostState {
    val snackbars = remember { SnackbarHostState() }
    val message = error?.userMessage()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            currentOnDismiss()
        }
    }
    return snackbars
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun FollowRequestsPreview() {
    val user = UserSummary("1", "bea", "Bea", "Madrid", null)
    GeoWoofTheme(dynamicColor = false) {
        FollowRequestsContent(
            uiState = FollowRequestsUiState(
                received = listOf(
                    FollowRequest("1", user, RequestDirection.Received, Instant.parse("2026-11-05T10:00:00Z")),
                ),
                isRefreshing = false,
            ),
            onBack = {},
            onRefresh = {},
            onOpenUser = {},
            onAccept = {},
            onReject = {},
            onCancel = {},
            onDismissError = {},
        )
    }
}

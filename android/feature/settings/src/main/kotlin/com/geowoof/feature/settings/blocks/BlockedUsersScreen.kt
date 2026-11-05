package com.geowoof.feature.settings.blocks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.BlockedUser
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.settings.R
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

data class BlockedUsersUiState(
    val blocked: List<BlockedUser> = emptyList(),
    val isRefreshing: Boolean = true,
    val isUnblocking: Boolean = false,
    val error: AppError? = null,
)

/** Blocked people do not appear anywhere else, so this is the only way back for them. */
@HiltViewModel
class BlockedUsersViewModel @Inject constructor(private val social: SocialRepository) : ViewModel() {
    private val status = MutableStateFlow(BlockedUsersUiState())

    val uiState: StateFlow<BlockedUsersUiState> = combine(social.blockedUsers, status) { blocked, current ->
        current.copy(blocked = blocked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BlockedUsersUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            status.update { it.copy(isRefreshing = true, error = null) }
            val failure = social.refreshBlockedUsers() as? Outcome.Failure
            status.update { it.copy(isRefreshing = false, error = failure?.error) }
        }
    }

    fun onUnblock(userId: String) {
        if (status.value.isUnblocking) return
        status.update { it.copy(isUnblocking = true) }
        viewModelScope.launch {
            val failure = social.unblock(userId) as? Outcome.Failure
            status.update { it.copy(isUnblocking = false, error = failure?.error) }
        }
    }

    fun onErrorShown() = status.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
internal fun BlockedUsersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockedUsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BlockedUsersContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onUnblock = viewModel::onUnblock,
        onDismissError = viewModel::onErrorShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockedUsersContent(
    uiState: BlockedUsersUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUnblock: (userId: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val message = uiState.error?.userMessage()
    val currentOnDismissError by rememberUpdatedState(onDismissError)
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            currentOnDismissError()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_blocked_users)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // A pull needs something that scrolls, also when the list is empty.
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (uiState.blocked.isEmpty() && !uiState.isRefreshing) {
                    item {
                        EmptyState(
                            icon = GeoWoofIcons.Block,
                            title = stringResource(R.string.feature_settings_no_blocked_title),
                            message = stringResource(R.string.feature_settings_no_blocked_message),
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
                items(uiState.blocked, key = { it.user.id }) { blocked ->
                    ListItem(
                        headlineContent = { Text(blocked.user.displayName) },
                        supportingContent = { Text("@${blocked.user.username}") },
                        leadingContent = {
                            RemoteAvatar(
                                image = blocked.user.avatar,
                                fallbackIcon = GeoWoofIcons.Person,
                                contentDescription = null,
                                size = AvatarSize,
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { onUnblock(blocked.user.id) }, enabled = !uiState.isUnblocking) {
                                Text(stringResource(R.string.feature_settings_unblock))
                            }
                        },
                    )
                }
            }
        }
    }
}

private val AvatarSize = 40.dp

@PreviewLightDark
@PreviewFontScale
@Composable
private fun BlockedUsersPreview() {
    GeoWoofTheme(dynamicColor = false) {
        BlockedUsersContent(
            uiState = BlockedUsersUiState(
                blocked = listOf(
                    BlockedUser(UserSummary("1", "bea", "Bea", null, null), Instant.parse("2026-11-05T10:00:00Z")),
                ),
                isRefreshing = false,
            ),
            onBack = {},
            onRefresh = {},
            onUnblock = {},
            onDismissError = {},
        )
    }
}

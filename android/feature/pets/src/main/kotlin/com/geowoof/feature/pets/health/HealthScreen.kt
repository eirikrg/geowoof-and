package com.geowoof.feature.pets.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.IconBadge
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.HealthEvent
import com.geowoof.core.ui.formatted
import com.geowoof.core.ui.openExactAlarmSettings
import com.geowoof.core.ui.openNotificationSettings
import com.geowoof.core.ui.rememberExactAlarmsAllowed
import com.geowoof.core.ui.rememberNotificationsEnabled
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.pets.R

@Composable
internal fun HealthScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HealthContent(
        uiState = uiState,
        actions = HealthActions(
            onBack = onBack,
            onAdd = onAdd,
            onOpenEvent = onOpenEvent,
            onComplete = viewModel::onComplete,
            onRefresh = viewModel::refresh,
            onErrorShown = viewModel::onErrorShown,
        ),
        modifier = modifier,
    )
}

internal class HealthActions(
    val onBack: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenEvent: (eventId: String) -> Unit = {},
    val onComplete: (HealthEvent) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HealthContent(uiState: HealthUiState, actions: HealthActions, modifier: Modifier = Modifier) {
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
                title = {
                    Text(
                        uiState.petName?.let { stringResource(R.string.feature_pets_health_of, it) }
                            ?: stringResource(R.string.feature_pets_health_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_pets_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAdd,
                    icon = { Icon(painterResource(GeoWoofIcons.Add), contentDescription = null) },
                    text = { Text(stringResource(R.string.feature_pets_health_add)) },
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing && !uiState.isLoading,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> SkeletonList()

                uiState.isEmpty -> EmptyState(
                    icon = GeoWoofIcons.Vaccines,
                    title = stringResource(R.string.feature_pets_health_empty_title),
                    message = stringResource(R.string.feature_pets_health_empty_message),
                    action = {
                        FilledTonalButton(onClick = actions.onAdd) {
                            Text(stringResource(R.string.feature_pets_health_add))
                        }
                    },
                )

                else -> HealthList(uiState, actions)
            }
        }
    }
}

@Composable
private fun HealthList(uiState: HealthUiState, actions: HealthActions) {
    val context = LocalContext.current
    val notificationsEnabled = rememberNotificationsEnabled()
    val exactAlarmsAllowed = rememberExactAlarmsAllowed()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room for the button that floats over the end of the list.
        contentPadding = PaddingValues(bottom = ListEndPadding),
    ) {
        // One at a time: without notifications, the time they would arrive does not matter.
        if (uiState.hasReminders && !notificationsEnabled) {
            item(key = "notifications") {
                RemindersWarning(
                    text = stringResource(R.string.feature_pets_health_notifications_off),
                    action = stringResource(R.string.feature_pets_health_turn_on),
                    onAction = context::openNotificationSettings,
                    isSerious = true,
                )
            }
        } else if (uiState.hasReminders && !exactAlarmsAllowed) {
            item(key = "exact-alarms") {
                RemindersWarning(
                    text = stringResource(R.string.feature_pets_health_alarms_late),
                    action = stringResource(R.string.feature_pets_health_allow),
                    onAction = context::openExactAlarmSettings,
                    isSerious = false,
                )
            }
        }
        section(R.string.feature_pets_health_overdue, uiState.overdue, uiState, actions)
        section(R.string.feature_pets_health_upcoming, uiState.upcoming, uiState, actions)
        section(R.string.feature_pets_health_history, uiState.history, uiState, actions)
    }
}

private fun LazyListScope.section(
    title: Int,
    events: List<HealthEvent>,
    uiState: HealthUiState,
    actions: HealthActions,
) {
    if (events.isEmpty()) return
    item(key = "title:$title") {
        Text(
            text = stringResource(title),
            modifier = Modifier
                .padding(
                    start = GeoWoofTheme.spacing.large,
                    end = GeoWoofTheme.spacing.large,
                    top = GeoWoofTheme.spacing.large,
                )
                .semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    items(events, key = HealthEvent::id) { event ->
        HealthEventRow(
            event = event,
            isCompleting = event.id in uiState.completing,
            onOpen = { actions.onOpenEvent(event.id) },
            onComplete = { actions.onComplete(event) },
        )
    }
}

/** What it is and when; what is pending is done from here, with one touch. */
@Composable
internal fun HealthEventRow(
    event: HealthEvent,
    isCompleting: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onComplete: (() -> Unit)? = null,
) {
    val isLate = event.isOverdue(java.time.LocalDate.now())
    val description = if (event.isDone) {
        stringResource(R.string.feature_pets_health_done_on, event.doneOn!!.formatted())
    } else {
        listOf(event.dueText(), event.repeat?.label()).filterNotNull().joinToString(SEPARATOR)
    }
    ListItem(
        modifier = modifier.clickable(onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBadge(icon = event.type.icon()) },
        overlineContent = { Text(stringResource(event.type.labelRes())) },
        headlineContent = { Text(event.title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text = description,
                color = if (isLate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (event.isDone || onComplete == null) {
            null
        } else {
            {
                if (isCompleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(ProgressPadding).size(ProgressSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onComplete) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.CheckCircle),
                            contentDescription = stringResource(R.string.feature_pets_health_mark_done, event.title),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
    )
}

/**
 * What keeps the reminders from arriving, or from arriving at their time, with the way to fix it in the settings: the
 * notifications that are off ([isSerious]) or the alarms at their time that are not allowed.
 */
@Composable
private fun RemindersWarning(
    text: String,
    action: String,
    onAction: () -> Unit,
    isSerious: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.small),
        shape = MaterialTheme.shapes.large,
        color = if (isSerious) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (isSerious) colors.onErrorContainer else colors.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(start = spacing.large, end = spacing.small, top = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(action)
            }
        }
    }
}

private const val SEPARATOR = " · "
private val ListEndPadding = 96.dp
private val ProgressSize = 24.dp
private val ProgressPadding = 12.dp

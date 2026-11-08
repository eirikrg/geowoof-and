package com.geowoof.feature.pets.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.ListGroup
import com.geowoof.core.designsystem.component.ListGroupDivider
import com.geowoof.core.designsystem.component.ListGroupItem
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.HealthEventType
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.ui.AskForNotifications
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.formatted
import com.geowoof.feature.pets.PetDateDialog
import com.geowoof.feature.pets.R
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HealthEditorScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HealthEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) currentOnDone()
    }
    HealthEditorContent(
        uiState = uiState,
        title = viewModel.title,
        notes = viewModel.notes,
        actions = HealthEditorActions(
            onBack = onBack,
            onTypeChange = viewModel::onTypeChange,
            onSuggestion = viewModel::onSuggestion,
            onPick = viewModel::onPick,
            onDueOnChange = viewModel::onDueOnChange,
            onDueAtChange = viewModel::onDueAtChange,
            onRepeatChange = viewModel::onRepeatChange,
            onUntilChange = viewModel::onUntilChange,
            onReminderChange = viewModel::onReminderChange,
            onDoneChange = viewModel::onDoneChange,
            onDoneOnChange = viewModel::onDoneOnChange,
            onSave = viewModel::save,
            onNotificationsAnswered = viewModel::onNotificationsAnswered,
            onRequestDelete = viewModel::onRequestDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
        ),
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
internal class HealthEditorActions(
    val onBack: () -> Unit = {},
    val onTypeChange: (HealthEventType) -> Unit = {},
    val onSuggestion: (String) -> Unit = {},
    val onPick: (HealthPicker?) -> Unit = {},
    val onDueOnChange: (java.time.LocalDate) -> Unit = {},
    val onDueAtChange: (LocalTime?) -> Unit = {},
    val onRepeatChange: (HealthRepeat?) -> Unit = {},
    val onUntilChange: (java.time.LocalDate?) -> Unit = {},
    val onReminderChange: (HealthReminder) -> Unit = {},
    val onDoneChange: (Boolean) -> Unit = {},
    val onDoneOnChange: (java.time.LocalDate) -> Unit = {},
    val onSave: () -> Unit = {},
    val onNotificationsAnswered: () -> Unit = {},
    val onRequestDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HealthEditorContent(
    uiState: HealthEditorUiState,
    title: TextFieldState,
    notes: TextFieldState,
    actions: HealthEditorActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = GeoWoofTheme.spacing
    FormScaffold(
        title = stringResource(
            if (uiState.isNew) R.string.feature_pets_health_new_title else R.string.feature_pets_health_edit_title,
        ),
        modifier = modifier,
        onBack = actions.onBack,
        actions = {
            IconButton(onClick = { context.addToCalendar(uiState, title.text.toString()) }) {
                Icon(
                    painter = painterResource(GeoWoofIcons.CalendarMonth),
                    contentDescription = stringResource(R.string.feature_pets_health_add_to_calendar),
                )
            }
            if (!uiState.isNew) {
                IconButton(onClick = actions.onRequestDelete) {
                    Icon(
                        painterResource(GeoWoofIcons.Delete),
                        contentDescription = stringResource(R.string.feature_pets_delete),
                    )
                }
            }
        },
    ) {
        Text(stringResource(R.string.feature_pets_health_type), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            HealthEventType.entries.forEach { type ->
                FilterChip(
                    selected = uiState.type == type,
                    onClick = { actions.onTypeChange(type) },
                    label = { Text(stringResource(type.labelRes())) },
                    leadingIcon = {
                        Icon(
                            painterResource(type.icon()),
                            contentDescription = null,
                            modifier = Modifier.size(ChipIconSize),
                        )
                    },
                )
            }
        }
        OutlinedTextField(
            state = title,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feature_pets_health_what)) },
            isError = uiState.isTitleMissing,
            supportingText = if (uiState.isTitleMissing) {
                { Text(stringResource(R.string.feature_pets_health_title_missing)) }
            } else {
                null
            },
            inputTransformation = InputTransformation.maxLength(TITLE_MAX_LENGTH),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        uiState.type.suggestionsRes()?.let { suggestions ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                stringArrayResource(suggestions).forEach { suggestion ->
                    SuggestionChip(onClick = { actions.onSuggestion(suggestion) }, label = { Text(suggestion) })
                }
            }
        }
        WhenGroup(uiState, actions)
        DoneGroup(uiState, actions)
        OutlinedTextField(
            state = notes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feature_pets_health_notes)) },
            inputTransformation = InputTransformation.maxLength(NOTES_MAX_LENGTH),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = NOTES_LINES),
        )
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_pets_save),
            loading = uiState.isSaving,
            onClick = actions.onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
        )
    }
    HealthEditorDialogs(uiState, actions)
}

/** When it is due, whether it repeats, and when to be reminded. */
@Composable
private fun WhenGroup(uiState: HealthEditorUiState, actions: HealthEditorActions) {
    ListGroup(inset = 0.dp) {
        ListGroupItem(
            headline = stringResource(R.string.feature_pets_health_date),
            supporting = uiState.dueOn.formatted(),
            icon = GeoWoofIcons.CalendarMonth,
            onClick = { actions.onPick(HealthPicker.DueOn) },
        )
        ListGroupDivider()
        ListGroupItem(
            headline = stringResource(R.string.feature_pets_health_time),
            supporting = uiState.dueAt?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                ?: stringResource(R.string.feature_pets_health_all_day),
            icon = GeoWoofIcons.Schedule,
            onClick = { actions.onPick(HealthPicker.DueAt) },
            trailing = uiState.dueAt?.let {
                {
                    IconButton(onClick = { actions.onDueAtChange(null) }) {
                        Icon(
                            painterResource(GeoWoofIcons.Close),
                            contentDescription = stringResource(R.string.feature_pets_health_all_day),
                        )
                    }
                }
            },
        )
        ListGroupDivider()
        ListGroupItem(
            headline = stringResource(R.string.feature_pets_health_repeats),
            supporting = uiState.repeat.label(),
            icon = GeoWoofIcons.Repeat,
            onClick = { actions.onPick(HealthPicker.Repeat) },
        )
        if (uiState.repeat != null) {
            ListGroupDivider()
            ListGroupItem(
                headline = stringResource(R.string.feature_pets_health_until),
                supporting = uiState.repeat.until?.formatted() ?: stringResource(R.string.feature_pets_health_no_end),
                icon = GeoWoofIcons.CalendarMonth,
                onClick = { actions.onPick(HealthPicker.Until) },
                trailing = uiState.repeat.until?.let {
                    {
                        IconButton(onClick = { actions.onUntilChange(null) }) {
                            Icon(
                                painterResource(GeoWoofIcons.Close),
                                contentDescription = stringResource(R.string.feature_pets_health_no_end),
                            )
                        }
                    }
                },
            )
        }
        ListGroupDivider()
        ListGroupItem(
            headline = stringResource(R.string.feature_pets_health_remind),
            supporting = stringResource(uiState.reminder.labelRes()),
            icon = GeoWoofIcons.Notifications,
            onClick = { actions.onPick(HealthPicker.Reminder) },
        )
    }
}

/** Whether it was done already, and when: then it goes to the history. */
@Composable
private fun DoneGroup(uiState: HealthEditorUiState, actions: HealthEditorActions) {
    ListGroup(inset = 0.dp) {
        ListGroupItem(
            headline = stringResource(R.string.feature_pets_health_already_done),
            icon = GeoWoofIcons.CheckCircle,
            modifier = Modifier.toggleable(
                value = uiState.isDone,
                role = Role.Switch,
                onValueChange = actions.onDoneChange,
            ),
            trailing = { Switch(checked = uiState.isDone, onCheckedChange = null) },
        )
        if (uiState.isDone) {
            ListGroupDivider()
            ListGroupItem(
                headline = stringResource(R.string.feature_pets_health_done_on_label),
                supporting = uiState.doneOn.formatted(),
                icon = GeoWoofIcons.CalendarMonth,
                onClick = { actions.onPick(HealthPicker.DoneOn) },
            )
        }
    }
}

@Composable
private fun HealthEditorDialogs(uiState: HealthEditorUiState, actions: HealthEditorActions) {
    val close = { actions.onPick(null) }
    when (uiState.picker) {
        HealthPicker.DueOn -> PetDateDialog(uiState.dueOn, actions.onDueOnChange, close, allowsFuture = true)

        HealthPicker.Until -> PetDateDialog(
            uiState.repeat?.until ?: uiState.dueOn,
            actions.onUntilChange,
            close,
            allowsFuture = true,
        )

        HealthPicker.DoneOn -> PetDateDialog(uiState.doneOn, actions.onDoneOnChange, close)

        HealthPicker.DueAt -> TimeDialog(
            uiState.dueAt ?: DefaultTime,
            onConfirm = actions.onDueAtChange,
            onDismiss = close,
        )

        HealthPicker.Repeat -> ChoiceDialog(
            title = stringResource(R.string.feature_pets_health_repeats),
            choices = (RepeatChoices + uiState.repeat?.copy(until = null)).distinct(),
            selected = uiState.repeat?.copy(until = null),
            label = { it.label() },
            onSelect = actions.onRepeatChange,
            onDismiss = close,
        )

        HealthPicker.Reminder -> ChoiceDialog(
            title = stringResource(R.string.feature_pets_health_remind),
            choices = HealthReminder.entries,
            selected = uiState.reminder,
            label = { stringResource(it.labelRes()) },
            onSelect = actions.onReminderChange,
            onDismiss = close,
        )

        null -> Unit
    }
    if (uiState.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            title = { Text(stringResource(R.string.feature_pets_health_delete_title)) },
            text = { Text(stringResource(R.string.feature_pets_health_delete_message)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDelete) { Text(stringResource(R.string.feature_pets_delete)) }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDelete) { Text(stringResource(R.string.feature_pets_cancel)) }
            },
        )
    }
    if (uiState.asksForNotifications) {
        AskForNotifications(
            title = stringResource(R.string.feature_pets_health_notifications_title),
            message = stringResource(R.string.feature_pets_health_notifications_message),
            onDone = actions.onNotificationsAnswered,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.feature_pets_accept))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_pets_cancel)) } },
    )
}

/** One of a few options, chosen with a touch. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == selected, role = Role.RadioButton, onClick = {
                                onSelect(choice)
                            })
                            .padding(vertical = GeoWoofTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Text(label(choice), modifier = Modifier.padding(start = GeoWoofTheme.spacing.medium))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_pets_cancel)) } },
    )
}

/** The calendar of the phone opens with the event filled in: it needs no permission, and the person saves it there. */
private fun Context.addToCalendar(uiState: HealthEditorUiState, title: String) {
    val zone = ZoneId.systemDefault()
    val start = (uiState.dueAt?.let(uiState.dueOn::atTime) ?: uiState.dueOn.atStartOfDay()).atZone(zone)
    val name = listOfNotNull(uiState.petName, title.trim().ifEmpty { null }).joinToString(": ")
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, name)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.plusHours(1).toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, uiState.dueAt == null)
    try {
        startActivity(intent)
    } catch (@Suppress("SwallowedException") exception: ActivityNotFoundException) {
        Toast.makeText(this, R.string.feature_pets_health_no_calendar, Toast.LENGTH_LONG).show()
    }
}

private const val TITLE_MAX_LENGTH = 100
private const val NOTES_MAX_LENGTH = 1000
private const val NOTES_LINES = 5
private val ChipIconSize = 18.dp
private val DefaultTime: LocalTime = LocalTime.of(10, 0)

package com.geowoof.feature.pets.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Pet
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.model.WeightIssue
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.formatted
import com.geowoof.core.ui.labelRes
import com.geowoof.core.ui.messageRes
import com.geowoof.core.ui.shortDescription
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.pets.PetDateDialog
import com.geowoof.feature.pets.R
import java.time.LocalDate

@Composable
internal fun PetDetailScreen(
    onBack: () -> Unit,
    onEdit: (petId: String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PetDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) currentOnDone()
    }
    PetDetailContent(
        uiState = uiState,
        weightText = viewModel.weightText,
        actions = PetDetailActions(
            onRefresh = viewModel::refresh,
            onErrorShown = viewModel::onErrorShown,
            onAddWeight = { viewModel.openAddWeight() },
            onEditWeight = viewModel::openEditWeight,
            onWeightDateChange = viewModel::onWeightDateChange,
            onSaveWeight = viewModel::saveWeight,
            onDeleteWeight = viewModel::deleteWeight,
            onDismissWeight = viewModel::dismissWeightEditor,
            onRequestDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
        ),
        onBack = onBack,
        onEdit = onEdit,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
internal class PetDetailActions(
    val onRefresh: () -> Unit,
    val onErrorShown: () -> Unit,
    val onAddWeight: () -> Unit,
    val onEditWeight: (WeightEntry) -> Unit,
    val onWeightDateChange: (LocalDate) -> Unit,
    val onSaveWeight: () -> Unit,
    val onDeleteWeight: () -> Unit,
    val onDismissWeight: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PetDetailContent(
    uiState: PetDetailUiState,
    weightText: TextFieldState,
    actions: PetDetailActions,
    onBack: () -> Unit,
    onEdit: (petId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                title = { Text(uiState.pet?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_pets_back),
                        )
                    }
                },
                actions = {
                    uiState.pet?.let { pet ->
                        IconButton(onClick = { onEdit(pet.id) }) {
                            Icon(
                                painter = painterResource(GeoWoofIcons.Edit),
                                contentDescription = stringResource(R.string.feature_pets_edit_title),
                            )
                        }
                        IconButton(onClick = actions.onRequestDelete, enabled = !uiState.isDeleting) {
                            Icon(
                                painter = painterResource(GeoWoofIcons.Delete),
                                contentDescription = stringResource(R.string.feature_pets_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = GeoWoofTheme.spacing.huge),
            ) {
                uiState.pet?.let { pet -> petDetails(pet) }
                weightSection(uiState.weights, actions)
            }
        }
    }
    uiState.weightEditor?.let { editor ->
        WeightDialog(editor = editor, weightText = weightText, actions = actions)
    }
    if (uiState.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            icon = { Icon(painterResource(GeoWoofIcons.Delete), contentDescription = null) },
            title = { Text(stringResource(R.string.feature_pets_delete_title, uiState.pet?.name.orEmpty())) },
            text = { Text(stringResource(R.string.feature_pets_delete_message)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDelete) {
                    Text(stringResource(R.string.feature_pets_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDelete) { Text(stringResource(R.string.feature_pets_cancel)) }
            },
        )
    }
}

private fun LazyListScope.petDetails(pet: Pet) {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GeoWoofTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.extraSmall),
        ) {
            RemoteAvatar(
                image = pet.avatar,
                fallbackIcon = GeoWoofIcons.Pets,
                contentDescription = stringResource(R.string.feature_pets_photo),
                size = 120.dp,
                variant = ImageVariant.Medium,
            )
            Text(pet.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = pet.shortDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pet.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    item { Fact(R.string.feature_pets_sex_label, stringResource(pet.sex.labelRes())) }
    pet.birthDate?.let { date -> item { Fact(R.string.feature_pets_birth_date_label, date.formatted()) } }
    pet.size?.let { size -> item { Fact(R.string.feature_pets_size_label, stringResource(size.labelRes())) } }
    pet.color?.let { color -> item { Fact(R.string.feature_pets_color_label, color) } }
    item {
        Fact(
            R.string.feature_pets_neutered_label,
            stringResource(if (pet.neutered) R.string.feature_pets_yes else R.string.feature_pets_no),
        )
    }
    pet.microchip?.let { microchip ->
        item {
            Fact(
                R.string.feature_pets_microchip_label,
                microchip,
                stringResource(R.string.feature_pets_microchip_private),
            )
        }
    }
}

@Composable
private fun Fact(label: Int, value: String, note: String? = null) {
    ListItem(
        overlineContent = { Text(stringResource(label)) },
        headlineContent = { Text(value) },
        supportingContent = note?.let { { Text(it) } },
    )
}

private fun LazyListScope.weightSection(weights: List<WeightEntry>, actions: PetDetailActions) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GeoWoofTheme.spacing.large, vertical = GeoWoofTheme.spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_pets_weight_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            FilledTonalButton(onClick = actions.onAddWeight) {
                Text(stringResource(R.string.feature_pets_add_weight))
            }
        }
    }
    if (weights.size >= MIN_POINTS_FOR_CHART) {
        item(key = "chart") {
            WeightChart(weights = weights, modifier = Modifier.padding(horizontal = GeoWoofTheme.spacing.large))
        }
    }
    if (weights.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.feature_pets_no_weights),
                modifier = Modifier.padding(horizontal = GeoWoofTheme.spacing.large),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(weights.asReversed(), key = WeightEntry::id) { entry ->
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_pets_weight_value, entry.weightKg)) },
            supportingContent = { Text(entry.measuredOn.formatted()) },
            leadingContent = { Icon(painterResource(GeoWoofIcons.MonitorWeight), contentDescription = null) },
            modifier = Modifier.clickable { actions.onEditWeight(entry) },
        )
    }
}

@Composable
private fun WeightDialog(
    editor: WeightEditorState,
    weightText: TextFieldState,
    actions: PetDetailActions,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = actions.onDismissWeight,
        modifier = modifier,
        title = {
            Text(
                stringResource(
                    if (editor.entryId == null) R.string.feature_pets_add_weight else R.string.feature_pets_edit_weight,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small)) {
                FormTextField(
                    state = weightText,
                    label = stringResource(R.string.feature_pets_weight_label),
                    supportingText = editor.weightIssue?.let { weightMessage(it) },
                    isError = editor.weightIssue != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    onKeyboardAction = { actions.onSaveWeight() },
                )
                TextButton(onClick = { picking = true }) {
                    Icon(painterResource(GeoWoofIcons.CalendarMonth), contentDescription = null)
                    Text(editor.measuredOn.formatted())
                }
                editor.dateIssue?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
                }
                FormError(editor.error)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSaveWeight, enabled = !editor.isSaving) {
                Text(stringResource(R.string.feature_pets_save))
            }
        },
        dismissButton = {
            Row {
                if (editor.entryId != null) {
                    TextButton(onClick = actions.onDeleteWeight, enabled = !editor.isSaving) {
                        Text(stringResource(R.string.feature_pets_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = actions.onDismissWeight) { Text(stringResource(R.string.feature_pets_cancel)) }
            }
        },
    )
    if (picking) {
        PetDateDialog(
            initialDate = editor.measuredOn,
            onConfirm = { date ->
                actions.onWeightDateChange(date)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun weightMessage(issue: WeightIssue): String = stringResource(
    when (issue) {
        WeightIssue.NotANumber -> R.string.feature_pets_weight_not_a_number
        WeightIssue.OutOfRange -> R.string.feature_pets_weight_out_of_range
        WeightIssue.TooManyDecimals -> R.string.feature_pets_weight_decimals
    },
)

private const val MIN_POINTS_FOR_CHART = 2

@PreviewLightDark
@Composable
private fun PetDetailPreview() {
    GeoWoofTheme(dynamicColor = false) {
        PetDetailContent(
            uiState = PetDetailUiState(),
            weightText = TextFieldState(),
            actions = PetDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
            onBack = {},
            onEdit = {},
        )
    }
}

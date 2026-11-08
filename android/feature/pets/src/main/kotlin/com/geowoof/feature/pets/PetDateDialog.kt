package com.geowoof.feature.pets

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Picks a date between 1990 and today, the range that the API accepts. With [allowsFuture], up to ten years from
 * today, as what is due in the health of a pet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PetDateDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    allowsFuture: Boolean = false,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = if (allowsFuture) DatesAhead else PastDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) {
                Text(stringResource(R.string.feature_pets_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_pets_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private val PastDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return !date.isAfter(LocalDate.now()) && date.year >= FIRST_YEAR
    }

    override fun isSelectableYear(year: Int): Boolean = year in FIRST_YEAR..LocalDate.now().year
}

@OptIn(ExperimentalMaterial3Api::class)
private val DatesAhead = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return !date.isAfter(LocalDate.now().plusYears(YEARS_AHEAD)) && date.year >= FIRST_YEAR
    }

    override fun isSelectableYear(year: Int): Boolean = year in FIRST_YEAR..LocalDate.now().year + YEARS_AHEAD
}

private const val FIRST_YEAR = 1990
private const val YEARS_AHEAD = 10L

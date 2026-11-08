package com.geowoof.core.data.mapper

import com.geowoof.core.database.HealthEventEntity
import com.geowoof.core.model.HealthEvent
import com.geowoof.core.model.HealthEventType
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.model.RepeatUnit
import com.geowoof.core.network.model.HealthEventBody
import com.geowoof.core.network.model.NetworkHealthEvent
import com.geowoof.core.network.model.NetworkHealthRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// A value of an enum that a newer server sends and this version does not know is read as the closest one it knows,
// instead of failing: Other for a type, no reminder, and no repetition.

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val MINUTES_PER_HOUR = 60

internal fun NetworkHealthEvent.toEntity() = HealthEventEntity(
    id = id,
    petId = petId,
    type = type,
    title = title,
    dueOnEpochDay = LocalDate.parse(dueOn).toEpochDay(),
    dueAtMinute = dueAt?.let { LocalTime.parse(it, TIME) }?.let { it.hour * MINUTES_PER_HOUR + it.minute },
    doneOnEpochDay = doneOn?.let { LocalDate.parse(it).toEpochDay() },
    repeatEvery = repeat?.every,
    repeatUnit = repeat?.unit,
    repeatUntilEpochDay = repeat?.until?.let { LocalDate.parse(it).toEpochDay() },
    reminder = reminder,
    notes = notes,
    updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
)

internal fun HealthEventEntity.toModel() = HealthEvent(
    id = id,
    petId = petId,
    type = HealthEventType.entries.firstOrNull { it.name == type } ?: HealthEventType.Other,
    title = title,
    dueOn = LocalDate.ofEpochDay(dueOnEpochDay),
    dueAt = dueAtMinute?.let { LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) },
    doneOn = doneOnEpochDay?.let(LocalDate::ofEpochDay),
    repeat = repeatEvery?.let { every ->
        RepeatUnit.entries.firstOrNull { it.name == repeatUnit }?.let { unit ->
            HealthRepeat(every, unit, repeatUntilEpochDay?.let(LocalDate::ofEpochDay))
        }
    },
    reminder = HealthReminder.entries.firstOrNull { it.name == reminder } ?: HealthReminder.None,
    notes = notes,
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

internal fun HealthEvent.toBody() = HealthEventBody(
    type = type.name,
    title = title.trim(),
    dueOn = dueOn.toString(),
    dueAt = dueAt?.format(TIME),
    doneOn = doneOn?.toString(),
    repeat = repeat?.let { NetworkHealthRepeat(it.every, it.unit.name, it.until?.toString()) },
    reminder = reminder.name,
    notes = notes?.trim()?.ifEmpty { null },
)

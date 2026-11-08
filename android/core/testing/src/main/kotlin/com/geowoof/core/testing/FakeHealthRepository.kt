package com.geowoof.core.testing

import com.geowoof.core.data.repository.HealthRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.HealthCompletion
import com.geowoof.core.model.HealthEvent
import com.geowoof.core.model.HealthEventType
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.HealthReminderItem
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object TestHealth {
    val TODAY: LocalDate = LocalDate.now()
    private const val DAYS_AHEAD = 10L

    @Suppress("LongParameterList")
    fun event(
        id: String = "rabies",
        petId: String = "luna",
        type: HealthEventType = HealthEventType.Vaccine,
        title: String = "Rabia",
        dueOn: LocalDate = TODAY.plusDays(DAYS_AHEAD),
        dueAt: LocalTime? = null,
        doneOn: LocalDate? = null,
        repeat: HealthRepeat? = null,
        reminder: HealthReminder = HealthReminder.DayBefore,
        notes: String? = null,
    ) = HealthEvent(id, petId, type, title, dueOn, dueAt, doneOn, repeat, reminder, notes, Instant.EPOCH)
}

/**
 * Keeps the events in memory, as Room would, and does what the server would: completing something that repeats makes
 * the next one from the day it was done.
 */
class FakeHealthRepository(
    events: List<HealthEvent> = emptyList(),
    private val petNames: Map<String, String> = emptyMap(),
) : HealthRepository {
    private val stored = MutableStateFlow(events)

    var failure: AppError? = null

    /** What was asked of it, in order: "save:rabies", "complete:rabies:2026-11-08"... */
    val calls = mutableListOf<String>()

    private var nextId = 0

    val events: List<HealthEvent> get() = stored.value

    override fun events(petId: String): Flow<List<HealthEvent>> = stored.map { list ->
        list.filter { it.petId == petId }
    }

    override suspend fun event(eventId: String): HealthEvent? = stored.value.firstOrNull { it.id == eventId }

    override fun pendingReminders(): Flow<List<HealthReminderItem>> = stored.map { list ->
        list.filter { !it.isDone && it.reminder != HealthReminder.None }.map {
            HealthReminderItem(
                it,
                petNames[it.petId] ?: it.petId,
            )
        }
    }

    override suspend fun reminderItem(eventId: String): HealthReminderItem? =
        event(eventId)?.let { HealthReminderItem(it, petNames[it.petId] ?: it.petId) }

    override suspend fun refresh(petId: String): Outcome<Unit> {
        calls += "refresh:$petId"
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override suspend fun refreshAll(): Outcome<Unit> {
        calls += "refreshAll"
        return Outcome.Success(Unit)
    }

    override fun newId(): String = "new-${nextId++}"

    override suspend fun save(event: HealthEvent): Outcome<HealthEvent> {
        calls += "save:${event.id}"
        failure?.let { return Outcome.Failure(it) }
        stored.update { list -> list.filterNot { it.id == event.id } + event }
        return Outcome.Success(event)
    }

    override suspend fun complete(event: HealthEvent, doneOn: LocalDate): Outcome<HealthCompletion> {
        calls += "complete:${event.id}:$doneOn"
        failure?.let { return Outcome.Failure(it) }
        val done = event.copy(doneOn = doneOn)
        val next = event.repeat
            ?.let { repeat -> repeat.after(doneOn).takeUnless { due -> repeat.until?.let(due::isAfter) == true } }
            ?.let { due -> event.copy(id = "${event.id}-next", dueOn = due, doneOn = null, notes = null) }
        stored.update { list -> list.filterNot { it.id == event.id } + listOfNotNull(done, next) }
        return Outcome.Success(HealthCompletion(done, next))
    }

    override suspend fun delete(event: HealthEvent): Outcome<Unit> {
        calls += "delete:${event.id}"
        failure?.let { return Outcome.Failure(it) }
        stored.update { list -> list.filterNot { it.id == event.id } }
        return Outcome.Success(Unit)
    }
}

package com.geowoof.core.data.repository

import com.geowoof.core.common.UuidV7
import com.geowoof.core.data.mapper.toBody
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.database.HealthEventDao
import com.geowoof.core.database.PetDao
import com.geowoof.core.model.HealthCompletion
import com.geowoof.core.model.HealthEvent
import com.geowoof.core.model.HealthReminderItem
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.network.PetsNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * The health of your pets: vaccines, deworming, appointments, medicines. Room is what the screens and the reminders
 * read, and writes go to the server first, as with the weights. The reminders of the phone follow Room by themselves.
 */
interface HealthRepository {
    fun events(petId: String): Flow<List<HealthEvent>>

    suspend fun event(eventId: String): HealthEvent?

    /** Everything pending with a reminder, of all your pets, again after each change: the alarms follow it. */
    fun pendingReminders(): Flow<List<HealthReminderItem>>

    suspend fun reminderItem(eventId: String): HealthReminderItem?

    suspend fun refresh(petId: String): Outcome<Unit>

    /** The health of all your pets, so that a phone where you just signed in reminds of it too. */
    suspend fun refreshAll(): Outcome<Unit>

    /** An identifier for a new event, which the server keeps: sending it twice does not create two. */
    fun newId(): String

    /** Creates it or replaces it whole. */
    suspend fun save(event: HealthEvent): Outcome<HealthEvent>

    /** If it repeats, the server makes the next one, counting from [doneOn]. */
    suspend fun complete(event: HealthEvent, doneOn: LocalDate): Outcome<HealthCompletion>

    suspend fun delete(event: HealthEvent): Outcome<Unit>
}

internal class DefaultHealthRepository @Inject constructor(
    private val network: PetsNetworkDataSource,
    private val events: HealthEventDao,
    private val pets: PetDao,
    private val sessions: SessionManager,
) : HealthRepository {
    override fun events(petId: String): Flow<List<HealthEvent>> =
        events.observeForPet(petId).map { list -> list.map { it.toModel() } }

    override suspend fun event(eventId: String): HealthEvent? = events.get(eventId)?.toModel()

    override fun pendingReminders(): Flow<List<HealthReminderItem>> =
        events.observePendingReminders().map { rows -> rows.map { HealthReminderItem(it.event.toModel(), it.petName) } }

    override suspend fun reminderItem(eventId: String): HealthReminderItem? =
        events.reminder(eventId)?.let { HealthReminderItem(it.event.toModel(), it.petName) }

    override suspend fun refresh(petId: String): Outcome<Unit> = when (val outcome = network.health(petId)) {
        is Outcome.Success -> {
            events.replaceForPet(petId, outcome.value.items.map { it.toEntity() })
            Outcome.Success(Unit)
        }

        is Outcome.Failure -> outcome
    }

    override suspend fun refreshAll(): Outcome<Unit> {
        val owner = (sessions.sessionState.value as? SessionState.SignedIn)?.user?.id ?: return Outcome.Success(Unit)
        val failures = pets.observeByOwner(owner).first().mapNotNull { pet -> refresh(pet.id) as? Outcome.Failure }
        return failures.firstOrNull() ?: Outcome.Success(Unit)
    }

    override fun newId(): String = UuidV7.generate(Instant.now().toEpochMilli())

    override suspend fun save(event: HealthEvent): Outcome<HealthEvent> =
        when (val outcome = network.putHealthEvent(event.petId, event.id, event.toBody())) {
            is Outcome.Success -> {
                val entity = outcome.value.toEntity()
                events.upsert(entity)
                Outcome.Success(entity.toModel())
            }

            is Outcome.Failure -> outcome
        }

    override suspend fun complete(event: HealthEvent, doneOn: LocalDate): Outcome<HealthCompletion> =
        when (val outcome = network.completeHealthEvent(event.petId, event.id, doneOn.toString())) {
            is Outcome.Success -> {
                val done = outcome.value.done.toEntity()
                val next = outcome.value.next?.toEntity()
                events.upsertAll(listOfNotNull(done, next))
                Outcome.Success(HealthCompletion(done.toModel(), next?.toModel()))
            }

            is Outcome.Failure -> outcome
        }

    override suspend fun delete(event: HealthEvent): Outcome<Unit> {
        val outcome = network.deleteHealthEvent(event.petId, event.id)
        if (outcome is Outcome.Success) events.delete(event.id)
        return outcome
    }
}

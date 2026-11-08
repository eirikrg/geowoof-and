package com.geowoof.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Something of the health of one of your pets. The days are epoch days and the time minutes of the day, local to the
 * owner. The enums are kept as their names, so that a value that this version does not know survives a refresh.
 */
@Entity(
    tableName = "health_events",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("petId", "dueOnEpochDay")],
)
data class HealthEventEntity(
    @PrimaryKey val id: String,
    val petId: String,
    val type: String,
    val title: String,
    val dueOnEpochDay: Long,
    val dueAtMinute: Int?,
    val doneOnEpochDay: Long?,
    val repeatEvery: Int?,
    val repeatUnit: String?,
    val repeatUntilEpochDay: Long?,
    val reminder: String,
    val notes: String?,
    val updatedAtEpochMs: Long,
)

/** What a reminder needs to say: the event and the name of its pet. */
data class HealthReminderRow(@Embedded val event: HealthEventEntity, val petName: String)

@Dao
abstract class HealthEventDao {
    @Query("SELECT * FROM health_events WHERE petId = :petId ORDER BY dueOnEpochDay, dueAtMinute, id")
    abstract fun observeForPet(petId: String): Flow<List<HealthEventEntity>>

    @Query("SELECT * FROM health_events WHERE id = :id")
    abstract suspend fun get(id: String): HealthEventEntity?

    @Query(
        "SELECT health_events.*, pets.name AS petName FROM health_events " +
            "JOIN pets ON pets.id = health_events.petId WHERE health_events.id = :id",
    )
    abstract suspend fun reminder(id: String): HealthReminderRow?

    /** Everything pending that has a reminder, of every pet, again each time any of it or the name of a pet changes. */
    @Query(
        "SELECT health_events.*, pets.name AS petName FROM health_events " +
            "JOIN pets ON pets.id = health_events.petId " +
            "WHERE doneOnEpochDay IS NULL AND reminder != 'None'",
    )
    abstract fun observePendingReminders(): Flow<List<HealthReminderRow>>

    @Upsert
    abstract suspend fun upsert(event: HealthEventEntity)

    @Upsert
    abstract suspend fun upsertAll(events: List<HealthEventEntity>)

    @Query("DELETE FROM health_events WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("DELETE FROM health_events WHERE petId = :petId")
    protected abstract suspend fun deleteForPet(petId: String)

    /** Makes the health of a pet match what the server returned. */
    @Transaction
    open suspend fun replaceForPet(petId: String, events: List<HealthEventEntity>) {
        deleteForPet(petId)
        upsertAll(events)
    }
}

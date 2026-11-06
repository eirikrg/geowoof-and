package com.geowoof.core.database

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.flow.Flow

// Room is the source of truth of what the screens show (docs/decisions/0009).

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val username: String?,
    val displayName: String,
    val bio: String?,
    val city: String?,
    val avatarAssetId: String?,
    val avatarThumbUrl: String?,
    val avatarMediumUrl: String?,
    val createdAtEpochMs: Long,
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
)

@Entity(tableName = "pets", indices = [Index("ownerId")])
data class PetEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val species: String,
    val speciesOther: String?,
    val breed: String?,
    val birthDateEpochDay: Long?,
    val sex: String,
    val size: String?,
    val color: String?,
    val neutered: Boolean,
    val microchip: String?,
    val avatarAssetId: String?,
    val avatarThumbUrl: String?,
    val avatarMediumUrl: String?,
    val bio: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "pet_weights",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("petId", "measuredOnEpochDay")],
)
data class PetWeightEntity(
    @PrimaryKey val id: String,
    val petId: String,
    val measuredOnEpochDay: Long,
    val weightKg: Double,
)

/** A single row. A walk needs it without a connection, before knowing whether to share anything. */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = ROW_ID,
    val liveVisibility: String?,
    val hideStartDistanceM: Int,
) {
    companion object {
        const val ROW_ID = 0
    }
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = ${UserSettingsEntity.ROW_ID}")
    fun observe(): Flow<UserSettingsEntity?>

    @Upsert
    suspend fun upsert(settings: UserSettingsEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: String): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
abstract class PetDao {
    @Query("SELECT * FROM pets WHERE ownerId = :ownerId ORDER BY createdAtEpochMs, id")
    abstract fun observeByOwner(ownerId: String): Flow<List<PetEntity>>

    @Query("SELECT * FROM pets WHERE id = :id")
    abstract fun observe(id: String): Flow<PetEntity?>

    @Upsert
    abstract suspend fun upsert(pet: PetEntity)

    @Upsert
    abstract suspend fun upsertAll(pets: List<PetEntity>)

    @Query("DELETE FROM pets WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("DELETE FROM pets WHERE ownerId = :ownerId AND id NOT IN (:keptIds)")
    protected abstract suspend fun deleteOthers(ownerId: String, keptIds: List<String>)

    /** Makes the pets of a person match what the server returned. */
    @Transaction
    open suspend fun replaceForOwner(ownerId: String, pets: List<PetEntity>) {
        deleteOthers(ownerId, pets.map { it.id })
        upsertAll(pets)
    }
}

@Dao
abstract class PetWeightDao {
    /** Oldest first, the order of the chart. */
    @Query("SELECT * FROM pet_weights WHERE petId = :petId ORDER BY measuredOnEpochDay")
    abstract fun observeForPet(petId: String): Flow<List<PetWeightEntity>>

    @Upsert
    abstract suspend fun upsert(entry: PetWeightEntity)

    @Upsert
    abstract suspend fun upsertAll(entries: List<PetWeightEntity>)

    @Query("DELETE FROM pet_weights WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("DELETE FROM pet_weights WHERE petId = :petId")
    protected abstract suspend fun deleteForPet(petId: String)

    /** Makes the history of a pet match what the server returned. */
    @Transaction
    open suspend fun replaceForPet(petId: String, entries: List<PetWeightEntity>) {
        deleteForPet(petId)
        upsertAll(entries)
    }
}

/** The friends now come from the server when a list opens. The requests come back with the next refresh. */
@DeleteTable(tableName = "friends")
@DeleteTable(tableName = "friend_requests")
class ReplaceFriendsWithFollows : AutoMigrationSpec

@Database(
    entities = [
        ProfileEntity::class,
        PetEntity::class,
        PetWeightEntity::class,
        ActiveWalkEntity::class,
        ActiveWalkPointEntity::class,
        WalkEntity::class,
        WalkPetEntity::class,
        WalkPhotoEntity::class,
        WalkTrackEntity::class,
        WalkTotalsEntity::class,
        WalkPageKeyEntity::class,
        FollowRequestEntity::class,
        BlockedUserEntity::class,
        FeedItemEntity::class,
        FeedPageKeyEntity::class,
        UserSettingsEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        InboxCountersEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = ReplaceFriendsWithFollows::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class GeoWoofDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun userSettingsDao(): UserSettingsDao

    abstract fun chatDao(): ChatDao

    abstract fun socialDao(): SocialDao

    abstract fun feedDao(): FeedDao

    abstract fun petDao(): PetDao

    abstract fun petWeightDao(): PetWeightDao

    abstract fun activeWalkDao(): ActiveWalkDao

    abstract fun walkDao(): WalkDao
}

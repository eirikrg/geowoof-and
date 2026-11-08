package com.geowoof.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// The walk being recorded. Each point is written the moment it is accepted, to survive the death of the process.

@Entity(tableName = "active_walk")
data class ActiveWalkEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val timeZone: String,
    /** The ids of the pets, separated by [ID_SEPARATOR]. */
    val petIds: String,
    val status: String,
    val currentSegment: Int,
)

@Entity(
    tableName = "active_walk_points",
    primaryKeys = ["walkId", "offsetMs"],
    foreignKeys = [
        ForeignKey(
            entity = ActiveWalkEntity::class,
            parentColumns = ["id"],
            childColumns = ["walkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActiveWalkPointEntity(
    val walkId: String,
    val segment: Int,
    val offsetMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
)

data class ActiveWalkWithPoints(
    @Embedded val walk: ActiveWalkEntity,
    @Relation(parentColumn = "id", entityColumn = "walkId") val points: List<ActiveWalkPointEntity>,
)

// Saved walks: the ones waiting to be uploaded and the cached ones of the server.

@Entity(tableName = "walks", indices = [Index("startedAtEpochMs", "id")])
data class WalkEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val timeZone: String,
    val visibility: String,
    val note: String?,
    val distanceM: Int,
    val durationS: Int,
    val movingTimeS: Int,
    val averageSpeedMps: Double,
    val paceSecondsPerKm: Int?,
    /** Polylines never contain [LINE_SEPARATOR]. */
    val previewPolylines: String,
    val updatedAtEpochMs: Long,
    val syncStatus: String,
    val syncErrorCode: String?,
)

@Entity(
    tableName = "walk_pets",
    primaryKeys = ["walkId", "petId"],
    foreignKeys = [
        ForeignKey(
            entity = WalkEntity::class,
            parentColumns = ["id"],
            childColumns = ["walkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("petId")],
)
data class WalkPetEntity(val walkId: String, val petId: String)

@Entity(
    tableName = "walk_photos",
    primaryKeys = ["walkId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = WalkEntity::class,
            parentColumns = ["id"],
            childColumns = ["walkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WalkPhotoEntity(
    val walkId: String,
    val position: Int,
    /** `null` until the photo has been uploaded. */
    val assetId: String?,
    val thumbUrl: String?,
    val mediumUrl: String?,
    /** The compressed copy on the device, until the walk has been uploaded. */
    val localPath: String?,
)

/** The full route, apart from the walk so that lists do not load it. */
@Entity(
    tableName = "walk_tracks",
    foreignKeys = [
        ForeignKey(
            entity = WalkEntity::class,
            parentColumns = ["id"],
            childColumns = ["walkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WalkTrackEntity(@PrimaryKey val walkId: String, val segmentsJson: String)

/** The totals of the server for a person ([MY_WALKS_KEY]) or a pet (its id). */
@Entity(tableName = "walk_totals")
data class WalkTotalsEntity(
    @PrimaryKey val listKey: String,
    val walkCount: Int,
    val distanceM: Int,
    val durationS: Int,
    val movingTimeS: Int,
    val weeklyStreak: Int,
)

/** Where each list of walks continues on the server. */
@Entity(tableName = "walk_page_keys")
data class WalkPageKeyEntity(@PrimaryKey val listKey: String, val nextCursor: String?)

const val ID_SEPARATOR = ","
const val LINE_SEPARATOR = "\n"

/** The list of the signed-in person; the list of a pet uses the id of the pet. */
const val MY_WALKS_KEY = "me"

data class WalkWithChildren(
    @Embedded val walk: WalkEntity,
    @Relation(parentColumn = "id", entityColumn = "walkId") val pets: List<WalkPetEntity>,
    @Relation(parentColumn = "id", entityColumn = "walkId") val photos: List<WalkPhotoEntity>,
)

/** Everything to store about a walk. Without [segmentsJson], the route already stored is kept. */
class WalkBundle(
    val walk: WalkEntity,
    val pets: List<WalkPetEntity>,
    val photos: List<WalkPhotoEntity>,
    val segmentsJson: String?,
)

@Dao
abstract class ActiveWalkDao {
    @Transaction
    @Query("SELECT * FROM active_walk LIMIT 1")
    abstract fun observe(): Flow<ActiveWalkWithPoints?>

    @Transaction
    @Query("SELECT * FROM active_walk LIMIT 1")
    abstract suspend fun current(): ActiveWalkWithPoints?

    @Upsert
    abstract suspend fun upsert(walk: ActiveWalkEntity)

    /** A repeated position is ignored: a point cannot appear twice in a route. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun appendPoint(point: ActiveWalkPointEntity)

    /** A position still on its way when the walk is paused or finished must not land after its end. */
    @Transaction
    open suspend fun appendPointIfRecording(point: ActiveWalkPointEntity) {
        if (statusOf(point.walkId) == RECORDING) appendPoint(point)
    }

    @Query("DELETE FROM active_walk")
    abstract suspend fun clear()

    @Query("SELECT status FROM active_walk WHERE id = :id")
    protected abstract suspend fun statusOf(id: String): String?

    private companion object {
        const val RECORDING = "Recording"
    }
}

@Dao
abstract class WalkDao {
    @Transaction
    @Query(
        "SELECT * FROM walks WHERE syncStatus != 'PendingDelete' ORDER BY startedAtEpochMs DESC, id DESC",
    )
    abstract fun pagingSource(): PagingSource<Int, WalkWithChildren>

    @Transaction
    @Query(
        "SELECT walks.* FROM walks INNER JOIN walk_pets ON walk_pets.walkId = walks.id " +
            "WHERE walk_pets.petId = :petId AND syncStatus != 'PendingDelete' " +
            "ORDER BY startedAtEpochMs DESC, id DESC",
    )
    abstract fun pagingSourceForPet(petId: String): PagingSource<Int, WalkWithChildren>

    @Transaction
    @Query("SELECT * FROM walks WHERE id = :id")
    abstract fun observe(id: String): Flow<WalkWithChildren?>

    @Transaction
    @Query("SELECT * FROM walks WHERE id = :id")
    abstract suspend fun get(id: String): WalkWithChildren?

    @Query("SELECT segmentsJson FROM walk_tracks WHERE walkId = :walkId")
    abstract fun observeTrack(walkId: String): Flow<String?>

    @Query("SELECT segmentsJson FROM walk_tracks WHERE walkId = :walkId")
    abstract suspend fun track(walkId: String): String?

    @Query("SELECT COUNT(*) FROM walks WHERE syncStatus != 'Synced'")
    abstract suspend fun countUnsynced(): Int

    @Query("UPDATE walks SET syncStatus = :status, syncErrorCode = :errorCode WHERE id = :id")
    abstract suspend fun setSyncStatus(id: String, status: String, errorCode: String?)

    @Query("UPDATE walk_photos SET assetId = :assetId WHERE walkId = :walkId AND position = :position")
    abstract suspend fun setPhotoAsset(walkId: String, position: Int, assetId: String)

    @Query("DELETE FROM walks WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** Saves a finished walk and ends the active one in a single step: a walk is never lost or kept twice. */
    @Transaction
    open suspend fun savePendingAndClearActive(walk: WalkBundle) {
        store(walk)
        clearActiveWalk()
    }

    /** What the server says about a walk. One waiting to be deleted stays hidden until that happens. */
    @Transaction
    open suspend fun storeFromServer(walks: List<WalkBundle>) {
        walks.forEach { bundle ->
            val isBeingDeleted = syncStatusOf(bundle.walk.id) == PENDING_DELETE
            if (!isBeingDeleted) store(bundle)
        }
    }

    /** The first page of the walks of the person replaces the cached ones. Pending walks are not touched. */
    @Transaction
    open suspend fun replaceFirstPage(walks: List<WalkBundle>, nextCursor: String?) {
        deleteSynced()
        storeFromServer(walks)
        storePageKey(WalkPageKeyEntity(MY_WALKS_KEY, nextCursor))
    }

    /** The first page of the walks of a pet: the rest of the cached walks may no longer include it. */
    @Transaction
    open suspend fun replaceFirstPageForPet(petId: String, walks: List<WalkBundle>, nextCursor: String?) {
        unlinkSynced(petId)
        storeFromServer(walks)
        storePageKey(WalkPageKeyEntity(petId, nextCursor))
    }

    @Transaction
    open suspend fun appendPage(listKey: String, walks: List<WalkBundle>, nextCursor: String?) {
        storeFromServer(walks)
        storePageKey(WalkPageKeyEntity(listKey, nextCursor))
    }

    @Query("SELECT * FROM walk_page_keys WHERE listKey = :listKey")
    abstract suspend fun pageKey(listKey: String): WalkPageKeyEntity?

    @Query("SELECT * FROM walk_totals WHERE listKey = :listKey")
    abstract fun observeTotals(listKey: String): Flow<WalkTotalsEntity?>

    @Upsert
    abstract suspend fun storeTotals(totals: WalkTotalsEntity)

    private suspend fun store(bundle: WalkBundle) {
        upsertWalk(bundle.walk)
        deletePets(bundle.walk.id)
        insertPets(bundle.pets)
        deletePhotos(bundle.walk.id)
        insertPhotos(bundle.photos)
        bundle.segmentsJson?.let { upsertTrack(WalkTrackEntity(bundle.walk.id, it)) }
    }

    @Query("SELECT syncStatus FROM walks WHERE id = :id")
    protected abstract suspend fun syncStatusOf(id: String): String?

    @Upsert
    protected abstract suspend fun upsertWalk(walk: WalkEntity)

    @Query("DELETE FROM walk_pets WHERE walkId = :walkId")
    protected abstract suspend fun deletePets(walkId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPets(pets: List<WalkPetEntity>)

    @Query("DELETE FROM walk_photos WHERE walkId = :walkId")
    protected abstract suspend fun deletePhotos(walkId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPhotos(photos: List<WalkPhotoEntity>)

    @Upsert
    protected abstract suspend fun upsertTrack(track: WalkTrackEntity)

    @Upsert
    protected abstract suspend fun storePageKey(key: WalkPageKeyEntity)

    @Query("DELETE FROM walks WHERE syncStatus = 'Synced'")
    protected abstract suspend fun deleteSynced()

    @Query(
        "DELETE FROM walk_pets WHERE petId = :petId AND walkId IN (SELECT id FROM walks WHERE syncStatus = 'Synced')",
    )
    protected abstract suspend fun unlinkSynced(petId: String)

    @Query("DELETE FROM active_walk")
    protected abstract suspend fun clearActiveWalk()

    private companion object {
        const val PENDING_DELETE = "PendingDelete"
    }
}

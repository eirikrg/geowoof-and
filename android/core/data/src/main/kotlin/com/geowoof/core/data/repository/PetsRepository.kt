package com.geowoof.core.data.repository

import com.geowoof.core.data.mapper.toBody
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.mapper.weightBody
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.database.PetDao
import com.geowoof.core.database.PetWeightDao
import com.geowoof.core.database.PetWeightEntity
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetDraft
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.network.PetsNetworkDataSource
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkWeightEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** Pets and their weight: read from Room, written online. */
interface PetsRepository {
    val myPets: Flow<List<Pet>>

    fun observePet(petId: String): Flow<Pet?>

    suspend fun refreshMyPets(): Outcome<Unit>

    suspend fun refreshPet(petId: String): Outcome<Unit>

    suspend fun create(draft: PetDraft): Outcome<Pet>

    suspend fun update(petId: String, draft: PetDraft): Outcome<Pet>

    suspend fun delete(petId: String): Outcome<Unit>

    /** Oldest first, the order of the chart. */
    fun observeWeights(petId: String): Flow<List<WeightEntry>>

    suspend fun refreshWeights(petId: String): Outcome<Unit>

    suspend fun addWeight(petId: String, measuredOn: LocalDate, weightKg: Double): Outcome<WeightEntry>

    suspend fun updateWeight(
        petId: String,
        entryId: String,
        measuredOn: LocalDate,
        weightKg: Double,
    ): Outcome<WeightEntry>

    suspend fun deleteWeight(petId: String, entryId: String): Outcome<Unit>
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultPetsRepository @Inject constructor(
    private val network: PetsNetworkDataSource,
    private val pets: PetDao,
    private val weights: PetWeightDao,
    private val sessions: SessionManager,
    private val changes: ContentChanges,
) : PetsRepository {
    private val currentUserId: Flow<String?> = sessions.sessionState
        .map { (it as? SessionState.SignedIn)?.user?.id }
        .distinctUntilChanged()

    override val myPets: Flow<List<Pet>> = currentUserId.flatMapLatest { userId ->
        if (userId ==
            null
        ) {
            flowOf(emptyList())
        } else {
            pets.observeByOwner(userId).map { list -> list.map { it.toModel() } }
        }
    }

    override fun observePet(petId: String): Flow<Pet?> = pets.observe(petId).map { it?.toModel() }

    override suspend fun refreshMyPets(): Outcome<Unit> = when (val outcome = network.myPets()) {
        is Outcome.Success -> {
            val found = outcome.value.items.map { it.toEntity() }
            currentOwner()?.let { pets.replaceForOwner(it, found) }
            Outcome.Success(Unit)
        }

        is Outcome.Failure -> outcome
    }

    override suspend fun refreshPet(petId: String): Outcome<Unit> = when (val outcome = network.pet(petId)) {
        is Outcome.Success -> {
            pets.upsert(outcome.value.toEntity())
            Outcome.Success(Unit)
        }

        is Outcome.Failure -> outcome
    }

    override suspend fun create(draft: PetDraft): Outcome<Pet> = store(network.createPet(draft.toBody()))

    override suspend fun update(petId: String, draft: PetDraft): Outcome<Pet> =
        store(network.updatePet(petId, draft.toBody()))

    override suspend fun delete(petId: String): Outcome<Unit> {
        val outcome = network.deletePet(petId)
        if (outcome is Outcome.Success) {
            pets.delete(petId)
            changes.notify(ContentChange.Pets)
        }

        return outcome
    }

    override fun observeWeights(petId: String): Flow<List<WeightEntry>> =
        weights.observeForPet(petId).map { list -> list.map { it.toModel() } }

    override suspend fun refreshWeights(petId: String): Outcome<Unit> {
        val entries = mutableListOf<PetWeightEntity>()
        var cursor: String? = null
        do {
            when (val page = network.weights(petId, PAGE_SIZE, cursor)) {
                is Outcome.Success -> {
                    entries += page.value.items.map { it.toEntity(petId) }
                    cursor = page.value.nextCursor
                }

                is Outcome.Failure -> return page
            }
        } while (cursor != null)

        weights.replaceForPet(petId, entries)
        return Outcome.Success(Unit)
    }

    override suspend fun addWeight(petId: String, measuredOn: LocalDate, weightKg: Double): Outcome<WeightEntry> =
        storeWeight(petId, network.addWeight(petId, weightBody(measuredOn, weightKg)))

    override suspend fun updateWeight(
        petId: String,
        entryId: String,
        measuredOn: LocalDate,
        weightKg: Double,
    ): Outcome<WeightEntry> = storeWeight(petId, network.updateWeight(petId, entryId, weightBody(measuredOn, weightKg)))

    override suspend fun deleteWeight(petId: String, entryId: String): Outcome<Unit> {
        val outcome = network.deleteWeight(petId, entryId)
        if (outcome is Outcome.Success) {
            weights.delete(entryId)
        }

        return outcome
    }

    private fun currentOwner(): String? = (sessions.sessionState.value as? SessionState.SignedIn)?.user?.id

    private suspend fun store(outcome: Outcome<NetworkPet>): Outcome<Pet> = when (outcome) {
        is Outcome.Success -> {
            val entity = outcome.value.toEntity()
            pets.upsert(entity)
            changes.notify(ContentChange.Pets)
            Outcome.Success(entity.toModel())
        }

        is Outcome.Failure -> outcome
    }

    private suspend fun storeWeight(petId: String, outcome: Outcome<NetworkWeightEntry>): Outcome<WeightEntry> =
        when (outcome) {
            is Outcome.Success -> {
                val entity = outcome.value.toEntity(petId)
                weights.upsert(entity)
                Outcome.Success(entity.toModel())
            }

            is Outcome.Failure -> outcome
        }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

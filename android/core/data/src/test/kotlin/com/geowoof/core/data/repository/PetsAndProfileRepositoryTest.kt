package com.geowoof.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.data.session.UserDataCleaner
import com.geowoof.core.database.GeoWoofDatabase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PetDraft
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.ProfileDraft
import com.geowoof.core.model.SessionState
import com.geowoof.core.network.PetsNetworkDataSource
import com.geowoof.core.network.ProfileNetworkDataSource
import com.geowoof.core.network.model.NetworkImage
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkProfile
import com.geowoof.core.network.model.NetworkWeightEntry
import com.geowoof.core.network.model.PetBody
import com.geowoof.core.network.model.ProfileBody
import com.geowoof.core.network.model.WeightBody
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The repositories against a real Room database, because it is what the screens read from. */
@RunWith(AndroidJUnit4::class)
class PetsAndProfileRepositoryTest {
    private lateinit var database: GeoWoofDatabase
    private val petsNetwork = FakePetsNetwork()
    private val profileNetwork = FakeProfileNetwork()
    private val ownerId = networkUser().id

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                GeoWoofDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun TestScope.signedInSessions(cleaner: UserDataCleaner = NoUserData): SessionManager {
        val sessions = SessionManager(InMemorySessionStorage(), cleaner, backgroundScope)
        runCurrent()
        sessions.start(networkSession())
        runCurrent()
        return sessions
    }

    private suspend fun TestScope.petsRepository() = DefaultPetsRepository(
        petsNetwork,
        database.petDao(),
        database.petWeightDao(),
        signedInSessions(),
        ContentChanges(),
    )

    private fun pet(id: String = "luna", species: String = "Dog") = NetworkPet(
        id = id,
        ownerId = ownerId,
        name = "Luna",
        species = species,
        birthDate = "2021-04-12",
        sex = "Female",
        size = "Medium",
        neutered = true,
        microchip = "941",
        avatar = NetworkImage("photo", "thumb", "medium"),
        createdAt = "2026-11-01T10:00:00Z",
        updatedAt = "2026-11-01T10:00:00Z",
    )

    private fun draft() = PetDraft(
        name = " Luna ",
        species = PetSpecies.Dog,
        speciesOther = "ignored",
        breed = "  ",
        birthDate = LocalDate.of(2021, 4, 12),
        sex = PetSex.Female,
        size = null,
        color = " Negra ",
        neutered = true,
        microchip = "",
        avatarAssetId = "photo",
        bio = null,
    )

    @Test
    fun refreshing_shows_the_pets_of_the_user_and_removes_the_ones_the_server_no_longer_has() = runTest {
        val repository = petsRepository()
        petsNetwork.pets = listOf(pet("luna"), pet("gone"))
        repository.refreshMyPets()
        assertEquals(setOf("luna", "gone"), repository.myPets.first().map { it.id }.toSet())

        petsNetwork.pets = listOf(pet("luna"))
        repository.refreshMyPets()

        val luna = repository.myPets.first().single()
        assertEquals("luna", luna.id)
        assertEquals(LocalDate.of(2021, 4, 12), luna.birthDate)
        assertEquals("941", luna.microchip)
        assertEquals("photo", luna.avatar?.assetId)
    }

    @Test
    fun a_failed_refresh_keeps_what_was_saved() = runTest {
        val repository = petsRepository()
        petsNetwork.pets = listOf(pet())
        repository.refreshMyPets()

        petsNetwork.failure = AppError.Network
        assertEquals(Outcome.Failure(AppError.Network), repository.refreshMyPets())

        assertEquals(1, repository.myPets.first().size)
    }

    @Test
    fun values_from_a_newer_api_fall_back_to_a_neutral_one() = runTest {
        val repository = petsRepository()
        petsNetwork.pets = listOf(pet(species = "Dragon"))

        repository.refreshMyPets()

        assertEquals(PetSpecies.Other, repository.myPets.first().single().species)
    }

    @Test
    fun a_new_pet_is_normalised_sent_and_stored() = runTest {
        val repository = petsRepository()

        val outcome = repository.create(draft())

        assertTrue(outcome is Outcome.Success)
        val sent = petsNetwork.sentBodies.single()
        assertEquals("Luna", sent.name)
        assertNull(sent.speciesOther)
        assertNull(sent.breed)
        assertEquals("Negra", sent.color)
        assertNull(sent.microchip)
        assertEquals("2021-04-12", sent.birthDate)
        assertEquals("luna", repository.observePet("luna").first()?.id)
    }

    @Test
    fun a_rejected_write_changes_nothing_on_the_device() = runTest {
        val repository = petsRepository()
        petsNetwork.failure = AppError.Network

        assertEquals(Outcome.Failure(AppError.Network), repository.create(draft()))
        assertNull(repository.observePet("luna").first())
    }

    @Test
    fun a_pet_is_only_removed_from_the_device_when_the_server_accepts() = runTest {
        val repository = petsRepository()
        repository.create(draft())

        petsNetwork.failure = AppError.Network
        repository.delete("luna")
        assertEquals("luna", repository.observePet("luna").first()?.id)

        petsNetwork.failure = null
        repository.delete("luna")
        assertNull(repository.observePet("luna").first())
    }

    @Test
    fun the_whole_weight_history_is_downloaded_page_by_page() = runTest {
        val repository = petsRepository()
        repository.create(draft())
        petsNetwork.weightPages["first"] = NetworkPage(
            listOf(NetworkWeightEntry("c", "2026-03-01", 19.0), NetworkWeightEntry("b", "2026-02-01", 18.5)),
            nextCursor = "next",
        )
        petsNetwork.weightPages["next"] =
            NetworkPage(listOf(NetworkWeightEntry("a", "2026-01-01", 18.0)), nextCursor = null)

        repository.refreshWeights("luna")

        val weights = repository.observeWeights("luna").first()
        assertEquals(listOf("a", "b", "c"), weights.map { it.id })
        assertEquals(18.0, weights.first().weightKg, 0.0)
        assertEquals(LocalDate.of(2026, 1, 1), weights.first().measuredOn)
    }

    @Test
    fun a_failed_page_keeps_the_previous_history() = runTest {
        val repository = petsRepository()
        repository.create(draft())
        repository.addWeight("luna", LocalDate.of(2026, 1, 1), 18.0)
        petsNetwork.weightPages["first"] =
            NetworkPage(listOf(NetworkWeightEntry("new", "2026-02-01", 19.0)), nextCursor = "next")
        petsNetwork.failingCursor = "next"

        val outcome = repository.refreshWeights("luna")

        assertEquals(Outcome.Failure(AppError.Network), outcome)
        assertEquals(1, repository.observeWeights("luna").first().size)
    }

    @Test
    fun weights_can_be_added_corrected_and_deleted() = runTest {
        val repository = petsRepository()
        repository.create(draft())

        val added = (repository.addWeight("luna", LocalDate.of(2026, 1, 1), 18.4) as Outcome.Success).value
        assertEquals(18.4, repository.observeWeights("luna").first().single().weightKg, 0.0)

        repository.updateWeight("luna", added.id, LocalDate.of(2026, 1, 2), 18.9)
        assertEquals(LocalDate.of(2026, 1, 2), repository.observeWeights("luna").first().single().measuredOn)

        repository.deleteWeight("luna", added.id)
        assertTrue(repository.observeWeights("luna").first().isEmpty())
    }

    @Test
    fun the_profile_is_downloaded_and_saved_locally() = runTest {
        val repository =
            DefaultProfileRepository(profileNetwork, database.profileDao(), signedInSessions(), ContentChanges())
        assertNull(repository.myProfile.first())

        repository.refresh()

        assertEquals("Ana", repository.myProfile.first()?.displayName)
    }

    @Test
    fun saving_the_profile_also_updates_the_name_of_the_session() = runTest {
        val sessions = signedInSessions()
        val repository = DefaultProfileRepository(profileNetwork, database.profileDao(), sessions, ContentChanges())

        val outcome = repository.save(ProfileDraft(" Ana López ", "  ", " Madrid ", "photo"))
        runCurrent()

        assertTrue(outcome is Outcome.Success)
        assertEquals(ProfileBody("Ana López", null, "Madrid", "photo"), profileNetwork.sent.single())
        assertEquals("Ana López", repository.myProfile.first()?.displayName)
        assertEquals("Ana López", (sessions.sessionState.value as SessionState.SignedIn).user.displayName)
    }

    @Test
    fun ending_the_session_removes_the_data_of_the_account() = runTest {
        var cleared = 0
        val sessions = signedInSessions(
            object : UserDataCleaner {
                override suspend fun clear() {
                    cleared++
                }
            },
        )

        sessions.clear()
        runCurrent()

        assertEquals(1, cleared)
        assertEquals(SessionState.SignedOut, sessions.sessionState.value)
    }

    private class FakePetsNetwork : PetsNetworkDataSource {
        var pets: List<NetworkPet> = emptyList()
        var failure: AppError? = null
        var failingCursor: String? = null
        val sentBodies = mutableListOf<PetBody>()
        val weightPages = mutableMapOf<String, NetworkPage<NetworkWeightEntry>>()
        private var nextWeight = 1

        private fun <T> result(value: () -> T): Outcome<T> =
            failure?.let { Outcome.Failure(it) } ?: Outcome.Success(value())

        private fun stored(body: PetBody) = NetworkPet(
            id = "luna",
            ownerId = networkUser().id,
            name = body.name,
            species = body.species,
            sex = body.sex,
            neutered = body.neutered,
            createdAt = "2026-11-01T10:00:00Z",
            updatedAt = "2026-11-01T10:00:00Z",
        )

        override suspend fun myPets() = result { NetworkPage(pets) }

        override suspend fun pet(id: String) = result { pets.first { it.id == id } }

        override suspend fun createPet(body: PetBody) = result {
            sentBodies += body
            stored(body)
        }

        override suspend fun updatePet(id: String, body: PetBody) = result { stored(body) }

        override suspend fun deletePet(id: String) = result { }

        override suspend fun weights(
            petId: String,
            limit: Int,
            cursor: String?,
        ): Outcome<NetworkPage<NetworkWeightEntry>> {
            if (cursor != null && cursor == failingCursor) return Outcome.Failure(AppError.Network)
            return Outcome.Success(weightPages[cursor ?: "first"] ?: NetworkPage(emptyList()))
        }

        override suspend fun addWeight(petId: String, body: WeightBody) =
            result { NetworkWeightEntry("w-${nextWeight++}", body.measuredOn, body.weightKg) }

        override suspend fun updateWeight(petId: String, weightId: String, body: WeightBody) =
            result { NetworkWeightEntry(weightId, body.measuredOn, body.weightKg) }

        override suspend fun deleteWeight(petId: String, weightId: String) = result { }
    }

    private class FakeProfileNetwork : ProfileNetworkDataSource {
        val sent = mutableListOf<ProfileBody>()

        private fun profile(displayName: String, city: String? = null) = NetworkProfile(
            id = networkUser().id,
            username = "ana",
            displayName = displayName,
            city = city,
            createdAt = "2026-11-01T10:00:00Z",
        )

        override suspend fun myProfile() = Outcome.Success(profile("Ana"))

        override suspend fun replaceMyProfile(body: ProfileBody): Outcome<NetworkProfile> {
            sent += body
            return Outcome.Success(profile(body.displayName, body.city))
        }
    }
}

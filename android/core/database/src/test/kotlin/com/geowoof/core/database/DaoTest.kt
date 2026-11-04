package com.geowoof.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var database: GeoWoofDatabase

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

    private fun pet(id: String, ownerId: String = "ana", createdAt: Long = 1, name: String = "Luna") = PetEntity(
        id = id,
        ownerId = ownerId,
        name = name,
        species = "Dog",
        speciesOther = null,
        breed = null,
        birthDateEpochDay = null,
        sex = "Female",
        size = null,
        color = null,
        neutered = false,
        microchip = null,
        avatarAssetId = null,
        avatarThumbUrl = null,
        avatarMediumUrl = null,
        bio = null,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = createdAt,
    )

    private fun weight(id: String, day: Long, petId: String = "luna") = PetWeightEntity(id, petId, day, 18.0)

    @Test
    fun pets_are_listed_per_owner_from_the_oldest() = runTest {
        val dao = database.petDao()
        dao.upsertAll(listOf(pet("b", createdAt = 2), pet("a", createdAt = 1), pet("other", ownerId = "bea")))

        assertEquals(listOf("a", "b"), dao.observeByOwner("ana").first().map { it.id })
    }

    @Test
    fun replacing_the_pets_of_a_person_removes_what_the_server_no_longer_has() = runTest {
        val dao = database.petDao()
        dao.upsertAll(listOf(pet("a"), pet("gone"), pet("other", ownerId = "bea")))

        dao.replaceForOwner("ana", listOf(pet("a", name = "Lunita"), pet("new")))

        assertEquals(setOf("a", "new"), dao.observeByOwner("ana").first().map { it.id }.toSet())
        assertEquals("Lunita", dao.observe("a").first()?.name)
        assertEquals("other", dao.observeByOwner("bea").first().single().id)
    }

    @Test
    fun an_empty_list_from_the_server_removes_every_pet_of_that_person() = runTest {
        val dao = database.petDao()
        dao.upsertAll(listOf(pet("a"), pet("b")))

        dao.replaceForOwner("ana", emptyList())

        assertTrue(dao.observeByOwner("ana").first().isEmpty())
    }

    @Test
    fun weights_are_ordered_for_the_chart_and_replaced_per_pet() = runTest {
        database.petDao().upsertAll(listOf(pet("luna"), pet("toby")))
        val dao = database.petWeightDao()
        dao.upsertAll(listOf(weight("late", 20), weight("early", 10), weight("toby", 5, petId = "toby")))

        assertEquals(listOf("early", "late"), dao.observeForPet("luna").first().map { it.id })

        dao.replaceForPet("luna", listOf(weight("new", 30)))

        assertEquals(listOf("new"), dao.observeForPet("luna").first().map { it.id })
        assertEquals(listOf("toby"), dao.observeForPet("toby").first().map { it.id })
    }

    @Test
    fun deleting_a_pet_deletes_its_weights() = runTest {
        database.petDao().upsert(pet("luna"))
        database.petWeightDao().upsert(weight("w", 10))

        database.petDao().delete("luna")

        assertNull(database.petDao().observe("luna").first())
        assertTrue(database.petWeightDao().observeForPet("luna").first().isEmpty())
    }

    @Test
    fun clearing_the_database_leaves_nothing_of_the_previous_account() = runTest {
        database.petDao().upsert(pet("luna"))
        database.profileDao().upsert(ProfileEntity("ana", "ana", "Ana", null, null, null, null, null, 1))

        database.clearAllTables()

        assertNull(database.profileDao().observe("ana").first())
        assertNull(database.petDao().observe("luna").first())
    }
}

package com.geowoof.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    // With an explicit driver: the default one rejects the paths of Windows.
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test"),
        driver = AndroidSQLiteDriver(),
        databaseClass = GeoWoofDatabase::class,
    )

    @Test
    fun an_existing_account_keeps_its_data_when_the_walks_arrive() {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO pets (id, ownerId, name, species, sex, neutered, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES ('pet-1', 'ana', 'Luna', 'Dog', 'Female', 1, 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(2).use { connection ->
            connection.prepare("SELECT name FROM pets").use { statement ->
                statement.step()
                assertEquals("Luna", statement.getText(0))
            }
            connection.prepare("SELECT COUNT(*) FROM walks").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
        }
    }

    @Test
    fun an_existing_account_keeps_its_walks_when_the_friends_arrive() {
        helper.createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO pets (id, ownerId, name, species, sex, neutered, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES ('pet-1', 'ana', 'Luna', 'Dog', 'Female', 1, 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(3).use { connection ->
            connection.prepare("SELECT name FROM pets").use { statement ->
                statement.step()
                assertEquals("Luna", statement.getText(0))
            }
            connection.prepare("SELECT COUNT(*) FROM friends").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
        }
    }
}

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

    @Test
    fun the_tables_of_friends_go_away_and_a_profile_stays_public() {
        helper.createDatabase(3).use { connection ->
            connection.execSQL(
                "INSERT INTO profiles (id, displayName, createdAtEpochMs) VALUES ('ana', 'Ana', 1)",
            )
            connection.execSQL(
                "INSERT INTO blocked_users (userId, username, displayName, sinceEpochMs) " +
                    "VALUES ('bea', 'bea', 'Bea', 1)",
            )
        }

        helper.runMigrationsAndValidate(4).use { connection ->
            connection.prepare("SELECT displayName, isPrivate FROM profiles").use { statement ->
                statement.step()
                assertEquals("Ana", statement.getText(0))
                assertEquals(0L, statement.getLong(1))
            }
            connection.prepare("SELECT COUNT(*) FROM blocked_users").use { statement ->
                statement.step()
                assertEquals(1L, statement.getLong(0))
            }
            connection.prepare("SELECT COUNT(*) FROM follow_requests").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
            connection.prepare(
                "SELECT COUNT(*) FROM sqlite_master WHERE name IN ('friends', 'friend_requests')",
            ).use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
        }
    }

    @Test
    fun the_feed_starts_empty_and_nothing_else_changes() {
        helper.createDatabase(4).use { connection ->
            connection.execSQL(
                "INSERT INTO profiles (id, displayName, createdAtEpochMs, isPrivate) VALUES ('ana', 'Ana', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(5).use { connection ->
            connection.prepare("SELECT isPrivate FROM profiles").use { statement ->
                statement.step()
                assertEquals(1L, statement.getLong(0))
            }
            connection.prepare("SELECT COUNT(*) FROM feed_items").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
        }
    }

    @Test
    fun the_settings_start_empty_so_the_app_asks_before_sharing() {
        helper.createDatabase(5).use { connection ->
            connection.execSQL(
                "INSERT INTO profiles (id, displayName, createdAtEpochMs, isPrivate) VALUES ('ana', 'Ana', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(6).use { connection ->
            connection.prepare("SELECT isPrivate FROM profiles").use { statement ->
                statement.step()
                assertEquals(1L, statement.getLong(0))
            }
            connection.prepare("SELECT COUNT(*) FROM user_settings").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
        }
    }

    @Test
    fun the_chat_starts_empty_and_nothing_else_changes() {
        helper.createDatabase(6).use { connection ->
            connection.execSQL(
                "INSERT INTO user_settings (id, liveVisibility, hideStartDistanceM) VALUES (0, 'Everyone', 500)",
            )
        }

        helper.runMigrationsAndValidate(7).use { connection ->
            connection.prepare("SELECT liveVisibility FROM user_settings").use { statement ->
                statement.step()
                assertEquals("Everyone", statement.getText(0))
            }
            listOf("conversations", "chat_messages", "inbox_counters").forEach { table ->
                connection.prepare("SELECT COUNT(*) FROM $table").use { statement ->
                    statement.step()
                    assertEquals(0L, statement.getLong(0))
                }
            }
        }
    }

    @Test
    fun the_recent_people_start_empty_and_nothing_else_changes() {
        helper.createDatabase(7).use { connection ->
            connection.execSQL(
                "INSERT INTO user_settings (id, liveVisibility, hideStartDistanceM) VALUES (0, 'Everyone', 500)",
            )
        }

        helper.runMigrationsAndValidate(8).use { connection ->
            connection.prepare("SELECT liveVisibility FROM user_settings").use { statement ->
                statement.step()
                assertEquals("Everyone", statement.getText(0))
            }
            connection.prepare("SELECT COUNT(*) FROM recent_people").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
        }
    }

    @Test
    fun the_health_of_the_pets_starts_empty_and_nothing_else_changes() {
        helper.createDatabase(8).use { connection ->
            connection.execSQL(
                "INSERT INTO pets (id, ownerId, name, species, sex, neutered, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES ('luna', 'ana', 'Luna', 'Dog', 'Female', 1, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(9).use { connection ->
            connection.prepare("SELECT name FROM pets").use { statement ->
                statement.step()
                assertEquals("Luna", statement.getText(0))
            }
            connection.prepare("SELECT COUNT(*) FROM health_events").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }
        }
    }
}

package com.geowoof.core.data.repository

import com.geowoof.core.datastore.AppearanceStorage
import com.geowoof.core.datastore.StoredAppearance
import com.geowoof.core.model.Appearance
import com.geowoof.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceRepositoryTest {
    private class FakeStorage(initial: StoredAppearance = StoredAppearance()) : AppearanceStorage {
        override val appearance = MutableStateFlow(initial)

        override suspend fun update(transform: (StoredAppearance) -> StoredAppearance) {
            appearance.value = transform(appearance.value)
        }
    }

    @Test
    fun a_theme_this_version_does_not_know_follows_the_system() = runTest {
        val repository = DefaultAppearanceRepository(FakeStorage(StoredAppearance(themeMode = "Sepia")))

        assertEquals(ThemeMode.System, repository.appearance.first().themeMode)
    }

    @Test
    fun each_preference_changes_without_touching_the_other() = runTest {
        val repository = DefaultAppearanceRepository(FakeStorage())

        repository.setThemeMode(ThemeMode.Dark)
        repository.setUseWallpaperColors(true)
        assertEquals(Appearance(ThemeMode.Dark, useWallpaperColors = true), repository.appearance.first())

        repository.setThemeMode(ThemeMode.Light)
        assertEquals(Appearance(ThemeMode.Light, useWallpaperColors = true), repository.appearance.first())
    }
}

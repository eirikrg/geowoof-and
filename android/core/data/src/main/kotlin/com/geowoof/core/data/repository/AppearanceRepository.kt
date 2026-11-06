package com.geowoof.core.data.repository

import com.geowoof.core.datastore.AppearanceStorage
import com.geowoof.core.datastore.StoredAppearance
import com.geowoof.core.model.Appearance
import com.geowoof.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** How the app looks on this device. It stays when the session ends, because it does not belong to an account. */
interface AppearanceRepository {
    val appearance: Flow<Appearance>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setUseWallpaperColors(use: Boolean)
}

internal class DefaultAppearanceRepository @Inject constructor(private val storage: AppearanceStorage) :
    AppearanceRepository {
    override val appearance: Flow<Appearance> = storage.appearance.map { it.toModel() }

    override suspend fun setThemeMode(mode: ThemeMode) = storage.update { it.copy(themeMode = mode.name) }

    override suspend fun setUseWallpaperColors(use: Boolean) = storage.update { it.copy(useWallpaperColors = use) }

    private fun StoredAppearance.toModel() = Appearance(
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.System,
        useWallpaperColors = useWallpaperColors,
    )
}

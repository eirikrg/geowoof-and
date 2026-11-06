package com.geowoof.core.testing

import com.geowoof.core.data.repository.AppearanceRepository
import com.geowoof.core.model.Appearance
import com.geowoof.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppearanceRepository : AppearanceRepository {
    override val appearance = MutableStateFlow(Appearance())

    override suspend fun setThemeMode(mode: ThemeMode) {
        appearance.value = appearance.value.copy(themeMode = mode)
    }

    override suspend fun setUseWallpaperColors(use: Boolean) {
        appearance.value = appearance.value.copy(useWallpaperColors = use)
    }
}

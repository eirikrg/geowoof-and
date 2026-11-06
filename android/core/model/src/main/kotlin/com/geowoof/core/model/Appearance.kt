package com.geowoof.core.model

enum class ThemeMode {
    System,
    Light,
    Dark,
}

/** How the app looks on this device: it does not travel with the account. */
data class Appearance(val themeMode: ThemeMode = ThemeMode.System, val useWallpaperColors: Boolean = false)

package com.geowoof.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.geowoof.app.ui.GeoWoofApp
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity; every screen is a Compose destination. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeoWoofTheme {
                GeoWoofApp()
            }
        }
    }
}

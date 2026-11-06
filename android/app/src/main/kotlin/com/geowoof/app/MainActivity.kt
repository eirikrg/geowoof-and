package com.geowoof.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geowoof.app.ui.GeoWoofApp
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.ThemeMode
import com.geowoof.core.ui.offerToSavePassword
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Single activity; every screen is a Compose destination. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var passwordSaveRequests: PasswordSaveRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            val state = viewModel.uiState.value
            state.sessionState == SessionState.Loading || state.appearance == null
        }
        enableEdgeToEdge()

        // Coming back from its saved state, the activity gets the same intent, and that link was already opened.
        val link = intent.takeLink()
        if (savedInstanceState == null) link?.let(viewModel::onLink)

        // Credential Manager needs an activity, and the form that asked for the save is already gone.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                passwordSaveRequests.pending.collect { offerToSavePassword(this@MainActivity, it.email, it.password) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.onAppVisible() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.listenToChat() }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isDark = when (uiState.appearance?.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                else -> isSystemInDarkTheme()
            }
            // The app can be dark while the system is not, and the icons of the bars have to follow it.
            DisposableEffect(isDark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            GeoWoofTheme(darkTheme = isDark, dynamicColor = uiState.appearance?.useWallpaperColors == true) {
                GeoWoofApp(
                    uiState = uiState,
                    onFinishFirstPetStep = viewModel::onFirstPetStepDone,
                    onConsumeLink = viewModel::onLinkOpened,
                    onAskedForNotifications = viewModel::onAskedForNotifications,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.takeLink()?.let(viewModel::onLink)
    }

    /** The NavController would also open it by itself, restarting the activity for the NEW_TASK of a notification. */
    private fun Intent.takeLink(): String? = dataString.also { data = null }
}

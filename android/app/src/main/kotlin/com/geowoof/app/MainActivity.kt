package com.geowoof.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.sessionState == SessionState.Loading }
        enableEdgeToEdge()

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
            GeoWoofTheme {
                GeoWoofApp(uiState = uiState, onFinishFirstPetStep = viewModel::onFirstPetStepDone)
            }
        }
    }
}

package com.geowoof.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.app.AppUiState
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.TestUsers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var uiState by mutableStateOf(AppUiState())

    private fun showGate() {
        composeRule.setContent {
            SessionGate(
                uiState = uiState,
                signedOut = { Text("welcome") },
                onboarding = { user -> Text(if (user.needsOnboarding) "onboarding" else "first pet") },
                main = { Text("main shell") },
            )
        }
    }

    @Test
    fun shows_nothing_while_the_session_loads() {
        showGate()

        composeRule.onNodeWithText("welcome").assertDoesNotExist()
        composeRule.onNodeWithText("main shell").assertDoesNotExist()
    }

    @Test
    fun follows_the_session_from_sign_up_to_the_main_shell_and_back() {
        showGate()

        uiState = AppUiState(SessionState.SignedOut)
        composeRule.onNodeWithText("welcome").assertIsDisplayed()

        uiState = AppUiState(SessionState.SignedIn(TestUsers.user(username = null)))
        composeRule.onNodeWithText("onboarding").assertIsDisplayed()

        // With the username chosen, the onboarding stays for its last step.
        val onboarded = SessionState.SignedIn(TestUsers.user(username = "ana"))
        uiState = AppUiState(onboarded, offerFirstPet = true)
        composeRule.onNodeWithText("first pet").assertIsDisplayed()

        uiState = AppUiState(onboarded)
        composeRule.onNodeWithText("main shell").assertIsDisplayed()

        uiState = AppUiState(SessionState.SignedOut)
        composeRule.onNodeWithText("welcome").assertIsDisplayed()
        composeRule.onNodeWithText("main shell").assertDoesNotExist()
    }
}

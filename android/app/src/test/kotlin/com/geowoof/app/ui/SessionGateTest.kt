package com.geowoof.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.TestUsers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var sessionState by mutableStateOf<SessionState>(SessionState.Loading)

    private fun showGate() {
        composeRule.setContent {
            SessionGate(
                sessionState = sessionState,
                signedOut = { Text("welcome") },
                onboarding = { user -> Text(if (user.emailVerified) "choose username" else "verify email") },
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

        sessionState = SessionState.SignedOut
        composeRule.onNodeWithText("welcome").assertIsDisplayed()

        sessionState = SessionState.SignedIn(TestUsers.user(username = null, emailVerified = false))
        composeRule.onNodeWithText("verify email").assertIsDisplayed()

        sessionState = SessionState.SignedIn(TestUsers.user(username = null, emailVerified = true))
        composeRule.onNodeWithText("choose username").assertIsDisplayed()

        sessionState = SessionState.SignedIn(TestUsers.user(username = "ana"))
        composeRule.onNodeWithText("main shell").assertIsDisplayed()

        sessionState = SessionState.SignedOut
        composeRule.onNodeWithText("welcome").assertIsDisplayed()
        composeRule.onNodeWithText("main shell").assertDoesNotExist()
    }
}

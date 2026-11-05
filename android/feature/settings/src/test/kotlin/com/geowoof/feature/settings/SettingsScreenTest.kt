package com.geowoof.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.User
import com.geowoof.core.testing.TestUsers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<String>()

    private fun showSettings(user: User) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                SettingsContent(
                    uiState = SettingsUiState(user = user),
                    onBack = { actions += "back" },
                    onVerifyEmail = { actions += "verify" },
                    onChangePassword = { actions += "password" },
                    onOpenBlockedUsers = { actions += "blocked" },
                    onPrivateChange = { actions += "private:$it" },
                    onDismissError = {},
                    onSignOut = { actions += "signOut" },
                    onConfirmSignOut = { actions += "confirmSignOut" },
                    onDismissSignOut = { actions += "dismissSignOut" },
                    onDeleteAccount = { actions += "delete" },
                )
            }
        }
    }

    @Test
    fun shows_the_account_and_offers_to_verify_the_email() {
        showSettings(TestUsers.user(emailVerified = false))

        composeRule.onNodeWithText("@ana").assertIsDisplayed()
        composeRule.onNodeWithText("ana@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Not verified").assertIsDisplayed()
        composeRule.onNodeWithText("Verify").performClick()

        assertEquals(listOf("verify"), actions)
    }

    @Test
    fun google_accounts_can_create_a_password() {
        showSettings(TestUsers.user(hasPassword = false))

        composeRule.onNodeWithText("Verified").assertIsDisplayed()
        composeRule.onNodeWithText("Create a password").performClick()

        assertEquals(listOf("password"), actions)
    }

    @Test
    fun every_account_action_is_reachable() {
        showSettings(TestUsers.user())

        composeRule.onNodeWithText("Change password").performScrollTo().performClick()
        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete account").performScrollTo().performClick()

        assertEquals(listOf("password", "signOut", "delete"), actions)
    }

    @Test
    fun the_profile_can_become_private_from_its_row() {
        showSettings(TestUsers.user())

        composeRule.onNode(hasText("Private profile")).performScrollTo().performClick()

        assertEquals(listOf("private:true"), actions)
    }

    @Test
    fun a_private_profile_can_become_public() {
        showSettings(TestUsers.user(isPrivate = true))

        composeRule.onNode(hasText("Private profile")).performScrollTo().performClick()

        assertEquals(listOf("private:false"), actions)
    }
}

package com.geowoof.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.User
import com.geowoof.core.model.UserSettings
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

    private fun showSettings(user: User, liveSettings: UserSettings = UserSettings()) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                SettingsContent(
                    uiState = SettingsUiState(user = user, settings = liveSettings),
                    onBack = { actions += "back" },
                    onVerifyEmail = { actions += "verify" },
                    onChangePassword = { actions += "password" },
                    onOpenBlockedUsers = { actions += "blocked" },
                    onPrivateChange = { actions += "private:$it" },
                    onLiveVisibilityChange = { actions += "live:$it" },
                    onHideStartChange = { actions += "hide:$it" },
                    onThemeModeChange = { actions += "theme:$it" },
                    onWallpaperColorsChange = { actions += "wallpaper:$it" },
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

    @Test
    fun without_having_chosen_it_says_nothing_is_shared() {
        showSettings(TestUsers.user())

        composeRule.onNodeWithText(
            "You have not chosen yet: your position is not shared",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_visibility_shows_what_is_chosen_and_changes_at_once() {
        showSettings(TestUsers.user(), UserSettings(LiveVisibility.FriendsOnly))

        composeRule.onNodeWithText("Who can see you while you walk").performScrollTo().performClick()
        composeRule.onNode(hasText("Only friends") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("Nobody") and isSelectable()).assertIsNotSelected()
        composeRule.onNode(hasText("Nobody") and isSelectable()).performClick()

        assertEquals(listOf("live:Nobody"), actions)
        composeRule.onNode(hasText("Nobody") and isSelectable()).assertDoesNotExist()
    }

    @Test
    fun the_hidden_start_offers_the_three_distances() {
        showSettings(TestUsers.user())

        composeRule.onNodeWithText("Hide the start of your walks").performScrollTo().performClick()
        composeRule.onNode(hasText("200 m") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("Do not hide it") and isSelectable()).performClick()

        assertEquals(listOf("hide:0"), actions)
    }

    @Test
    fun a_choice_can_be_left_as_it_was() {
        showSettings(TestUsers.user(), UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 500))

        composeRule.onNodeWithText("500 m").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<String>(), actions)
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }
}

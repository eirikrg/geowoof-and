package com.geowoof.core.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ReportReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                var selected by remember { mutableStateOf<ReportReason?>(null) }
                ReportDialog(
                    title = "Report",
                    selected = selected,
                    details = TextFieldState(),
                    onSelect = { selected = it },
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun it_cannot_be_sent_without_a_reason() {
        show()

        composeRule.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun choosing_a_reason_lets_it_be_sent() {
        show()

        composeRule.onNodeWithText("Spam").performClick()

        composeRule.onNodeWithText("Send").assertIsEnabled()
    }

    @Test
    fun each_reason_has_room_for_a_finger() {
        show()

        listOf("Spam", "Harassment", "Something else").forEach {
            composeRule.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
    }
}

package com.geowoof.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose does not let tests fake AutofillManager (its constructor is internal), so whether the
 * second save prompt disappears can only be checked on a device. This covers that submitting always
 * works, with or without an autofill service.
 */
@RunWith(AndroidJUnit4::class)
class CredentialSubmitTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var submissions = 0

    @Test
    fun submits_with_the_autofill_manager_of_the_device() {
        lateinit var submit: () -> Unit
        composeRule.setContent {
            submit = rememberCredentialSubmit { submissions++ }
        }

        composeRule.runOnIdle { submit() }

        assertEquals(1, submissions)
    }

    @Test
    fun submits_on_devices_without_autofill() {
        lateinit var submit: () -> Unit
        composeRule.setContent {
            CompositionLocalProvider(LocalAutofillManager provides null) {
                submit = rememberCredentialSubmit { submissions++ }
            }
        }

        composeRule.runOnIdle { submit() }

        assertEquals(1, submissions)
    }
}

package com.geowoof.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalAutofillManager

/**
 * Submit action for forms with a password field. It first ends the autofill session: otherwise
 * the autofill service also offers to save the password when the form closes, on top of the offer
 * from Credential Manager ([offerToSavePassword]), which only comes once the API has accepted it.
 * Autofill can still fill in saved passwords: a new session starts when a field gets the focus.
 */
@Composable
fun rememberCredentialSubmit(onSubmit: () -> Unit): () -> Unit {
    val autofillManager = LocalAutofillManager.current
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    return remember(autofillManager) {
        {
            autofillManager?.cancel()
            currentOnSubmit()
        }
    }
}

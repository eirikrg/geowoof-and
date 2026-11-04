package com.geowoof.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** Field for the 6-digit codes sent by email. Pasted text keeps only its digits. */
@Composable
fun VerificationCodeField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.core_ui_code_label)) },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        inputTransformation = DigitsOnly.then(InputTransformation.maxLength(CODE_LENGTH)),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        onKeyboardAction = onKeyboardAction,
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

private object DigitsOnly : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val digits = asCharSequence().filter(Char::isDigit)
        if (digits.length != length) {
            replace(0, length, digits)
        }
    }
}

private const val CODE_LENGTH = 6

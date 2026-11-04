package com.geowoof.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.R
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/**
 * Password field with a show/hide toggle. Its [contentType] lets autofill services (such as Google
 * Password Manager) fill in and offer to save the password.
 */
@Composable
fun PasswordField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    contentType: ContentType = ContentType.Password,
    imeAction: ImeAction = ImeAction.Done,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentType = contentType },
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        onKeyboardAction = onKeyboardAction,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(if (visible) GeoWoofIcons.VisibilityOff else GeoWoofIcons.Visibility),
                    contentDescription = stringResource(
                        if (visible) {
                            R.string.core_designsystem_hide_password
                        } else {
                            R.string.core_designsystem_show_password
                        },
                    ),
                )
            }
        },
    )
}

/** Single-line text field for forms. [contentType] enables autofill (email, name…). */
@Composable
fun FormTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    contentType: ContentType? = null,
    prefix: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    OutlinedTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier),
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

/** Primary action button that shows a progress indicator (and ignores taps) while [loading]. */
@Composable
fun ProgressButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    val loadingDescription = stringResource(R.string.core_designsystem_loading)
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinButtonHeight)
            .semantics { if (loading) stateDescription = loadingDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ProgressSize),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text)
            }
        }
    }
}

/** Keyboard options for email fields. */
val EmailKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)

private val MinButtonHeight = 48.dp
private val ProgressSize = 20.dp

@PreviewLightDark
@Composable
private fun FormComponentsPreview() {
    GeoWoofTheme(dynamicColor = false) {
        Surface {
            Column {
                FormTextField(state = rememberTextFieldState("ana@geowoof.dev"), label = "Email")
                PasswordField(state = rememberTextFieldState("Password1"), label = "Password")
                ProgressButton(text = "Sign in", loading = false, onClick = {})
                ProgressButton(text = "Sign in", loading = true, onClick = {})
            }
        }
    }
}

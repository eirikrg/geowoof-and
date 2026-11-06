package com.geowoof.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/**
 * The 6-digit code of an email, one digit per box. It takes the focus when it appears, because it is
 * the only thing to do on its screen. For the keyboard and TalkBack it is one ordinary text field; the
 * boxes only paint what it holds.
 */
@Composable
fun VerificationCodeField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    val label = stringResource(R.string.core_ui_code_label)
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Column(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalTextSelectionColors provides InvisibleSelection) {
            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label },
                inputTransformation = DigitsOnly.then(InputTransformation.maxLength(CODE_LENGTH)),
                textStyle = TextStyle(color = Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = onKeyboardAction,
                lineLimits = TextFieldLineLimits.SingleLine,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(Color.Transparent),
                decorator = { innerTextField ->
                    Box {
                        CodeBoxes(text = state.text, isFocused = isFocused, isError = isError)
                        Box(Modifier.matchParentSize()) { innerTextField() }
                    }
                },
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                modifier = Modifier.padding(
                    start = GeoWoofTheme.spacing.large,
                    top = GeoWoofTheme.spacing.extraSmall,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeBoxes(text: CharSequence, isFocused: Boolean, isError: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
    ) {
        repeat(CODE_LENGTH) { index ->
            CodeBox(
                digit = text.getOrNull(index),
                isNext = isFocused && index == text.length.coerceAtMost(CODE_LENGTH - 1),
                isError = isError,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CodeBox(digit: Char?, isNext: Boolean, isError: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .heightIn(min = BoxHeight)
            .background(colors.surfaceContainerLow, shape)
            .border(
                width = if (isNext) NextBorder else BoxBorder,
                color = when {
                    isError -> colors.error
                    isNext -> colors.primary
                    digit != null -> colors.outline
                    else -> colors.outlineVariant
                },
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = digit?.toString().orEmpty(), style = MaterialTheme.typography.headlineSmall)
    }
}

private object DigitsOnly : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val digits = asCharSequence().filter(Char::isDigit)
        if (digits.length != length) {
            replace(0, length, digits)
        }
    }
}

private val InvisibleSelection =
    TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)
private val BoxHeight = 60.dp
private val BoxBorder = 1.dp
private val NextBorder = 2.dp
private const val CODE_LENGTH = 6

@PreviewLightDark
@PreviewFontScale
@Composable
private fun VerificationCodeFieldPreview() {
    GeoWoofTheme(dynamicColor = false) {
        Surface { VerificationCodeField(state = rememberTextFieldState("1234")) }
    }
}

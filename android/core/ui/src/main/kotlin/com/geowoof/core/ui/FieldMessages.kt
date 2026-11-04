package com.geowoof.core.ui

import androidx.annotation.StringRes
import com.geowoof.core.model.CodeIssue
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.PasswordIssue
import com.geowoof.core.model.UsernameIssue

// Localised explanations of invalid form fields (supporting text under the field).

@StringRes
fun EmailIssue.messageRes(): Int = when (this) {
    EmailIssue.Empty -> R.string.core_ui_field_email_empty
    EmailIssue.Invalid -> R.string.core_ui_field_email_invalid
}

@StringRes
fun PasswordIssue.messageRes(): Int = when (this) {
    PasswordIssue.TooShort -> R.string.core_ui_field_password_short
    PasswordIssue.TooLong -> R.string.core_ui_field_password_long
    PasswordIssue.MissingLetter -> R.string.core_ui_field_password_letter
    PasswordIssue.MissingDigit -> R.string.core_ui_field_password_digit
}

@StringRes
fun DisplayNameIssue.messageRes(): Int = when (this) {
    DisplayNameIssue.Empty -> R.string.core_ui_field_name_empty
    DisplayNameIssue.TooLong -> R.string.core_ui_field_name_long
}

@StringRes
fun UsernameIssue.messageRes(): Int = when (this) {
    UsernameIssue.TooShort -> R.string.core_ui_field_username_short
    UsernameIssue.TooLong -> R.string.core_ui_field_username_long
    UsernameIssue.MustStartWithLetter -> R.string.core_ui_field_username_start
    UsernameIssue.InvalidCharacters -> R.string.core_ui_field_username_characters
    UsernameIssue.InvalidDots -> R.string.core_ui_field_username_dots
}

@StringRes
fun CodeIssue.messageRes(): Int = when (this) {
    CodeIssue.Incomplete -> R.string.core_ui_field_code_incomplete
}

/** Supporting text under new-password fields, before the user makes a mistake. */
@StringRes
val PasswordRulesHint: Int = R.string.core_ui_field_password_hint

/** Required-field message for passwords that are only checked for presence (sign-in, re-authentication). */
@StringRes
val PasswordRequired: Int = R.string.core_ui_field_password_empty

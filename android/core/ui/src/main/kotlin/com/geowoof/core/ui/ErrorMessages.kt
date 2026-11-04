package com.geowoof.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.geowoof.core.model.AppError

/** A localised explanation of an error, for snackbars and banners. Unknown codes get a generic text. */
@Composable
fun AppError.userMessage(): String = stringResource(messageRes())

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.core_ui_error_network

    AppError.SessionExpired -> R.string.core_ui_error_session_expired

    AppError.CredentialUnavailable -> R.string.core_ui_error_credential_unavailable

    is AppError.Unexpected -> R.string.core_ui_error_unexpected

    is AppError.Api -> API_MESSAGES[code] ?: when (status) {
        HTTP_TOO_MANY_REQUESTS -> R.string.core_ui_error_rate_limited
        in SERVER_ERRORS -> R.string.core_ui_error_server
        else -> R.string.core_ui_error_unexpected
    }
}

private const val HTTP_TOO_MANY_REQUESTS = 429
private val SERVER_ERRORS = 500..599

private val API_MESSAGES = mapOf(
    "auth.invalid_credentials" to R.string.core_ui_error_invalid_credentials,
    "auth.locked_out" to R.string.core_ui_error_locked_out,
    "auth.email_taken" to R.string.core_ui_error_email_taken,
    "auth.google_disabled" to R.string.core_ui_error_google_disabled,
    "auth.google_token_invalid" to R.string.core_ui_error_google_failed,
    "auth.google_email_unverified" to R.string.core_ui_error_google_email_unverified,
    "auth.code_invalid" to R.string.core_ui_error_code_invalid,
    "auth.code_expired" to R.string.core_ui_error_code_expired,
    "auth.code_attempts_exceeded" to R.string.core_ui_error_code_attempts,
    "auth.code_cooldown" to R.string.core_ui_error_code_cooldown,
    "auth.email_already_verified" to R.string.core_ui_error_email_already_verified,
    "auth.password_incorrect" to R.string.core_ui_error_password_incorrect,
    "auth.reauthentication_required" to R.string.core_ui_error_reauthentication_required,
    "identity.username_taken" to R.string.core_ui_error_username_taken,
    "identity.username_reserved" to R.string.core_ui_error_username_reserved,
    "email.delivery_failed" to R.string.core_ui_error_email_delivery,
    "validation.failed" to R.string.core_ui_error_validation,
    "rate_limit.exceeded" to R.string.core_ui_error_rate_limited,
)

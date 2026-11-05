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
    "media.invalid_image" to R.string.core_ui_error_image_invalid,
    "media.image_too_large" to R.string.core_ui_error_image_too_large,
    "media.processing_failed" to R.string.core_ui_error_image_failed,
    "media.upload_failed" to R.string.core_ui_error_image_failed,
    "media.upload_mismatch" to R.string.core_ui_error_image_failed,
    "media.upload_missing" to R.string.core_ui_error_image_failed,
    "media.asset_rejected" to R.string.core_ui_error_image_invalid,
    "media.quota_exceeded" to R.string.core_ui_error_image_quota,
    "pets.not_found" to R.string.core_ui_error_pet_not_found,
    "walks.not_found" to R.string.core_ui_error_walk_not_found,
    "identity.user_not_found" to R.string.core_ui_error_user_not_found,
    "auth.forbidden" to R.string.core_ui_error_email_required,
    "social.request_not_found" to R.string.core_ui_error_request_gone,
    "social.request_not_pending" to R.string.core_ui_error_request_gone,
    "social.lists_private" to R.string.core_ui_error_lists_private,
    "identity.profile_private" to R.string.core_ui_error_profile_private,
    "social.cannot_target_self" to R.string.core_ui_error_cannot_target_self,
    "social.post_not_found" to R.string.core_ui_error_post_not_found,
    "social.comment_not_found" to R.string.core_ui_error_post_not_found,
    "social.pet_not_owned" to R.string.core_ui_error_post_pet_not_owned,
    "social.cannot_repost" to R.string.core_ui_error_cannot_repost,
    "walks.pet_not_owned" to R.string.core_ui_error_walk_pet_not_owned,
    "walks.invalid_time_range" to R.string.core_ui_error_walk_invalid,
    "walks.too_long" to R.string.core_ui_error_walk_too_long,
    "walks.invalid_track" to R.string.core_ui_error_walk_invalid,
    "walks.too_many_points" to R.string.core_ui_error_walk_too_long,
    "walks.route_immutable" to R.string.core_ui_error_walk_invalid,
    "pets.limit_reached" to R.string.core_ui_error_pet_limit,
    "pets.weight_date_taken" to R.string.core_ui_error_weight_date_taken,
    "email.delivery_failed" to R.string.core_ui_error_email_delivery,
    "validation.failed" to R.string.core_ui_error_validation,
    "rate_limit.exceeded" to R.string.core_ui_error_rate_limited,
)

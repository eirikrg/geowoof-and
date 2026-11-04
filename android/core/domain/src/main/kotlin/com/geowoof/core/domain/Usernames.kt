package com.geowoof.core.domain

import com.geowoof.core.data.repository.AccountRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UsernameIssue
import com.geowoof.core.model.UsernameUnavailableReason
import javax.inject.Inject

/** Local username rules (same as the API): 3–30 of `a-z 0-9 _ .`, starting with a letter. */
class ValidateUsernameUseCase @Inject constructor() {
    fun normalize(raw: String): String = raw.trim().lowercase().removePrefix("@")

    operator fun invoke(raw: String): UsernameIssue? {
        val username = normalize(raw)
        return when {
            username.length < MIN_LENGTH -> UsernameIssue.TooShort

            username.length > MAX_LENGTH -> UsernameIssue.TooLong

            !username.first().isAsciiLowercaseLetter() -> UsernameIssue.MustStartWithLetter

            !username.all {
                it.isAsciiLowercaseLetter() || it.isDigit() || it == '_' || it == '.'
            } -> UsernameIssue.InvalidCharacters

            ".." in username || username.endsWith('.') -> UsernameIssue.InvalidDots

            else -> null
        }
    }

    private fun Char.isAsciiLowercaseLetter() = this in 'a'..'z'

    companion object {
        const val MIN_LENGTH = 3
        const val MAX_LENGTH = 30
    }
}

sealed interface UsernameStatus {
    data class Invalid(val issue: UsernameIssue) : UsernameStatus

    data class Available(val username: String) : UsernameStatus

    data class Unavailable(val reason: UsernameUnavailableReason) : UsernameStatus

    data class Unknown(val error: AppError) : UsernameStatus
}

/** Validates locally first, so the server is only asked about well-formed names. */
class CheckUsernameUseCase @Inject constructor(
    private val validate: ValidateUsernameUseCase,
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(raw: String): UsernameStatus {
        validate(raw)?.let { return UsernameStatus.Invalid(it) }
        return when (val outcome = accounts.usernameAvailability(validate.normalize(raw))) {
            is Outcome.Success -> when {
                outcome.value.available -> UsernameStatus.Available(outcome.value.username)
                else -> UsernameStatus.Unavailable(outcome.value.reason ?: UsernameUnavailableReason.Invalid)
            }

            is Outcome.Failure -> UsernameStatus.Unknown(outcome.error)
        }
    }
}

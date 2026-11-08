package com.geowoof.core.domain

import com.geowoof.core.model.CodeIssue
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.PasswordIssue
import javax.inject.Inject

// Client-side mirrors of the API rules, for instant feedback. The API stays
// authoritative: these never replace its validation.

class ValidateEmailUseCase @Inject constructor() {
    operator fun invoke(email: String): EmailIssue? {
        val normalized = email.trim()
        return when {
            normalized.isEmpty() -> EmailIssue.Empty
            normalized.length > MAX_EMAIL_LENGTH || !EMAIL_SHAPE.matches(normalized) -> EmailIssue.Invalid
            else -> null
        }
    }

    private companion object {
        const val MAX_EMAIL_LENGTH = 254
        val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

class ValidatePasswordUseCase @Inject constructor() {
    operator fun invoke(password: String): PasswordIssue? = when {
        password.length < MIN_LENGTH -> PasswordIssue.TooShort
        password.length > MAX_LENGTH -> PasswordIssue.TooLong
        password.none(Char::isLetter) -> PasswordIssue.MissingLetter
        password.none(Char::isDigit) -> PasswordIssue.MissingDigit
        else -> null
    }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 128
    }
}

class ValidateDisplayNameUseCase @Inject constructor() {
    operator fun invoke(displayName: String): DisplayNameIssue? = when {
        displayName.isBlank() -> DisplayNameIssue.Empty
        displayName.trim().length > MAX_LENGTH -> DisplayNameIssue.TooLong
        else -> null
    }

    companion object {
        const val MAX_LENGTH = 50
    }
}

class ValidateVerificationCodeUseCase @Inject constructor() {
    operator fun invoke(code: String): CodeIssue? {
        val normalized = code.trim()
        return if (normalized.length == LENGTH && normalized.all(Char::isDigit)) null else CodeIssue.Incomplete
    }

    companion object {
        const val LENGTH = 6
    }
}

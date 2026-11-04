package com.geowoof.core.model

// Why a form field is invalid, according to the API rules (docs/api.md). Validators live in :core:domain.

enum class EmailIssue {
    Empty,
    Invalid,
}

enum class PasswordIssue {
    TooShort,
    TooLong,
    MissingLetter,
    MissingDigit,
}

enum class DisplayNameIssue {
    Empty,
    TooLong,
}

enum class UsernameIssue {
    TooShort,
    TooLong,
    MustStartWithLetter,
    InvalidCharacters,
    InvalidDots,
}

/** Verification and password-reset codes have exactly 6 digits. */
enum class CodeIssue {
    Incomplete,
}

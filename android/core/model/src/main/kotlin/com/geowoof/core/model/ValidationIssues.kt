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

/** A short optional text that is longer than the API allows. */
enum class TextIssue {
    TooLong,
}

enum class PetNameIssue {
    Empty,
    TooLong,
}

enum class SpeciesOtherIssue {
    Empty,
    TooLong,
}

enum class MicrochipIssue {
    TooLong,
    InvalidCharacters,
}

enum class PetDateIssue {
    InTheFuture,
    TooOld,
}

enum class WeightIssue {
    NotANumber,
    OutOfRange,
    TooManyDecimals,
}

data class ProfileIssues(
    val displayName: DisplayNameIssue? = null,
    val bio: TextIssue? = null,
    val city: TextIssue? = null,
) {
    val isValid: Boolean get() = this == ProfileIssues()
}

data class PetIssues(
    val name: PetNameIssue? = null,
    val speciesOther: SpeciesOtherIssue? = null,
    val breed: TextIssue? = null,
    val birthDate: PetDateIssue? = null,
    val color: TextIssue? = null,
    val microchip: MicrochipIssue? = null,
    val bio: TextIssue? = null,
) {
    val isValid: Boolean get() = this == PetIssues()
}

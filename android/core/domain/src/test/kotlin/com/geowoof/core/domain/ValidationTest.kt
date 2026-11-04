package com.geowoof.core.domain

import com.geowoof.core.model.AppError
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PasswordIssue
import com.geowoof.core.model.UsernameAvailability
import com.geowoof.core.model.UsernameIssue
import com.geowoof.core.model.UsernameUnavailableReason
import com.geowoof.core.testing.FakeAccountRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    private val validateUsername = ValidateUsernameUseCase()

    @Test
    fun emails_follow_the_api_rules() {
        val validate = ValidateEmailUseCase()

        assertEquals(EmailIssue.Empty, validate("  "))
        assertEquals(EmailIssue.Invalid, validate("ana@example"))
        assertNull(validate(" Ana@Example.com "))
    }

    @Test
    fun passwords_need_length_a_letter_and_a_digit() {
        val validate = ValidatePasswordUseCase()

        assertEquals(PasswordIssue.TooShort, validate("abc1"))
        assertEquals(PasswordIssue.MissingDigit, validate("onlyletters"))
        assertEquals(PasswordIssue.MissingLetter, validate("12345678"))
        assertEquals(PasswordIssue.TooLong, validate("a1".repeat(65)))
        assertNull(validate("Password1"))
    }

    @Test
    fun display_names_have_one_to_fifty_characters() {
        val validate = ValidateDisplayNameUseCase()

        assertEquals(DisplayNameIssue.Empty, validate(" "))
        assertEquals(DisplayNameIssue.TooLong, validate("a".repeat(51)))
        assertNull(validate(" Luna "))
    }

    @Test
    fun usernames_follow_the_api_rules() {
        assertEquals(UsernameIssue.TooShort, validateUsername("ab"))
        assertEquals(UsernameIssue.MustStartWithLetter, validateUsername("1ana"))
        assertEquals(UsernameIssue.InvalidCharacters, validateUsername("ana paws"))
        assertEquals(UsernameIssue.InvalidCharacters, validateUsername("añoso"))
        assertEquals(UsernameIssue.InvalidDots, validateUsername("ana..paws"))
        assertEquals(UsernameIssue.InvalidDots, validateUsername("ana."))
        assertNull(validateUsername("@Ana.Paws_1"))
        assertEquals("ana.paws", validateUsername.normalize(" @Ana.Paws "))
    }

    @Test
    fun only_well_formed_usernames_are_checked_with_the_server() = runTest {
        val accounts = FakeAccountRepository()
        val check = CheckUsernameUseCase(validateUsername, accounts)
        accounts.availability =
            Outcome.Success(UsernameAvailability("bea", available = false, UsernameUnavailableReason.Taken))

        assertEquals(UsernameStatus.Invalid(UsernameIssue.TooShort), check("x"))
        assertEquals(0, accounts.availabilityChecks)
        assertEquals(UsernameStatus.Unavailable(UsernameUnavailableReason.Taken), check("Bea"))

        accounts.availability = Outcome.Success(UsernameAvailability("luna", available = true, reason = null))
        assertEquals(UsernameStatus.Available("luna"), check("@Luna"))

        accounts.availability = Outcome.Failure(AppError.Network)
        assertEquals(UsernameStatus.Unknown(AppError.Network), check("luna"))
    }
}

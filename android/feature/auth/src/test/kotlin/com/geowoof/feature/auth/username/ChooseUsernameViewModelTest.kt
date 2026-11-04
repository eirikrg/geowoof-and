package com.geowoof.feature.auth.username

import com.geowoof.core.domain.CheckUsernameUseCase
import com.geowoof.core.domain.UsernameStatus
import com.geowoof.core.domain.ValidateDisplayNameUseCase
import com.geowoof.core.domain.ValidateUsernameUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.UsernameAvailability
import com.geowoof.core.model.UsernameIssue
import com.geowoof.core.model.UsernameUnavailableReason
import com.geowoof.core.testing.FakeAccountRepository
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestUsers
import com.geowoof.feature.auth.enter
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChooseUsernameViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user(username = null, displayName = "Ana")))
    private val accounts = FakeAccountRepository()
    private val viewModel by lazy {
        ChooseUsernameViewModel(
            auth,
            accounts,
            ValidateUsernameUseCase(),
            CheckUsernameUseCase(ValidateUsernameUseCase(), accounts),
            ValidateDisplayNameUseCase(),
        )
    }

    @Test
    fun starts_with_the_current_display_name() {
        assertEquals("Ana", viewModel.displayName.text.toString())
    }

    @Test
    fun availability_is_checked_once_the_user_pauses_typing() = runTest {
        viewModel.username.enter("a")
        viewModel.username.enter("an")
        viewModel.username.enter("ana")
        runCurrent()

        advanceTimeBy(399)
        assertEquals(0, accounts.availabilityChecks)

        advanceTimeBy(2)
        assertEquals(1, accounts.availabilityChecks)
        assertEquals(UsernameStatus.Available("ana"), viewModel.uiState.value.usernameStatus)
    }

    @Test
    fun badly_formed_names_are_rejected_without_asking_the_server() = runTest {
        viewModel.username.enter("1ana")

        advanceTimeBy(401)

        assertEquals(UsernameStatus.Invalid(UsernameIssue.MustStartWithLetter), viewModel.uiState.value.usernameStatus)
        assertEquals(0, accounts.availabilityChecks)
    }

    @Test
    fun clearing_the_field_clears_the_result() = runTest {
        viewModel.username.enter("ana")
        advanceTimeBy(401)

        viewModel.username.enter("")
        advanceTimeBy(401)

        assertNull(viewModel.uiState.value.usernameStatus)
        assertEquals(1, accounts.availabilityChecks)
    }

    @Test
    fun choosing_a_username_keeps_an_unchanged_display_name() {
        viewModel.username.enter("Ana.Paws")

        viewModel.submit()

        assertEquals(listOf("ana.paws"), accounts.chosenUsernames)
        assertTrue(accounts.displayNames.isEmpty())
    }

    @Test
    fun a_changed_display_name_is_saved_before_the_username() {
        viewModel.username.enter("ana.paws")
        viewModel.displayName.enter(" Ana López ")

        viewModel.submit()

        assertEquals(listOf("Ana López"), accounts.displayNames)
        assertEquals(listOf("ana.paws"), accounts.chosenUsernames)
    }

    @Test
    fun a_name_taken_meanwhile_is_shown_on_the_field() {
        accounts.chooseResult = Outcome.Failure(AppError.Api(status = 409, code = "identity.username_taken"))
        viewModel.username.enter("ana.paws")

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(UsernameStatus.Unavailable(UsernameUnavailableReason.Taken), state.usernameStatus)
        assertNull(state.error)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun an_unavailable_name_is_not_submitted() = runTest {
        accounts.availability =
            Outcome.Success(
                UsernameAvailability("admin", available = false, reason = UsernameUnavailableReason.Reserved),
            )
        viewModel.username.enter("admin")
        advanceTimeBy(401)

        viewModel.submit()

        assertTrue(accounts.chosenUsernames.isEmpty())
    }

    @Test
    fun an_empty_display_name_is_reported() {
        viewModel.username.enter("ana.paws")
        viewModel.displayName.enter("  ")

        viewModel.submit()

        assertEquals(DisplayNameIssue.Empty, viewModel.uiState.value.displayNameIssue)
        assertTrue(accounts.chosenUsernames.isEmpty())
    }
}
